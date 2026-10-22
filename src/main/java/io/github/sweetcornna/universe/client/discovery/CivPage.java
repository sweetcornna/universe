package io.github.sweetcornna.universe.client.discovery;

import static io.github.sweetcornna.universe.client.factory.MachineScreen.ACCENT;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.DIM;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.TEXT;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.WARN;

import java.util.Optional;

import org.joml.Matrix3x2fStack;

import io.github.sweetcornna.universe.civ.Civ;
import io.github.sweetcornna.universe.client.TextWrap;
import io.github.sweetcornna.universe.client.civ.ClientCiv;
import io.github.sweetcornna.universe.core.civ.AlienTongue;
import io.github.sweetcornna.universe.core.civ.CivState;
import io.github.sweetcornna.universe.core.civ.Guild;
import io.github.sweetcornna.universe.core.civ.Lexicon;
import io.github.sweetcornna.universe.core.civ.Race;
import io.github.sweetcornna.universe.core.civ.Standing;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

/**
 * 发现日志的"文明"页（ADR-0027）：左边三个种族的声望、档、认识几个词，下面三个公会的声望；右边是选中种族的词表，
 * 认识的写"外星词 = 意思"，不认识的写"外星词 = ？？？"。
 */
final class CivPage {
    static final int LEFT_WIDTH = 176;
    private static final int RACE_ROW = 36;
    private static final int GUILD_ROW = 26;
    private static final int PADDING = 6;
    private static final int WORD_ROW = 11;
    private static final int WORD_COLUMN = 120;
    private static final int BAR_BACK = 0xFF1A262E;
    private static final int UNKNOWN = 0xFF5C666E;

    private final Font font;
    private Race selected;
    private double wordScroll;
    private int wordHeight;
    private int top;
    private int bottom;
    private int raceTop;

    CivPage(Font font) {
        this.font = font;
        this.selected = ClientCiv.dominantHere().orElse(Race.TRADER);
    }

    void draw(GuiGraphicsExtractor graphics, int top, int bottom, int width, int mouseX, int mouseY) {
        this.top = top;
        this.bottom = bottom;
        CivState state = ClientCiv.state();
        Optional<Race> here = ClientCiv.dominantHere();
        graphics.fill(0, top, LEFT_WIDTH, bottom, 0xC00A1218);
        graphics.fill(LEFT_WIDTH - 1, top, LEFT_WIDTH, bottom, 0x604FB4D0);

        int y = this.section(graphics, Component.translatable("gui.universe.civ.races"), 0, LEFT_WIDTH, top);
        this.raceTop = y;
        for (Race race : Race.values()) {
            boolean selected = race == this.selected;
            boolean hover = !selected && mouseX < LEFT_WIDTH && mouseY >= y && mouseY < y + RACE_ROW;
            if (selected || hover) {
                graphics.fill(0, y, LEFT_WIDTH - 1, y + RACE_ROW, selected ? 0x502A6A80 : 0x30FFFFFF);
            }
            if (selected) {
                graphics.fill(0, y, 2, y + RACE_ROW, ACCENT);
            }
            int color = ARGB.opaque(race.color());
            int x = PADDING + 2;
            graphics.text(this.font, Civ.raceName(race), x, y + 3, color, false);
            if (here.isPresent() && here.get() == race) {
                int nameWidth = this.font.width(Civ.raceName(race));
                this.small(graphics, Component.translatable("gui.universe.civ.here"), x + nameWidth + 4, y + 4, DIM);
            }
            Standing.Tier tier = state.tier(race);
            Component tierName = Civ.tierName(tier);
            graphics.text(this.font, tierName, LEFT_WIDTH - PADDING - 2 - this.font.width(tierName), y + 3,
                    tier == Standing.Tier.HOSTILE ? WARN : TEXT, false);
            this.bar(graphics, state.standing(race), tier, x, y + 14, LEFT_WIDTH - x - PADDING - 2, color);
            this.small(graphics, Component.translatable("gui.universe.civ.words_known", state.knownCount(race), Lexicon.SIZE), x, y + 24, DIM);
            y += RACE_ROW;
        }

        y = this.section(graphics, Component.translatable("gui.universe.civ.guilds"), 0, LEFT_WIDTH, y + 4);
        for (Guild guild : Guild.values()) {
            int x = PADDING + 2;
            Standing.Tier tier = state.tier(guild);
            graphics.text(this.font, Civ.guildName(guild), x, y + 3, TEXT, false);
            Component tierName = Civ.guildTierName(tier);
            graphics.text(this.font, tierName, LEFT_WIDTH - PADDING - 2 - this.font.width(tierName), y + 3,
                    tier == Standing.Tier.HOSTILE ? WARN : DIM, false);
            this.bar(graphics, state.standing(guild), tier, x, y + 14, LEFT_WIDTH - x - PADDING - 2, ACCENT);
            y += GUILD_ROW;
        }

        this.drawWords(graphics, state, width);
    }

