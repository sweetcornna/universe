package io.github.sweetcornna.universe.core.factory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.factory.BeltGraph.Chain;
import io.github.sweetcornna.universe.core.factory.BeltGraph.Side;
import io.github.sweetcornna.universe.core.factory.BeltGraph.Slope;
import io.github.sweetcornna.universe.core.factory.BeltGraph.Target;

class BeltGraphTest {
    private static final int SOUTH = 0;
    private static final int WEST = 1;
    private static final int NORTH = 2;
    private static final int EAST = 3;

    private record Belt(int x, int y, int z, int facing, Slope slope, BeltTier tier) implements BeltGraph.Node {
    }

    /** 一张小地图：按坐标放传送带。 */
    private static final class World implements BeltGraph.Lookup<Belt> {
        final Map<String, Belt> belts = new HashMap<>();

        Belt put(int x, int y, int z, int facing) {
            return this.put(x, y, z, facing, Slope.FLAT, BeltTier.MK1);
        }

        Belt put(int x, int y, int z, int facing, Slope slope, BeltTier tier) {
            Belt belt = new Belt(x, y, z, facing, slope, tier);
            this.belts.put(x + "," + y + "," + z, belt);
            return belt;
        }

        @Override
        public Belt at(int x, int y, int z) {
            return this.belts.get(x + "," + y + "," + z);
        }

        List<Chain<Belt>> chains() {
            return BeltGraph.chains(this.belts.values(), this);
        }
    }

    @Test
    void facingVectorsFollowVanillaOrder() {
        assertEquals(1, BeltGraph.dz(SOUTH));
        assertEquals(-1, BeltGraph.dx(WEST));
        assertEquals(-1, BeltGraph.dz(NORTH));
        assertEquals(1, BeltGraph.dx(EAST));
        assertEquals(EAST, BeltGraph.clockwise(NORTH));
        assertEquals(WEST, BeltGraph.counterClockwise(NORTH));
        assertTrue(BeltGraph.perpendicular(NORTH, EAST));
        assertFalse(BeltGraph.perpendicular(NORTH, SOUTH));
    }

    @Test
    void straightRunIsOneLine() {
        World world = new World();
        Belt a = world.put(0, 0, 0, EAST);
        Belt b = world.put(1, 0, 0, EAST);
        Belt c = world.put(2, 0, 0, EAST);
        List<Chain<Belt>> chains = world.chains();
        assertEquals(1, chains.size());
        assertEquals(List.of(a, b, c), chains.get(0).nodes());
        assertFalse(chains.get(0).loop());
        assertNull(BeltGraph.target(c, world));
    }

    @Test
    void cornerWithOneSideFeederIsACurve() {
        World world = new World();
        // 往东走，在 (2,0,0) 拐向北
        Belt a = world.put(1, 0, 0, EAST);
        Belt corner = world.put(2, 0, 0, NORTH);
        Belt after = world.put(2, 0, -1, NORTH);
        assertEquals(Side.LEFT, BeltGraph.curve(corner, world));
        List<Chain<Belt>> chains = world.chains();
        assertEquals(1, chains.size());
        assertEquals(List.of(a, corner, after), chains.get(0).nodes());

        // 往西走拐向北是从右边进来
        World other = new World();
        other.put(3, 0, 0, WEST);
        Belt right = other.put(2, 0, 0, NORTH);
        assertEquals(Side.RIGHT, BeltGraph.curve(right, other));
    }

    @Test
    void sideFeedIntoAStraightBeltMergesInTheMiddle() {
        World world = new World();
        Belt main0 = world.put(0, 0, 0, NORTH);
        Belt main1 = world.put(0, 0, -1, NORTH);
        Belt side = world.put(-1, 0, -1, EAST);
        assertEquals(Side.NONE, BeltGraph.curve(main1, world));
        List<Chain<Belt>> chains = world.chains();
        assertEquals(2, chains.size());
        Target<Belt> target = BeltGraph.target(side, world);
        assertNotNull(target);
        assertSame(main1, target.belt());
        assertTrue(target.side());
        assertSame(main0, BeltGraph.previous(main1, world));
    }

