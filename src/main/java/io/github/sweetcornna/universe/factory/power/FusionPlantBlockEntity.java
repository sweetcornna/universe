package io.github.sweetcornna.universe.factory.power;

import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 微型聚变发电站（ADR-0018）：最大 15 kW（戴森球计划 15 MW），烧氘核燃料棒。一根 540 万焦，满负荷烧 6 分钟；
 * 和煤的能量比照戴森球计划。
 */
public class FusionPlantBlockEntity extends FuelGeneratorBlockEntity {
    public static final long MAX_POWER = 750;
    public static final long ROD_ENERGY = 5_400_000;

    public FusionPlantBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.MINI_FUSION_POWER_PLANT.get(), pos, state, MAX_POWER, UniverseItems.DEUTERON_FUEL_ROD, ROD_ENERGY);
    }

    @Override
    protected MachineMenu.Type menuType() {
        return UniverseMenus.MINI_FUSION_POWER_PLANT;
    }
}
