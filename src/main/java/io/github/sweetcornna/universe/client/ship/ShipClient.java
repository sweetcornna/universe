package io.github.sweetcornna.universe.client.ship;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.ClientGalaxy;
import io.github.sweetcornna.universe.client.ScreenProjection;
import io.github.sweetcornna.universe.client.UniverseKeys;
import io.github.sweetcornna.universe.client.galaxy.GalaxyText;
import io.github.sweetcornna.universe.core.ship.ShipStats;
import io.github.sweetcornna.universe.core.space.SpaceLayout;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.entity.ship.ShipStructure;
import io.github.sweetcornna.universe.entity.ship.ShipWeapons;
import io.github.sweetcornna.universe.network.ShipControlPayload;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 驾驶飞船时的客户端部分：仪表盘、跟船大小走的第三人称镜头、引擎声；武器（ADR-0017）的开火键、模式键、
 * 准星和锁定框，挨打时屏幕边缘闪一下。
 *
 * <p>坐上飞船时自动切到第三人称（玩法设计 §5.2 的默认视角），下船后恢复原来的视角。驾驶有武器的船时攻击键不再打方块和实体，
 * 改成开火：按下、松开各发一个包，开火方向由服务端按视线算。
 */
public final class ShipClient {
    private static final int PANEL_WIDTH = 170;
    private static @Nullable CameraType cameraBeforeBoarding;
    private static @Nullable ShipEngineSound engineSound;
    private static int layoutSystem = -1;
    private static List<SpaceLayout.Body> layout = List.of();
    /** 开发脚本：当作一直按着开火键（无头验收用）。 */
    public static boolean forceFire;
    private static boolean sentFiring;
    private static float lastHull = -1;
    private static float lastShield = -1;
    private static int hurtFlash;
    private static int shieldFlash;
    private static final int BOLT_COLOR = 0xFFFFD45A;
    private static final int BEAM_COLOR = 0xFFFF7A4D;
    private static final int LOCK_COLOR = 0xFFFF5A4A;

