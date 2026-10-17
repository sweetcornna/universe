package io.github.sweetcornna.universe.core.galaxy;

import java.util.Locale;

/**
 * 星球的标志性地貌。每颗星球按类型从中抽一到三种，给它独有的面貌：
 * 同样是宜居星球，有的群山连绵，有的峰林遍布，有的是被峡谷切开的高原。
 */
public enum Landform {
    /** 绵延上百格高的脊状山脉，山顶积雪。 */
    MOUNTAIN_RANGES("山脉", "Mountain ranges"),
    /** 抬升的高原上切出蜿蜒的深谷，谷壁呈台阶状。 */
    CANYONS("大峡谷", "Canyons"),
    /** 平顶陡壁的方山和孤峰，崖壁上是彩色岩层。 */
    MESAS("方山台地", "Mesas"),
    /** 锥形火山，顶上有火山口。 */
    VOLCANOES("火山群", "Volcanoes"),
    /** 拔地而起的石柱和石峰，成片分布。 */
    KARST("峰林", "Karst towers"),
    /** 顺着风向排列的沙丘海。 */
    DUNES("沙丘海", "Dune seas"),
    /** 星罗棋布的小岛。 */
    ARCHIPELAGO("群岛", "Archipelagos"),
    /** 地壳撕开的长条深谷，两侧是陡崖。 */
    RIFTS("裂谷", "Rift valleys"),
    /** 圆形的垂直深坑。 */
    SINKHOLES("天坑", "Sinkholes"),
    /** 大片抬升的高原，边缘是陡坎。 */
    PLATEAUS("高原", "Plateaus");

    private final String nameZh;
    private final String nameEn;

    Landform(String nameZh, String nameEn) {
        this.nameZh = nameZh;
        this.nameEn = nameEn;
    }

    public String nameZh() {
        return this.nameZh;
    }

    public String nameEn() {
        return this.nameEn;
    }

    public String serializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    /** 星球上的一种地貌和它的强度（0 到 1）。 */
    public record Feature(Landform landform, double strength) {
    }
}
