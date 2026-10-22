package io.github.sweetcornna.universe.core.ship;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.ship.ShipDesign.Cell;
import io.github.sweetcornna.universe.core.ship.ShipDesign.Face;
import io.github.sweetcornna.universe.core.ship.ShipDesign.Role;
import io.github.sweetcornna.universe.core.tech.Grade;

/**
 * 飞船设计器（ADR-0024）：按类型、档次和种子生成一艘能飞的船。空间站卖的船、星球上坠毁的船都从这里来。
 *
 * <p>船身是一根头尖尾平的方柱，驾驶台在前面的顶上，三面围着玻璃。类型决定机翼和挂什么：战斗机是一对宽翼，
 * 翼前挂光子炮；运输船船身宽，两侧是货舱，箱子藏在船身里；探索船身子长，背上一排跃迁引擎和燃料舱，尾巴上
 * 有尾翼；穿梭机短小，一对短翼。推进器挂在肚子下面朝下喷，尾部朝后喷，再加一台脉冲引擎。
 *
 * <p>档次靠换用料凑：先按普通部件摆好，品质分不够就把部件逐个换成 Mk.II，还不够就把背上的船体板换成合金。
 * 最后补推进器，直到 1 g 下推重比至少 {@link #MIN_TWR}。
 */
public final class ShipDesigner {
    /** 卖的船在 1 g 下的推重比至少这么多。 */
    public static final double MIN_TWR = 1.5;
    /** 最长（含尾部推进器）。空间站起降台的直径是 15 格。 */
    public static final int MAX_LENGTH = 15;
    /** 换用料最多换几件（防止死循环；正常几十件以内就够了）。 */
    private static final int STEPS = 400;

    private final Rng rng;
    private final ShipType type;
    private final Grade grade;
    private final Map<Long, Cell> cells = new LinkedHashMap<>();
    private int length;
    private int halfWidth;
    private int height;
    private final List<long[]> belly = new ArrayList<>();
    private int nextBelly;

    private ShipDesigner(long seed, ShipType type, Grade grade) {
        this.rng = Rng.of(seed);
        this.type = type;
        this.grade = grade;
    }

    public static ShipDesign design(long seed, ShipType type, Grade grade) {
        return new ShipDesigner(seed, type, grade).build();
    }

    private ShipDesign build() {
        int primary = this.rng.nextInt(16);
        int accent = (primary + 1 + this.rng.nextInt(15)) % 16;
        switch (this.type) {
            case SHUTTLE -> this.shape(this.rng.range(7, 9), 1, 2);
            case FIGHTER -> this.shape(this.rng.range(9, 12), 1, 2);
            case HAULER -> this.shape(this.rng.range(11, 13), 2, 3);
            case EXPLORER -> this.shape(this.rng.range(11, 13), 1, 2);
        }
        this.body();
        int cockpit = this.cockpit();
        switch (this.type) {
            case SHUTTLE -> this.wings(2, 2, this.length / 2);
            case FIGHTER -> this.wings(this.rng.range(3, 4), 3, this.length / 2 - 1);
            case HAULER -> this.pods();
            case EXPLORER -> this.fins();
        }
        this.tail();
        this.equip(cockpit);
        this.upgrade();
        return new ShipDesign(this.type, this.grade, primary, accent, List.copyOf(this.cells.values()));
    }

    private void shape(int length, int halfWidth, int height) {
        this.length = length;
        this.halfWidth = halfWidth;
        this.height = height;
    }

    // ---- 外形

    /** 船身：z = 0 是尖头，往后变宽变高。顶上中间一条点缀色。 */
    private void body() {
        for (int z = 0; z < this.length; z++) {
            int hw = z == 0 ? 0 : z == 1 ? Math.min(1, this.halfWidth) : this.halfWidth;
            int h = z == 0 ? Math.max(1, this.height - 1) : this.height;
            for (int x = -hw; x <= hw; x++) {
                for (int y = 0; y < h; y++) {
                    boolean stripe = z >= 2 && x == 0 && y == this.height - 1;
                    this.set(x, y, z, stripe ? Role.TRIM : Role.HULL, Face.NORTH);
                }
            }
            if (z >= 2 && z < this.length - 1) {
                for (int x = -this.halfWidth; x <= this.halfWidth; x++) {
                    this.belly.add(new long[] {x, -1, z});
                }
            }
        }
    }

