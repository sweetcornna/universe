package io.github.sweetcornna.universe.core.fossil;

import java.util.EnumMap;
import java.util.Map;

import io.github.sweetcornna.universe.core.galaxy.PlanetType;

/**
 * 挖到哪个类群、哪个部位的骨头（ADR-0034 §三）。权重是类群稀有度的权重乘上星球类型的偏重：宜居星球多陆上的大动物，
 * 海洋星球多海兽，灼热、毒雾的星球多巨蠕和甲壳兽，冰冻星球的冻土里多巨兽；奇异种只在奇异星球上。
 *
 * <p>化石土挖开时有 {@value #DIRT_CHANCE} 的机会多出一块骨头；石哨兵掉的偏向稀有的类群。
 */
public final class FossilFinds {
    /** 每块化石土额外掉一块骨头的机会（原来的三种化石照旧掉）。 */
    public static final double DIRT_CHANCE = 0.4;
    /** 石哨兵掉的：常见类群的权重只算这么多。 */
    private static final double SENTRY_COMMON_FACTOR = 0.15;

    private FossilFinds() {
    }

    /** 这种星球上各类群的权重；为 0 的挖不到。 */
    public static Map<BoneFamily, Double> weights(PlanetType type) {
        Map<BoneFamily, Double> weights = new EnumMap<>(BoneFamily.class);
        for (BoneFamily family : BoneFamily.values()) {
            double weight = family == BoneFamily.EXOTIC && type != PlanetType.EXOTIC ? 0 : family.rarity().weight() * affinity(type, family);
            weights.put(family, weight);
        }
        return weights;
    }

    /** 星球类型对类群的偏重。 */
    static double affinity(PlanetType type, BoneFamily family) {
        return switch (type) {
            case HABITABLE -> switch (family) {
                case TITAN, PREDATOR, LONGNECK -> 1.4;
                case FLYER -> 1.3;
                default -> 1.0;
            };
            case BARREN -> switch (family) {
                case ARTHROPOD -> 2.2;
                case WORM -> 1.6;
                case LONGNECK, FLYER -> 0.7;
                default -> 1.0;
            };
            case SCORCHED -> switch (family) {
                case WORM -> 2.6;
                case ARTHROPOD -> 1.6;
                case PREDATOR -> 1.2;
                case MARINE -> 0.4;
                default -> 1.0;
            };
            case FROZEN -> switch (family) {
                case TITAN -> 2.0;
                case MARINE -> 1.6;
                case WORM -> 0.6;
                default -> 1.0;
            };
            case TOXIC -> switch (family) {
                case ARTHROPOD, WORM -> 2.2;
                case FLYER -> 0.6;
                default -> 1.0;
            };
            case IRRADIATED -> switch (family) {
                case PREDATOR, ARTHROPOD -> 1.6;
                default -> 1.0;
            };
            case OCEAN -> switch (family) {
                case MARINE -> 3.0;
                case FLYER -> 1.5;
                case TITAN, LONGNECK -> 0.6;
                default -> 1.0;
            };
            case EXOTIC -> family == BoneFamily.EXOTIC ? 3.0 : 0.8;
            case GAS_GIANT -> 1.0;
        };
    }

    /** 化石土里挖到的骨头：{@code familyRoll}、{@code partRoll} 是 [0, 1) 的随机数。 */
    public static Bone dig(PlanetType type, double familyRoll, double partRoll) {
        return new Bone(pick(weights(type), familyRoll), part(partRoll));
    }

    /** 石哨兵掉的骨头：常见的类群少得多。 */
    public static Bone sentry(PlanetType type, double familyRoll, double partRoll) {
        Map<BoneFamily, Double> weights = weights(type);
        weights.replaceAll((family, weight) -> family.rarity() == BoneFamily.Rarity.COMMON ? weight * SENTRY_COMMON_FACTOR : weight);
        return new Bone(pick(weights, familyRoll), part(partRoll));
    }

    static BonePart part(double roll) {
        double total = 0;
        for (BonePart part : BonePart.values()) {
            total += part.findWeight();
        }
        double target = roll * total;
        for (BonePart part : BonePart.values()) {
            target -= part.findWeight();
            if (target < 0) {
                return part;
            }
        }
        return BonePart.TAIL;
    }

    private static BoneFamily pick(Map<BoneFamily, Double> weights, double roll) {
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        double target = roll * total;
        BoneFamily last = BoneFamily.TITAN;
        for (Map.Entry<BoneFamily, Double> entry : weights.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            last = entry.getKey();
            target -= entry.getValue();
            if (target < 0) {
                return entry.getKey();
            }
        }
        return last;
    }
}
