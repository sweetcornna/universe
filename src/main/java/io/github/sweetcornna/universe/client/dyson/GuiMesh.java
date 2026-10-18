package io.github.sweetcornna.universe.client.dyson;

import java.util.Arrays;

import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;

/**
 * 界面里画任意的三角形和斜线（原版的界面只能画横平竖直的矩形）：攒一批四边形，一次交给界面渲染。
 * 三角形当作第四个点和第三个点重合的四边形；线段是沿法线加宽的细长四边形。
 */
final class GuiMesh {
    private float[] xy = new float[256];
    private int[] colors = new int[128];
    private int vertices;
    private float minX = Float.MAX_VALUE;
    private float minY = Float.MAX_VALUE;
    private float maxX = -Float.MAX_VALUE;
    private float maxY = -Float.MAX_VALUE;

    boolean isEmpty() {
        return this.vertices == 0;
    }

    private void vertex(float x, float y, int color) {
        if (this.vertices * 2 + 2 > this.xy.length) {
            this.xy = Arrays.copyOf(this.xy, this.xy.length * 2);
            this.colors = Arrays.copyOf(this.colors, this.colors.length * 2);
        }
        this.xy[this.vertices * 2] = x;
        this.xy[this.vertices * 2 + 1] = y;
        this.colors[this.vertices] = color;
        this.vertices++;
        this.minX = Math.min(this.minX, x);
        this.minY = Math.min(this.minY, y);
        this.maxX = Math.max(this.maxX, x);
        this.maxY = Math.max(this.maxY, y);
    }

    void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int color) {
        this.vertex(x0, y0, color);
        this.vertex(x1, y1, color);
        this.vertex(x2, y2, color);
        this.vertex(x3, y3, color);
    }

    void triangle(float x0, float y0, float x1, float y1, float x2, float y2, int color) {
        this.quad(x0, y0, x1, y1, x2, y2, x2, y2, color);
    }

    /** 线段，宽 width 个屏幕单位。 */
    void line(double x0, double y0, double x1, double y1, float width, int color) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < 1e-3) {
            return;
        }
        float nx = (float) (-dy / length * width / 2);
        float ny = (float) (dx / length * width / 2);
        this.quad((float) x0 + nx, (float) y0 + ny, (float) x0 - nx, (float) y0 - ny, (float) x1 - nx, (float) y1 - ny, (float) x1 + nx,
                (float) y1 + ny, color);
    }

    /** 以 (x, y) 为中心的小方块。 */
    void dot(double x, double y, float size, int color) {
        float h = size / 2;
        this.quad((float) x - h, (float) y - h, (float) x - h, (float) y + h, (float) x + h, (float) y + h, (float) x + h, (float) y - h, color);
    }

    /** 实心圆：中心一个颜色，边上一个颜色（做光晕）。 */
    void disc(double x, double y, double radius, int center, int edge) {
        int segments = Math.max(8, Math.min(48, (int) (radius * 2)));
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments;
            double a1 = Math.PI * 2 * (i + 1) / segments;
            this.vertex((float) x, (float) y, center);
            this.vertex((float) (x + Math.cos(a0) * radius), (float) (y + Math.sin(a0) * radius), edge);
            this.vertex((float) (x + Math.cos(a1) * radius), (float) (y + Math.sin(a1) * radius), edge);
            this.vertex((float) (x + Math.cos(a1) * radius), (float) (y + Math.sin(a1) * radius), edge);
        }
    }

    /** 交给界面，清空以便下一批。 */
    void submit(GuiGraphicsExtractor graphics) {
        if (this.vertices == 0) {
            return;
        }
        ScreenRectangle scissor = graphics.peekScissorStack();
        ScreenRectangle bounds = new ScreenRectangle((int) Math.floor(this.minX), (int) Math.floor(this.minY),
                (int) Math.ceil(this.maxX - this.minX) + 1, (int) Math.ceil(this.maxY - this.minY) + 1);
        Matrix3x2f pose = new Matrix3x2f(graphics.pose());
        bounds = bounds.transformMaxBounds(pose);
        graphics.submitGuiElementRenderState(new Batch(pose, Arrays.copyOf(this.xy, this.vertices * 2), Arrays.copyOf(this.colors, this.vertices),
                scissor, scissor != null ? scissor.intersection(bounds) : bounds));
        this.vertices = 0;
        this.minX = Float.MAX_VALUE;
        this.minY = Float.MAX_VALUE;
        this.maxX = -Float.MAX_VALUE;
        this.maxY = -Float.MAX_VALUE;
    }

    private record Batch(Matrix3x2fc pose, float[] xy, int[] colors, @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds)
            implements GuiElementRenderState {
        @Override
        public void buildVertices(VertexConsumer consumer) {
            for (int i = 0; i < this.colors.length; i++) {
                consumer.addVertexWith2DPose(this.pose, this.xy[i * 2], this.xy[i * 2 + 1]).setColor(this.colors[i]);
            }
        }

        @Override
        public RenderPipeline pipeline() {
            return RenderPipelines.GUI;
        }

        @Override
        public TextureSetup textureSetup() {
            return TextureSetup.noTexture();
        }
    }
}
