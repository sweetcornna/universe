package io.github.sweetcornna.universe.core.civ;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.mission.LandmarkTarget;
import io.github.sweetcornna.universe.core.mission.Mission;
import io.github.sweetcornna.universe.core.mission.MissionBoard;
import io.github.sweetcornna.universe.core.mission.MissionKind;
import io.github.sweetcornna.universe.core.tech.Grade;
import io.github.sweetcornna.universe.core.trade.Economy;
import io.github.sweetcornna.universe.core.trade.Goods;
import io.github.sweetcornna.universe.core.trade.TradeCategory;

class GuildMissionsTest {
    private static final Economy MINING = new Economy(Economy.Type.MINING, Economy.Wealth.AVERAGE);
    private static final List<Goods> CATALOG = List.of(
            Goods.of("universe:circuit_board", 20, TradeCategory.COMPONENT),
            Goods.of("universe:gear", 8, TradeCategory.COMPONENT),
            Goods.of("minecraft:iron_ingot", 5, TradeCategory.REFINED));
    private static final List<LandmarkTarget> TARGETS = List.of(
            new LandmarkTarget("s3/p1", "observatory", 120, -340, 362),
            new LandmarkTarget("s3/p2/m0", "knowledge_stone", -40, 60, 72));

    private static List<Mission> envoy(Guild guild, long day, double factor, List<LandmarkTarget> targets, int species) {
        return MissionBoard.envoyOffers(42, 3, MINING, day, CATALOG, species, GuildMissions.kinds(guild), guild.id(), factor, () -> targets);
    }

    @Test
    void everyKindBelongsToOneGuild() {
        Set<MissionKind> seen = EnumSet.noneOf(MissionKind.class);
        for (Guild guild : Guild.values()) {
            for (MissionKind kind : GuildMissions.kinds(guild)) {
                assertEquals(guild, GuildMissions.guild(kind));
                assertTrue(seen.add(kind), kind + " 归了两个公会");
            }
        }
        assertEquals(EnumSet.allOf(MissionKind.class), seen);
        assertEquals(Guild.MERCHANTS, GuildMissions.guild(MissionKind.DELIVERY));
        assertEquals(Guild.MERCENARIES, GuildMissions.guild(MissionKind.SENTINELS));
        assertEquals(Guild.EXPLORERS, GuildMissions.guild(MissionKind.LANDMARK));
    }

    @Test
    void envoyGuildIsFixedPerSeedAndCoversAll() {
        Set<Guild> guilds = EnumSet.noneOf(Guild.class);
        for (long seed = 0; seed < 60; seed++) {
            assertEquals(GuildMissions.envoyGuild(seed * 7919), GuildMissions.envoyGuild(seed * 7919));
            guilds.add(GuildMissions.envoyGuild(seed * 7919));
        }
        assertEquals(EnumSet.allOf(Guild.class), guilds);
    }

    @Test
    void envoyOffersAreFixedForTheDay() {
        for (Guild guild : Guild.values()) {
            List<Mission> a = envoy(guild, 10, 1, TARGETS, 6);
            assertEquals(MissionBoard.ENVOY_OFFERS, a.size());
            assertEquals(a, envoy(guild, 10, 1, TARGETS, 6));
            for (int i = 0; i < a.size(); i++) {
                Mission mission = a.get(i);
                assertEquals(MissionBoard.ENVOY_SLOT + i, mission.slot());
                assertEquals(guild, GuildMissions.guild(mission.kind()));
                assertEquals(3, mission.system());
                assertEquals(10, mission.day());
            }
            // 两种都出得了时一样一个
            assertNotEquals(a.get(0).kind(), a.get(1).kind(), guild.id());
        }
        // 换一天换一批，和任务板的 id 不重
        Set<String> ids = new HashSet<>();
        for (long day = 0; day < 20; day++) {
            for (Mission mission : envoy(Guild.MERCENARIES, day, 1, TARGETS, 6)) {
                assertTrue(ids.add(mission.id()));
            }
            for (Mission mission : MissionBoard.offers(42, 3, MINING, day, CATALOG, 6)) {
                assertFalse(ids.contains(mission.id()));
            }
        }
    }

