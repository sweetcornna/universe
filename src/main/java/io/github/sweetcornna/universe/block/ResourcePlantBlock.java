package io.github.sweetcornna.universe.block;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.ecology.ResourcePlant;
import io.github.sweetcornna.universe.core.microbe.EngineeredPlant;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.sentinel.Offense;
import io.github.sweetcornna.universe.microbe.EngineeredSeedlingItem;
import io.github.sweetcornna.universe.microbe.PlantedSeedlings;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.sentinel.Sentinels;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 资源植物（ADR-0026）：长熟时右键收获，收过的过一会儿（平均 15 分钟）重新长出来。打掉长熟的也能得到产物，
 * 植物本身拿不走，要种就拿产物去种。
 *
 * <p>种下的改造种苗（ADR-0039）也是这个方块，基因记在区块上（{@link PlantedSeedlings}），拆下来掉回同样的种苗。
 */
public class ResourcePlantBlock extends VegetationBlock {
    public static final BooleanProperty RIPE = BooleanProperty.create("ripe");
    /** 编辑进了荧光素酶的改造种苗：自己发光（ADR-0039）。野生的都不亮这一项。 */
    public static final BooleanProperty GLOWING = BooleanProperty.create("glowing");
    /** 带荧光素酶的亮度：和萤石差不多，晚上照得亮一小片地。 */
    public static final int GLOW_LIGHT = 12;
    private static final VoxelShape SHAPE = Block.column(12.0, 0.0, 13.0);
    private static final VoxelShape LOW = Block.column(12.0, 0.0, 8.0);

    private final ResourcePlant plant;

    public ResourcePlantBlock(ResourcePlant plant, Properties properties) {
        super(properties);
        this.plant = plant;
        this.registerDefaultState(this.stateDefinition.any().setValue(RIPE, true).setValue(GLOWING, false));
    }

    public ResourcePlant plant() {
        return this.plant;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RIPE, GLOWING);
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return switch (this.plant.habitat()) {
            case SOIL -> state.is(FloraBlocks.PLANT_SOIL);
            case ROCK, CAVE, WATER -> state.isFaceSturdy(level, pos, Direction.UP);
        };
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return this.mayPlaceOn(level.getBlockState(below), level, below);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(RIPE) ? SHAPE : LOW;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return !state.getValue(RIPE);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // 按默认的随机刻速度算，平均 15 分钟长回来
        if (random.nextDouble() < ResourcePlant.regrowChance(3)) {
            BlockState ripe = state.setValue(RIPE, true);
            level.setBlock(pos, ripe, Block.UPDATE_CLIENTS);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(ripe));
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!state.getValue(RIPE)) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel serverLevel) {
            this.harvest(serverLevel, pos, state, player);
        }
        return InteractionResult.SUCCESS;
    }

    private void harvest(ServerLevel level, BlockPos pos, BlockState state, Player player) {
        int count = this.plant.wildYield(Rng.of(level.getRandom().nextLong()));
        Block.popResource(level, pos, new ItemStack(UniverseItems.RESOURCES.get(this.plant.product()).get(), count));
        level.playSound(null, pos, this.plant.habitat() == ResourcePlant.Habitat.ROCK ? SoundEvents.AMETHYST_CLUSTER_BREAK
                : SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 0.8F + level.getRandom().nextFloat() * 0.4F);
        BlockState bare = state.setValue(RIPE, false);
        level.setBlock(pos, bare, Block.UPDATE_CLIENTS);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, bare));
        if (player instanceof ServerPlayer serverPlayer) {
            offend(serverPlayer, this.plant);
        }
    }

    /** 守卫机看着人采资源：引力果直接三级通缉，别的只加一点警戒值。 */
    public static void offend(ServerPlayer player, ResourcePlant plant) {
        Sentinels.offend(player, plant == ResourcePlant.GRAVITINO_HOST ? Offense.GRAVITINO : Offense.HARVEST);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        // 种下的改造种苗：拆掉、炸掉、被活塞顶掉都掉回同样的种苗（ADR-0039）
        EngineeredPlant planted = PlantedSeedlings.remove(level, pos);
        if (planted != null && planted.plant() == this.plant) {
            Block.popResource(level, pos, EngineeredSeedlingItem.stack(planted, 1));
        }
    }

    @Override
    public void playerDestroy(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity,
            ItemStack destroyedWith) {
        super.playerDestroy(level, player, pos, state, blockEntity, destroyedWith);
        if (state.getValue(RIPE)) {
            offend(player, this.plant);
        }
    }
}
