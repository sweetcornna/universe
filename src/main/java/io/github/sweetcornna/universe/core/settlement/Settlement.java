package io.github.sweetcornna.universe.core.settlement;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.civ.Race;
import io.github.sweetcornna.universe.core.poi.Landmark;
import io.github.sweetcornna.universe.core.poi.LandmarkType;
import io.github.sweetcornna.universe.core.poi.SettlementLayout;
import io.github.sweetcornna.universe.core.procgen.Rng;

/**
 * 一个聚落（ADR-0034 §二）：在哪里、谁是总督、各项数值、8 块地基、今天的决定、最近的事、有没有海盗要来。存档存的就是这个。
 *
 * <p>数值每天（{@link SettlementRules#DAY}）结算一次（{@link #advanceDay}）：出产进储备，交维护费，幸福往基准值靠，
 * 人口跟着幸福走，威胁涨，按威胁算今晚来不来海盗，出一个新的决定。
 */
public final class Settlement {
    /** 最近的一件事。{@code key} 是语言键的后半（{@code settlement.universe.event.<key>}），{@code arg} 是填进去的参数。 */
    public record Event(long day, String key, String arg) {
    }

    /** 决定带来的临时生产力，到 {@code until} 那天开始时失效。 */
    public record Boost(int amount, long until) {
    }

    /**
     * 一天的结算。
     *
     * @param units   出了几份
     * @param income  市场收了多少信用点
     * @param upkeep  维护费
     * @param fish    鱼塘出几条鱼（适配层按 {@code Fishing#pondCatch} 出）
     * @param navData 瞭望塔出几份导航数据
     * @param raid    今晚要来海盗
     */
    public record DayReport(long day, double units, long income, long upkeep, int fish, int navData, boolean raid,
            Decisions.@Nullable Decision decision) {
    }

    /**
     * 一次来袭的结果。
     *
     * @param damaged 打坏了哪几块地基
     * @param stolen  被抢走几份储备
     * @param reward  守住了给总督的信用点
     */
    public record RaidOutcome(boolean won, int fighters, List<Integer> damaged, double stolen, int reward) {
    }

    private final String dimension;
    private final long key;
    private final int x;
    private final int y;
    private final int z;
    private final int rotation;
    private final long seed;
    private final Race race;
    private final Plot[] plots = new Plot[SettlementLayout.plotCount()];
    private @Nullable UUID owner;
    private String ownerName = "";
    private long claimedDay = -1;
    private int population = SettlementRules.START_POPULATION;
    private double happiness = 50;
    private double threat = 10;
    private double reserve;
    private long debt;
    private long treasury;
    private Product product;
    private final List<Boost> boosts = new ArrayList<>();
    private @Nullable String decision;
    private final Deque<String> recent = new ArrayDeque<>();
    private final Deque<Event> events = new ArrayDeque<>();
    private long day = -1;
    private long raidAt = -1;
    private int raidsWon;
    private int raidsLost;

    public Settlement(String dimension, long key, int x, int y, int z, int rotation, long seed, Race race) {
        this.dimension = dimension;
        this.key = key;
        this.x = x;
        this.y = y;
        this.z = z;
        this.rotation = Math.floorMod(rotation, 4);
        this.seed = seed;
        this.race = race;
        this.product = Product.defaultFor(race);
        for (int i = 0; i < this.plots.length; i++) {
            this.plots[i] = new Plot();
        }
    }

    // ---- 位置

    /** 存档里的编号：维度加地标编号。 */
    public String id() {
        return id(this.dimension, this.key);
    }

    public static String id(String dimension, long key) {
        return dimension + "#" + Long.toUnsignedString(key, 36);
    }

    public String dimension() {
        return this.dimension;
    }

    public long key() {
        return this.key;
    }

    public int x() {
        return this.x;
    }

    /** 广场的地面高度。 */
    public int y() {
        return this.y;
    }

    public int z() {
        return this.z;
    }

    public int rotation() {
        return this.rotation;
    }

    public long seed() {
        return this.seed;
    }

    public Race race() {
        return this.race;
    }

    /** 当成地标用（布局的坐标都按地标算）。 */
    public Landmark landmark() {
        return new Landmark(LandmarkType.SETTLEMENT, this.x, this.z, 0, 0, this.key, this.seed, this.rotation);
    }

