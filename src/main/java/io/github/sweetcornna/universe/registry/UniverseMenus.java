package io.github.sweetcornna.universe.registry;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.logistics.StationKind;
import io.github.sweetcornna.universe.dyson.EmRailEjectorBlockEntity;
import io.github.sweetcornna.universe.entity.ship.ShipPanelMenu;
import io.github.sweetcornna.universe.dyson.LaunchSiloBlockEntity;
import io.github.sweetcornna.universe.dyson.RayReceiverBlockEntity;
import io.github.sweetcornna.universe.factory.FractionatorBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu.Role;
import io.github.sweetcornna.universe.factory.MachineMenu.SlotSpec;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.factory.FuelRecipeMachineBlockEntity;
import io.github.sweetcornna.universe.factory.RecipeMachineBlockEntity;
import io.github.sweetcornna.universe.factory.belt.TrafficMonitorBlockEntity;
import io.github.sweetcornna.universe.factory.power.AccumulatorBlockEntity;
import io.github.sweetcornna.universe.factory.power.EnergyExchangerBlockEntity;
import io.github.sweetcornna.universe.factory.power.FuelGeneratorBlockEntity;
import io.github.sweetcornna.universe.factory.power.GeothermalBlockEntity;
import io.github.sweetcornna.universe.factory.power.SolarPanelBlockEntity;
import io.github.sweetcornna.universe.factory.power.TeslaTowerBlockEntity;
import io.github.sweetcornna.universe.factory.power.ThermalPlantBlockEntity;
import io.github.sweetcornna.universe.factory.power.WindTurbineBlockEntity;
import io.github.sweetcornna.universe.factory.research.MatrixLabBlockEntity;
import io.github.sweetcornna.universe.factory.resource.MiningMachineBlockEntity;
import io.github.sweetcornna.universe.factory.resource.OilExtractorBlockEntity;
import io.github.sweetcornna.universe.factory.resource.WaterPumpBlockEntity;
import io.github.sweetcornna.universe.logistics.DistributorBlockEntity;
import io.github.sweetcornna.universe.logistics.StationBlockEntity;
import io.github.sweetcornna.universe.mecha.MechaMenu;
import io.github.sweetcornna.universe.tech.TechMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 设备界面：每种设备一个菜单类型，格子的布局写在这里。 */
public final class UniverseMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Universe.MOD_ID);
    /** 配方设备界面的大小。 */
    public static final int RECIPE_WIDTH = 218;
    public static final int RECIPE_HEIGHT = 200;
    /** 采集设备的界面宽度：左边读数，右边一个产物格。 */
    public static final int RESOURCE_WIDTH = 200;

    public static final MachineMenu.Type TESLA_TOWER = machine("tesla_tower", new MachineMenu.Type(TeslaTowerBlockEntity.DATA_COUNT, 92));
    public static final MachineMenu.Type WIND_TURBINE = machine("wind_turbine", new MachineMenu.Type(WindTurbineBlockEntity.DATA_COUNT, 66));
    public static final MachineMenu.Type SOLAR_PANEL = machine("solar_panel", new MachineMenu.Type(SolarPanelBlockEntity.DATA_COUNT, 80));
    public static final MachineMenu.Type THERMAL_PLANT = machine("thermal_plant", new MachineMenu.Type(ThermalPlantBlockEntity.DATA_COUNT, 166,
            new SlotSpec(9, 38, Role.FUEL)));
    public static final MachineMenu.Type ACCUMULATOR = machine("accumulator", new MachineMenu.Type(AccumulatorBlockEntity.DATA_COUNT, 64));
    // 烧燃料棒的发电设备（ADR-0018）：和火力发电厂一样一个燃料格
    public static final MachineMenu.Type MINI_FUSION_POWER_PLANT = machine("mini_fusion_power_plant",
            new MachineMenu.Type(FuelGeneratorBlockEntity.DATA_COUNT, 166, new SlotSpec(9, 38, Role.FUEL)));
    public static final MachineMenu.Type ARTIFICIAL_STAR = machine("artificial_star",
            new MachineMenu.Type(FuelGeneratorBlockEntity.DATA_COUNT, 166, new SlotSpec(9, 38, Role.FUEL)));
    // 配方设备：左边是配方清单，右边原料、进度、产物
    public static final MachineMenu.Type ARC_SMELTER = machine("arc_smelter", new MachineMenu.Type(RecipeMachineBlockEntity.DATA_COUNT,
            RECIPE_WIDTH, RECIPE_HEIGHT, input(118, 36), input(136, 36), input(118, 54), output(190, 45)));
    public static final MachineMenu.Type ASSEMBLER = machine("assembler", new MachineMenu.Type(RecipeMachineBlockEntity.DATA_COUNT,
            RECIPE_WIDTH, RECIPE_HEIGHT, input(118, 36), input(136, 36), input(118, 54), input(136, 54), output(190, 45)));
    public static final MachineMenu.Type OIL_REFINERY = machine("oil_refinery", new MachineMenu.Type(RecipeMachineBlockEntity.DATA_COUNT,
            RECIPE_WIDTH, RECIPE_HEIGHT, input(118, 45), input(136, 45), output(190, 36), output(190, 54)));
    public static final MachineMenu.Type ASSEMBLER_MK2 = machine("assembler_mk2", new MachineMenu.Type(RecipeMachineBlockEntity.DATA_COUNT,
            RECIPE_WIDTH, RECIPE_HEIGHT, input(118, 36), input(136, 36), input(118, 54), input(136, 54), output(190, 45)));
    public static final MachineMenu.Type ASSEMBLER_MK3 = machine("assembler_mk3", new MachineMenu.Type(RecipeMachineBlockEntity.DATA_COUNT,
            RECIPE_WIDTH, RECIPE_HEIGHT, input(118, 36), input(136, 36), input(118, 54), input(136, 54), output(190, 45)));
    public static final MachineMenu.Type PLANE_SMELTER = machine("plane_smelter", new MachineMenu.Type(RecipeMachineBlockEntity.DATA_COUNT,
            RECIPE_WIDTH, RECIPE_HEIGHT, input(118, 36), input(136, 36), input(118, 54), output(190, 45)));
    public static final MachineMenu.Type CHEMICAL_PLANT = machine("chemical_plant", new MachineMenu.Type(RecipeMachineBlockEntity.DATA_COUNT,
            RECIPE_WIDTH, RECIPE_HEIGHT, input(118, 36), input(136, 36), input(118, 54), output(190, 36), output(190, 54)));
    public static final MachineMenu.Type MINIATURE_PARTICLE_COLLIDER = machine("miniature_particle_collider",
            new MachineMenu.Type(RecipeMachineBlockEntity.DATA_COUNT, RECIPE_WIDTH, RECIPE_HEIGHT, input(118, 36), input(136, 36), input(118, 54),
                    output(190, 36), output(190, 54)));
    // 烧燃料的配方设备（ADR-0026）：燃料格放在原料格下面
    public static final MachineMenu.Type NUTRIENT_PROCESSOR = machine("nutrient_processor", new MachineMenu.Type(
            FuelRecipeMachineBlockEntity.DATA_COUNT, RECIPE_WIDTH, RECIPE_HEIGHT, input(118, 36), input(136, 36), input(118, 54), output(190, 45),
            new SlotSpec(136, 54, Role.FUEL)));
    public static final MachineMenu.Type PORTABLE_REFINER = machine("portable_refiner", new MachineMenu.Type(
            FuelRecipeMachineBlockEntity.DATA_COUNT, RECIPE_WIDTH, RECIPE_HEIGHT, input(127, 36), output(190, 45), new SlotSpec(127, 54, Role.FUEL)));
    // 高级设备（ADR-0022）：布局和低一级的一样
    public static final MachineMenu.Type NEGENTROPY_SMELTER = machine("negentropy_smelter", new MachineMenu.Type(
            RecipeMachineBlockEntity.DATA_COUNT, RECIPE_WIDTH, RECIPE_HEIGHT, input(118, 36), input(136, 36), input(118, 54), output(190, 45)));
    public static final MachineMenu.Type RECOMPOSING_ASSEMBLER = machine("recomposing_assembler", new MachineMenu.Type(
            RecipeMachineBlockEntity.DATA_COUNT, RECIPE_WIDTH, RECIPE_HEIGHT, input(118, 36), input(136, 36), input(118, 54), input(136, 54),
            output(190, 45)));
    public static final MachineMenu.Type QUANTUM_CHEMICAL_PLANT = machine("quantum_chemical_plant", new MachineMenu.Type(
            RecipeMachineBlockEntity.DATA_COUNT, RECIPE_WIDTH, RECIPE_HEIGHT, input(118, 36), input(136, 36), input(118, 54), output(190, 36),
            output(190, 54)));
    // 戴森球的设备（ADR-0016）：左边读数，右边一两个格子，格子下面是打开戴森球编辑器的按钮
    public static final MachineMenu.Type EM_RAIL_EJECTOR = machine("em_rail_ejector",
            new MachineMenu.Type(EmRailEjectorBlockEntity.DATA_COUNT, RESOURCE_WIDTH, 192, input(172, 30)));
    public static final MachineMenu.Type VERTICAL_LAUNCHING_SILO = machine("vertical_launching_silo",
            new MachineMenu.Type(LaunchSiloBlockEntity.DATA_COUNT, RESOURCE_WIDTH, 192, input(172, 30)));
    public static final MachineMenu.Type RAY_RECEIVER = machine("ray_receiver",
            new MachineMenu.Type(RayReceiverBlockEntity.DATA_COUNT, RESOURCE_WIDTH, 192, input(172, 30), output(172, 52)));
    // 采集设备：读数加一个产物格
    public static final MachineMenu.Type MINING_MACHINE = machine("mining_machine",
            new MachineMenu.Type(MiningMachineBlockEntity.DATA_COUNT, RESOURCE_WIDTH, 166, output(172, 46)));
    public static final MachineMenu.Type WATER_PUMP = machine("water_pump",
            new MachineMenu.Type(WaterPumpBlockEntity.DATA_COUNT, RESOURCE_WIDTH, 166, output(172, 46)));
    public static final MachineMenu.Type OIL_EXTRACTOR = machine("oil_extractor",
            new MachineMenu.Type(OilExtractorBlockEntity.DATA_COUNT, RESOURCE_WIDTH, 166, output(172, 46)));
    // 分馏塔：左边读数，右边原料格（氢）和两个产物格（氢、重氢）
    public static final MachineMenu.Type FRACTIONATOR = machine("fractionator",
            new MachineMenu.Type(FractionatorBlockEntity.DATA_COUNT, RESOURCE_WIDTH, 166, input(136, 46), output(172, 36), output(172, 56)));
    // 矩阵研究站：左边研究进度，右边每种矩阵一格（两排各三格，最后一格是白矩阵），再下面是打开科技树的按钮
    public static final MachineMenu.Type MATRIX_LAB = machine("matrix_lab",
            new MachineMenu.Type(MatrixLabBlockEntity.DATA_COUNT, 176, 192, input(112, 30), input(130, 30), input(148, 30), input(112, 48),
                    input(130, 48), input(148, 48)));
    // 物流站（ADR-0015）：布局见 StationBlockEntity#layout
    public static final MachineMenu.Type PLANETARY_LOGISTICS_STATION = machine("planetary_logistics_station",
            StationBlockEntity.layout(StationKind.PLANETARY));
    public static final MachineMenu.Type INTERSTELLAR_LOGISTICS_STATION = machine("interstellar_logistics_station",
            StationBlockEntity.layout(StationKind.INTERSTELLAR));
    public static final MachineMenu.Type ORBITAL_COLLECTOR = machine("orbital_collector", StationBlockEntity.layout(StationKind.COLLECTOR));
    // 工厂的补全（ADR-0022）：地热电站只有读数；能量枢纽左边读数，右边空的、满的蓄电器各一格；流速监测器只有读数和按钮
    public static final MachineMenu.Type GEOTHERMAL_POWER_STATION = machine("geothermal_power_station",
            new MachineMenu.Type(GeothermalBlockEntity.DATA_COUNT, 66));
    public static final MachineMenu.Type ENERGY_EXCHANGER = machine("energy_exchanger",
            new MachineMenu.Type(EnergyExchangerBlockEntity.DATA_COUNT, RESOURCE_WIDTH, 166, input(136, 46), output(172, 46)));
    public static final MachineMenu.Type LOGISTICS_DISTRIBUTOR = machine("logistics_distributor",
            new MachineMenu.Type(DistributorBlockEntity.DATA_COUNT, 176, 198, input(152, 58)));
    public static final MachineMenu.Type TRAFFIC_MONITOR = machine("traffic_monitor",
            new MachineMenu.Type(TrafficMonitorBlockEntity.DATA_COUNT, 176, 84));
    // 储物仓：3 行 10 列
    public static final MachineMenu.Type STORAGE_DEPOT = machine("storage_depot",
            new MachineMenu.Type(0, 196, 170, grid(9, 20, 10, 3)));

    /** 技术配置界面（ADR-0014）。 */
    public static final DeferredHolder<MenuType<?>, MenuType<TechMenu>> TECH = MENUS.register("tech",
            () -> IMenuTypeExtension.create(TechMenu::new));
    /** 机甲面板（ADR-0023）。 */
    public static final DeferredHolder<MenuType<?>, MenuType<MechaMenu>> MECHA = MENUS.register("mecha",
            () -> IMenuTypeExtension.create(MechaMenu::new));
    /** 飞船面板：评级、数值、驾驶台的技术槽（ADR-0024）。 */
    public static final DeferredHolder<MenuType<?>, MenuType<ShipPanelMenu>> SHIP_PANEL = MENUS.register("ship_panel",
            () -> IMenuTypeExtension.create(ShipPanelMenu::new));

    private UniverseMenus() {
    }

    private static SlotSpec input(int x, int y) {
        return new SlotSpec(x, y, Role.INPUT);
    }

    private static SlotSpec[] grid(int x, int y, int columns, int rows) {
        SlotSpec[] slots = new SlotSpec[columns * rows];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                slots[row * columns + column] = input(x + column * 18, y + row * 18);
            }
        }
        return slots;
    }

    private static SlotSpec output(int x, int y) {
        return new SlotSpec(x, y, Role.OUTPUT);
    }

    private static MachineMenu.Type machine(String name, MachineMenu.Type type) {
        type.bind(MENUS.register(name, () -> IMenuTypeExtension.create((id, inventory, buf) -> new MachineMenu(type, id, inventory, buf))));
        return type;
    }

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
