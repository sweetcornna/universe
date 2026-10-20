package io.github.sweetcornna.universe.client.story;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.network.EndingPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.Musics;
import net.minecraft.util.FormattedCharSequence;

/**
 * 结局画面（ADR-0018）：黑底星空，探测器的最后一条日志从下往上慢慢滚过，接着是这个世界的统计。最后一行停在画面
 * 中间，过一会儿自己关掉。按住空格加速，Esc 随时关。放原版的片尾音乐。
 */
public class EndingScreen extends Screen {
    /** 结局正文有几段（语言键 story.universe.ending.1…）。 */
    public static final int PARAGRAPHS = 8;
    private static final int LINE_HEIGHT = 12;
    private static final float SPEED = 0.5F;
    private static final float FAST = 4F;
    /** 最后一行停下以后再等几刻关掉。 */
    private static final int HOLD_TICKS = 200;
    private static final int TITLE = 0xFF7FE3FF;
    private static final int BODY = 0xFFE4ECF2;
    private static final int LABEL = 0xFF8FA3B3;
    private static final int VALUE = 0xFFFFE3A0;

    private final EndingPayload stats;
    private final List<Line> lines = new ArrayList<>();
    private final float[][] stars;
    private float scroll;
    private float lastScroll;
    private boolean fast;
    private int held;

    private record Line(@Nullable FormattedCharSequence text, @Nullable FormattedCharSequence value, boolean centered, int color) {
    }

    public EndingScreen(EndingPayload stats) {
        super(Component.translatable("story.universe.ending.title"));
        this.stats = stats;
        Random random = new Random(7);
        this.stars = new float[160][];
        for (int i = 0; i < this.stars.length; i++) {
            this.stars[i] = new float[] {random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat() * 6.28F};
        }
    }

    @Override
    protected void init() {
        this.lines.clear();
        int width = Math.min(300, this.width - 40);
        this.centered(this.title.copy().withStyle(ChatFormatting.BOLD), TITLE);
        this.blank(2);
        for (int i = 1; i <= PARAGRAPHS; i++) {
            for (FormattedCharSequence line : this.font.split(Component.translatable("story.universe.ending." + i), width)) {
                this.lines.add(new Line(line, null, false, BODY));
            }
            this.blank(1);
        }
        this.centered(Component.translatable("story.universe.ending.end"), LABEL);
        this.blank(4);
        this.centered(Component.translatable("story.universe.ending.stats").withStyle(ChatFormatting.BOLD), TITLE);
        this.blank(1);
        long seconds = this.stats.gameTime() / 20;
        this.stat("time", Component.translatable("story.universe.stat.time.value", seconds / 3600, seconds / 60 % 60,
                this.stats.gameTime() / 24000 + 1));
        this.stat("systems", Component.literal(Integer.toString(this.stats.systems())));
        this.stat("bodies", Component.literal(Integer.toString(this.stats.bodies())));
        this.stat("species", Component.literal(Integer.toString(this.stats.species())));
        this.stat("dyson", Component.translatable("story.universe.stat.dyson.value", this.stats.layers(), this.stats.shells()));
        this.stat("power", Component.literal(MachineScreen.watts(Math.round(this.stats.dysonPower() * 10))));
        this.stat("techs", Component.translatable("story.universe.stat.techs.value", this.stats.techs()));
        this.stat("upgrades", Component.translatable("story.universe.stat.upgrades.value", this.stats.upgradeLevels()));
        this.blank(4);
        this.centered(Component.translatable("story.universe.ending.continue"), LABEL);
        this.blank(2);
        this.centered(Component.translatable("story.universe.ending.thanks").withStyle(ChatFormatting.BOLD), TITLE);
    }

    private void centered(Component text, int color) {
        this.lines.add(new Line(text.getVisualOrderText(), null, true, color));
    }

    private void blank(int count) {
        for (int i = 0; i < count; i++) {
            this.lines.add(new Line(null, null, false, BODY));
        }
    }

    private void stat(String key, Component value) {
        this.lines.add(new Line(Component.translatable("story.universe.stat." + key).getVisualOrderText(), value.getVisualOrderText(), false,
                LABEL));
    }

    /** 最后一行停在画面中间时的滚动量。 */
    private float maxScroll() {
        return this.height + (this.lines.size() - 1) * LINE_HEIGHT - (this.height / 2F - LINE_HEIGHT / 2F);
    }

    @Override
    public void tick() {
        this.lastScroll = this.scroll;
        float max = this.maxScroll();
        if (this.scroll < max) {
            this.scroll = Math.min(max, this.scroll + (this.fast ? FAST : SPEED));
        } else if (++this.held > HOLD_TICKS) {
            this.onClose();
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(0, 0, this.width, this.height, 0xFF02040A);
        // 星星：位置固定，跟着文字慢慢往上漂一点，亮度各自闪
        float drift = (this.lastScroll + (this.scroll - this.lastScroll) * a) * 0.15F;
        float time = (System.currentTimeMillis() % 100_000) / 1000F;
        for (float[] star : this.stars) {
            int x = (int) (star[0] * this.width);
            int y = (int) ((star[1] * this.height - drift) % this.height + this.height) % this.height;
            float glow = 0.45F + 0.4F * (float) Math.sin(time * (0.6F + star[2]) + star[3]);
            int alpha = (int) (255 * Math.max(0.1F, Math.min(1, glow)));
            int size = star[2] > 0.92F ? 2 : 1;
            graphics.fill(x, y, x + size, y + size, alpha << 24 | 0xDDE8FF);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        float scroll = this.lastScroll + (this.scroll - this.lastScroll) * a;
        int column = Math.min(300, this.width - 40);
        int left = (this.width - column) / 2;
        float y = this.height - scroll;
        for (Line line : this.lines) {
            if (y > -LINE_HEIGHT && y < this.height && line.text() != null) {
                int ty = Math.round(y);
                if (line.centered()) {
                    graphics.centeredText(this.font, line.text(), this.width / 2, ty, line.color());
                } else if (line.value() != null) {
                    // 统计：标签靠右对齐到中线左边，数值从中线右边开始
                    graphics.text(this.font, line.text(), this.width / 2 - 6 - this.font.width(line.text()), ty, line.color(), false);
                    graphics.text(this.font, line.value(), this.width / 2 + 6, ty, VALUE, false);
                } else {
                    graphics.text(this.font, line.text(), left, ty, line.color(), false);
                }
            }
            y += LINE_HEIGHT;
        }
        Component hint = Component.translatable("story.universe.ending.skip");
        graphics.text(this.font, hint, this.width - 8 - this.font.width(hint), this.height - 14, 0x60FFFFFF, false);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_SPACE) {
            this.fast = true;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (event.key() == InputConstants.KEY_SPACE) {
            this.fast = false;
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        this.minecraft.getMusicManager().stopPlaying(Musics.CREDITS);
    }

    @Override
    public Music getBackgroundMusic() {
        return Musics.CREDITS;
    }
}
