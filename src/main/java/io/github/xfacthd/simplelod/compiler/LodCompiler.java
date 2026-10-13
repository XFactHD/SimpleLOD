package io.github.xfacthd.simplelod.compiler;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.StagingBuffer;
import com.mojang.blaze3d.vertex.TlsfAllocator;
import com.mojang.blaze3d.vertex.UberGpuBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import io.github.xfacthd.simplelod.data.LodChunk;
import io.github.xfacthd.simplelod.storage.LodChunkStorage;
import io.github.xfacthd.simplelod.data.LodSection;
import io.github.xfacthd.simplelod.data.LodSectionMask;
import io.github.xfacthd.simplelod.data.LevelOfDetail;
import io.github.xfacthd.simplelod.util.Config;
import io.github.xfacthd.simplelod.util.ThrottledTaskStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FaceInfo;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.Zone;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.function.Function;

public abstract sealed class LodCompiler implements Closeable permits LodCompilerNaive, LodCompilerGreedy {
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Direction[] HOR_DIRECTIONS = Direction.Plane.HORIZONTAL.stream().toArray(Direction[]::new);
    private static final @Nullable LodSection[] @Nullable[] EMPTY_ADJ_SECTIONS = new LodSection[4][];
    static final int FACE_MASK_ALL = 0b00111111;
    static final int FACE_MASK_DOWN = 1 << Direction.DOWN.ordinal();
    static final int FACE_MASK_UP = 1 << Direction.UP.ordinal();
    static final int FACE_MASK_NORTH = 1 << Direction.NORTH.ordinal();
    static final int FACE_MASK_SOUTH = 1 << Direction.SOUTH.ordinal();
    static final int FACE_MASK_WEST = 1 << Direction.WEST.ordinal();
    static final int FACE_MASK_EAST = 1 << Direction.EAST.ordinal();
    static final int[] EMPTY_COLORS = LevelOfDetail.BLOCK_INDIVIDUAL.makeArray();
    private static final VertexFormat VERTEX_FORMAT = DefaultVertexFormat.POSITION_COLOR_NORMAL;
    private static final int UBER_HEAP_SIZE = 134217728; // 128 MB
    private static final int UBER_STAGING_SIZE = 33554432; // 32 MB
    public static final int VERTEX_SIZE = VERTEX_FORMAT.getVertexSize();
    private static final ThrottledTaskStore TASK_STORE = ThrottledTaskStore.doubleExecutors();
    private static final Executor EXECUTOR = Util.backgroundExecutor().forName("SimpleLOD-lod_compiler");
    private static final Deque<ByteBufferBuilder> BUFFER_POOL = new ConcurrentLinkedDeque<>();
    @Nullable
    private static LodCompiler activeCompiler;

    private final StagingBuffer stagingBuffer = StagingBuffer.create("LOD", RenderSystem.getDevice(), UBER_STAGING_SIZE);
    private final UberGpuBuffer<LodChunkMesh> uberGpuBuffer = new UberGpuBuffer<>("LOD", GpuBuffer.USAGE_VERTEX, UBER_HEAP_SIZE, VERTEX_SIZE, stagingBuffer);

    LodCompiler() { }

    public static void setup() {
        activeCompiler = Config.isGreedyMesher() ? new LodCompilerGreedy() : new LodCompilerNaive();
    }

    public static void shutdown() {
        if (activeCompiler != null) {
            activeCompiler.close();
            activeCompiler = null;
        }
    }

    public static void cancelTasks() {
        TASK_STORE.cancelTasks();
    }

    public static void clearBuffers() {
        // Trigger a no-op upload to clear the UberGpuBuffer#skippedStagedAllocations set
        uploadStagedBuffers();
    }

    public static void compile(LodChunkStorage storage, LodChunk chunk, LevelOfDetail level, LodSectionMask sectionMask) {
        if (chunk.isEmpty()) {
            throw new IllegalArgumentException("Cannot generate mesh for empty LodChunk");
        }

        LodCompiler compiler = Objects.requireNonNull(activeCompiler);
        Function<Void, CompileResult> compileTask = buildCompileTask(compiler, storage, chunk, level, sectionMask.copy());
        TASK_STORE.addTask(future -> future
                .thenApplyAsync(compileTask, EXECUTOR)
                .thenApplyAsync(compiler::upload, Minecraft.getInstance())
        );
    }

