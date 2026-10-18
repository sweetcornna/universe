package io.github.sweetcornna.universe.client.discovery;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.galaxy.Planet;
import net.minecraft.util.Util;

/**
 * 上一帧天空里画了哪些天体、在什么方向、多大：天空渲染器画的时候记下来，分析目镜拿来判断准星对着哪个天体。
 * 方向是世界坐标里从摄像机出发的单位向量。
 */
public final class SkyTargets {
    /**
     * 一个天体。
     *
     * @param planet    行星或卫星；恒星为 null
     * @param direction 方向
     * @param radius    视半径（度）
     */
    public record Target(@Nullable Planet planet, Vector3fc direction, float radius) {
    }

    private static List<Target> current = List.of();
    private static List<Target> building = new ArrayList<>();
    /** 上次画完的时间：换到不用本模组天空的维度后就不再更新，过一会儿当作没有。 */
    private static long updated;

    private SkyTargets() {
    }

    /** 天空渲染器开始画一帧。 */
    public static void begin() {
        building = new ArrayList<>();
    }

    public static void add(@Nullable Planet planet, Vector3f direction, float angularDiameter) {
        building.add(new Target(planet, new Vector3f(direction).normalize(), angularDiameter / 2));
    }

    /** 画完了：换成这一帧的。 */
    public static void end() {
        current = List.copyOf(building);
        updated = Util.getMillis();
    }

    /** 没有自定义天空的维度（主世界以外的原版维度）不画，清空。 */
    public static void clear() {
        current = List.of();
    }

    /**
     * 准星对着的天体：视线和天体方向的夹角在视半径加一点余量以内，取最近的那个。
     *
     * @param look 视线方向（单位向量）
     */
    public static @Nullable Target aimedAt(Vector3fc look) {
        if (Util.getMillis() - updated > 500) {
            return null;
        }
        Target best = null;
        double bestScore = Double.MAX_VALUE;
        for (Target target : current) {
            double angle = Math.toDegrees(Math.acos(Math.clamp(look.dot(target.direction()), -1.0F, 1.0F)));
            double reach = target.radius() + 1.5;
            if (angle < reach && angle / reach < bestScore) {
                bestScore = angle / reach;
                best = target;
            }
        }
        return best;
    }
}
