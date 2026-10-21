package io.github.sweetcornna.universe.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.commands.RenderPass;

import io.github.sweetcornna.universe.client.dyson.DysonSkyRenderer;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.MoonPhase;

/**
 * 用原版天空的维度（主世界）里，戴森球画在原版天空的同一个 RenderPass 里，排在太阳、月亮和星星后面。
 *
 * <p>以前在 {@code RenderLevelStageEvent.AfterSky} 里另开 RenderPass 画，开着 Iris 光影包时画不进光影包的缓冲区，
 * 看不见（ADR-0019）。末地的天空不走这个方法，也没有戴森球。
 */
@Mixin(SkyRenderer.class)
abstract class SkyRendererMixin {
    @Inject(method = "renderSunMoonAndStars", at = @At("TAIL"))
    private void universe$drawDysonSphere(RenderPass renderPass, PoseStack poseStack, float sunAngle, float moonAngle, float starAngle,
            MoonPhase moonPhase, float rainBrightness, float starBrightness, CallbackInfo ci) {
        DysonSkyRenderer.drawInSky(renderPass);
    }
}
