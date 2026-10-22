package io.github.sweetcornna.universe.client.darkfog;

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
 * 黑雾建筑的模型（ADR-0028 §二），每种一个，贴图都是 64×64。模型按缩小的尺寸搭，渲染时按 {@link #scale()} 放大，
 * 像素粗一些，免得贴图太大：
 * <ul>
 * <li>钻芯：底座、立柱、顶上的冠和尖塔，立柱外面一圈转动的环（放大 4 倍）。</li>
 * <li>营地：一块平台、四根柱子、顶上的框，中间一颗转动的核（放大 3 倍）。三种营地同一个模型，发光的颜色不同。</li>
 * <li>高能激光塔：底座、细高的桅杆、顶上能转的炮头和透镜（放大 2 倍）。</li>
 * <li>等离子哨塔：底座、塔身、能转的炮塔和朝天斜着的炮管（放大 2 倍）。</li>
 * <li>中继站：悬在空中的机身、四根伸出去的臂、底下的发射口和顶上的鳍（放大 3 倍）。</li>
 * </ul>
 * 贴图的展开方式和 {@code tools/m16_dark_fog_textures.py} 里的方块列表一一对应，改了这里要同步改那边。
 */
public final class DarkFogBuildingModel extends EntityModel<DarkFogBuildingRenderState> {
    private final ModelPart body;
    private final @Nullable ModelPart spin;
    private final @Nullable ModelPart head;
    private final float spinSpeed;
    private final float scale;
    private final boolean floats;

    private DarkFogBuildingModel(ModelPart root, float scale, float spinSpeed, boolean floats) {
        super(root);
        this.body = root.getChild("body");
        this.spin = this.body.hasChild("spin") ? this.body.getChild("spin") : null;
        this.head = this.body.hasChild("head") ? this.body.getChild("head") : null;
        this.spinSpeed = spinSpeed;
        this.scale = scale;
        this.floats = floats;
    }

    /** 渲染时放大几倍。 */
    public float scale() {
        return this.scale;
    }

    private static PartDefinition body(MeshDefinition mesh, CubeListBuilder cubes) {
        return mesh.getRoot().addOrReplaceChild("body", cubes, PartPose.offset(0, 24, 0));
    }

    public static DarkFogBuildingModel core() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = body(mesh, CubeListBuilder.create()
                .texOffs(0, 0).addBox(-8, -3, -8, 16, 3, 16)
                .texOffs(0, 19).addBox(-4, -14, -4, 8, 11, 8)
                .texOffs(0, 38).addBox(-6, -17, -6, 12, 3, 12)
                .texOffs(48, 38).addBox(-1, -22, -1, 2, 5, 2));
        body.addOrReplaceChild("spin", CubeListBuilder.create().texOffs(0, 53).addBox(-5, -0.5F, -5, 10, 1, 10), PartPose.offset(0, -8, 0));
        return new DarkFogBuildingModel(LayerDefinition.create(mesh, 64, 64).bakeRoot(), 4, 0.05F, false);
    }

    public static DarkFogBuildingModel camp() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = body(mesh, CubeListBuilder.create()
                .texOffs(0, 0).addBox(-8, -2, -8, 16, 2, 16)
                .texOffs(0, 18).addBox(-7, -12, -7, 2, 10, 2)
                .texOffs(0, 18).addBox(5, -12, -7, 2, 10, 2)
                .texOffs(0, 18).addBox(-7, -12, 5, 2, 10, 2)
                .texOffs(0, 18).addBox(5, -12, 5, 2, 10, 2)
                .texOffs(0, 30).addBox(-7, -14, -7, 14, 2, 14));
        body.addOrReplaceChild("spin", CubeListBuilder.create().texOffs(0, 46).addBox(-3, -3, -3, 6, 6, 6), PartPose.offset(0, -7, 0));
        return new DarkFogBuildingModel(LayerDefinition.create(mesh, 64, 64).bakeRoot(), 3, 0.08F, false);
    }

    public static DarkFogBuildingModel laserTower() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = body(mesh, CubeListBuilder.create()
                .texOffs(0, 0).addBox(-6, -4, -6, 12, 4, 12)
                .texOffs(0, 16).addBox(-2, -24, -2, 4, 20, 4));
        body.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(16, 16).addBox(-4, -3, -4, 8, 6, 8)
                        .texOffs(48, 0).addBox(-1.5F, -1.5F, -6, 3, 3, 2),
                PartPose.offset(0, -27, 0));
        return new DarkFogBuildingModel(LayerDefinition.create(mesh, 64, 64).bakeRoot(), 2, 0, false);
    }

    public static DarkFogBuildingModel plasmaSentry() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = body(mesh, CubeListBuilder.create()
                .texOffs(0, 0).addBox(-8, -3, -8, 16, 3, 16)
                .texOffs(0, 19).addBox(-5, -12, -5, 10, 9, 10));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 38).addBox(-4, -4, -4, 8, 4, 8),
                PartPose.offset(0, -12, 0));
        head.addOrReplaceChild("barrel", CubeListBuilder.create().texOffs(32, 38).addBox(-1.5F, -1.5F, -9, 3, 3, 9),
                PartPose.offsetAndRotation(0, -2, -1, 0.6F, 0, 0));
        return new DarkFogBuildingModel(LayerDefinition.create(mesh, 64, 64).bakeRoot(), 2, 0, false);
    }

    public static DarkFogBuildingModel relay() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-6, -4, -6, 12, 4, 12)
                        .texOffs(16, 16).addBox(-2, 0, -2, 4, 3, 4)
                        .texOffs(32, 16).addBox(-0.5F, -7, -3, 1, 3, 6),
                PartPose.offset(0, 20, 0));
        PartDefinition spin = body.addOrReplaceChild("spin", CubeListBuilder.create(), PartPose.offset(0, -2, 0));
        for (int i = 0; i < 4; i++) {
            spin.addOrReplaceChild("arm" + i, CubeListBuilder.create().texOffs(0, 16).addBox(-1, -1, 0, 2, 2, 6),
                    PartPose.offsetAndRotation(Mth.sin(i * Mth.HALF_PI) * 6, 0, Mth.cos(i * Mth.HALF_PI) * 6, 0, i * Mth.HALF_PI, 0));
        }
        return new DarkFogBuildingModel(LayerDefinition.create(mesh, 64, 64).bakeRoot(), 3, 0.02F, true);
    }

    @Override
    public void setupAnim(DarkFogBuildingRenderState state) {
        super.setupAnim(state);
        float age = state.ageInTicks;
        if (this.spin != null) {
            this.spin.yRot = state.powered ? age * this.spinSpeed : 0;
            if (state.powered) {
                this.spin.y += Mth.sin(age * 0.1F + state.phase) * 0.4F;
            }
        }
        if (this.head != null) {
            this.head.yRot = state.headYaw;
        }
        if (this.floats) {
            // 中继站在空中上下浮
            this.body.y += Mth.sin(age * 0.05F + state.phase) * 0.6F;
        }
    }
}
