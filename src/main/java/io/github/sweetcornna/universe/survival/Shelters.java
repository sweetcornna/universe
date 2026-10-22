package io.github.sweetcornna.universe.survival;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 庇护所（ADR-0026）：头顶有不是树叶的实心方块，四个水平方向里至少三个方向不远处有墙，就算在屋里或洞里。
 * 只看头顶的话站在树下也算，所以还要看墙。每名玩家半秒查一次。
 */
public final class Shelters {
    /** 往上找屋顶几格。 */
    static final int ROOF_REACH = 12;
    /** 往四周找墙几格。 */
    static final int WALL_REACH = 8;
    /** 几个方向有墙才算围起来。 */
    static final int WALLS_NEEDED = 3;
    private static final int INTERVAL = 10;
    private static final Map<UUID, long[]> CACHE = new ConcurrentHashMap<>();

    private Shelters() {
    }

    public static boolean sheltered(ServerPlayer player, ServerLevel level) {
        long now = level.getGameTime();
        long[] cached = CACHE.get(player.getUUID());
        if (cached == null || now - cached[0] >= INTERVAL || now < cached[0]) {
            cached = new long[] {now, check(level, BlockPos.containing(player.getEyePosition())) ? 1 : 0};
            CACHE.put(player.getUUID(), cached);
        }
        return cached[1] == 1;
    }

    public static void forget(UUID player) {
        CACHE.remove(player);
    }

    static boolean check(ServerLevel level, BlockPos eye) {
        if (level.canSeeSky(eye) || !roofed(level, eye)) {
            return false;
        }
        int walls = 0;
        BlockPos feet = eye.below();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (wall(level, eye, direction) || wall(level, feet, direction)) {
                walls++;
            }
        }
        return walls >= WALLS_NEEDED;
    }

    private static boolean roofed(ServerLevel level, BlockPos eye) {
        BlockPos.MutableBlockPos pos = eye.mutable();
        for (int i = 1; i <= ROOF_REACH; i++) {
            pos.move(Direction.UP);
            if (solid(level, pos)) {
                return true;
            }
        }
        return false;
    }

    private static boolean wall(ServerLevel level, BlockPos from, Direction direction) {
        BlockPos.MutableBlockPos pos = from.mutable();
        for (int i = 1; i <= WALL_REACH; i++) {
            pos.move(direction);
            if (solid(level, pos)) {
                return true;
            }
        }
        return false;
    }

    /** 挡得住风雨的方块：挡人走路、不是树叶（玻璃、门算）。 */
    private static boolean solid(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isSolid() && !state.is(BlockTags.LEAVES);
    }
}