    @Test
    void higherRankPaysMoreForTheSameJob() {
        for (long day = 0; day < 30; day++) {
            List<Mission> novice = envoy(Guild.EXPLORERS, day, GuildMissions.rewardFactor(Standing.Tier.STRANGER), TARGETS, 6);
            List<Mission> master = envoy(Guild.EXPLORERS, day, GuildMissions.rewardFactor(Standing.Tier.KINDRED), TARGETS, 6);
            for (int i = 0; i < novice.size(); i++) {
                Mission low = novice.get(i);
                Mission high = master.get(i);
                assertEquals(low.kind(), high.kind());
                assertEquals(low.count(), high.count());
                assertEquals(low.item(), high.item());
                assertEquals(low.bonus(), high.bonus());
                // 两边各取整到十位，差不过 15
                assertEquals(low.reward() * 1.75, high.reward(), 15, low + " / " + high);
            }
        }
        double last = 0;
        for (Standing.Tier tier : Standing.Tier.values()) {
            if (tier != Standing.Tier.HOSTILE) {
                assertTrue(GuildMissions.rewardFactor(tier) > last || tier == Standing.Tier.STRANGER);
                last = GuildMissions.rewardFactor(tier);
            }
        }
        assertFalse(GuildMissions.serves(Standing.Tier.HOSTILE));
        assertTrue(GuildMissions.serves(Standing.Tier.STRANGER));
    }

    @Test
    void newKindsHaveTargetsAndSaneRewards() {
        Set<MissionKind> kinds = EnumSet.noneOf(MissionKind.class);
        for (long day = 0; day < 40; day++) {
            for (Guild guild : List.of(Guild.MERCENARIES, Guild.EXPLORERS)) {
                for (Mission mission : envoy(guild, day, 1, TARGETS, 6)) {
                    kinds.add(mission.kind());
                    switch (mission.kind()) {
                        case SENTINELS -> {
                            assertTrue(mission.count() >= 3 && mission.count() <= 8, mission.toString());
                            assertEquals(mission.count() * 160, mission.reward(), 10);
                        }
                        case LANDMARK -> {
                            Optional<LandmarkTarget> target = LandmarkTarget.parse(mission.item());
                            assertTrue(target.isPresent() && TARGETS.contains(target.get()), mission.item());
                            assertEquals(1, mission.count());
                            assertEquals(350 + target.get().distance() * 0.5, mission.reward(), 10);
                        }
                        default -> {
                        }
                    }
                    assertTrue(mission.reward() >= 10 && mission.reward() < 3000, mission.toString());
                }
            }
        }
        assertTrue(kinds.containsAll(List.of(MissionKind.SENTINELS, MissionKind.LANDMARK, MissionKind.BOUNTY, MissionKind.SURVEY)), kinds.toString());
        // 任务板不出新的两种
        for (long day = 0; day < 60; day++) {
            for (Mission mission : MissionBoard.offers(42, 3, MINING, day, CATALOG, 6)) {
                assertTrue(mission.kind() != MissionKind.SENTINELS && mission.kind() != MissionKind.LANDMARK);
            }
        }
    }

    @Test
    void missingTargetsFallBackToTheOtherKind() {
        for (long day = 0; day < 20; day++) {
            List<Mission> offers = envoy(Guild.EXPLORERS, day, 1, List.of(), 6);
            assertEquals(2, offers.size());
            offers.forEach(mission -> assertEquals(MissionKind.SURVEY, mission.kind()));
            List<Mission> noSpecies = envoy(Guild.EXPLORERS, day, 1, TARGETS, 0);
            noSpecies.forEach(mission -> assertEquals(MissionKind.LANDMARK, mission.kind()));
        }
        assertTrue(envoy(Guild.EXPLORERS, 1, 1, List.of(), 0).isEmpty());
        // 不用出地标任务的公会不去算目标
        MissionBoard.envoyOffers(42, 3, MINING, 1, CATALOG, 6, GuildMissions.kinds(Guild.MERCHANTS), "merchants", 1, () -> {
            throw new AssertionError("商会不该算地标");
        });
    }

