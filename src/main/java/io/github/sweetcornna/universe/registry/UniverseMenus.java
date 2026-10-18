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

    private UniverseMenus() {
    }

    private static SlotSpec input(int x, int y) {
        return new SlotSpec(x, y, Role.INPUT);
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
