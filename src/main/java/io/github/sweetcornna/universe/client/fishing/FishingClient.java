package io.github.sweetcornna.universe.client.fishing;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.dev.DevScript;
import io.github.sweetcornna.universe.client.discovery.DiscoveryLogScreen;
import io.github.sweetcornna.universe.fishing.FishCaughtPayload;
import io.github.sweetcornna.universe.fishing.FishingBobber;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * 钓鱼的客户端部分（ADR-0034 §一）：浮标渲染器、饵格和捕鱼笼的界面、钓上来的卡片、HUD，和开发脚本的几个命令。
 *
 * <p>开发脚本：
 *
 * <ul>
 *   <li>{@code fishcast}：按一下右键（甩竿；咬钩时就是钩住）
 *   <li>{@code fishauto on|off}：咬钩时自动钩住，收线时看张力自己按、松右键
 *   <li>{@code fishreel on|off}：一直按着、松开右键
 *   <li>{@code fishlog}：打开发现日志的钓鱼页
 * </ul>
 */
public final class FishingClient {
    /** 自动收线时张力低于这个就按着，高于 {@link #AUTO_RELEASE} 就松开。 */
    private static final float AUTO_HOLD = 0.45F;
    private static final float AUTO_RELEASE = 0.7F;
    private static boolean auto;
    private static boolean reel;
    private static boolean autoHolding;

    private FishingClient() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                event -> event.registerEntityRenderer(UniverseEntities.FISHING_BOBBER.get(), FishingBobberRenderer::new));
        modBus.addListener(RegisterMenuScreensEvent.class, event -> {
            event.register(UniverseMenus.BAIT.get(), BaitScreen::new);
            event.register(UniverseMenus.FISH_TRAP.get(), FishTrapScreen::new);
        });
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(FishCaughtPayload.TYPE,
                (payload, context) -> context.enqueueWork(() -> FishingHud.showCatch(payload))));
        modBus.addListener(RegisterGuiLayersEvent.class,
                event -> event.registerBelow(VanillaGuiLayers.CROSSHAIR, Universe.id("fishing"), FishingHud::render));
        gameBus.addListener(ClientTickEvent.Post.class, event -> tick());
        DevScript.EXTRA_VERBS.put("fishcast", args -> click());
        DevScript.EXTRA_VERBS.put("fishauto", args -> {
            auto = args.length == 0 || !args[0].equals("off");
            if (!auto) {
                hold(false);
            }
        });
        DevScript.EXTRA_VERBS.put("fishreel", args -> {
            reel = args.length == 0 || !args[0].equals("off");
            hold(reel);
        });
        DevScript.EXTRA_VERBS.put("fishlog", args -> Minecraft.getInstance().gui.setScreen(DiscoveryLogScreen.fishing()));
    }

    private static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        FishingHud.tick(minecraft);
        if (reel) {
            hold(true);
        }
        if (!auto) {
            return;
        }
        FishingBobber bobber = FishingHud.bobber();
        if (bobber == null) {
            if (autoHolding) {
                hold(false);
            }
            return;
        }
        switch (bobber.phase()) {
            // 按下去就钩住，接着按着开始收线
            case BITE -> hold(true);
            case HOOKED -> {
                float tension = bobber.displayTension(1);
                if (bobber.dashing() || bobber.warning() || tension > AUTO_RELEASE) {
                    hold(false);
                } else if (tension < AUTO_HOLD) {
                    hold(true);
                }
            }
            default -> {
                if (autoHolding) {
                    hold(false);
                }
            }
        }
    }

    private static void hold(boolean down) {
        Minecraft.getInstance().options.keyUse.setDown(down);
        autoHolding = down;
    }

    private static void click() {
        KeyMapping.click(Minecraft.getInstance().options.keyUse.getKey());
    }
}
