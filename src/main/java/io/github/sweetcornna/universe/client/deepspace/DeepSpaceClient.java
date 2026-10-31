package io.github.sweetcornna.universe.client.deepspace;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.HudMarker;
import io.github.sweetcornna.universe.client.UniverseKeys;
import io.github.sweetcornna.universe.client.dev.DevScript;
import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.client.station.StationClient;
import io.github.sweetcornna.universe.core.deepspace.DeepSpaceSites.Kind;
import io.github.sweetcornna.universe.core.deepspace.Integrity;
import io.github.sweetcornna.universe.core.ship.ShipShape;
import io.github.sweetcornna.universe.deepspace.DeepSpacePayloads;
import io.github.sweetcornna.universe.deepspace.ShipTractor;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * 深空的客户端部分（ADR-0034 §四）：HUD 上标出附近的碎片带、前哨站、残骸；靠近残骸时顶上显示结构完整度，
 * 报警时闪红、熔毁时倒计时；出舱键、牵引光束键；画牵引光束和碎片；出舱以后标着自己的船。
 */
public final class DeepSpaceClient {
    /** 出舱（默认 X）：坐在船上、在太空里时下船。 */
    public static final KeyMapping EVA = new KeyMapping("key.universe.eva", InputConstants.Type.KEYBOARD, InputConstants.KEY_X,
            UniverseKeys.CATEGORY);
    /** 牵引光束（默认 C）：开着船时开关。 */
    public static final KeyMapping TRACTOR = new KeyMapping("key.universe.tractor", InputConstants.Type.KEYBOARD, InputConstants.KEY_C,
            UniverseKeys.CATEGORY);
    /** 离残骸这么近时显示完整度（格）。 */
    private static final double PANEL_RANGE = 220;
    /** 多久没收到兴趣点就不标了（刻）：换了维度、离开太空。 */
    private static final int STALE = 60;
    private static final int[] COLORS = {0xB9C3CC, 0x8FD3FF, 0xFFA552};
    private static final int ALARM_COLOR = 0xFF4A3A;
    private static final int SHIP_COLOR = 0x7FE7FF;
    private static final int TRACTOR_COLOR = 0x6FE3FF;
    private static final int PANEL_WIDTH = 182;

    private static List<DeepSpacePayloads.Marker> markers = List.of();
    private static @Nullable ResourceKey<Level> markersIn;
    private static long markersAt;
    /** 出舱前坐的那艘船（HUD 上标出来，回船用）。 */
    private static int lastShip = -1;

