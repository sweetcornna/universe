package io.github.sweetcornna.universe.client.exocraft;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.exocraft.Torpedo;
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

/**
 * 鱼雷的渲染器（ADR-0026）：灰色的圆柱弹体、红色弹头、十字尾翼，尾部喷口发青光；弹头顺着速度的方向。
 * 贴图的展开方式和 {@code tools/m14_submarine_textures.py} 里的 {@code torpedo()} 一一对应。
 */
public class TorpedoRenderer extends EntityRenderer<Torpedo, TorpedoRenderer.State> {
    private static final Identifier TEXTURE = Universe.id("textures/entity/torpedo.png");
    private static final Identifier GLOW = Universe.id("textures/entity/torpedo_glow.png");
    private final Model model = Model.create();

    public TorpedoRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(Torpedo entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.yRot = entity.getYRot(partialTicks);
        state.xRot = entity.getXRot(partialTicks);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0, 0.2F, 0);
        // 弹射物的偏航是 atan2(x, z)、俯仰朝上为正（和生物的反过来）
        poseStack.rotate(Axis.YP.rotationDegrees(state.yRot + 180));
        poseStack.rotate(Axis.XP.rotationDegrees(state.xRot));
        poseStack.scale(-1, -1, 1);
        collector.submitModel(this.model, state, poseStack, RenderTypes.entityCutout(TEXTURE), state.lightCoords, OverlayTexture.NO_OVERLAY,
                state.outlineColor);
        collector.order(1).submitModel(this.model, state, poseStack, RenderTypes.eyes(GLOW), state.lightCoords, OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF, null, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    /** 鱼雷一帧的渲染状态。 */
    public static class State extends EntityRenderState {
        float yRot;
        float xRot;
    }

    /** 弹头朝 -z，中心在原点，长 0.8 格。 */
    static final class Model extends EntityModel<State> {
        private Model(ModelPart root) {
            super(root);
        }

        static Model create() {
            MeshDefinition mesh = new MeshDefinition();
            mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create()
                            .texOffs(0, 0).addBox(-1.5F, -1.5F, -5, 3, 3, 10)
                            .texOffs(0, 13).addBox(-1, -1, -6, 2, 2, 1)
                            .texOffs(6, 13).addBox(-1, -1, 5, 2, 2, 2)
                            .texOffs(0, 17).addBox(-3.5F, -0.5F, 3, 7, 1, 2)
                            .texOffs(18, 17).addBox(-0.5F, -3.5F, 3, 1, 7, 2),
                    PartPose.ZERO);
            return new Model(LayerDefinition.create(mesh, 32, 32).bakeRoot());
        }
    }
}