    /** 驾驶台在第三格的顶上，前、左、右、上是玻璃，后面一块点缀色当靠背。返回驾驶台的 z。 */
    private int cockpit() {
        int z = 2;
        int y = this.height;
        this.set(0, y, z, Role.HELM, Face.NORTH);
        this.set(0, y, z - 1, Role.GLASS, Face.NORTH);
        this.set(-1, y, z, Role.GLASS, Face.NORTH);
        this.set(1, y, z, Role.GLASS, Face.NORTH);
        this.set(0, y + 1, z, Role.GLASS, Face.NORTH);
        this.set(0, y, z + 1, Role.TRIM, Face.NORTH);
        return z;
    }

    /** 一对平的机翼，贴着船身底层往两边伸，翼尖后角是灯。 */
    private void wings(int span, int depth, int root) {
        for (int side = -1; side <= 1; side += 2) {
            for (int s = 1; s <= span; s++) {
                int x = side * (this.halfWidth + s);
                // 越往外越短：翼尖往后收
                int front = root + (s - 1) / 2;
                for (int z = front; z < root + depth; z++) {
                    this.set(x, 0, z, s == span ? Role.TRIM : Role.HULL, Face.NORTH);
                    this.belly.add(new long[] {x, -1, z});
                }
            }
            this.set(side * (this.halfWidth + span), 0, root + depth, Role.LAMP, Face.NORTH);
        }
    }

    /** 运输船两侧的货舱：贴着船身的两格高的长条。 */
    private void pods() {
        for (int side = -1; side <= 1; side += 2) {
            int x = side * (this.halfWidth + 1);
            for (int z = 3; z < this.length - 2; z++) {
                for (int y = 0; y < 2; y++) {
                    this.set(x, y, z, y == 1 ? Role.TRIM : Role.HULL, Face.NORTH);
                }
                this.belly.add(new long[] {x, -1, z});
            }
            this.set(x, 1, this.length - 2, Role.LAMP, Face.NORTH);
        }
    }

    /** 探索船：尾部一对斜着往后的侧翼，背上一片竖尾翼。 */
    private void fins() {
        int root = this.length - 4;
        for (int side = -1; side <= 1; side += 2) {
            for (int s = 1; s <= 2; s++) {
                for (int z = root + s - 1; z < this.length; z++) {
                    this.set(side * (this.halfWidth + s), 0, z, s == 2 ? Role.TRIM : Role.HULL, Face.NORTH);
                }
            }
            this.set(side * (this.halfWidth + 2), 0, this.length, Role.LAMP, Face.NORTH);
        }
        for (int z = this.length - 3; z < this.length; z++) {
            this.set(0, this.height, z, Role.TRIM, Face.NORTH);
        }
        this.set(0, this.height + 1, this.length - 2, Role.TRIM, Face.NORTH);
        this.set(0, this.height + 1, this.length - 1, Role.LAMP, Face.NORTH);
    }

    /** 尾部：中间一台脉冲引擎，两边各一个朝后喷的推进器。 */
    private void tail() {
        int z = this.length;
        int y = this.height > 2 ? 1 : 0;
        this.set(0, y, z, Role.IMPULSE, Face.SOUTH);
        this.set(-this.halfWidth, 0, z, Role.THRUSTER, Face.SOUTH);
        this.set(this.halfWidth, 0, z, Role.THRUSTER, Face.SOUTH);
        if (this.halfWidth == 0) {
            throw new IllegalStateException("body too thin");
        }
    }

    // ---- 部件

