package io.github.sweetcornna.universe.client.poi;

import java.util.List;

import io.github.sweetcornna.universe.core.poi.PoiType;
import io.github.sweetcornna.universe.network.LocatePoiPayload;
import io.github.sweetcornna.universe.poi.Pois;
import io.github.sweetcornna.universe.poi.Waypoint;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 信号增强器的界面（ADR-0014）：显示身上有几份导航数据，五个按钮选要找的兴趣点种类，选了就消耗一份、关掉界面，
 * 结果由服务端发消息、设航点。
 */
public class SignalBoosterScreen extends Screen {
    private static final int WIDTH = 240;
    private static final int ACCENT = 0xFFFF6E9A;
    private final BlockPos booster;
    private int left;
    private int top;
    /** 说明文字换行后的高度决定下面几行的位置（中英文的行数不一样）。 */
    private int dataY;
    private int buttonsY;
    private int panelHeight;

    public SignalBoosterScreen(BlockPos booster) {
        super(Component.translatable("block.universe.signal_booster"));
        this.booster = booster;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int navigationData() {
        LocalPlayer player = this.minecraft.player;
        if (player == null) {
            return 0;
        }
        Inventory inventory = player.getInventory();
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(UniverseItems.NAVIGATION_DATA.get())) {
                total += inventory.getItem(i).getCount();
            }
        }
        return total;
    }

    private boolean creative() {
        return this.minecraft.player != null && this.minecraft.player.isCreative();
    }

    @Override
    protected void init() {
        List<PoiType> types = Pois.locatable();
        int hintHeight = this.font.split(this.hint(), WIDTH - 24).size() * this.font.lineHeight;
        this.dataY = 24 + hintHeight + 6;
        this.buttonsY = this.dataY + 16;
        this.panelHeight = this.buttonsY + types.size() * 24 + 30;
        this.left = (this.width - WIDTH) / 2;
        this.top = (this.height - this.panelHeight) / 2;
        boolean usable = this.creative() || this.navigationData() > 0;
        for (int i = 0; i < types.size(); i++) {
            PoiType type = types.get(i);
            Button button = Button.builder(Pois.name(type), b -> this.locate(type))
                    .bounds(this.left + 20, this.top + this.buttonsY + i * 24, WIDTH - 40, 20).build();
            button.active = usable;
            this.addRenderableWidget(button);
        }
    }

    private Component hint() {
        return Component.translatable("gui.universe.signal_booster.hint", Pois.LOCATE_RADIUS);
    }

    private void locate(PoiType type) {
        ClientPacketDistributor.sendToServer(new LocatePoiPayload(this.booster, type));
        this.onClose();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(0, 0, this.width, this.height, 0xB0000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int height = this.panelHeight;
        graphics.fill(this.left - 1, this.top - 1, this.left + WIDTH + 1, this.top + height + 1, 0xFF8C3A55);
        graphics.fill(this.left, this.top, this.left + WIDTH, this.top + height, 0xF00A0709);
        graphics.text(this.font, this.title, this.left + 12, this.top + 10, ACCENT, false);
        graphics.textWithWordWrap(this.font, this.hint(), this.left + 12, this.top + 24, WIDTH - 24, 0xFFD8C8CE, false);
        int count = this.navigationData();
        Component data = this.creative() ? Component.translatable("gui.universe.signal_booster.data_creative")
                : Component.translatable("gui.universe.signal_booster.data", count);
        graphics.text(this.font, data, this.left + 12, this.top + this.dataY, count > 0 || this.creative() ? 0xFFFFE08A : 0xFFFF6A5A, false);
        int bottom = this.top + this.buttonsY + Pois.locatable().size() * 24 + 6;
        if (count == 0 && !this.creative()) {
            graphics.textWithWordWrap(this.font, Component.translatable("gui.universe.signal_booster.no_data"), this.left + 12, bottom,
                    WIDTH - 24, 0xFF9A8A90, false);
        } else if (this.minecraft.player != null) {
            Waypoint waypoint = this.minecraft.player.getData(UniverseAttachments.WAYPOINT);
            Component current = waypoint.poiType()
                    .map(type -> (Component) Component.translatable("gui.universe.signal_booster.current", Pois.name(type),
                            (int) Math.round(Math.sqrt(this.minecraft.player.distanceToSqr(waypoint.pos().getX() + 0.5,
                                    this.minecraft.player.getY(), waypoint.pos().getZ() + 0.5)))))
                    .orElse(Component.translatable("gui.universe.signal_booster.no_waypoint"));
            graphics.text(this.font, current, this.left + 12, bottom, 0xFF9A8A90, false);
        }
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }
}
