package io.github.sweetcornna.universe.client.exocraft;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * 潜艇的模型（ADR-0026）：照阿尔文号这类深潜器做的单人潜艇。粗短的艇身，前半截顶上一个罩住驾驶员上半身的玻璃舱，
 * 后半截是甲板和舱口；艇头两盏灯、一对机械臂，两侧各一个侧推舱；艇尾是尾锥、导流罩里的四叶螺旋桨和十字尾翼。
 * 艇头朝 -z，长 3.5 格（含螺旋桨和机械臂）、宽 1.75 格；原点下方 24 像素是艇底。贴图 256×128。
 * <p>
 * 玻璃单独是一个模型（{@link #glass()}），用半透明的渲染类型画；光柱在艇身模型里，只有发光层上有颜色，没人开或者出了水就不画。
 * 贴图的展开方式和 {@code tools/m14_submarine_textures.py} 里的方块列表一一对应（texOffs 和尺寸），改了这里要同步改那边。
 */
public final class SubmarineModel extends EntityModel<SubmarineRenderState> {
    /** 驾驶舱的中心（模型像素）：渲染器绕这里俯仰，驾驶员的头总在玻璃罩里。 */
    public static final float PIVOT_Y = 6;
    public static final float PIVOT_Z = -7;

    private final ModelPart propeller;
    private final ModelPart beam;

    private SubmarineModel(ModelPart root) {
        super(root);
        ModelPart hull = root.getChild("hull");
        this.propeller = hull.getChild("propeller");
        this.beam = hull.getChild("beam");
    }

    public static SubmarineModel create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition hull = root.addOrReplaceChild("hull", CubeListBuilder.create()
                        // 艇身、后甲板、驾驶舱底座
                        .texOffs(0, 0).addBox(-10, 8, -20, 20, 14, 40)
                        .texOffs(0, 54).addBox(-8, 5, 0, 16, 3, 20)
                        .texOffs(128, 96).addBox(-8.5F, 7, -18.5F, 17, 1.5F, 21)
                        // 艇头和尖头的声呐罩
                        .texOffs(72, 54).addBox(-8, 10, -24, 16, 10, 4)
                        .texOffs(72, 68).addBox(-5, 12, -26, 10, 6, 2)
                        // 尾锥
                        .texOffs(0, 77).addBox(-7, 10, 20, 14, 10, 5)
                        // 导流罩：上下、左右四根
                        .texOffs(38, 79).addBox(-8, 8, 25, 16, 1, 4)
                        .texOffs(38, 79).addBox(-8, 21, 25, 16, 1, 4)
                        .texOffs(78, 79).addBox(-8, 9, 25, 1, 12, 4)
                        .texOffs(78, 79).mirror().addBox(7, 9, 25, 1, 12, 4).mirror(false)
                        // 尾翼：背上的竖鳍、腹下的小鳍、两侧的水平鳍
                        .texOffs(88, 81).addBox(-0.5F, -1, 12, 1, 6, 10)
                        .texOffs(110, 81).addBox(-0.5F, 22, 14, 1, 2, 7)
                        .texOffs(38, 84).addBox(-15, 15, 16, 5, 1, 7)
                        .texOffs(38, 84).mirror().addBox(10, 15, 16, 5, 1, 7).mirror(false)
                        // 两侧的侧推舱
                        .texOffs(0, 92).addBox(-14, 13, -8, 4, 6, 14)
                        .texOffs(0, 92).mirror().addBox(10, 13, -8, 4, 6, 14).mirror(false)
                        // 艇头两盏灯、一对折起来的机械臂
                        .texOffs(36, 92).addBox(-8, 11, -25, 3, 3, 1)
                        .texOffs(36, 92).mirror().addBox(5, 11, -25, 3, 3, 1).mirror(false)
                        .texOffs(44, 92).addBox(-7, 19, -27, 2, 2, 7)
                        .texOffs(44, 92).mirror().addBox(5, 19, -27, 2, 2, 7).mirror(false),
                PartPose.ZERO);
        // 螺旋桨：桨毂和十字的四片桨叶，绕艇的纵轴转
        hull.addOrReplaceChild("propeller", CubeListBuilder.create()
                        .texOffs(96, 68).addBox(-1.5F, -1.5F, -2, 3, 3, 4)
                        .texOffs(110, 68).addBox(-0.5F, -6, -0.5F, 1, 12, 1)
                        .texOffs(38, 77).addBox(-6, -0.5F, -0.5F, 12, 1, 1),
                PartPose.offset(0, 15, 27));
        // 灯照出去的光柱：三截越来越粗，越远越淡
        hull.addOrReplaceChild("beam", CubeListBuilder.create()
                        .texOffs(192, 68).addBox(-4, 10, -38, 8, 6, 12)
                        .texOffs(128, 68).addBox(-7, 8, -56, 14, 10, 18)
                        .texOffs(128, 26).addBox(-11, 5, -82, 22, 16, 26),
                PartPose.ZERO);
        return new SubmarineModel(LayerDefinition.create(mesh, 256, 128).bakeRoot());
    }

    /** 驾驶舱的玻璃罩：四层越往上越小，拼成一个圆顶。几块共用贴图上同一片玻璃。 */
    public static Glass glass() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("dome", CubeListBuilder.create()
                        .texOffs(128, 0).addBox(-8, 3, -18, 16, 5, 20)
                        .texOffs(128, 0).addBox(-7, -1, -17, 14, 3.9F, 18)
                        .texOffs(128, 0).addBox(-5, -4, -15, 10, 2.9F, 14)
                        .texOffs(128, 0).addBox(-3, -5, -12, 6, 0.9F, 8),
                PartPose.ZERO);
        return new Glass(LayerDefinition.create(mesh, 256, 128).bakeRoot());
    }

    @Override
    public void setupAnim(SubmarineRenderState state) {
        super.setupAnim(state);
        this.propeller.zRot = state.propellerSpin;
        this.beam.visible = state.lights;
    }

    /** 玻璃罩，没有动画。 */
    public static final class Glass extends EntityModel<SubmarineRenderState> {
        private Glass(ModelPart root) {
            super(root);
        }
    }
}
