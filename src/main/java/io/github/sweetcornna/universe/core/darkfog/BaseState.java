package io.github.sweetcornna.universe.core.darkfog;

import java.util.Arrays;

/**
 * 一处黑雾地面基地的状态（ADR-0028 §二、§三），按游戏时间结算：区块没加载时也照算，回来时一次补上。
 *
 * <ul>
 *   <li>能量：中继站在就是满的；中继站没了，存的能量（10 分钟）用完基地就停摆，不攒物质、不盖、不产、威胁不涨。</li>
 *   <li>物质：钻芯每秒取 0.12（乘生长速度），攒着盖建筑、补单位；取到的物质一半算经验。</li>
 *   <li>建筑：按 {@link BaseLayout} 的顺序，等级够、物质够就盖下一座，打掉的也这样补（中继站、钻芯不补）。
 *       有能量时慢慢回血。</li>
 *   <li>单位：每个营地按自己的节奏补，满了不补；超载时快十倍。每个单位要 1 份物质。</li>
 *   <li>威胁：用电和挨打涨，满了派一波；打完清零，冷却 3 分钟。</li>
 * </ul>
 */
public final class BaseState {
    public enum Status {
        /** 选好了地方，等基地中心的区块生成。 */
        PENDING,
        ACTIVE,
        /** 中心的区块是以前生成的：这里不放基地。 */
        VOID,
        /** 钻芯被打掉了。 */
        DESTROYED
    }

    /** 结算环境：这段时间里的生长速度、超载、用电涨的威胁。 */
    public record Environment(double growth, boolean overdrive, double threatPerTick) {
        public static final Environment IDLE = new Environment(1, false, 0);
    }

    public static final double MATTER_PER_SECOND = 0.12;
    public static final double MAX_MATTER = 600;
    public static final double XP_PER_MATTER = 0.5;
    /** 中继站没了以后，存的能量还能用多少刻。 */
    public static final int ENERGY_TICKS = 12_000;
    /** 有能量时建筑每秒回多少（满血的比例）。 */
    public static final double REGEN_PER_SECOND = 0.002;
    public static final int OVERDRIVE = 10;
    public static final double UNIT_COST = 1;
    /** 第一次放出来时已经长了多久。 */
    public static final int INITIAL_GROWTH_TICKS = 30 * 1200;
    /** 一次最多补多久（以免很久没来的存档一次算太多）。 */
    public static final long MAX_CATCH_UP_TICKS = 12L * 60 * 1200;
    /** 盖一座要多久（秒）：盖完一座，下一座等这么久。 */
    public static final int BUILD_SECONDS = 60;
    /** 有建筑被打掉以后，过多久才开始补（秒）。不然物质够的基地打掉一座下一秒就冒出来。 */
    public static final int REBUILD_DELAY_SECONDS = 180;

    private final int id;
    private final int x;
    private int y;
    private final int z;
    private Status status;
    private int level;
    private double xp;
    private double matter;
    private int energy;
    private double threat;
    private int cooldown;
    private boolean ground;
    /** 每个槽位的血量（满血的比例）；0 是没有。 */
    private final float[] health = new float[BaseLayout.size()];
    private final int[] units = new int[UnitKind.values().length];
    private final double[] progress = new double[UnitKind.values().length];
    /** 结算到了哪一刻。 */
    private long updated;
    /** 还要多少秒才能盖下一座。 */
    private int buildDelay;

    public BaseState(int id, int x, int z) {
        this.id = id;
        this.x = x;
        this.y = Integer.MIN_VALUE;
        this.z = z;
        this.status = Status.PENDING;
    }

