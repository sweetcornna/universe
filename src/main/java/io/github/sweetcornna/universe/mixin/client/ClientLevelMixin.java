package io.github.sweetcornna.universe.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.sweetcornna.universe.client.ClientGalaxy;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ColorResolver;

/**
 * 星球上的水按星球的液体颜色染色。
 *
 * <p>水的颜色来自生物群系的 {@code water_color}，而星球共用同一批生物群系，所以在客户端世界计算方块染色时
 * 把水的那一项换掉。这个方法会在区块网格构建线程上调用，{@link ClientGalaxy#waterColor} 可以在任意线程调用。
 */
@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {
    @Inject(method = "calculateBlockTint", at = @At("HEAD"), cancellable = true)
    private void universe$planetWaterColor(BlockPos pos, ColorResolver resolver, CallbackInfoReturnable<Integer> cir) {
        if (resolver == BiomeColors.WATER_COLOR_RESOLVER) {
            int color = ClientGalaxy.waterColor(((ClientLevel) (Object) this).dimension());
            if (color != 0) {
                cir.setReturnValue(color);
            }
        }
    }
}
