package io.github.sweetcornna.universe.client.terraform;

import io.github.sweetcornna.universe.registry.UniverseMenus;
import io.github.sweetcornna.universe.terraform.TerraformPayloads;
import io.github.sweetcornna.universe.terraform.Terraforming;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * 星球改造在客户端的部分（ADR-0036 §六）：收服务端发来的各星球改造状态，交给 {@link Terraforming} 记着，天空、天气、
 * 危害提示这些读现用星球的地方就跟着变；再登记大气释放塔、轨道反射镜的界面。
 */
public final class TerraformClient {
    private TerraformClient() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(TerraformPayloads.Sync.TYPE,
                (payload, context) -> context.enqueueWork(() -> Terraforming.receive(payload))));
        modBus.addListener(RegisterMenuScreensEvent.class, event -> {
            event.register(UniverseMenus.ATMOSPHERE_RELEASE_TOWER.menuType(), ReleaseTowerScreen::new);
            event.register(UniverseMenus.ORBITAL_MIRROR.menuType(), OrbitalMirrorScreen::new);
            event.register(UniverseMenus.ECOLOGY_SEEDER.menuType(), EcologySeederScreen::new);
        });
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> Terraforming.clearClient());
    }
}
