package io.github.sweetcornna.universe.poi;

import java.util.List;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.fossil.DigSiteLayout;
import io.github.sweetcornna.universe.core.poi.FossilPit;
import io.github.sweetcornna.universe.core.poi.Landmark;
import io.github.sweetcornna.universe.core.poi.LandmarkType;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * 野外的地标的建筑（ADR-0026）：化石坑、打捞点、废弃建筑，还有古生物发掘场（ADR-0034 §三）。原点是中心的地面方块，
 * 所有方块离中心不超过 15 格。
 *
 * <p>地标的原点取的是"挡路的最高方块"，中心有树时会落在树冠上，所以这里都以 {@code p.ground(0, 0)}（跳过树和植物的地面）为基准。
 */
public final class WildStructures {
    /** 废弃建筑的补给箱：生存物资为主，常有导航数据。 */
    public static final ResourceKey<LootTable> ABANDONED_BUILDING_LOOT = ResourceKey.create(Registries.LOOT_TABLE,
            Universe.id("chests/abandoned_building"));
    /** 发掘场帐篷里的箱子、桌边的工具桶：干粮、氧气、挖出来还没登记的化石和骨头。 */
    public static final ResourceKey<LootTable> DIG_SITE_LOOT = ResourceKey.create(Registries.LOOT_TABLE, Universe.id("chests/dig_site"));

    private WildStructures() {
    }

