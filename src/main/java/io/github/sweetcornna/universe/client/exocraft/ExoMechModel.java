package io.github.sweetcornna.universe.client.exocraft;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * 机甲的模型（ADR-0026）：两足机甲。胸口是驾驶舱（正面一块玻璃），头上一圈面罩和天线，背上两个喷气包；
 * 右臂一门爆能炮，左臂是扫描仪；两条腿分大腿、小腿和脚，膝盖往后弯。
 * 正面朝 -z，宽 2 格、高 3.4 格；原点下方 24 像素是地面。贴图 128×128。
 * <p>
 * 走路时两腿前后摆、膝盖跟着弯，身子上下颠；喷气离地时腿往前收。
 * 贴图的展开方式和 {@code tools/m14_vehicle_textures.py} 里的方块列表一一对应（texOffs 和尺寸），改了这里要同步改那边。
 */
public final class ExoMechModel extends EntityModel<ExoMechRenderState> {
    private final ModelPart body;
    private final ModelPart leftArm;
    private final ModelPart rightArm;
    private final ModelPart leftLeg;
    private final ModelPart rightLeg;
    private final ModelPart leftShin;
    private final ModelPart rightShin;

    private ExoMechModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.leftArm = this.body.getChild("left_arm");
        this.rightArm = this.body.getChild("right_arm");
        this.leftLeg = root.getChild("left_leg");
        this.rightLeg = root.getChild("right_leg");
        this.leftShin = this.leftLeg.getChild("shin");
        this.rightShin = this.rightLeg.getChild("shin");
    }

    public static ExoMechModel create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        // 驾驶舱（胸口）、玻璃、头、骨盆
                        .texOffs(0, 0).addBox(-10, -27, -10, 20, 23, 18)
                        .texOffs(76, 0).addBox(-7, -24, -11, 14, 10, 1)
                        .texOffs(76, 11).addBox(-4, -31, -6, 8, 4, 9)
                        .texOffs(0, 41).addBox(-8, -4, -6, 16, 4, 12)
                        // 背上两个喷气包和喷口
                        .texOffs(56, 41).addBox(3.5F, -23, 8, 5, 15, 5)
                        .texOffs(56, 41).mirror().addBox(-8.5F, -23, 8, 5, 15, 5).mirror(false)
                        .texOffs(76, 41).addBox(4.5F, -8, 9, 3, 2, 3)
                        .texOffs(76, 41).mirror().addBox(-7.5F, -8, 9, 3, 2, 3).mirror(false)
                        // 天线
                        .texOffs(44, 99).addBox(2, -36, 0, 1, 5, 1),
                PartPose.ZERO);
        // 右臂：肩甲、上臂、炮身和炮管（炮口和 ExoMech.muzzle 对齐）
        body.addOrReplaceChild("right_arm", CubeListBuilder.create().mirror()
                        .texOffs(0, 78).addBox(-3.5F, -3, -3.5F, 7, 5, 7)
                        .texOffs(28, 78).addBox(-2.5F, 2, -2.5F, 5, 10, 5)
                        .mirror(false)
                        .texOffs(48, 78).addBox(-3.5F, 10, -10, 7, 7, 14)
                        .texOffs(90, 78).addBox(-1.5F, 12, -18, 3, 3, 8),
                PartPose.offset(-13, -22, 0));
        // 左臂：肩甲、上臂、扫描仪
        body.addOrReplaceChild("left_arm", CubeListBuilder.create()
                        .texOffs(0, 78).addBox(-3.5F, -3, -3.5F, 7, 5, 7)
                        .texOffs(28, 78).addBox(-2.5F, 2, -2.5F, 5, 10, 5)
                        .texOffs(0, 99).addBox(-2.5F, 10, -7, 5, 5, 10)
                        .texOffs(30, 99).addBox(-3, 8, -9, 6, 6, 1),
                PartPose.offset(13, -22, 0));
        for (int side = -1; side <= 1; side += 2) {
            CubeListBuilder thigh = CubeListBuilder.create().texOffs(0, 61);
            CubeListBuilder shin = CubeListBuilder.create().texOffs(20, 61);
            if (side < 0) {
                thigh.mirror();
                shin.mirror();
            }
            PartDefinition leg = root.addOrReplaceChild(side > 0 ? "left_leg" : "right_leg", thigh.addBox(-2.5F, 0, -2.5F, 5, 12, 5),
                    PartPose.offset(side * 6, -2, 0));
            leg.addOrReplaceChild("shin", shin.addBox(-2, 0, -2, 4, 10, 4)
                            .texOffs(36, 61).addBox(-3.5F, 10, -5, 7, 4, 9)
                            .texOffs(68, 61).addBox(-2.5F, -1.5F, -3.5F, 5, 4, 2),
                    PartPose.offset(0, 12, 0));
        }
        return new ExoMechModel(LayerDefinition.create(mesh, 128, 128).bakeRoot());
    }

    @Override
    public void setupAnim(ExoMechRenderState state) {
        super.setupAnim(state);
        float amount = state.strideAmount;
        float swing = Mth.sin(state.stride) * 0.55F * amount;
        // 腿往前摆是负的 xRot；膝盖只往后弯，腿往前收的那半步弯得多
        this.leftLeg.xRot = swing;
        this.rightLeg.xRot = -swing;
        this.leftShin.xRot = Math.max(0, Mth.cos(state.stride)) * 0.7F * amount;
        this.rightShin.xRot = Math.max(0, -Mth.cos(state.stride)) * 0.7F * amount;
        // 手臂反着摆一点，炮口基本朝前
        this.leftArm.xRot = -swing * 0.3F;
        this.rightArm.xRot = swing * 0.15F;
        // 每迈一步身子往下沉一下
        this.body.y += Math.abs(Mth.cos(state.stride)) * amount * 0.8F;
        if (state.thrusting && !state.grounded) {
            this.leftLeg.xRot = -0.35F;
            this.rightLeg.xRot = -0.25F;
            this.leftShin.xRot = 0.7F;
            this.rightShin.xRot = 0.6F;
        }
    }
}
