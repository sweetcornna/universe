package io.github.sweetcornna.universe.block.landmark;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.civ.Civ;
import io.github.sweetcornna.universe.core.civ.Race;
import io.github.sweetcornna.universe.poi.Pois;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 知识石（ADR-0027 §五）：两格高的石柱，刻着发光的外星文字。每个玩家第一次摸一块，学会这个恒星系主导种族的一个词；
 * 摸过的记在玩家身上（星球加坐标）。挖不掉、推不动：搬到别处就成了另一块，能一直刷词。
 */
public class KnowledgeStoneBlock extends Block {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    /** 摸一下的结果。 */
    public enum Reading {
        /** 第一次摸，学会了一个词。 */
        LEARNED,
        /** 第一次摸，可这个种族的词全认识了。 */
        ALL_KNOWN,
        /** 这块摸过了。 */
        READ_BEFORE,
        /** 不在任何恒星系里（比如别的模组的维度），认不出是哪个种族的字。 */
        UNREADABLE
    }

    public KnowledgeStoneBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxY() || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return this.defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        level.setBlockAndUpdate(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER));
    }

    /** 上下两半少了一半，另一半也没了。 */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (directionToNeighbour.getAxis() == Direction.Axis.Y && half == DoubleBlockHalf.LOWER == (directionToNeighbour == Direction.UP)) {
            return neighbourState.is(this) && neighbourState.getValue(HALF) != half ? state : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            touch(serverPlayer, serverLevel, lower(state, pos));
        }
        return InteractionResult.SUCCESS;
    }

    /** 文字周围飘着附魔台那样的字符，往石头上飞。 */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER || random.nextInt(3) != 0) {
            return;
        }
        double angle = random.nextDouble() * Math.PI * 2;
        double dx = Math.cos(angle) * 1.2;
        double dz = Math.sin(angle) * 1.2;
        double dy = random.nextDouble() * 1.4 - 0.2;
        level.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, dx, dy, dz);
    }

    /** 石柱下半那一格：摸哪一半都算这一块。 */
    public static BlockPos lower(BlockState state, BlockPos pos) {
        return state.hasProperty(HALF) && state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    /** 记在玩家身上的那一块的键：星球的路径加下半那一格的坐标。 */
    public static String key(ServerLevel level, BlockPos lower) {
        return level.dimension().identifier().getPath() + "@" + lower.getX() + "," + lower.getY() + "," + lower.getZ();
    }

    /** 摸一下知识石（{@code lower} 是下半那一格）。 */
    public static Reading touch(ServerPlayer player, ServerLevel level, BlockPos lower) {
        Optional<Race> race = Civ.dominant(level);
        if (race.isEmpty()) {
            tell(player, Component.translatable("message.universe.knowledge_stone.unreadable"));
            return Reading.UNREADABLE;
        }
        if (!Civ.state(player).touchStone(key(level, lower))) {
            tell(player, Component.translatable("message.universe.knowledge_stone.read"));
            level.playSound(null, lower, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 0.6F);
            return Reading.READ_BEFORE;
        }
        // 学会了会提示、连同摸过的这一块一起同步；都认识时这里自己同步
        int concept = Civ.teachWord(player, race.get(), -1L, Pois.claimKey(level, lower));
        level.playSound(null, lower, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 0.8F);
        level.sendParticles(ParticleTypes.ENCHANT, lower.getX() + 0.5, lower.getY() + 1.4, lower.getZ() + 0.5, 50, 0.5, 0.9, 0.5, 0.6);
        if (concept < 0) {
            Civ.sync(player);
            tell(player, Component.translatable("message.universe.knowledge_stone.all_known"));
            return Reading.ALL_KNOWN;
        }
        return Reading.LEARNED;
    }

    /** 没有连接的假玩家（GameTest 的模拟玩家）不发。 */
    private static void tell(ServerPlayer player, Component message) {
        if (player.connection != null) {
            player.sendOverlayMessage(message);
        }
    }
}
