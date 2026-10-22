package io.github.sweetcornna.universe.client.civ;

import io.github.sweetcornna.universe.core.civ.Race;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * 外星 NPC 的模型（ADR-0027 §一）：三个种族各一套方块，骨架一样（头、身子、两臂、两腿），动作共用。
 *
 * <ul>
 *   <li>商旅族：矮，1.3 格。圆身子、大脑袋、鸟一样的喙和头冠，胳膊上一排飞羽，鸟脚。</li>
 *   <li>学者族：高瘦，2 格。细长的四肢，背上一整块壳，头上一顶壳盖、两根触须，长袍。</li>
 *   <li>戍卫族：壮，2.2 格。全身是甲：胸甲、肩甲、护手、护膝，头盔上一道冠，下颌两根獠牙。</li>
 * </ul>
 * 贴图的展开方式和 {@code tools/m15_npc_textures.py} 里的方块列表一一对应（texOffs 和尺寸），改了这里要同步改那边。
 */
public final class AlienNpcModel extends EntityModel<AlienNpcRenderState> {
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final ModelPart antennae;
    private final float armSwing;
    private final float legSwing;

    private AlienNpcModel(ModelPart root, float armSwing, float legSwing) {
        super(root);
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
        this.antennae = this.head.hasChild("antennae") ? this.head.getChild("antennae") : null;
        this.armSwing = armSwing;
        this.legSwing = legSwing;
    }

    public static AlienNpcModel create(Race race) {
        return switch (race) {
            case TRADER -> trader();
            case SCHOLAR -> scholar();
            case WARDEN -> warden();
        };
    }

