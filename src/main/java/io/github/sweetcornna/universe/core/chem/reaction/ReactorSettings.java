package io.github.sweetcornna.universe.core.chem.reaction;

/**
 * 反应釜的工况：温度（K）和压强（bar），超出范围的拉回边上。温度上限约 1290 °C（ADR-0037：部分氧化、安德卢梭法、BMA 法在 1000 °C 以上）。
 */
public record ReactorSettings(double temperature, double pressure) {
    public static final double MIN_TEMPERATURE = 300;
    public static final double MAX_TEMPERATURE = 1560;
    public static final double MIN_PRESSURE = 1;
    public static final double MAX_PRESSURE = 300;
    /** 0 °C 是多少 K。 */
    public static final double ZERO_CELSIUS = 273.15;

    public ReactorSettings {
        temperature = clamp(temperature, MIN_TEMPERATURE, MAX_TEMPERATURE);
        pressure = clamp(pressure, MIN_PRESSURE, MAX_PRESSURE);
    }

    public double celsius() {
        return this.temperature - ZERO_CELSIUS;
    }

    public ReactorSettings withTemperature(double temperature) {
        return new ReactorSettings(temperature, this.pressure);
    }

    public ReactorSettings withPressure(double pressure) {
        return new ReactorSettings(this.temperature, pressure);
    }

    private static double clamp(double value, double min, double max) {
        return Double.isNaN(value) ? min : Math.max(min, Math.min(max, value));
    }
}
