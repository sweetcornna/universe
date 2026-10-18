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
    WARP_DRIVE(5.0),
    // ---- ADR-0017：武器、护盾和 Mk.II 部件
    /** 合金船体板：比船体板略重，每块给船体加 {@link ShipStats#HULL_PER_ALLOY} 点。 */
    ALLOY_PLATING(0.6),
    /** Mk.II 推进器：推力 {@link ShipStats#THRUST_PER_THRUSTER_MK2}。 */
    THRUSTER_MK2(2.0),
    /** Mk.II 燃料舱：容量 {@link ShipStats#FUEL_PER_TANK_MK2}。 */
    FUEL_TANK_MK2(2.0),
    /** Mk.II 脉冲引擎：推得动更重的船，最快 {@link ShipStats#IMPULSE_SPEED_MK2} 格/刻。 */
    IMPULSE_ENGINE_MK2(4.5),
    /** Mk.II 跃迁引擎：每台射程多 10 光年。 */
    WARP_DRIVE_MK2(5.5),
    /** 光子炮：开火键发射光子弹。 */
    PHOTON_CANNON(2.0),
    /** 采矿光束：采矿模式下按住开火键持续照射。 */
    MINING_BEAM(2.0),
    /** 护盾发生器：每台 {@link ShipStats#SHIELD_PER_GENERATOR} 点护盾。 */
    SHIELD_GENERATOR(3.0);

    private final double mass;

    ShipPart(double mass) {
        this.mass = mass;
    }

    /** 质量（吨）。 */
    public double mass() {
        return this.mass;
    }
}
