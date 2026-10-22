package io.github.sweetcornna.universe.core.darkfog;

import java.util.Optional;

/**
 * 黑雾的地面单位（ADR-0028 §二）。数值是 0 级的，每级血量和伤害按 {@link DarkFogLevels#scale} 加。
 * 存档里记的是 {@link #id()}，以后只能往后加。
 */
public enum UnitKind {
    /** 突袭者：悬浮，慢，近身电弧，参加进攻。 */
    RAIDER("raider", 30, 4, true),
    /** 游骑兵：飞，快，远程激光，参加进攻。 */
    RANGER("ranger", 20, 3, true),
    /** 守卫者：飞，在基地上空巡逻，只守家。 */
    GUARDIAN("guardian", 60, 5, false);

    private final String id;
    private final float baseHealth;
    private final float baseDamage;
    private final boolean attacks;

    UnitKind(String id, float baseHealth, float baseDamage, boolean attacks) {
        this.id = id;
        this.baseHealth = baseHealth;
        this.baseDamage = baseDamage;
        this.attacks = attacks;
    }

    public String id() {
        return this.id;
    }

    public float baseHealth() {
        return this.baseHealth;
    }

    /** 一次攻击的伤害（打玩家、打设施都按这个）。 */
    public float baseDamage() {
        return this.baseDamage;
    }

    /** 参不参加进攻。 */
    public boolean attacks() {
        return this.attacks;
    }

    /** 哪种营地出这种单位。 */
    public BuildingKind camp() {
        return switch (this) {
            case RAIDER -> BuildingKind.RAIDER_CAMP;
            case RANGER -> BuildingKind.RANGER_CAMP;
            case GUARDIAN -> BuildingKind.GUARDIAN_CAMP;
        };
    }

    public static Optional<UnitKind> byId(String id) {
        for (UnitKind kind : values()) {
            if (kind.id.equals(id)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
