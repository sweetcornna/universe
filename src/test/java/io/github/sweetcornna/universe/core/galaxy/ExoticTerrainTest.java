package io.github.sweetcornna.universe.core.galaxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.terrain.TerrainModel;

/** 奇异星球的六种地貌（ADR-0030 §二）。 */
class ExoticTerrainTest {
    private static final long SEED = 20261023L;
    /** 测试用的地面：缓缓起伏的平原，海平面 100。 */
    private static final int SEA = 100;

    /** 起伏的干地，没有熔岩。 */
    private static ExoticTerrain.Ground rolling() {
        return new ExoticTerrain.Ground() {
            @Override
            public double height(int x, int z) {
                return SEA + 12 + 3 * Math.sin(x / 37.0) + 2.5 * Math.cos(z / 29.0);
            }

            @Override
            public boolean lava(int x, int z) {
                return false;
            }
        };
    }

    private static ExoticTerrain terrain(ExoticForm form, ExoticTerrain.Keepout keepout) {
        return new ExoticTerrain(form, SEED, 1.0, 288, Liquid.WATER, SEA, TerrainModel.MAX_Y, rolling(), keepout);
    }

    private static ExoticTerrain terrain(ExoticForm form) {
        return terrain(form, ExoticTerrain.Keepout.NONE);
    }

