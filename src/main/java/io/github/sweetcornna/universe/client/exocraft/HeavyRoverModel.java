package io.github.sweetcornna.universe.client.exocraft;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * 重型车的模型（ADR-0026）：八轮运输车。长底盘，车头是封闭的驾驶室（顶上一排灯、一门采矿激光、一根天线），
 * 中间一节橙色货舱，车尾一台精炼机（带烟囱，背面一扇观察窗，炼东西时亮）。左右各四个大轮子。
 * 车头朝 -z，宽 2.2 格、长 3.5 格；原点下方 24 像素是地面。贴图 256×128。
 *
 * <p>贴图的展开方式和 {@code tools/m14_vehicle_textures.py} 里的方块列表一一对应（texOffs 和尺寸），改了这里要同步改那边。
 */
public final class HeavyRoverModel extends EntityModel<ExocraftRenderState> {
    /** 四根轴的 z（像素）：前两根跟着方向盘偏。 */
    private static final float[] AXLES = {-19, -6, 7, 20};
    private final ModelPart[] wheels = new ModelPart[8];
    private final ModelPart turret;
    private final ModelPart furnace;

    private HeavyRoverModel(ModelPart root) {
        super(root);
        for (int i = 0; i < 8; i++) {
            this.wheels[i] = root.getChild("wheel" + i);
        }
        ModelPart body = root.getChild("body");
        this.turret = body.getChild("turret");
        this.furnace = body.getChild("furnace");
    }

    public static HeavyRoverModel create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        // 底盘、驾驶室、货舱、精炼机
                        .texOffs(0, 0).addBox(-13, 12, -26, 26, 6, 52)
                        .texOffs(156, 0).addBox(-12, -15, -26, 24, 27, 16)
                        .texOffs(0, 58).addBox(-12, -6, -8, 24, 18, 18)
                        .texOffs(84, 58).addBox(-11, -8, 11, 22, 20, 15)
                        // 精炼机的烟囱、驾驶室顶上的灯排
                        .texOffs(180, 58).addBox(5, -16, 18, 3, 8, 3)
                        .texOffs(192, 58).addBox(-10, -17, -25, 20, 2, 3)
                        // 两盏大灯、天线、前保险杠
                        .texOffs(68, 94).addBox(6, 2, -27, 4, 3, 1)
                        .texOffs(68, 94).mirror().addBox(-10, 2, -27, 4, 3, 1).mirror(false)
                        .texOffs(78, 94).addBox(9, -25, -12, 1, 10, 1)
                        .texOffs(82, 94).addBox(-12, 13, -28, 24, 3, 2),
                PartPose.ZERO);
        // 精炼机背面的观察窗：炼东西时才画（发光层把它点成炉火色）
        body.addOrReplaceChild("furnace", CubeListBuilder.create().texOffs(158, 58).addBox(-5, -2, 26, 10, 6, 1), PartPose.ZERO);
        // 采矿激光：驾驶室顶上，炮管朝前
        body.addOrReplaceChild("turret", CubeListBuilder.create()
                        .texOffs(30, 94).addBox(-2.5F, -3, -2.5F, 5, 3, 5)
                        .texOffs(50, 94).addBox(-1, -2.5F, -9, 2, 2, 7),
                PartPose.offset(0, -15, -13));
        for (int i = 0; i < 8; i++) {
            float x = i % 2 == 0 ? 15 : -15;
            CubeListBuilder wheel = CubeListBuilder.create().texOffs(0, 94);
            if (x < 0) {
                wheel.mirror();
            }
            root.addOrReplaceChild("wheel" + i, wheel.addBox(-2.5F, -5, -5, 5, 10, 10), PartPose.offset(x, 19, AXLES[i / 2]));
        }
        return new HeavyRoverModel(LayerDefinition.create(mesh, 256, 128).bakeRoot());
    }

    @Override
    public void setupAnim(ExocraftRenderState state) {
        super.setupAnim(state);
        // 轮子比探索车的小一圈（半径 5 像素对 6），同样的路要多转一点
        for (int i = 0; i < 8; i++) {
            this.wheels[i].xRot = state.wheelSpin * 1.2F;
        }
        // 前两根轴跟着方向盘偏，后两根反着偏一点（八轮车的多轴转向）
        for (int i = 0; i < 8; i++) {
            int axle = i / 2;
            this.wheels[i].yRot = axle == 0 ? state.steer : axle == 1 ? state.steer * 0.5F : axle == 3 ? -state.steer * 0.3F : 0;
        }
        this.furnace.visible = state.refining;
        if (state.laser) {
            this.turret.y += (float) Math.sin(state.ageInTicks * 2.3F) * 0.15F;
        }
    }
}
