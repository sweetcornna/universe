package io.github.sweetcornna.universe.client.audio;

import static io.github.sweetcornna.universe.client.audio.MachineSoundscape.bind;
import static io.github.sweetcornna.universe.client.audio.MachineSoundscape.working;

import io.github.sweetcornna.universe.factory.power.WindTurbineBlockEntity;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.sound.MachineSounds;

/**
 * 哪种机器放哪条运转声（ADR-0029 §三），第一次进世界时登记一次。
 *
 * <p>写法：{@code MachineSoundscape.bind(UniverseBlocks.ASSEMBLER.get(), MachineSounds.ASSEMBLER, 0.6F, 1.0F);}
 * 不是 {@code MachineBlock} 的设备给出自己的响度函数。同一类不同等级共用一条声音，音高往上走。
 */
final class MachineSoundTable {
    private MachineSoundTable() {
    }

    static void bindAll() {
        // 加工
        bind(UniverseBlocks.ASSEMBLER.get(), MachineSounds.ASSEMBLER, 0.45F, 0.92F);
        bind(UniverseBlocks.ASSEMBLER_MK2.get(), MachineSounds.ASSEMBLER, 0.45F, 1.0F);
        bind(UniverseBlocks.ASSEMBLER_MK3.get(), MachineSounds.ASSEMBLER, 0.45F, 1.1F);
        bind(UniverseBlocks.RECOMPOSING_ASSEMBLER.get(), MachineSounds.ASSEMBLER, 0.5F, 0.85F);
        bind(UniverseBlocks.ARC_SMELTER.get(), MachineSounds.ARC_SMELTER, 0.55F, 1.0F);
        bind(UniverseBlocks.PLANE_SMELTER.get(), MachineSounds.PLANE_SMELTER, 0.5F, 1.0F);
        bind(UniverseBlocks.NEGENTROPY_SMELTER.get(), MachineSounds.PLANE_SMELTER, 0.55F, 0.8F);
        bind(UniverseBlocks.CHEMICAL_PLANT.get(), MachineSounds.CHEMICAL, 0.5F, 1.0F);
        bind(UniverseBlocks.QUANTUM_CHEMICAL_PLANT.get(), MachineSounds.CHEMICAL, 0.5F, 0.9F);
        bind(UniverseBlocks.OIL_REFINERY.get(), MachineSounds.CHEMICAL, 0.55F, 0.8F);
        bind(UniverseBlocks.PORTABLE_REFINER.get(), MachineSounds.CHEMICAL, 0.4F, 1.2F);
        bind(UniverseBlocks.FRACTIONATOR.get(), MachineSounds.FRACTIONATOR, 0.45F, 1.0F);
        bind(UniverseBlocks.ORBITAL_COLLECTOR.get(), MachineSounds.FRACTIONATOR, 0.45F, 0.75F);
        bind(UniverseBlocks.MATRIX_LAB.get(), MachineSounds.LAB, 0.4F, 1.0F);
        bind(UniverseBlocks.SELF_EVOLUTION_LAB.get(), MachineSounds.LAB, 0.45F, 0.9F);
        // 资源
        bind(UniverseBlocks.MINING_MACHINE.get(), MachineSounds.MINER, 0.55F, 1.0F);
        bind(UniverseBlocks.ADVANCED_MINING_MACHINE.get(), MachineSounds.MINER, 0.55F, 1.12F);
        bind(UniverseBlocks.WATER_PUMP.get(), MachineSounds.WATER_PUMP, 0.45F, 1.0F);
        bind(UniverseBlocks.OIL_EXTRACTOR.get(), MachineSounds.OIL_EXTRACTOR, 0.5F, 1.0F);
        // 发电和电网
        bind(UniverseBlocks.THERMAL_PLANT.get(), MachineSounds.THERMAL, 0.6F, 1.0F);
        bind(UniverseBlocks.MINI_FUSION_POWER_PLANT.get(), MachineSounds.FUSION, 0.55F, 1.0F);
        bind(UniverseBlocks.ARTIFICIAL_STAR.get(), MachineSounds.FUSION, 0.7F, 0.7F);
        bind(UniverseBlocks.GEOTHERMAL_POWER_STATION.get(), MachineSounds.GEOTHERMAL, 0.55F, 1.0F);
        // 风越大越响
        bind(UniverseBlocks.WIND_TURBINE.get(), MachineSounds.WIND, 0.45F, 1.0F, blockEntity -> blockEntity instanceof WindTurbineBlockEntity turbine
                ? working(blockEntity) * Math.min(1.0, turbine.wind()) : 0);
        bind(UniverseBlocks.ENERGY_EXCHANGER.get(), MachineSounds.TRANSFORMER, 0.45F, 1.0F);
        bind(UniverseBlocks.ACCUMULATOR.get(), MachineSounds.TRANSFORMER, 0.3F, 0.9F);
        // 电塔到处都是，压得很轻，也让它们排在机器后面
        bind(UniverseBlocks.TESLA_TOWER.get(), MachineSounds.TRANSFORMER, 0.25F, 1.0F, blockEntity -> working(blockEntity) * 0.3);
        bind(UniverseBlocks.WIRELESS_POWER_TOWER.get(), MachineSounds.TRANSFORMER, 0.25F, 1.05F, blockEntity -> working(blockEntity) * 0.3);
        bind(UniverseBlocks.SATELLITE_SUBSTATION.get(), MachineSounds.TRANSFORMER, 0.25F, 0.95F, blockEntity -> working(blockEntity) * 0.3);
        // 戴森球
        bind(UniverseBlocks.MINIATURE_PARTICLE_COLLIDER.get(), MachineSounds.COLLIDER, 0.5F, 1.0F);
        bind(UniverseBlocks.RAY_RECEIVER.get(), MachineSounds.RECEIVER, 0.45F, 1.0F);
        // 生物设备
        bind(UniverseBlocks.HYDROPONIC_TRAY.get(), MachineSounds.BIO, 0.3F, 1.0F, blockEntity -> working(blockEntity) * 0.6);
        bind(UniverseBlocks.NUTRIENT_PROCESSOR.get(), MachineSounds.BIO, 0.35F, 0.9F);
        bind(UniverseBlocks.EGG_SEQUENCER.get(), MachineSounds.BIO, 0.35F, 1.1F);
        // 物流：有无人机、运输船在外面飞，或者在充电时算在运转
        bind(UniverseBlocks.PLANETARY_LOGISTICS_STATION.get(), MachineSounds.STATION, 0.45F, 1.0F);
        bind(UniverseBlocks.INTERSTELLAR_LOGISTICS_STATION.get(), MachineSounds.STATION, 0.5F, 0.85F);
        // 防御：护盾展开着、干扰塔在干扰时
        bind(UniverseBlocks.PLANETARY_SHIELD_GENERATOR.get(), MachineSounds.SHIELD, 0.6F, 1.0F);
        bind(UniverseBlocks.JAMMER_TOWER.get(), MachineSounds.JAMMER, 0.45F, 1.0F);
    }
}
