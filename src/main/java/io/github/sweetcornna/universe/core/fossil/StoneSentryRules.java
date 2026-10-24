package io.github.sweetcornna.universe.core.fossil;

/**
 * 石哨兵的规矩（ADR-0034 §三）：发掘场边上睡着几块石头，有人在旁边挖化石土，石头一次比一次躁动；挖到第
 * {@value #WAKE_DIGS} 块就醒过来，浮起来朝人扔石块，血少到 {@value #FUSE_HEALTH} 以下冲过来自爆。
 */
public final class StoneSentryRules {
    /** 挖几块化石土醒过来。 */
    public static final int WAKE_DIGS = 3;
    /** 挖化石土时，多远以内睡着的石哨兵会被吵到（水平方向，格）。 */
    public static final int HEARING = 20;
    /** 竖直方向。 */
    public static final int HEARING_HEIGHT = 8;

    public static final double MAX_HEALTH = 40;
    public static final double ARMOR = 6;
    /** 石块打中一下（普通难度）。 */
    public static final float ROCK_DAMAGE = 4.0F;
    /** 两次扔石块之间隔几刻（再随机加一点）。 */
    public static final int THROW_INTERVAL = 40;
    /** 扔得到多远。 */
    public static final double THROW_RANGE = 24;
    /** 血量比例低于这个就冲过去自爆。 */
    public static final double FUSE_HEALTH = 0.3;
    /** 点着以后多少刻炸；冲到 {@link #FUSE_REACH} 以内提前炸。 */
    public static final int FUSE_TICKS = 40;
    public static final double FUSE_REACH = 2.0;
    /** 自爆的威力（和原版爆炸的半径一个意思）：不炸方块，只伤人。 */
    public static final float BLAST_RADIUS = 2.6F;
    /** 打下来掉几块骨头。 */
    public static final int MIN_DROPS = 1;
    public static final int MAX_DROPS = 2;

    private StoneSentryRules() {
    }

    /** 又挖了一块化石土：躁动加一级。返回新的躁动级别；到 {@value #WAKE_DIGS} 就该醒了。 */
    public static int disturb(int unrest) {
        return Math.min(WAKE_DIGS, Math.max(0, unrest) + 1);
    }

    public static boolean awake(int unrest) {
        return unrest >= WAKE_DIGS;
    }

    /** 该不该点着自爆。 */
    public static boolean fuse(double health, double maxHealth) {
        return maxHealth > 0 && health / maxHealth < FUSE_HEALTH;
    }

    /** 石块飞多少刻：远的飞得久，10–24 刻，跑着躲得开。 */
    public static int flightTicks(double distance) {
        return (int) Math.max(10, Math.min(24, Math.round(distance * 0.75)));
    }
}
