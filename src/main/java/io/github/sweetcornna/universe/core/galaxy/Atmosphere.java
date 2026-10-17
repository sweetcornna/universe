package io.github.sweetcornna.universe.core.galaxy;

/**
 * 大气。
 *
 * @param pressure 地表气压，单位是母星的标准大气压；没有大气时为 0
 */
public record Atmosphere(Kind kind, double pressure) {
    public static final Atmosphere NONE = new Atmosphere(Kind.NONE, 0);

    public enum Kind {
        /** 真空：天空是黑的，不下雨，声音传不远。 */
        NONE("无", "None"),
        /** 稀薄：不能呼吸，偶有沙尘。 */
        THIN("稀薄", "Thin"),
        /** 可以呼吸，不需要生命维持。 */
        BREATHABLE("可呼吸", "Breathable"),
        /** 有毒：不能呼吸，还会造成毒性危害。 */
        TOXIC("有毒", "Toxic"),
        /** 浓密的高压大气，多半很热。 */
        DENSE("浓密", "Dense");

        private final String nameZh;
        private final String nameEn;

        Kind(String nameZh, String nameEn) {
            this.nameZh = nameZh;
            this.nameEn = nameEn;
        }

        public String nameZh() {
            return this.nameZh;
        }

        public String nameEn() {
            return this.nameEn;
        }
    }

    public boolean breathable() {
        return this.kind == Kind.BREATHABLE;
    }

    public boolean present() {
        return this.kind != Kind.NONE;
    }
}
