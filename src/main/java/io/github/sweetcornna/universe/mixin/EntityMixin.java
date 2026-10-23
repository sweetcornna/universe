package io.github.sweetcornna.universe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.sweetcornna.universe.survival.EntityGravity;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.world.entity.Entity;

/**
 * 太空维度里没有"世界底部"：星球比维度的高度大得多，飞船、玩家、海盗要能绕到星球下面去（ADR-0021）。
 * 原版每刻检查实体是不是掉到最低高度以下 64 格，掉下去就删掉或者让它受虚空伤害，这里在太空里跳过。
 *
 * <p>不是生物的实体（掉落物、箭、雪球……）在星球上按星球的重力下落（ADR-0030 §六，{@link EntityGravity}）。
 */
@Mixin(Entity.class)
abstract class EntityMixin {
    @Inject(method = "checkBelowWorld", at = @At("HEAD"), cancellable = true)
    private void universe$noVoidInSpace(CallbackInfo ci) {
        if (SpaceDimensions.isSpace(((Entity) (Object) this).level().dimension())) {
            ci.cancel();
        }
    }

    @Inject(method = "getGravity", at = @At("RETURN"), cancellable = true)
    private void universe$planetGravity(CallbackInfoReturnable<Double> cir) {
        double gravity = cir.getReturnValueD();
        double scaled = EntityGravity.scale((Entity) (Object) this, gravity);
        if (scaled != gravity) {
            cir.setReturnValue(scaled);
        }
    }
}
