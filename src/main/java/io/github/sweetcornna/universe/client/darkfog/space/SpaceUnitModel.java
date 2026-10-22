package io.github.sweetcornna.universe.client.darkfog.space;

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
 * 太空单位的模型（ADR-0028 §八）。枪骑是一支细长的机身，机头伸出两根发光的长叉（"枪"），两片后掠翼、两片外倾的尾鳍；
 * 座头鲸是一条胖的鲸鱼形的船：方圆的船身、垂下来的腹部，两片大胸鳍，后面一副上下摆的尾叶，背上一座双管炮塔。
 * 座头鲸按一半的尺寸建模，渲染时放大一倍。
 *
 * <p>贴图的展开方式和 {@code tools/m16_space_unit_textures.py} 里的方块列表一一对应，改了这里要同步改那边。
 */
public final class SpaceUnitModel extends EntityModel<SpaceUnitRenderState> {
    private final ModelPart body;
    private final List<ModelPart> parts;
    private final boolean humpback;

    private SpaceUnitModel(ModelPart root, boolean humpback, int partCount) {
        super(root);
        this.humpback = humpback;
        this.body = root.getChild("body");
        this.parts = IntStream.range(0, partCount).mapToObj(i -> this.body.getChild("part" + i)).toList();
    }

    /** 枪骑：贴图 64×64。 */
    public static SpaceUnitModel lancer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3, -2, -11, 6, 4, 20)
                        .texOffs(0, 24).addBox(-2, -3.5F, -6, 4, 2, 9)
                        .texOffs(52, 0).addBox(-2, -1.5F, 9, 4, 3, 2),
                PartPose.offset(0, 17, 0));
        for (int i = 0; i < 2; i++) {
            int side = i == 0 ? -1 : 1;
            body.addOrReplaceChild("part" + i, CubeListBuilder.create().texOffs(28, 24).addBox(-0.5F, -0.5F, -10, 1, 1, 10),
                    PartPose.offset(side * 2, 0.5F, -11));
        }
        body.addOrReplaceChild("part2", CubeListBuilder.create().texOffs(0, 36).addBox(-12, -0.5F, -4, 12, 1, 8),
                PartPose.offset(-3, 0.5F, 2));
        body.addOrReplaceChild("part3", CubeListBuilder.create().texOffs(0, 36).mirror().addBox(0, -0.5F, -4, 12, 1, 8),
                PartPose.offset(3, 0.5F, 2));
        for (int i = 0; i < 2; i++) {
            int side = i == 0 ? -1 : 1;
            body.addOrReplaceChild("part" + (4 + i), CubeListBuilder.create().texOffs(40, 36).addBox(-0.5F, -5, 0, 1, 5, 5),
                    PartPose.offsetAndRotation(side * 2, -1.5F, 3, 0, 0, side * 0.35F));
        }
        return new SpaceUnitModel(LayerDefinition.create(mesh, 64, 64).bakeRoot(), false, 6);
    }

    /** 座头鲸：贴图 128×128，半尺寸。 */
    public static SpaceUnitModel humpback() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-10, -6, -20, 20, 12, 40)
                        .texOffs(0, 52).addBox(-8, 6, -16, 16, 3, 30)
                        .texOffs(0, 86).addBox(-4, -9, -6, 8, 3, 8)
                        .texOffs(92, 52).addBox(-7, -3, 20, 5, 5, 3)
                        .texOffs(92, 52).addBox(2, -3, 20, 5, 5, 3),
                PartPose.offset(0, 12, 0));
        body.addOrReplaceChild("part0", CubeListBuilder.create()
                        .texOffs(32, 86).addBox(-3, -3, -3, 6, 3, 6)
                        .texOffs(56, 86).addBox(-2.5F, -2, -11, 1, 1, 8)
                        .texOffs(56, 86).addBox(1.5F, -2, -11, 1, 1, 8),
                PartPose.offset(0, -9, -2));
        body.addOrReplaceChild("part1", CubeListBuilder.create().texOffs(0, 98).addBox(-14, -1, -6, 14, 2, 12),
                PartPose.offset(-10, 3, -4));
        body.addOrReplaceChild("part2", CubeListBuilder.create().texOffs(0, 98).mirror().addBox(0, -1, -6, 14, 2, 12),
                PartPose.offset(10, 3, -4));
        body.addOrReplaceChild("part3", CubeListBuilder.create().texOffs(52, 98).addBox(-12, -1, 0, 24, 2, 9),
                PartPose.offset(0, 0, 20));
        return new SpaceUnitModel(LayerDefinition.create(mesh, 128, 128).bakeRoot(), true, 4);
    }

    @Override
    public void setupAnim(SpaceUnitRenderState state) {
        super.setupAnim(state);
        float age = state.ageInTicks;
        this.body.zRot = state.roll * Mth.DEG_TO_RAD;
        if (!this.humpback) {
            // 机头跟着速度俯仰
            this.body.xRot = state.xRot * Mth.DEG_TO_RAD;
            this.body.y += Mth.sin(age * 0.2F + state.phase) * 0.3F;
            float open = state.angry ? 0.06F + Mth.sin(age * 0.9F) * 0.03F : 0;
            this.parts.get(0).yRot = open;
            this.parts.get(1).yRot = -open;
            float flex = Mth.sin(age * 0.25F + state.phase) * 0.03F;
            this.parts.get(2).zRot = -0.06F + flex;
            this.parts.get(3).zRot = 0.06F - flex;
            return;
        }
        this.body.xRot = state.xRot * Mth.DEG_TO_RAD * 0.4F;
        this.body.y += Mth.sin(age * 0.05F + state.phase) * 0.6F;
        // 炮塔慢慢来回转，打的时候转得快
        this.parts.get(0).yRot = Mth.sin(age * (state.angry ? 0.06F : 0.015F) + state.phase) * 0.7F;
        float stroke = Mth.sin(age * 0.06F + state.phase);
        this.parts.get(1).zRot = 0.08F + stroke * 0.12F;
        this.parts.get(2).zRot = -0.08F - stroke * 0.12F;
        this.parts.get(3).xRot = Mth.sin(age * 0.06F + state.phase - 1.2F) * 0.18F;
    }
}
