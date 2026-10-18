package io.github.sweetcornna.universe.factory;

import io.github.sweetcornna.universe.factory.RecipeMachineBlockEntity.Spec;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipe.Kind;
import io.github.sweetcornna.universe.registry.UniverseMenus;

/** 配方设备的规格。功率是戴森球计划的千瓦数除以 20（焦耳/刻），速度和格子数也照它来。 */
public final class RecipeMachines {
    /** 电弧熔炉：360 kW，速度 1。 */
    public static final Spec ARC_SMELTER = new Spec(Kind.SMELTING, 2, 1, 18, 1.0, UniverseMenus.ARC_SMELTER);
    /** 制造台 Mk.I：270 kW，速度 0.75。 */
    public static final Spec ASSEMBLER = new Spec(Kind.ASSEMBLING, 4, 1, 13, 0.75, UniverseMenus.ASSEMBLER);
    /** 原油精炼厂：960 kW，速度 1。 */
    public static final Spec OIL_REFINERY = new Spec(Kind.REFINING, 2, 2, 48, 1.0, UniverseMenus.OIL_REFINERY);
    /** 制造台 Mk.II：540 kW，速度 1（ADR-0015）。 */
    public static final Spec ASSEMBLER_MK2 = new Spec(Kind.ASSEMBLING, 4, 1, 27, 1.0, UniverseMenus.ASSEMBLER_MK2);
    /** 制造台 Mk.III：1.08 MW，速度 1.5。 */
    public static final Spec ASSEMBLER_MK3 = new Spec(Kind.ASSEMBLING, 4, 1, 54, 1.5, UniverseMenus.ASSEMBLER_MK3);
    /** 位面熔炉：1.44 MW，速度 2。 */
    public static final Spec PLANE_SMELTER = new Spec(Kind.SMELTING, 2, 1, 72, 2.0, UniverseMenus.PLANE_SMELTER);
    /** 化工厂：720 kW，速度 1，三种原料、两种产物。 */
    public static final Spec CHEMICAL_PLANT = new Spec(Kind.CHEMICAL, 3, 2, 36, 1.0, UniverseMenus.CHEMICAL_PLANT);

    private RecipeMachines() {
    }
}
