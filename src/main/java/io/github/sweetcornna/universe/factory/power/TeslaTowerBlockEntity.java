package io.github.sweetcornna.universe.factory.power;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.power.PowerSystem;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 电力感应塔（玩法设计 §9.4）：覆盖范围内的设备自动接入，连接距离内的塔连成一个电网。界面显示整个电网的情况。
 */
public class TeslaTowerBlockEntity extends MachineBlockEntity {
    public static final PowerSystem.TowerSpec SPEC = new PowerSystem.TowerSpec(12, 6);
    /** 界面数据：塔数、设备数、发电、用电、满足率、充电、放电。 */
    public static final int TOWERS = BASE_DATA;
    public static final int DEVICES = BASE_DATA + 1;
    public static final int GENERATION = BASE_DATA + 2;
    public static final int DEMAND = BASE_DATA + 4;
    public static final int SATISFACTION = BASE_DATA + 6;
    public static final int CHARGED = BASE_DATA + 7;
    public static final int DISCHARGED = BASE_DATA + 9;
    public static final int DATA_COUNT = BASE_DATA + 11;

    public TeslaTowerBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.TESLA_TOWER.get(), pos, state, 0);
    }

    @Override
    protected void joinGrid(LevelPower power) {
        power.addTower(this.worldPosition, SPEC);
    }

    @Override
    protected void leaveGrid(LevelPower power) {
        power.removeTower(this.worldPosition);
    }

    private PowerSystem.@Nullable Grid grid() {
        if (this.level instanceof ServerLevel serverLevel) {
            LevelPower power = LevelPower.existing(serverLevel);
            return power == null ? null : power.gridOfTower(this.worldPosition);
        }
        return null;
    }

    @Override
    public boolean connected() {
        return true;
    }

    @Override
    protected long powerFlow() {
        PowerSystem.Grid grid = this.grid();
        return grid == null ? 0 : grid.supplied();
    }

    @Override
    protected boolean tickMachine() {
        // 电网里有电在流动时塔顶亮
        PowerSystem.Grid grid = this.grid();
        return grid != null && grid.generation() + grid.discharged() > 0;
    }

    @Override
    protected int dataCount() {
        return DATA_COUNT;
    }

    @Override
    protected int dataValue(int index) {
        PowerSystem.Grid grid = this.grid();
        if (index < BASE_DATA) {
            return index == 0 ? (grid == null ? 1000 : (int) Math.round(grid.satisfaction() * 1000)) : super.dataValue(index);
        }
        if (grid == null) {
            return 0;
        }
        return switch (index) {
            case TOWERS -> grid.towerCount();
            case DEVICES -> grid.deviceCount();
            case GENERATION -> MachineMenu.low(grid.generation() * 10);
            case GENERATION + 1 -> MachineMenu.high(grid.generation() * 10);
            case DEMAND -> MachineMenu.low(grid.demand() * 10);
            case DEMAND + 1 -> MachineMenu.high(grid.demand() * 10);
            case SATISFACTION -> (int) Math.round(grid.satisfaction() * 1000);
            case CHARGED -> MachineMenu.low(grid.charged() * 10);
            case CHARGED + 1 -> MachineMenu.high(grid.charged() * 10);
            case DISCHARGED -> MachineMenu.low(grid.discharged() * 10);
            case DISCHARGED + 1 -> MachineMenu.high(grid.discharged() * 10);
            default -> 0;
        };
    }

    @Override
    protected MachineMenu.Type menuType() {
        return UniverseMenus.TESLA_TOWER;
    }
}
