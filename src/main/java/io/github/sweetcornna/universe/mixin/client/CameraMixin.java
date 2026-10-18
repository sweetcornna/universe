package io.github.sweetcornna.universe.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.sweetcornna.universe.client.ship.ShipClient;
import net.minecraft.client.Camera;

/**
 * 坐在飞船上时第三人称镜头往上抬（ADR-0017）：船和驾驶员落在准星下面，不挡住前方的目标和炮口准星，和无人深空的飞船视角一样。
 *
 * <p>在原版往后拉镜头之前抬，拉镜头时的碰撞检测从抬高后的位置算，不会穿进机库的天花板。
 */
@Mixin(Camera.class)
abstract class CameraMixin {
    @Shadow
    protected abstract void move(float forwards, float up, float right);

    @Inject(method = "alignWithEntity", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/client/ClientHooks;getDetachedCameraDistance(Lnet/minecraft/client/Camera;ZFFFF)F"))
    private void universe$liftShipCamera(float partialTicks, CallbackInfo ci) {
        float lift = ShipClient.cameraLift((Camera) (Object) this);
        if (lift > 0) {
            this.move(0, lift, 0);
        }
    }
}