    private ShipClient() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                event -> event.registerEntityRenderer(UniverseEntities.SHIP.get(), ShipRenderer::new));
        modBus.addListener(RegisterGuiLayersEvent.class,
                event -> {
                    event.registerAbove(VanillaGuiLayers.HOTBAR, Universe.id("ship_hud"), ShipClient::renderHud);
                    event.registerBelow(VanillaGuiLayers.CROSSHAIR, Universe.id("ship_reticle"), ShipClient::renderReticle);
                });
        gameBus.addListener(CalculateDetachedCameraDistanceEvent.class, ShipClient::cameraDistance);
        gameBus.addListener(ClientTickEvent.Post.class, event -> tick());
        gameBus.addListener(InputEvent.InteractionKeyMappingTriggered.class, ShipClient::interceptAttack);
    }

    /** 驾驶有武器的船：攻击键不打方块、不打实体（包括自己的船），交给开火。 */
    private static void interceptAttack(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isAttack() && armedPilot(currentShip())) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    private static boolean armedPilot(@Nullable ShipEntity ship) {
        return ship != null && ship.structure() != null && ship.pilot() == Minecraft.getInstance().player && ship.structure().stats().armed();
    }

    /** 开发脚本：切换武器模式。 */
    public static void toggleWeaponMode() {
        ClientPacketDistributor.sendToServer(new ShipControlPayload(ShipControlPayload.MODE));
    }

    private static @Nullable ShipEntity currentShip() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.getVehicle() instanceof ShipEntity ship ? ship : null;
    }

    private static void cameraDistance(CalculateDetachedCameraDistanceEvent event) {
        ShipEntity ship = currentShip();
        if (ship != null && ship.structure() != null) {
            event.setDistance(Math.max(event.getDistance(), wantedDistance(ship)));
        }
    }

    /** 镜头离船多远：罩得住整艘船。 */
    private static float wantedDistance(ShipEntity ship) {
        return (float) (ship.structure().shape().radius() * 1.6 + ship.structure().shape().height() * 0.5 + 3);
    }

    /**
     * 坐在船上时第三人称镜头往上抬多少格（{@code CameraMixin} 调用）：船落在准星下面，挡不住前方的目标。
     * 正面看自己（镜像的第三人称）时不抬。
     */
    public static float cameraLift(Camera camera) {
        Minecraft minecraft = Minecraft.getInstance();
        ShipEntity ship = currentShip();
        if (ship == null || ship.structure() == null || !camera.isDetached() || minecraft.options.getCameraType().isMirrored()) {
            return 0;
        }
        return (float) (0.2 * Math.max(4, wantedDistance(ship)) + 0.5 * ship.structure().shape().height());
    }

    private static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        ShipEntity ship = currentShip();
        if (ship != null) {
            if (cameraBeforeBoarding == null) {
                cameraBeforeBoarding = minecraft.options.getCameraType();
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
            if (engineSound == null || engineSound.isStopped()) {
                engineSound = new ShipEngineSound(ship);
                minecraft.getSoundManager().play(engineSound);
            }
        } else {
            if (cameraBeforeBoarding != null) {
                minecraft.options.setCameraType(cameraBeforeBoarding);
                cameraBeforeBoarding = null;
            }
            engineSound = null;
        }
        boolean armed = armedPilot(ship);
        if (ship != null && ship.pilot() == minecraft.player) {
            while (UniverseKeys.TOOL_MODE.consumeClick()) {
                if (minecraft.gui.screen() == null && armed) {
                    toggleWeaponMode();
                }
            }
        }
        boolean firing = armed && minecraft.gui.screen() == null && (minecraft.options.keyAttack.isDown() || forceFire);
        if (firing != sentFiring) {
            ClientPacketDistributor.sendToServer(new ShipControlPayload(firing ? ShipControlPayload.FIRE_ON : ShipControlPayload.FIRE_OFF));
            sentFiring = firing;
        }
        // 船体掉了闪红，只打掉护盾闪蓝
        if (ship != null) {
            float hull = ship.hull();
            float shield = ship.shield();
            if (lastHull >= 0 && hull < lastHull - 0.01F) {
                hurtFlash = 10;
            } else if (lastShield >= 0 && shield < lastShield - 0.01F) {
                shieldFlash = 8;
            }
            lastHull = hull;
            lastShield = shield;
        } else {
            lastHull = -1;
            lastShield = -1;
        }
        if (hurtFlash > 0) {
            hurtFlash--;
        }
        if (shieldFlash > 0) {
            shieldFlash--;
        }
    }

    // ---- 准星、锁定框、挨打闪屏

    private static void renderReticle(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        ShipEntity ship = currentShip();
        if (ship == null || ship.structure() == null || minecraft.gui.hud.isHidden() || minecraft.player == null) {
            return;
        }
        if (hurtFlash > 0) {
            edges(graphics, 0xE5533D, hurtFlash / 10F);
        } else if (shieldFlash > 0) {
            edges(graphics, 0x6FD8FF, shieldFlash / 8F * 0.7F);
        }
        if (!armedPilot(ship)) {
            return;
        }
        ScreenProjection projection = ScreenProjection.of(minecraft, graphics.guiWidth(), graphics.guiHeight());
        // 炮口实际朝的方向（限制在船头的锥形里），投到远处
        Vec3 aim = ShipWeapons.aim(ship, minecraft.player);
        int[] point = projection.project(projection.eye().add(aim.scale(256)));
        boolean beam = ship.beamMode();
        int color = ship.weaponOverheated() ? 0xFFE5533D : beam ? BEAM_COLOR : BOLT_COLOR;
        if (projection.onScreen(point, 0)) {
            reticle(graphics, point[0], point[1], color, beam);
        }
        Entity target = ShipWeapons.assistTarget(ship, minecraft.player);
        if (target != null) {
            int[] t = projection.project(target.getBoundingBox().getCenter());
            if (projection.onScreen(t, 0)) {
                lock(graphics, t[0], t[1], minecraft.font, (int) Math.round(target.distanceTo(ship)));
            }
        }
    }

    /** 炮口准星：中间一个点，四周四段短线；采矿模式是一个小方框。 */
    private static void reticle(GuiGraphicsExtractor graphics, int x, int y, int color, boolean beam) {
        if (beam) {
            graphics.fill(x - 5, y - 5, x + 6, y - 4, color);
            graphics.fill(x - 5, y + 5, x + 6, y + 6, color);
            graphics.fill(x - 5, y - 4, x - 4, y + 5, color);
            graphics.fill(x + 5, y - 4, x + 6, y + 5, color);
        } else {
            graphics.fill(x - 8, y, x - 4, y + 1, color);
            graphics.fill(x + 5, y, x + 9, y + 1, color);
            graphics.fill(x, y - 8, x + 1, y - 4, color);
            graphics.fill(x, y + 5, x + 1, y + 9, color);
        }
        graphics.fill(x, y, x + 1, y + 1, color);
    }

    /** 锁定框：四个红色的折角，下面写距离。 */
    private static void lock(GuiGraphicsExtractor graphics, int x, int y, Font font, int distance) {
        int r = 9;
        int len = 4;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                int cx = x + sx * r;
                int cy = y + sy * r;
                graphics.fill(Math.min(cx, cx - sx * len), cy, Math.min(cx, cx - sx * len) + len + 1, cy + 1, LOCK_COLOR);
                graphics.fill(cx, Math.min(cy, cy - sy * len), cx + 1, Math.min(cy, cy - sy * len) + len + 1, LOCK_COLOR);
            }
        }
        String text = distance + "m";
        graphics.text(font, text, x - font.width(text) / 2, y + r + 3, LOCK_COLOR, true);
    }

    /** 屏幕四边一圈渐隐的颜色。 */
    private static void edges(GuiGraphicsExtractor graphics, int rgb, float strength) {
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        int band = 14;
        for (int i = 0; i < band; i++) {
            int alpha = (int) (110 * strength * (1 - i / (float) band));
            if (alpha <= 0) {
                continue;
            }
            int color = alpha << 24 | rgb;
            graphics.fill(i, i, w - i, i + 1, color);
            graphics.fill(i, h - i - 1, w - i, h - i, color);
            graphics.fill(i, i + 1, i + 1, h - i - 1, color);
            graphics.fill(w - i - 1, i + 1, w - i, h - i - 1, color);
        }
    }

    // ---- 仪表盘

    private static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        ShipEntity ship = currentShip();
        // F1 隐藏界面时仪表盘也藏起来
        if (ship == null || ship.structure() == null || minecraft.gui.hud.isHidden()) {
            return;
        }
        ShipStructure structure = ship.structure();
        ShipStats stats = structure.stats();
        Font font = minecraft.font;
        boolean pilot = ship.pilot() == minecraft.player;
        OptionalInt spaceSystem = SpaceDimensions.system(ship.level().dimension());
        boolean space = spaceSystem.isPresent();

        List<Component> lines = new ArrayList<>();
        Component status = ship.impulseActive() ? Component.translatable("hud.universe.ship.status.impulse")
                : space ? Component.translatable("hud.universe.ship.status.space")
                : ship.isLanded() ? Component.translatable("hud.universe.ship.status.landed")
                : Component.translatable("hud.universe.ship.status.flying");
        lines.add(Component.translatable("hud.universe.ship.speed", String.format("%.1f", ship.clientSpeed * 20)));
        if (space) {
            nearest(spaceSystem.getAsInt(), ship, lines);
            if (stats.warpDrives() > 0) {
                lines.add(Component.translatable("hud.universe.ship.warp", (int) stats.warpRange(), warpers(minecraft),
                        UniverseKeys.STAR_MAP.getTranslatedKeyMessage()));
            }
        } else {
            lines.add(Component.translatable("hud.universe.ship.altitude", (int) Math.floor(ship.getY()),
                    ship.level().getMaxY() + ShipEntity.SPACE_ALTITUDE));
            double twr = stats.thrustToWeight(ship.gravity());
            lines.add(Component.translatable("hud.universe.ship.thrust", String.format("%.2f", twr), String.format("%.2f", ship.gravity())));
        }
        lines.add(Component.translatable("hud.universe.ship.mass", stats.blocks(), String.format("%.0f", stats.mass())));

        Component title = Component.translatable("hud.universe.ship.title").append(" · ").append(status);
        float capacity = (float) Math.max(1, stats.fuelCapacity());
        Component fuelText = Component.translatable("hud.universe.ship.fuel", Math.round(ship.fuel()), Math.round(capacity));
        Component keys = Component.translatable(space ? "hud.universe.ship.keys.space" : "hud.universe.ship.keys");
        String hintKey = ship.isLanded() ? "hud.universe.ship.hint.park"
                : ship.fuel() <= 0 && !minecraft.player.hasInfiniteMaterials() ? "hud.universe.ship.hint.no_fuel"
                : space ? "hud.universe.ship.hint.land"
                : "hud.universe.ship.hint.space";
        Component hint = Component.translatable(hintKey);
        // 船体、护盾、武器（ADR-0017）
        double maxHull = Math.max(1, stats.hull());
        Component hullText = Component.translatable("hud.universe.ship.hull", Math.round(ship.hull()), Math.round(maxHull));
        Component shieldText = stats.shield() > 0
                ? Component.translatable("hud.universe.ship.shield", Math.round(ship.shield()), Math.round(stats.shield())) : null;
        Component weaponText = null;
        if (stats.armed()) {
            Component weapon = ship.beamMode()
                    ? Component.translatable("hud.universe.ship.weapon.beam", stats.beams())
                    : Component.translatable("hud.universe.ship.weapon.cannon", stats.cannons());
            weaponText = Component.translatable("hud.universe.ship.weapon", weapon);
            if (ship.weaponOverheated()) {
                weaponText = weaponText.copy().append(Component.translatable("hud.universe.ship.weapon.overheated"));
            }
        }
        Component weaponKeys = stats.armed() ? Component.translatable("hud.universe.ship.keys.weapons",
                minecraft.options.keyAttack.getTranslatedKeyMessage(), UniverseKeys.TOOL_MODE.getTranslatedKeyMessage()) : null;
        // 面板跟着最长的一行变宽，英文的按键说明比中文长
        int panelWidth = Math.max(PANEL_WIDTH, font.width(title) + 6);
        for (Component line : lines) {
            panelWidth = Math.max(panelWidth, font.width(line) + 6);
        }
        if (pilot) {
            panelWidth = Math.max(panelWidth, Math.max(font.width(keys), font.width(hint)) + 6);
            if (weaponKeys != null) {
                panelWidth = Math.max(panelWidth, font.width(weaponKeys) + 6);
            }
        }
        if (weaponText != null) {
            panelWidth = Math.max(panelWidth, font.width(weaponText) + 6);
        }

        int x = 6;
        int lineHeight = 10;
        int height = 14 + lines.size() * lineHeight + 14 + 14 + (shieldText != null ? 14 : 0) + (weaponText != null ? 14 : 0)
                + (pilot ? 22 + (weaponKeys != null ? 10 : 0) : 0);
        int y = graphics.guiHeight() / 2 - height / 2;
        graphics.fill(x - 3, y - 3, x + panelWidth, y + height, 0x90000000);
        graphics.text(font, title, x, y, 0xFF9FE6FF, false);
        y += 13;
        for (Component line : lines) {
            graphics.text(font, line, x, y, 0xFFE0E0E0, false);
            y += lineHeight;
        }
        // 燃料条
        float fuelShare = Mth.clamp(ship.fuel() / capacity, 0, 1);
        graphics.text(font, fuelText, x, y, 0xFFE0E0E0, false);
        y += 10;
        int barWidth = PANEL_WIDTH - 8;
        int color = fuelShare > 0.3F ? 0xFF4FD16B : fuelShare > 0.1F ? 0xFFE8C341 : 0xFFE5533D;
        graphics.fill(x, y, x + barWidth, y + 3, 0xFF303030);
        graphics.fill(x, y, x + Math.round(barWidth * fuelShare), y + 3, color);
        y += 6;
        float hullShare = Mth.clamp((float) (ship.hull() / maxHull), 0, 1);
        y = bar(graphics, font, hullText, x, y, barWidth, hullShare,
                hullShare > 0.5F ? 0xFFD9DDE3 : hullShare > 0.25F ? 0xFFE8C341 : 0xFFE5533D);
        if (shieldText != null) {
            y = bar(graphics, font, shieldText, x, y, barWidth, Mth.clamp((float) (ship.shield() / stats.shield()), 0, 1), 0xFF6FD8FF);
        }
        if (weaponText != null) {
            int heatColor = ship.weaponOverheated() ? 0xFFE5533D : ship.weaponHeat() > 70 ? 0xFFFF9F43 : 0xFFFFE08A;
            y = bar(graphics, font, weaponText, x, y, barWidth, Mth.clamp(ship.weaponHeat() / 100F, 0, 1), heatColor);
        }
        if (pilot) {
            graphics.text(font, keys, x, y, 0xFF9A9A9A, false);
            y += 10;
            if (weaponKeys != null) {
                graphics.text(font, weaponKeys, x, y, 0xFF9A9A9A, false);
                y += 10;
            }
            int parking = ship.parkingTicks();
            if (parking > 0) {
                graphics.text(font, Component.translatable("hud.universe.ship.parking"), x, y, 0xFFFFD27F, false);
                int w = Math.round(barWidth * parking / (float) ShipEntity.PARK_TICKS);
                graphics.fill(x + 60, y + 3, x + 60 + Math.min(w, barWidth - 60), y + 6, 0xFFFFD27F);
            } else {
                graphics.text(font, hint, x, y, 0xFF9A9A9A, false);
            }
        }
    }

    /** 一行字加下面一根进度条，返回下一行的 y。 */
    private static int bar(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int width, float share, int color) {
        graphics.text(font, text, x, y, 0xFFE0E0E0, false);
        y += 10;
        graphics.fill(x, y, x + width, y + 2, 0xFF303030);
        graphics.fill(x, y, x + Math.round(width * share), y + 2, color);
        return y + 4;
    }

    /** 太空里列出最近的三个天体：名字、距离和方位。 */
    private static void nearest(int system, ShipEntity ship, List<Component> lines) {
        if (system != layoutSystem) {
            layout = ClientGalaxy.galaxy().flatMap(galaxy -> galaxy.system(system)).map(SpaceLayout::of).orElse(List.of());
            layoutSystem = system;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        float viewYaw = player == null ? ship.getYRot() : player.getYRot();
        List<SpaceLayout.Body> sorted = new ArrayList<>(layout);
        sorted.sort(Comparator.comparingDouble(body -> body.distanceTo(ship.getX(), ship.getY(), ship.getZ()) - body.radius()));
        for (int i = 0; i < Math.min(3, sorted.size()); i++) {
            SpaceLayout.Body body = sorted.get(i);
            double dx = body.x() - ship.getX();
            double dz = body.z() - ship.getZ();
            float bearing = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float relative = Mth.wrapDegrees(bearing - viewYaw);
            int distance = (int) Math.max(0, body.distanceTo(ship.getX(), ship.getY(), ship.getZ()) - body.radius());
            String name = GalaxyText.planet(body.planet());
            Component kind = body.planet().landable() ? Component.empty() : Component.translatable("hud.universe.ship.unlandable");
            // 箭头只管水平方向；高度差超过半个大气球时标出来，不然会从星球上方或下方飞过去
            double dy = body.y() - ship.getY();
            Component vertical = Math.abs(dy) < body.radius() * 0.5 ? Component.empty()
                    : Component.translatable(dy > 0 ? "hud.universe.ship.above" : "hud.universe.ship.below", (int) Math.abs(dy));
            lines.add(Component.literal(arrow(relative) + " ").append(Component.translatable("hud.universe.ship.target", name, distance))
                    .append(vertical).append(kind));
        }
    }

    /** 背包里有几个空间翘曲器；创造模式显示 ∞。 */
    private static String warpers(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return "0";
        }
        if (player.hasInfiniteMaterials()) {
            return "∞";
        }
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(UniverseItems.SPACE_WARPER.get())) {
                count += stack.getCount();
            }
        }
        return Integer.toString(count);
    }

    /** 相对视线的方位画成八个方向的箭头。 */
    private static String arrow(float relative) {
        String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
        int index = Math.floorMod(Math.round(relative / 45F), 8);
        return arrows[index];
    }
}