    @Test
    void twoSideFeedersAreBothMerges() {
        World world = new World();
        Belt middle = world.put(0, 0, 0, NORTH);
        world.put(-1, 0, 0, EAST);
        world.put(1, 0, 0, WEST);
        assertNull(BeltGraph.previous(middle, world));
        assertEquals(Side.NONE, BeltGraph.curve(middle, world));
        assertEquals(3, world.chains().size());
    }

    @Test
    void headOnBeltsDoNotConnect() {
        World world = new World();
        Belt a = world.put(0, 0, 0, EAST);
        world.put(1, 0, 0, WEST);
        assertNull(BeltGraph.target(a, world));
        assertEquals(2, world.chains().size());
    }

    @Test
    void slopesClimbAndDescend() {
        World world = new World();
        // 平 → 上坡 → 上面一层平 → 下坡 → 回到地面
        Belt a = world.put(0, 0, 0, EAST);
        Belt up = world.put(1, 0, 0, EAST, Slope.UP, BeltTier.MK1);
        Belt top = world.put(2, 1, 0, EAST);
        Belt down = world.put(3, 0, 0, EAST, Slope.DOWN, BeltTier.MK1);
        Belt end = world.put(4, 0, 0, EAST);
        assertArrayEquals(new int[] {2, 1, 0}, BeltGraph.exit(up));
        assertArrayEquals(new int[] {3, 1, 0}, BeltGraph.entry(down));
        List<Chain<Belt>> chains = world.chains();
        assertEquals(1, chains.size());
        assertEquals(List.of(a, up, top, down, end), chains.get(0).nodes());
        // 坡道的侧面不收东西
        World side = new World();
        Belt slope = side.put(0, 0, 0, NORTH, Slope.UP, BeltTier.MK1);
        Belt feeder = side.put(-1, 0, 0, EAST);
        assertNull(BeltGraph.target(feeder, side));
        assertEquals(Side.NONE, BeltGraph.curve(slope, side));
    }

    @Test
    void differentTiersAreSeparateLinesButStillHandOver() {
        World world = new World();
        Belt slow = world.put(0, 0, 0, EAST);
        Belt fast = world.put(1, 0, 0, EAST, Slope.FLAT, BeltTier.MK2);
        assertNull(BeltGraph.previous(fast, world));
        assertEquals(2, world.chains().size());
        Target<Belt> target = BeltGraph.target(slow, world);
        assertNotNull(target);
        assertSame(fast, target.belt());
        assertFalse(target.side());
    }

    @Test
    void loopBreaksAtTheSmallestCoordinate() {
        World world = new World();
        Belt a = world.put(0, 0, 0, EAST);
        Belt b = world.put(1, 0, 0, SOUTH);
        Belt c = world.put(1, 0, 1, WEST);
        Belt d = world.put(0, 0, 1, NORTH);
        List<Chain<Belt>> chains = world.chains();
        assertEquals(1, chains.size());
        assertTrue(chains.get(0).loop());
        assertEquals(List.of(a, b, c, d), chains.get(0).nodes());
        Target<Belt> target = BeltGraph.target(d, world);
        assertNotNull(target);
        assertSame(a, target.belt());
    }

    @Test
    void feedersFindEveryBeltEndingInABlock() {
        World world = new World();
        world.put(-1, 0, 0, EAST);
        world.put(0, -1, 1, NORTH, Slope.UP, BeltTier.MK1);
        world.put(0, 0, -1, SOUTH, Slope.DOWN, BeltTier.MK1);
        world.put(5, 5, 5, EAST);
        assertEquals(3, BeltGraph.feeders(0, 0, 0, world).size());
    }

    @Test
    void replaceRangeSwapsOnlyThatStretch() {
        BeltLine line = new BeltLine(3, BeltTier.MK1);
        assertTrue(line.insert(500, 1));
        assertTrue(line.insert(300, 2));
        assertTrue(line.insert(100, 3));
        // 换掉第二格（200–400）
        line.replaceRange(200, 400, new int[] {250, 350}, new int[] {7, 8});
        assertArrayEquals(new int[] {500, 350, 250, 100}, line.positions());
        assertArrayEquals(new int[] {1, 8, 7, 3}, line.items());
        // 最后一格连停在尽头的一起换
        assertTrue(line.insert(600, 4));
        line.replaceRange(400, 600, new int[] {600}, new int[] {9});
        assertEquals(600, line.position(0));
        assertEquals(9, line.item(0));
        assertEquals(4, line.count());
    }
}
