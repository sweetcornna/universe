package io.github.sweetcornna.universe.deepspace;

import io.github.sweetcornna.universe.core.deepspace.Layout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * 一座残骸、前哨站在世界里的摆法：局部坐标的原点放在哪，绕竖轴转了几个 90°（按种子转，不是每艘都头朝 +x）。
 *
 * @param rotation 0–3；转一下是把局部的 +x 转到世界的 +z（顺时针，从上往下看）
 */
public record Placement(BlockPos origin, int rotation) {
    public Placement {
        rotation = Math.floorMod(rotation, 4);
    }

    /** 让局部的包围盒中心落在 {@code center}。 */
    public static Placement centered(Layout layout, Vec3 center, int rotation) {
        double[] c = layout.center();
        Placement zero = new Placement(BlockPos.ZERO, rotation);
        int[] r = zero.rotate((int) Math.floor(c[0]), (int) Math.floor(c[2]));
        return new Placement(BlockPos.containing(center.x - r[0], center.y - Math.floor(c[1]), center.z - r[1]), rotation);
    }

    private int[] rotate(int x, int z) {
        return switch (this.rotation) {
            case 1 -> new int[] {-z, x};
            case 2 -> new int[] {-x, -z};
            case 3 -> new int[] {z, -x};
            default -> new int[] {x, z};
        };
    }

    public BlockPos world(int x, int y, int z) {
        int[] r = this.rotate(x, z);
        return this.origin.offset(r[0], y, r[1]);
    }

    public BlockPos world(Layout.Cell cell) {
        return this.world(cell.x(), cell.y(), cell.z());
    }

    /** 局部坐标（可以是小数，比如方块中心）在世界里的位置。 */
    public Vec3 world(double x, double y, double z) {
        double wx;
        double wz;
        switch (this.rotation) {
            case 1 -> {
                wx = -z + 1;
                wz = x;
            }
            case 2 -> {
                wx = -x + 1;
                wz = -z + 1;
            }
            case 3 -> {
                wx = z;
                wz = -x + 1;
            }
            default -> {
                wx = x;
                wz = z;
            }
        }
        return new Vec3(this.origin.getX() + wx, this.origin.getY() + y, this.origin.getZ() + wz);
    }

    public Direction.Axis axis(Direction.Axis axis) {
        if (axis == Direction.Axis.Y || this.rotation % 2 == 0) {
            return axis;
        }
        return axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
    }

    public Direction facing(Direction facing) {
        Direction result = facing;
        for (int i = 0; i < this.rotation; i++) {
            result = result.getClockWise();
        }
        return result;
    }

    /** 包住整座的方块范围，外面多放一格。 */
    public BoundingBox box(Layout layout) {
        int[] min = layout.min();
        int[] max = layout.max();
        BlockPos a = this.world(min[0], min[1], min[2]);
        BlockPos b = this.world(max[0], max[1], max[2]);
        return BoundingBox.fromCorners(a, b).inflatedBy(1);
    }
}
