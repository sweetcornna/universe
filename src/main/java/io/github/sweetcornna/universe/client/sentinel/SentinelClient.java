package io.github.sweetcornna.universe.client.sentinel;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.sentinel.Wanted;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.sentinel.SentinelHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * 守卫机的 HUD（ADR-0014），画在屏幕顶部正中：
 * 被通缉时是"守卫机通缉"和三颗菱形（亮几颗就是几级），下面一行是"被发现"或者脱离倒计时和进度条；
 * 没被通缉、但警戒值不为零时，只画一条细细的警戒条。
 */
public final class SentinelClient {
    private static final int RED = 0xFFFF4A3A;
    private static final int DIM = 0xFF4A2A28;

    private SentinelClient() {
    }

    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(RegisterGuiLayersEvent.class,
                event -> event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, Universe.id("sentinel_alert"), SentinelClient::render));
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.gui.hud.isHidden() || player.isDeadOrDying()) {
            return;
        }
        SentinelHud hud = player.getData(UniverseAttachments.SENTINEL_HUD);
        if (hud.planet() == 0) {
            return;
        }
        Font font = minecraft.font;
        int cx = graphics.guiWidth() / 2;
        int y = 4;
        float time = minecraft.level.getGameTime() + delta.getGameTimeDeltaPartialTick(false);
        if (hud.level() > 0) {
            Component label = Component.translatable("hud.universe.sentinel.wanted");
            int stars = Wanted.MAX_LEVEL * 12;
            int width = font.width(label) + 6 + stars;
            int x = cx - width / 2;
            graphics.fill(x - 4, y - 2, x + width + 4, y + 24, 0x90000000);
            graphics.text(font, label, x, y + 1, RED, true);
            int sx = x + font.width(label) + 6 + 5;
            float pulse = hud.seen() ? 0.7F + 0.3F * Mth.sin(time * 0.5F) : 1;
            for (int i = 0; i < Wanted.MAX_LEVEL; i++) {
                int color = i < hud.level() ? scale(RED, pulse) : DIM;
                filledDiamond(graphics, sx + i * 12, y + 4, color);
            }
            int lineY = y + 13;
            if (hud.seen()) {
                Component seen = Component.translatable("hud.universe.sentinel.seen");
                graphics.text(font, seen, cx - font.width(seen) / 2, lineY, scale(0xFFFF8A7A, pulse), true);
            } else {
                int seconds = (int) Math.ceil((1 - hud.escape()) * Wanted.ESCAPE_TICKS / 20F);
                Component escaping = Component.translatable("hud.universe.sentinel.escaping", seconds);
                int barWidth = 40;
                int total = font.width(escaping) + 4 + barWidth;
                int tx = cx - total / 2;
                graphics.text(font, escaping, tx, lineY, 0xFF9AF0A8, true);
                int bx = tx + font.width(escaping) + 4;
                graphics.fill(bx - 1, lineY + 2, bx + barWidth + 1, lineY + 7, 0xC0000000);
                graphics.fill(bx, lineY + 3, bx + Math.round(barWidth * hud.escape()), lineY + 6, 0xFF6EE08A);
            }
            return;
        }
        if (hud.alert() <= 0) {
            return;
        }
        Component label = Component.translatable("hud.universe.sentinel.alert");
        int barWidth = 50;
        int total = font.width(label) + 4 + barWidth;
        int x = cx - total / 2;
        int color = hud.alert() > 0.7F ? 0xFFFF8A3A : 0xFFFFD34A;
        graphics.text(font, label, x, y, color, true);
        int bx = x + font.width(label) + 4;
        graphics.fill(bx - 1, y + 2, bx + barWidth + 1, y + 7, 0xC0000000);
        graphics.fill(bx, y + 3, bx + Math.round(barWidth * hud.alert()), y + 6, color);
    }

    /** 实心的菱形，中心在 (x, y)，半径 4。 */
    private static void filledDiamond(GuiGraphicsExtractor graphics, int x, int y, int color) {
        for (int i = -4; i <= 4; i++) {
            int w = 4 - Math.abs(i);
            graphics.fill(x - w - 1, y + i, x + w + 2, y + i + 1, 0xC0000000);
        }
        for (int i = -3; i <= 3; i++) {
            int w = 3 - Math.abs(i);
            graphics.fill(x - w, y + i, x + w + 1, y + i + 1, color);
        }
    }

    private static int scale(int argb, float f) {
        int r = (int) (((argb >> 16) & 0xFF) * f);
        int g = (int) (((argb >> 8) & 0xFF) * f);
        int b = (int) ((argb & 0xFF) * f);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}
