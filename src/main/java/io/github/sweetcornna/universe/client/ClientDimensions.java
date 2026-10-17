package io.github.sweetcornna.universe.client;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.network.DimensionListPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionTransitionScreenEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端这一侧的运行时维度：同步维度清单，并给运行时维度挂上过场画面。
 *
 * <p>过场画面是 S1 的验证项。{@link RegisterDimensionTransitionScreenEvent} 只在客户端启动时触发一次，
 * 按维度键精确匹配，而运行时维度的键要进了世界才知道。26.3 的事件把注册直接写进
 * {@code DimensionTransitionScreenManager} 的静态表，所以这里留着事件对象，知道新维度时再补注册。
 * 这依赖 NeoForge 的内部实现，结论和长期方案见 ADR-0004。
 */
public final class ClientDimensions {
    private static @Nullable RegisterDimensionTransitionScreenEvent transitionScreens;

    private ClientDimensions() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class,
                event -> event.register(DimensionListPayload.TYPE, ClientDimensions::handle));
        modBus.addListener(RegisterDimensionTransitionScreenEvent.class, event -> transitionScreens = event);
        // 登录包里的维度清单已经包含当时加载着的运行时维度
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingIn.class,
                event -> event.getPlayer().connection.levels().forEach(ClientDimensions::registerTransitionScreen));
    }

    private static void handle(DimensionListPayload payload, IPayloadContext context) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return;
        }
        Set<ResourceKey<Level>> levels = connection.levels();
        levels.removeAll(payload.removed());
        levels.addAll(payload.added());
        payload.added().forEach(ClientDimensions::registerTransitionScreen);
        Universe.LOGGER.debug("Client dimension list: +{} -{}, {} dimensions known",
                payload.added(), payload.removed(), levels.size());
    }

    private static void registerTransitionScreen(ResourceKey<Level> dimension) {
        if (transitionScreens != null && dimension.identifier().getNamespace().equals(Universe.MOD_ID)) {
            // 暂时借用末地传送门的背景，进入运行时维度时能直接看出补注册是否生效；正式的降落画面留给 S4
            transitionScreens.registerIncomingEffect(dimension, (tracker, reason) -> {
                Universe.LOGGER.debug("Runtime transition screen for {}", dimension.identifier());
                return new LevelLoadingScreen(tracker, LevelLoadingScreen.Reason.END_PORTAL);
            });
        }
    }
}
