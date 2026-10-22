package io.github.sweetcornna.universe.survival;

import java.util.Optional;

import io.github.sweetcornna.universe.core.ecology.ResourcePlant;

/** 能种的物品：资源植物的产物，包括氧气和钠（ADR-0026）。 */
public interface Plantable {
    Optional<ResourcePlant> plant();
}
