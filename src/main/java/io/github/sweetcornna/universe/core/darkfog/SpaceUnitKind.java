package io.github.sweetcornna.universe.core.darkfog;

import java.util.Optional;

/**
 * 太空巢穴的单位（ADR-0028 §八）。血量、伤害是 0 级时的，等级加的按 {@link DarkFogLevels#scale}。
 * 速度按格/刻（飞船巡航两格多一刻，海盗战机 2.2）。存档里记的是 {@link #id()}，以后只能往后加。
 */
public enum SpaceUnitKind {
    /** 枪骑：快，激光，一轮一轮俯冲着打；守巢，也去轰炸星球。 */
    LANCER("lancer", HiveBuildingKind.LANCER_PORT, 100, 3, 1.9, 0.12, 128, 220),
    /** 座头鲸：慢，耐打，炮重，隔着一段距离绕着打；只守巢。血量乘满级的倍数也不能超过原版血量上限 1024。 */
    HUMPBACK("humpback", HiveBuildingKind.HUMPBACK_PORT, 450, 9, 0.45, 0.03, 96, 140);

    private final String id;
    private final HiveBuildingKind port;
    private final float baseHealth;
    private final float baseDamage;
    private final double speed;
    private final double accel;
    private final double sight;
    private final double leash;

    SpaceUnitKind(String id, HiveBuildingKind port, float baseHealth, float baseDamage, double speed, double accel, double sight, double leash) {
        this.id = id;
        this.port = port;
        this.baseHealth = baseHealth;
        this.baseDamage = baseDamage;
        this.speed = speed;
        this.accel = accel;
        this.sight = sight;
        this.leash = leash;
    }

    public String id() {
        return this.id;
    }

    /** 从哪种港口出来。 */
    public HiveBuildingKind port() {
        return this.port;
    }

    /** 0 级时的血量。 */
    public float baseHealth() {
        return this.baseHealth;
    }

    /** 0 级时一发的伤害。 */
    public float baseDamage() {
        return this.baseDamage;
    }

    /** 巡航速度（格/刻）。 */
    public double speed() {
        return this.speed;
    }

    /** 每刻最多改变多少速度：决定转弯有多利索。 */
    public double accel() {
        return this.accel;
    }

    /** 守巢时多远内的人会被盯上。 */
    public double sight() {
        return this.sight;
    }

    /** 守巢时离家多远就不追了，回去。 */
    public double leash() {
        return this.leash;
    }

    public static Optional<SpaceUnitKind> byId(String id) {
        for (SpaceUnitKind kind : values()) {
            if (kind.id.equals(id)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
