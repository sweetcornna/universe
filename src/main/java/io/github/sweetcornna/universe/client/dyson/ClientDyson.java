package io.github.sweetcornna.universe.client.dyson;

import java.util.Optional;
import java.util.OptionalInt;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.client.ClientGalaxy;
import io.github.sweetcornna.universe.client.UniverseKeys;
import io.github.sweetcornna.universe.core.dyson.DysonSphere;
import io.github.sweetcornna.universe.network.DysonSyncPayload;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 戴森球的客户端部分（ADR-0016）：收服务端发来的、玩家所在恒星系的戴森球，Y 键打开编辑器。天空、设备界面、
 * 编辑器都从这里读。
 */
public final class ClientDyson {
    private static @Nullable DysonSyncPayload payload;
    private static @Nullable DysonSphere sphere;
    /** 每收到一份加一，画面据此判断要不要重建缓存。 */
    private static long version;

    private ClientDyson() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(DysonSyncPayload.TYPE, ClientDyson::handle));
        modBus.addListener(RegisterMenuScreensEvent.class, event -> {
            event.register(UniverseMenus.EM_RAIL_EJECTOR.menuType(), DysonScreens.Ejector::new);
            event.register(UniverseMenus.VERTICAL_LAUNCHING_SILO.menuType(), DysonScreens.Silo::new);
            event.register(UniverseMenus.RAY_RECEIVER.menuType(), DysonScreens.Receiver::new);
        });
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerBlockEntityRenderer(UniverseBlockEntities.EM_RAIL_EJECTOR.get(), EjectorRenderer::new);
            event.registerBlockEntityRenderer(UniverseBlockEntities.VERTICAL_LAUNCHING_SILO.get(), SiloRenderer::new);
            event.registerBlockEntityRenderer(UniverseBlockEntities.RAY_RECEIVER.get(), ReceiverRenderer::new);
        });
        modBus.addListener(ModelEvent.RegisterStandalone.class, EjectorRenderer::registerModels);
        modBus.addListener(ModelEvent.RegisterStandalone.class, SiloRenderer::registerModels);
        modBus.addListener(ModelEvent.RegisterStandalone.class, ReceiverRenderer::registerModels);
        DysonSkyRenderer.registerEvents(modBus, gameBus);
        gameBus.addListener(ClientTickEvent.Post.class, event -> {
            Minecraft minecraft = Minecraft.getInstance();
            while (UniverseKeys.DYSON_EDITOR.consumeClick()) {
                if (minecraft.gui.screen() == null && minecraft.player != null) {
                    openEditor();
                }
            }
        });
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            payload = null;
            sphere = null;
            version++;
        });
    }

    public static void openEditor() {
        Minecraft.getInstance().gui.setScreen(new DysonEditorScreen());
    }

    private static void handle(DysonSyncPayload received, IPayloadContext context) {
        payload = received;
        sphere = received.toSphere();
        version++;
    }

    public static long version() {
        return version;
    }

    /** 玩家所在恒星系的序号；在下界、末地这些地方是空。 */
    public static OptionalInt currentSystem() {
        return ClientGalaxy.currentSystem();
    }

    /** 恒星系 system 的戴森球：只有玩家所在的恒星系有。 */
    public static Optional<DysonSphere> sphere(int system) {
        DysonSyncPayload current = payload;
        return current != null && current.system() == system ? Optional.ofNullable(sphere) : Optional.empty();
    }

    /** 最近一份同步的附带信息（纬度限制、接收站取走的电、在飞的火箭）。 */
    public static Optional<DysonSyncPayload> payload() {
        return Optional.ofNullable(payload);
    }
}
