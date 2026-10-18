package io.github.sweetcornna.universe.factory;

import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 工厂锚的方块实体：加载时补上票据（重复加没关系），方块真的被拆掉时撤掉。区块卸载、服务器关闭时不撤，
 * 票据要留在存档里，下次开服时把这片区块和这颗星球一起加载回来。
 */
public class FactoryAnchorBlockEntity extends BlockEntity {
    public FactoryAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.FACTORY_ANCHOR.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level instanceof ServerLevel serverLevel) {
            FactoryAnchorBlock.force(serverLevel, this.worldPosition, true);
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level instanceof ServerLevel serverLevel) {
            FactoryAnchorBlock.force(serverLevel, pos, false);
        }
    }
}
