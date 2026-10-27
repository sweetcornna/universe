package io.github.sweetcornna.universe.core.chem.reaction;

/**
 * 反应釜界面上那张"温度 × 压强"的产量图（ADR-0036 §四）：温度按 90 K 一格分 10 格，压强按对数分 8 格。
 * 在哪个工况跑过一批，那一格就填上颜色；格子的编号是存档格式的一部分（反应釜按编号记跑过的格子）。
 */
public final class YieldGrid {
    public static final int TEMPERATURE_CELLS = 10;
    public static final int PRESSURE_CELLS = 8;
    public static final int CELLS = TEMPERATURE_CELLS * PRESSURE_CELLS;
    private static final double SPAN_T = ReactorSettings.MAX_TEMPERATURE - ReactorSettings.MIN_TEMPERATURE;
    private static final double SPAN_LN_P = Math.log(ReactorSettings.MAX_PRESSURE / ReactorSettings.MIN_PRESSURE);

    private YieldGrid() {
    }

    /** 一格：温度是第几列（低到高），压强是第几行（低到高）。 */
    public record Cell(int temperature, int pressure) {
        public Cell {
            if (temperature < 0 || temperature >= TEMPERATURE_CELLS || pressure < 0 || pressure >= PRESSURE_CELLS) {
                throw new IllegalArgumentException("cell out of range: " + temperature + ", " + pressure);
            }
        }

        /** 编号，0 到 {@link #CELLS} − 1。 */
        public int index() {
            return this.temperature * PRESSURE_CELLS + this.pressure;
        }
    }

    /** 工况落在哪一格；正好在边上的算右边那格，最右边的算最后一格。 */
    public static Cell cell(ReactorSettings settings) {
        double t = (settings.temperature() - ReactorSettings.MIN_TEMPERATURE) / SPAN_T;
        double p = Math.log(settings.pressure() / ReactorSettings.MIN_PRESSURE) / SPAN_LN_P;
        return new Cell(bucket(t, TEMPERATURE_CELLS), bucket(p, PRESSURE_CELLS));
    }

    /** 一格中间的工况。 */
    public static ReactorSettings center(Cell cell) {
        double t = ReactorSettings.MIN_TEMPERATURE + (cell.temperature() + 0.5) / TEMPERATURE_CELLS * SPAN_T;
        double p = ReactorSettings.MIN_PRESSURE * Math.exp((cell.pressure() + 0.5) / PRESSURE_CELLS * SPAN_LN_P);
        return new ReactorSettings(t, p);
    }

    public static Cell byIndex(int index) {
        if (index < 0 || index >= CELLS) {
            throw new IllegalArgumentException("index out of range: " + index);
        }
        return new Cell(index / PRESSURE_CELLS, index % PRESSURE_CELLS);
    }

    private static int bucket(double fraction, int cells) {
        return Math.max(0, Math.min(cells - 1, (int) Math.floor(fraction * cells)));
    }
}
