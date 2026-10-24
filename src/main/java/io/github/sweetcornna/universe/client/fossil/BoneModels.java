package io.github.sweetcornna.universe.client.fossil;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.fossil.BoneBox;
import io.github.sweetcornna.universe.core.fossil.BoneFamily;
import io.github.sweetcornna.universe.core.fossil.BoneShapes;
import io.github.sweetcornna.universe.core.fossil.BoneShapes.Piece;
import io.github.sweetcornna.universe.core.fossil.Skeleton;
import io.github.sweetcornna.universe.core.fossil.SkeletonPose;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * 骨头的立体模型（ADR-0034 §三）：{@link BoneShapes} 里每个类群每块骨头的方块烤成一个 {@link ModelPart}，第一次用到时烤，
 * 共用一张 {@value BoneBox#TEXTURE_SIZE}×{@value BoneBox#TEXTURE_SIZE} 的骨头贴图，按类群的颜色着色。
 *
 * <p>骨头模型是 y 朝上画的（和实体模型反过来），直接放进方块实体、界面的坐标系里，不用翻转。
 * 渲染是延后提交的，所以烤好的 {@link ModelPart} 一直不动，每块骨头放在哪全靠 {@link PoseStack}。
 */
public final class BoneModels {
    public static final Identifier TEXTURE = Universe.id("textures/entity/fossil/bone.png");
    private static final Map<BoneFamily, Map<Piece, ModelPart>> PARTS = new EnumMap<>(BoneFamily.class);
    /** 摆好的骨架：每帧都要用，按骨架记下来。 */
    private static final Map<Skeleton, SkeletonPose> POSES = new HashMap<>();
    private static final int POSE_CACHE = 256;

    private BoneModels() {
    }

    /** 一个类群的一块骨头（四肢分前后左右）。 */
    public static ModelPart part(BoneFamily family, Piece piece) {
        return PARTS.computeIfAbsent(family, f -> new EnumMap<>(Piece.class)).computeIfAbsent(piece, p -> bake(family, p));
    }

    private static ModelPart bake(BoneFamily family, Piece piece) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        List<BoneBox> boxes = BoneShapes.boxes(family, piece);
        for (int i = 0; i < boxes.size(); i++) {
            BoneBox b = boxes.get(i);
            root.addOrReplaceChild("b" + i, CubeListBuilder.create().texOffs(b.u(), b.v()).addBox(b.x(), b.y(), b.z(), b.w(), b.h(), b.d()),
                    PartPose.offsetAndRotation(b.px(), b.py(), b.pz(), b.rx(), b.ry(), b.rz()));
        }
        return LayerDefinition.create(mesh, BoneBox.TEXTURE_SIZE, BoneBox.TEXTURE_SIZE).bakeRoot();
    }

    /** 一副骨架摆出来的样子（空的骨架返回 null）。 */
    public static SkeletonPose pose(Skeleton skeleton) {
        if (skeleton.isEmpty()) {
            return null;
        }
        if (POSES.size() > POSE_CACHE) {
            POSES.clear();
        }
        return POSES.computeIfAbsent(skeleton, SkeletonPose::of);
    }

    /** 骨头的颜色：类群的底色，不透明。 */
    public static int tint(BoneFamily family) {
        return 0xFF000000 | family.tint();
    }

    /**
     * 按摆好的样子提交一副骨架。{@link PoseStack} 的原点是骨架坐标系的原点，单位是格（骨架坐标是像素，这里除以 16）。
     */
    public static void submit(SkeletonPose pose, PoseStack poseStack, SubmitNodeCollector collector, int light) {
        RenderType type = RenderTypes.entityCutout(TEXTURE);
        for (SkeletonPose.Placement placement : pose.placements()) {
            poseStack.pushPose();
            poseStack.translate(placement.x() / 16F, placement.y() / 16F, placement.z() / 16F);
            if (placement.pitch() != 0) {
                poseStack.rotate(Axis.XP, placement.pitch());
            }
            poseStack.scale(placement.scale(), placement.scale(), placement.scale());
            collector.submitModelPart(part(placement.family(), placement.piece()), poseStack, type, light, OverlayTexture.NO_OVERLAY, null,
                    tint(placement.family()));
            poseStack.popPose();
        }
    }

    /**
     * 把一副骨架塞进 {@code width}×{@code height}×{@code depth}（格）的范围：前后左右居中，最低点落在原点。
     * 调用前 {@link PoseStack} 的原点在范围底面的中心。
     */
    public static void submitFitted(SkeletonPose pose, float width, float height, float depth, float max, PoseStack poseStack,
            SubmitNodeCollector collector, int light) {
        float k = pose.fit(width * 16, height * 16, depth * 16, max);
        float[] offset = pose.offset();
        poseStack.pushPose();
        poseStack.scale(k, k, k);
        poseStack.translate(offset[0] / 16F, offset[1] / 16F, offset[2] / 16F);
        submit(pose, poseStack, collector, light);
        poseStack.popPose();
    }
}