    /** 右边：选中种族的词表，按概念的顺序排成几列。 */
    private void drawWords(GuiGraphicsExtractor graphics, CivState state, int width) {
        int left = LEFT_WIDTH + PADDING + 2;
        int room = width - left - PADDING - 4;
        Race race = this.selected;
        graphics.enableScissor(LEFT_WIDTH, this.top, width, this.bottom);
        int y = this.top + 6 - (int) this.wordScroll;
        int start = y;
        y = this.title(graphics, Component.translatable("gui.universe.civ.lexicon", Civ.raceName(race)), left, y, ARGB.opaque(race.color()));
        graphics.text(this.font, Component.translatable("gui.universe.civ.words_known", state.knownCount(race), Lexicon.SIZE), left, y, TEXT, false);
        y += 11;
        y = TextWrap.draw(graphics, this.font, Component.translatable("gui.universe.civ.lexicon_hint"), left, y, room, DIM, false) + 5;
        Optional<AlienTongue> tongue = ClientCiv.tongue(race);
        int columns = Math.max(1, room / WORD_COLUMN);
        int columnWidth = room / columns;
        int rows = (Lexicon.SIZE + columns - 1) / columns;
        for (int concept = 0; concept < Lexicon.SIZE; concept++) {
            // 一列一列往下排，读起来顺着概念的顺序
            int column = concept / rows;
            int row = concept % rows;
            int x = left + column * columnWidth;
            int rowY = y + row * WORD_ROW;
            boolean known = state.knows(race, concept);
            String alien = tongue.isPresent() ? tongue.get().word(concept) : "?";
            Component meaning = known ? Civ.wordMeaning(concept) : Component.translatable("gui.universe.civ.unknown_word");
            String line = this.font.plainSubstrByWidth(alien + " = " + meaning.getString(), columnWidth - 4);
            graphics.text(this.font, line, x, rowY, known ? 0xFFE6F2F6 : UNKNOWN, false);
        }
        y += rows * WORD_ROW;
        graphics.disableScissor();
        this.wordHeight = y + (int) this.wordScroll - start;
        int visible = this.bottom - this.top - 6;
        if (this.wordHeight > visible) {
            int barHeight = Math.max(12, visible * visible / this.wordHeight);
            int barTop = this.top + 6 + (int) ((visible - barHeight) * this.wordScroll / (this.wordHeight - visible));
            graphics.fill(width - 3, barTop, width - 1, barTop + barHeight, 0x804FB4D0);
        }
    }

    /** 声望条：这一档里走到哪了，右边写"现在 / 下一档"。 */
    private void bar(GuiGraphicsExtractor graphics, int standing, Standing.Tier tier, int x, int y, int width, int color) {
        Component numbers = Component.literal(standing + " / " + tier.next());
        int textWidth = (int) Math.ceil(this.font.width(numbers) * 0.75F);
        int barWidth = width - textWidth - 4;
        int span = Math.max(1, tier.next() - tier.min());
        int filled = Math.clamp((long) barWidth * (standing - tier.min()) / span, 0, barWidth);
        graphics.fill(x, y + 1, x + barWidth, y + 5, BAR_BACK);
        graphics.fill(x, y + 1, x + filled, y + 5, color);
        this.small(graphics, numbers, x + barWidth + 4, y, DIM);
    }

    private int section(GuiGraphicsExtractor graphics, Component title, int left, int right, int y) {
        graphics.fill(left, y, right - 1, y + 14, 0xD0081016);
        graphics.text(this.font, title, left + PADDING, y + 3, TEXT, false);
        return y + 14;
    }

    private int title(GuiGraphicsExtractor graphics, Component text, int x, int y, int color) {
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(1.25F);
        graphics.text(this.font, text, 0, 0, color, true);
        pose.popMatrix();
        return y + 14;
    }

    private void small(GuiGraphicsExtractor graphics, Component text, int x, int y, int color) {
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(0.75F);
        graphics.text(this.font, text, 0, 0, color, false);
        pose.popMatrix();
    }

    // ---- 输入

    /** 点了种族那一行就换词表；返回点没点中。 */
    boolean click(double x, double y) {
        if (x >= LEFT_WIDTH || y < this.raceTop) {
            return false;
        }
        int row = (int) ((y - this.raceTop) / RACE_ROW);
        if (row < 0 || row >= Race.values().length) {
            return false;
        }
        if (Race.values()[row] != this.selected) {
            this.selected = Race.values()[row];
            this.wordScroll = 0;
        }
        return true;
    }

    void scroll(double x, double amount) {
        if (x < LEFT_WIDTH) {
            return;
        }
        int visible = this.bottom - this.top - 6;
        this.wordScroll = Math.clamp(this.wordScroll - amount * WORD_ROW * 2, 0, Math.max(0, this.wordHeight - visible));
    }
}
