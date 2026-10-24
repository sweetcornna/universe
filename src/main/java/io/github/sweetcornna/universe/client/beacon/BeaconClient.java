package io.github.sweetcornna.universe.client.beacon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.beacon.HoloBeaconBlockEntity;
import io.github.sweetcornna.universe.client.ClientGalaxy;
import io.github.sweetcornna.universe.client.HudMarker;
import io.github.sweetcornna.universe.client.ScreenProjection;
import io.github.sweetcornna.universe.core.beacon.BeaconStyle;
import io.github.sweetcornna.universe.network.HoloBeaconPayloads;
import io.github.sweetcornna.universe.network.MemoPayloads;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * 全息信标和备忘的客户端（ADR-0033 §六）：信标的界面和渲染器、HUD 上同一颗星球的信标、给星图用的信标表，以及星球和恒星系备忘的界面。
 */
public final class BeaconClient {
    /** HUD 上最多标几个信标（离得近的优先）。 */
    private static final int HUD_MAX = 16;
    /** 离得这么近就不标了，看得见实物。 */
    private static final double HUD_NEAR = 6;

    private static List<HoloBeaconPayloads.Entry> beacons = List.of();
    private static @Nullable String pendingKey;
    private static Component pendingTitle = Component.empty();
    private static @Nullable Screen pendingParent;

    private BeaconClient() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> {
            event.register(HoloBeaconPayloads.Open.TYPE, (payload, context) -> context.enqueueWork(() -> {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.level != null && minecraft.level.getBlockEntity(payload.pos()) instanceof HoloBeaconBlockEntity beacon) {
                    minecraft.gui.setScreen(new HoloBeaconScreen(beacon));
                }
            }));
            event.register(HoloBeaconPayloads.All.TYPE, (payload, context) -> context.enqueueWork(() -> beacons = List.copyOf(payload.beacons())));
            event.register(MemoPayloads.Content.TYPE, (payload, context) -> context.enqueueWork(() -> {
                if (payload.key().equals(pendingKey)) {
                    pendingKey = null;
                    Minecraft.getInstance().gui.setScreen(new MemoScreen(payload.key(), pendingTitle, payload.memo(), pendingParent));
                }
            }));
        });
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                event -> event.registerBlockEntityRenderer(UniverseBlockEntities.HOLO_BEACON.get(), HoloBeaconRenderer::new));
        modBus.addListener(RegisterGuiLayersEvent.class,
                event -> event.registerBelow(VanillaGuiLayers.CROSSHAIR, Universe.id("holo_beacons"), BeaconClient::renderHud));
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            beacons = List.of();
            pendingKey = null;
            pendingParent = null;
        });
    }

    public static List<HoloBeaconPayloads.Entry> beacons() {
        return beacons;
    }

    /** 星图上 system 这个恒星系旁边要标的信标：范围到恒星系的只在人也在这个恒星系时标，到星团的都标。 */
    public static List<HoloBeaconPayloads.Entry> onStarMap(int system) {
        OptionalInt here = ClientGalaxy.currentSystem();
        boolean sameSystem = here.isPresent() && here.getAsInt() == system;
        List<HoloBeaconPayloads.Entry> list = new ArrayList<>();
        for (HoloBeaconPayloads.Entry beacon : beacons) {
            if (beacon.system() == system && beacon.visibility().onStarMap(sameSystem)) {
                list.add(beacon);
            }
        }
        return list;
    }

    /** 向服务端要一页备忘，到了就打开界面；关掉以后回到 parent。 */
    public static void openMemo(String key, Component title, @Nullable Screen parent) {
        pendingKey = key;
        pendingTitle = title;
        pendingParent = parent;
        ClientPacketDistributor.sendToServer(new MemoPayloads.Open(key));
    }

    public static @Nullable Item icon(String id) {
        Identifier key = id.isEmpty() ? null : Identifier.tryParse(id);
        Item item = key == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(key);
        return item == Items.AIR ? null : item;
    }

    /** 信标的名字：写了字用字，没写用图标的名字，都没有就叫"全息信标"。 */
    public static Component name(String label, String icon) {
        if (!label.isEmpty()) {
            return Component.literal(label);
        }
        Item item = icon(icon);
        return item != null ? new ItemStack(item).getHoverName() : Component.translatable("block.universe.holo_beacon");
    }

    private static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (beacons.isEmpty() || player == null || minecraft.level == null || minecraft.gui.hud.isHidden()) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        List<HoloBeaconPayloads.Entry> here = new ArrayList<>();
        for (HoloBeaconPayloads.Entry beacon : beacons) {
            if (beacon.dimension() == minecraft.level.dimension() && Vec3.atCenterOf(beacon.pos()).distanceTo(eye) > HUD_NEAR) {
                here.add(beacon);
            }
        }
        if (here.isEmpty()) {
            return;
        }
        here.sort(Comparator.comparingDouble(beacon -> Vec3.atCenterOf(beacon.pos()).distanceToSqr(eye)));
        float pulse = 0.85F + 0.15F * (float) Math.sin((minecraft.level.getGameTime() + delta.getGameTimeDeltaPartialTick(false)) * 0.1);
        ScreenProjection projection = ScreenProjection.of(minecraft, graphics.guiWidth(), graphics.guiHeight());
        // 远的先画，近的盖在上面
        for (int i = Math.min(here.size(), HUD_MAX) - 1; i >= 0; i--) {
            HoloBeaconPayloads.Entry beacon = here.get(i);
            Vec3 target = Vec3.atBottomCenterOf(beacon.pos()).add(0, 2.2, 0);
            int distance = (int) Math.round(target.distanceTo(eye));
            Component label = Component.translatable("hud.universe.waypoint", name(beacon.label(), beacon.icon()), distance);
            HudMarker.draw(graphics, minecraft, target, BeaconStyle.color(beacon.color()), label, pulse);
            Item icon = icon(beacon.icon());
            int[] point = projection.project(target);
            if (icon != null && projection.onScreen(point, 24)) {
                graphics.item(new ItemStack(icon), point[0] - 8, point[1] - 24);
            }
        }
    }
}
