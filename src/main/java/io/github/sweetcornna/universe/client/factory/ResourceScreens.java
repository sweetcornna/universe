package io.github.sweetcornna.universe.client.factory;

import java.util.Locale;

import io.github.sweetcornna.universe.core.factory.Veins;
import io.github.sweetcornna.universe.factory.FractionatorBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.factory.resource.MiningMachineBlockEntity;
import io.github.sweetcornna.universe.factory.resource.OilExtractorBlockEntity;
import io.github.sweetcornna.universe.factory.resource.WaterPumpBlockEntity;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** 采集设备的界面：采矿机、抽水站、原油萃取站。右边一个产物格，左边是读数。 */
public final class ResourceScreens {
    private ResourceScreens() {
    }

    private static void outputLabel(MachineScreen screen, GuiGraphicsExtractor graphics) {
        Component label = Component.translatable("gui.universe.machine.output_slot");
        graphics.text(screen.getFont(), label, 180 - screen.getFont().width(label) / 2, 34, MachineScreen.DIM, false);
    }

    /** 产物格堆满了：设备停下来等着，提示玩家接传送带或容器。 */
    private static boolean outputFull(MachineMenu menu) {
        ItemStack stack = menu.getSlot(0).getItem();
        return !stack.isEmpty() && stack.getCount() >= stack.getMaxStackSize();
    }

    /** 最后一行：产物格满了就提示，否则写满电产量。 */
    private static void lastLine(MachineScreen screen, GuiGraphicsExtractor graphics, Component rate, int y) {
        if (outputFull(screen.getMenu())) {
            graphics.text(screen.getFont(), Component.translatable("gui.universe.machine.output_full"), 8, y, MachineScreen.BAD, false);
        } else {
            graphics.text(screen.getFont(), rate, 8, y, MachineScreen.DIM, false);
        }
    }

    /** 采矿机：采的是什么矿、几个节点、还剩多少、满电时的产量。 */
    public static final class MiningMachine extends MachineScreen {
        public MiningMachine(MachineMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        @Override
        protected void extractInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            this.powerLine(graphics, 8, 22);
            outputLabel(this, graphics);
            int nodes = this.menu.data(MiningMachineBlockEntity.NODES);
            long id = this.menu.wide(MiningMachineBlockEntity.ITEM) - 1;
            int area = this.menu.data(MiningMachineBlockEntity.AREA);
            if (nodes == 0 || id < 0) {
                graphics.text(this.font, Component.translatable("gui.universe.mining_machine.none", area), 8, 36, BAD, false);
                graphics.text(this.font, Component.translatable("gui.universe.mining_machine.hint"), 8, 48, DIM, false);
                return;
            }
            Item item = BuiltInRegistries.ITEM.byId((int) id);
            graphics.text(this.font, Component.translatable("gui.universe.mining_machine.vein", new ItemStack(item).getHoverName(), nodes),
                    8, 36, TEXT, false);
            long remaining = this.menu.wide(MiningMachineBlockEntity.REMAINING);
            // 资源倍率 0（ADR-0028 §七）：每个节点的储量都是 Veins.INFINITE
            Component reserve = remaining >= Veins.INFINITE ? Component.translatable("gui.universe.mining_machine.infinite")
                    : Component.literal(String.format(Locale.ROOT, "%,d", remaining));
            graphics.text(this.font, Component.translatable("gui.universe.mining_machine.remaining", reserve), 8, 48, TEXT, false);
            double perMinute = nodes * 1200.0 / MiningMachineBlockEntity.TICKS_PER_ITEM * this.menu.data(MiningMachineBlockEntity.SPEED) / 100;
            lastLine(this, graphics, Component.translatable("gui.universe.mining_machine.rate",
                    String.format(Locale.ROOT, "%.0f", perMinute)), 60);
        }
    }

    /** 抽水站：下面有没有水。 */
    public static final class WaterPump extends MachineScreen {
        public WaterPump(MachineMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        @Override
        protected void extractInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            this.powerLine(graphics, 8, 22);
            outputLabel(this, graphics);
            int source = this.menu.data(WaterPumpBlockEntity.SOURCE);
            String key = switch (source) {
                case WaterPumpBlockEntity.WATER -> "ok";
                case WaterPumpBlockEntity.ACID -> "acid";
                default -> "no_water";
            };
            // 酸液源头上抽的是硫酸（ADR-0030）
            graphics.text(this.font, Component.translatable("gui.universe.water_pump." + key), 8, 36,
                    source == WaterPumpBlockEntity.NO_WATER ? BAD : GOOD, false);
            lastLine(this, graphics, Component.translatable("gui.universe.water_pump.rate",
                    1200 / WaterPumpBlockEntity.TICKS_PER_ITEM), 48);
        }
    }

    /** 分馏塔：每秒处理了多少氢、一共分出多少重氢；右边的格子各标一下。 */
    public static final class Fractionator extends MachineScreen {
        public Fractionator(MachineMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        @Override
        protected void extractInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            this.powerLine(graphics, 8, 22);
            Component in = Component.translatable("gui.universe.fractionator.input");
            graphics.text(this.font, in, 144 - this.font.width(in) / 2, 34, DIM, false);
            int rate = this.menu.data(FractionatorBlockEntity.RATE);
            if (this.menu.getSlot(FractionatorBlockEntity.INPUT).getItem().isEmpty() && rate == 0) {
                graphics.text(this.font, Component.translatable("gui.universe.fractionator.idle"), 8, 36, BAD, false);
            } else {
                graphics.text(this.font, Component.translatable("gui.universe.fractionator.rate", rate, 30), 8, 36, TEXT, false);
            }
            graphics.text(this.font, Component.translatable("gui.universe.fractionator.produced",
                    String.format(Locale.ROOT, "%,d", this.menu.wide(FractionatorBlockEntity.PRODUCED))), 8, 48, TEXT, false);
            graphics.text(this.font, Component.translatable("gui.universe.fractionator.hint"), 8, 60, DIM, false);
        }
    }

    /** 原油萃取站：涌泉现在的产量。 */
    public static final class OilExtractor extends MachineScreen {
        public OilExtractor(MachineMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        @Override
        protected void extractInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            this.powerLine(graphics, 8, 22);
            outputLabel(this, graphics);
            int rate = this.menu.data(OilExtractorBlockEntity.RATE);
            if (rate <= 0) {
                graphics.text(this.font, Component.translatable("gui.universe.oil_extractor.none"), 8, 36, BAD, false);
                return;
            }
            graphics.text(this.font, Component.translatable("gui.universe.oil_extractor.rate",
                    String.format(Locale.ROOT, "%.2f", rate / 100.0)), 8, 36, TEXT, false);
            lastLine(this, graphics, Component.translatable("gui.universe.oil_extractor.hint"), 48);
        }
    }
}