    /** 读存档用。 */
    public BaseState(int id, int x, int y, int z, Status status, int level, double xp, double matter, int energy, double threat, int cooldown,
            boolean ground, float[] health, int[] units, double[] progress, long updated, int buildDelay) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.z = z;
        this.status = status;
        this.level = DarkFogLevels.clamp(level);
        this.xp = xp;
        this.matter = matter;
        this.energy = energy;
        this.threat = threat;
        this.cooldown = cooldown;
        this.ground = ground;
        System.arraycopy(health, 0, this.health, 0, Math.min(health.length, this.health.length));
        System.arraycopy(units, 0, this.units, 0, Math.min(units.length, this.units.length));
        System.arraycopy(progress, 0, this.progress, 0, Math.min(progress.length, this.progress.length));
        this.updated = updated;
        this.buildDelay = Math.max(0, buildDelay);
    }

    /** 中心的区块刚生成：基地在这里扎下，钻芯和中继站先有，再按"初始成长"长一阵。 */
    public void activate(int level, long now, double growth) {
        this.status = Status.ACTIVE;
        this.level = DarkFogLevels.clamp(level);
        Arrays.fill(this.health, 0);
        this.health[BaseLayout.CORE] = 1;
        this.health[BaseLayout.RELAY] = 1;
        this.energy = ENERGY_TICKS;
        this.updated = now - INITIAL_GROWTH_TICKS;
        this.advanceTo(now, new Environment(growth, false, 0));
    }

    /** 巢穴的中继站落地（ADR-0028 §八）：钻芯和中继站马上有，从这一刻开始长，不补初始成长；第一座建筑也要等一阵。 */
    public void land(int level, long now) {
        this.status = Status.ACTIVE;
        this.level = DarkFogLevels.clamp(level);
        this.xp = 0;
        Arrays.fill(this.health, 0);
        this.health[BaseLayout.CORE] = 1;
        this.health[BaseLayout.RELAY] = 1;
        this.energy = ENERGY_TICKS;
        this.updated = now;
        this.buildDelay = BUILD_SECONDS;
    }

    /** 巢穴派来的中继站补上了被打掉的那座：能量又满了。基地瓦解了、中继站还在时不补。 */
    public boolean restoreRelay() {
        if (this.status != Status.ACTIVE || this.relayAlive()) {
            return false;
        }
        this.health[BaseLayout.RELAY] = 1;
        this.energy = ENERGY_TICKS;
        return true;
    }

    public void voidSite() {
        this.status = Status.VOID;
    }

    /** 结算到 {@code now}：按秒走，不满一秒的留到下次。 */
    public void advanceTo(long now, Environment environment) {
        if (this.status != Status.ACTIVE) {
            this.updated = now;
            return;
        }
        long elapsed = now - this.updated;
        if (elapsed > MAX_CATCH_UP_TICKS) {
            this.updated = now - MAX_CATCH_UP_TICKS;
            elapsed = MAX_CATCH_UP_TICKS;
        }
        long seconds = Math.max(0, elapsed / 20);
        for (long i = 0; i < seconds; i++) {
            this.second(environment);
        }
        this.updated += seconds * 20;
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
        if (this.relayAlive()) {
            this.energy = ENERGY_TICKS;
        } else {
            this.energy = Math.max(0, this.energy - 20);
        }
        if (!this.powered()) {
            return;
        }
        // 物质攒满了照样长经验：放着不管的基地也会慢慢变强
        double mined = MATTER_PER_SECOND * environment.growth();
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
    }

    /** 盖下一座：顺序里第一个缺着、等级够的槽位，物质够就盖；一座一座来，刚被打掉过就先缓一阵。 */
    private void build() {
        if (this.buildDelay > 0) {
            this.buildDelay--;
            return;
        }
        for (int i = 0; i < this.health.length; i++) {
            if (this.health[i] > 0 || !BaseLayout.rebuildable(i)) {
                continue;
            }
            BaseLayout.Slot slot = BaseLayout.slot(i);
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
        for (UnitKind kind : UnitKind.values()) {
            int k = kind.ordinal();
            int camps = this.camps(kind);
            int capacity = camps * BaseLayout.campCapacity(kind);
            if (this.units[k] >= capacity) {
                this.units[k] = capacity;
                this.progress[k] = 0;
                continue;
            }
            double rate = camps * environment.growth() * (environment.overdrive() ? OVERDRIVE : 1) / unitSeconds(kind);
            this.progress[k] = Math.min(this.progress[k] + rate, Math.max(1, camps));
            while (this.progress[k] >= 1 && this.matter >= UNIT_COST && this.units[k] < capacity) {
                this.units[k]++;
                this.matter -= UNIT_COST;
                this.progress[k] -= 1;
            }
        }
    }

    /** 一个营地多少秒补一个单位。 */
    public static double unitSeconds(UnitKind kind) {
        return switch (kind) {
            case RAIDER -> 20;
            case RANGER -> 30;
            case GUARDIAN -> 45;
        };
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

    /** 基地挨打（不管谁打的）：伤害算经验，再涨威胁。 */
    public void hurt(double damage, double threatPerDamage) {
        if (this.status != Status.ACTIVE || damage <= 0) {
            return;
        }
        this.gainXp(damage);
        this.threat = Math.min(1, this.threat + damage * threatPerDamage);
    }

    /** 某座建筑被打掉了；打掉钻芯基地就没了。 */
    public void destroy(int slot) {
        this.health[slot] = 0;
        if (slot == BaseLayout.CORE) {
            this.status = Status.DESTROYED;
            Arrays.fill(this.health, 0);
            Arrays.fill(this.units, 0);
            Arrays.fill(this.progress, 0);
            this.threat = 0;
        } else {
            this.buildDelay = Math.max(this.buildDelay, REBUILD_DELAY_SECONDS);
            BaseLayout.Slot destroyed = BaseLayout.slot(slot);
            for (UnitKind kind : UnitKind.values()) {
                if (kind.camp() == destroyed.kind()) {
                    int capacity = this.camps(kind) * BaseLayout.campCapacity(kind);
                    this.units[kind.ordinal()] = Math.min(this.units[kind.ordinal()], capacity);
                }
            }
        }
    }

    /** 派出一波：从营地里扣掉。 */
    public Threat.Wave launch(int aggressiveness) {
        Threat.Wave wave = Threat.wave(this.units[UnitKind.RAIDER.ordinal()], this.units[UnitKind.RANGER.ordinal()], this.level, aggressiveness);
        this.units[UnitKind.RAIDER.ordinal()] -= wave.raiders();
        this.units[UnitKind.RANGER.ordinal()] -= wave.rangers();
        return wave;
    }

    /** 一波打完了（打退了或者撤了）：威胁清零，冷却。 */
    public void waveOver() {
        this.threat = 0;
        this.cooldown = Threat.COOLDOWN_TICKS;
    }

    /** 守家的单位死了一个。 */
    public void unitLost(UnitKind kind) {
        int k = kind.ordinal();
        this.units[k] = Math.max(0, this.units[k] - 1);
    }

    public int camps(UnitKind kind) {
        int count = 0;
        for (int i = 0; i < this.health.length; i++) {
            if (this.health[i] > 0 && BaseLayout.slot(i).kind() == kind.camp()) {
                count++;
            }
        }
        return count;
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

    public boolean relayAlive() {
        return this.health[BaseLayout.RELAY] > 0;
    }

    public boolean powered() {
        return this.status == Status.ACTIVE && this.energy > 0;
    }

    public boolean threatFull() {
        return this.threat >= 1 - 1e-6;
    }

    public int id() {
        return this.id;
    }

    public int x() {
        return this.x;
    }

    /** 钻芯脚下那一格（底板的高度）；地面还没压平时是 {@link Integer#MIN_VALUE}。 */
    public int y() {
        return this.y;
    }

    public int z() {
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

    public int energy() {
        return this.energy;
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

    /** 还要多少秒才能盖下一座。 */
    public int buildDelay() {
        return this.buildDelay;
    }

    public boolean ground() {
        return this.ground;
    }

    /** 地面压平、铺好了底板，记下底板的高度。 */
    public void groundBuilt(int y) {
        this.y = y;
        this.ground = true;
    }

    public int units(UnitKind kind) {
        return this.units[kind.ordinal()];
    }

    public void setUnits(UnitKind kind, int count) {
        this.units[kind.ordinal()] = Math.max(0, count);
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

    public long updated() {
        return this.updated;
    }
}
