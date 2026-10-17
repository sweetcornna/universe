package io.github.sweetcornna.universe.client.sky;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.vertex.VertexFormat;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.ClientGalaxy;
import io.github.sweetcornna.universe.client.ClientWeather;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.procgen.Colors;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;
import io.github.sweetcornna.universe.core.procgen.SimplexNoise;
import io.github.sweetcornna.universe.core.sky.BodyLook;
import io.github.sweetcornna.universe.core.sky.SkyBody;
import io.github.sweetcornna.universe.core.sky.SkyColors;
import io.github.sweetcornna.universe.core.sky.SkyModel;
import io.github.sweetcornna.universe.core.sky.StarField;
import io.github.sweetcornna.universe.core.space.SpaceLayout;
import io.github.sweetcornna.universe.world.planet.PlanetEnvironment;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.CustomSkyboxRenderer;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RegisterCustomEnvironmentEffectRendererEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/**
 * 星球维度的天空（技术架构 §3.4）：这颗星球的天色、本恒星系的星空和银河、真实颜色和大小的恒星，
 * 以及同一恒星系里的其他行星和卫星，按轨道算出的方向和相位画出来。
 *
 * <p>天球按原版太阳的角度转动：恒星沿原版太阳的路径走（光照、昼夜和原版一致），其他天体按黄道经纬度
 * 跟它保持相对位置。天色、日落光晕、星星亮度取自环境属性（{@link PlanetEnvironment} 已换成星球的值，
 * 入夜和天气的变化也在里面），这里只负责画。维度类型的 {@code neoforge:custom_skybox} 指向这个渲染器；
 * 星团信息还没到时返回 false，退回原版天空。
 */
public final class PlanetSkyRenderer implements CustomSkyboxRenderer {
    public static final Identifier ID = Universe.id("planet");
    private static final ContextKey<SkyFrame> FRAME = new ContextKey<>(Universe.id("sky_frame"));
    /** 天体画在离观察者多远的地方（和原版太阳一样）。 */
    private static final float DISTANCE = 100;
    private static final int STAR_COUNT = 2600;
    private static final int BAND_SEGMENTS = 180;
    private static final float HALF_PI = (float) (Math.PI / 2);

    /** 渲染线程上第一次画天空时才取，模组构造时还没有渲染线程。 */
    private RenderSystem.@Nullable AutoStorageIndexBuffer quadIndices;
    private @Nullable GpuBuffer skyDisc;
    private @Nullable GpuBuffer darkDisc;
    private @Nullable GpuBuffer sunriseFan;
    private @Nullable GpuBuffer bodyQuad;
    private @Nullable GpuBuffer starBuffer;
    private int starIndexCount;
    private @Nullable GpuBuffer bandBuffer;
    private int bandIndexCount;
    private long starSystemSeed;
    private @Nullable ResourceKey<Level> cachedDimension;
    private PlanetEnvironment.@Nullable Context cachedContext;

    /** 一帧要画的东西，在提取渲染状态时算好。 */
    private record SkyFrame(PlanetEnvironment.Context context, List<SkyBody> bodies, double days) {
    }

    /** 太空维度的一帧：天体按真实的位置和大小画，观察者就是摄像机。 */
    private record SpaceFrame(StarSystem system, List<SpaceLayout.Body> bodies, Vec3 camera, double days) {
    }

