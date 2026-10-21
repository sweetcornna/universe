package io.github.sweetcornna.universe.client.sky;

import java.util.Locale;

import org.joml.Matrix4fc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * 太空里的尘埃（ADR-0021）：摄像机周围一个 {@value #CELL} 格的立方体里散着几百颗看不见的尘粒，立方体首尾相接地铺满空间，
 * 摄像机走到哪里都在中间。飞起来以后，每颗尘粒按摄像机的速度拉成一道细光，开脉冲引擎时变成满屏往后掠的流光，
 * 远处的星球几乎不动，只有它们看得出飞得多快。停着不动时看不见。
 */
public final class SpaceDust {
    private static final int COUNT = 360;
    private static final float CELL = 72;
    /** 速度（格/刻）低于这个看不见。 */
    private static final double MIN_SPEED = 0.25;
    private static final float[] OFFSETS = new float[COUNT * 3];
    private static Vec3 velocity = Vec3.ZERO;
    /** 上一帧画了几道，验收脚本的日志里看。 */
    private static int drawn;

    static {
        RandomSource random = RandomSource.create(20261021L);
        for (int i = 0; i < OFFSETS.length; i++) {
            OFFSETS[i] = random.nextFloat() * CELL;
        }
    }

    private SpaceDust() {
    }

    static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ClientTickEvent.Post.class, event -> tick());
        gameBus.addListener(SubmitCustomGeometryEvent.class, SpaceDust::submit);
    }

    private static boolean inSpace(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        return level != null && SpaceDimensions.isSpace(level.dimension());
    }

    /** 每刻量一次摄像机跟着的实体（坐着飞船就是飞船）这一刻走了多远，平滑一下；一刻挪了几百格是传送，不算。 */
    private static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = minecraft.gameRenderer.mainCamera().entity();
        if (!inSpace(minecraft) || entity == null) {
            velocity = Vec3.ZERO;
            return;
        }
        Entity root = entity.getRootVehicle();
        Vec3 step = new Vec3(root.getX() - root.xo, root.getY() - root.yo, root.getZ() - root.zo);
        if (step.lengthSqr() < 300 * 300) {
            velocity = velocity.lerp(step, 0.5);
        }
    }

    public static String stats() {
        return String.format(Locale.ROOT, "dust %d at %.1f b/t", drawn, velocity.length());
    }

    private static void submit(SubmitCustomGeometryEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        drawn = 0;
        double speed = velocity.length();
        if (!inSpace(minecraft) || speed < MIN_SPEED) {
            return;
        }
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        float dx = (float) (velocity.x / speed);
        float dy = (float) (velocity.y / speed);
        float dz = (float) (velocity.z / speed);
        // 拖影大约是一刻半里走过的路，太长了就截住
        float length = (float) Mth.clamp(speed * 1.5, 0.4, 30);
        float strength = (float) Mth.clamp((speed - MIN_SPEED) / 3.0, 0, 1);
        float half = CELL / 2;
        PoseStack pose = event.getPoseStack();
        event.getSubmitNodeCollector().submitCustomGeometry(pose, RenderTypes.lightning(), (p, buffer) -> {
            Matrix4fc matrix = p.pose();
            for (int i = 0; i < COUNT; i++) {
                // 离摄像机最近的那一份：每个分量落在 [-CELL/2, CELL/2)
                float rx = wrap(OFFSETS[i * 3] - (float) (camera.x % CELL), half);
                float ry = wrap(OFFSETS[i * 3 + 1] - (float) (camera.y % CELL), half);
                float rz = wrap(OFFSETS[i * 3 + 2] - (float) (camera.z % CELL), half);
                float distance = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
                // 太近的会糊在镜头上，太远的贴着立方体的边会突然出现
                float fade = Mth.clamp((distance - 2.5F) / 8.0F, 0, 1) * Mth.clamp((half - distance) / (half * 0.35F), 0, 1);
                if (fade <= 0.01F) {
                    continue;
                }
                streak(buffer, matrix, rx, ry, rz, dx, dy, dz, length, 0.03F + distance * 0.002F, (int) (150 * strength * fade));
                drawn++;
            }
        });
    }

    private static float wrap(float value, float half) {
        float size = half * 2;
        float v = value % size;
        if (v < -half) {
            v += size;
        } else if (v >= half) {
            v -= size;
        }
        return v;
    }

    /** 从尘粒往速度方向拖一道光：尘粒那头最亮，尾巴淡到 0，宽度方向垂直于视线和速度。顶点按摄像机看过去逆时针排，不然被背面剔除。 */
    private static void streak(VertexConsumer buffer, Matrix4fc matrix, float x, float y, float z, float dx, float dy, float dz, float length,
            float width, int alpha) {
        if (alpha <= 0) {
            return;
        }
        // 宽度方向 = 速度 × 视线（视线就是摄像机到尘粒的方向）
        float sx = dy * z - dz * y;
        float sy = dz * x - dx * z;
        float sz = dx * y - dy * x;
        float s = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (s < 1e-4F) {
            return;
        }
        sx *= width / s;
        sy *= width / s;
        sz *= width / s;
        float tx = x + dx * length;
        float ty = y + dy * length;
        float tz = z + dz * length;
        buffer.addVertex(matrix, x + sx, y + sy, z + sz).setColor(210, 225, 255, alpha);
        buffer.addVertex(matrix, x - sx, y - sy, z - sz).setColor(210, 225, 255, alpha);
        buffer.addVertex(matrix, tx - sx, ty - sy, tz - sz).setColor(170, 195, 255, 0);
        buffer.addVertex(matrix, tx + sx, ty + sy, tz + sz).setColor(170, 195, 255, 0);
    }
}
