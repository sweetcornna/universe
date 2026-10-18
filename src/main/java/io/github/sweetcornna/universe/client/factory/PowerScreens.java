package io.github.sweetcornna.universe.client.factory;

import java.util.Locale;

import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.factory.power.AccumulatorBlockEntity;
import io.github.sweetcornna.universe.factory.power.TeslaTowerBlockEntity;
import io.github.sweetcornna.universe.factory.power.WindTurbineBlockEntity;
import io.github.sweetcornna.universe.factory.power.ThermalPlantBlockEntity;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** 电网设备的界面：感应塔、风力涡轮机、火力发电厂、蓄电器。 */
public final class PowerScreens {
    private PowerScreens() {
    }

    /** 电力感应塔：整个电网的发电、用电、满足率和蓄电器的充放电。 */
    public static final class TeslaTower extends MachineScreen {
        public TeslaTower(MachineMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        @Override
        protected void extractInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            graphics.text(this.font, Component.translatable("gui.universe.tesla_tower.grid",
                    this.menu.data(TeslaTowerBlockEntity.TOWERS), this.menu.data(TeslaTowerBlockEntity.DEVICES)), 8, 22, TEXT, false);
            long generation = this.menu.wide(TeslaTowerBlockEntity.GENERATION);
            long demand = this.menu.wide(TeslaTowerBlockEntity.DEMAND);
            graphics.text(this.font, Component.translatable("gui.universe.tesla_tower.flow", watts(generation), watts(demand)), 8, 34, TEXT, false);
            int satisfaction = this.menu.data(TeslaTowerBlockEntity.SATISFACTION);
            int color = satisfaction >= 999 ? GOOD : satisfaction >= 500 ? WARN : BAD;
            Component label = Component.translatable("gui.universe.tesla_tower.satisfaction", satisfaction / 10);
            graphics.text(this.font, label, 8, 48, color, false);
            int barX = 8 + this.font.width(label) + 6;
            bar(graphics, barX, 49, 168 - barX, 6, satisfaction / 1000F, color);
            graphics.text(this.font, Component.translatable("gui.universe.tesla_tower.storage",
                    watts(this.menu.wide(TeslaTowerBlockEntity.CHARGED)), watts(this.menu.wide(TeslaTowerBlockEntity.DISCHARGED))),
                    8, 62, TEXT, false);
            graphics.text(this.font, Component.translatable("gui.universe.tesla_tower.range",
                    TeslaTowerBlockEntity.SPEC.coverRange(), TeslaTowerBlockEntity.SPEC.connectRange()), 8, 78, DIM, false);
        }
    }

    /** 风力涡轮机：发电量和风力系数。 */
    public static final class WindTurbine extends MachineScreen {
        public WindTurbine(MachineMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        @Override
        protected void extractInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            this.powerLine(graphics, 8, 22);
            float wind = this.menu.data(WindTurbineBlockEntity.WIND) / 100F;
            Component label = Component.translatable("gui.universe.wind_turbine.wind", String.format(Locale.ROOT, "%.2f", wind));
            graphics.text(this.font, label, 8, 36, TEXT, false);
            int barX = 8 + this.font.width(label) + 6;
            bar(graphics, barX, 37, 168 - barX, 6, wind / 2F, wind > 0 ? ACCENT : BAD);
            graphics.text(this.font, Component.translatable("gui.universe.wind_turbine.hint"), 8, 52, DIM, false);
        }
    }

    /** 火力发电厂：燃料格和炉膛里剩下的能量。 */
    public static final class ThermalPlant extends MachineScreen {
        public ThermalPlant(MachineMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        @Override
        protected void extractInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            this.powerLine(graphics, 8, 22);
            float fuel = this.menu.data(ThermalPlantBlockEntity.FUEL) / 1000F;
            graphics.text(this.font, Component.translatable("gui.universe.thermal_plant.fuel"), 32, 37, TEXT, false);
            bar(graphics, 32, 49, 136, 5, fuel, fuel > 0.2F ? 0xFFF0943A : BAD);
            graphics.text(this.font, Component.translatable("gui.universe.thermal_plant.hint"), 8, 60, DIM, false);
        }
    }

    /** 蓄电器：电量和充放电。 */
    public static final class Accumulator extends MachineScreen {
        public Accumulator(MachineMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        @Override
        protected void extractInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            if (this.menu.data(0) < 0) {
                graphics.text(this.font, Component.translatable("gui.universe.machine.unpowered"), 8, 22, BAD, false);
            } else {
                int direction = this.menu.data(AccumulatorBlockEntity.DIRECTION);
                String key = direction > 0 ? "gui.universe.accumulator.charging" : direction < 0 ? "gui.universe.accumulator.discharging"
                        : "gui.universe.accumulator.idle";
                graphics.text(this.font, Component.translatable(key, watts(this.menu.wide(1))), 8, 22, direction < 0 ? WARN : GOOD, false);
            }
            long energy = this.menu.wide(AccumulatorBlockEntity.ENERGY);
            graphics.text(this.font, Component.translatable("gui.universe.accumulator.energy", joules(energy),
                    joules(AccumulatorBlockEntity.CAPACITY)), 8, 36, TEXT, false);
            bar(graphics, 8, 49, 160, 8, energy / (float) AccumulatorBlockEntity.CAPACITY, GOOD);
        }
    }
}
