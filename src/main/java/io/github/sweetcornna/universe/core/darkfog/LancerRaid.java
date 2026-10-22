package io.github.sweetcornna.universe.core.darkfog;

/**
 * 一次枪骑轰炸的阶段（ADR-0028 §八），每刻推进一次：
 *
 * <ul>
 *   <li>{@link Phase#WARNING 预警}：{@link #WARNING_TICKS} 刻，到点在目标上空（离地 {@link #SPAWN_HEIGHT} 格）放出来。</li>
 *   <li>{@link Phase#DIVE 俯冲}：降到离地 {@link #BOMB_HEIGHT} 格；都到了，或者到点了，开始轰炸。</li>
 *   <li>{@link Phase#BOMB 轰炸}：在目标一带的设施上空扔炸弹。炸弹扔完、到点、或者打得只剩
 *       {@link #RETREAT_SURVIVORS} 架以下，就撤。</li>
 *   <li>{@link Phase#LEAVE 撤走}：爬升，到了离地 {@link #LEAVE_HEIGHT} 格消失；都没了或者到点就结束。</li>
 * </ul>
 */
public final class LancerRaid {
    public enum Phase {
        WARNING,
        DIVE,
        BOMB,
        LEAVE,
        OVER
    }

    /** 预警多少刻。 */
    public static final int WARNING_TICKS = 20 * 20;
    /** 放出来的高度、轰炸的高度、撤走时消失的高度（离地多少格）。 */
    public static final double SPAWN_HEIGHT = 70;
    public static final double BOMB_HEIGHT = 28;
    public static final double LEAVE_HEIGHT = 110;
    /** 离地这么高以上算高空目标（只有导弹炮塔、等离子炮塔打得到）。 */
    public static final double HIGH_ALTITUDE = 16;
    /** 离轰炸高度这么近就算到了。 */
    public static final double ARRIVED = 4;
    /** 底下有护盾的穹顶时，停在穹顶外壳以上这么高扔炸弹（钻不进穹顶）。 */
    public static final double DOME_CLEARANCE = 8;

    /** 在这一列扔炸弹的高度：离地 {@link #BOMB_HEIGHT} 格，有穹顶盖着就停在穹顶外面。{@code dome} 是穹顶外壳的高度，没有是 NaN。 */
    public static double bombAltitude(double ground, double dome) {
        double y = ground + BOMB_HEIGHT;
        return Double.isNaN(dome) ? y : Math.max(y, dome + DOME_CLEARANCE);
    }
    /** 在出现点和目标之间：放出来的地方离目标多远（水平）。 */
    public static final double SPAWN_DISTANCE = 48;
    /**
     * 每架带几颗炸弹，两颗之间隔多少刻。一架炸完要 20 来秒：原来 6 颗隔 1.5 秒，9 秒就扔完撤了，炮塔刚转过来就没了目标。
     */
    public static final int BOMBS = 8;
    public static final int BOMB_INTERVAL = 60;
    /** 一颗炸弹 0 级时的伤害、炸开的半径；打设施按 {@link Tactics#FACILITY_DAMAGE} 倍，溅到人按 {@link #PLAYER_SPLASH} 倍。 */
    public static final float BOMB_DAMAGE = 12;
    public static final double BOMB_RADIUS = 3.5;
    public static final double PLAYER_SPLASH = 0.5;
    /** 在设施正上方这么近（水平）就扔。 */
    public static final double DROP_RADIUS = 3;
    /** 找设施的范围：目标周围多远。 */
    public static final double RAID_RADIUS = 48;
    /** 一开始比这多、打得剩这么几架以下就撤。 */
    public static final int RETREAT_SURVIVORS = 2;
    /** 俯冲、轰炸、撤走最多多少刻。 */
    public static final int DIVE_MAX_TICKS = 20 * 12;
    public static final int BOMB_MAX_TICKS = 20 * 60;
    public static final int LEAVE_MAX_TICKS = 20 * 20;

