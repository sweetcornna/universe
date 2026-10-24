package io.github.sweetcornna.universe.registry.sound;

import static io.github.sweetcornna.universe.registry.UniverseSounds.sound;

import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/** 炮塔、黑雾、守卫机的声音（ADR-0029），配方在 {@code tools/sounds/hostile.py}。爆炸和能量弹命中用共用的。 */
public final class HostileSounds {
    // 玩家的炮塔
    public static final DeferredHolder<SoundEvent, SoundEvent> GAUSS = sound("turret.gauss", 4, 48, "高斯炮塔：开火", "Gauss turret fires");
    public static final DeferredHolder<SoundEvent, SoundEvent> LASER = sound("turret.laser", 3, 48, "激光炮塔：开火", "Laser turret fires");
    public static final DeferredHolder<SoundEvent, SoundEvent> IMPLOSION = sound("turret.implosion", 3, 64, "聚爆炮：开炮", "Implosion cannon fires");
    public static final DeferredHolder<SoundEvent, SoundEvent> MISSILE = sound("turret.missile", 3, 64, "导弹炮塔：发射", "Missile turret launches");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLASMA = sound("turret.plasma", 3, 64, "等离子炮塔：开火", "Plasma turret fires");
    public static final DeferredHolder<SoundEvent, SoundEvent> SR_PLASMA = sound("turret.sr_plasma", 3, 48, "近程等离子炮塔：开火", "SR plasma turret fires");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLASMA_BURST = sound("turret.plasma_burst", 3, 64, "等离子团：炸开", "Plasma bursts");

    // 黑雾的地面单位和建筑
    public static final DeferredHolder<SoundEvent, SoundEvent> RAIDER_ZAP = sound("darkfog.raider_zap", 4, "突袭者：放电", "Raider zaps");
    public static final DeferredHolder<SoundEvent, SoundEvent> RANGER_SHOT = sound("darkfog.ranger_shot", 4, 32, "游骑兵：射击", "Ranger fires");
    public static final DeferredHolder<SoundEvent, SoundEvent> GUARDIAN_SHOT = sound("darkfog.guardian_shot", 3, 32, "守卫者：开火", "Dark Fog guardian fires");
    public static final DeferredHolder<SoundEvent, SoundEvent> UNIT_AMBIENT = sound("darkfog.unit_ambient", 4, "黑雾单位：嗡鸣", "Dark Fog unit hums");
    public static final DeferredHolder<SoundEvent, SoundEvent> UNIT_HURT = sound("darkfog.unit_hurt", 4, "黑雾单位：受损", "Dark Fog unit takes damage");
    public static final DeferredHolder<SoundEvent, SoundEvent> UNIT_DEATH = sound("darkfog.unit_death", 3, 32, "黑雾单位：毁坏", "Dark Fog unit breaks apart");
    public static final DeferredHolder<SoundEvent, SoundEvent> TOWER_LASER = sound("darkfog.tower_laser", 3, 48, "激光塔：灼烧", "Laser tower burns");
    public static final DeferredHolder<SoundEvent, SoundEvent> SENTRY_FIRE = sound("darkfog.sentry_fire", 3, 48, "等离子哨塔：发射", "Plasma sentry fires");
    public static final DeferredHolder<SoundEvent, SoundEvent> BUILDING_HUM = sound("darkfog.building_hum", 3, "黑雾建筑：嗡鸣", "Dark Fog structure hums");
    public static final DeferredHolder<SoundEvent, SoundEvent> BUILDING_HURT = sound("darkfog.building_hurt", 4, "黑雾建筑：受损", "Dark Fog structure takes damage");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLLAPSE = sound("darkfog.collapse", 2, 96, "黑雾建筑：崩塌", "Dark Fog structure collapses");

    // 太空里的黑雾和巢穴
    public static final DeferredHolder<SoundEvent, SoundEvent> LANCER_SHOT = sound("darkfog.lancer_shot", 3, 64, "枪骑：开火", "Lancer fires");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOMB_DROP = sound("darkfog.bomb_drop", 2, 96, "枪骑：投弹", "Lancer drops a bomb");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUMPBACK_SHOT = sound("darkfog.humpback_shot", 3, 96, "座头鲸：开火", "Humpback fires");
    public static final DeferredHolder<SoundEvent, SoundEvent> HIVE_PLASMA = sound("darkfog.hive_plasma", 3, 96, "等离子塔：开火", "Plasma tower fires");
    public static final DeferredHolder<SoundEvent, SoundEvent> HIVE_LASER = sound("darkfog.hive_laser", 3, 64, "相位激光塔：开火", "Phase laser fires");
    public static final DeferredHolder<SoundEvent, SoundEvent> HIVE_CORE = sound("darkfog.hive_core", 2, 64, "巢穴核心：轰鸣", "Hive core drones");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRAFT_HUM = sound("darkfog.craft_hum", 2, 48, "黑雾飞行器：嗡鸣", "Dark Fog craft hums");
    public static final DeferredHolder<SoundEvent, SoundEvent> RELAY_LAND = sound("darkfog.relay_land", 2, 96, "中继站：降落", "Relay station lands");

