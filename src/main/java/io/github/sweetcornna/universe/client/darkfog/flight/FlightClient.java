package io.github.sweetcornna.universe.client.darkfog.flight;

import io.github.sweetcornna.universe.registry.UniverseEntities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** 飞着的中继站和种子（ADR-0028 §八）的客户端注册。 */
public final class FlightClient {
    private FlightClient() {
    }

    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerEntityRenderer(UniverseEntities.DARK_FOG_FLYING_RELAY.get(), RelayCraftRenderer::new);
            event.registerEntityRenderer(UniverseEntities.DARK_FOG_SEED.get(), SeedRenderer::new);
        });
    }
}
