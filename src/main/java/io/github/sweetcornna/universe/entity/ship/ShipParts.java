package io.github.sweetcornna.universe.entity.ship;

import java.util.Set;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.ship.ShipPart;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.BlockRelocability;
import net.neoforged.neoforge.registries.DeferredBlock;

/** 方块在飞船上的角色，以及哪些方块能装上船。 */
public final class ShipParts {
    /** 不会被组装进飞船的方块：天然地形、植物等。船停在地上时不会把地面一起带走。 */
    public static final TagKey<Block> EXCLUDED = TagKey.create(Registries.BLOCK, Universe.id("ship_excluded"));

    private static Set<Block> plating;

    private ShipParts() {
    }

    public static ShipPart of(BlockState state) {
        Block block = state.getBlock();
        if (block == UniverseBlocks.SHIP_HELM.get()) {
            return ShipPart.HELM;
        } else if (block == UniverseBlocks.SHIP_THRUSTER.get()) {
            return ShipPart.THRUSTER;
        } else if (block == UniverseBlocks.FUEL_TANK.get()) {
            return ShipPart.FUEL_TANK;
        } else if (block == UniverseBlocks.IMPULSE_ENGINE.get()) {
            return ShipPart.IMPULSE_ENGINE;
        } else if (block == UniverseBlocks.SHIP_SEAT.get()) {
            return ShipPart.SEAT;
        }
        if (plating == null) {
            plating = Set.copyOf(UniverseBlocks.HULL_PLATING.values().stream().map(DeferredBlock::get).toList());
        }
        if (plating.contains(block)) {
            return ShipPart.PLATING;
        }
        if (state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS) || state.is(Tags.BlockItems.GLASS_BLOCKS.block())
                || state.is(Tags.BlockItems.GLASS_PANES.block()) || state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()) {
            return ShipPart.LIGHT;
        }
        return ShipPart.HULL;
    }

    /** 这一格能不能装上船：不是空气、液体、天然地形、不可破坏的方块，也没有拒绝被搬动。 */
    public static boolean canAssemble(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getBlock() instanceof LiquidBlock || state.is(EXCLUDED)) {
            return false;
        }
        if (state.getDestroySpeed(level, pos) < 0) {
            return false;
        }
        return !(state.getRelocability(level, pos) instanceof BlockRelocability.No);
    }
}
