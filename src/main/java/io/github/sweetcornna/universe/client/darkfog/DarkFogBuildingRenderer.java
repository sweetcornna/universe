package io.github.sweetcornna.universe.client.darkfog;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.darkfog.BuildingKind;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogBuilding;
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
 * 黑雾建筑的渲染器：每种建筑一个模型（三种营地共用一个，发光颜色不同），按种类换模型、放大。发光层有能量时一明一暗，
 * 没能量时暗下去。激光塔烧着目标时从透镜画一道光束过去，中继站有能量时往下连一道光柱到钻芯。建筑不会像生物那样倒下。
 */
public class DarkFogBuildingRenderer extends MobRenderer<DarkFogBuilding, DarkFogBuildingRenderState, DarkFogBuildingModel> {
    private static final int OFF = 0x40202C;
    private static final int LASER = 0xFF3A5A;
    private static final int RELAY_BEAM = 0xB040FF;
    /** 中继站往下找钻芯找多远。 */
    private static final double RELAY_REACH = 56;
    private final Map<BuildingKind, Look> looks = new EnumMap<>(BuildingKind.class);

    /** 一种建筑的模型、贴图、发光层、发光颜色和阴影。 */
    private record Look(DarkFogBuildingModel model, Identifier texture, Identifier glow, int color, float shadow) {
    }

    public DarkFogBuildingRenderer(EntityRendererProvider.Context context) {
        super(context, DarkFogBuildingModel.core(), 2.0F);
        DarkFogBuildingModel camp = DarkFogBuildingModel.camp();
        this.looks.put(BuildingKind.CORE, look(this.model, "dark_fog_core", 0xFF2A48, 2.2F));
        this.looks.put(BuildingKind.RAIDER_CAMP, look(camp, "dark_fog_camp", 0xFF2A48, 1.6F));
        this.looks.put(BuildingKind.RANGER_CAMP, look(camp, "dark_fog_camp", 0xFF8A2A, 1.6F));
        this.looks.put(BuildingKind.GUARDIAN_CAMP, look(camp, "dark_fog_camp", 0xC040FF, 1.6F));
        this.looks.put(BuildingKind.LASER_TOWER, look(DarkFogBuildingModel.laserTower(), "dark_fog_laser_tower", 0xFF3A5A, 0.8F));
        this.looks.put(BuildingKind.PLASMA_SENTRY, look(DarkFogBuildingModel.plasmaSentry(), "dark_fog_plasma_sentry", 0xFF7A2A, 1.1F));
        this.looks.put(BuildingKind.RELAY, look(DarkFogBuildingModel.relay(), "dark_fog_relay", 0xC040FF, 0));
        this.addLayer(new GlowLayer(this));
    }

    private static Look look(DarkFogBuildingModel model, String name, int color, float shadow) {
        return new Look(model, Universe.id("textures/entity/" + name + ".png"), Universe.id("textures/entity/" + name + "_glow.png"), color, shadow);
    }

    private Look look(DarkFogBuildingRenderState state) {
        return this.looks.get(state.kind);
    }

    @Override
    public DarkFogBuildingRenderState createRenderState() {
        return new DarkFogBuildingRenderState();
    }

    @Override
    public void extractRenderState(DarkFogBuilding entity, DarkFogBuildingRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.kind = entity.kind();
        state.powered = entity.powered();
        state.phase = (entity.getId() * 37 % 100) / 100F * Mth.TWO_PI;
        // 不像生物那样倒下，也不闪红
        state.deathTime = 0;
        Vec3 origin = entity.getPosition(partialTicks);
        Vec3 aim = aim(entity, partialTicks);
        state.headYaw = 0;
        if (aim != null) {
            Vec3 d = aim.subtract(origin);
            float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90;
            state.headYaw = Mth.wrapDegrees(yaw - entity.getYRot()) * Mth.DEG_TO_RAD;
        }
        state.beam = beam(entity, origin, aim, partialTicks);
    }

    /** 炮塔瞄着的地方。 */
    private static @Nullable Vec3 aim(DarkFogBuilding entity, float partialTicks) {
        int id = entity.aimedEntity();
        if (id >= 0) {
            Entity target = entity.level().getEntity(id);
            if (target != null) {
                return target.getPosition(partialTicks).add(0, target.getBbHeight() * 0.6, 0);
            }
        }
        return entity.aimedBlock().map(Vec3::atCenterOf).orElse(null);
    }

