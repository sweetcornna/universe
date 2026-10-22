package io.github.sweetcornna.universe.core.sentinel;

/**
 * 守卫机的种类（ADR-0026）。通缉的每一波按种类数，地面单位走路，别的都飞。只往后加。
 */
public enum SentinelKind {
    /** 普通无人机。 */
    DRONE("sentinel", true),
    /** 重型无人机。 */
    HEAVY("heavy_sentinel", true),
    /** 维修无人机：不开火，给附近受伤的守卫机回血。 */
    REPAIR("repair_sentinel", true),
    /** 召唤无人机：活着时隔一阵传送来几架无人机。 */
    SUMMONER("summoner_sentinel", true),
    /** 四足机：地面单位，跑得快，会扑。 */
    QUADRUPED("quadruped_sentinel", false),
    /** 步行机：地面单位，4 格高，走得慢。 */
    WALKER("walker_sentinel", false);

    private final String id;
    private final boolean flying;

    SentinelKind(String id, boolean flying) {
        this.id = id;
        this.flying = flying;
    }

    /** 实体类型的路径。 */
    public String id() {
        return this.id;
    }

    public boolean flying() {
        return this.flying;
    }
}
