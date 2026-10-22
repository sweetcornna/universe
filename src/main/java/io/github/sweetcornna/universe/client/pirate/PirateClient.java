package io.github.sweetcornna.universe.client.pirate;

import java.util.List;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.HudMarker;
import io.github.sweetcornna.universe.client.ship.ShipClient;
import io.github.sweetcornna.universe.encounter.SentinelInterceptor;
import io.github.sweetcornna.universe.pirate.PirateFighter;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * 海盗的客户端部分（ADR-0017）：渲染器；太空里附近有海盗时，HUD 顶上写着还剩几架，每架海盗标一个红色的标记（在视野外时在屏幕边上指过去）；
 * 飞船锁定海盗时，锁定框下面画它的护盾和船体（这一架不再标标记，免得字叠在一起）。
 * 太空守卫机的拦截机（ADR-0024）也是这样，单独一行、标橙色。
 */
public final class PirateClient {
    private static final int COLOR = 0xFF4A5A;
    private static final int INTERCEPTOR_COLOR = 0xFFB02E;
    private static final int SHIELD_COLOR = 0xFF7FDBFF;
    private static final int HULL_COLOR = 0xFFE5533D;
    /** 这么近以内的海盗才标出来。 */
    private static final double RANGE = 320;

    private PirateClient() {
    }

    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerEntityRenderer(UniverseEntities.PIRATE_FIGHTER.get(), PirateRenderer::new);
            event.registerEntityRenderer(UniverseEntities.SENTINEL_INTERCEPTOR.get(),
                    context -> new PirateRenderer(context, "sentinel_interceptor", 0xFFFFC23A));
        });
        modBus.addListener(RegisterGuiLayersEvent.class,
                event -> event.registerBelow(VanillaGuiLayers.CROSSHAIR, Universe.id("pirates"), PirateClient::renderHud));
    }

    /** 附近没撤走的海盗战机。 */
    public static List<PirateFighter> nearby(LocalPlayer player) {
        AABB box = player.getBoundingBox().inflate(RANGE);
        return player.level().getEntitiesOfClass(PirateFighter.class, box, f -> f.isAlive() && !f.leaving());
    }

    private static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gui.hud.isHidden() || !SpaceDimensions.isSpace(player.level().dimension())) {
            return;
        }
        List<PirateFighter> pirates = nearby(player);
        if (pirates.isEmpty()) {
            return;
        }
        float time = player.tickCount + delta.getGameTimeDeltaPartialTick(false);
        float blink = 0.6F + 0.4F * Mth.sin(time * 0.3F);
        // 顶上的警报：还剩几架海盗、几架拦截机
        long interceptors = pirates.stream().filter(p -> p instanceof SentinelInterceptor).count();
        int y = 30;
        if (pirates.size() > interceptors) {
            banner(graphics, minecraft.font, Component.translatable("hud.universe.pirate.count", pirates.size() - interceptors), y, COLOR, blink);
            y += 16;
        }
        if (interceptors > 0) {
            banner(graphics, minecraft.font, Component.translatable("hud.universe.encounter.interceptors", interceptors), y, INTERCEPTOR_COLOR, blink);
        }
        Entity locked = ShipClient.lockedTarget();
        for (PirateFighter pirate : pirates) {
            if (pirate == locked) {
                continue;
            }
            int distance = (int) Math.round(pirate.distanceTo(player));
            boolean interceptor = pirate instanceof SentinelInterceptor;
            HudMarker.draw(graphics, minecraft, pirate.getBoundingBox().getCenter(), interceptor ? INTERCEPTOR_COLOR : COLOR,
                    Component.translatable(interceptor ? "hud.universe.encounter.interceptor_marker" : "hud.universe.pirate.marker", distance), 0.85F);
        }
    }

    private static void banner(GuiGraphicsExtractor graphics, Font font, Component text, int y, int color, float blink) {
        int w = font.width(text);
        int x = (graphics.guiWidth() - w) / 2;
        graphics.fill(x - 6, y - 3, x + w + 6, y + 11, 0x90000000);
        graphics.fill(x - 6, y - 3, x - 4, y + 11, 0xFF000000 | color);
        graphics.fill(x + w + 4, y - 3, x + w + 6, y + 11, 0xFF000000 | color);
        graphics.text(font, text, x, y, ((int) (255 * blink) << 24) | color, true);
    }

    /** 锁定框下面的护盾条和船体条（飞船的准星画完锁定框后调用）。 */
    public static void bars(GuiGraphicsExtractor graphics, int x, int y, PirateFighter pirate) {
        int width = 30;
        int left = x - width / 2;
        bar(graphics, left, y, width, pirate.shield() / (float) pirate.maxShield(), SHIELD_COLOR);
        bar(graphics, left, y + 3, width, pirate.hull() / (float) pirate.maxHull(), HULL_COLOR);
    }

    private static void bar(GuiGraphicsExtractor graphics, int x, int y, int width, float fraction, int color) {
        graphics.fill(x - 1, y - 1, x + width + 1, y + 2, 0xA0000000);
        int filled = Math.round(width * Mth.clamp(fraction, 0, 1));
        if (filled > 0) {
            graphics.fill(x, y, x + filled, y + 1, color);
        }
    }
}
