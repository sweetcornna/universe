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
    /** 研究站认的矩阵，顺序就是研究站格子的顺序。以后加新的矩阵只能往后加，免得存档里的格子对不上。 */
    public static final List<String> MATRICES = List.of(BLUE, RED);

    /** M3 的科技：电磁学阶段上交物品，之后用蓝矩阵、再用蓝红两种矩阵研究。 */
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
            Tech.lab("fluid_storage", List.of("electromagnetic_matrix"), List.of(BLUE), 20, items("water_pump")),
            Tech.lab("electromagnetic_drive", List.of("electromagnetic_matrix"), List.of(BLUE), 20, items("electric_motor")),
            Tech.lab("thermal_power", List.of("electromagnetic_matrix"), List.of(BLUE), 20, items("thermal_plant")),
            Tech.lab("steel_smelting", List.of("electromagnetic_matrix"), List.of(BLUE), 30, items("steel")),
            Tech.lab("plasma_refining", List.of("fluid_storage", "electromagnetic_drive", "steel_smelting"), List.of(BLUE), 60,
                    items("oil_extractor", "oil_refinery", "prism", "plasma_exciter")),
            Tech.lab("energy_matrix", List.of("plasma_refining", "thermal_power"), List.of(BLUE), 60, items("energy_matrix")),
            Tech.lab("energy_storage", List.of("energy_matrix"), List.of(BLUE, RED), 30, items("accumulator")),
            Tech.lab("x_ray_cracking", List.of("energy_matrix"), List.of(BLUE, RED), 30, items("energetic_graphite")),
            Tech.lab("warp_drive", List.of("energy_storage", "x_ray_cracking"), List.of(BLUE, RED), 60,
                    items("warp_drive", "space_warper"))));

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
