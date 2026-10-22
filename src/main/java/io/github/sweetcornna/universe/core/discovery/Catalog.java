package io.github.sweetcornna.universe.core.discovery;

import java.util.List;
import java.util.Optional;

import io.github.sweetcornna.universe.core.ecology.CreatureSpecies;
import io.github.sweetcornna.universe.core.ecology.Fauna;
import io.github.sweetcornna.universe.core.ecology.Flora;
import io.github.sweetcornna.universe.core.ecology.PlantSpecies;
import io.github.sweetcornna.universe.core.ecology.TreeSpecies;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.procgen.NameGenerator.Name;

/**
 * 发现日志里的东西在星团数据里对应什么：程序生成的名字、所在的星球、是哪个物种。
 */
public final class Catalog {
    private Catalog() {
    }

    /** 所在的天体（恒星系没有）。 */
    public static Optional<Planet> planet(DiscoveryKey key, Galaxy galaxy) {
        return key.body() == null ? Optional.empty() : galaxy.body(key.body());
    }

    /** 陆地的物种序号从 0 起，水生的从 {@link Fauna#MARINE_INDEX} 起（ADR-0026）。 */
    public static Optional<CreatureSpecies> creature(DiscoveryKey key, Galaxy galaxy) {
        return key.kind() != DiscoveryKey.Kind.CREATURE ? Optional.empty()
                : planet(key, galaxy).flatMap(planet -> Fauna.of(planet).find(key.index()));
    }

    public static Optional<PlantSpecies> plant(DiscoveryKey key, Galaxy galaxy) {
        return key.kind() != DiscoveryKey.Kind.PLANT ? Optional.empty()
                : planet(key, galaxy).flatMap(planet -> pick(Flora.of(planet).plants(), key.index()));
    }

    public static Optional<TreeSpecies> tree(DiscoveryKey key, Galaxy galaxy) {
        return key.kind() != DiscoveryKey.Kind.TREE ? Optional.empty()
                : planet(key, galaxy).flatMap(planet -> pick(Flora.of(planet).trees(), key.index()));
    }

    /** 程序生成的名字；矿物没有（用方块本身的名字）。 */
    public static Optional<Name> name(DiscoveryKey key, Galaxy galaxy) {
        return switch (key.kind()) {
            case SYSTEM -> galaxy.system(key.system()).map(system -> system.name());
            case BODY -> planet(key, galaxy).map(Planet::name);
            case CREATURE -> creature(key, galaxy).map(CreatureSpecies::name);
            case PLANT -> plant(key, galaxy).map(PlantSpecies::name);
            case TREE -> tree(key, galaxy).map(TreeSpecies::name);
            case MINERAL -> Optional.empty();
        };
    }

    private static <T> Optional<T> pick(List<T> list, int index) {
        return index >= 0 && index < list.size() ? Optional.of(list.get(index)) : Optional.empty();
    }
}
