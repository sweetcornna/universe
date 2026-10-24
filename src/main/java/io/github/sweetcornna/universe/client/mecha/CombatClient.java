package io.github.sweetcornna.universe.client.mecha;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.UniverseKeys;
import io.github.sweetcornna.universe.client.darkfog.TurretProjectileRenderer;
import io.github.sweetcornna.universe.core.mecha.combat.ShieldBurst;
import io.github.sweetcornna.universe.core.mecha.fleet.FleetUnitKind;
import io.github.sweetcornna.universe.mecha.Mecha;
import io.github.sweetcornna.universe.mecha.combat.CombatPayloads;
import io.github.sweetcornna.universe.mecha.combat.MechaCombat;
import io.github.sweetcornna.universe.mecha.combat.MechaMissile;
import io.github.sweetcornna.universe.mecha.fleet.FleetPayloads;
import io.github.sweetcornna.universe.mecha.fleet.FleetUnit;
import io.github.sweetcornna.universe.mecha.fleet.MechaFleet;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.survival.SuitHud;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * 机甲战斗和舰队的客户端（ADR-0033 §一、§二）：舰队键、护盾爆发键，准星下面的蓄力条和冷却，快捷栏右边出动着的舰队，
 * 舰队单位和机甲弹药的渲染器。
 */
public final class CombatClient {
    /** 出动或召回舰队：在星球上管地面编队，在太空里管太空舰队（默认 Z）。 */
    public static final KeyMapping FLEET = new KeyMapping("key.universe.fleet", InputConstants.Type.KEYBOARD, InputConstants.KEY_Z,
            UniverseKeys.CATEGORY);
    /** 按住蓄力、松开放出护盾爆发（默认左 Alt）。 */
    public static final KeyMapping SHIELD_BURST = new KeyMapping("key.universe.shield_burst", InputConstants.Type.KEYBOARD,
            InputConstants.KEY_LALT, UniverseKeys.CATEGORY);
    private static final int BAR_WIDTH = 40;
    /** 没放成（护盾太少、蓄得太短）的提示显示多久。 */
    private static final int FAIL_TICKS = 30;

    private static boolean charging;
    private static long chargeStart;
    private static long readyAt;
    private static long failedAt = Long.MAX_VALUE;
    private static int groundOut;
    private static int spaceOut;

