package io.github.sweetcornna.universe.registry;

import io.github.sweetcornna.universe.darkfog.turret.TurretShell;
import io.github.sweetcornna.universe.darkfog.turret.TurretMissile;
import io.github.sweetcornna.universe.asteroid.Asteroid;
import io.github.sweetcornna.universe.darkfog.defense.ThrownUnit;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogBuilding;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogGuardian;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogRaider;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogRanger;
import io.github.sweetcornna.universe.exocraft.ExoMech;
import io.github.sweetcornna.universe.exocraft.Exocraft;
import io.github.sweetcornna.universe.exocraft.HeavyRover;
import io.github.sweetcornna.universe.exocraft.HoverCar;
import io.github.sweetcornna.universe.exocraft.Submarine;
import io.github.sweetcornna.universe.exocraft.Torpedo;
import io.github.sweetcornna.universe.encounter.SentinelInterceptor;
import io.github.sweetcornna.universe.pirate.PirateFighter;
import io.github.sweetcornna.universe.sentinel.QuadrupedSentinel;
import io.github.sweetcornna.universe.sentinel.RepairSentinel;
import io.github.sweetcornna.universe.sentinel.Sentinel;
import io.github.sweetcornna.universe.sentinel.SummonerSentinel;
import io.github.sweetcornna.universe.sentinel.WalkerSentinel;
import io.github.sweetcornna.universe.entity.PlasmaBolt;
import io.github.sweetcornna.universe.entity.PlasmaGrenade;
import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.entity.AlienCreature;
import io.github.sweetcornna.universe.entity.AlienFlyer;
import io.github.sweetcornna.universe.entity.AquaticCreature;
import io.github.sweetcornna.universe.entity.civ.AlienNpc;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 实体类型注册。 */
public final class UniverseEntities {
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(Universe.MOD_ID);

    /** 地上的动物（四足、六足、两足、节蠕虫）。碰撞箱按物种在实体里算，这里只是默认值。 */
    public static final DeferredHolder<EntityType<?>, EntityType<AlienCreature>> ALIEN_CREATURE = ENTITIES.registerEntityType(
            "alien_creature", AlienCreature::new, MobCategory.CREATURE, builder -> builder.sized(0.9F, 1.0F).clientTrackingRange(10));
    /** 水里的动物（ADR-0026）：鱼、鳗、水母、乌贼。 */
    public static final DeferredHolder<EntityType<?>, EntityType<AquaticCreature>> AQUATIC_CREATURE = ENTITIES.registerEntityType(
            "aquatic_creature", AquaticCreature::new, MobCategory.WATER_CREATURE, builder -> builder.sized(0.7F, 0.5F).clientTrackingRange(10));
    /** 飞兽。 */
    public static final DeferredHolder<EntityType<?>, EntityType<AlienFlyer>> ALIEN_FLYER = ENTITIES.registerEntityType(
            "alien_flyer", AlienFlyer::new, MobCategory.CREATURE, builder -> builder.sized(0.6F, 0.6F).clientTrackingRange(10));

    /** 组装好的飞船。尺寸按船身在实体里算；每刻同步位置，远处也看得见。 */
    public static final DeferredHolder<EntityType<?>, EntityType<ShipEntity>> SHIP = ENTITIES.registerEntityType(
            "ship", ShipEntity::new, MobCategory.MISC, builder -> builder.sized(1.0F, 1.0F).clientTrackingRange(16).updateInterval(1).fireImmune().noLootTable());

    /** 多功能工具的等离子弹（ADR-0014）。飞行轨迹是直线，客户端按速度推算，不用常同步；不存档。 */
    public static final DeferredHolder<EntityType<?>, EntityType<PlasmaBolt>> PLASMA_BOLT = ENTITIES.registerEntityType(
            "plasma_bolt", PlasmaBolt::new, MobCategory.MISC,
            builder -> builder.sized(0.25F, 0.25F).clientTrackingRange(6).updateInterval(10).noSave().noLootTable().fireImmune());
    /** 等离子发射器的榴弹（ADR-0026）：会弹跳，位置常同步；不存档。 */
    public static final DeferredHolder<EntityType<?>, EntityType<PlasmaGrenade>> PLASMA_GRENADE = ENTITIES.registerEntityType(
            "plasma_grenade", PlasmaGrenade::new, MobCategory.MISC,
            builder -> builder.sized(0.3F, 0.3F).clientTrackingRange(6).updateInterval(2).noSave().noLootTable().fireImmune());

