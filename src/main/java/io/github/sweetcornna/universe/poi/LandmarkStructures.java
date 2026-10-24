package io.github.sweetcornna.universe.poi;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.poi.SealedCrateBlock;
import io.github.sweetcornna.universe.core.poi.Landmark;
import io.github.sweetcornna.universe.core.poi.LandmarkLayout;
import io.github.sweetcornna.universe.core.ship.CrashedShip;
import io.github.sweetcornna.universe.core.ship.ShipDesign;
import io.github.sweetcornna.universe.core.ship.ShipDesign.Cell;
import io.github.sweetcornna.universe.entity.ship.ShipBlueprints;
import io.github.sweetcornna.universe.fluid.AcidFluid;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * 地标的建筑（ADR-0026）。和兴趣点一样用代码拼（{@link PoiStructures.Placer}），原点是中心的海底方块，所有方块离中心不超过 15 格。
 * 海底的地标盖在水里：留空的地方是水，台阶、半砖、箱子都泡着水。
 */
public final class LandmarkStructures {
    public static final ResourceKey<LootTable> SHIPWRECK_LOOT = loot("chests/shipwreck");
    public static final ResourceKey<LootTable> SEABED_RUIN_LOOT = loot("chests/seabed_ruin");
    /** 中心上面至少要有几格水（地形模型和真实地形有出入时不放）。 */
    private static final int MIN_WATER = LandmarkLayout.SEABED_DEPTH - 4;

    private LandmarkStructures() {
    }

    private static ResourceKey<LootTable> loot(String path) {
        return ResourceKey.create(Registries.LOOT_TABLE, Universe.id(path));
    }

    /**
     * 放下一处地标。海底的地标中心上面的水不够深就不放。
     *
     * @return 放了没有
     */
    public static boolean build(WorldGenLevel level, Landmark landmark) {
        int x = landmark.x();
        int z = landmark.z();
        boolean seabed = landmark.type().seabed();
        int y = seabed ? PoiStructures.seabedY(level, x, z) : PoiStructures.groundY(level, x, z);
        if (y <= level.getMinY() + 6 || !seabed && !level.getFluidState(new BlockPos(x, y, z)).isEmpty()) {
            // 地上的地标中心不放在湖里
            return false;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y + 1, z);
        int surface = y;
        while (AcidFluid.sea(level.getFluidState(pos)) && pos.getY() < level.getMaxY()) {
            surface = pos.getY();
            pos.move(Direction.UP);
        }
        if (seabed ? surface - y < MIN_WATER : surface > y) {
            // 海底的要够深；地上的不放进水里
            return false;
        }
        PoiStructures.Placer p = new PoiStructures.Placer(level, new BlockPos(x, y, z), landmark.rotation(), RandomSource.create(landmark.seed()));
        if (seabed) {
            p.underwater(surface);
        }
        switch (landmark.type()) {
            case SHIPWRECK -> shipwreck(p, landmark.seed());
            case SEABED_RUIN -> seabedRuin(p);
            case TRADING_POST -> OutpostStructures.tradingPost(p, landmark);
            case MANUFACTURING_FACILITY -> OutpostStructures.manufacturingFacility(p, landmark);
            case OBSERVATORY -> OutpostStructures.observatory(p, landmark);
            case FOSSIL_PIT -> WildStructures.fossilPit(p, landmark);
            case SALVAGE_SITE -> WildStructures.salvageSite(p, landmark);
            case ABANDONED_BUILDING -> WildStructures.abandonedBuilding(p, landmark);
            case KNOWLEDGE_STONE -> SettlementStructures.knowledgeStone(p, SettlementStructures.race(level));
            case SETTLEMENT -> SettlementStructures.settlement(p, landmark, SettlementStructures.race(level));
            case DIG_SITE -> WildStructures.digSite(p, landmark);
        }
        return true;
    }

