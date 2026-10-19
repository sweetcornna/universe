package io.github.sweetcornna.universe.core.ecology;

import io.github.sweetcornna.universe.core.ecology.CreatureSpecies.Dorsal;

/**
 * 喂养异星动物的规则（ADR-0017）：吃什么、多大机会驯服、喂过之后产出什么、能不能骑、骑着跑多快跳多高。
 * 只看物种的参数，适配层按这些数做喂食、驯服和骑乘。
 */
public final class Husbandry {
    /** 喂过之后多少刻产出一份产物（30 秒）。 */
    public static final int PRODUCE_DELAY = 600;
    /** 喂过之后多少刻才再产出（5 分钟）；驯服的这段时间里吃饱了，受了伤才吃。 */
    public static final int FEED_COOLDOWN = 6000;
    /** 野生的两口之间至少隔多少刻：驯服靠一口一口地喂，不受产出的五分钟限制（捕食者平均要喂十几口）。 */
    public static final int TAME_INTERVAL = 20;
    /** 体型到多大才驮得动人。 */
    public static final double RIDE_MIN_SIZE = 1.2;
    /** 吃饱了还喂（驯服的、受了伤的）回多少血。 */
    public static final float FEED_HEAL = 4;

    private Husbandry() {
    }

    /** 食性：温顺、易受惊的吃草，有领地意识的和捕食者吃肉。 */
    public enum Diet {
        HERBIVORE, CARNIVORE
    }

    /** 喂过之后的产物。 */
    public enum Product {
        /** 异星蛋：两足、会飞的，和背上长甲片、骨刺的四足兽。 */
        EGG,
        /** 异星绒毛：其余的四足兽。 */
        FLUFF,
        /** 生物胶质：六足虫和节蠕虫。 */
        GEL
    }

    public static Diet diet(Temperament temperament) {
        return temperament.fights() ? Diet.CARNIVORE : Diet.HERBIVORE;
    }

    /** 每喂一次驯服的机会：越凶越难。 */
    public static double tameChance(Temperament temperament) {
        return switch (temperament) {
            case DOCILE -> 0.35;
            case SKITTISH -> 0.25;
            case TERRITORIAL -> 0.15;
            case PREDATOR -> 0.08;
        };
    }

    public static Product product(CreatureSpecies species) {
        return switch (species.plan()) {
            case BIPED, FLYER -> Product.EGG;
            case HEXAPOD, SERPENT -> Product.GEL;
            case QUADRUPED -> species.body().dorsal() == Dorsal.PLATES || species.body().dorsal() == Dorsal.SPIKES ? Product.EGG : Product.FLUFF;
        };
    }

    /** 一次产几个：体型不到 1.2 产 1 个，1.2 起 2 个，2 起 3 个。 */
    public static int productCount(CreatureSpecies species) {
        return Math.clamp((int) Math.floor(species.size() * 1.25 + 0.5), 1, 3);
    }

    /** 驯服后能不能骑：四足、六足、两足，体型到 {@link #RIDE_MIN_SIZE}。 */
    public static boolean rideable(CreatureSpecies species) {
        return species.size() >= RIDE_MIN_SIZE && switch (species.plan()) {
            case QUADRUPED, HEXAPOD, BIPED -> true;
            case SERPENT, FLYER -> false;
        };
    }

    /** 骑着跑的速度（原版移动速度属性；马在 0.11 到 0.34 之间，玩家走路 0.1）：按物种的速度，限在 0.16 到 0.32。 */
    public static double rideSpeed(CreatureSpecies species) {
        return Math.clamp(species.speed(), 0.16, 0.32);
    }

    /** 骑着蓄满力起跳的竖直速度（格/刻）：两足兽腿长跳得最高（约 4 格），四足兽约 3 格，六足虫约 2 格（原版不蓄力的跳是 0.42，1.25 格）。 */
    public static double rideJump(CreatureSpecies species) {
        return switch (species.plan()) {
            case BIPED -> 0.8;
            case QUADRUPED -> 0.68;
            default -> 0.56;
        };
    }
}
