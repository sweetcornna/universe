package io.github.sweetcornna.universe.microbe;

import net.neoforged.bus.api.IEventBus;

/** 微生物和基因（ADR-0036 §五）在服务端要登记的事件：登录时发基因库。 */
public final class Microbes {
    private Microbes() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        GeneLibraries.registerEvents(gameBus);
    }
}
