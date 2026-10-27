package io.github.sweetcornna.universe.client.sky;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;

import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.procgen.Colors;
import io.github.sweetcornna.universe.core.sky.BodyLook;
import io.github.sweetcornna.universe.core.sky.PlanetAppearance;
import net.minecraft.client.renderer.DynamicGpuDataStorage;
import net.minecraft.client.renderer.DynamicGpuDataStorageMapped;

/**
 * 用光线求交画星球（着色器 {@code sky_planet}，ADR-0021）：太空里按真实的位置和大小画，星球天空里按压缩过的视大小画，
 * 站在有环的星球上还能看见横过天空的环。
 *
 * <p>每颗星球每帧往参数块里写一份，参数块按帧轮换（{@link #beginFrame}）。远处的星球画在一个刚好盖住它的四边形上；
 * 近到占了大半个视野时（刚起飞的时候）改成画一个包住摄像机的立方体，逐像素求交。
 */
final class PlanetRenderer {
    /** 天空里的东西画在离摄像机多远的地方（和 {@code PlanetSkyRenderer} 一致）。 */
    private static final float DISTANCE = 100;
    /** 四边形能画多大的视角（半角，度）；再大就画立方体。 */
    private static final double MAX_QUAD_HALF_ANGLE = 60;
    private static final int UBO_SIZE = new Std140SizeCalculator().putMat4f().putMat4f()
            .putVec4().putVec4().putVec4().putVec4().putVec4().putVec4().putVec4().putVec4().putVec4().putVec4().putVec4().putVec4()
            .get();

    private @Nullable DynamicGpuDataStorageMapped<Info> storage;
    private @Nullable GpuBuffer quad;
    private @Nullable GpuBuffer cube;
    private final Map<Long, PlanetAppearance> appearances = new HashMap<>();
    private final Map<Long, Terraformed> terraformed = new HashMap<>();

