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
 * <p>空间站在第一颗能着陆的行星（母星系是母星）的大气球外 {@value #DISTANCE} 格的黄道面上，方位由种子定，避开别的天体；
 * 机库的开口朝向那颗行星（取最近的正方向）。建筑是一个直径 45 格的圆盘：地板在 y = 0，室内 9 格高，
 * 开口一侧是机库（地上画着停机坪，旁边是飞船维修台），另一侧是大厅（交易终端、任务板、传送器）。
 * 圆盘外面（ADR-0021）：机库开口前两排引导灯，背后一根桁架通到一面碟形天线，两边伸出两百多格宽的太阳翼。
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
    public static final double DISTANCE = 900;
    /** 机库开口：宽 17（本地 |z| ≤ 8）、高 9（1–9 层）。 */
    public static final int OPENING_HALF_WIDTH = 8;
    /** 停机坪中心（本地 x）和半径。 */
    public static final int PAD_X = 11;
    public static final int PAD_RADIUS = 7;
    /** 飞船维修台的位置（本地坐标，地板上一层）。 */
    public static final int REPAIR_X = PAD_X;
    public static final int REPAIR_Z = PAD_RADIUS + 3;
    /** 飞船交易终端：停机坪另一边，和维修台对称（ADR-0024）。 */
    public static final int DEALER_X = PAD_X;
    public static final int DEALER_Z = -REPAIR_Z;
    /** 传送器：大厅里交易终端旁边，面朝机库；到站的人站在它前面两格。 */
    public static final int TELEPORTER_X = -15;
    public static final int TELEPORTER_Z = -6;
    /** 任务板：交易终端另一边，和传送器对称。 */
    public static final int MISSION_BOARD_X = -15;
    public static final int MISSION_BOARD_Z = 6;
    /** 离别的天体至少这么远。 */
    private static final double CLEARANCE = 400;
    /** 机库开口前的引导灯从外墙往外排多远（ADR-0021）。 */
    public static final int APPROACH_LENGTH = 96;
    /** 背后的桁架多长，尽头是一面碟形天线。 */
    static final int SPINE_LENGTH = 72;
    static final int DISH_RADIUS = 8;
    /** 两翼的太阳能板：横梁在本地 x = {@value #WING_X}、y = {@value #WING_Y}，每边从外墙往外伸 {@value #WING_SPAN} 格。 */
    static final int WING_X = -8;
    static final int WING_Y = 5;
    static final int WING_SPAN = 92;
    /** 整座空间站（连同两翼）离中心最远多少格。 */
    public static final int EXTENT = RADIUS + WING_SPAN;

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
        /** 飞船交易终端，在停机坪另一边，和维修台对着。 */
        SHIP_DEALER,
        /** 传送器，在大厅里交易终端旁边。 */
        TELEPORTER,
        /** 任务板，在交易终端另一边。 */
        MISSION_BOARD,
        /** 两翼的太阳能板。 */
        SOLAR
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

        /** 包住整座建筑（连同外面的结构）的方块范围：{minX, minY, minZ, maxX, maxY, maxZ}。 */
        public int[] bounds() {
            int back = RADIUS + SPINE_LENGTH + DISH_RADIUS;
            int front = RADIUS + APPROACH_LENGTH;
            int side = EXTENT + 1;
            int[] a = this.toWorld(-back, MIN_Y, -side);
            int[] b = this.toWorld(front, MAX_Y, side);
            return new int[] {Math.min(a[0], b[0]), this.y + MIN_Y, Math.min(a[2], b[2]), Math.max(a[0], b[0]), this.y + MAX_Y, Math.max(a[2], b[2])};
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
            double margin = Math.hypot(sx, sz) - starRadius - 3000;
            for (SpaceLayout.Body body : bodies) {
                margin = Math.min(margin, body.distanceTo(sx, 0, sz) - body.radius() - EXTENT - CLEARANCE);
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
        if (r > RADIUS + 0.5) {
            return exterior(x, y, z);
        }
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
        if (y == 1 && x == MISSION_BOARD_X && z == MISSION_BOARD_Z) {
            return new Placement(Part.MISSION_BOARD, Dir.EAST);
        }
        // 维修台：停机坪的一侧，面朝停机坪
        if (y == 1 && x == REPAIR_X && z == REPAIR_Z) {
            return new Placement(Part.REPAIR, Dir.NORTH);
        }
        if (y == 1 && x == DEALER_X && z == DEALER_Z) {
            return new Placement(Part.SHIP_DEALER, Dir.SOUTH);
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

    /** 圆盘外面：引导灯、桁架和天线、太阳翼。 */
    private static Placement exterior(int x, int y, int z) {
        // 机库开口前两排引导灯，地板和天花板的高度各一排，每 8 格一盏
        if (x > RADIUS && x <= RADIUS + APPROACH_LENGTH && (x - RADIUS) % 8 == 0 && Math.abs(z) == OPENING_HALF_WIDTH + 4
                && (y == 0 || y == CEILING)) {
            return place(Part.LIGHT);
        }
        if (x < -RADIUS) {
            return spine(-x - RADIUS, y, z);
        }
        int out = Math.abs(z) - RADIUS;
        if (out < -2 || out > WING_SPAN) {
            return null;
        }
        // 太阳翼的横梁，尖上一盏灯
        if (x == WING_X && y == WING_Y) {
            return place(Part.HULL);
        }
        if (out == WING_SPAN && x == WING_X && y == WING_Y + 1) {
            return place(Part.LIGHT);
        }
        // 四块太阳能板，每块 18 格长、19 格宽，中间隔 4 格露出横梁，边上一圈灯带
        if (y == WING_Y && out >= 6) {
            int k = (out - 6) % 22;
            int across = Math.abs(x - WING_X);
            if (k < 18 && across <= 9) {
                return place(k == 0 || k == 17 || across == 9 ? Part.TRIM : Part.SOLAR);
            }
        }
        return null;
    }

    /** 背后的桁架（{@code along} 是离外墙多远）：5×5 的方管，四条边梁、面上斜撑，每 12 格一圈灯带；中段两侧一排散热片；尽头一面朝外的碟形天线。 */
    private static Placement spine(int along, int y, int z) {
        int az = Math.abs(z);
        if (along <= SPINE_LENGTH) {
            if (az <= 2 && y >= 2 && y <= 6) {
                boolean face = az == 2 || y == 2 || y == 6;
                if (!face) {
                    return null;
                }
                if (along % 12 == 0) {
                    return place(Part.TRIM);
                }
                boolean beam = az == 2 && (y == 2 || y == 6);
                return beam || Math.floorMod(along + y + z, 4) == 0 ? place(Part.HULL) : null;
            }
            if (y == 4 && along >= 10 && along <= 50 && az >= 3 && az <= 14 && along % 10 < 7) {
                return place(Part.HULL);
            }
            return null;
        }
        int out = along - SPINE_LENGTH;
        double rr = Math.hypot(z, y - 4);
        // 碟面：中间离桁架最近，往外翘，凹面朝外
        if (rr <= DISH_RADIUS + 0.5 && out == 1 + (int) Math.round(rr * rr / (DISH_RADIUS * 3.0))) {
            return place(Part.HULL);
        }
        // 馈源杆，顶上一盏灯
        if (z == 0 && y == 4 && out >= 2 && out <= 7) {
            return place(out == 7 ? Part.LIGHT : Part.HULL);
        }
        return null;
    }

    private static Placement place(Part part) {
        return new Placement(part, null);
    }
}
