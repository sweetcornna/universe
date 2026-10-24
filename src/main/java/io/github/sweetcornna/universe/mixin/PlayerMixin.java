package io.github.sweetcornna.universe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 坐在飞船上按 Shift 不会直接下船：驾驶员的 Shift 是"下降"，着陆后按住才停船；乘客着陆后才能下船。
 * 在太空里乘客按 Shift、驾驶员按出舱键出舱（ADR-0034 §四）。这些事由 {@link ShipEntity} 自己处理。
 *
 * <p>原版在服务端每刻检查 {@code wantsToStopRiding}（就是 Shift 键）然后下船；用 NeoForge 的下马事件取消的话，
 * 它每刻都会把玩家的视角掰回去，所以直接在这里拦。
 */
@Mixin(Player.class)
abstract class PlayerMixin {
    @Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
    private void universe$stayOnShip(CallbackInfoReturnable<Boolean> cir) {
        if (((Player) (Object) this).getVehicle() instanceof ShipEntity) {
            cir.setReturnValue(false);
        }
    }
}
