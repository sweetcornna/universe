package io.github.sweetcornna.universe.gametest;

import java.util.Objects;

import io.github.sweetcornna.universe.perf.PerfBench;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * 参照基地的性能测量（ADR-0038），只在设了 {@link PerfBench#PROPERTY} 时登记，这时别的游戏内测试都不登记，量到的只有参照基地。
 * 基地搭在测试场地以东几千格的空地上；搭好以后按真实时间等区块更新起来，热身，再计时。由 {@link UniverseGameTests} 登记。
 */
final class PerfBenchGameTests {
    /** 基地离测试场地多远。 */
    private static final int OFFSET = 4000;

    private PerfBenchGameTests() {
    }

    /** 测试最多跑几刻：等区块、热身、计时，再留一些余量。 */
    static int maxTicks(PerfBench.Settings settings) {
        return RealTime.TICKS + PerfBench.WARMUP + settings.ticks() + 200;
    }

    static void measure(GameTestHelper helper) {
        PerfBench.Settings settings = Objects.requireNonNull(PerfBench.Settings.fromSystem(), PerfBench.PROPERTY);
        BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 0)).offset(OFFSET, 0, 0);
        PerfBench bench = PerfBench.build(helper.getLevel(), origin, settings);
        RealTime.await(helper, bench::ticking, "参照基地的区块还没开始更新")
                .thenIdle(PerfBench.WARMUP)
                .thenExecute(bench::startTiming)
                .thenIdle(settings.ticks())
                .thenExecute(bench::finish)
                .thenSucceed();
    }
}
