package io.github.sweetcornna.universe.core.deepspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.deepspace.DebrisField.Size;
import io.github.sweetcornna.universe.core.deepspace.DebrisField.Slot;

class DebrisFieldTest {
    @Test
    void thirtyToFiftyPiecesInsideTheField() {
        Map<Size, Integer> sizes = new EnumMap<>(Size.class);
        for (long seed = 0; seed < 40; seed++) {
            List<Slot> slots = DebrisField.slots(seed);
            assertEquals(slots, DebrisField.slots(seed));
            assertTrue(slots.size() >= DebrisField.MIN_COUNT && slots.size() <= DebrisField.MAX_COUNT, "块数 " + slots.size());
            for (Slot slot : slots) {
                assertTrue(Math.hypot(slot.dx(), slot.dz()) <= DebrisField.RADIUS + 1e-6);
                assertTrue(Math.abs(slot.dy()) <= DebrisField.THICKNESS + 1e-6);
                sizes.merge(slot.size(), 1, Integer::sum);
            }
        }
        for (Size size : Size.values()) {
            assertTrue(sizes.getOrDefault(size, 0) > 0, "三种大小都有：" + size);
        }
        assertTrue(sizes.get(Size.SMALL) > sizes.get(Size.LARGE), "小块比大块多");
    }

    @Test
    void shapesHaveTheirSizeAndSmallOnesAreGrabbable() {
        for (long seed = 0; seed < 200; seed++) {
            for (Size size : Size.values()) {
                Layout shape = DebrisField.shape(seed, size);
                int cells = shape.cells().size();
                assertTrue(cells >= 1 && cells <= size.max(), size + " 有 " + cells + " 格");
                if (size == Size.SMALL) {
                    // 小块缩到一半画，外接球不超过引力抓取器抓得动的大小
                    assertTrue(DebrisField.radius(shape, size) * 2 <= 2.2, "小块的直径 " + DebrisField.radius(shape, size) * 2);
                }
            }
        }
    }
}
