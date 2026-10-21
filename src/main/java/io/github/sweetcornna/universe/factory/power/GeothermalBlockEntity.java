package io.github.sweetcornna.universe.factory.power;

import java.util.Optional;

import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 地热发电站（ADR-0022）：放在熔岩上面（下面一格是熔岩）才发电，满功率是戴森球计划的 4.8 MW；灼热星球和下界这种
 * 地底更热的地方多五成。不烧燃料，一直发。
 */
public class GeothermalBlockEntity extends MachineBlockEntity {
    /** 满功率：戴森球计划的 4.8 MW。 */
    public static final long MAX_POWER = 240;
    /** 界面数据：地热强度（百分数，0 是下面没有熔岩）。 */
    public static final int HEAT = BASE_DATA;
    public static final int DATA_COUNT = BASE_DATA + 1;

    private double heat;

    public GeothermalBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.GEOTHERMAL_POWER_STATION.get(), pos, state, 0);
    }

    @Override
    public long generation() {
        return Math.round(MAX_POWER * this.heat);
    }

    @Override
    protected boolean generator() {
        return true;
    }

    @Override
    protected boolean tickMachine() {
        if (this.level instanceof ServerLevel serverLevel && serverLevel.getGameTime() % 20 == Math.floorMod(this.worldPosition.asLong(), 20)) {
            this.heat = heat(serverLevel, this.worldPosition);
        }
        return this.heat > 0 && this.load > 0;
    }

    /** 这个位置的地热：下面一格不是熔岩是 0。 */
    public static double heat(ServerLevel level, BlockPos pos) {
        if (!level.getFluidState(pos.below()).is(FluidTags.LAVA)) {
            return 0;
        }
        Optional<Planet> body = Planets.body(level);
        boolean hot = body.map(planet -> planet.type() == PlanetType.SCORCHED).orElse(level.dimension() == Level.NETHER);
        return hot ? 1.5 : 1;
    }

    @Override
    protected int dataCount() {
        return DATA_COUNT;
    }

    @Override
    protected int dataValue(int index) {
        return index == HEAT ? (int) Math.round(this.heat * 100) : super.dataValue(index);
    }

    @Override
    protected MachineMenu.Type menuType() {
        return UniverseMenus.GEOTHERMAL_POWER_STATION;
    }
}
