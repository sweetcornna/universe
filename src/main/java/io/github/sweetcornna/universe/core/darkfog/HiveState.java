package io.github.sweetcornna.universe.core.darkfog;

import java.util.Arrays;

/**
 * 一座黑雾太空巢穴的状态（ADR-0028 §八），按游戏时间结算：太空维度没加载时也照算，回来时一次补上。
 *
 * <ul>
 *   <li>物质：核心自己攒一点，光子接收器吸恒星的光攒得多（恒星越亮越多），偷到戴森球的电再多一些；取到的物质一半算经验。</li>
 *   <li>建筑：按 {@link HiveLayout} 的顺序，等级够、物质够就盖下一座，一座一座来，打掉的缓一阵再补。慢慢回血。</li>
 *   <li>单位：每个港口按自己的节奏补，满了不补。枪骑 2 份物质，座头鲸 6 份。</li>
 *   <li>威胁：恒星系里用电涨得慢（地面基地的三分之一），打中继站涨得猛，打巢穴自己也涨；满了派枪骑去轰炸，打完清零、冷却。</li>
 *   <li>中继站、种子：物质和等级够了就派，派完要等一阵。</li>
 *   <li>核心打掉：恒星系里还有中继站就过 10 分钟在原地重建（降 3 级），中继站都没了就彻底消灭。</li>
 * </ul>
 */
public final class HiveState {
    public enum Status {
        ACTIVE,
        /** 核心打掉了，等着重建。 */
        REBUILDING,
        /** 彻底消灭了。 */
        GONE
    }

    /**
     * 结算环境。
     *
     * @param growth        成长速度
     * @param light         恒星的光照倍率（戴森球的 {@code lightFactor}）
     * @param siphon        这一阵偷到的戴森球功率（焦/刻）
     * @param threatPerTick 用电每刻涨的威胁
     * @param relays        恒星系里还活着的中继站（地面基地上的加上飞着的）
     */
    public record Environment(double growth, double light, double siphon, double threatPerTick, int relays) {
        public static final Environment IDLE = new Environment(1, 1, 0, 0, 1);
    }

    /** 核心自己每秒攒的物质。 */
    public static final double CORE_MATTER = 0.06;
    /** 每座光子接收器每秒攒的物质（光照倍率 1 时）。 */
    public static final double RECEIVER_MATTER = 0.08;
    /** 偷戴森球的电换物质：每秒多攒 {@code SIPHON_MATTER × log2(1 + 功率 / SIPHON_SCALE)}。 */
    public static final double SIPHON_MATTER = 0.05;
    public static final double SIPHON_SCALE = 500;
    /** 每座光子接收器偷走戴森球多大一部分发电，最多 {@link #MAX_SIPHON}。 */
    public static final double SIPHON_PER_RECEIVER = 0.03;
    public static final double MAX_SIPHON = 0.15;
    public static final double MAX_MATTER = 2000;
    public static final double XP_PER_MATTER = 0.5;
    /** 每秒回多少血（满血的比例）。 */
    public static final double REGEN_PER_SECOND = 0.002;
    /** 盖一座要多久（秒）：盖完一座，下一座等这么久。 */
    public static final int BUILD_SECONDS = 90;
    /** 有建筑被打掉以后，过多久才开始补（秒）。 */
    public static final int REBUILD_DELAY_SECONDS = 300;
    /** 第一次建起来时已经长了多久。 */
    public static final int INITIAL_GROWTH_TICKS = 30 * 1200;
    /** 一次最多补多久。 */
    public static final long MAX_CATCH_UP_TICKS = 12L * 60 * 1200;
    /** 核心打掉以后过多久重建（秒），重建时降几级。 */
    public static final int RESURRECT_SECONDS = 600;
    public static final int RESURRECT_LEVEL_LOSS = 3;
    /** 用电涨威胁按地面基地的这么多倍算。 */
    public static final double POWER_THREAT_SCALE = 1.0 / 3;
    /** 打巢穴挨多少伤害涨满一次威胁（打核心按两倍算，打单位按一半）。 */
    public static final double DAMAGE_TO_FILL = 3000;
    /** 中继站挨多少伤害涨满一次威胁。 */
    public static final double RELAY_DAMAGE_TO_FILL = 400;
    /** 一次轰炸打完以后多久不涨威胁（刻）。 */
    public static final int RAID_COOLDOWN_TICKS = 6000;
    /** 一次轰炸最多几架。 */
    public static final int MAX_RAID = 24;
    /** 中继站：要几级、多少物质，派完隔多久（秒），没地方去时隔多久再试。 */
    public static final int RELAY_MIN_LEVEL = 1;
    public static final double RELAY_COST = 150;
    public static final int RELAY_SECONDS = 1800;
    public static final int RELAY_RETRY_SECONDS = 120;
    /** 一个巢穴同时最多有几个中继站在路上。 */
    public static final int MAX_RELAYS_IN_FLIGHT = 2;
    /** 种子：要几级、多少物质，放完隔多久（秒），没地方去时隔多久再试。 */
    public static final int SEED_MIN_LEVEL = 8;
    public static final double SEED_COST = 800;
    public static final int SEED_SECONDS = 4 * 3600;
    public static final int SEED_RETRY_SECONDS = 600;
    /** 每档攻击性派出港口里多大一部分枪骑：被动、迟钝、普通、敏锐、好斗、狂暴。 */
    private static final double[] RAID_SHARE = {0, 0.3, 0.5, 0.65, 0.8, 1};

