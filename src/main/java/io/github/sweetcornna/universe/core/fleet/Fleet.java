package io.github.sweetcornna.universe.core.fleet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.tech.Grade;
import io.github.sweetcornna.universe.core.tech.ModuleHost;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.core.tech.TechModule;

/**
 * 一名玩家的舰队（ADR-0024）：货船、雇来的护卫舰、在外面的远征，以及今天接过哪几个远征任务。存档存的就是这个。
 *
 * <p>远征出发时就把每一段遭遇算好（{@link Expedition#resolve}），过程按时间一段段揭开；回来以后在终端里领，
 * 领的时候才把受损、沉没、升级落到护卫舰上，奖励按出发时定下的种子算。
 */
public final class Fleet {
    /**
     * 货船。
     *
     * @param id 编号，也是船舱维度 {@code universe:freighter/<id>} 的编号
     */
    public record Freighter(int id, long seed, Grade grade) {
        public double range() {
            return FreighterRules.range(this.grade);
        }
    }

    /**
     * 一支在外面的远征。
     *
     * @param day    接任务的那一天
     * @param slot   那天的第几个任务
     * @param team   派出去的护卫舰编号
     * @param start  出发的游戏刻
     * @param events 出发时算好的各段遭遇
     * @param seed   领奖励时用的种子
     * @param hive   清剿的是哪个恒星系的黑雾巢穴（{@link HiveSortie}）；普通的远征是 -1，这时 {@code day}、{@code slot} 才有意义
     */
    public record Voyage(long day, int slot, Expedition.Offer offer, List<Integer> team, long start, List<Expedition.Event> events, long seed,
            int hive) {
        public Voyage {
            team = List.copyOf(team);
            events = List.copyOf(events);
        }

        /** 普通的远征。 */
        public Voyage(long day, int slot, Expedition.Offer offer, List<Integer> team, long start, List<Expedition.Event> events, long seed) {
            this(day, slot, offer, team, start, events, seed, -1);
        }

        /** 是清剿巢穴。 */
        public boolean sortie() {
            return this.hive >= 0;
        }

        public long end() {
            return this.start + this.offer.ticks();
        }

        public boolean done(long now) {
            return now >= this.end();
        }

        /** 现在揭开了几段：第 i 段在全程的 (i + 1) / (段数 + 1) 处揭开，回来时全部揭开。 */
        public int revealed(long now) {
            if (this.done(now)) {
                return this.events.size();
            }
            long elapsed = Math.max(0, now - this.start);
            return (int) Math.min(this.events.size(), elapsed * (this.events.size() + 1) / this.offer.ticks());
        }

        /** 0–1 的进度。 */
        public double progress(long now) {
            return Math.clamp((now - this.start) / (double) this.offer.ticks(), 0, 1);
        }
    }

    /**
     * 领回来的结果。
     *
     * @param lost     沉了的护卫舰
     * @param damaged  受损的（不含沉了的）
     * @param promoted 升了级的
     */
    public record Claim(List<Expedition.Reward> rewards, List<Frigate> lost, List<Frigate> damaged, List<Frigate> promoted) {
    }

    /** 不能雇、不能出发的原因。 */
    public enum Problem {
        NO_FREIGHTER, FLEET_FULL, NO_TEAM, TEAM_TOO_BIG, UNKNOWN_FRIGATE, BUSY, DAMAGED, TOO_MANY_VOYAGES, TAKEN
    }

    private @Nullable Freighter freighter;
    private final List<Frigate> frigates = new ArrayList<>();
    private int nextFrigate;
    private final List<Voyage> voyages = new ArrayList<>();
    /** 接过的远征任务："天/序号"。 */
    private final Set<String> taken = new HashSet<>();

    public Fleet() {
    }

    public Fleet(@Nullable Freighter freighter, List<Frigate> frigates, int nextFrigate, List<Voyage> voyages, Set<String> taken) {
        this.freighter = freighter;
        this.frigates.addAll(frigates);
        this.nextFrigate = nextFrigate;
        this.voyages.addAll(voyages);
        this.taken.addAll(taken);
    }

    public Optional<Freighter> freighter() {
        return Optional.ofNullable(this.freighter);
    }

