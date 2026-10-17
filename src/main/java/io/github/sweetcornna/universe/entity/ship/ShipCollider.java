package io.github.sweetcornna.universe.entity.ship;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.core.ship.ShipShape;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 飞船和地形的碰撞：船身每个外露的方块当成一个小盒子，逐轴扫掠（和原版实体的做法一样，只是盒子多）。
 *
 * <p>转过非 90° 角时方块不再和网格对齐，这里仍按对齐的盒子算，转角处会有一点穿插，换来简单和快。
 * 飞船自己的方块在飞行时不在世界里，不会和自己相撞。
 */
final class ShipCollider {
    /** 盒子水平方向的半宽略小于半格，贴着墙飞或者穿过一格宽的缝时不会卡住。 */
    private static final double HALF_XZ = 0.495;
    private static final double EPSILON = 1.0E-7;

    private final Level level;
    /** 这一刻查过的方块碰撞形状（已平移到世界坐标），空形状不存。 */
    private final Long2ObjectOpenHashMap<VoxelShape> cache = new Long2ObjectOpenHashMap<>();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    ShipCollider(Level level) {
        this.level = level;
    }

    /** 每刻开始时清空缓存：地形可能变了。 */
    void reset() {
        this.cache.clear();
    }

    /** 船身外露方块在世界里的盒子。 */
    static List<AABB> boxes(ShipStructure structure, double x, double y, double z, float relativeYaw) {
        ShipShape shape = structure.shape();
        double r = Math.toRadians(relativeYaw);
        double c = Math.cos(r);
        double s = Math.sin(r);
        List<AABB> boxes = new ArrayList<>();
        for (int i = 0; i < shape.size(); i++) {
            if (!shape.exposed(i)) {
                continue;
            }
            double lx = shape.x(i);
            double lz = shape.z(i);
            double cx = x + lx * c - lz * s;
            double cz = z + lx * s + lz * c;
            double by = y + shape.y(i);
            boxes.add(new AABB(cx - HALF_XZ, by, cz - HALF_XZ, cx + HALF_XZ, by + 1, cz + HALF_XZ));
        }
        return boxes;
    }

    /** 盒子沿 {@code motion} 移动时实际能走多远。 */
    Vec3 collide(List<AABB> boxes, Vec3 motion) {
        if (motion.lengthSqr() < EPSILON || boxes.isEmpty()) {
            return motion;
        }
        AABB all = union(boxes);
        List<VoxelShape> shapes = this.shapesIn(all.expandTowards(motion).inflate(EPSILON));
        if (shapes.isEmpty()) {
            return motion;
        }
        double dx = motion.x;
        double dy = motion.y;
        double dz = motion.z;
        List<AABB> moved = boxes;
        if (dy != 0) {
            dy = collideAxis(Direction.Axis.Y, moved, shapes, dy);
            moved = offset(moved, 0, dy, 0);
        }
        boolean zFirst = Math.abs(dx) < Math.abs(dz);
        if (zFirst && dz != 0) {
            dz = collideAxis(Direction.Axis.Z, moved, shapes, dz);
            moved = offset(moved, 0, 0, dz);
        }
        if (dx != 0) {
            dx = collideAxis(Direction.Axis.X, moved, shapes, dx);
            moved = offset(moved, dx, 0, 0);
        }
        if (!zFirst && dz != 0) {
            dz = collideAxis(Direction.Axis.Z, moved, shapes, dz);
        }
        return new Vec3(dx, dy, dz);
    }

