package io.github.sweetcornna.universe.factory.power;

import io.github.sweetcornna.universe.core.power.PowerSystem;
import io.github.sweetcornna.universe.factory.TallMachineBlock;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;

/**
 * 输电的塔（电力感应塔、无线输电塔、卫星配电站）：两格高，共用一个方块实体，连接距离、覆盖范围和给外骨骼充能的
 * 半径看方块（ADR-0022）。
 */
public class PowerTowerBlock extends TallMachineBlock {
    private final PowerSystem.TowerSpec spec;
    private final int chargeRadius;

    public PowerTowerBlock(Properties properties, PowerSystem.TowerSpec spec, int chargeRadius) {
        super(properties, UniverseBlockEntities.TESLA_TOWER);
        this.spec = spec;
        this.chargeRadius = chargeRadius;
    }

    public PowerSystem.TowerSpec spec() {
        return this.spec;
    }

    /** 给附近外骨骼的生命维持充能的半径；0 是不充。 */
    public int chargeRadius() {
        return this.chargeRadius;
    }
}
