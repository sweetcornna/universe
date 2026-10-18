package io.github.sweetcornna.universe.registry;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.factory.RecipeMachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu.Role;
import io.github.sweetcornna.universe.factory.MachineMenu.SlotSpec;
import io.github.sweetcornna.universe.factory.power.AccumulatorBlockEntity;
import io.github.sweetcornna.universe.factory.power.TeslaTowerBlockEntity;
import io.github.sweetcornna.universe.factory.power.ThermalPlantBlockEntity;
import io.github.sweetcornna.universe.factory.power.WindTurbineBlockEntity;
import io.github.sweetcornna.universe.factory.resource.MiningMachineBlockEntity;
import io.github.sweetcornna.universe.factory.resource.OilExtractorBlockEntity;
import io.github.sweetcornna.universe.factory.resource.WaterPumpBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
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
    // 采集设备：读数加一个产物格
    public static final MachineMenu.Type MINING_MACHINE = machine("mining_machine",
            new MachineMenu.Type(MiningMachineBlockEntity.DATA_COUNT, RESOURCE_WIDTH, 166, output(172, 46)));
    public static final MachineMenu.Type WATER_PUMP = machine("water_pump",
            new MachineMenu.Type(WaterPumpBlockEntity.DATA_COUNT, RESOURCE_WIDTH, 166, output(172, 46)));
    public static final MachineMenu.Type OIL_EXTRACTOR = machine("oil_extractor",
            new MachineMenu.Type(OilExtractorBlockEntity.DATA_COUNT, RESOURCE_WIDTH, 166, output(172, 46)));
    // 储物仓：3 行 10 列
    public static final MachineMenu.Type STORAGE_DEPOT = machine("storage_depot",
            new MachineMenu.Type(0, 196, 170, grid(9, 20, 10, 3)));

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
