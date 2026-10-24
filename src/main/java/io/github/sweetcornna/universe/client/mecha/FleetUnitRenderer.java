package io.github.sweetcornna.universe.client.mecha;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.mecha.fleet.FleetUnitKind;
import io.github.sweetcornna.universe.mecha.fleet.FleetUnit;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 舰队单位的渲染器（ADR-0033 §一）：五种单位各一个模型和贴图；发光层是推进器、灯和炮口，按青色着色，开火时亮一下。
 * 激光单位开火时从机头画一道光到目标，驱逐舰的点防御是一道琥珀色的短光。被打掉时打着转炸开。
 */
public class FleetUnitRenderer extends MobRenderer<FleetUnit, FleetUnitRenderState, FleetUnitModel> {
    static final int GLOW = 0x8FE8FF;
    private final FleetUnitKind kind;
    private final Identifier texture;
    private final Identifier glow;
    private final float modelScale;

    private FleetUnitRenderer(EntityRendererProvider.Context context, FleetUnitKind kind) {
        super(context, FleetUnitModel.of(kind), kind.space() ? 0 : 0.25F);
        this.kind = kind;
        this.texture = Universe.id("textures/entity/fleet_" + kind.id() + ".png");
        this.glow = Universe.id("textures/entity/fleet_" + kind.id() + "_glow.png");
        // 驱逐舰按一半的尺寸建模
        this.modelScale = kind == FleetUnitKind.DESTROYER ? 2 : 1;
        this.addLayer(new GlowLayer(this));
    }

    public static EntityRendererProvider<FleetUnit> provider(FleetUnitKind kind) {
        return context -> new FleetUnitRenderer(context, kind);
    }

    @Override
    public FleetUnitRenderState createRenderState() {
        return new FleetUnitRenderState();
    }

    @Override
    public void extractRenderState(FleetUnit entity, FleetUnitRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.kind = this.kind;
        state.phase = (entity.getId() * 37 % 100) / 100F * Mth.TWO_PI;
        state.roll = entity.roll(partialTicks);
        Vec3 origin = entity.getPosition(partialTicks);
        Entity target = entity.beamTarget() >= 0 ? entity.level().getEntity(entity.beamTarget()) : null;
        state.beam = target == null ? null : aim(target, partialTicks).subtract(origin);
        state.beamFade = entity.beamTicks() / (float) FleetUnit.BEAM_TICKS;
        Entity pd = entity.pointDefenseTarget() >= 0 ? entity.level().getEntity(entity.pointDefenseTarget()) : null;
        state.pointDefense = pd == null ? null : aim(pd, partialTicks).subtract(origin);
        state.pointDefenseFade = entity.pointDefenseTicks() / (float) FleetUnit.BEAM_TICKS;
        // 炮塔转向最近开火的目标
        Entity aimed = target != null ? target : pd;
        state.aiming = aimed != null;
        if (aimed != null) {
            Vec3 to = aim(aimed, partialTicks).subtract(origin);
            float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90;
            state.aimYaw = Mth.wrapDegrees(yaw - state.bodyRot);
        }
    }

    private static Vec3 aim(Entity target, float partialTicks) {
        return target.getPosition(partialTicks).add(0, target.getBbHeight() * 0.5, 0);
    }

    @Override
    protected AABB getBoundingBoxForCulling(FleetUnit entity, float partialTicks) {
        AABB box = super.getBoundingBoxForCulling(entity, partialTicks);
        int id = entity.beamTarget() >= 0 ? entity.beamTarget() : entity.pointDefenseTarget();
        Entity target = id >= 0 ? entity.level().getEntity(id) : null;
        return target == null ? box : box.minmax(target.getBoundingBox());
    }

    @Override
    public void submit(FleetUnitRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        super.submit(state, poseStack, collector, camera);
        Vec3 origin = new Vec3(state.x, state.y, state.z);
        Vec3 eye = camera.pos.subtract(origin);
        Vec3 muzzle = muzzle(state);
        if (state.beam != null) {
            float width = this.kind == FleetUnitKind.CORVETTE ? 0.22F : 0.12F;
            FleetBeams.submit(poseStack, collector, muzzle, state.beam, eye, FleetUnit.laserColor(), width, state.beamFade);
        }
        if (state.pointDefense != null) {
            FleetBeams.submit(poseStack, collector, new Vec3(0, state.boundingBoxHeight, 0), state.pointDefense, eye, FleetUnit.pointDefenseColor(),
                    0.1F, state.pointDefenseFade);
        }
    }

    /** 炮口：机头前面、机身中间的高度（相对于单位的位置）。 */
    private static Vec3 muzzle(FleetUnitRenderState state) {
        float yaw = state.bodyRot * Mth.DEG_TO_RAD;
        double reach = state.boundingBoxWidth * 0.5;
        return new Vec3(-Mth.sin(yaw) * reach, state.boundingBoxHeight * 0.45, Mth.cos(yaw) * reach);
    }

    @Override
    protected void setupRotations(FleetUnitRenderState state, PoseStack poseStack, float bodyRot, float entityScale) {
        poseStack.rotateDegrees(Axis.YP, 180.0F - bodyRot);
        if (state.deathTime > 0) {
            float h = state.boundingBoxHeight / 2 / entityScale;
            poseStack.translate(0, h, 0);
            poseStack.rotateDegrees(Axis.ZP, state.deathTime * 40);
            poseStack.translate(0, -h, 0);
        }
    }

    @Override
    protected void scale(FleetUnitRenderState state, PoseStack poseStack) {
        if (this.modelScale != 1) {
            poseStack.scale(this.modelScale, this.modelScale, this.modelScale);
        }
    }

    @Override
    public Identifier getTextureLocation(FleetUnitRenderState state) {
        return this.texture;
    }

    @Override
    protected int getBlockLightLevel(FleetUnit entity, BlockPos pos) {
        return Math.max(7, super.getBlockLightLevel(entity, pos));
    }

    @Override
    protected boolean shouldShowName(FleetUnit entity, double distanceSquared) {
        return false;
    }

    private static final class GlowLayer extends RenderLayer<FleetUnitRenderState, FleetUnitModel> {
        private final FleetUnitRenderer renderer;

        GlowLayer(FleetUnitRenderer renderer) {
            super(renderer);
            this.renderer = renderer;
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, FleetUnitRenderState state, float yRot, float xRot) {
            if (state.isInvisible) {
                return;
            }
            float fired = Math.max(state.beamFade, state.pointDefenseFade);
            float pulse = 0.7F + 0.1F * Mth.sin(state.ageInTicks * 0.4F + state.phase) + 0.25F * fired;
            collector.order(1).submitModel(this.getParentModel(), state, poseStack, RenderTypes.eyes(this.renderer.glow), lightCoords,
                    OverlayTexture.NO_OVERLAY, 0xFF000000 | FleetBeams.scale(GLOW, pulse), null, state.outlineColor);
        }
    }
}