    /** 一颗星球这一帧的参数（布局见着色器）。 */
    record Info(Matrix4f quad, Matrix4f body, Vector4f center, Vector4f sun, Vector4f sunColor, Vector4f style, Vector4f colorA,
            Vector4f colorB, Vector4f colorC, Vector4f atmos, Vector4f haze, Vector4f rings, Vector4f ringColor, Vector4f extra)
            implements DynamicGpuDataStorage.DynamicGpuData {
        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer).putMat4f(this.quad).putMat4f(this.body).putVec4(this.center).putVec4(this.sun)
                    .putVec4(this.sunColor).putVec4(this.style).putVec4(this.colorA).putVec4(this.colorB).putVec4(this.colorC)
                    .putVec4(this.atmos).putVec4(this.haze).putVec4(this.rings).putVec4(this.ringColor).putVec4(this.extra);
        }
    }

    /**
     * 一颗星球在这一帧的样子。
     *
     * @param center     星球中心相对摄像机的位置（以云顶半径为单位，世界坐标）
     * @param sun        从星球指向恒星的单位向量（世界坐标）
     * @param axis       自转轴（世界坐标）
     * @param spin       自转角（弧度）
     * @param irradiance 光照强度（1 是母星那样）
     * @param starColor  恒星的颜色
     * @param visibility 可见度 0–1
     * @param occlusion  白天的天光挡住多少，太空里是 1
     * @param seconds    动画时间（秒）
     */
    record View(Vector3f center, Vector3f sun, Vector3f axis, float spin, float irradiance, int starColor, float visibility,
            float occlusion, float seconds) {
    }

    /** 每帧画天空之前调用一次：参数块换到下一份。 */
    void beginFrame() {
        if (this.storage == null) {
            this.storage = new DynamicGpuDataStorageMapped<>("Universe planet UBO", UBO_SIZE, GpuBuffer.USAGE_UNIFORM, 16);
            this.quad = buildQuad();
            this.cube = buildCube();
        } else {
            this.storage.endFrame();
        }
    }

    /** 星球的样子；改造过的（ADR-0036 §六）按现用的大气、温度画，状态变了重算。 */
    PlanetAppearance appearance(Planet planet) {
        Planet shown = io.github.sweetcornna.universe.client.ClientGalaxy.galaxy()
                .map(galaxy -> io.github.sweetcornna.universe.terraform.Terraforming.clientEffective(galaxy, planet)).orElse(planet);
        if (shown != planet) {
            Terraformed cached = this.terraformed.get(planet.seed());
            if (cached == null || cached.planet() != shown) {
                cached = new Terraformed(shown, PlanetAppearance.of(shown));
                this.terraformed.put(planet.seed(), cached);
            }
            return cached.appearance();
        }
        return this.appearances.computeIfAbsent(planet.seed(), seed -> PlanetAppearance.of(planet));
    }

    /** 改造过的星球的样子和算它时的现用星球。 */
    private record Terraformed(Planet planet, PlanetAppearance appearance) {
    }

    /** 画一颗星球（连同大气和环）。 */
    void draw(RenderPass pass, Planet planet, View view) {
        this.draw(pass, planet, view, false);
    }

    /** 站在这颗星球上：只画它的环，环面按观察者的纬度倾斜。 */
    void drawOwnRings(RenderPass pass, Planet planet, View view) {
        this.draw(pass, planet, view, true);
    }

    private void draw(RenderPass pass, Planet planet, View view, boolean ringsOnly) {
        if (this.storage == null || this.quad == null || this.cube == null) {
            return;
        }
        PlanetAppearance look = this.appearance(planet);
        BodyLook body = BodyLook.of(planet);
        float distance = view.center().length();
        float extent = (float) Math.max(1 + look.atmosphereTop() * 1.4 + 0.02, body.hasRings() ? body.ringOuter() : 0) * 1.05F;
        boolean useQuad = !ringsOnly && distance > extent * 1.02F
                && Math.toDegrees(Math.asin(Math.min(1, extent / distance))) < MAX_QUAD_HALF_ANGLE;
        Matrix4f geometry;
        if (useQuad) {
            Vector3f dir = new Vector3f(view.center()).div(distance);
            Vector3f reference = Math.abs(dir.y()) < 0.95F ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
            Vector3f u = new Vector3f(reference).cross(dir).normalize();
            Vector3f v = new Vector3f(dir).cross(u).normalize();
            // 留出几个像素的余量，远处画成光点时不被切掉
            float half = DISTANCE * (float) Math.tan(Math.asin(Math.min(1, extent / distance))) * 1.1F + 0.4F;
            geometry = new Matrix4f(
                    u.x() * half, u.y() * half, u.z() * half, 0,
                    dir.x(), dir.y(), dir.z(), 0,
                    v.x() * half, v.y() * half, v.z() * half, 0,
                    dir.x() * DISTANCE, dir.y() * DISTANCE, dir.z() * DISTANCE, 1);
        } else {
            geometry = new Matrix4f().scale(DISTANCE);
        }
        // 世界方向换到星球坐标：z 是自转轴，x、y 绕轴转过自转角
        Vector3f axis = new Vector3f(view.axis()).normalize();
        Vector3f reference = Math.abs(axis.y()) < 0.95F ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        Vector3f e1 = new Vector3f(axis).cross(reference).normalize();
        Vector3f e2 = new Vector3f(axis).cross(e1).normalize();
        float cs = (float) Math.cos(view.spin());
        float sn = (float) Math.sin(view.spin());
        Vector3f r1 = new Vector3f(e1).mul(cs).add(new Vector3f(e2).mul(sn));
        Vector3f r2 = new Vector3f(e2).mul(cs).sub(new Vector3f(e1).mul(sn));
        Matrix4f toBody = new Matrix4f(
                r1.x(), r2.x(), axis.x(), 0,
                r1.y(), r2.y(), axis.y(), 0,
                r1.z(), r2.z(), axis.z(), 0,
                0, 0, 0, 1);
        double[] star = starLight(view.starColor());
        Info info = new Info(geometry, toBody,
                new Vector4f(view.center(), view.visibility()),
                new Vector4f(view.sun(), view.irradiance()),
                new Vector4f((float) star[0], (float) star[1], (float) star[2], view.occlusion()),
                new Vector4f(look.style().shaderId(), (float) look.seed(), (float) look.clouds(), (float) look.seaLevel()),
                vec(look.a(), (float) look.emissive()),
                vec(look.b(), (float) look.iceCap()),
                vec(look.c(), (float) look.relief()),
                vec(look.rayleigh(), (float) look.atmosphereTop()),
                vec(look.haze(), (float) look.hazeDepth()),
                new Vector4f((float) body.ringInner(), (float) body.ringOuter(), (float) look.ringOpacity(), (float) body.pattern()),
                vec(look.ringColor(), ringsOnly ? 1 : 0),
                new Vector4f(view.seconds(), (float) look.aurora(), (float) body.pattern(), (float) look.exposure()));
        GpuBufferSlice slice = this.storage.writeData(info);
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(RenderSystem.getModelViewStack()));
        pass.setPipeline(RenderSystem.getCompiledPipeline(SkyPipelines.PLANET));
        pass.setUniform("DynamicTransforms", transform);
        pass.setUniform("PlanetInfo", slice);
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        if (useQuad) {
            pass.setVertexBuffer(0, this.quad.slice());
            pass.setIndexBuffer(indices.getBuffer(6), indices.type());
            pass.drawIndexed(6, 1, 0, 0, 0);
        } else {
            pass.setVertexBuffer(0, this.cube.slice());
            pass.setIndexBuffer(indices.getBuffer(36), indices.type());
            pass.drawIndexed(36, 1, 0, 0, 0);
        }
    }

    /** 恒星光的颜色：线性、亮度归一，冷的恒星偏红、热的偏蓝，不至于把星球染得太重。 */
    static double[] starLight(int color) {
        double r = Math.pow(Colors.red(color) / 255.0, 2.2);
        double g = Math.pow(Colors.green(color) / 255.0, 2.2);
        double b = Math.pow(Colors.blue(color) / 255.0, 2.2);
        double luminance = Math.max(1e-3, 0.2126 * r + 0.7152 * g + 0.0722 * b);
        double[] light = {r / luminance, g / luminance, b / luminance};
        for (int i = 0; i < 3; i++) {
            light[i] = 0.35 + 0.65 * Math.min(light[i], 2.0);
        }
        return light;
    }

    private static Vector4f vec(double[] rgb, float w) {
        return new Vector4f((float) rgb[0], (float) rgb[1], (float) rgb[2], w);
    }

    /** 中心在原点、躺在 xz 平面上的单位四边形。 */
    private static GpuBuffer buildQuad() {
        return build("Universe planet quad", 4, builder -> {
            builder.addVertex(-1.0F, 0.0F, -1.0F);
            builder.addVertex(1.0F, 0.0F, -1.0F);
            builder.addVertex(1.0F, 0.0F, 1.0F);
            builder.addVertex(-1.0F, 0.0F, 1.0F);
        });
    }

    /** 边长 2 的立方体，六个面。 */
    private static GpuBuffer buildCube() {
        float[][] faces = {
                {1, -1, -1, 1, 1, -1, 1, 1, 1, 1, -1, 1},
                {-1, -1, 1, -1, 1, 1, -1, 1, -1, -1, -1, -1},
                {-1, 1, -1, -1, 1, 1, 1, 1, 1, 1, 1, -1},
                {-1, -1, 1, -1, -1, -1, 1, -1, -1, 1, -1, 1},
                {-1, -1, 1, 1, -1, 1, 1, 1, 1, -1, 1, 1},
                {1, -1, -1, -1, -1, -1, -1, 1, -1, 1, 1, -1}};
        return build("Universe planet cube", 24, builder -> {
            for (float[] face : faces) {
                for (int i = 0; i < 4; i++) {
                    builder.addVertex(face[i * 3], face[i * 3 + 1], face[i * 3 + 2]);
                }
            }
        });
    }

    private interface Filler {
        void fill(BufferBuilder builder);
    }

    private static GpuBuffer build(String name, int vertices, Filler filler) {
        Supplier<String> label = () -> name;
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(vertices * DefaultVertexFormat.POSITION.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION);
            filler.fill(builder);
            try (MeshData mesh = builder.buildOrThrow()) {
                return RenderSystem.getDevice().createBuffer(label, GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
            }
        }
    }
}
