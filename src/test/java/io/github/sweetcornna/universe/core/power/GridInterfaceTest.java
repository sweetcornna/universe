package io.github.sweetcornna.universe.core.power;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GridInterfaceTest {
    @Test
    void inputModeGeneratesWhatWasPouredIn() {
        GridInterface gi = new GridInterface();
        assertEquals(0, gi.generation(), "空缓冲不发电");
        assertEquals(0, gi.demand(), "输入模式不要电");
        assertEquals(1000, gi.insert(1000, 5));
        assertEquals(1000, gi.generation());
        // 电网只用了一半
        assertEquals(500, gi.settle(gi.generation(), gi.demand(), 1, 0.5));
        assertEquals(500, gi.buffer());
        assertEquals(0, gi.extract(100, 5), "输入模式不让抽");
    }

    @Test
    void outputModeDrawsFromGridAndGivesOut() {
        GridInterface gi = new GridInterface();
        gi.setOutput(true);
        assertEquals(GridInterface.RATE, gi.demand(), "空缓冲按每刻上限要电");
        assertEquals(0, gi.generation());
        // 电网只供得上八成
        long taken = gi.settle(gi.generation(), gi.demand(), 0.8, 0);
        assertEquals(Math.round(GridInterface.RATE * 0.8), taken);
        assertEquals(taken, gi.buffer());
        assertEquals(0, gi.insert(100, 1), "输出模式不收");
        assertEquals(1000, gi.extract(1000, 1));
        assertEquals(taken - 1000, gi.buffer());
    }

    @Test
    void externalSideIsCappedPerTick() {
        GridInterface gi = new GridInterface();
        assertEquals(GridInterface.RATE, gi.insert(10_000, 7), "一刻最多收 2250");
        assertEquals(0, gi.insert(1, 7), "同一刻再灌不进");
        assertEquals(GridInterface.RATE, gi.external(7));
        // 下一刻额度回来了，但缓冲只剩 2250 的空
        assertEquals(GridInterface.BUFFER - GridInterface.RATE, gi.insert(10_000, 8));
        assertEquals(GridInterface.BUFFER, gi.buffer());
        assertEquals(0, gi.insert(1, 9), "缓冲满了不收");
        assertEquals(0, gi.external(9));
    }

    @Test
    void gridSideIsCappedPerTick() {
        GridInterface gi = new GridInterface();
        gi.setBuffer(GridInterface.BUFFER);
        assertEquals(GridInterface.RATE, gi.generation(), "满缓冲也只按每刻上限发");
        gi.setOutput(true);
        assertEquals(0, gi.demand(), "满缓冲不要电");
        gi.setBuffer(100);
        assertEquals(GridInterface.RATE, gi.demand());
    }

    @Test
    void bufferSurvivesModeSwitch() {
        GridInterface gi = new GridInterface();
        gi.insert(1500, 1);
        gi.setOutput(true);
        assertEquals(1500, gi.buffer());
        assertEquals(1500, gi.extract(5000, 2), "换成输出以后缓冲里的照样给出去");
    }

    @Test
    void snapshotRestoresEverything() {
        GridInterface gi = new GridInterface();
        gi.insert(300, 3);
        long[] before = gi.snapshot();
        gi.insert(1200, 3);
        gi.restore(before);
        assertEquals(300, gi.buffer());
        assertEquals(300, gi.external(3));
        assertArrayEquals(before, gi.snapshot());
    }

    @Test
    void settleNeverOverdrawsOrOverfills() {
        GridInterface gi = new GridInterface();
        gi.setBuffer(100);
        assertEquals(100, gi.settle(2250, 0, 1, 1), "报的比缓冲多也只给缓冲里有的");
        assertEquals(0, gi.buffer());
        gi.setOutput(true);
        gi.setBuffer(GridInterface.BUFFER - 50);
        assertEquals(50, gi.settle(0, 2250, 1, 0));
        assertEquals(GridInterface.BUFFER, gi.buffer());
    }
}
