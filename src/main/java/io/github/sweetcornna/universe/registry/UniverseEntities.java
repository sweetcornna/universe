package io.github.sweetcornna.universe.registry;

import io.github.sweetcornna.universe.pirate.PirateFighter;
import io.github.sweetcornna.universe.sentinel.Sentinel;
import io.github.sweetcornna.universe.entity.PlasmaBolt;
import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.entity.AlienCreature;
import io.github.sweetcornna.universe.entity.AlienFlyer;
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

    /** 守卫机（ADR-0014）：不存档，由 {@code Sentinels} 按玩家附近的情况补充。 */
    public static final DeferredHolder<EntityType<?>, EntityType<Sentinel>> SENTINEL = ENTITIES.registerEntityType(
            "sentinel", Sentinel::new, MobCategory.MISC,
            builder -> builder.sized(0.8F, 0.75F).eyeHeight(0.45F).clientTrackingRange(10).noSave().fireImmune());
    public static final DeferredHolder<EntityType<?>, EntityType<Sentinel>> HEAVY_SENTINEL = ENTITIES.registerEntityType(
            "heavy_sentinel", Sentinel::new, MobCategory.MISC,
            builder -> builder.sized(1.4F, 1.1F).eyeHeight(0.55F).clientTrackingRange(10).noSave().fireImmune());

    /** 海盗战机（ADR-0017）：由 {@code Pirates} 按袭击生成，不存档；打得远、飞得快，每刻同步位置。 */
    public static final DeferredHolder<EntityType<?>, EntityType<PirateFighter>> PIRATE_FIGHTER = ENTITIES.registerEntityType(
            "pirate_fighter", PirateFighter::new, MobCategory.MISC,
            builder -> builder.sized(2.6F, 1.2F).clientTrackingRange(16).updateInterval(1).noSave().fireImmune());

    private UniverseEntities() {
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        modBus.addListener(EntityAttributeCreationEvent.class, event -> {
            event.put(ALIEN_CREATURE.get(), AlienCreature.createAttributes().build());
            event.put(ALIEN_FLYER.get(), AlienFlyer.createAttributes().build());
            event.put(SENTINEL.get(), Sentinel.createAttributes(false).build());
            event.put(HEAVY_SENTINEL.get(), Sentinel.createAttributes(true).build());
        });
    }
}
