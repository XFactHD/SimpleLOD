package io.github.xfacthd.simplelod.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.xfacthd.simplelod.generator.LodGenerator;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection$CompileTask")
abstract class MixinSectionRebuildTask {
    @Final
    @Shadow
    protected RenderSectionRegion region;

    @Inject(
            method = "doTask",
            at = @At(
                    value = "NEW",
                    target = "net/minecraft/client/renderer/chunk/CompiledSectionMesh"
            )
    )
    private void simplelod$enqueueLodUpdate(
            SectionBufferBuilderPack buffers,
            CallbackInfoReturnable<SectionRenderDispatcher.RenderSection.SectionTask.SectionTaskResult> cir,
            @Local long sectionNode,
            @Local SectionCompiler.Results results
    ) {
        LodGenerator.enqueueLodUpdate(sectionNode, region, results.renderedLayers.isEmpty());
    }
}
