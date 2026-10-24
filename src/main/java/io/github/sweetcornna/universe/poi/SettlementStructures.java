package io.github.sweetcornna.universe.poi;

import io.github.sweetcornna.universe.block.landmark.KnowledgeStoneBlock;
import io.github.sweetcornna.universe.core.civ.Race;
import io.github.sweetcornna.universe.core.poi.Landmark;
import io.github.sweetcornna.universe.core.poi.SettlementLayout;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * 外星文明的地标（ADR-0027 §五）：知识石和聚落。坐标系和别的地标一样（{@link PoiStructures}）：原点是中心的地面方块，
 * "前方"是本地的 -z。小屋、货摊、知识石、NPC 站的位置都从 {@link SettlementLayout} 取，和 NPC 的管理器对得上。
 *
 * <p>聚落按这个恒星系主导种族的样子盖：商旅族是暖色的帐篷和木头货摊，学者族是白墙、玻璃、横平竖直，
 * 戍卫族是黑石头和厚墙。整片地压平到中心的高度，边上慢慢接回原来的地面。
 */
public final class SettlementStructures {
    /** 压平的范围：这个半径以内压到中心的高度，往外到 {@link #EDGE} 慢慢接回原来的地面。 */
    private static final double FLAT = 9;
    private static final double EDGE = 12.5;
    private static final int SPAN = 12;
    private static final DyeColor[] TENTS = {DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.RED, DyeColor.BROWN};

    private SettlementStructures() {
    }

    /** 这个世界所在恒星系的主导种族；不是星球时算母星系的。世界生成的线程里也能调。 */
    static Race race(WorldGenLevel level) {
        ServerLevel server = level.getLevel();
        return Planets.planet(server).flatMap(planet -> Planets.galaxy(server.getServer()).system(planet.id().system()))
                .map(Race::dominant).orElse(Race.TRADER);
    }

