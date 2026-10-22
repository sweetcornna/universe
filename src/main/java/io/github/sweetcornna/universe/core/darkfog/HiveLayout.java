package io.github.sweetcornna.universe.core.darkfog;

import java.util.List;

/**
 * 太空巢穴的布局和建造顺序（ADR-0028 §八）：核心在中间，第一座光子接收器在正上方，别的建筑在两圈上，一圈在核心的赤道面上，
 * 一圈上下错开。每个槽位有建造要的等级和物质；缺了的按顺序补，核心不补（打掉了整座巢穴瓦解）。
 *
 * <p>槽位的顺序是存档格式：以后只能往后加。
 */
public final class HiveLayout {
    /** 巢穴占的半径：建筑都在这个球里。 */
    public static final int RADIUS = 64;

    /**
     * 一个槽位。
     *
     * @param minLevel 巢穴到这一级才盖
     * @param cost     盖它要的物质
     * @param dx       相对核心中心的位置
     */
    public record Slot(HiveBuildingKind kind, int minLevel, double cost, int dx, int dy, int dz) {
    }

    public static final int CORE = 0;
    /** 第一座光子接收器：巢穴刚建起来就有。 */
    public static final int FIRST_RECEIVER = 1;

    public static final List<Slot> SLOTS = List.of(
            new Slot(HiveBuildingKind.CORE, 0, 0, 0, 0, 0),
            new Slot(HiveBuildingKind.PHOTON_RECEIVER, 0, 0, 0, 40, 0),
            ring(HiveBuildingKind.LANCER_PORT, 0, 60, 36, 0, 0),
            ring(HiveBuildingKind.PHASE_LASER, 0, 80, 46, 90, 8),
            new Slot(HiveBuildingKind.PHOTON_RECEIVER, 1, 90, 0, -40, 0),
            ring(HiveBuildingKind.PLASMA_TOWER, 2, 120, 46, 270, -8),
            ring(HiveBuildingKind.HUMPBACK_PORT, 3, 150, 36, 180, 0),
            ring(HiveBuildingKind.LANCER_PORT, 4, 120, 36, 60, 18),
            ring(HiveBuildingKind.PHASE_LASER, 5, 120, 54, 330, -14),
            ring(HiveBuildingKind.PHOTON_RECEIVER, 6, 140, 28, 135, 34),
            ring(HiveBuildingKind.PLASMA_TOWER, 8, 160, 54, 150, 14),
            ring(HiveBuildingKind.HUMPBACK_PORT, 10, 200, 36, 240, -18),
            ring(HiveBuildingKind.PHASE_LASER, 12, 160, 54, 210, 20),
            ring(HiveBuildingKind.PLASMA_TOWER, 14, 200, 54, 30, -20),
            ring(HiveBuildingKind.LANCER_PORT, 16, 180, 36, 300, -18),
            ring(HiveBuildingKind.PHOTON_RECEIVER, 18, 200, 28, 315, -34),
            ring(HiveBuildingKind.PLASMA_TOWER, 20, 240, 58, 90, -24),
            ring(HiveBuildingKind.PHASE_LASER, 22, 220, 58, 270, 24),
            ring(HiveBuildingKind.HUMPBACK_PORT, 25, 280, 36, 120, -18));

    private HiveLayout() {
    }

    private static Slot ring(HiveBuildingKind kind, int minLevel, double cost, int radius, int degrees, int dy) {
        double angle = Math.toRadians(degrees);
        return new Slot(kind, minLevel, cost, (int) Math.round(Math.cos(angle) * radius), dy, (int) Math.round(Math.sin(angle) * radius));
    }

    public static Slot slot(int index) {
        return SLOTS.get(index);
    }

    public static int size() {
        return SLOTS.size();
    }

    /** 盖了以后会不会再补：核心不补。 */
    public static boolean rebuildable(int index) {
        return index != CORE;
    }

    /** 每个港口最多停多少个单位：枪骑 12 个，座头鲸 4 个。 */
    public static int portCapacity(SpaceUnitKind kind) {
        return kind == SpaceUnitKind.HUMPBACK ? 4 : 12;
    }
}
