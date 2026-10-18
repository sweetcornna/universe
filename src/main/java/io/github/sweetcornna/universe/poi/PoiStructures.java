package io.github.sweetcornna.universe.poi;

import java.util.List;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.poi.DropPodBlock;
import io.github.sweetcornna.universe.block.poi.SupplyCrateBlock;
import io.github.sweetcornna.universe.core.poi.Poi;
import io.github.sweetcornna.universe.core.poi.PoiType;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * 兴趣点的建筑（ADR-0014）：用代码拼，跟着兴趣点的种子随机颜色、破损和朝向。地面方块用星球自己的方块（客户端按星球调色板染色），
 * 金属部分用船体板。
 *
 * <p>坐标系：原点是兴趣点中心的地面方块，y 往上；"前方"是本地的 -z，按 {@link Poi#rotation()} 转 90° 的倍数。
 * 所有方块离中心不超过 15 格，世界生成时都落在 3×3 区块里。
 */
public final class PoiStructures {
    public static final ResourceKey<LootTable> CRASHED_SHIP_LOOT = loot("chests/crashed_ship");
    public static final ResourceKey<LootTable> OUTPOST_LOOT = loot("chests/outpost");
    private static final DyeColor[] HULL_COLORS = {DyeColor.WHITE, DyeColor.LIGHT_GRAY, DyeColor.GRAY, DyeColor.ORANGE, DyeColor.CYAN,
            DyeColor.BLUE, DyeColor.RED, DyeColor.YELLOW, DyeColor.GREEN, DyeColor.PURPLE};
    private static final DyeColor[] ACCENTS = {DyeColor.ORANGE, DyeColor.CYAN, DyeColor.YELLOW, DyeColor.RED, DyeColor.BLUE, DyeColor.LIME};

    private PoiStructures() {
    }

    private static ResourceKey<LootTable> loot(String path) {
        return ResourceKey.create(Registries.LOOT_TABLE, Universe.id(path));
    }

    /**
     * 放下一个兴趣点。中心的地面在水里（地形模型和真实地形有出入时）就不放。
     *
     * @return 放了没有
     */
    public static boolean build(WorldGenLevel level, Poi poi) {
        int y = groundY(level, poi.x(), poi.z());
        BlockPos center = new BlockPos(poi.x(), y, poi.z());
        if (!level.getFluidState(center).isEmpty() || !level.getFluidState(center.above()).isEmpty() || y <= level.getMinY() + 4) {
            return false;
        }
        Placer p = new Placer(level, center, poi.rotation(), RandomSource.create(poi.seed()));
        switch (poi.type()) {
            case CRASHED_SHIP -> crashedShip(p);
            case DROP_POD -> dropPod(p);
            case OUTPOST -> outpost(p);
            case RUINS -> ruins(p);
            case BURIED_TECH -> buriedTech(p);
            case SIGNAL_BOOSTER -> signalBooster(p);
        }
        return true;
    }

    // ---- 各种兴趣点

    /** 掉落舱：半埋在烧焦地面里的小舱体，正面是控制台，顶上一根天线。 */
    private static void dropPod(Placer p) {
        BlockState white = plating(DyeColor.WHITE);
        BlockState orange = plating(DyeColor.ORANGE);
        p.clearVegetation(PoiType.DROP_POD.radius() + 1, 8);
        p.scorch(3.2, 0.6);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                p.foundation(dx, dz, -1, white);
                boolean corner = dx != 0 && dz != 0;
                p.set(dx, 0, dz, white);
                p.set(dx, 1, dz, corner ? orange : white);
                p.set(dx, 2, dz, white);
                if (!corner) {
                    p.set(dx, 3, dz, white);
                }
            }
        }
        p.set(0, 1, -1, UniverseBlocks.DROP_POD.get().defaultBlockState().setValue(DropPodBlock.FACING, Direction.NORTH));
        p.set(0, 2, -1, Blocks.STAINED_GLASS.lightBlue().defaultBlockState());
        p.set(0, 4, 0, Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, Direction.UP));
        // 落地时崩掉的几块
        p.debris(3, 4, List.of(white, orange, Blocks.IRON_BARS.defaultBlockState()), 3);
    }

    /** 埋藏的科技：地上冒出几簇晶体，正下方 2–4 格埋着科技储藏箱。 */
    private static void buriedTech(Placer p) {
        p.clearVegetation(PoiType.BURIED_TECH.radius(), 4);
        BlockState crystal = UniverseBlocks.PLANET_CRYSTAL.get().defaultBlockState();
        int clusters = 3 + p.random.nextInt(3);
        for (int i = 0; i < clusters; i++) {
            double angle = p.random.nextDouble() * Math.PI * 2;
            double r = 0.8 + p.random.nextDouble() * 1.8;
            int dx = (int) Math.round(Math.cos(angle) * r);
            int dz = (int) Math.round(Math.sin(angle) * r);
            int ground = p.ground(dx, dz);
            int height = 1 + p.random.nextInt(2);
            for (int h = 1; h <= height; h++) {
                p.set(dx, ground + h, dz, crystal);
            }
        }
        int depth = 2 + p.random.nextInt(3);
        p.set(0, -depth, 0, UniverseBlocks.TECH_CACHE.get().defaultBlockState());
        // 挖下去的那一格换成砾石，挖的时候能看出不一样
        p.set(0, 0, 0, Blocks.GRAVEL.defaultBlockState());
    }

    /** 信号增强器：小平台上一根天线杆，旁边两块太阳能板。 */
    private static void signalBooster(Placer p) {
        BlockState gray = plating(DyeColor.LIGHT_GRAY);
        p.clearVegetation(PoiType.SIGNAL_BOOSTER.radius(), 8);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                p.foundation(dx, dz, 0, gray);
                p.set(dx, 0, dz, gray);
                p.clear(dx, dz, 1, 6);
            }
        }
        p.set(0, 1, 0, UniverseBlocks.SIGNAL_BOOSTER.get().defaultBlockState());
        for (int y = 2; y <= 4; y++) {
            p.set(0, y, 0, Blocks.IRON_BARS.defaultBlockState());
        }
        p.set(0, 5, 0, Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, Direction.UP));
        p.set(-1, 1, 1, Blocks.DAYLIGHT_DETECTOR.defaultBlockState());
        p.set(1, 1, 1, Blocks.DAYLIGHT_DETECTOR.defaultBlockState());
    }

    /** 废弃据点：一间 7×7 的金属小屋，里面有数据终端和补给箱，屋顶有天线，墙上有几处破洞。 */
    private static void outpost(Placer p) {
        RandomSource random = p.random;
        DyeColor mainColor = random.nextBoolean() ? DyeColor.WHITE : DyeColor.LIGHT_GRAY;
        BlockState main = plating(mainColor);
        BlockState accent = plating(ACCENTS[random.nextInt(ACCENTS.length)]);
        BlockState floor = plating(DyeColor.GRAY);
        p.clearVegetation(PoiType.OUTPOST.radius(), 10);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                p.clear(dx, dz, 1, 8);
            }
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                p.foundation(dx, dz, 0, floor);
                p.set(dx, 0, dz, floor);
                boolean wall = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                boolean corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
                if (wall) {
                    for (int y = 1; y <= 3; y++) {
                        // 上沿有几块掉了
                        if (y == 3 && !corner && random.nextInt(9) == 0) {
                            continue;
                        }
                        p.set(dx, y, dz, corner ? accent : main);
                    }
                }
                p.set(dx, 4, dz, dx == 0 || dz == 0 ? accent : main);
            }
        }
        // 门口和台阶
        p.set(0, 1, -3, Blocks.AIR.defaultBlockState());
        p.set(0, 2, -3, Blocks.AIR.defaultBlockState());
        for (int dx = -1; dx <= 1; dx++) {
            p.foundation(dx, -4, 0, floor);
            p.set(dx, 0, -4, floor);
        }
        // 窗户
        BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
        p.set(-3, 2, 0, pane);
        p.set(3, 2, 0, pane);
        p.set(-1, 2, 3, pane);
        p.set(1, 2, 3, pane);
        p.set(0, 4, 0, Blocks.SEA_LANTERN.defaultBlockState());
        // 屋里：终端靠后墙对着门，补给箱在两个角上，一把座椅
        p.set(0, 1, 2, UniverseBlocks.DATA_TERMINAL.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        p.crate(2, 1, 2, Direction.WEST, OUTPOST_LOOT);
        if (random.nextBoolean()) {
            p.crate(-2, 1, -2, Direction.EAST, OUTPOST_LOOT);
        }
        p.set(-2, 1, 1, UniverseBlocks.SHIP_SEAT.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        // 屋顶天线，屋外一个燃料罐
        p.set(2, 5, 2, Blocks.IRON_BARS.defaultBlockState());
        p.set(2, 6, 2, Blocks.IRON_BARS.defaultBlockState());
        p.set(2, 7, 2, Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, Direction.UP));
        int tankGround = p.ground(5, 1);
        if (Math.abs(tankGround) <= 3) {
            p.set(5, tankGround + 1, 1, UniverseBlocks.FUEL_TANK.get().defaultBlockState());
        }
        p.debris(5, 8, List.of(main, Blocks.IRON_BARS.defaultBlockState(), Blocks.IRON_CHAIN.defaultBlockState()), 3);
    }

    /** 先驱者遗迹：石砖铺的圆形广场中间立着石碑，一圈断掉的石柱，前面一道拱门，周围散着碎石。 */
    private static void ruins(Placer p) {
        RandomSource random = p.random;
        BlockState tiles = Blocks.DEEPSLATE_TILES.defaultBlockState();
        BlockState crackedTiles = Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState();
        BlockState bricks = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        BlockState crackedBricks = Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
        BlockState polished = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        BlockState chiseled = Blocks.CHISELED_DEEPSLATE.defaultBlockState();
        BlockState rubble = UniverseBlocks.PLANET_COBBLESTONE.get().defaultBlockState();
        p.clearVegetation(5, 8);
        // 广场：半径 4.5 的圆，三成是裂开的，少数缺了
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > 4.5 || random.nextInt(12) == 0) {
                    continue;
                }
                p.foundation(dx, dz, -1, rubble);
                p.set(dx, 0, dz, random.nextInt(10) < 3 ? crackedTiles : tiles);
                p.clear(dx, dz, 1, 6);
            }
        }
        // 石碑：雕纹底座、发光的符文石、磨制顶石，四面一圈台阶
        p.set(0, 0, 0, polished);
        p.set(0, 1, 0, chiseled);
        p.set(0, 2, 0, UniverseBlocks.MONOLITH.get().defaultBlockState());
        p.set(0, 3, 0, polished);
        BlockState slab = Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            p.set(side.getStepX(), 1, side.getStepZ(), slab);
        }
        // 一圈六根石柱，断在不同的高度
        double start = random.nextDouble() * Math.PI;
        for (int i = 0; i < 6; i++) {
            double angle = start + i * Math.PI / 3;
            int dx = (int) Math.round(Math.cos(angle) * 7);
            int dz = (int) Math.round(Math.sin(angle) * 7);
            if (dz <= -6 && Math.abs(dx) <= 3) {
                continue;
            }
            pillar(p, dx, dz, 2 + random.nextInt(5), bricks, crackedBricks, chiseled, polished);
        }
        // 拱门：广场前方两根柱子架一道横梁
        int left = pillar(p, -2, -8, 5, bricks, crackedBricks, chiseled, polished);
        int right = pillar(p, 2, -8, 5, bricks, crackedBricks, chiseled, polished);
        int top = Math.max(left, right) + 1;
        for (int dx = -2; dx <= 2; dx++) {
            p.set(dx, top, -8, dx == 0 ? chiseled : bricks);
        }
        // 倒下的柱子和碎石
        for (int i = 0; i < 3; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int sx = (int) Math.round(Math.cos(angle) * (6 + random.nextInt(3)));
            int sz = (int) Math.round(Math.sin(angle) * (6 + random.nextInt(3)));
            Direction along = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            for (int j = 0; j < 3 + random.nextInt(2); j++) {
                int dx = sx + along.getStepX() * j;
                int dz = sz + along.getStepZ() * j;
                if (Math.abs(dx) > 10 || Math.abs(dz) > 10) {
                    break;
                }
                p.set(dx, p.ground(dx, dz) + 1, dz, random.nextBoolean() ? bricks : crackedBricks);
            }
        }
        p.debris(5, 10, List.of(rubble, crackedBricks, Blocks.COBBLED_DEEPSLATE.defaultBlockState()), 10);
    }

    /** 一根石柱：底下磨制石垫在地里，往上是石砖，顶上有时是雕纹石。返回柱顶的相对高度。 */
    private static int pillar(Placer p, int dx, int dz, int height, BlockState bricks, BlockState cracked, BlockState chiseled,
            BlockState polished) {
        int ground = p.ground(dx, dz);
        p.foundation(dx, dz, ground - 1, polished);
        p.set(dx, ground, dz, polished);
        for (int h = 1; h <= height; h++) {
            BlockState state = h == height && p.random.nextInt(3) == 0 ? chiseled : p.random.nextInt(4) == 0 ? cracked : bricks;
            p.set(dx, ground + h, dz, state);
        }
        return ground + height;
    }

    /**
     * 坠毁飞船：机头扎进陨坑、机尾翘起的残骸。机身三格宽，一侧有破口能进货舱；两片机翼一片断了；
     * 尾部的推进器、燃料罐能拆，少数还有脉冲引擎、跃迁引擎；机身上几处还在冒烟，后面拖着一道烧焦的痕迹。
     */
    private static void crashedShip(Placer p) {
        RandomSource random = p.random;
        DyeColor mainColor = HULL_COLORS[random.nextInt(HULL_COLORS.length)];
        DyeColor accentColor = ACCENTS[random.nextInt(ACCENTS.length)];
        if (accentColor == mainColor) {
            accentColor = DyeColor.WHITE;
        }
        BlockState main = plating(mainColor);
        BlockState accent = plating(accentColor);
        BlockState dark = plating(DyeColor.GRAY);
        BlockState glass = Blocks.STAINED_GLASS.lightBlue().defaultBlockState();
        BlockState smolder = UniverseBlocks.SMOLDERING_WRECK.get().defaultBlockState();
        p.clearVegetation(PoiType.CRASHED_SHIP.radius(), 12);
        p.scorch(11, 0.75);
        // 陨坑：在机头一带，中间深 3 格
        int craterZ = -3;
        double craterR = 4.6;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = craterZ - 6; dz <= craterZ + 6; dz++) {
                double d = Math.sqrt(dx * dx + (dz - craterZ) * (dz - craterZ)) + random.nextDouble() * 0.5;
                int ground = p.ground(dx, dz);
                if (d < craterR) {
                    int floor = -(int) Math.round(3 * (1 - d * d / (craterR * craterR)));
                    p.clear(dx, dz, floor + 1, Math.max(ground, 0) + 3);
                    p.set(dx, floor, dz, p.scorched());
                } else if (d < craterR + 1.5 && random.nextFloat() < 0.7F) {
                    p.set(dx, ground + 1, dz, random.nextBoolean() ? UniverseBlocks.PLANET_ASH.get().defaultBlockState() : p.scorched());
                }
            }
        }
        // 机身：z = -5（机头）到 5（机尾），机头低、机尾高
        int[] base = new int[11];
        for (int i = 0; i <= 10; i++) {
            int dz = i - 5;
            // 陨坑一带按原来的地面算，坑外跟着地形走
            int ground = dz <= craterZ + 4 ? 0 : Math.clamp(p.ground(0, dz), -4, 4);
            base[i] = ground + (int) Math.round(-2 + i * 0.3);
        }
        for (int i = 0; i <= 10; i++) {
            int dz = i - 5;
            int y = base[i];
            if (i == 0) {
                p.set(0, y, dz, main);
                continue;
            }
            for (int dx = -1; dx <= 1; dx++) {
                p.set(dx, y, dz, dark);
                boolean cockpit = i <= 2;
                p.set(dx, y + 1, dz, cockpit && dx == 0 ? glass : dx == 0 ? accent : main);
                if (i >= 3) {
                    p.set(dx, y + 2, dz, cockpit ? glass : main);
                }
            }
            if (i <= 2) {
                p.set(-1, y + 1, dz, glass);
                p.set(1, y + 1, dz, glass);
            }
        }
        // 货舱：中段掏空，右侧撞出一个破口
        for (int i = 5; i <= 7; i++) {
            p.set(0, base[i] + 1, i - 5, Blocks.AIR.defaultBlockState());
        }
        p.set(1, base[6] + 1, 1, Blocks.AIR.defaultBlockState());
        p.set(1, base[6] + 2, 1, Blocks.AIR.defaultBlockState());
        p.crate(0, base[6] + 1, 1, Direction.EAST, CRASHED_SHIP_LOOT);
        // 机翼：左边完整，右边断成两截，外面那截掉在地上
        for (int w = 2; w <= 4; w++) {
            for (int dz = 0; dz <= 1; dz++) {
                p.set(-w, base[dz + 5], dz, w == 4 ? accent : main);
                if (w <= 3) {
                    p.set(w, base[dz + 5], dz, w == 3 ? accent : main);
                }
            }
        }
        int fallenX = 6 + random.nextInt(2);
        int fallenZ = random.nextInt(3) - 1;
        p.set(fallenX, p.ground(fallenX, fallenZ) + 1, fallenZ, accent);
        p.set(fallenX, p.ground(fallenX, fallenZ + 1) + 1, fallenZ + 1, main);
        // 尾部：两台推进器朝后，中间燃料罐，可能还有引擎
        int tail = base[10];
        p.set(-1, tail + 1, 6, UniverseBlocks.SHIP_THRUSTER.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.SOUTH));
        p.set(1, tail + 1, 6, UniverseBlocks.SHIP_THRUSTER.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.SOUTH));
        p.set(0, tail + 1, 4, UniverseBlocks.FUEL_TANK.get().defaultBlockState());
        p.set(0, base[8] + 1, 3, UniverseBlocks.FUEL_TANK.get().defaultBlockState());
        if (random.nextInt(4) == 0) {
            p.set(0, tail + 1, 5, UniverseBlocks.IMPULSE_ENGINE.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        }
        if (random.nextInt(12) == 0) {
            p.set(0, base[8] + 1, 2, UniverseBlocks.WARP_DRIVE.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        }
        // 冒烟的地方
        int smoke = 2 + random.nextInt(3);
        for (int k = 0; k < smoke; k++) {
            int i = 3 + random.nextInt(8);
            int dx = random.nextInt(3) - 1;
            p.set(dx, base[i] + 2, i - 5, smolder);
        }
        // 来的方向（机尾后面）拖出一道烧焦的痕迹，沿途散着碎片
        for (int dz = 7; dz <= 13; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (Math.abs(dx) == 1 && dz > 10) {
                    continue;
                }
                int ground = p.ground(dx, dz);
                p.clear(dx, dz, ground + 1, ground + 4);
                p.set(dx, ground, dz, p.scorched());
            }
            if (dz % 3 == 0) {
                int dx = random.nextBoolean() ? 2 : -2;
                p.set(dx, p.ground(dx, dz) + 1, dz, random.nextInt(3) == 0 ? smolder : random.nextBoolean() ? main : dark);
            }
        }
    }

    // ---- 工具

    private static BlockState plating(DyeColor color) {
        return UniverseBlocks.HULL_PLATING.get(color).get().defaultBlockState();
    }

    /** 地面方块的 y：从最高的方块往下找，跳过树、植物；碰到液体也算地面（由调用方判断）。 */
    static int groundY(WorldGenLevel level, int x, int z) {
        Heightmap.Types type = level instanceof ServerLevel ? Heightmap.Types.WORLD_SURFACE : Heightmap.Types.WORLD_SURFACE_WG;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, level.getHeight(type, x, z) - 1, z);
        while (pos.getY() > level.getMinY()) {
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty() || state.is(BlockTags.BLOCKS_MOTION) && !vegetation(state)) {
                return pos.getY();
            }
            pos.move(Direction.DOWN);
        }
        return pos.getY();
    }

    /** 树干、树叶、菌盖、花这些清场时要拿掉的东西。 */
    static boolean vegetation(BlockState state) {
        return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(UniverseBlocks.PLANET_CAP.get())
                || state.is(UniverseBlocks.PLANET_BLOOM.get()) || state.is(BlockTags.REPLACEABLE);
    }

    /** 在以兴趣点中心地面为原点、转过朝向的坐标系里放方块。 */
    static final class Placer {
        private final WorldGenLevel level;
        private final BlockPos origin;
        private final Rotation rotation;
        final RandomSource random;
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        Placer(WorldGenLevel level, BlockPos origin, int rotation, RandomSource random) {
            this.level = level;
            this.origin = origin;
            this.rotation = Rotation.values()[Math.floorMod(rotation, 4)];
            this.random = random;
        }

        /** 本地坐标转成世界坐标（和结构模板的旋转一致：顺时针 90° 时 (x, z) → (-z, x)）。 */
        BlockPos at(int dx, int dy, int dz) {
            int x = switch (this.rotation) {
                case NONE -> dx;
                case CLOCKWISE_90 -> -dz;
                case CLOCKWISE_180 -> -dx;
                case COUNTERCLOCKWISE_90 -> dz;
            };
            int z = switch (this.rotation) {
                case NONE -> dz;
                case CLOCKWISE_90 -> dx;
                case CLOCKWISE_180 -> -dz;
                case COUNTERCLOCKWISE_90 -> -dx;
            };
            return this.pos.set(this.origin.getX() + x, this.origin.getY() + dy, this.origin.getZ() + z);
        }

        void set(int dx, int dy, int dz, BlockState state) {
            this.level.setBlock(this.at(dx, dy, dz), state.rotate(this.rotation), 2);
        }

        BlockState get(int dx, int dy, int dz) {
            return this.level.getBlockState(this.at(dx, dy, dz));
        }

        /** 这一列地面相对原点的高度。 */
        int ground(int dx, int dz) {
            BlockPos column = this.at(dx, 0, dz);
            return groundY(this.level, column.getX(), column.getZ()) - this.origin.getY();
        }

        /** 把一列从 {@code from} 到 {@code to}（相对高度，含两端）清成空气。 */
        void clear(int dx, int dz, int from, int to) {
            for (int y = from; y <= to; y++) {
                if (!this.get(dx, y, dz).isAir()) {
                    this.set(dx, y, dz, Blocks.AIR.defaultBlockState());
                }
            }
        }

        /** 半径内的树和植物拿掉（地面以上 {@code height} 格以内），地形不动。 */
        void clearVegetation(int radius, int height) {
            for (int dx = -radius - 2; dx <= radius + 2; dx++) {
                for (int dz = -radius - 2; dz <= radius + 2; dz++) {
                    if (dx * dx + dz * dz > (radius + 2) * (radius + 2)) {
                        continue;
                    }
                    int ground = this.ground(dx, dz);
                    for (int y = ground + 1; y <= ground + height; y++) {
                        BlockState state = this.get(dx, y, dz);
                        if (!state.isAir() && vegetation(state)) {
                            this.set(dx, y, dz, Blocks.AIR.defaultBlockState());
                        }
                    }
                }
            }
        }

        /** 地面比 {@code top} 低时往下垫到地面，免得建筑悬空。 */
        void foundation(int dx, int dz, int top, BlockState state) {
            int ground = this.ground(dx, dz);
            for (int y = ground + 1; y <= top; y++) {
                this.set(dx, y, dz, state);
            }
        }

        /** 烧焦的地面方块。 */
        BlockState scorched() {
            int roll = this.random.nextInt(10);
            if (roll < 4) {
                return UniverseBlocks.PLANET_ASH.get().defaultBlockState();
            } else if (roll < 7) {
                return Blocks.BLACKSTONE.defaultBlockState();
            } else if (roll < 9) {
                return Blocks.SMOOTH_BASALT.defaultBlockState();
            }
            return Blocks.MAGMA_BLOCK.defaultBlockState();
        }

        /** 半径内的地面按概率烧焦，越往外越少。 */
        void scorch(double radius, double chance) {
            int r = (int) Math.ceil(radius);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > radius || this.random.nextDouble() > chance * (1 - d / radius * 0.7)) {
                        continue;
                    }
                    int ground = this.ground(dx, dz);
                    BlockState state = this.get(dx, ground, dz);
                    if (state.getFluidState().isEmpty() && !state.is(BlockTags.LOGS)) {
                        this.set(dx, ground, dz, this.scorched());
                    }
                }
            }
        }

        /** 在半径 [min, max] 的环上随机撒几块碎片。 */
        void debris(int min, int max, List<BlockState> blocks, int count) {
            for (int i = 0; i < count; i++) {
                double angle = this.random.nextDouble() * Math.PI * 2;
                double r = min + this.random.nextDouble() * (max - min);
                int dx = (int) Math.round(Math.cos(angle) * r);
                int dz = (int) Math.round(Math.sin(angle) * r);
                int ground = this.ground(dx, dz);
                if (this.get(dx, ground, dz).getFluidState().isEmpty() && this.get(dx, ground + 1, dz).isAir()) {
                    this.set(dx, ground + 1, dz, blocks.get(this.random.nextInt(blocks.size())));
                }
            }
        }

        /** 放一个补给箱，第一次打开时按战利品表装东西。 */
        void crate(int dx, int dy, int dz, Direction facing, ResourceKey<LootTable> loot) {
            this.set(dx, dy, dz, UniverseBlocks.SUPPLY_CRATE.get().defaultBlockState().setValue(SupplyCrateBlock.FACING, facing));
            RandomizableContainer.setBlockEntityLootTable(this.level, this.random, this.at(dx, dy, dz).immutable(), loot);
        }
    }
}
