package io.github.sweetcornna.universe.registry;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.logistics.StationKind;
import io.github.sweetcornna.universe.dyson.EmRailEjectorBlockEntity;
import io.github.sweetcornna.universe.dyson.LaunchSiloBlockEntity;
import io.github.sweetcornna.universe.dyson.RayReceiverBlockEntity;
import io.github.sweetcornna.universe.factory.FractionatorBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu.Role;
import io.github.sweetcornna.universe.factory.MachineMenu.SlotSpec;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.factory.RecipeMachineBlockEntity;
import io.github.sweetcornna.universe.factory.power.AccumulatorBlockEntity;
import io.github.sweetcornna.universe.factory.power.SolarPanelBlockEntity;
import io.github.sweetcornna.universe.factory.power.TeslaTowerBlockEntity;
import io.github.sweetcornna.universe.factory.power.ThermalPlantBlockEntity;
import io.github.sweetcornna.universe.factory.power.WindTurbineBlockEntity;
import io.github.sweetcornna.universe.factory.research.MatrixLabBlockEntity;
import io.github.sweetcornna.universe.factory.resource.MiningMachineBlockEntity;
import io.github.sweetcornna.universe.factory.resource.OilExtractorBlockEntity;
import io.github.sweetcornna.universe.factory.resource.WaterPumpBlockEntity;
import io.github.sweetcornna.universe.logistics.StationBlockEntity;
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
    // 配方设备：左边是配方清单，右边原料、进度、产物
    public static final MachineMenu.Type ARC_SMELTER = machine("arc_smelter", new MachineMenu.Type(RecipeMachineBlockEntity.DATA_COUNT,
            RECIPE_WIDTH, RECIPE_HEIGHT, input(118, 45), input(136, 45), output(190, 45)));
    public static final MachineMenu.Type ASSEMBLER = machine("assembler", new MachineMenu.Type(RecipeMachineBlockEntity.DATA_COUNT,
            RECIPE_WIDTH, RECIPE_HEIGHT, input(118, 36), input(136, 36), input(118, 54), input(136, 54), output(190, 45)));
    public static final MachineMenu.Type OIL_REFINERY = machine("oil_refinery", new MachineMenu.Type(RecipeMachineBlockEntity.DATA_COUNT,
            RECIPE_WIDTH, RECIPE_HEIGHT, input(118, 45), input(136, 45), output(190, 36), output(190, 54)));
    public static final MachineMenu.Type ASSEMBLER_MK2 = machine("assembler_mk2", new MachineMenu.Type(RecipeMachineBlockEntity.DATA_COUNT,
            RECIPE_WIDTH, RECIPE_HEIGHT, input(118, 36), input(136, 36), input(118, 54), input(136, 54), output(190, 45)));
    public static final MachineMenu.Type ASSEMBLER_MK3 = machine("assembler_mk3", new MachineMenu.Type(RecipeMachineBlockEntity.DATA_COUNT,
            RECIPE_WIDTH, RECIPE_HEIGHT, input(118, 36), input(136, 36), input(118, 54), input(136, 54), output(190, 45)));
    public static final MachineMenu.Type PLANE_SMELTER = machine("plane_smelter", new MachineMenu.Type(RecipeMachineBlockEntity.DATA_COUNT,
            RECIPE_WIDTH, RECIPE_HEIGHT, input(118, 45), input(136, 45), output(190, 45)));
    public static final MachineMenu.Type CHEMICAL_PLANT = machine("chemical_plant", new MachineMenu.Type(RecipeMachineBlockEntity.DATA_COUNT,
            RECIPE_WIDTH, RECIPE_HEIGHT, input(118, 36), input(136, 36), input(118, 54), output(190, 36), output(190, 54)));
    public static final MachineMenu.Type MINIATURE_PARTICLE_COLLIDER = machine("miniature_particle_collider",
            new MachineMenu.Type(RecipeMachineBlockEntity.DATA_COUNT, RECIPE_WIDTH, RECIPE_HEIGHT, input(118, 36), input(136, 36), input(118, 54),
                    output(190, 45)));
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
    // 矩阵研究站：左边研究进度，右边每种矩阵一格（上面一排三格，下面一排两格），再下面是打开科技树的按钮
    public static final MachineMenu.Type MATRIX_LAB = machine("matrix_lab",
            new MachineMenu.Type(MatrixLabBlockEntity.DATA_COUNT, 176, 180, input(112, 30), input(130, 30), input(148, 30), input(121, 48),
                    input(139, 48)));
    // 物流站（ADR-0015）：布局见 StationBlockEntity#layout
    public static final MachineMenu.Type PLANETARY_LOGISTICS_STATION = machine("planetary_logistics_station",
            StationBlockEntity.layout(StationKind.PLANETARY));
    public static final MachineMenu.Type INTERSTELLAR_LOGISTICS_STATION = machine("interstellar_logistics_station",
            StationBlockEntity.layout(StationKind.INTERSTELLAR));
    public static final MachineMenu.Type ORBITAL_COLLECTOR = machine("orbital_collector", StationBlockEntity.layout(StationKind.COLLECTOR));
    // 储物仓：3 行 10 列
    public static final MachineMenu.Type STORAGE_DEPOT = machine("storage_depot",
            new MachineMenu.Type(0, 196, 170, grid(9, 20, 10, 3)));

    /** 技术配置界面（ADR-0014）。 */
    public static final DeferredHolder<MenuType<?>, MenuType<TechMenu>> TECH = MENUS.register("tech",
            () -> IMenuTypeExtension.create(TechMenu::new));

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
