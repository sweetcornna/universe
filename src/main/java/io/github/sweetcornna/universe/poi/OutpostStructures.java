package io.github.sweetcornna.universe.poi;

import java.util.List;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.landmark.ReinforcedDoorBlock;
import io.github.sweetcornna.universe.core.poi.Landmark;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * 有人用过的地标的建筑（ADR-0026）：交易站、制造设施、观测站。坐标系和兴趣点一样（{@link PoiStructures}）：
 * 原点是中心的地面方块，"前方"是本地的 -z，所有方块离中心不超过 15 格。地面先压平到中心的高度，矮的地方往下垫地基。
 *
 * <p>着陆台四周的台面用黑石和闪长岩：都在 {@code ship_excluded} 里，停在台上的船组装时不会把台面带走。
 */
public final class OutpostStructures {
    public static final ResourceKey<LootTable> TRADING_POST_LOOT = ResourceKey.create(Registries.LOOT_TABLE, Universe.id("chests/trading_post"));
    private static final DyeColor[] ACCENTS = {DyeColor.ORANGE, DyeColor.CYAN, DyeColor.YELLOW, DyeColor.LIME, DyeColor.LIGHT_BLUE};

    private OutpostStructures() {
    }

    // ---- 交易站

    /**
     * 交易站：中间一间 7×7 的白色小屋，屋里靠后墙一台交易终端、两个补给箱；屋前面三块着陆台排成一个品字，
     * 台和屋之间铺着路。屋顶有天线和信号灯，屋边一个燃料罐。
     */
    static void tradingPost(PoiStructures.Placer p, Landmark landmark) {
        RandomSource random = p.random;
        int b = p.ground(0, 0);
        BlockState main = plating(DyeColor.WHITE);
        BlockState accent = plating(ACCENTS[random.nextInt(ACCENTS.length)]);
        BlockState floor = plating(DyeColor.GRAY);
        BlockState apron = Blocks.BLACKSTONE.defaultBlockState();
        BlockState marking = Blocks.DIORITE.defaultBlockState();
        p.clearVegetation(14, 14);
        // 屋子
        flatten(p, -4, -4, 4, 4, b, 8, floor, floor);
        hut(p, b, main, accent, floor);
        p.set(0, b + 1, 2, UniverseBlocks.OUTPOST_TRADE_TERMINAL.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        p.crate(2, b + 1, 2, Direction.WEST, TRADING_POST_LOOT);
        p.crate(-2, b + 1, 2, Direction.EAST, TRADING_POST_LOOT);
        p.set(-2, b + 1, -1, UniverseBlocks.SHIP_SEAT.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        // 着陆台：左前、右前、正前
        pad(p, -9, -7, b, apron, marking);
        pad(p, 9, -7, b, apron, marking);
        pad(p, 0, -11, b, apron, marking);
        // 门口到三块台子的路
        for (int dz = -8; dz <= -4; dz++) {
            flatten(p, 0, dz, 0, dz, b, 4, apron, apron);
        }
        for (int dx = -6; dx <= 6; dx++) {
            if (Math.abs(dx) > 1) {
                flatten(p, dx, -7, dx, -7, b, 4, apron, apron);
            }
        }
        // 屋顶的天线和信号灯，屋边的燃料罐
        p.set(2, b + 5, 2, Blocks.IRON_BARS.defaultBlockState());
        p.set(2, b + 6, 2, Blocks.IRON_BARS.defaultBlockState());
        p.set(2, b + 7, 2, Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, Direction.UP));
        p.set(-2, b + 5, -2, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
        flatten(p, 5, 1, 5, 2, b, 4, floor, floor);
        p.set(5, b + 1, 1, UniverseBlocks.FUEL_TANK.get().defaultBlockState());
        p.set(5, b + 1, 2, UniverseBlocks.FUEL_TANK.get().defaultBlockState());
    }

    /** 7×7 的小屋：墙三格高，前墙中间开门，两侧开窗，屋顶中间一盏灯。 */
    private static void hut(PoiStructures.Placer p, int b, BlockState main, BlockState accent, BlockState floor) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                boolean wall = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                boolean corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
                if (wall) {
                    for (int y = 1; y <= 3; y++) {
                        p.set(dx, b + y, dz, corner ? accent : main);
                    }
                }
                p.set(dx, b + 4, dz, dx == 0 || dz == 0 ? accent : main);
            }
        }
        p.set(0, b + 1, -3, Blocks.AIR.defaultBlockState());
        p.set(0, b + 2, -3, Blocks.AIR.defaultBlockState());
        BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
        p.set(-3, b + 2, 0, pane);
        p.set(3, b + 2, 0, pane);
        p.set(-2, b + 2, -3, pane);
        p.set(2, b + 2, -3, pane);
        p.set(0, b + 4, 0, Blocks.SEA_LANTERN.defaultBlockState());
    }

    /** 一块着陆台：5×5 的黑石台面，四角和中线描闪长岩，正中间是着陆台。 */
    private static void pad(PoiStructures.Placer p, int cx, int cz, int b, BlockState apron, BlockState marking) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean mark = Math.abs(dx) == 2 && Math.abs(dz) == 2 || dx == 0 && Math.abs(dz) == 2 || dz == 0 && Math.abs(dx) == 2;
                flatten(p, cx + dx, cz + dz, cx + dx, cz + dz, b, 10, mark ? marking : apron, apron);
            }
        }
        p.set(cx, b, cz, UniverseBlocks.LANDING_PAD.get().defaultBlockState());
    }

    // ---- 制造设施

    /**
     * 制造设施：11×9、6 格高的一座方楼，墙、顶、地板都是打不坏的加固板，前墙正中一扇 3 格宽、2 格高的加固门。
     * 楼里靠后墙一台终端对着门，两排机柜和天花板的灯；楼顶四角红灯，门前一片黑石空地给守卫巡逻。
     */
    static void manufacturingFacility(PoiStructures.Placer p, Landmark landmark) {
        int b = p.ground(0, 0);
        BlockState wall = UniverseBlocks.REINFORCED_PLATING.get().defaultBlockState();
        BlockState trim = plating(DyeColor.RED);
        BlockState cabinet = plating(DyeColor.GRAY);
        BlockState apron = Blocks.BLACKSTONE.defaultBlockState();
        p.clearVegetation(12, 14);
        flatten(p, -6, -9, 6, 5, b, 9, apron, apron);
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                boolean edge = Math.abs(dx) == 5 || Math.abs(dz) == 4;
                p.set(dx, b, dz, wall);
                if (edge) {
                    for (int y = 1; y <= 5; y++) {
                        p.set(dx, b + y, dz, wall);
                    }
                }
                p.set(dx, b + 6, dz, wall);
            }
        }
        // 门：前墙正中 3 格宽、2 格高
        for (int dx = -1; dx <= 1; dx++) {
            BlockState door = UniverseBlocks.REINFORCED_DOOR.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
            p.set(dx, b + 1, -4, door.setValue(ReinforcedDoorBlock.HALF, DoubleBlockHalf.LOWER));
            p.set(dx, b + 2, -4, door.setValue(ReinforcedDoorBlock.HALF, DoubleBlockHalf.UPPER));
        }
        // 门框：红色的边，上方一排警示灯
        for (int dx = -2; dx <= 2; dx++) {
            p.set(dx, b + 3, -5, dx % 2 == 0 ? trim : Blocks.SHROOMLIGHT.defaultBlockState());
        }
        p.set(-2, b + 1, -5, trim);
        p.set(-2, b + 2, -5, trim);
        p.set(2, b + 1, -5, trim);
        p.set(2, b + 2, -5, trim);
        // 楼里：终端、两排机柜、灯
        p.set(0, b + 1, 3, UniverseBlocks.FACILITY_TERMINAL.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        for (int dz = -2; dz <= 2; dz += 2) {
            for (int side = -1; side <= 1; side += 2) {
                p.set(side * 4, b + 1, dz, cabinet);
                p.set(side * 4, b + 2, dz, dz == 0 ? Blocks.SEA_LANTERN.defaultBlockState() : cabinet);
                p.set(side * 4, b + 3, dz, Blocks.IRON_BARS.defaultBlockState());
            }
        }
        p.set(-1, b + 1, 3, Blocks.IRON_BARS.defaultBlockState());
        p.set(1, b + 1, 3, Blocks.IRON_BARS.defaultBlockState());
        for (int dx = -2; dx <= 2; dx += 4) {
            for (int dz = -2; dz <= 2; dz += 4) {
                p.set(dx, b + 6, dz, Blocks.SEA_LANTERN.defaultBlockState());
            }
        }
        // 楼顶四角的红灯
        for (int dx = -5; dx <= 5; dx += 10) {
            for (int dz = -4; dz <= 4; dz += 8) {
                p.set(dx, b + 7, dz, trim);
                p.set(dx, b + 8, dz, Blocks.SHROOMLIGHT.defaultBlockState());
            }
        }
    }

    // ---- 观测站

    /**
     * 观测站：一座半径 3 的圆塔，四格高的白墙上扣着玻璃穹顶，穹顶下一架斜指着天的望远镜；塔前开门，终端在门对面。
     * 塔外一圈平台，旁边一面碟形天线。
     */
    static void observatory(PoiStructures.Placer p, Landmark landmark) {
        int b = p.ground(0, 0);
        BlockState main = plating(DyeColor.WHITE);
        BlockState floor = plating(DyeColor.LIGHT_GRAY);
        BlockState dark = plating(DyeColor.BLACK);
        BlockState glass = Blocks.STAINED_GLASS.lightGray().defaultBlockState();
        p.clearVegetation(8, 14);
        // 平台：半径 5.5 的圆
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                if (dx * dx + dz * dz <= 30) {
                    flatten(p, dx, dz, dx, dz, b, 10, floor, floor);
                }
            }
        }
        // 塔身：半径 3 的圆墙，四格高，上面穹顶
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > 3.5) {
                    continue;
                }
                if (d > 2.5) {
                    for (int y = 1; y <= 4; y++) {
                        p.set(dx, b + y, dz, y == 4 ? dark : main);
                    }
                }
                // 穹顶：半径 3.5 的半球壳
                for (int y = 0; y <= 3; y++) {
                    double r = Math.sqrt(dx * dx + dz * dz + y * y);
                    if (r > 2.6 && r <= 3.6) {
                        p.set(dx, b + 5 + y, dz, glass);
                    }
                }
            }
        }
        // 门：前方两格高的开口
        p.set(0, b + 1, -3, Blocks.AIR.defaultBlockState());
        p.set(0, b + 2, -3, Blocks.AIR.defaultBlockState());
        // 望远镜：底座在中间，镜筒斜着伸向后上方，前端一块镜片
        p.set(0, b + 1, 0, dark);
        p.set(0, b + 2, 0, Blocks.IRON_BARS.defaultBlockState());
        p.set(0, b + 3, 1, dark);
        p.set(0, b + 4, 1, dark);
        p.set(0, b + 5, 2, dark);
        p.set(0, b + 6, 2, Blocks.STAINED_GLASS.lightBlue().defaultBlockState());
        // 终端对着门，靠着后墙
        p.set(-1, b + 1, 2, UniverseBlocks.OBSERVATORY_TERMINAL.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        p.set(1, b + 1, 2, Blocks.SEA_LANTERN.defaultBlockState());
        // 碟形天线：一根杆上一面斜着的盘子
        int ax = 5;
        int az = 2;
        p.set(ax, b + 1, az, Blocks.IRON_BARS.defaultBlockState());
        p.set(ax, b + 2, az, Blocks.IRON_BARS.defaultBlockState());
        for (int dz = -1; dz <= 1; dz++) {
            p.set(ax, b + 3, az + dz, main);
            p.set(ax - 1, b + 4, az + dz, main);
        }
        p.set(ax, b + 4, az, Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, Direction.WEST));
        p.debris(7, 9, List.of(floor, Blocks.IRON_BARS.defaultBlockState()), 2);
    }

    // ---- 工具

    /**
     * 把一片地压平到 {@code b}：地面比 {@code b} 低的往下垫 {@code fill}，顶面换成 {@code top}，上面清空 {@code clear} 格
     * （高出来的山包也削掉）。
     */
    private static void flatten(PoiStructures.Placer p, int x0, int z0, int x1, int z1, int b, int clear, BlockState top, BlockState fill) {
        for (int dx = x0; dx <= x1; dx++) {
            for (int dz = z0; dz <= z1; dz++) {
                int ground = p.ground(dx, dz);
                for (int y = ground + 1; y < b; y++) {
                    p.set(dx, y, dz, fill);
                }
                p.set(dx, b, dz, top);
                p.clear(dx, dz, b + 1, Math.max(ground, b) + clear);
            }
        }
    }

    private static BlockState plating(DyeColor color) {
        return UniverseBlocks.HULL_PLATING.get(color).get().defaultBlockState();
    }
}
