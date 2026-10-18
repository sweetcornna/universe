package io.github.sweetcornna.universe.dyson;

import java.util.Optional;

import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;

/** 戴森球设备看不看得见天空和恒星（ADR-0016）。 */
public final class DysonSight {
    private DysonSight() {
    }

    public static boolean space(Level level) {
        return SpaceDimensions.system(level.dimension()).isPresent();
    }

    /** 头顶 above 这一格看得见天空（太空里总看得见）。 */
    public static boolean sky(Level level, BlockPos above) {
        return space(level) || level.dimensionType().hasSkyLight() && level.canSeeSky(above);
    }

    /**
     * 恒星的高度角（度）：太空里当作在正上方；没有太阳的维度是 -90。太阳角度 0 是正午、±90 是地平线。
     */
    public static double sunElevation(Level level, BlockPos pos) {
        if (space(level)) {
            return 90;
        }
        if (!level.dimensionType().hasSkyLight()) {
            return -90;
        }
        double angle = level.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, pos);
        double wrapped = ((angle % 360) + 540) % 360 - 180;
        return 90 - Math.abs(wrapped);
    }

    /** 这颗星球有大气（主世界算有）。 */
    public static boolean atmosphere(ServerLevel level) {
        if (space(level)) {
            return false;
        }
        Optional<Planet> body = Planets.body(level);
        return body.map(planet -> planet.atmosphere().present()).orElse(level.dimension() == Level.OVERWORLD);
    }
}