    /** 盒子所在的地方是空的（转向前检查转过去会不会嵌进地形）。 */
    boolean fits(List<AABB> boxes) {
        for (AABB box : boxes) {
            AABB inner = box.deflate(0.05);
            VoxelShape boxShape = null;
            for (VoxelShape shape : this.shapesIn(inner)) {
                if (boxShape == null) {
                    boxShape = Shapes.create(inner);
                }
                if (Shapes.joinIsNotEmpty(shape, boxShape, BooleanOp.AND)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 船底贴着地面。 */
    boolean onGround(List<AABB> boxes) {
        double probe = -0.05;
        return this.collide(boxes, new Vec3(0, probe, 0)).y > probe + 1.0E-4;
    }

    private static double collideAxis(Direction.Axis axis, List<AABB> boxes, List<VoxelShape> shapes, double distance) {
        for (AABB box : boxes) {
            distance = Shapes.collide(axis, box, shapes, distance);
            if (Math.abs(distance) < EPSILON) {
                return 0;
            }
        }
        return distance;
    }

    /** 区域内的方块碰撞形状。整段空气的区块分段直接跳过，没加载的区块当成空的。 */
    private List<VoxelShape> shapesIn(AABB area) {
        List<VoxelShape> shapes = new ArrayList<>();
        int x0 = (int) Math.floor(area.minX);
        int y0 = Math.max(this.level.getMinY(), (int) Math.floor(area.minY));
        int z0 = (int) Math.floor(area.minZ);
        int x1 = (int) Math.floor(area.maxX);
        int y1 = Math.min(this.level.getMaxY(), (int) Math.floor(area.maxY));
        int z1 = (int) Math.floor(area.maxZ);
        if (y0 > y1) {
            return shapes;
        }
        for (int cx = SectionPos.blockToSectionCoord(x0); cx <= SectionPos.blockToSectionCoord(x1); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(z0); cz <= SectionPos.blockToSectionCoord(z1); cz++) {
                if (!this.level.hasChunk(cx, cz)) {
                    continue;
                }
                LevelChunk chunk = this.level.getChunk(cx, cz);
                LevelChunkSection[] sections = chunk.getSections();
                for (int sy = SectionPos.blockToSectionCoord(y0); sy <= SectionPos.blockToSectionCoord(y1); sy++) {
                    int index = this.level.getSectionIndexFromSectionY(sy);
                    if (index < 0 || index >= sections.length || sections[index].hasOnlyAir()) {
                        continue;
                    }
                    int bx0 = Math.max(x0, SectionPos.sectionToBlockCoord(cx));
                    int bx1 = Math.min(x1, SectionPos.sectionToBlockCoord(cx, 15));
                    int by0 = Math.max(y0, SectionPos.sectionToBlockCoord(sy));
                    int by1 = Math.min(y1, SectionPos.sectionToBlockCoord(sy, 15));
                    int bz0 = Math.max(z0, SectionPos.sectionToBlockCoord(cz));
                    int bz1 = Math.min(z1, SectionPos.sectionToBlockCoord(cz, 15));
                    for (int x = bx0; x <= bx1; x++) {
                        for (int y = by0; y <= by1; y++) {
                            for (int z = bz0; z <= bz1; z++) {
                                VoxelShape shape = this.shapeAt(chunk, x, y, z);
                                if (shape != null) {
                                    shapes.add(shape);
                                }
                            }
                        }
                    }
                }
            }
        }
        return shapes;
    }

    private VoxelShape shapeAt(LevelChunk chunk, int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        if (this.cache.containsKey(key)) {
            return this.cache.get(key);
        }
        this.cursor.set(x, y, z);
        VoxelShape shape = chunk.getBlockState(this.cursor).getCollisionShape(this.level, this.cursor, CollisionContext.empty());
        VoxelShape placed = shape.isEmpty() ? null : shape.move(x, y, z);
        this.cache.put(key, placed);
        return placed;
    }

    private static AABB union(List<AABB> boxes) {
        double x0 = Double.MAX_VALUE;
        double y0 = Double.MAX_VALUE;
        double z0 = Double.MAX_VALUE;
        double x1 = -Double.MAX_VALUE;
        double y1 = -Double.MAX_VALUE;
        double z1 = -Double.MAX_VALUE;
        for (AABB box : boxes) {
            x0 = Math.min(x0, box.minX);
            y0 = Math.min(y0, box.minY);
            z0 = Math.min(z0, box.minZ);
            x1 = Math.max(x1, box.maxX);
            y1 = Math.max(y1, box.maxY);
            z1 = Math.max(z1, box.maxZ);
        }
        return new AABB(x0, y0, z0, x1, y1, z1);
    }

    private static List<AABB> offset(List<AABB> boxes, double dx, double dy, double dz) {
        List<AABB> moved = new ArrayList<>(boxes.size());
        for (AABB box : boxes) {
            moved.add(box.move(dx, dy, dz));
        }
        return moved;
    }
}