    private DeepSpaceClient() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(DeepSpacePayloads.Sites.TYPE,
                (payload, context) -> context.enqueueWork(() -> receive(payload))));
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                event -> event.registerEntityRenderer(UniverseEntities.DEBRIS_CHUNK.get(), DebrisChunkRenderer::new));
        modBus.addListener(RegisterGuiLayersEvent.class,
                event -> event.registerBelow(VanillaGuiLayers.CROSSHAIR, Universe.id("deep_space"), DeepSpaceClient::renderHud));
        gameBus.addListener(ClientTickEvent.Post.class, event -> tick());
        gameBus.addListener(SubmitCustomGeometryEvent.class, DeepSpaceClient::submitBeams);
        // 退出世界时不再拿着上一个世界和里面的船
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            nearbyShips = List.of();
            shipsLevel = null;
            shipsTick = Long.MIN_VALUE;
        });
        devVerbs();
    }

    private static void receive(DeepSpacePayloads.Sites payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        markers = payload.markers();
        markersIn = minecraft.level.dimension();
        markersAt = minecraft.level.getGameTime();
    }

    /** 现在要标的兴趣点（过时了、换了维度就没有）。 */
    static List<DeepSpacePayloads.Marker> markers(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.level.dimension() != markersIn || minecraft.level.getGameTime() - markersAt > STALE) {
            return List.of();
        }
        return markers;
    }

    // ---- 按键

    private static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        if (player.getVehicle() instanceof ShipEntity ship) {
            lastShip = ship.getId();
        }
        while (EVA.consumeClick()) {
            eva(minecraft);
        }
        while (TRACTOR.consumeClick()) {
            tractor(minecraft);
        }
    }

    private static void eva(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        if (!(player.getVehicle() instanceof ShipEntity)) {
            player.sendOverlayMessage(Component.translatable("message.universe.eva.not_aboard"));
            return;
        }
        if (!SpaceDimensions.isSpace(player.level().dimension())) {
            player.sendOverlayMessage(Component.translatable("message.universe.eva.unavailable"));
            return;
        }
        ClientPacketDistributor.sendToServer(DeepSpacePayloads.Eva.INSTANCE);
    }

    private static void tractor(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player != null && player.getVehicle() instanceof ShipEntity ship && ship.pilot() == player) {
            ClientPacketDistributor.sendToServer(DeepSpacePayloads.Tractor.INSTANCE);
        }
    }

    // ---- HUD

    private static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.gui.hud.isHidden()) {
            return;
        }
        float partial = delta.getGameTimeDeltaPartialTick(false);
        double time = minecraft.level.getGameTime() + partial;
        float pulse = 0.75F + 0.25F * (float) Math.sin(time * 0.15);
        Vec3 eye = player.getEyePosition(partial);
        DeepSpacePayloads.Marker nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (DeepSpacePayloads.Marker marker : markers(minecraft)) {
            Vec3 at = new Vec3(marker.x(), marker.y(), marker.z());
            double distance = eye.distanceTo(at);
            Kind kind = Kind.values()[Math.clamp(marker.kind(), 0, Kind.values().length - 1)];
            if (kind == Kind.HULK && distance < nearestDistance) {
                nearest = marker;
                nearestDistance = distance;
            }
            // 走到跟前就不标了，免得菱形糊在残骸上
            if (distance < kind.extent() * 0.6) {
                continue;
            }
            String meters = String.format(Locale.ROOT, "%,d", Math.round(distance));
            boolean alarm = kind == Kind.HULK && marker.integrity() >= 0 && marker.integrity() < Integrity.ALARM;
            Component label = kind == Kind.HULK && marker.integrity() >= 0 && marker.integrity() < Integrity.FULL
                    ? Component.translatable("hud.universe.deep_space.hulk_marker", marker.integrity(), meters)
                    : Component.translatable("hud.universe.deep_space.marker", Component.translatable("hud.universe.deep_space." + kind.key()), meters);
            int color = alarm || marker.countdown() >= 0 ? ALARM_COLOR : COLORS[kind.ordinal()];
            HudMarker.draw(graphics, minecraft, at, color, label, alarm ? 0.55F + 0.45F * (float) Math.abs(Math.sin(time * 0.3)) : pulse);
        }
        if (nearest != null && nearestDistance < PANEL_RANGE) {
            hulkPanel(graphics, minecraft, nearest, time);
        }
        ownShip(graphics, minecraft, player, eye, pulse);
        tractorStatus(graphics, minecraft, player);
    }

    /** 顶上的结构完整度：一条进度条，报警闪红，熔毁倒计时。 */
    private static void hulkPanel(GuiGraphicsExtractor graphics, Minecraft minecraft, DeepSpacePayloads.Marker hulk, double time) {
        int integrity = Math.max(0, hulk.integrity());
        boolean meltdown = hulk.countdown() >= 0;
        boolean alarm = integrity < Integrity.ALARM || meltdown;
        int x = (graphics.guiWidth() - PANEL_WIDTH) / 2;
        int y = 30;
        int height = meltdown || hulk.salvaged() ? 34 : 24;
        boolean flash = alarm && Math.sin(time * (meltdown ? 0.6 : 0.3)) > 0;
        graphics.fill(x - 1, y - 1, x + PANEL_WIDTH + 1, y + height + 1, flash ? 0xC0FF3A2A : MachineScreen.BORDER);
        graphics.fill(x, y, x + PANEL_WIDTH, y + height, 0xC0101820);
        Component title = Component.translatable("hud.universe.deep_space.integrity", integrity);
        graphics.text(minecraft.font, title, x + 5, y + 4, alarm ? 0xFFFF6A5A : 0xFFE0E8EE, true);
        int barX = x + 5;
        int barY = y + 15;
        int barWidth = PANEL_WIDTH - 10;
        graphics.fill(barX, barY, barX + barWidth, barY + 5, 0xFF24303A);
        int fill = barWidth * integrity / Integrity.FULL;
        int barColor = integrity < Integrity.ALARM ? 0xFFE8483A : integrity < 60 ? 0xFFE8A93A : 0xFF6FD08C;
        graphics.fill(barX, barY, barX + fill, barY + 5, barColor);
        // 报警线
        int alarmX = barX + barWidth * Integrity.ALARM / Integrity.FULL;
        graphics.fill(alarmX, barY - 1, alarmX + 1, barY + 6, 0xFFFF6A5A);
        if (meltdown) {
            int seconds = Mth.ceil(hulk.countdown() / 20.0);
            Component warn = Component.translatable("hud.universe.deep_space.meltdown", seconds);
            graphics.text(minecraft.font, warn, x + (PANEL_WIDTH - minecraft.font.width(warn)) / 2, y + 23, flash ? 0xFFFFFFFF : 0xFFFF4A3A, true);
        } else if (hulk.salvaged()) {
            Component done = Component.translatable("hud.universe.deep_space.salvaged");
            graphics.text(minecraft.font, done, x + (PANEL_WIDTH - minecraft.font.width(done)) / 2, y + 23, 0xFF9FE8FF, true);
        }
    }

    /** 出舱以后：标着自己刚下来的那艘船。 */
    private static void ownShip(GuiGraphicsExtractor graphics, Minecraft minecraft, LocalPlayer player, Vec3 eye, float pulse) {
        if (lastShip < 0 || player.isPassenger() || !StationClient.weightless(player)) {
            return;
        }
        Entity entity = minecraft.level.getEntity(lastShip);
        if (!(entity instanceof ShipEntity ship) || ship.structure() == null) {
            return;
        }
        Vec3 at = ship.position().add(0, ship.structure().shape().height() / 2.0, 0);
        String meters = String.format(Locale.ROOT, "%,d", Math.round(eye.distanceTo(at)));
        HudMarker.draw(graphics, minecraft, at, SHIP_COLOR, Component.translatable("hud.universe.deep_space.own_ship", meters), pulse);
    }

    /** 开着船时右下角写牵引光束开没开。 */
    private static void tractorStatus(GuiGraphicsExtractor graphics, Minecraft minecraft, LocalPlayer player) {
        if (!(player.getVehicle() instanceof ShipEntity ship) || ship.pilot() != player || !ship.tractorOn()) {
            return;
        }
        Component text = Component.translatable("hud.universe.deep_space.tractor_on", Math.round(ShipTractor.range(ship)),
                TRACTOR.getTranslatedKeyMessage());
        int w = minecraft.font.width(text);
        int x = graphics.guiWidth() - w - 8;
        int y = graphics.guiHeight() - 40;
        graphics.fill(x - 3, y - 2, x + w + 3, y + minecraft.font.lineHeight + 1, 0x90000000);
        graphics.text(minecraft.font, text, x, y, 0xFF000000 | TRACTOR_COLOR, true);
    }

    // ---- 牵引光束

    /** 摄像机周围多远的船要画牵引光束。 */
    private static final double BEAM_VIEW = 160;
    /** 附近的船每刻查一次（多查这么远），同一刻里各帧共用；摄像机挪出这么远就重查。 */
    private static final double BEAM_MARGIN = 16;
    private static List<ShipEntity> nearbyShips = List.of();
    private static @Nullable Level shipsLevel;
    private static long shipsTick = Long.MIN_VALUE;
    private static Vec3 shipsCamera = Vec3.ZERO;

    /** 摄像机附近的船。船的碰撞箱每刻才动一次，按刻查、多查一圈；每帧再按摄像机的位置筛（ADR-0038）。 */
    private static List<ShipEntity> nearbyShips(Level level, Vec3 camera) {
        if (level != shipsLevel || level.getGameTime() != shipsTick || camera.distanceToSqr(shipsCamera) > BEAM_MARGIN * BEAM_MARGIN) {
            shipsLevel = level;
            shipsTick = level.getGameTime();
            shipsCamera = camera;
            nearbyShips = level.getEntitiesOfClass(ShipEntity.class, new AABB(camera, camera).inflate(BEAM_VIEW + BEAM_MARGIN));
        }
        return nearbyShips;
    }

    /** 开着牵引光束的船：朝每样正往里拉的东西画一股淡青的束。 */
    private static void submitBeams(SubmitCustomGeometryEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double time = minecraft.level.getGameTime() + partial;
        List<Vec3[]> beams = new ArrayList<>();
        AABB view = new AABB(camera, camera).inflate(BEAM_VIEW);
        for (ShipEntity ship : nearbyShips(minecraft.level, camera)) {
            if (ship.isRemoved() || !ship.tractorOn() || !ship.getBoundingBox().intersects(view) || ship.structure() == null) {
                continue;
            }
            ShipShape shape = ship.structure().shape();
            double range = ShipTractor.range(ship);
            Vec3 center = ship.getPosition(partial).add(0, shape.height() / 2.0, 0);
            int count = 0;
            for (Entity entity : minecraft.level.getEntities(ship, new AABB(center, center).inflate(range), ShipTractor::pullable)) {
                Vec3 at = entity.getPosition(partial).add(0, entity.getBbHeight() / 2.0, 0);
                Vec3 toShip = center.subtract(at);
                double distance = toShip.length();
                // 只画正往船这边飘的（抛出去的货往外飘，不画）
                if (distance > range || distance < 1e-3 || entity.getDeltaMovement().dot(toShip.scale(1 / distance)) < 0.02 || count++ > 24) {
                    continue;
                }
                Vec3 from = center.add(toShip.scale(-Math.min(shape.radius() * 0.7, distance * 0.5) / distance));
                beams.add(new Vec3[] {from.subtract(camera), at.subtract(camera)});
            }
        }
        if (beams.isEmpty()) {
            return;
        }
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), Beams.RENDER_TYPE, (pose, buffer) -> {
            for (Vec3[] beam : beams) {
                Beams.wavy(pose, buffer, beam[0], beam[1], Vec3.ZERO, TRACTOR_COLOR, 0.35F, 0.25, 8, time);
            }
        });
    }

    // ---- 开发脚本

    private static void devVerbs() {
        DevScript.EXTRA_VERBS.put("dshulk", args -> command("universe deepspace hulk" + (args.length > 0 ? " " + args[0] : "")));
        DevScript.EXTRA_VERBS.put("dsoutpost", args -> command("universe deepspace outpost"));
        DevScript.EXTRA_VERBS.put("dsdebris", args -> command("universe deepspace debris"));
        DevScript.EXTRA_VERBS.put("dsgoto", args -> command("universe deepspace goto " + (args.length > 0 ? args[0] : "hulk")));
        DevScript.EXTRA_VERBS.put("dsstatus", args -> command("universe deepspace status"));
        DevScript.EXTRA_VERBS.put("dsdetonate", args -> command("universe deepspace detonate"));
        DevScript.EXTRA_VERBS.put("dsclear", args -> command("universe deepspace clear"));
        DevScript.EXTRA_VERBS.put("eva", args -> eva(Minecraft.getInstance()));
        DevScript.EXTRA_VERBS.put("board", args -> board(Minecraft.getInstance()));
        DevScript.EXTRA_VERBS.put("tractor", args -> {
            Minecraft minecraft = Minecraft.getInstance();
            boolean want = args.length == 0 || !args[0].equals("off");
            if (minecraft.player != null && minecraft.player.getVehicle() instanceof ShipEntity ship && ship.tractorOn() != want) {
                tractor(minecraft);
            }
        });
    }

    private static void command(String command) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.connection.sendCommand(command);
        }
    }

    /** 回船：对着最近的船（先找刚下来的那艘）右键。 */
    private static void board(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.gameMode == null || player.isPassenger()) {
            return;
        }
        Entity target = lastShip >= 0 ? minecraft.level.getEntity(lastShip) : null;
        if (!(target instanceof ShipEntity) || target.distanceTo(player) > 24) {
            target = minecraft.level.getEntitiesOfClass(ShipEntity.class, player.getBoundingBox().inflate(24)).stream()
                    .min((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player))).orElse(null);
        }
        if (target != null) {
            minecraft.gameMode.interact(player, target, new EntityHitResult(target), InteractionHand.MAIN_HAND);
        }
    }
}