    /** 太空维度的天空（维度类型的 {@code neoforge:custom_skybox} 指向它），和星球天空共用星空和天体的画法。 */
    public static final Identifier SPACE_ID = Universe.id("space");
    private static final ContextKey<SpaceFrame> SPACE_FRAME = new ContextKey<>(Universe.id("space_frame"));
    private int spaceSystemIndex = -1;
    private @Nullable StarSystem spaceSystem;
    private List<SpaceLayout.Body> spaceBodies = List.of();

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        PlanetSkyRenderer renderer = new PlanetSkyRenderer();
        modBus.addListener(RegisterCustomEnvironmentEffectRendererEvent.class, event -> {
            event.registerSkyboxRenderer(ID, renderer);
            event.registerSkyboxRenderer(SPACE_ID, renderer);
        });
        modBus.addListener(RegisterRenderPipelinesEvent.class, SkyPipelines::register);
        gameBus.addListener(ExtractLevelRenderStateEvent.class, renderer::extract);
    }

    private void extract(ExtractLevelRenderStateEvent event) {
        LevelRenderState state = event.getRenderState();
        if (state.customSkyboxRenderer != this) {
            return;
        }
        // 轨道按母星日计时，跟着世界的游戏刻走
        double days = (state.gameTime + state.worldPartialTicks) / 24000.0;
        OptionalInt space = SpaceDimensions.system(event.getLevel().dimension());
        if (space.isPresent()) {
            state.setRenderData(FRAME, null);
            state.setRenderData(SPACE_FRAME, this.spaceFrame(space.getAsInt(), event.getCamera().position(), days));
            return;
        }
        state.setRenderData(SPACE_FRAME, null);
        PlanetEnvironment.Context context = this.context(event.getLevel().dimension());
        if (context == null) {
            state.setRenderData(FRAME, null);
            return;
        }
        List<SkyBody> bodies = SkyModel.bodies(context.system(), context.planet(), days);
        state.setRenderData(FRAME, new SkyFrame(context, bodies, days));
    }

    private PlanetEnvironment.@Nullable Context context(ResourceKey<Level> dimension) {
        if (dimension != this.cachedDimension || this.cachedContext == null) {
            this.cachedDimension = dimension;
            this.cachedContext = ClientGalaxy.galaxy()
                    .flatMap(galaxy -> PlanetEnvironment.context(galaxy, dimension))
                    .orElse(null);
        }
        return this.cachedContext;
    }

    private @Nullable SpaceFrame spaceFrame(int systemIndex, Vec3 camera, double days) {
        if (systemIndex != this.spaceSystemIndex || this.spaceSystem == null) {
            this.spaceSystem = ClientGalaxy.galaxy().flatMap(galaxy -> galaxy.system(systemIndex)).orElse(null);
            this.spaceBodies = this.spaceSystem == null ? List.of() : SpaceLayout.of(this.spaceSystem);
            this.spaceSystemIndex = this.spaceSystem == null ? -1 : systemIndex;
        }
        return this.spaceSystem == null ? null : new SpaceFrame(this.spaceSystem, this.spaceBodies, camera, days);
    }

    @Override
    public boolean renderSky(LevelRenderState levelState, SkyRenderState sky, Matrix4fc modelView, GpuBufferSlice skyFog) {
        SpaceFrame space = levelState.getRenderData(SPACE_FRAME);
        if (space != null) {
            this.renderSpace(space, skyFog);
            return true;
        }
        SkyFrame frame = levelState.getRenderData(FRAME);
        if (frame == null) {
            return false;
        }
        this.ensureBuffers(frame.context().system().seed());
        RenderSystem.setShaderFog(skyFog);
        RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Universe planet sky",
                target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            this.drawSkyDisc(pass, sky.skyColor);
            this.drawSunrise(pass, sky.sunAngle, sky.sunriseAndSunsetColor);
            SkyColors colors = frame.context().colors();
            float dayFactor = dayFactor(sky.skyColor);
            float stars = Mth.clamp(sky.starBrightness * 2.0F, 0.0F, 1.0F) * sky.rainBrightness;
            if (stars > 0.002F) {
                this.drawStarField(pass, sky.starAngle, stars);
            }
            for (SkyBody body : frame.bodies()) {
                if (body.kind() == SkyBody.Kind.STAR) {
                    this.drawStar(pass, body, sky, colors);
                } else {
                    this.drawBody(pass, body, frame.days(), sky, colors, dayFactor);
                }
            }
            if (sky.shouldRenderDarkDisc) {
                this.drawDarkDisc(pass);
            }
        }
        return true;
    }

    /**
     * 太空：黑色背景上的星空，恒星和每颗行星、卫星按摄像机到它的真实距离和大气球半径算出视大小，
     * 都画在天球上（深度不写），所以总在飞船和方块后面——飞船飞进大气球之前就会切换维度，不会穿进星球里。
     */
    private void renderSpace(SpaceFrame frame, GpuBufferSlice skyFog) {
        this.ensureBuffers(frame.system().seed());
        RenderSystem.setShaderFog(skyFog);
        RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Universe space sky",
                target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            this.drawSkyDisc(pass, new Vector3f(0.0F, 0.0F, 0.0F));
            this.drawStarField(pass, 0.0F, 1.0F);
            Vec3 camera = frame.camera();
            Vector3f toStar = new Vector3f((float) -camera.x, (float) -camera.y, (float) -camera.z);
            double starDistance = toStar.length();
            toStar.normalize();
            float starAngular = (float) Math.max(0.8, Math.toDegrees(2 * Math.atan(SpaceLayout.STAR_RADIUS / Math.max(starDistance, 1))));
            boolean starDrawn = starDistance <= 1;
            // 由远及近地画，近的盖住远的；恒星按它的距离插在中间，整个躲在恒星背后的星球不画
            List<SpaceLayout.Body> bodies = new ArrayList<>(frame.bodies());
            bodies.sort(Comparator.comparingDouble(body -> -body.distanceTo(camera.x, camera.y, camera.z)));
            for (SpaceLayout.Body body : bodies) {
                double distance = body.distanceTo(camera.x, camera.y, camera.z);
                if (distance <= body.radius()) {
                    continue;
                }
                if (!starDrawn && distance < starDistance) {
                    this.drawStarAt(pass, toStar, starAngular, frame.system().star().color());
                    starDrawn = true;
                }
                // 再远也留一个小点，看得出那里有颗星球
                float angular = (float) Math.max(0.3, Math.toDegrees(2 * Math.asin(body.radius() / distance)));
                Vector3f direction = new Vector3f((float) (body.x() - camera.x), (float) (body.y() - camera.y),
                        (float) (body.z() - camera.z)).normalize();
                if (!starDrawn) {
                    double apart = Math.toDegrees(Math.acos(Mth.clamp(direction.dot(toStar), -1.0F, 1.0F)));
                    if (apart < (starAngular - angular) / 2) {
                        continue;
                    }
                }
                Vector3f light = new Vector3f((float) -body.x(), (float) -body.y(), (float) -body.z()).normalize();
                this.drawBodyAt(pass, body.planet(), direction, angular, light, frame.days());
            }
            if (!starDrawn) {
                this.drawStarAt(pass, toStar, starAngular, frame.system().star().color());
            }
        }
    }

    private void drawStarAt(RenderPass pass, Vector3f direction, float angularDiameter, int color) {
        Matrix4f celestial = celestialFor(direction);
        float radius = radiusAtDistance(angularDiameter);
        // 真空里没有散射，只留一圈不大的光晕
        float extent = Math.max(2.0F, 8.0F / (angularDiameter / 2));
        Matrix4f matrix = bodyMatrix(celestial, radius * extent);
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(matrix,
                new Vector4f(Colors.red(color) / 255.0F, Colors.green(color) / 255.0F, Colors.blue(color) / 255.0F, 1.0F),
                new Vector3f(0.0F, extent, 0.0F),
                new Matrix4f());
        this.drawQuad(pass, SkyPipelines.STAR, transform);
    }

    private void drawBodyAt(RenderPass pass, Planet planet, Vector3f direction, float angularDiameter, Vector3f lightWorld, double days) {
        BodyLook look = BodyLook.of(planet);
        Matrix4f celestial = celestialFor(direction);
        Vector3f e1 = celestial.transformDirection(new Vector3f(1.0F, 0.0F, 0.0F));
        Vector3f e2 = celestial.transformDirection(new Vector3f(0.0F, 0.0F, 1.0F));
        Vector3f toViewer = new Vector3f(direction).negate();
        Vector3f light = new Vector3f(lightWorld.dot(e1), lightWorld.dot(e2), lightWorld.dot(toViewer));
        // 黄道坐标里 x 是黄道面的法线，太空维度里黄道面是 y = 0
        double[] axisEcliptic = look.axis();
        Vector3f axisWorld = new Vector3f((float) axisEcliptic[1], (float) axisEcliptic[0], (float) axisEcliptic[2]);
        Vector3f axis = new Vector3f(axisWorld.dot(e1), axisWorld.dot(e2), axisWorld.dot(toViewer)).normalize();
        int[] colors = SkyModel.bodyColors(planet);
        this.drawDisc(pass, planet, look, celestial, angularDiameter, light, axis, colors[0], colors[1], 1.0F, 1.0F, days);
    }

    /** 让天球坐标的 +y 指向 {@code direction} 的旋转（列向量依次是圆盘的两个轴和指向）。 */
    private static Matrix4f celestialFor(Vector3f direction) {
        Vector3f reference = Math.abs(direction.y()) < 0.95F ? new Vector3f(0.0F, 1.0F, 0.0F) : new Vector3f(1.0F, 0.0F, 0.0F);
        Vector3f e1 = new Vector3f(reference).cross(direction).normalize();
        Vector3f e2 = new Vector3f(e1).cross(direction).normalize();
        return new Matrix4f(
                e1.x(), e1.y(), e1.z(), 0.0F,
                direction.x(), direction.y(), direction.z(), 0.0F,
                e2.x(), e2.y(), e2.z(), 0.0F,
                0.0F, 0.0F, 0.0F, 1.0F);
    }

    /** 天空有多亮：0 是夜里或真空，1 是母星正午那样的蓝天。 */
    private static float dayFactor(Vector3fc skyColor) {
        float brightest = Math.max(skyColor.x(), Math.max(skyColor.y(), skyColor.z()));
        return Mth.clamp(brightest / 0.6F, 0.0F, 1.0F);
    }

    private void drawSkyDisc(RenderPass pass, Vector3fc skyColor) {
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrixCopy(), new Vector4f(skyColor, 1.0F));
        pass.setPipeline(RenderSystem.getCompiledPipeline(RenderPipelines.SKY));
        pass.setUniform("DynamicTransforms", transform);
        pass.setVertexBuffer(0, this.skyDisc.slice());
        pass.draw(10, 1, 0, 0);
    }

    private void drawDarkDisc(RenderPass pass) {
        Matrix4f matrix = new Matrix4f(RenderSystem.getModelViewStack()).translate(0.0F, 12.0F, 0.0F);
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(matrix, new Vector4f(0.0F, 0.0F, 0.0F, 1.0F));
        pass.setPipeline(RenderSystem.getCompiledPipeline(RenderPipelines.SKY));
        pass.setUniform("DynamicTransforms", transform);
        pass.setVertexBuffer(0, this.darkDisc.slice());
        pass.draw(10, 1, 0, 0);
    }

    /** 原版的日出日落光晕，颜色和强度已经按星球的大气换过。 */
    private void drawSunrise(RenderPass pass, float sunAngle, Vector4fc color) {
        float alpha = color.w();
        if (alpha <= 0.001F) {
            return;
        }
        float side = Mth.sin(sunAngle) < 0.0F ? (float) Math.PI : 0.0F;
        Matrix4f matrix = new Matrix4f(RenderSystem.getModelViewStack())
                .rotateX(HALF_PI)
                .rotateZ(side + HALF_PI)
                .scale(1.0F, 1.0F, alpha);
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(matrix, new Vector4f(color));
        pass.setPipeline(RenderSystem.getCompiledPipeline(RenderPipelines.SUNRISE_SUNSET));
        pass.setUniform("DynamicTransforms", transform);
        pass.setVertexBuffer(0, this.sunriseFan.slice());
        pass.draw(18, 1, 0, 0);
    }

    private void drawStarField(RenderPass pass, float starAngle, float brightness) {
        Matrix4f matrix = new Matrix4f(RenderSystem.getModelViewStack()).rotateY(-HALF_PI).rotateX(starAngle);
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms()
                .writeTransform(matrix, new Vector4f(1.0F, 1.0F, 1.0F, brightness));
        pass.setPipeline(RenderSystem.getCompiledPipeline(SkyPipelines.POINTS));
        pass.setUniform("DynamicTransforms", transform);
        pass.setVertexBuffer(0, this.bandBuffer.slice());
        pass.setIndexBuffer(this.quadIndices.getBuffer(this.bandIndexCount), this.quadIndices.type());
        pass.drawIndexed(this.bandIndexCount, 1, 0, 0, 0);
        pass.setVertexBuffer(0, this.starBuffer.slice());
        pass.setIndexBuffer(this.quadIndices.getBuffer(this.starIndexCount), this.quadIndices.type());
        pass.drawIndexed(this.starIndexCount, 1, 0, 0, 0);
    }

    private void drawStar(RenderPass pass, SkyBody star, SkyRenderState sky, SkyColors colors) {
        Matrix4f celestial = new Matrix4f().rotateY(-HALF_PI).rotateX(sky.sunAngle);
        Vector3f direction = celestial.transformDirection(new Vector3f(0.0F, 1.0F, 0.0F));
        float radius = radiusAtDistance(star.angularDiameter());
        // 光晕的大小按角度定（有大气时约 25–40 度），不随恒星的视大小放大
        float haloDegrees = colors.scatter() > 0 ? 25.0F + 15.0F * (float) Math.min(colors.scatter(), 1.0) : 8.0F;
        float extent = Math.max(2.0F, haloDegrees / (float) (star.angularDiameter() / 2));
        // 沙尘里只剩一团暗淡的光
        float visibility = sky.rainBrightness * horizonFade(direction.y(), colors.scatter()) * (1.0F - 0.92F * ClientWeather.intensity());
        if (visibility <= 0.001F) {
            return;
        }
        Matrix4f matrix = bodyMatrix(celestial, radius * extent);
        int color = star.color();
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(matrix,
                new Vector4f(Colors.red(color) / 255.0F, Colors.green(color) / 255.0F, Colors.blue(color) / 255.0F, visibility),
                new Vector3f((float) colors.scatter(), extent, 0.0F),
                new Matrix4f());
        this.drawQuad(pass, SkyPipelines.STAR, transform);
    }

    private void drawBody(RenderPass pass, SkyBody body, double days, SkyRenderState sky, SkyColors colors, float dayFactor) {
        Planet planet = body.planet();
        BodyLook look = BodyLook.of(planet);
        // 天球坐标：先转到恒星所在的位置，再按黄道经纬度偏开
        Matrix4f celestial = new Matrix4f()
                .rotateY(-HALF_PI)
                .rotateX(sky.sunAngle - (float) body.longitude())
                .rotateZ((float) body.latitude());
        Vector3f direction = celestial.transformDirection(new Vector3f(0.0F, 1.0F, 0.0F));
        float visibility = sky.rainBrightness * horizonFade(direction.y(), colors.scatter()) * (1.0F - ClientWeather.intensity());
        if (body.kind() == SkyBody.Kind.PLANET || body.kind() == SkyBody.Kind.SIBLING) {
            // 小而暗的行星在白天的天光里几乎看不见
            visibility *= 1.0F - 0.8F * dayFactor;
        }
        if (visibility <= 0.001F) {
            return;
        }
        Vector3f e1 = celestial.transformDirection(new Vector3f(1.0F, 0.0F, 0.0F));
        Vector3f e2 = celestial.transformDirection(new Vector3f(0.0F, 0.0F, 1.0F));
        Vector3f toViewer = new Vector3f(direction).negate();
        // 圆盘坐标系里的光照方向：亮的一侧朝着恒星，相位角由亮面比例反推
        Vector3f sun = new Matrix4f().rotateY(-HALF_PI).rotateX(sky.sunAngle).transformDirection(new Vector3f(0.0F, 1.0F, 0.0F));
        Vector3f towardSun = new Vector3f(sun).sub(new Vector3f(direction).mul(sun.dot(direction)));
        if (towardSun.lengthSquared() < 1.0E-8F) {
            towardSun.set(e1);
        }
        towardSun.normalize();
        float phase = (float) Math.acos(Mth.clamp(2.0 * body.illumination() - 1.0, -1.0, 1.0));
        float sinPhase = Mth.sin(phase);
        Vector3f light = new Vector3f(sinPhase * towardSun.dot(e1), sinPhase * towardSun.dot(e2), Mth.cos(phase));
        // 自转轴固定在天球上，跟着星空一起转
        double[] axisEcliptic = look.axis();
        Vector3f axisWorld = new Matrix4f().rotateY(-HALF_PI).rotateX(sky.starAngle)
                .transformDirection(new Vector3f((float) axisEcliptic[0], (float) axisEcliptic[1], (float) axisEcliptic[2]));
        Vector3f axis = new Vector3f(axisWorld.dot(e1), axisWorld.dot(e2), axisWorld.dot(toViewer)).normalize();
        float occlusion = 1.0F - 0.97F * dayFactor;
        this.drawDisc(pass, planet, look, celestial, (float) body.angularDiameter(), light, axis, body.color(), body.bandColor(),
                visibility, occlusion, days);
    }

    /**
     * 画一颗星球的圆盘。
     *
     * @param light     圆盘坐标系里指向恒星的方向（x、y 沿圆盘的两个轴，z 指向观察者）
     * @param axis      圆盘坐标系里的自转轴
     * @param occlusion 天光遮住多少（白天的天光会盖住暗面）
     */
    private void drawDisc(RenderPass pass, Planet planet, BodyLook look, Matrix4f celestial, float angularDiameter, Vector3f light,
            Vector3f axis, int main, int band, float visibility, float occlusion, double days) {
        float rimStrength = rimStrength(planet);
        float extent = look.hasRings() ? (float) look.ringOuter() + 0.05F : rimStrength > 0 ? 1.15F : 1.03F;
        float radius = radiusAtDistance(angularDiameter);
        Matrix4f matrix = bodyMatrix(celestial, radius * extent);
        int rim = planet.palette().sky();
        Matrix4f parameters = new Matrix4f(
                light.x(), light.y(), light.z(), look.surface().shaderId(),
                Colors.red(band) / 255.0F, Colors.green(band) / 255.0F, Colors.blue(band) / 255.0F, (float) look.pattern(),
                axis.x(), axis.y(), axis.z(), (float) look.ringInner(),
                Colors.red(rim) / 255.0F * rimStrength, Colors.green(rim) / 255.0F * rimStrength,
                Colors.blue(rim) / 255.0F * rimStrength, (float) look.ringOuter());
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(matrix,
                new Vector4f(Colors.red(main) / 255.0F, Colors.green(main) / 255.0F, Colors.blue(main) / 255.0F, visibility),
                new Vector3f(occlusion, extent, (float) look.spinAngle(days)),
                parameters);
        this.drawQuad(pass, SkyPipelines.BODY, transform);
    }

    /** 大气辉光的强度：气态巨行星总有厚大气，没有大气的星球没有。 */
    private static float rimStrength(Planet planet) {
        if (planet.type() == PlanetType.GAS_GIANT) {
            return 0.6F;
        }
        if (!planet.atmosphere().present()) {
            return 0.0F;
        }
        return (float) Math.min(0.8, 0.25 + 0.4 * planet.atmosphere().pressure());
    }

    /** 落到地平线附近变暗、变没；有大气时消光更明显。 */
    private static float horizonFade(float height, double scatter) {
        float fade = Mth.clamp((height + 0.03F) / 0.05F, 0.0F, 1.0F);
        if (scatter > 0) {
            fade *= 1.0F - (float) Math.min(scatter, 1.0) * 0.5F * (1.0F - Mth.clamp(height / 0.15F, 0.0F, 1.0F));
        }
        return fade;
    }

    /** 视直径（度）在 {@link #DISTANCE} 处对应的半径。 */
    private static float radiusAtDistance(double angularDiameter) {
        return DISTANCE * (float) Math.tan(Math.toRadians(angularDiameter / 2));
    }

    /** 观察方向乘上天球位置，再推到 {@link #DISTANCE} 处、放大成指定半宽的四边形。 */
    private static Matrix4f bodyMatrix(Matrix4f celestial, float halfSize) {
        Matrix4fStack stack = RenderSystem.getModelViewStack();
        return new Matrix4f(stack).mul(celestial).translate(0.0F, DISTANCE, 0.0F).scale(halfSize, 1.0F, halfSize);
    }

    private void drawQuad(RenderPass pass, RenderPipeline pipeline, GpuBufferSlice transform) {
        pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
        pass.setUniform("DynamicTransforms", transform);
        pass.setVertexBuffer(0, this.bodyQuad.slice());
        pass.setIndexBuffer(this.quadIndices.getBuffer(6), this.quadIndices.type());
        pass.drawIndexed(6, 1, 0, 0, 0);
    }

    private void ensureBuffers(long seed) {
        if (this.skyDisc == null) {
            this.quadIndices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            this.skyDisc = buildSkyDisc("Universe sky disc", 16.0F);
            this.darkDisc = buildSkyDisc("Universe dark disc", -16.0F);
            this.sunriseFan = buildSunriseFan();
            this.bodyQuad = buildBodyQuad();
        }
        if (this.starBuffer == null || seed != this.starSystemSeed) {
            if (this.starBuffer != null) {
                this.starBuffer.close();
                this.bandBuffer.close();
            }
            StarField field = StarField.generate(seed, STAR_COUNT);
            this.starSystemSeed = seed;
            this.buildStars(field);
            this.buildBand(field, seed);
        }
    }

    private static GpuBuffer buildSkyDisc(String name, float height) {
        return build(name, PrimitiveTopology.TRIANGLE_FAN, DefaultVertexFormat.POSITION, 10, builder -> {
            float radius = Math.signum(height) * 512.0F;
            builder.addVertex(0.0F, height, 0.0F);
            for (int angle = -180; angle <= 180; angle += 45) {
                float rad = angle * Mth.DEG_TO_RAD;
                builder.addVertex(radius * Mth.cos(rad), height, 512.0F * Mth.sin(rad));
            }
        }).buffer();
    }

    private static GpuBuffer buildSunriseFan() {
        return build("Universe sunrise fan", PrimitiveTopology.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR, 18, builder -> {
            builder.addVertex(0.0F, 100.0F, 0.0F).setColor(ARGB.white(1.0F));
            for (int i = 0; i <= 16; i++) {
                float angle = i * Mth.TWO_PI / 16.0F;
                float sin = Mth.sin(angle);
                float cos = Mth.cos(angle);
                builder.addVertex(sin * 120.0F, cos * 120.0F, -cos * 40.0F).setColor(ARGB.white(0.0F));
            }
        }).buffer();
    }

    /** 中心在原点、躺在 xz 平面上的单位四边形，UV 是 -1 到 1 的圆盘坐标。 */
    private static GpuBuffer buildBodyQuad() {
        return build("Universe sky body quad", PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX, 4, builder -> {
            builder.addVertex(-1.0F, 0.0F, -1.0F).setUv(-1.0F, -1.0F);
            builder.addVertex(1.0F, 0.0F, -1.0F).setUv(1.0F, -1.0F);
            builder.addVertex(1.0F, 0.0F, 1.0F).setUv(1.0F, 1.0F);
            builder.addVertex(-1.0F, 0.0F, 1.0F).setUv(-1.0F, 1.0F);
        }).buffer();
    }

    private void buildStars(StarField field) {
        float[] data = field.data();
        Built built = build("Universe star field", PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR,
                field.count() * 4, builder -> {
                    for (int i = 0; i < field.count(); i++) {
                        int o = i * StarField.STRIDE;
                        Vector3f center = new Vector3f(data[o], data[o + 1], data[o + 2]);
                        float half = 0.2F + 0.55F * data[o + 3];
                        int color = ARGB.colorFromFloat(data[o + 7], data[o + 4], data[o + 5], data[o + 6]);
                        addBillboard(builder, center, half, half, color);
                    }
                });
        this.starBuffer = built.buffer();
        this.starIndexCount = built.indexCount();
    }

    /** 银河：沿星空的银河大圆画两条柔光带，宽的一条很淡，窄的一条更亮、有暗带断开。 */
    private void buildBand(StarField field, long seed) {
        double[] pole = field.galacticPole();
        Vector3f normal = new Vector3f((float) pole[0], (float) pole[1], (float) pole[2]).normalize();
        Vector3f a = new Vector3f(normal).cross(Math.abs(normal.z()) < 0.9F ? new Vector3f(0, 0, 1) : new Vector3f(1, 0, 0)).normalize();
        Vector3f b = new Vector3f(normal).cross(a);
        SimplexNoise noise = new SimplexNoise(Seeds.derive(seed, "sky/band"));
        Rng rng = Rng.of(Seeds.derive(seed, "sky/band_color"));
        int tint = Colors.lerp(0xC8D2FF, 0xFFE2C0, rng.nextDouble());
        Built built = build("Universe galactic band", PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR,
                BAND_SEGMENTS * 8, builder -> {
                    this.addBandStrip(builder, a, b, normal, noise, 0.28F, 0.06F, tint, 0.0);
                    this.addBandStrip(builder, a, b, normal, noise, 0.09F, 0.10F, Colors.lerp(tint, 0xFFFFFF, 0.3), 40.0);
                });
        this.bandBuffer = built.buffer();
        this.bandIndexCount = built.indexCount();
    }

    private void addBandStrip(BufferBuilder builder, Vector3f a, Vector3f b, Vector3f normal, SimplexNoise noise,
            float halfWidth, float alpha, int tint, double noiseOffset) {
        for (int i = 0; i < BAND_SEGMENTS; i++) {
            float t0 = i * Mth.TWO_PI / BAND_SEGMENTS;
            float t1 = (i + 1) * Mth.TWO_PI / BAND_SEGMENTS;
            float c0 = bandBrightness(noise, t0, noiseOffset);
            float c1 = bandBrightness(noise, t1, noiseOffset);
            float wobble0 = 0.04F * (float) noise.noise(Math.cos(t0) * 2 + 100, Math.sin(t0) * 2 + noiseOffset);
            float wobble1 = 0.04F * (float) noise.noise(Math.cos(t1) * 2 + 100, Math.sin(t1) * 2 + noiseOffset);
            Vector3f p0 = circle(a, b, t0);
            Vector3f p1 = circle(a, b, t1);
            int color0 = ARGB.color((int) (alpha * c0 * 255), tint);
            int color1 = ARGB.color((int) (alpha * c1 * 255), tint);
            bandVertex(builder, p0, normal, wobble0 - halfWidth, -1.0F, color0);
            bandVertex(builder, p1, normal, wobble1 - halfWidth, -1.0F, color1);
            bandVertex(builder, p1, normal, wobble1 + halfWidth, 1.0F, color1);
            bandVertex(builder, p0, normal, wobble0 + halfWidth, 1.0F, color0);
        }
    }

    private static float bandBrightness(SimplexNoise noise, float angle, double offset) {
        double x = Math.cos(angle) * 3;
        double y = Math.sin(angle) * 3;
        double n = noise.fbm(x + offset, y - offset, 4);
        return Mth.clamp((float) (0.55 + 0.9 * n), 0.0F, 1.2F);
    }

    private static Vector3f circle(Vector3f a, Vector3f b, float angle) {
        return new Vector3f(a).mul(Mth.cos(angle)).add(new Vector3f(b).mul(Mth.sin(angle)));
    }

    private static void bandVertex(BufferBuilder builder, Vector3f onCircle, Vector3f normal, float offset, float v, int color) {
        Vector3f position = new Vector3f(onCircle).add(new Vector3f(normal).mul(offset)).normalize(DISTANCE);
        builder.addVertex(position.x(), position.y(), position.z()).setUv(0.0F, v).setColor(color);
    }

    /** 以 {@code direction} 为中心、面向原点的小四边形，推到 {@link #DISTANCE} 处。 */
    private static void addBillboard(BufferBuilder builder, Vector3f direction, float halfU, float halfV, int color) {
        Vector3f center = new Vector3f(direction).normalize(DISTANCE);
        Vector3f reference = Math.abs(direction.y()) < 0.9F ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        Vector3f u = new Vector3f(direction).cross(reference).normalize(halfU);
        Vector3f v = new Vector3f(direction).cross(u).normalize(halfV);
        vertex(builder, center, u, v, -1, -1, color);
        vertex(builder, center, u, v, 1, -1, color);
        vertex(builder, center, u, v, 1, 1, color);
        vertex(builder, center, u, v, -1, 1, color);
    }

    private static void vertex(BufferBuilder builder, Vector3f center, Vector3f u, Vector3f v, int su, int sv, int color) {
        builder.addVertex(center.x() + u.x() * su + v.x() * sv, center.y() + u.y() * su + v.y() * sv,
                center.z() + u.z() * su + v.z() * sv).setUv(su, sv).setColor(color);
    }

    private record Built(GpuBuffer buffer, int indexCount) {
    }

    private interface Filler {
        void fill(BufferBuilder builder);
    }

    private static Built build(String name, PrimitiveTopology topology, VertexFormat format, int vertices, Filler filler) {
        Supplier<String> label = () -> name;
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(vertices * format.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(bytes, topology, format);
            filler.fill(builder);
            try (MeshData mesh = builder.buildOrThrow()) {
                GpuBuffer buffer = RenderSystem.getDevice().createBuffer(label, GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
                return new Built(buffer, mesh.drawState().indexCount());
            }
        }
    }
}