    public void setFreighter(@Nullable Freighter freighter) {
        this.freighter = freighter;
    }

    public List<Frigate> frigates() {
        return Collections.unmodifiableList(this.frigates);
    }

    public int nextFrigate() {
        return this.nextFrigate;
    }

    public List<Voyage> voyages() {
        return Collections.unmodifiableList(this.voyages);
    }

    public Set<String> taken() {
        return Collections.unmodifiableSet(this.taken);
    }

    public @Nullable Frigate frigate(int id) {
        for (Frigate frigate : this.frigates) {
            if (frigate.id() == id) {
                return frigate;
            }
        }
        return null;
    }

    /** 这艘在远征里。 */
    public boolean busy(int id) {
        for (Voyage voyage : this.voyages) {
            if (voyage.team().contains(id)) {
                return true;
            }
        }
        return false;
    }

    /** 停在货船边上的（不在远征里的）。 */
    public List<Frigate> docked() {
        List<Frigate> docked = new ArrayList<>();
        for (Frigate frigate : this.frigates) {
            if (!this.busy(frigate.id())) {
                docked.add(frigate);
            }
        }
        return docked;
    }

    // ---- 雇

    public @Nullable Problem canHire() {
        if (this.freighter == null) {
            return Problem.NO_FREIGHTER;
        }
        return this.frigates.size() >= FreighterRules.MAX_FRIGATES ? Problem.FLEET_FULL : null;
    }

    /** 雇下交易终端上挂着的一艘，换成舰队里的编号。先查 {@link #canHire}。 */
    public Frigate hire(Frigate offer) {
        Frigate hired = new Frigate(this.nextFrigate++, offer.designation(), offer.type(), offer.baseStats(), offer.traits(), offer.baseFuel(), 0,
                false);
        this.frigates.add(hired);
        return hired;
    }

    /** 解散一艘停着的。 */
    public boolean dismiss(int id) {
        Frigate frigate = this.frigate(id);
        if (frigate == null || this.busy(id)) {
            return false;
        }
        return this.frigates.remove(frigate);
    }

    /** 修好一艘受损的（钱由调用方收）。 */
    public boolean repair(int id) {
        Frigate frigate = this.frigate(id);
        if (frigate == null || !frigate.damaged() || this.busy(id)) {
            return false;
        }
        frigate.setDamaged(false);
        return true;
    }

    // ---- 远征

    private static String key(long day, int slot) {
        return day + "/" + slot;
    }

    public boolean taken(long day, int slot) {
        return this.taken.contains(key(day, slot));
    }

    public List<Frigate> team(List<Integer> ids) {
        List<Frigate> team = new ArrayList<>();
        for (int id : ids) {
            Frigate frigate = this.frigate(id);
            if (frigate != null) {
                team.add(frigate);
            }
        }
        return team;
    }

    public @Nullable Problem canLaunch(long day, int slot, List<Integer> team) {
        if (this.freighter == null) {
            return Problem.NO_FREIGHTER;
        }
        if (this.taken(day, slot)) {
            return Problem.TAKEN;
        }
        return this.teamProblem(team);
    }

    /** 能不能派这一队去清剿巢穴：不占每天的远征任务，别的和 {@link #canLaunch} 一样。 */
    public @Nullable Problem canSortie(List<Integer> team) {
        if (this.freighter == null) {
            return Problem.NO_FREIGHTER;
        }
        return this.teamProblem(team);
    }

    private @Nullable Problem teamProblem(List<Integer> team) {
        if (this.voyages.size() >= Expedition.MAX_ACTIVE) {
            return Problem.TOO_MANY_VOYAGES;
        }
        Set<Integer> unique = new LinkedHashSet<>(team);
        if (unique.isEmpty()) {
            return Problem.NO_TEAM;
        }
        if (unique.size() > Expedition.MAX_TEAM) {
            return Problem.TEAM_TOO_BIG;
        }
        for (int id : unique) {
            Frigate frigate = this.frigate(id);
            if (frigate == null) {
                return Problem.UNKNOWN_FRIGATE;
            }
            if (this.busy(id)) {
                return Problem.BUSY;
            }
            if (frigate.damaged()) {
                return Problem.DAMAGED;
            }
        }
        return null;
    }

