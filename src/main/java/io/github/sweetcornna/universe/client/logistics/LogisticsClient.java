package io.github.sweetcornna.universe.client.logistics;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.network.LogisticsFlightsPayload;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
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
 * 物流载具的画法（ADR-0015）：服务端只在行程变了时发一份在飞的载具，客户端按自己的游戏时间算每架的位置。
 *
 * <p>无人机走一条拱形航线：从停机坪升起，弧线飞到对方，再降落。运输船在同一个星球上的两座站之间飞得更高；
 * 去别的星球时从停机坪直冲上天，回来时从天上降下来。
 */
public final class LogisticsClient {
    private static final ContextKey<List<Craft>> CRAFTS = new ContextKey<>(Universe.id("logistics_crafts"));
    /** 只画这个距离以内的载具。 */
    private static final double VIEW_DISTANCE = 256;
    /** 运输船去别的星球时，起降各用多少刻。 */
    private static final double VESSEL_LIFT_TICKS = 200;
    /** 运输船冲上天的高度。 */
    private static final double VESSEL_LIFT_HEIGHT = 480;

    private static List<LogisticsFlightsPayload.Flight> flights = List.of();

    private LogisticsClient() {
    }

    /** 要画的一架：位置（相对相机）、朝向、模型。 */
    private record Craft(double x, double y, double z, float yaw, float pitch, float scale, int light, ItemStackRenderState model) {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class,
                event -> event.register(LogisticsFlightsPayload.TYPE, LogisticsClient::handle));
        gameBus.addListener(ExtractLevelRenderStateEvent.class, LogisticsClient::extract);
        gameBus.addListener(SubmitCustomGeometryEvent.class, LogisticsClient::submit);
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> flights = List.of());
    }

    private static void handle(LogisticsFlightsPayload payload, IPayloadContext context) {
        flights = List.copyOf(payload.flights());
    }

    /** 某一时刻载具在哪：世界坐标和飞行方向；看不见（在别的星球）时是 {@code null}。 */
    static double @org.jspecify.annotations.Nullable [] position(LogisticsFlightsPayload.Flight flight, double now) {
        if (now < flight.departAt() || now > flight.returnAt()) {
            return null;
        }
        boolean outbound = now < flight.arriveAt();
        double start = outbound ? flight.departAt() : flight.arriveAt();
        double end = outbound ? flight.arriveAt() : flight.returnAt();
        double s = end <= start ? 1 : (now - start) / (end - start);
        Vec3 from = outbound ? new Vec3(flight.fromX(), flight.fromY(), flight.fromZ()) : new Vec3(flight.toX(), flight.toY(), flight.toZ());
        Vec3 to = outbound ? new Vec3(flight.toX(), flight.toY(), flight.toZ()) : new Vec3(flight.fromX(), flight.fromY(), flight.fromZ());
        boolean fromHere = outbound ? flight.homeHere() : flight.otherHere();
        boolean toHere = outbound ? flight.otherHere() : flight.homeHere();
        if (fromHere && toHere) {
            return arc(from, to, s, flight.vessel());
        }
        // 跨星球的运输船：只画起飞和降落
        if (fromHere) {
            double t = (now - start) / VESSEL_LIFT_TICKS;
            return t > 1 ? null : lift(from, t, false);
        }
        if (toHere) {
            double t = (end - now) / VESSEL_LIFT_TICKS;
            return t > 1 ? null : lift(to, t, true);
        }
        return null;
    }

    /** 同一个世界里的航线：先垂直升起，水平平滑加减速，高度走一条拱。 */
    private static double[] arc(Vec3 from, Vec3 to, double s, boolean vessel) {
        double distance = Math.sqrt(Mth.square(to.x - from.x) + Mth.square(to.z - from.z));
        double cruise = vessel ? Math.min(120, 30 + distance * 0.3) : Math.min(40, 6 + distance * 0.2);
        // 前后各 10% 的时间升降，中间平飞
        double climb = 0.1;
        double horizontal = s < climb ? 0 : s > 1 - climb ? 1 : smooth((s - climb) / (1 - 2 * climb));
        double height;
        if (s < climb) {
            height = cruise * smooth(s / climb);
        } else if (s > 1 - climb) {
            height = cruise * smooth((1 - s) / climb);
        } else {
            height = cruise;
        }
        double x = Mth.lerp(horizontal, from.x, to.x);
        double z = Mth.lerp(horizontal, from.z, to.z);
        double y = Mth.lerp(horizontal, from.y, to.y) + height;
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        // 升降时机头微微朝上/朝下
        double pitch = s < climb ? -20 * Math.sin(Math.PI * s / climb) : s > 1 - climb ? 20 * Math.sin(Math.PI * (1 - s) / climb) : 0;
        return new double[] {x, y, z, Math.toDegrees(Math.atan2(-dx, dz)), pitch};
    }

    /** 跨星球的起降：t 从 0 到 1 是离停机坪越来越远（降落时倒着放）。 */
    private static double[] lift(Vec3 pad, double t, boolean landing) {
        double e = t * t;
        double y = pad.y + VESSEL_LIFT_HEIGHT * e;
        // 往上冲的同时稍稍偏一点，不是笔直的柱子
        double drift = 40 * e;
        double yaw = (pad.x * 31 + pad.z * 17) % 360;
        double x = pad.x - Math.sin(Math.toRadians(yaw)) * drift;
        double z = pad.z + Math.cos(Math.toRadians(yaw)) * drift;
        return new double[] {x, y, z, yaw + (landing ? 180 : 0), landing ? 60 * Math.min(1, t * 3) : -60 * Math.min(1, t * 3)};
    }

    private static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    private static void extract(ExtractLevelRenderStateEvent event) {
        LevelRenderState state = event.getRenderState();
        ClientLevel level = event.getLevel();
        if (flights.isEmpty() || level == null) {
            state.setRenderData(CRAFTS, null);
            return;
        }
        Vec3 camera = event.getCamera().position();
        double now = state.gameTime + state.worldPartialTicks;
        List<Craft> crafts = new ArrayList<>();
        Minecraft minecraft = Minecraft.getInstance();
        ItemStack drone = new ItemStack(UniverseItems.LOGISTICS_DRONE.get());
        ItemStack vessel = new ItemStack(UniverseItems.LOGISTICS_VESSEL.get());
        int seed = 0;
        for (LogisticsFlightsPayload.Flight flight : flights) {
            double[] p = position(flight, now);
            if (p == null) {
                continue;
            }
            double dx = p[0] - camera.x;
            double dy = p[1] - camera.y;
            double dz = p[2] - camera.z;
            double limit = flight.vessel() ? VIEW_DISTANCE * 2 : VIEW_DISTANCE;
            if (dx * dx + dy * dy + dz * dz > limit * limit) {
                continue;
            }
            ItemStackRenderState model = new ItemStackRenderState();
            minecraft.getItemModelResolver().updateForTopItem(model, flight.vessel() ? vessel : drone, ItemDisplayContext.FIXED, level, null,
                    seed++);
            BlockPos at = BlockPos.containing(p[0], p[1], p[2]);
            int light = LightCoordsUtil.pack(Math.max(9, level.getBrightness(LightLayer.BLOCK, at)), level.getBrightness(LightLayer.SKY, at));
            crafts.add(new Craft(dx, dy, dz, (float) p[3], (float) p[4], flight.vessel() ? 3.0F : 1.6F, light, model));
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
            pose.rotateDegrees(Axis.XP, craft.pitch);
            pose.scale(craft.scale, craft.scale, craft.scale);
            craft.model.submit(pose, event.getSubmitNodeCollector(), craft.light, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }
}
