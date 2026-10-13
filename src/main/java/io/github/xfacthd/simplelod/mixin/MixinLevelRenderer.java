package io.github.xfacthd.simplelod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.xfacthd.simplelod.storage.LodChunkStorage;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Objects;

@Mixin(LevelRenderer.class)
abstract class MixinLevelRenderer {
    @WrapOperation(
            method = "invalidateCompiledGeometry",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ViewArea;repositionCamera(Lnet/minecraft/core/SectionPos;)Z"
            )
    )
    @SuppressWarnings("MethodMayBeStatic")
    private boolean simplelod$replaceCameraPosition(ViewArea viewArea, SectionPos vanillaCameraSectionPos, Operation<Boolean> operation) {
        SectionPos capturedCameraSectionPos = LodChunkStorage.get().getCapturedCameraSectionPos();
        SectionPos cameraSectionPos = Objects.requireNonNullElse(capturedCameraSectionPos, vanillaCameraSectionPos);
        return operation.call(viewArea, cameraSectionPos);
    }
}
