package io.github.sweetcornna.universe.client.civ;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.sweetcornna.universe.client.TextWrap;
import io.github.sweetcornna.universe.client.galaxy.GalaxyText;
import io.github.sweetcornna.universe.core.civ.Lexicon;
import io.github.sweetcornna.universe.core.civ.NpcRole;
import io.github.sweetcornna.universe.core.civ.Race;
import io.github.sweetcornna.universe.core.civ.Standing;
import io.github.sweetcornna.universe.core.civ.Value;
import io.github.sweetcornna.universe.network.DialoguePayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 和外星 NPC 对话的界面（ADR-0027 §四），照兴趣点的文字界面逐行显示。上面是名字、种族和你在这个种族的声望档；中间一段外星话
 * （认识的词换成译文、加亮，不认识的照外星写法）和一段旁白；下面三个选项，答过了变灰。最底下一排是送礼、请教一个词、离开。
 * 选完、送完、问完以后 NPC 的回话和得到的东西接在旁白下面。
 */
public class DialogueScreen extends Screen {
    private static final int TICKS_PER_LINE = 2;
    private static final int MAX_WIDTH = 360;
    private static final int PAD = 14;
    private static final int HEADER = 42;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 2;
    private static final int MAX_DISTANCE = 8;
    private static final int UNKNOWN_WORD = 0xFF8C93A0;

    private DialoguePayloads.State state;
    private final Race race;
    private final NpcRole role;
    private List<FormattedCharSequence> lines = List.of();
    private int panelWidth;
    private int panelHeight;
    private int left;
    private int top;
    private int ticks;
    private final List<Button> options = new ArrayList<>();
    private @Nullable Button gift;
    private @Nullable Button ask;
    private @Nullable Component giftTip;
    private @Nullable Component askTip;

    public DialogueScreen(DialoguePayloads.State state) {
        super(Component.literal(GalaxyText.pick(state.nameZh(), state.nameEn())));
        this.state = state;
        this.race = Race.values()[Math.floorMod(state.race(), Race.values().length)];
        this.role = NpcRole.values()[Math.floorMod(state.role(), NpcRole.values().length)];
    }

    /** 对着哪个 NPC。 */
    public int entity() {
        return this.state.entity();
    }

