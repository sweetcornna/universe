package io.github.sweetcornna.universe.client.exocraft;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * 探索车的模型（ADR-0017）：敞篷的越野车，底盘、发动机罩、防滚架、座椅，车尾一只货箱，发动机罩上架着采矿激光，四个轮子。
 * 车头朝 -z，宽 1.75 格、长 2.25 格；原点下方 24 像素是地面（和原版生物模型一样）。贴图 128×128。
 *
 * <p>贴图的展开方式和 {@code tools/exocraft_textures.py} 里的方块列表一一对应（texOffs 和尺寸），改了这里要同步改那边。
 */
public final class ExocraftModel extends EntityModel<ExocraftRenderState> {
    private static final String[] WHEELS = {"wheel_front_left", "wheel_front_right", "wheel_back_left", "wheel_back_right"};
    private final ModelPart[] wheels = new ModelPart[4];
    private final ModelPart turret;

    private ExocraftModel(ModelPart root) {
        super(root);
        for (int i = 0; i < 4; i++) {
            this.wheels[i] = root.getChild(WHEELS[i]);
        }
        this.turret = root.getChild("body").getChild("turret");
    }

    public static ExocraftModel create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        // 底盘
                        .texOffs(0, 0).addBox(-10, 9, -18, 20, 8, 36)
                        // 发动机罩、车尾货箱
                        .texOffs(32, 44).addBox(-9, 6, -18, 18, 3, 12)
                        .texOffs(0, 68).addBox(-9, 3, 8, 18, 6, 10)
                        // 座椅和靠背
                        .texOffs(56, 68).addBox(-4, 4, -2, 8, 5, 8)
                        .texOffs(88, 68).addBox(-4, -7, 5, 8, 11, 2)
                        // 防滚架：四根立柱、两根纵梁、前后两根横梁
                        .texOffs(92, 44).addBox(-10, -6, -5, 2, 15, 2)
                        .texOffs(92, 44).mirror().addBox(8, -6, -5, 2, 15, 2).mirror(false)
                        .texOffs(92, 44).addBox(-10, -6, 6, 2, 15, 2)
                        .texOffs(92, 44).mirror().addBox(8, -6, 6, 2, 15, 2).mirror(false)
                        .texOffs(0, 86).addBox(-10, -8, -5, 2, 2, 13)
                        .texOffs(0, 86).mirror().addBox(8, -8, -5, 2, 2, 13).mirror(false)
                        .texOffs(32, 86).addBox(-9, -8, -5, 18, 2, 2)
                        .texOffs(32, 86).addBox(-9, -8, 6, 18, 2, 2)
                        // 挡泥板（左右各两块）
                        .texOffs(72, 86).addBox(-15, 9, -17, 5, 2, 12)
                        .texOffs(72, 86).mirror().addBox(10, 9, -17, 5, 2, 12).mirror(false)
                        .texOffs(72, 86).addBox(-15, 9, 5, 5, 2, 12)
                        .texOffs(72, 86).mirror().addBox(10, 9, 5, 5, 2, 12).mirror(false)
                        // 车头两盏大灯、车尾天线
                        .texOffs(32, 104).addBox(-8, 6, -19, 3, 2, 1)
                        .texOffs(32, 104).mirror().addBox(5, 6, -19, 3, 2, 1).mirror(false)
                        .texOffs(40, 104).addBox(7, -7, 15, 1, 10, 1),
                PartPose.ZERO);
        // 采矿激光：底座和炮管，炮管朝前
        body.addOrReplaceChild("turret", CubeListBuilder.create()
                        .texOffs(0, 104).addBox(-2, -3, -2, 4, 3, 4)
                        .texOffs(16, 104).addBox(-1, -2.5F, -8, 2, 2, 6),
                PartPose.offset(0, 6, -12));
        float[][] wheelPos = {{-12.5F, -11}, {12.5F, -11}, {-12.5F, 11}, {12.5F, 11}};
        for (int i = 0; i < 4; i++) {
            CubeListBuilder wheel = CubeListBuilder.create().texOffs(0, 44);
            if (wheelPos[i][0] > 0) {
                wheel.mirror();
            }
            root.addOrReplaceChild(WHEELS[i], wheel.addBox(-2, -6, -6, 4, 12, 12), PartPose.offset(wheelPos[i][0], 18, wheelPos[i][1]));
        }
        return new ExocraftModel(LayerDefinition.create(mesh, 128, 128).bakeRoot());
    }

    @Override
    public void setupAnim(ExocraftRenderState state) {
        super.setupAnim(state);
        for (ModelPart wheel : this.wheels) {
            wheel.xRot = state.wheelSpin;
        }
        // 前轮跟着方向盘偏一点
        this.wheels[0].yRot = state.steer;
        this.wheels[1].yRot = state.steer;
        // 激光开着时炮塔微微抖
        if (state.laser) {
            this.turret.y += (float) Math.sin(state.ageInTicks * 2.3F) * 0.15F;
        }
    }
}
