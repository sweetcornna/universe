package io.github.sweetcornna.universe.core.space;

import java.util.List;
import java.util.Optional;

import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;

/**
 * 每个恒星系的空间站（ADR-0017）：在哪、长什么样、哪里算"站内"。
 *
 * <p>空间站在第一颗能着陆的行星（母星系是母星）的大气球外 700 格的黄道面上，方位由种子定，避开别的天体；
 * 机库的开口朝向那颗行星（取最近的正方向）。建筑是一个直径 45 格的圆盘：地板在 y = 0，室内 9 格高，
 * 开口一侧是机库（地上画着停机坪，旁边是飞船维修台），另一侧是大厅（交易终端、任务板、传送器）。
 *
 * <p>方块按"本地坐标"描述：原点是地板中心，+x 朝机库开口；放进世界时按 {@link Site#hangar()} 转过去。
 */
public final class StationLayout {
    /** 圆盘的半径（格，含外墙）。 */
    public static final int RADIUS = 22;
    /** 室内高度：地板上面 1–9 层是空的，10 层是天花板。 */
    public static final int CEILING = 10;
    /** 建筑的上下范围（相对地板）。 */
    public static final int MIN_Y = -4;
    public static final int MAX_Y = 18;
    /** 离行星大气球多远。 */
    public static final double DISTANCE = 700;
    /** 机库开口：宽 17（本地 |z| ≤ 8）、高 9（1–9 层）。 */
    public static final int OPENING_HALF_WIDTH = 8;
    /** 停机坪中心（本地 x）和半径。 */
    public static final int PAD_X = 11;
    public static final int PAD_RADIUS = 7;
    /** 飞船维修台的位置（本地坐标，地板上一层）。 */
    public static final int REPAIR_X = PAD_X;
    public static final int REPAIR_Z = PAD_RADIUS + 3;
    /** 传送器：大厅里交易终端旁边，面朝机库；到站的人站在它前面两格。 */
    public static final int TELEPORTER_X = -15;
    public static final int TELEPORTER_Z = -6;
    /** 离别的天体至少这么远。 */
    private static final double CLEARANCE = 150;

    private StationLayout() {
    }

    /** 水平方向；本地 +x 是 {@link #EAST}。 */
    public enum Dir {
        SOUTH(0, 1), WEST(-1, 0), NORTH(0, -1), EAST(1, 0);

        private final int dx;
        private final int dz;

        Dir(int dx, int dz) {
            this.dx = dx;
            this.dz = dz;
        }

        public int dx() {
            return this.dx;
        }

        public int dz() {
            return this.dz;
        }

        /** 本地方向转到世界里：本地 +x 对着 hangar。 */
        public Dir rotate(Dir hangar) {
            // 按顺时针的步数相加：EAST 0、SOUTH 1、WEST 2、NORTH 3
            return fromSteps(this.steps() + hangar.steps());
        }

        private int steps() {
            return switch (this) {
                case EAST -> 0;
                case SOUTH -> 1;
                case WEST -> 2;
                case NORTH -> 3;
            };
        }

        private static Dir fromSteps(int steps) {
            return switch (Math.floorMod(steps, 4)) {
                case 0 -> EAST;
                case 1 -> SOUTH;
                case 2 -> WEST;
                default -> NORTH;
            };
        }
    }

    /** 空间站的组成部分，适配层换成具体的方块。 */
    public enum Part {
        /** 外壳：浅灰的面板。 */
        HULL,
        /** 地板：深灰的格栅。 */
        FLOOR,
        /** 窗户。 */
        GLASS,
        /** 灯板。 */
        LIGHT,
        /** 停机坪和安全线的黄黑标线。 */
        PAD,
        /** 青色的灯带。 */
        TRIM,
        /** 交易终端（朝向见 {@link Placement#facing()}）。 */
        TERMINAL,
        /** 飞船维修台，在机库里停机坪旁边。 */
        REPAIR,
        /** 传送器，在大厅里交易终端旁边。 */
        TELEPORTER
    }

    /**
     * 世界里的空间站。
     *
     * @param x      地板中心的方块坐标
     * @param y      地板所在的层
     * @param z      地板中心的方块坐标
     * @param hangar 机库开口朝哪边
     */
    public record Site(int x, int y, int z, Dir hangar) {
        /** 世界坐标转本地坐标。 */
        public int[] toLocal(int wx, int wy, int wz) {
            int dx = wx - this.x;
            int dz = wz - this.z;
            int lx;
            int lz;
            switch (this.hangar) {
                case EAST -> {
                    lx = dx;
                    lz = dz;
                }
                case SOUTH -> {
                    lx = dz;
                    lz = -dx;
                }
                case WEST -> {
                    lx = -dx;
                    lz = -dz;
                }
                default -> {
                    lx = -dz;
                    lz = dx;
                }
            }
            return new int[] {lx, wy - this.y, lz};
        }

