package io.github.xfacthd.simplelod.render;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.SamplerCache;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.DeviceInfo;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import io.github.xfacthd.simplelod.SimpleLOD;
import io.github.xfacthd.simplelod.compiler.LodChunkMesh;
import io.github.xfacthd.simplelod.debug.RendererStats;
import io.github.xfacthd.simplelod.storage.LodChunkStorage;
import io.github.xfacthd.simplelod.util.AccumulativeStopwatch;
import io.github.xfacthd.simplelod.util.Config;
import io.github.xfacthd.simplelod.util.Utils;
import net.minecraft.Optionull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicGpuDataStorage;
import net.minecraft.client.renderer.DynamicGpuDataStorageMapped;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.FlipFrameEvent;
import net.neoforged.neoforge.client.event.PrepareRenderBuffersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract sealed class LodRenderer implements AutoCloseable permits LodRendererIndirect, LodRendererSeparate {
    private static final int TRANSFORM_UBO_SIZE = new Std140SizeCalculator().putMat4f().get();
    public static final String TRANSFORM_UBO_NAME = "Transform";
    public static final String LOD_CHUNK_UBO_NAME = "LodChunk";
    private static final Identifier NOISE_TEXTURE_ID = SimpleLOD.id("textures/noise.png");
    private static final ContextKey<VisibleLodChunks> EXTRACTED_DATA_KEY = new ContextKey<>(SimpleLOD.id("visible_lod_chunks"));
    private static final ContextKey<LodChunksToRender> PREPARED_DATA_KEY = new ContextKey<>(SimpleLOD.id("lod_chunks_to_render"));
    private static final AccumulativeStopwatch EXTRACT_STOPWATCH = new AccumulativeStopwatch();
    private static final AccumulativeStopwatch PREPARE_STOPWATCH = new AccumulativeStopwatch();
    private static final AccumulativeStopwatch RENDER_STOPWATCH = new AccumulativeStopwatch();
    @Nullable
    private static LodRenderer activeRenderer = null;
    private static int lastVisibleChunks = 0;

    private final DynamicGpuDataStorageMapped<Transform> transformUniforms = new DynamicGpuDataStorageMapped<>(
            "LOD Transform UBO", TRANSFORM_UBO_SIZE, GpuBuffer.USAGE_UNIFORM, 1
    );
    private final NoiseTexture noiseTexture = new NoiseTexture();

    LodRenderer() {
        Minecraft.getInstance().getTextureManager().register(NOISE_TEXTURE_ID, noiseTexture);
    }

    public static void setup(AddClientReloadListenersEvent event) {
        NeoForge.EVENT_BUS.addListener(LodRenderer::onExtractRenderState);
        NeoForge.EVENT_BUS.addListener(LodRenderer::onPrepareRenderBuffers);
        NeoForge.EVENT_BUS.addListener(LodRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(LodRenderer::onSetupFog);
        NeoForge.EVENT_BUS.addListener(LodRenderer::onFlipFrame);

        DeviceInfo deviceInfo = RenderSystem.getDevice().getDeviceInfo();
        int maxIndirectDrawCount = deviceInfo.limits().maxDrawIndirectDrawCount();
        boolean useMdi = Config.isIndirectRenderer() && canUseMdi(deviceInfo, maxIndirectDrawCount);
        activeRenderer = useMdi ? new LodRendererIndirect(maxIndirectDrawCount) : new LodRendererSeparate();
        if (!FMLLoader.getCurrent().isProduction()) {
            event.addListener(NoiseTexture.RELOADER_ID, activeRenderer.noiseTexture);
        }
    }

    public static void shutdown() {
        if (activeRenderer != null) {
            activeRenderer.close();
            activeRenderer = null;
        }
    }

    private static void onExtractRenderState(ExtractLevelRenderStateEvent event) {
        EXTRACT_STOPWATCH.start();
        lastVisibleChunks = extract(event.getRenderState(), event.getOptionsRenderState());
        EXTRACT_STOPWATCH.stop();
    }

    // FIXME: fog isn't moved properly at large render distances
    private static void onSetupFog(ViewportEvent.RenderFog event) {
        FogData fogData = event.getFogData();
        int realRenderDistance = (int) (fogData.renderDistanceEnd / 16F);
        int lodRenderDistance = Config.getRenderDistance();
        if (lodRenderDistance > realRenderDistance) {
            int diff = (lodRenderDistance - realRenderDistance) * 16;
            fogData.renderDistanceStart += diff;
            fogData.renderDistanceEnd += diff;
        }
    }

    private static void onPrepareRenderBuffers(PrepareRenderBuffersEvent event) {
        PREPARE_STOPWATCH.start();
        LevelRenderState renderState = event.getLevelRenderState();
        VisibleLodChunks visibleChunks = renderState.getRenderData(EXTRACTED_DATA_KEY);
        if (visibleChunks != null) {
            LodRenderer renderer = Objects.requireNonNull(activeRenderer);
            LodChunksToRender toRender = renderer.prepare(visibleChunks, renderState.renderWireframeTerrain, renderState.cameraRenderState.viewRotationMatrix);
            renderState.setRenderData(PREPARED_DATA_KEY, toRender);
        }
        PREPARE_STOPWATCH.stop();
    }

    private static void onRenderLevelStage(RenderLevelStageEvent.AfterOpaqueBlocks event) {
        RENDER_STOPWATCH.start();
        LodChunksToRender toRender = event.getLevelRenderState().getRenderData(PREPARED_DATA_KEY);
        if (toRender != null) {
            Objects.requireNonNull(activeRenderer).render(toRender, event.getRenderPass());
        }
        RENDER_STOPWATCH.stop();
    }

    private static void onFlipFrame(@SuppressWarnings("unused") FlipFrameEvent event) {
        // FlipFrameEvent may fire after GameShuttingDownEvent destroyed the renderer
        if (activeRenderer != null) {
            activeRenderer.endFrame();
        }
    }

    private static int extract(LevelRenderState renderState, OptionsRenderState options) {
        LodChunkStorage storage = LodChunkStorage.get();
        CameraRenderState camera = renderState.cameraRenderState;
        int renderDistance = options.renderDistance;
        int renderDistanceSquared = renderDistance * renderDistance;
        int lodRenderDistance = Config.getRenderDistance();
        BlockPos cameraBlockPos = camera.blockPos;
        long cameraChunkPos = ChunkPos.pack(cameraBlockPos);

        int expectedChunkCount = Utils.getExpectedChunkCountBetween(renderDistance, lodRenderDistance) / 4;
        List<VisibleLodChunks.Entry> visibleChunks = new ArrayList<>(expectedChunkCount);
        storage.forEachVisible(camera, renderDistance, (chunkPos, chunk, sectionMask) -> {
            LodChunkMesh mesh = chunk.getMesh(storage, cameraChunkPos, renderDistanceSquared, sectionMask);
            if (mesh != null) {
                visibleChunks.add(new VisibleLodChunks.Entry(chunkPos, mesh));
            }
        });
        if (!visibleChunks.isEmpty()) {
            int minBlockY = SectionPos.sectionToBlockCoord(storage.getMinChunkY());
            renderState.setRenderData(EXTRACTED_DATA_KEY, new VisibleLodChunks(minBlockY, visibleChunks));
        }
        return visibleChunks.size();
    }

    private LodChunksToRender prepare(VisibleLodChunks visibleChunks, boolean wireframe, Matrix4f modelViewMatrix) {
        GpuBufferSlice transformBuffer = transformUniforms.writeData(new Transform(new Matrix4f(modelViewMatrix)));
        LodChunksToRender chunksToRender = prepare(transformBuffer, wireframe, visibleChunks);
        visibleChunks.finish();
        RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).requestIndexCount(chunksToRender.maxIndexCount());
        return chunksToRender;
    }

    abstract LodChunksToRender prepare(GpuBufferSlice transformBuffer, boolean wireframe, VisibleLodChunks visibleChunks);

    private void render(LodChunksToRender chunksToRender, RenderPass renderPass) {
        RenderSystem.AutoStorageIndexBuffer autoIndices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer defaultIndexBuffer = autoIndices.getBuffer(chunksToRender.maxIndexCount());

        SamplerCache samplerCache = RenderSystem.getSamplerCache();
        GpuSampler noiseSampler = samplerCache.getRepeat(FilterMode.NEAREST);
        GpuTextureView noiseTexture = this.noiseTexture.getTextureView();
        GpuSampler lightSampler = samplerCache.getClampToEdge(FilterMode.LINEAR);
        GpuTextureView lightTexture = Minecraft.getInstance().gameRenderer.levelLightmap();

        renderPass.setPipeline(RenderSystem.getCompiledPipeline(chunksToRender.pipeline()));
        RenderSystem.bindDefaultUniforms(renderPass);
        renderPass.setUniform(TRANSFORM_UBO_NAME, chunksToRender.transformBuffer());
        renderPass.setUniform("Sampler0", noiseTexture, noiseSampler);
        renderPass.setUniform("Sampler2", lightTexture, lightSampler);
        chunksToRender.render(renderPass, defaultIndexBuffer, autoIndices.type());
    }

    void endFrame() {
        transformUniforms.endFrame();
    }

    abstract String getType();

    @Override
    public void close() {
        transformUniforms.close();
    }

    private static boolean canUseMdi(DeviceInfo deviceInfo, int maxIndirectDrawCount) {
        if (maxIndirectDrawCount <= 0) {
            return false;
        }
        if (!deviceInfo.features().nonZeroFirstInstance()) {
            return false;
        }
        if (deviceInfo.hintsAndWorkarounds().multiDrawIndirectHasKnownIssues()) {
            return false;
        }
        return true;
    }

    public static RendererStats getDebugStats() {
        String mode = Optionull.mapOrDefault(activeRenderer, LodRenderer::getType, "-");
        return new RendererStats(mode, lastVisibleChunks, EXTRACT_STOPWATCH.getAverage(), PREPARE_STOPWATCH.getAverage(), RENDER_STOPWATCH.getAverage());
    }

    sealed interface LodChunksToRender permits LodRendererSeparate.SeparateLodChunksToRender, LodRendererIndirect.IndirectLodChunksToRender {
        RenderPipeline pipeline();

        int maxIndexCount();

        GpuBufferSlice transformBuffer();

        void render(RenderPass renderPass, GpuBuffer defaultIndexBuffer, IndexType defaultIndexType);
    }

    private record Transform(Matrix4fc modelViewMatrix) implements DynamicGpuDataStorage.DynamicGpuData {
        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer).putMat4f(modelViewMatrix);
        }
    }
}