    private void equip(int cockpit) {
        // 背上从驾驶台后面第二格往后排；运输船的箱子和燃料舱藏在船身里
        List<long[]> spine = new ArrayList<>();
        for (int z = cockpit + 2; z < this.length - 1; z++) {
            if (this.type == ShipType.EXPLORER && z >= this.length - 3) {
                break;
            }
            spine.add(new long[] {0, this.height, z});
        }
        for (int z = cockpit + 2; z < this.length - 1; z++) {
            for (int side = -1; side <= 1; side += 2) {
                if (this.halfWidth >= 1) {
                    spine.add(new long[] {side, this.height, z});
                }
            }
        }
        List<long[]> inside = new ArrayList<>();
        if (this.halfWidth >= 2 && this.height >= 3) {
            for (int z = 3; z < this.length - 1; z++) {
                for (int x = -this.halfWidth + 1; x <= this.halfWidth - 1; x++) {
                    inside.add(new long[] {x, 1, z});
                }
            }
        }
        int warps;
        int tanks;
        int cannons;
        int shields = 0;
        int cargo = 0;
        switch (this.type) {
            case SHUTTLE -> {
                warps = 1;
                tanks = this.rng.range(1, 2);
                cannons = 1;
            }
            case FIGHTER -> {
                warps = 1;
                tanks = this.rng.range(1, 2);
                cannons = this.grade == Grade.C ? 2 : this.rng.range(2, 4);
                shields = this.grade.ordinal() >= Grade.B.ordinal() ? 1 : 0;
            }
            case HAULER -> {
                warps = 1;
                tanks = this.rng.range(2, 3);
                cannons = 1;
                cargo = this.rng.range(2, 4);
                shields = this.grade.ordinal() >= Grade.A.ordinal() ? 1 : 0;
            }
            default -> {
                warps = this.rng.range(2, 3);
                tanks = this.rng.range(3, 4);
                cannons = this.rng.chance(0.5) ? 1 : 0;
            }
        }
        int s = 0;
        int in = 0;
        for (int i = 0; i < cargo; i++) {
            if (in < inside.size()) {
                long[] p = inside.get(in++);
                this.set((int) p[0], (int) p[1], (int) p[2], Role.CARGO, Face.NORTH);
            }
        }
        for (int i = 0; i < warps && s < spine.size(); i++) {
            long[] p = spine.get(s++);
            this.set((int) p[0], (int) p[1], (int) p[2], Role.WARP, Face.NORTH);
        }
        for (int i = 0; i < shields && s < spine.size(); i++) {
            long[] p = spine.get(s++);
            this.set((int) p[0], (int) p[1], (int) p[2], Role.SHIELD, Face.NORTH);
        }
        for (int i = 0; i < tanks; i++) {
            long[] p = in < inside.size() ? inside.get(in++) : s < spine.size() ? spine.get(s++) : null;
            if (p != null) {
                this.set((int) p[0], (int) p[1], (int) p[2], Role.FUEL_TANK, Face.NORTH);
            }
        }
        this.cannons(cannons);
    }

    /** 光子炮朝前：战斗机挂在机翼前缘，别的船挂在船头两侧。 */
    private void cannons(int count) {
        List<long[]> mounts = new ArrayList<>();
        for (Cell c : this.cells.values()) {
            if (c.y() != 0 || c.role() == Role.LAMP || c.x() == 0) {
                continue;
            }
            // 前面一格空着、贴着船身或机翼的格子
            if (!this.cells.containsKey(key(c.x(), 0, c.z() - 1))) {
                mounts.add(new long[] {c.x(), 0, c.z() - 1});
            }
        }
        // 从外往里挂，左右成对
        mounts.sort((a, b) -> a[0] == b[0] ? Long.compare(a[2], b[2]) : Long.compare(Math.abs(b[0]), Math.abs(a[0])));
        if (count == 1) {
            this.set(0, -1, 1, Role.CANNON, Face.NORTH);
            return;
        }
        int placed = 0;
        for (long[] m : mounts) {
            if (placed >= count) {
                break;
            }
            if (m[0] > 0) {
                continue;
            }
            long[] mirror = null;
            for (long[] o : mounts) {
                if (o[0] == -m[0] && o[2] == m[2]) {
                    mirror = o;
                }
            }
            if (mirror == null || this.cells.containsKey(key((int) m[0], 0, (int) m[2]))) {
                continue;
            }
            this.set((int) m[0], 0, (int) m[2], Role.CANNON, Face.NORTH);
            this.set((int) mirror[0], 0, (int) mirror[2], Role.CANNON, Face.NORTH);
            placed += 2;
        }
        if (placed == 0 && count > 0) {
            this.set(0, -1, 1, Role.CANNON, Face.NORTH);
        }
    }

