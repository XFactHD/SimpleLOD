package io.github.xfacthd.simplelod.render;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import net.minecraft.client.renderer.DynamicGpuData;
import net.minecraft.client.renderer.DynamicGpuDataStorageMapped;
import net.minecraft.client.renderer.DynamicGpuDataStorageNonMapped;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class LodRendererIndirect extends LodRenderer {
    /// @see DynamicGpuData.IndexedDraw
    private static final int MDI_COMMAND_SIZE = Integer.BYTES * 5;

    private final DynamicGpuDataStorageNonMapped<LodChunkInfo> lodChunkInstances = new DynamicGpuDataStorageNonMapped<>(
            "LOD Chunk Instances", LodChunkInfo.SIZE, GpuBuffer.USAGE_VERTEX, 2
    );
    private final DynamicGpuDataStorageMapped<DynamicGpuData.IndexedDraw> lodChunkCommands = new DynamicGpuDataStorageMapped<>(
            "LOD Chunk Commands", MDI_COMMAND_SIZE, GpuBuffer.USAGE_INDIRECT_PARAMETERS, 2
    );
    private final int maxIndirectDrawCount;

    LodRendererIndirect(int maxIndirectDrawCount) {
        this.maxIndirectDrawCount = maxIndirectDrawCount;
    }

    @Override
    LodRenderer.LodChunksToRender prepare(GpuBufferSlice transformBuffer, boolean wireframe, VisibleLodChunks chunks) {
        record DrawGroup(GpuBuffer buffer, List<DynamicGpuData.IndexedDraw> draws) {}

        List<LodChunkInfo> chunkInfos = new ArrayList<>(chunks.chunks().size());
        List<List<DynamicGpuData.IndexedDraw>> drawCommands = new ArrayList<>(5);
        Map<GpuBuffer, DrawGroup> drawGroupCache = new Reference2ObjectLinkedOpenHashMap<>(5);
        int maxIndexCount = chunks.forEach(chunkInfos, (mesh, baseVertex, infoIndex) -> {
            GpuBuffer buffer = Objects.requireNonNull(mesh.buffer());
            DrawGroup group = drawGroupCache.get(buffer);
            //noinspection ConstantValue
            if (group == null) {
                group = new DrawGroup(buffer, new ArrayList<>());
                drawCommands.add(group.draws);
                drawGroupCache.put(buffer, group);
            }
            group.draws.add(new DynamicGpuData.IndexedDraw(mesh.indexCount(), 1, 0, baseVertex, infoIndex));
        });
        GpuBufferSlice chunkInfoBuffer = lodChunkInstances.writeDataBatched(chunkInfos);
        GpuBufferSlice[] drawCommandBuffers = lodChunkCommands.writeDataBatchedMultiple(drawCommands);

        int index = 0;
        List<IndirectDrawGroup> drawGroups = new ArrayList<>(drawGroupCache.size());
        for (DrawGroup group : drawGroupCache.values()) {
            drawGroups.add(new IndirectDrawGroup(group.buffer.slice(), drawCommandBuffers[index], group.draws.size()));
            index++;
        }

        RenderPipeline pipeline = wireframe ? LodRenderPipelines.LOD_WIREFRAME_MDI : LodRenderPipelines.LOD_OPAQUE_MDI;
        return new IndirectLodChunksToRender(transformBuffer, pipeline, maxIndexCount, chunkInfoBuffer, drawGroups, maxIndirectDrawCount);
    }

    @Override
    void endFrame() {
        super.endFrame();
        lodChunkInstances.endFrame();
        lodChunkCommands.endFrame();
    }

    @Override
    String getType() {
        return "mdi";
    }

    @Override
    public void close() {
        super.close();
        lodChunkInstances.close();
        lodChunkCommands.close();
    }

    record IndirectLodChunksToRender(
            GpuBufferSlice transformBuffer,
            RenderPipeline pipeline,
            int maxIndexCount,
            GpuBufferSlice chunkInfos,
            List<IndirectDrawGroup> drawGroups,
            int maxIndirectDrawCount
    ) implements LodRenderer.LodChunksToRender {
        @Override
        public void render(RenderPass renderPass, GpuBuffer defaultIndexBuffer, IndexType defaultIndexType) {
            if (drawGroups.isEmpty()) {
                return;
            }

            renderPass.setVertexBuffer(1, chunkInfos);
            renderPass.setIndexBuffer(defaultIndexBuffer, defaultIndexType);

            for (IndirectDrawGroup group : drawGroups) {
                if (group.drawCount <= 0) {
                    continue;
                }

                renderPass.setVertexBuffer(0, group.vertexBuffer);

                GpuBufferSlice cmdBuffer = group.commandBuffer;
                int remainingDrawCount = group.drawCount;
                long offset = 0L;
                while (remainingDrawCount > 0) {
                    int passDrawCount = Math.min(remainingDrawCount, maxIndirectDrawCount);
                    long length = (long) passDrawCount * MDI_COMMAND_SIZE;
                    GpuBufferSlice passSlice = cmdBuffer.slice(offset, length);
                    renderPass.drawIndexedIndirect(passSlice, passDrawCount);
                    remainingDrawCount -= passDrawCount;
                    offset += length;
                }
            }
        }
    }

    private record IndirectDrawGroup(GpuBufferSlice vertexBuffer, GpuBufferSlice commandBuffer, int drawCount) { }
}
