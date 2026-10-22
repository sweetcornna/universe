package io.github.sweetcornna.universe.client.sentinel;

import java.util.List;
import java.util.stream.IntStream;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * 守卫机的模型（ADR-0014，ADR-0026 加了维修、召唤无人机）。普通守卫机是一颗带独眼面罩的球形机身，两侧挂着推进舱；
 * 重型守卫机机身更大，四角各一个推进舱，侧面加了装甲板，腹部有一门炮。维修无人机小一圈，腹下一个发光的修理头、
 * 两只机械臂；召唤无人机机身方正，腰上套着一圈转动的发光环。
 *
 * <p>机身整体跟着视线转（面罩对着目标），上下浮动；推进舱随飞行速度前倾。贴图的展开方式和
 * {@code tools/combat_textures.py}、{@code tools/m14_drone_textures.py} 里的方块列表一一对应，改了这里要同步改那边。
 */
public final class SentinelModel extends EntityModel<SentinelRenderState> {
    private final ModelPart body;
    private final List<ModelPart> pods;
    private final @Nullable ModelPart antenna;
    private final List<ModelPart> arms;
    private final @Nullable ModelPart ring;

    private SentinelModel(ModelPart root, int podCount, boolean hasAntenna, int armCount, boolean hasRing) {
        super(root);
        this.body = root.getChild("body");
        this.pods = IntStream.range(0, podCount).mapToObj(i -> this.body.getChild("pod" + i)).toList();
        this.antenna = hasAntenna ? this.body.getChild("antenna") : null;
        this.arms = IntStream.range(0, armCount).mapToObj(i -> this.body.getChild("arm" + i)).toList();
        this.ring = hasRing ? this.body.getChild("ring") : null;
    }

