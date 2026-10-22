package io.github.sweetcornna.universe.client.darkfog.hive;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.darkfog.HiveBuildingKind;
import io.github.sweetcornna.universe.core.darkfog.HiveLayout;
import io.github.sweetcornna.universe.darkfog.hive.HiveBuilding;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 太空巢穴建筑的渲染器：每种建筑一个模型，按种类换模型、放大。发光层有能量时一明一暗，没能量时暗下去。
 * 炮塔的炮头转向瞄着的目标；光子接收器的盘朝着恒星（太空维度的原点），有能量时从发射杆往恒星那边伸出一道细光束。
 * 准星对着的建筑在头顶写名字和等级（太空里隔得远，按视线判断，不按原版的够得着才算）。
 * 核心往每座建筑拉一根发光的系索，亮点顺着往外流，远远看去是连成一片的一座巢穴。
 */
public class HiveBuildingRenderer extends MobRenderer<HiveBuilding, HiveBuildingRenderState, HiveBuildingModel> {
    private static final int OFF = 0x302028;
    private static final int RECEIVER_BEAM = 0xB060FF;
    /** 光束伸出去多长。 */
    private static final double BEAM_LENGTH = 48;
    /** 接收盘的中心离底下多高、发射杆的头离盘多远（格）。 */
    private static final double DISH_HEIGHT = 14 * 4 / 16.0;
    private static final double DISH_TIP = 11 * 4 / 16.0;
    /** 名字最远在多远显示、视线偏开多少还算对着（余弦）。 */
    private static final double NAME_RANGE = 96;
    private static final double NAME_CONE = Math.cos(Math.toRadians(5));
    /** 系索：在核心周围多远以内找建筑、隔多少刻重新找一次、核心那头离底下多高、带子多宽。 */
    private static final double TETHER_RANGE = HiveLayout.RADIUS + 8;
    private static final int TETHER_REFRESH = 40;
    private static final double TETHER_HUB = 3.5;
    private static final float TETHER_WIDTH = 0.6F;
    private final Map<HiveBuildingKind, Look> looks = new EnumMap<>(HiveBuildingKind.class);
    /** 每个核心（实体 ID）连着的建筑：建筑不动，隔一阵重新找一次就够。 */
    private final Map<Integer, Tethers> tethers = new HashMap<>();

    /** 一种建筑的模型、贴图、发光层和发光颜色。 */
    private record Look(HiveBuildingModel model, Identifier texture, Identifier glow, int color) {
    }

    /** 一个核心连着的建筑（相对于核心的位置）和找的时间。 */
    private record Tethers(long time, List<Vec3> ends) {
    }

    public HiveBuildingRenderer(EntityRendererProvider.Context context) {
        super(context, HiveBuildingModel.core(), 0);
        this.looks.put(HiveBuildingKind.CORE, look(this.model, HiveBuildingKind.CORE, 0xFF2A48));
        this.looks.put(HiveBuildingKind.PHOTON_RECEIVER, look(HiveBuildingModel.photonReceiver(), HiveBuildingKind.PHOTON_RECEIVER, 0xB060FF));
        this.looks.put(HiveBuildingKind.PLASMA_TOWER, look(HiveBuildingModel.plasmaTower(), HiveBuildingKind.PLASMA_TOWER, 0xFF7A2A));
        this.looks.put(HiveBuildingKind.PHASE_LASER, look(HiveBuildingModel.phaseLaser(), HiveBuildingKind.PHASE_LASER, 0xD040FF));
        this.looks.put(HiveBuildingKind.LANCER_PORT, look(HiveBuildingModel.lancerPort(), HiveBuildingKind.LANCER_PORT, 0xFF3A5A));
        this.looks.put(HiveBuildingKind.HUMPBACK_PORT, look(HiveBuildingModel.humpbackPort(), HiveBuildingKind.HUMPBACK_PORT, 0xC040FF));
        this.addLayer(new GlowLayer(this));
    }

    private static Look look(HiveBuildingModel model, HiveBuildingKind kind, int color) {
        String name = "hive_" + kind.id();
        return new Look(model, Universe.id("textures/entity/" + name + ".png"), Universe.id("textures/entity/" + name + "_glow.png"), color);
    }

    private Look look(HiveBuildingRenderState state) {
        return this.looks.get(state.kind);
    }

    @Override
    public HiveBuildingRenderState createRenderState() {
        return new HiveBuildingRenderState();
    }

    @Override
    public void extractRenderState(HiveBuilding entity, HiveBuildingRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.kind = entity.hiveKind();
        state.powered = entity.powered();
        state.phase = (entity.getId() * 37 % 100) / 100F * Mth.TWO_PI;
        // 不像生物那样倒下，也不闪红
        state.deathTime = 0;
        Vec3 origin = entity.getPosition(partialTicks);
        state.headYaw = 0;
        state.headPitch = 0;
        state.beamFrom = null;
        state.beamTo = null;
        state.tethers = state.kind == HiveBuildingKind.CORE ? this.tethers(entity) : List.of();
        Vec3 pivot = null;
        Vec3 aim = null;
        if (state.kind == HiveBuildingKind.PHOTON_RECEIVER) {
            pivot = origin.add(0, DISH_HEIGHT, 0);
            aim = Vec3.ZERO;
        } else if (state.kind.isTower()) {
            pivot = entity.muzzle();
            aim = aim(entity, partialTicks);
        }
        if (pivot != null && aim != null) {
            Vec3 d = aim.subtract(pivot);
            if (d.lengthSqr() > 1e-6) {
                float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90;
                state.headYaw = Mth.wrapDegrees(yaw - entity.getYRot()) * Mth.DEG_TO_RAD;
                state.headPitch = (float) -Mth.atan2(d.y, Math.hypot(d.x, d.z));
                if (state.kind == HiveBuildingKind.PHOTON_RECEIVER && state.powered) {
                    Vec3 toStar = d.normalize();
                    state.beamFrom = new Vec3(0, DISH_HEIGHT, 0).add(toStar.scale(DISH_TIP));
                    state.beamTo = state.beamFrom.add(toStar.scale(BEAM_LENGTH));
                }
            }
        }
    }

