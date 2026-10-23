package io.github.sweetcornna.universe.core.survival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.galaxy.Hazard;
import io.github.sweetcornna.universe.core.procgen.Colors;

/** 酸液（ADR-0030 §一）：泡在酸里、淋酸雨时的毒性防护，和物品的腐蚀。 */
class AcidTest {
    /** 有毒性的星球上，露天。 */
    private static final Environment TOXIC = new Environment(false, Map.of(Hazard.TOXIC, 1.0), false, 0, false, false, false);
    /** 没有毒性的星球（玩家倒的酸、酸雨）。 */
    private static final Environment CLEAN = new Environment(false, Map.of(), false, 0, false, false, false);

    private static int ticksUntil(LifeSupport support, Environment env, Suit suit, Predicate<LifeSupport> done) {
        for (int tick = 1; tick < 100_000; tick++) {
            support.tick(env, suit, false, true);
            if (done.test(support)) {
                return tick;
            }
        }
        throw new AssertionError("never");
    }

    private static int toxicEmpty(Environment env, Suit suit) {
        return ticksUntil(new LifeSupport(), env, suit, s -> s.protection(Hazard.TOXIC) == 0);
    }

    @Test
    void immersionDrainsToxicProtectionFiveTimesFaster() {
        int fog = toxicEmpty(TOXIC, Suit.FULL);
        int acid = toxicEmpty(TOXIC.withAcid(true, false), Suit.FULL);
        assertEquals(5 * 60 * 20, fog, 2);
        // 毒雾 1 加酸液 4：一分钟
        assertEquals(60 * 20, acid, 2);
    }

    @Test
    void acidPouredOnACleanPlanetStillBurns() {
        Environment env = CLEAN.withAcid(true, false);
        assertTrue(env.exposedTo(Hazard.TOXIC));
        assertFalse(CLEAN.exposedTo(Hazard.TOXIC));
        assertEquals(75 * 20, toxicEmpty(env, Suit.FULL), 2);
    }

    @Test
    void shelterDoesNotHelpInAcid() {
        Environment sheltered = new Environment(false, Map.of(Hazard.TOXIC, 1.0), true, 0, false, false, false);
        LifeSupport support = new LifeSupport();
        for (int i = 0; i < 600; i++) {
            support.tick(sheltered, Suit.FULL, false, true);
        }
        assertEquals(LifeSupport.HAZARD_MAX, support.protection(Hazard.TOXIC), 0);
        // 洞里的酸潭：照样掉，而且不算外面的风暴
        Environment pool = new Environment(false, Map.of(Hazard.TOXIC, 1.0), true, 2, false, false, false).withAcid(true, false);
        assertEquals(60 * 20, toxicEmpty(pool, Suit.FULL), 2);
    }

    @Test
    void shipCabinKeepsAcidOut() {
        Environment cabin = new Environment(false, Map.of(Hazard.TOXIC, 1.0), false, 0, false, false, true).withAcid(true, true);
        assertFalse(cabin.exposedTo(Hazard.TOXIC));
        LifeSupport support = new LifeSupport();
        for (int i = 0; i < 2000; i++) {
            assertFalse(support.tick(cabin, Suit.NONE, false, true).any());
        }
        assertEquals(LifeSupport.HAZARD_MAX, support.protection(Hazard.TOXIC), 0);
    }

    @Test
    void bareSkinBurnsAtOnceAndFasterThanFog() {
        LifeSupport support = new LifeSupport();
        Environment env = TOXIC.withAcid(true, false);
        float total = 0;
        int hits = 0;
        for (int tick = 0; tick < 100; tick++) {
            LifeSupport.Damage damage = support.tick(env, Suit.NONE, false, true);
            Float toxic = damage.hazards().get(Hazard.TOXIC);
            if (toxic != null) {
                if (hits == 0) {
                    assertEquals(0, tick, "第一刻就该灼伤");
                }
                hits++;
                total += toxic;
            }
        }
        assertEquals(0, support.protection(Hazard.TOXIC), 0);
        // 每秒 2 点，5 秒 5 次
        assertEquals(5, hits);
        assertEquals(5 * Acid.BURN_DAMAGE, total, 0.001);
        assertTrue(Acid.BURN_DAMAGE / Acid.BURN_INTERVAL > LifeSupport.HAZARD_DAMAGE / 40);
    }

    @Test
    void acidRainAddsToToxicFog() {
        Environment rain = TOXIC.withAcid(false, true);
        assertEquals(1.5, rain.severity(Hazard.TOXIC), 1e-9);
        assertTrue(toxicEmpty(rain, Suit.FULL) < toxicEmpty(TOXIC, Suit.FULL));
        // 没有毒性的星球淋到酸雨也掉
        Environment cleanRain = CLEAN.withAcid(false, true);
        assertTrue(cleanRain.exposedTo(Hazard.TOXIC));
        assertEquals(Acid.RAIN_SEVERITY, cleanRain.severity(Hazard.TOXIC), 1e-9);
        // 别的危害不受影响
        assertEquals(0, rain.severity(Hazard.COLD), 0);
    }

    @Test
    void withAcidKeepsTheRestAndPressure() {
        Environment deep = TOXIC.withPressure(true).withAcid(true, false);
        assertTrue(deep.pressure() && deep.acid() && !deep.acidRain());
        assertEquals(TOXIC.withPressure(true), deep.withAcid(false, false));
        assertFalse(Environment.SAFE.acid() || Environment.SAFE.acidRain());
    }

    @Test
    void itemsCorrodeAfterADelay() {
        assertEquals(0, Acid.corroded(64, 1));
        assertEquals(0, Acid.corroded(64, Acid.CORRODE_DELAY - 1));
        assertEquals(4, Acid.corroded(64, Acid.CORRODE_DELAY));
        assertEquals(0, Acid.corroded(64, Acid.CORRODE_DELAY + 1));
        assertEquals(1, Acid.corroded(1, Acid.CORRODE_DELAY));
        assertEquals(0, Acid.corroded(0, Acid.CORRODE_DELAY));
        // 一个两秒蚀掉，一组大约一分钟
        int count = 64;
        int ticks = 0;
        while (count > 0) {
            ticks++;
            count -= Acid.corroded(count, ticks);
        }
        assertTrue(ticks > 40 * 20 && ticks < 80 * 20, "一组蚀光用了 " + ticks + " 刻");
    }

    @Test
    void colourLeansYellowGreen() {
        int color = Acid.color(-1);
        assertEquals(Acid.DEFAULT_COLOR, color);
        // 紫色的液体色也被往黄绿拉
        int purple = 0x8030C0;
        int tinted = Acid.color(purple);
        assertTrue(Colors.green(tinted) > Colors.green(purple));
        assertTrue(Colors.blue(tinted) < Colors.blue(purple));
    }
}
