package io.github.sweetcornna.universe.core.ship;

/**
 * 飞船上一个方块在飞行计算里的角色。普通方块按轻重分两类，功能部件各有作用。
 */
public enum ShipPart {
    /** 普通的结构方块（石头、木板、金属块……）。 */
    HULL(1.0),
    /** 轻的方块（玻璃、羊毛、地毯、火把……）。 */
    LIGHT(0.3),
    /** 船体板：专门造船用的轻合金，只有普通方块一半重。 */
    PLATING(0.5),
    /** 乘客座椅。 */
    SEAT(0.5),
    /** 驾驶座：一艘船有且只有一个，是组装的起点和驾驶员的位置。 */
    HELM(1.0),
    /** 推进器：提供升力和推力。 */
    THRUSTER(2.0),
    /** 燃料舱：储存燃料。 */
    FUEL_TANK(1.5),
    /** 脉冲引擎：在太空里加速巡航。 */
    IMPULSE_ENGINE(4.0),
    /** 跃迁引擎：在恒星系之间跃迁，装得越多射程越远。 */
    WARP_DRIVE(5.0);

    private final double mass;

    ShipPart(double mass) {
        this.mass = mass;
    }

    /** 质量（吨）。 */
    public double mass() {
        return this.mass;
    }
}