    /** 核心连着哪些建筑：系索那头在建筑的半腰。 */
    private List<Vec3> tethers(HiveBuilding core) {
        long now = core.level().getGameTime();
        Tethers cached = this.tethers.get(core.getId());
        if (cached != null && now - cached.time() < TETHER_REFRESH && now >= cached.time()) {
            return cached.ends();
        }
        if (this.tethers.size() > 64) {
            this.tethers.clear();
        }
        Vec3 origin = core.position();
        List<Vec3> ends = new ArrayList<>();
        for (HiveBuilding building : core.level().getEntitiesOfClass(HiveBuilding.class, core.getBoundingBox().inflate(TETHER_RANGE),
                building -> building != core && building.hiveKind() != HiveBuildingKind.CORE)) {
            ends.add(building.position().add(0, building.getBbHeight() * 0.5, 0).subtract(origin));
        }
        this.tethers.put(core.getId(), new Tethers(now, List.copyOf(ends)));
        return ends;
    }

    /** 炮塔瞄着的地方。 */
    private static @Nullable Vec3 aim(HiveBuilding entity, float partialTicks) {
        int id = entity.aimedEntity();
        if (id < 0) {
            return null;
        }
        Entity target = entity.level().getEntity(id);
        return target == null ? null : target.getPosition(partialTicks).add(0, target.getBbHeight() * 0.5, 0);
    }

    @Override
    protected AABB getBoundingBoxForCulling(HiveBuilding entity, float partialTicks) {
        // 模型比碰撞箱大一圈（核心的环、接收盘）；接收器还有往外伸的光束
        AABB box = super.getBoundingBoxForCulling(entity, partialTicks).inflate(3);
        if (entity.hiveKind() == HiveBuildingKind.CORE) {
            // 核心还画连到各座建筑的系索
            return box.inflate(TETHER_RANGE);
        }
        if (entity.hiveKind() == HiveBuildingKind.PHOTON_RECEIVER) {
            Vec3 origin = entity.getPosition(partialTicks);
            Vec3 end = origin.add(0, DISH_HEIGHT, 0).add(origin.scale(-1).normalize().scale(BEAM_LENGTH));
            return box.minmax(new AABB(end, end));
        }
        return box;
    }

    @Override
    protected boolean shouldShowName(HiveBuilding entity, double distanceToCameraSq) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || distanceToCameraSq > NAME_RANGE * NAME_RANGE) {
            return false;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 to = entity.getBoundingBox().getCenter().subtract(eye);
        double length = to.length();
        return length < 1e-3 || to.scale(1 / length).dot(player.getViewVector(1)) > NAME_CONE;
    }

    @Override
    protected @Nullable Component getNameTag(HiveBuilding entity) {
        return Component.translatable("entity.universe.hive_building.tag", entity.getDisplayName(), entity.darkFogLevel());
    }

    @Override
    public void submit(HiveBuildingRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        this.model = this.look(state).model();
        super.submit(state, poseStack, collector, camera);
        Vec3 eye = camera.pos.subtract(new Vec3(state.x, state.y, state.z));
        if (!state.tethers.isEmpty()) {
            int color = state.powered ? HiveBeams.scale(this.look(state).color(), 0.55F) : OFF;
            Vec3 hub = new Vec3(0, TETHER_HUB, 0);
            for (Vec3 end : state.tethers) {
                HiveBeams.tether(poseStack, collector, hub, end, eye, color, TETHER_WIDTH, state.ageInTicks + state.phase * 10);
            }
        }
        if (state.beamFrom != null && state.beamTo != null) {
            float pulse = 0.65F + 0.35F * Mth.sin(state.ageInTicks * 0.15F + state.phase);
            HiveBeams.submit(poseStack, collector, state.beamFrom, state.beamTo, eye, HiveBeams.scale(RECEIVER_BEAM, pulse), 0.35F);
        }
    }

    @Override
    protected void scale(HiveBuildingRenderState state, PoseStack poseStack) {
        float scale = this.look(state).model().scale();
        poseStack.scale(scale, scale, scale);
    }

    @Override
    protected float getFlipDegrees() {
        return 0;
    }

    @Override
    public Identifier getTextureLocation(HiveBuildingRenderState state) {
        return this.look(state).texture();
    }

    @Override
    protected int getBlockLightLevel(HiveBuilding entity, BlockPos pos) {
        return Math.max(6, super.getBlockLightLevel(entity, pos));
    }

    private static final class GlowLayer extends RenderLayer<HiveBuildingRenderState, HiveBuildingModel> {
        private final HiveBuildingRenderer renderer;

        GlowLayer(HiveBuildingRenderer renderer) {
            super(renderer);
            this.renderer = renderer;
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, HiveBuildingRenderState state, float yRot,
                float xRot) {
            if (state.isInvisible) {
                return;
            }
            Look look = this.renderer.look(state);
            int color = state.powered ? HiveBeams.scale(look.color(), 0.75F + 0.25F * Mth.sin(state.ageInTicks * 0.08F + state.phase)) : OFF;
            collector.order(1).submitModel(this.getParentModel(), state, poseStack, RenderTypes.eyes(look.glow()),
                    lightCoords, OverlayTexture.NO_OVERLAY, 0xFF000000 | color, null, state.outlineColor);
        }
    }
}
