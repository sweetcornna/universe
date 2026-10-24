package io.github.sweetcornna.universe.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.sweetcornna.universe.block.OreKind;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.Resource;
import io.github.sweetcornna.universe.network.ScanFilterPayload;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 按矿种高亮（ADR-0033 §八）：拿着多功能工具按 Shift + 模式键打开，列出这颗星球有的矿（按星球的资源表），选一种以后
 * 扫描只标这种矿。主世界没有资源表，列原版的矿。
 */
public class ScanFilterScreen extends Screen {
    private static final int WIDTH = 200;
    private static final int ROW = 20;
    private static final int ACCENT = 0xFF7FE0F7;
    private static final int TEXT = 0xFFDCE8EE;
    private static final int DIM = 0xFF8BA3AF;
    private static final List<Item> VANILLA = List.of(Items.RAW_IRON, Items.RAW_COPPER, Items.COAL, Items.RAW_GOLD, Items.REDSTONE,
            Items.LAPIS_LAZULI, Items.DIAMOND, Items.EMERALD);
    /** 第一行是"全部"（null）。 */
    private final List<@Nullable Item> rows = new ArrayList<>();
    private int left;
    private int top;

    public ScanFilterScreen() {
        super(Component.translatable("gui.universe.scan_filter.title"));
        this.rows.add(null);
        this.rows.addAll(oresHere());
    }

    /** 这颗星球有的矿开采出来的物品，按资源表的顺序；不在星球上时是原版的矿。 */
    static List<Item> oresHere() {
        Optional<Planet> planet = ClientGalaxy.currentPlanet();
        if (planet.isEmpty()) {
            return VANILLA;
        }
        Set<Item> items = new LinkedHashSet<>();
        for (Resource.Deposit deposit : planet.get().resources()) {
            OreKind kind = OreKind.of(deposit.resource());
            if (kind != null) {
                items.add(UniverseItems.oreDrop(kind));
            }
        }
        return List.copyOf(items);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int panelHeight() {
        return 34 + this.rows.size() * ROW + 16;
    }

    @Override
    protected void init() {
        this.left = (this.width - WIDTH) / 2;
        this.top = Math.max(4, (this.height - this.panelHeight()) / 2);
    }

    private String current() {
        return this.minecraft.player == null ? "" : this.minecraft.player.getData(UniverseAttachments.SCAN_FILTER);
    }

    private static String id(@Nullable Item item) {
        return item == null ? "" : BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < this.left + 8 || mouseX >= this.left + WIDTH - 8) {
            return -1;
        }
        int row = (int) Math.floor((mouseY - this.top - 30) / ROW);
        return row >= 0 && row < this.rows.size() ? row : -1;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(0, 0, this.width, this.height, 0x90000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int height = this.panelHeight();
        graphics.fill(this.left - 1, this.top - 1, this.left + WIDTH + 1, this.top + height + 1, 0xFF2E7A92);
        graphics.fill(this.left, this.top, this.left + WIDTH, this.top + height, 0xF2101A21);
        graphics.text(this.font, this.title, this.left + 10, this.top + 8, ACCENT, false);
        graphics.text(this.font, Component.translatable("gui.universe.scan_filter.hint"), this.left + 10, this.top + 19, DIM, false);
        String current = this.current();
        int hovered = this.rowAt(mouseX, mouseY);
        for (int i = 0; i < this.rows.size(); i++) {
            Item item = this.rows.get(i);
            int y = this.top + 30 + i * ROW;
            boolean selected = id(item).equals(current);
            if (selected || i == hovered) {
                graphics.fill(this.left + 8, y, this.left + WIDTH - 8, y + ROW - 2, selected ? 0xFF1F4A5A : 0xFF16323D);
            }
            if (selected) {
                graphics.fill(this.left + 8, y, this.left + 10, y + ROW - 2, ACCENT);
            }
            if (item == null) {
                graphics.text(this.font, Component.translatable("gui.universe.scan_filter.all"), this.left + 34, y + 5, TEXT, false);
            } else {
                ItemStack stack = new ItemStack(item);
                graphics.item(stack, this.left + 14, y + 1);
                graphics.text(this.font, stack.getHoverName(), this.left + 34, y + 5, TEXT, false);
            }
        }
        if (this.rows.size() == 1) {
            graphics.text(this.font, Component.translatable("gui.universe.scan_filter.none"), this.left + 34, this.top + 30 + ROW + 5, DIM, false);
        }
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int row = event.button() == InputConstants.MOUSE_BUTTON_LEFT ? this.rowAt(event.x(), event.y()) : -1;
        if (row < 0) {
            return super.mouseClicked(event, doubleClick);
        }
        ClientPacketDistributor.sendToServer(new ScanFilterPayload(id(this.rows.get(row))));
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        this.onClose();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (UniverseKeys.TOOL_MODE.matches(event)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}
