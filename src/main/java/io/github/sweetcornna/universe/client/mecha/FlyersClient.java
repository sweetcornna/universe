package io.github.sweetcornna.universe.client.mecha;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.network.FlyersPayload;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 建造无人机和配送机器人的画法（ADR-0023）：服务端发来每架现在这一段航线，客户端按游戏时间从起点朝目标飞，
 * 中间稍微拱起来；目标是玩家时追着玩家飞。带着货的在下面挂着那个物品。
 */
public final class FlyersClient {
    private static final ContextKey<List<Craft>> CRAFTS = new ContextKey<>(Universe.id("mecha_flyers"));
    private static final double VIEW_DISTANCE = 128;

    private static final Map<Integer, FlyersPayload.Flyer> FLYERS = new HashMap<>();

    private record Craft(double x, double y, double z, float yaw, float scale, int light, ItemStackRenderState model,
            @Nullable ItemStackRenderState cargo) {
    }

    private FlyersClient() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(FlyersPayload.TYPE, FlyersClient::handle));
        gameBus.addListener(ExtractLevelRenderStateEvent.class, FlyersClient::extract);
        gameBus.addListener(SubmitCustomGeometryEvent.class, FlyersClient::submit);
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> FLYERS.clear());
    }

    private static void handle(FlyersPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.reset()) {
                FLYERS.clear();
            }
            for (FlyersPayload.Flyer flyer : payload.flyers()) {
                FLYERS.put(flyer.id(), flyer);
            }
            payload.removed().forEach(FLYERS::remove);
        });
    }

    /** 某一时刻这一架在哪：世界坐标和朝向。 */
    static double[] position(FlyersPayload.Flyer flyer, double now, ClientLevel level, float partialTick) {
        Vec3 from = new Vec3(flyer.fx(), flyer.fy(), flyer.fz());
        if (flyer.speed() <= 0) {
            // 停在虚影旁边建：上下轻轻晃
            double bob = Math.sin((now + flyer.id() * 7) * 0.25) * 0.08;
            return new double[] {from.x, from.y + bob, from.z, (flyer.id() * 37) % 360};
        }
        Vec3 target = new Vec3(flyer.tx(), flyer.ty(), flyer.tz());
        if (flyer.entity() >= 0) {
            Entity entity = level.getEntity(flyer.entity());
            if (entity != null) {
                target = entity.getPosition(partialTick).add(0, entity.getBbHeight() * 0.8, 0);
            }
        }
        double distance = from.distanceTo(target);
        double s = distance <= 1e-6 ? 1 : Math.min(1, flyer.speed() * Math.max(0, now - flyer.start()) / distance);
        double eased = s * s * (3 - 2 * s);
        double arc = Math.sin(Math.PI * s) * Math.min(2.5, distance * 0.12);
        Vec3 at = from.lerp(target, eased).add(0, arc, 0);
        double yaw = Math.toDegrees(Math.atan2(-(target.x - from.x), target.z - from.z));
        return new double[] {at.x, at.y, at.z, yaw};
    }

    private static void extract(ExtractLevelRenderStateEvent event) {
        LevelRenderState state = event.getRenderState();
        ClientLevel level = event.getLevel();
        if (FLYERS.isEmpty() || level == null) {
            state.setRenderData(CRAFTS, null);
            return;
        }
        Vec3 camera = event.getCamera().position();
        float partial = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double now = state.gameTime + state.worldPartialTicks;
        Minecraft minecraft = Minecraft.getInstance();
        ItemStack drone = new ItemStack(UniverseItems.CONSTRUCTION_DRONE.get());
        ItemStack bot = new ItemStack(UniverseItems.LOGISTICS_BOT.get());
        List<Craft> crafts = new ArrayList<>();
        int seed = 0;
        for (FlyersPayload.Flyer flyer : FLYERS.values()) {
            double[] p = position(flyer, now, level, partial);
            double dx = p[0] - camera.x;
            double dy = p[1] - camera.y;
            double dz = p[2] - camera.z;
            if (dx * dx + dy * dy + dz * dz > VIEW_DISTANCE * VIEW_DISTANCE) {
                continue;
            }
            boolean isBot = flyer.kind() == FlyersPayload.BOT;
            ItemStackRenderState model = new ItemStackRenderState();
            minecraft.getItemModelResolver().updateForTopItem(model, isBot ? bot : drone, ItemDisplayContext.FIXED, level, null, seed++);
            ItemStackRenderState cargo = null;
            if (flyer.cargo() >= 0) {
                cargo = new ItemStackRenderState();
                minecraft.getItemModelResolver().updateForTopItem(cargo, new ItemStack(BuiltInRegistries.ITEM.byId(flyer.cargo())),
                        ItemDisplayContext.FIXED, level, null, seed++);
            }
            BlockPos at = BlockPos.containing(p[0], p[1], p[2]);
            int light = LightCoordsUtil.pack(Math.max(10, level.getBrightness(LightLayer.BLOCK, at)), level.getBrightness(LightLayer.SKY, at));
            crafts.add(new Craft(dx, dy, dz, (float) p[3], isBot ? 0.7F : 0.6F, light, model, cargo));
        }
        state.setRenderData(CRAFTS, crafts.isEmpty() ? null : crafts);
    }

    private static void submit(SubmitCustomGeometryEvent event) {
        List<Craft> crafts = event.getLevelRenderState().getRenderData(CRAFTS);
        if (crafts == null) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        for (Craft craft : crafts) {
            pose.pushPose();
            pose.translate(craft.x, craft.y, craft.z);
            pose.rotateDegrees(Axis.YP, -craft.yaw);
            pose.pushPose();
            pose.scale(craft.scale, craft.scale, craft.scale);
            craft.model.submit(pose, event.getSubmitNodeCollector(), craft.light, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
            if (craft.cargo != null) {
                pose.translate(0, -0.32, 0);
                pose.scale(0.3F, 0.3F, 0.3F);
                craft.cargo.submit(pose, event.getSubmitNodeCollector(), craft.light, OverlayTexture.NO_OVERLAY, 0);
            }
            pose.popPose();
        }
    }

    /** 测试和截图用：现在在飞的有几架。 */
    public static int count() {
        return FLYERS.size();
    }
}
