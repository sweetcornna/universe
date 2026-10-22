package io.github.sweetcornna.universe.client.darkfog.hive;

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
 * 太空巢穴建筑的模型（ADR-0028 §八），每种一个，贴图都是 128×128。模型按缩小的尺寸搭，渲染时按 {@link #scale()} 放大：
 * <ul>
 * <li>核心：宽底座、立柱、冠和尖塔，四面的肋，底下的龙骨，腰上一圈转动的方环（放大 6 倍，底座 9 格宽，比碰撞箱大一圈）。</li>
 * <li>光子接收器：底座、颈、一面带边框的接收盘和中间的发射杆，整个盘朝着恒星（放大 4 倍）。</li>
 * <li>等离子塔：底座、塔身、领圈，能转的炮塔和长炮管（放大 4 倍）。</li>
 * <li>相位激光塔：底座、细桅杆和四片翼，能转的炮头、透镜和两侧的发射器（放大 4 倍）。</li>
 * <li>枪骑港：后面的机库、前面的门框和门里发光的出口（放大 4 倍）。</li>
 * <li>座头鲸港：大底座、厅、屋脊、正面的大门和两侧的吊舱（放大 5 倍）。</li>
 * </ul>
 * 贴图的展开方式和 {@code tools/m16_hive_textures.py} 里的方块列表一一对应，改了这里要同步改那边。
 */
public final class HiveBuildingModel extends EntityModel<HiveBuildingRenderState> {
    private static final int TEXTURE = 128;
    private final ModelPart body;
    private final @Nullable ModelPart spin;
    private final @Nullable ModelPart head;
    private final float spinSpeed;
    private final float scale;

    private HiveBuildingModel(ModelPart root, float scale, float spinSpeed) {
        super(root);
        this.body = root.getChild("body");
        this.spin = this.body.hasChild("spin") ? this.body.getChild("spin") : null;
        this.head = this.body.hasChild("head") ? this.body.getChild("head") : null;
        this.spinSpeed = spinSpeed;
        this.scale = scale;
    }

    /** 渲染时放大几倍。 */
    public float scale() {
        return this.scale;
    }

    private static PartDefinition body(MeshDefinition mesh, CubeListBuilder cubes) {
        return mesh.getRoot().addOrReplaceChild("body", cubes, PartPose.offset(0, 24, 0));
    }

    private static HiveBuildingModel bake(MeshDefinition mesh, float scale, float spinSpeed) {
        return new HiveBuildingModel(LayerDefinition.create(mesh, TEXTURE, TEXTURE).bakeRoot(), scale, spinSpeed);
    }

    public static HiveBuildingModel core() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = body(mesh, CubeListBuilder.create()
                .texOffs(0, 0).addBox(-12, -6, -12, 24, 6, 24)
                .texOffs(0, 30).addBox(-8, -18, -8, 16, 12, 16)
                .texOffs(0, 58).addBox(-10, -21, -10, 20, 3, 20)
                .texOffs(96, 0).addBox(-2, -31, -2, 4, 10, 4)
                .texOffs(96, 14).addBox(-4, 0, -4, 8, 8, 8)
                .texOffs(64, 30).addBox(-1, -17, -12, 2, 11, 4)
                .texOffs(64, 30).addBox(-1, -17, 8, 2, 11, 4)
                .texOffs(80, 30).addBox(-12, -17, -1, 4, 11, 2)
                .texOffs(80, 30).addBox(8, -17, -1, 4, 11, 2));
        body.addOrReplaceChild("spin", CubeListBuilder.create()
                .texOffs(0, 81).addBox(-16, -1, -16, 32, 2, 3)
                .texOffs(0, 81).addBox(-16, -1, 13, 32, 2, 3)
                .texOffs(0, 86).addBox(-16, -1, -13, 3, 2, 26)
                .texOffs(0, 86).addBox(13, -1, -13, 3, 2, 26), PartPose.offset(0, -12, 0));
        return bake(mesh, 6, 0.03F);
    }

    public static HiveBuildingModel photonReceiver() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = body(mesh, CubeListBuilder.create()
                .texOffs(0, 0).addBox(-5, -6, -5, 10, 6, 10)
                .texOffs(40, 0).addBox(-2, -12, -2, 4, 6, 4));
        body.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 16).addBox(-10, -10, 0, 20, 20, 2)
                .texOffs(0, 40).addBox(-12, -12, -3, 24, 2, 3)
                .texOffs(0, 40).addBox(-12, 10, -3, 24, 2, 3)
                .texOffs(56, 16).addBox(-12, -10, -3, 2, 20, 3)
                .texOffs(56, 16).addBox(10, -10, -3, 2, 20, 3)
                .texOffs(0, 46).addBox(-1, -1, -9, 2, 2, 9)
                .texOffs(24, 46).addBox(-2, -2, -11, 4, 4, 2), PartPose.offset(0, -14, 0));
        return bake(mesh, 4, 0);
    }

    public static HiveBuildingModel plasmaTower() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = body(mesh, CubeListBuilder.create()
                .texOffs(0, 0).addBox(-8, -4, -8, 16, 4, 16)
                .texOffs(0, 20).addBox(-5, -14, -5, 10, 10, 10)
                .texOffs(64, 0).addBox(-7, -11, -7, 14, 2, 14));
        body.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 40).addBox(-6, -3, -6, 12, 6, 12)
                .texOffs(48, 20).addBox(-2, -2, -20, 4, 4, 14)
                .texOffs(84, 20).addBox(-3, -3, -12, 6, 6, 2), PartPose.offset(0, -17, 0));
        return bake(mesh, 4, 0);
    }

    public static HiveBuildingModel phaseLaser() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = body(mesh, CubeListBuilder.create()
                .texOffs(0, 0).addBox(-6, -3, -6, 12, 3, 12)
                .texOffs(48, 0).addBox(-2, -15, -2, 4, 12, 4)
                .texOffs(64, 0).addBox(-0.5F, -12, -5, 1, 8, 3)
                .texOffs(64, 0).addBox(-0.5F, -12, 2, 1, 8, 3)
                .texOffs(72, 0).addBox(-5, -12, -0.5F, 3, 8, 1)
                .texOffs(72, 0).addBox(2, -12, -0.5F, 3, 8, 1));
        body.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 16).addBox(-4, -3, -4, 8, 6, 8)
                .texOffs(32, 16).addBox(-1.5F, -1.5F, -8, 3, 3, 4)
                .texOffs(48, 16).addBox(-6, -1, -3, 2, 2, 5)
                .texOffs(48, 16).addBox(4, -1, -3, 2, 2, 5), PartPose.offset(0, -18, 0));
        return bake(mesh, 4, 0);
    }

    public static HiveBuildingModel lancerPort() {
        MeshDefinition mesh = new MeshDefinition();
        body(mesh, CubeListBuilder.create()
                .texOffs(0, 0).addBox(-10, -16, 0, 20, 16, 12)
                .texOffs(0, 28).addBox(-12, -20, -4, 24, 4, 4)
                .texOffs(0, 36).addBox(-12, -2, -4, 24, 2, 4)
                .texOffs(64, 0).addBox(-12, -18, -4, 4, 16, 4)
                .texOffs(64, 0).addBox(8, -18, -4, 4, 16, 4)
                .texOffs(0, 42).addBox(-8, -18, -1, 16, 16, 1)
                .texOffs(80, 0).addBox(-1, -19, 2, 2, 3, 8));
        return bake(mesh, 4, 0);
    }

    public static HiveBuildingModel humpbackPort() {
        MeshDefinition mesh = new MeshDefinition();
        body(mesh, CubeListBuilder.create()
                .texOffs(0, 0).addBox(-10, -3, -10, 20, 3, 20)
                .texOffs(0, 23).addBox(-8, -13, -8, 16, 10, 16)
                .texOffs(64, 23).addBox(-3, -16, -9, 6, 3, 18)
                .texOffs(0, 49).addBox(-5, -11, -9, 10, 7, 1)
                .texOffs(24, 49).addBox(-12, -8, -4, 4, 5, 8)
                .texOffs(24, 49).addBox(8, -8, -4, 4, 5, 8));
        return bake(mesh, 5, 0);
    }

    @Override
    public void setupAnim(HiveBuildingRenderState state) {
        super.setupAnim(state);
        float age = state.ageInTicks;
        if (this.spin != null) {
            this.spin.yRot = state.powered ? age * this.spinSpeed + state.phase : state.phase;
            if (state.powered) {
                this.spin.y += Mth.sin(age * 0.08F + state.phase) * 0.5F;
            }
        }
        if (this.head != null) {
            this.head.yRot = state.headYaw;
            this.head.xRot = state.headPitch;
        }
        // 太空里的东西都悬着，慢慢上下浮
        this.body.y += Mth.sin(age * 0.03F + state.phase) * 0.4F;
    }
}
