package io.github.sweetcornna.universe.core.civ;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.procgen.NameGenerator;

class CivTest {
    @Test
    void dominantRaceSplitsRoughlyInThirdsAndHomeIsTraders() {
        Galaxy galaxy = GalaxyGenerator.generate(20261022L, GalaxyGenerator.MAX_SYSTEM_COUNT);
        assertEquals(Race.TRADER, Race.dominant(galaxy.systems().getFirst()));
        Map<Race, Integer> counts = new EnumMap<>(Race.class);
        for (StarSystem system : galaxy.systems()) {
            counts.merge(Race.dominant(system), 1, Integer::sum);
        }
        for (Race race : Race.values()) {
            double share = counts.getOrDefault(race, 0) / (double) galaxy.systems().size();
            assertTrue(share > 0.28 && share < 0.39, race + ": " + share);
        }
    }

    @Test
    void standingTiersAndClamp() {
        assertEquals(Standing.Tier.HOSTILE, Standing.tier(-6));
        assertEquals(Standing.Tier.STRANGER, Standing.tier(-5));
        assertEquals(Standing.Tier.STRANGER, Standing.tier(0));
        assertEquals(Standing.Tier.STRANGER, Standing.tier(9));
        assertEquals(Standing.Tier.ACQUAINTED, Standing.tier(10));
        assertEquals(Standing.Tier.REGULAR, Standing.tier(25));
        assertEquals(Standing.Tier.FRIEND, Standing.tier(45));
        assertEquals(Standing.Tier.KINDRED, Standing.tier(70));
        assertEquals(Standing.Tier.KINDRED, Standing.tier(100));
        assertEquals(Standing.MIN, Standing.clamp(-500));
        assertEquals(Standing.MAX, Standing.clamp(500));
        assertEquals(Standing.MAX, Standing.Tier.KINDRED.next());
        assertFalse(Standing.canTalk(Standing.Tier.HOSTILE));
        assertFalse(Standing.canAskWord(Standing.Tier.STRANGER));
        assertTrue(Standing.canAskWord(Standing.Tier.ACQUAINTED));

        CivState state = new CivState();
        assertFalse(state.addStanding(Race.SCHOLAR, 9));
        assertTrue(state.addStanding(Race.SCHOLAR, 1));
        assertEquals(Standing.Tier.ACQUAINTED, state.tier(Race.SCHOLAR));
        state.addStanding(Race.SCHOLAR, 1000);
        assertEquals(Standing.MAX, state.standing(Race.SCHOLAR));
        assertTrue(state.addStanding(Guild.MERCENARIES, -1000));
        assertEquals(Standing.MIN, state.standing(Guild.MERCENARIES));
        assertEquals(Standing.START, state.standing(Race.TRADER));
    }

    @Test
    void learnOneDailyAndRestore() {
        CivState state = new CivState();
        assertEquals(Lexicon.INNATE, state.knownCount(Race.WARDEN));
        // 已经认识的词里挑不出新的
        assertEquals(-1, state.learnOne(Race.WARDEN, Lexicon.INNATE_MASK, 1));
        long mask = 1L << 20 | 1L << 30;
        int first = state.learnOne(Race.WARDEN, mask, 7);
        assertTrue(first == 20 || first == 30);
        assertEquals(first == 20 ? 30 : 20, state.learnOne(Race.WARDEN, mask, 7));
        assertEquals(-1, state.learnOne(Race.WARDEN, mask, 7));
        assertFalse(state.knows(Race.TRADER, 20));
        // 整张词表学完
        while (state.learnOne(Race.TRADER, -1L, state.knownCount(Race.TRADER)) >= 0) {
            assertTrue(state.knownCount(Race.TRADER) <= Lexicon.SIZE);
        }
        assertEquals(Lexicon.SIZE, state.knownCount(Race.TRADER));

        assertFalse(state.usedToday("talk:a", 5));
        state.markUsed("talk:a", 5);
        assertTrue(state.usedToday("talk:a", 5));
        assertFalse(state.usedToday("talk:a", 6));
        state.markUsed("gift:b", 6);
        assertFalse(state.daily().containsKey("talk:a"));

        assertTrue(state.touchStone("s1/p2:1,2,3"));
        assertFalse(state.touchStone("s1/p2:1,2,3"));

        CivState copy = CivState.restore(state.races(), state.guilds(), state.wordMasks(), state.stones(), state.daily());
        assertEquals(state.knownCount(Race.WARDEN), copy.knownCount(Race.WARDEN));
        assertEquals(state.stones(), copy.stones());
        assertTrue(copy.usedToday("gift:b", 6));
        // 旧档的数组短了，或者数值越界、缺了一开始就认识的词
        CivState old = CivState.restore(new int[] {999}, new int[0], new long[] {0}, List.of(), Map.of());
        assertEquals(Standing.MAX, old.standing(Race.TRADER));
        assertEquals(Standing.START, old.standing(Race.WARDEN));
        assertEquals(Lexicon.INNATE, old.knownCount(Race.TRADER));
    }

