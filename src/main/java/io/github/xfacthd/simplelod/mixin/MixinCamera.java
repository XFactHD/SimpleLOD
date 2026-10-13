package io.github.xfacthd.simplelod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.xfacthd.simplelod.util.Config;
import net.minecraft.client.Camera;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Camera.class)
abstract class MixinCamera {
    @WrapOperation(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Options;getEffectiveRenderDistance()I"
            )
    )
    @SuppressWarnings("MethodMayBeStatic")
    private int simplelod$adjustRenderDistanceForFarPlane(Options options, Operation<Integer> original) {
        return Math.max(original.call(options), Config.getRenderDistance());
    }
}
