package io.github.sweetcornna.universe.core.ship;

import java.util.SplittableRandom;

/**
 * 海盗战机的飞行和开火（ADR-0017）：一架战机一个实例，每刻调用一次 {@link #step}。向量都是 {x, y, z} 的 double 数组，
 * 速度的单位是格/刻。
 *
 * <p>战机一直在飞，转弯靠有限的加速度，所以绕的是大圈。打法是一轮一轮地俯冲：
 * <ul>
 * <li>{@link Mode#ATTACK 进攻}：冲向目标（带提前量），每架从不同的侧面切进来，离得远时散开、离得近时收拢，几架交叉着打；
 * 机头对准目标、进了射程就打一梭三发。
 * <li>{@link Mode#EXTEND 拉开}：冲到跟前以后顺着来势斜着飞过去，拉开一段距离再掉头进攻。
 * <li>{@link Mode#REGROUP 后撤}：船体剩三成五以下时退到 {@link #REGROUP_RANGE} 格外绕圈，等护盾回满再回来；之后一段时间不再后撤。
 * <li>{@link Mode#LEAVE 撤走}：袭击结束（玩家跃迁、降落、飞进空间站附近）时背对目标加速飞走。
 * </ul>
 */
public final class PirateFlight {
    public enum Mode {
        ATTACK,
        EXTEND,
        REGROUP,
        LEAVE
    }

    /** 巡航速度、撤走时的速度、最低速度（格/刻）。 */
    public static final double SPEED = 2.2;
    public static final double LEAVE_SPEED = 3.0;
    public static final double MIN_SPEED = 0.6;
    /** 每刻最多改变多少速度：决定转弯半径（约 {@code SPEED² / ACCEL} 格）。 */
    public static final double ACCEL = 0.14;
    /** 机头偏离目标这么多度以内、这么近以内才开火。 */
    public static final double FIRE_CONE = 10;
    public static final double FIRE_RANGE = 90;
    /** 一梭几发、两发隔几刻；两梭之间冷却多少刻。 */
    public static final int BURST = 3;
    public static final int BURST_GAP = 3;
    public static final int COOLDOWN_MIN = 25;
    public static final int COOLDOWN_MAX = 45;
    /** 弹速（相对战机，格/刻）。 */
    public static final double BOLT_SPEED = 3.5;
    /** 离目标（的外沿）这么近就拉开；拉开到这么远、或者飞了这么多刻就掉头。 */
    public static final double BREAK_DISTANCE = 14;
    public static final double EXTEND_DISTANCE = 55;
    public static final int EXTEND_MAX_TICKS = 70;
    /** 船体剩几成以下后撤，退到多远；后撤至少多少刻，回来以后多少刻内不再后撤。 */
    public static final double REGROUP_HULL = 0.35;
    public static final double REGROUP_RANGE = 80;
    public static final int REGROUP_MIN_TICKS = 120;
    public static final int REGROUP_COOLDOWN = 400;
    private static final double[] UP = {0, 1, 0};

    private final SplittableRandom random;
    /** 这一架的巡航速度和每刻最多改变的速度（拦截机比海盗快，转弯也跟着利索）。 */
    private final double speed;
    private final double accel;
    /** 从哪个侧面切进来（绕着视线的角度，弧度）。 */
    private final double side;
    /** 后撤时绕圈的方向。 */
    private final double orbit;
    private Mode mode = Mode.ATTACK;
    private int timer;
    private int burstLeft;
    private int burstGap;
    private int cooldown;
    private int regroupCooldown;
    private double[] extendDirection = {0, 0, 1};

    /**
     * @param seed  随机数种子
     * @param slot  这是第几架（0 起）
     * @param count 这一波一共几架：几架平分切入的侧面
     */
    public PirateFlight(long seed, int slot, int count) {
        this(seed, slot, count, 1);
    }

    /** @param speedFactor 速度倍率：海盗是 1，太空守卫机的拦截机快一些 */
    public PirateFlight(long seed, int slot, int count, double speedFactor) {
        this.speed = SPEED * speedFactor;
        this.accel = ACCEL * speedFactor;
        this.random = new SplittableRandom(seed);
        this.side = Math.PI * 2 * slot / Math.max(1, count) + this.random.nextDouble() * 0.6;
        this.orbit = this.random.nextBoolean() ? 1 : -1;
        this.cooldown = this.random.nextInt(10, 30);
    }

    /**
     * 这一刻的结果。
     *
     * @param velocity 新的速度
     * @param fire     这一刻打一发
     */
    public record Step(double[] velocity, boolean fire) {
    }

    public Mode mode() {
        return this.mode;
    }

    /** 袭击结束：撤走。 */
    public void leave() {
        this.mode = Mode.LEAVE;
        this.burstLeft = 0;
    }

