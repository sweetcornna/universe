package io.github.sweetcornna.universe.darkfog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

import io.github.sweetcornna.universe.core.darkfog.Support;
import io.github.sweetcornna.universe.darkfog.defense.SignalTowerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * 信号塔（ADR-0028 §五）：嘲讽 48 格内的黑雾，让同一个电网的导弹炮塔借它的范围，让附近的黑雾基地超载。
 *
 * <p>每个世界记着已加载的信号塔（方块实体加载、卸载时登记），只算通电的。信号塔同时是电网里的一座塔，
 * 它所在的电网用 {@code LevelPower.gridOfTower(信号塔的位置)} 查。
 */
public final class Taunts {
    /** 嘲讽、超载的范围。 */
    public static final double RANGE = Support.TAUNT_RANGE;

    private static final Map<ServerLevel, Set<BlockPos>> TOWERS = new WeakHashMap<>();

    private Taunts() {
    }

    /** 信号塔加载时登记。 */
    public static void add(ServerLevel level, BlockPos pos) {
        TOWERS.computeIfAbsent(level, key -> new HashSet<>()).add(pos.immutable());
    }

    /** 信号塔移除、卸载时注销。 */
    public static void remove(ServerLevel level, BlockPos pos) {
        Set<BlockPos> towers = TOWERS.get(level);
        if (towers != null) {
            towers.remove(pos);
        }
    }

    /** {@code range} 格内最近的通电的信号塔：黑雾先打它。 */
    public static Optional<BlockPos> nearestTaunt(ServerLevel level, Vec3 pos, double range) {
        double limit = Math.min(range, RANGE);
        BlockPos best = null;
        double bestDistance = limit * limit;
        for (BlockPos tower : signalTowers(level)) {
            double distance = center(tower).distanceToSqr(pos);
            if (distance <= bestDistance) {
                best = tower;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    /** 基地 48 格内有没有通电的信号塔（营地超载）。 */
    public static boolean overdrives(ServerLevel level, Vec3 basePos) {
        return nearestTaunt(level, basePos, RANGE).isPresent();
    }

    /** 这个维度里所有通电的信号塔（导弹炮塔借它的范围）。 */
    public static List<BlockPos> signalTowers(ServerLevel level) {
        Set<BlockPos> towers = TOWERS.get(level);
        if (towers == null || towers.isEmpty()) {
            return List.of();
        }
        List<BlockPos> powered = new ArrayList<>();
        for (BlockPos pos : towers) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof SignalTowerBlockEntity tower && tower.powered()) {
                powered.add(pos);
            }
        }
        return powered;
    }

    /** 信号塔的中心：两格高，取上下两格的交界。 */
    public static Vec3 center(BlockPos tower) {
        return new Vec3(tower.getX() + 0.5, tower.getY() + 1, tower.getZ() + 0.5);
    }
}
