package io.github.sweetcornna.universe.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.galaxy.GalaxyText;
import io.github.sweetcornna.universe.core.galaxy.Hazard;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.survival.LifeSupport;
import io.github.sweetcornna.universe.network.JetpackPayload;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.survival.SuitHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 外骨骼的客户端部分：左上角的读数面板、数值见底时屏幕中间的警告，以及喷气背包的推力。
 *
 * <p>喷气背包：穿着外骨骼胸甲时，在空中再按一次跳跃键启动，按住持续向上推，松开就停，落地后关闭。
 * 玩家的移动由客户端决定，所以推力加在这里；能量在本地按同样的规则预测，和服务端同步过来的读数差太多时校正。
 */
public final class SuitClient {
    /** 净向上加速度（格/刻²）和最大上升速度。 */
    private static final double THRUST = 0.045;
    private static final double MAX_RISE = 0.5;
    /** 按住前进时的水平加速度和上限。 */
    private static final double PUSH = 0.025;
    private static final double MAX_PUSH_SPEED = 0.45;
    private static final int PANEL_WIDTH = 120;

    private static boolean jumpWasDown;
    private static boolean armed;
    private static boolean thrusting;
    private static float jet = LifeSupport.JET_MAX;

    private SuitClient() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterGuiLayersEvent.class,
                event -> event.registerAbove(VanillaGuiLayers.HOTBAR, Universe.id("suit_hud"), SuitClient::renderHud));
        gameBus.addListener(PlayerTickEvent.Pre.class, event -> {
            if (event.getEntity() == Minecraft.getInstance().player) {
                jetpack((LocalPlayer) event.getEntity());
            }
        });
    }

    // ---- 喷气背包

    private static void jetpack(LocalPlayer player) {
        boolean jumpDown = player.input.keyPresses.jump();
        boolean chest = player.getItemBySlot(EquipmentSlot.CHEST).is(UniverseItems.EXOSUIT_CHESTPLATE);
        boolean blocked = !chest || player.onGround() || player.isInWater() || player.isInLava() || player.isPassenger()
                || player.getAbilities().mayfly || player.isFallFlying();
        if (blocked) {
            armed = false;
        } else if (jumpDown && !jumpWasDown) {
            armed = true;
        }
        jumpWasDown = jumpDown;

        float synced = player.getData(UniverseAttachments.SUIT_HUD).jet();
        if (Math.abs(synced - jet) > 15) {
            jet = synced;
        }
        boolean thrust = armed && jumpDown && jet > 0;
        if (thrust) {
            jet = Math.max(0, jet - LifeSupport.JET_BURN);
            push(player);
        } else if (player.onGround() || player.isPassenger()) {
            jet = Math.min(LifeSupport.JET_MAX, jet + LifeSupport.JET_RECHARGE);
        }
        if (thrust != thrusting) {
            thrusting = thrust;
            ClientPacketDistributor.sendToServer(new JetpackPayload(thrust));
        }
    }

    private static void push(LocalPlayer player) {
        Vec3 v = player.getDeltaMovement();
        double gravity = player.getAttributeValue(Attributes.GRAVITY);
        double vy = Math.min(Math.max(v.y, -0.6) + gravity + THRUST, MAX_RISE);
        double vx = v.x;
        double vz = v.z;
        if (player.input.keyPresses.forward()) {
            float yaw = player.getYRot() * Mth.DEG_TO_RAD;
            vx += -Mth.sin(yaw) * PUSH;
            vz += Mth.cos(yaw) * PUSH;
            double horizontal = Math.sqrt(vx * vx + vz * vz);
            if (horizontal > MAX_PUSH_SPEED) {
                vx *= MAX_PUSH_SPEED / horizontal;
                vz *= MAX_PUSH_SPEED / horizontal;
            }
        }
        player.setDeltaMovement(vx, vy, vz);
        player.resetFallDistance();
        // 背后两股小火焰
        float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
        double backX = Mth.sin(yaw) * 0.3;
        double backZ = -Mth.cos(yaw) * 0.3;
        double sideX = Mth.cos(yaw) * 0.18;
        double sideZ = Mth.sin(yaw) * 0.18;
        for (int side = -1; side <= 1; side += 2) {
            double x = player.getX() + backX + sideX * side;
            double z = player.getZ() + backZ + sideZ * side;
            player.level().addParticle(ParticleTypes.SMALL_FLAME, x, player.getY() + 0.7, z, 0, -0.15, 0);
            if (player.tickCount % 2 == 0) {
                player.level().addParticle(ParticleTypes.CLOUD, x, player.getY() + 0.4, z, 0, -0.05, 0);
            }
        }
    }

    // ---- 读数面板

    private record Bar(Component label, float value, float max, int color, Component note) {
    }

    private static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gui.hud.isHidden() || player.isCreative() || player.isSpectator()) {
            return;
        }
        SuitHud hud = player.getData(UniverseAttachments.SUIT_HUD);
        boolean chest = hud.has(SuitHud.CHEST);
        List<Bar> bars = new ArrayList<>();
        if (hud.has(SuitHud.NEEDS_OXYGEN) || hud.oxygen() < LifeSupport.OXYGEN_MAX) {
            Component note = hud.has(SuitHud.NEEDS_OXYGEN) && !hud.has(SuitHud.SEALED)
                    ? Component.translatable("hud.universe.suit.unsealed") : Component.empty();
            bars.add(new Bar(Component.translatable("hud.universe.suit.oxygen"), hud.oxygen(), LifeSupport.OXYGEN_MAX, 0xFF4FC3F7, note));
        }
        for (Hazard hazard : Hazard.values()) {
            if (hud.exposed(hazard) || hud.protection(hazard) < LifeSupport.HAZARD_MAX) {
                Component note = hazard == Hazard.COLD && hud.has(SuitHud.NEAR_HEAT) ? Component.translatable("hud.universe.suit.near_heat")
                        : hazard == Hazard.HEAT && hud.has(SuitHud.NEAR_COLD) ? Component.translatable("hud.universe.suit.near_cold")
                        : Component.empty();
                bars.add(new Bar(Component.translatable("hazard.universe." + hazard.name().toLowerCase(java.util.Locale.ROOT)),
                        hud.protection(hazard), LifeSupport.HAZARD_MAX, hazardColor(hazard), note));
            }
        }
        float shownJet = thrusting || jet < LifeSupport.JET_MAX ? jet : hud.jet();
        if (chest && shownJet < LifeSupport.JET_MAX) {
            bars.add(new Bar(Component.translatable("hud.universe.suit.jetpack"), shownJet, LifeSupport.JET_MAX, 0xFFFFC14D, Component.empty()));
        }
        if (bars.isEmpty()) {
            return;
        }
        Font font = minecraft.font;
        Optional<Planet> planet = ClientGalaxy.currentPlanet();
        int x = 6;
        int y = 6;
        int height = 12 + bars.size() * 15 + (planet.isPresent() ? 10 : 0);
        graphics.fill(x - 3, y - 3, x + PANEL_WIDTH, y + height, 0x90000000);
        Component title = Component.translatable("hud.universe.suit.title");
        if (hud.has(SuitHud.SUPPLIED)) {
            title = title.copy().append(" · ").append(Component.translatable("hud.universe.suit.cabin"));
        } else if (hud.has(SuitHud.SHELTERED)) {
            title = title.copy().append(" · ").append(Component.translatable("hud.universe.suit.sheltered"));
        } else if (hud.has(SuitHud.STORM)) {
            title = title.copy().append(" · ").append(Component.translatable("hud.universe.suit.storm"));
        }
        graphics.text(font, title, x, y, 0xFF9FE6FF, false);
        y += 11;
        if (planet.isPresent()) {
            Planet p = planet.get();
            String name = GalaxyText.planet(p);
            graphics.text(font, Component.translatable("hud.universe.suit.planet", name, Math.round(p.temperatureCelsius()),
                    String.format("%.2f", p.gravity())), x, y, 0xFFB0B0B0, false);
            y += 10;
        }
        int barWidth = PANEL_WIDTH - 8;
        for (Bar bar : bars) {
            float share = Mth.clamp(bar.value() / bar.max(), 0, 1);
            int percent = Math.round(share * 100);
            graphics.text(font, bar.label(), x, y, 0xFFE0E0E0, false);
            String value = percent + "%";
            int textColor = share < 0.2F ? 0xFFFF6A5A : 0xFFE0E0E0;
            graphics.text(font, Component.literal(value), x + barWidth - font.width(value), y, textColor, false);
            if (!bar.note().getString().isEmpty()) {
                graphics.text(font, bar.note(), x + font.width(bar.label()) + 4, y, share < 0.2F ? 0xFFFF6A5A : 0xFF9A9A9A, false);
            }
            y += 10;
            graphics.fill(x, y, x + barWidth, y + 3, 0xFF303030);
            graphics.fill(x, y, x + Math.round(barWidth * share), y + 3, share < 0.2F ? 0xFFE5533D : bar.color());
            y += 5;
        }
        warnings(graphics, font, hud, player);
    }

    private static void warnings(GuiGraphicsExtractor graphics, Font font, SuitHud hud, LocalPlayer player) {
        // 每秒闪两次
        if ((player.tickCount / 5) % 2 == 1) {
            return;
        }
        Component warning = null;
        if (hud.has(SuitHud.NEEDS_OXYGEN) && hud.oxygen() < LifeSupport.OXYGEN_MAX * 0.25F) {
            warning = Component.translatable(hud.oxygen() <= 0 ? "hud.universe.suit.warning.no_oxygen" : "hud.universe.suit.warning.oxygen");
        } else {
            for (Hazard hazard : Hazard.values()) {
                if (hud.exposed(hazard) && hud.protection(hazard) < LifeSupport.HAZARD_MAX * 0.2F) {
                    warning = Component.translatable("hud.universe.suit.warning.hazard",
                            Component.translatable("hazard.universe." + hazard.name().toLowerCase(java.util.Locale.ROOT)));
                    break;
                }
            }
        }
        if (warning != null) {
            int width = font.width(warning);
            int x = graphics.guiWidth() / 2 - width / 2;
            int y = graphics.guiHeight() / 2 - 30;
            graphics.fill(x - 4, y - 3, x + width + 4, y + 11, 0xA0400000);
            graphics.text(font, warning, x, y, 0xFFFF6A5A, false);
        }
    }

    private static int hazardColor(Hazard hazard) {
        return switch (hazard) {
            case COLD -> 0xFF9AD8FF;
            case HEAT -> 0xFFFF8A3D;
            case TOXIC -> 0xFF8BD14F;
            case RADIATION -> 0xFFE8E04A;
        };
    }
}