    @Test
    void landmarkTargetRoundTrips() {
        for (LandmarkTarget target : TARGETS) {
            assertEquals(Optional.of(target), LandmarkTarget.parse(target.encode()));
        }
        assertTrue(LandmarkTarget.parse("").isEmpty());
        assertTrue(LandmarkTarget.parse("minecraft:iron_ingot").isEmpty());
        assertTrue(LandmarkTarget.parse("s1/p1|observatory|x|2|3").isEmpty());
        LandmarkTarget target = TARGETS.getFirst();
        assertTrue(target.reached(120.5 + 15, -339.5));
        assertFalse(target.reached(120.5 + 17, -339.5));
    }

    @Test
    void giftsGrowWithRank() {
        for (Guild guild : Guild.values()) {
            assertTrue(GuildMissions.gift(guild, Standing.Tier.STRANGER).isEmpty());
            assertTrue(GuildMissions.gift(guild, Standing.Tier.HOSTILE).isEmpty());
            GuildMissions.Gift member = GuildMissions.gift(guild, Standing.Tier.ACQUAINTED).orElseThrow();
            assertTrue(member.modules().isEmpty());
            long credits = member.credits();
            Grade grade = Grade.C;
            for (Standing.Tier tier : List.of(Standing.Tier.REGULAR, Standing.Tier.FRIEND, Standing.Tier.KINDRED)) {
                GuildMissions.Gift gift = GuildMissions.gift(guild, tier).orElseThrow();
                assertTrue(gift.credits() > credits);
                assertFalse(gift.modules().isEmpty());
                assertTrue(gift.grade().compareTo(grade) > 0);
                credits = gift.credits();
                grade = gift.grade();
            }
            assertEquals(Grade.S, grade);
        }
        Map<Guild, GuildMissions.Gift> veteran = Map.of(
                Guild.MERCHANTS, GuildMissions.gift(Guild.MERCHANTS, Standing.Tier.REGULAR).orElseThrow(),
                Guild.EXPLORERS, GuildMissions.gift(Guild.EXPLORERS, Standing.Tier.REGULAR).orElseThrow(),
                Guild.MERCENARIES, GuildMissions.gift(Guild.MERCENARIES, Standing.Tier.REGULAR).orElseThrow());
        assertTrue(veteran.get(Guild.MERCHANTS).credits() > veteran.get(Guild.MERCENARIES).credits());
        assertTrue(veteran.get(Guild.EXPLORERS).navigationData() > veteran.get(Guild.MERCENARIES).navigationData());
    }

    @Test
    void giftsClaimOncePerRank() {
        CivState state = new CivState();
        assertFalse(state.giftClaimable(Guild.MERCHANTS));
        assertTrue(state.claimGift(Guild.MERCHANTS).isEmpty());
        state.addStanding(Guild.MERCHANTS, 12);
        assertTrue(state.giftClaimable(Guild.MERCHANTS));
        assertEquals(Optional.of(Standing.Tier.ACQUAINTED), state.claimGift(Guild.MERCHANTS));
        assertFalse(state.giftClaimable(Guild.MERCHANTS));
        assertTrue(state.claimGift(Guild.MERCHANTS).isEmpty());
        // 跳了两档能连着领两次
        state.addStanding(Guild.MERCHANTS, 40);
        assertEquals(Optional.of(Standing.Tier.REGULAR), state.claimGift(Guild.MERCHANTS));
        assertEquals(Optional.of(Standing.Tier.FRIEND), state.claimGift(Guild.MERCHANTS));
        assertTrue(state.claimGift(Guild.MERCHANTS).isEmpty());
        // 掉档再升回来不再给
        state.addStanding(Guild.MERCHANTS, -30);
        state.addStanding(Guild.MERCHANTS, 30);
        assertFalse(state.giftClaimable(Guild.MERCHANTS));
        // 别的公会不受影响；读档后保留
        assertFalse(state.giftClaimable(Guild.EXPLORERS));
        CivState restored = CivState.restore(state.races(), state.guilds(), state.wordMasks(), state.stones(), state.daily(), state.guildGifts());
        assertEquals(state.guildGifts()[Guild.MERCHANTS.ordinal()], restored.guildGifts()[Guild.MERCHANTS.ordinal()]);
        CivState old = CivState.restore(state.races(), state.guilds(), state.wordMasks(), state.stones(), state.daily());
        assertEquals(Standing.Tier.STRANGER.ordinal(), old.guildGifts()[Guild.EXPLORERS.ordinal()]);
    }
}
