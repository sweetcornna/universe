package io.github.sweetcornna.universe.block.portal;

import io.github.sweetcornna.universe.portal.Portals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 传送门开着时石环里的那层光（ADR-0024）：走进去就到拨通的那颗星球的传送门前。门关上（或者服务器重启过、没有对应的开门记录）
 * 就自己消失。没有碰撞，也点不中，不挡着人看拨号台。
 */
public class PortalFieldBlock extends Block {
    public PortalFieldBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /** 挨着的光面不画，环里看着是一整片。 */
    @Override
    protected boolean skipRendering(BlockState state, BlockState neighborState, Direction direction) {
        return neighborState.is(this) || super.skipRendering(state, neighborState, direction);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (level instanceof ServerLevel serverLevel && entity instanceof ServerPlayer player) {
            Portals.enter(player, serverLevel, pos);
        }
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!Portals.isOpen(level, pos)) {
            level.removeBlock(pos, false);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.PORTAL, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + 0.5,
                    (random.nextDouble() - 0.5) * 0.5, (random.nextDouble() - 0.5) * 0.5, (random.nextDouble() - 0.5) * 0.5);
        }
    }
}
