package io.github.sweetcornna.universe.client.exocraft;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * 悬浮车的模型（ADR-0026）：低矮的单座敞篷车，扁平的车身、斜着的挡风玻璃、车头一门采矿激光，车底四块推力垫，车尾两个喷口，两侧一对翼舱。
 * 车头朝 -z，宽 1.6 格、长 2.9 格；原点下方 24 像素是车底（离地还有 1 格，是悬浮的高度）。贴图 128×128。
 * 转弯时车身往里侧倾。
 *
 * <p>贴图的展开方式和 {@code tools/m14_vehicle_textures.py} 里的方块列表一一对应（texOffs 和尺寸），改了这里要同步改那边。
 */
public final class HoverCarModel extends EntityModel<ExocraftRenderState> {
    private final ModelPart body;
    private final ModelPart turret;

    private HoverCarModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.turret = this.body.getChild("turret");
    }

    public static HoverCarModel create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        // 车身、车底的裙板、车头
                        .texOffs(0, 0).addBox(-12, 14, -16, 24, 6, 32)
                        .texOffs(0, 38).addBox(-13, 20, -14, 26, 3, 28)
                        .texOffs(0, 69).addBox(-10, 15, -22, 20, 5, 6)
                        // 座椅和靠背
                        .texOffs(52, 69).addBox(-4, 12, -1, 8, 2, 7)
                        .texOffs(82, 69).addBox(-4, 5, 6, 8, 7, 2)
                        // 两侧的翼舱、车尾的喷口罩
                        .texOffs(38, 80).addBox(12, 15, 2, 3, 3, 12)
                        .texOffs(38, 80).mirror().addBox(-15, 15, 2, 3, 3, 12).mirror(false)
                        .texOffs(68, 80).addBox(-7, 13, 16, 14, 6, 4)
                        // 两个喷口
                        .texOffs(0, 95).addBox(-6, 14, 20, 4, 4, 1)
                        .texOffs(0, 95).mirror().addBox(2, 14, 20, 4, 4, 1).mirror(false)
                        // 四块推力垫
                        .texOffs(10, 95).addBox(-12, 23, -13, 6, 1, 6)
                        .texOffs(10, 95).addBox(6, 23, -13, 6, 1, 6)
                        .texOffs(10, 95).addBox(-12, 23, 7, 6, 1, 6)
                        .texOffs(10, 95).addBox(6, 23, 7, 6, 1, 6)
                        // 车头两盏灯
                        .texOffs(66, 95).addBox(5, 16, -23, 3, 2, 1)
                        .texOffs(66, 95).mirror().addBox(-8, 16, -23, 3, 2, 1).mirror(false),
                PartPose.ZERO);
        // 挡风玻璃：往后斜
        body.addOrReplaceChild("windshield", CubeListBuilder.create().texOffs(0, 80).addBox(-9, -6, 0, 18, 6, 1),
                PartPose.offsetAndRotation(0, 14, -11, -0.55F, 0, 0));
        // 采矿激光：底座和炮管，炮管朝前
        body.addOrReplaceChild("turret", CubeListBuilder.create()
                        .texOffs(34, 95).addBox(-2, -3, -2, 4, 3, 4)
                        .texOffs(50, 95).addBox(-1, -2.5F, -8, 2, 2, 6),
                PartPose.offset(0, 15, -17));
        return new HoverCarModel(LayerDefinition.create(mesh, 128, 128).bakeRoot());
    }

    @Override
    public void setupAnim(ExocraftRenderState state) {
        super.setupAnim(state);
        // 有人开时车身轻轻浮动，转弯往里侧倾
        if (state.hovering) {
            this.body.y += (float) Math.sin(state.ageInTicks * 0.15F) * 0.4F;
        }
        this.body.zRot = -state.steer * 0.35F;
        if (state.laser) {
            this.turret.y += (float) Math.sin(state.ageInTicks * 2.3F) * 0.15F;
        }
    }
}