    /** 光束的终点，相对于建筑的位置。 */
    private static @Nullable Vec3 beam(DarkFogBuilding entity, Vec3 origin, @Nullable Vec3 aim, float partialTicks) {
        if (!entity.powered()) {
            return null;
        }
        if (entity.kind() == BuildingKind.LASER_TOWER) {
            return aim == null ? null : aim.subtract(origin);
        }
        if (entity.kind() == BuildingKind.RELAY) {
            AABB below = new AABB(origin.x - 6, origin.y - RELAY_REACH, origin.z - 6, origin.x + 6, origin.y, origin.z + 6);
            return entity.level().getEntitiesOfClass(DarkFogBuilding.class, below, b -> b.kind() == BuildingKind.CORE && b.isAlive()).stream()
                    .min(Comparator.comparingDouble(b -> b.distanceToSqr(entity)))
                    .map(core -> core.getPosition(partialTicks).add(0, core.getBbHeight(), 0).subtract(origin))
                    .orElse(null);
        }
        return null;
    }

    @Override
    protected AABB getBoundingBoxForCulling(DarkFogBuilding entity, float partialTicks) {
        AABB box = super.getBoundingBoxForCulling(entity, partialTicks);
        if (entity.kind() == BuildingKind.RELAY) {
            return box.expandTowards(0, -RELAY_REACH, 0);
        }
        Vec3 aim = aim(entity, partialTicks);
        return aim == null ? box : box.minmax(new AABB(aim, aim));
    }

    @Override
    public void submit(DarkFogBuildingRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        this.model = this.look(state).model();
        super.submit(state, poseStack, collector, camera);
        if (state.beam != null) {
            Vec3 origin = new Vec3(state.x, state.y, state.z);
            Vec3 eye = camera.pos.subtract(origin);
            if (state.kind == BuildingKind.LASER_TOWER) {
                float flicker = 0.9F + 0.1F * Mth.sin(state.ageInTicks * 1.7F);
                DarkFogBeams.submit(poseStack, collector, new Vec3(0, 3.6, 0), state.beam, eye, DarkFogUnitRenderer.scale(LASER, flicker),
                        0.22F, false, state.ageInTicks);
            } else {
                float pulse = 0.7F + 0.3F * Mth.sin(state.ageInTicks * 0.2F + state.phase);
                DarkFogBeams.submit(poseStack, collector, new Vec3(0, 0.3, 0), state.beam, eye, DarkFogUnitRenderer.scale(RELAY_BEAM, pulse),
                        0.5F, false, state.ageInTicks);
            }
        }
    }

    @Override
    protected void scale(DarkFogBuildingRenderState state, PoseStack poseStack) {
        float scale = this.look(state).model().scale();
        poseStack.scale(scale, scale, scale);
    }

    @Override
    protected float getFlipDegrees() {
        return 0;
    }

    @Override
    public Identifier getTextureLocation(DarkFogBuildingRenderState state) {
        return this.look(state).texture();
    }

    @Override
    protected float getShadowRadius(DarkFogBuildingRenderState state) {
        return this.look(state).shadow();
    }

    @Override
    protected int getBlockLightLevel(DarkFogBuilding entity, BlockPos pos) {
        return Math.max(5, super.getBlockLightLevel(entity, pos));
    }

    private static final class GlowLayer extends RenderLayer<DarkFogBuildingRenderState, DarkFogBuildingModel> {
        private final DarkFogBuildingRenderer renderer;

        GlowLayer(DarkFogBuildingRenderer renderer) {
            super(renderer);
            this.renderer = renderer;
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, DarkFogBuildingRenderState state, float yRot,
                float xRot) {
            if (state.isInvisible) {
                return;
            }
            Look look = this.renderer.look(state);
            int color = state.powered ? DarkFogUnitRenderer.scale(look.color(), 0.75F + 0.25F * Mth.sin(state.ageInTicks * 0.1F + state.phase)) : OFF;
            collector.order(1).submitModel(this.getParentModel(), state, poseStack, RenderTypes.eyes(look.glow()),
                    lightCoords, OverlayTexture.NO_OVERLAY, 0xFF000000 | color, null, state.outlineColor);
        }
    }
}
