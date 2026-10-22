package io.github.sweetcornna.universe.client.portal;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.portal.Glyphs;
import io.github.sweetcornna.universe.core.portal.PortalAddress;
import io.github.sweetcornna.universe.network.PortalPayloads;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 先驱者传送门的拨号盘（ADR-0024）：十六个符文排成四行，认识的才按得动；按满六个拨号。上面写着这座门自己的地址。
 */
public class PortalDialScreen extends Screen {
    /** 十六个符文排成一条的贴图，每个 16×16，白色，画的时候乘颜色。 */
    public static final Identifier GLYPHS = Universe.id("textures/gui/portal/glyphs.png");
    private static final int WIDTH = 220;
    private static final int HEIGHT = 236;
    private static final int KEY = 26;
    private static final int ACCENT = 0xFF8FE8FF;
    private static final int DIM = 0xFF7A8890;
    private final PortalPayloads.Open payload;
    private final List<Integer> dialed = new ArrayList<>();
    private int left;
    private int top;
    private Button dial;
    private Button back;

    public PortalDialScreen(PortalPayloads.Open payload) {
        super(Component.translatable("gui.universe.portal.title"));
        this.payload = payload;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean usable(int glyph) {
        return this.payload.all() || Glyphs.knows(this.payload.known(), glyph);
    }

    @Override
    protected void init() {
        this.left = (this.width - WIDTH) / 2;
        this.top = (this.height - HEIGHT) / 2;
        int gridLeft = this.left + (WIDTH - 4 * KEY) / 2;
        int gridTop = this.top + 78;
        for (int glyph = 0; glyph < PortalAddress.GLYPHS; glyph++) {
            int g = glyph;
            GlyphButton key = new GlyphButton(gridLeft + glyph % 4 * KEY, gridTop + glyph / 4 * KEY, KEY - 2, KEY - 2, g, b -> this.press(g));
            key.active = this.usable(glyph);
            this.addRenderableWidget(key);
        }
        int buttonY = this.top + HEIGHT - 28;
        this.back = this.addRenderableWidget(Button.builder(Component.translatable("gui.universe.portal.back"), b -> this.erase())
                .bounds(this.left + 12, buttonY, 60, 20).build());
        this.dial = this.addRenderableWidget(Button.builder(Component.translatable("gui.universe.portal.dial"), b -> this.send())
                .bounds(this.left + WIDTH - 12 - 100, buttonY, 100, 20).build());
        this.refresh();
    }

    private void refresh() {
        this.dial.active = this.dialed.size() == PortalAddress.LENGTH;
        this.back.active = !this.dialed.isEmpty();
    }

    private void press(int glyph) {
        if (this.dialed.size() < PortalAddress.LENGTH) {
            this.dialed.add(glyph);
        }
        this.refresh();
    }

    private void erase() {
        if (!this.dialed.isEmpty()) {
            this.dialed.remove(this.dialed.size() - 1);
        }
        this.refresh();
    }

    private void send() {
        ClientPacketDistributor.sendToServer(new PortalPayloads.Dial(this.payload.dial(), List.copyOf(this.dialed)));
        this.onClose();
    }

    /** 画第几个符文，左上角在 (x, y)，边长 size。 */
    public static void glyph(GuiGraphicsExtractor graphics, int glyph, int x, int y, int size, int color) {
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(size / 16.0F);
        graphics.blit(RenderPipelines.GUI_TEXTURED, GLYPHS, 0, 0, glyph * 16, 0, 16, 16, 16 * PortalAddress.GLYPHS, 16, color);
        pose.popMatrix();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(0, 0, this.width, this.height, 0xB0000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(this.left - 1, this.top - 1, this.left + WIDTH + 1, this.top + HEIGHT + 1, 0xFF3A5E7A);
        graphics.fill(this.left, this.top, this.left + WIDTH, this.top + HEIGHT, 0xF0080A10);
        graphics.text(this.font, this.title, this.left + 12, this.top + 10, ACCENT, false);
        // 这座门自己的地址
        Component here = Component.translatable("gui.universe.portal.here");
        graphics.text(this.font, here, this.left + 12, this.top + 26, DIM, false);
        int x = this.left + 16 + this.font.width(here);
        for (int i = 0; i < this.payload.own().size(); i++) {
            glyph(graphics, this.payload.own().get(i), x + i * 13, this.top + 23, 12, 0xFFB8C4CC);
        }
        // 拨出去的地址：六个格子
        int slotLeft = this.left + (WIDTH - PortalAddress.LENGTH * 30 + 6) / 2;
        for (int i = 0; i < PortalAddress.LENGTH; i++) {
            int sx = slotLeft + i * 30;
            int sy = this.top + 42;
            graphics.fill(sx, sy, sx + 24, sy + 24, 0xFF1C2A36);
            graphics.fill(sx + 1, sy + 1, sx + 23, sy + 23, 0xFF0C1218);
            if (i < this.dialed.size()) {
                glyph(graphics, this.dialed.get(i), sx + 2, sy + 2, 20, ACCENT);
            }
        }
        Component known = Component.translatable("gui.universe.portal.known", Glyphs.count(this.payload.known()), PortalAddress.GLYPHS);
        // 符文键下面、按钮上面
        graphics.text(this.font, known, this.left + (WIDTH - this.font.width(known)) / 2, this.top + 78 + 4 * KEY + 4, DIM, false);
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    /** 一个符文键：认识的画亮的符文，不认识的画一个问号。 */
    private final class GlyphButton extends Button.Plain {
        private final int glyph;

        GlyphButton(int x, int y, int w, int h, int glyph, Button.OnPress onPress) {
            super(x, y, w, h, Component.translatable("gui.universe.portal.glyph", glyph + 1), onPress, Button.DEFAULT_NARRATION);
            this.glyph = glyph;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            this.extractDefaultSprite(graphics);
            if (this.active) {
                glyph(graphics, this.glyph, this.getX() + 3, this.getY() + 3, this.getWidth() - 6, 0xFFE8F8FF);
            } else {
                Component unknown = Component.literal("?");
                graphics.text(PortalDialScreen.this.font, unknown, this.getX() + (this.getWidth() - PortalDialScreen.this.font.width(unknown)) / 2,
                        this.getY() + (this.getHeight() - 8) / 2, DIM, false);
            }
        }
    }
}
