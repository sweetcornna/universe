package io.github.sweetcornna.universe.core.mecha;

/**
 * 外骨骼的能量核心和燃烧室（ADR-0023）。电量以焦为单位。
 *
 * <p>燃烧室一次烧一个燃料：燃料的能量先记在"正在烧的"里，每刻按燃烧室的功率转进核心，核心满了就停下来等。
 * 核心的电只给机甲的能力用：机械骨骼的加速、飞行、建造无人机。
 */
public final class MechaEnergy {
    /** 悬停耗电：1.2 kW。 */
    public static final double HOVER = 60;
    /** 移动耗电：每 m/s 100 W。 */
    public static final double PER_SPEED = 5;
    /** 建造无人机：每 m/s 60 W。 */
    public static final double DRONE_PER_SPEED = 3;
    /** 无线输电塔给核心充电的最大功率：15 kW。 */
    public static final double TOWER_CHARGE = 750;
    /** 低于这个比例时 HUD 报警。 */
    public static final double LOW = 0.15;

    private double energy;
    private double burning;
    private double burningTotal;

    public MechaEnergy() {
    }

    public MechaEnergy(double energy, double burning, double burningTotal) {
        this.energy = Math.max(0, energy);
        this.burning = Math.max(0, burning);
        this.burningTotal = Math.max(this.burning, burningTotal);
    }

    /** 飞行一刻的耗电：悬停加上按速度算的部分。 */
    public static double flight(double speed) {
        return HOVER + PER_SPEED * speed;
    }

    /** 机械骨骼一刻的耗电：只算加速多出来的那部分速度。 */
    public static double frame(double speed, double bonus) {
        return bonus <= 0 ? 0 : PER_SPEED * speed * bonus / (1 + bonus);
    }

    /** 一架飞着的建造无人机一刻的耗电。 */
    public static double drone(double speed) {
        return DRONE_PER_SPEED * speed;
    }

    /** 燃烧室要不要再放一个燃料：没有正在烧的，核心又没满。 */
    public boolean wantsFuel(long capacity) {
        return this.burning <= 0 && this.energy < capacity;
    }

    /** 放进一个燃料。 */
    public void addFuel(double joules) {
        if (joules <= 0) {
            return;
        }
        this.burning += joules;
        this.burningTotal = this.burning;
    }

    /**
     * 推进一刻：燃烧室按功率往核心里送电。
     *
     * @return 这一刻送进核心多少焦
     */
    public double burn(long capacity, long power) {
        double room = capacity - this.energy;
        if (room <= 0 || this.burning <= 0 || power <= 0) {
            return 0;
        }
        double moved = Math.min(Math.min(power, this.burning), room);
        this.energy += moved;
        this.burning -= moved;
        if (this.burning < 1e-6) {
            this.burning = 0;
            this.burningTotal = 0;
        }
        return moved;
    }

    /**
     * 从外面充电（无线输电塔）。
     *
     * @return 收下了多少焦
     */
    public double charge(double joules, long capacity) {
        double room = capacity - this.energy;
        if (joules <= 0 || room <= 0) {
            return 0;
        }
        double taken = Math.min(joules, room);
        this.energy += taken;
        return taken;
    }

    /** 还能充多少。 */
    public double room(long capacity) {
        return Math.max(0, capacity - this.energy);
    }

    /**
     * 用电。
     *
     * @return 够用时扣掉、返回真；不够时扣到零、返回假
     */
    public boolean draw(double joules) {
        if (joules <= 0) {
            return true;
        }
        if (this.energy >= joules) {
            this.energy -= joules;
            return true;
        }
        this.energy = 0;
        return false;
    }

    /** 容量变小（不该发生，以防万一）时去掉多的。 */
    public void clamp(long capacity) {
        this.energy = Math.min(this.energy, capacity);
    }

    public double energy() {
        return this.energy;
    }

    public boolean empty() {
        return this.energy <= 0;
    }

    /** 正在烧的燃料还剩多少焦。 */
    public double burning() {
        return this.burning;
    }

    /** 正在烧的燃料一共多少焦（面板画进度）。 */
    public double burningTotal() {
        return this.burningTotal;
    }

    /** 电量占容量的比例。 */
    public double share(long capacity) {
        return capacity <= 0 ? 0 : Math.min(1, this.energy / capacity);
    }

    /** 补满（创造模式）。 */
    public void fill(long capacity) {
        this.energy = capacity;
    }
}