    private static Function<Void, CompileResult> buildCompileTask(LodCompiler compiler, LodChunkStorage storage, LodChunk chunk, LevelOfDetail level, LodSectionMask sectionMask) {
        if (level == LevelOfDetail.CHUNK) {
            int minSection = chunk.getMinSection();
            int maxSection = chunk.getMaxSection();
            int chunkColor = chunk.getChunkLevelColor();
            // TODO: add mask for horizontal neighbors on the CHUNK level with the same or larger vertical extents
            return _ -> compileChunkAsync(chunk, sectionMask, minSection, maxSection, chunkColor);
        }

        @Nullable LodSection[] @Nullable[] adjSections = EMPTY_ADJ_SECTIONS;
        // FIXME: neighbor sections with a different detail level or whose related vanilla section is visible must be discarded from the copy
        //@Nullable LodSection[] @Nullable[] adjSections = new LodSection[4][];
        //long chunkPos = chunk.getChunkPos();
        //for (Direction adjDir : HOR_DIRECTIONS) {
        //    LodChunk adjChunk = storage.getChunk(Utils.offset(chunkPos, adjDir.getStepX(), adjDir.getStepZ()));
        //    adjSections[adjDir.get2DDataValue()] = Optionull.map(adjChunk, LodChunk::getSectionsClone);
        //}
        SectionData sectionData = new SectionData(chunk.getMinSection(), chunk.getMaxSection(), chunk.getSectionsClone(), adjSections, sectionMask);
        return _ -> compiler.compileSectionsAsync(chunk, level, sectionMask, sectionData);
    }

    public static String getMeshMode() {
        return switch (activeCompiler) {
            case LodCompilerNaive _ -> "naive";
            case LodCompilerGreedy _ -> "greedy";
            case null -> "-";
        };
    }

    private static CompileResult compileChunkAsync(LodChunk chunk, LodSectionMask sectionMask, int minSection, int maxSection, int chunkColor) {
        try (Zone ignored = Profiler.get().zone("SimpleLod - Compile LOD section")) {
            ByteBufferBuilder buffer = acquireBuffer();
            BufferBuilder builder = new BufferBuilder(buffer, PrimitiveTopology.QUADS, VERTEX_FORMAT);

            if (chunkColor != 0) {
                int minY = minSection * 16;
                int maxY = (maxSection + 1) * 16;
                compileVoxel(builder, 0, minY, 0, 16, maxY, 16, FACE_MASK_ALL, chunkColor);
            }

            return new CompileResult(chunk, buffer, builder.build(), LevelOfDetail.CHUNK, sectionMask);
        }
    }

    private CompileResult compileSectionsAsync(LodChunk chunk, LevelOfDetail level, LodSectionMask sectionMask, SectionData sectionData) {
        try (Zone ignored = Profiler.get().zone("SimpleLod - Compile LOD section")) {
            ByteBufferBuilder buffer = acquireBuffer();
            BufferBuilder builder = new BufferBuilder(buffer, PrimitiveTopology.QUADS, VERTEX_FORMAT);
            if (level == LevelOfDetail.SECTION) {
                compileSectionVoxels(builder, sectionData);
            } else {
                compileBlockVoxels(builder, sectionData, level);
            }
            return new CompileResult(chunk, buffer, builder.build(), level, sectionMask);
        }
    }

    private Void upload(CompileResult result) {
        LodChunkMesh mesh = null;
        boolean success = true;
        if (result.meshData != null) {
            mesh = new LodChunkMesh(this, result.level, result.sectionMask, result.meshData.drawState().indexCount());
            if (!uberGpuBuffer.addAllocation(mesh, mesh, result.meshData.vertexBuffer())) {
                mesh = null;
                success = false;
            }
            result.meshData.close();
        }
        BUFFER_POOL.addLast(result.buffer);
        result.chunk.onCompileComplete(mesh, success);
        return null;
    }

    public static boolean uploadStagedBuffers() {
        LodCompiler compiler = Objects.requireNonNull(activeCompiler);
        GpuDevice device = RenderSystem.getDevice();
        try (StagingBuffer.Uploader uploader = compiler.stagingBuffer.startUploading(device.createCommandEncoder())) {
            return compiler.uberGpuBuffer.uploadStagedAllocations(device, uploader);
        }
    }

    final void updateMeshBufferSlice(LodChunkMesh mesh) {
        TlsfAllocator.Allocation allocation = uberGpuBuffer.getAllocation(mesh);
        if (allocation == null) {
            mesh.updateBuffer(null, 0);
            return;
        }

        GpuBuffer buffer = uberGpuBuffer.getGpuBuffer(allocation);
        long offset = allocation.getOffsetFromHeap();
        mesh.updateBuffer(buffer, offset);
    }

