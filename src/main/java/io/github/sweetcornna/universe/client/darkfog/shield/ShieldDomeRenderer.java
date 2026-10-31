package io.github.sweetcornna.universe.client.darkfog.shield;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.VertexConsumer;

import io.github.sweetcornna.universe.core.darkfog.ShieldMath;
import io.github.sweetcornna.universe.darkfog.shield.ShieldPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * 画行星护盾的穹顶（ADR-0028 §八）：半透明的壳，从地平线下一点画到顶，大的穹顶压扁（{@link ShieldMath#height}）。
 * 正对着看是一层淡淡的颜色，越往边上越亮（和肥皂泡一样，斜着看穿过的那层更厚），上面有经纬线和慢慢流动的波纹。
 * 能量在展开线以上是青色，挨打掉到展开线以下偏橙，快打光时偏红；挨打的地方闪白光，打碎的穹顶放大一点淡掉。
 *
 * <p>用不写深度、不剔除背面的半透明四边形：穹顶罩着一大片地方，写了深度的话后面画的水、粒子会被它挡掉。
 */
final class ShieldDomeRenderer {
    /** 离穹顶表面多远以内画。 */
    private static final double VIEW = 900;
    /** 从地平线下这么多度画起：地面高低不平，留一点不露缝。 */
    private static final double BELOW = Math.toRadians(-12);
    /** 经线几条、纬线画在哪几个高度角上。 */
    private static final int MERIDIANS = 24;
    private static final double[] PARALLELS = {Math.toRadians(18), Math.toRadians(38), Math.toRadians(60)};
    private static final int[] FULL = {80, 196, 255};
    private static final int[] HALF = {255, 172, 64};
    private static final int[] LOW = {255, 86, 52};

    private ShieldDomeRenderer() {
    }

    /**
     * 一组经度、高度角上的三角函数（ADR-0038）。穹顶的顶点都落在固定的几组经纬上，按组算一次记下，每帧每个顶点就不用再算；
     * 表里的数和逐个现算的完全一样。波纹的两项按和角公式拆开：跟经纬有关的一半在表里，跟时间有关的一半每帧算一次。
     */
    private static final class Angles {
        final double[] theta;
        final double[] cosTheta;
        final double[] sinTheta;
        final double[] phi;
        final double[] cosPhi;
        final double[] sinPhi;
        /** 波纹第一项 9θ + 7φ 的正弦、余弦，下标 i * phi.length + j。 */
        final double[] sinWave;
        final double[] cosWave;
        /** 波纹第二项 11φ 的正弦、余弦。 */
        final double[] sinBand;
        final double[] cosBand;

        Angles(double[] theta, double[] phi) {
            this.theta = theta;
            this.phi = phi;
            this.cosTheta = new double[theta.length];
            this.sinTheta = new double[theta.length];
            for (int i = 0; i < theta.length; i++) {
                this.cosTheta[i] = Math.cos(theta[i]);
                this.sinTheta[i] = Math.sin(theta[i]);
            }
            this.cosPhi = new double[phi.length];
            this.sinPhi = new double[phi.length];
            this.sinBand = new double[phi.length];
            this.cosBand = new double[phi.length];
            for (int j = 0; j < phi.length; j++) {
                this.cosPhi[j] = Math.cos(phi[j]);
                this.sinPhi[j] = Math.sin(phi[j]);
                this.sinBand[j] = Math.sin(phi[j] * 11);
                this.cosBand[j] = Math.cos(phi[j] * 11);
            }
            this.sinWave = new double[theta.length * phi.length];
            this.cosWave = new double[theta.length * phi.length];
            for (int i = 0; i < theta.length; i++) {
                for (int j = 0; j < phi.length; j++) {
                    double wave = theta[i] * 9 + phi[j] * 7;
                    this.sinWave[i * phi.length + j] = Math.sin(wave);
                    this.cosWave[i * phi.length + j] = Math.cos(wave);
                }
            }
        }

        /** 经线 {@code lon} 条（0 到 2π，首尾都有），纬线从地平线下一点到顶 {@code lat} 圈（首尾都有）。 */
        static Angles of(int lon, int lat) {
            return new Angles(thetas(lon, lon + 1), phis(lat));
        }

        static double[] thetas(int lon, int count) {
            double[] theta = new double[count];
            for (int i = 0; i < count; i++) {
                theta[i] = Math.PI * 2 * i / lon;
            }
            return theta;
        }

        static double[] phis(int lat) {
            double[] phi = new double[lat + 1];
            for (int j = 0; j <= lat; j++) {
                phi[j] = BELOW + (Math.PI / 2 - BELOW) * j / lat;
            }
            return phi;
        }
    }

    /** 外壳、经线、纬线用到的几组经纬，按（经线数、纬线圈数）记下。只在渲染线程上用。 */
    private static final Map<Long, Angles> SHELLS = new HashMap<>();
    private static final Map<Integer, Angles> MERIDIAN_GRIDS = new HashMap<>();
    private static final Map<Integer, Angles> PARALLEL_GRIDS = new HashMap<>();

    private static Angles shellAngles(int lon, int lat) {
        return SHELLS.computeIfAbsent((long) lon << 32 | lat, key -> Angles.of(lon, lat));
    }

    /** 一个要画的穹顶：球心相对摄像机的位置、水平半径、高度、颜色和透明度的倍率，挨打的闪光按发生器认；{@code wave} 是波纹里跟时间有关的那一半。 */
    private record Shell(Vec3 rel, Vec3 center, double radius, double height, int[] rgb, float fade, float boost, @Nullable BlockPos generator,
            List<ShieldClient.Flash> flashes, double time, Wave wave) {
    }

    /** 波纹随时间流动：sin(9θ + 7φ + 0.05t)·sin(11φ − 0.03t) 里 t 那一半的正弦、余弦，每帧算一次。 */
    private record Wave(double sinT, double cosT, double sinU, double cosU) {
        static Wave at(double time) {
            return new Wave(Math.sin(time * 0.05), Math.cos(time * 0.05), Math.sin(time * 0.03), Math.cos(time * 0.03));
        }
    }

    static void submit(SubmitCustomGeometryEvent event) {
        List<ShieldPayloads.Dome> domes = ShieldClient.domes();
        long now = ShieldClient.now();
        List<ShieldClient.Collapse> collapses = ShieldClient.collapses(now);
        if (domes.isEmpty() && collapses.isEmpty()) {
            return;
        }
        List<ShieldClient.Flash> flashes = ShieldClient.flashes(now);
        double time = now + event.getLevelRenderState().worldPartialTicks;
        Wave wave = Wave.at(time);
        Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderTypes.debugQuads(), (pose, buffer) -> {
            Matrix4fc matrix = pose.pose();
            for (ShieldPayloads.Dome dome : domes) {
                Vec3 center = ShieldClient.center(dome.generator());
                double distance = center.distanceTo(camera) - dome.radius();
                if (distance > VIEW) {
                    continue;
                }
                float fade = (float) Mth.clamp((VIEW - distance) / (VIEW * 0.4), 0, 1);
                boolean near = distance < 400;
                Shell shell = new Shell(center.subtract(camera), center, dome.radius(), ShieldMath.height(dome.radius()), color(dome.charge()), fade, 1,
                        dome.generator(), flashes, time, wave);
                shell(buffer, matrix, shell, near ? 48 : 24, near ? 14 : 8);
                if (near) {
                    grid(buffer, matrix, shell, 14, 48);
                }
            }
            for (ShieldClient.Collapse collapse : collapses) {
                double t = Mth.clamp((time - collapse.start()) / ShieldClient.COLLAPSE_TICKS, 0, 1);
                float fade = (float) ((1 - t) * (1 - t));
                double radius = collapse.radius() * (1 + 0.08 * t);
                Shell shell = new Shell(collapse.center().subtract(camera), collapse.center(), radius, ShieldMath.height(collapse.radius()) * (1 + 0.08 * t),
                        new int[] {210, 240, 255}, fade, 2.2F, null, List.of(), time, wave);
                shell(buffer, matrix, shell, 32, 10);
            }
        });
    }

    /** 能量占"罩满"那条线的比例 → 颜色：展开线（一半）以上青色，往下偏橙，快没了红色。 */
    private static int[] color(float charge) {
        double c = Mth.clamp(charge, 0, 1);
        if (c >= 0.5) {
            return FULL;
        }
        return c >= 0.25 ? mix(HALF, FULL, (c - 0.25) / 0.25) : mix(LOW, HALF, c / 0.25);
    }

    private static int[] mix(int[] a, int[] b, double t) {
        return new int[] {(int) Mth.lerp(t, a[0], b[0]), (int) Mth.lerp(t, a[1], b[1]), (int) Mth.lerp(t, a[2], b[2])};
    }

    /** 外壳：经线 {@code lon} 条，纬线从地平线下一点到顶 {@code lat} 圈。 */
    private static void shell(VertexConsumer buffer, Matrix4fc matrix, Shell shell, int lon, int lat) {
        if (shell.fade() <= 0.01F) {
            return;
        }
        Angles a = shellAngles(lon, lat);
        for (int j = 0; j < lat; j++) {
            for (int i = 0; i < lon; i++) {
                vertex(buffer, matrix, shell, a, i, j, 0, 0, 0, 0);
                vertex(buffer, matrix, shell, a, i + 1, j, 0, 0, 0, 0);
                vertex(buffer, matrix, shell, a, i + 1, j + 1, 0, 0, 0, 0);
                vertex(buffer, matrix, shell, a, i, j + 1, 0, 0, 0, 0);
            }
        }
    }

    /** 经纬线：贴着外壳的细带子，比外壳亮一些。经线分 {@code lat} 段，纬线分 {@code lon} 段。 */
    private static void grid(VertexConsumer buffer, Matrix4fc matrix, Shell shell, int lat, int lon) {
        double width = Math.max(0.2, shell.radius() * 0.004);
        double half = width / 2;
        Angles meridians = MERIDIAN_GRIDS.computeIfAbsent(lat, key -> new Angles(Angles.thetas(MERIDIANS, MERIDIANS), Angles.phis(lat)));
        for (int m = 0; m < MERIDIANS; m++) {
            // 经线横着往经度增加的方向撑开
            double sx = -meridians.sinTheta[m] * half;
            double sz = meridians.cosTheta[m] * half;
            for (int j = 0; j < lat; j++) {
                vertex(buffer, matrix, shell, meridians, m, j, -sx, 0, -sz, 1);
                vertex(buffer, matrix, shell, meridians, m, j, sx, 0, sz, 1);
                vertex(buffer, matrix, shell, meridians, m, j + 1, sx, 0, sz, 1);
                vertex(buffer, matrix, shell, meridians, m, j + 1, -sx, 0, -sz, 1);
            }
        }
        Angles parallels = PARALLEL_GRIDS.computeIfAbsent(lon, key -> new Angles(Angles.thetas(lon, lon + 1), PARALLELS));
        for (int p = 0; p < PARALLELS.length; p++) {
            for (int i = 0; i < lon; i++) {
                double[] up0 = up(shell, parallels, i, p, half);
                double[] up1 = up(shell, parallels, i + 1, p, half);
                vertex(buffer, matrix, shell, parallels, i, p, -up0[0], -up0[1], -up0[2], 1);
                vertex(buffer, matrix, shell, parallels, i + 1, p, -up1[0], -up1[1], -up1[2], 1);
                vertex(buffer, matrix, shell, parallels, i + 1, p, up1[0], up1[1], up1[2], 1);
                vertex(buffer, matrix, shell, parallels, i, p, up0[0], up0[1], up0[2], 1);
            }
        }
    }

    /** 纬线往上撑开的方向：沿着外壳往顶上，长度是线宽的一半（{@code half}）。 */
    private static double[] up(Shell shell, Angles a, int i, int j, double half) {
        double dx = -a.sinPhi[j] * a.cosTheta[i] * shell.radius();
        double dy = a.cosPhi[j] * shell.height();
        double dz = -a.sinPhi[j] * a.sinTheta[i] * shell.radius();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        // 和 Vec3.normalize 一样，太短的当成零
        if (length < 1.0E-5F) {
            return new double[3];
        }
        return new double[] {dx / length * half, dy / length * half, dz / length * half};
    }

    /**
     * 外壳上第 {@code i} 个经度、第 {@code j} 个高度角那一点再挪 ({@code ox}, {@code oy}, {@code oz})；{@code line} 是 1 时画的是经纬线，亮一些。
     */
    private static void vertex(VertexConsumer buffer, Matrix4fc matrix, Shell shell, Angles a, int i, int j, double ox, double oy, double oz, float line) {
        double cosTheta = a.cosTheta[i];
        double sinTheta = a.sinTheta[i];
        double cosPhi = a.cosPhi[j];
        double sinPhi = a.sinPhi[j];
        double localX = cosPhi * cosTheta * shell.radius() + ox;
        double localY = sinPhi * shell.height() + oy;
        double localZ = cosPhi * sinTheta * shell.radius() + oz;
        double x = shell.rel().x + localX;
        double y = shell.rel().y + localY;
        double z = shell.rel().z + localZ;
        // 椭球的法线：(x/r², y/h², z/r²)
        double nx = cosPhi * cosTheta / shell.radius();
        double ny = sinPhi / shell.height();
        double nz = cosPhi * sinTheta / shell.radius();
        double n = Math.sqrt(nx * nx + ny * ny + nz * nz);
        double length = Math.sqrt(x * x + y * y + z * z);
        double facing = length < 1e-6 || n < 1e-12 ? 1 : Math.abs((x * nx + y * ny + z * nz) / (length * n));
        double rim = 1 - facing;
        double time = shell.time();
        // 正对着也留一层淡淡的颜色，再叠一层慢慢流动的波纹，看得出罩着东西
        Wave wave = shell.wave();
        int w = i * a.phi.length + j;
        double first = a.sinWave[w] * wave.cosT() + a.cosWave[w] * wave.sinT();
        double second = a.sinBand[j] * wave.cosU() - a.cosBand[j] * wave.sinU();
        double ripple = 0.5 + 0.5 * first * second;
        double alpha = (0.08 + 0.05 * ripple + 0.32 * rim * rim * rim + 0.16 * line) * shell.boost();
        double flash = 0;
        for (ShieldClient.Flash hit : shell.flashes()) {
            if (shell.generator() == null || !hit.generator().equals(shell.generator())) {
                continue;
            }
            double age = (time - hit.start()) / ShieldClient.FLASH_TICKS;
            if (age < 0 || age > 1) {
                continue;
            }
            double dx = shell.center().x + localX - hit.at().x;
            double dy = shell.center().y + localY - hit.at().y;
            double dz = shell.center().z + localZ - hit.at().z;
            double spread = Math.max(12, shell.radius() * 0.22);
            double weight = Math.exp(-(dx * dx + dy * dy + dz * dz) / (spread * spread));
            flash += weight * Math.min(1, 0.35 + hit.strength() / 30) * (1 - age) * (1 - age);
        }
        flash = Math.min(1, flash);
        alpha += 0.55 * flash;
        int[] rgb = shell.rgb();
        int r = (int) Mth.lerp(flash, rgb[0], 255);
        int g = (int) Mth.lerp(flash, rgb[1], 255);
        int b = (int) Mth.lerp(flash, rgb[2], 255);
        int alphaByte = (int) Mth.clamp(alpha * shell.fade() * 255, 0, 255);
        buffer.addVertex(matrix, (float) x, (float) y, (float) z).setColor(r, g, b, alphaByte);
    }
}
