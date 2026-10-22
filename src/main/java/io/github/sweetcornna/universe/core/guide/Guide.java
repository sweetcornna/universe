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
                    page("suit", "universe:exosuit_chestplate", "universe:exosuit_helmet", "universe:exosuit_chestplate",
                            "universe:exosuit_leggings", "universe:exosuit_boots")),
            chapter("power", "universe:wind_turbine",
                    page("grid", "universe:tesla_tower"),
                    page("generators", "", "universe:wind_turbine", "universe:solar_panel", "universe:thermal_plant"),
                    page("storage", "universe:accumulator"),
                    page("towers", "", "universe:tesla_tower", "universe:wireless_power_tower", "universe:satellite_substation"),
                    page("geothermal", "universe:geothermal_power_station"),
                    page("exchanger", "", "universe:energy_exchanger", "universe:accumulator")),
            chapter("gathering", "universe:mining_machine",
                    page("mining", "universe:mining_machine"),
                    page("veins", "", "universe:planet_iron_ore", "universe:titanium_ore", "universe:silicon_ore", "universe:fire_ice_ore",
                            "universe:optical_crystal_ore", "universe:uranium_ore"),
                    page("smelting", "universe:arc_smelter"),
                    page("fluids", "", "universe:oil_extractor", "universe:water_pump")),
            chapter("research", "universe:matrix_lab",
                    page("hand_in", "", "universe:magnetic_coil", "universe:circuit_board", "universe:gear", "universe:survey_data"),
                    page("lab", "universe:matrix_lab"),
                    page("matrices", "universe:electromagnetic_matrix", "universe:electromagnetic_matrix", "universe:energy_matrix",
                            "universe:structure_matrix", "universe:information_matrix", "universe:gravity_matrix", "universe:universe_matrix"),
                    page("upgrades", ""),
                    page("stacking", "universe:self_evolution_lab", "universe:matrix_lab", "universe:self_evolution_lab")),
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
                    page("distributor", "universe:logistics_distributor", "universe:logistics_distributor", "universe:logistics_bot"),
                    page("slots", ""),
                    page("blueprint", "universe:blueprint"),
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
                    page("dealer", "", "universe:ship_dealer", "universe:landing_pad"),
                    page("freighter", "", "universe:freighter_console"),
                    page("fleet", "", "universe:fleet_terminal"),
                    page("encounters", ""),
                    page("space_sentinels", "", "universe:emergency_receiver", "universe:credit_chip")),
            chapter("survival", "universe:exosuit_helmet",
                    page("hazards", "universe:hazard_cell", "universe:oxygen_capsule", "universe:hazard_cell"),
                    // M14（ADR-0026）
                    page("resources", "", "universe:oxygen", "universe:sodium", "universe:star_bulb", "universe:solanium",
                            "universe:cactus_flesh", "universe:marrow_bulb"),
                    page("hydroponics", "universe:hydroponic_tray"),
                    page("cooking", "universe:nutrient_processor", "universe:nutrient_processor", "universe:star_pie", "universe:custard",
                            "universe:spicy_roast", "universe:cactus_juice"),
                    page("refiner", "universe:portable_refiner", "universe:portable_refiner", "universe:cold_gel", "universe:heat_gel",
                            "universe:radiation_gel", "universe:toxin_gel"),
                    page("border", ""),
                    page("modules", "", "universe:drop_pod"),
                    page("core", "", "universe:exosuit_chestplate", "universe:combustible_unit", "universe:hydrogen_fuel_rod"),
                    page("flight", "")),
            chapter("exploration", "universe:survey_data",
                    page("visor", ""),
                    page("discoveries", "", "universe:survey_data"),
                    page("poi", "universe:signal_booster", "universe:monolith", "universe:data_terminal", "universe:tech_cache",
                            "universe:navigation_data"),
                    page("bases", "", "universe:base_beacon", "universe:teleporter"),
                    page("portals", "", "universe:portal_dial", "universe:portal_glyph")),
            chapter("trade", "universe:circuit_board",
                    page("stations", ""),
                    page("trading", ""),
                    page("missions", ""),
                    page("pirates", "", "universe:photon_cannon", "universe:shield_generator")),
            chapter("husbandry", "universe:herbivore_feed",
                    page("taming", "universe:herbivore_feed", "minecraft:wheat", "universe:herbivore_feed", "minecraft:beef",
                            "universe:carnivore_feed"),
                    page("produce", "", "universe:alien_egg", "universe:alien_fluff", "universe:bio_gel"),
                    page("exocraft", "universe:exocraft")),
            chapter("dyson", "universe:solar_sail",
                    page("sails", "universe:em_rail_ejector", "universe:solar_sail"),
                    page("editor", ""),
                    page("rockets", "universe:small_carrier_rocket", "universe:vertical_launching_silo", "universe:dyson_sphere_component"),
                    page("receiver", "universe:ray_receiver", "universe:critical_photon", "universe:graviton_lens")),
            chapter("endgame", "universe:universe_matrix",
                    page("antimatter", "universe:antimatter", "universe:miniature_particle_collider"),
                    page("power", "", "universe:mini_fusion_power_plant", "universe:artificial_star", "universe:antimatter_fuel_rod"),
                    page("advanced", "universe:recomposing_assembler", "universe:negentropy_smelter", "universe:recomposing_assembler",
                            "universe:quantum_chemical_plant", "universe:self_evolution_lab"),
                    page("white", "universe:universe_matrix"),
                    page("finale", "")),
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
