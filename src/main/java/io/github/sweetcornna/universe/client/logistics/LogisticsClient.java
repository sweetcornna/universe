package io.github.sweetcornna.universe.client.logistics;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.logistics.SpaceRoute;
import io.github.sweetcornna.universe.network.LogisticsFlightsPayload;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 物流载具的画法（ADR-0015）：服务端只在行程变了时发一份在飞的载具，客户端按自己的游戏时间算每架的位置。
 *
 * <p>无人机走一条拱形航线：从停机坪升起，弧线飞到对方，再降落。运输船在同一个星球上的两座站之间飞得更高；
 * 去别的星球时从停机坪直冲上天，回来时从天上降下来。
 *
 * <p>恒星系的太空维度里（ADR-0030）：运输船从起点星球的大气球钻出来，沿一条绕开恒星的曲线飞到终点的大气球；跨恒星系的
 * 一路加速飞到恒星系边上，船身拉长、一闪就跃迁走了，跃迁进来的反过来。远处的船挪到近处、按视大小缩小了画，
 * 但至少留一个亮点，免得超出远裁剪面就没了。
 */
public final class LogisticsClient {
    private static final ContextKey<List<Craft>> CRAFTS = new ContextKey<>(Universe.id("logistics_crafts"));
    /** 只画这个距离以内的载具。 */
    private static final double VIEW_DISTANCE = 256;
    /** 运输船去别的星球时，起降各用多少刻。 */
    private static final double VESSEL_LIFT_TICKS = 200;
    /** 运输船冲上天的高度。 */
    private static final double VESSEL_LIFT_HEIGHT = 480;
    /** 太空里这个距离以外的运输船挪到这里画。 */
    private static final double SPACE_NEAR = 384;
    /** 太空里的运输船最远看得见多远。 */
    private static final double SPACE_VIEW = 24_000;
    /** 太空里远处的运输船至少画多大（挪到 {@link #SPACE_NEAR} 以后的缩放）。 */
    private static final float SPACE_MIN_SCALE = 1.2F;
    /** 太空里最多画多少艘远处的船。 */
    private static final int SPACE_MAX_FAR = 64;
    /** 跃迁的闪光：淡蓝。 */
    private static final int WARP_FLASH = 0xFFA8D8FF;

    private static List<LogisticsFlightsPayload.Flight> flights = List.of();
    private static long lastTick = Long.MIN_VALUE;

    private LogisticsClient() {
    }

