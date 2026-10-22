package io.github.sweetcornna.universe.client.creature;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.sweetcornna.universe.core.ecology.BodyPlan;
import io.github.sweetcornna.universe.core.ecology.CreatureAnatomy;
import io.github.sweetcornna.universe.core.ecology.CreatureAnatomy.Box;
import io.github.sweetcornna.universe.core.ecology.CreatureAnatomy.Part;
import io.github.sweetcornna.universe.core.ecology.CreatureSpecies;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * 按 {@link CreatureAnatomy} 拼出的实体模型，每个物种一个。动画按部件的角色来：
 * 腿按相位摆动（六足虫的腿水平划动），尾巴左右甩，节蠕虫身体波动，飞兽在空中扇翅、落地收翅，头跟着视线转。
 */
public final class AlienCreatureModel extends EntityModel<AlienCreatureRenderState> {
    private static final float DEG = Mth.DEG_TO_RAD;

    private final BodyPlan plan;
    private final List<Animated> parts;
    /** 迈步频率：腿越短、个子越大（同样走一格要迈的步子少），频率越低。 */
    private final float stride;

    private record Animated(ModelPart model, Part part) {
    }

    private AlienCreatureModel(ModelPart root, CreatureSpecies species, CreatureAnatomy anatomy) {
        super(root);
        this.plan = species.plan();
        Map<String, ModelPart> byName = new HashMap<>();
        List<Animated> animated = new ArrayList<>();
        for (Part part : anatomy.parts()) {
            ModelPart parent = part.parent() == null ? root : byName.get(part.parent());
            ModelPart model = parent.getChild(part.name());
            byName.put(part.name(), model);
            animated.add(new Animated(model, part));
        }
        this.parts = List.copyOf(animated);
        float legLength = Math.max(2, species.body().legLength());
        this.stride = (float) (0.6662 * Mth.clamp(Math.sqrt(9.0 / legLength), 0.75, 1.9) / Math.max(0.5, species.size()));
    }

    public static AlienCreatureModel create(CreatureSpecies species, CreatureAnatomy anatomy) {
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> definitions = new HashMap<>();
        for (Part part : anatomy.parts()) {
            CubeListBuilder cubes = CubeListBuilder.create();
            for (Box box : part.boxes()) {
                cubes.texOffs(box.u(), box.v()).addBox(box.x(), box.y(), box.z(), box.w(), box.h(), box.d());
            }
            PartDefinition parent = part.parent() == null ? mesh.getRoot() : definitions.get(part.parent());
            definitions.put(part.name(), parent.addOrReplaceChild(part.name(), cubes,
                    PartPose.offsetAndRotation(part.px(), part.py(), part.pz(), part.xRot(), part.yRot(), part.zRot())));
        }
        ModelPart root = LayerDefinition.create(mesh, anatomy.textureWidth(), anatomy.textureHeight()).bakeRoot();
        return new AlienCreatureModel(root, species, anatomy);
    }

