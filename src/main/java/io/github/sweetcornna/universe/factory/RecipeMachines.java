package io.github.sweetcornna.universe.factory;

import io.github.sweetcornna.universe.factory.RecipeMachineBlockEntity.Spec;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipe.Kind;
import io.github.sweetcornna.universe.registry.UniverseMenus;

/** 配方设备的规格。功率是戴森球计划的千瓦数除以 20（焦耳/刻），速度和格子数也照它来。 */
public final class RecipeMachines {
    /** 电弧熔炉：360 kW，速度 1，三种原料（钛合金要钛、钢和硫酸）。 */
    public static final Spec ARC_SMELTER = new Spec(Kind.SMELTING, 3, 1, 18, 1.0, UniverseMenus.ARC_SMELTER);
    /** 制造台 Mk.I：270 kW，速度 0.75。 */
    public static final Spec ASSEMBLER = new Spec(Kind.ASSEMBLING, 4, 1, 13, 0.75, UniverseMenus.ASSEMBLER);
    /** 原油精炼厂：960 kW，速度 1。 */
    public static final Spec OIL_REFINERY = new Spec(Kind.REFINING, 2, 2, 48, 1.0, UniverseMenus.OIL_REFINERY);
    /** 制造台 Mk.II：540 kW，速度 1（ADR-0015）。 */
    public static final Spec ASSEMBLER_MK2 = new Spec(Kind.ASSEMBLING, 4, 1, 27, 1.0, UniverseMenus.ASSEMBLER_MK2);
    /** 制造台 Mk.III：1.08 MW，速度 1.5。 */
    public static final Spec ASSEMBLER_MK3 = new Spec(Kind.ASSEMBLING, 4, 1, 54, 1.5, UniverseMenus.ASSEMBLER_MK3);
    /** 位面熔炉：1.44 MW，速度 2，三种原料。 */
    public static final Spec PLANE_SMELTER = new Spec(Kind.SMELTING, 3, 1, 72, 2.0, UniverseMenus.PLANE_SMELTER);
    /** 化工厂：720 kW，速度 1，三种原料、两种产物。 */
    public static final Spec CHEMICAL_PLANT = new Spec(Kind.CHEMICAL, 3, 2, 36, 1.0, UniverseMenus.CHEMICAL_PLANT);
    /** 微型粒子对撞机：12 MW，速度 1，三种原料（ADR-0016）、两种产物（反物质和氢，ADR-0018）。 */
    public static final Spec MINIATURE_PARTICLE_COLLIDER = new Spec(Kind.PARTICLE, 3, 2, 600, 1.0, UniverseMenus.MINIATURE_PARTICLE_COLLIDER);

    /** 负熵熔炉：2.88 MW，速度 3（ADR-0022）。 */
    public static final Spec NEGENTROPY_SMELTER = new Spec(Kind.SMELTING, 3, 1, 144, 3.0, UniverseMenus.NEGENTROPY_SMELTER);
    /** 重组式制造台：2.7 MW，速度 3。 */
    public static final Spec RECOMPOSING_ASSEMBLER = new Spec(Kind.ASSEMBLING, 4, 1, 135, 3.0, UniverseMenus.RECOMPOSING_ASSEMBLER);
    /** 量子化工厂：2.16 MW，速度 2。 */
    public static final Spec QUANTUM_CHEMICAL_PLANT = new Spec(Kind.CHEMICAL, 3, 2, 108, 2.0, UniverseMenus.QUANTUM_CHEMICAL_PLANT);

    /** 营养处理器（ADR-0026）：烧燃料，不要电，三种原料。 */
    public static final Spec NUTRIENT_PROCESSOR = new Spec(Kind.NUTRIENT, 3, 1, 0, 1.0, UniverseMenus.NUTRIENT_PROCESSOR);
    /** 便携精炼机：烧燃料，一种原料。 */
    public static final Spec PORTABLE_REFINER = new Spec(Kind.PORTABLE_REFINING, 1, 1, 0, 1.0, UniverseMenus.PORTABLE_REFINER);

    private RecipeMachines() {
    }
}
