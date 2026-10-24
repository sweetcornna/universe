package io.github.sweetcornna.universe.core.hazard;

import io.github.sweetcornna.universe.core.procgen.Rng;

/**
 * 火山喷发（ADR-0034 §六）：先 {@value #WARNING} 刻"地面在震动"，再 {@value #DURATION} 刻里每秒落一两颗熔岩弹，
 * 落在玩家 {@value #MIN_RANGE}–{@value #MAX_RANGE} 格外。熔岩弹从远处的火口抛出来，按抛物线飞，落地时让
 * {@value #BURN_RADIUS} 格内的人着火、烫伤，不炸方块。
 */
public final class EruptionPlan {
    public static final int WARNING = 3 * 20;
    public static final int DURATION = 20 * 20;
    public static final int MIN_RANGE = 8;
    public static final int MAX_RANGE = 40;
    public static final double BURN_RADIUS = 3;
    /** 火口离玩家多远（水平）。 */
    public static final int SOURCE_MIN = 36;
    public static final int SOURCE_MAX = 56;
    /** 熔岩弹每刻掉的速度（格/刻²），比原版的箭重一点，飞得高、落得陡。 */
    public static final double GRAVITY = 0.05;
    /** 正中挨一下掉几点血；往外按距离减到 1。 */
    static final float BURN_DAMAGE = 7;

    private EruptionPlan() {
    }

    public enum Phase {
        WARNING, ERUPTING, OVER
    }

    public static Phase phase(int age) {
        return age < WARNING ? Phase.WARNING : age < WARNING + DURATION ? Phase.ERUPTING : Phase.OVER;
    }

    /** 喷发一共多少刻（含预警）。 */
    public static int length() {
        return WARNING + DURATION;
    }

    /**
     * 这一刻抛出几颗：每秒第 0 刻一颗，第 10 刻一半的机会再一颗，所以每秒一两颗。
     *
     * @param age 喷发开始以后第几刻（含预警）
     */
    public static int bombs(int age, Rng rng) {
        if (phase(age) != Phase.ERUPTING) {
            return 0;
        }
        int tick = (age - WARNING) % 20;
        if (tick == 0) {
            return 1;
        }
        return tick == 10 && rng.chance(0.5) ? 1 : 0;
    }

    /**
     * 一颗熔岩弹落在哪：离喷发中心（玩家当时的位置）的水平偏移。
     *
     * @return {@code {dx, dz}}
     */
    public static double[] landing(Rng rng) {
        double distance = rng.range((double) MIN_RANGE, (double) MAX_RANGE);
        double angle = rng.nextDouble() * Math.PI * 2;
        return new double[] {Math.cos(angle) * distance, Math.sin(angle) * distance};
    }

    /** 火口在喷发中心的哪个方向、多远。 */
    public static double[] source(Rng rng) {
        double distance = rng.range((double) SOURCE_MIN, (double) SOURCE_MAX);
        double angle = rng.nextDouble() * Math.PI * 2;
        return new double[] {Math.cos(angle) * distance, Math.sin(angle) * distance};
    }

    /** 飞多少刻：远的飞得久一点，弧线差不多高。 */
    public static int flightTicks(double horizontal) {
        return (int) Math.clamp(Math.round(34 + horizontal * 0.45), 34, 80);
    }

    /** 落地处 {@code distance} 格的人挨几点；超出 {@value #BURN_RADIUS} 格为 0。 */
    public static float burn(double distance) {
        if (distance > BURN_RADIUS) {
            return 0;
        }
        return (float) Math.max(1, Math.round(BURN_DAMAGE * (1 - distance / BURN_RADIUS) * 2) / 2.0);
    }

    /** 落地处 {@code distance} 格的人着火几刻。 */
    public static int fireTicks(double distance) {
        return distance > BURN_RADIUS ? 0 : (int) Math.round(100 - 40 * distance / BURN_RADIUS);
    }

    /**
     * 抛出去的初速度：每刻先减去重力，再按速度走一步（和原版的投掷物一样；熔岩弹没有空气阻力），{@code ticks} 刻以后
     * 正好到 {@code (dx, dy, dz)}。
     *
     * @return {@code {vx, vy, vz}}（格/刻）
     */
    public static double[] launch(double dx, double dy, double dz, int ticks, double gravity) {
        if (ticks <= 0) {
            throw new IllegalArgumentException("ticks " + ticks);
        }
        double vy = (dy + gravity * ticks * (ticks + 1) / 2.0) / ticks;
        return new double[] {dx / ticks, vy, dz / ticks};
    }

    /** 按 {@link #launch} 的走法，{@code ticks} 刻以后到了哪（单元测试和预测落点用）。 */
    public static double[] position(double[] velocity, int ticks, double gravity) {
        double x = 0;
        double y = 0;
        double z = 0;
        double vy = velocity[1];
        for (int i = 0; i < ticks; i++) {
            vy -= gravity;
            x += velocity[0];
            y += vy;
            z += velocity[2];
        }
        return new double[] {x, y, z};
    }
}
