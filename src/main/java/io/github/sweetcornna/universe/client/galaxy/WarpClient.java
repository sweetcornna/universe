package io.github.sweetcornna.universe.client.galaxy;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.ClientGalaxy;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.network.WarpPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 跃迁的画面（ADR-0012）：充能时星光从画面中心拉成越来越长的线、四周泛紫、视野变宽，最后一道白光；
 * 到了以后白光退去、星光收回。只给船上的玩家看，由服务端发 {@link WarpPayload} 触发。
 */
public final class WarpClient {
    private static final int STREAKS = 220;
    private static WarpPayload.@Nullable Stage stage;
    private static long started;
    private static long duration = 1;
    private static int target = -1;

    private WarpClient() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(WarpPayload.TYPE, WarpClient::handle));
        modBus.addListener(RegisterGuiLayersEvent.class, event -> event.registerAboveAll(Universe.id("warp"), WarpClient::render));
        gameBus.addListener(ViewportEvent.ComputeFov.class, WarpClient::fov);
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> stage = null);
    }

    private static void handle(WarpPayload payload, IPayloadContext context) {
        if (payload.stage() == WarpPayload.Stage.CANCEL) {
            stage = null;
            return;
        }
        stage = payload.stage();
        started = Util.getMillis();
        duration = Math.max(1, payload.ticks() * 50L);
        target = payload.system();
    }

    /** 当前阶段进行到几成；没在跃迁或者已经结束时为负。 */
    private static float progress() {
        if (stage == null) {
            return -1;
        }
        float t = (Util.getMillis() - started) / (float) duration;
        if (t >= 1 && stage == WarpPayload.Stage.ARRIVE) {
            stage = null;
            return -1;
        }
        return Math.min(t, 1);
    }

    /** 星光拉伸的强度：充能时 0 → 1，到达后 1 → 0。 */
    private static float intensity() {
        float t = progress();
        if (t < 0) {
            return 0;
        }
        return stage == WarpPayload.Stage.SPOOL ? t * t : (1 - t) * (1 - t) * (1 - t);
    }

    private static void fov(ViewportEvent.ComputeFov event) {
        float k = intensity();
        if (k > 0) {
            event.setFOV(event.getFOV() * (1 + 0.35F * k));
        }
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        float t = progress();
        if (t < 0) {
            return;
        }
        float k = intensity();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        double cx = width / 2.0;
        double cy = height / 2.0;
        double reach = Math.hypot(cx, cy);
        double seconds = (Util.getMillis() - started) / 1000.0;

        // 四周泛紫
        int edge = ARGB.color((int) (110 * k), 0x5A2A9A);
        graphics.fillGradient(0, 0, width, height / 3, edge, 0);
        graphics.fillGradient(0, height * 2 / 3, width, height, 0, edge);

        // 星光：每条线有固定的方向，沿半径往外跑，越接近跃迁越长、越亮
        int count = (int) (40 + (STREAKS - 40) * k);
        for (int i = 0; i < count; i++) {
            double angle = hash(i * 3 + 1) * Math.PI * 2;
            double speed = 0.25 + 1.6 * k + hash(i * 3 + 2) * 0.5;
            double phase = (hash(i * 3) + seconds * speed) % 1.0;
            double r0 = (0.06 + phase * phase) * reach;
            double length = (0.01 + 0.45 * k * k) * reach * (0.3 + phase);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            int alpha = (int) (255 * Math.min(1, k * 1.4) * Math.min(1, phase * 3));
            int color = hash(i * 7) < 0.25 ? ARGB.color(alpha, 0xC9A6FF) : ARGB.color(alpha, 0xEAF2FF);
            StarMapScreen.line(graphics, cx + cos * r0, cy + sin * r0, cx + cos * (r0 + length), cy + sin * (r0 + length),
                    0.6F + 0.8F * k, color);
        }

        // 充能的最后一段白光越来越亮；到达时从全白退回去
        float flash = stage == WarpPayload.Stage.SPOOL ? Mth.clamp((t - 0.82F) / 0.18F, 0, 1) : Mth.clamp(1 - t / 0.35F, 0, 1);
        if (flash > 0) {
            graphics.fill(0, 0, width, height, ARGB.color((int) (235 * flash * flash), 0xFFFFFF));
        }

        if (stage == WarpPayload.Stage.SPOOL) {
            StarSystem system = ClientGalaxy.galaxy().flatMap(galaxy -> galaxy.system(target)).orElse(null);
            Component text = system == null ? Component.translatable("hud.universe.warp.spooling_unknown")
                    : Component.translatable("hud.universe.warp.spooling", GalaxyText.system(system));
            Minecraft minecraft = Minecraft.getInstance();
            graphics.centeredText(minecraft.font, text, width / 2, height / 4, ARGB.color((int) (255 * Math.min(1, t * 4)), 0xD8C4FF));
        }
    }

    /** 0..1 的伪随机数，同一个序号永远一样。 */
    private static double hash(int i) {
        long h = i * 0x9E3779B97F4A7C15L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
    }
}
