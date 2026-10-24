package io.github.sweetcornna.universe.core.settlement;

import static io.github.sweetcornna.universe.core.settlement.Cost.GLASS;
import static io.github.sweetcornna.universe.core.settlement.Cost.STONE;
import static io.github.sweetcornna.universe.core.settlement.Cost.TIMBER;

import java.util.List;
import java.util.Locale;

/**
 * 聚落的建筑（ADR-0034 §二）：占地、作用和三段施工的材料。作用按档次（{@link #factor}）放大。按名字存档，只往后加。
 *
 * <p>占地是半宽：{@code halfX}、{@code halfZ} 都按建筑自己的朝向算，门在本地的 -z 那一面，朝着聚落中间。
 */
public enum BuildingType {
    /** 总督办公室：第一个盖，有了它才有管理界面和每天的决定。 */
    OFFICE(2, 2, 0, 0, 0, 0, 0, 0, List.of(
            List.of(Cost.of(STONE, 32)),
            List.of(Cost.of(TIMBER, 24), Cost.of("steel", 8)),
            List.of(Cost.of(GLASS, 8), Cost.of("circuit_board", 4)))),
    /** 住宅：住房 +3。 */
    HABITATION(2, 2, 3, 0, 0, 0, 0, 0, List.of(
            List.of(Cost.of(STONE, 24)),
            List.of(Cost.of(TIMBER, 32)),
            List.of(Cost.of(GLASS, 6), Cost.of("steel", 4)))),
    /** 农场：生产力 +10，能出食物。 */
    FARM(3, 3, 0, 10, 0, 0, 0, 0, List.of(
            List.of(Cost.of(STONE, 16), Cost.of(TIMBER, 8)),
            List.of(Cost.of(TIMBER, 24), Cost.of("steel", 4)),
            List.of(Cost.of("minecraft:bone_meal", 16), Cost.of("gear", 4)))),
    /** 工坊：生产力 +15，能出零件。 */
    WORKSHOP(3, 2, 0, 15, 0, 0, 0, 0, List.of(
            List.of(Cost.of(STONE, 32)),
            List.of(Cost.of(TIMBER, 24), Cost.of("steel", 12)),
            List.of(Cost.of("gear", 8), Cost.of("electric_motor", 2)))),
    /** 酒馆：幸福 +12。 */
    SALOON(3, 3, 0, 0, 12, 0, 0, 0, List.of(
            List.of(Cost.of(STONE, 24)),
            List.of(Cost.of(TIMBER, 32), Cost.of("steel", 4)),
            List.of(Cost.of(GLASS, 12), Cost.of("circuit_board", 2)))),
    /** 市场：每天收一笔信用点。 */
    MARKET(3, 3, 0, 0, 3, 150, 0, 0, List.of(
            List.of(Cost.of(STONE, 32)),
            List.of(Cost.of(TIMBER, 32), Cost.of("steel", 6)),
            List.of(Cost.of(GLASS, 6), Cost.of("circuit_board", 4), Cost.of("magnetic_coil", 4)))),
    /** 瞭望塔：每天一份附近兴趣点的导航数据，威胁每天 −5。 */
    WATCHTOWER(1, 1, 0, 0, 0, 0, 5, 10, List.of(
            List.of(Cost.of(STONE, 24)),
            List.of(Cost.of(TIMBER, 24), Cost.of("steel", 8)),
            List.of(Cost.of(GLASS, 4), Cost.of("magnetic_coil", 4), Cost.of("circuit_board", 2)))),
    /** 防御炮台：海盗来时朝它们开火，威胁每天 −3。 */
    TURRET(1, 1, 0, 0, 0, 0, 3, 25, List.of(
            List.of(Cost.of(STONE, 24), Cost.of("steel", 8)),
            List.of(Cost.of("steel", 16), Cost.of("gear", 8)),
            List.of(Cost.of("electric_motor", 2), Cost.of("circuit_board", 4), Cost.of("plasma_exciter", 2)))),
    /** 着陆坪：能停船，生产力 +5。 */
    LANDING_PAD(4, 4, 0, 5, 0, 0, 0, 0, List.of(
            List.of(Cost.of(STONE, 64)),
            List.of(Cost.of("steel", 16), Cost.of("gear", 4)),
            List.of(Cost.of("circuit_board", 4), Cost.of("magnetic_coil", 8)))),
    /** 鱼塘：每天出鱼。 */
    FISHING_POND(3, 3, 0, 0, 2, 0, 0, 0, List.of(
            List.of(Cost.of(STONE, 24)),
            List.of(Cost.of(TIMBER, 16)),
            List.of(Cost.of(GLASS, 4), Cost.of("gear", 2))));

