package io.github.sweetcornna.universe.core.chem;

/**
 * 电解槽的五条配方（ADR-0036 §三）用多少电。
 *
 * <p>不做永动机：电解出的氢再拿去发电，拿回的电要比电解用掉的少。氢最多能换回的电是做成液氢燃料棒再烧：
 * 10 个氢做 2 根燃料棒，每根在火力发电厂烧 31700 刻、每刻 15 J，合每个氢 95100 J（直接烧只有 70500 J）。
 * 电解每出一个氢至少要用掉它的 {@link #MIN_RATIO} 倍。重氢拿去聚变是核能，不受这条限制。
 *
 * <p>现实里电解水每摩尔氢约 360 kJ（每千克 50 kWh），氯碱每摩尔氯约 600 kJ，唐斯电解槽每摩尔钠约 830 kJ，
 * MOXIE 分解二氧化碳的焓变和电解水差不多。游戏里氢的燃料值是照戴森球计划定的，所以电解的电照氢的燃料值折算，
 * 几条配方之间的比例照现实。电积铜现实里每千克 2 kWh 上下，和冶炼差不多，照游戏里冶炼的电定，不和氢比。
 */
public final class Electrolysis {
    /** 氢在火力发电厂直接烧：4700 刻 × 每刻 15 J（{@code ThermalPlantBlockEntity.JOULES_PER_BURN_TICK}）。 */
    public static final long HYDROGEN_BURN_JOULES = 4700L * 15;
    /** 氢做成液氢燃料棒再烧，每个氢换回的电：2 × 31700 × 15 / 10。 */
    public static final long HYDROGEN_ROD_JOULES = 2L * 31700 * 15 / 10;
    /** 电解每出一个氢，用电至少是氢最多能换回的电的几倍。 */
    public static final double MIN_RATIO = 1.4;
    /** 电解槽的功率（J/刻，戴森球计划的 10 MW）。 */
    public static final long POWER = 500;

    /**
     * 一条配方。
     *
     * @param id       配方名
     * @param power    建议的功率（J/刻）
     * @param ticks    做一次几刻
     * @param hydrogen 做一次出几个氢
     */
    public enum Recipe {
        /** 2H₂O → 2H₂ + O₂：2 水 → 2 氢 + 1 氧。 */
        WATER("water", POWER, 560, 2),
        /** 2NaCl + 2H₂O → Cl₂ + H₂ + 2NaOH：2 盐 + 2 水 → 1 氯气 + 1 氢 + 2 氢氧化钠。比电解水每摩尔多用约 1.7 倍。 */
        BRINE("brine", POWER, 480, 1),
        /** 2NaCl → 2Na + Cl₂：2 盐 → 2 钠 + 1 氯气。现实里每摩尔钠是电解水每摩尔氢的 2.3 倍，这里按 2 倍。 */
        MOLTEN_SALT("molten_salt", POWER, 1120, 0),
        /** 2CO₂ → 2CO + O₂（照毅力号的 MOXIE）：和电解水一样多。 */
        MOXIE("moxie", POWER, 560, 0),
        /** 2CuSO₄ + 2H₂O → 2Cu + 2H₂SO₄ + O₂：2 浸出液 → 2 铜锭 + 1 硫酸。照冶炼的电定。 */
        ELECTROWINNING("electrowinning", 100, 60, 0);

        private final String id;
        private final long power;
        private final int ticks;
        private final int hydrogen;

        Recipe(String id, long power, int ticks, int hydrogen) {
            this.id = id;
            this.power = power;
            this.ticks = ticks;
            this.hydrogen = hydrogen;
        }

        public String id() {
            return this.id;
        }

        public long power() {
            return this.power;
        }

        public int ticks() {
            return this.ticks;
        }

        public int hydrogen() {
            return this.hydrogen;
        }

        /** 做一次用多少电（J）。 */
        public long joules() {
            return this.power * this.ticks;
        }

        /** 每出一个氢用多少电；不出氢为 0。 */
        public double joulesPerHydrogen() {
            return this.hydrogen == 0 ? 0 : (double) this.joules() / this.hydrogen;
        }

        /** 用这台机器的功率（J/刻）做一次要几刻，保持用电不变。 */
        public int ticksAt(long machinePower) {
            return (int) Math.ceil((double) this.joules() / machinePower);
        }
    }

    private Electrolysis() {
    }

    /** 氢最多能换回的电（J）。 */
    public static long hydrogenRecovery() {
        return Math.max(HYDROGEN_BURN_JOULES, HYDROGEN_ROD_JOULES);
    }
}
