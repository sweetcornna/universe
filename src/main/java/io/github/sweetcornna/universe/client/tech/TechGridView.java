package io.github.sweetcornna.universe.client.tech;

import java.util.List;
import java.util.Locale;

import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.core.tech.TechGrid;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.Slot;

/**
 * 技术格网格的画法（ADR-0034 §五），外骨骼、多功能工具、飞船驾驶台的界面共用：超频格一圈慢慢明暗的橙光，
 * 挨着的同种模块之间在两格的边框上搭一道连线；悬停写出这一格的加成。都画在背景层，格子里的物品盖在上面。
 */
public final class TechGridView {
    /** 超频格的环。 */
    private static final int RING = 0xFFFF9A2E;
    /** 还没解锁的超频格：环暗一些，不闪。 */
    private static final int RING_LOCKED = 0xFF7A4A1E;
    private static final int GLOW = 0xFF8C1A;
    /** 连线：中间亮，两头伸进格子里的一点暗一些。 */
    private static final int LINK = 0xFFB8F6FF;
    private static final int LINK_EDGE = 0xFF4FA9BF;
    private static final int SPACING = 18;

    private TechGridView() {
    }

    /**
     * 一个超频格。{@code x}、{@code y} 是格子内部的左上角（和 {@link Slot#x} 一样）。
     *
     * @param live 解锁了、能用的格子；没解锁的画暗环
     */
    public static void supercharged(GuiGraphicsExtractor graphics, int x, int y, boolean live) {
        if (live) {
            float pulse = 0.5F + 0.5F * Mth.sin((Util.getMillis() % 100000L) / 420.0F);
            int glow = (int) (0x38 + 0x48 * pulse);
            // 光晕压在相邻格子的边框上，只往外一像素
            graphics.fill(x - 2, y - 2, x + 18, y + 18, glow << 24 | GLOW);
        }
        int ring = live ? RING : RING_LOCKED;
        graphics.fill(x - 1, y - 1, x + 17, y, ring);
        graphics.fill(x - 1, y + 16, x + 17, y + 17, ring);
        graphics.fill(x - 1, y, x, y + 16, ring);
        graphics.fill(x + 16, y, x + 17, y + 16, ring);
        if (live) {
            // 四个角各一点亮色
            int spark = 0xFFFFD9A0;
            graphics.fill(x - 1, y - 1, x + 1, y, spark);
            graphics.fill(x + 15, y + 16, x + 17, y + 17, spark);
        }
    }

    /**
     * 两格之间的连线：画在两格相邻那条边框上，中间亮，两头各伸进格子一像素。
     *
     * @param a 序号小的那一格（左边或上面）
     * @param b 序号大的那一格
     */
    public static void link(GuiGraphicsExtractor graphics, int left, int top, Slot a, Slot b) {
        int ax = left + a.x;
        int ay = top + a.y;
        if (b.y == a.y && b.x - a.x == SPACING) {
            graphics.fill(ax + 15, ay + 6, ax + 19, ay + 10, LINK_EDGE);
            graphics.fill(ax + 16, ay + 4, ax + 18, ay + 12, LINK);
        } else if (b.x == a.x && b.y - a.y == SPACING) {
            graphics.fill(ax + 6, ay + 15, ax + 10, ay + 19, LINK_EDGE);
            graphics.fill(ax + 4, ay + 16, ax + 12, ay + 18, LINK);
        }
    }

    /** 画一边网格的超频格和连线。{@code slots} 是这一边的格子，按网格序号。 */
    public static void grid(GuiGraphicsExtractor graphics, int left, int top, TechGrid grid, List<? extends Slot> slots, List<ModuleType> types,
            int supercharged, boolean[] live) {
        for (int i = 0; i < Math.min(grid.size(), slots.size()); i++) {
            if (TechGrid.supercharged(supercharged, i)) {
                Slot slot = slots.get(i);
                supercharged(graphics, left + slot.x, top + slot.y, live[i]);
            }
        }
        for (int[] pair : grid.links(types)) {
            link(graphics, left, top, slots.get(pair[0]), slots.get(pair[1]));
        }
    }

    /** 悬停时的一行："相邻 +10%，超频 ×1.25"。没有加成时为 {@code null}。 */
    public static Component boostLine(TechGrid.Boost boost) {
        if (!boost.any()) {
            return null;
        }
        String adjacent = String.valueOf(Math.round(boost.adjacency() * 100));
        String supercharge = String.format(Locale.ROOT, "%.2f", TechGrid.SUPERCHARGE);
        Component line;
        if (boost.links() > 0 && boost.supercharged()) {
            line = Component.translatable("gui.universe.tech.boost.both", adjacent, supercharge);
        } else if (boost.links() > 0) {
            line = Component.translatable("gui.universe.tech.boost.adjacent", adjacent);
        } else {
            line = Component.translatable("gui.universe.tech.boost.supercharged", supercharge);
        }
        return line.copy().withColor(boost.supercharged() ? RING : LINK);
    }

    /** 悬停在空的超频格上。 */
    public static Component emptySupercharged(boolean live) {
        return Component.translatable(live ? "gui.universe.tech.supercharged_slot" : "gui.universe.tech.supercharged_locked",
                String.format(Locale.ROOT, "%.2f", TechGrid.SUPERCHARGE));
    }
}