    /** 守卫机（ADR-0014）：不存档，由 {@code Sentinels} 按玩家附近的情况补充。 */
    public static final DeferredHolder<EntityType<?>, EntityType<Sentinel>> SENTINEL = ENTITIES.registerEntityType(
            "sentinel", Sentinel::new, MobCategory.MISC,
            builder -> builder.sized(0.8F, 0.75F).eyeHeight(0.45F).clientTrackingRange(10).noSave().fireImmune());
    public static final DeferredHolder<EntityType<?>, EntityType<Sentinel>> HEAVY_SENTINEL = ENTITIES.registerEntityType(
            "heavy_sentinel", Sentinel::new, MobCategory.MISC,
            builder -> builder.sized(1.4F, 1.1F).eyeHeight(0.55F).clientTrackingRange(10).noSave().fireImmune());
    /** 维修无人机（ADR-0026）。 */
    public static final DeferredHolder<EntityType<?>, EntityType<RepairSentinel>> REPAIR_SENTINEL = ENTITIES.registerEntityType(
            "repair_sentinel", RepairSentinel::new, MobCategory.MISC,
            builder -> builder.sized(0.7F, 0.6F).eyeHeight(0.35F).clientTrackingRange(10).noSave().fireImmune());
    /** 召唤无人机（ADR-0026）。 */
    public static final DeferredHolder<EntityType<?>, EntityType<SummonerSentinel>> SUMMONER_SENTINEL = ENTITIES.registerEntityType(
            "summoner_sentinel", SummonerSentinel::new, MobCategory.MISC,
            builder -> builder.sized(1.0F, 0.9F).eyeHeight(0.5F).clientTrackingRange(10).noSave().fireImmune());
    /** 四足机（ADR-0026）：地面单位。 */
    public static final DeferredHolder<EntityType<?>, EntityType<QuadrupedSentinel>> QUADRUPED_SENTINEL = ENTITIES.registerEntityType(
            "quadruped_sentinel", QuadrupedSentinel::new, MobCategory.MISC,
            builder -> builder.sized(1.6F, 1.5F).eyeHeight(1.2F).clientTrackingRange(10).noSave().fireImmune());
    /** 步行机（ADR-0026）：4 格高的地面单位。 */
    public static final DeferredHolder<EntityType<?>, EntityType<WalkerSentinel>> WALKER_SENTINEL = ENTITIES.registerEntityType(
            "walker_sentinel", WalkerSentinel::new, MobCategory.MISC,
            builder -> builder.sized(2.2F, 4.0F).eyeHeight(3.4F).clientTrackingRange(12).noSave().fireImmune());

    /** 探索车（ADR-0017）：驾驶员的客户端算位置，别人看到的每刻同步、插值。 */
    public static final DeferredHolder<EntityType<?>, EntityType<Exocraft>> EXOCRAFT = ENTITIES.registerEntityType(
            "exocraft", Exocraft::new, MobCategory.MISC,
            builder -> builder.sized(2.0F, 1.9F).clientTrackingRange(10).updateInterval(1).fireImmune().noLootTable());

    /** 潜艇（ADR-0026）：和探索车一样由驾驶员的客户端算位置。 */
    public static final DeferredHolder<EntityType<?>, EntityType<Submarine>> SUBMARINE = ENTITIES.registerEntityType(
            "submarine", Submarine::new, MobCategory.MISC,
            builder -> builder.sized(2.2F, 1.5F).clientTrackingRange(10).updateInterval(1).fireImmune().noLootTable());
    /** 悬浮车、重型车、机甲（ADR-0026）：和探索车一样由驾驶员的客户端算位置。 */
    public static final DeferredHolder<EntityType<?>, EntityType<HoverCar>> HOVER_CAR = ENTITIES.registerEntityType(
            "hover_car", HoverCar::new, MobCategory.MISC,
            builder -> builder.sized(1.9F, 1.2F).clientTrackingRange(10).updateInterval(1).fireImmune().noLootTable());
    public static final DeferredHolder<EntityType<?>, EntityType<HeavyRover>> HEAVY_ROVER = ENTITIES.registerEntityType(
            "heavy_rover", HeavyRover::new, MobCategory.MISC,
            builder -> builder.sized(2.6F, 2.4F).clientTrackingRange(10).updateInterval(1).fireImmune().noLootTable());
    public static final DeferredHolder<EntityType<?>, EntityType<ExoMech>> EXO_MECH = ENTITIES.registerEntityType(
            "exo_mech", ExoMech::new, MobCategory.MISC,
            builder -> builder.sized(1.8F, 3.4F).clientTrackingRange(10).updateInterval(1).fireImmune().noLootTable());
    /** 潜艇的鱼雷：直线跑，客户端按速度推算；不存档。 */
    public static final DeferredHolder<EntityType<?>, EntityType<Torpedo>> TORPEDO = ENTITIES.registerEntityType(
            "torpedo", Torpedo::new, MobCategory.MISC,
            builder -> builder.sized(0.4F, 0.4F).clientTrackingRange(6).updateInterval(5).noSave().noLootTable().fireImmune());

