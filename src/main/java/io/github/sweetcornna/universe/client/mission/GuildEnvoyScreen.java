package io.github.sweetcornna.universe.client.mission;

import java.util.List;
import java.util.Locale;

import io.github.sweetcornna.universe.client.galaxy.GalaxyText;
import io.github.sweetcornna.universe.core.civ.Guild;
import io.github.sweetcornna.universe.core.civ.Standing;
import io.github.sweetcornna.universe.core.mission.Mission;
import io.github.sweetcornna.universe.core.mission.MissionKind;
import io.github.sweetcornna.universe.mission.MissionState;
import io.github.sweetcornna.universe.network.GuildEnvoyPayloads;
import io.github.sweetcornna.universe.network.OpenMissionBoardPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 公会使者的界面（ADR-0027 §六）：上面是公会和你的档，中间今天的两个任务（和任务板一样接），下面手上的任务、交付、放弃，
 * 最下面领礼物。点了按钮服务端处理完再发一份刷新。
 */
public class GuildEnvoyScreen extends Screen {
    private static final int WIDTH = 330;
    private static final int HEADER = 42;
    private static final int CARD = 40;
    private static final int ACTIVE = 52;
    private static final int GIFT = 30;
    private static final int FOOTER = 16;
    private static final int MAX_DISTANCE = 8;
    private static final int GRAY = 0xFF9AA8AE;
    private static final int GUILD_COLOR = 0xFF7FD8F0;
    private GuildEnvoyPayloads.Open payload;
    private int left;
    private int top;
    private int panelHeight;

    public GuildEnvoyScreen(GuildEnvoyPayloads.Open payload) {
        super(Component.translatable("gui.universe.guild.title", guildName(payload)));
        this.payload = payload;
    }

    /** 对着哪个使者。 */
    public int entity() {
        return this.payload.entity();
    }

    /** 服务端刷新了内容：重排按钮。 */
    public void update(GuildEnvoyPayloads.Open payload) {
        this.payload = payload;
        this.rebuildWidgets();
    }

    private static Component guildName(GuildEnvoyPayloads.Open payload) {
        return Component.translatable("civ.universe.guild." + guild(payload).id());
    }

    private static Guild guild(GuildEnvoyPayloads.Open payload) {
        return Guild.values()[Math.floorMod(payload.guild(), Guild.values().length)];
    }

    private Standing.Tier tier() {
        return Standing.tier(this.payload.standing());
    }

    private boolean expelled() {
        return this.tier() == Standing.Tier.HOSTILE;
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

    private int giftTop() {
        return this.activeTop() + ACTIVE;
    }

    @Override
    protected void init() {
        this.panelHeight = HEADER + this.cards() * CARD + 4 + ACTIVE + GIFT + FOOTER;
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
            Button.Builder builder = Button.builder(label, b -> this.send(GuildEnvoyPayloads.Action.ACCEPT, slot))
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
            Button handIn = Button.builder(Component.translatable("gui.universe.mission.hand_in"), b -> this.send(GuildEnvoyPayloads.Action.HAND_IN, 0))
                    .bounds(this.left + WIDTH - 136, y, 60, 20).build();
            handIn.active = active.mission().system() == this.payload.system() && this.progress(active) >= active.mission().count();
            this.addRenderableWidget(handIn);
            this.addRenderableWidget(Button.builder(Component.translatable("gui.universe.mission.abandon"),
                    b -> this.send(GuildEnvoyPayloads.Action.ABANDON, 0)).bounds(this.left + WIDTH - 72, y, 60, 20).build());
        }
        Button gift = Button.builder(Component.translatable("gui.universe.guild.claim"), b -> this.send(GuildEnvoyPayloads.Action.GIFT, 0))
                .bounds(this.left + WIDTH - 92, this.giftTop() + 4, 80, 20).build();
        gift.active = this.payload.gift() >= 0;
        this.addRenderableWidget(gift);
    }

    /** 送货按背包里现有的件数，别的按服务端算的进度。 */
    private int progress(MissionState.Active active) {
        return active.mission().kind() == MissionKind.DELIVERY ? Math.min(active.mission().count(), this.payload.have()) : active.progress();
    }

    private void send(int action, int slot) {
        ClientPacketDistributor.sendToServer(new GuildEnvoyPayloads.Action(this.payload.entity(), action, slot));
    }

