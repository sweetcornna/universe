package io.github.sweetcornna.universe.core.factory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FluidUnitsTest {
    @Test
    void splitsAndJoinsMillibuckets() {
        assertEquals(2500, FluidUnits.total(2, 500));
        assertEquals(2, FluidUnits.whole(2500));
        assertEquals(500, FluidUnits.extra(2500));
        assertEquals(0, FluidUnits.whole(999));
        assertEquals(999, FluidUnits.extra(999));
    }

    @Test
    void smallDrawsOweUntilAWholeItemIsTaken() {
        // 管道一次抽 250：前三次只记欠着，第四次拿走一个物品
        FluidUnits.Draw draw = FluidUnits.draw(3, 0, 250);
        assertEquals(new FluidUnits.Draw(250, 0, 250), draw);
        draw = FluidUnits.draw(3, 750, 250);
        assertEquals(new FluidUnits.Draw(250, 1, 0), draw);
        draw = FluidUnits.draw(3, 900, 250);
        assertEquals(new FluidUnits.Draw(250, 1, 150), draw);
    }

    @Test
    void drawsAreLimitedToWhatIsThere() {
        assertEquals(2600, FluidUnits.available(3, 400));
        FluidUnits.Draw draw = FluidUnits.draw(3, 400, 10_000);
        assertEquals(new FluidUnits.Draw(2600, 3, 0), draw, "抽空了，欠着的也清掉");
        assertEquals(new FluidUnits.Draw(0, 0, 0), FluidUnits.draw(0, 0, 1000));
        assertEquals(new FluidUnits.Draw(2000, 2, 0), FluidUnits.draw(5, 0, 2000));
    }
}