    /** 小行星（ADR-0017）：由 {@code Asteroids} 在玩家附近生成，不存档；一动不动（自转只在客户端画），很少同步位置。 */
    public static final DeferredHolder<EntityType<?>, EntityType<Asteroid>> ASTEROID = ENTITIES.registerEntityType(
            "asteroid", Asteroid::new, MobCategory.MISC,
            builder -> builder.sized(2.0F, 2.0F).clientTrackingRange(16).updateInterval(40).noSave().fireImmune().noLootTable());

    /** 海盗战机（ADR-0017）：由 {@code Pirates} 按袭击生成，不存档；打得远、飞得快，每刻同步位置。 */
    public static final DeferredHolder<EntityType<?>, EntityType<PirateFighter>> PIRATE_FIGHTER = ENTITIES.registerEntityType(
            "pirate_fighter", PirateFighter::new, MobCategory.MISC,
            builder -> builder.sized(2.6F, 1.2F).clientTrackingRange(16).updateInterval(1).noSave().fireImmune());

    /** 太空守卫机的拦截机（ADR-0024）：和海盗战机一样大。 */
    public static final DeferredHolder<EntityType<?>, EntityType<SentinelInterceptor>> SENTINEL_INTERCEPTOR = ENTITIES.registerEntityType(
            "sentinel_interceptor", SentinelInterceptor::new, MobCategory.MISC,
            builder -> builder.sized(2.6F, 1.2F).clientTrackingRange(16).updateInterval(1).noSave().fireImmune());
    /** 外星文明的 NPC（ADR-0027 §三）：不存档，由管理器放；尺寸按种族在实体里定，这里是商旅族的。 */
    public static final DeferredHolder<EntityType<?>, EntityType<AlienNpc>> ALIEN_NPC = ENTITIES.registerEntityType(
            "alien_npc", AlienNpc::new, MobCategory.MISC,
            builder -> builder.sized(0.6F, 1.3F).eyeHeight(1.15F).clientTrackingRange(10).noSave().noLootTable().fireImmune());

