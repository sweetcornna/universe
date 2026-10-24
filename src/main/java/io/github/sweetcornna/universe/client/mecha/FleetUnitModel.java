package io.github.sweetcornna.universe.client.mecha;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.core.mecha.fleet.FleetUnitKind;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * 舰队单位的模型（ADR-0033 §一）。三种地面单位是巴掌大的无人机：原型机是一块方机身、两侧两个推进舱、机头下面一根激光发射管；
 * 精准无人机细长，前掠的两片翼、尾鳍和机头下面更长的发射管；攻击无人机厚实，三角翼、两台发动机，腹下一门等离子炮。
 * 两种舰船：护卫舰是长条的船身、两侧挂着发动机舱、舰桥后置，舰首上面两座双联激光炮塔；
 * 驱逐舰按一半的尺寸建模、渲染时放大一倍：长船身、两舷的装甲带、中间的上层建筑和桅楼，舰首一座双管等离子主炮，
 * 甲板四角四座点防御炮。
 *
 * <p>贴图的展开方式和 {@code tools/textures/fleet.py} 里的方块列表一一对应，改了这里要同步改那边。
 */
public final class FleetUnitModel extends EntityModel<FleetUnitRenderState> {
    private final ModelPart body;
    /** 跟着目标转的炮塔（舰船），没有就是空的。 */
    private final List<ModelPart> turrets;
    /** 推进舱、翼：轻轻摆动。 */
    private final List<ModelPart> flaps;
    private final float baseY;
    private final float bob;

    private FleetUnitModel(ModelPart root, int turretCount, int flapCount, float bob) {
        super(root);
        this.body = root.getChild("body");
        this.baseY = this.body.y;
        this.bob = bob;
        this.turrets = new ArrayList<>();
        for (int i = 0; i < turretCount; i++) {
            this.turrets.add(this.body.getChild("turret" + i));
        }
        this.flaps = new ArrayList<>();
        for (int i = 0; i < flapCount; i++) {
            this.flaps.add(this.body.getChild("flap" + i));
        }
    }

    public static FleetUnitModel of(FleetUnitKind kind) {
        return switch (kind) {
            case PROTOTYPE -> prototype();
            case PRECISION_DRONE -> precisionDrone();
            case ATTACK_DRONE -> attackDrone();
            case CORVETTE -> corvette();
            case DESTROYER -> destroyer();
        };
    }

