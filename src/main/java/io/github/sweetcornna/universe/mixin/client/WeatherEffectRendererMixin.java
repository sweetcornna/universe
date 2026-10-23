package io.github.sweetcornna.universe.mixin.client;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.mojang.blaze3d.vertex.VertexConsumer;

import io.github.sweetcornna.universe.client.fluid.AcidClient;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;

/**
 * 酸雨染成黄绿色（ADR-0030 §一）。原版把雨和雪都画成白色（{@code ARGB.white(alpha)}），这里算雨的颜色时
 * 乘上 {@link AcidClient#rainColor()}；不下酸雨时返回 0，照原样画白色。
 *
 * <p>雨和雪走同一个方法，用 {@code maxAlpha} 区分：原版雨传 1.0、雪传 0.8。
 */
@Mixin(WeatherEffectRenderer.class)
abstract class WeatherEffectRendererMixin {
    @Redirect(method = "prepareInstances", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;white(F)I"))
    private int universe$acidRainColor(float alpha, VertexConsumer builder, List<WeatherEffectRenderer.ColumnInstance> columns,
            Vec3 cameraPos, float maxAlpha, int radius, float intensity) {
        int white = ARGB.white(alpha);
        if (maxAlpha < 1.0F) {
            return white;
        }
        int color = AcidClient.rainColor();
        return color == 0 ? white : ARGB.multiply(white, color);
    }
}