    /** 按地面函数算出一个区块的列（和世界生成一样，逐列取真实高度）。 */
    private static ExoticTerrain.Columns columns(ExoticTerrain.Ground ground, int x0, int z0) {
        int[] g = new int[256];
        byte[] s = new byte[256];
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = x0 + dx;
                int z = z0 + dz;
                g[dx * 16 + dz] = (int) Math.floor(ground.height(x, z));
                s[dx * 16 + dz] = ground.lava(x, z) ? ExoticTerrain.BLOCKED
                        : ground.height(x, z) >= SEA + 1 ? ExoticTerrain.DRY : ExoticTerrain.WET;
            }
        }
        return new ExoticTerrain.Columns(g, s);
    }

    /** 一片区域里所有非空的列：键是世界坐标，值是方块段。 */
    private static java.util.Map<Long, List<ExoticTerrain.Span>> scan(ExoticTerrain terrain, ExoticTerrain.Ground ground,
            int chunks) {
        java.util.Map<Long, List<ExoticTerrain.Span>> found = new java.util.LinkedHashMap<>();
        for (int cx = 0; cx < chunks; cx++) {
            for (int cz = 0; cz < chunks; cz++) {
                int x0 = cx * 16;
                int z0 = cz * 16;
                ExoticTerrain.Chunk chunk = terrain.chunk(x0, z0, columns(ground, x0, z0));
                for (int dx = 0; dx < 16; dx++) {
                    for (int dz = 0; dz < 16; dz++) {
                        List<ExoticTerrain.Span> spans = chunk.column(dx, dz);
                        if (!spans.isEmpty()) {
                            found.put((long) (x0 + dx) << 32 | (z0 + dz) & 0xFFFFFFFFL, spans);
                        }
                    }
                }
            }
        }
        return found;
    }

    @Test
    void everyFormGeneratesSomething() {
        for (ExoticForm form : ExoticForm.values()) {
            ExoticTerrain.Ground ground = rolling();
            ExoticTerrain terrain = terrain(form);
            int columns = scan(terrain, ground, 8).size();
            assertTrue(columns > 0, form + " 一片 128×128 的地上什么都没生成");
        }
    }

    /**
     * 最要紧的一条：跨区块边界的同一座石拱、同一根柱子，接缝两侧连得上。把一片地按区块逐个生成，
     * 再把同一片地当成一个整体（每一列单独算）生成，两次的结果必须一模一样——区块怎么切都不影响。
     */
    @Test
    void unitsSpanChunkBordersSeamlessly() {
        for (ExoticForm form : ExoticForm.values()) {
            ExoticTerrain.Ground ground = rolling();
            ExoticTerrain terrain = terrain(form);
            // 按 16×16 的区块生成 4×4 个区块
            var byChunk = scan(terrain, ground, 4);
            // 同一片地，每一列各自当成一个区块的西北角单独生成
            var byColumn = new java.util.LinkedHashMap<Long, List<ExoticTerrain.Span>>();
            for (int x = 0; x < 64; x++) {
                for (int z = 0; z < 64; z++) {
                    ExoticTerrain.Chunk one = terrain.chunk(x, z, columns(ground, x, z));
                    List<ExoticTerrain.Span> spans = one.column(0, 0);
                    if (!spans.isEmpty()) {
                        byColumn.put((long) x << 32 | z & 0xFFFFFFFFL, spans);
                    }
                }
            }
            assertEquals(byColumn, byChunk, form + " 按区块生成和逐列生成的结果不一样，接缝处会错开");
        }
    }

    /** 接缝两侧紧挨的两列不会一边老高、一边完全空着（同一座结构被切断）。 */
    @Test
    void neighbouringColumnsAcrossABorderAgree() {
        for (ExoticForm form : ExoticForm.values()) {
            ExoticTerrain.Ground ground = rolling();
            ExoticTerrain terrain = terrain(form);
            var found = scan(terrain, ground, 6);
            for (var entry : found.entrySet()) {
                int x = (int) (entry.getKey() >> 32);
                int z = (int) (long) entry.getKey();
                if (x % 16 != 15 || x + 1 >= 96) {
                    continue;
                }
                int g = (int) Math.floor(ground.height(x, z));
                int height = entry.getValue().getLast().top() - g;
                if (height < 6) {
                    continue;
                }
                // 这一列是接缝左侧、而且挺高：右边那一列要么也有东西，要么这座结构本来就到这儿为止。
                // 高柱子不该正好在区块边界上被整齐地切掉——用"右边一列和左边一列的高度差"衡量
                List<ExoticTerrain.Span> right = found.get((long) (x + 1) << 32 | z & 0xFFFFFFFFL);
                if (right != null) {
                    int rg = (int) Math.floor(ground.height(x + 1, z));
                    assertTrue(Math.abs(right.getLast().top() - rg - height) <= height,
                            form + " 接缝 x=" + x + " z=" + z + " 两侧高度差了一倍以上");
                }
            }
        }
    }

    /** 同一列从不同的区块原点算出来的结果必须一模一样（区块对齐不影响结果）。 */
    @Test
    void oneColumnIsIndependentOfItsChunk() {
        for (ExoticForm form : ExoticForm.values()) {
            ExoticTerrain.Ground ground = rolling();
            ExoticTerrain terrain = terrain(form);
            for (int x = 40; x < 56; x++) {
                for (int z = 70; z < 78; z++) {
                    // 这一列落在以它为西北角的区块里，和落在把它包在中间的那个区块里
                    int ax0 = x;
                    int az0 = z;
                    ExoticTerrain.Chunk a = terrain.chunk(ax0, az0, columns(ground, ax0, az0));
                    int bx0 = Math.floorDiv(x, 16) * 16;
                    int bz0 = Math.floorDiv(z, 16) * 16;
                    ExoticTerrain.Chunk b = terrain.chunk(bx0, bz0, columns(ground, bx0, bz0));
                    List<ExoticTerrain.Span> fromA = a.column(0, 0);
                    List<ExoticTerrain.Span> fromB = b.column(x - bx0, z - bz0);
                    assertEquals(fromA, fromB, form + " 列 (" + x + ", " + z + ") 在两个区块里算出的结果不一样");
                }
            }
        }
    }

    /** 同一个种子两次生成完全一样。 */
    @Test
    void sameSeedSameResult() {
        for (ExoticForm form : ExoticForm.values()) {
            ExoticTerrain.Ground ground = rolling();
            var first = scan(terrain(form), ground, 4);
            var second = scan(terrain(form), ground, 4);
            assertEquals(first, second, form + " 同一个种子两次生成不一样");
        }
    }

    /** 让开要让开的东西：整片区域都被挡住时，一个方块都不放。 */
    @Test
    void respectsKeepout() {
        ExoticTerrain.Keepout all = (x, z, radius) -> true;
        for (ExoticForm form : ExoticForm.values()) {
            ExoticTerrain.Ground ground = rolling();
            var found = scan(terrain(form, all), ground, 6);
            assertTrue(found.isEmpty(), form + " 在全挡住的地方还放了 " + found.size() + " 列");
        }
    }

    /** 熔岩（BLOCKED）的列上什么都不放。 */
    @Test
    void skipsBlockedColumns() {
        ExoticTerrain.Ground lava = new ExoticTerrain.Ground() {
            @Override
            public double height(int x, int z) {
                return SEA + 12;
            }

            @Override
            public boolean lava(int x, int z) {
                return true;
            }
        };
        for (ExoticForm form : ExoticForm.values()) {
            ExoticTerrain terrain = new ExoticTerrain(form, SEED, 1.0, 288, Liquid.LAVA, SEA, TerrainModel.MAX_Y, lava,
                    ExoticTerrain.Keepout.NONE);
            var found = scan(terrain, lava, 6);
            assertTrue(found.isEmpty(), form + " 在熔岩上放了 " + found.size() + " 列");
        }
    }

    /** 石林、结核球、多边形、石拱只长在干地上，水里不长。 */
    @Test
    void dryOnlyFormsStayOutOfWater() {
        ExoticTerrain.Ground seabed = new ExoticTerrain.Ground() {
            @Override
            public double height(int x, int z) {
                // 整片都在海平面以下
                return SEA - 10;
            }

            @Override
            public boolean lava(int x, int z) {
                return false;
            }
        };
        List<ExoticForm> dryOnly = List.of(ExoticForm.HOODOOS, ExoticForm.CONCRETIONS, ExoticForm.POLYGONS, ExoticForm.ARCHES);
        for (ExoticForm form : dryOnly) {
            ExoticTerrain terrain = new ExoticTerrain(form, SEED, 1.0, 288, Liquid.WATER, SEA, TerrainModel.MAX_Y, seabed,
                    ExoticTerrain.Keepout.NONE);
            var found = scan(terrain, seabed, 6);
            assertTrue(found.isEmpty(), form + " 在水下放了 " + found.size() + " 列");
        }
    }

    /** 柱状玄武岩伸进浅海，晶柱也长在水里。 */
    @Test
    void basaltAndCrystalsReachIntoWater() {
        ExoticTerrain.Ground shallow = new ExoticTerrain.Ground() {
            @Override
            public double height(int x, int z) {
                // 浅海：水深两格
                return SEA - 2;
            }

            @Override
            public boolean lava(int x, int z) {
                return false;
            }
        };
        for (ExoticForm form : List.of(ExoticForm.BASALT_COLUMNS, ExoticForm.GIANT_CRYSTALS)) {
            ExoticTerrain terrain = new ExoticTerrain(form, SEED, 1.0, 288, Liquid.WATER, SEA, TerrainModel.MAX_Y, shallow,
                    ExoticTerrain.Keepout.NONE);
            var found = scan(terrain, shallow, 8);
            assertFalse(found.isEmpty(), form + " 该伸进浅海，却什么都没生成");
        }
    }

    /** 方块段都在能放方块的范围里，上下不颠倒。 */
    @Test
    void spansAreWellFormed() {
        for (ExoticForm form : ExoticForm.values()) {
            ExoticTerrain.Ground ground = rolling();
            ExoticTerrain terrain = terrain(form);
            for (List<ExoticTerrain.Span> spans : scan(terrain, ground, 6).values()) {
                for (ExoticTerrain.Span span : spans) {
                    assertTrue(span.bottom() <= span.top(), form + " 方块段上下颠倒：" + span);
                    assertTrue(span.top() <= TerrainModel.MAX_Y, form + " 方块段超出世界顶：" + span);
                    assertTrue(span.bottom() >= TerrainModel.MIN_Y, form + " 方块段低于世界底：" + span);
                }
            }
        }
    }

    /** 低重力的星球上长得比正常重力高。 */
    @Test
    void lowGravityGrowsTaller() {
        ExoticTerrain.Ground ground = rolling();
        for (ExoticForm form : List.of(ExoticForm.BASALT_COLUMNS, ExoticForm.HOODOOS, ExoticForm.ARCHES)) {
            int normal = tallest(new ExoticTerrain(form, SEED, 1.0, 288, Liquid.WATER, SEA, TerrainModel.MAX_Y, ground,
                    ExoticTerrain.Keepout.NONE), ground);
            int light = tallest(new ExoticTerrain(form, SEED, 0.18, 288, Liquid.WATER, SEA, TerrainModel.MAX_Y, ground,
                    ExoticTerrain.Keepout.NONE), ground);
            assertTrue(light > normal, form + " 低重力下没长得更高：" + light + " vs " + normal);
        }
    }

    private static int tallest(ExoticTerrain terrain, ExoticTerrain.Ground ground) {
        int best = 0;
        for (var entry : scan(terrain, ground, 8).entrySet()) {
            int x = (int) (entry.getKey() >> 32);
            int z = (int) (long) entry.getKey();
            int g = (int) Math.floor(ground.height(x, z));
            for (ExoticTerrain.Span span : entry.getValue()) {
                best = Math.max(best, span.top() - g);
            }
        }
        return best;
    }

    /** 多边形地面按温度换材质：冷的用冰楔，热而干的用盐壳，中间用碎石。 */
    @Test
    void polygonMaterialFollowsTemperature() {
        ExoticTerrain.Ground flat = new ExoticTerrain.Ground() {
            @Override
            public double height(int x, int z) {
                return SEA + 8;
            }

            @Override
            public boolean lava(int x, int z) {
                return false;
            }
        };
        assertEquals(ExoticTerrain.Material.ICE, polygonMaterial(flat, 230));
        assertEquals(ExoticTerrain.Material.STONES, polygonMaterial(flat, 280));
        assertEquals(ExoticTerrain.Material.SALT, polygonMaterial(flat, 320));
    }

    private static ExoticTerrain.Material polygonMaterial(ExoticTerrain.Ground ground, double kelvin) {
        ExoticTerrain terrain = new ExoticTerrain(ExoticForm.POLYGONS, SEED, 1.0, kelvin, Liquid.NONE, SEA,
                TerrainModel.MAX_Y, ground, ExoticTerrain.Keepout.NONE);
        List<ExoticTerrain.Material> seen = new ArrayList<>();
        for (List<ExoticTerrain.Span> spans : scan(terrain, ground, 4).values()) {
            spans.forEach(span -> seen.add(span.material()));
        }
        assertFalse(seen.isEmpty(), kelvin + " K 的多边形地面什么都没生成");
        return seen.getFirst();
    }

    /** 每种地貌只用自己该用的材质。 */
    @Test
    void eachFormUsesItsOwnMaterials() {
        ExoticTerrain.Ground ground = rolling();
        assertMaterials(ExoticForm.BASALT_COLUMNS, ground, ExoticTerrain.Material.COLUMN, ExoticTerrain.Material.COLUMN_TOP,
                ExoticTerrain.Material.JOINT);
        assertMaterials(ExoticForm.HOODOOS, ground, ExoticTerrain.Material.SHAFT, ExoticTerrain.Material.CAPROCK);
        assertMaterials(ExoticForm.GIANT_CRYSTALS, ground, ExoticTerrain.Material.CRYSTAL);
        assertMaterials(ExoticForm.CONCRETIONS, ground, ExoticTerrain.Material.BOULDER, ExoticTerrain.Material.VEIN,
                ExoticTerrain.Material.SPHERULE);
        assertMaterials(ExoticForm.ARCHES, ground, ExoticTerrain.Material.ARCH);
    }

    private static void assertMaterials(ExoticForm form, ExoticTerrain.Ground ground, ExoticTerrain.Material... allowed) {
        List<ExoticTerrain.Material> ok = List.of(allowed);
        for (List<ExoticTerrain.Span> spans : scan(terrain(form), ground, 8).values()) {
            for (ExoticTerrain.Span span : spans) {
                assertTrue(ok.contains(span.material()), form + " 用了不该用的材质 " + span.material());
            }
        }
    }

    /** 每颗奇异星球都挑得出一种地貌，同一颗星球每次一样。 */
    @Test
    void everyExoticPlanetHasAForm() {
        Galaxy galaxy = GalaxyGenerator.generate(SEED, 48);
        int exotic = 0;
        for (var system : galaxy.systems()) {
            for (Planet planet : system.bodies()) {
                if (planet.type() == PlanetType.EXOTIC) {
                    exotic++;
                    ExoticForm form = ExoticForm.of(planet).orElseThrow();
                    assertEquals(form, ExoticForm.of(planet.seed()));
                } else {
                    assertTrue(ExoticForm.of(planet).isEmpty(), planet.name() + " 不是奇异星球却有子类型");
                }
            }
        }
        assertTrue(exotic > 0, "这个星团里没有奇异星球，换个种子");
    }

    /** 调试命令用的 locate：找得到附近的一处地貌。 */
    @Test
    void locateFindsAFormation() {
        for (ExoticForm form : ExoticForm.values()) {
            ExoticTerrain terrain = terrain(form);
            assertTrue(terrain.locate(0, 0, 256).isPresent(), form + " 在 256 格内找不到");
        }
    }
}
