package io.github.sweetcornna.universe.core.research;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.sweetcornna.universe.core.research.Tech.ItemCost;

/**
 * 科技树（玩法设计 §9.5）：科技和它们的前置关系。界面按"深度"（最长的前置链）分列，同一列按声明顺序排。
 */
public final class TechTree {
    private static final String NS = "universe:";
    private static final String BLUE = NS + "electromagnetic_matrix";
    private static final String RED = NS + "energy_matrix";
    private static final String YELLOW = NS + "structure_matrix";
    private static final String PURPLE = NS + "information_matrix";
    private static final String GREEN = NS + "gravity_matrix";
    /** 研究站认的矩阵，顺序就是研究站格子的顺序。以后加新的矩阵只能往后加，免得存档里的格子对不上。 */
    public static final List<String> MATRICES = List.of(BLUE, RED, YELLOW, PURPLE, GREEN);

    /**
     * M3 的科技：电磁学阶段上交物品，之后用蓝矩阵、再用蓝红两种矩阵研究；M4 加了上交勘探数据的科技（ADR-0012）；
     * M5 加了物流和中期工业（ADR-0015）：蓝红阶段做到结构矩阵，蓝红黄做到信息矩阵，四种矩阵研究星际物流。
     * M6 加了戴森球（ADR-0016）：蓝红黄研究太阳帆和弹射器，四种矩阵研究射线接收站、量子芯片、对撞机和引力矩阵，
     * 有了绿矩阵才能研究垂直发射井和应力系统。M7 加了飞船武器和护盾（ADR-0017）：蓝红黄研究光子炮和采矿光束，
     * 四种矩阵研究护盾发生器。
     */
    public static final TechTree DEFAULT = new TechTree(List.of(
            Tech.handIn("electromagnetism", List.of(),
                    List.of(cost("magnetic_coil", 10), cost("circuit_board", 10)),
                    items("tesla_tower", "wind_turbine", "mining_machine")),
            Tech.handIn("basic_logistics", List.of("electromagnetism"),
                    List.of(cost("gear", 10), cost("circuit_board", 10)),
                    items("conveyor_belt", "sorter", "storage_depot")),
            Tech.handIn("automatic_metallurgy", List.of("electromagnetism"),
                    List.of(cost("minecraft:iron_ingot", 20), cost("magnetic_coil", 10)),
                    items("arc_smelter")),
            Tech.handIn("basic_assembling", List.of("basic_logistics", "automatic_metallurgy"),
                    List.of(cost("circuit_board", 20), cost("gear", 20)),
                    items("assembler")),
            Tech.handIn("electromagnetic_matrix", List.of("basic_assembling"),
                    List.of(cost("circuit_board", 10), cost("magnetic_coil", 10)),
                    items("matrix_lab", "electromagnetic_matrix")),
            // 先开着飞船把母星系里的星球跑一遍，攒够勘探数据才能离开母星系
            Tech.handIn("interstellar_survey", List.of("electromagnetic_matrix"), List.of(cost("survey_data", 12)), items()),
            // 技术模块（ADR-0014）：C 级模块能在制造台里做，更好的只能去兴趣点找
            Tech.handIn("tech_modules", List.of("electromagnetic_matrix"), List.of(cost("survey_data", 6), cost("circuit_board", 10)),
                    items("module_life_support", "module_cold_lining", "module_heat_lining", "module_toxin_filter",
                            "module_radiation_shield", "module_jet_booster", "module_servo", "module_shield", "module_beam_focus",
                            "module_heat_sink", "module_scanner_array", "module_analyzer", "module_weapon_calibrator")),
            Tech.lab("fluid_storage", List.of("electromagnetic_matrix"), List.of(BLUE), 20, items("water_pump")),
            Tech.lab("electromagnetic_drive", List.of("electromagnetic_matrix"), List.of(BLUE), 20, items("electric_motor")),
            Tech.lab("thermal_power", List.of("electromagnetic_matrix"), List.of(BLUE), 20, items("thermal_plant")),
            Tech.lab("steel_smelting", List.of("electromagnetic_matrix"), List.of(BLUE), 30, items("steel")),
            Tech.lab("plasma_refining", List.of("fluid_storage", "electromagnetic_drive", "steel_smelting"), List.of(BLUE), 60,
                    items("oil_extractor", "oil_refinery", "prism", "plasma_exciter")),
            Tech.lab("energy_matrix", List.of("plasma_refining", "thermal_power"), List.of(BLUE), 60, items("energy_matrix")),
            Tech.lab("energy_storage", List.of("energy_matrix"), List.of(BLUE, RED), 30, items("accumulator")),
            Tech.lab("x_ray_cracking", List.of("energy_matrix"), List.of(BLUE, RED), 30, items("energetic_graphite")),
            Tech.lab("warp_drive", List.of("energy_storage", "x_ray_cracking", "interstellar_survey"), List.of(BLUE, RED), 60,
                    items("warp_drive", "space_warper")),
            // 勘探数据校准了星图，空间翘曲器的配方产量翻倍
            Tech.handIn("warp_calibration", List.of("warp_drive"), List.of(cost("survey_data", 30)), items("space_warper")),
            // ---- M5：物流和中期工业（ADR-0015）
            Tech.lab("magnetic_levitation", List.of("energy_matrix"), List.of(BLUE, RED), 40,
                    items("electromagnetic_turbine", "super_magnetic_ring")),
            Tech.lab("improved_logistics", List.of("magnetic_levitation"), List.of(BLUE, RED), 40,
                    items("conveyor_belt_mk2", "sorter_mk2", "splitter", "storage_tank")),
            Tech.lab("basic_chemical", List.of("energy_matrix"), List.of(BLUE, RED), 50,
                    items("chemical_plant", "plastic", "sulfuric_acid", "organic_crystal")),
            Tech.lab("semiconductor", List.of("energy_matrix"), List.of(BLUE, RED), 50, items("crystal_silicon", "microcrystalline_component")),
            Tech.lab("thruster", List.of("energy_matrix", "steel_smelting"), List.of(BLUE, RED), 40, items("thruster")),
            Tech.lab("solar_power", List.of("semiconductor"), List.of(BLUE, RED), 40, items("solar_panel")),
            Tech.lab("processor", List.of("semiconductor"), List.of(BLUE, RED), 60, items("processor")),
            Tech.lab("high_strength_materials", List.of("basic_chemical"), List.of(BLUE, RED), 60, items("graphene", "carbon_nanotube")),
            Tech.lab("deuterium_fractionation", List.of("basic_chemical"), List.of(BLUE, RED), 50, items("fractionator", "deuterium")),
            Tech.lab("improved_assembling", List.of("processor", "high_strength_materials"), List.of(BLUE, RED), 60, items("assembler_mk2")),
            Tech.lab("planetary_logistics", List.of("improved_logistics", "processor", "thruster", "high_strength_materials"),
                    List.of(BLUE, RED), 80, items("planetary_logistics_station", "logistics_drone", "particle_container")),
            Tech.lab("structure_matrix", List.of("high_strength_materials", "processor"), List.of(BLUE, RED), 80,
                    items("structure_matrix", "synthetic_diamond", "titanium_crystal")),
            Tech.lab("efficient_logistics", List.of("planetary_logistics", "structure_matrix"), List.of(BLUE, RED, YELLOW), 80,
                    items("conveyor_belt_mk3", "sorter_mk3")),
            Tech.lab("titanium_alloy", List.of("structure_matrix"), List.of(BLUE, RED, YELLOW), 80, items("titanium_alloy")),
            Tech.lab("information_matrix", List.of("titanium_alloy"), List.of(BLUE, RED, YELLOW), 100,
                    items("information_matrix", "particle_broadband")),
            Tech.lab("frame_material", List.of("titanium_alloy", "high_strength_materials"), List.of(BLUE, RED, YELLOW), 100,
                    items("frame_material")),
            Tech.lab("quantum_printing", List.of("information_matrix"), List.of(BLUE, RED, YELLOW, PURPLE), 150,
                    items("titanium_glass", "casimir_crystal", "plane_filter", "quantum_chip")),
            Tech.lab("plane_smelting", List.of("frame_material", "quantum_printing"), List.of(BLUE, RED, YELLOW, PURPLE), 120,
                    items("plane_smelter")),
            Tech.lab("interstellar_logistics", List.of("efficient_logistics", "information_matrix", "warp_drive"),
                    List.of(BLUE, RED, YELLOW, PURPLE), 150,
                    items("interstellar_logistics_station", "logistics_vessel", "reinforced_thruster", "factory_anchor")),
            Tech.lab("orbital_collection", List.of("interstellar_logistics", "deuterium_fractionation"), List.of(BLUE, RED, YELLOW, PURPLE), 150,
                    items("orbital_collector")),
            Tech.lab("advanced_mining", List.of("frame_material", "quantum_printing"), List.of(BLUE, RED, YELLOW, PURPLE), 120,
                    items("advanced_mining_machine")),
            Tech.lab("high_speed_assembling", List.of("improved_assembling", "quantum_printing"), List.of(BLUE, RED, YELLOW, PURPLE), 120,
                    items("assembler_mk3")),
            // ---- 戴森球（ADR-0016）
            Tech.lab("solar_sail_orbit", List.of("structure_matrix", "solar_power"), List.of(BLUE, RED, YELLOW), 100,
                    items("photon_combiner", "solar_sail", "em_rail_ejector")),
            Tech.lab("ray_receiver", List.of("solar_sail_orbit", "information_matrix"), List.of(BLUE, RED, YELLOW, PURPLE), 150,
                    items("ray_receiver")),
            Tech.lab("miniature_collider", List.of("frame_material", "information_matrix"), List.of(BLUE, RED, YELLOW, PURPLE), 150,
                    items("miniature_particle_collider", "strange_matter")),
            Tech.lab("gravitational_wave_refraction", List.of("miniature_collider"), List.of(BLUE, RED, YELLOW, PURPLE), 150,
                    items("graviton_lens")),
            Tech.lab("gravity_matrix", List.of("gravitational_wave_refraction", "quantum_printing"), List.of(BLUE, RED, YELLOW, PURPLE), 200,
                    items("gravity_matrix")),
            Tech.lab("vertical_launching_silo", List.of("gravity_matrix", "solar_sail_orbit", "deuterium_fractionation"),
                    List.of(BLUE, RED, YELLOW, PURPLE, GREEN), 200,
                    items("vertical_launching_silo", "dyson_sphere_component", "small_carrier_rocket", "deuteron_fuel_rod")),
            // 应力系统不解锁物品，放宽戴森球能建的纬度
            Tech.lab("dyson_stress_1", List.of("vertical_launching_silo"), List.of(BLUE, RED, YELLOW, PURPLE, GREEN), 200, items()),
            Tech.lab("dyson_stress_2", List.of("dyson_stress_1"), List.of(BLUE, RED, YELLOW, PURPLE, GREEN), 400, items()),
            // ---- M7：飞船武器和护盾（ADR-0017）
            Tech.lab("ship_weapons", List.of("structure_matrix", "thruster"), List.of(BLUE, RED, YELLOW), 80,
                    items("photon_cannon", "mining_beam")),
            Tech.lab("ship_shields", List.of("ship_weapons", "information_matrix"), List.of(BLUE, RED, YELLOW, PURPLE), 120,
                    items("shield_generator")),
            // ---- M7：探索车（ADR-0017）
            Tech.lab("exocraft", List.of("energy_storage", "processor"), List.of(BLUE, RED), 40, items("exocraft")),
            // ---- M7：传送网络（ADR-0017）。基地信标不用研究
            Tech.lab("teleport_network", List.of("warp_drive"), List.of(BLUE, RED), 80, items("teleporter"))));

