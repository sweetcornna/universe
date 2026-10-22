package io.github.sweetcornna.universe.client.sentinel;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * 步行机的模型（ADR-0026），照无人深空的 Walker：两条粗腿撑着骨盆，上面一个方座舱，正面一道横条面罩，
 * 两侧各挂一门炮，顶上传感罩和两根天线，座舱背后是反应堆的散热口（弱点）。座舱跟着视线转，腿不转。
 * 走路时大腿前后摆、膝盖弯、脚掌保持贴地；抬腿踩地时右腿高高抬起，身子往左歪。
 * 贴图 128×128，展开方式和 {@code tools/m14_ground_sentinel_textures.py} 里的方块列表一一对应，改了这里要同步改那边。
 */
public final class WalkerSentinelModel extends EntityModel<GroundSentinelRenderState> {
    private final ModelPart pelvis;
    private final ModelPart cockpit;
    private final ModelPart leftCannon;
    private final ModelPart rightCannon;
    private final ModelPart[] thighs = new ModelPart[2];
    private final ModelPart[] shins = new ModelPart[2];
    private final ModelPart[] feet = new ModelPart[2];

    public WalkerSentinelModel() {
        this(createLayer().bakeRoot());
    }

    private WalkerSentinelModel(ModelPart root) {
        super(root);
        this.pelvis = root.getChild("pelvis");
        this.cockpit = this.pelvis.getChild("cockpit");
        this.leftCannon = this.cockpit.getChild("left_cannon");
        this.rightCannon = this.cockpit.getChild("right_cannon");
        String[] sides = {"left", "right"};
        for (int i = 0; i < 2; i++) {
            this.thighs[i] = root.getChild(sides[i] + "_leg");
            this.shins[i] = this.thighs[i].getChild("shin");
            this.feet[i] = this.shins[i].getChild("foot");
        }
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition pelvis = root.addOrReplaceChild("pelvis", CubeListBuilder.create()
                .texOffs(0, 42).addBox(-7, -3, -4, 14, 6, 8), PartPose.offset(0, -14, 0));
        PartDefinition cockpit = pelvis.addOrReplaceChild("cockpit", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-9, -16, -9, 18, 14, 18)
                        .texOffs(76, 0).addBox(-4, -20, -4, 8, 4, 8)
                        .texOffs(76, 12).addBox(-6, -11, -9.5F, 12, 3, 1)
                        .texOffs(76, 16).addBox(-6, -2, -7, 12, 3, 12)
                        .texOffs(0, 32).addBox(-5, -13, 9, 10, 7, 2),
                PartPose.offset(0, -3, 0));
        for (int side = -1; side <= 1; side += 2) {
            cockpit.addOrReplaceChild(side > 0 ? "left_antenna" : "right_antenna", CubeListBuilder.create().texOffs(110, 0)
                    .addBox(-0.5F, -6, -0.5F, 1, 6, 1), PartPose.offsetAndRotation(side * 6, -16, 5, -0.15F, 0, side * 0.1F));
            cockpit.addOrReplaceChild(side > 0 ? "left_cannon" : "right_cannon", CubeListBuilder.create()
                            .texOffs(48, 42).mirror(side > 0).addBox(-2, -2, -10, 4, 4, 14)
                            .texOffs(86, 32).addBox(-1, -1, -10.5F, 2, 2, 1),
                    PartPose.offset(side * 11, -8, -2));
        }
        for (int side = -1; side <= 1; side += 2) {
            boolean mirror = side > 0;
            PartDefinition thigh = root.addOrReplaceChild(side > 0 ? "left_leg" : "right_leg", CubeListBuilder.create()
                    .texOffs(0, 60).mirror(mirror).addBox(-3, -1, -3, 6, 18, 6), PartPose.offset(side * 8.5F, -14, 0));
            PartDefinition shin = thigh.addOrReplaceChild("shin", CubeListBuilder.create()
                            .texOffs(24, 60).mirror(mirror).addBox(-2.5F, 0, -2.5F, 5, 18, 5)
                            .texOffs(84, 60).mirror(mirror).addBox(-3.5F, -3, -4, 7, 6, 2),
                    PartPose.offset(0, 16, 0));
            shin.addOrReplaceChild("foot", CubeListBuilder.create().texOffs(44, 60).mirror(mirror).addBox(-4, 0, -6, 8, 4, 11),
                    PartPose.offset(0, 18, 0));
        }
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(GroundSentinelRenderState state) {
        super.setupAnim(state);
        float age = state.ageInTicks;
        float pos = state.walkAnimationPos;
        float speed = Math.min(1, state.walkAnimationSpeed);
        this.cockpit.yRot = Mth.clamp(state.yRot, -75, 75) * Mth.DEG_TO_RAD;
        this.cockpit.xRot = Mth.clamp(state.xRot, -20, 20) * Mth.DEG_TO_RAD * 0.5F;
        // 炮管跟着视线俯仰
        this.leftCannon.xRot = state.xRot * Mth.DEG_TO_RAD * 0.5F;
        this.rightCannon.xRot = this.leftCannon.xRot;
        for (int i = 0; i < 2; i++) {
            float swing = pos * 0.45F + (i == 0 ? 0 : Mth.PI);
            this.thighs[i].xRot = Mth.sin(swing) * 0.55F * speed;
            this.shins[i].xRot = Math.max(0, Mth.cos(swing)) * 0.7F * speed;
        }
        // 每迈一步骨盆往下一沉，左右晃一点
        this.pelvis.y += Math.abs(Mth.sin(pos * 0.45F)) * 1.2F * speed + Mth.sin(age * 0.08F) * 0.3F;
        this.pelvis.zRot = Mth.sin(pos * 0.45F) * 0.04F * speed;
        if (state.stomp > 0) {
            float s = Mth.sin(Math.min(1, state.stomp * 1.4F) * Mth.HALF_PI);
            this.thighs[1].xRot = -1.0F * s;
            this.shins[1].xRot = 1.3F * s;
            this.pelvis.zRot = 0.09F * s;
            this.pelvis.y -= 1.5F * s;
        }
        for (int i = 0; i < 2; i++) {
            this.feet[i].xRot = -(this.thighs[i].xRot + this.shins[i].xRot);
        }
    }
}
