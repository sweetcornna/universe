package io.github.sweetcornna.universe.client.fishing;

import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.core.fishing.FishSpecies;
import io.github.sweetcornna.universe.core.fishing.FishTable;
import io.github.sweetcornna.universe.core.fishing.Reeling;
import io.github.sweetcornna.universe.fishing.FishCaughtPayload;
import io.github.sweetcornna.universe.fishing.FishText;
import io.github.sweetcornna.universe.fishing.FishingBobber;
import io.github.sweetcornna.universe.fishing.Fishing;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 钓鱼的 HUD（ADR-0034 §一）：
 *
 * <ul>
 *   <li>咬钩时准星上方一个跳动的"！"和"右键钩住"；鱼影游过来时一行"水下有动静"；搁在地上时提示收回。</li>
 *   <li>收线时快捷栏上方一条张力条（绿区标出来，马上要冲时边框闪黄，猛冲时闪红）、一条进度条，数值在服务端同步的
 *       两次之间插值。</li>
 *   <li>钓上来时屏幕上方一张卡片：图标、名字（按档次上色）、多重、水域和档次，新纪录、第一次钓到另外标。</li>
 * </ul>
 */
public final class FishingHud {
    private static final int BAR_WIDTH = 160;
    private static final int CARD_TICKS = 100;
    private static final int GREEN = 0xFF4FC86A;
    private static final int AMBER = 0xFFFFB43C;
    private static final int RED = 0xFFE0402A;

    private static @Nullable FishingBobber bobber;
    private static @Nullable FishCaughtPayload card;
    private static int cardAge;

    private FishingHud() {
    }

