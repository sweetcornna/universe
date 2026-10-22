package io.github.sweetcornna.universe.core.ecology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.ecology.Companion.Mood;

class CompanionTest {
    @Test
    void tamedStartsHalfTrustedAndFull() {
        Companion companion = Companion.tamed();
        assertEquals(Companion.TAME_TRUST, companion.trust());
        assertEquals(Companion.FULL, companion.satiety());
        assertFalse(companion.hungry(), "刚驯服的吃饱了");
    }

    @Test
    void trustGainIsCappedPerDay() {
        Companion companion = Companion.tamed();
        long day = 10L * Companion.DAY;
        assertEquals(5, companion.feed(day + 100));
        assertEquals(5, companion.feed(day + 200));
        assertEquals(0, companion.feed(day + 300), "一天最多涨 10");
        assertEquals(60, companion.trust());
        // 第二天又能涨
        assertEquals(5, companion.feed(day + Companion.DAY));
        assertEquals(65, companion.trust());
    }

    @Test
    void trustStopsAtHundred() {
        Companion companion = Companion.tamed();
        for (int day = 0; day < 20; day++) {
            companion.feed((long) day * Companion.DAY);
            companion.feed((long) day * Companion.DAY + 1);
        }
        assertEquals(Companion.MAX_TRUST, companion.trust());
        assertEquals(0, companion.feed(30L * Companion.DAY));
    }

    @Test
    void satietyEmptiesInTwoDays() {
        Companion companion = Companion.tamed();
        for (int i = 0; i < Companion.DAY; i++) {
            companion.tick(true);
        }
        assertEquals(0.5, companion.satietyShare(), 1e-9);
        assertTrue(companion.hungry());
        for (int i = 0; i < Companion.DAY; i++) {
            companion.tick(true);
        }
        assertEquals(0, companion.satiety());
        // 喂一口补一半，补不过满格
        companion.feed(0);
        assertEquals(Companion.MEAL, companion.satiety());
        companion.feed(1);
        companion.feed(2);
        assertEquals(Companion.FULL, companion.satiety());
    }

    @Test
    void starvingLosesTrustSlowly() {
        Companion companion = Companion.restore(50, 0, 0, 0, 0, 0, 0);
        for (int i = 0; i < Companion.DAY; i++) {
            companion.tick(true);
        }
        assertEquals(40, companion.trust(), "饿一天掉 10 点信任");
        companion.feed(0);
        for (int i = 0; i < Companion.STARVE_INTERVAL; i++) {
            companion.tick(true);
        }
        assertEquals(45, companion.trust(), "喂了一口 +5，之后就不掉了");
    }

    @Test
    void moodFollowsSatietyAndTrust() {
        assertEquals(Mood.CALM, Companion.tamed().mood(), "刚驯服：吃饱了，信任一般");
        assertEquals(Mood.HAPPY, Companion.restore(80, Companion.FULL, 0, 0, 0, 0, 0).mood());
        assertEquals(Mood.SAD, Companion.restore(80, Companion.FULL / 10, 0, 0, 0, 0, 0).mood(), "饿了");
        assertEquals(Mood.SAD, Companion.restore(20, Companion.FULL, 0, 0, 0, 0, 0).mood(), "不信任");
        assertEquals(Mood.CALM, Companion.restore(80, Companion.FULL / 3, 0, 0, 0, 0, 0).mood(), "不太饱");
    }

    @Test
    void laysEveryTwoDaysWhenFedAndTrusting() {
        // 一直饱着：早晚各喂一口
        Companion companion = Companion.tamed();
        int eggs = 0;
        for (int day = 0; day < 7; day++) {
            for (int i = 0; i < Companion.DAY; i++) {
                if (i % (Companion.DAY / 2) == 0) {
                    companion.feed((long) day * Companion.DAY + i);
                }
                if (companion.tick(true)) {
                    eggs++;
                }
            }
        }
        assertEquals(3, eggs, "七天下三个");
    }

    @Test
    void noEggsWhenHungryDistrustfulOrYoung() {
        // 不喂：饱食过半只撑一天，攒不满两天
        Companion hungry = Companion.tamed();
        for (int i = 0; i < 4 * Companion.DAY; i++) {
            assertFalse(hungry.tick(true));
        }
        Companion distrustful = Companion.restore(40, Companion.FULL, 0, 0, 0, 0, 0);
        assertFalse(distrustful.canLay());
        Companion young = Companion.tamed();
        for (int day = 0; day < 3; day++) {
            young.feed((long) day * Companion.DAY);
            for (int i = 0; i < Companion.DAY; i++) {
                assertFalse(young.tick(false));
            }
        }
        assertEquals(0, young.layProgress());
    }

    @Test
    void digsOncePerDay() {
        Companion companion = Companion.tamed();
        assertTrue(companion.canDig(1000));
        companion.dig(1000);
        assertFalse(companion.canDig(1000 + Companion.DAY - 1));
        assertEquals(1, companion.digWait(1000 + Companion.DAY - 1));
        assertTrue(companion.canDig(1000 + Companion.DAY));
        assertEquals(0, companion.digWait(1000 + Companion.DAY));
    }

    @Test
    void youngGrowFromHalfToFull() {
        assertEquals(0.5, Companion.youngScale(0), 1e-9);
        assertEquals(0.75, Companion.youngScale(Companion.GROW_TIME / 2), 1e-9);
        assertEquals(1, Companion.youngScale(Companion.GROW_TIME), 1e-9);
        assertEquals(1, Companion.youngScale(Companion.GROW_TIME * 3), 1e-9);
    }

    @Test
    void restoreClampsBadValues() {
        Companion companion = Companion.restore(250, -5, 0, 99, -1, 0, 0);
        assertEquals(Companion.MAX_TRUST, companion.trust());
        assertEquals(0, companion.satiety());
        assertEquals(Companion.DAILY_TRUST, companion.trustToday());
        assertEquals(0, companion.layProgress());
    }
}
