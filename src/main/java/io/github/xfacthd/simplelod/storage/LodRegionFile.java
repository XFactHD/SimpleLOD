package io.github.xfacthd.simplelod.storage;

import com.mojang.logging.LogUtils;
import io.github.xfacthd.simplelod.data.LodChunk;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.FastBufferedInputStream;
import net.minecraft.util.FileUtil;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionBitmap;
import org.apache.commons.io.function.IORunnable;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

// TODO: switch from compressed NBT to compressed raw binary storage
final class LodRegionFile implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SECTOR_BYTES = 2048;
    private static final int OFFSETS_COUNT = LodRegionPos.REGION_SIZE * LodRegionPos.REGION_SIZE;
    private static final int HEADER_SECTORS = 1;
    private static final int SECTOR_INDEX_OFFSET = 8;
    private static final int SECTOR_INDEX_BITS = 16;
    private static final int SECTOR_INDEX_MASK = (1 << SECTOR_INDEX_BITS) - 1;
    private static final int SECTOR_COUNT_MASK = (1 << SECTOR_INDEX_OFFSET) - 1;
    private static final int MARKER_OFFSET_EXTERNAL = 0xFF000000;
    private static final int CHUNK_SIZE_BYTES = Integer.BYTES;
    private static final ByteBuffer PADDING_BUFFER = ByteBuffer.allocateDirect(1);

    private final Path folderPath;
    private final Path regionFilePath;
    private final FileChannel fileChannel;
    private final ByteBuffer header = ByteBuffer.allocateDirect(SECTOR_BYTES);
    private final IntBuffer offsets;
    private final RegionBitmap usedSectors = new RegionBitmap();
    private final Int2ObjectMap<LodChunk> dirtyChunks = new Int2ObjectOpenHashMap<>();
    private final Int2ReferenceMap<InProgressWrite> inProgressWrites = new Int2ReferenceOpenHashMap<>();
    private final Int2ReferenceMap<CompletableFuture<Void>> inProgressReads = new Int2ReferenceOpenHashMap<>();
    private CompletableFuture<Void> saveFuture = CompletableFuture.completedFuture(null);

    static LodRegionFile create(Path folderPath, long regionPos) {
        try {
            return new LodRegionFile(folderPath, regionPos);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private LodRegionFile(Path folderPath, long regionPos) throws IOException {
        this.folderPath = folderPath;
        this.regionFilePath = regionPath(folderPath, regionPos);
        try {
            FileUtil.createDirectoriesSafe(folderPath);
            if (Minecraft.getInstance().options.syncWrites) {
                this.fileChannel = FileChannel.open(regionFilePath, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.DSYNC);
            } else {
                this.fileChannel = FileChannel.open(regionFilePath, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.offsets = header.asIntBuffer();
        this.offsets.limit(OFFSETS_COUNT);
        this.usedSectors.force(0, HEADER_SECTORS);

        int readHeaderBytes = fileChannel.read(header, 0L);
        if (readHeaderBytes != -1) {
            if (readHeaderBytes != SECTOR_BYTES) {
                LOGGER.warn("LOD region file {} has truncated header: {}", regionFilePath, readHeaderBytes);
            }

            long size = Files.size(regionFilePath);
            for (int i = 0; i < OFFSETS_COUNT; i++) {
                int offset = offsets.get(i);
                if (offset != 0 && offset != MARKER_OFFSET_EXTERNAL) {
                    int sectorIndex = getSectorIndex(offset);
                    int sectorCount = getSectorCount(offset);
                    if (sectorIndex < HEADER_SECTORS) {
                        LOGGER.warn("LOD region file {} has invalid sector at index: {}; sector {} overlaps with header", regionFilePath, i, sectorIndex);
                        offsets.put(i, 0);
                    } else if (sectorCount == 0) {
                        LOGGER.warn("LOD region file {} has an invalid sector at index: {}; size has to be > 0", regionFilePath, i);
                        offsets.put(i, 0);
                    } else if ((long) sectorIndex * SECTOR_BYTES > size) {
                        LOGGER.warn("LOD region file {} has an invalid sector at index: {}; sector {} is out of bounds", regionFilePath, i, sectorIndex);
                        offsets.put(i, 0);
                    } else {
                        usedSectors.force(sectorIndex, sectorCount);
                    }
                }
            }
        }
    }

    void update(long chunkPos, LodChunk chunk) {
        dirtyChunks.put(LodRegionPos.getRegionRelative(chunkPos), chunk);
    }

    void load(long chunkPos, int chunkHeight, LodChunk.Factory chunkFactory, Consumer<LodChunk> consumer) {
        int chunkKey = LodRegionPos.getRegionRelative(chunkPos);
        if (inProgressWrites.containsKey(chunkKey)) {
            InProgressWrite write = inProgressWrites.get(chunkKey);
            consumer.accept(LodChunkSerializer.unpack(write.packedData, chunkFactory));
            return;
        }

        int offset = offsets.get(chunkKey);
        if (offset == 0 || inProgressReads.containsKey(chunkKey)) {
            return;
        }

        CompletableFuture<Void> activeRead = new CompletableFuture<>();
        inProgressReads.put(chunkKey, activeRead);
        CompletableFuture.supplyAsync(() -> readChunk(chunkPos, offset, chunkHeight, chunkFactory, activeRead), Util.backgroundExecutor())
                .handleAsync((chunk, error) -> {
                    if (chunk != null) {
                        consumer.accept(chunk);
                    } else if (error != null) {
                        LOGGER.error("Failed to read chunk {} from LOD region file {}: ", ChunkPos.unpack(chunkPos), regionFilePath, error);
                    }
                    return null;
                }, Minecraft.getInstance());
        activeRead.thenRunAsync(() -> inProgressReads.remove(chunkKey, activeRead), Minecraft.getInstance());
    }

    private @Nullable LodChunk readChunk(long chunkPos, int offset, int chunkHeight, LodChunk.Factory chunkFactory, CompletableFuture<@Nullable Void> activeRead) {
        try {
            CompoundTag nbtData;
            if (offset == MARKER_OFFSET_EXTERNAL) {
                Path externalFile = chunkPath(folderPath, chunkPos);
                if (!Files.isRegularFile(externalFile)) {
                    return null;
                }
                nbtData = readChunkCompressed(Files.newInputStream(externalFile));
            } else {
                int sectorIndex = getSectorIndex(offset);
                int sectorCount = getSectorCount(offset);
                ByteBuffer buffer = ByteBuffer.allocate(sectorCount * SECTOR_BYTES);
                fileChannel.read(buffer, (long) sectorIndex * SECTOR_BYTES);
                buffer.flip();
                if (buffer.remaining() < CHUNK_SIZE_BYTES) {
                    return null;
                }
                int length = buffer.getInt();
                if (length < 0 || length > buffer.remaining()) {
                    return null;
                }
                nbtData = readChunkCompressed(buffer, length);
            }
            return LodChunkSerializer.unpack(LodChunkSerializer.deserialize(chunkPos, chunkHeight, nbtData), chunkFactory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            activeRead.complete(null);
        }
    }

    void save(boolean force) {
        if (dirtyChunks.isEmpty()) {
            return;
        }
        if (!force && !saveFuture.isDone()) {
            LOGGER.info("Previous save not complete, aborting auto-save");
            return;
        }

        for (LodChunk chunk : dirtyChunks.values()) {
            submitWrite(chunk.getChunkPos(), LodChunkSerializer.packSections(chunk));
        }
        dirtyChunks.clear();

        if (force) {
            saveFuture.join();
            flush();
        }
    }

    private void submitWrite(long chunkPos, LodChunkSerializer.PackedData packedData) {
        InProgressWrite write = new InProgressWrite(packedData);
        int chunkIndex = LodRegionPos.getRegionRelative(chunkPos);

        inProgressWrites.put(chunkIndex, write);
        CompletableFuture<Void> activeRead = inProgressReads.get(chunkIndex);
        saveFuture = saveFuture.thenRunAsync(() -> writeChunk(chunkPos, chunkIndex, write, activeRead), Util.backgroundExecutor());

        write.future.handleAsync((_, error) -> {
            inProgressWrites.remove(chunkIndex, write);
            if (error != null) {
                LOGGER.error("Failed to write chunk {} in LOD region file {}: ", chunkIndex, regionFilePath);
            }
            return null;
        }, Minecraft.getInstance());
    }

    private void writeChunk(long chunkPos, int chunkIndex, InProgressWrite write, @Nullable CompletableFuture<Void> activeRead) {
        if (activeRead != null) {
            activeRead.join();
        }

        CompoundTag nbtData = LodChunkSerializer.serialize(write.packedData);
        try {
            ByteBuffer data = writeChunkCompressed(nbtData);
            int prevOffset = offsets.get(chunkIndex);
            int size = data.remaining();
            int sectorCount = getSectorCountForSize(size);
            IORunnable commitTask;
            if (sectorCount > SECTOR_COUNT_MASK) {
                if (prevOffset != 0 && prevOffset != MARKER_OFFSET_EXTERNAL) {
                    usedSectors.free(getSectorIndex(prevOffset), getSectorCount(prevOffset));
                }
                offsets.put(chunkIndex, MARKER_OFFSET_EXTERNAL);
                Path extPath = chunkPath(folderPath, chunkPos);
                LOGGER.warn("Saving oversized chunk {} ({} bytes} to external file {}", chunkPos, size, extPath);
                commitTask = writeExternalFile(extPath, data);
            } else {
                int sectorIndex = allocateSectors(prevOffset, sectorCount);
                offsets.put(chunkIndex, packOffset(sectorIndex, sectorCount));
                commitTask = () -> Files.deleteIfExists(chunkPath(folderPath, chunkPos));
                writeToFileChannel(fileChannel, data, 0, (long) sectorIndex * SECTOR_BYTES);
            }
            writeToFileChannel(fileChannel, header, 0, 0L);
            commitTask.run();
            write.future.complete(null);
        } catch (IOException e) {
            write.future.completeExceptionally(e);
        }
    }

    private int allocateSectors(int prevOffset, int sectorCount) {
        int prevSectorIndex = getSectorIndex(prevOffset);
        int prevSectorCount = getSectorCount(prevOffset);
        if (sectorCount > prevSectorCount) {
            usedSectors.free(prevSectorIndex, prevSectorCount);
            return usedSectors.allocate(sectorCount);
        }
        if (sectorCount < prevSectorCount) {
            usedSectors.free(prevSectorIndex + sectorCount, prevSectorCount - sectorCount);
        }
        return prevSectorIndex;
    }

    private IORunnable writeExternalFile(Path extPath, ByteBuffer data) throws IOException {
        Path tmpPath = Files.createTempFile(folderPath, "tmp", null);
        try (FileChannel extFile = FileChannel.open(tmpPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            writeToFileChannel(extFile, data, CHUNK_SIZE_BYTES, 0);
        }
        return () -> moveTempFile(tmpPath, extPath);
    }

    private void flush() {
        try {
            fileChannel.force(true);
        } catch (IOException e) {
            LOGGER.error("Failed to flush LOD region file {}: ", regionFilePath, e);
        }
    }

    private void padToFullSector() throws IOException {
        int fileSize = (int) fileChannel.size();
        int paddedSize = getSectorCountForSize(fileSize) * SECTOR_BYTES;
        if (fileSize != paddedSize) {
            ByteBuffer padding = PADDING_BUFFER.duplicate();
            padding.position(0);
            writeToFileChannel(fileChannel, padding, 0, paddedSize - 1);
        }
    }

    @Override
    public void close() {
        try {
            padToFullSector();
        } catch (IOException e) {
            LOGGER.error("Failed to pad LOD region file {}: ", regionFilePath, e);
        }
        flush();
        try {
            fileChannel.close();
        } catch (IOException e) {
            LOGGER.error("Failed to close LOD region file {}: ", regionFilePath, e);
        }
    }

    private static int getSectorIndex(int offset) {
        return offset >> SECTOR_INDEX_OFFSET & SECTOR_INDEX_MASK;
    }

    private static int getSectorCount(int offset) {
        return offset & SECTOR_COUNT_MASK;
    }

    private static int getSectorCountForSize(int size) {
        return (size + SECTOR_BYTES - 1) / SECTOR_BYTES;
    }

    private static int packOffset(int index, int count) {
        return ((index & SECTOR_INDEX_MASK) << SECTOR_INDEX_OFFSET) | (count & SECTOR_COUNT_MASK);
    }

    private static Path regionPath(Path folderPath, long regionPos) {
        int regionX = LodRegionPos.getX(regionPos);
        int regionZ = LodRegionPos.getZ(regionPos);
        return folderPath.resolve(String.format(Locale.ROOT, "r.%d.%d.slr", regionX, regionZ));
    }

    private static Path chunkPath(Path folderPath, long chunkPos) {
        int chunkX = ChunkPos.getX(chunkPos);
        int chunkZ = ChunkPos.getZ(chunkPos);
        return folderPath.resolve(String.format(Locale.ROOT, "c.%d.%d.slc", chunkX, chunkZ));
    }

    private static ByteBuffer writeChunkCompressed(CompoundTag nbtData) throws IOException {
        LodChunkOutputStream outputStream = new LodChunkOutputStream();
        OutputStream compressor = new DeflaterOutputStream(outputStream);
        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(compressor))) {
            NbtIo.write(nbtData, dos);
        }
        return outputStream.asBuffer();
    }

    private static CompoundTag readChunkCompressed(ByteBuffer data, int size) throws IOException {
        return readChunkCompressed(new ByteArrayInputStream(data.array(), CHUNK_SIZE_BYTES, size));
    }

    private static CompoundTag readChunkCompressed(InputStream inputStream) throws IOException {
        InputStream decompressor = new InflaterInputStream(inputStream);
        try (DataInputStream dis = new DataInputStream(new FastBufferedInputStream(decompressor))) {
            return NbtIo.read(dis, NbtAccounter.defaultQuota());
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void writeToFileChannel(FileChannel channel, ByteBuffer data, int bufferPosition, long filePosition) throws IOException {
        data.position(bufferPosition);
        channel.write(data, filePosition);
    }

    private static void moveTempFile(Path tempPath, Path targetPath) throws IOException {
        try {
            Files.move(tempPath, targetPath, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static final class InProgressWrite {
        private final LodChunkSerializer.PackedData packedData;
        private final CompletableFuture<@Nullable Void> future = new CompletableFuture<>();

        private InProgressWrite(LodChunkSerializer.PackedData packedData) {
            this.packedData = packedData;
        }
    }

    private static final class LodChunkOutputStream extends ByteArrayOutputStream {
        LodChunkOutputStream() {
            super(SECTOR_BYTES);
            // Write placeholder bytes for chunk size
            write(0);
            write(0);
            write(0);
            write(0);
        }

        ByteBuffer asBuffer() {
            ByteBuffer buffer = ByteBuffer.wrap(buf, 0, count);
            buffer.putInt(0, count - CHUNK_SIZE_BYTES);
            return buffer;
        }
    }
}
