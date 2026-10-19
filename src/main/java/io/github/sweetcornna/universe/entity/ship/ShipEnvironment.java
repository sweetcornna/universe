package io.github.sweetcornna.universe.entity.ship;

import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.ship.ShipFlight.Environment;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** 飞船（和探索车）所在维度的重力和大气：星球维度按星球参数，主世界按母星，太空是真空失重。 */
public final class ShipEnvironment {
    private ShipEnvironment() {
    }

    public static Environment of(ServerLevel level) {
        if (SpaceDimensions.isSpace(level.dimension())) {
            return Environment.SPACE;
        }
        Planet planet = level.dimension() == Level.OVERWORLD
                ? Planets.galaxy(level.getServer()).homePlanet()
                : Planets.planet(level).orElse(null);
        if (planet == null) {
            // 下界、末地和别的模组的维度
            return Environment.HOMEWORLD;
        }
        return new Environment(planet.gravity(), planet.atmosphere().pressure(), false);
    }
}
