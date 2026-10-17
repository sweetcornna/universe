package io.github.sweetcornna.universe.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * 太空维度的区块不存盘：里面只有虚空，下次用到时重新生成（空区块生成器很快）。飞船、玩家这些实体存在单独的
 * 实体文件里，不受影响。不拦的话，开脉冲引擎飞一趟就会写出几十 MB 的空区块。
 */
@Mixin(ChunkMap.class)
abstract class ChunkMapMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @Inject(method = "save(Lnet/minecraft/world/level/chunk/ChunkAccess;)Z", at = @At("HEAD"), cancellable = true)
    private void universe$skipSpaceChunks(ChunkAccess chunk, CallbackInfoReturnable<Boolean> cir) {
        if (SpaceDimensions.isSpace(this.level.dimension())) {
            chunk.tryMarkSaved();
            cir.setReturnValue(false);
        }
    }
}