    /**
     * 先用普通推进器补够推力，再一件件换用料凑档次。换了更重的 Mk.II 部件要再补推进器：A、S 档补 Mk.II 的，
     * 免得把品质分拉下来；B 档补普通的，免得一下子冲到 A。
     */
    private void upgrade() {
        double floor = ShipRating.GRADE_FLOOR[this.grade.ordinal()];
        Role extra = this.grade.ordinal() >= Grade.A.ordinal() ? Role.THRUSTER_MK2 : Role.THRUSTER;
        this.thrust(Role.THRUSTER);
        for (int i = 0; i < STEPS && this.quality() < floor; i++) {
            if (!this.advanceOne() && !this.alloyOne()) {
                break;
            }
            this.thrust(extra);
        }
    }

    private double quality() {
        return ShipRating.quality(this.counts());
    }

    private Map<ShipPart, Integer> counts() {
        Map<ShipPart, Integer> counts = new java.util.EnumMap<>(ShipPart.class);
        for (Cell cell : this.cells.values()) {
            counts.merge(cell.role().part(), 1, Integer::sum);
        }
        return counts;
    }

    /** 随机挑一个还没换的部件换成 Mk.II；换了会变类型的（比如穿梭机的跃迁引擎换成 Mk.II 就成了探索船）不换。 */
    private boolean advanceOne() {
        List<Cell> options = new ArrayList<>();
        for (Cell c : this.cells.values()) {
            if (c.role().advanced() != c.role()) {
                options.add(c);
            }
        }
        while (!options.isEmpty()) {
            Cell c = options.remove(this.rng.nextInt(options.size()));
            this.set(c.x(), c.y(), c.z(), c.role().advanced(), c.face());
            if (this.ratedType() == this.type) {
                return true;
            }
            this.set(c.x(), c.y(), c.z(), c.role(), c.face());
        }
        return false;
    }

    private ShipType ratedType() {
        int cargo = 0;
        for (Cell c : this.cells.values()) {
            if (c.role() == Role.CARGO) {
                cargo += ShipDesign.CARGO_SLOTS;
            }
        }
        return ShipRating.of(this.counts(), cargo).type();
    }

    /** 把一块露在顶上的船体板换成合金。 */
    private boolean alloyOne() {
        for (Cell c : this.cells.values()) {
            if ((c.role() == Role.HULL || c.role() == Role.TRIM) && !this.cells.containsKey(key(c.x(), c.y() + 1, c.z()))) {
                this.set(c.x(), c.y(), c.z(), Role.ALLOY, c.face());
                return true;
            }
        }
        for (Cell c : this.cells.values()) {
            if (c.role() == Role.HULL || c.role() == Role.TRIM) {
                this.set(c.x(), c.y(), c.z(), Role.ALLOY, c.face());
                return true;
            }
        }
        return false;
    }

    /** 推重比不够就往肚子下面加推进器。 */
    private void thrust(Role thruster) {
        while (ShipStats.of(this.counts()).thrustToWeight(1) < MIN_TWR && this.nextBelly < this.belly.size()) {
            long[] p = this.belly.get(this.nextBelly++);
            if (!this.cells.containsKey(key((int) p[0], (int) p[1], (int) p[2]))) {
                this.set((int) p[0], (int) p[1], (int) p[2], thruster, Face.DOWN);
            }
        }
    }

    private void set(int x, int y, int z, Role role, Face face) {
        this.cells.put(key(x, y, z), new Cell(x, y, z, role, face));
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512);
    }
}
