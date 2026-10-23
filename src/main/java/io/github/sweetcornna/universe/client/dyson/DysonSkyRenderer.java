package io.github.sweetcornna.universe.client.dyson;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

import org.joml.Matrix4f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.ClientConfig;
import io.github.sweetcornna.universe.client.ClientGalaxy;
import io.github.sweetcornna.universe.client.ClientWeather;
import io.github.sweetcornna.universe.client.sky.PlanetSkyRenderer;
import io.github.sweetcornna.universe.core.dyson.DysonGrid;
import io.github.sweetcornna.universe.core.dyson.DysonLayer;
import io.github.sweetcornna.universe.core.dyson.DysonPalette;
import io.github.sweetcornna.universe.core.dyson.DysonSky;
import io.github.sweetcornna.universe.core.dyson.DysonSphere;
import io.github.sweetcornna.universe.core.dyson.SwarmOrbit;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.sky.SkyModel;
import io.github.sweetcornna.universe.core.space.SpaceLayout;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/**
 * 天空里的戴森球（ADR-0016）：建好的节点、框架、球壳，和戴森云的太阳帆。所有维度共用：
 * 星球（包括用原版天空的主世界）上按观察者在轨道上的位置看过去，角距离重新映射到恒星圆盘外面（{@link DysonSky}）；
 * 太空维度里按真实的位置画（半径换成太空里的距离，和行星的摆法一致）。
 *
 * <p>画在天空自己的那个 {@code RenderPass} 里，排在天空的最后：本模组的维度由 {@link PlanetSkyRenderer} 调
 * {@link #drawInSky}，用原版天空的维度由 {@code SkyRendererMixin} 调。不另开 RenderPass，是因为开着 Iris 光影包时，
 * 新开的 RenderPass 绑的是原版的主帧缓冲，光影包最后一步会把它整个盖掉；天空的 RenderPass 里前面画过光影包接管的原版天空，
 * 后面画的东西跟着进了光影包的缓冲区（ADR-0019）。RenderPass 开着的时候不能写缓冲区，所以顶点在提取阶段就上传好。
 *
 * <p>背面的框架和节点先画、暗一些；然后是正面的球壳（半透明，能挡住后面的恒星）；最后是正面的框架、节点和帆。
 * 发光的部分在暗的天空里叠加混合；亮的天空里叠加会发白，按天空的亮度换成半透明的金色。
 */
