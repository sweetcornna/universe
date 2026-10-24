package io.github.sweetcornna.universe.client.fossil;

import java.util.List;
import java.util.stream.IntStream;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * 石哨兵的模型（ADR-0034 §三）：一块 10 格见方的大石头当身子，正面一道裂开的眼缝，头顶一块冠石，下面一块垂着的颚石；
 * 两边各浮着一块拳头大的石头，扔石块时一起抬起来往前甩；身边绕着三块小碎石。贴图 64×64，展开方式和
 * {@code tools/textures/fossil.py} 里的方块列表一一对应，改了这里要同步改那边。
 *
 * <ul>
 *   <li>身子 10×10×10：(0, 0)；冠石 6×3×5：(0, 20)；颚石 8×2×8：(22, 20)；</li>
 *   <li>拳石 5×6×5：(0, 30)；碎石 3×3×3：(20, 30)；眉石 11×2×4：(32, 30)。</li>
 * </ul>
 */
public final class StoneSentryModel extends EntityModel<StoneSentryRenderState> {
    private static final int PEBBLES = 3;
    private final ModelPart body;
    private final ModelPart jaw;
    private final ModelPart leftFist;
    private final ModelPart rightFist;
    private final ModelPart ring;
    private final List<ModelPart> pebbles;

    private StoneSentryModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.jaw = this.body.getChild("jaw");
        this.leftFist = root.getChild("left_fist");
        this.rightFist = root.getChild("right_fist");
        this.ring = root.getChild("ring");
        this.pebbles = IntStream.range(0, PEBBLES).mapToObj(i -> this.ring.getChild("pebble" + i)).toList();
    }

    public static StoneSentryModel create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-5, -5, -5, 10, 10, 10)
                        .texOffs(0, 20).addBox(-3, -8, -2, 6, 3, 5)
                        .texOffs(32, 30).addBox(-5.5F, -5.5F, -5.5F, 11, 2, 4),
                PartPose.offset(0, 13, 0));
        body.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(22, 20).addBox(-4, 0, -4, 8, 2, 8),
                PartPose.offset(0, 5.5F, 0.5F));
        root.addOrReplaceChild("right_fist", CubeListBuilder.create().texOffs(0, 30).addBox(-2.5F, -3, -2.5F, 5, 6, 5),
                PartPose.offset(-8.5F, 15, -0.5F));
        root.addOrReplaceChild("left_fist", CubeListBuilder.create().texOffs(0, 30).mirror().addBox(-2.5F, -3, -2.5F, 5, 6, 5),
                PartPose.offset(8.5F, 15, -0.5F));
        PartDefinition ring = root.addOrReplaceChild("ring", CubeListBuilder.create(), PartPose.offset(0, 13, 0));
        for (int i = 0; i < PEBBLES; i++) {
            float a = i * Mth.TWO_PI / PEBBLES;
            ring.addOrReplaceChild("pebble" + i, CubeListBuilder.create().texOffs(20, 30).addBox(-1.5F, -1.5F, -1.5F, 3, 3, 3),
                    PartPose.offsetAndRotation(Mth.cos(a) * 10, (i - 1) * 2.5F, Mth.sin(a) * 10, a, a * 0.7F, 0));
        }
        return new StoneSentryModel(LayerDefinition.create(mesh, 64, 64).bakeRoot());
    }

    @Override
    public void setupAnim(StoneSentryRenderState state) {
        super.setupAnim(state);
        float age = state.ageInTicks;
        // 浮起来：刚醒时整个往下沉着，慢慢抬上来；之后上下浮动
        float sink = state.rising * 12;
        float bob = Mth.sin(age * 0.09F + state.bobPhase) * 0.9F;
        this.body.yRot = state.yRot * Mth.DEG_TO_RAD;
        this.body.xRot = state.xRot * Mth.DEG_TO_RAD * 0.5F;
        this.body.y += sink + bob;
        // 点着了：身子抖
        if (state.fuse > 0) {
            float shake = 0.4F + state.fuse * 0.8F;
            this.body.x += Mth.sin(age * 2.7F) * shake;
            this.body.z += Mth.cos(age * 3.1F) * shake;
        }
        this.jaw.xRot = 0.08F + Mth.sin(age * 0.07F) * 0.05F + state.windup * 0.35F;
        // 拳石：平时跟着身子浮，抬手时举过头顶往前甩
        float lift = state.windup;
        float yaw = state.yRot * Mth.DEG_TO_RAD;
        for (int side = -1; side <= 1; side += 2) {
            ModelPart fist = side < 0 ? this.rightFist : this.leftFist;
            float ox = side * 8.5F;
            float oz = -0.5F - lift * 2;
            fist.x = ox * Mth.cos(yaw) + oz * Mth.sin(yaw);
            fist.z = -ox * Mth.sin(yaw) + oz * Mth.cos(yaw);
            fist.y += sink + Mth.sin(age * 0.11F + state.bobPhase + side) * 1.1F - lift * 9;
            fist.yRot = yaw;
            fist.xRot = -lift * 1.2F;
            fist.zRot = side * (0.15F + Mth.sin(age * 0.05F + side) * 0.06F);
        }
        // 碎石绕着转，点着了转得飞快
        this.ring.y += sink * 0.6F + bob * 0.5F;
        this.ring.yRot = age * (state.fuse > 0 ? 0.45F : 0.05F) + state.bobPhase;
        for (int i = 0; i < this.pebbles.size(); i++) {
            ModelPart pebble = this.pebbles.get(i);
            pebble.y += Mth.sin(age * 0.13F + i * 2.1F) * 1.2F;
            pebble.xRot += age * 0.03F;
        }
    }
}
