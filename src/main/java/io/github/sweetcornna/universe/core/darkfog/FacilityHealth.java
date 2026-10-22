package io.github.sweetcornna.universe.core.darkfog;

import java.util.Map;
import java.util.Set;

/**
 * 设施的耐久（ADR-0028 §四）：上限按方块定，修复的速度。方块按 ID 的路径（不带命名空间）查，没列出来的是一般设备。
 */
public final class FacilityHealth {
    /** 传送带、分拣器这类细小的东西。 */
    public static final float LIGHT = 60;
    /** 一般设备。 */
    public static final float NORMAL = 400;
    /** 大型设备、物流站。 */
    public static final float LARGE = 1200;
    /** 战场分析基地：耐打，能当墙用。 */
    public static final float BATTLEFIELD_BASE = 3000;

    /** 建造无人机一趟修多少。 */
    public static final float DRONE_REPAIR = 120;
    /** 战场分析基地每次（半秒）给一个设施修多少，电满的时候。 */
    public static final float BASE_REPAIR = 30;
    /** 战场分析基地一次最多同时修几个。 */
    public static final int BASE_REPAIR_TARGETS = 4;

    private static final Set<String> LIGHT_BLOCKS = Set.of("conveyor_belt", "conveyor_belt_mk2", "conveyor_belt_mk3", "sorter", "sorter_mk2",
            "sorter_mk3", "pile_sorter", "splitter", "traffic_monitor");
    private static final Set<String> LARGE_BLOCKS = Set.of("planetary_logistics_station", "interstellar_logistics_station", "orbital_collector",
            "em_rail_ejector", "vertical_launching_silo", "ray_receiver", "miniature_particle_collider", "mini_fusion_power_plant",
            "artificial_star", "advanced_mining_machine", "quantum_chemical_plant", "self_evolution_lab", "negentropy_smelter",
            "recomposing_assembler", "logistics_distributor");
    /** 防御设施（ADR-0028 §五）的耐久。 */
    private static final Map<String, Float> DEFENSE = Map.of(
            "gauss_turret", 400F,
            "laser_turret", 300F,
            "implosion_cannon", 500F,
            "missile_turret", 600F,
            "jammer_tower", 300F,
            "signal_tower", 1000F,
            "battlefield_analysis_base", BATTLEFIELD_BASE);

    private FacilityHealth() {
    }

    /** 这种方块的耐久上限。 */
    public static float max(String path) {
        Float defense = DEFENSE.get(path);
        if (defense != null) {
            return defense;
        }
        if (LIGHT_BLOCKS.contains(path)) {
            return LIGHT;
        }
        return LARGE_BLOCKS.contains(path) ? LARGE : NORMAL;
    }

    /** 挨了 {@code damage} 以后还剩多少；不会小于 0。 */
    public static float afterDamage(float health, float damage) {
        return Math.max(0, health - Math.max(0, damage));
    }

    /** 修了 {@code amount} 以后有多少；不会超过上限。 */
    public static float afterRepair(float health, float max, float amount) {
        return Math.min(max, health + Math.max(0, amount));
    }

    /** 还剩的比例，0–1。 */
    public static float ratio(float health, float max) {
        return max <= 0 ? 1 : Math.max(0, Math.min(1, health / max));
    }

    /** 满了：差得不到半点也算满，免得浮点误差留下一条永远修不完的记录。 */
    public static boolean full(float health, float max) {
        return health >= max - 0.5F;
    }
}
