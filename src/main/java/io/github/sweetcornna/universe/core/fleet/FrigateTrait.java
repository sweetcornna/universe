package io.github.sweetcornna.universe.core.fleet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

/**
 * 护卫舰的特性（ADR-0024）：加减一项属性，或者加减燃料消耗。好的特性 {@code good} 为真。
 *
 * @param area 加在哪项属性上；{@code null} 表示改的是燃料
 */
public enum FrigateTrait {
    VETERAN_GUNNERS("老练的炮手", "Veteran Gunners", Area.COMBAT, 4),
    REINFORCED_ARMOUR("加厚装甲", "Reinforced Armour", Area.COMBAT, 3),
    DEEP_SCANNERS("深空扫描仪", "Deep-Space Scanners", Area.EXPLORATION, 4),
    CARTOGRAPHERS("制图员", "Cartographers", Area.EXPLORATION, 3),
    ONBOARD_REFINERY("船载精炼厂", "Onboard Refinery", Area.INDUSTRY, 4),
    MINING_DRONES("采矿无人机", "Mining Drones", Area.INDUSTRY, 3),
    SILVER_TONGUE("会谈生意的船长", "Silver-Tongued Captain", Area.TRADE, 4),
    EXPANDED_HOLDS("扩容货舱", "Expanded Holds", Area.TRADE, 3),
    EFFICIENT_ENGINES("省油的引擎", "Efficient Engines", null, -3),
    LEAKY_FUEL_LINES("漏油", "Leaky Fuel Lines", null, 3),
    JITTERY_CREW("胆小的船员", "Jittery Crew", Area.COMBAT, -3),
    FAULTY_SENSORS("传感器失灵", "Faulty Sensors", Area.EXPLORATION, -3),
    RUSTED_TOOLS("生锈的工具", "Rusted Tools", Area.INDUSTRY, -3),
    BAD_REPUTATION("名声不好", "Bad Reputation", Area.TRADE, -3);

    private final String nameZh;
    private final String nameEn;
    private final @Nullable Area area;
    private final int amount;

    FrigateTrait(String nameZh, String nameEn, @Nullable Area area, int amount) {
        this.nameZh = nameZh;
        this.nameEn = nameEn;
        this.area = area;
        this.amount = amount;
    }

    public String nameZh() {
        return this.nameZh;
    }

    public String nameEn() {
        return this.nameEn;
    }

    public @Nullable Area area() {
        return this.area;
    }

    /** 加在属性上的点数，或者燃料的增减（吨）。 */
    public int amount() {
        return this.amount;
    }

    /** 好特性：加属性或者省燃料。 */
    public boolean good() {
        return this.area == null ? this.amount < 0 : this.amount > 0;
    }

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public static List<FrigateTrait> of(boolean good) {
        List<FrigateTrait> list = new ArrayList<>();
        for (FrigateTrait trait : values()) {
            if (trait.good() == good) {
                list.add(trait);
            }
        }
        return list;
    }
}
