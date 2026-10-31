package io.github.sweetcornna.universe.darkfog.space;

import io.github.sweetcornna.universe.perf.ServerPerf;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 太空单位和枪骑轰炸（ADR-0028 §八）的事件注册。 */
public final class SpaceUnits {
    private SpaceUnits() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(EntityAttributeCreationEvent.class, event -> {
            event.put(UniverseEntities.DARK_FOG_LANCER.get(), DarkFogLancer.createAttributes().build());
            event.put(UniverseEntities.DARK_FOG_HUMPBACK.get(), DarkFogHumpback.createAttributes().build());
        });
        gameBus.addListener(ServerTickEvent.Post.class, event -> ServerPerf.time("darkfog/lancers", () -> LancerRaids.tick(event.getServer())));
        gameBus.addListener(ServerStoppedEvent.class, event -> LancerRaids.clear());
        gameBus.addListener(RegisterCommandsEvent.class, event -> LancerCommand.register(event.getDispatcher()));
    }
}
