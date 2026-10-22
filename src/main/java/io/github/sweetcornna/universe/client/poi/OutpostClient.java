package io.github.sweetcornna.universe.client.poi;

import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** 地上的地标在客户端的部分（ADR-0026）：制造设施终端的界面。 */
public final class OutpostClient {
    private OutpostClient() {
    }

    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(RegisterMenuScreensEvent.class, event -> event.register(UniverseMenus.FACILITY_TERMINAL.get(), FacilityTerminalScreen::new));
    }
}
