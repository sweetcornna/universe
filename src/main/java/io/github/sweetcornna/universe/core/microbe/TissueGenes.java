package io.github.sweetcornna.universe.core.microbe;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import io.github.sweetcornna.universe.core.ecology.Biotope;
import io.github.sweetcornna.universe.core.ecology.CreatureSpecies;
import io.github.sweetcornna.universe.core.ecology.ResourcePlant;
import io.github.sweetcornna.universe.core.fishing.Habitat;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;

/**
 * 动物、鱼、资源植物的组织里能测出的基因（ADR-0036 §五）。一个物种总是测出同样的几个。
 *
 * <ul>
 *   <li>鱼：冰下的有抗冻蛋白（南极鱼；1991 年有人把比目鱼的抗冻基因转进过番茄），深海的会发光，酸水里的有质子泵，
 *       温泉、雨林的暖水里的有嗜热酶，奇异和辐射星球水里的有 DNA 修复。</li>
 *   <li>动物：会发光的有荧光素酶，住在冷的地方的有抗冻蛋白，干旱的有相容溶质；辐射、毒雾、灼热星球上的分别有
 *       DNA 修复、质子泵、嗜热酶。</li>
 *   <li>资源植物：照 ADR-0026 里各自的原型。</li>
 * </ul>
 */
public final class TissueGenes {
    private TissueGenes() {
    }

    /** 这种水域的鱼。 */
    public static Set<Gene> fish(Habitat habitat) {
        return switch (habitat) {
            case ICE -> of(Gene.ANTIFREEZE_PROTEIN);
            case DEEP -> of(Gene.LUCIFERASE);
            case ACID -> of(Gene.PROTON_PUMP);
            case WARM -> of(Gene.THERMOSTABLE_ENZYMES);
            case EXOTIC -> of(Gene.DNA_REPAIR);
            case FRESH, COASTAL, CAVE -> of();
        };
    }

    /** 一种动物，{@code type} 是它所在星球的类型。 */
    public static Set<Gene> creature(PlanetType type, CreatureSpecies species) {
        return creature(type, species.biotopes(), species.skin().glowingPattern() || species.skin().glowingEyes());
    }

    /** 一种动物：住在哪些生境、会不会发光。 */
    public static Set<Gene> creature(PlanetType type, Collection<Biotope> biotopes, boolean glowing) {
        EnumSet<Gene> genes = EnumSet.noneOf(Gene.class);
        if (glowing) {
            genes.add(Gene.LUCIFERASE);
        }
        if (biotopes.contains(Biotope.COLD) || type == PlanetType.FROZEN) {
            genes.add(Gene.ANTIFREEZE_PROTEIN);
        }
        if (biotopes.contains(Biotope.ARID)) {
            genes.add(Gene.COMPATIBLE_SOLUTES);
        }
        switch (type) {
            case IRRADIATED -> genes.add(Gene.DNA_REPAIR);
            case TOXIC -> genes.add(Gene.PROTON_PUMP);
            case SCORCHED -> genes.add(Gene.THERMOSTABLE_ENZYMES);
            default -> {
            }
        }
        return Collections.unmodifiableSet(genes);
    }

    /**
     * 一种资源植物。氧囊草照墨角藻（褐藻，有碳浓缩机制），盐角草照盐生植物，星莓丛照沙棘（根上的弗兰克氏菌固氮），
     * 霜苔照南极地衣，日焰藤照嗜热植物，伽马草照切尔诺贝利的黑色素真菌，毒霉簇照分解毒物的真菌，桶形仙人掌耐旱，
     * 烛光海带会发光也会光合。盐晶簇是矿物，引力果和洞穴髓球测不出能用的基因。
     */
    public static Set<Gene> plant(ResourcePlant plant) {
        return switch (plant) {
            case OXYGEN_BLADDER -> of(Gene.OXYGENIC_PHOTOSYNTHESIS, Gene.CARBON_CONCENTRATING);
            case SALTWORT -> of(Gene.COMPATIBLE_SOLUTES);
            case STAR_BRAMBLE -> of(Gene.NITROGENASE);
            case FROSTWORT -> of(Gene.ANTIFREEZE_PROTEIN);
            case SOLAR_VINE -> of(Gene.THERMOSTABLE_ENZYMES);
            case GAMMA_WEED -> of(Gene.DNA_REPAIR);
            case FUNGAL_CLUSTER -> of(Gene.PROTON_PUMP);
            case ECHINOCACTUS -> of(Gene.COMPATIBLE_SOLUTES);
            case CANDLE_KELP -> of(Gene.LUCIFERASE, Gene.OXYGENIC_PHOTOSYNTHESIS);
            case SALT_CRYSTAL, GRAVITINO_HOST, CAVE_MARROW -> of();
        };
    }

    private static Set<Gene> of(Gene... genes) {
        EnumSet<Gene> set = EnumSet.noneOf(Gene.class);
        Collections.addAll(set, genes);
        return Collections.unmodifiableSet(set);
    }
}