    public double distance(double px, double pz) {
        double dx = this.x + 0.5 - px;
        double dz = this.z + 0.5 - pz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ---- 总督

    public Optional<UUID> owner() {
        return Optional.ofNullable(this.owner);
    }

    public boolean owned() {
        return this.owner != null;
    }

    public boolean ownedBy(UUID player) {
        return player.equals(this.owner);
    }

    public String ownerName() {
        return this.ownerName;
    }

    public long claimedDay() {
        return this.claimedDay;
    }

    /** 接管：成了总督。之前有人管过的话，建筑和数值接着用。 */
    public void claim(UUID player, String name, long gameTime) {
        long today = SettlementRules.day(gameTime);
        boolean fresh = this.claimedDay < 0;
        this.owner = player;
        this.ownerName = name;
        this.claimedDay = today;
        if (this.day < 0) {
            this.day = today;
        }
        if (fresh) {
            this.population = SettlementRules.START_POPULATION;
            this.happiness = 50;
            this.threat = 10;
        }
        this.raidAt = -1;
        this.log("claimed", name);
    }

    /** 辞去总督：建筑留着，等下一个人来接。 */
    public void resign() {
        this.log("resigned", this.ownerName);
        this.owner = null;
        this.ownerName = "";
        this.decision = null;
        this.raidAt = -1;
    }

    // ---- 地基

    public Plot plot(int index) {
        return this.plots[index];
    }

    public int plotCount() {
        return this.plots.length;
    }

    /** 有开着的这种建筑。 */
    public boolean has(BuildingType type) {
        for (Plot plot : this.plots) {
            if (plot.active() && plot.type() == type) {
                return true;
            }
        }
        return false;
    }

    /** 不管在不在施工，有没有这种建筑。 */
    public boolean planned(BuildingType type) {
        for (Plot plot : this.plots) {
            if (plot.type() == type) {
                return true;
            }
        }
        return false;
    }

    /** 能不能在空地上盖 {@code type}：第一个只能盖总督办公室，办公室开张以后别的才能盖；办公室只能有一座。 */
    public boolean canStart(BuildingType type) {
        if (type.unique() && this.planned(type)) {
            return false;
        }
        return type == BuildingType.OFFICE || this.has(BuildingType.OFFICE);
    }

    /** 在 {@code index} 那块空地上开工；不行返回 false。 */
    public boolean start(int index, BuildingType type) {
        if (index < 0 || index >= this.plots.length || this.plots[index].type() != null || !this.canStart(type)) {
            return false;
        }
        this.plots[index].start(type);
        return true;
    }

    /** 到时间的施工、升级完成；返回完成了的地基和完成了什么。 */
    public List<int[]> tickPlots(long now) {
        List<int[]> finished = new ArrayList<>();
        for (int i = 0; i < this.plots.length; i++) {
            Plot plot = this.plots[i];
            Plot.Finished done = plot.tick(now);
            if (done != null) {
                finished.add(new int[] {i, done.ordinal()});
                if (done == Plot.Finished.BUILT) {
                    this.log("built", plot.type().id());
                } else if (done == Plot.Finished.UPGRADED) {
                    this.log("upgraded", plot.type().id() + ":" + BuildingType.letter(plot.grade()));
                }
            }
        }
        return finished;
    }

    /** 开张。 */
    public boolean open(int index) {
        Plot plot = this.plots[index];
        if (!plot.open()) {
            return false;
        }
        this.log("opened", plot.type().id());
        return true;
    }

    // ---- 数值

    public int population() {
        return this.population;
    }

    public double happiness() {
        return this.happiness;
    }

    public double threat() {
        return this.threat;
    }

    public double reserve() {
        return this.reserve;
    }

    public long debt() {
        return this.debt;
    }

    /** 市场收的、还没取走的信用点。 */
    public long treasury() {
        return this.treasury;
    }

    public Product product() {
        return this.product;
    }

    public List<Boost> boosts() {
        return List.copyOf(this.boosts);
    }

    public int raidsWon() {
        return this.raidsWon;
    }

    public int raidsLost() {
        return this.raidsLost;
    }

    /** 住房上限：原来的小屋加住宅。 */
    public int housing() {
        double housing = SettlementRules.BASE_HOUSING;
        for (Plot plot : this.plots) {
            if (plot.active()) {
                housing += plot.type().housing(plot.grade());
            }
        }
        return (int) Math.floor(housing);
    }

    /** 幸福的基准值：建筑给的，住不下要扣。 */
    public double happinessTarget() {
        double target = 45;
        for (Plot plot : this.plots) {
            if (plot.active()) {
                target += plot.type().happiness(plot.grade());
            }
        }
        target -= 8.0 * Math.max(0, this.population - this.housing());
        return Math.clamp(target, 0, 100);
    }

    public int boost() {
        int total = 0;
        for (Boost boost : this.boosts) {
            total += boost.amount();
        }
        return total;
    }

    /** 生产力：人手、建筑、决定带来的临时加成，幸福高了干得快。 */
    public double productivity() {
        double value = 5 + this.population + this.boost() + (this.happiness - 50) / 5;
        for (Plot plot : this.plots) {
            if (plot.active()) {
                value += plot.type().productivity(plot.grade());
            }
        }
        return Math.clamp(value, 0, 100);
    }

    /** 等级分数：开着的建筑（档次越高越多）、人口、幸福。 */
    public double score() {
        double score = this.population / 2.0 + this.happiness / 25;
        for (Plot plot : this.plots) {
            if (plot.active()) {
                score += 2 + plot.grade();
            }
        }
        return score;
    }

    /** 等级：0–3（C、B、A、S）。 */
    public int settlementClass() {
        return SettlementRules.settlementClass(this.score());
    }

    public int reserveCap() {
        return SettlementRules.reserveCap(this.settlementClass());
    }

    /** 盖好了的建筑数。 */
    public int buildings() {
        int count = 0;
        for (Plot plot : this.plots) {
            if (plot.built()) {
                count++;
            }
        }
        return count;
    }

    public long upkeep() {
        return (long) SettlementRules.UPKEEP * this.buildings();
    }

    public long income() {
        double income = 0;
        for (Plot plot : this.plots) {
            if (plot.active()) {
                income += plot.type().income(plot.grade());
            }
        }
        return Math.round(income);
    }

    /** 威胁一天涨多少：干得多、人多招眼，瞭望塔和炮台压低。 */
    public double threatDaily() {
        double value = this.productivity() / 10 + this.population / 4.0;
        for (Plot plot : this.plots) {
            if (plot.active()) {
                value -= plot.type().threatRelief(plot.grade());
            }
        }
        return value;
    }

    /** 防御值：炮台、瞭望塔，加上人心和人手。 */
    public double defense() {
        double value = this.happiness / 5 + this.population;
        for (Plot plot : this.plots) {
            if (plot.active()) {
                value += plot.type().defense(plot.grade());
            }
        }
        return value;
    }

    // ---- 生产

    /** 这个聚落能选的产品。 */
    public List<Product> products() {
        List<Product> list = new ArrayList<>();
        for (Product p : Product.values()) {
            if (this.available(p)) {
                list.add(p);
            }
        }
        return list;
    }

    public boolean available(Product p) {
        return (p.race() == null || p.race() == this.race) && (p.building() == null || this.has(p.building()));
    }

    /** 现在实际在出的：选的那种暂时出不了（建筑坏了）就出种族默认的。 */
    public Product effectiveProduct() {
        return this.available(this.product) ? this.product : Product.defaultFor(this.race);
    }

    public boolean setProduct(Product p) {
        if (!this.available(p)) {
            return false;
        }
        this.product = p;
        return true;
    }

    /** 从储备里取最多 {@code batches} 批；返回取了几批（每批 {@link Product#items()} 个）。 */
    public int withdraw(int batches) {
        Product p = this.effectiveProduct();
        int can = (int) Math.floor(this.reserve / p.units() + 1e-9);
        int taken = Math.clamp(batches, 0, can);
        this.reserve = Math.max(0, this.reserve - taken * (double) p.units());
        return taken;
    }

    /** 把储备里的整份都卖掉；返回卖得的信用点。 */
    public long sell() {
        int units = (int) Math.floor(this.reserve + 1e-9);
        this.reserve -= units;
        return (long) units * this.effectiveProduct().unitValue();
    }

    /** 取走市场收的信用点。 */
    public long takeTreasury() {
        long taken = this.treasury;
        this.treasury = 0;
        return taken;
    }

    /** 记到聚落的账上（守住来袭时总督不在线）。 */
    public void addTreasury(long credits) {
        this.treasury += Math.max(0, credits);
    }

    /** 总督替聚落还欠款；返回还了多少。 */
    public long payDebt(long credits) {
        long paid = Math.clamp(credits, 0, this.debt);
        this.debt -= paid;
        return paid;
    }

    // ---- 每天

    /** 处理到第几天了。 */
    public long day() {
        return this.day;
    }

    /** 结算第 {@code newDay} 天（这一天开始的时候）。没有总督的聚落不结算，只记日子。 */
    public DayReport advanceDay(long newDay, Rng rng) {
        this.day = newDay;
        if (this.owner == null) {
            return new DayReport(newDay, 0, 0, 0, 0, 0, false, null);
        }
        this.boosts.removeIf(boost -> boost.until() <= newDay);
        // 出产
        double units = this.productivity() / 10;
        this.reserve = Math.min(this.reserveCap(), this.reserve + units);
        int fish = 0;
        int navData = 0;
        for (Plot plot : this.plots) {
            if (plot.active() && plot.type() == BuildingType.FISHING_POND) {
                fish += SettlementRules.fishPerDay(plot.grade());
            } else if (plot.active() && plot.type() == BuildingType.WATCHTOWER) {
                navData += SettlementRules.navDataPerDay(plot.grade());
            }
        }
        // 市场的收入进账，维护费先从账上出，不够折算储备，再不够记欠款
        long income = this.income();
        this.treasury += income;
        long upkeep = this.upkeep();
        long due = upkeep;
        long fromTreasury = Math.min(due, this.treasury);
        this.treasury -= fromTreasury;
        due -= fromTreasury;
        if (due > 0) {
            int value = this.effectiveProduct().unitValue();
            double needed = due / (double) value;
            double used = Math.min(needed, this.reserve);
            this.reserve -= used;
            due -= Math.round(used * value);
        }
        if (due > 0) {
            if (this.debt == 0) {
                this.log("debt", Long.toString(due));
            }
            this.debt += due;
        }
        if (this.debt > 0 && this.treasury > 0) {
            long paid = Math.min(this.debt, this.treasury);
            this.debt -= paid;
            this.treasury -= paid;
        }
        // 幸福
        if (this.debt > 0) {
            this.happiness -= 5;
        }
        this.happiness = Math.clamp(this.happiness + SettlementRules.drift(this.happiness, this.happinessTarget()), 0, 100);
        // 人口
        if (this.happiness < 25 && this.population > 1) {
            this.population--;
            this.log("left", "");
        } else if (this.happiness > 70 && this.population < this.housing()) {
            this.population++;
            this.log("arrived", "");
        }
        // 威胁和海盗
        this.threat = Math.clamp(this.threat + this.threatDaily(), 0, 100);
        boolean raid = false;
        if (this.raidAt < 0 && newDay - this.claimedDay >= SettlementRules.RAID_GRACE_DAYS
                && rng.chance(SettlementRules.raidChance(this.threat))) {
            this.raidAt = SettlementRules.raidTime(newDay);
            raid = true;
        }
        // 决定
        Decisions.Decision picked = null;
        if (this.has(BuildingType.OFFICE)) {
            picked = Decisions.pick(rng.nextLong(), this::has, List.copyOf(this.recent));
            this.decision = picked.id();
        }
        return new DayReport(newDay, units, income, upkeep, fish, navData, raid, picked);
    }

    // ---- 决定

    public Optional<Decisions.Decision> decision() {
        return this.decision == null ? Optional.empty() : Decisions.byId(this.decision);
    }

    /** 选得起这个选项吗：信用点和储备够不够。 */
    public boolean affordable(Decisions.Effect effect, long credits) {
        return (effect.credits() >= 0 || credits >= -effect.credits()) && (effect.reserve() >= 0 || this.reserve >= -effect.reserve());
    }

    /**
     * 选今天决定的第 {@code option} 个选项：数值的变化在这里算，信用点、声望、物品、单词交给适配层。选不了返回空。
     *
     * @param credits 总督现在有多少信用点
     */
    public Optional<Decisions.Effect> decide(int option, long credits) {
        Optional<Decisions.Decision> pending = this.decision();
        if (pending.isEmpty() || option < 0 || option >= pending.get().options().size()) {
            return Optional.empty();
        }
        Decisions.Decision d = pending.get();
        Decisions.Effect effect = d.options().get(option);
        if (!this.affordable(effect, credits)) {
            return Optional.empty();
        }
        this.happiness = Math.clamp(this.happiness + effect.happiness(), 0, 100);
        this.threat = Math.clamp(this.threat + effect.threat(), 0, 100);
        this.reserve = Math.clamp(this.reserve + effect.reserve(), 0, this.reserveCap());
        if (effect.boost() != 0) {
            this.boosts.add(new Boost(effect.boost(), this.day + Math.max(1, effect.days())));
        }
        if (effect.population() > 0) {
            this.population += Math.min(effect.population(), Math.max(0, this.housing() - this.population));
        } else if (effect.population() < 0) {
            this.population = Math.max(1, this.population + effect.population());
        }
        this.decision = null;
        this.recent.addLast(d.id());
        while (this.recent.size() > SettlementRules.RECENT_DECISIONS) {
            this.recent.removeFirst();
        }
        this.log("decided", d.id() + ":" + option);
        return Optional.of(effect);
    }

    public List<String> recentDecisions() {
        return List.copyOf(this.recent);
    }

    // ---- 海盗

    /** 来袭开始的游戏刻；没有是 -1。 */
    public long raidAt() {
        return this.raidAt;
    }

    /** 马上安排一次来袭（调试、威胁满了）。 */
    public void scheduleRaid(long gameTime) {
        this.raidAt = gameTime;
    }

    public void cancelRaid() {
        this.raidAt = -1;
    }

    /** 这次来几架。 */
    public int raidFighters() {
        return SettlementRules.fighters(this.settlementClass(), this.threat);
    }

    /** 总督不在场：按防御值算输赢，结果落到数值上。 */
    public RaidOutcome resolveRaid(Rng rng) {
        int fighters = this.raidFighters();
        boolean won = SettlementRules.defended(this.defense(), SettlementRules.strength(fighters, this.threat), rng.nextDouble());
        return this.finishRaid(won, fighters, rng);
    }

    /**
     * 来袭结束：守住了幸福 +10、威胁清零；没守住两座建筑受损、幸福 -15、储备被抢走三成，威胁降 40（海盗抢够了先走）。
     */
    public RaidOutcome finishRaid(boolean won, int fighters, Rng rng) {
        this.raidAt = -1;
        if (won) {
            this.happiness = Math.clamp(this.happiness + 10, 0, 100);
            this.threat = 0;
            this.raidsWon++;
            int reward = SettlementRules.raidReward(fighters);
            this.log("raid_won", Integer.toString(fighters));
            return new RaidOutcome(true, fighters, List.of(), 0, reward);
        }
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < this.plots.length; i++) {
            if (this.plots[i].active()) {
                candidates.add(i);
            }
        }
        List<Integer> damaged = new ArrayList<>();
        for (int k = 0; k < 2 && !candidates.isEmpty(); k++) {
            int index = candidates.remove(rng.nextInt(candidates.size()));
            this.plots[index].damage();
            damaged.add(index);
        }
        double stolen = this.reserve * 0.3;
        this.reserve -= stolen;
        this.happiness = Math.clamp(this.happiness - 15, 0, 100);
        this.threat = Math.max(0, this.threat - 40);
        this.raidsLost++;
        this.log("raid_lost", Integer.toString(damaged.size()));
        return new RaidOutcome(false, fighters, List.copyOf(damaged), stolen, 0);
    }

