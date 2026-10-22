package io.github.sweetcornna.universe.core.research;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.research.Tech.ItemCost;
import io.github.sweetcornna.universe.core.research.Upgrade.Infinite;
import io.github.sweetcornna.universe.core.research.Upgrade.Tier;

/**
 * 科技树（玩法设计 §9.5）：科技和它们的前置关系。界面按"深度"（最长的前置链）分列，同一列按声明顺序排。
 * 升级科技（ADR-0018）另外放一列，排在最右边。
 */
public final class TechTree {
    private static final String NS = "universe:";
    private static final String BLUE = NS + "electromagnetic_matrix";
    private static final String RED = NS + "energy_matrix";
    private static final String YELLOW = NS + "structure_matrix";
    private static final String PURPLE = NS + "information_matrix";
    private static final String GREEN = NS + "gravity_matrix";
    private static final String WHITE = NS + "universe_matrix";
    /** 黑雾矩阵（ADR-0028 §六）：只从黑雾掉落得到，研究黑雾那一支科技。 */
    public static final String DARK_FOG = NS + "dark_fog_matrix";
    /** 研究站认的矩阵，顺序就是研究站格子的顺序。以后加新的矩阵只能往后加，免得存档里的格子对不上。 */
    public static final List<String> MATRICES = List.of(BLUE, RED, YELLOW, PURPLE, GREEN, WHITE, DARK_FOG);