    private CombatClient() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            for (FleetUnitKind kind : FleetUnitKind.values()) {
                event.registerEntityRenderer(MechaFleet.type(kind), FleetUnitRenderer.provider(kind));
            }
            event.registerEntityRenderer(UniverseEntities.PLASMA_SHOT.get(), PlasmaShotRenderer::new);
            event.<MechaMissile>registerEntityRenderer(UniverseEntities.MECHA_MISSILE.get(), TurretProjectileRenderer::missile);
        });
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(CombatPayloads.BurstState.TYPE,
                (payload, context) -> context.enqueueWork(() -> receive(payload))));
        modBus.addListener(RegisterGuiLayersEvent.class, event -> {
            event.registerBelow(VanillaGuiLayers.CROSSHAIR, Universe.id("shield_burst"), CombatClient::renderBurst);
            event.registerAbove(VanillaGuiLayers.HOTBAR, Universe.id("fleet_status"), CombatClient::renderFleet);
        });
        gameBus.addListener(ClientTickEvent.Post.class, event -> tick());
        gameBus.addListener(ItemTooltipEvent.class, CombatClient::onTooltip);
    }

    private static void receive(CombatPayloads.BurstState payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        long now = minecraft.level.getGameTime();
        if (payload.fired()) {
            readyAt = now + payload.cooldown();
        } else {
            failedAt = now;
        }
    }

    private static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            // 退出存档时清掉：换个存档游戏时间对不上
            charging = false;
            readyAt = 0;
            failedAt = Long.MAX_VALUE;
            return;
        }
        long now = minecraft.level.getGameTime();
        while (FLEET.consumeClick()) {
            if (minecraft.gui.screen() == null && player.isAlive()) {
                ClientPacketDistributor.sendToServer(FleetPayloads.Toggle.INSTANCE);
            }
        }
        boolean down = SHIELD_BURST.isDown() && minecraft.gui.screen() == null && player.isAlive() && !player.isSpectator();
        if (down && !charging && now >= readyAt && Mecha.chest(player)) {
            charging = true;
            chargeStart = now;
            ClientPacketDistributor.sendToServer(new CombatPayloads.Burst(true));
        } else if (!down && charging) {
            charging = false;
            ClientPacketDistributor.sendToServer(new CombatPayloads.Burst(false));
        }
        // 出动着的舰队：数一数附近自己的单位
        if (now % 10 == 0) {
            int ground = 0;
            int space = 0;
            for (Entity entity : minecraft.level.entitiesForRendering()) {
                if (entity instanceof FleetUnit unit && unit.isAlive() && unit.ownerEntityId() == player.getId()) {
                    if (unit.kind().space()) {
                        space++;
                    } else {
                        ground++;
                    }
                }
            }
            groundOut = ground;
            spaceOut = space;
        }
    }

    /** 准星下面：蓄力时一条越来越满的蓝条，冷却时一条越来越短的灰条。 */
    private static void renderBurst(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.gui.hud.isHidden()) {
            return;
        }
        long now = minecraft.level.getGameTime();
        int x = graphics.guiWidth() / 2 - BAR_WIDTH / 2;
        int y = graphics.guiHeight() / 2 + 12;
        if (charging) {
            float charge = (float) ShieldBurst.charge((int) Math.min(ShieldBurst.MAX_CHARGE, now - chargeStart));
            SuitHud hud = player.getData(UniverseAttachments.SUIT_HUD);
            boolean enough = ShieldBurst.spent(hud.shield(), charge) >= ShieldBurst.MIN_SPENT;
            graphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + 3, 0x90000000);
            graphics.fill(x, y, x + Math.round(BAR_WIDTH * charge), y + 2, enough ? 0xFF7FD8FF : 0xFFE5583F);
            if (charge >= 1) {
                graphics.fill(x, y, x + BAR_WIDTH, y + 2, 0xFFDFF6FF);
            }
        } else if (now < readyAt) {
            float left = Mth.clamp((readyAt - now) / (float) ShieldBurst.COOLDOWN, 0, 1);
            graphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + 2, 0x60000000);
            graphics.fill(x, y, x + Math.round(BAR_WIDTH * left), y + 1, 0xFF8BA3AF);
        }
        if (now >= failedAt && now - failedAt < FAIL_TICKS) {
            Component text = Component.translatable("hud.universe.burst.weak");
            graphics.text(minecraft.font, text, graphics.guiWidth() / 2 - minecraft.font.width(text) / 2, y + 5, 0xFFE5583F, true);
        }
    }

    /** 快捷栏右边：出动着几架（地面、太空分开）。 */
    private static void renderFleet(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gui.hud.isHidden() || groundOut + spaceOut == 0) {
            return;
        }
        int x = graphics.guiWidth() / 2 + 91 + 6;
        int y = graphics.guiHeight() - 19;
        if (groundOut > 0) {
            graphics.fakeItem(new ItemStack(UniverseItems.PROTOTYPE.get()), x, y);
            graphics.text(minecraft.font, "×" + groundOut, x + 17, y + 5, 0xFF7FE0F7, true);
            x += 17 + minecraft.font.width("×" + groundOut) + 4;
        }
        if (spaceOut > 0) {
            graphics.fakeItem(new ItemStack(UniverseItems.CORVETTE.get()), x, y);
            graphics.text(minecraft.font, "×" + spaceOut, x + 17, y + 5, 0xFF7FE0F7, true);
        }
    }

    /** 能扔的干扰胶囊、水瓶加一行说明。 */
    private static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.is(UniverseItems.JAMMING_CAPSULE.get())) {
            event.getToolTip().add(Component.translatable("tooltip.universe.throw.jamming", (int) MechaCombat.JAM_RADIUS, MechaCombat.JAM_TICKS / 20)
                    .withStyle(ChatFormatting.GRAY));
        } else if (MechaCombat.water(stack)) {
            event.getToolTip().add(Component.translatable("tooltip.universe.throw.water", (int) MechaCombat.WATER_RADIUS)
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
