package io.github.sweetcornna.universe.client.exocraft;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.exocraft.Exocraft;
import io.github.sweetcornna.universe.exocraft.HeavyRover;
import io.github.sweetcornna.universe.exocraft.HoverCar;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * 探索车的渲染器：车头朝车的偏航，轮子按走过的路转，前轮跟着转弯偏；大灯、炮口和天线顶上的灯叠一层发光；拿镐敲时晃一下。
 * 悬浮车和重型车（ADR-0026）用同一个渲染器，换模型和贴图（{@link #hoverCar}、{@link #heavyRover}）。
 */
public class ExocraftRenderer<T extends Exocraft> extends EntityRenderer<T, ExocraftRenderState> {
    private static final Identifier TEXTURE = Universe.id("textures/entity/exocraft.png");
    private static final Identifier GLOW = Universe.id("textures/entity/exocraft_glow.png");
    private final EntityModel<ExocraftRenderState> model;
    private final Identifier texture;
    private final Identifier glow;
    /** 模型伸出碰撞箱多少格（重型车比碰撞箱长）。 */
    private final double overhang;

    public ExocraftRenderer(EntityRendererProvider.Context context) {
        this(context, ExocraftModel.create(), TEXTURE, GLOW, 1.1F, 0);
    }

    private ExocraftRenderer(EntityRendererProvider.Context context, EntityModel<ExocraftRenderState> model, Identifier texture,
            Identifier glow, float shadow, double overhang) {
        super(context);
        this.model = model;
        this.texture = texture;
        this.glow = glow;
        this.overhang = overhang;
        this.shadowRadius = shadow;
    }

    public static ExocraftRenderer<HoverCar> hoverCar(EntityRendererProvider.Context context) {
        return new ExocraftRenderer<>(context, HoverCarModel.create(), Universe.id("textures/entity/hover_car.png"),
                Universe.id("textures/entity/hover_car_glow.png"), 1.0F, 0.3);
    }

    public static ExocraftRenderer<HeavyRover> heavyRover(EntityRendererProvider.Context context) {
        return new ExocraftRenderer<>(context, HeavyRoverModel.create(), Universe.id("textures/entity/heavy_rover.png"),
                Universe.id("textures/entity/heavy_rover_glow.png"), 1.5F, 0.6);
    }

    @Override
    public ExocraftRenderState createRenderState() {
        return new ExocraftRenderState();
    }

    @Override
    public void extractRenderState(T entity, ExocraftRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.yRot = entity.getYRot(partialTicks);
        state.wheelSpin = Mth.lerp(partialTicks, entity.wheelSpinO, entity.wheelSpin);
        // 这一刻偏航变了多少，看成方向盘打了多少
        float turning = Mth.wrapDegrees(entity.getYRot() - entity.yRotO);
        state.steer = Mth.clamp(turning * 0.12F, -0.45F, 0.45F);
        state.laser = entity.laserOn();
        state.hurtTime = entity.getHurtTime() - partialTicks;
        state.hurtDir = entity.getHurtDir();
        state.hovering = entity instanceof HoverCar && entity.isVehicle();
        state.refining = entity instanceof HeavyRover rover && rover.refining();
    }

    @Override
    protected AABB getBoundingBoxForCulling(T entity, float partialTicks) {
        return super.getBoundingBoxForCulling(entity, partialTicks).inflate(this.overhang, 0.5, this.overhang);
    }

    @Override
    public void submit(ExocraftRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0, 1.501F, 0);
        poseStack.rotate(Axis.YP.rotationDegrees(180 - state.yRot));
        if (state.hurtTime > 0) {
            poseStack.rotate(Axis.XP.rotationDegrees(Mth.sin(state.hurtTime) * state.hurtTime * 0.4F * state.hurtDir));
        }
        poseStack.scale(-1, -1, 1);
        collector.submitModel(this.model, state, poseStack, RenderTypes.entityCutout(this.texture), state.lightCoords,
                OverlayTexture.NO_OVERLAY, state.outlineColor);
        collector.order(1).submitModel(this.model, state, poseStack, RenderTypes.eyes(this.glow), state.lightCoords, OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF, null, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
