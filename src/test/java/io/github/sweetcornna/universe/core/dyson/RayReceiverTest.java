package io.github.sweetcornna.universe.core.dyson;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RayReceiverTest {
    @Test
    void continuityRisesOverTwentyMinutesAndFallsInTwo() {
        double c = 0;
        for (int t = 0; t < 12_000; t++) {
            c = RayReceiver.continuity(c, true);
        }
        assertEquals(0.5, c, 1e-6);
        for (int t = 0; t < 12_000; t++) {
            c = RayReceiver.continuity(c, true);
        }
        assertEquals(1, c, 1e-9);
        for (int t = 0; t < 2_400; t++) {
            c = RayReceiver.continuity(c, false);
        }
        assertEquals(0, c, 1e-9);
    }

    @Test
    void numbersMatchDysonSphereProgram() {
        // 6 MW → 15 MW，效率 30% → 58%
        assertEquals(300, RayReceiver.maxOutput(0, false), 1e-9);
        assertEquals(750, RayReceiver.maxOutput(1, false), 1e-9);
        assertEquals(1500, RayReceiver.maxOutput(1, true), 1e-9);
        assertEquals(0.3, RayReceiver.efficiency(0), 1e-9);
        assertEquals(0.58, RayReceiver.efficiency(1), 1e-9);
        assertEquals(1000, RayReceiver.powerRequest(0, false, 1), 1e-9);
        // 射线传输效率升 1 级：损耗 70% → 59.5%
        assertEquals(0.405, RayReceiver.efficiency(0, 0.85), 1e-9);
        // 光子模式满功率每分钟 6 个
        double perMinute = RayReceiver.photonRequest(1, false) * 1200 / RayReceiver.PHOTON_ENERGY;
        assertEquals(6, perMinute, 1e-9);
        assertEquals(12_000, RayReceiver.photonRequest(1, true), 1e-9);
    }

    @Test
    void darkFogLensHalvesLossAndRisesTwiceAsFast() {
        RayReceiver.Lens lens = RayReceiver.Lens.DARK_FOG;
        // 功率和光子的需求和引力透镜一样翻倍
        assertEquals(RayReceiver.maxOutput(1, RayReceiver.Lens.GRAVITON), RayReceiver.maxOutput(1, lens), 1e-9);
        assertEquals(RayReceiver.photonRequest(0.5, RayReceiver.Lens.GRAVITON), RayReceiver.photonRequest(0.5, lens), 1e-9);
        // 损耗 70% → 35%，58% 的效率损失的 42% 砍掉一半是 79%
        assertEquals(0.65, RayReceiver.efficiency(0, lens, 1), 1e-9);
        assertEquals(0.79, RayReceiver.efficiency(1, lens, 1), 1e-9);
        assertEquals(1500 / 0.79, RayReceiver.powerRequest(1, lens, 1), 1e-9);
        // 10 分钟积满
        double c = 0;
        for (int t = 0; t < 12_000; t++) {
            c = RayReceiver.continuity(c, true, lens);
        }
        assertEquals(1, c, 1e-6);
        // 一个能用 18000 刻，引力透镜 12000
        assertEquals(18_000, lens.ticks());
        assertEquals(RayReceiver.LENS_TICKS, RayReceiver.Lens.GRAVITON.ticks());
        assertEquals(lens, RayReceiver.Lens.byKey("dark_fog"));
        assertEquals(RayReceiver.Lens.NONE, RayReceiver.Lens.byKey("?"));
    }
}
