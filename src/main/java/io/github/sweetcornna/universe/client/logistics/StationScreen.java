package io.github.sweetcornna.universe.client.logistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.core.logistics.Logic;
import io.github.sweetcornna.universe.core.logistics.StationKind;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.logistics.StationBlockEntity;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 物流站界面（ADR-0015）：上面是电池、载具投放口和无人机航程，下面每格一行：物品、存量条、本地和远程的供需、上限。
 *
 * <p>手里拿着东西点物品框：空格子设定成这种物品，同种物品存进去；空手点物品框取出一组。空手点投放口取出空闲的载具。
 * 上限的 −/+ 一次 100，按住 Shift 一次 1000，在存量条上滚轮也行。轨道采集器只看产量和取货。
 */
public class StationScreen extends MachineScreen {
    private static final int ROW_X = 8;
    private static final int ROW_HEIGHT = StationBlockEntity.ROW_HEIGHT;
    private static final int STATION_ROWS_Y = StationBlockEntity.ROWS_Y;
    private static final int COLLECTOR_ROWS_Y = StationBlockEntity.COLLECTOR_ROWS_Y;
    private static final int BAR_X = 30;
    private static final int BAR_WIDTH = 100;
    private static final int LOCAL_X = 134;
    private static final int REMOTE_X = 174;
    private static final int LOGIC_WIDTH = 38;
    private static final int MINUS_X = 216;
    private static final int PLUS_X = 229;
    private static final int CLEAR_X = 242;
    private static final int SMALL = 11;
    private static final int RANGE_MINUS_X = 126;
    private static final int RANGE_PLUS_X = 139;
    private static final int RANGE_Y = 38;
    private static final int SUPPLY = 0xFF4FA3F7;
    private static final int DEMAND = 0xFFE89A3C;

    private final StationKind kind;