    private final int system;
    private final double x;
    private final double y;
    private final double z;
    private Status status;
    private int level;
    private double xp;
    private double matter;
    private double threat;
    /** 轰炸打完的冷却，刻。 */
    private int cooldown;
    /** 每个槽位的血量（满血的比例）；0 是没有。 */
    private final float[] health = new float[HiveLayout.size()];
    private final int[] units = new int[SpaceUnitKind.values().length];
    private final double[] progress = new double[SpaceUnitKind.values().length];
    /** 结算到了哪一刻。 */
    private long updated;
    /** 还要多少秒才能盖下一座。 */
    private int buildDelay;
    /** 核心打掉后还要多少秒重建。 */
    private int rebuildDelay;
    /** 还要多少秒才能派下一个中继站、放下一颗种子。 */
    private int relayDelay;
    private int seedDelay;

    /** 新巢穴：还没长，先是 {@link Status#ACTIVE}，接着调 {@link #activate} 或 {@link #found}。 */
    public HiveState(int system, double x, double y, double z) {
        this.system = system;
        this.x = x;
        this.y = y;
        this.z = z;
        this.status = Status.ACTIVE;
    }

    /** 读存档用；{@code timers} 是冷却、建造、重建、中继站、种子五个计时。 */
    public HiveState(int system, double x, double y, double z, Status status, int level, double xp, double matter, double threat,
            float[] health, int[] units, double[] progress, long updated, int[] timers) {
        this(system, x, y, z);
        this.status = status;
        this.level = DarkFogLevels.clamp(level);
        this.xp = xp;
        this.matter = matter;
        this.threat = threat;
        System.arraycopy(health, 0, this.health, 0, Math.min(health.length, this.health.length));
        System.arraycopy(units, 0, this.units, 0, Math.min(units.length, this.units.length));
        System.arraycopy(progress, 0, this.progress, 0, Math.min(progress.length, this.progress.length));
        this.updated = updated;
        int[] t = Arrays.copyOf(timers, 5);
        this.cooldown = Math.max(0, t[0]);
        this.buildDelay = Math.max(0, t[1]);
        this.rebuildDelay = Math.max(0, t[2]);
        this.relayDelay = Math.max(0, t[3]);
        this.seedDelay = Math.max(0, t[4]);
    }

    /** 开局就有的巢穴：核心和第一座光子接收器先有，再按"初始成长"长一阵。 */
    public void activate(int level, long now, double growth, double light) {
        this.reset(level);
        this.updated = now - INITIAL_GROWTH_TICKS;
        this.advanceTo(now, new Environment(growth, light, 0, 0, 1));
    }

    /** 种子落下建的新巢：核心和第一座光子接收器，从这一刻开始长。 */
    public void found(int level, long now) {
        this.reset(level);
        this.updated = now;
        this.buildDelay = BUILD_SECONDS;
    }