    /** 原型机：贴图 64×32。 */
    static FleetUnitModel prototype() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3, -2, -4, 6, 4, 8)
                        .texOffs(0, 14).addBox(-0.5F, 2, -6, 1, 1, 3)
                        .texOffs(12, 14).addBox(-0.5F, -5, 1.5F, 1, 3, 1),
                PartPose.offset(0, 20, 0));
        body.addOrReplaceChild("flap0", CubeListBuilder.create().texOffs(30, 0).addBox(-1, -1, -2, 2, 2, 4),
                PartPose.offset(-4, 0.5F, 1));
        body.addOrReplaceChild("flap1", CubeListBuilder.create().texOffs(30, 0).mirror().addBox(-1, -1, -2, 2, 2, 4),
                PartPose.offset(4, 0.5F, 1));
        return new FleetUnitModel(LayerDefinition.create(mesh, 64, 32).bakeRoot(), 0, 2, 0.5F);
    }

    /** 精准无人机：贴图 64×32。 */
    static FleetUnitModel precisionDrone() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-2, -1.5F, -5, 4, 3, 10)
                        .texOffs(0, 15).addBox(-0.5F, 1.5F, -8, 1, 1, 4)
                        .texOffs(30, 7).addBox(-0.5F, -4.5F, 2, 1, 3, 3)
                        .texOffs(12, 15).addBox(-1.5F, -1, 5, 3, 2, 1),
                PartPose.offset(0, 20, 0));
        body.addOrReplaceChild("flap0", CubeListBuilder.create().texOffs(30, 0).addBox(-5, -0.5F, -2, 5, 1, 4),
                PartPose.offsetAndRotation(-2, 0.5F, 0, 0, -0.35F, 0));
        body.addOrReplaceChild("flap1", CubeListBuilder.create().texOffs(30, 0).mirror().addBox(0, -0.5F, -2, 5, 1, 4),
                PartPose.offsetAndRotation(2, 0.5F, 0, 0, 0.35F, 0));
        return new FleetUnitModel(LayerDefinition.create(mesh, 64, 32).bakeRoot(), 0, 2, 0.4F);
    }

    /** 攻击无人机：贴图 64×64。 */
    static FleetUnitModel attackDrone() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3, -2, -5, 6, 4, 10)
                        .texOffs(0, 16).addBox(-1, 2, -8, 2, 2, 6)
                        .texOffs(34, 9).addBox(-1.5F, -3, -4, 3, 1, 4)
                        .texOffs(18, 16).addBox(-3, -1, 5, 2, 2, 3)
                        .texOffs(18, 16).mirror().addBox(1, -1, 5, 2, 2, 3),
                PartPose.offset(0, 18.5F, 0));
        body.addOrReplaceChild("flap0", CubeListBuilder.create().texOffs(34, 0).addBox(-6, -0.5F, -3, 6, 1, 6),
                PartPose.offset(-3, 0.5F, 1));
        body.addOrReplaceChild("flap1", CubeListBuilder.create().texOffs(34, 0).mirror().addBox(0, -0.5F, -3, 6, 1, 6),
                PartPose.offset(3, 0.5F, 1));
        return new FleetUnitModel(LayerDefinition.create(mesh, 64, 64).bakeRoot(), 0, 2, 0.35F);
    }

    /** 护卫舰：贴图 128×128。 */
    static FleetUnitModel corvette() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-6, -4, -18, 12, 8, 36)
                        .texOffs(0, 46).addBox(-3, -8, 0, 6, 4, 8)
                        .texOffs(0, 68).addBox(-5, -3, 18, 10, 6, 2)
                        .texOffs(26, 68).addBox(-4, -2.5F, -22, 8, 5, 4)
                        .texOffs(72, 46).addBox(6, 0.5F, 1, 2, 1, 6)
                        .texOffs(72, 46).mirror().addBox(-8, 0.5F, 1, 2, 1, 6),
                PartPose.offset(0, 14.5F, 0));
        body.addOrReplaceChild("flap0", CubeListBuilder.create().texOffs(30, 46).addBox(-2, -2, -8, 4, 4, 16),
                PartPose.offset(-10, 1, 4));
        body.addOrReplaceChild("flap1", CubeListBuilder.create().texOffs(30, 46).mirror().addBox(-2, -2, -8, 4, 4, 16),
                PartPose.offset(10, 1, 4));
        for (int i = 0; i < 2; i++) {
            PartDefinition turret = body.addOrReplaceChild("turret" + i, CubeListBuilder.create().texOffs(0, 60).addBox(-1.5F, -2, -1.5F, 3, 2, 3),
                    PartPose.offset(0, -4, i == 0 ? -12 : -6));
            turret.addOrReplaceChild("barrels", CubeListBuilder.create()
                            .texOffs(14, 60).addBox(-1, -0.5F, -5, 1, 1, 5)
                            .texOffs(14, 60).addBox(0, -0.5F, -5, 1, 1, 5),
                    PartPose.offset(0, -1, -1.5F));
        }
        return new FleetUnitModel(LayerDefinition.create(mesh, 128, 128).bakeRoot(), 2, 2, 0.15F);
    }

    /** 驱逐舰：贴图 128×128，半尺寸。 */
    static FleetUnitModel destroyer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-6, -4, -20, 12, 8, 40)
                        .texOffs(0, 50).addBox(-4, -8, -4, 8, 4, 14)
                        .texOffs(46, 50).addBox(-2, -11, 2, 4, 3, 4)
                        .texOffs(0, 70).addBox(6, -1, -12, 2, 4, 24)
                        .texOffs(0, 70).mirror().addBox(-8, -1, -12, 2, 4, 24)
                        .texOffs(54, 76).addBox(-5.5F, -2, 20, 4, 4, 3)
                        .texOffs(54, 76).mirror().addBox(1.5F, -2, 20, 4, 4, 3)
                        .texOffs(70, 70).addBox(-4, -2.5F, -26, 8, 6, 6),
                PartPose.offset(0, 16, 0));
        PartDefinition main = body.addOrReplaceChild("turret0", CubeListBuilder.create().texOffs(64, 50).addBox(-2.5F, -2, -2.5F, 5, 2, 5),
                PartPose.offset(0, -4, -12));
        main.addOrReplaceChild("barrels", CubeListBuilder.create()
                        .texOffs(86, 50).addBox(-1.5F, -0.5F, -8, 1, 1, 8)
                        .texOffs(86, 50).addBox(0.5F, -0.5F, -8, 1, 1, 8),
                PartPose.offset(0, -1, -2));
        float[][] pd = {{-5, 6}, {5, 6}, {-5, -6}, {5, -6}};
        for (int i = 0; i < pd.length; i++) {
            PartDefinition turret = body.addOrReplaceChild("turret" + (i + 1), CubeListBuilder.create().texOffs(54, 70).addBox(-1, -1, -1, 2, 1, 2),
                    PartPose.offset(pd[i][0], -4, pd[i][1]));
            turret.addOrReplaceChild("barrel", CubeListBuilder.create().texOffs(100, 70).addBox(-0.5F, -0.5F, -3, 1, 1, 3),
                    PartPose.offset(0, -1, -1));
        }
        return new FleetUnitModel(LayerDefinition.create(mesh, 128, 128).bakeRoot(), 5, 0, 0.1F);
    }

    @Override
    public void setupAnim(FleetUnitRenderState state) {
        super.setupAnim(state);
        float age = state.ageInTicks;
        this.body.y = this.baseY + Mth.sin(age * 0.18F + state.phase) * this.bob;
        this.body.zRot = state.roll * Mth.DEG_TO_RAD;
        this.body.xRot = Mth.clamp(state.xRot, -40, 40) * Mth.DEG_TO_RAD * 0.6F;
        // 推进舱、翼跟着侧倾轻轻摆
        float sway = Mth.sin(age * 0.3F + state.phase) * 0.04F;
        for (int i = 0; i < this.flaps.size(); i++) {
            float side = i % 2 == 0 ? -1 : 1;
            this.flaps.get(i).zRot = sway * side + state.roll * Mth.DEG_TO_RAD * 0.15F;
        }
        // 炮塔转向目标；没有目标时朝前，慢慢左右扫
        for (int i = 0; i < this.turrets.size(); i++) {
            float idle = Mth.sin(age * 0.02F + state.phase + i) * 0.4F;
            // 和原版的头一样：相对机身的偏航角直接当 yRot
            this.turrets.get(i).yRot = state.aiming ? state.aimYaw * Mth.DEG_TO_RAD : idle;
        }
    }
}
