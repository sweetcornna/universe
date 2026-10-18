package io.github.sweetcornna.universe.client;

import org.joml.Matrix3x2fStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * HUD 上指向世界里某个位置的标记：在视野里画一个跳动的菱形和文字；在视野外时在屏幕边上画一个指过去的箭头。
 * 兴趣点的航点（ADR-0014）、空间站（ADR-0017）都用它。
 */
public final class HudMarker {
    /** 目标在屏幕外时，箭头离屏幕边多远。 */
    private static final int EDGE = 18;

    private HudMarker() {
    }

    /**
     * @param rgb   颜色（不带透明度）
     * @param pulse 0–1 的亮度，调用方按时间算好让它跳动
     */
    public static void draw(GuiGraphicsExtractor graphics, Minecraft minecraft, Vec3 target, int rgb, Component label, float pulse) {
        ScreenProjection projection = ScreenProjection.of(minecraft, graphics.guiWidth(), graphics.guiHeight());
        Font font = minecraft.font;
        int alpha = (int) (255 * pulse);
        int color = (alpha << 24) | rgb;
        int[] point = projection.project(target);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        if (point != null && point[0] >= EDGE && point[0] <= width - EDGE && point[1] >= EDGE && point[1] <= height - EDGE) {
            MultitoolClient.diamond(graphics, point[0], point[1], color, 0xA0000000);
            MultitoolClient.diamond(graphics, point[0], point[1] - 2, color & 0x60FFFFFF, 0);
            int w = font.width(label);
            graphics.text(font, label, point[0] - w / 2, point[1] + 8, 0xFF000000 | rgb, true);
            return;
        }
        int[] edge = projection.edge(target, EDGE);
        double angle = Math.atan2(edge[1] - height / 2.0, edge[0] - width / 2.0);
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(edge[0], edge[1]);
        pose.rotate((float) angle);
        // 指向外面的三角形
        for (int x = -5; x <= 5; x++) {
            int h = (5 - x) / 2;
            graphics.fill(x - 1, -h - 1, x + 1, h + 2, 0xA0000000);
        }
        for (int x = -4; x <= 4; x++) {
            int h = (4 - x) / 2;
            graphics.fill(x, -h, x + 1, h + 1, color);
        }
        pose.popMatrix();
        int w = font.width(label);
        int tx = Math.clamp(edge[0] - w / 2, 4, width - w - 4);
        int ty = edge[1] < height / 2 ? edge[1] + 10 : edge[1] - 18;
        graphics.text(font, label, tx, ty, 0xFF000000 | rgb, true);
    }
}