    private void reset(int level) {
        this.status = Status.ACTIVE;
        this.level = DarkFogLevels.clamp(level);
        this.xp = 0;
        this.matter = 0;
        this.threat = 0;
        this.cooldown = 0;
        Arrays.fill(this.health, 0);
        Arrays.fill(this.units, 0);
        Arrays.fill(this.progress, 0);
        this.health[HiveLayout.CORE] = 1;
        this.health[HiveLayout.FIRST_RECEIVER] = 1;
        this.buildDelay = 0;
        this.rebuildDelay = 0;
        this.relayDelay = 0;
        this.seedDelay = 0;
    }

    /** 结算到 {@code now}：按秒走，不满一秒的留到下次。 */
    public void advanceTo(long now, Environment environment) {
        if (this.status == Status.GONE) {
            this.updated = now;
            return;
        }
        long elapsed = now - this.updated;
        if (elapsed > MAX_CATCH_UP_TICKS) {
            this.updated = now - MAX_CATCH_UP_TICKS;
            elapsed = MAX_CATCH_UP_TICKS;
        }
        long seconds = Math.max(0, elapsed / 20);
        for (long i = 0; i < seconds && this.status != Status.GONE; i++) {
            if (this.status == Status.REBUILDING) {
                this.waitForRebuild(environment);
            } else {
                this.second(environment);
            }
        }
        this.updated += seconds * 20;
        if (this.status == Status.GONE) {
            this.updated = now;
        }
    }

    /** 停摆的时候也要把时钟拨到现在，免得以后一次补上。 */
    public void skipTo(long now) {
        this.updated = now;
    }

    /** 把时钟往回拨：下次结算多长 {@code ticks} 刻（调试命令"长一阵"用）。 */
    public void backdate(long ticks) {
        this.updated -= Math.max(0, ticks);
    }

    private void second(Environment environment) {
        double mined = this.matterPerSecond(environment);
        this.matter = Math.min(MAX_MATTER, this.matter + mined);
        this.gainXp(mined * XP_PER_MATTER);
        for (int i = 0; i < this.health.length; i++) {
            if (this.health[i] > 0 && this.health[i] < 1) {
                this.health[i] = (float) Math.min(1, this.health[i] + REGEN_PER_SECOND);
            }
        }
        this.build();
        this.breed(environment);
        if (this.cooldown > 0) {
            this.cooldown = Math.max(0, this.cooldown - 20);
        } else {
            this.threat = Math.min(1, this.threat + environment.threatPerTick() * 20);
        }
        this.relayDelay = Math.max(0, this.relayDelay - 1);
        this.seedDelay = Math.max(0, this.seedDelay - 1);
    }

    /** 每秒攒多少物质（已经乘了成长速度）。 */
    public double matterPerSecond(Environment environment) {
        double light = Math.max(0.25, Math.min(4, environment.light()));
        double receivers = this.receivers() * RECEIVER_MATTER * Math.sqrt(light);
        double siphon = environment.siphon() > 0 ? SIPHON_MATTER * Math.log1p(environment.siphon() / SIPHON_SCALE) / Math.log(2) : 0;
        return (CORE_MATTER + receivers + siphon) * environment.growth();
    }

    private void waitForRebuild(Environment environment) {
        if (environment.relays() <= 0) {
            this.status = Status.GONE;
            return;
        }
        this.rebuildDelay = Math.max(0, this.rebuildDelay - 1);
        if (this.rebuildDelay == 0) {
            int level = Math.max(0, this.level - RESURRECT_LEVEL_LOSS);
            this.reset(level);
            this.buildDelay = BUILD_SECONDS;
        }
    }

    /** 盖下一座：顺序里第一个缺着、等级够的槽位，物质够就盖；一座一座来。 */
    private void build() {
        if (this.buildDelay > 0) {
            this.buildDelay--;
            return;
        }
        for (int i = 0; i < this.health.length; i++) {
            if (this.health[i] > 0 || !HiveLayout.rebuildable(i)) {
                continue;
            }
            HiveLayout.Slot slot = HiveLayout.slot(i);
            if (slot.minLevel() > this.level) {
                continue;
            }
            if (this.matter >= slot.cost()) {
                this.matter -= slot.cost();
                this.health[i] = 1;
                this.buildDelay = BUILD_SECONDS;
            }
            return;
        }
    }

