package io.github.sweetcornna.universe.core.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.civ.Race;
import io.github.sweetcornna.universe.core.procgen.Rng;

/** 聚落的施工、每天的结算、决定和海盗来袭（ADR-0034 §二）。 */
class SettlementTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static Settlement claimed(Race race) {
        Settlement s = new Settlement("universe:test", 42, 0, 64, 0, 0, 7, race);
        s.claim(PLAYER, "Dev", 0);
        return s;
    }

    /** 把第 {@code index} 块地基上的 {@code type} 一路盖完、开张。 */
    private static void build(Settlement s, int index, BuildingType type, long[] now) {
        assertTrue(s.start(index, type), "开不了工：" + type);
        Plot plot = s.plot(index);
        for (int stage = 0; stage < BuildingType.STAGES; stage++) {
            assertEquals(Plot.Phase.DELIVER, plot.phase());
            for (Cost cost : plot.costs()) {
                plot.deliver(cost, cost.count(), now[0]);
            }
            assertEquals(Plot.Phase.WAIT, plot.phase());
            now[0] += SettlementRules.STAGE_WAIT;
            s.tickPlots(now[0]);
        }
        assertEquals(Plot.Phase.OPENING, plot.phase());
        assertTrue(s.open(index));
        assertEquals(Plot.Phase.ACTIVE, plot.phase());
    }

    @Test
    void officeFirstAndOnlyOne() {
        Settlement s = claimed(Race.TRADER);
        assertFalse(s.canStart(BuildingType.FARM));
        assertTrue(s.start(0, BuildingType.OFFICE));
        assertFalse(s.start(1, BuildingType.OFFICE));
        assertFalse(s.start(1, BuildingType.FARM), "办公室还没开张");
        long[] now = {0};
        s.plot(0).clear();
        build(s, 0, BuildingType.OFFICE, now);
        assertTrue(s.start(1, BuildingType.FARM));
        assertFalse(s.start(1, BuildingType.SALOON), "地基已经占了");
    }

    @Test
    void stagesWaitBeforeFinishing() {
        Settlement s = claimed(Race.SCHOLAR);
        s.start(2, BuildingType.OFFICE);
        Plot plot = s.plot(2);
        Cost stone = plot.costs().getFirst();
        assertEquals(10, plot.deliver(stone, 10, 0));
        assertEquals(stone.count() - 10, plot.missing(stone));
        assertEquals(stone.count() - 10, plot.deliver(stone, 999, 100), "多交的不收");
        assertEquals(Plot.Phase.WAIT, plot.phase());
        s.tickPlots(100 + SettlementRules.STAGE_WAIT - 1);
        assertEquals(0, plot.stage());
        assertEquals(0.5, plot.progress(100 + SettlementRules.STAGE_WAIT / 2), 1e-9);
        s.tickPlots(100 + SettlementRules.STAGE_WAIT);
        assertEquals(1, plot.stage());
        assertEquals(Plot.Phase.DELIVER, plot.phase());
        assertEquals(BuildingType.OFFICE.stageCost(1), plot.costs());
    }

    @Test
    void upgradeMultipliesEffects() {
        Settlement s = claimed(Race.TRADER);
        long[] now = {0};
        build(s, 0, BuildingType.OFFICE, now);
        build(s, 1, BuildingType.HABITATION, now);
        assertEquals(SettlementRules.BASE_HOUSING + 3, s.housing());
        Plot home = s.plot(1);
        assertTrue(home.startUpgrade());
        for (Cost cost : home.costs()) {
            home.deliver(cost, cost.count(), now[0]);
        }
        assertTrue(home.active(), "升级时照常起作用");
        s.tickPlots(now[0] + SettlementRules.STAGE_WAIT);
        assertEquals(1, home.grade());
        assertEquals(SettlementRules.BASE_HOUSING + 4, s.housing(), "3 × 1.5 取整");
    }

    @Test
    void dayProducesPaysUpkeepAndRecordsDebt() {
        Settlement s = claimed(Race.TRADER);
        long[] now = {0};
        build(s, 0, BuildingType.OFFICE, now);
        build(s, 1, BuildingType.WORKSHOP, now);
        double productivity = s.productivity();
        Settlement.DayReport report = s.advanceDay(1, new Rng(1));
        assertEquals(productivity / 10, report.units(), 1e-9);
        assertEquals(2L * SettlementRules.UPKEEP, report.upkeep());
        // 信用点产品一份值 80，维护费 80 刚好折掉一份
        assertEquals(productivity / 10 - 1, s.reserve(), 1e-9);
        assertEquals(0, s.debt());
        assertTrue(report.decision() != null, "有办公室就有决定");

        Settlement poor = claimed(Race.TRADER);
        long[] t = {0};
        build(poor, 0, BuildingType.OFFICE, t);
        poor.debugSet(10, 0, 0);
        double before = poor.happiness();
        poor.advanceDay(1, new Rng(2));
        assertTrue(poor.debt() > 0, "储备不够记欠款");
        assertTrue(poor.happiness() < before + SettlementRules.drift(before, poor.happinessTarget()), "有欠款幸福掉 5");
    }

    @Test
    void reserveStopsAtTheCap() {
        Settlement s = claimed(Race.SCHOLAR);
        s.debugSet(100, 0, 1000);
        assertEquals(s.reserveCap(), s.reserve(), 1e-9);
        s.advanceDay(1, new Rng(3));
        assertTrue(s.reserve() <= s.reserveCap());
    }

    @Test
    void populationFollowsHappiness() {
        Settlement s = claimed(Race.WARDEN);
        s.debugSet(10, 0, 0);
        int before = s.population();
        s.advanceDay(1, new Rng(4));
        assertEquals(before - 1, s.population());
        s.debugSet(95, 0, 0);
        int now = s.population();
        s.advanceDay(2, new Rng(5));
        assertEquals(now + 1, s.population(), "有空房");
    }

    @Test
    void threatRisesAndTowersPushItDown() {
        Settlement s = claimed(Race.WARDEN);
        assertTrue(s.threatDaily() > 0);
        long[] now = {0};
        build(s, 0, BuildingType.OFFICE, now);
        build(s, 1, BuildingType.WATCHTOWER, now);
        build(s, 2, BuildingType.TURRET, now);
        Settlement bare = claimed(Race.WARDEN);
        assertTrue(s.threatDaily() < bare.threatDaily() - 7);
    }

    @Test
    void raidsWaitForTheGraceDays() {
        Settlement s = claimed(Race.TRADER);
        s.debugSet(50, 100, 0);
        s.advanceDay(1, new Rng(6));
        assertEquals(-1, s.raidAt(), "头两天不来");
        s.advanceDay(2, new Rng(6));
        assertEquals(SettlementRules.raidTime(2), s.raidAt(), "威胁满了一定来，入夜开始");
    }

    @Test
    void defenseDecidesAbstractRaids() {
        Settlement weak = claimed(Race.TRADER);
        weak.debugSet(20, 100, 50);
        Settlement.RaidOutcome lost = weak.resolveRaid(new Rng(7));
        assertFalse(lost.won());
        assertEquals(15, lost.stolen(), 1e-9);
        assertEquals(35, weak.reserve(), 1e-9);

        Settlement strong = claimed(Race.WARDEN);
        long[] now = {0};
        build(strong, 0, BuildingType.OFFICE, now);
        for (int i = 1; i <= 4; i++) {
            build(strong, i, BuildingType.TURRET, now);
        }
        strong.debugSet(80, 30, 0);
        Settlement.RaidOutcome won = strong.resolveRaid(new Rng(8));
        assertTrue(won.won());
        assertEquals(0, strong.threat(), 1e-9);
        assertEquals(SettlementRules.raidReward(won.fighters()), won.reward());
    }

    @Test
    void lostRaidDamagesTwoBuildingsUntilRepaired() {
        Settlement s = claimed(Race.SCHOLAR);
        long[] now = {0};
        build(s, 0, BuildingType.OFFICE, now);
        build(s, 1, BuildingType.HABITATION, now);
        build(s, 2, BuildingType.SALOON, now);
        Settlement.RaidOutcome outcome = s.finishRaid(false, 4, new Rng(9));
        assertEquals(2, outcome.damaged().size());
        int broken = outcome.damaged().getFirst();
        Plot plot = s.plot(broken);
        assertFalse(plot.active());
        assertEquals(Plot.Phase.DAMAGED, plot.phase());
        for (Cost cost : plot.costs()) {
            plot.deliver(cost, cost.count(), now[0]);
        }
        assertTrue(plot.active(), "修理不用等");
    }

    @Test
    void decisionsChangeNumbers() {
        Settlement s = claimed(Race.TRADER);
        long[] now = {0};
        build(s, 0, BuildingType.OFFICE, now);
        s.debugSet(50, 50, 30);
        s.forceDecision("festival");
        assertTrue(s.decide(0, 0).isPresent());
        assertEquals(62, s.happiness(), 1e-9);
        assertEquals(10, s.reserve(), 1e-9);
        assertTrue(s.decision().isEmpty());

        s.forceDecision("classroom");
        assertTrue(s.decide(0, 999).isEmpty(), "信用点不够");
        Decisions.Effect effect = s.decide(0, 5000).orElseThrow();
        assertEquals(-1000, effect.credits());
        assertEquals(5, s.boost());
        s.advanceDay(s.day() + 5, new Rng(10));
        assertEquals(0, s.boost(), "五天以后失效");
    }

    @Test
    void productsFollowRaceAndBuildings() {
        Settlement s = claimed(Race.WARDEN);
        assertTrue(s.products().contains(Product.STEEL));
        assertFalse(s.products().contains(Product.CREDITS));
        assertFalse(s.setProduct(Product.GEARS));
        long[] now = {0};
        build(s, 0, BuildingType.OFFICE, now);
        build(s, 1, BuildingType.WORKSHOP, now);
        assertTrue(s.setProduct(Product.GEARS));
        s.debugSet(50, 0, 7.5);
        assertEquals(7, s.withdraw(100));
        assertEquals(0.5, s.reserve(), 1e-9);
        // 工坊坏了就出种族默认的
        s.plot(1).damage();
        assertEquals(Product.STEEL, s.effectiveProduct());
    }

    @Test
    void resignKeepsBuildings() {
        Settlement s = claimed(Race.TRADER);
        long[] now = {0};
        build(s, 0, BuildingType.OFFICE, now);
        s.resign();
        assertFalse(s.owned());
        assertTrue(s.has(BuildingType.OFFICE));
        assertNull(s.advanceDay(1, new Rng(11)).decision(), "没有总督不结算");
    }
}