    /** 每刻找一次自己的浮标。 */
    static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        bobber = null;
        if (player == null || minecraft.level == null) {
            card = null;
            return;
        }
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof FishingBobber candidate && candidate.getOwner() == player && !candidate.isRemoved()) {
                bobber = candidate;
                break;
            }
        }
        if (card != null && ++cardAge > CARD_TICKS) {
            card = null;
        }
    }

    /** 自己的浮标（开发脚本自动收线用）。 */
    static @Nullable FishingBobber bobber() {
        return bobber;
    }

    static void showCatch(FishCaughtPayload payload) {
        card = payload;
        cardAge = 0;
    }

    static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gui.hud.isHidden()) {
            return;
        }
        float partial = delta.getGameTimeDeltaPartialTick(false);
        FishingBobber own = bobber;
        if (own != null && Fishing.fishing(minecraft.player.getMainHandItem())) {
            switch (own.phase()) {
                case BITE -> bite(graphics, minecraft.font, own, partial);
                case HOOKED -> reeling(graphics, minecraft.font, own, partial);
                case APPROACH -> hint(graphics, minecraft.font, Component.translatable("hud.universe.fishing.approach"), 0xFFB8D8E0);
                case GROUNDED -> hint(graphics, minecraft.font, Component.translatable("hud.universe.fishing.grounded"), MachineScreen.DIM);
                case FLOATING -> hint(graphics, minecraft.font, Component.translatable("hud.universe.fishing.waiting"), MachineScreen.DIM);
                case FLYING -> {
                }
            }
        }
        if (card != null) {
            card(graphics, minecraft.font, card, cardAge + partial);
        }
    }

    /** 准星下面一行小字。 */
    private static void hint(GuiGraphicsExtractor graphics, Font font, Component text, int color) {
        int x = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() / 2 + 14;
        graphics.centeredText(font, text, x, y, color);
    }

    private static void bite(GuiGraphicsExtractor graphics, Font font, FishingBobber own, float partial) {
        int cx = graphics.guiWidth() / 2;
        int cy = graphics.guiHeight() / 2;
        float bounce = (float) Math.abs(Math.sin((own.phaseAge() + partial) * 0.8)) * 4;
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(cx, cy - 22 - bounce);
        pose.scale(2.5F);
        graphics.centeredText(font, Component.literal("!"), 0, -4, AMBER);
        pose.popMatrix();
        graphics.centeredText(font, Component.translatable("hud.universe.fishing.bite", Component.keybind("key.use")), cx, cy + 14, 0xFFFFFFFF);
    }

    private static void reeling(GuiGraphicsExtractor graphics, Font font, FishingBobber own, float partial) {
        float tension = own.displayTension(partial);
        float progress = own.displayProgress(partial);
        int x = (graphics.guiWidth() - BAR_WIDTH) / 2;
        int y = graphics.guiHeight() - 72;
        long time = own.tickCount;
        boolean flash = time / 3 % 2 == 0;
        int frame = own.dashing() ? (flash ? RED : 0xFF801810) : own.warning() ? (flash ? AMBER : 0xFF805A10) : 0xFF2E7A92;
        graphics.fill(x - 4, y - 14, x + BAR_WIDTH + 4, y + 22, 0xC0050A0E);
        graphics.outline(x - 4, y - 14, BAR_WIDTH + 8, 36, frame);
        // 张力条：绿区标出来
        graphics.text(font, Component.translatable("hud.universe.fishing.tension"), x, y - 11, MachineScreen.DIM, false);
        Component state = own.dashing() ? Component.translatable("hud.universe.fishing.dash")
                : own.warning() ? Component.translatable("hud.universe.fishing.warning")
                : own.slack() ? Component.translatable("hud.universe.fishing.slack") : Component.empty();
        int stateColor = own.dashing() ? RED : own.warning() ? AMBER : 0xFFB8D8E0;
        graphics.text(font, state, x + BAR_WIDTH - font.width(state), y - 11, stateColor, false);
        graphics.fill(x, y, x + BAR_WIDTH, y + 6, MachineScreen.INSET);
        int greenFrom = x + Math.round((float) Reeling.GREEN_LOW * BAR_WIDTH);
        int greenTo = x + Math.round((float) Reeling.GREEN_HIGH * BAR_WIDTH);
        graphics.fill(greenFrom, y, greenTo, y + 6, 0x604FC86A);
        graphics.fill(greenTo, y, x + BAR_WIDTH, y + 6, 0x40E0402A);
        int marker = x + Math.round(Math.clamp(tension, 0, 1) * BAR_WIDTH);
        int markerColor = tension > Reeling.GREEN_HIGH ? RED : tension < Reeling.GREEN_LOW ? 0xFFB8D8E0 : GREEN;
        graphics.fill(marker - 1, y - 2, marker + 1, y + 8, markerColor);
        // 进度条
        graphics.fill(x, y + 12, x + BAR_WIDTH, y + 17, MachineScreen.INSET);
        graphics.fill(x, y + 12, x + Math.round(Math.clamp(progress, 0, 1) * BAR_WIDTH), y + 17, 0xFF6FD8C8);
    }

    private static void card(GuiGraphicsExtractor graphics, Font font, FishCaughtPayload payload, float age) {
        FishSpecies species = FishTable.byIndex(payload.species()).orElse(null);
        Item item = species != null ? UniverseItems.FISH.get(species).get() : itemOf(payload.item());
        Component name = species != null ? FishText.name(species).withColor(species.rarity().color() & 0xFFFFFF) : new ItemStack(item).getHoverName();
        int width = Math.max(150, font.width(name) + 50);
        int x = (graphics.guiWidth() - width) / 2;
        // 前 6 刻从上面滑下来
        int y = 24 - Math.round(Math.max(0, 6 - age) * 4);
        int height = species != null ? 40 : 26;
        int border = species != null ? species.rarity().color() : 0xFF2E7A92;
        graphics.fill(x, y, x + width, y + height, 0xE0050A0E);
        graphics.outline(x, y, width, height, border);
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x + 5, y + (height - 32) / 2F);
        pose.scale(2);
        graphics.item(new ItemStack(item), 0, 0);
        pose.popMatrix();
        int tx = x + 42;
        graphics.text(font, Component.translatable("hud.universe.fishing.caught"), tx, y + 4, MachineScreen.DIM, false);
        graphics.text(font, name, tx, y + 14, 0xFFFFFFFF, false);
        if (species != null) {
            Component line = FishText.weight(payload.weight()).append("  ")
                    .append(Component.translatable("fishing.universe.habitat." + species.habitat().key())).append(" · ")
                    .append(Component.translatable("fishing.universe.rarity." + species.rarity().key()));
            graphics.text(font, line, tx, y + 25, 0xFFB8D8E0, false);
            Component badge = payload.first() ? Component.translatable("hud.universe.fishing.first")
                    : payload.record() ? Component.translatable("hud.universe.fishing.record") : null;
            if (badge != null) {
                graphics.text(font, badge, x + width - font.width(badge) - 5, y + 4, AMBER, false);
            }
        }
    }

    private static Item itemOf(String id) {
        Identifier key = Identifier.tryParse(id);
        Item item = key == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(key);
        return item == Items.AIR ? Items.COD : item;
    }
}