    private void breed(Environment environment) {
        for (SpaceUnitKind kind : SpaceUnitKind.values()) {
            int k = kind.ordinal();
            int ports = this.ports(kind);
            int capacity = this.capacity(kind);
            if (this.units[k] >= capacity) {
                this.units[k] = capacity;
                this.progress[k] = 0;
                continue;
            }
            double rate = ports * environment.growth() / unitSeconds(kind);
            this.progress[k] = Math.min(this.progress[k] + rate, Math.max(1, ports));
            while (this.progress[k] >= 1 && this.matter >= unitCost(kind) && this.units[k] < capacity) {
                this.units[k]++;
                this.matter -= unitCost(kind);
                this.progress[k] -= 1;
            }
        }
    }

    /** 一个港口多少秒补一个单位。 */
    public static double unitSeconds(SpaceUnitKind kind) {
        return kind == SpaceUnitKind.HUMPBACK ? 120 : 40;
    }

    /** 一个单位要几份物质。 */
    public static double unitCost(SpaceUnitKind kind) {
        return kind == SpaceUnitKind.HUMPBACK ? 6 : 2;
    }

    private void gainXp(double amount) {
        this.xp += amount;
        while (this.level < DarkFogLevels.MAX_LEVEL && this.xp >= DarkFogLevels.xpToNext(this.level)) {
            this.xp -= DarkFogLevels.xpToNext(this.level);
            this.level++;
        }
        if (this.level >= DarkFogLevels.MAX_LEVEL) {
            this.xp = 0;
        }
    }

    // ---- 威胁

    /** 用电每刻涨多少威胁：地面基地的三分之一。 */
    public static double powerThreatPerTick(double watts, DarkFogSettings settings) {
        return Threat.powerPerTick(watts, settings) * POWER_THREAT_SCALE;
    }

    /** 巢穴的建筑挨一点伤害涨多少威胁。 */
    public static double buildingThreatPerDamage(HiveBuildingKind kind, DarkFogSettings settings) {
        double weight = kind == HiveBuildingKind.CORE ? 2 : 1;
        return weight / DAMAGE_TO_FILL * settings.combatThreat() * Threat.aggression(settings.aggressiveness());
    }

    /** 守巢的单位挨一点伤害涨多少威胁。 */
    public static double unitThreatPerDamage(DarkFogSettings settings) {
        return 0.5 / DAMAGE_TO_FILL * settings.combatThreat() * Threat.aggression(settings.aggressiveness());
    }

    /** 中继站挨一点伤害涨多少威胁。 */
    public static double relayThreatPerDamage(DarkFogSettings settings) {
        return 1 / RELAY_DAMAGE_TO_FILL * settings.combatThreat() * Threat.aggression(settings.aggressiveness());
    }

    /** 巢穴挨打（不管谁打的）：伤害算经验，再涨威胁。 */
    public void hurt(double damage, double threatPerDamage) {
        if (this.status != Status.ACTIVE || damage <= 0) {
            return;
        }
        this.gainXp(damage);
        this.addThreat(damage * threatPerDamage);
    }

    /** 涨威胁（冷却的时候也涨：打它的中继站，它马上记仇）。 */
    public void addThreat(double amount) {
        if (this.status != Status.ACTIVE || amount <= 0) {
            return;
        }
        this.threat = Math.min(1, this.threat + amount);
    }

    // ---- 打掉

    /** 某座建筑被打掉了；核心要用 {@link #collapse}。 */
    public void destroy(int slot) {
        if (slot == HiveLayout.CORE || this.status != Status.ACTIVE) {
            return;
        }
        this.health[slot] = 0;
        this.buildDelay = Math.max(this.buildDelay, REBUILD_DELAY_SECONDS);
        HiveLayout.Slot destroyed = HiveLayout.slot(slot);
        for (SpaceUnitKind kind : SpaceUnitKind.values()) {
            if (kind.port() == destroyed.kind()) {
                this.units[kind.ordinal()] = Math.min(this.units[kind.ordinal()], this.capacity(kind));
            }
        }
    }