    final void discardMesh(LodChunkMesh mesh) {
        uberGpuBuffer.removeAllocation(mesh);
    }

    private static ByteBufferBuilder acquireBuffer() {
        ByteBufferBuilder buffer = BUFFER_POOL.pollFirst();
        if (buffer == null) {
            buffer = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
        }
        return buffer;
    }

    private static void compileSectionVoxels(VertexConsumer buffer, SectionData sectionData) {
        int minSection = sectionData.minSection();
        int maxSection = sectionData.maxSection();
        LodSection section = sectionData.getSection(minSection);
        LodSection sectionBelow = null;
        LodSection sectionAbove;
        for (int i = minSection; i <= maxSection; i++) {
            sectionAbove = i < maxSection ? sectionData.getSection(i + 1) : null;
            if (section != null) {
                int minY = i * 16;
                int faceMask = FACE_MASK_ALL;
                if (i > minSection && sectionBelow != null) {
                    faceMask &= ~FACE_MASK_DOWN;
                }
                if (i < maxSection && sectionAbove != null) {
                    faceMask &= ~FACE_MASK_UP;
                }
                if (sectionData.getAdjSection(Direction.NORTH, i) != null) {
                    faceMask &= ~FACE_MASK_NORTH;
                }
                if (sectionData.getAdjSection(Direction.SOUTH, i) != null) {
                    faceMask &= ~FACE_MASK_SOUTH;
                }
                if (sectionData.getAdjSection(Direction.WEST, i) != null) {
                    faceMask &= ~FACE_MASK_WEST;
                }
                if (sectionData.getAdjSection(Direction.EAST, i) != null) {
                    faceMask &= ~FACE_MASK_EAST;
                }
                if (faceMask != 0) {
                    compileVoxel(buffer, 0, minY, 0, 16, minY + 16, 16, faceMask, section.getSectionLevelColor());
                }
            }
            sectionBelow = section;
            section = sectionAbove;
        }
    }

    private void compileBlockVoxels(VertexConsumer buffer, SectionData sectionData, LevelOfDetail level) {
        int divisor = level.getVoxelSize();
        int size = level.getAxisSize();
        int minSection = sectionData.minSection();
        int maxSection = sectionData.maxSection();
        LodSection section = sectionData.getSection(minSection);
        LodSection sectionBelow = null;
        LodSection sectionAbove;
        for (int i = minSection; i <= maxSection; i++) {
            sectionAbove = i < maxSection ? sectionData.getSection(i + 1) : null;
            if (section != null) {
                compileSectionBlockVoxels(buffer, section, sectionBelow, sectionAbove, sectionData, level, i, divisor, size);
            }
            sectionBelow = section;
            section = sectionAbove;
        }
    }

    abstract void compileSectionBlockVoxels(
            VertexConsumer buffer,
            LodSection section,
            @Nullable LodSection sectionBelow,
            @Nullable LodSection sectionAbove,
            SectionData sectionData,
            LevelOfDetail level,
            int index,
            int divisor,
            int size
    );

    static void compileVoxel(VertexConsumer buffer, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int faceMask, int color) {
        if (color == 0) {
            return;
        }

        for (Direction side : DIRECTIONS) {
            int mask = 1 << side.ordinal();
            if ((faceMask & mask) != 0) {
                compileVoxelFace(buffer, minX, minY, minZ, maxX, maxY, maxZ, side, color);
            }
        }
    }

    static void compileVoxelFace(VertexConsumer buffer, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Direction side, int color) {
        FaceInfo faceInfo = FaceInfo.fromFacing(side);
        for (int i = 0; i < 4; i++) {
            FaceInfo.VertexInfo vertInfo = faceInfo.getVertexInfo(i);
            float x = vertInfo.xFace().select(minX, minY, minZ, maxX, maxY, maxZ);
            float y = vertInfo.yFace().select(minX, minY, minZ, maxX, maxY, maxZ);
            float z = vertInfo.zFace().select(minX, minY, minZ, maxX, maxY, maxZ);
            buffer.addVertex(x, y, z).setColor(color).setNormal(side.getStepX(), side.getStepY(), side.getStepZ());
        }
    }

    @Override
    public void close() {
        uberGpuBuffer.close();
        stagingBuffer.close();
        BUFFER_POOL.forEach(ByteBufferBuilder::close);
        BUFFER_POOL.clear();
    }

    private record CompileResult(LodChunk chunk, ByteBufferBuilder buffer, @Nullable MeshData meshData, LevelOfDetail level, LodSectionMask sectionMask) { }
}
