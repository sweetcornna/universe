package io.github.sweetcornna.universe.client.dyson;

import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** 客户端算恒星在哪个方向，设备的渲染器用来转炮管、碟面。 */
final class ClientSun {
    private ClientSun() {
    }

    /**
     * 从 pos 看恒星的单位方向。星球上恒星沿原版太阳的路径走（天空也是这么画的，黄道面是世界的 xy 平面）；
     * 太空维度里恒星在世界原点。
     */
    static Vec3 direction(Level level, BlockPos pos) {
        if (SpaceDimensions.system(level.dimension()).isPresent()) {
            Vec3 toStar = new Vec3(-pos.getX() - 0.5, -pos.getY() - 0.5, -pos.getZ() - 0.5);
            return toStar.lengthSqr() < 1 ? new Vec3(0, 1, 0) : toStar.normalize();
        }
        double angle = Math.toRadians(level.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, pos));
        return new Vec3(-Math.sin(angle), Math.cos(angle), 0);
    }

    static boolean space(Level level) {
        return SpaceDimensions.system(level.dimension()).isPresent();
    }
}
