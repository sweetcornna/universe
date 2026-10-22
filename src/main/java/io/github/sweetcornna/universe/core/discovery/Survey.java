package io.github.sweetcornna.universe.core.discovery;

import io.github.sweetcornna.universe.core.ecology.Fauna;
import io.github.sweetcornna.universe.core.ecology.Flora;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Planet;

/**
 * 勘探数据（ADR-0012）：第一个发现某样东西给几个，一颗星球上有几种动植物。
 */
public final class Survey {
    /** 一颗星球上的动植物全部发现后再给这么多。 */
    public static final int COMPLETION_BONUS = 5;

    private Survey() {
    }

    /** 第一个发现它的玩家拿到几个勘探数据。 */
    public static int reward(DiscoveryKey key, Galaxy galaxy) {
        return switch (key.kind()) {
            case SYSTEM -> galaxy.system(key.system()).map(system -> switch (system.star().starClass()) {
                case BLACK_HOLE -> 20;
                case NEUTRON_STAR -> 12;
                case RED_GIANT, WHITE_DWARF -> 8;
                case O, B -> 7;
                default -> 5;
            }).orElse(0);
            case BODY -> galaxy.body(key.body()).map(planet -> planet.landable() ? 4 : 3).orElse(0);
            case CREATURE -> 2;
            case PLANT, TREE, MINERAL -> 1;
        };
    }

    /**
     * 一颗星球上有几种动物（陆地的加水里的）、地面植物和树。
     */
    public record Census(int creatures, int plants, int trees) {
        public static final Census NONE = new Census(0, 0, 0);

        public int total() {
            return this.creatures + this.plants + this.trees;
        }

        public int of(DiscoveryKey.Kind kind) {
            return switch (kind) {
                case CREATURE -> this.creatures;
                case PLANT -> this.plants;
                case TREE -> this.trees;
                default -> 0;
            };
        }
    }

    /** 母星（主世界）上是原版的动植物，不算。 */
    public static Census census(Planet planet) {
        if (planet.id().equals(GalaxyGenerator.HOME_PLANET_ID) || !planet.landable()) {
            return Census.NONE;
        }
        Flora flora = Flora.of(planet);
        return new Census(Fauna.of(planet).all().size(), flora.plants().size(), flora.trees().size());
    }

    /** 这颗星球上的动植物是不是都发现了（没有动植物的星球不算）。 */
    public static boolean complete(DiscoveryLog log, DiscoveryKey body, Census census) {
        if (census.total() == 0) {
            return false;
        }
        return log.countOn(body, DiscoveryKey.Kind.CREATURE) >= census.creatures()
                && log.countOn(body, DiscoveryKey.Kind.PLANT) >= census.plants()
                && log.countOn(body, DiscoveryKey.Kind.TREE) >= census.trees();
    }
}