        /** 本地坐标转世界坐标。 */
        public int[] toWorld(int lx, int ly, int lz) {
            int dx;
            int dz;
            switch (this.hangar) {
                case EAST -> {
                    dx = lx;
                    dz = lz;
                }
                case SOUTH -> {
                    dx = -lz;
                    dz = lx;
                }
                case WEST -> {
                    dx = -lx;
                    dz = -lz;
                }
                default -> {
                    dx = lz;
                    dz = -lx;
                }
            }
            return new int[] {this.x + dx, this.y + ly, this.z + dz};
        }

        /** 地板中心上方一格的中心点，传送、拖船回来用。 */
        public double[] center() {
            return new double[] {this.x + 0.5, this.y + 1, this.z + 0.5};
        }

        /** 停机坪中心（世界坐标，地板上方一格）。 */
        public double[] pad() {
            int[] world = this.toWorld(PAD_X, 1, 0);
            return new double[] {world[0] + 0.5, world[1], world[2] + 0.5};
        }

        public double distanceTo(double px, double py, double pz) {
            double dx = px - (this.x + 0.5);
            double dy = py - this.y;
            double dz = pz - (this.z + 0.5);
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        /** 点在不在站内（有重力和空气）：圆盘的外墙以内、地板和天花板之间。 */
        public boolean inside(double px, double py, double pz) {
            double dx = px - (this.x + 0.5);
            double dz = pz - (this.z + 0.5);
            double ly = py - this.y;
            return dx * dx + dz * dz < (RADIUS - 0.5) * (RADIUS - 0.5) && ly >= 0.5 && ly < CEILING;
        }

        /** 包住整座建筑的方块范围：{minX, minY, minZ, maxX, maxY, maxZ}。 */
        public int[] bounds() {
            return new int[] {this.x - RADIUS - 1, this.y + MIN_Y, this.z - RADIUS - 1, this.x + RADIUS + 1, this.y + MAX_Y, this.z + RADIUS + 1};
        }
    }

    /**
     * 一个要放的方块。
     *
     * @param facing 有朝向的方块（交易终端）在本地坐标里面朝哪边（放进世界时用 {@link Dir#rotate} 转），别的方块为 null
     */
    public record Placement(Part part, Dir facing) {
    }

    // ---- 位置

    /** 恒星系的空间站；一颗行星都没有的恒星系没有空间站。 */
    public static Optional<Site> site(StarSystem system) {
        List<SpaceLayout.Body> bodies = SpaceLayout.of(system);
        SpaceLayout.Body anchor = anchor(system, bodies);
        if (anchor == null) {
            return Optional.empty();
        }
        Rng rng = Rng.of(Seeds.derive(system.seed(), "station"));
        double start = rng.nextDouble() * 2 * Math.PI;
        double distance = anchor.radius() + DISTANCE;
        double starRadius = SpaceLayout.starRadius(system);
        double bestX = 0;
        double bestZ = 0;
        double bestMargin = Double.NEGATIVE_INFINITY;
        // 从种子定的方位开始每次转 15°，找第一个离所有天体都够远的地方；都不够远就取最空的
        for (int i = 0; i < 24; i++) {
            double angle = start + i * Math.PI / 12;
            double sx = anchor.x() + Math.cos(angle) * distance;
            double sz = anchor.z() + Math.sin(angle) * distance;
            double margin = Math.hypot(sx, sz) - starRadius - 300;
            for (SpaceLayout.Body body : bodies) {
                margin = Math.min(margin, body.distanceTo(sx, 0, sz) - body.radius() - RADIUS - CLEARANCE);
            }
            if (margin > bestMargin) {
                bestMargin = margin;
                bestX = sx;
                bestZ = sz;
            }
            if (margin >= 0) {
                break;
            }
        }
        int x = (int) Math.floor(bestX);
        int z = (int) Math.floor(bestZ);
        // 机库朝向行星
        double toX = anchor.x() - x;
        double toZ = anchor.z() - z;
        Dir hangar = Math.abs(toX) >= Math.abs(toZ) ? (toX >= 0 ? Dir.EAST : Dir.WEST) : (toZ >= 0 ? Dir.SOUTH : Dir.NORTH);
        return Optional.of(new Site(x, 0, z, hangar));
    }

    /** 空间站绕着的那颗行星。 */
    public static Optional<Planet> anchor(StarSystem system) {
        SpaceLayout.Body anchor = anchor(system, SpaceLayout.of(system));
        return anchor == null ? Optional.empty() : Optional.of(anchor.planet());
    }

    /** 空间站绕着的行星：母星系是母星，别的恒星系是第一颗能着陆的行星，没有就是第一颗行星。 */
    private static SpaceLayout.Body anchor(StarSystem system, List<SpaceLayout.Body> bodies) {
        SpaceLayout.Body first = null;
        for (SpaceLayout.Body body : bodies) {
            if (body.id().equals(GalaxyGenerator.HOME_PLANET_ID)) {
                return body;
            }
        }
        for (SpaceLayout.Body body : bodies) {
            if (body.planet().moon()) {
                continue;
            }
            if (first == null) {
                first = body;
            }
            if (body.planet().landable()) {
                return body;
            }
        }
        return first;
    }

