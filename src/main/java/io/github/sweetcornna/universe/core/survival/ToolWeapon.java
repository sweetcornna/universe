package io.github.sweetcornna.universe.core.survival;

/**
 * 装进多功能工具槽里的武器（ADR-0026 §六）。研究后在制造台里做，装上以后按切换键能切到它。
 * 只能往后加，免得存档里的模式对不上。
 *
 * <p>伤害、射速和爆能枪一样吃工具上的加成：伤害按 {@code 基础值 × (1 + 伤害加成)}，射速（标枪是蓄力快慢）按
 * {@code 基础值 × (1 + 射速加成)}；升温和采矿激光共用一份热量，每种武器有自己的升温倍数。
 */
public enum ToolWeapon {
    /** 散射爆能枪：一发 8 颗弹丸，每颗 1.5，16 格以内，1.2 秒一发。 */
    SCATTER_BLASTER("scatter_blaster", 1.5, 1 / 1.2, 0.5F, 8, 16, 0xFFB45A),
    /** 脉冲喷射器：每颗 1.5，每秒 10 发，散布大，按住 3 秒多就过热。 */
    PULSE_SPITTER("pulse_spitter", 1.5, 10, 1.25F, 1, 48, 0xC8FF5A),
    /** 烈焰标枪：按住蓄力 1.5 秒，松开立刻打中 120 格内的第一个目标，18 点。射速加成让蓄力变快。 */
    BLAZE_JAVELIN("blaze_javelin", 18, 0, 0.6F, 1, 120, 0xFF6A2A),
    /** 等离子发射器：会弹跳的榴弹，2 秒后炸开，半径 3 格 10 点，不破坏方块，3 秒一发。 */
    PLASMA_LAUNCHER("plasma_launcher", 10, 1 / 3.0, 0.5F, 1, 3, 0xD46AFF);

    /** 弹丸和脉冲弹每刻飞几格（和爆能枪一样）。 */
    public static final double BOLT_SPEED = PlasmaWeapon.SPEED;
    /** 散射的散布（原版弹射物的含义）：16 格外散开两三格。 */
    public static final float SCATTER_SPREAD = 7F;
    /** 脉冲喷射器的散布：比爆能枪大得多，远了打不准。 */
    public static final float PULSE_SPREAD = 4F;
    /** 标枪蓄满要几刻（没有射速加成时）。 */
    public static final int CHARGE_TICKS = 30;
    /** 榴弹离开枪口多少刻后炸开。 */
    public static final int GRENADE_FUSE = 40;
    /** 榴弹爆炸边上剩几成伤害：爆心满伤害，往外线性减少。 */
    public static final double GRENADE_EDGE = 0.5;
    /** 榴弹出膛速度（格/刻）。 */
    public static final double GRENADE_SPEED = 1.1;
    /** 榴弹撞到方块弹回来剩几成速度。 */
    public static final double GRENADE_BOUNCE = 0.45;

    private final String id;
    private final double damage;
    private final double rate;
    private final float heatFactor;
    private final int projectiles;
    private final double range;
    private final int color;

    ToolWeapon(String id, double damage, double rate, float heatFactor, int projectiles, double range, int color) {
        this.id = id;
        this.damage = damage;
        this.rate = rate;
        this.heatFactor = heatFactor;
        this.projectiles = projectiles;
        this.range = range;
        this.color = color;
    }

    /** 物品的注册名，也是语言键的后缀。 */
    public String id() {
        return this.id;
    }

    /** 一颗弹丸（榴弹是爆心）的基础伤害。 */
    public double baseDamage() {
        return this.damage;
    }

    /** 加成后一颗弹丸的伤害。 */
    public float damage(double damageBoost) {
        return (float) (this.damage * damageBoost);
    }

    /** 每秒几发；标枪是蓄力的，0。 */
    public double rate() {
        return this.rate;
    }

    /** 按住扳机时的升温是采矿激光的几倍。 */
    public float heatFactor() {
        return this.heatFactor;
    }

    /** 一发打出几颗弹丸。 */
    public int projectiles() {
        return this.projectiles;
    }

    /** 射程（格）；榴弹是爆炸半径。 */
    public double range() {
        return this.range;
    }

    /** 弹体、光束的颜色（RGB）。 */
    public int color() {
        return this.color;
    }

    /** 弹体按 {@code speed} 每刻飞，飞够射程要几刻。 */
    public int lifetime(double speed) {
        return (int) Math.ceil(this.range / speed);
    }

    /** 标枪蓄满要几刻：射速加成让它变快。 */
    public static int chargeTicks(double rateBoost) {
        return Math.max(1, (int) Math.round(CHARGE_TICKS / rateBoost));
    }

    /** 离榴弹爆心 {@code distance} 格处挨的伤害；半径外是 0。 */
    public static float grenadeDamage(double distance, double damageBoost) {
        double radius = PLASMA_LAUNCHER.range;
        if (distance > radius) {
            return 0;
        }
        double falloff = 1 - (1 - GRENADE_EDGE) * Math.max(0, distance) / radius;
        return (float) (PLASMA_LAUNCHER.damage * damageBoost * falloff);
    }
}
