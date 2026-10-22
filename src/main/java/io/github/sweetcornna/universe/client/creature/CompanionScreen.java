package io.github.sweetcornna.universe.client.creature;

import static io.github.sweetcornna.universe.client.factory.MachineScreen.ACCENT;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.BAD;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.BORDER;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.DIM;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.GOOD;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.INSET;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.PANEL;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.TEXT;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.WARN;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.client.discovery.ClientDiscoveries;
import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.client.galaxy.GalaxyText;
import io.github.sweetcornna.universe.core.discovery.DiscoveryKey;
import io.github.sweetcornna.universe.core.discovery.DiscoveryLog;
import io.github.sweetcornna.universe.core.ecology.Companion;
import io.github.sweetcornna.universe.core.ecology.CreatureSpecies;
import io.github.sweetcornna.universe.entity.AlienCreature;
import io.github.sweetcornna.universe.entity.CompanionMenu;
import io.github.sweetcornna.universe.network.CompanionPayloads;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 伙伴界面（ADR-0026 §九）：左边是这只动物，右边名字（能改）、物种、体型、信任、饱食、心情、下蛋，
 * 下面四个按钮：跟随 / 待命、骑乘、挖掘、放生（点两下才放）。
 */
public class CompanionScreen extends AbstractContainerScreen<CompanionMenu> {
    private static final int WIDTH = 248;
    private static final int HEIGHT = 154;
    private static final int PREVIEW_X = 8;
    private static final int PREVIEW_Y = 24;
    private static final int PREVIEW_WIDTH = 76;
    private static final int PREVIEW_HEIGHT = 94;
    private static final int RIGHT_X = 92;
    private static final int NAME_Y = 24;
    private static final int NAME_WIDTH = 108;
    private static final int LABEL_WIDTH = 42;
    private static final int ROW_Y = 48;
    private static final int ROW = 12;
    private static final int BAR_WIDTH = 60;
    private static final int BUTTON_Y = 126;
    private static final int BUTTON_WIDTH = 55;
    private static final int BUTTON_GAP = 4;
    /** 放生要在 3 秒内再点一下。 */
    private static final int CONFIRM_TICKS = 60;
    private static final int TRUST_COLOR = 0xFF6FC3E8;
    private static final int FOOD_COLOR = 0xFFE8A04A;

    private @Nullable EditBox nameBox;
    private @Nullable Button saveButton;
    private @Nullable Button followButton;
    private @Nullable Button rideButton;
    private @Nullable Button digButton;
    private @Nullable Button releaseButton;
    private int confirmRelease;
    private @Nullable Component digTip;
    private @Nullable Component rideTip;