    /** 服务端发来了新的内容（选完、送完、问完）：已经显示出来的行不再重新逐行显示。 */
    public void update(DialoguePayloads.State state) {
        int shown = this.shown();
        this.state = state;
        this.rebuildWidgets();
        this.ticks = Math.min(shown, this.lines.size()) * TICKS_PER_LINE;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- 文字

    private int accent() {
        return 0xFF000000 | this.race.color();
    }

    /** 外星话：认识的词换成译文、加亮，不认识的照外星写法；后面写认识了几个。 */
    private Component alienLine() {
        MutableComponent line = Component.literal("“");
        int known = 0;
        for (int i = 0; i < this.state.words().size(); i++) {
            if (i > 0) {
                line.append(" ");
            }
            int concept = this.state.words().get(i);
            if (concept >= 0 && concept < Lexicon.SIZE && (this.state.known() >>> concept & 1) != 0) {
                known++;
                line.append(Component.translatable("civ.universe.word." + Lexicon.id(concept)).withColor(this.race.color())
                        .withStyle(ChatFormatting.UNDERLINE));
            } else {
                line.append(Component.literal(this.state.alien().get(i)).withColor(UNKNOWN_WORD & 0xFFFFFF).withStyle(ChatFormatting.ITALIC));
            }
        }
        line.append("”  ");
        line.append(Component.translatable("gui.universe.dialogue.known", known, this.state.words().size()).withStyle(ChatFormatting.DARK_GRAY));
        return line;
    }

    private List<FormattedCharSequence> wrap(boolean scene, int width) {
        List<FormattedCharSequence> wrapped = new ArrayList<>(TextWrap.split(this.font, this.alienLine(), width));
        if (scene) {
            wrapped.add(FormattedCharSequence.EMPTY);
            wrapped.addAll(TextWrap.split(this.font, Component.translatable("civ.universe.encounter." + this.state.encounter() + ".scene")
                    .withStyle(ChatFormatting.GRAY), width));
        }
        if (!this.state.notes().isEmpty()) {
            wrapped.add(FormattedCharSequence.EMPTY);
            for (Component note : this.state.notes()) {
                wrapped.addAll(TextWrap.split(this.font, note, width));
            }
        }
        return wrapped;
    }

    private int lineHeight() {
        return this.font.lineHeight + 2;
    }

    private int fixedHeight() {
        return HEADER + 8 + 3 * BUTTON_HEIGHT + 2 * BUTTON_GAP + 6 + BUTTON_HEIGHT + 10;
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(MAX_WIDTH, this.width - 24);
        int wrap = this.panelWidth - 2 * PAD;
        this.lines = this.wrap(true, wrap);
        // 屏幕矮的时候有回话就不再显示旁白
        if (this.fixedHeight() + this.lines.size() * this.lineHeight() > this.height - 12 && !this.state.notes().isEmpty()) {
            this.lines = this.wrap(false, wrap);
        }
        int bodyRoom = Math.max(1, (this.height - 12 - this.fixedHeight()) / this.lineHeight());
        if (this.lines.size() > bodyRoom) {
            this.lines = this.lines.subList(this.lines.size() - bodyRoom, this.lines.size());
        }
        this.panelHeight = this.fixedHeight() + this.lines.size() * this.lineHeight();
        this.left = (this.width - this.panelWidth) / 2;
        this.top = (this.height - this.panelHeight) / 2;

        int y = this.top + HEADER + this.lines.size() * this.lineHeight() + 8;
        int buttonWidth = this.panelWidth - 2 * PAD;
        boolean answered = this.state.has(DialoguePayloads.ANSWERED);
        this.options.clear();
        this.giftTip = null;
        this.askTip = null;
        for (int i = 0; i < this.state.options().size(); i++) {
            int index = i;
            Value value = Value.values()[Math.floorMod(this.state.options().get(i), Value.values().length)];
            Component label = Component.literal((i + 1) + ". ").append(Component.translatable(
                    "civ.universe.encounter." + this.state.encounter() + ".option." + value.name().toLowerCase(Locale.ROOT)));
            Button button = Button.builder(label, b -> this.send(DialoguePayloads.Action.ANSWER, index))
                    .bounds(this.left + PAD, y, buttonWidth, BUTTON_HEIGHT).build();
            button.active = !answered;
            if (answered) {
                button.setTooltip(Tooltip.create(Component.translatable("gui.universe.dialogue.answered_today")));
            }
            this.options.add(this.addRenderableWidget(button));
            y += BUTTON_HEIGHT + BUTTON_GAP;
        }
        y += 6 - BUTTON_GAP;
        int third = (buttonWidth - 2 * 4) / 3;
        this.gift = this.addRenderableWidget(Button.builder(Component.translatable("gui.universe.dialogue.gift"),
                b -> this.send(DialoguePayloads.Action.GIFT, 0)).bounds(this.left + PAD, y, third, BUTTON_HEIGHT).build());
        this.ask = this.addRenderableWidget(Button.builder(Component.translatable("gui.universe.dialogue.ask"),
                b -> this.send(DialoguePayloads.Action.ASK, 0)).bounds(this.left + PAD + third + 4, y, third, BUTTON_HEIGHT).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.universe.dialogue.leave"), b -> this.onClose())
                .bounds(this.left + PAD + 2 * (third + 4), y, buttonWidth - 2 * (third + 4), BUTTON_HEIGHT).build());
        this.updateButtons();
    }

    /** 送礼看手里有没有东西，所以每刻都按现在的主手算一遍。 */
    private void updateButtons() {
        if (this.gift == null || this.ask == null || this.minecraft.player == null) {
            return;
        }
        ItemStack held = this.minecraft.player.getMainHandItem();
        boolean gifted = this.state.has(DialoguePayloads.GIFTED);
        this.gift.active = !gifted && !held.isEmpty();
        this.giftTip = this.tooltip(this.gift, this.giftTip, gifted ? Component.translatable("gui.universe.dialogue.gifted_today")
                : held.isEmpty() ? Component.translatable("gui.universe.dialogue.empty_hand")
                : Component.translatable("gui.universe.dialogue.gift_hint", held.getHoverName()));
        boolean canAsk = this.state.has(DialoguePayloads.CAN_ASK);
        boolean asked = this.state.has(DialoguePayloads.ASKED);
        this.ask.active = canAsk && !asked;
        this.askTip = this.tooltip(this.ask, this.askTip, Component.translatable(!canAsk ? "gui.universe.dialogue.ask_locked"
                : asked ? "gui.universe.dialogue.asked_today" : "gui.universe.dialogue.ask_hint"));
    }

    /** 提示变了才换（每刻都换会让提示的延迟一直从头算）。 */
    private @Nullable Component tooltip(Button button, @Nullable Component current, Component wanted) {
        if (!wanted.equals(current)) {
            button.setTooltip(Tooltip.create(wanted));
        }
        return wanted;
    }

    private void send(int action, int option) {
        ClientPacketDistributor.sendToServer(new DialoguePayloads.Action(this.state.entity(), action, option));
    }

    @Override
    public void tick() {
        this.ticks++;
        this.updateButtons();
        // NPC 不在了（收走了、换了一天）或者走远了就关上
        Entity npc = this.minecraft.level == null ? null : this.minecraft.level.getEntity(this.state.entity());
        if (npc == null || this.minecraft.player == null || npc.distanceToSqr(this.minecraft.player) > MAX_DISTANCE * MAX_DISTANCE) {
            this.onClose();
        }
    }

    private int shown() {
        return Math.min(this.lines.size(), this.ticks / TICKS_PER_LINE + 1);
    }

    private boolean done() {
        return this.shown() >= this.lines.size();
    }

    // ---- 画

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(0, 0, this.width, this.height, 0x90000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int x = this.left;
        int y = this.top;
        int accent = this.accent();
        int border = 0xFF000000 | (accent >> 1 & 0x7F7F7F);
        graphics.fill(x - 1, y - 1, x + this.panelWidth + 1, y + this.panelHeight + 1, border);
        graphics.fill(x, y, x + this.panelWidth, y + this.panelHeight, 0xF0080A0E);
        // 左边一道种族颜色的边
        graphics.fill(x, y, x + 2, y + this.panelHeight, (accent & 0x00FFFFFF) | 0xA0000000);
        Component heading = Component.translatable("gui.universe.dialogue.heading", Component.translatable("civ.universe.race." + this.race.id()),
                Component.translatable("civ.universe.role." + this.role.id()));
        graphics.text(this.font, heading, x + PAD, y + 10, (accent & 0x00FFFFFF) | 0xB0000000, false);
        Standing.Tier tier = Standing.Tier.values()[Math.floorMod(this.state.tier(), Standing.Tier.values().length)];
        Component standing = Component.translatable("gui.universe.dialogue.tier",
                Component.translatable("civ.universe.standing." + tier.name().toLowerCase(Locale.ROOT)), this.state.standing());
        graphics.text(this.font, standing, x + this.panelWidth - PAD - this.font.width(standing), y + 10, 0xFFB8C0CC, false);
        graphics.text(this.font, this.title, x + PAD, y + 22, accent, false);
        graphics.fill(x + PAD, y + 34, x + this.panelWidth - PAD, y + 35, (accent & 0x00FFFFFF) | 0x60000000);
        int ty = y + HEADER;
        int shown = this.shown();
        for (int i = 0; i < shown; i++) {
            graphics.text(this.font, this.lines.get(i), x + PAD, ty, 0xFFE4E8EE, false);
            ty += this.lineHeight();
        }
        if (!this.done() && this.ticks / 6 % 2 == 0) {
            graphics.fill(x + PAD, ty + 1, x + PAD + 6, ty + this.font.lineHeight - 1, accent);
        }
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (!this.done()) {
            this.ticks = this.lines.size() * TICKS_PER_LINE;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        int index = key == InputConstants.KEY_1 ? 0 : key == InputConstants.KEY_2 ? 1 : key == InputConstants.KEY_3 ? 2 : -1;
        if (index >= 0 && index < this.options.size() && this.options.get(index).active) {
            // 数字键 1–3 直接选
            this.send(DialoguePayloads.Action.ANSWER, index);
            return true;
        }
        if (key == InputConstants.KEY_SPACE && !this.done()) {
            this.ticks = this.lines.size() * TICKS_PER_LINE;
            return true;
        }
        return super.keyPressed(event);
    }
}
