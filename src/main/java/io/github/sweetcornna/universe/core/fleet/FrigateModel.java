package io.github.sweetcornna.universe.core.fleet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.sweetcornna.universe.core.fleet.FreighterLayout.Cell;
import io.github.sweetcornna.universe.core.fleet.FreighterLayout.Role;

/**
 * 停在货船边上的护卫舰（ADR-0024）：不做实体，按种类画成一艘方块小船，船头和货船一样朝 -z。
 * 坐标原点在船身中线的船底；每艘都在 x ±{@link #HALF_WIDTH}、z ±{@link #HALF_LENGTH} 以内。
 *
 * <p>停的地方：货船右舷（+x）外面两列，每列 {@link #PER_ROW} 艘，前后错开 {@link #SPACING} 格。
 */
public final class FrigateModel {
    public static final int HALF_WIDTH = 5;
    public static final int HALF_LENGTH = 7;
    public static final int PER_ROW = 6;
    public static final int SPACING = 14;
    /** 第一列离船舷多远、两列隔多远。 */
    static final int ROW_GAP = 10;
    static final int ROW_SPACING = 13;

    private FrigateModel() {
    }

    /** 第几个停泊位相对货船原点的位置 {x, y, z}。 */
    public static int[] berth(FreighterLayout.Exterior exterior, int slot) {
        int row = slot / PER_ROW;
        int column = slot % PER_ROW;
        int x = exterior.halfWidth() + ROW_GAP + HALF_WIDTH + row * ROW_SPACING;
        int z = (column - (PER_ROW - 1) / 2) * SPACING - SPACING / 2;
        return new int[] {x, 2 + row * 3, z};
    }

    public static List<Cell> cells(FrigateType type) {
        Map<Long, Cell> cells = new LinkedHashMap<>();
        switch (type) {
            case COMBAT -> {
                box(cells, -1, 0, -4, 1, 1, 4, Role.HULL);
                box(cells, 0, 0, -6, 0, 0, -5, Role.HULL);
                box(cells, 0, 2, -3, 0, 2, -2, Role.GLASS);
                // 掠翼
                box(cells, -5, 0, 0, -2, 0, 3, Role.PLATE);
                box(cells, 2, 0, 0, 5, 0, 3, Role.PLATE);
                box(cells, -5, 0, 3, -5, 0, 3, Role.LIGHT);
                box(cells, 5, 0, 3, 5, 0, 3, Role.LIGHT);
                box(cells, -1, 0, 5, -1, 0, 5, Role.ENGINE);
                box(cells, 1, 0, 5, 1, 0, 5, Role.ENGINE);
                box(cells, 0, 1, 5, 0, 1, 5, Role.ENGINE);
            }
            case EXPLORATION -> {
                box(cells, -1, 0, -3, 1, 1, 5, Role.HULL);
                // 船头一根长长的探测杆
                box(cells, 0, 0, -7, 0, 0, -4, Role.PLATE);
                box(cells, 0, 0, -7, 0, 0, -7, Role.LIGHT);
                box(cells, -1, 2, -2, 1, 2, 0, Role.GLASS);
                // 背上的天线
                box(cells, 0, 2, 2, 0, 4, 2, Role.PLATE);
                box(cells, 0, 5, 2, 0, 5, 2, Role.LIGHT);
                box(cells, -3, 1, 3, -2, 1, 3, Role.PLATE);
                box(cells, 2, 1, 3, 3, 1, 3, Role.PLATE);
                box(cells, -3, 1, 3, -3, 1, 3, Role.LIGHT);
                box(cells, 3, 1, 3, 3, 1, 3, Role.LIGHT);
                box(cells, -1, 0, 6, 1, 0, 6, Role.ENGINE);
            }
            case INDUSTRIAL -> {
                box(cells, -3, 0, -4, 3, 3, 4, Role.HULL);
                box(cells, -3, 0, -2, 3, 3, -2, Role.PLATE);
                box(cells, -3, 0, 1, 3, 3, 1, Role.PLATE);
                box(cells, -1, 2, -5, 1, 3, -5, Role.GLASS);
                // 背上一排集装箱
                box(cells, -2, 4, -1, 2, 4, 3, Role.PLATE);
                box(cells, -3, 3, -4, -3, 3, -4, Role.LIGHT);
                box(cells, 3, 3, -4, 3, 3, -4, Role.LIGHT);
                box(cells, -2, 1, 5, -2, 2, 5, Role.ENGINE);
                box(cells, 2, 1, 5, 2, 2, 5, Role.ENGINE);
            }
            case TRADE -> {
                box(cells, -1, 0, -5, 1, 2, 5, Role.HULL);
                box(cells, -1, 0, -6, 1, 1, -6, Role.HULL);
                box(cells, 0, 2, -5, 0, 2, -4, Role.GLASS);
                // 两边挂着货舱
                box(cells, -4, 0, -2, -3, 1, 3, Role.PLATE);
                box(cells, 3, 0, -2, 4, 1, 3, Role.PLATE);
                box(cells, -2, 1, 0, -2, 1, 1, Role.PLATE);
                box(cells, 2, 1, 0, 2, 1, 1, Role.PLATE);
                box(cells, -4, 1, -2, -4, 1, -2, Role.LIGHT);
                box(cells, 4, 1, -2, 4, 1, -2, Role.LIGHT);
                box(cells, -1, 1, 6, 1, 1, 6, Role.ENGINE);
                box(cells, -4, 0, 4, -3, 0, 4, Role.ENGINE);
                box(cells, 3, 0, 4, 4, 0, 4, Role.ENGINE);
            }
            case SUPPORT -> {
                box(cells, -2, 0, -3, 2, 2, 3, Role.HULL);
                box(cells, -1, 0, -4, 1, 1, -4, Role.HULL);
                box(cells, -1, 2, -4, 1, 2, -4, Role.GLASS);
                box(cells, -1, 3, -2, 1, 3, 2, Role.PLATE);
                box(cells, 0, 4, 0, 0, 4, 0, Role.LIGHT);
                box(cells, -2, 1, -3, -2, 1, -3, Role.LIGHT);
                box(cells, 2, 1, -3, 2, 1, -3, Role.LIGHT);
                box(cells, -1, 1, 4, 1, 1, 4, Role.ENGINE);
            }
        }
        return List.copyOf(new ArrayList<>(cells.values()));
    }

    /** 填一个实心的长方体；后填的盖掉先填的。 */
    private static void box(Map<Long, Cell> cells, int x0, int y0, int z0, int x1, int y1, int z1, Role role) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    cells.put(((long) (x + 64) << 16) | ((long) (y + 64) << 8) | (z + 64), new Cell(x, y, z, role));
                }
            }
        }
    }
}
