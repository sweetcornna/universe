package io.github.sweetcornna.universe.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * 乘客总能看见自己坐的载具和同一辆车上的人。原版在实体所在的区块还没发给这个玩家时就停止同步这个实体；
 * 开着脉冲引擎一刻飞几十格，脚下的区块总在排队，客户端上的飞船每秒要被删掉、重建好几次，
 * 乘客跟着下船又上船，镜头、读数面板和舱内的声音都一跳一跳的。
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
abstract class TrackedEntityMixin {
    @Shadow
    @Final
    private Entity entity;

    @ModifyExpressionValue(method = "updatePlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkMap;isChunkTracked(Lnet/minecraft/server/level/ServerPlayer;II)Z"))
    private boolean universe$keepOwnVehicle(boolean tracked, @Local(argsOnly = true) ServerPlayer player) {
        return tracked || this.entity.getRootVehicle() == player.getRootVehicle();
    }
}
