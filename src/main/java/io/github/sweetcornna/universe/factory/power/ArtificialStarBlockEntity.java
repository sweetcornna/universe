package io.github.sweetcornna.universe.factory.power;

import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 人造恒星（ADR-0018）：最大 72 kW（戴森球计划 72 MW），烧反物质燃料棒。一根 6480 万焦，满负荷烧 15 分钟；
 * 和煤、氘核燃料棒的能量比照戴森球计划。
 */
public class ArtificialStarBlockEntity extends FuelGeneratorBlockEntity {
    public static final long MAX_POWER = 3600;
    public static final long ROD_ENERGY = 64_800_000;

    public ArtificialStarBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.ARTIFICIAL_STAR.get(), pos, state, MAX_POWER, UniverseItems.ANTIMATTER_FUEL_ROD, ROD_ENERGY);
    }

    @Override
    protected MachineMenu.Type menuType() {
        return UniverseMenus.ARTIFICIAL_STAR;
    }
}
