package io.github.sweetcornna.universe.registry;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.entity.AlienCreature;
import io.github.sweetcornna.universe.entity.AlienFlyer;
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

    private UniverseEntities() {
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        modBus.addListener(EntityAttributeCreationEvent.class, event -> {
            event.put(ALIEN_CREATURE.get(), AlienCreature.createAttributes().build());
            event.put(ALIEN_FLYER.get(), AlienFlyer.createAttributes().build());
        });
    }
}