    public StationScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        MachineMenu.Type type = menu.type();
        this.kind = type == UniverseMenus.INTERSTELLAR_LOGISTICS_STATION ? StationKind.INTERSTELLAR
                : type == UniverseMenus.ORBITAL_COLLECTOR ? StationKind.COLLECTOR : StationKind.PLANETARY;
    }

    // ---- 布局（和 StationBlockEntity#layout 对齐）

    private int rowsY() {
        return this.kind == StationKind.COLLECTOR ? COLLECTOR_ROWS_Y : STATION_ROWS_Y;
    }

    private int rowY(int row) {
        return this.rowsY() + row * ROW_HEIGHT;
    }

    // ---- 数据

    private int rowData(int row, int field) {
        return this.menu.data(StationBlockEntity.ROWS + row * StationBlockEntity.ROW_SIZE + field);
    }

    private Item rowItem(int row) {
        int id = (int) MachineMenu.join(this.rowData(row, 0), this.rowData(row, 1)) - 1;
        return id < 0 ? Items.AIR : BuiltInRegistries.ITEM.byId(id);
    }

    private int count(int row) {
        return this.rowData(row, 2);
    }

    private int max(int row) {
        return this.rowData(row, 3);
    }

    private Logic local(int row) {
        return Logic.values()[(this.rowData(row, 4) & 3) % Logic.values().length];
    }

    private Logic remote(int row) {
        return Logic.values()[((this.rowData(row, 4) >> 2) & 3) % Logic.values().length];
    }

    // ---- 画

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = this.leftPos;
        int y = this.topPos;
        // 空着的投放口画出该放的东西
        for (int i = 0; i < this.menu.type().slots().size(); i++) {
            Slot slot = this.menu.getSlot(i);
            if (!slot.hasItem()) {
                graphics.fakeItem(portItem(i), x + slot.x, y + slot.y);
                graphics.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xA00A1015);
            }
        }
        for (int row = 0; row < this.kind.slots(); row++) {
            int ry = y + this.rowY(row);
            slotFrame(graphics, x + ROW_X, ry + 1);
            Item item = this.rowItem(row);
            if (item != Items.AIR) {
                graphics.fakeItem(new ItemStack(item), x + ROW_X, ry + 1);
            }
            // 行与行之间一道细线
            graphics.fill(x + 6, ry + ROW_HEIGHT - 1, x + this.imageWidth - 6, ry + ROW_HEIGHT, 0x302E7A92);
        }
    }

    private static ItemStack portItem(int index) {
        return new ItemStack(switch (index) {
            case 0 -> UniverseItems.LOGISTICS_DRONE.get();
            case 1 -> UniverseItems.LOGISTICS_VESSEL.get();
            default -> UniverseItems.SPACE_WARPER.get();
        });
    }

    @Override
    protected void extractInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.kind == StationKind.COLLECTOR) {
            graphics.text(this.font, Component.translatable("gui.universe.orbital_collector.hint"), 8, 22, DIM, false);
            graphics.text(this.font, Component.translatable("gui.universe.station.item"), BAR_X, COLLECTOR_ROWS_Y - 10, DIM, false);
            graphics.text(this.font, Component.translatable("gui.universe.orbital_collector.rate"), LOCAL_X, COLLECTOR_ROWS_Y - 10, DIM,
                    false);
        } else {
            this.header(graphics, mouseX, mouseY);
        }
        for (int row = 0; row < this.kind.slots(); row++) {
            this.row(graphics, row, mouseX, mouseY);
        }
    }

    private void header(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        long energy = MachineMenu.join(this.menu.data(StationBlockEntity.ENERGY), this.menu.data(StationBlockEntity.ENERGY + 1)) * 100;
        long battery = this.kind.battery();
        graphics.text(this.font, Component.translatable("gui.universe.station.energy", joules(energy), joules(battery)), 8, 20, TEXT, false);
        long charge = MachineMenu.join(this.menu.data(StationBlockEntity.CHARGE), this.menu.data(StationBlockEntity.CHARGE + 1));
        Component status;
        int color;
        if (this.menu.data(0) < 0) {
            status = Component.translatable("gui.universe.station.unpowered");
            color = BAD;
        } else if (charge > 0) {
            status = Component.literal("+" + watts(charge));
            color = GOOD;
        } else if (energy >= battery) {
            status = Component.translatable("gui.universe.station.full");
            color = DIM;
        } else {
            status = Component.translatable("gui.universe.station.no_surplus");
            color = WARN;
        }
        // 电量条后面跟着充电状态
        bar(graphics, 8, 31, 100, 4, battery == 0 ? 0 : (float) energy / battery, energy * 5 < battery ? WARN : GOOD);
        graphics.text(this.font, status, 113, 29, color, false);
        // 无人机航程
        graphics.text(this.font, Component.translatable("gui.universe.station.range", this.menu.data(StationBlockEntity.RANGE)), 8, RANGE_Y + 2,
                TEXT, false);
        smallButton(graphics, RANGE_MINUS_X, RANGE_Y, "-", mouseX, mouseY);
        smallButton(graphics, RANGE_PLUS_X, RANGE_Y, "+", mouseX, mouseY);
        // 投放口下面的数量
        List<Slot> ports = this.menu.slots.subList(0, this.menu.type().slots().size());
        for (int i = 0; i < ports.size(); i++) {
            Slot slot = ports.get(i);
            String text = switch (i) {
                case 0 -> vehicles(this.menu.data(StationBlockEntity.DRONES));
                case 1 -> vehicles(this.menu.data(StationBlockEntity.VESSELS));
                default -> String.valueOf(this.menu.data(StationBlockEntity.WARPERS));
            };
            graphics.centeredText(this.font, Component.literal(text), slot.x + 8, 40, DIM);
        }
        // 列名
        int y = STATION_ROWS_Y - 10;
        graphics.text(this.font, Component.translatable("gui.universe.station.item"), BAR_X, y, DIM, false);
        graphics.text(this.font, Component.translatable("gui.universe.station.local"), LOCAL_X, y, DIM, false);
        if (this.kind.remote()) {
            graphics.text(this.font, Component.translatable("gui.universe.station.remote"), REMOTE_X, y, DIM, false);
        }
        graphics.text(this.font, Component.translatable("gui.universe.station.limit"), MINUS_X, y, DIM, false);
    }

    /** 一万以上写成“12k”“10.5k”，确切的数在提示里。 */
    static String compact(int value) {
        if (value < 10_000) {
            return String.valueOf(value);
        }
        return value % 1000 == 0 ? value / 1000 + "k" : String.format(Locale.ROOT, "%.1fk", value / 1000.0);
    }

    /** 放不下就截短，末尾加省略号。 */
    private String fit(Component text, int width) {
        if (this.font.width(text) <= width) {
            return text.getString();
        }
        return this.font.plainSubstrByWidth(text.getString(), width - this.font.width("…")) + "…";
    }

    /** 空闲/总数。 */
    private static String vehicles(int packed) {
        return ((packed >> 8) & 0xFF) + "/" + (packed & 0xFF);
    }

    private void row(GuiGraphicsExtractor graphics, int row, int mouseX, int mouseY) {
        int y = this.rowY(row);
        Item item = this.rowItem(row);
        int count = this.count(row);
        int max = this.max(row);
        int incoming = this.rowData(row, 5);
        if (item == Items.AIR) {
            graphics.text(this.font, Component.translatable("gui.universe.station.empty_slot"), BAR_X, y + 2, DIM, false);
        } else {
            Component name = Component.translatable(item.getDescriptionId());
            String amount = this.kind == StationKind.COLLECTOR ? compact(count) : compact(count) + "/" + compact(max);
            int amountWidth = this.font.width(amount);
            graphics.text(this.font, this.fit(name, BAR_WIDTH - amountWidth - 4), BAR_X, y + 2, TEXT, false);
            graphics.text(this.font, amount, BAR_X + BAR_WIDTH - amountWidth, y + 2, count >= max && max > 0 ? WARN : TEXT, false);
            int capacity = this.kind.slotCapacity();
            int limit = this.kind == StationKind.COLLECTOR ? capacity : Math.max(1, max);
            bar(graphics, BAR_X, y + 13, BAR_WIDTH, 3, (float) count / limit, SUPPLY);
            if (this.kind != StationKind.COLLECTOR && incoming > 0) {
                // 在途的货：接在存量后面，颜色淡一些
                int from = BAR_X + Math.round(BAR_WIDTH * Math.min(1, (float) count / limit));
                int to = BAR_X + Math.round(BAR_WIDTH * Math.min(1, (float) (count + incoming) / limit));
                graphics.fill(from, y + 13, to, y + 16, 0x804FA3F7);
            }
        }
        if (this.kind == StationKind.COLLECTOR) {
            double rate = incoming / 1000.0;
            graphics.text(this.font, String.format(Locale.ROOT, "+%.2f/s", rate), LOCAL_X, y + 6, rate > 0 ? GOOD : BAD, false);
            return;
        }
        if (item == Items.AIR) {
            return;
        }
        this.logicButton(graphics, LOCAL_X, y + 3, this.local(row), mouseX, mouseY);
        if (this.kind.remote()) {
            this.logicButton(graphics, REMOTE_X, y + 3, this.remote(row), mouseX, mouseY);
        }
        smallButton(graphics, MINUS_X, y + 3, "-", mouseX, mouseY);
        smallButton(graphics, PLUS_X, y + 3, "+", mouseX, mouseY);
        smallButton(graphics, CLEAR_X, y + 3, "×", mouseX, mouseY);
    }

    private void logicButton(GuiGraphicsExtractor graphics, int x, int y, Logic logic, int mouseX, int mouseY) {
        int color = switch (logic) {
            case SUPPLY -> SUPPLY;
            case DEMAND -> DEMAND;
            case STORAGE -> DIM;
        };
        boolean hover = inside(mouseX, mouseY, x, y, LOGIC_WIDTH, 14);
        graphics.fill(x, y, x + LOGIC_WIDTH, y + 14, hover ? 0xFF24404D : 0xFF16262E);
        graphics.outline(x, y, LOGIC_WIDTH, 14, color);
        graphics.centeredText(this.font, Component.translatable("gui.universe.station.logic." + logic.name().toLowerCase(Locale.ROOT)),
                x + LOGIC_WIDTH / 2, y + 3, color);
    }

    private void smallButton(GuiGraphicsExtractor graphics, int x, int y, String label, int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, x, y, SMALL, 14);
        graphics.fill(x, y, x + SMALL, y + 14, hover ? 0xFF2E7A92 : 0xFF16262E);
        graphics.outline(x, y, SMALL, 14, BORDER);
        graphics.centeredText(this.font, Component.literal(label), x + SMALL / 2 + 1, y + 3, TEXT);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    // ---- 提示

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        List<Component> lines = this.tooltip(mouseX - this.leftPos, mouseY - this.topPos);
        if (!lines.isEmpty()) {
            graphics.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
        }
    }

    private List<Component> tooltip(int x, int y) {
        List<Component> lines = new ArrayList<>();
        Slot hovered = this.hoveredSlot;
        if (hovered != null && hovered.index < this.menu.type().slots().size() && !hovered.hasItem() && this.menu.getCarried().isEmpty()) {
            String key = switch (hovered.index) {
                case 0 -> "drones";
                case 1 -> "vessels";
                default -> "warpers";
            };
            lines.add(portItem(hovered.index).getHoverName());
            lines.add(Component.translatable("gui.universe.station.port." + key).withStyle(ChatFormatting.GRAY));
            return lines;
        }
        if (this.kind != StationKind.COLLECTOR && inside(x, y, RANGE_MINUS_X, RANGE_Y, RANGE_PLUS_X + SMALL - RANGE_MINUS_X, 14)) {
            lines.add(Component.translatable("gui.universe.station.range_hint").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        int row = this.rowAt(y);
        if (row < 0) {
            return lines;
        }
        int ry = this.rowY(row);
        Item item = this.rowItem(row);
        if (inside(x, y, ROW_X - 1, ry, 18, 18)) {
            if (item != Items.AIR) {
                lines.add(Component.translatable(item.getDescriptionId()));
            }
            if (this.kind != StationKind.COLLECTOR) {
                lines.add(Component.translatable(item == Items.AIR ? "gui.universe.station.set_hint" : "gui.universe.station.deposit_hint")
                        .withStyle(ChatFormatting.GRAY));
            }
            if (item != Items.AIR) {
                lines.add(Component.translatable("gui.universe.station.withdraw_hint").withStyle(ChatFormatting.GRAY));
            }
            return lines;
        }
        if (item == Items.AIR) {
            return lines;
        }
        if (inside(x, y, BAR_X, ry, BAR_WIDTH, ROW_HEIGHT - 2)) {
            lines.add(Component.translatable("gui.universe.station.stock", this.count(row), this.max(row), this.kind.slotCapacity()));
            if (this.kind != StationKind.COLLECTOR) {
                int incoming = this.rowData(row, 5);
                if (incoming > 0) {
                    lines.add(Component.translatable("gui.universe.station.incoming", incoming).withStyle(ChatFormatting.AQUA));
                }
                lines.add(Component.translatable("gui.universe.station.scroll_hint").withStyle(ChatFormatting.GRAY));
            }
            return lines;
        }
        if (this.kind == StationKind.COLLECTOR) {
            return lines;
        }
        boolean local = inside(x, y, LOCAL_X, ry + 3, LOGIC_WIDTH, 14);
        boolean remote = this.kind.remote() && inside(x, y, REMOTE_X, ry + 3, LOGIC_WIDTH, 14);
        if (local || remote) {
            Logic logic = local ? this.local(row) : this.remote(row);
            String scope = local ? "local" : "remote";
            lines.add(Component.translatable("gui.universe.station." + scope + "_title"));
            lines.add(Component.translatable("gui.universe.station.logic_hint." + scope + "." + logic.name().toLowerCase(Locale.ROOT))
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.universe.station.cycle_hint").withStyle(ChatFormatting.DARK_GRAY));
        } else if (inside(x, y, MINUS_X, ry + 3, PLUS_X + SMALL - MINUS_X, 14)) {
            lines.add(Component.translatable("gui.universe.station.limit_hint").withStyle(ChatFormatting.GRAY));
        } else if (inside(x, y, CLEAR_X, ry + 3, SMALL, 14)) {
            lines.add(Component.translatable("gui.universe.station.clear_hint").withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    private int rowAt(int y) {
        int row = Math.floorDiv(y - this.rowsY(), ROW_HEIGHT);
        return row >= 0 && row < this.kind.slots() ? row : -1;
    }

    // ---- 点击

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int x = (int) Math.floor(event.x() - this.leftPos);
        int y = (int) Math.floor(event.y() - this.topPos);
        boolean left = event.button() == InputConstants.MOUSE_BUTTON_LEFT;
        boolean shift = event.hasShiftDown();
        // 空手点投放口：取出空闲的载具
        Slot hovered = this.hoveredSlot;
        if (left && hovered != null && hovered.index < this.menu.type().slots().size() && !hovered.hasItem()
                && this.menu.getCarried().isEmpty()) {
            return this.press(StationBlockEntity.TAKE_DRONES + hovered.index);
        }
        if (!left) {
            return super.mouseClicked(event, doubleClick);
        }
        if (this.kind != StationKind.COLLECTOR && inside(x, y, RANGE_MINUS_X, RANGE_Y, SMALL, 14)) {
            return this.press(StationBlockEntity.RANGE_MINUS);
        }
        if (this.kind != StationKind.COLLECTOR && inside(x, y, RANGE_PLUS_X, RANGE_Y, SMALL, 14)) {
            return this.press(StationBlockEntity.RANGE_PLUS);
        }
        int row = this.rowAt(y);
        if (row >= 0) {
            int ry = this.rowY(row);
            int base = row * 16;
            if (inside(x, y, ROW_X - 1, ry, 18, 18)) {
                return this.press(base + (this.menu.getCarried().isEmpty() ? StationBlockEntity.WITHDRAW : StationBlockEntity.SET_OR_DEPOSIT));
            }
            if (this.kind != StationKind.COLLECTOR && this.rowItem(row) != Items.AIR) {
                if (inside(x, y, LOCAL_X, ry + 3, LOGIC_WIDTH, 14)) {
                    return this.press(base + StationBlockEntity.CYCLE_LOCAL);
                }
                if (this.kind.remote() && inside(x, y, REMOTE_X, ry + 3, LOGIC_WIDTH, 14)) {
                    return this.press(base + StationBlockEntity.CYCLE_REMOTE);
                }
                if (inside(x, y, MINUS_X, ry + 3, SMALL, 14)) {
                    return this.press(base + (shift ? StationBlockEntity.MAX_MINUS_BIG : StationBlockEntity.MAX_MINUS));
                }
                if (inside(x, y, PLUS_X, ry + 3, SMALL, 14)) {
                    return this.press(base + (shift ? StationBlockEntity.MAX_PLUS_BIG : StationBlockEntity.MAX_PLUS));
                }
                if (inside(x, y, CLEAR_X, ry + 3, SMALL, 14)) {
                    return this.press(base + StationBlockEntity.CLEAR);
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int x = (int) Math.floor(mouseX - this.leftPos);
        int y = (int) Math.floor(mouseY - this.topPos);
        int row = this.rowAt(y);
        if (this.kind != StationKind.COLLECTOR && row >= 0 && scrollY != 0 && this.rowItem(row) != Items.AIR
                && inside(x, y, BAR_X, this.rowY(row), BAR_WIDTH, ROW_HEIGHT - 2)) {
            return this.press(row * 16 + (scrollY > 0 ? StationBlockEntity.MAX_PLUS : StationBlockEntity.MAX_MINUS));
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean press(int button) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, button);
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
        return true;
    }
}