    /** 档次：C、B、A、S。 */
    public static final int GRADES = 4;
    private static final double[] FACTORS = {1, 1.5, 2, 3};
    private static final String[] LETTERS = {"C", "B", "A", "S"};
    /** 施工分几段。 */
    public static final int STAGES = 3;

    private final int halfX;
    private final int halfZ;
    private final int housing;
    private final int productivity;
    private final int happiness;
    private final int income;
    private final int threatRelief;
    private final int defense;
    private final List<List<Cost>> stages;

    BuildingType(int halfX, int halfZ, int housing, int productivity, int happiness, int income, int threatRelief, int defense,
            List<List<Cost>> stages) {
        this.halfX = halfX;
        this.halfZ = halfZ;
        this.housing = housing;
        this.productivity = productivity;
        this.happiness = happiness;
        this.income = income;
        this.threatRelief = threatRelief;
        this.defense = defense;
        this.stages = stages;
    }

    /** 存档、语言键里用的名字。 */
    public String id() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public static BuildingType byId(String id) {
        return valueOf(id.toUpperCase(Locale.ROOT));
    }

    public int halfX() {
        return this.halfX;
    }

    public int halfZ() {
        return this.halfZ;
    }

    /** 这一档的作用倍数：×1、×1.5、×2、×3。 */
    public static double factor(int grade) {
        return FACTORS[Math.clamp(grade, 0, GRADES - 1)];
    }

    public static String letter(int grade) {
        return LETTERS[Math.clamp(grade, 0, GRADES - 1)];
    }

    public double housing(int grade) {
        return this.housing * factor(grade);
    }

    public double productivity(int grade) {
        return this.productivity * factor(grade);
    }

    public double happiness(int grade) {
        return this.happiness * factor(grade);
    }

    /** 每天收的信用点（市场）。 */
    public double income(int grade) {
        return this.income * factor(grade);
    }

    /** 每天压低的威胁。 */
    public double threatRelief(int grade) {
        return this.threatRelief * factor(grade);
    }

    /** 不在场时算输赢用的防御值。 */
    public double defense(int grade) {
        return this.defense * factor(grade);
    }

    /** 第 {@code stage} 段（0 起）要交的材料。 */
    public List<Cost> stageCost(int stage) {
        return this.stages.get(Math.clamp(stage, 0, STAGES - 1));
    }

    /** 升到 {@code grade}（1–3）要交的材料：几种建筑一样。 */
    public static List<Cost> upgradeCost(int grade) {
        return switch (grade) {
            case 1 -> List.of(Cost.of(TIMBER, 16), Cost.of("steel", 16), Cost.of("circuit_board", 4));
            case 2 -> List.of(Cost.of(GLASS, 16), Cost.of("titanium_alloy", 8), Cost.of("processor", 2));
            default -> List.of(Cost.of("titanium_alloy", 16), Cost.of("titanium_glass", 8), Cost.of("quantum_chip", 1));
        };
    }

    /** 被海盗打坏了以后修好要交的材料。 */
    public static List<Cost> repairCost() {
        return List.of(Cost.of(TIMBER, 8), Cost.of("steel", 6));
    }

    /** 一座聚落只能有一座的建筑。 */
    public boolean unique() {
        return this == OFFICE;
    }
}
