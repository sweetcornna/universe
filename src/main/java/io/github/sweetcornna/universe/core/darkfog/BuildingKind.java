package io.github.sweetcornna.universe.core.darkfog;

import java.util.Optional;

/**
 * 黑雾地面基地的建筑（ADR-0028 §二）。都是不动的实体；血量是 0 级的，每级按 {@link DarkFogLevels#scale} 加。
 * 存档里记的是 {@link #id()}，以后只能往后加。
 */
public enum BuildingKind {
    /** 悬在基地上空 40 格，给基地送能量。 */
    RELAY("relay", 400),
    /** 钻芯：基地的核心，打掉它基地就没了。 */
    CORE("core", 1200),
    RAIDER_CAMP("raider_camp", 500),
    RANGER_CAMP("ranger_camp", 500),
    GUARDIAN_CAMP("guardian_camp", 700),
    /** 高能激光塔：近，打地面。 */
    LASER_TOWER("laser_tower", 400),
    /** 等离子哨塔：远，有节奏，跳起来能躲。 */
    PLASMA_SENTRY("plasma_sentry", 500);

    private final String id;
    private final float baseHealth;

    BuildingKind(String id, float baseHealth) {
        this.id = id;
        this.baseHealth = baseHealth;
    }

    public String id() {
        return this.id;
    }

    /** 0 级的血量。 */
    public float baseHealth() {
        return this.baseHealth;
    }

    public boolean isCamp() {
        return this == RAIDER_CAMP || this == RANGER_CAMP || this == GUARDIAN_CAMP;
    }

    public boolean isTurret() {
        return this == LASER_TOWER || this == PLASMA_SENTRY;
    }

    public static Optional<BuildingKind> byId(String id) {
        for (BuildingKind kind : values()) {
            if (kind.id.equals(id)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
