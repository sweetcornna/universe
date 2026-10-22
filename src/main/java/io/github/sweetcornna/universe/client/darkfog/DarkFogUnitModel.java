package io.github.sweetcornna.universe.client.darkfog;

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
 * 黑雾单位的模型（ADR-0028 §二）。突袭者是一块扁平的六边形机身，前面伸出两根放电的叉，底下一块发光的悬浮板和四片导流片；
 * 游骑兵是一支细长的飞镖，两片后掠翼和一片尾鳍；守卫者是一颗方正的核心，四面挂着转动的护盾板，底下一根尖刺。
 *
 * <p>贴图的展开方式和 {@code tools/m16_dark_fog_textures.py} 里的方块列表一一对应，改了这里要同步改那边。
 */
public final class DarkFogUnitModel extends EntityModel<DarkFogUnitRenderState> {
    private final ModelPart body;
    private final List<ModelPart> parts;
    private final Kind kind;

    private enum Kind {
        RAIDER,
        RANGER,
        GUARDIAN
    }

    private DarkFogUnitModel(ModelPart root, Kind kind, int partCount) {
        super(root);
        this.kind = kind;
        this.body = root.getChild("body");
        this.parts = IntStream.range(0, partCount).mapToObj(i -> this.body.getChild("part" + i)).toList();
    }

    /** 突袭者：贴图 64×64。 */
    public static DarkFogUnitModel raider() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-6, -2, -7, 12, 4, 14)
                        .texOffs(0, 18).addBox(-4, -4.5F, -4, 8, 3, 8)
                        .texOffs(0, 30).addBox(-5, 2, -5, 10, 1, 10),
                PartPose.offset(0, 18, 0));
        for (int i = 0; i < 2; i++) {
            int side = i == 0 ? -1 : 1;
            body.addOrReplaceChild("part" + i, CubeListBuilder.create().texOffs(32, 18).mirror(side > 0).addBox(-1, -1, -6, 2, 2, 6),
                    PartPose.offset(side * 3.5F, 0.5F, -6));
        }
        int i = 2;
        for (int sz = -1; sz <= 1; sz += 2) {
            for (int sx = -1; sx <= 1; sx += 2) {
                body.addOrReplaceChild("part" + i, CubeListBuilder.create().texOffs(40, 30).addBox(-0.5F, 0, -0.5F, 1, 3, 1),
                        PartPose.offset(sx * 4.5F, 2, sz * 5));
                i++;
            }
        }
        return new DarkFogUnitModel(LayerDefinition.create(mesh, 64, 64).bakeRoot(), Kind.RAIDER, 6);
    }

    /** 游骑兵：贴图 64×32。 */
    public static DarkFogUnitModel ranger() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-2, -1.5F, -6, 4, 3, 12)
                        .texOffs(32, 0).addBox(-1, -1, -9, 2, 2, 3)
                        .texOffs(32, 8).addBox(-0.5F, -4, 2, 1, 3, 4),
                PartPose.offset(0, 18, 0));
        body.addOrReplaceChild("part0", CubeListBuilder.create().texOffs(0, 16).addBox(-7, -0.5F, -3, 7, 1, 6),
                PartPose.offset(-2, 0, 1));
        body.addOrReplaceChild("part1", CubeListBuilder.create().texOffs(0, 16).mirror().addBox(0, -0.5F, -3, 7, 1, 6),
                PartPose.offset(2, 0, 1));
        return new DarkFogUnitModel(LayerDefinition.create(mesh, 64, 32).bakeRoot(), Kind.RANGER, 2);
    }

    /** 守卫者：贴图 64×64。 */
    public static DarkFogUnitModel guardian() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-5, -5, -5, 10, 10, 10)
                        .texOffs(40, 0).addBox(-2, -1.5F, -6, 4, 3, 1)
                        .texOffs(24, 20).addBox(-1.5F, 5, -1.5F, 3, 4, 3),
                PartPose.offset(0, 12, 0));
        PartDefinition ring = body.addOrReplaceChild("part0", CubeListBuilder.create(), PartPose.ZERO);
        for (int i = 0; i < 4; i++) {
            ring.addOrReplaceChild("shield" + i, CubeListBuilder.create().texOffs(0, 20).addBox(-4, -6, -1, 8, 12, 2),
                    PartPose.offsetAndRotation(Mth.sin(i * Mth.HALF_PI) * 9, 0, Mth.cos(i * Mth.HALF_PI) * 9, 0, i * Mth.HALF_PI, 0));
        }
        return new DarkFogUnitModel(LayerDefinition.create(mesh, 64, 64).bakeRoot(), Kind.GUARDIAN, 1);
    }

    @Override
    public void setupAnim(DarkFogUnitRenderState state) {
        super.setupAnim(state);
        float age = state.ageInTicks;
        this.body.yRot = state.yRot * Mth.DEG_TO_RAD;
        float speed = Math.min(1, state.walkAnimationSpeed * 2);
        switch (this.kind) {
            case RAIDER -> {
                this.body.y += Mth.sin(age * 0.15F + state.bobPhase) * 0.5F;
                this.body.xRot = speed * 0.12F;
                this.body.zRot = Mth.sin(age * 0.07F + state.bobPhase) * 0.05F;
                // 放电叉张合，打的时候张得更开
                float open = state.angry ? 0.25F + Mth.sin(age * 0.8F) * 0.1F : 0.08F;
                this.parts.get(0).yRot = open;
                this.parts.get(1).yRot = -open;
                for (int i = 2; i < 6; i++) {
                    this.parts.get(i).xRot = Mth.sin(age * 0.2F + i) * 0.15F + speed * 0.4F;
                }
            }
            case RANGER -> {
                this.body.xRot = state.xRot * Mth.DEG_TO_RAD * 0.6F;
                this.body.y += Mth.sin(age * 0.12F + state.bobPhase) * 0.6F;
                // 转弯时侧倾
                this.body.zRot = Mth.sin(age * 0.05F + state.bobPhase) * 0.15F;
                float flap = Mth.sin(age * 0.3F + state.bobPhase) * 0.05F;
                this.parts.get(0).zRot = 0.08F + flap;
                this.parts.get(1).zRot = -0.08F - flap;
            }
            case GUARDIAN -> {
                this.body.y += Mth.sin(age * 0.08F + state.bobPhase) * 0.8F;
                this.body.xRot = state.xRot * Mth.DEG_TO_RAD * 0.3F;
                // 护盾环自己转，不跟着机身朝向
                this.parts.get(0).yRot = age * (state.angry ? 0.08F : 0.03F) - this.body.yRot;
            }
        }
    }
}
