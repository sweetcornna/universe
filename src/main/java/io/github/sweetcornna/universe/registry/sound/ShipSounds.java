package io.github.sweetcornna.universe.registry.sound;

import static io.github.sweetcornna.universe.registry.UniverseSounds.sound;

import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/** 飞船、载具、货船的声音（ADR-0029），配方在 {@code tools/sounds/ships.py}。 */
public final class ShipSounds {
    // 驾驶时跟着玩家的三层循环（client.ship），外面听到的别的船
    public static final DeferredHolder<SoundEvent, SoundEvent> CABIN = sound("ship.cabin", 1, "飞船：舱内轰鸣", "Ship hull rumbles");
    public static final DeferredHolder<SoundEvent, SoundEvent> AIR = sound("ship.air", 1, "飞船：气流呼啸", "Air rushes past");
    public static final DeferredHolder<SoundEvent, SoundEvent> PULSE = sound("ship.pulse", 1, "脉冲引擎：嗡鸣", "Pulse engine hums");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXTERIOR = sound("ship.exterior", 1, 48, "飞船：推进器轰鸣", "Ship thrusters roar");

    public static final DeferredHolder<SoundEvent, SoundEvent> TAKEOFF = sound("ship.takeoff", 2, 64, "飞船：起飞", "Ship takes off");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAND = sound("ship.land", 2, 24, "飞船：着陆", "Ship lands");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARP_CHARGE = sound("ship.warp_charge", 1, "跃迁引擎：充能", "Warp drive charges");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARP = sound("ship.warp", 1, 64, "飞船：跃迁离开", "Ship warps away");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARP_EXIT = sound("ship.warp_exit", 2, 64, "飞船：跃出", "Ship drops out of warp");
    public static final DeferredHolder<SoundEvent, SoundEvent> HULL_HIT = sound("ship.hull_hit", 3, 32, "船体：受击", "Hull takes a hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> BUMP = sound("ship.bump", 2, "飞船：擦碰", "Ship bumps");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHIELD_HIT = sound("ship.shield_hit", 3, 32, "飞船护盾：挡下", "Ship shield absorbs");
    public static final DeferredHolder<SoundEvent, SoundEvent> PHOTON = sound("ship.photon", 3, 32, "光子炮：开火", "Photon cannon fires");
    /** 采矿光束：服务端每 8 刻放一段，叠起来是连续的。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> BEAM = sound("ship.beam", 2, "采矿光束：照射", "Mining beam hums");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUMMON = sound("ship.summon", 1, 32, "飞船：抵达", "Ship arrives");
    public static final DeferredHolder<SoundEvent, SoundEvent> POWER_UP = sound("ship.power_up", 1, "飞船：通电", "Ship powers up");
    public static final DeferredHolder<SoundEvent, SoundEvent> POWER_DOWN = sound("ship.power_down", 1, "飞船：断电", "Ship powers down");

    // 载具
    public static final DeferredHolder<SoundEvent, SoundEvent> ROVER = sound("vehicle.rover", 1, 32, "探索车：行驶", "Exocraft drives");
    public static final DeferredHolder<SoundEvent, SoundEvent> MECH_JET = sound("vehicle.mech_jet", 1, 32, "机甲：喷气", "Mech jets");
    public static final DeferredHolder<SoundEvent, SoundEvent> MECH_STEP = sound("vehicle.mech_step", 3, 24, "机甲：脚步", "Mech stomps");
    public static final DeferredHolder<SoundEvent, SoundEvent> CANNON = sound("vehicle.cannon", 3, 32, "机甲爆能炮：开火", "Mech cannon fires");
    /** 探索车的采矿激光：服务端每 10 刻放一段。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> LASER = sound("vehicle.laser", 2, "车载激光：照射", "Exocraft laser hums");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCAN = sound("vehicle.scan", 1, 32, "车载扫描", "Exocraft scans");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUBMARINE = sound("vehicle.submarine", 1, "潜艇：推进", "Submarine propels");
    public static final DeferredHolder<SoundEvent, SoundEvent> SONAR = sound("vehicle.sonar", 2, 48, "声呐：发出", "Sonar pings");
    public static final DeferredHolder<SoundEvent, SoundEvent> TORPEDO = sound("vehicle.torpedo", 2, 24, "鱼雷：发射", "Torpedo launches");
    public static final DeferredHolder<SoundEvent, SoundEvent> VEHICLE_SUMMON = sound("vehicle.summon", 2, "载具：就位", "Vehicle deploys");

    // 货船和海盗
    public static final DeferredHolder<SoundEvent, SoundEvent> FREIGHTER_WARP_IN = sound("freighter.warp_in", 1, 128, "货船：跃入", "Freighter warps in");
    public static final DeferredHolder<SoundEvent, SoundEvent> TERMINAL = sound("freighter.terminal", 3, "终端：确认", "Terminal beeps");
    public static final DeferredHolder<SoundEvent, SoundEvent> PIRATE_ALARM = sound("pirate.alarm", 1, 48, "警报：海盗来袭", "Pirate alarm blares");
    public static final DeferredHolder<SoundEvent, SoundEvent> PIRATE_SCAN = sound("pirate.scan", 1, 32, "海盗：扫描货舱", "Pirates scan cargo");
    public static final DeferredHolder<SoundEvent, SoundEvent> PIRATE_LASER = sound("pirate.laser", 3, 48, "海盗战机：开火", "Pirate fighter fires");

    private ShipSounds() {
    }

    public static void init() {
    }
}
