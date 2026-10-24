package io.github.sweetcornna.universe.client.fishing;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.fishing.FishTable;
import io.github.sweetcornna.universe.fishing.FishingBobber;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 钓鱼器的浮标（ADR-0034 §一）：一张朝着镜头的浮标贴图，一根从右手连过来的线，鱼影游过来时水下画一条半透明的鱼。
 *
 * <ul>
 *   <li>线：等鱼时照原版垂成弧线；收线时张力越大越直，颜色从浅灰变到红。</li>
 *   <li>鱼影：用那种鱼的物品图标平躺在水面下半格，按这里的水色压暗，从 3 格外朝浮标游过来，越近越清楚；
 *       不是鱼时画一团模糊的影子。</li>
 * </ul>
 */
public class FishingBobberRenderer extends EntityRenderer<FishingBobber, FishingBobberRenderer.State> {
    private static final Identifier TEXTURE = Universe.id("textures/entity/fishing_bobber.png");
    private static final Identifier JUNK_SHADOW = Universe.id("textures/entity/fishing_shadow.png");
    private static final RenderType RENDER_TYPE = RenderTypes.entityCutout(TEXTURE);
    private static final double VIEW_BOBBING_SCALE = 960.0;
    /** 鱼影从多远游过来（格）。 */
    private static final float SHADOW_DISTANCE = 3F;

    public static class State extends EntityRenderState {
        Vec3 lineOffset = Vec3.ZERO;
        float tension;
        boolean hooked;
        int shadow = -1;
        /** 鱼影游了多少（0 刚出现，1 到了浮标下面）。 */
        float shadowProgress;
        float shadowAngle;
        int waterColor = 0x3F76E4;
        @Nullable Identifier shadowTexture;
        float shadowScale = 1;
    }

    public FishingBobberRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(FishingBobber entity, Frustum culler, double camX, double camY, double camZ, float partialTicks) {
        return super.shouldRender(entity, culler, camX, camY, camZ, partialTicks) && entity.playerOwner() != null;
    }

