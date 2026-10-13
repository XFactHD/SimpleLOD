package io.github.xfacthd.simplelod.render;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.DynamicGpuDataStorageMapped;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

final class LodRendererSeparate extends LodRenderer {
    private static final List<String> UNIFORM_NAMES = List.of(LodRenderer.LOD_CHUNK_UBO_NAME);

    private final DynamicGpuDataStorageMapped<LodChunkInfo> lodChunkUniforms = new DynamicGpuDataStorageMapped<>(
            "LOD Chunk UBO", LodChunkInfo.SIZE, GpuBuffer.USAGE_UNIFORM, 2
    );

    @Override
    LodRenderer.LodChunksToRender prepare(GpuBufferSlice transformBuffer, boolean wireframe, VisibleLodChunks chunks) {
        int expectedChunkCount = chunks.chunks().size();
        List<RenderPass.Draw<GpuBufferSlice[]>> draws = new ArrayList<>(expectedChunkCount);
        List<LodChunkInfo> chunkInfos = new ArrayList<>(expectedChunkCount);
        int maxIndexCount = chunks.forEach(chunkInfos, (mesh, baseVertex, infoIndex) -> {
            GpuBuffer vertexBuffer = Objects.requireNonNull(mesh.buffer());
            UboUploader uboUploader = (chunkUbos, uploader) -> uploader.setUniform(LodRenderer.LOD_CHUNK_UBO_NAME, chunkUbos[infoIndex]);
            draws.add(new RenderPass.Draw<>(0, vertexBuffer, null, null, 0, mesh.indexCount(), baseVertex, uboUploader));
        });
        GpuBufferSlice[] chunkUniforms = lodChunkUniforms.writeData(chunkInfos.toArray(LodChunkInfo[]::new));

        RenderPipeline pipeline = wireframe ? LodRenderPipelines.LOD_WIREFRAME : LodRenderPipelines.LOD_OPAQUE;
        return new SeparateLodChunksToRender(transformBuffer, pipeline, maxIndexCount, draws, chunkUniforms);
    }

    @Override
    void endFrame() {
        super.endFrame();
        lodChunkUniforms.endFrame();
    }

    @Override
    String getType() {
        return "naive";
    }

    @Override
    public void close() {
        super.close();
        lodChunkUniforms.close();
    }

    record SeparateLodChunksToRender(
            GpuBufferSlice transformBuffer,
            RenderPipeline pipeline,
            int maxIndexCount,
            List<RenderPass.Draw<GpuBufferSlice[]>> draws,
            GpuBufferSlice[] chunkUniforms
    ) implements LodRenderer.LodChunksToRender {
        @Override
        public void render(RenderPass renderPass, GpuBuffer defaultIndexBuffer, IndexType defaultIndexType) {
            renderPass.drawMultipleIndexed(draws, defaultIndexBuffer, defaultIndexType, UNIFORM_NAMES, chunkUniforms);
        }
    }

    private interface UboUploader extends BiConsumer<GpuBufferSlice[], RenderPass.UniformUploader> { }
}