    // 守卫机
    public static final DeferredHolder<SoundEvent, SoundEvent> SENTINEL_SCAN = sound("sentinel.scan", 2, 32, "守卫机：扫描", "Sentinel scans");
    public static final DeferredHolder<SoundEvent, SoundEvent> SENTINEL_ALERT = sound("sentinel.alert", 2, 48, "守卫机：警报", "Sentinel alarm sounds");
    public static final DeferredHolder<SoundEvent, SoundEvent> SENTINEL_STAND_DOWN = sound("sentinel.stand_down", 1, 32, "守卫机：解除警戒", "Sentinels stand down");
    public static final DeferredHolder<SoundEvent, SoundEvent> SENTINEL_WARP = sound("sentinel.warp", 3, 32, "守卫机：跃迁", "Sentinel warps");
    public static final DeferredHolder<SoundEvent, SoundEvent> SENTINEL_SHOT = sound("sentinel.shot", 4, 32, "守卫机：开火", "Sentinel fires");
    public static final DeferredHolder<SoundEvent, SoundEvent> SENTINEL_HEAVY_SHOT = sound("sentinel.heavy_shot", 3, 48, "重型守卫机：开火", "Heavy sentinel fires");
    public static final DeferredHolder<SoundEvent, SoundEvent> SENTINEL_AMBIENT = sound("sentinel.ambient", 3, "守卫机：嗡鸣", "Sentinel hums");
    public static final DeferredHolder<SoundEvent, SoundEvent> SENTINEL_HURT = sound("sentinel.hurt", 4, "守卫机：受损", "Sentinel takes damage");
    public static final DeferredHolder<SoundEvent, SoundEvent> SENTINEL_DEATH = sound("sentinel.death", 2, 32, "守卫机：坠毁", "Sentinel crashes");
    public static final DeferredHolder<SoundEvent, SoundEvent> SENTINEL_VENT_HIT = sound("sentinel.vent_hit", 2, "守卫机：散热口中弹", "Sentinel vent hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> SENTINEL_STEP = sound("sentinel.step", 4, "守卫机：脚步", "Sentinel walks");
    public static final DeferredHolder<SoundEvent, SoundEvent> QUAD_POUNCE = sound("sentinel.pounce", 2, "四足机：飞扑", "Sentinel Quad pounces");
    public static final DeferredHolder<SoundEvent, SoundEvent> QUAD_STRIKE = sound("sentinel.strike", 2, "四足机：撞击", "Sentinel Quad strikes");
    public static final DeferredHolder<SoundEvent, SoundEvent> QUAD_CHARGE = sound("sentinel.charge", 1, 32, "四足机：蓄能", "Sentinel Quad charges");
    public static final DeferredHolder<SoundEvent, SoundEvent> QUAD_LASER = sound("sentinel.laser", 2, 48, "四足机：激光", "Sentinel Quad fires laser");
    public static final DeferredHolder<SoundEvent, SoundEvent> WALKER_ROAR = sound("sentinel.roar", 2, 64, "步行机：轰鸣", "Sentinel Walker roars");
    public static final DeferredHolder<SoundEvent, SoundEvent> WALKER_STOMP = sound("sentinel.stomp", 2, 64, "步行机：践踏", "Sentinel Walker stomps");
    public static final DeferredHolder<SoundEvent, SoundEvent> REPAIR_WELD = sound("sentinel.repair", 2, "维修无人机：焊接", "Repair Drone welds");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUMMON_CHARGE = sound("sentinel.summon_charge", 1, 32, "召唤无人机：蓄能", "Summoner Drone charges");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUMMON = sound("sentinel.summon", 2, 32, "召唤无人机：召唤", "Summoner Drone summons");

    private HostileSounds() {
    }

    public static void init() {
    }
}
