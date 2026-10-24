package io.github.sweetcornna.universe.block.poi;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.registry.sound.WorldSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 打捞舱（ADR-0026）：埋在打捞点的保护壳下面。右键打开，按战利品表倒出打捞的废料和零件，打开的就空了。
 * 没打开就挖掉的，挖的时候先打开，东西掉在原地。
 */
public class SalvageCapsuleBlock extends Block {
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, Universe.id("chests/salvage_capsule"));

    public SalvageCapsuleBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (state.getValue(OPEN)) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            open(server, pos, state, player, hitResult.getDirection());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!state.getValue(OPEN) && level instanceof ServerLevel server) {
            state = open(server, pos, state, player, Direction.UP);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** 打开：倒出舱里的东西，换成打开的样子。 */
    private static BlockState open(ServerLevel level, BlockPos pos, BlockState state, Player player, Direction face) {
        for (ItemStack stack : contents(level, pos, player)) {
            Block.popResourceFromFace(level, pos, face, stack);
        }
        level.playSound(null, pos, WorldSounds.CAPSULE_OPEN.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        Vec3 out = Vec3.atCenterOf(pos).relative(face, 0.6);
        level.sendParticles(ParticleTypes.CLOUD, out.x, out.y, out.z, 8, 0.2, 0.2, 0.2, 0.02);
        BlockState opened = state.setValue(OPEN, true);
        level.setBlock(pos, opened, Block.UPDATE_CLIENTS);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, opened));
        return opened;
    }

    /** 舱里的东西：每次打开现掷一次战利品表。 */
    public static List<ItemStack> contents(ServerLevel level, BlockPos pos, Player player) {
        List<ItemStack> items = new ArrayList<>();
        LootTable table = level.getServer().reloadableRegistries().getLootTable(LOOT);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withOptionalParameter(LootContextParams.THIS_ENTITY, player)
                .create(LootContextParamSets.CHEST);
        table.getRandomItems(params, items::add);
        return items;
    }
}