    // ---- 建筑

    /** 本地坐标 (x, y, z) 上放什么；空气返回 null。 */
    public static Placement at(int x, int y, int z) {
        if (y < MIN_Y || y > MAX_Y) {
            return null;
        }
        double r = Math.sqrt(x * x + z * z);
        if (y < 0) {
            return below(r, y);
        }
        if (y == 0) {
            return floor(x, z, r);
        }
        if (y < CEILING) {
            return room(x, y, z, r);
        }
        if (y == CEILING) {
            if (r > RADIUS + 0.5) {
                return null;
            }
            boolean lamp = r < RADIUS - 2.5 && Math.floorMod(x, 4) == 2 && Math.floorMod(z, 4) == 2;
            return place(lamp ? Part.LIGHT : Part.HULL);
        }
        return roof(x, y, z, r);
    }

    /** 地板下面：一层层收窄的船底，正中一盏灯。 */
    private static Placement below(double r, int y) {
        double limit = switch (y) {
            case -1 -> RADIUS - 0.5;
            case -2 -> RADIUS - 3.5;
            case -3 -> RADIUS - 8.5;
            default -> 6.5;
        };
        if (r > limit) {
            return null;
        }
        if (y == MIN_Y && r < 2.5) {
            return place(Part.LIGHT);
        }
        return place(Part.HULL);
    }

    /** 地板：外圈是外壳，机库里画停机坪，机库和大厅之间一道安全线。 */
    private static Placement floor(int x, int z, double r) {
        if (r > RADIUS + 0.5) {
            return null;
        }
        if (r > RADIUS - 1.5) {
            return place(Part.HULL);
        }
        if (x == 1 && Math.abs(z) <= RADIUS - 3 && Math.floorMod(z, 2) == 0) {
            return place(Part.PAD);
        }
        double pad = Math.hypot(x - PAD_X, z);
        boolean ring = pad > PAD_RADIUS - 1 && pad <= PAD_RADIUS;
        // 停机坪中间的 H
        int hx = x - PAD_X;
        boolean letter = (Math.abs(hx) <= 2 && (z == -2 || z == 2)) || (hx == 0 && Math.abs(z) <= 2);
        if (ring || letter) {
            return place(Part.PAD);
        }
        // 大厅地上一圈灯带
        if (x < -2 && Math.abs(r - 12) < 0.5) {
            return place(Part.TRIM);
        }
        return place(Part.FLOOR);
    }

    /** 室内：外墙（两格厚，开窗）、机库开口、大厅里的柱子和交易终端。 */
    private static Placement room(int x, int y, int z, double r) {
        if (r > RADIUS + 0.5) {
            return null;
        }
        if (r > RADIUS - 1.5) {
            // 机库开口
            if (x > 0 && Math.abs(z) <= OPENING_HALF_WIDTH) {
                return null;
            }
            // 开口两边的灯带
            if (x > 0 && Math.abs(z) == OPENING_HALF_WIDTH + 1) {
                return place(Part.TRIM);
            }
            // 窗户：每 30° 一扇，3–6 层
            double angle = Math.toDegrees(Math.atan2(z, x));
            if (y >= 3 && y <= 6 && Math.floorMod((int) Math.round(angle), 30) < 12) {
                return place(Part.GLASS);
            }
            if (y == 8 && r <= RADIUS - 0.5) {
                return place(Part.TRIM);
            }
            // 外墙上一圈灯带，远处也看得见
            if (y == 9 && r > RADIUS - 0.5) {
                return place(Part.TRIM);
            }
            return place(Part.HULL);
        }
        // 大厅的四根柱子
        if (x == -10 && Math.abs(z) == 10 || x == -16 && Math.abs(z) == 5) {
            return place(y == 5 ? Part.TRIM : Part.HULL);
        }
        // 交易终端：靠大厅里侧，面朝机库
        if (y == 1 && x == -15 && z == 0) {
            return new Placement(Part.TERMINAL, Dir.EAST);
        }
        if (y == 1 && x == TELEPORTER_X && z == TELEPORTER_Z) {
            return new Placement(Part.TELEPORTER, Dir.EAST);
        }
        // 维修台：停机坪的一侧，面朝停机坪
        if (y == 1 && x == REPAIR_X && z == REPAIR_Z) {
            return new Placement(Part.REPAIR, Dir.NORTH);
        }
        return null;
    }

    /** 屋顶：收窄的穹顶，正中一根天线，顶上一盏灯。 */
    private static Placement roof(int x, int y, int z, double r) {
        double limit = switch (y) {
            case CEILING + 1 -> RADIUS - 2.5;
            case CEILING + 2 -> RADIUS - 7.5;
            case CEILING + 3 -> RADIUS - 13.5;
            default -> -1;
        };
        if (r <= limit) {
            return place(Part.HULL);
        }
        if (x == 0 && z == 0 && y > CEILING + 3) {
            return place(y == MAX_Y ? Part.LIGHT : Part.HULL);
        }
        return null;
    }

    private static Placement place(Part part) {
        return new Placement(part, null);
    }
}
