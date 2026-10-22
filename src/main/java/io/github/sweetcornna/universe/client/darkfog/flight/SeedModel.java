package io.github.sweetcornna.universe.client.darkfog.flight;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * 黑雾种子的模型（ADR-0028 §八），贴图 64×64，渲染时放大 {@link #SCALE} 倍：一颗方壳，六个面各伸出一根刺，慢慢翻滚；
 * 外面一圈斜着的环往反方向转，环上挂两个小舱。贴图的展开和 {@code tools/m16_flight_textures.py} 一一对应。
 */
public final class SeedModel extends EntityModel<SeedRenderState> {
    public static final float SCALE = 2.5F;
    private final ModelPart shell;
    private final ModelPart ring;

    public SeedModel() {
        this(create());
    }

    private SeedModel(ModelPart root) {
        super(root);
        ModelPart body = root.getChild("body");
        this.shell = body.getChild("shell");
        this.ring = body.getChild("tilt").getChild("ring");
    }

    private static ModelPart create() {
        MeshDefinition mesh = new MeshDefinition();
        // 碰撞箱 2.5 格高，放大 2.5 倍后是 16 个模型像素，中心在 24 - 8
        PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0, 16, 0));
        PartDefinition shell = body.addOrReplaceChild("shell", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -4, -4, 8, 8, 8), PartPose.ZERO);
        float[][] spikes = {{0, 0}, {0, Mth.PI}, {0, Mth.HALF_PI}, {0, -Mth.HALF_PI}, {Mth.HALF_PI, 0}, {-Mth.HALF_PI, 0}};
        for (int i = 0; i < spikes.length; i++) {
            shell.addOrReplaceChild("spike" + i, CubeListBuilder.create().texOffs(0, 16).addBox(-1, -1, 4, 2, 2, 6),
                    PartPose.rotation(spikes[i][0], spikes[i][1], 0));
        }
        PartDefinition tilt = body.addOrReplaceChild("tilt", CubeListBuilder.create(), PartPose.rotation(0.4F, 0, 0.2F));
        PartDefinition ring = tilt.addOrReplaceChild("ring", CubeListBuilder.create().texOffs(0, 24).addBox(-7, -0.5F, -7, 14, 1, 14), PartPose.ZERO);
        ring.addOrReplaceChild("pod0", CubeListBuilder.create().texOffs(32, 0).addBox(-2, -2, -2, 4, 4, 4), PartPose.offset(9, 0, 0));
        ring.addOrReplaceChild("pod1", CubeListBuilder.create().texOffs(32, 0).addBox(-2, -2, -2, 4, 4, 4), PartPose.offset(-9, 0, 0));
        return LayerDefinition.create(mesh, 64, 64).bakeRoot();
    }

    @Override
    public void setupAnim(SeedRenderState state) {
        super.setupAnim(state);
        float age = state.ageInTicks + state.phase * 40;
        this.shell.yRot = age * 0.03F;
        this.shell.xRot = age * 0.017F;
        this.ring.yRot = -age * 0.06F;
    }
}