    private final int launched;
    private Phase phase = Phase.WARNING;
    private int ticks;

    /** @param launched 派了几架 */
    public LancerRaid(int launched) {
        this.launched = launched;
    }

    public Phase phase() {
        return this.phase;
    }

    /** 这个阶段过了多少刻。 */
    public int ticks() {
        return this.ticks;
    }

    public int launched() {
        return this.launched;
    }

    /** 预警还剩多少刻；过了预警是 0。 */
    public int warningLeft() {
        return this.phase == Phase.WARNING ? Math.max(0, WARNING_TICKS - this.ticks) : 0;
    }

    /**
     * 推进一刻。
     *
     * @param alive     还活着、还在场的几架
     * @param arrived   其中到了轰炸高度的几架
     * @param bombsLeft 一共还剩几颗炸弹
     * @return 推进以后的阶段
     */
    public Phase tick(int alive, int arrived, int bombsLeft) {
        this.ticks++;
        boolean retreat = retreat(this.launched, alive);
        Phase next = switch (this.phase) {
            case WARNING -> this.ticks >= WARNING_TICKS ? Phase.DIVE : Phase.WARNING;
            case DIVE -> {
                if (alive == 0) {
                    yield Phase.OVER;
                }
                if (retreat) {
                    yield Phase.LEAVE;
                }
                yield arrived >= alive || this.ticks >= DIVE_MAX_TICKS ? Phase.BOMB : Phase.DIVE;
            }
            case BOMB -> {
                if (alive == 0) {
                    yield Phase.OVER;
                }
                yield bombsLeft <= 0 || retreat || this.ticks >= BOMB_MAX_TICKS ? Phase.LEAVE : Phase.BOMB;
            }
            case LEAVE -> alive == 0 || this.ticks >= LEAVE_MAX_TICKS ? Phase.OVER : Phase.LEAVE;
            case OVER -> Phase.OVER;
        };
        if (next != this.phase) {
            this.phase = next;
            this.ticks = 0;
        }
        return this.phase;
    }

    /** 跳过预警：下一刻就放出来（调试命令、游戏内测试用）。 */
    public void skipWarning() {
        if (this.phase == Phase.WARNING) {
            this.ticks = Math.max(this.ticks, WARNING_TICKS - 1);
        }
    }

    /**
     * 炸弹离爆心 {@code distance} 格的伤害倍率：爆心 1，到边上剩一半，边外 0。
     */
    public static double splash(double distance) {
        if (distance > BOMB_RADIUS) {
            return 0;
        }
        return 1 - 0.5 * Math.max(0, distance) / BOMB_RADIUS;
    }

    /** 马上撤（星球卸载了、黑雾关了之类）。还在预警就直接结束。 */
    public void abort() {
        if (this.phase == Phase.WARNING) {
            this.phase = Phase.OVER;
        } else if (this.phase != Phase.OVER) {
            this.phase = Phase.LEAVE;
        }
        this.ticks = 0;
    }

    /** 派了 {@code launched} 架、剩 {@code alive} 架时该不该撤：只剩一两架就撤（本来就只有一两架的打到最后）。 */
    public static boolean retreat(int launched, int alive) {
        return launched > RETREAT_SURVIVORS && alive <= RETREAT_SURVIVORS;
    }

    /** 离地 {@code aboveGround} 格算不算高空。 */
    public static boolean highAltitude(double aboveGround) {
        return aboveGround >= HIGH_ALTITUDE;
    }

    /** 这个阶段要飞在离地多高。 */
    public static double cruiseHeight(Phase phase) {
        return switch (phase) {
            case WARNING -> SPAWN_HEIGHT;
            case DIVE, BOMB -> BOMB_HEIGHT;
            case LEAVE, OVER -> LEAVE_HEIGHT + 20;
        };
    }
}
