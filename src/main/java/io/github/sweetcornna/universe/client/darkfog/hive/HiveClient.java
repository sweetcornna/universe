package io.github.sweetcornna.universe.client.darkfog.hive;

import io.github.sweetcornna.universe.registry.UniverseEntities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** 太空巢穴建筑和它们打出来的东西的客户端部分（ADR-0028 §八）。 */
public final class HiveClient {
    private HiveClient() {
    }

    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerEntityRenderer(UniverseEntities.HIVE_BUILDING.get(), HiveBuildingRenderer::new);
            event.registerEntityRenderer(UniverseEntities.HIVE_PLASMA_SHELL.get(), HiveShellRenderer::new);
        });
    }
}