    /** 一个种族的建材。 */
    private record Style(BlockState plaza, BlockState plazaRing, BlockState plazaMark, BlockState path, BlockState plinth, BlockState base,
            BlockState post, BlockState wall, BlockState roof, BlockState counter, BlockState lamp, BlockState lampPost) {
        static Style of(Race race) {
            return switch (race) {
                case TRADER -> new Style(Blocks.SMOOTH_SANDSTONE.defaultBlockState(), Blocks.DYED_TERRACOTTA.orange().defaultBlockState(),
                        Blocks.CHISELED_SANDSTONE.defaultBlockState(), Blocks.DIRT_PATH.defaultBlockState(), Blocks.CUT_SANDSTONE.defaultBlockState(),
                        Blocks.CHISELED_SANDSTONE.defaultBlockState(), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(),
                        Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.WOOL.orange().defaultBlockState(),
                        Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), Blocks.LANTERN.defaultBlockState(),
                        Blocks.SPRUCE_FENCE.defaultBlockState());
                case SCHOLAR -> new Style(Blocks.SMOOTH_QUARTZ.defaultBlockState(), Blocks.CONCRETE.white().defaultBlockState(),
                        Blocks.CONCRETE.lightBlue().defaultBlockState(), Blocks.SMOOTH_QUARTZ.defaultBlockState(), Blocks.SMOOTH_QUARTZ.defaultBlockState(),
                        Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState(), Blocks.QUARTZ_PILLAR.defaultBlockState(),
                        Blocks.CONCRETE.white().defaultBlockState(), Blocks.STAINED_GLASS.lightBlue().defaultBlockState(),
                        Blocks.SMOOTH_QUARTZ.defaultBlockState(), Blocks.SEA_LANTERN.defaultBlockState(), Blocks.QUARTZ_PILLAR.defaultBlockState());
                case WARDEN -> new Style(Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.DEEPSLATE_TILES.defaultBlockState(),
                        Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), Blocks.POLISHED_BLACKSTONE.defaultBlockState(),
                        Blocks.POLISHED_BLACKSTONE.defaultBlockState(), Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(),
                        Blocks.POLISHED_BLACKSTONE.defaultBlockState(), Blocks.DEEPSLATE_BRICKS.defaultBlockState(),
                        Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.POLISHED_BLACKSTONE.defaultBlockState(),
                        Blocks.SOUL_LANTERN.defaultBlockState(), Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState());
            };
        }
    }

    // ---- 知识石

    /** 野外的知识石：一块 3×3 的石台，正中一块底座，上面立着两格高的石柱。石台的石料按这个恒星系的主导种族。 */
    static void knowledgeStone(PoiStructures.Placer p, Race race) {
        Style style = Style.of(race);
        int b = p.ground(0, 0);
        p.clearVegetation(3, 10);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level(p, dx, dz, b, 6);
                p.set(dx, b, dz, style.plinth());
            }
        }
        p.set(0, b + 1, 0, style.base());
        stone(p, 0, b + 2, 0);
    }

    /** 两格高的知识石，下半在 {@code y}。 */
    private static void stone(PoiStructures.Placer p, int dx, int y, int dz) {
        BlockState lower = UniverseBlocks.KNOWLEDGE_STONE.get().defaultBlockState();
        p.set(dx, y, dz, lower);
        p.set(dx, y + 1, dz, lower.setValue(KnowledgeStoneBlock.HALF, DoubleBlockHalf.UPPER));
    }

    // ---- 聚落

    /**
     * 聚落：中间一片铺过的广场，靠里立着知识石；广场后面一个货摊，靠后墙一台交易终端；左右三四间小屋，门朝广场；
     * 前面一条路进来，路口和广场后面各两盏灯。NPC 站的地方空着。
     */
    static void settlement(PoiStructures.Placer p, Landmark landmark, Race race) {
        Style style = Style.of(race);
        int b = p.ground(0, 0);
        p.clearVegetation(13, 14);
        int[][] height = flatten(p, b);
        // 货摊那一片有一部分在接回原地面的那一圈里，也压到中心的高度
        int[] area = SettlementLayout.stall();
        for (int dx = area[0] - 1; dx <= area[2] + 1; dx++) {
            for (int dz = area[1] - 1; dz <= area[3] + 1; dz++) {
                level(p, dx, dz, b, 8);
            }
        }
        // 广场
        for (int dx = -SettlementLayout.PLAZA; dx <= SettlementLayout.PLAZA; dx++) {
            for (int dz = -SettlementLayout.PLAZA; dz <= SettlementLayout.PLAZA; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > SettlementLayout.PLAZA * SettlementLayout.PLAZA + 5) {
                    continue;
                }
                p.set(dx, b, dz, plazaBlock(style, race, dx, dz, d2));
            }
        }
        // 路：从前面进来，跟着压过的地面
        for (int dz = -SPAN; dz < -SettlementLayout.PLAZA; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx * dx + dz * dz <= EDGE * EDGE) {
                    p.set(dx, height[dx + SPAN][dz + SPAN], dz, style.path());
                }
            }
        }
        // 知识石
        int[] stone = SettlementLayout.stone();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                p.set(stone[0] + dx, b, stone[1] + dz, style.plinth());
            }
        }
        p.set(stone[0], b + 1, stone[1], style.base());
        stone(p, stone[0], b + 2, stone[1]);
        stall(p, b, style, race);
        for (int[] hut : SettlementLayout.huts(landmark)) {
            Frame frame = new Frame(hut[0], hut[1], hut[0] < 0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90);
            switch (race) {
                case TRADER -> tent(p, frame, b, TENTS[p.random.nextInt(TENTS.length)]);
                case SCHOLAR -> studyHut(p, frame, b);
                case WARDEN -> fort(p, frame, b);
            }
        }
        // 灯：路口两盏，广场后面两盏
        for (int[] lamp : SettlementLayout.lamps()) {
            int y = height[lamp[0] + SPAN][lamp[1] + SPAN];
            p.set(lamp[0], y + 1, lamp[1], style.lampPost());
            p.set(lamp[0], y + 2, lamp[1], style.lampPost());
            p.set(lamp[0], y + 3, lamp[1], style.lamp());
        }
        if (race == Race.TRADER) {
            // 货摊两边堆着货
            p.set(-3, b + 1, 9, style.counter());
            p.set(-3, b + 1, 10, Blocks.HAY_BLOCK.defaultBlockState());
            p.set(3, b + 1, 10, style.counter());
        }
    }

    private static BlockState plazaBlock(Style style, Race race, int dx, int dz, int d2) {
        return switch (race) {
            // 一圈一圈的砂岩，最外圈陶瓦，正中雕纹砂岩
            case TRADER -> d2 <= 2 ? style.plazaMark() : d2 > 20 ? style.plazaRing() : style.plaza();
            // 方格：横竖两条中线描蓝，其余白
            case SCHOLAR -> dx == 0 || dz == 0 ? style.plazaMark() : (Math.max(Math.abs(dx), Math.abs(dz)) & 1) == 0 ? style.plaza() : style.plazaRing();
            // 黑石砖，中间一圈深板岩瓦
            case WARDEN -> d2 <= 1 ? style.plazaMark() : d2 >= 9 && d2 <= 16 ? style.plazaRing() : style.plaza();
        };
    }

    /**
     * 整片地压平：{@link #FLAT} 以内压到中心的高度 {@code b}，往外到 {@link #EDGE} 按距离慢慢接回原来的地面。
     * 返回每一列压过以后的地面高度（下标是本地坐标加 {@link #SPAN}）。
     */
    private static int[][] flatten(PoiStructures.Placer p, int b) {
        int[][] height = new int[2 * SPAN + 1][2 * SPAN + 1];
        for (int dx = -SPAN; dx <= SPAN; dx++) {
            for (int dz = -SPAN; dz <= SPAN; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                int ground = p.ground(dx, dz);
                int target = b;
                if (d > EDGE) {
                    target = ground;
                } else if (d > FLAT) {
                    target = (int) Math.round(b + (ground - b) * (d - FLAT) / (EDGE - FLAT));
                }
                if (d <= EDGE) {
                    level(p, dx, dz, target, 10);
                }
                height[dx + SPAN][dz + SPAN] = target;
            }
        }
        return height;
    }

    /**
     * 把一列的地面挪到 {@code y}：低了用这一列原来地面下面的土垫高，高了削掉；顶上留原来的地面方块（水面换成星球的风化层），
     * 上面清空 {@code clear} 格。
     */
    private static void level(PoiStructures.Placer p, int dx, int dz, int y, int clear) {
        int ground = p.ground(dx, dz);
        BlockState surface = p.get(dx, ground, dz);
        if (surface.isAir() || !surface.getFluidState().isEmpty()) {
            surface = UniverseBlocks.PLANET_REGOLITH.get().defaultBlockState();
        }
        BlockState fill = p.get(dx, ground - 1, dz);
        if (fill.isAir() || !fill.getFluidState().isEmpty()) {
            fill = surface;
        }
        for (int h = ground; h < y; h++) {
            p.set(dx, h, dz, fill);
        }
        p.set(dx, y, dz, surface);
        p.clear(dx, dz, y + 1, Math.max(ground, y) + clear);
    }

    /**
     * 货摊：广场后面 5×4 的一个棚子，四角立柱，后墙和两边半人高的矮墙，顶上一层棚顶；靠后墙正中一台交易终端，
     * 终端两边和前面两角是柜台，正前方空着站人。
     */
    private static void stall(PoiStructures.Placer p, int b, Style style, Race race) {
        int[] terminal = SettlementLayout.terminal();
        int[] area = SettlementLayout.stall();
        int tz = terminal[1];
        int front = area[1];
        int back = area[3];
        for (int dx = area[0]; dx <= area[2]; dx++) {
            for (int dz = front; dz <= back; dz++) {
                p.set(dx, b, dz, race == Race.TRADER ? style.wall() : style.plaza());
                boolean side = dx == area[0] || dx == area[2];
                if (side && (dz == front || dz == back)) {
                    for (int y = 1; y <= 3; y++) {
                        p.set(dx, b + y, dz, style.post());
                    }
                } else if (dz == back) {
                    for (int y = 1; y <= 3; y++) {
                        p.set(dx, b + y, dz, style.wall());
                    }
                } else if (side) {
                    p.set(dx, b + 1, dz, style.wall());
                }
                // 商旅族的棚顶是橘白相间的布
                BlockState roof = race == Race.TRADER ? Blocks.WOOL.pick((dx & 1) == 0 ? DyeColor.ORANGE : DyeColor.WHITE).defaultBlockState()
                        : style.roof();
                p.set(dx, b + 4, dz, roof);
            }
        }
        // 棚子前面那一排也铺上
        for (int dx = area[0]; dx <= area[2]; dx++) {
            p.set(dx, b, front - 1, style.plaza());
        }
        p.set(terminal[0], b + 1, tz, UniverseBlocks.OUTPOST_TRADE_TERMINAL.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        p.set(terminal[0] - 1, b + 1, tz, style.counter());
        // 交易终端右边是聚落终端（ADR-0034 §二）：没有总督时在这里接管，有了总督打开概况
        p.set(terminal[0] + 1, b + 1, tz, UniverseBlocks.SETTLEMENT_TERMINAL.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        p.set(terminal[0] - 1, b + 1, front, style.counter());
        p.set(terminal[0] + 1, b + 1, front, style.counter());
        p.set(terminal[0], b + 3, tz - 1, hanging(style.lamp()));
    }

    private static BlockState hanging(BlockState lamp) {
        return lamp.hasProperty(LanternBlock.HANGING) ? lamp.setValue(LanternBlock.HANGING, true) : lamp;
    }

    // ---- 小屋：每间有自己的坐标系，门在本屋的 -z，转过去朝着广场

    /** 一间小屋的位置和朝向（在聚落的本地坐标里）。 */
    private record Frame(int cx, int cz, Rotation rotation) {
        /** 屋里的坐标转成聚落的本地坐标（和 {@link PoiStructures.Placer} 的转法一样）。 */
        int[] at(int hx, int hz) {
            return switch (this.rotation) {
                case NONE -> new int[] {this.cx + hx, this.cz + hz};
                case CLOCKWISE_90 -> new int[] {this.cx - hz, this.cz + hx};
                case CLOCKWISE_180 -> new int[] {this.cx - hx, this.cz - hz};
                case COUNTERCLOCKWISE_90 -> new int[] {this.cx + hz, this.cz - hx};
            };
        }

        void set(PoiStructures.Placer p, int hx, int y, int hz, BlockState state) {
            int[] at = this.at(hx, hz);
            p.set(at[0], y, at[1], state.rotate(this.rotation));
        }

        /** 占地（再往外一格）压平到 {@code b}，地面铺 {@code floor}。 */
        void ground(PoiStructures.Placer p, int half, int b, BlockState floor) {
            for (int hx = -half - 1; hx <= half + 1; hx++) {
                for (int hz = -half - 1; hz <= half + 1; hz++) {
                    int[] at = this.at(hx, hz);
                    level(p, at[0], at[1], b, 8);
                    if (Math.abs(hx) <= half && Math.abs(hz) <= half) {
                        this.set(p, hx, b, hz, floor);
                    }
                }
            }
        }
    }

    /** 商旅族的帐篷：5×5，布顶用羊毛台阶搭成人字形，屋脊顺着进深；前面开门，后面封死。里面一个木桶、一盏灯。 */
    private static void tent(PoiStructures.Placer p, Frame f, int b, DyeColor color) {
        f.ground(p, 2, b, Blocks.SPRUCE_PLANKS.defaultBlockState());
        BlockState cloth = Blocks.WOOL.pick(color).defaultBlockState();
        BlockState stairs = Blocks.WOOL_STAIRS.pick(color).defaultBlockState();
        BlockState east = stairs.setValue(StairBlock.FACING, Direction.EAST);
        BlockState west = stairs.setValue(StairBlock.FACING, Direction.WEST);
        BlockState ridge = Blocks.WOOL.pick(color == DyeColor.BROWN ? DyeColor.ORANGE : DyeColor.WHITE).defaultBlockState();
        for (int hz = -2; hz <= 2; hz++) {
            f.set(p, -2, b + 1, hz, east);
            f.set(p, 2, b + 1, hz, west);
            f.set(p, -1, b + 2, hz, east);
            f.set(p, 1, b + 2, hz, west);
            f.set(p, 0, b + 3, hz, ridge);
        }
        // 后面封死，前面中间开门
        for (int hx = -1; hx <= 1; hx++) {
            f.set(p, hx, b + 1, 2, cloth);
        }
        f.set(p, 0, b + 2, 2, cloth);
        f.set(p, -1, b + 1, -2, cloth);
        f.set(p, 1, b + 1, -2, cloth);
        // 门口的帐篷杆
        f.set(p, 0, b + 4, -2, Blocks.SPRUCE_FENCE.defaultBlockState());
        f.set(p, -1, b + 1, 1, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        f.set(p, 1, b + 1, 1, Blocks.LANTERN.defaultBlockState());
        f.set(p, 0, b + 1, 0, Blocks.CARPET.pick(color == DyeColor.RED ? DyeColor.YELLOW : DyeColor.RED).defaultBlockState());
    }

    /** 学者族的小屋：5×5 的白房子，石英柱做四角，一圈玻璃窗，平顶中间一块天窗和一盏海晶灯；屋里两个书架。 */
    private static void studyHut(PoiStructures.Placer p, Frame f, int b) {
        f.ground(p, 2, b, Blocks.SMOOTH_QUARTZ.defaultBlockState());
        BlockState wall = Blocks.CONCRETE.white().defaultBlockState();
        BlockState glass = Blocks.GLASS.defaultBlockState();
        for (int hx = -2; hx <= 2; hx++) {
            for (int hz = -2; hz <= 2; hz++) {
                boolean edge = Math.abs(hx) == 2 || Math.abs(hz) == 2;
                boolean corner = Math.abs(hx) == 2 && Math.abs(hz) == 2;
                if (edge) {
                    for (int y = 1; y <= 3; y++) {
                        f.set(p, hx, b + y, hz, corner ? Blocks.QUARTZ_PILLAR.defaultBlockState() : y == 2 ? glass : wall);
                    }
                }
                boolean skylight = Math.abs(hx) <= 1 && Math.abs(hz) <= 1;
                f.set(p, hx, b + 4, hz, hx == 0 && hz == 0 ? Blocks.SEA_LANTERN.defaultBlockState()
                        : skylight ? Blocks.STAINED_GLASS.lightBlue().defaultBlockState() : Blocks.SMOOTH_QUARTZ.defaultBlockState());
            }
        }
        f.set(p, 0, b + 1, -2, Blocks.AIR.defaultBlockState());
        f.set(p, 0, b + 2, -2, Blocks.AIR.defaultBlockState());
        f.set(p, -1, b + 1, 1, Blocks.BOOKSHELF.defaultBlockState());
        f.set(p, 1, b + 1, 1, Blocks.BOOKSHELF.defaultBlockState());
        f.set(p, 0, b + 1, 1, Blocks.CARPET.lightBlue().defaultBlockState());
    }

    /**
     * 戍卫族的小屋：7×7 的深板岩砖堡，墙两格厚，里面只有 3×3；前面一条两格深的门洞，两边墙上开射击孔，顶上一圈垛口，
     * 屋里吊一盏灵魂灯。
     */
    private static void fort(PoiStructures.Placer p, Frame f, int b) {
        f.ground(p, SettlementLayout.HUT_HALF, b, Blocks.DEEPSLATE_TILES.defaultBlockState());
        BlockState outer = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        BlockState inner = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState pillar = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        for (int hx = -3; hx <= 3; hx++) {
            for (int hz = -3; hz <= 3; hz++) {
                int ring = Math.max(Math.abs(hx), Math.abs(hz));
                boolean corner = Math.abs(hx) == 3 && Math.abs(hz) == 3;
                if (ring >= 2) {
                    for (int y = 1; y <= 3; y++) {
                        f.set(p, hx, b + y, hz, corner ? pillar : ring == 3 ? outer : inner);
                    }
                }
                f.set(p, hx, b + 4, hz, Blocks.DEEPSLATE_TILES.defaultBlockState());
                if (ring == 3 && ((hx + hz) & 1) == 0) {
                    f.set(p, hx, b + 5, hz, corner ? pillar : outer);
                }
            }
        }
        // 门洞穿过两层墙
        for (int hz = -3; hz <= -2; hz++) {
            f.set(p, 0, b + 1, hz, Blocks.AIR.defaultBlockState());
            f.set(p, 0, b + 2, hz, Blocks.AIR.defaultBlockState());
        }
        // 左右和后面的射击孔
        f.set(p, -3, b + 2, 0, Blocks.IRON_BARS.defaultBlockState());
        f.set(p, -2, b + 2, 0, Blocks.AIR.defaultBlockState());
        f.set(p, 3, b + 2, 0, Blocks.IRON_BARS.defaultBlockState());
        f.set(p, 2, b + 2, 0, Blocks.AIR.defaultBlockState());
        f.set(p, 0, b + 2, 3, Blocks.IRON_BARS.defaultBlockState());
        f.set(p, 0, b + 2, 2, Blocks.AIR.defaultBlockState());
        f.set(p, 0, b + 3, 0, hanging(Blocks.SOUL_LANTERN.defaultBlockState()));
        f.set(p, -1, b + 1, 1, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
    }
}
