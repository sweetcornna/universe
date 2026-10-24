package io.github.sweetcornna.universe.core.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class SoundscapeTest {
    private static Soundscape.Source at(String group, double x) {
        return new Soundscape.Source(group, x, 0, 0, 1);
    }

    @Test
    void oneMachineNextToYouIsAboutTwoThirds() {
        List<Soundscape.Voice> voices = Soundscape.mix(List.of(at("a", 0)), 0, 0, 0, 24, 8);
        assertEquals(1, voices.size());
        assertEquals(1 - Math.exp(-1), voices.get(0).volume(), 1e-9);
    }

    @Test
    void moreMachinesGetLouderButSaturate() {
        List<Soundscape.Source> four = new ArrayList<>();
        List<Soundscape.Source> hundred = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            if (i < 4) {
                four.add(at("a", 0));
            }
            hundred.add(at("a", 0));
        }
        double v4 = Soundscape.mix(four, 0, 0, 0, 24, 8).get(0).volume();
        double v100 = Soundscape.mix(hundred, 0, 0, 0, 24, 8).get(0).volume();
        assertTrue(v4 > 0.97);
        assertTrue(v100 <= 1 && v100 - v4 < 0.02);
    }

    @Test
    void positionLeansTowardTheNearOnes() {
        // 一台在 2 格，三台在 20 格：中心在近的那台附近，不在平均位置 15.5
        List<Soundscape.Source> sources = List.of(at("a", 2), at("a", 20), at("a", 20), at("a", 20));
        Soundscape.Voice voice = Soundscape.mix(sources, 0, 0, 0, 24, 8).get(0);
        assertTrue(voice.x() < 8, "x " + voice.x());
    }

    @Test
    void outOfRangeMachinesAreSilent() {
        assertTrue(Soundscape.mix(List.of(at("a", 30)), 0, 0, 0, 24, 8).isEmpty());
    }

    @Test
    void groupsAreSeparateAndTheLoudestKept() {
        List<Soundscape.Source> sources = List.of(at("near", 1), at("mid", 10), at("far", 20));
        List<Soundscape.Voice> voices = Soundscape.mix(sources, 0, 0, 0, 24, 2);
        assertEquals(2, voices.size());
        assertEquals("near", voices.get(0).group());
        assertEquals("mid", voices.get(1).group());
    }
}