    @Override
    public void tick() {
        // 使者不在了或者走远了就关上
        Entity npc = this.minecraft.level == null ? null : this.minecraft.level.getEntity(this.payload.entity());
        if (npc == null || this.minecraft.player == null || npc.distanceToSqr(this.minecraft.player) > MAX_DISTANCE * MAX_DISTANCE) {
            this.onClose();
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(0, 0, this.width, this.height, 0xB0000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(this.left - 1, this.top - 1, this.left + WIDTH + 1, this.top + this.panelHeight + 1, 0xFF2E6E7E);
        graphics.fill(this.left, this.top, this.left + WIDTH, this.top + this.panelHeight, 0xF0080C0E);
        // 头：公会、使者的名字；右边你的档和声望
        graphics.text(this.font, this.title, this.left + 12, this.top + 8, GUILD_COLOR, false);
        Component name = Component.translatable("gui.universe.guild.envoy", GalaxyText.pick(this.payload.nameZh(), this.payload.nameEn()));
        graphics.text(this.font, name, this.left + 12, this.top + 21, GRAY, false);
        Standing.Tier tier = this.tier();
        Component rank = Component.translatable("civ.universe.guild_standing." + tier.name().toLowerCase(Locale.ROOT));
        graphics.text(this.font, rank, this.left + WIDTH - 12 - this.font.width(rank), this.top + 8, this.expelled() ? 0xFFFF6A5A : 0xFFE6EEF2, false);
        Component points = tier == Standing.Tier.KINDRED ? Component.literal(String.valueOf(this.payload.standing()))
                : Component.literal(this.payload.standing() + " / " + tier.next());
        graphics.text(this.font, points, this.left + WIDTH - 12 - this.font.width(points), this.top + 21, GRAY, false);
        graphics.fill(this.left + 8, this.top + HEADER - 8, this.left + WIDTH - 8, this.top + HEADER - 7, 0xFF2E6E7E);
        // 今天的任务
        List<Mission> offers = this.payload.offers();
        if (offers.isEmpty()) {
            Component none = Component.translatable(this.expelled() ? "gui.universe.guild.expelled" : "gui.universe.guild.no_offers");
            graphics.text(this.font, none, this.left + 16, this.top + HEADER + 14, this.expelled() ? 0xFFFF8A7A : GRAY, false);
        }
        for (int i = 0; i < offers.size(); i++) {
            Mission mission = offers.get(i);
            int y = this.top + HEADER + i * CARD;
            boolean done = this.payload.states().get(i) == OpenMissionBoardPayload.DONE;
            graphics.fill(this.left + 8, y, this.left + WIDTH - 8, y + CARD - 4, 0xFF111A20);
            graphics.fill(this.left + 8, y, this.left + 11, y + CARD - 4, MissionText.color(mission.kind()));
            Component kind = MissionText.kind(mission.kind());
            graphics.text(this.font, kind, this.left + 16, y + 6, MissionText.color(mission.kind()), false);
            graphics.text(this.font, MissionText.goal(mission), this.left + 20 + this.font.width(kind), y + 6, done ? GRAY : 0xFFE6EEF2, false);
            graphics.text(this.font, MissionText.reward(mission), this.left + 16, y + 20, done ? GRAY : 0xFFF2C94C, false);
        }
        // 手上的任务
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
        // 礼物
        int giftY = this.giftTop();
        Component gift;
        if (this.payload.gift() >= 0) {
            Standing.Tier reached = Standing.Tier.values()[Math.min(this.payload.gift(), Standing.Tier.values().length - 1)];
            gift = Component.translatable("gui.universe.guild.gift_ready",
                    Component.translatable("civ.universe.guild_standing." + reached.name().toLowerCase(Locale.ROOT)));
        } else {
            gift = Component.translatable(this.expelled() ? "gui.universe.guild.gift_expelled" : "gui.universe.guild.gift_next");
        }
        graphics.text(this.font, gift, this.left + 14, giftY + 10, this.payload.gift() >= 0 ? 0xFF8AE08A : GRAY, false);
        Component footer = Component.translatable("gui.universe.mission.refresh");
        graphics.text(this.font, footer, this.left + (WIDTH - this.font.width(footer)) / 2, this.top + this.panelHeight - 12, 0xFF5E6C72, false);
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }
}