    /** 出发：先查 {@link #canLaunch}。燃料由调用方收。 */
    public Voyage launch(long day, int slot, Expedition.Offer offer, List<Integer> team, long now, Rng rng) {
        List<Integer> ids = List.copyOf(new LinkedHashSet<>(team));
        List<Expedition.Event> events = Expedition.resolve(offer, this.team(ids), rng.fork("events"));
        Voyage voyage = new Voyage(day, slot, offer, ids, now, events, rng.nextLong());
        this.voyages.add(voyage);
        // 前天以前接的任务不用再记
        this.taken.removeIf(key -> Long.parseLong(key.substring(0, key.indexOf('/'))) < day - 1);
        this.taken.add(key(day, slot));
        return voyage;
    }

    /** 派去清剿 {@code system} 的巢穴：先查 {@link #canSortie}，{@code offer} 按巢穴等级定（{@link HiveSortie#offer}）。燃料由调用方收。 */
    public Voyage sortie(int system, Expedition.Offer offer, List<Integer> team, long now, Rng rng) {
        List<Integer> ids = List.copyOf(new LinkedHashSet<>(team));
        List<Expedition.Event> events = HiveSortie.resolve(offer.difficulty(), this.team(ids), rng.fork("events"));
        Voyage voyage = new Voyage(-1, -1, offer, ids, now, events, rng.nextLong(), system);
        this.voyages.add(voyage);
        return voyage;
    }

    /** 在外面的远征都改成刚好回来（调试命令用）：出发时刻往前挪，遭遇不变。 */
    public void hurry(long now) {
        this.voyages.replaceAll(v -> v.done(now) ? v
                : new Voyage(v.day(), v.slot(), v.offer(), v.team(), now - v.offer().ticks(), v.events(), v.seed(), v.hive()));
    }

    /**
     * 领回一支回来了的远征：沉了的从舰队里去掉，受损的记上，其余的出勤次数加一、到了就升级。清剿的奖励只有信用点，
     * 巢穴那边打掉了什么、带回什么掉落由调用方去结算。
     */
    public Claim claim(Voyage voyage) {
        if (!this.voyages.remove(voyage)) {
            throw new IllegalArgumentException("not out: " + voyage);
        }
        Rng rng = Rng.of(voyage.seed());
        List<Expedition.Reward> rewards = voyage.sortie() ? HiveSortie.rewards(voyage.offer().difficulty(), voyage.events(), rng.fork("rewards"))
                : Expedition.rewards(voyage.offer(), voyage.events(), rng.fork("rewards"));
        Set<Integer> lost = new LinkedHashSet<>();
        Set<Integer> hurt = new LinkedHashSet<>();
        for (Expedition.Event event : voyage.events()) {
            if (event.outcome() == Expedition.Outcome.LOST) {
                lost.add(event.frigate());
            } else if (event.outcome() == Expedition.Outcome.DAMAGED) {
                hurt.add(event.frigate());
            }
        }
        hurt.removeAll(lost);
        List<Frigate> lostShips = new ArrayList<>();
        List<Frigate> damaged = new ArrayList<>();
        List<Frigate> promoted = new ArrayList<>();
        Rng ranks = rng.fork("ranks");
        for (int id : voyage.team()) {
            Frigate frigate = this.frigate(id);
            if (frigate == null) {
                continue;
            }
            if (lost.contains(id)) {
                this.frigates.remove(frigate);
                lostShips.add(frigate);
                continue;
            }
            if (hurt.contains(id)) {
                frigate.setDamaged(true);
                damaged.add(frigate);
            }
            if (frigate.returned(ranks)) {
                promoted.add(frigate);
            }
        }
        return new Claim(rewards, lostShips, damaged, promoted);
    }

    /** 远征带回来的飞船技术模块：随机一种，难度越高档次越好。 */
    public static TechModule rewardModule(int difficulty, Rng rng) {
        ModuleType type = rng.pick(ModuleType.of(ModuleHost.SHIP));
        double[] odds = {Math.max(5, 60 - 12 * difficulty), 30, 6 + 5 * difficulty, 1 + 2 * difficulty};
        Grade grade = rng.weighted(List.of(Grade.values()), g -> odds[g.ordinal()]);
        return TechModule.roll(type, grade, rng);
    }
}