public final class DysonSkyRenderer {
    /** 画在离观察者多远的天球上（和天空渲染器一样）。 */
    private static final float DISTANCE = 100;
    private static final RenderPipeline.Snippet SNIPPET = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withVertexShader(Identifier.withDefaultNamespace("core/position_color"))
            .withFragmentShader(Identifier.withDefaultNamespace("core/position_color"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withCull(false)
            .buildSnippet();
    /** 框架、节点、帆：叠加。 */
    static final RenderPipeline GLOW = RenderPipeline.builder(SNIPPET)
            .withLocation(Universe.id("pipeline/sky_dyson_glow"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .build();
    /** 球壳：半透明。 */
    static final RenderPipeline SHELL = RenderPipeline.builder(SNIPPET)
            .withLocation(Universe.id("pipeline/sky_dyson_shell"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .build();

    private static final double FRAME_STEP = Math.toRadians(6);
    private static final double FRAME_STEP_LOW = Math.toRadians(15);
    private static final double SHELL_STEP = Math.toRadians(8);
    private static final float FRAME_WIDTH = 0.075F;
    private static final float NODE_SIZE = 0.24F;
    private static final float SAIL_SIZE = 0.09F;
    private static final int MAX_SAIL_DOTS = 700;
    private static final int MAX_SAIL_DOTS_LOW = 200;
    /** 原版太阳的亮方块：边长 30 的贴图中间一半，离观察者 100 格；戴森球从它的角外面开始画。 */
    private static final double VANILLA_SUN_CORNER = Math.atan(7.5 * Math.sqrt(2) / 100);
    /** 亮的天空里发光部分改用的颜色：深一些的金色，蓝天上看得出来。 */
    private static final int TINT = 0xC88420;

    /** 背面的球壳（ADR-0030 §四）：最先画，在背面的框架和节点后面。 */
    private final Batch backShells = new Batch();
    private final Batch backTint = new Batch();
    private final Batch back = new Batch();
    private final Batch shells = new Batch();
    private final Batch frontTint = new Batch();
    private final Batch front = new Batch();
    /** 按画的顺序：背面的球壳、背面（半透明、叠加）、正面的球壳、正面（半透明、叠加）。 */
    private final Batch[] batches = {this.backShells, this.backTint, this.back, this.shells, this.frontTint, this.front};
    private static final RenderPipeline[] PIPELINES = {SHELL, SHELL, GLOW, SHELL, SHELL, GLOW};
    private final Frame frame = new Frame(this.batches);
    private static @Nullable DysonSkyRenderer instance;
    /** 这一帧要画的（提取阶段定下来，天空里画）；没有要画的是 null。 */
    private @Nullable Frame toDraw;
    /** 天空有多亮：0 是黑的（夜里、太空、没有大气），1 是晴天的蓝天。 */
    private double bright;
    /** 这一帧按简化画质画（设置里的戴森球画质）。 */
    private boolean low;
    // 顶点每刻只算一次（天上的东西转得慢，最快的帆一刻也挪不到两三个像素），同一刻里的后几帧直接画上次上传的
    private long builtTick = Long.MIN_VALUE;
    private @Nullable ResourceKey<Level> builtDimension;
    private ClientConfig.@Nullable DysonSky builtDetail;
    private int builtSystem = -1;
    private Vec3 builtCamera = Vec3.ZERO;
    /** 顶点重算一次加一；和上传时记下的不一样才重新上传。 */
    private int version;
    private int uploadedVersion = -1;
    private final long[] offsets = new long[6];
    // 开发脚本测帧率用：累计的 CPU 耗时（算顶点和上传）、帧数、上一帧的顶点数
    private static long cpuNanos;
    private static int cpuFrames;
    private static int lastVertices;
    private @Nullable GpuBuffer buffer;
    private @Nullable ByteBufferBuilder bytes;

    /** 一帧要画的几批四边形。 */
    private record Frame(Batch[] batches) {
        boolean isEmpty() {
            for (Batch batch : this.batches) {
                if (batch.vertices > 0) {
                    return false;
                }
            }
            return true;
        }
    }

    /** 攒一批四边形：顶点是天球上的位置和颜色。 */
    private static final class Batch {
        float[] xyz = new float[3 * 4096];
        int[] colors = new int[4096];
        int vertices;

        void clear() {
            this.vertices = 0;
        }

        void vertex(double x, double y, double z, int color) {
            if (this.vertices == this.colors.length) {
                this.colors = Arrays.copyOf(this.colors, this.colors.length * 2);
                this.xyz = Arrays.copyOf(this.xyz, this.xyz.length * 2);
            }
            this.xyz[this.vertices * 3] = (float) x;
            this.xyz[this.vertices * 3 + 1] = (float) y;
            this.xyz[this.vertices * 3 + 2] = (float) z;
            this.colors[this.vertices] = color;
            this.vertices++;
        }

        /** a、b 是天球上的点：沿天球切向加宽成细长条。 */
        void line(double[] a, double[] b, float halfWidth, int color) {
            double nx = a[1] * b[2] - a[2] * b[1];
            double ny = a[2] * b[0] - a[0] * b[2];
            double nz = a[0] * b[1] - a[1] * b[0];
            double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length < 1e-6) {
                return;
            }
            nx *= halfWidth / length;
            ny *= halfWidth / length;
            nz *= halfWidth / length;
            this.vertex(a[0] + nx, a[1] + ny, a[2] + nz, color);
            this.vertex(a[0] - nx, a[1] - ny, a[2] - nz, color);
            this.vertex(b[0] - nx, b[1] - ny, b[2] - nz, color);
            this.vertex(b[0] + nx, b[1] + ny, b[2] + nz, color);
        }

        /** 天球上 p 处一个正对观察者的小方块。 */
        void dot(double[] p, float half, int color) {
            double[] reference = Math.abs(p[1]) < 0.9 * DISTANCE ? new double[] {0, 1, 0} : new double[] {1, 0, 0};
            double ux = p[1] * reference[2] - p[2] * reference[1];
            double uy = p[2] * reference[0] - p[0] * reference[2];
            double uz = p[0] * reference[1] - p[1] * reference[0];
            double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
            ux *= half / ul;
            uy *= half / ul;
            uz *= half / ul;
            double vx = (p[1] * uz - p[2] * uy) / DISTANCE;
            double vy = (p[2] * ux - p[0] * uz) / DISTANCE;
            double vz = (p[0] * uy - p[1] * ux) / DISTANCE;
            this.vertex(p[0] - ux - vx, p[1] - uy - vy, p[2] - uz - vz, color);
            this.vertex(p[0] + ux - vx, p[1] + uy - vy, p[2] + uz - vz, color);
            this.vertex(p[0] + ux + vx, p[1] + uy + vy, p[2] + uz + vz, color);
            this.vertex(p[0] - ux + vx, p[1] - uy + vy, p[2] - uz + vz, color);
        }

        void triangle(double[] a, double[] b, double[] c, int color) {
            this.vertex(a[0], a[1], a[2], color);
            this.vertex(b[0], b[1], b[2], color);
            this.vertex(c[0], c[1], c[2], color);
            this.vertex(c[0], c[1], c[2], color);
        }
    }

    /** 开发脚本用：上次调用以来平均每帧的 CPU 耗时（毫秒）和上一帧的顶点数，读完清零。 */
    public static String takeStats() {
        String stats = String.format(Locale.ROOT, "%.3f ms/frame, %d vertices", cpuFrames == 0 ? 0 : cpuNanos / 1e6 / cpuFrames, lastVertices);
        cpuNanos = 0;
        cpuFrames = 0;
        return stats;
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        DysonSkyRenderer renderer = new DysonSkyRenderer();
        instance = renderer;
        modBus.addListener(RegisterRenderPipelinesEvent.class, event -> {
            event.registerPipeline(GLOW);
            event.registerPipeline(SHELL);
        });
        gameBus.addListener(ExtractLevelRenderStateEvent.class, event -> {
            long start = System.nanoTime();
            renderer.extract(event);
            cpuNanos += System.nanoTime() - start;
            cpuFrames++;
        });
    }

    /** 在天空的 RenderPass 里画这一帧的戴森球，排在天空最后。只在渲染线程上调。 */
    public static void drawInSky(RenderPass pass) {
        DysonSkyRenderer renderer = instance;
        if (renderer == null || renderer.toDraw == null) {
            return;
        }
        long start = System.nanoTime();
        renderer.draw(pass, renderer.toDraw);
        cpuNanos += System.nanoTime() - start;
    }

    // ---- 提取：算出天球上的位置

    /** 从观察者看过去：戴森球坐标里的点（AU）在天球上的位置，和它在不在朝向观察者的一面。 */
    private interface Projector {
        /** @param unit 球面上的单位向量（已经转好朝向），radius 是层或轨道的半径（AU） */
        double[] project(double[] unit, double radius);

        boolean facing(double[] unit);
    }

    private void extract(ExtractLevelRenderStateEvent event) {
        LevelRenderState state = event.getRenderState();
        this.toDraw = null;
        lastVertices = 0;
        ClientLevel level = event.getLevel();
        OptionalInt systemIndex = ClientDyson.currentSystem();
        Optional<Galaxy> galaxy = ClientGalaxy.galaxy();
        if (systemIndex.isEmpty() || galaxy.isEmpty()) {
            return;
        }
        ClientConfig.DysonSky detail = ClientConfig.dysonSky();
        if (detail == ClientConfig.DysonSky.OFF) {
            return;
        }
        this.low = detail == ClientConfig.DysonSky.LOW;
        DysonSphere sphere = ClientDyson.sphere(systemIndex.getAsInt()).orElse(null);
        StarSystem system = galaxy.get().system(systemIndex.getAsInt()).orElse(null);
        if (sphere == null || system == null || sphere.orbits().isEmpty() && sphere.layers().isEmpty()) {
            return;
        }
        double time = state.gameTime;
        Vec3 camera = event.getCamera().position();
        Projector projector;
        float visibility;
        if (SpaceDimensions.system(level.dimension()).isPresent()) {
            projector = space(camera);
            visibility = 1;
            this.bright = 0;
        } else {
            Planet observer = Planets.bodyId(level.dimension()).flatMap(galaxy.get()::body).orElse(null);
            if (observer == null) {
                return;
            }
            Planet parent = observer.moon() ? system.planets().get(observer.id().planet()) : null;
            double[] here = SkyModel.position(system, observer, parent, time / 24000.0);
            double sunAngle = Math.toRadians(level.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, BlockPos.containing(camera)));
            double inner = 1.3 * Math.toRadians(SkyModel.starDiameter(system, here) / 2);
            if (!(state.customSkyboxRenderer instanceof PlanetSkyRenderer)) {
                // 原版天空（母星）：太阳是个大方块，比真实的恒星圆盘大得多
                inner = Math.max(inner, 1.1 * VANILLA_SUN_CORNER);
            }
            projector = planet(here, sunAngle, inner);
            Vector3fc sky = level.environmentAttributes().getValue(EnvironmentAttributes.SKY_COLOR, BlockPos.containing(camera));
            this.bright = Math.max(0, Math.min(1, (0.3 * sky.x() + 0.59 * sky.y() + 0.11 * sky.z()) * 1.5));
            // 白天的天光里淡一些，下雨、沙尘暴里几乎看不见
            double day = Math.max(0, Math.min(1, Math.cos(sunAngle) * 2 + 0.3));
            visibility = (float) ((1 - 0.45 * day) * (1 - 0.8 * level.getRainLevel(1.0F)) * (1 - 0.9 * ClientWeather.intensity()));
        }
        if (visibility <= 0.01F) {
            return;
        }
        if (state.gameTime == this.builtTick && level.dimension() == this.builtDimension && detail == this.builtDetail
                && systemIndex.getAsInt() == this.builtSystem && camera.distanceToSqr(this.builtCamera) < 16) {
            this.show();
            return;
        }
        this.builtTick = state.gameTime;
        this.builtDimension = level.dimension();
        this.builtDetail = detail;
        this.builtSystem = systemIndex.getAsInt();
        this.builtCamera = camera;
        this.version++;
        for (Batch batch : this.batches) {
            batch.clear();
        }
        for (DysonLayer layer : sphere.layers()) {
            this.layer(layer, projector, time, visibility);
        }
        for (SwarmOrbit orbit : sphere.orbits()) {
            this.swarm(orbit, projector, time, visibility);
        }
        this.show();
    }

    /** 有东西要画：顶点变了就先上传，记下这一帧要画。 */
    private void show() {
        if (this.frame.isEmpty()) {
            return;
        }
        if (this.uploadedVersion != this.version || this.buffer == null) {
            this.upload();
        }
        this.toDraw = this.frame;
    }

    /** 星球上：真实方向再按角距离映射到恒星圆盘外面。 */
    private static Projector planet(double[] here, double sunAngle, double inner) {
        double[] star = DysonSky.starDirection(sunAngle);
        return new Projector() {
            @Override
            public double[] project(double[] unit, double radius) {
                double[] point = DysonSky.ecliptic(new double[] {unit[0] * radius, unit[1] * radius, unit[2] * radius});
                double[] direction = DysonSky.remap(star, DysonSky.skyDirection(here, point, sunAngle), inner);
                return new double[] {direction[0] * DISTANCE, direction[1] * DISTANCE, direction[2] * DISTANCE};
            }

            @Override
            public boolean facing(double[] unit) {
                return DysonSky.facing(DysonSky.ecliptic(unit), here);
            }
        };
    }

    /** 太空里：恒星在原点，半径按行星的摆法换成格。 */
    private static Projector space(Vec3 camera) {
        double[] eye = {camera.x, camera.y, camera.z};
        return new Projector() {
            @Override
            public double[] project(double[] unit, double radius) {
                double distance = SpaceLayout.orbitDistance(radius);
                double dx = unit[0] * distance - eye[0];
                double dy = unit[1] * distance - eye[1];
                double dz = unit[2] * distance - eye[2];
                double length = Math.max(1e-6, Math.sqrt(dx * dx + dy * dy + dz * dz));
                return new double[] {dx / length * DISTANCE, dy / length * DISTANCE, dz / length * DISTANCE};
            }

            @Override
            public boolean facing(double[] unit) {
                return DysonSky.facing(unit, eye);
            }
        };
    }

    private static int argb(int alpha, int rgb) {
        return Math.max(0, Math.min(255, alpha)) << 24 | rgb & 0xFFFFFF;
    }

    private void layer(DysonLayer layer, Projector projector, double time, float visibility) {
        DysonGrid grid = layer.grid();
        double radius = layer.radius();
        double spin = time / DysonSphere.spinPeriod(radius) * 360;
        double[][] units = new double[grid.nodeCount()][];
        for (int i = 0; i < grid.nodeCount(); i++) {
            units[i] = DysonSphere.orient(grid.node(i), layer.inclination(), layer.ascendingNode(), spin);
        }
        // 球壳：只画朝着观察者的一面，铺得越满越不透明
        for (int s = 0; s < grid.shellCount(); s++) {
            int cells = layer.cells(s);
            if (cells <= 0) {
                continue;
            }
            int[] shell = grid.shell(s);
            double[] center = new double[3];
            for (int node : shell) {
                center[0] += units[node][0];
                center[1] += units[node][1];
                center[2] += units[node][2];
            }
            center = normalize(center);
            // 背面的球壳也画（ADR-0030 §四）：暗一些，画在背面的框架后面；简化画质只画正面
            boolean facing = projector.facing(center);
            if (!facing && this.low) {
                continue;
            }
            double fill = Math.min(1, cells / (double) layer.shellCost(s));
            // 每块壳的亮度略有不同，看得出拼接；蓝天里颜色深一些、更不透明，免得和天色混成灰的
            double shade = 0.85 + 0.15 * Math.sin(s * 12.9898 + layer.id() * 3.1);
            int custom = layer.shellColor(s);
            int rgb;
            if (custom == 0) {
                rgb = mix(mix(0xC8781E, 0xFFC060, shade), mix(0x8A5010, 0xD08A28, shade), this.bright);
            } else {
                // 上过色的：按同样的明暗变化调深浅，蓝天里压暗一点
                int tinted = DysonPalette.mix(DysonPalette.mix(0, custom, shade), custom, 0.5);
                rgb = DysonPalette.mix(tinted, DysonPalette.mix(0, tinted, 0.7), this.bright);
            }
            double side = facing ? 1 : 0.45;
            int color = argb((int) ((30 + 120 * fill) * visibility * (1 + 0.6 * this.bright) * side), rgb);
            Batch batch = facing ? this.shells : this.backShells;
            for (int k = 0; k < shell.length; k++) {
                this.fan(batch, projector, radius, center, units[shell[k]], units[shell[(k + 1) % shell.length]], color);
            }
        }
        // 框架：建好的部分（建了一半的画一截）
        for (int f = 0; f < grid.frameCount(); f++) {
            int sp = layer.frameSp(f);
            if (sp <= 0) {
                continue;
            }
            double progress = Math.min(1, sp / (double) layer.frameCost(f));
            int[] ends = grid.frame(f);
            double[] a = units[ends[0]];
            double[] b = units[ends[1]];
            double arc = Math.acos(Math.max(-1, Math.min(1, a[0] * b[0] + a[1] * b[1] + a[2] * b[2])));
            int steps = Math.max(1, (int) Math.ceil(arc / (this.low ? FRAME_STEP_LOW : FRAME_STEP)));
            double[] previous = a;
            double[] previousSky = projector.project(a, radius);
            for (int k = 1; k <= steps; k++) {
                double t = Math.min(progress, k / (double) steps);
                double[] next = DysonSky.slerp(a, b, t);
                double[] nextSky = projector.project(next, radius);
                boolean facing = projector.facing(normalize(new double[] {previous[0] + next[0], previous[1] + next[1], previous[2] + next[2]}));
                if (facing || !this.low) {
                    this.glowLine(facing, previousSky, nextSky, (facing ? 210 : 70) * visibility,
                            DysonPalette.rgb(layer.frameColor(f), DysonPalette.FRAME));
                }
                if (t >= progress) {
                    break;
                }
                previous = next;
                previousSky = nextSky;
            }
        }
        // 节点
        for (int i = 0; i < grid.nodeCount(); i++) {
            if (!layer.nodeBuilt(i)) {
                continue;
            }
            boolean facing = projector.facing(units[i]);
            if (facing || !this.low) {
                this.glowDot(facing, projector.project(units[i], radius), NODE_SIZE, (facing ? 235 : 90) * visibility,
                        DysonPalette.rgb(layer.nodeColor(i), DysonPalette.NODE));
            }
        }
    }

    /** 球壳的一片扇形（中心和一条边），在球面上细分成小三角形，贴着球面弯。 */
    private void fan(Batch batch, Projector projector, double radius, double[] center, double[] a, double[] b, int color) {
        double arc = Math.max(angle(center, a), angle(center, b));
        int n = this.low ? 1 : Math.max(1, Math.min(6, (int) Math.ceil(arc / SHELL_STEP)));
        double[][][] grid = new double[n + 1][][];
        for (int i = 0; i <= n; i++) {
            grid[i] = new double[i + 1][];
            for (int j = 0; j <= i; j++) {
                // 重心坐标：从中心出发，第 i 行在 a、b 之间分 i 份
                double wc = 1 - i / (double) n;
                double wa = (i - j) / (double) n;
                double wb = j / (double) n;
                double[] p = normalize(new double[] {center[0] * wc + a[0] * wa + b[0] * wb, center[1] * wc + a[1] * wa + b[1] * wb,
                        center[2] * wc + a[2] * wa + b[2] * wb});
                grid[i][j] = projector.project(p, radius);
            }
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                batch.triangle(grid[i][j], grid[i + 1][j], grid[i + 1][j + 1], color);
                if (j < i) {
                    batch.triangle(grid[i][j], grid[i + 1][j + 1], grid[i][j + 1], color);
                }
            }
        }
    }

    private void swarm(SwarmOrbit orbit, Projector projector, double time, float visibility) {
        if (orbit.sails() <= 0) {
            return;
        }
        int dots = (int) Math.min(this.low ? MAX_SAIL_DOTS_LOW : MAX_SAIL_DOTS, 6 + Math.sqrt(orbit.sails()) * 7);
        double phase = time / DysonSphere.sailPeriod(orbit.radius()) * Math.PI * 2;
        for (int i = 0; i < dots; i++) {
            // 每张帆的速度略有不同，慢慢散开；有的在闪
            double a = phase * (1 + 0.015 * Math.sin(i * 7.31)) + i * 2.399963;
            double wobble = 1 + Math.sin(i * 78.233) * 0.02;
            double[] unit = DysonSphere.orient(new double[] {Math.cos(a) * wobble, Math.sin(i * 3.1) * 0.015, Math.sin(a) * wobble},
                    orbit.inclination(), orbit.ascendingNode(), 0);
            boolean facing = projector.facing(unit);
            double twinkle = 0.6 + 0.4 * Math.sin(time * 0.15 + i * 1.7);
            this.glowDot(true, projector.project(unit, orbit.radius()), SAIL_SIZE, (facing ? 200 : 110) * twinkle * visibility, 0xFFE6A0);
        }
    }

    /** 发光的线：暗的天空里叠加；天空越亮，越多地换成半透明的深色。 */
    private void glowLine(boolean facing, double[] a, double[] b, double alpha, int rgb) {
        int tint = (int) (alpha * this.bright);
        int glow = (int) (alpha * (1 - 0.75 * this.bright));
        if (tint > 0) {
            (facing ? this.frontTint : this.backTint).line(a, b, FRAME_WIDTH, argb(tint, tint(rgb)));
        }
        if (glow > 0) {
            (facing ? this.front : this.back).line(a, b, FRAME_WIDTH, argb(glow, rgb));
        }
    }

    /** 发光的点，和 {@link #glowLine} 一样按天空的亮度分到两批。 */
    private void glowDot(boolean facing, double[] p, float half, double alpha, int rgb) {
        int tint = (int) (alpha * this.bright);
        int glow = (int) (alpha * (1 - 0.75 * this.bright));
        if (tint > 0) {
            (facing ? this.frontTint : this.backTint).dot(p, half, argb(tint, tint(rgb)));
        }
        if (glow > 0) {
            (facing ? this.front : this.back).dot(p, half, argb(glow, rgb));
        }
    }

    /**
     * 亮天空里半透明那一份用的颜色：默认的金色照旧用 {@link #TINT}（调出来的，蓝天上最像），上过色的按本色压暗，
     * 免得玩家涂的蓝色、绿色在白天全变成金的。
     */
    private static int tint(int rgb) {
        return rgb == DysonPalette.FRAME || rgb == DysonPalette.NODE ? TINT : DysonPalette.mix(0, rgb, 0.78);
    }

    private static double angle(double[] a, double[] b) {
        return Math.acos(Math.max(-1, Math.min(1, a[0] * b[0] + a[1] * b[1] + a[2] * b[2])));
    }

    private static double[] normalize(double[] v) {
        double length = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return length < 1e-12 ? new double[] {0, 1, 0} : new double[] {v[0] / length, v[1] / length, v[2] / length};
    }

    private static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return r << 16 | g << 8 | bl;
    }

    // ---- 画

    private void upload() {
        int stride = DefaultVertexFormat.POSITION_COLOR.getVertexSize();
        int total = 0;
        for (Batch batch : this.batches) {
            total += batch.vertices;
        }
        long size = (long) total * stride;
        if (this.buffer == null || this.buffer.size() < size) {
            if (this.buffer != null) {
                this.buffer.close();
            }
            this.buffer = RenderSystem.getDevice().createBuffer(() -> "Universe Dyson sky", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                    Math.max(size, 64 * 1024) * 2);
        }
        if (this.bytes == null) {
            this.bytes = new ByteBufferBuilder(256 * 1024);
        }
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        long offset = 0;
        for (int i = 0; i < this.batches.length; i++) {
            this.offsets[i] = offset;
            Batch batch = this.batches[i];
            if (batch.vertices == 0) {
                continue;
            }
            BufferBuilder builder = new BufferBuilder(this.bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (int v = 0; v < batch.vertices; v++) {
                builder.addVertex(batch.xyz[v * 3], batch.xyz[v * 3 + 1], batch.xyz[v * 3 + 2]).setColor(batch.colors[v]);
            }
            try (MeshData mesh = builder.buildOrThrow()) {
                ByteBuffer data = mesh.vertexBuffer();
                int length = data.remaining();
                encoder.writeToBuffer(this.buffer.slice(offset, length), data);
                offset += length;
            }
        }
        this.uploadedVersion = this.version;
    }

    private void draw(RenderPass pass, Frame current) {
        GpuBuffer vertices = this.buffer;
        if (vertices == null) {
            return;
        }
        int stride = DefaultVertexFormat.POSITION_COLOR.getVertexSize();
        Batch[] batches = current.batches();
        int total = 0;
        int maxVertices = 0;
        for (Batch batch : batches) {
            total += batch.vertices;
            maxVertices = Math.max(maxVertices, batch.vertices);
        }
        lastVertices = total;
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer indexBuffer = indices.getBuffer(maxVertices / 4 * 6);
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(RenderSystem.getModelViewStack()),
                new Vector4f(1.0F, 1.0F, 1.0F, 1.0F));
        pass.pushDebugGroup(() -> "Universe Dyson sky");
        RenderSystem.bindDefaultUniforms(pass);
        pass.setIndexBuffer(indexBuffer, indices.type());
        for (int i = 0; i < batches.length; i++) {
            Batch batch = batches[i];
            if (batch.vertices == 0) {
                continue;
            }
            pass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINES[i]));
            pass.setUniform("DynamicTransforms", transform);
            pass.setVertexBuffer(0, vertices.slice(this.offsets[i], (long) batch.vertices * stride));
            pass.drawIndexed(batch.vertices / 4 * 6, 1, 0, 0, 0);
        }
        pass.popDebugGroup();
    }
}
