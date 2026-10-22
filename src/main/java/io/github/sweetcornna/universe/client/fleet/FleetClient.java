package io.github.sweetcornna.universe.client.fleet;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import io.github.sweetcornna.universe.network.FleetPayloads;
import io.github.sweetcornna.universe.world.freighter.FreighterDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** 货船的客户端部分（ADR-0024）：船舱里照着货船停的地方画太空，货船控制台和舰队终端的界面。 */
public final class FleetClient {
    /** 货船编号 → 停在哪。 */
    private static final Map<Integer, FleetPayloads.Location> LOCATIONS = new HashMap<>();

    private FleetClient() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> {
            event.register(FleetPayloads.Location.TYPE, (payload, context) -> context.enqueueWork(() -> LOCATIONS.put(payload.freighter(), payload)));
            event.register(FleetPayloads.Console.TYPE, (payload, context) -> context.enqueueWork(() -> {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.gui.screen() instanceof FreighterConsoleScreen screen) {
                    screen.update(payload);
                } else if (payload.open()) {
                    minecraft.gui.setScreen(new FreighterConsoleScreen(payload));
                }
            }));
            event.register(FleetPayloads.Terminal.TYPE, (payload, context) -> context.enqueueWork(() -> {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.gui.screen() instanceof FleetTerminalScreen screen) {
                    screen.update(payload);
                } else if (payload.open()) {
                    minecraft.gui.setScreen(new FleetTerminalScreen(payload));
                }
            }));
        });
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> LOCATIONS.clear());
    }

    /** 这个维度是货船的船舱时，货船停在哪。 */
    public static Optional<FleetPayloads.Location> location(ResourceKey<Level> dimension) {
        OptionalInt id = FreighterDimensions.id(dimension);
        return id.isPresent() ? Optional.ofNullable(LOCATIONS.get(id.getAsInt())) : Optional.empty();
    }
}