    @Override
    protected boolean affectedByCulling(FishingBobber entity) {
        return false;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(FishingBobber entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        Player owner = entity.playerOwner();
        if (owner == null) {
            state.lineOffset = Vec3.ZERO;
        } else {
            float swing = Mth.sin(Mth.sqrt(owner.getSwingAnimation(partialTicks)) * (float) Math.PI);
            Vec3 hand = this.handPos(owner, swing, partialTicks);
            state.lineOffset = hand.subtract(entity.getPosition(partialTicks).add(0, 0.25, 0));
        }
        FishingBobber.Phase phase = entity.phase();
        state.hooked = phase == FishingBobber.Phase.HOOKED;
        state.tension = state.hooked ? entity.displayTension(partialTicks) : 0;
        state.shadow = phase == FishingBobber.Phase.APPROACH || phase == FishingBobber.Phase.BITE ? entity.shadow() : -1;
        if (state.shadow != -1) {
            int total = Math.max(1, entity.approachTicks());
            state.shadowProgress = phase == FishingBobber.Phase.BITE ? 1 : Math.min(1, (entity.phaseAge() + partialTicks) / total);
            state.shadowAngle = entity.shadowAngle();
            state.shadowTexture = FishTable.byIndex(state.shadow)
                    .map(species -> Universe.id("textures/item/" + species.itemId() + ".png")).orElse(JUNK_SHADOW);
            state.shadowScale = FishTable.byIndex(state.shadow).map(species -> 0.7F + 0.25F * species.size().ordinal()).orElse(0.9F);
            if (Minecraft.getInstance().level != null) {
                state.waterColor = BiomeColors.getAverageWaterColor(Minecraft.getInstance().level, BlockPos.containing(entity.position()));
            }
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.shadow != -1 && state.shadowTexture != null) {
            this.submitShadow(state, poseStack, collector);
        }
        poseStack.pushPose();
        poseStack.pushPose();
        poseStack.scale(0.5F, 0.5F, 0.5F);
        poseStack.rotate(camera.orientation);
        collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> {
            vertex(buffer, pose, state.lightCoords, 0, 0, 0, 1, -1);
            vertex(buffer, pose, state.lightCoords, 1, 0, 1, 1, -1);
            vertex(buffer, pose, state.lightCoords, 1, 1, 1, 0, -1);
            vertex(buffer, pose, state.lightCoords, 0, 1, 0, 0, -1);
        });
        poseStack.popPose();
        float xa = (float) state.lineOffset.x;
        float ya = (float) state.lineOffset.y;
        float za = (float) state.lineOffset.z;
        float width = Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;
        // 张力越大线越直；颜色从浅灰变红
        float sag = 1 - Math.min(1, state.tension * 1.2F);
        int color = state.hooked ? ARGB.srgbLerp(Math.min(1, state.tension), 0xFFC8D4D8, 0xFFE0402A) : 0xFFB8C4C8;
        collector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
            int steps = 16;
            for (int i = 0; i < steps; i++) {
                float a0 = i / (float) steps;
                float a1 = (i + 1) / (float) steps;
                line(xa, ya, za, sag, buffer, pose, a0, a1, width, color);
                line(xa, ya, za, sag, buffer, pose, a1, a0, width, color);
            }
        });
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    /** 水下的鱼影：平躺在水面下半格，头朝浮标。 */
    private void submitShadow(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        float distance = SHADOW_DISTANCE * (1 - state.shadowProgress);
        float angle = state.shadowAngle;
        // 游的方向：朝浮标；加一点左右摆
        float wiggle = Mth.sin((state.ageInTicks) * 0.6F) * 0.12F;
        float heading = (float) Math.atan2(-Mth.cos(angle), -Mth.sin(angle)) + wiggle;
        float alpha = 0.25F + 0.45F * state.shadowProgress;
        int tint = ARGB.color(Math.round(alpha * 255), ARGB.scaleRGB(state.waterColor, 0.25F));
        RenderType type = RenderTypes.entityTranslucent(state.shadowTexture);
        poseStack.pushPose();
        poseStack.translate(Mth.sin(angle) * distance, -0.45, Mth.cos(angle) * distance);
        poseStack.rotate(Axis.YP.rotation(heading));
        poseStack.rotate(Axis.XP.rotationDegrees(-90));
        float s = state.shadowScale;
        poseStack.scale(s, s, s);
        collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
            vertex(buffer, pose, state.lightCoords, 0, 0, 0, 1, tint);
            vertex(buffer, pose, state.lightCoords, 1, 0, 1, 1, tint);
            vertex(buffer, pose, state.lightCoords, 1, 1, 1, 0, tint);
            vertex(buffer, pose, state.lightCoords, 0, 1, 0, 0, tint);
        });
        poseStack.popPose();
    }

    /** 线从右手（第一人称时从屏幕右下角）出来，照原版钓竿。 */
    private Vec3 handPos(Player owner, float swing, float partialTicks) {
        int invert = owner.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        if (this.entityRenderDispatcher.options.getCameraType().isFirstPerson() && owner == Minecraft.getInstance().player) {
            float fov = this.entityRenderDispatcher.options.fov().get().intValue();
            double scale = VIEW_BOBBING_SCALE / fov;
            Vec3 view = this.entityRenderDispatcher.camera.getNearPlane(fov).getPointOnPlane(invert * 0.525F, -0.1F).scale(scale)
                    .yRot(swing * 0.5F).xRot(-swing * 0.7F);
            return owner.getEyePosition(partialTicks).add(view);
        }
        float yaw = Mth.lerp(partialTicks, owner.yBodyRotO, owner.yBodyRot) * (float) (Math.PI / 180.0);
        double sin = Mth.sin(yaw);
        double cos = Mth.cos(yaw);
        float playerScale = owner.getScale();
        double right = invert * 0.35 * playerScale;
        double forward = 0.8 * playerScale;
        float crouch = owner.isCrouching() ? -0.1875F : 0.0F;
        return owner.getEyePosition(partialTicks).add(-cos * right - sin * forward, crouch - 0.45 * playerScale, -sin * right + cos * forward);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, int light, float x, int y, int u, int v, int color) {
        buffer.addVertex(pose, x - 0.5F, y - 0.5F, 0)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0, 1, 0);
    }

    /** 线上的一段：{@code sag} 为 1 时照原版垂成弧，为 0 时拉直。 */
    private static void line(float xa, float ya, float za, float sag, VertexConsumer buffer, PoseStack.Pose pose, float a, float next, float width,
            int color) {
        float x = xa * a;
        float y = y(ya, a, sag);
        float z = za * a;
        float nx = xa * next - x;
        float ny = y(ya, next, sag) - y;
        float nz = za * next - z;
        float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= length;
        ny /= length;
        nz /= length;
        buffer.addVertex(pose, x, y, z).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(width);
    }

    private static float y(float ya, float a, float sag) {
        float curved = ya * (a * a + a) * 0.5F;
        float straight = ya * a;
        return Mth.lerp(sag, straight, curved) + 0.25F;
    }
}
