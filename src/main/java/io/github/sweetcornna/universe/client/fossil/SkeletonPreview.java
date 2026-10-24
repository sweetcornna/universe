package io.github.sweetcornna.universe.client.fossil;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.sweetcornna.universe.core.fossil.Skeleton;
import io.github.sweetcornna.universe.core.fossil.SkeletonPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.util.Mth;

/**
 * 界面里转着看的立体骨架（ADR-0034 §三）：骨架展台、收藏家的界面里画一副正在拼的骨架，慢慢转、稍微从上往下看。
 * 用原版界面的"画中画"渲染：先画进一张贴图，再贴到界面上。
 */
public final class SkeletonPreview {
    /** 一秒转多少度。 */
    private static final float SPIN = 24;
    private static final float TILT = -18;

    private SkeletonPreview() {
    }

    /** 在 (x0, y0)–(x1, y1) 的框里画 {@code skeleton}；空的不画。 */
    public static void draw(GuiGraphicsExtractor graphics, Skeleton skeleton, int x0, int y0, int x1, int y1) {
        if (skeleton.isEmpty()) {
            return;
        }
        float yaw = (System.currentTimeMillis() % 360_000L) / 1000F * SPIN + 35;
        graphics.submitPictureInPictureRenderState(new State(skeleton, yaw, x0, y0, x1, y1, 1, null,
                PictureInPictureRenderState.getBounds(x0, y0, x1, y1, null)));
    }

    /** 画一帧要的东西。 */
    public record State(Skeleton skeleton, float yaw, int x0, int y0, int x1, int y1, float scale, @Nullable ScreenRectangle scissorArea,
            @Nullable ScreenRectangle bounds) implements PictureInPictureRenderState {
    }

    /** 画中画的渲染器，在 {@code RegisterPictureInPictureRenderersEvent} 里登记。 */
    public static final class Renderer extends PictureInPictureRenderer<State> {
        @Override
        public Class<State> getRenderStateClass() {
            return State.class;
        }

        @Override
        protected void renderToTexture(State state, PoseStack poseStack, SubmitNodeCollector collector) {
            SkeletonPose pose = BoneModels.pose(state.skeleton());
            if (pose == null) {
                return;
            }
            Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
            SkeletonPose.Bounds full = pose.full();
            float width = state.x1() - state.x0();
            float height = state.y1() - state.y0();
            // 界面的 y 朝下：转半圈让骨架立起来；稍微往前倾，从上往下看
            poseStack.rotate(Axis.ZP, Mth.PI);
            poseStack.rotateDegrees(Axis.XP, TILT);
            poseStack.rotateDegrees(Axis.YP, state.yaw());
            float k = Math.min(width * 0.9F / (Math.max(full.width(), full.depth()) / 16F), height * 0.8F / (full.height() / 16F));
            poseStack.scale(k, k, k);
            float[] offset = pose.offset();
            poseStack.translate(offset[0] / 16F, (offset[1] - full.height() / 2) / 16F, offset[2] / 16F);
            BoneModels.submit(pose, poseStack, collector, 0xF000F0);
        }

        @Override
        protected float getTranslateY(int height, int guiScale) {
            return height / 2.0F;
        }

        @Override
        protected String getTextureLabel() {
            return "universe skeleton";
        }
    }
}
