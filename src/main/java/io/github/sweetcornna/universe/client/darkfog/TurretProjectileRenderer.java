package io.github.sweetcornna.universe.client.darkfog;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * 聚爆炮的炮弹和导弹（ADR-0028 §五）：弹头顺着速度的方向，尾部（导弹的喷口、炮弹的引信环）发光。贴图的展开方式和
 * {@code tools/m16_turret_textures.py} 里的 {@code shell()}、{@code missile()} 一一对应。
 */
public class TurretProjectileRenderer<T extends Projectile> extends EntityRenderer<T, TurretProjectileRenderer.State> {
    private final Identifier texture;
    private final Identifier glow;
    private final Model model;

    private TurretProjectileRenderer(EntityRendererProvider.Context context, String name, Model model) {
        super(context);
        this.texture = Universe.id("textures/entity/" + name + ".png");
        this.glow = Universe.id("textures/entity/" + name + "_glow.png");
        this.model = model;
    }

    public static <T extends Projectile> TurretProjectileRenderer<T> shell(EntityRendererProvider.Context context) {
        return new TurretProjectileRenderer<>(context, "turret_shell", Model.shell());
    }

    public static <T extends Projectile> TurretProjectileRenderer<T> missile(EntityRendererProvider.Context context) {
        return new TurretProjectileRenderer<>(context, "turret_missile", Model.missile());
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.yRot = entity.getYRot(partialTicks);
        state.xRot = entity.getXRot(partialTicks);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0, 0.175F, 0);
        // 弹射物的偏航是 atan2(x, z)、俯仰朝上为正（和生物的反过来）
        poseStack.rotate(Axis.YP.rotationDegrees(state.yRot + 180));
        poseStack.rotate(Axis.XP.rotationDegrees(state.xRot));
        poseStack.scale(-1, -1, 1);
        collector.submitModel(this.model, state, poseStack, RenderTypes.entityCutout(this.texture), state.lightCoords, OverlayTexture.NO_OVERLAY,
                state.outlineColor);
        collector.order(1).submitModel(this.model, state, poseStack, RenderTypes.eyes(this.glow), LightCoordsUtil.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, 0xFFFFFFFF, null, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    /** 一帧的渲染状态。 */
    public static class State extends EntityRenderState {
        float yRot;
        float xRot;
    }

    /** 弹头朝 -z，中心在原点。 */
    static final class Model extends EntityModel<State> {
        private Model(ModelPart root) {
            super(root);
        }

        /** 炮弹：粗短的弹体加一个尖头，长 6 像素。 */
        static Model shell() {
            MeshDefinition mesh = new MeshDefinition();
            mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create()
                            .texOffs(0, 0).addBox(-1.5F, -1.5F, -2, 3, 3, 4)
                            .texOffs(0, 8).addBox(-1, -1, -3, 2, 2, 1)
                            .texOffs(0, 12).addBox(-1, -1, 2, 2, 2, 1),
                    PartPose.ZERO);
            return new Model(LayerDefinition.create(mesh, 32, 32).bakeRoot());
        }

        /** 导弹：细长的弹体、尖头、十字尾翼，尾部一个喷口，长 14 像素。 */
        static Model missile() {
            MeshDefinition mesh = new MeshDefinition();
            mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create()
                            .texOffs(0, 0).addBox(-1, -1, -5, 2, 2, 10)
                            .texOffs(0, 14).addBox(-0.5F, -0.5F, -7, 1, 1, 2)
                            .texOffs(8, 14).addBox(-0.5F, -0.5F, 5, 1, 1, 1)
                            .texOffs(0, 18).addBox(-2.5F, -0.5F, 3, 5, 1, 2)
                            .texOffs(14, 18).addBox(-0.5F, -2.5F, 3, 1, 5, 2),
                    PartPose.ZERO);
            return new Model(LayerDefinition.create(mesh, 32, 32).bakeRoot());
        }
    }
}
