package io.github.sweetcornna.universe.core.ship;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import io.github.sweetcornna.universe.core.tech.Grade;

/**
 * 飞船设计器生成的一艘船（ADR-0024）：每格放什么部件，坐标是船自己的，船头朝 -z（北），y 向上。
 *
 * @param type    设计时要的类型
 * @param grade   设计时要的档次
 * @param primary 船体板的主色（染料序号 0–15）
 * @param accent  点缀色（染料序号）
 * @param cells   每一格
 */
public record ShipDesign(ShipType type, Grade grade, int primary, int accent, List<Cell> cells) {
    /** 一格放的东西。适配层把它换成具体的方块。 */
    public enum Role {
        /** 主色的船体板。 */
        HULL(ShipPart.PLATING),
        /** 点缀色的船体板。 */
        TRIM(ShipPart.PLATING),
        ALLOY(ShipPart.ALLOY_PLATING),
        /** 驾驶舱的玻璃。 */
        GLASS(ShipPart.LIGHT),
        /** 翼尖的灯（实心方块）。 */
        LAMP(ShipPart.HULL),
        HELM(ShipPart.HELM),
        THRUSTER(ShipPart.THRUSTER),
        THRUSTER_MK2(ShipPart.THRUSTER_MK2),
        FUEL_TANK(ShipPart.FUEL_TANK),
        FUEL_TANK_MK2(ShipPart.FUEL_TANK_MK2),
        IMPULSE(ShipPart.IMPULSE_ENGINE),
        IMPULSE_MK2(ShipPart.IMPULSE_ENGINE_MK2),
        WARP(ShipPart.WARP_DRIVE),
        WARP_MK2(ShipPart.WARP_DRIVE_MK2),
        CANNON(ShipPart.PHOTON_CANNON),
        SHIELD(ShipPart.SHIELD_GENERATOR),
        /** 货箱（原版箱子，27 格）。 */
        CARGO(ShipPart.HULL);

        private final ShipPart part;

        Role(ShipPart part) {
            this.part = part;
        }

        public ShipPart part() {
            return this.part;
        }

        /** 升级成 Mk.II 以后的样子；没有 Mk.II 的返回自己。 */
        Role advanced() {
            return switch (this) {
                case THRUSTER -> THRUSTER_MK2;
                case FUEL_TANK -> FUEL_TANK_MK2;
                case IMPULSE -> IMPULSE_MK2;
                case WARP -> WARP_MK2;
                default -> this;
            };
        }
    }

    /** 有朝向的部件的朝向：喷口、炮口、驾驶台都按这个摆。 */
    public enum Face {
        NORTH, SOUTH, EAST, WEST, UP, DOWN
    }

    public record Cell(int x, int y, int z, Role role, Face face) {
    }

    /** 一个货箱的格数。 */
    public static final int CARGO_SLOTS = 27;

    public Map<ShipPart, Integer> parts() {
        Map<ShipPart, Integer> counts = new EnumMap<>(ShipPart.class);
        for (Cell cell : this.cells) {
            counts.merge(cell.role().part(), 1, Integer::sum);
        }
        return counts;
    }

    public int cargoSlots() {
        int n = 0;
        for (Cell cell : this.cells) {
            if (cell.role() == Role.CARGO) {
                n += CARGO_SLOTS;
            }
        }
        return n;
    }

    public ShipStats stats() {
        return ShipStats.of(this.parts());
    }

    public ShipRating rating() {
        return ShipRating.of(this.parts(), this.cargoSlots());
    }

    /** 包围盒的大小：x、y、z 方向各几格。 */
    public int[] size() {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Cell c : this.cells) {
            minX = Math.min(minX, c.x());
            minY = Math.min(minY, c.y());
            minZ = Math.min(minZ, c.z());
            maxX = Math.max(maxX, c.x());
            maxY = Math.max(maxY, c.y());
            maxZ = Math.max(maxZ, c.z());
        }
        return new int[] {maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1};
    }

    /** 最低的一格的 y（落地时这一层贴着地面）。 */
    public int minY() {
        int min = Integer.MAX_VALUE;
        for (Cell c : this.cells) {
            min = Math.min(min, c.y());
        }
        return min;
    }
}
