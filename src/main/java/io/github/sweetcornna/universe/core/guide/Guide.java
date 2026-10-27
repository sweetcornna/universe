package io.github.sweetcornna.universe.core.guide;

import java.util.List;

/**
 * 手册的目录（ADR-0018）：章节、每章的页、每页嵌的物品和配方。文字在语言文件里：章名 {@code guide.universe.<章>}，
 * 页标题 {@code guide.universe.<章>.<页>.title}，正文 {@code guide.universe.<章>.<页>}。
 *
 * <p>"日志"和"结局"两章的页是客户端按剧情进度现做的，这里只占一个位置。
 */
public final class Guide {
    public static final String LOG = "log";
    public static final String ENDING = "ending";

    /**
     * 一页。
     *
     * @param items  正文下面一排物品图标（注册名）
     * @param recipe 要画的配方：配方的 ID，找不到这个配方时当成物品注册名，画产出它的第一个配方；空串不画
     */
    public record Page(String id, List<String> items, String recipe) {
    }

    public record Chapter(String id, String icon, List<Page> pages) {
    }

    public static final List<Chapter> CHAPTERS = List.of(
            chapter("start", "universe:damaged_multitool",
                    page("crash", "", "universe:damaged_multitool", "universe:oxygen_capsule", "universe:hazard_cell"),
                    page("multitool", "universe:multitool_from_repair"),
                    page("laser", "", "universe:multitool"),
                    page("tools", "", "universe:multitool_cabinet", "universe:sentinel_scrap"),
                    page("suit", "universe:exosuit_chestplate", "universe:exosuit_helmet", "universe:exosuit_chestplate",
                            "universe:exosuit_leggings", "universe:exosuit_boots")),
            chapter("power", "universe:wind_turbine",
                    page("grid", "universe:tesla_tower"),
                    page("generators", "", "universe:wind_turbine", "universe:solar_panel", "universe:thermal_plant"),
                    page("storage", "universe:accumulator"),
                    page("towers", "", "universe:tesla_tower", "universe:wireless_power_tower", "universe:satellite_substation"),
                    page("geothermal", "universe:geothermal_power_station"),
                    page("exchanger", "", "universe:energy_exchanger", "universe:accumulator"),
                    // 和别的模组接电、接管道（ADR-0030 §三）
                    page("interface", "universe:grid_interface")),
            chapter("gathering", "universe:mining_machine",
                    page("mining", "universe:mining_machine"),
                    page("veins", "", "universe:planet_iron_ore", "universe:titanium_ore", "universe:silicon_ore", "universe:fire_ice_ore",
                            "universe:optical_crystal_ore", "universe:uranium_ore"),
                    // 枯竭提醒、按矿种扫描（ADR-0033 §八）
                    page("depletion", "", "universe:mining_machine", "universe:advanced_mining_machine"),
                    page("scan_filter", "", "universe:multitool"),
                    page("smelting", "universe:arc_smelter"),
                    page("fluids", "", "universe:oil_extractor", "universe:water_pump")),
            chapter("research", "universe:matrix_lab",
                    page("hand_in", "", "universe:magnetic_coil", "universe:circuit_board", "universe:gear", "universe:survey_data"),
                    page("lab", "universe:matrix_lab"),
                    page("matrices", "universe:electromagnetic_matrix", "universe:electromagnetic_matrix", "universe:energy_matrix",
                            "universe:structure_matrix", "universe:information_matrix", "universe:gravity_matrix", "universe:universe_matrix"),
                    page("upgrades", ""),
                    // 研究队列（ADR-0033 §五）
                    page("queue", ""),
                    page("stacking", "universe:self_evolution_lab", "universe:matrix_lab", "universe:self_evolution_lab"),
                    // 统计面板和仪表盘（ADR-0033 §四）
                    page("stats", ""),
                    page("dashboard", "")),
            chapter("logistics", "universe:conveyor_belt",
                    page("belts", "universe:conveyor_belt", "universe:conveyor_belt", "universe:conveyor_belt_mk2", "universe:conveyor_belt_mk3"),
                    page("sorters", "universe:sorter", "universe:sorter", "universe:splitter", "universe:storage_depot"),
                    page("splitter", "", "universe:splitter", "universe:storage_depot"),
                    page("proliferator", "", "universe:spray_coater", "universe:proliferator_mk1", "universe:proliferator_mk2",
                            "universe:proliferator_mk3"),
                    page("piling", "", "universe:automatic_piler", "universe:pile_sorter", "universe:traffic_monitor"),
                    page("assembler", "universe:assembler", "universe:assembler", "universe:assembler_mk2", "universe:assembler_mk3"),
                    page("stations", "", "universe:planetary_logistics_station", "universe:interstellar_logistics_station",
                            "universe:logistics_drone", "universe:logistics_vessel"),
                    // 运输船在恒星系的太空里看得见（ADR-0030 §五）
                    page("vessels", "", "universe:logistics_vessel", "universe:space_warper"),
                    // 五层配对、物品路线和控制面板（ADR-0033 §三）
                    page("dispatch", "", "universe:interstellar_logistics_station", "universe:planetary_logistics_station"),
                    page("control_panel", "", "universe:interstellar_logistics_station", "universe:logistics_distributor"),
                    page("control_routes", ""),
                    page("distributor", "universe:logistics_distributor", "universe:logistics_distributor", "universe:logistics_bot"),
                    page("slots", ""),
                    page("blueprint", "universe:blueprint"),
                    // 作者、版本、只贴设备、材料清单（ADR-0033 §九）
                    page("blueprint_devices", "", "universe:blueprint", "universe:conveyor_belt", "universe:sorter"),
                    page("drones", "", "universe:blueprint", "universe:construction_drone")),
            chapter("ship", "universe:ship_helm",
                    page("build", "", "universe:ship_helm", "universe:ship_thruster", "universe:impulse_engine", "universe:fuel_tank",
                            "universe:hull_plating_light_gray"),
                    page("fly", ""),
                    page("warp", "", "universe:warp_drive", "universe:space_warper"),
                    // M13（ADR-0024）
                    page("rating", "", "universe:module_thrust_efficiency", "universe:module_pulse_tuning", "universe:module_hyperdrive_booster",
                            "universe:module_deflector", "universe:module_hull_reinforcement", "universe:module_fire_control"),
                    page("drives", "", "universe:cadmium_drive", "universe:emeril_drive", "universe:indium_drive"),
                    page("black_hole", ""),
                    // M17（ADR-0030 §六）
                    page("cargo", ""),
                    page("dealer", "", "universe:ship_dealer", "universe:landing_pad"),
                    page("freighter", "", "universe:freighter_console"),
                    page("fleet", "", "universe:fleet_terminal"),
                    page("encounters", ""),
                    page("space_sentinels", "", "universe:emergency_receiver", "universe:credit_chip")),
            chapter("survival", "universe:exosuit_helmet",
                    page("hazards", "universe:hazard_cell", "universe:oxygen_capsule", "universe:hazard_cell"),
                    // 酸海和酸雨（ADR-0030 §一）
                    page("acid", "", "universe:acid_bucket", "universe:sulfuric_acid", "universe:water_pump"),
                    // 间歇泉、火山喷发、孢子云、尘卷风（ADR-0034 §六）
                    page("local_hazards", "", "universe:hot_geyser_crust", "universe:sulfur", "universe:salt", "universe:frost_crystal"),
                    // M14（ADR-0026）
                    page("resources", "", "universe:oxygen", "universe:sodium", "universe:star_bulb", "universe:solanium",
                            "universe:cactus_flesh", "universe:marrow_bulb"),
                    page("hydroponics", "universe:hydroponic_tray"),
                    page("cooking", "universe:nutrient_processor", "universe:nutrient_processor", "universe:star_pie", "universe:custard",
                            "universe:spicy_roast", "universe:cactus_juice"),
                    page("refiner", "universe:portable_refiner", "universe:portable_refiner", "universe:cold_gel", "universe:heat_gel",
                            "universe:radiation_gel", "universe:toxin_gel"),
                    page("border", ""),
                    page("sentinels", ""),
                    page("weapons", "", "universe:scatter_blaster", "universe:pulse_spitter", "universe:blaze_javelin", "universe:plasma_launcher"),
                    page("modules", "", "universe:drop_pod"),
                    // 相邻和超频（ADR-0034 §五）
                    page("tech_layout", ""),
                    page("core", "", "universe:exosuit_chestplate", "universe:combustible_unit", "universe:hydrogen_fuel_rod"),
                    page("flight", ""),
                    // M18 机甲的舰队和战斗（ADR-0033 §一、§二）
                    page("fleet", "universe:prototype", "universe:prototype", "universe:precision_drone", "universe:attack_drone"),
                    page("fleet_field", ""),
                    page("space_fleet", "universe:corvette", "universe:corvette", "universe:destroyer"),
                    page("mecha_weapons", "", "universe:magnum_ammo_box", "universe:missile_set", "universe:plasma_capsule"),
                    page("shield_burst", "", "universe:jamming_capsule")),
            chapter("exploration", "universe:survey_data",
                    page("visor", ""),
                    page("discoveries", "", "universe:survey_data"),
                    page("poi", "universe:signal_booster", "universe:monolith", "universe:data_terminal", "universe:tech_cache",
                            "universe:navigation_data"),
                    page("bases", "", "universe:base_beacon", "universe:teleporter"),
                    // 全息信标和备忘（ADR-0033 §六）
                    page("holo_beacon", "universe:holo_beacon", "universe:holo_beacon"),
                    page("beacon_range", ""),
                    page("memos", ""),
                    page("portals", "", "universe:portal_dial", "universe:portal_glyph"),
                    page("pillars", "", "universe:sentinel_pillar_terminal", "universe:sentinel_pillar_light"),
                    // 奇异星球的六种地貌（ADR-0030 §二）
                    page("exotic", "", "universe:planet_crystal", "universe:planet_strata", "universe:planet_rock"),
                    page("exotic_more", ""),
                    // 巨型生物（ADR-0026 §九）
                    page("giants", "", "universe:herbivore_feed", "universe:carnivore_feed"),
                    // 水下（ADR-0026）
                    page("underwater", "", "universe:kelp_pod", "universe:clam", "universe:living_pearl", "universe:alien_fish"),
                    page("submarine", "universe:submarine"),
                    page("wrecks", "", "universe:sealed_crate", "universe:living_pearl"),
                    // 野外的地标（ADR-0026）
                    page("fossils", "", "universe:fossil_dirt", "universe:shell_fossil", "universe:arthropod_fossil", "universe:skull_fossil"),
                    page("salvage", "", "universe:salvage_capsule", "universe:salvaged_scrap", "universe:data_terminal", "universe:navigation_data"),
                    page("fulgurite", "", "universe:fulgurite"),
                    // 有人的地标（ADR-0026）
                    page("trading_post", "", "universe:outpost_trade_terminal", "universe:landing_pad"),
                    page("facility", "", "universe:reinforced_door", "universe:facility_terminal"),
                    page("observatory", "", "universe:observatory_terminal")),
            chapter("trade", "universe:circuit_board",
                    page("stations", ""),
                    page("trading", ""),
                    page("missions", ""),
                    page("pirates", "", "universe:photon_cannon", "universe:shield_generator")),
            // 外星文明（ADR-0027）
            chapter("civilization", "universe:portal_glyph",
                    page("races", ""),
                    page("language", ""),
                    page("standing", ""),
                    // 对话（ADR-0027 §四）
                    page("dialogue", ""),
                    // 技术商（ADR-0027 §六）
                    page("merchant", ""),
                    // 公会（ADR-0027 §六）
                    page("guilds", ""),
                    // 知识石和聚落（ADR-0027 §五）
                    page("stones", "", "universe:knowledge_stone"),
                    page("settlements", "", "universe:outpost_trade_terminal", "universe:knowledge_stone")),
            chapter("husbandry", "universe:herbivore_feed",
                    page("taming", "universe:herbivore_feed", "minecraft:wheat", "universe:herbivore_feed", "minecraft:beef",
                            "universe:carnivore_feed"),
                    page("produce", "", "universe:alien_egg", "universe:alien_fluff", "universe:bio_gel"),
                    // 伙伴（ADR-0026 §九）
                    page("companions", "", "universe:herbivore_feed", "universe:carnivore_feed", "universe:survey_data"),
                    // 生物卵和卵序列仪（ADR-0026 §九）
                    page("eggs", "", "universe:creature_egg"),
                    page("sequencer", "universe:egg_sequencer"),
                    page("exocraft", "universe:exocraft"),
                    // 别的载具和召唤台（ADR-0026）
                    page("vehicles", "", "universe:hover_car", "universe:heavy_rover", "universe:exo_mech"),
                    page("summoner", "universe:vehicle_summoner")),
            chapter("dyson", "universe:solar_sail",
                    page("sails", "universe:em_rail_ejector", "universe:solar_sail"),
                    page("editor", ""),
                    // 自己设计戴森壳（M17，ADR-0030 §四）
                    page("design", ""),
                    page("design_share", ""),
                    page("rockets", "universe:small_carrier_rocket", "universe:vertical_launching_silo", "universe:dyson_sphere_component"),
                    page("receiver", "universe:ray_receiver", "universe:critical_photon", "universe:graviton_lens", "universe:dark_fog_lens"),
                    // 黑雾透镜（ADR-0033 §七）
                    page("dark_fog_lens", "universe:dark_fog_lens", "universe:dark_fog_lens", "universe:graviton_lens")),
            chapter("endgame", "universe:universe_matrix",
                    page("antimatter", "universe:antimatter", "universe:miniature_particle_collider"),
                    page("power", "", "universe:mini_fusion_power_plant", "universe:artificial_star", "universe:antimatter_fuel_rod"),
                    page("advanced", "universe:recomposing_assembler", "universe:negentropy_smelter", "universe:recomposing_assembler",
                            "universe:quantum_chemical_plant", "universe:self_evolution_lab"),
                    page("white", "universe:universe_matrix"),
                    page("finale", "")),
            // 黑雾（ADR-0028）
            chapter("dark_fog", "universe:dark_fog_matrix",
                    page("bases", ""),
                    page("attacks", ""),
                    page("turrets", "", "universe:gauss_turret", "universe:laser_turret", "universe:implosion_cannon", "universe:missile_turret"),
                    page("ammo", "", "universe:magnum_ammo_box", "universe:shell_set", "universe:missile_set"),
                    page("plasma", "", "universe:plasma_turret", "universe:sr_plasma_turret", "universe:plasma_capsule",
                            "universe:antimatter_capsule"),
                    page("support", "", "universe:signal_tower", "universe:jammer_tower", "universe:explosive_unit"),
                    page("shield", "universe:planetary_shield_generator"),
                    page("damage", ""),
                    page("repair", "universe:battlefield_analysis_base"),
                    page("hive", ""),
                    page("lancers", ""),
                    page("relays", ""),
                    page("seeds", ""),
                    page("loot", "", "universe:energy_shard", "universe:silicon_neuron", "universe:matter_recombinator",
                            "universe:negentropy_singularity", "universe:core_element", "universe:dark_fog_matrix"),
                    page("world_options", "")),
            // 钓鱼（ADR-0034 §一）
            chapter("fishing", "universe:fishing_rig",
                    page("rig", "universe:fishing_rig", "universe:fishing_rig", "universe:multitool"),
                    page("reeling", ""),
                    page("waters", ""),
                    page("conditions", "", "universe:message_bottle", "universe:sealed_cache"),
                    page("bait", "universe:stink_bait", "universe:stink_bait", "universe:spinner_lure", "universe:deep_sinker", "universe:glow_bait",
                            "universe:static_lure", "universe:star_core_bait"),
                    page("release_trap", "universe:fish_trap"),
                    page("seafood", "universe:deep_sea_stew", "universe:grilled_fillet", "universe:fish_soup", "universe:sashimi_platter",
                            "universe:deep_sea_stew", "universe:ice_fish_soup", "universe:legendary_feast")),
            // 管理聚落（ADR-0034 §二）
            chapter("settlement", "minecraft:bell",
                    page("claim", "", "universe:steel", "universe:carbon_nanotube", "universe:circuit_board"),
                    page("construction", ""),
                    page("buildings", ""),
                    page("days", ""),
                    page("products", ""),
                    page("decisions", ""),
                    page("raids", "")),
            // 古生物（ADR-0034 §三）
            chapter("paleontology", "universe:skeleton_display",
                    page("bones", "", "universe:fossil_dirt", "universe:bone_titan_skull", "universe:bone_predator_ribcage", "universe:bone_flyer_limbs",
                            "universe:bone_worm_limbs"),
                    page("displays", "universe:skeleton_display", "universe:fossil_pedestal", "universe:skeleton_display", "universe:fossil_exhibit"),
                    page("collectors", "", "universe:fossil_exhibit"),
                    page("dig_sites", "", "universe:fossil_dirt")),
            // 深空和引力抓取器（ADR-0034 §四、§七）
            chapter("deep_space", "universe:salvaged_reactor_core",
                    page("sites", "", "universe:scrap_metal", "universe:alloy_plate", "universe:salvaged_electronics"),
                    page("hulks", "", "universe:salvaged_reactor_core"),
                    page("tractor", "universe:module_tractor_beam", "universe:module_tractor_beam"),
                    page("eva", ""),
                    page("gravity_coil", "universe:gravity_coil")),
            // 化学（ADR-0036 §一–§四）
            chapter("chemistry", "universe:electrolyzer",
                    page("substances", "", "universe:raw_titanium", "minecraft:raw_copper", "universe:salt"),
                    page("atmosphere", "", "universe:nitrogen", "universe:oxygen", "universe:carbon_dioxide", "universe:argon", "universe:ammonia"),
                    page("extractor", "universe:atmospheric_extractor", "universe:atmospheric_extractor", "universe:nitrogen", "universe:carbon_dioxide",
                            "universe:argon"),
                    page("electrolysis", "universe:electrolysis/water", "universe:electrolyzer", "universe:hydrogen", "universe:oxygen",
                            "universe:carbon_monoxide"),
                    page("salt", "universe:electrolysis/brine", "universe:salt", "universe:chlorine", "universe:sodium", "universe:sodium_hydroxide",
                            "universe:copper_leach_solution"),
                    page("titanium", "universe:chemical/hunter_titanium", "universe:titanium_tetrachloride", "universe:sodium", "universe:vanadium_slag"),
                    page("nitrates", "universe:chemical/nitric_acid", "universe:ammonia", "universe:nitric_acid", "universe:ammonium_nitrate",
                            "universe:hydrogen_cyanide"),
                    page("methalox", "universe:chemical/methalox_fuel", "universe:methane", "universe:methalox_fuel"),
                    page("reactor", "universe:reactor/haber", "universe:chemical_reactor", "universe:ammonia", "universe:methane"),
                    page("reactions", "", "universe:ammonia", "universe:sulfuric_acid", "universe:methane", "universe:carbon_monoxide",
                            "universe:refined_oil"),
                    page("equilibrium", "", "universe:chemical_reactor"),
                    page("conditions", ""),
                    page("catalysts", "", "universe:iron_catalyst", "universe:vanadium_catalyst", "universe:nickel_catalyst")),
            // 微生物和基因、生物反应器（ADR-0036 §五）
            chapter("biology", "universe:sequencer",
                    page("sampling", "", "universe:sample_tube", "universe:sample"),
                    page("microbiomes", "", "universe:sample"),
                    page("incubator", "universe:incubator", "universe:incubator", "universe:sample", "universe:culture_medium"),
                    page("expanding", "universe:nutrient/culture_medium", "universe:strain_tube", "universe:culture_medium", "universe:agar"),
                    page("sequencer", "universe:sequencer", "universe:sequencer", "universe:sequencing_chip", "universe:strain_tube"),
                    page("gene_library", "universe:assembling/sequencing_chip", "universe:sequencing_chip", "universe:survey_data"),
                    page("genes", ""),
                    page("editing", "universe:gene_editor", "universe:gene_editor", "universe:editing_kit", "universe:engineered_seedling"),
                    page("tray", "", "universe:engineered_seedling", "universe:hydroponic_tray"),
                    page("fertilizer", "universe:chemical/ammonium_nitrate", "universe:ammonium_nitrate", "universe:hydroponic_tray"),
                    page("bioreactor", "universe:bioreactor", "universe:bioreactor", "universe:strain_tube"),
                    page("culture", "", "universe:bioreactor"),
                    page("processes", "universe:bioreactor/bioleaching_copper", "universe:copper_leach_solution", "universe:gold_leach_solution",
                            "universe:nickel_ingot"),
                    page("algae", "universe:bioreactor/algae", "universe:algal_biomass", "universe:oxygen"),
                    page("fixation", "universe:bioreactor/nitrogen_fixation", "universe:ammonia", "universe:methane", "universe:plastic")),
            // 星球改造（ADR-0036 §六）
            chapter("terraforming", "universe:atmosphere_release_tower",
                    page("thresholds", ""),
                    page("release_tower", "universe:atmosphere_release_tower", "universe:atmosphere_release_tower", "universe:nitrogen",
                            "universe:oxygen", "universe:methane"),
                    page("mirrors", "universe:orbital_mirror", "universe:orbital_mirror"),
                    page("seeder", "universe:ecology_seeder", "universe:ecology_seeder", "universe:strain_tube", "universe:engineered_seedling"),
                    page("pioneers", "", "universe:strain_tube", "universe:engineered_seedling"),
                    page("biosphere", ""),
                    page("cleanup", ""),
                    page("example", "")),
            chapter(LOG, "minecraft:writable_book"),
            chapter(ENDING, "minecraft:nether_star"));

    private Guide() {
    }

    private static Chapter chapter(String id, String icon, Page... pages) {
        return new Chapter(id, icon, List.of(pages));
    }

    private static Page page(String id, String recipe, String... items) {
        return new Page(id, List.of(items), recipe);
    }

    public static Chapter chapter(String id) {
        return CHAPTERS.stream().filter(chapter -> chapter.id().equals(id)).findFirst().orElseThrow();
    }
}
