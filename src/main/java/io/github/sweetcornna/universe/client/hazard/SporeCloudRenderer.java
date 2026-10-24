package io.github.sweetcornna.universe.client.hazard;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.hazard.SporeDrift;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.hazard.SporeCloud;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * 孢子云（ADR-0034 §六）：十几团朝着镜头的雾，按云的种子摆在半径 {@value SporeDrift#RADIUS} 格的一片里，
 * 慢慢翻滚、一团团此起彼伏。浓淡跟着云的生灭走；镜头钻进一团雾时那一团淡掉，免得糊满屏幕（这时画面另外发绿）。
 */
public class SporeCloudRenderer extends EntityRenderer<SporeCloud, SporeCloudRenderer.State> {
    private static final Identifier TEXTURE = Universe.id("textures/entity/hazard/spore_fog.png");
    private static final RenderType TYPE = RenderTypes.entityTranslucent(TEXTURE, false);
    private static final int PUFFS = 16;
    /** 雾团的颜色：从浅黄绿到孢子的黄绿。 */
    private static final int PALE = 0xC4D08A;

    public static class State extends EntityRenderState {
        float density;
        int seed;
    }

    public SporeCloudRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    protected AABB getBoundingBoxForCulling(SporeCloud entity, float partialTicks) {
        // 雾团伸出碰撞箱外面
        return super.getBoundingBoxForCulling(entity, partialTicks).inflate(3);
    }

    @Override
    public void extractRenderState(SporeCloud entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.density = (float) entity.density();
        state.seed = entity.seed();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        if (state.density > 0.01F) {
            float cx = (float) (camera.pos.x - state.x);
            float cy = (float) (camera.pos.y - state.y);
            float cz = (float) (camera.pos.z - state.z);
            Vector3f right = camera.orientation.transform(new Vector3f(1, 0, 0));
            Vector3f up = camera.orientation.transform(new Vector3f(0, 1, 0));
            float density = state.density;
            int seed = state.seed;
            float time = state.ageInTicks;
            int light = state.lightCoords;
            submitNodeCollector.submitCustomGeometry(poseStack, TYPE,
                    (pose, buffer) -> puffs(pose, buffer, seed, time, density, cx, cy, cz, right, up, light));
        }
        super.submit(state, poseStack, submitNodeCollector, camera);
    }

    private static void puffs(PoseStack.Pose pose, VertexConsumer buffer, int seed, float time, float density, float cx, float cy, float cz,
            Vector3f right, Vector3f up, int light) {
        Rng rng = Rng.of(seed);
        for (int i = 0; i < PUFFS; i++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double radius = Math.sqrt(rng.nextDouble()) * SporeDrift.RADIUS * 0.8;
            float height = (float) rng.range(-0.8, 2.4);
            float size = (float) rng.range(3.2, 5.6);
            float phase = (float) (rng.nextDouble() * Math.PI * 2);
            float roll = (float) (rng.nextDouble() * Math.PI * 2);
            float shade = (float) rng.nextDouble();
            // 翻滚：一半顺时针一半逆时针绕着慢慢转，上下起伏，一团团胀缩
            double turn = angle + time * (0.002 + 0.003 * shade) * (i % 2 == 0 ? 1 : -1);
            float x = (float) (Math.cos(turn) * radius);
            float z = (float) (Math.sin(turn) * radius);
            float y = height + 0.35F * Mth.sin(time * 0.025F + phase);
            float breathe = 1 + 0.12F * Mth.sin(time * 0.02F + phase * 1.7F);
            float half = size * (0.55F + 0.45F * density) * breathe / 2;
            float alpha = density * (0.32F + 0.22F * shade);
            float dx = x - cx;
            float dy = y - cy;
            float dz = z - cz;
            float distance = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            alpha *= Mth.clamp((distance - 0.8F) / (half * 1.6F), 0, 1);
            if (alpha <= 0.01F) {
                continue;
            }
            int rgb = lerp(PALE, SporeCloud.COLOR, shade);
            int color = (int) (alpha * 255) << 24 | rgb;
            float spin = roll + time * 0.004F * (i % 3 - 1);
            float cos = Mth.cos(spin) * half;
            float sin = Mth.sin(spin) * half;
            // 贴片的两条边：镜头的右、上方向转一个角度
            float ax = right.x() * cos + up.x() * sin;
            float ay = right.y() * cos + up.y() * sin;
            float az = right.z() * cos + up.z() * sin;
            float bx = -right.x() * sin + up.x() * cos;
            float by = -right.y() * sin + up.y() * cos;
            float bz = -right.z() * sin + up.z() * cos;
            vertex(buffer, pose, x - ax - bx, y - ay - by, z - az - bz, 0, 1, color, light);
            vertex(buffer, pose, x + ax - bx, y + ay - by, z + az - bz, 1, 1, color, light);
            vertex(buffer, pose, x + ax + bx, y + ay + by, z + az + bz, 1, 0, color, light);
            vertex(buffer, pose, x - ax + bx, y - ay + by, z - az + bz, 0, 0, color, light);
        }
    }

    private static int lerp(int a, int b, float t) {
        int r = (int) Mth.lerp(t, a >> 16 & 0xFF, b >> 16 & 0xFF);
        int g = (int) Mth.lerp(t, a >> 8 & 0xFF, b >> 8 & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return r << 16 | g << 8 | bl;
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float u, float v, int color, int light) {
        buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0, 1, 0);
    }
}