    private final List<Tech> techs;
    private final Map<String, Tech> byId;
    private final Map<String, Integer> depth;

    public TechTree(List<Tech> techs) {
        this.techs = List.copyOf(techs);
        Map<String, Tech> byId = new LinkedHashMap<>();
        for (Tech tech : techs) {
            if (byId.put(tech.id(), tech) != null) {
                throw new IllegalArgumentException("duplicate tech " + tech.id());
            }
        }
        this.byId = Collections.unmodifiableMap(byId);
        // 前置必须写在前面，这样也就不会有环
        Map<String, Integer> depth = new HashMap<>();
        for (Tech tech : techs) {
            int d = 0;
            for (String required : tech.requires()) {
                Integer parent = depth.get(required);
                if (parent == null) {
                    throw new IllegalArgumentException(tech.id() + " requires unknown or later tech " + required);
                }
                d = Math.max(d, parent + 1);
            }
            depth.put(tech.id(), d);
        }
        this.depth = Collections.unmodifiableMap(depth);
    }

    private static ItemCost cost(String item, int count) {
        return new ItemCost(item.contains(":") ? item : NS + item, count);
    }

    private static List<String> items(String... names) {
        List<String> ids = new ArrayList<>();
        for (String name : names) {
            ids.add(NS + name);
        }
        return ids;
    }

