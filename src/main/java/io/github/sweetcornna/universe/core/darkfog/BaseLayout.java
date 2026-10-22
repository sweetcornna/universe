package io.github.sweetcornna.universe.core.darkfog;

import java.util.List;

/**
 * 地面基地的布局和建造顺序（ADR-0028 §二）：钻芯在中间，中继站悬在上空，营地围一圈，炮塔在外圈。
 * 每个槽位有建造要的等级和物质；缺了的按顺序补，中继站第一块里不补（等第二块的巢穴派新的来）。
 *
 * <p>槽位的顺序是存档格式：以后只能往后加。
 */
public final class BaseLayout {
    /** 基地占的半径（压平、铺底板的范围）。 */
    public static final int RADIUS = 22;
    /** 中继站悬在钻芯上方多高。 */
    public static final int RELAY_HEIGHT = 40;

    /**
     * 一个槽位。
     *
     * @param minLevel 基地到这一级才盖
     * @param cost     盖它要的物质
     * @param dx       相对钻芯的位置
     */
    public record Slot(BuildingKind kind, int minLevel, double cost, int dx, int dz) {
    }

    public static final int CORE = 0;
    public static final int RELAY = 1;

    public static final List<Slot> SLOTS = List.of(
            new Slot(BuildingKind.CORE, 0, 0, 0, 0),
            new Slot(BuildingKind.RELAY, 0, 0, 0, 0),
            ring(BuildingKind.RAIDER_CAMP, 0, 30, 10, 0),
            ring(BuildingKind.LASER_TOWER, 0, 40, 17, 30),
            ring(BuildingKind.RANGER_CAMP, 2, 60, 10, 120),
            ring(BuildingKind.PLASMA_SENTRY, 3, 80, 17, 150),
            ring(BuildingKind.RAIDER_CAMP, 5, 70, 10, 240),
            ring(BuildingKind.LASER_TOWER, 5, 60, 17, 270),
            ring(BuildingKind.GUARDIAN_CAMP, 6, 100, 11, 60),
            ring(BuildingKind.RANGER_CAMP, 8, 90, 11, 180),
            ring(BuildingKind.PLASMA_SENTRY, 10, 110, 17, 90),
            ring(BuildingKind.LASER_TOWER, 12, 80, 17, 210),
            ring(BuildingKind.GUARDIAN_CAMP, 14, 130, 11, 300),
            ring(BuildingKind.RAIDER_CAMP, 16, 110, 5, 45),
            ring(BuildingKind.PLASMA_SENTRY, 18, 140, 17, 330),
            ring(BuildingKind.RANGER_CAMP, 20, 130, 5, 225),
            ring(BuildingKind.LASER_TOWER, 22, 100, 19, 0),
            ring(BuildingKind.PLASMA_SENTRY, 25, 160, 19, 180));

    private BaseLayout() {
    }

    private static Slot ring(BuildingKind kind, int minLevel, double cost, int radius, int degrees) {
        double angle = Math.toRadians(degrees);
        return new Slot(kind, minLevel, cost, (int) Math.round(Math.cos(angle) * radius), (int) Math.round(Math.sin(angle) * radius));
    }

    public static Slot slot(int index) {
        return SLOTS.get(index);
    }

    public static int size() {
        return SLOTS.size();
    }

    /** 盖了以后会不会再补：钻芯、中继站不补。 */
    public static boolean rebuildable(int index) {
        return index != CORE && index != RELAY;
    }

    /** 每个营地最多住多少个单位：突袭者、游骑兵 12 个，守卫者 8 个。 */
    public static int campCapacity(UnitKind kind) {
        return kind == UnitKind.GUARDIAN ? 8 : 12;
    }
}
