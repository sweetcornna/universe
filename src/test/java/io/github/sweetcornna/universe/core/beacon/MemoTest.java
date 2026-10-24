package io.github.sweetcornna.universe.core.beacon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.discovery.DiscoveryKey;
import io.github.sweetcornna.universe.core.galaxy.BodyId;

class MemoTest {
    @Test
    void cleanDropsBlankLinesAndCapsLengthAndCount() {
        List<Memo.Line> raw = new ArrayList<>();
        raw.add(new Memo.Line("  铁矿在山北  ", false));
        raw.add(new Memo.Line("   ", true));
        raw.add(new Memo.Line("x".repeat(80), true));
        raw.add(new Memo.Line("带\n换行§c和格式", false));
        for (int i = 0; i < 10; i++) {
            raw.add(new Memo.Line("第 " + i + " 行", false));
        }
        Memo memo = Memo.clean(raw);
        assertEquals(Memo.MAX_LINES, memo.size());
        assertEquals("铁矿在山北", memo.lines().get(0).text());
        assertEquals(Memo.MAX_CHARS, memo.lines().get(1).text().length());
        assertTrue(memo.lines().get(1).done());
        assertEquals("带换行c和格式", memo.lines().get(2).text());
        assertTrue(Memo.clean(List.of(new Memo.Line("", true))).isEmpty());
    }

    @Test
    void cleanCountsCodePointsNotChars() {
        String emoji = "🚀".repeat(Memo.MAX_CHARS + 5);
        String text = Memo.cleanText(emoji, Memo.MAX_CHARS);
        assertEquals(Memo.MAX_CHARS, text.codePointCount(0, text.length()));
    }

    @Test
    void toggleFlipsOneLine() {
        Memo memo = new Memo(List.of(new Memo.Line("a", false), new Memo.Line("b", false)));
        Memo next = memo.toggle(1);
        assertEquals(1, next.done());
        assertTrue(next.lines().get(1).done());
        assertFalse(next.lines().get(0).done());
        assertEquals(memo, memo.toggle(5));
        assertEquals(0, next.toggle(1).done());
    }

    @Test
    void visibilityRanges() {
        assertTrue(BeaconVisibility.PLANET.visible(true, true));
        assertFalse(BeaconVisibility.PLANET.visible(false, true));
        assertTrue(BeaconVisibility.SYSTEM.visible(false, true));
        assertFalse(BeaconVisibility.SYSTEM.visible(false, false));
        assertTrue(BeaconVisibility.CLUSTER.visible(false, false));
        // 星图上：到星系的只在同一个星系里标，到星团的哪都标，本星球的不标
        assertFalse(BeaconVisibility.PLANET.onStarMap(true));
        assertTrue(BeaconVisibility.SYSTEM.onStarMap(true));
        assertFalse(BeaconVisibility.SYSTEM.onStarMap(false));
        assertTrue(BeaconVisibility.CLUSTER.onStarMap(false));
        assertEquals(BeaconVisibility.PLANET, BeaconVisibility.CLUSTER.next());
        assertEquals(BeaconVisibility.SYSTEM, BeaconVisibility.byKey("system"));
        assertEquals(BeaconVisibility.PLANET, BeaconVisibility.byKey("bad"));
    }

    @Test
    void styleClampsColorAndLabel() {
        assertEquals(BeaconStyle.color(0), BeaconStyle.color(99));
        assertEquals(BeaconStyle.MAX_LABEL, BeaconStyle.cleanLabel("y".repeat(50)).length());
    }

    @Test
    void memoKeysMapToDiscoveries() {
        BodyId moon = BodyId.moon(3, 1, 0);
        assertEquals(Optional.of(DiscoveryKey.body(moon)), MemoKeys.discovery(MemoKeys.body(moon)));
        assertEquals(Optional.of(DiscoveryKey.system(7)), MemoKeys.discovery(MemoKeys.system(7)));
        assertTrue(MemoKeys.discovery("system:-1").isEmpty());
        assertTrue(MemoKeys.discovery("system:x").isEmpty());
        assertTrue(MemoKeys.discovery("planet:3").isEmpty());
        assertTrue(MemoKeys.discovery(null).isEmpty());
    }
}