    public CompanionScreen(CompanionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(RegisterMenuScreensEvent.class, event -> event.register(UniverseMenus.COMPANION.get(), CompanionScreen::new));
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;
        String value = this.nameBox == null ? this.currentName() : this.nameBox.getValue();
        this.nameBox = new EditBox(this.font, x + RIGHT_X, y + NAME_Y, NAME_WIDTH, 16, Component.translatable("gui.universe.companion.name"));
        this.nameBox.setMaxLength(DiscoveryLog.MAX_NAME);
        this.nameBox.setHint(Component.translatable("gui.universe.companion.name_hint"));
        this.nameBox.setValue(value);
        this.nameBox.setResponder(text -> this.updateButtons());
        this.addRenderableWidget(this.nameBox);
        this.saveButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.universe.companion.save"), b -> this.save())
                .bounds(x + RIGHT_X + NAME_WIDTH + 4, y + NAME_Y - 2, WIDTH - RIGHT_X - NAME_WIDTH - 12, 20).build());
        this.followButton = this.addRenderableWidget(this.button(0, Component.translatable("gui.universe.companion.stay"),
                b -> this.press(CompanionMenu.FOLLOW)));
        this.rideButton = this.addRenderableWidget(this.button(1, Component.translatable("gui.universe.companion.ride"),
                b -> this.press(CompanionMenu.RIDE)));
        this.digButton = this.addRenderableWidget(this.button(2, Component.translatable("gui.universe.companion.dig"),
                b -> this.press(CompanionMenu.DIG)));
        this.releaseButton = this.addRenderableWidget(this.button(3, Component.translatable("gui.universe.companion.release"), b -> this.release()));
        this.digTip = null;
        this.rideTip = null;
        this.updateButtons();
    }

    private Button button(int index, Component label, Button.OnPress onPress) {
        return Button.builder(label, onPress)
                .bounds(this.leftPos + 8 + index * (BUTTON_WIDTH + BUTTON_GAP), this.topPos + BUTTON_Y, BUTTON_WIDTH, 20).build();
    }

    private String currentName() {
        AlienCreature creature = this.menu.creature();
        return creature == null || creature.getCustomName() == null ? "" : creature.getCustomName().getString();
    }

    private void press(int button) {
        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, button);
    }

    private void release() {
        if (this.confirmRelease > 0) {
            this.confirmRelease = 0;
            this.press(CompanionMenu.RELEASE);
            return;
        }
        this.confirmRelease = CONFIRM_TICKS;
        this.updateButtons();
    }

    private void save() {
        if (this.nameBox == null || this.saveButton == null || !this.saveButton.active) {
            return;
        }
        ClientPacketDistributor.sendToServer(new CompanionPayloads.Rename(this.menu.containerId, this.nameBox.getValue()));
        this.nameBox.setFocused(false);
        this.setFocused(null);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.confirmRelease > 0) {
            this.confirmRelease--;
        }
        this.updateButtons();
    }

    /** 按钮的字、能不能按、提示，按同步过来的读数现算。 */
    private void updateButtons() {
        if (this.nameBox == null || this.saveButton == null || this.followButton == null || this.rideButton == null || this.digButton == null
                || this.releaseButton == null) {
            return;
        }
        Optional<String> clean = DiscoveryLog.cleanName(this.nameBox.getValue());
        this.saveButton.active = clean.isPresent() && !clean.get().equals(this.currentName());
        boolean staying = this.menu.get(CompanionMenu.STAYING) != 0;
        this.followButton.setMessage(Component.translatable(staying ? "gui.universe.companion.follow" : "gui.universe.companion.stay"));

        int ride = this.menu.get(CompanionMenu.RIDE_STATE);
        AlienCreature creature = this.menu.creature();
        this.rideButton.active = ride == CompanionMenu.RIDE_OK && creature != null && !creature.isVehicle();
        this.rideTip = this.tooltip(this.rideButton, this.rideTip, switch (ride) {
            case CompanionMenu.RIDE_SMALL -> Component.translatable("gui.universe.companion.ride_small");
            case CompanionMenu.RIDE_YOUNG -> Component.translatable("gui.universe.companion.ride_young");
            default -> null;
        });

        int digging = this.menu.get(CompanionMenu.DIGGING);
        int wait = this.menu.get(CompanionMenu.DIG_WAIT);
        this.digButton.setMessage(Component.translatable(digging > 0 ? "gui.universe.companion.digging" : "gui.universe.companion.dig"));
        this.digButton.active = digging == 0 && wait == 0;
        this.digTip = this.tooltip(this.digButton, this.digTip, digging > 0 ? null : wait > 0
                ? Component.translatable("gui.universe.companion.dig_wait", Math.max(1, (wait + 59) / 60))
                : Component.translatable("gui.universe.companion.dig_hint"));

        this.releaseButton.setMessage(this.confirmRelease > 0
                ? Component.translatable("gui.universe.companion.release_confirm").withStyle(ChatFormatting.RED)
                : Component.translatable("gui.universe.companion.release"));
    }

    /** 提示变了才换（每刻都换会让提示的延迟一直从头算）。 */
    private @Nullable Component tooltip(Button button, @Nullable Component current, @Nullable Component wanted) {
        if (!Objects.equals(current, wanted)) {
            button.setTooltip(wanted == null ? null : Tooltip.create(wanted));
        }
        return wanted;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            this.minecraft.player.closeContainer();
            return true;
        }
        if (this.nameBox != null && this.nameBox.isFocused()) {
            if (event.isConfirmation()) {
                this.save();
                return true;
            }
            return this.nameBox.keyPressed(event) || this.nameBox.canConsumeInput() || super.keyPressed(event);
        }
        return super.keyPressed(event);
    }

    // ---- 画

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = this.leftPos;
        int y = this.topPos;
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, BORDER);
        graphics.fill(x + 1, y + 1, x + this.imageWidth - 1, y + this.imageHeight - 1, PANEL);
        graphics.fill(x + 6, y + 18, x + this.imageWidth - 6, y + 19, 0x602E7A92);
        int px = x + PREVIEW_X;
        int py = y + PREVIEW_Y;
        graphics.fill(px - 1, py - 1, px + PREVIEW_WIDTH + 1, py + PREVIEW_HEIGHT + 1, 0xFF24404D);
        graphics.fill(px, py, px + PREVIEW_WIDTH, py + PREVIEW_HEIGHT, INSET);
        AlienCreature creature = this.menu.creature();
        if (creature != null) {
            // 预览按没缩放的身形摆：高的按高度、宽的按宽度放进框里
            float scale = Math.max(0.05F, creature.getScale());
            float height = Math.max(0.3F, creature.getBbHeight() / scale);
            float width = Math.max(0.3F, creature.getBbWidth() / scale);
            int size = (int) Math.max(6, Math.min((PREVIEW_HEIGHT - 22) / height, (PREVIEW_WIDTH - 14) / width));
            InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, px, py, px + PREVIEW_WIDTH, py + PREVIEW_HEIGHT, size, 0.0625F,
                    mouseX, mouseY, creature);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, Component.translatable("gui.universe.companion.title"), 8, 7, ACCENT, false);
        AlienCreature creature = this.menu.creature();
        if (creature == null) {
            return;
        }
        int valueX = RIGHT_X + LABEL_WIDTH;
        int valueWidth = WIDTH - 8 - valueX;
        int y = ROW_Y;
        CreatureSpecies species = creature.species();
        this.label(graphics, "species", y);
        graphics.text(this.font, this.font.plainSubstrByWidth(speciesName(creature, species), valueWidth), valueX, y, TEXT, false);

        y += ROW;
        this.label(graphics, "size", y);
        String size = "×" + String.format(Locale.ROOT, "%.2f", creature.traits().size());
        graphics.text(this.font, size, valueX, y, TEXT, false);
        int growth = this.menu.get(CompanionMenu.GROWTH);
        if (growth < 100) {
            graphics.text(this.font, Component.translatable("gui.universe.companion.young", growth + "%"), valueX + this.font.width(size) + 6, y,
                    WARN, false);
        }

        y += ROW;
        int trust = this.menu.get(CompanionMenu.TRUST);
        this.label(graphics, "trust", y);
        MachineScreen.bar(graphics, valueX, y + 1, BAR_WIDTH, 6, trust / (float) Companion.MAX_TRUST, TRUST_COLOR);
        graphics.text(this.font, String.valueOf(trust), valueX + BAR_WIDTH + 5, y, TEXT, false);

        y += ROW;
        int satiety = this.menu.get(CompanionMenu.SATIETY);
        this.label(graphics, "satiety", y);
        MachineScreen.bar(graphics, valueX, y + 1, BAR_WIDTH, 6, satiety / 100F, satiety < 20 ? BAD : FOOD_COLOR);
        graphics.text(this.font, satiety + "%", valueX + BAR_WIDTH + 5, y, TEXT, false);

        y += ROW;
        Companion.Mood mood = this.menu.mood();
        this.label(graphics, "mood", y);
        int moodColor = switch (mood) {
            case HAPPY -> GOOD;
            case CALM -> TEXT;
            case SAD -> BAD;
        };
        graphics.text(this.font, Component.translatable("gui.universe.companion.mood." + mood.name().toLowerCase(Locale.ROOT)), valueX, y,
                moodColor, false);

        y += ROW;
        this.label(graphics, "eggs", y);
        int layState = this.menu.get(CompanionMenu.LAY_STATE);
        Component eggs = switch (layState) {
            case CompanionMenu.LAY_HUNGRY -> Component.translatable("gui.universe.companion.eggs_hungry");
            case CompanionMenu.LAY_TRUST -> Component.translatable("gui.universe.companion.eggs_trust");
            case CompanionMenu.LAY_YOUNG -> Component.translatable("gui.universe.companion.eggs_young");
            default -> Component.translatable("gui.universe.companion.eggs_in",
                    String.format(Locale.ROOT, "%.1f", this.menu.get(CompanionMenu.LAY_WAIT) * 20 / (double) Companion.DAY));
        };
        graphics.text(this.font, eggs, valueX, y, layState == CompanionMenu.LAY_OK ? TEXT : DIM, false);
    }

    private void label(GuiGraphicsExtractor graphics, String key, int y) {
        graphics.text(this.font, Component.translatable("gui.universe.companion." + key), RIGHT_X, y, DIM, false);
    }

    /** 物种名：在本模组的星球上出生的按发现记录（玩家可能改过名），别的用生成的名字。 */
    private static String speciesName(AlienCreature creature, CreatureSpecies species) {
        return creature.homeBody().map(body -> ClientDiscoveries.name(DiscoveryKey.creature(body, species.index()), species.name()))
                .orElseGet(() -> GalaxyText.name(species.name()));
    }
}
