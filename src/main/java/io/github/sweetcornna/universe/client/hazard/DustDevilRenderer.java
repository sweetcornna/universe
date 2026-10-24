package io.github.sweetcornna.universe.client.hazard;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.hazard.Vortex;
import io.github.sweetcornna.universe.hazard.DustDevil;
import io.github.sweetcornna.universe.hazard.HazardLook;
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
 * 尘卷风（ADR-0034 §六）：几层套着的、越往上越粗的半透明尘筒，贴图是一道道斜着的尘纹，绕着转、往上走，
 * 外层慢里层快。柱身像蛇一样微微扭着；底下最浓，往上淡到看不见。颜色是这颗星球的沙土色。
 * 起风时柱子从地面往上长、停风时变细变淡。转着的尘和脚下卷起的沙土是实体自己撒的粒子。
 */
public class DustDevilRenderer extends EntityRenderer<DustDevil, DustDevilRenderer.State> {
    private static final Identifier TEXTURE = Universe.id("textures/entity/hazard/dust_devil.png");
    private static final RenderType TYPE = RenderTypes.entityTranslucent(TEXTURE, false);
    private static final int SEGMENTS = 20;
    private static final int RINGS = 12;
    /** 每层：半径的比例、转的快慢（贴图每刻挪多少圈）、往上走的快慢、浓淡、颜色往白提多少。 */
    private static final float[][] LAYERS = {
            {1.0F, 0.020F, 0.020F, 0.34F, 0.0F},
            {0.72F, -0.032F, 0.030F, 0.40F, 0.10F},
            {0.42F, 0.050F, 0.045F, 0.46F, 0.18F},
    };

    public static class State extends EntityRenderState {
        float strength;
        int sand = 0xC8A878;
        int id;
    }

    public DustDevilRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    protected AABB getBoundingBoxForCulling(DustDevil entity, float partialTicks) {
        // 柱子顶上比碰撞箱粗
        return super.getBoundingBoxForCulling(entity, partialTicks).inflate(2, 0.5, 2);
    }

    @Override
    public void extractRenderState(DustDevil entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.strength = (float) entity.strength();
        state.sand = HazardLook.sand(entity.level());
        state.id = entity.getId();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        if (state.strength > 0.01F) {
            float strength = state.strength;
            float time = state.ageInTicks;
            int sand = state.sand;
            int light = state.lightCoords;
            float phase = state.id * 1.37F;
            submitNodeCollector.submitCustomGeometry(poseStack, TYPE, (pose, buffer) -> {
                for (float[] layer : LAYERS) {
                    funnel(pose, buffer, layer, strength, time, phase, sand, light);
                }
            });
        }
        super.submit(state, poseStack, submitNodeCollector, camera);
    }

    private static void funnel(PoseStack.Pose pose, VertexConsumer buffer, float[] layer, float strength, float time, float phase, int sand,
            int light) {
        // 起风时从地面长上去
        float top = (float) Vortex.HEIGHT * Mth.clamp(0.25F + strength * 0.9F, 0, 1);
        float spin = time * layer[1];
        float rise = time * layer[2];
        int rgb = lighten(sand, layer[4]);
        float[] xs = new float[RINGS + 1];
        float[] ys = new float[RINGS + 1];
        float[] zs = new float[RINGS + 1];
        float[] rs = new float[RINGS + 1];
        int[] alphas = new int[RINGS + 1];
        for (int j = 0; j <= RINGS; j++) {
            float t = j / (float) RINGS;
            float h = t * top;
            // 柱身扭着：越往上摆得越开
            float sway = 0.45F * t;
            xs[j] = sway * Mth.sin(time * 0.045F + h * 0.32F + phase);
            zs[j] = sway * Mth.cos(time * 0.038F + h * 0.27F + phase);
            ys[j] = h;
            rs[j] = (float) Vortex.radiusAt(h) * layer[0] * (0.5F + 0.5F * strength);
            // 底下最浓，贴地那一圈稍淡（和地面接得自然），往上淡到没有
            float profile = Mth.clamp(t * 5 + 0.45F, 0, 1) * (float) Math.pow(1 - t, 1.4);
            alphas[j] = (int) (255 * Mth.clamp(layer[3] * strength * profile, 0, 1));
        }
        for (int j = 0; j < RINGS; j++) {
            if (alphas[j] == 0 && alphas[j + 1] == 0) {
                continue;
            }
            float v0 = j / (float) RINGS * 1.5F - rise;
            float v1 = (j + 1) / (float) RINGS * 1.5F - rise;
            for (int k = 0; k < SEGMENTS; k++) {
                float a0 = k / (float) SEGMENTS * Mth.TWO_PI;
                float a1 = (k + 1) / (float) SEGMENTS * Mth.TWO_PI;
                // 贴图绕一圈铺两遍，越往上越拧
                float u0 = k / (float) SEGMENTS * 2 + spin;
                float u1 = (k + 1) / (float) SEGMENTS * 2 + spin;
                float twist0 = j * 0.05F;
                float twist1 = (j + 1) * 0.05F;
                vertex(buffer, pose, xs[j] + Mth.cos(a0) * rs[j], ys[j], zs[j] + Mth.sin(a0) * rs[j], u0 + twist0, v0, alphas[j] << 24 | rgb,
                        light);
                vertex(buffer, pose, xs[j] + Mth.cos(a1) * rs[j], ys[j], zs[j] + Mth.sin(a1) * rs[j], u1 + twist0, v0, alphas[j] << 24 | rgb,
                        light);
                vertex(buffer, pose, xs[j + 1] + Mth.cos(a1) * rs[j + 1], ys[j + 1], zs[j + 1] + Mth.sin(a1) * rs[j + 1], u1 + twist1, v1,
                        alphas[j + 1] << 24 | rgb, light);
                vertex(buffer, pose, xs[j + 1] + Mth.cos(a0) * rs[j + 1], ys[j + 1], zs[j + 1] + Mth.sin(a0) * rs[j + 1], u0 + twist1, v1,
                        alphas[j + 1] << 24 | rgb, light);
            }
        }
    }

    /** 往白里提一点（里层的尘被光透着，亮一些）。 */
    private static int lighten(int rgb, float k) {
        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        return (int) Mth.lerp(k, r, 255) << 16 | (int) Mth.lerp(k, g, 255) << 8 | (int) Mth.lerp(k, b, 255);
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
