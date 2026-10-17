package io.github.sweetcornna.universe.core.survival;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class VeinClustersTest {
    @Test
    void mergesNearbyBlocksOfTheSameKind() {
        VeinClusters clusters = new VeinClusters();
        // 一团 3×3×1 的钛（种类 1），旁边混着一块铁（种类 2）
        for (int x = 10; x < 13; x++) {
            for (int z = 0; z < 3; z++) {
                clusters.add(x, 40, z, 1);
            }
        }
        clusters.add(11, 41, 1, 2);
        // 远处另一团钛
        clusters.add(30, 40, 0, 1);
        clusters.add(31, 40, 0, 1);
        List<VeinClusters.Vein> veins = clusters.nearest(0, 40, 0, 10);
        assertEquals(3, veins.size());
        VeinClusters.Vein first = veins.get(0);
        assertEquals(1, first.kind());
        assertEquals(9, first.count());
        assertEquals(11, first.x());
        assertEquals(1, first.z());
        assertEquals(2, veins.get(1).kind());
        assertEquals(2, veins.get(2).count());
    }

    @Test
    void keepsOnlyTheNearest() {
        VeinClusters clusters = new VeinClusters();
        for (int i = 0; i < 50; i++) {
            clusters.add(i * 10, 0, 0, 1);
        }
        List<VeinClusters.Vein> veins = clusters.nearest(0, 0, 0, 5);
        assertEquals(5, veins.size());
        assertEquals(40, veins.get(4).x());
    }
}
