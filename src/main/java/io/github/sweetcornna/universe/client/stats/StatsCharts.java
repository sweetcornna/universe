package io.github.sweetcornna.universe.client.stats;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

import io.github.sweetcornna.universe.client.ClientGalaxy;
import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.client.galaxy.GalaxyText;
import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.dyson.Dyson;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** 统计面板和仪表盘共用的画法和叫法：曲线、环形图、数的写法、维度和星系的名字。 */
final class StatsCharts {
    static final int PRODUCED = 0xFF5BD47A;
    static final int CONSUMED = 0xFFE89A3C;
    static final int CHARGING = 0xFF4FC3E0;
    static final int SPARE = 0xFF2C5A3A;
    static final int TRACK = 0xFF1A2A33;
    /** 星系的统计范围写成 {@code system:<序号>}，和服务端一样。 */
    static final String SYSTEM_PREFIX = "system:";

    private StatsCharts() {
    }

    // ---- 画

    /**
     * 两条阶梯折线，按两条里最大的数归一。第一条线下面淡淡地铺一层，好分出哪条是哪条。
     *
     * @return 归一用的最大值
     */
    static float lines(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float[] a, float[] b, int colorA, int colorB) {
        graphics.fill(x, y, x + width, y + height, MachineScreen.INSET);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF2A4A58);
        int n = Math.max(a.length, b.length);
        float max = 0;
        for (float v : a) {
            max = Math.max(max, v);
        }
        for (float v : b) {
            max = Math.max(max, v);
        }
        if (n == 0 || max <= 0) {
            return 0;
        }
        line(graphics, x, y, width, height, a, n, max, colorA, true);
        line(graphics, x, y, width, height, b, n, max, colorB, false);
        return max;
    }

    private static void line(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float[] values, int n, float max, int color,
            boolean area) {
        int prev = -1;
        for (int i = 0; i < values.length; i++) {
            int x0 = x + i * width / n;
            int x1 = x + (i + 1) * width / n;
            int py = y + height - 2 - Math.round((height - 3) * Math.min(1, values[i] / max));
            if (area) {
                graphics.fill(x0, py + 1, x1, y + height - 1, 0x30000000 | color & 0xFFFFFF);
            }
            graphics.fill(x0, py, x1, py + 1, color);
            if (prev >= 0 && prev != py) {
                graphics.fill(x0, Math.min(prev, py), x0 + 1, Math.max(prev, py) + 1, color);
            }
            prev = py;
        }
    }

    /**
     * 环形图：从正上方顺时针，一段一段按 values 占 total 的比例上色，剩下的画成 rest。同一行同一种颜色的连续像素并成一块画。
     */
    static void ring(GuiGraphicsExtractor graphics, int cx, int cy, int outer, int inner, double[] values, int[] colors, double total, int rest) {
        double[] ends = new double[values.length];
        double sum = 0;
        for (int i = 0; i < values.length; i++) {
            sum += total > 0 ? Math.max(0, values[i]) / total : 0;
            ends[i] = Math.min(1, sum);
        }
        double outer2 = (outer + 0.5) * (outer + 0.5);
        double inner2 = (inner - 0.5) * (inner - 0.5);
        for (int dy = -outer; dy <= outer; dy++) {
            int runStart = 0;
            int runColor = 0;
            boolean inRun = false;
            for (int dx = -outer; dx <= outer + 1; dx++) {
                double d2 = (double) dx * dx + (double) dy * dy;
                int color = 0;
                boolean on = dx <= outer && d2 <= outer2 && d2 >= inner2;
                if (on) {
                    double angle = Math.atan2(dx + 0.5, -(dy + 0.5));
                    double fraction = (angle < 0 ? angle + 2 * Math.PI : angle) / (2 * Math.PI);
                    color = rest;
                    for (int i = 0; i < ends.length; i++) {
                        if (fraction < ends[i]) {
                            color = colors[i];
                            break;
                        }
                    }
                }
                if (inRun && (!on || color != runColor)) {
                    graphics.fill(cx + runStart, cy + dy, cx + dx, cy + dy + 1, runColor);
                    inRun = false;
                }
                if (on && !inRun) {
                    inRun = true;
                    runStart = dx;
                    runColor = color;
                }
            }
        }
    }

    /** 一根进度条。 */
    static void bar(GuiGraphicsExtractor graphics, int x, int y, int width, int height, double fraction, int color) {
        graphics.fill(x, y, x + width, y + height, TRACK);
        graphics.fill(x, y, x + (int) Math.round(width * Math.max(0, Math.min(1, fraction))), y + height, color);
    }

    // ---- 数

    /** 每分钟的数：小的留一位小数，大的用 k、M。 */
    static String rate(double value) {
        double size = Math.abs(value);
        if (size < 0.05) {
            return "0";
        }
        if (size < 10) {
            return String.format(Locale.ROOT, "%.1f", value);
        }
        if (size < 10_000) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        if (size < 10_000_000) {
            return String.format(Locale.ROOT, "%.1fk", value / 1000);
        }
        return String.format(Locale.ROOT, "%.1fM", value / 1_000_000);
    }

    /** 每刻多少焦耳写成瓦。 */
    static String watts(double joulesPerTick) {
        return MachineScreen.watts(Math.round(joulesPerTick * 10));
    }

    static String percent(double fraction) {
        return Math.round(fraction * 100) + "%";
    }

    // ---- 名字

    static Item item(String id) {
        Identifier key = Identifier.tryParse(id);
        return key == null ? Items.BARRIER : BuiltInRegistries.ITEM.getOptional(key).orElse(Items.BARRIER);
    }

    static Component itemName(String id) {
        return Component.translatable(item(id).getDescriptionId());
    }

    /** 设备种类（方块 ID）的方块。 */
    static Block block(String id) {
        Identifier key = Identifier.tryParse(id);
        return key == null ? net.minecraft.world.level.block.Blocks.AIR
                : BuiltInRegistries.BLOCK.getOptional(key).orElse(net.minecraft.world.level.block.Blocks.AIR);
    }

    /** 维度所在的星系。 */
    static OptionalInt systemOf(String dimension) {
        Identifier id = Identifier.tryParse(dimension);
        return id == null ? OptionalInt.empty() : Dyson.systemOf(ResourceKey.create(Registries.DIMENSION, id));
    }

    static OptionalInt parseSystem(String scope) {
        if (!scope.startsWith(SYSTEM_PREFIX)) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(scope.substring(SYSTEM_PREFIX.length())));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    static Component systemName(int system) {
        return ClientGalaxy.galaxy().flatMap(galaxy -> galaxy.system(system)).<Component>map(star -> Component.literal(GalaxyText.system(star)))
                .orElse(Component.literal("#" + system));
    }

    /** 统计范围的名字：全部、星系、星球（含主世界的母星）、"某某星系 太空"，别的维度用它的 id。 */
    static Component scopeName(String scope) {
        if (scope.isEmpty()) {
            return Component.translatable("gui.universe.stats.all");
        }
        OptionalInt system = parseSystem(scope);
        if (system.isPresent()) {
            return Component.translatable("gui.universe.stats.system", systemName(system.getAsInt()));
        }
        return dimensionName(scope);
    }

    static Component dimensionName(String dimension) {
        if (dimension.isEmpty()) {
            return Component.translatable("gui.universe.stats.all");
        }
        Identifier id = Identifier.tryParse(dimension);
        Optional<Galaxy> galaxy = ClientGalaxy.galaxy();
        if (id == null || galaxy.isEmpty()) {
            return Component.literal(dimension);
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
        OptionalInt space = SpaceDimensions.system(key);
        if (space.isPresent()) {
            return galaxy.get().system(space.getAsInt())
                    .<Component>map(system -> Component.translatable("gui.universe.stats.space", GalaxyText.system(system)))
                    .orElse(Component.literal(dimension));
        }
        Optional<BodyId> body = Planets.bodyId(key);
        if (body.isPresent()) {
            return galaxy.get().body(body.get()).<Component>map(planet -> Component.literal(GalaxyText.planet(planet)))
                    .orElse(Component.literal(dimension));
        }
        return Component.literal(id.getPath());
    }
}
