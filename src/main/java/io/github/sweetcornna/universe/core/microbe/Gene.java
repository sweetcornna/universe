package io.github.sweetcornna.universe.core.microbe;

/**
 * 能测出来、能编辑进别的生物的基因（ADR-0036 §五），每个照着现实里的来源。
 * 顺序和名字是存档格式的一部分（基因库和物品按名字存），只往后加。
 */
public enum Gene {
    /** 抗冻蛋白：南极鱼、比目鱼。耐寒。 */
    ANTIFREEZE_PROTEIN(Role.TOLERANCE, "抗冻蛋白", "Antifreeze Protein"),
    /** 嗜热酶：栖热水生菌、甲烷嗜高热菌。耐热。 */
    THERMOSTABLE_ENZYMES(Role.TOLERANCE, "嗜热酶", "Thermostable Enzymes"),
    /** DNA 修复：耐辐射奇球菌。耐辐射。 */
    DNA_REPAIR(Role.TOLERANCE, "DNA 修复", "DNA Repair"),
    /** 质子泵：嗜酸菌。耐酸、耐毒。 */
    PROTON_PUMP(Role.TOLERANCE, "质子泵", "Proton Pump"),
    /** 相容溶质：嗜盐古菌。耐盐、耐旱。 */
    COMPATIBLE_SOLUTES(Role.TOLERANCE, "相容溶质", "Compatible Solutes"),
    /** 紫外防护色素：拟色球藻、沙漠蓝藻。稀薄大气和真空下的强紫外。 */
    UV_SHIELD(Role.TOLERANCE, "紫外防护色素", "UV Shield Pigments"),
    /** 固氮酶：根瘤菌。 */
    NITROGENASE(Role.FUNCTION, "固氮酶", "Nitrogenase"),
    /** 碳浓缩：蓝藻的羧酶体。光合更快。 */
    CARBON_CONCENTRATING(Role.FUNCTION, "碳浓缩", "Carbon Concentrating"),
    /** 荧光素酶：费氏弧菌、萤火虫。发光。 */
    LUCIFERASE(Role.FUNCTION, "荧光素酶", "Luciferase"),
    /** 产甲烷：产甲烷古菌。 */
    METHANOGENESIS(Role.FUNCTION, "产甲烷", "Methanogenesis"),
    /** 铁硫氧化：嗜酸氧化亚铁硫杆菌。浸矿。 */
    IRON_SULFUR_OXIDATION(Role.FUNCTION, "铁硫氧化", "Iron-Sulfur Oxidation"),
    /** 塑料降解酶：大阪堺菌。 */
    PETASE(Role.FUNCTION, "塑料降解酶", "PETase"),
    /** 产氧光合：蓝藻。放氧，改造星球的主力。 */
    OXYGENIC_PHOTOSYNTHESIS(Role.FUNCTION, "产氧光合", "Oxygenic Photosynthesis");

    /** 一样东西最多加几个外来的基因（ADR-0036 §五）。 */
    public static final int MAX_ADDED = 4;
    /** 每加一个外来基因，生长速度乘多少（代谢负担）。 */
    public static final double BURDEN = 0.9;

    /** 基因的用处：耐受环境，或者带来一种本事。 */
    public enum Role {
        TOLERANCE,
        FUNCTION
    }

    private final Role role;
    private final String nameZh;
    private final String nameEn;

    Gene(Role role, String nameZh, String nameEn) {
        this.role = role;
        this.nameZh = nameZh;
        this.nameEn = nameEn;
    }

    public Role role() {
        return this.role;
    }

    public String nameZh() {
        return this.nameZh;
    }

    public String nameEn() {
        return this.nameEn;
    }

    /** 小写的名字，用在语言键和注册名里。 */
    public String id() {
        return this.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** 按名字找，找不到为 {@code null}。 */
    public static Gene byName(String name) {
        for (Gene gene : values()) {
            if (gene.name().equals(name)) {
                return gene;
            }
        }
        return null;
    }
}