    @Test
    void lexiconHas64DistinctConcepts() {
        assertEquals(64, Lexicon.SIZE);
        assertEquals(Lexicon.SIZE, new HashSet<>(Lexicon.CONCEPTS).size());
        for (int i = 0; i < Lexicon.SIZE; i++) {
            assertEquals(i, Lexicon.index(Lexicon.id(i)));
        }
        assertEquals(-1, Lexicon.index("nothing"));
    }

    @Test
    void tongueIsDeterministicUniqueAndStylesDiffer() {
        Set<String> all = new HashSet<>();
        for (Race race : Race.values()) {
            AlienTongue tongue = AlienTongue.of(42, race);
            AlienTongue again = AlienTongue.of(42, race);
            AlienTongue other = AlienTongue.of(43, race);
            Set<String> words = new HashSet<>();
            int changed = 0;
            for (int concept = 0; concept < Lexicon.SIZE; concept++) {
                String word = tongue.word(concept);
                assertEquals(word, again.word(concept));
                assertTrue(word.matches("[a-z]+"), word);
                assertTrue(words.add(word), race + " 重复: " + word);
                all.add(word);
                if (!word.equals(other.word(concept))) {
                    changed++;
                }
                switch (race) {
                    // 辅音元音一个隔一个
                    case TRADER -> assertTrue(word.matches("([^aeiou][aeiou])+"), word);
                    case SCHOLAR -> assertTrue(word.matches(".*[^aeiouy]{2}.*"), word);
                    case WARDEN -> assertTrue(word.matches(".*[aeiou]{2}.*"), word);
                }
            }
            assertTrue(changed > Lexicon.SIZE / 2, "换个世界，词就换了");
            NameGenerator.Name name = tongue.name(99);
            assertEquals(name, again.name(99));
            assertTrue(Character.isUpperCase(name.en().charAt(0)));
            assertFalse(name.zh().isEmpty());
            assertNotEquals(name, tongue.name(100));
        }
        // 三个种族的词几乎不撞
        assertTrue(all.size() > Lexicon.SIZE * 3 - 3);
    }

    @Test
    void exactlyOneRightOptionPerRace() {
        assertEquals(6, Encounters.all().size());
        Set<String> ids = new HashSet<>();
        for (Encounter encounter : Encounters.all()) {
            assertTrue(ids.add(encounter.id()));
            assertTrue(encounter.words().size() >= 3 && encounter.words().size() <= 5, encounter.id());
            assertEquals(encounter.words().size(), Long.bitCount(encounter.wordMask()));
            assertEquals(0, Lexicon.INNATE_MASK & encounter.wordMask(), "题目里的词都要能学");
        }
        Set<List<Value>> orders = new HashSet<>();
        Set<String> picked = new HashSet<>();
        for (long seed = 0; seed < 200; seed++) {
            Encounter encounter = Encounters.pick(seed);
            assertEquals(encounter, Encounters.pick(seed));
            assertEquals(Set.of(Value.values()), Set.copyOf(encounter.options()));
            orders.add(encounter.options());
            picked.add(encounter.id());
            for (Race race : Race.values()) {
                int right = 0;
                for (int option = 0; option < encounter.options().size(); option++) {
                    if (encounter.correct(race, option)) {
                        right++;
                    }
                }
                assertEquals(1, right);
            }
        }
        assertEquals(6, orders.size(), "三个选项的六种顺序都出现过");
        assertEquals(6, picked.size());
    }

    @Test
    void giftsAndRewards() {
        assertTrue(Gifts.liked(Race.TRADER, Gifts.Kind.VALUABLES));
        assertTrue(Gifts.liked(Race.TRADER, Gifts.Kind.FOOD));
        assertTrue(Gifts.liked(Race.SCHOLAR, Gifts.Kind.RELIC));
        assertTrue(Gifts.liked(Race.WARDEN, Gifts.Kind.WEAPON));
        assertFalse(Gifts.liked(Race.WARDEN, Gifts.Kind.FOOD));
        for (Race race : Race.values()) {
            assertFalse(Gifts.liked(race, Gifts.Kind.OTHER));
        }

        Map<Reward.Kind, Integer> kinds = new EnumMap<>(Reward.Kind.class);
        int modules = 0;
        for (long seed = 0; seed < 2000; seed++) {
            for (Race race : Race.values()) {
                Reward reward = Reward.forAnswer(race, seed);
                assertEquals(reward, Reward.forAnswer(race, seed));
                assertTrue(reward.amount() > 0);
                kinds.merge(reward.kind(), 1, Integer::sum);
                switch (reward.kind()) {
                    case CREDITS -> {
                        assertEquals(Race.TRADER, race);
                        assertTrue(reward.amount() >= 200 && reward.amount() <= 600);
                    }
                    case TRADE_GOODS -> assertEquals(Race.TRADER, race);
                    case NAVIGATION_DATA, SURVEY_DATA -> assertEquals(Race.SCHOLAR, race);
                    case TECH_MODULE -> {
                        assertEquals(Race.SCHOLAR, race);
                        modules++;
                    }
                    case SENTINEL_SCRAP, MAGNETIC_COIL, WEAPON_MODULE -> assertEquals(Race.WARDEN, race);
                }
            }
        }
        assertEquals(Reward.Kind.values().length, kinds.size());
        assertTrue(modules > 120 && modules < 280, "技术模块约一成: " + modules);
    }
}