    public List<Tech> techs() {
        return this.techs;
    }

    public Optional<Tech> get(String id) {
        return Optional.ofNullable(this.byId.get(id));
    }

    /** 最长的前置链长度：没有前置的是 0。 */
    public int depth(String id) {
        Integer d = this.depth.get(id);
        if (d == null) {
            throw new IllegalArgumentException("unknown tech " + id);
        }
        return d;
    }

    /** 一共几列：最深的科技的深度加一。 */
    public int columns() {
        int columns = 0;
        for (int d : this.depth.values()) {
            columns = Math.max(columns, d + 1);
        }
        return columns;
    }

    /** 最长的一列有几个科技。 */
    public int rows() {
        Map<Integer, Integer> counts = new HashMap<>();
        int rows = 0;
        for (int d : this.depth.values()) {
            rows = Math.max(rows, counts.merge(d, 1, Integer::sum));
        }
        return rows;
    }

    /** 同一深度里的第几个（按声明顺序），界面排版用。 */
    public int row(String id) {
        int d = this.depth(id);
        int row = 0;
        for (Tech tech : this.techs) {
            if (tech.id().equals(id)) {
                return row;
            }
            if (this.depth.get(tech.id()) == d) {
                row++;
            }
        }
        throw new IllegalArgumentException("unknown tech " + id);
    }
}
