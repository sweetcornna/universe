package io.github.sweetcornna.universe.core.microbe;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.ecology.ResourceFlora;
import io.github.sweetcornna.universe.core.ecology.ResourcePlant;
import io.github.sweetcornna.universe.core.galaxy.Atmosphere;
import io.github.sweetcornna.universe.core.galaxy.Hazard;
import io.github.sweetcornna.universe.core.galaxy.Liquid;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;

/**
 * 改造种苗能种在哪里（ADR-0036 §五）。资源植物本来长在哪里照 {@link ResourceFlora}；改造种苗还能种在
 * 每一道障碍都有对应基因的星球上。真空、太热（平均温度过 {@link #MAX_PLANT_K}）的星球种不了，要先改造（§六）。
 */
public final class PlantTolerance {
    /** 植物能活的最高平均温度（K），57 °C 上下。 */
    public static final double MAX_PLANT_K = 330;
    /** 冷过这个平均温度（K）算寒冷。 */
    public static final double COLD_K = 263;
    /** 热过这个平均温度（K）算炎热。 */
    public static final double HOT_K = 313;

    private PlantTolerance() {
    }

    /** 一颗星球对植物的障碍，各要一个基因；没有基因能过的是死路。 */
    public enum Barrier {
        COLD(Gene.ANTIFREEZE_PROTEIN, "寒冷", "Cold"),
        HEAT(Gene.THERMOSTABLE_ENZYMES, "炎热", "Heat"),
        TOXIC(Gene.PROTON_PUMP, "毒性", "Toxicity"),
        RADIATION(Gene.DNA_REPAIR, "辐射", "Radiation"),
        ARID(Gene.COMPATIBLE_SOLUTES, "干旱", "Drought"),
        /** 稀薄的大气挡不住紫外。 */
        UV(Gene.UV_SHIELD, "强紫外", "Harsh UV"),
        VACUUM(null, "真空", "Vacuum"),
        TOO_HOT(null, "过热", "Too Hot");

        private final @Nullable Gene gene;
        private final String nameZh;
        private final String nameEn;

        Barrier(@Nullable Gene gene, String nameZh, String nameEn) {
            this.gene = gene;
            this.nameZh = nameZh;
            this.nameEn = nameEn;
        }

        /** 过这道障碍要的基因；死路为 {@code null}。 */
        public @Nullable Gene gene() {
            return this.gene;
        }

        public boolean impossible() {
            return this.gene == null;
        }

        public String nameZh() {
            return this.nameZh;
        }

        public String nameEn() {
            return this.nameEn;
        }
    }

    /** 这颗星球（改造过的就传现用的环境）对植物的障碍。 */
    public static Set<Barrier> barriers(Planet planet) {
        EnumSet<Barrier> barriers = EnumSet.noneOf(Barrier.class);
        Atmosphere.Kind kind = planet.atmosphere().kind();
        if (kind == Atmosphere.Kind.NONE) {
            barriers.add(Barrier.VACUUM);
        } else if (kind == Atmosphere.Kind.THIN) {
            barriers.add(Barrier.UV);
        }
        double t = planet.temperature();
        if (t > MAX_PLANT_K) {
            barriers.add(Barrier.TOO_HOT);
        } else if (t > HOT_K || planet.hasHazard(Hazard.HEAT)) {
            barriers.add(Barrier.HEAT);
        }
        if (t < COLD_K || planet.hasHazard(Hazard.COLD)) {
            barriers.add(Barrier.COLD);
        }
        if (planet.hasHazard(Hazard.TOXIC) || kind == Atmosphere.Kind.TOXIC) {
            barriers.add(Barrier.TOXIC);
        }
        if (planet.hasHazard(Hazard.RADIATION)) {
            barriers.add(Barrier.RADIATION);
        }
        if (planet.liquid() == Liquid.NONE || planet.liquid() == Liquid.LAVA) {
            barriers.add(Barrier.ARID);
        }
        return barriers;
    }

    /** 这类星球一般的障碍，给手册和界面用；真种的时候以 {@link #barriers(Planet)} 为准。 */
    public static Set<Barrier> typicalBarriers(PlanetType type) {
        return switch (type) {
            case HABITABLE, OCEAN, EXOTIC -> EnumSet.noneOf(Barrier.class);
            case TOXIC -> EnumSet.of(Barrier.TOXIC);
            case FROZEN -> EnumSet.of(Barrier.COLD, Barrier.UV);
            case BARREN -> EnumSet.of(Barrier.ARID, Barrier.UV);
            case IRRADIATED -> EnumSet.of(Barrier.RADIATION, Barrier.ARID, Barrier.UV);
            case SCORCHED -> EnumSet.of(Barrier.TOO_HOT);
            case GAS_GIANT -> EnumSet.of(Barrier.VACUUM);
        };
    }

    /** 这些基因过不了的障碍（含死路）。 */
    public static Set<Barrier> unmet(Set<Barrier> barriers, Collection<Gene> genes) {
        EnumSet<Barrier> unmet = EnumSet.noneOf(Barrier.class);
        for (Barrier barrier : barriers) {
            if (barrier.impossible() || !genes.contains(barrier.gene())) {
                unmet.add(barrier);
            }
        }
        return unmet;
    }

    /**
     * 带着这些基因（组织里天生的加编辑进去的）的种苗能不能种在这颗星球的地里。没有编辑过的（{@code engineered} 为假）
     * 照 {@link ResourceFlora#grows}。
     */
    public static boolean canPlant(ResourcePlant plant, Collection<Gene> genes, boolean engineered, Planet planet) {
        if (plant.habitat() != ResourcePlant.Habitat.SOIL) {
            return false;
        }
        if (ResourceFlora.grows(plant, planet.type(), planet.ecology().lifeless())) {
            return true;
        }
        return engineered && unmet(barriers(planet), genes).isEmpty();
    }

    /** 按 {@link #typicalBarriers} 算，带着这些基因的改造种苗一般能种在哪几类星球上（本来就长的也算）。 */
    public static Set<PlanetType> allowedTypes(ResourcePlant plant, Collection<Gene> genes) {
        EnumSet<PlanetType> types = EnumSet.noneOf(PlanetType.class);
        if (plant.habitat() != ResourcePlant.Habitat.SOIL) {
            return types;
        }
        for (PlanetType type : PlanetType.values()) {
            if (!type.landable()) {
                continue;
            }
            if (ResourceFlora.plants(type, false).contains(plant) || unmet(typicalBarriers(type), genes).isEmpty()) {
                types.add(type);
            }
        }
        return types;
    }
}