    /**
     * 沉船：飞船设计器造的一整艘船，烂掉了一些船体板，船底陷进海底一格，驾驶台和值钱的部件早就拆走了；
     * 船边堆着沙，四周散着掉下来的船体板。货箱里有东西。
     */
    private static void shipwreck(PoiStructures.Placer p, long seed) {
        RandomSource random = p.random;
        ShipDesign design = CrashedShip.design(seed);
        List<Cell> cells = CrashedShip.wreck(design, seed);
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Cell cell : design.cells()) {
            minX = Math.min(minX, cell.x());
            maxX = Math.max(maxX, cell.x());
            minZ = Math.min(minZ, cell.z());
            maxZ = Math.max(maxZ, cell.z());
        }
        int cx = Math.floorDiv(minX + maxX, 2);
        int cz = Math.floorDiv(minZ + maxZ, 2);
        int minY = design.minY();
        int base = p.ground(0, 0);
        // 船身朝一边歪：每往一侧一格，下沉的高度差一点
        double roll = (random.nextDouble() - 0.5) * 0.3;
        Set<Long> footprint = new HashSet<>();
        BlockState hull = ShipBlueprints.state(design, new Cell(0, 0, 0, ShipDesign.Role.HULL, ShipDesign.Face.NORTH));
        BlockState trim = ShipBlueprints.state(design, new Cell(0, 0, 0, ShipDesign.Role.TRIM, ShipDesign.Face.NORTH));
        for (Cell cell : cells) {
            boolean cargo = cell.role() == ShipDesign.Role.CARGO;
            // 泡烂的船体板掉了一些
            if (!cargo && random.nextFloat() < 0.12F) {
                continue;
            }
            BlockState state = switch (cell.role()) {
                // 驾驶台、跃迁引擎、护盾、炮早被拆走，只剩船体板
                case HELM -> trim;
                case WARP, WARP_MK2, SHIELD, CANNON -> hull;
                case THRUSTER, THRUSTER_MK2, FUEL_TANK, FUEL_TANK_MK2, IMPULSE, IMPULSE_MK2 ->
                        random.nextBoolean() ? ShipBlueprints.state(design, cell) : hull;
                default -> ShipBlueprints.state(design, cell);
            };
            int dx = cell.x() - cx;
            int dz = cell.z() - cz;
            int dy = base + cell.y() - minY + (int) Math.round(dx * roll);
            p.set(dx, dy, dz, state);
            if (cargo) {
                p.loot(dx, dy, dz, SHIPWRECK_LOOT);
            }
            footprint.add(column(dx, dz));
        }
        // 船边的沙堆
        BlockState sand = UniverseBlocks.PLANET_SAND.get().defaultBlockState();
        for (int dx = minX - cx - 2; dx <= maxX - cx + 2; dx++) {
            for (int dz = minZ - cz - 2; dz <= maxZ - cz + 2; dz++) {
                if (footprint.contains(column(dx, dz)) || !near(footprint, dx, dz) || random.nextFloat() > 0.55F) {
                    continue;
                }
                int ground = p.ground(dx, dz);
                p.set(dx, ground + 1, dz, sand);
                if (random.nextFloat() < 0.3F) {
                    p.set(dx, ground + 2, dz, sand);
                }
            }
        }
        // 掉在海底的船体板
        for (int k = 0, tries = 0; k < 7 && tries < 60; tries++) {
            int dx = random.nextInt(25) - 12;
            int dz = random.nextInt(25) - 12;
            if (footprint.contains(column(dx, dz)) || near(footprint, dx, dz) || dx * dx + dz * dz > 160) {
                continue;
            }
            int ground = p.ground(dx, dz);
            p.set(dx, ground + 1, dz, random.nextBoolean() ? hull : trim);
            k++;
        }
    }

    /**
     * 海底遗迹：一座 11×11 的石厅，下半截埋在海底里，屋里挖空泡着水；四角的柱子断在不同的高度，阶梯式的拱顶塌了一块。
     * 前墙正中一道门，门外挖出一道斜坡；屋里四根柱子挂着海晶灯，中间一座平台，后墙和两侧各一个海底密封箱。
     */
    private static void seabedRuin(PoiStructures.Placer p) {
        RandomSource random = p.random;
        BlockState bricks = Blocks.PRISMARINE_BRICKS.defaultBlockState();
        BlockState dark = Blocks.DARK_PRISMARINE.defaultBlockState();
        BlockState rough = Blocks.PRISMARINE.defaultBlockState();
        BlockState lantern = Blocks.SEA_LANTERN.defaultBlockState();
        BlockState slab = Blocks.PRISMARINE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState sand = UniverseBlocks.PLANET_SAND.get().defaultBlockState();
        BlockState water = Blocks.AIR.defaultBlockState();
        int floor = -3;
        int top = 4;
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                boolean wall = Math.abs(dx) == 5 || Math.abs(dz) == 5;
                boolean corner = Math.abs(dx) == 5 && Math.abs(dz) == 5;
                p.set(dx, floor - 1, dz, rough);
                p.set(dx, floor, dz, (dx + dz & 1) == 0 ? dark : bricks);
                if (corner) {
                    int height = top + 1 + random.nextInt(4);
                    for (int y = floor + 1; y <= height; y++) {
                        p.set(dx, y, dz, dark);
                    }
                } else if (wall) {
                    for (int y = floor + 1; y <= top; y++) {
                        // 墙的上沿缺了几块
                        if (y >= top - 1 && random.nextInt(5) == 0) {
                            p.set(dx, y, dz, water);
                            continue;
                        }
                        p.set(dx, y, dz, y == top ? dark : random.nextInt(6) == 0 ? rough : bricks);
                    }
                } else {
                    for (int y = floor + 1; y <= top; y++) {
                        p.set(dx, y, dz, water);
                    }
                }
            }
        }
        // 阶梯式的拱顶，中间塌了一块
        for (int ring = 4; ring >= 1; ring--) {
            int y = top + 5 - ring;
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring || random.nextInt(4) == 0 || ring <= 2 && dx >= 0 && dz <= 0) {
                        continue;
                    }
                    p.set(dx, y, dz, ring == 4 ? dark : bricks);
                }
            }
        }
        // 门洞和门外的斜坡
        for (int dx = -1; dx <= 1; dx++) {
            for (int y = floor + 1; y <= floor + 3; y++) {
                p.set(dx, y, -5, water);
            }
            for (int step = 1; step <= 4; step++) {
                int dz = -5 - step;
                int ground = p.ground(dx, dz);
                int level = Math.min(ground, floor + step - 1);
                for (int y = level + 1; y <= ground; y++) {
                    p.set(dx, y, dz, water);
                }
                p.set(dx, level, dz, step % 2 == 0 ? slab : bricks);
            }
        }
        // 屋里的四根柱子和海晶灯
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                for (int y = floor + 1; y <= top; y++) {
                    p.set(3 * sx, y, 3 * sz, y == floor + 3 ? lantern : dark);
                }
            }
        }
        // 中间的平台
        p.set(0, floor + 1, 0, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        p.set(0, floor + 2, 0, lantern);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            p.set(side.getStepX(), floor + 1, side.getStepZ(), slab);
        }
        // 三个密封箱：后墙正中、左右两侧
        sealed(p, 0, floor + 1, 4, Direction.NORTH);
        sealed(p, -4, floor + 1, 0, Direction.EAST);
        sealed(p, 4, floor + 1, 0, Direction.WEST);
        // 淤进来的沙
        for (int i = 0; i < 10; i++) {
            int dx = random.nextInt(9) - 4;
            int dz = random.nextInt(9) - 4;
            if (Math.abs(dx) == 3 && Math.abs(dz) == 3 || Math.abs(dx) <= 1 && Math.abs(dz) <= 1 || dz == 4 || Math.abs(dx) == 4 && dz == 0) {
                continue;
            }
            p.set(dx, floor + 1, dz, sand);
        }
        // 倒在外面的柱子
        for (int i = 0; i < 3; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int sx = (int) Math.round(Math.cos(angle) * (7 + random.nextInt(3)));
            int sz = (int) Math.round(Math.sin(angle) * (7 + random.nextInt(3)));
            Direction along = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            for (int j = 0; j < 3; j++) {
                int dx = sx + along.getStepX() * j;
                int dz = sz + along.getStepZ() * j;
                if (Math.abs(dx) > 12 || Math.abs(dz) > 12 || Math.abs(dx) <= 6 && Math.abs(dz) <= 6) {
                    break;
                }
                p.set(dx, p.ground(dx, dz) + 1, dz, random.nextBoolean() ? dark : bricks);
            }
        }
    }

    private static void sealed(PoiStructures.Placer p, int dx, int dy, int dz, Direction facing) {
        p.container(dx, dy, dz, UniverseBlocks.SEALED_CRATE.get().defaultBlockState().setValue(SealedCrateBlock.FACING, facing),
                SEABED_RUIN_LOOT);
    }

    private static long column(int dx, int dz) {
        return ((long) dx << 32) ^ (dz & 0xFFFFFFFFL);
    }

    /** (dx, dz) 紧挨着船（八个方向之一）。 */
    private static boolean near(Set<Long> footprint, int dx, int dz) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (footprint.contains(column(dx + x, dz + z))) {
                    return true;
                }
            }
        }
        return false;
    }
}
