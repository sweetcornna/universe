package io.github.sweetcornna.universe.core.darkfog;

import java.util.Optional;

/**
 * 太空巢穴的建筑（ADR-0028 §八）。血量是 0 级时的，等级加的血按 {@link DarkFogLevels#scale}。
 * 存档里记的是 {@link #id()}，以后只能往后加。
 */
public enum HiveBuildingKind {
    /** 核心：打掉了巢穴瓦解；恒星系里还有中继站就会重建。 */
    CORE("core", 6000),
    /** 光子接收器：吸恒星的能量，恒星系有戴森球时偷一部分戴森球的发电。 */
    PHOTON_RECEIVER("photon_receiver", 1200),
    /** 等离子塔：远程炮塔，打飞船。 */
    PLASMA_TOWER("plasma_tower", 1600),
    /** 相位激光塔：近程，射得快。 */
    PHASE_LASER("phase_laser", 1400),
    /** 枪骑港：补枪骑。 */
    LANCER_PORT("lancer_port", 1800),
    /** 座头鲸港：补座头鲸。 */
    HUMPBACK_PORT("humpback_port", 2200);

    private final String id;
    private final float baseHealth;

    HiveBuildingKind(String id, float baseHealth) {
        this.id = id;
        this.baseHealth = baseHealth;
    }

    public String id() {
        return this.id;
    }

    /** 0 级时的血量。 */
    public float baseHealth() {
        return this.baseHealth;
    }

    public boolean isTower() {
        return this == PLASMA_TOWER || this == PHASE_LASER;
    }

    public boolean isPort() {
        return this == LANCER_PORT || this == HUMPBACK_PORT;
    }

    public static Optional<HiveBuildingKind> byId(String id) {
        for (HiveBuildingKind kind : values()) {
            if (kind.id.equals(id)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
