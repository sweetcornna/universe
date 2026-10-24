package io.github.sweetcornna.universe.core.stats;

import java.util.ArrayList;
import java.util.List;

/**
 * 仪表盘（ADR-0033 §四）：钉到屏幕上的统计图，最多 6 张。没拖动过的从屏幕右边往下叠，拖动过的记屏幕上的位置（按屏幕宽高的
 * 比例记，换了窗口大小也在差不多的地方），可以收成一行，可以关掉。存在玩家身上，每个存档各一份。
 *
 * <p>这里的方法都不改传进来的清单，返回新的清单。
 */
public final class Dashboard {
    public static final int MAX_PINS = 6;

    /** 能钉的图。按序号编码，新的只往后加。 */
    public enum Kind {
        /** 一种物品的产出和消耗。target 是物品 ID。 */
        ITEM,
        /** 电力。 */
        POWER,
        /** 研究进度。 */
        RESEARCH,
        /** 一个星系的戴森球发电。target 是星系序号。 */
        DYSON,
        /** 一座物流站的存货。target 是物流站的 UUID。 */
        STATION
    }

    /**
     * 一张钉住的图。
     *
     * @param scope 看哪儿：维度 ID，{@code system:<序号>} 是一个星系，空是全部（物品、电力用）
     * @param range 统计的时间范围（{@link ProductionStats.Range} 的序号）
     * @param x 左上角在屏幕上的横向位置（0–1），负数表示没拖动过，叠在右边
     */
    public record Pin(Kind kind, String target, String scope, int range, float x, float y, boolean collapsed) {
        public static Pin of(Kind kind, String target, String scope, int range) {
            return new Pin(kind, target, scope, range, -1, -1, false);
        }

        public boolean placed() {
            return this.x >= 0 && this.y >= 0;
        }

        /** 钉的是同一张图（位置、收起不算）。 */
        public boolean same(Pin other) {
            return this.kind == other.kind && this.target.equals(other.target) && this.scope.equals(other.scope);
        }
    }

    private Dashboard() {
    }

    /** 钉上一张；满了或者已经钉过同一张时不变。 */
    public static List<Pin> add(List<Pin> pins, Pin pin) {
        if (pins.size() >= MAX_PINS || contains(pins, pin)) {
            return pins;
        }
        List<Pin> next = new ArrayList<>(pins);
        next.add(pin);
        return List.copyOf(next);
    }

    public static boolean contains(List<Pin> pins, Pin pin) {
        for (Pin existing : pins) {
            if (existing.same(pin)) {
                return true;
            }
        }
        return false;
    }

    public static List<Pin> remove(List<Pin> pins, int index) {
        if (index < 0 || index >= pins.size()) {
            return pins;
        }
        List<Pin> next = new ArrayList<>(pins);
        next.remove(index);
        return List.copyOf(next);
    }

    /** 拖到屏幕上的某处（按比例，夹在 0–1 里）；传负数回到右边叠着。 */
    public static List<Pin> place(List<Pin> pins, int index, float x, float y) {
        if (index < 0 || index >= pins.size()) {
            return pins;
        }
        Pin pin = pins.get(index);
        boolean reset = x < 0 || y < 0 || Float.isNaN(x) || Float.isNaN(y);
        float nx = reset ? -1 : Math.min(1, x);
        float ny = reset ? -1 : Math.min(1, y);
        return set(pins, index, new Pin(pin.kind(), pin.target(), pin.scope(), pin.range(), nx, ny, pin.collapsed()));
    }

    /** 收起成一行，或者展开。 */
    public static List<Pin> toggle(List<Pin> pins, int index) {
        if (index < 0 || index >= pins.size()) {
            return pins;
        }
        Pin pin = pins.get(index);
        return set(pins, index, new Pin(pin.kind(), pin.target(), pin.scope(), pin.range(), pin.x(), pin.y(), !pin.collapsed()));
    }

    /** 读档时用：多出来的、重复的去掉。 */
    public static List<Pin> sanitize(List<Pin> pins) {
        List<Pin> next = new ArrayList<>();
        for (Pin pin : pins) {
            if (next.size() < MAX_PINS && !contains(next, pin)) {
                next.add(pin);
            }
        }
        return List.copyOf(next);
    }

    private static List<Pin> set(List<Pin> pins, int index, Pin pin) {
        List<Pin> next = new ArrayList<>(pins);
        next.set(index, pin);
        return List.copyOf(next);
    }

    /**
     * 排版：没拖动过的贴着屏幕右边从 top 往下叠，叠不下了就往左挪一列；拖动过的放在记下的位置，挪进屏幕里。
     *
     * @param heights 每张图现在的高度（收起的是一行的高度）
     * @return 每张图左上角的坐标，两个数一组
     */
    public static int[] layout(List<Pin> pins, int[] heights, int width, int screenWidth, int screenHeight, int top, int margin, int gap) {
        int[] out = new int[pins.size() * 2];
        int column = 0;
        int y = top;
        for (int i = 0; i < pins.size(); i++) {
            Pin pin = pins.get(i);
            int h = heights[i];
            int px;
            int py;
            if (pin.placed()) {
                px = Math.round(pin.x() * screenWidth);
                py = Math.round(pin.y() * screenHeight);
            } else {
                if (y + h > screenHeight - margin && y > top) {
                    column++;
                    y = top;
                }
                px = screenWidth - margin - width - column * (width + gap);
                py = y;
                y += h + gap;
            }
            out[i * 2] = Math.max(0, Math.min(px, screenWidth - width));
            out[i * 2 + 1] = Math.max(0, Math.min(py, screenHeight - h));
        }
        return out;
    }

    /** 一串定时取的样（戴森球发电这种没有存下来的曲线用）：只留最近的几个，从旧到新。 */
    public static final class Samples {
        private final double[] values;
        private int size;
        private int next;

        public Samples(int capacity) {
            this.values = new double[capacity];
        }

        public void add(double value) {
            this.values[this.next] = value;
            this.next = (this.next + 1) % this.values.length;
            this.size = Math.min(this.size + 1, this.values.length);
        }

        public double[] values() {
            double[] out = new double[this.size];
            int first = (this.next - this.size + this.values.length) % this.values.length;
            for (int i = 0; i < this.size; i++) {
                out[i] = this.values[(first + i) % this.values.length];
            }
            return out;
        }
    }
}
