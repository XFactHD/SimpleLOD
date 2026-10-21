package io.github.xfacthd.simplelod.storage;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import io.github.xfacthd.simplelod.compiler.LodCompiler;
import io.github.xfacthd.simplelod.data.LodChunk;
import io.github.xfacthd.simplelod.data.LodSection;
import io.github.xfacthd.simplelod.data.LodSectionMask;
import io.github.xfacthd.simplelod.data.quadtree.ChunkQuadTree;
import io.github.xfacthd.simplelod.debug.StorageStats;
import io.github.xfacthd.simplelod.generator.LodGenerator;
import io.github.xfacthd.simplelod.util.AccumulativeStopwatch;
import io.github.xfacthd.simplelod.util.Config;
import io.github.xfacthd.simplelod.util.PathResolver;
import io.github.xfacthd.simplelod.util.Utils;
import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.Objects;

// TODO: consider dropping regions which are fully out-of-range
// FIXME: some chunks seem to fail to mesh on initial load
public final class LodChunkStorage implements Closeable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long AUTO_SAVE_INTERVAL = 6000L;
    private static final AccumulativeStopwatch UPDATE_STOPWATCH = new AccumulativeStopwatch();
    @Nullable
    private static LodChunkStorage activeStorage = null;

    private final ClientLevel level;
    private final Path rootPath;
    private final int minChunkY;
    private final int maxChunkY;
    private final int chunkHeight;
    private final LodSectionMask scratchMask;
    private final LodSectionMask fullMask;
    private final Long2ObjectMap<LodRegionFile> regions = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectMap<LodChunk> chunks = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectFunction<LodRegionFile> regionFactory;
    private final LodChunk.Factory chunkFactory;
    @Nullable
    private SectionPos lastCameraSectionPos;
    private int lastVanillaRenderDistance;
    @Nullable
    private SectionPos capturedCameraSectionPos;
    @Nullable
    private ChunkQuadTree vanillaChunkQuadTree;
    @Nullable
    private ChunkQuadTree lodChunkQuadTree;
    private boolean captureCameraSectionPos;
    private long lastAutoSave = 0L;

    private LodChunkStorage(ClientLevel level) {
        this.level = level;
        this.rootPath = PathResolver.getStoragePath(level);
        this.minChunkY = level.getMinSectionY();
        this.maxChunkY = level.getMaxSectionY();
        this.chunkHeight = level.getSectionsCount();
        this.scratchMask = LodSectionMask.create(chunkHeight);
        this.fullMask = LodSectionMask.getConstant(chunkHeight, true);
        this.regionFactory = pos -> LodRegionFile.create(rootPath, pos);
        this.chunkFactory = pos -> new LodChunk(pos, chunkHeight);
    }

    public int getMinChunkY() {
        return minChunkY;
    }

    public void updateSection(long sectionPos, @Nullable LodSection section) {
        long chunkPos = ChunkPos.pack(SectionPos.x(sectionPos), SectionPos.z(sectionPos));
        int index = SectionPos.y(sectionPos) - minChunkY;
        LodChunk chunk = chunks.computeIfAbsent(chunkPos, chunkFactory);
        if (chunk.updateSection(index, section, false)) {
            getRegionFile(chunkPos).update(chunkPos, chunk);
            Utils.ifPresent(chunks.get(Utils.offset(chunkPos, -1,  0)), LodChunk::invalidateMesh);
            Utils.ifPresent(chunks.get(Utils.offset(chunkPos,  1,  0)), LodChunk::invalidateMesh);
            Utils.ifPresent(chunks.get(Utils.offset(chunkPos,  0, -1)), LodChunk::invalidateMesh);
            Utils.ifPresent(chunks.get(Utils.offset(chunkPos,  0,  1)), LodChunk::invalidateMesh);
        }
    }

    public void forEachVisible(CameraRenderState camera, int vanillaRenderDistance, LodChunkConsumer consumer) {
        if (vanillaChunkQuadTree == null || lodChunkQuadTree == null) {
            return;
        }

        int camY = capturedCameraSectionPos != null ? capturedCameraSectionPos.y() : SectionPos.blockToSectionCoord(camera.blockPos.getY());
        // TODO: try misusing the "transient block" cleanup system to keep LOD chunks rendered until the real chunk is visible
        Frustum frustum = camera.cullFrustum;
        int minBlockY = SectionPos.sectionToBlockCoord(minChunkY);
        int maxBlockY = SectionPos.sectionToBlockCoord(maxChunkY + 1);
        vanillaChunkQuadTree.forEachInFrustum(frustum, minBlockY, maxBlockY, pos -> {
            LodChunk chunk = chunks.get(pos);
            //noinspection ConstantValue
            if (chunk == null) {
                return;
            }

            scratchMask.clear(true);
            int minY = Math.max(minChunkY, camY - vanillaRenderDistance);
            int maxY = Math.min(maxChunkY, camY + vanillaRenderDistance);
            for (int i = minY; i <= maxY; i++) {
                scratchMask.setPresent(i - minChunkY, false);
            }
            scratchMask.and(chunk.getSectionMask());
            if (!scratchMask.isEmpty() && chunk.isInFrustum(frustum, minChunkY, scratchMask)) {
                consumer.accept(pos, chunk, scratchMask);
            }
        });
        lodChunkQuadTree.forEachInFrustum(frustum, minBlockY, maxBlockY, pos -> {
            LodChunk chunk = chunks.get(pos);
            //noinspection ConstantValue
            if (chunk != null && chunk.isInFrustum(frustum, minChunkY, chunk.getSectionMask())) {
                consumer.accept(pos, chunk, fullMask);
            }
        });
    }

    public void invalidateMeshes() {
        chunks.values().forEach(LodChunk::invalidateMesh);
    }

    public @Nullable SectionPos getCapturedCameraSectionPos() {
        return capturedCameraSectionPos;
    }

    public StorageStats getDebugStats() {
        return new StorageStats(chunks.size(), UPDATE_STOPWATCH.getAverage());
    }

    @Override
    public void close() {
        LodGenerator.cancelTasks();
        LodCompiler.cancelTasks();
        chunks.values().forEach(LodChunk::close);
        chunks.clear();
        regions.values().forEach(LodRegionFile::close);
        regions.clear();
        LodCompiler.clearBuffers();
    }

    private LodRegionFile getRegionFile(long chunkPos) {
        return regions.computeIfAbsent(LodRegionPos.fromChunk(chunkPos), regionFactory);
    }

    private void loadFromDisk(long chunkPos) {
        getRegionFile(chunkPos).load(chunkPos, chunkHeight, chunkFactory, chunk -> chunks.putIfAbsent(chunkPos, chunk));
    }

    private void saveAll(boolean force) {
        if (force) {
            LOGGER.info("Saving LOD chunks...");
        }
        for (LodRegionFile region : regions.values()) {
            region.save(force);
        }
    }

    public static LodChunkStorage get() {
        return Objects.requireNonNull(activeStorage, "Not in a level");
    }

    public static @Nullable LodChunkStorage tryGet() {
        return activeStorage;
    }

    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ClientLevel level) {
            activeStorage = new LodChunkStorage(level);
        }
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (activeStorage != null && event.getLevel() == activeStorage.level) {
            activeStorage.saveAll(true);
            activeStorage.close();
            activeStorage = null;
        }
    }

    public static void onDebugKeyInput(InputEvent.Key event) {
        if (activeStorage != null && event.getKey() == InputConstants.KEY_U && Minecraft.getInstance().options.keyDebugModifier.isDown()) {
            if (event.getKeyEvent().hasShiftDown()) {
                activeStorage.capturedCameraSectionPos = null;
            } else {
                activeStorage.captureCameraSectionPos = true;
            }
        }
    }

    public static void onClientTick(@SuppressWarnings("unused") ClientTickEvent.Post event) {
        LodChunkStorage storage = activeStorage;
        if (storage == null) {
            return;
        }

        UPDATE_STOPWATCH.start();

        if (storage.level.getGameTime() - storage.lastAutoSave > AUTO_SAVE_INTERVAL) {
            storage.saveAll(false);
            storage.lastAutoSave = storage.level.getGameTime();
        }

        SectionPos cameraPos;
        if (storage.capturedCameraSectionPos != null && !storage.captureCameraSectionPos) {
            cameraPos = storage.capturedCameraSectionPos;
        } else {
            cameraPos = SectionPos.of(Minecraft.getInstance().gameRenderer.mainCamera().blockPosition());
        }
        SectionPos lastCameraPos = storage.lastCameraSectionPos;
        int vanillaRenderDistance = Minecraft.getInstance().options.getEffectiveRenderDistance();
        if (!Objects.equals(lastCameraPos, cameraPos) || storage.lastVanillaRenderDistance != vanillaRenderDistance) {
            if (updateLoadedChunks(storage, cameraPos, lastCameraPos, vanillaRenderDistance)) {
                storage.lastCameraSectionPos = cameraPos;
                storage.lastVanillaRenderDistance = vanillaRenderDistance;
            }
        }

        if (storage.captureCameraSectionPos) {
            storage.capturedCameraSectionPos = cameraPos;
            storage.captureCameraSectionPos = false;
        }

        UPDATE_STOPWATCH.stop();
    }

    private static boolean updateLoadedChunks(LodChunkStorage storage, SectionPos cameraPos, @Nullable SectionPos lastCameraPos, int vanillaRenderDistance) {
        if (lastCameraPos == null && !Minecraft.getInstance().gameRenderer.mainCamera().isInitialized()) {
            // We may reach this before the camera is fully initialized, causing it to appear at 0,0,0 and breaking initial LOD loading
            return false;
        }

        long origin = ChunkPos.pack(cameraPos.x(), cameraPos.z());
        int lodRenderDistance = Config.getRenderDistance();
        storage.vanillaChunkQuadTree = ChunkQuadTree.create(origin, 0, vanillaRenderDistance);
        storage.lodChunkQuadTree = ChunkQuadTree.create(origin, vanillaRenderDistance + 1, lodRenderDistance);

        if (lastCameraPos == null) {
            Utils.forAllChunksAroundCircular(origin, vanillaRenderDistance, lodRenderDistance, storage::loadFromDisk);
            return true;
        }

        // TODO: calculate difference of camera positions and only traverse circles which changed for both loading and discarding
        //       (discard and reload all when the distance is higher than the LOD RD or maybe even when higher than vanilla RD)
        Utils.forAllChunksAroundCircular(origin, 0, lodRenderDistance, chunkPos -> {
            int chunkX = ChunkPos.getX(chunkPos);
            int chunkZ = ChunkPos.getZ(chunkPos);
            if (!ChunkTrackingView.isInViewDistance(lastCameraPos.x(), lastCameraPos.z(), lodRenderDistance, chunkX, chunkZ)) {
                storage.loadFromDisk(chunkPos);
            }
        });

        storage.chunks.values().removeIf(chunk -> {
            long chunkPos = chunk.getChunkPos();
            int chunkX = ChunkPos.getX(chunkPos);
            int chunkZ = ChunkPos.getZ(chunkPos);
            if (!ChunkTrackingView.isInViewDistance(cameraPos.x(), cameraPos.z(), lodRenderDistance, chunkX, chunkZ)) {
                chunk.close();
                return true;
            }
            return false;
        });
        return true;
    }

    @FunctionalInterface
    public interface LodChunkConsumer {
        void accept(long chunkPos, LodChunk chunk, LodSectionMask sectionMask);
    }
}
