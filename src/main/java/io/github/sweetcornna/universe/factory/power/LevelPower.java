package io.github.sweetcornna.universe.factory.power;

import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.power.PowerDevice;
import io.github.sweetcornna.universe.core.power.PowerSystem;
import io.github.sweetcornna.universe.perf.ServerPerf;
import io.github.sweetcornna.universe.stats.ProductionLog;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * 一个服务端世界的电网：包着核心层的 {@link PowerSystem}，塔和设备的编号是方块位置（ADR-0011）。
 *
 * <p>方块实体在加载时接入、移除或区块卸载时退出，所以这里只有已加载区块里的设备。每刻在世界 tick 开始时结算，
 * 先于方块实体：设备这一刻按刚结算出的满足率干活，发电机按负荷烧燃料。
 */
public final class LevelPower {
    private static final Map<ServerLevel, LevelPower> LEVELS = new WeakHashMap<>();

    private final PowerSystem system = new PowerSystem();

    private LevelPower() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(LevelTickEvent.Pre.class, event -> {
            if (event.getLevel() instanceof ServerLevel level && level.tickRateManager().runsNormally()) {
                LevelPower power = LEVELS.get(level);
                if (power != null) {
                    long start = ServerPerf.begin();
                    power.system.tick();
                    ProductionLog.power(level, power.system.grids());
                    ServerPerf.end("power", start);
                }
            }
        });
        gameBus.addListener(LevelEvent.Unload.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                LEVELS.remove(level);
            }
        });
        gameBus.addListener(ServerStoppedEvent.class, event -> LEVELS.clear());
    }

    public static LevelPower of(ServerLevel level) {
        return LEVELS.computeIfAbsent(level, key -> new LevelPower());
    }

    /** 已经有电网时返回它；退出电网时用，免得给正在卸载的世界又建一个。 */
    public static @Nullable LevelPower existing(ServerLevel level) {
        return LEVELS.get(level);
    }

    public void addTower(BlockPos pos, PowerSystem.TowerSpec spec) {
        this.system.addTower(pos.asLong(), pos.getX(), pos.getY(), pos.getZ(), spec);
    }

    public void removeTower(BlockPos pos) {
        this.system.removeTower(pos.asLong());
    }

    public void addDevice(BlockPos pos, PowerDevice device) {
        this.system.addDevice(pos.asLong(), pos.getX(), pos.getY(), pos.getZ(), device);
    }

    public void removeDevice(BlockPos pos) {
        this.system.removeDevice(pos.asLong());
    }

    /** 设备所在的电网；没有塔覆盖时为空。 */
    public PowerSystem.@Nullable Grid gridOf(BlockPos device) {
        return this.system.gridOf(device.asLong());
    }

    public PowerSystem.@Nullable Grid gridOfTower(BlockPos tower) {
        return this.system.gridOfTower(tower.asLong());
    }
}