    /** 商旅族，贴图 64×64。 */
    private static AlienNpcModel trader() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create()
                        // 脑袋、上喙、下喙、头冠、两边的耳羽
                        .texOffs(0, 0).addBox(-3.5F, -7, -3.5F, 7, 7, 7)
                        .texOffs(28, 0).addBox(-1.5F, -4, -7.5F, 3, 2, 4)
                        .texOffs(28, 6).addBox(-1, -2, -6.5F, 2, 1, 3)
                        .texOffs(42, 0).addBox(-0.5F, -10, -2, 1, 3, 6)
                        .texOffs(56, 0).addBox(-4.5F, -7, 0, 1, 2, 3)
                        .texOffs(56, 0).mirror().addBox(3.5F, -7, 0, 1, 2, 3).mirror(false),
                PartPose.offset(0, 11, -0.5F));
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        // 圆身子、袍子的下摆、挎包
                        .texOffs(0, 16).addBox(-4, 0, -2.5F, 8, 7, 5)
                        .texOffs(0, 28).addBox(-4.5F, 7, -3, 9, 3, 6)
                        .texOffs(26, 16).addBox(4, 4, -1.5F, 2, 3, 3),
                PartPose.offset(0, 11, 0));
        CubeListBuilder arm = CubeListBuilder.create()
                .texOffs(36, 10).addBox(-1, -0.5F, -1, 2, 6, 2)
                .texOffs(44, 10).addBox(-0.5F, 0, 1, 1, 6, 3);
        root.addOrReplaceChild("right_arm", arm, PartPose.offset(-5, 12, 0));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().mirror()
                .texOffs(36, 10).addBox(-1, -0.5F, -1, 2, 6, 2)
                .texOffs(44, 10).addBox(-0.5F, 0, 1, 1, 6, 3), PartPose.offset(5, 12, 0));
        CubeListBuilder leg = CubeListBuilder.create()
                .texOffs(52, 10).addBox(-1, 0, -1, 2, 6, 2)
                .texOffs(30, 28).addBox(-1.5F, 5, -3, 3, 1, 4);
        root.addOrReplaceChild("right_leg", leg, PartPose.offset(-2, 18, 0));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().mirror()
                .texOffs(52, 10).addBox(-1, 0, -1, 2, 6, 2)
                .texOffs(30, 28).addBox(-1.5F, 5, -3, 3, 1, 4), PartPose.offset(2, 18, 0));
        return new AlienNpcModel(LayerDefinition.create(mesh, 64, 64).bakeRoot(), 0.9F, 1.2F);
    }

    /** 学者族，贴图 64×64。 */
    private static AlienNpcModel scholar() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                        // 窄长的头、往后伸的壳盖、口器
                        .texOffs(0, 0).addBox(-2.5F, -7, -2.5F, 5, 7, 5)
                        .texOffs(20, 0).addBox(-3, -8, -2, 6, 3, 7)
                        .texOffs(50, 0).addBox(-1.5F, -2, -3.5F, 3, 2, 1),
                PartPose.offset(0, 1, -0.5F));
        head.addOrReplaceChild("antennae", CubeListBuilder.create()
                        .texOffs(46, 0).addBox(-2.5F, -3, -0.5F, 1, 3, 1)
                        .texOffs(46, 0).mirror().addBox(1.5F, -3, -0.5F, 1, 3, 1).mirror(false),
                PartPose.offset(0, -7, -1));
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        // 身子、背上的壳、壳脊、长袍的下摆
                        .texOffs(0, 16).addBox(-2.5F, -10, -2, 5, 10, 4)
                        .texOffs(18, 16).addBox(-3.5F, -11, 2, 7, 12, 3)
                        .texOffs(38, 16).addBox(-0.5F, -12, 4.5F, 1, 12, 1)
                        .texOffs(20, 32).addBox(-3.5F, 0, -2.5F, 7, 6, 5),
                PartPose.offset(0, 11, 0));
        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(44, 32).addBox(-1, -0.5F, -1, 2, 13, 2)
                .texOffs(52, 32).addBox(-1, 12.5F, -1.5F, 2, 2, 1), PartPose.offset(-3.5F, 2, 0));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().mirror()
                .texOffs(44, 32).addBox(-1, -0.5F, -1, 2, 13, 2)
                .texOffs(52, 32).addBox(-1, 12.5F, -1.5F, 2, 2, 1), PartPose.offset(3.5F, 2, 0));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(0, 32).addBox(-1, 0, -1, 2, 13, 2)
                .texOffs(8, 32).addBox(-1.5F, 12, -1.5F, 3, 1, 3), PartPose.offset(-1.8F, 11, 0));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().mirror()
                .texOffs(0, 32).addBox(-1, 0, -1, 2, 13, 2)
                .texOffs(8, 32).addBox(-1.5F, 12, -1.5F, 3, 1, 3), PartPose.offset(1.8F, 11, 0));
        return new AlienNpcModel(LayerDefinition.create(mesh, 64, 64).bakeRoot(), 0.6F, 0.8F);
    }

    /** 戍卫族，贴图 128×64。 */
    private static AlienNpcModel warden() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create()
                        // 头盔、盔冠、两根獠牙
                        .texOffs(66, 0).addBox(-3.5F, -7, -3.5F, 7, 7, 7)
                        .texOffs(94, 0).addBox(-1, -10, -3, 2, 2, 8)
                        .texOffs(114, 0).addBox(-3, -2, -4.5F, 1, 2, 1)
                        .texOffs(114, 0).mirror().addBox(2, -2, -4.5F, 1, 2, 1).mirror(false),
                PartPose.offset(0, -2, -1));
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        // 身子、胸甲、腰带、背甲
                        .texOffs(0, 0).addBox(-6, -14, -3.5F, 12, 14, 7)
                        .texOffs(38, 0).addBox(-6.5F, -13, -4.5F, 13, 8, 1)
                        .texOffs(0, 21).addBox(-6.5F, -2, -4, 13, 3, 8)
                        .texOffs(42, 21).addBox(-4, -12, 3.5F, 8, 9, 2),
                PartPose.offset(0, 12, 0));
        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(62, 21).addBox(-2, -1, -2, 4, 13, 4)
                .texOffs(78, 21).addBox(-3.5F, -3, -3.5F, 7, 4, 7)
                .texOffs(106, 21).addBox(-2.5F, 8, -2.5F, 5, 4, 5), PartPose.offset(-8, 0, 0));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().mirror()
                .texOffs(62, 21).addBox(-2, -1, -2, 4, 13, 4)
                .texOffs(78, 21).addBox(-3.5F, -3, -3.5F, 7, 4, 7)
                .texOffs(106, 21).addBox(-2.5F, 8, -2.5F, 5, 4, 5), PartPose.offset(8, 0, 0));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(0, 32).addBox(-2, 0, -2, 4, 12, 4)
                .texOffs(16, 32).addBox(-2.5F, 4, -2.5F, 5, 4, 1)
                .texOffs(28, 32).addBox(-2.5F, 10, -3, 5, 2, 5), PartPose.offset(-3, 12, 0));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().mirror()
                .texOffs(0, 32).addBox(-2, 0, -2, 4, 12, 4)
                .texOffs(16, 32).addBox(-2.5F, 4, -2.5F, 5, 4, 1)
                .texOffs(28, 32).addBox(-2.5F, 10, -3, 5, 2, 5), PartPose.offset(3, 12, 0));
        return new AlienNpcModel(LayerDefinition.create(mesh, 128, 64).bakeRoot(), 0.7F, 1.0F);
    }

    @Override
    public void setupAnim(AlienNpcRenderState state) {
        super.setupAnim(state);
        float age = state.ageInTicks + state.phase;
        this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
        this.head.xRot = state.xRot * Mth.DEG_TO_RAD;
        // 走路：两腿前后摆，胳膊反着摆
        float pos = state.walkAnimationPos;
        float speed = Math.min(1, state.walkAnimationSpeed);
        float swing = Mth.cos(pos * 0.6662F);
        this.rightLeg.xRot = swing * this.legSwing * speed;
        this.leftLeg.xRot = -swing * this.legSwing * speed;
        this.rightArm.xRot = -swing * this.armSwing * speed;
        this.leftArm.xRot = swing * this.armSwing * speed;
        // 站着时喘气：身子和头轻轻起伏，胳膊微微张开
        float breath = Mth.sin(age * 0.08F);
        this.body.y += breath * 0.25F;
        this.head.y += breath * 0.25F;
        this.rightArm.zRot = 0.06F + breath * 0.03F;
        this.leftArm.zRot = -0.06F - breath * 0.03F;
        if (this.antennae != null) {
            this.antennae.xRot = Mth.sin(age * 0.13F) * 0.12F;
            this.antennae.zRot = Mth.sin(age * 0.07F) * 0.05F;
        }
        // 手势：抬起右手摆一摆，歪一下头
        if (state.gesture > 0) {
            float lift = Mth.sin(state.gesture * Mth.PI);
            this.rightArm.xRot = Mth.lerp(lift, this.rightArm.xRot, -2.2F);
            this.rightArm.zRot += lift * Mth.sin(age * 0.9F) * 0.35F;
            this.head.zRot = lift * 0.18F;
        }
    }
}
