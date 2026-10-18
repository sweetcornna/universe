package io.github.sweetcornna.universe.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * 太空维度里全是空气的区块不存盘：下次用到时重新生成（空区块生成器很快）。不拦的话，开脉冲引擎飞一趟就会写出
 * 几十 MB 的空区块。有方块的区块（空间站、玩家搭的东西、轨道采集器）照常存盘（ADR-0017）；存过盘的区块记一笔，
 * 以后方块被拆光了也要再存一次，免得读回旧的。飞船、玩家这些实体存在单独的实体文件里，不受影响。
 */
@Mixin(ChunkMap.class)
abstract class ChunkMapMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @Inject(method = "save(Lnet/minecraft/world/level/chunk/ChunkAccess;)Z", at = @At("HEAD"), cancellable = true)
    private void universe$skipSpaceChunks(ChunkAccess chunk, CallbackInfoReturnable<Boolean> cir) {
        if (!SpaceDimensions.isSpace(this.level.dimension())) {
            return;
        }
        boolean empty = true;
        for (LevelChunkSection section : chunk.getSections()) {
            if (!section.hasOnlyAir()) {
                empty = false;
                break;
            }
        }
        if (!empty) {
            if (!chunk.hasData(UniverseAttachments.SPACE_BUILT)) {
                chunk.setData(UniverseAttachments.SPACE_BUILT, true);
            }
        } else if (!chunk.hasData(UniverseAttachments.SPACE_BUILT)) {
            chunk.tryMarkSaved();
            cir.setReturnValue(false);
        }
    }
}