    /**
     * 核心被打掉：建筑、单位、物质全没了。
     *
     * @param relays 恒星系里还有活着的中继站：有就等着重建，没有就彻底消灭
     */
    public void collapse(boolean relays) {
        if (this.status != Status.ACTIVE) {
            return;
        }
        Arrays.fill(this.health, 0);
        Arrays.fill(this.units, 0);
        Arrays.fill(this.progress, 0);
        this.matter = 0;
        this.threat = 0;
        this.cooldown = 0;
        if (relays) {
            this.status = Status.REBUILDING;
            this.rebuildDelay = RESURRECT_SECONDS;
        } else {
            this.status = Status.GONE;
        }
    }

    /** 恒星系里的中继站都没了：等着重建的巢穴就此消灭。 */
    public void relaysLost() {
        if (this.status == Status.REBUILDING) {
            this.status = Status.GONE;
        }
    }

    // ---- 轰炸

    /** 威胁满了、不在冷却、港口里有枪骑。 */
    public boolean raidReady() {
        return this.status == Status.ACTIVE && this.threatFull() && this.cooldown == 0 && this.units(SpaceUnitKind.LANCER) > 0;
    }

    /** 这次派几架：港口里的枪骑按攻击性取一部分，等级越高取得越多（每级多 1%），至少一架。被动的不派。 */
    public int raidSize(int aggressiveness) {
        if (aggressiveness <= DarkFogSettings.PASSIVE) {
            return 0;
        }
        int available = this.units(SpaceUnitKind.LANCER);
        if (available <= 0) {
            return 0;
        }
        double share = Math.min(1, RAID_SHARE[Math.min(RAID_SHARE.length - 1, aggressiveness)] + 0.01 * this.level);
        return Math.min(MAX_RAID, Math.min(available, Math.max(1, (int) Math.ceil(available * share))));
    }

    /** 派出去了 {@code count} 架：从港口里扣掉。 */
    public void raidLaunched(int count) {
        int k = SpaceUnitKind.LANCER.ordinal();
        this.units[k] = Math.max(0, this.units[k] - Math.max(0, count));
    }

    /** 轰炸打完了：活着的回港口，威胁清零，冷却。 */
    public void raidOver(int survivors) {
        if (this.status != Status.ACTIVE) {
            return;
        }
        int k = SpaceUnitKind.LANCER.ordinal();
        this.units[k] = Math.min(this.capacity(SpaceUnitKind.LANCER), this.units[k] + Math.max(0, survivors));
        this.threat = 0;
        this.cooldown = RAID_COOLDOWN_TICKS;
    }

    // ---- 中继站、种子

    public boolean relayReady() {
        return this.status == Status.ACTIVE && this.level >= RELAY_MIN_LEVEL && this.matter >= RELAY_COST && this.relayDelay == 0;
    }

    public void relayLaunched() {
        this.matter -= RELAY_COST;
        this.relayDelay = RELAY_SECONDS;
    }

    /** 没地方去：过一阵再试。 */
    public void relayFailed() {
        this.relayDelay = RELAY_RETRY_SECONDS;
    }

    public boolean seedReady() {
        return this.status == Status.ACTIVE && this.level >= SEED_MIN_LEVEL && this.matter >= SEED_COST && this.seedDelay == 0;
    }

    public void seedLaunched() {
        this.matter -= SEED_COST;
        this.seedDelay = SEED_SECONDS;
    }

    public void seedFailed() {
        this.seedDelay = SEED_RETRY_SECONDS;
    }

    /** 中继站、种子的计时清零（调试命令用）。 */
    public void readyFlights() {
        this.relayDelay = 0;
        this.seedDelay = 0;
    }

    // ---- 护卫舰清剿

    /**
     * 护卫舰赢了 {@code successes} 段：每段打掉 3 个单位（先打枪骑），每两段打掉一座建筑（从最后盖的打起，不碰核心）。
     *
     * @return {打掉的单位, 打掉的建筑}
     */
    public int[] strike(int successes) {
        if (this.status != Status.ACTIVE || successes <= 0) {
            return new int[] {0, 0};
        }
        int kills = 0;
        for (int i = 0; i < successes * 3; i++) {
            SpaceUnitKind kind = this.units(SpaceUnitKind.LANCER) > 0 ? SpaceUnitKind.LANCER
                    : this.units(SpaceUnitKind.HUMPBACK) > 0 ? SpaceUnitKind.HUMPBACK : null;
            if (kind == null) {
                break;
            }
            this.units[kind.ordinal()]--;
            kills++;
        }
        int destroyed = 0;
        for (int i = this.health.length - 1; i > HiveLayout.CORE && destroyed < successes / 2; i--) {
            if (this.health[i] > 0) {
                this.destroy(i);
                destroyed++;
            }
        }
        return new int[] {kills, destroyed};
    }

