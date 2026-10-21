package io.github.sweetcornna.universe.factory.power;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.power.PowerSystem;
import io.github.sweetcornna.universe.core.mecha.MechaEnergy;
import io.github.sweetcornna.universe.core.survival.LifeSupport;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.mecha.Mecha;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import io.github.sweetcornna.universe.survival.Exosuit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * 电力感应塔（玩法设计 §9.4）：覆盖范围内的设备自动接入，连接距离内的塔连成一个电网。界面显示整个电网的情况。
 *
 * <p>无线输电塔和卫星配电站（ADR-0022）也用这个方块实体，范围看方块（{@link PowerTowerBlock}）。无线输电塔另外作为用电设备
 * 接入：附近有穿着外骨骼的玩家时每刻用 {@link #CHARGE_POWER} 焦耳，给生命维持充氧、给危害防护充能。
 */
public class TeslaTowerBlockEntity extends MachineBlockEntity {
    public static final PowerSystem.TowerSpec SPEC = new PowerSystem.TowerSpec(12, 6);
    /** 无线输电塔（ADR-0022）：连得近、覆盖小，另外给附近的外骨骼充能。 */
    public static final PowerSystem.TowerSpec WIRELESS_SPEC = new PowerSystem.TowerSpec(10, 4);
    public static final int WIRELESS_CHARGE_RADIUS = 5;
    /** 卫星配电站：远距离连线，覆盖一大片。 */
    public static final PowerSystem.TowerSpec SATELLITE_SPEC = new PowerSystem.TowerSpec(40, 20);
    /** 界面数据：塔数、设备数、发电、用电、满足率、充电、放电。 */
    public static final int TOWERS = BASE_DATA;
    public static final int DEVICES = BASE_DATA + 1;
    public static final int GENERATION = BASE_DATA + 2;
    public static final int DEMAND = BASE_DATA + 4;
    public static final int SATISFACTION = BASE_DATA + 6;
    public static final int CHARGED = BASE_DATA + 7;
    public static final int DISCHARGED = BASE_DATA + 9;
    /** 覆盖范围（低 8 位）和连接距离（高 8 位），给外骨骼充能的半径。 */
    public static final int RANGE = BASE_DATA + 11;
    public static final int CHARGE_RADIUS = BASE_DATA + 12;
    public static final int DATA_COUNT = BASE_DATA + 13;
    /** 无线输电塔给外骨骼充能的功率：戴森球计划的 1.5 MW。 */
    public static final long CHARGE_POWER = 75;
    /** 满电时每秒充多少（占满值的比例）。 */
    private static final float CHARGE_PER_SECOND = 0.05F;

    private boolean charging;
    private double chargeSupplied;
    /** 附近的机甲核心这一刻还想充多少（ADR-0023），每座塔最多 {@link MechaEnergy#TOWER_CHARGE}。 */
    private double mechaDemand;
    /** 不封顶的总和，按比例分电用。 */
    private double mechaWanted;

    public TeslaTowerBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.TESLA_TOWER.get(), pos, state, 0);
    }

    public PowerSystem.TowerSpec spec() {
        return this.getBlockState().getBlock() instanceof PowerTowerBlock tower ? tower.spec() : SPEC;
    }

    private int chargeRadius() {
        return this.getBlockState().getBlock() instanceof PowerTowerBlock tower ? tower.chargeRadius() : 0;
    }

    @Override
    protected void joinGrid(LevelPower power) {
        power.addTower(this.worldPosition, this.spec());
        if (this.chargeRadius() > 0) {
            power.addDevice(this.worldPosition, this);
        }
    }

    @Override
    protected void leaveGrid(LevelPower power) {
        power.removeTower(this.worldPosition);
        if (this.chargeRadius() > 0) {
            power.removeDevice(this.worldPosition);
        }
    }

    @Override
    public long demand() {
        return this.charging ? CHARGE_POWER + Math.round(this.mechaDemand) : 0;
    }

    @Override
    public void settle(double satisfaction, double load, long stored) {
        super.settle(satisfaction, load, stored);
        this.chargeSupplied = this.charging ? satisfaction : 0;
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
        int radius = this.chargeRadius();
        if (radius > 0 && this.level instanceof ServerLevel serverLevel) {
            this.charge(serverLevel, radius);
        }
        // 电网里有电在流动时塔顶亮
        PowerSystem.Grid grid = this.grid();
        return grid != null && grid.generation() + grid.discharged() > 0;
    }

    /**
     * 每秒看一次附近有没有穿外骨骼的玩家，有就要电；拿到多少电就充多少。密封的外骨骼补氧和防护，穿着胸甲的给机甲核心充电，
     * 几个人分这座塔的 {@link MechaEnergy#TOWER_CHARGE}。
     */
    private void charge(ServerLevel level, int radius) {
        double supplied = this.chargeSupplied;
        double asked = this.mechaWanted;
        this.chargeSupplied = 0;
        if (level.getGameTime() % 20 != Math.floorMod(this.worldPosition.asLong(), 20) && !this.charging) {
            return;
        }
        AABB box = new AABB(this.worldPosition).inflate(radius);
        boolean any = false;
        double demand = 0;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box, Mecha::chest)) {
            any = true;
            double want = Mecha.towerDemand(player);
            if (supplied > 0) {
                if (Exosuit.of(player).sealed()) {
                    LifeSupport support = player.getData(UniverseAttachments.LIFE_SUPPORT);
                    float share = (float) (CHARGE_PER_SECOND / 20 * supplied);
                    support.addOxygen(LifeSupport.OXYGEN_MAX * share);
                    support.rechargeHazards(LifeSupport.HAZARD_MAX * share);
                }
                if (asked > 0 && want > 0) {
                    Mecha.towerCharge(player, want * supplied * Math.min(1, MechaEnergy.TOWER_CHARGE / asked));
                }
            }
            demand += want;
        }
        this.charging = any;
        this.mechaWanted = demand;
        this.mechaDemand = Math.min(MechaEnergy.TOWER_CHARGE, demand);
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
        if (index == RANGE) {
            PowerSystem.TowerSpec spec = this.spec();
            return spec.coverRange() | spec.connectRange() << 8;
        }
        if (index == CHARGE_RADIUS) {
            return this.chargeRadius();
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
