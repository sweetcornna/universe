package io.github.sweetcornna.universe.client.ship;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.ClientGalaxy;
import io.github.sweetcornna.universe.client.UniverseKeys;
import io.github.sweetcornna.universe.core.ship.ShipStats;
import io.github.sweetcornna.universe.core.space.SpaceLayout;
import io.github.sweetcornna.universe.core.space.Warp;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.entity.ship.ShipStructure;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * 驾驶飞船时的客户端部分：仪表盘、跟船大小走的第三人称镜头、引擎声。
 *
 * <p>坐上飞船时自动切到第三人称（玩法设计 §5.2 的默认视角），下船后恢复原来的视角。
 */
public final class ShipClient {
    private static final int PANEL_WIDTH = 170;
    private static @Nullable CameraType cameraBeforeBoarding;
    private static @Nullable ShipEngineSound engineSound;
    private static int layoutSystem = -1;
    private static List<SpaceLayout.Body> layout = List.of();

    private ShipClient() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                event -> event.registerEntityRenderer(UniverseEntities.SHIP.get(), ShipRenderer::new));
        modBus.addListener(RegisterGuiLayersEvent.class,
                event -> event.registerAbove(VanillaGuiLayers.HOTBAR, Universe.id("ship_hud"), ShipClient::renderHud));
        gameBus.addListener(CalculateDetachedCameraDistanceEvent.class, ShipClient::cameraDistance);
        gameBus.addListener(ClientTickEvent.Post.class, event -> tick());
    }

    private static @Nullable ShipEntity currentShip() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.getVehicle() instanceof ShipEntity ship ? ship : null;
    }

    private static void cameraDistance(CalculateDetachedCameraDistanceEvent event) {
        ShipEntity ship = currentShip();
        if (ship != null && ship.structure() != null) {
            float wanted = (float) (ship.structure().shape().radius() * 1.6 + ship.structure().shape().height() * 0.5 + 3);
            event.setDistance(Math.max(event.getDistance(), wanted));
        }
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
                lines.add(Component.translatable("hud.universe.ship.warp", (int) Warp.range(stats.warpDrives()), warpers(minecraft),
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
        // 面板跟着最长的一行变宽，英文的按键说明比中文长
        int panelWidth = Math.max(PANEL_WIDTH, font.width(title) + 6);
        for (Component line : lines) {
            panelWidth = Math.max(panelWidth, font.width(line) + 6);
        }
        if (pilot) {
            panelWidth = Math.max(panelWidth, Math.max(font.width(keys), font.width(hint)) + 6);
        }

        int x = 6;
        int lineHeight = 10;
        int height = 14 + lines.size() * lineHeight + 14 + (pilot ? 22 : 0);
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
        if (pilot) {
            graphics.text(font, keys, x, y, 0xFF9A9A9A, false);
            y += 10;
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
            String name = Minecraft.getInstance().options.languageCode.startsWith("zh") ? body.planet().name().zh() : body.planet().name().en();
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
