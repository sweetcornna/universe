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
 * 四足机的模型（ADR-0026），照无人深空的 Quad：一条窄长的机身，背上一道脊甲和尾部的散热口（弱点），
 * 前面一颗带横条面罩的头，下巴是光束的发射口；四条腿各分大腿、小腿和脚掌，走路时对角的两条腿一起迈。
 * 贴图 64×64，展开方式和 {@code tools/m14_ground_sentinel_textures.py} 里的方块列表一一对应，改了这里要同步改那边。
 */
public final class QuadrupedSentinelModel extends EntityModel<GroundSentinelRenderState> {
    private static final String[] LEGS = {"left_front_leg", "right_front_leg", "left_hind_leg", "right_hind_leg"};
    /** 对角的两条腿同一个相位。 */
    private static final float[] PHASE = {0, Mth.PI, Mth.PI, 0};
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart tail;
    private final ModelPart[] legs = new ModelPart[4];
    private final ModelPart[] shins = new ModelPart[4];

    public QuadrupedSentinelModel() {
        this(createLayer().bakeRoot());
    }

    private QuadrupedSentinelModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.head = this.body.getChild("head");
        this.tail = this.body.getChild("tail");
        for (int i = 0; i < 4; i++) {
            this.legs[i] = this.body.getChild(LEGS[i]);
            this.shins[i] = this.legs[i].getChild("shin");
        }
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.5F, -3, -8, 9, 6, 16)
                        .texOffs(0, 22).addBox(-3, -4.5F, -6, 6, 2, 11)
                        .texOffs(36, 22).addBox(-2.5F, -3.5F, 7.5F, 5, 4, 1),
                PartPose.offset(0, 10, 1));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 36).addBox(-3.5F, -3, -7, 7, 6, 7)
                        .texOffs(30, 36).addBox(-2.5F, -1.5F, -7.5F, 5, 2, 1)
                        .texOffs(44, 36).addBox(-1.5F, 1, -9, 3, 3, 3)
                        .texOffs(56, 0).addBox(-4.5F, -4.5F, -3, 1, 3, 3)
                        .texOffs(56, 0).mirror().addBox(3.5F, -4.5F, -3, 1, 3, 3),
                PartPose.offset(0, -1, -8));
        body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(36, 28).addBox(-0.5F, -6, -0.5F, 1, 6, 1),
                PartPose.offsetAndRotation(0, -3, 7, -0.5F, 0, 0));
        for (int i = 0; i < 4; i++) {
            float x = i % 2 == 0 ? 5 : -5;
            float z = i < 2 ? -6 : 6;
            boolean mirror = i % 2 == 0;
            PartDefinition leg = body.addOrReplaceChild(LEGS[i], CubeListBuilder.create().texOffs(0, 50).mirror(mirror)
                    .addBox(-1.5F, 0, -1.5F, 3, 7, 3), PartPose.offset(x, 1.5F, z));
            leg.addOrReplaceChild("shin", CubeListBuilder.create()
                            .texOffs(12, 50).mirror(mirror).addBox(-1, 0, -1, 2, 5, 2)
                            .texOffs(20, 50).mirror(mirror).addBox(-1.5F, 5, -2, 3, 1, 4),
                    PartPose.offset(0, 6.5F, 0));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(GroundSentinelRenderState state) {
        super.setupAnim(state);
        float age = state.ageInTicks;
        float pos = state.walkAnimationPos;
        float speed = Math.min(1, state.walkAnimationSpeed);
        this.head.yRot = Mth.clamp(state.yRot, -60, 60) * Mth.DEG_TO_RAD;
        this.head.xRot = state.xRot * Mth.DEG_TO_RAD;
        // 站着时机身微微起伏
        this.body.y += Mth.sin(age * 0.1F) * 0.3F * (1 - speed);
        if (state.pouncing) {
            // 扑在空中：前腿往前伸，后腿往后蹬
            for (int i = 0; i < 4; i++) {
                this.legs[i].xRot = i < 2 ? -1.1F : 0.9F;
                this.shins[i].xRot = i < 2 ? 0.3F : 0;
            }
            this.body.xRot = -0.15F;
        } else {
            for (int i = 0; i < 4; i++) {
                float swing = pos * 0.8F + PHASE[i];
                this.legs[i].xRot = Mth.cos(swing) * 0.9F * speed;
                // 往前迈的时候膝盖弯起来
                this.shins[i].xRot = Math.max(0, -Mth.sin(swing)) * 0.8F * speed;
            }
        }
        if (state.charge > 0) {
            // 蓄力：伏低身子，前腿撑开，头往下压
            float c = state.charge;
            this.body.y += c * 1.5F;
            this.legs[0].xRot -= c * 0.35F;
            this.legs[1].xRot -= c * 0.35F;
            this.legs[2].xRot += c * 0.25F;
            this.legs[3].xRot += c * 0.25F;
            this.shins[0].xRot += c * 0.5F;
            this.shins[1].xRot += c * 0.5F;
            this.head.xRot += c * 0.15F;
            this.head.y += Mth.sin(age * 2.5F) * 0.15F * c;
        }
        this.tail.zRot = Mth.sin(age * 0.2F) * 0.15F;
        this.tail.xRot = -0.5F + Mth.sin(age * 0.13F) * 0.08F + (state.hostile ? -0.3F : 0);
    }
}