    /** 黑雾地面基地的建筑（ADR-0028 §二）：不动，不存档，由 {@code DarkFogBases} 按存档放；尺寸按种类在实体里算。 */
    public static final DeferredHolder<EntityType<?>, EntityType<DarkFogBuilding>> DARK_FOG_BUILDING = ENTITIES.registerEntityType(
            "dark_fog_building", DarkFogBuilding::new, MobCategory.MISC,
            builder -> builder.sized(3.0F, 4.0F).clientTrackingRange(12).updateInterval(20).noSave().noLootTable().fireImmune());
    /** 黑雾的单位（ADR-0028 §二）：不存档。 */
    public static final DeferredHolder<EntityType<?>, EntityType<DarkFogRaider>> DARK_FOG_RAIDER = ENTITIES.registerEntityType(
            "dark_fog_raider", DarkFogRaider::new, MobCategory.MISC,
            builder -> builder.sized(1.2F, 1.0F).eyeHeight(0.6F).clientTrackingRange(10).noSave().noLootTable().fireImmune());
    public static final DeferredHolder<EntityType<?>, EntityType<DarkFogRanger>> DARK_FOG_RANGER = ENTITIES.registerEntityType(
            "dark_fog_ranger", DarkFogRanger::new, MobCategory.MISC,
            builder -> builder.sized(0.9F, 0.7F).eyeHeight(0.4F).clientTrackingRange(10).noSave().noLootTable().fireImmune());
    public static final DeferredHolder<EntityType<?>, EntityType<DarkFogGuardian>> DARK_FOG_GUARDIAN = ENTITIES.registerEntityType(
            "dark_fog_guardian", DarkFogGuardian::new, MobCategory.MISC,
            builder -> builder.sized(1.6F, 1.2F).eyeHeight(0.7F).clientTrackingRange(10).noSave().noLootTable().fireImmune());
    /** 游骑兵、守卫者的激光弹：直线飞，客户端按速度推算；不存档。 */
    public static final DeferredHolder<EntityType<?>, EntityType<io.github.sweetcornna.universe.darkfog.entity.DarkFogBolt>> DARK_FOG_BOLT =
            ENTITIES.registerEntityType("dark_fog_bolt", io.github.sweetcornna.universe.darkfog.entity.DarkFogBolt::new, MobCategory.MISC,
                    builder -> builder.sized(0.25F, 0.25F).clientTrackingRange(6).updateInterval(10).noSave().noLootTable().fireImmune());
    /** 等离子哨塔吊射的炮弹：有重力，位置常同步；不存档。 */
    public static final DeferredHolder<EntityType<?>, EntityType<io.github.sweetcornna.universe.darkfog.entity.DarkFogShell>> DARK_FOG_SHELL =
            ENTITIES.registerEntityType("dark_fog_shell", io.github.sweetcornna.universe.darkfog.entity.DarkFogShell::new, MobCategory.MISC,
                    builder -> builder.sized(0.4F, 0.4F).clientTrackingRange(8).updateInterval(2).noSave().noLootTable().fireImmune());
    /** 聚爆炮的炮弹、导弹炮塔的导弹（ADR-0028 §五）：不存档；导弹会拐弯，每刻同步。 */
    public static final DeferredHolder<EntityType<?>, EntityType<TurretShell>> TURRET_SHELL = ENTITIES.registerEntityType(
            "turret_shell", TurretShell::new, MobCategory.MISC,
            builder -> builder.sized(0.35F, 0.35F).clientTrackingRange(8).updateInterval(2).noSave().noLootTable().fireImmune());
    public static final DeferredHolder<EntityType<?>, EntityType<TurretMissile>> TURRET_MISSILE = ENTITIES.registerEntityType(
            "turret_missile", TurretMissile::new, MobCategory.MISC,
            builder -> builder.sized(0.35F, 0.35F).clientTrackingRange(8).updateInterval(1).noSave().noLootTable().fireImmune());
    /** 扔出去的燃烧单元、爆破单元（ADR-0028 §五）：和雪球一样画物品本身；不存档。 */
    public static final DeferredHolder<EntityType<?>, EntityType<ThrownUnit>> THROWN_UNIT = ENTITIES.registerEntityType(
            "thrown_unit", ThrownUnit::new, MobCategory.MISC,
            builder -> builder.sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10).noSave().noLootTable());
    // ---- M16 第二块（ADR-0028 §八）
    /** 太空巢穴的建筑（ADR-0028 §八）：不动，不存档，由 {@code DarkFogHives} 放；尺寸按种类在实体里算。太空里远远就要看得见。 */
    public static final DeferredHolder<EntityType<?>, EntityType<io.github.sweetcornna.universe.darkfog.hive.HiveBuilding>> HIVE_BUILDING =
            ENTITIES.registerEntityType("hive_building", io.github.sweetcornna.universe.darkfog.hive.HiveBuilding::new, MobCategory.MISC,
                    builder -> builder.sized(6.0F, 5.0F).clientTrackingRange(32).updateInterval(20).noSave().noLootTable().fireImmune());
    /** 巢穴等离子塔的炮弹：不受重力、直线飞，客户端按速度推算；不存档。 */
    public static final DeferredHolder<EntityType<?>, EntityType<io.github.sweetcornna.universe.darkfog.hive.HivePlasmaShell>> HIVE_PLASMA_SHELL =
            ENTITIES.registerEntityType("hive_plasma_shell", io.github.sweetcornna.universe.darkfog.hive.HivePlasmaShell::new, MobCategory.MISC,
                    builder -> builder.sized(0.6F, 0.6F).clientTrackingRange(16).updateInterval(10).noSave().noLootTable().fireImmune());
    // M16 黑雾：巢穴建筑（B）的实体加在这行上面
    /** 巢穴的枪骑、座头鲸（ADR-0028 §八）：在太空里自由飞，不存档，由 {@code DarkFogHives}、{@code LancerRaids} 放。 */
    public static final DeferredHolder<EntityType<?>, EntityType<io.github.sweetcornna.universe.darkfog.space.DarkFogLancer>> DARK_FOG_LANCER =
            ENTITIES.registerEntityType("dark_fog_lancer", io.github.sweetcornna.universe.darkfog.space.DarkFogLancer::new, MobCategory.MISC,
                    builder -> builder.sized(2.2F, 0.9F).eyeHeight(0.45F).clientTrackingRange(16).updateInterval(1).noSave().noLootTable()
                            .fireImmune());
    public static final DeferredHolder<EntityType<?>, EntityType<io.github.sweetcornna.universe.darkfog.space.DarkFogHumpback>> DARK_FOG_HUMPBACK =
            ENTITIES.registerEntityType("dark_fog_humpback", io.github.sweetcornna.universe.darkfog.space.DarkFogHumpback::new, MobCategory.MISC,
                    builder -> builder.sized(6.0F, 3.0F).eyeHeight(1.8F).clientTrackingRange(20).updateInterval(2).noSave().noLootTable()
                            .fireImmune());
    /** 枪骑扔的炸弹：有重力，位置常同步；不存档。 */
    public static final DeferredHolder<EntityType<?>, EntityType<io.github.sweetcornna.universe.darkfog.space.LancerBomb>> LANCER_BOMB =
            ENTITIES.registerEntityType("lancer_bomb", io.github.sweetcornna.universe.darkfog.space.LancerBomb::new, MobCategory.MISC,
                    builder -> builder.sized(0.4F, 0.4F).clientTrackingRange(10).updateInterval(2).noSave().noLootTable().fireImmune());
    // M16 黑雾：太空单位（U）的实体加在这行上面
    /** 起飞、降落中的中继站和种子（ADR-0028 §八）：飞行本身按时间算，这两个只是看得见、打得着的样子；不存档。 */
    public static final DeferredHolder<EntityType<?>, EntityType<io.github.sweetcornna.universe.darkfog.flight.DarkFogCraft>> DARK_FOG_FLYING_RELAY =
            ENTITIES.registerEntityType("dark_fog_flying_relay", io.github.sweetcornna.universe.darkfog.flight.DarkFogCraft::new, MobCategory.MISC,
                    builder -> builder.sized(3.0F, 2.0F).clientTrackingRange(32).updateInterval(2).noSave().noLootTable().fireImmune());
    public static final DeferredHolder<EntityType<?>, EntityType<io.github.sweetcornna.universe.darkfog.flight.DarkFogCraft>> DARK_FOG_SEED =
            ENTITIES.registerEntityType("dark_fog_seed", io.github.sweetcornna.universe.darkfog.flight.DarkFogCraft::new, MobCategory.MISC,
                    builder -> builder.sized(2.5F, 2.5F).clientTrackingRange(32).updateInterval(2).noSave().noLootTable().fireImmune());
    // M16 黑雾：中继站和种子（R）的实体加在这行上面
    /** 等离子炮塔、近程等离子炮塔打出的等离子团（ADR-0028 §八）：直线飞得快，每刻同步；不存档。 */
    public static final DeferredHolder<EntityType<?>, EntityType<io.github.sweetcornna.universe.darkfog.turret.TurretPlasma>> TURRET_PLASMA =
            ENTITIES.registerEntityType("turret_plasma", io.github.sweetcornna.universe.darkfog.turret.TurretPlasma::new, MobCategory.MISC,
                    builder -> builder.sized(0.4F, 0.4F).clientTrackingRange(8).updateInterval(1).noSave().noLootTable().fireImmune());
    // M16 黑雾：等离子炮塔（T）的实体加在这行上面
    // ---- M18 舰队和战斗（ADR-0033 §一、§二）
    /** 机甲放出来的舰队单位：物品一直在编队格里，实体只是它在外面的样子，不存档。 */
    public static final DeferredHolder<EntityType<?>, EntityType<io.github.sweetcornna.universe.mecha.fleet.FleetUnit>> FLEET_PROTOTYPE =
            fleet("fleet_prototype", io.github.sweetcornna.universe.core.mecha.fleet.FleetUnitKind.PROTOTYPE, 0.7F, 0.5F, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<io.github.sweetcornna.universe.mecha.fleet.FleetUnit>> FLEET_PRECISION_DRONE =
            fleet("fleet_precision_drone", io.github.sweetcornna.universe.core.mecha.fleet.FleetUnitKind.PRECISION_DRONE, 0.8F, 0.5F, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<io.github.sweetcornna.universe.mecha.fleet.FleetUnit>> FLEET_ATTACK_DRONE =
            fleet("fleet_attack_drone", io.github.sweetcornna.universe.core.mecha.fleet.FleetUnitKind.ATTACK_DRONE, 1.0F, 0.7F, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<io.github.sweetcornna.universe.mecha.fleet.FleetUnit>> FLEET_CORVETTE =
            fleet("fleet_corvette", io.github.sweetcornna.universe.core.mecha.fleet.FleetUnitKind.CORVETTE, 2.5F, 1.2F, 20);
    public static final DeferredHolder<EntityType<?>, EntityType<io.github.sweetcornna.universe.mecha.fleet.FleetUnit>> FLEET_DESTROYER =
            fleet("fleet_destroyer", io.github.sweetcornna.universe.core.mecha.fleet.FleetUnitKind.DESTROYER, 5.0F, 2.0F, 24);
    /** 舰队单位、机甲等离子炮打出的等离子团：算放它的玩家打的，直线飞，每刻同步；不存档。 */
    public static final DeferredHolder<EntityType<?>, EntityType<io.github.sweetcornna.universe.mecha.combat.PlasmaShot>> PLASMA_SHOT =
            ENTITIES.registerEntityType("plasma_shot", io.github.sweetcornna.universe.mecha.combat.PlasmaShot::new, MobCategory.MISC,
                    builder -> builder.sized(0.4F, 0.4F).clientTrackingRange(10).updateInterval(1).noSave().noLootTable().fireImmune());
    /** 机甲的导弹：会拐弯，每刻同步；不存档。 */
    public static final DeferredHolder<EntityType<?>, EntityType<io.github.sweetcornna.universe.mecha.combat.MechaMissile>> MECHA_MISSILE =
            ENTITIES.registerEntityType("mecha_missile", io.github.sweetcornna.universe.mecha.combat.MechaMissile::new, MobCategory.MISC,
                    builder -> builder.sized(0.35F, 0.35F).clientTrackingRange(8).updateInterval(1).noSave().noLootTable().fireImmune());

    private static DeferredHolder<EntityType<?>, EntityType<io.github.sweetcornna.universe.mecha.fleet.FleetUnit>> fleet(String name,
            io.github.sweetcornna.universe.core.mecha.fleet.FleetUnitKind kind, float width, float height, int tracking) {
        return ENTITIES.registerEntityType(name, (type, level) -> new io.github.sweetcornna.universe.mecha.fleet.FleetUnit(type, level, kind),
                MobCategory.MISC, builder -> builder.sized(width, height).eyeHeight(height * 0.5F).clientTrackingRange(tracking).updateInterval(1)
                        .noSave().noLootTable().fireImmune());
    }
    // M18 舰队和战斗（A）的实体加在这行上面
    // M18 物流调度（B）的实体加在这行上面
    // M18 统计和研究（C）的实体加在这行上面
    // M18 信标、透镜、矿脉、蓝图（D）的实体加在这行上面

    private UniverseEntities() {
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        modBus.addListener(EntityAttributeCreationEvent.class, event -> {
            event.put(ALIEN_CREATURE.get(), AlienCreature.createAttributes().build());
            event.put(ALIEN_FLYER.get(), AlienFlyer.createAttributes().build());
            event.put(AQUATIC_CREATURE.get(), AquaticCreature.createAttributes().build());
            event.put(SENTINEL.get(), Sentinel.createAttributes(false).build());
            event.put(HEAVY_SENTINEL.get(), Sentinel.createAttributes(true).build());
            event.put(REPAIR_SENTINEL.get(), RepairSentinel.createAttributes().build());
            event.put(SUMMONER_SENTINEL.get(), SummonerSentinel.createAttributes().build());
            event.put(QUADRUPED_SENTINEL.get(), QuadrupedSentinel.createAttributes().build());
            event.put(WALKER_SENTINEL.get(), WalkerSentinel.createAttributes().build());
            event.put(ALIEN_NPC.get(), AlienNpc.createAttributes().build());
            event.put(DARK_FOG_BUILDING.get(), DarkFogBuilding.createAttributes().build());
            event.put(DARK_FOG_RAIDER.get(), DarkFogRaider.createAttributes().build());
            event.put(DARK_FOG_RANGER.get(), DarkFogRanger.createAttributes().build());
            event.put(DARK_FOG_GUARDIAN.get(), DarkFogGuardian.createAttributes().build());
        });
    }
}
