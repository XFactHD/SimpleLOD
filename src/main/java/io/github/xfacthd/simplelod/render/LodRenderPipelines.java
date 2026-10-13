package io.github.xfacthd.simplelod.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PolygonMode;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import io.github.xfacthd.simplelod.SimpleLOD;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.util.LightCoordsUtil;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

public final class LodRenderPipelines {
    private static final BindGroupLayout TRANSFORM_LAYOUT = BindGroupLayout.builder()
            .withUniform(LodRenderer.TRANSFORM_UBO_NAME, UniformType.UNIFORM_BUFFER)
            .build();
    private static final BindGroupLayout LOD_CHUNK_LAYOUT = BindGroupLayout.builder()
            .withUniform(LodRenderer.LOD_CHUNK_UBO_NAME, UniformType.UNIFORM_BUFFER)
            .build();
    private static final VertexFormat LOD_INSTANCE_FORMAT = VertexFormat.builder(1)
            .addAttribute("ChunkPosition", GpuFormat.RGB32_SINT)
            .build();
    private static final RenderPipeline.Snippet LOD_SNIPPET = RenderPipeline.builder()
            .withVertexShader(SimpleLOD.id("core/lod"))
            .withFragmentShader(SimpleLOD.id("core/lod"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_NORMAL)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.LIGHTING)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(TRANSFORM_LAYOUT)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER2)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withShaderDefine("LOD_BLOCK_LIGHT", 0)
            .withShaderDefine("LOD_SKY_LIGHT", LightCoordsUtil.smoothSky(LightCoordsUtil.FULL_BRIGHT))
            .buildSnippet();
    private static final RenderPipeline.Snippet LOD_SEPARATE_SNIPPET = RenderPipeline.builder(LOD_SNIPPET)
            .withBindGroupLayout(LOD_CHUNK_LAYOUT)
            .buildSnippet();
    private static final RenderPipeline.Snippet LOD_MDI_SNIPPET = RenderPipeline.builder(LOD_SNIPPET)
            .withVertexBinding(1, LOD_INSTANCE_FORMAT)
            .withShaderDefine("MULTIDRAW_LOD")
            .buildSnippet();
    public static final RenderPipeline LOD_OPAQUE = RenderPipeline.builder(LOD_SEPARATE_SNIPPET)
            .withLocation(SimpleLOD.id("pipeline/lod_opaque"))
            .build();
    public static final RenderPipeline LOD_WIREFRAME = RenderPipeline.builder(LOD_SEPARATE_SNIPPET)
            .withLocation(SimpleLOD.id("pipeline/lod_wireframe"))
            .withPolygonMode(PolygonMode.WIREFRAME)
            .build();
    public static final RenderPipeline LOD_OPAQUE_MDI = RenderPipeline.builder(LOD_MDI_SNIPPET)
            .withLocation(SimpleLOD.id("pipeline/lod_opaque_mdi"))
            .build();
    public static final RenderPipeline LOD_WIREFRAME_MDI = RenderPipeline.builder(LOD_MDI_SNIPPET)
            .withLocation(SimpleLOD.id("pipeline/lod_wireframe_mdi"))
            .withPolygonMode(PolygonMode.WIREFRAME)
            .build();

    public static void onRegisterRenderPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(LOD_OPAQUE);
        event.registerPipeline(LOD_WIREFRAME);
        event.registerPipeline(LOD_OPAQUE_MDI);
        event.registerPipeline(LOD_WIREFRAME_MDI);
    }

    private LodRenderPipelines() { }
}
