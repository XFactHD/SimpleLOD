package io.github.xfacthd.simplelod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.xfacthd.simplelod.storage.LodChunkStorage;
import net.minecraft.client.SectionUpdateTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.core.SectionPos;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(LevelExtractor.class)
abstract class MixinLevelExtractor {
    @Shadow
    @Nullable
    private ClientLevel level;

    @Inject(method = "allChanged", at = @At("TAIL"))
    private void simplelod$handleAllChanged(CallbackInfo ci) {
        if (level != null) {
            LodChunkStorage.get().invalidateMeshes();
        }
    }

    @WrapOperation(
            method = { "extract", "allChanged" },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/SectionUpdateTracker;repositionCamera(Lnet/minecraft/core/SectionPos;)V"
            )
    )
    @SuppressWarnings("MethodMayBeStatic")
    private void simplelod$replaceCameraPosition(SectionUpdateTracker updateTracker, SectionPos vanillaCameraSectionPos, Operation<Void> operation) {
        SectionPos capturedCameraSectionPos = LodChunkStorage.get().getCapturedCameraSectionPos();
        SectionPos cameraSectionPos = Objects.requireNonNullElse(capturedCameraSectionPos, vanillaCameraSectionPos);
        operation.call(updateTracker, cameraSectionPos);
    }
}