    /** 要画的一架：位置（相对相机）、朝向、模型；stretch 是跃迁时沿机身拉长的倍数。 */
    private record Craft(double x, double y, double z, float yaw, float pitch, float scale, float stretch, int light,
            ItemStackRenderState model) {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class,
                event -> event.register(LogisticsFlightsPayload.TYPE, LogisticsClient::handle));
        gameBus.addListener(ExtractLevelRenderStateEvent.class, LogisticsClient::extract);
        gameBus.addListener(SubmitCustomGeometryEvent.class, LogisticsClient::submit);
        gameBus.addListener(ClientTickEvent.Post.class, event -> warpFlashes());
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            flights = List.of();
            lastTick = Long.MIN_VALUE;
        });
    }

    private static void handle(LogisticsFlightsPayload payload, IPayloadContext context) {
        flights = List.copyOf(payload.flights());
    }

    /** 某一时刻载具在哪：世界坐标和飞行方向（太空航线还有拉长的倍数）；看不见（在别的星球、跃迁里）时是 {@code null}。 */
    static double @Nullable [] position(LogisticsFlightsPayload.Flight flight, double now) {
        if (now < flight.departAt() || now > flight.returnAt()) {
            return null;
        }
        if (flight.route()) {
            return route(flight, now);
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

    /** 太空航线上的一段：{fromKind, toKind, 起点, 控制点, 终点, 看得见的时段}。 */
    private record Leg(int fromKind, int toKind, double[] from, double[] via, double[] to, double[] window) {
    }

    private static Leg leg(LogisticsFlightsPayload.Flight flight, boolean outbound) {
        double[] home = {flight.fromX(), flight.fromY(), flight.fromZ()};
        double[] other = {flight.toX(), flight.toY(), flight.toZ()};
        double[] via = {flight.viaX(), flight.viaY(), flight.viaZ()};
        int fromKind = outbound ? flight.homeKind() : flight.otherKind();
        int toKind = outbound ? flight.otherKind() : flight.homeKind();
        double[] window = outbound ? SpaceRoute.window(fromKind, toKind, flight.departAt(), flight.arriveAt())
                : SpaceRoute.window(fromKind, toKind, flight.arriveAt(), flight.returnAt());
        return new Leg(fromKind, toKind, outbound ? home : other, via, outbound ? other : home, window);
    }

    /** 太空航线：{x, y, z, yaw, pitch, stretch}；还在大气球里、在跃迁里时是 {@code null}。 */
    private static double @Nullable [] route(LogisticsFlightsPayload.Flight flight, double now) {
        Leg leg = leg(flight, now < flight.arriveAt());
        double start = leg.window[0];
        double end = leg.window[1];
        if (end <= start || now < start || now > end) {
            return null;
        }
        double s = SpaceRoute.ease(leg.fromKind, leg.toKind, (now - start) / (end - start));
        double[] p = SpaceRoute.point(leg.from, leg.via, leg.to, s);
        // 二次贝塞尔曲线的切线就是机头的方向
        double dx = (1 - s) * (leg.via[0] - leg.from[0]) + s * (leg.to[0] - leg.via[0]);
        double dy = (1 - s) * (leg.via[1] - leg.from[1]) + s * (leg.to[1] - leg.via[1]);
        double dz = (1 - s) * (leg.via[2] - leg.from[2]) + s * (leg.to[2] - leg.via[2]);
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        return new double[] {p[0], p[1], p[2], yaw, pitch, SpaceRoute.stretch(leg.fromKind, leg.toKind, start, end, now)};
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

    /** 每刻看一遍：这一刻里有运输船跃迁离开或跃迁进来，就在那里闪一下。 */
    private static void warpFlashes() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || flights.isEmpty()) {
            lastTick = Long.MIN_VALUE;
            return;
        }
        long now = level.getGameTime();
        long previous = lastTick;
        lastTick = now;
        if (previous == Long.MIN_VALUE || now <= previous || now - previous > 20) {
            return;
        }
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        for (LogisticsFlightsPayload.Flight flight : flights) {
            if (!flight.route()) {
                continue;
            }
            for (boolean outbound : new boolean[] {true, false}) {
                Leg leg = leg(flight, outbound);
                double at;
                if (leg.toKind == SpaceRoute.EDGE) {
                    at = leg.window[1];
                } else if (leg.fromKind == SpaceRoute.EDGE) {
                    at = leg.window[0];
                } else {
                    continue;
                }
                if (leg.window[1] <= leg.window[0] || at <= previous || at > now) {
                    continue;
                }
                double[] p = route(flight, at);
                if (p != null) {
                    flash(level, camera, p);
                }
            }
        }
    }

    /** 跃迁的闪光和沿航向甩出去的一串光点；离得远时挪到近处，和船的画法一样。 */
    private static void flash(ClientLevel level, Vec3 camera, double[] p) {
        double dx = p[0] - camera.x;
        double dy = p[1] - camera.y;
        double dz = p[2] - camera.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance > SPACE_VIEW) {
            return;
        }
        double k = distance > SPACE_NEAR ? SPACE_NEAR / distance : 1;
        double x = camera.x + dx * k;
        double y = camera.y + dy * k;
        double z = camera.z + dz * k;
        level.addAlwaysVisibleParticle(ColorParticleOption.create(ParticleTypes.FLASH, WARP_FLASH), true, x, y, z, 0, 0, 0);
        double yaw = Math.toRadians(p[3]);
        double pitch = Math.toRadians(p[4]);
        double hx = -Math.sin(yaw) * Math.cos(pitch);
        double hy = -Math.sin(pitch);
        double hz = Math.cos(yaw) * Math.cos(pitch);
        for (int i = 0; i < 12; i++) {
            double speed = (0.4 + i * 0.12) * Math.max(0.3, k);
            level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, true, x, y, z, hx * speed, hy * speed, hz * speed);
        }
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
        // 两种模型这一帧各解析一次，所有架次共用；随时间变的模型（动画贴图）才一架架解析
        ItemStackRenderState droneModel = null;
        ItemStackRenderState vesselModel = null;
        int seed = 0;
        int far = 0;
        for (LogisticsFlightsPayload.Flight flight : flights) {
            double[] p = position(flight, now);
            if (p == null) {
                continue;
            }
            double dx = p[0] - camera.x;
            double dy = p[1] - camera.y;
            double dz = p[2] - camera.z;
            double distanceSqr = dx * dx + dy * dy + dz * dz;
            float scale = flight.vessel() ? 3.0F : 1.6F;
            int light;
            if (flight.route()) {
                if (distanceSqr > SPACE_VIEW * SPACE_VIEW) {
                    continue;
                }
                double distance = Math.sqrt(distanceSqr);
                if (distance > SPACE_NEAR) {
                    if (far >= SPACE_MAX_FAR) {
                        continue;
                    }
                    far++;
                    // 方向不变挪到近处，按视大小缩小，但不小于一个亮点
                    double k = SPACE_NEAR / distance;
                    dx *= k;
                    dy *= k;
                    dz *= k;
                    scale = Math.max(SPACE_MIN_SCALE, (float) (scale * k));
                }
                // 太空里被恒星照着
                light = LightCoordsUtil.FULL_BRIGHT;
            } else {
                double limit = flight.vessel() ? VIEW_DISTANCE * 2 : VIEW_DISTANCE;
                if (distanceSqr > limit * limit) {
                    continue;
                }
                BlockPos at = BlockPos.containing(p[0], p[1], p[2]);
                light = LightCoordsUtil.pack(Math.max(9, level.getBrightness(LightLayer.BLOCK, at)), level.getBrightness(LightLayer.SKY, at));
            }
            ItemStackRenderState model = flight.vessel() ? vesselModel : droneModel;
            if (model == null) {
                model = new ItemStackRenderState();
                minecraft.getItemModelResolver().updateForTopItem(model, flight.vessel() ? vessel : drone, ItemDisplayContext.FIXED, level, null, seed);
                if (!model.isAnimated()) {
                    if (flight.vessel()) {
                        vesselModel = model;
                    } else {
                        droneModel = model;
                    }
                }
            }
            seed++;
            float stretch = p.length > 5 ? (float) p[5] : 1.0F;
            crafts.add(new Craft(dx, dy, dz, (float) p[3], (float) p[4], scale, stretch, light, model));
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
            // 机头沿 +z：跃迁时沿机身拉长
            pose.scale(craft.scale, craft.scale, craft.scale * craft.stretch);
            craft.model.submit(pose, event.getSubmitNodeCollector(), craft.light, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }
}
