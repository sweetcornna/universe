package io.github.sweetcornna.universe.core.survival;

import java.util.EnumMap;
import java.util.Map;

import io.github.sweetcornna.universe.core.galaxy.Hazard;

/**
 * 外骨骼的生命维持和危害防护（玩法设计 §3.1–3.2）。每个玩家一份，每刻按环境推进一次。
 *
 * <ul>
 *   <li><b>氧气</b>：不能呼吸的地方持续消耗。穿着密封的外骨骼（头盔和胸甲）能撑 10 分钟，不穿只能憋 15 秒；
 *       耗尽后每秒掉 2 点血。能呼吸的大气里、飞船里很快补满。</li>
 *   <li><b>防护</b>：每种危害一条。暴露在危害里持续下降，整套外骨骼标准强度下撑 5 分钟，缺件更快，
 *       不穿只有半分钟；降到零后每两秒掉 1 点血。室内、旁边有热源（防寒）或冷源（防热）时慢下来，
 *       风暴时加快。离开危害后慢慢恢复。</li>
 *   <li><b>喷气背包</b>：胸甲上的，空中推进时消耗，落地很快充满。</li>
 * </ul>
 *
 * <p>消耗品（氧气罐、防护电池）由适配层在数值低时自动使用，调 {@link #addOxygen}、{@link #rechargeHazards}。
 */
public final class LifeSupport {
    public static final float OXYGEN_MAX = 100;
    public static final float HAZARD_MAX = 100;
    public static final float JET_MAX = 100;
    /** 密封的外骨骼：10 分钟耗尽。 */
    static final float SUIT_OXYGEN_DRAIN = OXYGEN_MAX / (10 * 60 * 20);
    /** 没有密封：15 秒耗尽。 */
    static final float BARE_OXYGEN_DRAIN = OXYGEN_MAX / (15 * 20);
    static final float OXYGEN_REFILL = OXYGEN_MAX / (5 * 20);
    /** 整套外骨骼、标准强度：5 分钟耗尽。 */
    static final float HAZARD_DRAIN = HAZARD_MAX / (5 * 60 * 20);
    static final float HAZARD_RECOVER = HAZARD_MAX / (25 * 20);
    static final float HAZARD_REFILL = HAZARD_MAX / (5 * 20);
    /** 喷气背包连续推进 2.5 秒，落地 2 秒充满。 */
    public static final float JET_BURN = JET_MAX / 50;
    public static final float JET_RECHARGE = JET_MAX / 40;
    /** 氧气耗尽后每 20 刻掉 2 点血。 */
    public static final float SUFFOCATION_DAMAGE = 2;
    static final int SUFFOCATION_INTERVAL = 20;
    /** 防护耗尽后每 40 刻掉 1 点血。 */
    public static final float HAZARD_DAMAGE = 1;
    static final int HAZARD_INTERVAL = 40;

    private float oxygen = OXYGEN_MAX;
    private final EnumMap<Hazard, Float> protection = new EnumMap<>(Hazard.class);
    private float jet = JET_MAX;
    private int suffocationTimer;
    private final EnumMap<Hazard, Integer> hazardTimers = new EnumMap<>(Hazard.class);

    public LifeSupport() {
        for (Hazard hazard : Hazard.values()) {
            this.protection.put(hazard, HAZARD_MAX);
        }
    }

    public LifeSupport(float oxygen, Map<Hazard, Float> protection, float jet) {
        this();
        this.oxygen = clamp(oxygen, OXYGEN_MAX);
        protection.forEach((hazard, value) -> this.protection.put(hazard, clamp(value, HAZARD_MAX)));
        this.jet = clamp(jet, JET_MAX);
    }

    /** 这一刻造成的伤害。 */
    public record Damage(float suffocation, Map<Hazard, Float> hazards) {
        public static final Damage NONE = new Damage(0, Map.of());

        public boolean any() {
            return this.suffocation > 0 || !this.hazards.isEmpty();
        }
    }

    /**
     * 推进一刻。
     *
     * @param jetting 这一刻喷气背包在推进（适配层确认过玩家穿着胸甲、在空中）
     * @param onGround 站在地上（喷气背包充电）
     */
    public Damage tick(Environment env, Suit suit, boolean jetting, boolean onGround) {
        float suffocation = this.tickOxygen(env, suit);
        Map<Hazard, Float> hazardDamage = Map.of();
        for (Hazard hazard : Hazard.values()) {
            float damage = this.tickHazard(hazard, env, suit);
            if (damage > 0) {
                if (hazardDamage.isEmpty()) {
                    hazardDamage = new EnumMap<>(Hazard.class);
                }
                hazardDamage.put(hazard, damage);
            }
        }
        if (jetting && suit.chest() && this.jet > 0) {
            this.jet = Math.max(0, this.jet - JET_BURN);
        } else if (onGround || env.supplied()) {
            this.jet = Math.min(JET_MAX, this.jet + JET_RECHARGE);
        }
        return suffocation == 0 && hazardDamage.isEmpty() ? Damage.NONE : new Damage(suffocation, hazardDamage);
    }

