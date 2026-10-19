package io.github.sweetcornna.universe.client.pirate;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * 海盗战机的模型（ADR-0017）：细长的机身、尖机头、座舱罩、垂直尾翼，两侧是内外两段机翼，翼尖挂着炮管，机尾两台引擎。
 * 机头朝 -z，长约 2 格、翼展约 2.75 格；贴图 128×64。
 *
 * <p>贴图的展开方式和 {@code tools/combat_textures.py} 里的方块列表一一对应（texOffs 和尺寸），改了这里要同步改那边。
 */
public final class PirateModel extends EntityModel<PirateRenderState> {
    private final ModelPart body;
    private final ModelPart fin;

    private PirateModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.fin = this.body.getChild("fin");
    }

    public static PirateModel create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create()
                        // 机身、机头、座舱罩
                        .texOffs(0, 0).addBox(-3, -2, -14, 6, 4, 24)
                        .texOffs(60, 0).addBox(-2, -1.5F, -19, 4, 3, 5)
                        .texOffs(80, 0).addBox(-2, -4, -10, 4, 2, 7)
                        // 机翼内段、外段、翼尖炮管（左右对称）
                        .texOffs(0, 28).addBox(-14, -0.5F, -4, 11, 1, 12)
                        .texOffs(0, 28).mirror().addBox(3, -0.5F, -4, 11, 1, 12).mirror(false)
                        .texOffs(46, 28).addBox(-21, -0.5F, 0, 7, 1, 7)
                        .texOffs(46, 28).mirror().addBox(14, -0.5F, 0, 7, 1, 7).mirror(false)
                        .texOffs(74, 28).addBox(-22, -1.5F, -8, 2, 2, 12)
                        .texOffs(74, 28).mirror().addBox(20, -1.5F, -8, 2, 2, 12).mirror(false)
                        // 机尾两台引擎
                        .texOffs(0, 44).addBox(-6, -2.5F, 6, 4, 4, 7)
                        .texOffs(0, 44).mirror().addBox(2, -2.5F, 6, 4, 4, 7).mirror(false),
                PartPose.ZERO);
        body.addOrReplaceChild("fin", CubeListBuilder.create().texOffs(104, 0).addBox(-0.5F, -6, 0, 1, 6, 7),
                PartPose.offset(0, -2, 4));
        return new PirateModel(LayerDefinition.create(mesh, 128, 64).bakeRoot());
    }

    @Override
    public void setupAnim(PirateRenderState state) {
        super.setupAnim(state);
        // 一点点抖动：引擎推着机身
        this.body.y += Mth.sin(state.ageInTicks * 0.9F + state.phase) * 0.15F;
        this.fin.yRot = Mth.clamp(-state.bank * 0.004F, -0.2F, 0.2F);
    }
}