    /** 普通守卫机：贴图 64×32。 */
    public static SentinelModel drone() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4, -3.5F, -4, 8, 7, 8)
                        .texOffs(32, 0).addBox(-2.5F, -2, -5, 5, 4, 1)
                        .texOffs(44, 0).addBox(-0.5F, -6.5F, -2, 1, 3, 5)
                        .texOffs(20, 16).addBox(-3, -2.5F, 4, 6, 5, 1),
                PartPose.offset(0, 17, 0));
        for (int i = 0; i < 2; i++) {
            int side = i == 0 ? -1 : 1;
            body.addOrReplaceChild("pod" + i, CubeListBuilder.create().texOffs(0, 16).mirror(side > 0).addBox(-1.5F, -2, -2.5F, 3, 4, 5),
                    PartPose.offset(side * 5.5F, 0.5F, 0));
        }
        body.addOrReplaceChild("antenna", CubeListBuilder.create().texOffs(16, 16).addBox(-0.5F, 0, -0.5F, 1, 3, 1),
                PartPose.offset(0, 3.5F, 1));
        return new SentinelModel(LayerDefinition.create(mesh, 64, 32).bakeRoot(), 2, true, 0, false);
    }

    /** 重型守卫机：贴图 64×64。 */
    public static SentinelModel heavy() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-6, -4.5F, -6, 12, 9, 12)
                        .texOffs(0, 22).addBox(-4, -2.5F, -7, 8, 5, 1)
                        .texOffs(36, 22).addBox(-1.5F, 4.5F, -5, 3, 2, 7)
                        .texOffs(0, 32).addBox(-1, -7.5F, -4, 2, 3, 8)
                        .texOffs(20, 32).addBox(-7, -3, -4, 1, 6, 8)
                        .texOffs(20, 32).mirror().addBox(6, -3, -4, 1, 6, 8),
                PartPose.offset(0, 16, 0));
        int i = 0;
        for (int sz = -1; sz <= 1; sz += 2) {
            for (int sx = -1; sx <= 1; sx += 2) {
                body.addOrReplaceChild("pod" + i, CubeListBuilder.create().texOffs(20, 22).mirror(sx > 0).addBox(-2, -2.5F, -2, 4, 5, 4),
                        PartPose.offset(sx * 9, 1, sz * 5));
                i++;
            }
        }
        return new SentinelModel(LayerDefinition.create(mesh, 64, 64).bakeRoot(), 4, false, 0, false);
    }

    /** 维修无人机：贴图 64×32。小机身、面罩、腹下的修理头，两侧推进舱，两只机械臂。 */
    public static SentinelModel repair() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3, -2.5F, -3, 6, 5, 6)
                        .texOffs(24, 0).addBox(-2, -1.5F, -4, 4, 3, 1)
                        .texOffs(36, 0).addBox(-2, 2.5F, -2, 4, 1, 4)
                        .texOffs(24, 12).addBox(-1.5F, -3.5F, -0.5F, 3, 1, 1),
                PartPose.offset(0, 17, 0));
        for (int i = 0; i < 2; i++) {
            int side = i == 0 ? -1 : 1;
            body.addOrReplaceChild("pod" + i, CubeListBuilder.create().texOffs(0, 12).mirror(side > 0).addBox(-1, -1.5F, -2, 2, 3, 4),
                    PartPose.offset(side * 4, 0, 0));
            body.addOrReplaceChild("arm" + i, CubeListBuilder.create().texOffs(16, 12).addBox(-0.5F, 0, -0.5F, 1, 4, 1),
                    PartPose.offset(side * 1.8F, 2.5F, -1.5F));
        }
        return new SentinelModel(LayerDefinition.create(mesh, 64, 32).bakeRoot(), 2, false, 2, false);
    }

    /** 召唤无人机：贴图 64×64。方正的机身、面罩、顶上的尖角，腰上一圈转动的环，腹下两个推进舱。 */
    public static SentinelModel summoner() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4, -4, -4, 8, 8, 8)
                        .texOffs(32, 0).addBox(-3, -1.5F, -5, 6, 3, 1)
                        .texOffs(48, 0).addBox(-1, -7, -1, 2, 3, 2),
                PartPose.offset(0, 15, 0));
        body.addOrReplaceChild("ring", CubeListBuilder.create()
                        .texOffs(0, 16).addBox(-7, -0.5F, -7, 14, 1, 2)
                        .texOffs(0, 20).addBox(-7, -0.5F, 5, 14, 1, 2)
                        .texOffs(0, 24).addBox(-7, -0.5F, -5, 2, 1, 10)
                        .texOffs(24, 24).addBox(5, -0.5F, -5, 2, 1, 10),
                PartPose.ZERO);
        for (int i = 0; i < 2; i++) {
            int side = i == 0 ? -1 : 1;
            body.addOrReplaceChild("pod" + i, CubeListBuilder.create().texOffs(0, 40).mirror(side > 0).addBox(-1.5F, -2, -2, 3, 4, 4),
                    PartPose.offset(side * 3, 5.5F, 0));
        }
        return new SentinelModel(LayerDefinition.create(mesh, 64, 64).bakeRoot(), 2, false, 0, true);
    }

    @Override
    public void setupAnim(SentinelRenderState state) {
        super.setupAnim(state);
        float age = state.ageInTicks;
        // 面罩对着视线方向
        this.body.yRot = state.yRot * Mth.DEG_TO_RAD;
        this.body.xRot = state.xRot * Mth.DEG_TO_RAD * 0.8F;
        this.body.y += Mth.sin(age * 0.1F + state.bobPhase) * 0.8F;
        this.body.zRot = Mth.sin(age * 0.05F + state.bobPhase) * 0.04F;
        float lean = Math.min(1, state.walkAnimationSpeed * 2) * 0.45F;
        for (int i = 0; i < this.pods.size(); i++) {
            ModelPart pod = this.pods.get(i);
            pod.xRot = lean + Mth.sin(age * 0.17F + i * 1.3F) * 0.06F;
        }
        if (this.antenna != null) {
            this.antenna.xRot = Mth.sin(age * 0.13F) * 0.15F;
            this.antenna.zRot = Mth.sin(age * 0.09F + 1) * 0.1F;
        }
        for (int i = 0; i < this.arms.size(); i++) {
            ModelPart arm = this.arms.get(i);
            arm.xRot = -0.35F + Mth.sin(age * 0.21F + i * Mth.PI) * 0.25F;
            arm.zRot = (i == 0 ? 1 : -1) * 0.12F;
        }
        if (this.ring != null) {
            // 召唤前转得更快
            this.ring.yRot = age * (state.charging ? 0.35F : 0.08F);
            this.ring.y = Mth.sin(age * 0.07F + state.bobPhase) * 0.6F;
        }
    }
}
