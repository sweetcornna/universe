package io.github.sweetcornna.universe.client.mission;

import java.util.List;

import io.github.sweetcornna.universe.core.mission.Mission;
import io.github.sweetcornna.universe.core.mission.MissionKind;
import io.github.sweetcornna.universe.mission.MissionState;
import io.github.sweetcornna.universe.network.MissionActionPayload;
import io.github.sweetcornna.universe.network.OpenMissionBoardPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 任务板的界面（ADR-0017）：今天的三个任务，一行一个，右边是"接受"；下面是手上的任务、进度、交付和放弃。
 * 点了按钮服务端处理完会重新发一次界面内容。
 */
public class MissionBoardScreen extends Screen {
    private static final int WIDTH = 330;
    private static final int HEADER = 30;
    private static final int CARD = 40;
    private static final int ACTIVE = 52;
    private static final int FOOTER = 16;
    private static final int ACCENT = 0xFF6EDCF5;
    private static final int GRAY = 0xFF9AA8AE;
    private final OpenMissionBoardPayload payload;
    private int left;
    private int top;
    private int panelHeight;

    public MissionBoardScreen(OpenMissionBoardPayload payload) {
        super(Component.translatable("block.universe.mission_board"));
        this.payload = payload;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int cards() {
        return Math.max(1, this.payload.offers().size());
    }

    private int activeTop() {
        return this.top + HEADER + this.cards() * CARD + 4;
    }

    @Override
    protected void init() {
        this.panelHeight = HEADER + this.cards() * CARD + 4 + ACTIVE + FOOTER;
        this.left = (this.width - WIDTH) / 2;
        this.top = (this.height - this.panelHeight) / 2;
        boolean busy = this.payload.active().isPresent();
        List<Mission> offers = this.payload.offers();
        for (int i = 0; i < offers.size(); i++) {
            int state = this.payload.states().get(i);
            Component label = switch (state) {
                case OpenMissionBoardPayload.ACTIVE -> Component.translatable("gui.universe.mission.in_progress");
                case OpenMissionBoardPayload.DONE -> Component.translatable("gui.universe.mission.done");
                default -> Component.translatable("gui.universe.mission.accept");
            };
            int slot = i;
            Button.Builder builder = Button.builder(label, b -> this.send(MissionActionPayload.ACCEPT, slot))
                    .bounds(this.left + WIDTH - 72, this.top + HEADER + i * CARD + 9, 60, 20);
            if (state == OpenMissionBoardPayload.AVAILABLE && busy) {
                builder.tooltip(Tooltip.create(Component.translatable("gui.universe.mission.busy_hint")));
            }
            Button button = builder.build();
            button.active = state == OpenMissionBoardPayload.AVAILABLE && !busy;
            this.addRenderableWidget(button);
        }
        if (busy) {
            MissionState.Active active = this.payload.active().get();
            int y = this.activeTop() + 26;
            Button handIn = Button.builder(Component.translatable("gui.universe.mission.hand_in"), b -> this.send(MissionActionPayload.HAND_IN, 0))
                    .bounds(this.left + WIDTH - 136, y, 60, 20).build();
            handIn.active = active.mission().system() == this.payload.system() && this.progress(active) >= active.mission().count();
            this.addRenderableWidget(handIn);
            this.addRenderableWidget(Button.builder(Component.translatable("gui.universe.mission.abandon"), b -> this.send(MissionActionPayload.ABANDON, 0))
                    .bounds(this.left + WIDTH - 72, y, 60, 20).build());
        }
    }

    /** 送货按背包里现有的件数，别的按服务端算的进度。 */
    private int progress(MissionState.Active active) {
        return active.mission().kind() == MissionKind.DELIVERY ? Math.min(active.mission().count(), this.payload.have()) : active.progress();
    }

    private void send(int action, int slot) {
        ClientPacketDistributor.sendToServer(new MissionActionPayload(this.payload.pos(), action, slot));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(0, 0, this.width, this.height, 0xB0000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(this.left - 1, this.top - 1, this.left + WIDTH + 1, this.top + this.panelHeight + 1, 0xFF2E6E7E);
        graphics.fill(this.left, this.top, this.left + WIDTH, this.top + this.panelHeight, 0xF0080C0E);
        graphics.text(this.font, this.title, this.left + 12, this.top + 8, ACCENT, false);
        Component station = MissionText.station(this.payload.system());
        graphics.text(this.font, station, this.left + WIDTH - 12 - this.font.width(station), this.top + 8, GRAY, false);
        List<Mission> offers = this.payload.offers();
        for (int i = 0; i < offers.size(); i++) {
            Mission mission = offers.get(i);
            int y = this.top + HEADER + i * CARD;
            boolean done = this.payload.states().get(i) == OpenMissionBoardPayload.DONE;
            graphics.fill(this.left + 8, y, this.left + WIDTH - 8, y + CARD - 4, 0xFF111A20);
            graphics.fill(this.left + 8, y, this.left + 11, y + CARD - 4, MissionText.color(mission.kind()));
            Component kind = MissionText.kind(mission.kind());
            graphics.text(this.font, kind, this.left + 16, y + 6, MissionText.color(mission.kind()), false);
            graphics.text(this.font, MissionText.goal(mission), this.left + 20 + this.font.width(kind), y + 6, done ? GRAY : 0xFFE6EEF2, false);
            Component reward = MissionText.reward(mission);
            graphics.text(this.font, reward, this.left + 16, y + 20, done ? GRAY : 0xFFF2C94C, false);
            // 归哪个公会（ADR-0027 §六）：做完加这个公会的声望；挤不下就不写
            Component guild = Component.literal("· ").append(MissionText.guild(mission.kind()));
            int guildX = this.left + 20 + this.font.width(reward);
            if (guildX + this.font.width(guild) < this.left + WIDTH - 76) {
                graphics.text(this.font, guild, guildX, y + 20, GRAY, false);
            }
        }
        int y = this.activeTop();
        graphics.fill(this.left + 8, y, this.left + WIDTH - 8, y + ACTIVE - 4, 0xFF0D1418);
        graphics.text(this.font, Component.translatable("gui.universe.mission.active"), this.left + 14, y + 5, GRAY, false);
        if (this.payload.active().isPresent()) {
            MissionState.Active active = this.payload.active().get();
            Mission mission = active.mission();
            graphics.text(this.font, MissionText.goal(mission), this.left + 14, y + 17, 0xFFE6EEF2, false);
            int progress = this.progress(active);
            Component status = progress >= mission.count() && mission.system() != this.payload.system()
                    ? Component.translatable("gui.universe.mission.from", MissionText.station(mission.system()))
                    : Component.translatable("gui.universe.mission.progress", progress, mission.count());
            graphics.text(this.font, status, this.left + 14, y + 32, progress >= mission.count() ? 0xFF8AE08A : 0xFFFFE08A, false);
        } else {
            graphics.text(this.font, Component.translatable("gui.universe.mission.none"), this.left + 14, y + 20, GRAY, false);
        }
        Component footer = Component.translatable("gui.universe.mission.refresh");
        graphics.text(this.font, footer, this.left + (WIDTH - this.font.width(footer)) / 2, this.top + this.panelHeight - 12, 0xFF5E6C72, false);
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }
}
