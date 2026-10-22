package io.github.sweetcornna.universe.core.space;

import io.github.sweetcornna.universe.core.galaxy.StarClass;

/**
 * 跃迁驱动的档次（ADR-0024）：装了哪一档就能去这一档和下面几档的恒星。普通的跃迁引擎是最低一档。
 *
 * <p>无人深空里镉、翠金、铟分别打开红、绿、蓝恒星；真实的恒星没有绿色，这里按恒星的环境多极端排：先是红巨星，
 * 再是白星和白矮星，最后是蓝星和致密星。主序的红、橙、黄、黄白星（约八成）不要档次。
 */
public enum DriveTier {
    BASIC("跃迁引擎", "Warp Drive"),
    CADMIUM("镉驱动", "Cadmium Drive"),
    EMERIL("翠金驱动", "Emeril Drive"),
    INDIUM("铟驱动", "Indium Drive");

    private final String nameZh;
    private final String nameEn;

    DriveTier(String nameZh, String nameEn) {
        this.nameZh = nameZh;
        this.nameEn = nameEn;
    }

    public String nameZh() {
        return this.nameZh;
    }

    public String nameEn() {
        return this.nameEn;
    }

    /** 去这种恒星至少要哪一档。 */
    public static DriveTier required(StarClass star) {
        return switch (star) {
            case M, K, G, F -> BASIC;
            case RED_GIANT -> CADMIUM;
            case A, WHITE_DWARF -> EMERIL;
            case B, O, NEUTRON_STAR, BLACK_HOLE -> INDIUM;
        };
    }

    /** 装着这一档能不能去这种恒星。 */
    public boolean reaches(StarClass star) {
        return this.ordinal() >= required(star).ordinal();
    }

    /** 两档里高的那一档。 */
    public DriveTier max(DriveTier other) {
        return other.ordinal() > this.ordinal() ? other : this;
    }
}