    private float tickOxygen(Environment env, Suit suit) {
        if (!env.needsOxygen()) {
            this.oxygen = Math.min(OXYGEN_MAX, this.oxygen + OXYGEN_REFILL);
            this.suffocationTimer = 0;
            return 0;
        }
        this.oxygen = Math.max(0, this.oxygen - (suit.sealed() ? SUIT_OXYGEN_DRAIN : BARE_OXYGEN_DRAIN));
        if (this.oxygen > 0) {
            this.suffocationTimer = 0;
            return 0;
        }
        // 刚耗尽时立刻掉一次血，之后按间隔
        if (this.suffocationTimer++ % SUFFOCATION_INTERVAL == 0) {
            return SUFFOCATION_DAMAGE;
        }
        return 0;
    }

    private float tickHazard(Hazard hazard, Environment env, Suit suit) {
        float value = this.protection.get(hazard);
        if (env.supplied()) {
            this.protection.put(hazard, Math.min(HAZARD_MAX, value + HAZARD_REFILL));
            this.hazardTimers.remove(hazard);
            return 0;
        }
        Double severity = env.hazards().get(hazard);
        if (severity == null) {
            this.protection.put(hazard, Math.min(HAZARD_MAX, value + HAZARD_RECOVER));
            this.hazardTimers.remove(hazard);
            return 0;
        }
        float drain = (float) (HAZARD_DRAIN * severity * suit.hazardFactor() * modifier(hazard, env));
        value = Math.max(0, value - drain);
        this.protection.put(hazard, value);
        if (value > 0) {
            this.hazardTimers.remove(hazard);
            return 0;
        }
        int timer = this.hazardTimers.getOrDefault(hazard, 0);
        this.hazardTimers.put(hazard, timer + 1);
        return timer % HAZARD_INTERVAL == 0 ? HAZARD_DAMAGE : 0;
    }

    /** 环境对消耗速度的影响。 */
    static double modifier(Hazard hazard, Environment env) {
        double factor = 1;
        if (env.sheltered()) {
            // 毒气会渗进室内，遮挡帮助小一些
            factor *= hazard == Hazard.TOXIC ? 0.6 : 1 / 3.0;
        }
        if (hazard == Hazard.COLD && env.nearHeat() || hazard == Hazard.HEAT && env.nearCold()) {
            factor *= 0.25;
        }
        if (!env.sheltered()) {
            factor *= switch (env.storm()) {
                case 0 -> 1.0;
                case 1 -> 1.3;
                default -> 2.0;
            };
        }
        return factor;
    }

    public float oxygen() {
        return this.oxygen;
    }

    public float protection(Hazard hazard) {
        return this.protection.get(hazard);
    }

    public Map<Hazard, Float> protection() {
        return Map.copyOf(this.protection);
    }

    public float jet() {
        return this.jet;
    }

    public boolean canJet() {
        return this.jet > 0;
    }

    public void addOxygen(float amount) {
        this.oxygen = clamp(this.oxygen + amount, OXYGEN_MAX);
    }

    /** 防护电池：每条防护都补一些。 */
    public void rechargeHazards(float amount) {
        for (Hazard hazard : Hazard.values()) {
            this.protection.put(hazard, clamp(this.protection.get(hazard) + amount, HAZARD_MAX));
        }
    }

    /** 补满（重生、创造模式）。 */
    public void refill() {
        this.oxygen = OXYGEN_MAX;
        this.protection.replaceAll((hazard, value) -> HAZARD_MAX);
        this.jet = JET_MAX;
        this.suffocationTimer = 0;
        this.hazardTimers.clear();
    }

    /** 这条防护在当前环境里最需要补：暴露中、而且低于给定比例。 */
    public boolean low(Hazard hazard, Environment env, float share) {
        return env.exposedTo(hazard) && this.protection.get(hazard) < HAZARD_MAX * share;
    }

    private static float clamp(float value, float max) {
        return Math.clamp(value, 0, max);
    }
}