    /**
     * M3 的科技：电磁学阶段上交物品，之后用蓝矩阵、再用蓝红两种矩阵研究；M4 加了上交勘探数据的科技（ADR-0012）；
     * M5 加了物流和中期工业（ADR-0015）：蓝红阶段做到结构矩阵，蓝红黄做到信息矩阵，四种矩阵研究星际物流。
     * M6 加了戴森球（ADR-0016）：蓝红黄研究太阳帆和弹射器，四种矩阵研究射线接收站、量子芯片、对撞机和引力矩阵，
     * 有了绿矩阵才能研究垂直发射井和应力系统。M7 加了飞船武器和护盾（ADR-0017）：蓝红黄研究光子炮和采矿光束，
     * 四种矩阵研究护盾发生器。M8 加了终局（ADR-0018）：四种矩阵研究微型聚变，五种矩阵研究反物质、人造恒星和宇宙矩阵，
     * 最后的"任务完成"只要白矩阵。
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
                            "module_heat_sink", "module_scanner_array", "module_analyzer", "module_weapon_calibrator",
                            // 飞船模块（ADR-0024）
                            "module_thrust_efficiency", "module_pulse_tuning", "module_hyperdrive_booster", "module_deflector",
                            "module_hull_reinforcement", "module_fire_control")),
            Tech.lab("fluid_storage", List.of("electromagnetic_matrix"), List.of(BLUE), 20, items("water_pump")),
            Tech.lab("electromagnetic_drive", List.of("electromagnetic_matrix"), List.of(BLUE), 20, items("electric_motor")),
            Tech.lab("thermal_power", List.of("electromagnetic_matrix"), List.of(BLUE), 20, items("thermal_plant", "combustible_unit")),
            Tech.lab("steel_smelting", List.of("electromagnetic_matrix"), List.of(BLUE), 30, items("steel", "foundation")),
            Tech.lab("plasma_refining", List.of("fluid_storage", "electromagnetic_drive", "steel_smelting"), List.of(BLUE), 60,
                    items("oil_extractor", "oil_refinery", "prism", "plasma_exciter")),
            Tech.lab("energy_matrix", List.of("plasma_refining", "thermal_power"), List.of(BLUE), 60, items("energy_matrix")),
            Tech.lab("energy_storage", List.of("energy_matrix"), List.of(BLUE, RED), 40, items("accumulator")),
            Tech.lab("x_ray_cracking", List.of("energy_matrix"), List.of(BLUE, RED), 40, items("energetic_graphite")),
            Tech.lab("warp_drive", List.of("energy_storage", "x_ray_cracking", "interstellar_survey"), List.of(BLUE, RED), 80,
                    items("warp_drive", "space_warper")),
            // 勘探数据校准了星图，空间翘曲器的配方产量翻倍
            Tech.handIn("warp_calibration", List.of("warp_drive"), List.of(cost("survey_data", 30)), items("space_warper")),
            // ---- M5：物流和中期工业（ADR-0015）
            Tech.lab("magnetic_levitation", List.of("energy_matrix"), List.of(BLUE, RED), 60,
                    items("electromagnetic_turbine", "super_magnetic_ring")),
            Tech.lab("improved_logistics", List.of("magnetic_levitation"), List.of(BLUE, RED), 60,
                    items("conveyor_belt_mk2", "sorter_mk2", "splitter", "storage_tank")),
            Tech.lab("basic_chemical", List.of("energy_matrix"), List.of(BLUE, RED), 70,
                    items("chemical_plant", "plastic", "sulfuric_acid", "organic_crystal")),
            Tech.lab("semiconductor", List.of("energy_matrix"), List.of(BLUE, RED), 70, items("crystal_silicon", "microcrystalline_component")),
            Tech.lab("thruster", List.of("energy_matrix", "steel_smelting"), List.of(BLUE, RED), 60, items("thruster")),
            Tech.lab("solar_power", List.of("semiconductor"), List.of(BLUE, RED), 60, items("solar_panel")),
            Tech.lab("processor", List.of("semiconductor"), List.of(BLUE, RED), 80, items("processor")),
            Tech.lab("high_strength_materials", List.of("basic_chemical"), List.of(BLUE, RED), 80, items("graphene", "carbon_nanotube")),
            Tech.lab("deuterium_fractionation", List.of("basic_chemical"), List.of(BLUE, RED), 70,
                    items("fractionator", "deuterium", "hydrogen_fuel_rod")),
            Tech.lab("improved_assembling", List.of("processor", "high_strength_materials"), List.of(BLUE, RED), 80, items("assembler_mk2")),
            Tech.lab("planetary_logistics", List.of("improved_logistics", "processor", "thruster", "high_strength_materials"),
                    List.of(BLUE, RED), 120, items("planetary_logistics_station", "logistics_drone", "particle_container")),
            // M12（ADR-0023）：物流配送器、配送机器人和机甲的物流栏位
            Tech.lab("distribution_logistics", List.of("planetary_logistics"), List.of(BLUE, RED), 150,
                    items("logistics_distributor", "logistics_bot")),
            Tech.lab("structure_matrix", List.of("high_strength_materials", "processor"), List.of(BLUE, RED), 120,
                    items("structure_matrix", "synthetic_diamond", "titanium_crystal")),
            Tech.lab("efficient_logistics", List.of("planetary_logistics", "structure_matrix"), List.of(BLUE, RED, YELLOW), 140,
                    items("conveyor_belt_mk3", "sorter_mk3")),
            Tech.lab("titanium_alloy", List.of("structure_matrix"), List.of(BLUE, RED, YELLOW), 140, items("titanium_alloy")),
            Tech.lab("information_matrix", List.of("titanium_alloy"), List.of(BLUE, RED, YELLOW), 200,
                    items("information_matrix", "particle_broadband")),
            Tech.lab("frame_material", List.of("titanium_alloy", "high_strength_materials"), List.of(BLUE, RED, YELLOW), 160,
                    items("frame_material")),
            Tech.lab("quantum_printing", List.of("information_matrix"), List.of(BLUE, RED, YELLOW, PURPLE), 200,
                    items("titanium_glass", "casimir_crystal", "plane_filter", "quantum_chip")),
            Tech.lab("plane_smelting", List.of("frame_material", "quantum_printing"), List.of(BLUE, RED, YELLOW, PURPLE), 160,
                    items("plane_smelter")),
            Tech.lab("interstellar_logistics", List.of("efficient_logistics", "information_matrix", "warp_drive"),
                    List.of(BLUE, RED, YELLOW, PURPLE), 200,
                    items("interstellar_logistics_station", "logistics_vessel", "reinforced_thruster", "factory_anchor")),
            Tech.lab("orbital_collection", List.of("interstellar_logistics", "deuterium_fractionation"), List.of(BLUE, RED, YELLOW, PURPLE), 180,
                    items("orbital_collector")),
            Tech.lab("advanced_mining", List.of("frame_material", "quantum_printing"), List.of(BLUE, RED, YELLOW, PURPLE), 160,
                    items("advanced_mining_machine")),
            Tech.lab("high_speed_assembling", List.of("improved_assembling", "quantum_printing"), List.of(BLUE, RED, YELLOW, PURPLE), 160,
                    items("assembler_mk3")),
            // ---- M11：工厂的补全（ADR-0022）
            Tech.lab("proliferator_mk1", List.of("plasma_refining"), List.of(BLUE), 60, items("proliferator_mk1", "spray_coater")),
            Tech.lab("proliferator_mk2", List.of("proliferator_mk1", "structure_matrix"), List.of(BLUE, RED, YELLOW), 120,
                    items("proliferator_mk2")),
            Tech.lab("proliferator_mk3", List.of("proliferator_mk2", "information_matrix"), List.of(BLUE, RED, YELLOW, PURPLE), 160,
                    items("proliferator_mk3")),
            Tech.lab("integrated_logistics", List.of("efficient_logistics"), List.of(BLUE, RED, YELLOW), 150,
                    items("automatic_piler", "traffic_monitor")),
            Tech.lab("pile_sorting", List.of("integrated_logistics", "information_matrix"), List.of(BLUE, RED, YELLOW, PURPLE), 160,
                    items("pile_sorter")),
            Tech.lab("wireless_power", List.of("energy_storage"), List.of(BLUE, RED), 60, items("wireless_power_tower")),
            Tech.lab("geothermal_power", List.of("thermal_power", "structure_matrix"), List.of(BLUE, RED, YELLOW), 120,
                    items("geothermal_power_station")),
            Tech.lab("satellite_power", List.of("wireless_power", "frame_material"), List.of(BLUE, RED, YELLOW), 140,
                    items("satellite_substation")),
            Tech.lab("energy_exchange", List.of("energy_storage", "information_matrix"), List.of(BLUE, RED, YELLOW, PURPLE), 160,
                    items("energy_exchanger")),
            // ---- 戴森球（ADR-0016）
            Tech.lab("solar_sail_orbit", List.of("structure_matrix", "solar_power"), List.of(BLUE, RED, YELLOW), 200,
                    items("photon_combiner", "solar_sail", "em_rail_ejector")),
            Tech.lab("ray_receiver", List.of("solar_sail_orbit", "information_matrix"), List.of(BLUE, RED, YELLOW, PURPLE), 200,
                    items("ray_receiver")),
            Tech.lab("miniature_collider", List.of("frame_material", "information_matrix"), List.of(BLUE, RED, YELLOW, PURPLE), 200,
                    items("miniature_particle_collider", "strange_matter")),
            Tech.lab("gravitational_wave_refraction", List.of("miniature_collider"), List.of(BLUE, RED, YELLOW, PURPLE), 200,
                    items("graviton_lens")),
            Tech.lab("gravity_matrix", List.of("gravitational_wave_refraction", "quantum_printing"), List.of(BLUE, RED, YELLOW, PURPLE), 240,
                    items("gravity_matrix")),
            // M11 的高级设备（ADR-0022）：五种矩阵
            Tech.lab("negentropy_smelting", List.of("gravity_matrix", "plane_smelting"), List.of(BLUE, RED, YELLOW, PURPLE, GREEN), 300,
                    items("negentropy_smelter")),
            Tech.lab("recomposing_assembly", List.of("gravity_matrix", "high_speed_assembling"), List.of(BLUE, RED, YELLOW, PURPLE, GREEN), 300,
                    items("recomposing_assembler")),
            Tech.lab("quantum_chemistry", List.of("gravity_matrix", "basic_chemical"), List.of(BLUE, RED, YELLOW, PURPLE, GREEN), 300,
                    items("quantum_chemical_plant")),
            Tech.lab("self_evolution", List.of("gravity_matrix"), List.of(BLUE, RED, YELLOW, PURPLE, GREEN), 300, items("self_evolution_lab")),
            Tech.lab("vertical_launching_silo", List.of("gravity_matrix", "solar_sail_orbit", "deuterium_fractionation"),
                    List.of(BLUE, RED, YELLOW, PURPLE, GREEN), 200,
                    items("vertical_launching_silo", "dyson_sphere_component", "small_carrier_rocket", "deuteron_fuel_rod")),
            // 应力系统不解锁物品，放宽戴森球能建的纬度
            Tech.lab("dyson_stress_1", List.of("vertical_launching_silo"), List.of(BLUE, RED, YELLOW, PURPLE, GREEN), 200, items()),
            Tech.lab("dyson_stress_2", List.of("dyson_stress_1"), List.of(BLUE, RED, YELLOW, PURPLE, GREEN), 300, items()),
            // ---- M7：飞船武器和护盾（ADR-0017）
            Tech.lab("ship_weapons", List.of("structure_matrix", "thruster"), List.of(BLUE, RED, YELLOW), 160,
                    items("photon_cannon", "mining_beam")),
            Tech.lab("ship_shields", List.of("ship_weapons", "information_matrix"), List.of(BLUE, RED, YELLOW, PURPLE), 160,
                    items("shield_generator")),
            // ---- M7：探索车（ADR-0017）
            Tech.lab("exocraft", List.of("energy_storage", "processor"), List.of(BLUE, RED), 60, items("exocraft")),
            // ---- M14：载具（ADR-0026）。全地形载具解锁悬浮车、重型车和召唤台；潜艇和机甲接在它后面
            Tech.lab("all_terrain_vehicles", List.of("exocraft"), List.of(BLUE, RED), 80, items("hover_car", "heavy_rover", "vehicle_summoner")),
            Tech.lab("submarine", List.of("all_terrain_vehicles"), List.of(BLUE, RED), 80, items("submarine")),
            Tech.lab("exo_mech", List.of("all_terrain_vehicles"), List.of(BLUE, RED), 100, items("exo_mech")),
            // ---- M7：传送网络（ADR-0017）。基地信标不用研究
            Tech.lab("teleport_network", List.of("warp_drive"), List.of(BLUE, RED), 100, items("teleporter")),
            // ---- M13：飞船与航行（ADR-0024）。着陆台电磁学阶段就能研究；三档驱动接在跃迁引擎后面，一档比一档要的矩阵多
            Tech.handIn("landing_pad", List.of("electromagnetism"), List.of(cost("magnetic_coil", 8), cost("minecraft:iron_ingot", 16)),
                    items("landing_pad")),
            // ---- M14：星球生活（ADR-0026）。水培槽电磁学阶段就能研究，要交野外采来的氧气
            Tech.handIn("hydroponics", List.of("electromagnetism"), List.of(cost("oxygen", 12), cost("circuit_board", 6)),
                    items("hydroponic_tray")),
            // 基因编辑（ADR-0026 §九）：卵序列仪接在水培后面，要交异星兽皮
            Tech.handIn("gene_editing", List.of("hydroponics"), List.of(cost("alien_hide", 8), cost("circuit_board", 6)),
                    items("egg_sequencer")),
            // 多功能工具的武器（ADR-0026 §六）：等离子武器电磁学阶段就能研究，要交守卫机残骸；重武器要蓝矩阵
            Tech.handIn("plasma_weapons", List.of("electromagnetism"), List.of(cost("sentinel_scrap", 8), cost("magnetic_coil", 10)),
                    items("scatter_blaster", "pulse_spitter")),
            Tech.lab("heavy_plasma_weapons", List.of("plasma_weapons", "electromagnetic_matrix"), List.of(BLUE), 60,
                    items("blaze_javelin", "plasma_launcher")),
            Tech.lab("cadmium_drive", List.of("warp_drive", "structure_matrix"), List.of(BLUE, RED, YELLOW), 160, items("cadmium_drive")),
            Tech.lab("emeril_drive", List.of("cadmium_drive", "information_matrix"), List.of(BLUE, RED, YELLOW, PURPLE), 200,
                    items("emeril_drive")),
            Tech.lab("indium_drive", List.of("emeril_drive", "gravity_matrix"), List.of(BLUE, RED, YELLOW, PURPLE, GREEN), 300,
                    items("indium_drive")),
            // ---- M8：蓝图（ADR-0018），蓝矩阵阶段就能研究
            Tech.lab("blueprint", List.of("basic_assembling", "electromagnetic_drive"), List.of(BLUE), 30, items("blueprint")),
            // ---- M8：终局（ADR-0018）
            Tech.lab("mini_fusion_power", List.of("deuterium_fractionation", "information_matrix"), List.of(BLUE, RED, YELLOW, PURPLE), 200,
                    items("mini_fusion_power_plant")),
            Tech.lab("dirac_inversion", List.of("ray_receiver", "miniature_collider"), List.of(BLUE, RED, YELLOW, PURPLE, GREEN), 300,
                    items("antimatter")),
            Tech.lab("controlled_annihilation", List.of("dirac_inversion"), List.of(BLUE, RED, YELLOW, PURPLE, GREEN), 350,
                    items("annihilation_constraint_sphere", "antimatter_fuel_rod")),
            Tech.lab("artificial_star", List.of("controlled_annihilation", "mini_fusion_power"), List.of(BLUE, RED, YELLOW, PURPLE, GREEN), 400,
                    items("artificial_star")),
            Tech.lab("universe_matrix", List.of("dirac_inversion", "gravity_matrix"), List.of(BLUE, RED, YELLOW, PURPLE, GREEN), 500,
                    items("composite_matrix", "universe_matrix")),
            // 研究完就是通关
            Tech.lab("mission_complete", List.of("universe_matrix"), List.of(WHITE), 3000, items()),
            // ---- M16：防御（ADR-0028 §五）。高斯炮塔和机枪弹箱电磁学阶段就能研究，要上交物品；后面的按材料的档次挂
            Tech.handIn("gauss_turret", List.of("electromagnetism"), List.of(cost("magnetic_coil", 10), cost("gear", 10),
                    cost("minecraft:iron_ingot", 20)), items("gauss_turret", "magnum_ammo_box")),
            Tech.lab("explosives", List.of("gauss_turret", "basic_chemical"), List.of(BLUE, RED), 60, items("explosive_unit")),
            Tech.lab("laser_turret", List.of("gauss_turret", "energy_matrix"), List.of(BLUE, RED), 60, items("laser_turret")),
            Tech.lab("signal_tower", List.of("laser_turret", "wireless_power"), List.of(BLUE, RED), 80, items("signal_tower")),
            Tech.lab("titanium_ammo", List.of("gauss_turret", "titanium_alloy"), List.of(BLUE, RED, YELLOW), 120, items("titanium_ammo_box")),
            Tech.lab("missile_turret", List.of("laser_turret", "structure_matrix"), List.of(BLUE, RED, YELLOW), 120,
                    items("missile_turret", "missile_set")),
            Tech.lab("jammer_tower", List.of("signal_tower", "structure_matrix"), List.of(BLUE, RED, YELLOW), 100, items("jammer_tower")),
            Tech.lab("battlefield_analysis", List.of("signal_tower", "structure_matrix"), List.of(BLUE, RED, YELLOW), 120,
                    items("battlefield_analysis_base")),
            Tech.lab("crystal_explosives", List.of("explosives", "structure_matrix"), List.of(BLUE, RED, YELLOW), 120,
                    items("crystal_explosive_unit")),
            Tech.lab("superalloy_ammo", List.of("titanium_ammo", "information_matrix"), List.of(BLUE, RED, YELLOW, PURPLE), 180,
                    items("superalloy_ammo_box")),
            // ---- M16：黑雾（ADR-0028 §六）。交上黑雾的掉落才认得黑雾矩阵，后面的科技每点都要黑雾矩阵
            Tech.handIn("dark_fog_analysis", List.of("gauss_turret"), List.of(cost("energy_shard", 10), cost("dark_fog_matrix", 5)),
                    items("dark_fog_matrix")),
            Tech.lab("implosion_cannon", List.of("dark_fog_analysis", "energy_matrix"), List.of(BLUE, RED, DARK_FOG), 60,
                    items("implosion_cannon", "shell_set")),
            Tech.lab("high_explosive_shells", List.of("implosion_cannon", "explosives", "structure_matrix"), List.of(BLUE, RED, YELLOW, DARK_FOG),
                    100, items("high_explosive_shell_set")),
            Tech.lab("jamming_capsules", List.of("jammer_tower", "dark_fog_analysis"), List.of(BLUE, RED, YELLOW, DARK_FOG), 100,
                    items("jamming_capsule")),
            Tech.lab("supersonic_missiles", List.of("missile_turret", "dark_fog_analysis"), List.of(BLUE, RED, YELLOW, DARK_FOG), 120,
                    items("supersonic_missile_set")),
            Tech.lab("crystal_shells", List.of("high_explosive_shells", "crystal_explosives", "information_matrix"),
                    List.of(BLUE, RED, YELLOW, PURPLE, DARK_FOG), 160, items("crystal_shell_set")),
            // ---- M16 第二块（ADR-0028 §八）
            // 等离子炮塔要超音速导弹、量子打印（钛化玻璃）、氘分馏；近程的跟在后面；反物质胶囊要反物质
            Tech.lab("plasma_turret", List.of("supersonic_missiles", "quantum_printing", "deuterium_fractionation"),
                    List.of(BLUE, RED, YELLOW, PURPLE, DARK_FOG), 180, items("plasma_turret", "plasma_capsule")),
            Tech.lab("sr_plasma_turret", List.of("plasma_turret"), List.of(BLUE, RED, YELLOW, PURPLE, DARK_FOG), 160, items("sr_plasma_turret")),
            Tech.lab("antimatter_capsule", List.of("plasma_turret", "dirac_inversion"), List.of(BLUE, RED, YELLOW, PURPLE, GREEN, DARK_FOG), 240,
                    items("antimatter_capsule")),
            // M16 黑雾：等离子炮塔（T）的科技（每条末尾带逗号）加在这行上面
            // 行星护盾要干扰塔和信息矩阵：枪骑开始从太空来轰炸的时候
            Tech.lab("planetary_shield", List.of("jammer_tower", "information_matrix"), List.of(BLUE, RED, YELLOW, PURPLE), 200,
                    items("planetary_shield_generator")),
            // M16 黑雾：行星护盾（S）的科技（每条末尾带逗号）加在这行上面
            Tech.lab("gravity_missiles", List.of("supersonic_missiles", "gravity_matrix"), List.of(BLUE, RED, YELLOW, PURPLE, GREEN, DARK_FOG), 200,
                    items("gravity_missile_set"))),
            // 升级科技：前几级用低档矩阵，之后用白矩阵无限研究
            List.of(
                    new Upgrade(Upgrades.VEIN_UTILIZATION, NS + "mining_machine", List.of("electromagnetic_matrix"), List.of(
                            tier(40, BLUE), tier(80, BLUE, RED), tier(120, BLUE, RED, YELLOW), tier(200, BLUE, RED, YELLOW, PURPLE)),
                            new Infinite(List.of(WHITE), 300, 100)),
                    new Upgrade(Upgrades.RESEARCH_SPEED, NS + "matrix_lab", List.of("electromagnetic_matrix"), List.of(
                            tier(30, BLUE), tier(60, BLUE, RED), tier(150, BLUE, RED, YELLOW, PURPLE)),
                            new Infinite(List.of(WHITE), 250, 100)),
                    new Upgrade(Upgrades.CARRIER_ENGINE, NS + "logistics_drone", List.of("planetary_logistics"), List.of(
                            tier(40, BLUE, RED), tier(60, BLUE, RED), tier(100, BLUE, RED, YELLOW), tier(150, BLUE, RED, YELLOW, PURPLE),
                            tier(200, BLUE, RED, YELLOW, PURPLE, GREEN)),
                            new Infinite(List.of(WHITE), 300, 100)),
                    new Upgrade(Upgrades.CARRIER_CAPACITY, NS + "logistics_vessel", List.of("planetary_logistics"), List.of(
                            tier(40, BLUE, RED), tier(60, BLUE, RED), tier(100, BLUE, RED, YELLOW), tier(120, BLUE, RED, YELLOW),
                            tier(150, BLUE, RED, YELLOW, PURPLE), tier(200, BLUE, RED, YELLOW, PURPLE, GREEN)),
                            null),
                    new Upgrade(Upgrades.RAY_EFFICIENCY, NS + "ray_receiver", List.of("ray_receiver"), List.of(
                            tier(100, BLUE, RED, YELLOW, PURPLE), tier(150, BLUE, RED, YELLOW, PURPLE),
                            tier(200, BLUE, RED, YELLOW, PURPLE, GREEN), tier(300, BLUE, RED, YELLOW, PURPLE, GREEN)),
                            new Infinite(List.of(WHITE), 400, 150)),
                    new Upgrade(Upgrades.SAIL_LIFETIME, NS + "solar_sail", List.of("solar_sail_orbit"), List.of(
                            tier(60, BLUE, RED, YELLOW), tier(90, BLUE, RED, YELLOW), tier(120, BLUE, RED, YELLOW, PURPLE),
                            tier(160, BLUE, RED, YELLOW, PURPLE), tier(200, BLUE, RED, YELLOW, PURPLE, GREEN),
                            tier(300, BLUE, RED, YELLOW, PURPLE, GREEN)),
                            null),
                    // M11（ADR-0022）：集装机和集装分拣器一摞叠几个；星图上能看到多少星系的资源
                    new Upgrade(Upgrades.BELT_STACKING, NS + "automatic_piler", List.of("integrated_logistics"), List.of(
                            tier(150, BLUE, RED, YELLOW, PURPLE), tier(200, BLUE, RED, YELLOW, PURPLE, GREEN)),
                            null),
                    new Upgrade(Upgrades.UNIVERSE_EXPLORATION, NS + "survey_data", List.of("interstellar_survey"), List.of(
                            tier(40, BLUE), tier(80, BLUE, RED), tier(120, BLUE, RED, YELLOW), tier(200, BLUE, RED, YELLOW, PURPLE)),
                            null),
                    // M12（ADR-0023）：机甲
                    new Upgrade(Upgrades.MECHA_CORE, NS + "accumulator", List.of("electromagnetic_matrix"), List.of(
                            tier(40, BLUE), tier(80, BLUE, RED), tier(120, BLUE, RED, YELLOW), tier(160, BLUE, RED, YELLOW, PURPLE),
                            tier(200, BLUE, RED, YELLOW, PURPLE, GREEN)),
                            new Infinite(List.of(WHITE), 300, 100)),
                    new Upgrade(Upgrades.ENERGY_CIRCUIT, NS + "thermal_plant", List.of("electromagnetic_matrix"), List.of(
                            tier(40, BLUE), tier(80, BLUE, RED), tier(120, BLUE, RED, YELLOW), tier(160, BLUE, RED, YELLOW, PURPLE),
                            tier(200, BLUE, RED, YELLOW, PURPLE, GREEN)),
                            new Infinite(List.of(WHITE), 300, 100)),
                    new Upgrade(Upgrades.DRIVE_ENGINE, NS + "thruster", List.of("electromagnetic_drive"), List.of(
                            tier(40, BLUE), tier(80, BLUE, RED), tier(150, BLUE, RED, YELLOW), tier(200, BLUE, RED, YELLOW, PURPLE)),
                            null),
                    new Upgrade(Upgrades.MECHANICAL_FRAME, NS + "exosuit_leggings", List.of("electromagnetic_matrix"), List.of(
                            tier(30, BLUE), tier(60, BLUE), tier(90, BLUE, RED), tier(120, BLUE, RED), tier(150, BLUE, RED, YELLOW),
                            tier(200, BLUE, RED, YELLOW), tier(250, BLUE, RED, YELLOW, PURPLE), tier(300, BLUE, RED, YELLOW, PURPLE, GREEN)),
                            null),
                    new Upgrade(Upgrades.COMMUNICATION_CONTROL, NS + "construction_drone", List.of("blueprint"), List.of(
                            tier(40, BLUE), tier(80, BLUE, RED), tier(120, BLUE, RED, YELLOW), tier(160, BLUE, RED, YELLOW, PURPLE),
                            tier(200, BLUE, RED, YELLOW, PURPLE, GREEN)),
                            new Infinite(List.of(WHITE), 300, 100)),
                    new Upgrade(Upgrades.DRONE_ENGINE, NS + "electric_motor", List.of("blueprint"), List.of(
                            tier(40, BLUE), tier(80, BLUE, RED), tier(120, BLUE, RED, YELLOW), tier(160, BLUE, RED, YELLOW, PURPLE),
                            tier(200, BLUE, RED, YELLOW, PURPLE, GREEN)),
                            new Infinite(List.of(WHITE), 300, 100)),
                    new Upgrade(Upgrades.MASS_CONSTRUCTION, NS + "blueprint", List.of("blueprint"), List.of(
                            tier(60, BLUE), tier(120, BLUE, RED), tier(200, BLUE, RED, YELLOW), tier(300, BLUE, RED, YELLOW, PURPLE)),
                            null)));

    private final List<Tech> techs;
    private final Map<String, Tech> byId;
    private final Map<String, Integer> depth;
    private final List<Upgrade> upgrades;
    private final Map<String, Upgrade> upgradesById;

    public TechTree(List<Tech> techs) {
        this(techs, List.of());
    }

    public TechTree(List<Tech> techs, List<Upgrade> upgrades) {
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
        this.upgrades = List.copyOf(upgrades);
        Map<String, Upgrade> upgradesById = new LinkedHashMap<>();
        for (Upgrade upgrade : upgrades) {
            if (byId.containsKey(upgrade.id()) || upgradesById.put(upgrade.id(), upgrade) != null) {
                throw new IllegalArgumentException("duplicate tech " + upgrade.id());
            }
            for (String required : upgrade.requires()) {
                if (!byId.containsKey(required)) {
                    throw new IllegalArgumentException(upgrade.id() + " requires unknown tech " + required);
                }
            }
        }
        this.upgradesById = Collections.unmodifiableMap(upgradesById);
    }

    private static Tier tier(int points, String... matrices) {
        return new Tier(List.of(matrices), points);
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

    public List<Upgrade> upgrades() {
        return this.upgrades;
    }

    public Optional<Upgrade> upgrade(String id) {
        return Optional.ofNullable(this.upgradesById.get(id));
    }

    /** 哪个科技解锁了这个物品（研究站用的矩阵靠它找到"有没有这种矩阵"）；没有时为空。 */
    public @Nullable String unlockedBy(String item) {
        for (Tech tech : this.techs) {
            if (tech.unlocks().contains(item)) {
                return tech.id();
            }
        }
        return null;
    }

    /** 研究升级科技的第 level 级之前要完成的科技：这条线的前置，加上这一级用到的矩阵各自的科技。 */
    public List<String> requirements(Upgrade upgrade, int level) {
        List<String> ids = new ArrayList<>(upgrade.requires());
        for (String matrix : upgrade.matrices(level)) {
            String tech = this.unlockedBy(matrix);
            if (tech != null && !ids.contains(tech)) {
                ids.add(tech);
            }
        }
        return ids;
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