    /**
     * 推进一刻。
     *
     * @param pos       战机的位置
     * @param vel       战机现在的速度
     * @param target    目标的中心
     * @param targetVel 目标的速度
     * @param clearance 目标的半径：离它的外沿算距离，免得撞上大船
     * @param hull      战机船体剩几成（0–1）
     * @param shield    战机护盾剩几成（0–1）
     */
    public Step step(double[] pos, double[] vel, double[] target, double[] targetVel, double clearance, double hull, double shield) {
        double[] to = sub(target, pos);
        double distance = ShipCombat.length(to);
        double[] dir = distance > 1e-6 ? scale(to, 1 / distance) : new double[] {0, 0, 1};
        double speed = ShipCombat.length(vel);
        double[] forward = speed > 1e-6 ? scale(vel, 1 / speed) : dir;
        double gap = distance - clearance;
        if (this.regroupCooldown > 0) {
            this.regroupCooldown--;
        }
        if (this.mode != Mode.LEAVE && this.mode != Mode.REGROUP && hull < REGROUP_HULL && this.regroupCooldown == 0) {
            this.mode = Mode.REGROUP;
            this.timer = 0;
            this.burstLeft = 0;
        }

        double[] lateral = this.lateral(dir);
        double[] desired;
        double maxSpeed = this.speed;
        switch (this.mode) {
            case ATTACK -> {
                double lead = Math.min(40, distance / this.speed);
                double[] aim = add(target, scale(targetVel, lead));
                // 远处散开，近处收拢：几架从不同的方向同时切过来
                double offset = Math.min(14, distance * 0.15);
                desired = scale(ShipCombat.normalize(sub(add(aim, scale(lateral, offset)), pos)), this.speed);
                if (gap < BREAK_DISTANCE) {
                    this.mode = Mode.EXTEND;
                    this.timer = 0;
                    double climb = (this.random.nextDouble() - 0.5) * 0.4;
                    this.extendDirection = ShipCombat.normalize(add(add(forward, scale(lateral, 0.9)), scale(UP, climb)));
                }
            }
            case EXTEND -> {
                this.timer++;
                desired = scale(this.extendDirection, this.speed);
                if (this.timer > 20 && gap > EXTEND_DISTANCE || this.timer > EXTEND_MAX_TICKS) {
                    this.mode = Mode.ATTACK;
                }
            }
            case REGROUP -> {
                this.timer++;
                double[] tangent = scale(ShipCombat.normalize(cross(dir, UP)), this.orbit);
                if (distance < REGROUP_RANGE) {
                    desired = scale(ShipCombat.normalize(add(scale(dir, -1), scale(tangent, 0.4))), this.speed);
                } else {
                    desired = scale(ShipCombat.normalize(add(tangent, scale(dir, (distance - REGROUP_RANGE) / 20))), this.speed * 0.8);
                }
                if (this.timer > REGROUP_MIN_TICKS && shield >= 0.999) {
                    this.mode = Mode.ATTACK;
                    this.regroupCooldown = REGROUP_COOLDOWN;
                }
            }
            default -> {
                maxSpeed = LEAVE_SPEED;
                desired = scale(ShipCombat.normalize(add(scale(dir, -1), scale(UP, 0.3))), LEAVE_SPEED);
            }
        }
        // 别撞上目标
        if (gap < 5 && this.mode != Mode.LEAVE) {
            desired = add(desired, scale(dir, -this.speed * 1.5));
        }

        double[] change = sub(desired, vel);
        double limit = this.mode == Mode.LEAVE ? this.accel * 1.5 : this.accel;
        double length = ShipCombat.length(change);
        if (length > limit) {
            change = scale(change, limit / length);
        }
        double[] next = add(vel, change);
        double nextSpeed = ShipCombat.length(next);
        if (nextSpeed < MIN_SPEED) {
            next = scale(nextSpeed > 1e-6 ? scale(next, 1 / nextSpeed) : forward, MIN_SPEED);
        } else if (nextSpeed > maxSpeed) {
            next = scale(next, maxSpeed / nextSpeed);
        }

        return new Step(next, this.fire(ShipCombat.normalize(next), dir, distance));
    }

    private boolean fire(double[] nose, double[] dir, double distance) {
        if (this.cooldown > 0) {
            this.cooldown--;
        }
        if (this.burstGap > 0) {
            this.burstGap--;
        }
        if (this.mode == Mode.LEAVE || this.mode == Mode.REGROUP) {
            return false;
        }
        if (this.burstLeft == 0 && this.cooldown == 0 && this.mode == Mode.ATTACK && distance < FIRE_RANGE && distance > 6
                && ShipCombat.angle(nose, dir) < FIRE_CONE) {
            this.burstLeft = BURST;
            this.burstGap = 0;
        }
        if (this.burstLeft > 0 && this.burstGap == 0) {
            this.burstLeft--;
            this.burstGap = BURST_GAP;
            if (this.burstLeft == 0) {
                this.cooldown = this.random.nextInt(COOLDOWN_MIN, COOLDOWN_MAX + 1);
            }
            return true;
        }
        return false;
    }

    /** 和视线垂直、按 {@link #side} 绕视线转过的方向：以水平为主，上下只占三成五（玩家的船不会俯仰，炮只能抬高 60°）。 */
    private double[] lateral(double[] dir) {
        double[] right = cross(dir, UP);
        if (ShipCombat.length(right) < 1e-6) {
            right = new double[] {1, 0, 0};
        }
        right = ShipCombat.normalize(right);
        double[] up = cross(right, dir);
        return ShipCombat.normalize(add(scale(right, Math.cos(this.side)), scale(up, Math.sin(this.side) * 0.35)));
    }

    static double[] add(double[] a, double[] b) {
        return new double[] {a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    static double[] sub(double[] a, double[] b) {
        return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    static double[] scale(double[] a, double f) {
        return new double[] {a[0] * f, a[1] * f, a[2] * f};
    }

    static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }
}
