package io.github.sweetcornna.universe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.sweetcornna.universe.world.planet.PlanetWeather;
import net.minecraft.server.level.ServerLevel;

/**
 * 星球维度用自己的天气（{@link PlanetWeather}）。
 *
 * <p>原版每个能下雨的维度每刻都会推进一次全服共用的天气计时，并把降雨强度拉向全服的状态；
 * 没有事件能拦住这一步，所以在方法开头接管星球维度。
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {
    @Inject(method = "advanceWeatherCycle", at = @At("HEAD"), cancellable = true)
    private void universe$planetWeather(CallbackInfo ci) {
        if (PlanetWeather.tick((ServerLevel) (Object) this)) {
            ci.cancel();
        }
    }
}
