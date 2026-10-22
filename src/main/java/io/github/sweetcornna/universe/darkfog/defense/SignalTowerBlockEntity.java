package io.github.sweetcornna.universe.darkfog.defense;

import io.github.sweetcornna.universe.core.darkfog.Support;
import io.github.sweetcornna.universe.core.power.PowerSystem;
import io.github.sweetcornna.universe.darkfog.DarkFogEnemy;
import io.github.sweetcornna.universe.darkfog.Taunts;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.factory.power.LevelPower;
import io.github.sweetcornna.universe.factory.power.TeslaTowerBlockEntity;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 信号塔（ADR-0028 §五）：两格高，在电网里既是一座连得远、覆盖大的塔，又是一台一直要电的设备。通电时
 * 48 格内的黑雾先打它（{@link Taunts#nearestTaunt}），同一个电网的导弹炮塔能打它范围里的目标，靠近黑雾基地会让它超载。
 */
public class SignalTowerBlockEntity extends TeslaTowerBlockEntity {
    public static final PowerSystem.TowerSpec SIGNAL_SPEC = new PowerSystem.TowerSpec(Support.SIGNAL_CONNECT, Support.SIGNAL_COVER);
    /** 界面数据：电力感应塔的那些，加上通没通电、嘲讽范围里有几个黑雾。 */
    public static final int POWERED = TeslaTowerBlockEntity.DATA_COUNT;
    public static final int TAUNTED = TeslaTowerBlockEntity.DATA_COUNT + 1;
    public static final int SIGNAL_DATA_COUNT = TeslaTowerBlockEntity.DATA_COUNT + 2;

    private double supplied;
    private int taunted;

    public SignalTowerBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.SIGNAL_TOWER.get(), pos, state);
    }

    @Override
    public PowerSystem.TowerSpec spec() {
        return SIGNAL_SPEC;
    }

    @Override
    protected void joinGrid(LevelPower power) {
        power.addTower(this.worldPosition, SIGNAL_SPEC);
        power.addDevice(this.worldPosition, this);
        if (this.level instanceof ServerLevel serverLevel) {
            Taunts.add(serverLevel, this.worldPosition);
        }
    }

    @Override
    protected void leaveGrid(LevelPower power) {
        power.removeTower(this.worldPosition);
        power.removeDevice(this.worldPosition);
        if (this.level instanceof ServerLevel serverLevel) {
            Taunts.remove(serverLevel, this.worldPosition);
        }
    }

    @Override
    public long demand() {
        return Support.SIGNAL_POWER;
    }

    @Override
    public void settle(double satisfaction, double load, long stored) {
        super.settle(satisfaction, load, stored);
        this.supplied = satisfaction;
    }

    /** 通没通电：有电才嘲讽、才借给导弹炮塔范围。只在服务端有意义。 */
    public boolean powered() {
        return this.supplied > 0;
    }

    @Override
    protected long powerFlow() {
        return Math.round(Support.SIGNAL_POWER * this.supplied);
    }

    @Override
    protected boolean tickMachine() {
        if (this.level instanceof ServerLevel level && level.getGameTime() % 20 == Math.floorMod(this.worldPosition.asLong(), 20)) {
            this.taunted = this.powered() ? countEnemies(level, Taunts.center(this.worldPosition), Taunts.RANGE) : 0;
        }
        return this.powered();
    }

    /** {@code range} 格内的黑雾单位和建筑有几个。 */
    static int countEnemies(ServerLevel level, Vec3 center, double range) {
        int count = 0;
        for (Entity entity : level.getEntities((Entity) null, new AABB(center, center).inflate(range),
                entity -> entity instanceof DarkFogEnemy && entity.isAlive())) {
            if (entity.position().distanceToSqr(center) <= range * range) {
                count++;
            }
        }
        return count;
    }

    @Override
    protected int dataCount() {
        return SIGNAL_DATA_COUNT;
    }

    @Override
    protected int dataValue(int index) {
        return switch (index) {
            case POWERED -> this.powered() ? 1 : 0;
            case TAUNTED -> this.taunted;
            default -> super.dataValue(index);
        };
    }

    @Override
    protected MachineMenu.Type menuType() {
        return UniverseMenus.SIGNAL_TOWER;
    }
}