    /** 修好了一块地基（交齐了材料）。 */
    public void repaired(int index) {
        this.log("repaired", this.plots[index].type() == null ? "" : this.plots[index].type().id());
    }

    // ---- 最近的事

    public List<Event> events() {
        return List.copyOf(this.events);
    }

    public void log(String eventKey, String arg) {
        this.events.addLast(new Event(Math.max(0, this.day), eventKey, arg));
        while (this.events.size() > SettlementRules.HISTORY) {
            this.events.removeFirst();
        }
    }

    // ---- 读档

    /** 读档用：把存下来的数值放回去。 */
    public void restore(@Nullable UUID owner, String ownerName, long claimedDay, int population, double happiness, double threat, double reserve,
            long debt, long treasury, Product product, @Nullable String decision, long day, long raidAt, int raidsWon, int raidsLost) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.claimedDay = claimedDay;
        this.population = Math.max(1, population);
        this.happiness = Math.clamp(happiness, 0, 100);
        this.threat = Math.clamp(threat, 0, 100);
        this.reserve = Math.max(0, reserve);
        this.debt = Math.max(0, debt);
        this.treasury = Math.max(0, treasury);
        this.product = product;
        this.decision = decision;
        this.day = day;
        this.raidAt = raidAt;
        this.raidsWon = raidsWon;
        this.raidsLost = raidsLost;
    }

    public void restoreBoosts(List<Boost> list) {
        this.boosts.clear();
        this.boosts.addAll(list);
    }

    public void restoreRecent(List<String> list) {
        this.recent.clear();
        this.recent.addAll(list);
    }

    public void restoreEvents(List<Event> list) {
        this.events.clear();
        this.events.addAll(list);
    }

    /** 调试用：直接改数值。 */
    public void debugSet(double happiness, double threat, double reserve) {
        this.happiness = Math.clamp(happiness, 0, 100);
        this.threat = Math.clamp(threat, 0, 100);
        this.reserve = Math.clamp(reserve, 0, this.reserveCap());
    }

    /** 今天的决定（调试用）：换成指定的那个。 */
    public void forceDecision(@Nullable String id) {
        this.decision = id;
    }
}