    // ---- 查询

    public int ports(SpaceUnitKind kind) {
        int count = 0;
        for (int i = 0; i < this.health.length; i++) {
            if (this.health[i] > 0 && HiveLayout.slot(i).kind() == kind.port()) {
                count++;
            }
        }
        return count;
    }

    /** 港口里最多停多少个这种单位。 */
    public int capacity(SpaceUnitKind kind) {
        return this.ports(kind) * HiveLayout.portCapacity(kind);
    }

    /** 活着的光子接收器有几座。 */
    public int receivers() {
        int count = 0;
        for (int i = 0; i < this.health.length; i++) {
            if (this.health[i] > 0 && HiveLayout.slot(i).kind() == HiveBuildingKind.PHOTON_RECEIVER) {
                count++;
            }
        }
        return count;
    }

    /** 偷走戴森球多大一部分发电。 */
    public double siphonShare() {
        return this.status == Status.ACTIVE ? Math.min(MAX_SIPHON, this.receivers() * SIPHON_PER_RECEIVER) : 0;
    }

    public int buildings() {
        int count = 0;
        for (float h : this.health) {
            if (h > 0) {
                count++;
            }
        }
        return count;
    }

    public boolean alive(int slot) {
        return this.health[slot] > 0;
    }

    public float health(int slot) {
        return this.health[slot];
    }

    /** 实体那边的血量写回来（满血的比例，0 以上）。 */
    public void setHealth(int slot, float fraction) {
        if (this.health[slot] > 0) {
            this.health[slot] = Math.max(0.001F, Math.min(1, fraction));
        }
    }

    public boolean threatFull() {
        return this.threat >= 1 - 1e-6;
    }

    public int system() {
        return this.system;
    }

    public double x() {
        return this.x;
    }

    public double y() {
        return this.y;
    }

    public double z() {
        return this.z;
    }

    public Status status() {
        return this.status;
    }

    public int level() {
        return this.level;
    }

    public void setLevel(int level) {
        this.level = DarkFogLevels.clamp(level);
        this.xp = 0;
    }

    public double xp() {
        return this.xp;
    }

    public double matter() {
        return this.matter;
    }

    public void setMatter(double matter) {
        this.matter = Math.max(0, Math.min(MAX_MATTER, matter));
    }

    public double threat() {
        return this.threat;
    }

    public void setThreat(double threat) {
        this.threat = Math.max(0, Math.min(1, threat));
        if (threat > 0) {
            this.cooldown = 0;
        }
    }

    public int cooldown() {
        return this.cooldown;
    }

    public int buildDelay() {
        return this.buildDelay;
    }

    public int rebuildDelay() {
        return this.rebuildDelay;
    }

    public int relayDelay() {
        return this.relayDelay;
    }

    public int seedDelay() {
        return this.seedDelay;
    }

    public int units(SpaceUnitKind kind) {
        return this.units[kind.ordinal()];
    }

    public void setUnits(SpaceUnitKind kind, int count) {
        this.units[kind.ordinal()] = Math.max(0, count);
    }

    /** 守巢的单位死了一个。 */
    public void unitLost(SpaceUnitKind kind) {
        int k = kind.ordinal();
        this.units[k] = Math.max(0, this.units[k] - 1);
    }

    public float[] healthArray() {
        return this.health.clone();
    }

    public int[] unitArray() {
        return this.units.clone();
    }

    public double[] progressArray() {
        return this.progress.clone();
    }

    /** 冷却、建造、重建、中继站、种子五个计时，存档用。 */
    public int[] timers() {
        return new int[] {this.cooldown, this.buildDelay, this.rebuildDelay, this.relayDelay, this.seedDelay};
    }

    public long updated() {
        return this.updated;
    }
}
