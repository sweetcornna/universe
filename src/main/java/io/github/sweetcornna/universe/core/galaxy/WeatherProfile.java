package io.github.sweetcornna.universe.core.galaxy;

import java.util.Locale;

/**
 * 星球的天气特征（玩法设计 §2.1）。
 *
 * @param precipitation 降水（或者沙尘、火山灰）的种类
 * @param frequency     有天气的时间占比 0–1
 * @param stormChance   天气变成风暴的概率 0–1；风暴让危害加倍、能见度下降
 * @param wind          风力 0–1，影响风暴里的推力和风力发电
 * @param lightning     风暴时是否打雷
 */
public record WeatherProfile(Precipitation precipitation, double frequency, double stormChance, double wind, boolean lightning) {
    public static final WeatherProfile CALM = new WeatherProfile(Precipitation.NONE, 0, 0, 0, false);

    public enum Precipitation {
        NONE("无", "None"),
        RAIN("雨", "Rain"),
        SNOW("雪", "Snow"),
        ACID_RAIN("酸雨", "Acid rain"),
        /** 火山灰，灼热星球。 */
        ASH("火山灰", "Ash"),
        /** 沙尘暴，荒芜和辐射星球。 */
        DUST("沙尘", "Dust"),
        /** 晶尘，奇异星球。 */
        CRYSTAL("晶尘", "Crystal dust");

        private final String nameZh;
        private final String nameEn;

        Precipitation(String nameZh, String nameEn) {
            this.nameZh = nameZh;
            this.nameEn = nameEn;
        }

        public String nameZh() {
            return this.nameZh;
        }

        public String nameEn() {
            return this.nameEn;
        }

        /** 是否是落到地上的液体或固体（会积雪、熄火）；沙尘类只影响能见度和危害。 */
        public boolean falls() {
            return this == RAIN || this == SNOW || this == ACID_RAIN;
        }

        public String serializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