    /**
     * 化石坑：一处没人管的发掘现场。7×7 的坑一层层往下挖（{@link FossilPit}），坑底露着化石土；四角钉着测量桩，
     * 一边是挖出来的土堆，另一边一个金属顶棚，棚下有补给箱、筛土的架子和一盏马灯。
     */
    static void fossilPit(PoiStructures.Placer p, Landmark landmark) {
        RandomSource random = p.random;
        p.clearVegetation(LandmarkType.FOSSIL_PIT.radius() + 2, 10);
        int base = p.ground(0, 0);
        BlockState top = surface(p, base);
        flatten(p, 8, base, top);
        for (int dx = -FossilPit.HALF; dx <= FossilPit.HALF; dx++) {
            for (int dz = -FossilPit.HALF; dz <= FossilPit.HALF; dz++) {
                int depth = FossilPit.depth(dx, dz);
                p.clear(dx, dz, base - depth + 1, base);
                // 坑底露出来的是下面的土，不是地表的草皮
                if (p.get(dx, base - depth, dz).isAir()) {
                    p.set(dx, base - depth, dz, soil());
                }
            }
        }
        BlockState fossil = UniverseBlocks.FOSSIL_DIRT.get().defaultBlockState();
        for (FossilPit.Cell cell : FossilPit.layout(landmark.seed())) {
            p.set(cell.dx(), base + cell.dy(), cell.dz(), fossil);
        }
        // 四角的测量桩
        BlockState stake = Blocks.IRON_CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                p.set(sx * (FossilPit.HALF + 1), base + 1, sz * (FossilPit.HALF + 1), stake);
            }
        }
        // 挖出来的土堆在坑的一边
        BlockState gravel = Blocks.GRAVEL.defaultBlockState();
        for (int dx = 5; dx <= 7; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int height = (dx == 6 && Math.abs(dz) <= 1 ? 2 : 1) - (Math.abs(dz) == 2 && random.nextBoolean() ? 1 : 0);
                for (int h = 1; h <= height; h++) {
                    p.set(dx, base + h, dz, random.nextInt(3) == 0 ? gravel : soil());
                }
            }
        }
        // 另一边的顶棚：四根柱子顶着一块橙色的金属板
        BlockState post = Blocks.IRON_BARS.defaultBlockState();
        BlockState roof = plating(DyeColor.ORANGE);
        for (int dx = -8; dx <= -5; dx++) {
            for (int dz = -2; dz <= 1; dz++) {
                boolean corner = (dx == -8 || dx == -5) && (dz == -2 || dz == 1);
                if (corner) {
                    p.set(dx, base + 1, dz, post);
                    p.set(dx, base + 2, dz, post);
                }
                p.set(dx, base + 3, dz, roof);
            }
        }
        p.set(-6, base + 2, 0, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        p.crate(-7, base + 1, -1, Direction.EAST, PoiStructures.OUTPOST_LOOT);
        p.set(-6, base + 1, 1, Blocks.SCAFFOLDING.defaultBlockState());
        p.set(-7, base + 1, 1, soil());
    }

    /**
     * 打捞点：一块烧焦的地，正中一摞 3 块保护壳，最下面埋着打捞舱；旁边一根遇险信标的天线杆亮着，四周散着碎片。
     */
    static void salvageSite(PoiStructures.Placer p, Landmark landmark) {
        RandomSource random = p.random;
        p.clearVegetation(LandmarkType.SALVAGE_SITE.radius() + 1, 10);
        int base = p.ground(0, 0);
        BlockState top = surface(p, base);
        flatten(p, 3, base, top);
        p.scorch(5.5, 0.55);
        BlockState shell = plating(DyeColor.GRAY);
        for (int i = 0; i < 3; i++) {
            p.set(0, base - i, 0, shell);
        }
        p.set(0, base - 3, 0, UniverseBlocks.SALVAGE_CAPSULE.get().defaultBlockState());
        // 舱下面垫实，不悬在洞上
        if (p.get(0, base - 4, 0).isAir()) {
            p.set(0, base - 4, 0, soil());
        }
        // 保护壳旁边一圈翻起来的土
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (random.nextInt(3) != 0) {
                p.set(side.getStepX(), base + 1, side.getStepZ(), soil());
            }
        }
        // 遇险信标：一根天线杆，顶上一盏灯
        int ground = p.ground(3, -2);
        if (Math.abs(ground) <= 3) {
            p.set(3, ground + 1, -2, plating(DyeColor.ORANGE));
            p.set(3, ground + 2, -2, Blocks.IRON_BARS.defaultBlockState());
            p.set(3, ground + 3, -2, Blocks.IRON_BARS.defaultBlockState());
            p.set(3, ground + 4, -2, Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, Direction.UP));
        }
        p.debris(3, 7, List.of(shell, UniverseBlocks.SMOLDERING_WRECK.get().defaultBlockState(), Blocks.IRON_BARS.defaultBlockState()), 5);
    }

    /**
     * 废弃建筑：一间 7×9 的旧居住舱，墙皮锈了，一角塌了，屋顶漏了个洞；门板掉在门口地上。屋里靠后墙一台数据终端
     * （第一次读给导航数据），一个补给箱，天花板上一盏不亮的灯。外面倒着一截天线和一个燃料罐。
     */
    static void abandonedBuilding(PoiStructures.Placer p, Landmark landmark) {
        RandomSource random = p.random;
        p.clearVegetation(LandmarkType.ABANDONED_BUILDING.radius() + 2, 10);
        int base = p.ground(0, 0);
        BlockState top = surface(p, base);
        flatten(p, 7, base, top);
        BlockState wall = plating(random.nextBoolean() ? DyeColor.LIGHT_GRAY : DyeColor.WHITE);
        BlockState rust = plating(DyeColor.BROWN);
        BlockState floor = plating(DyeColor.GRAY);
        // 塌掉的是后面的一个角
        int collapseX = random.nextBoolean() ? 3 : -3;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                p.foundation(dx, dz, base - 1, floor);
                p.set(dx, base, dz, floor);
                p.clear(dx, dz, base + 1, base + 6);
                boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 4;
                boolean corner = Math.abs(dx) == 3 && Math.abs(dz) == 4;
                boolean collapsed = Math.abs(dx - collapseX) <= 1 && dz >= 3;
                if (edge) {
                    for (int y = 1; y <= 3; y++) {
                        if (collapsed && y >= 2 || !corner && random.nextInt(14) == 0) {
                            continue;
                        }
                        p.set(dx, base + y, dz, y == 2 && !corner || random.nextInt(5) == 0 ? rust : wall);
                    }
                }
                // 屋顶：塌的那角和一处破洞没有
                boolean hole = dx == -collapseX / 3 && (dz == 0 || dz == -1);
                if (!collapsed && !hole && random.nextInt(16) != 0) {
                    p.set(dx, base + 4, dz, random.nextInt(6) == 0 ? rust : wall);
                }
            }
        }
        // 门口：门框空着，门板倒在外面的地上
        p.set(0, base + 1, -4, Blocks.AIR.defaultBlockState());
        p.set(0, base + 2, -4, Blocks.AIR.defaultBlockState());
        p.foundation(0, -5, base - 1, floor);
        p.set(0, base, -5, floor);
        p.set(0, base + 1, -5, Blocks.IRON_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.HALF, Half.BOTTOM).setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        // 窗户，一扇碎了
        BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
        boolean brokenLeft = random.nextBoolean();
        p.set(-3, base + 2, 0, brokenLeft ? Blocks.AIR.defaultBlockState() : pane);
        p.set(3, base + 2, 0, brokenLeft ? pane : Blocks.AIR.defaultBlockState());
        // 屋里
        p.set(-collapseX / 3 * 2, base + 1, 3, UniverseBlocks.DATA_TERMINAL.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        p.crate(collapseX / 3 * -2, base + 1, -2, collapseX > 0 ? Direction.EAST : Direction.WEST, ABANDONED_BUILDING_LOOT);
        p.set(0, base + 4, 1, Blocks.REDSTONE_LAMP.defaultBlockState());
        p.set(collapseX / 3 * 2, base + 1, -1, UniverseBlocks.SHIP_SEAT.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, collapseX > 0 ? Direction.WEST : Direction.EAST));
        // 塌的那角：外面的土和碎板灌进来
        for (int dx = collapseX - 1; dx <= collapseX + 1; dx++) {
            if (Math.abs(dx) <= 2 && random.nextBoolean()) {
                p.set(dx, base + 1, 3, top);
            }
        }
        p.set(collapseX + Integer.signum(collapseX), p.ground(collapseX + Integer.signum(collapseX), 4) + 1, 4, rust);
        // 屋外：倒在地上的一截天线，一个歪着的燃料罐
        int side = -collapseX / 3 * 5;
        BlockState chain = Blocks.IRON_CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Z);
        for (int dz = -2; dz <= 0; dz++) {
            int g = p.ground(side, dz);
            if (Math.abs(g) <= 2) {
                p.set(side, g + 1, dz, chain);
            }
        }
        int tank = p.ground(side, 3);
        if (Math.abs(tank) <= 2) {
            p.set(side, tank + 1, 3, UniverseBlocks.FUEL_TANK.get().defaultBlockState());
        }
        p.debris(5, 9, List.of(wall, rust, Blocks.IRON_BARS.defaultBlockState()), 4);
    }

    /**
     * 古生物发掘场（ADR-0034 §三，布局见 {@link DigSiteLayout}）：中间一片拉着测绳的探方，六个方坑挖下去一两格，
     * 坑底露着化石土，有一个坑里还露着一截没起出来的大骨头；三顶帐篷门朝探方，北边那顶是收藏家的，门口一张桌子、
     * 一个工具桶，桌上一盏灯；探方南边是筛过的土堆，东北角一个筛土的架子。外面一圈睡着三四个石哨兵。
     */
    static void digSite(PoiStructures.Placer p, Landmark landmark) {
        RandomSource random = p.random;
        DigSiteLayout.Layout layout = DigSiteLayout.layout(landmark.seed());
        p.clearVegetation(LandmarkType.DIG_SITE.radius(), 10);
        int base = p.ground(0, 0);
        BlockState top = surface(p, base);
        flatten(p, 12, base, top);
        // 方坑：挖下去，坑底先铺土，再按布局露出化石土
        for (DigSiteLayout.Square square : layout.squares()) {
            for (int x = square.x0(); x <= square.x1(); x++) {
                for (int z = square.z0(); z <= square.z1(); z++) {
                    p.clear(x, z, base - square.depth() + 1, base);
                    p.set(x, base - square.depth(), z, soil());
                }
            }
        }
        BlockState fossil = UniverseBlocks.FOSSIL_DIRT.get().defaultBlockState();
        for (DigSiteLayout.Cell cell : layout.fossils()) {
            p.set(cell.dx(), base + cell.dy(), cell.dz(), fossil);
        }
        // 一个坑底露着一截大骨头：横着的一排骨块，中间一节竖着冒出来
        DigSiteLayout.Square exposed = layout.squares().get(random.nextInt(layout.squares().size()));
        int boneZ = exposed.z0() + 1;
        int floor = base - exposed.depth();
        for (int x = exposed.x0(); x <= exposed.x1(); x++) {
            p.set(x, floor, boneZ, Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        }
        if (exposed.depth() > 1) {
            p.set(exposed.x0() + 1, floor + 1, boneZ, Blocks.BONE_BLOCK.defaultBlockState());
        }
        // 测绳：探方四角钉着木桩，桩顶挂灯，桩之间拉着绳子
        int hx = DigSiteLayout.FIELD_HALF_X + 1;
        int hz = DigSiteLayout.FIELD_HALF_Z + 1;
        BlockState lineX = Blocks.TRIPWIRE.defaultBlockState().setValue(TripWireBlock.EAST, true).setValue(TripWireBlock.WEST, true);
        BlockState lineZ = Blocks.TRIPWIRE.defaultBlockState().setValue(TripWireBlock.NORTH, true).setValue(TripWireBlock.SOUTH, true);
        for (int x = -hx + 1; x < hx; x++) {
            p.set(x, base + 1, -hz, lineX);
            p.set(x, base + 1, hz, lineX);
        }
        for (int z = -hz + 1; z < hz; z++) {
            p.set(-hx, base + 1, z, lineZ);
            p.set(hx, base + 1, z, lineZ);
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                p.set(sx * hx, base + 1, sz * hz, Blocks.SPRUCE_FENCE.defaultBlockState());
                p.set(sx * hx, base + 2, sz * hz, Blocks.LANTERN.defaultBlockState());
            }
        }
        // 帐篷：收藏家那顶是橙色的
        List<DigSiteLayout.Tent> tents = layout.tents();
        for (int i = 0; i < tents.size(); i++) {
            DigSiteLayout.Tent tent = tents.get(i);
            boolean collector = i == 0;
            tent(p, base, tent, collector ? DyeColor.ORANGE : DyeColor.LIGHT_GRAY, collector ? DyeColor.WHITE : DyeColor.ORANGE);
            int[] back = tentCell(tent, -1, 0);
            if (i < 2) {
                p.crate(back[0], base + 1, back[1], facing(tent), DIG_SITE_LOOT);
            } else {
                p.set(back[0], base + 1, back[1], Blocks.CARPET.pick(DyeColor.WHITE).defaultBlockState());
            }
            int[] mid = tentCell(tent, 0, 0);
            p.set(mid[0], base + 1, mid[1], Blocks.CARPET.pick(DyeColor.BROWN).defaultBlockState());
        }
        // 收藏家的桌子和工具桶
        int[] table = DigSiteLayout.TABLE;
        p.foundation(table[0], table[1], base, top);
        p.set(table[0], base + 1, table[1], Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        p.set(table[0], base + 2, table[1], Blocks.LANTERN.defaultBlockState());
        p.foundation(table[0] - 1, table[1], base, top);
        p.container(table[0] - 1, base + 1, table[1], Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), DIG_SITE_LOOT);
        // 南边筛过的土堆
        BlockState gravel = Blocks.GRAVEL.defaultBlockState();
        for (int x = -4; x <= 4; x++) {
            for (int z = hz + 1; z <= hz + 2; z++) {
                int height = Math.abs(x) <= 1 ? 2 : 1;
                if (Math.abs(x) == 4 && random.nextBoolean()) {
                    continue;
                }
                for (int h = 1; h <= height - (z == hz + 2 && random.nextBoolean() ? 1 : 0); h++) {
                    p.set(x, base + h, z, random.nextInt(3) == 0 ? gravel : soil());
                }
            }
        }
        // 东北角筛土的架子
        p.set(hx - 1, base + 1, -hz - 1, Blocks.SCAFFOLDING.defaultBlockState());
        p.set(hx, base + 1, -hz - 1, gravel);
        // 睡着的石哨兵：落在各自那一列的地上，离中心高低差太大的不放
        BlockState sentry = UniverseBlocks.DORMANT_STONE_SENTRY.get().defaultBlockState();
        for (int[] spot : layout.sentries()) {
            int ground = p.ground(spot[0], spot[1]);
            if (Math.abs(ground - base) <= LandmarkType.DIG_SITE.slope() && p.get(spot[0], ground, spot[1]).getFluidState().isEmpty()) {
                p.set(spot[0], ground + 1, spot[1], sentry);
            }
        }
    }

    /**
     * 一顶 A 字形的帐篷：两边的帆布第一格是整块、第二格是朝里斜的台阶，屋脊在第三格，换一种颜色；背面封死，正面中间是门。
     */
    private static void tent(PoiStructures.Placer p, int base, DigSiteLayout.Tent tent, DyeColor canvas, DyeColor ridge) {
        BlockState wall = Blocks.WOOL.pick(canvas).defaultBlockState();
        BlockState slope = Blocks.WOOL_STAIRS.pick(canvas).defaultBlockState();
        BlockState top = Blocks.WOOL.pick(ridge).defaultBlockState();
        int[] f = tent.forward();
        // 横着的 +1 方向
        Direction right = step(f[1], -f[0]);
        for (int along = -2; along <= 1; along++) {
            for (int across = -1; across <= 1; across++) {
                int[] at = tentCell(tent, along, across);
                p.foundation(at[0], at[1], base, soil());
                p.clear(at[0], at[1], base + 1, base + 3);
                if (across != 0) {
                    p.set(at[0], base + 1, at[1], wall);
                    // 台阶高的一边朝屋脊
                    p.set(at[0], base + 2, at[1], slope.setValue(StairBlock.FACING, across < 0 ? right : right.getOpposite()));
                } else {
                    p.set(at[0], base + 3, at[1], top);
                    if (along == -2) {
                        p.set(at[0], base + 1, at[1], wall);
                        p.set(at[0], base + 2, at[1], wall);
                    }
                }
            }
        }
    }

    /** 本地坐标的一步对应的水平方向。 */
    private static Direction step(int dx, int dz) {
        return dx > 0 ? Direction.EAST : dx < 0 ? Direction.WEST : dz < 0 ? Direction.NORTH : Direction.SOUTH;
    }

    /** 帐篷里顺着门的方向第 {@code along} 格、横着第 {@code across} 格（本地坐标）。 */
    private static int[] tentCell(DigSiteLayout.Tent tent, int along, int across) {
        int[] f = tent.forward();
        return new int[] {tent.x() + along * f[0] + across * f[1], tent.z() + along * f[1] - across * f[0]};
    }

    /** 帐篷门朝的方向（本地坐标）。 */
    private static Direction facing(DigSiteLayout.Tent tent) {
        int[] f = tent.forward();
        return step(f[0], f[1]);
    }

    // ---- 工具

    private static BlockState plating(DyeColor color) {
        return UniverseBlocks.HULL_PLATING.get(color).get().defaultBlockState();
    }

    /** 翻出来的土：星壤（客户端按星球的调色板染色）。 */
    private static BlockState soil() {
        return UniverseBlocks.PLANET_SOIL.get().defaultBlockState();
    }

    /** 中心的地表方块；是液体时用星壤。 */
    private static BlockState surface(PoiStructures.Placer p, int base) {
        BlockState state = p.get(0, base, 0);
        return state.getFluidState().isEmpty() && !state.isAir() ? state : soil();
    }

    /** 半径内的地面整平到 {@code base}：高出来的挖掉，低下去的用这一列自己的地表方块垫起来，最上面一层铺 {@code top}。 */
    private static void flatten(PoiStructures.Placer p, int radius, int base, BlockState top) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius + radius) {
                    continue;
                }
                int ground = p.ground(dx, dz);
                if (ground == base) {
                    continue;
                }
                BlockState own = p.get(dx, ground, dz);
                BlockState fill = own.getFluidState().isEmpty() ? own : top;
                if (ground > base) {
                    p.clear(dx, dz, base + 1, ground);
                } else {
                    for (int y = ground + 1; y < base; y++) {
                        p.set(dx, y, dz, fill);
                    }
                }
                p.set(dx, base, dz, top);
            }
        }
    }
}