    @Override
    public void setupAnim(AlienCreatureRenderState state) {
        super.setupAnim(state);
        if (this.plan.aquatic()) {
            this.swim(state);
            return;
        }
        float pos = state.walkAnimationPos;
        float amount = Math.min(1, state.walkAnimationSpeed);
        float age = state.ageInTicks;
        boolean flying = state.flying;
        for (Animated a : this.parts) {
            ModelPart m = a.model();
            Part p = a.part();
            switch (p.role()) {
                case LEG -> {
                    if (flying) {
                        // 飞起来时腿往后收
                        m.xRot += 0.9F;
                    } else if (this.plan == BodyPlan.HEXAPOD) {
                        // 虫子的腿向两侧张开，前后划动、抬腿交替
                        m.zRot += -p.side() * 0.32F;
                        m.yRot += Mth.cos(pos * this.stride * 1.4F + p.phase()) * 0.55F * amount;
                        m.zRot += -p.side() * Math.max(0, Mth.sin(pos * this.stride * 1.4F + p.phase())) * 0.3F * amount;
                    } else if (this.plan == BodyPlan.SERPENT) {
                        m.xRot += Mth.cos(pos * this.stride * 1.6F + p.phase()) * 0.7F * amount;
                    } else {
                        m.xRot += Mth.cos(pos * this.stride + p.phase()) * 1.2F * amount;
                    }
                }
                case ARM -> m.xRot += Mth.cos(pos * this.stride + p.phase()) * 0.5F * amount + Mth.sin(age * 0.08F) * 0.05F;
                case TAIL -> {
                    if (this.plan == BodyPlan.FLYER) {
                        m.xRot += Mth.sin(age * 0.2F) * 0.06F;
                    } else {
                        m.yRot += Mth.sin(age * 0.12F + p.index() * 0.7F) * 0.12F * (1 + amount * 1.5F);
                        m.xRot += Mth.sin(age * 0.05F + p.index()) * 0.03F;
                    }
                }
                case WING -> {
                    if (flying) {
                        m.zRot += p.side() * Mth.sin(age * 1.3F) * 0.85F;
                    } else {
                        // 落地时翅膀折到背上
                        m.yRot += -p.side() * 1.25F;
                        m.zRot += -p.side() * 0.25F;
                    }
                }
                case SEGMENT -> {
                    if (p.index() > 0) {
                        m.yRot += Mth.sin(pos * this.stride * 0.9F - p.index() * 0.85F) * 0.32F * Math.min(1, amount * 2)
                                + Mth.sin(age * 0.07F - p.index() * 0.85F) * 0.07F;
                    }
                }
                case HEAD -> {
                    m.yRot += state.yRot * DEG * 0.7F;
                    m.xRot += state.xRot * DEG * 0.7F + Mth.sin(age * 0.07F) * 0.03F;
                    if (state.attackAnim > 0) {
                        // 扑咬：头往前下方一冲
                        m.xRot += Mth.sin(state.attackAnim * Mth.PI) * 0.6F;
                    }
                }
                case DECOR -> {
                    if (p.name().startsWith("antenna")) {
                        m.xRot += Mth.sin(age * 0.15F + p.side()) * 0.1F;
                        m.zRot += Mth.sin(age * 0.11F + p.side() * 2) * 0.06F;
                    }
                }
                case BODY -> {
                    if (!flying) {
                        m.zRot += Mth.cos(pos * this.stride) * 0.03F * amount;
                    } else {
                        m.xRot += Mth.sin(age * 1.3F) * 0.03F;
                    }
                }
                default -> {
                }
            }
        }
    }

    /**
     * 水生动物（ADR-0026）：鱼摆尾、鳗身体波动、水母的钟一张一缩、乌贼的腕开合。游得越快摆得越快，
     * 整个身体顺着游的方向抬头低头（轴心在身体中间）。
     */
    private void swim(AlienCreatureRenderState state) {
        float age = state.ageInTicks;
        float amount = Math.min(1, state.walkAnimationSpeed * 2);
        float beat = age * (0.25F + 0.4F * amount);
        float pitch = state.swimPitch * DEG;
        float pulse = Mth.sin(age * 0.12F);
        for (Animated a : this.parts) {
            ModelPart m = a.model();
            Part p = a.part();
            switch (p.role()) {
                case BODY -> {
                    if (this.plan == BodyPlan.JELLY) {
                        m.xScale = 1 + pulse * 0.08F;
                        m.zScale = 1 + pulse * 0.08F;
                        m.yScale = 1 - pulse * 0.06F;
                    } else {
                        m.xRot += pitch;
                        if (this.plan == BodyPlan.FISH) {
                            m.yRot += Mth.sin(beat) * 0.08F;
                        }
                    }
                }
                case SEGMENT -> {
                    if (p.index() == 0) {
                        m.xRot += pitch;
                        m.yRot += Mth.sin(beat * 0.8F) * 0.08F;
                    } else {
                        m.yRot += Mth.sin(beat * 0.8F - p.index() * 0.9F) * 0.3F;
                    }
                }
                case TAIL -> m.yRot += Mth.sin(beat - 0.6F) * 0.45F;
                case FIN -> m.zRot += p.side() * Mth.sin(age * 0.3F) * (this.plan == BodyPlan.SQUID ? 0.35F : 0.25F);
                case TENTACLE -> {
                    if (this.plan == BodyPlan.JELLY) {
                        // 钟缩起来时触手往外甩一点，平时慢慢飘
                        float spread = Math.max(0, -pulse) * 0.25F;
                        m.xRot += Mth.sin(age * 0.09F + p.phase()) * 0.1F - Mth.sin(p.phase()) * spread;
                        m.zRot += Mth.cos(age * 0.07F + p.phase() * 1.3F) * 0.1F + Mth.cos(p.phase()) * spread;
                    } else {
                        // 乌贼的腕拖在身后，一张一合
                        float open = (Mth.sin(age * 0.2F) + 1) * 0.12F + 0.05F;
                        m.xRot += -Mth.sin(p.phase()) * open;
                        m.yRot += Mth.cos(p.phase()) * open;
                    }
                }
                case HEAD -> {
                    if (state.attackAnim > 0) {
                        m.xRot += Mth.sin(state.attackAnim * Mth.PI) * 0.4F;
                    }
                }
                case DECOR -> m.xRot += Mth.sin(age * 0.15F + p.side()) * 0.1F;
                default -> {
                }
            }
        }
    }
}
