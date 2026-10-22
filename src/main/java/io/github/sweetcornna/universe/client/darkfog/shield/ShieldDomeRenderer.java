package io.github.sweetcornna.universe.client.darkfog.shield;

import java.util.List;

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

    /** 一个要画的穹顶：球心相对摄像机的位置、水平半径、高度、颜色和透明度的倍率，挨打的闪光按发生器认。 */
    private record Shell(Vec3 rel, Vec3 center, double radius, double height, int[] rgb, float fade, float boost, @Nullable BlockPos generator,
            List<ShieldClient.Flash> flashes, double time) {
        /** 经度 {@code theta}、高度角 {@code phi} 那一点相对球心的位置。 */
        Vec3 point(double theta, double phi) {
            return new Vec3(Math.cos(phi) * Math.cos(theta) * this.radius, Math.sin(phi) * this.height, Math.cos(phi) * Math.sin(theta) * this.radius);
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
                        dome.generator(), flashes, time);
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
                        new int[] {210, 240, 255}, fade, 2.2F, null, List.of(), time);
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
        for (int j = 0; j < lat; j++) {
            double phi0 = BELOW + (Math.PI / 2 - BELOW) * j / lat;
            double phi1 = BELOW + (Math.PI / 2 - BELOW) * (j + 1) / lat;
            for (int i = 0; i < lon; i++) {
                double theta0 = Math.PI * 2 * i / lon;
                double theta1 = Math.PI * 2 * (i + 1) / lon;
                vertex(buffer, matrix, shell, theta0, phi0, Vec3.ZERO, 0);
                vertex(buffer, matrix, shell, theta1, phi0, Vec3.ZERO, 0);
                vertex(buffer, matrix, shell, theta1, phi1, Vec3.ZERO, 0);
                vertex(buffer, matrix, shell, theta0, phi1, Vec3.ZERO, 0);
            }
        }
    }

    /** 经纬线：贴着外壳的细带子，比外壳亮一些。经线分 {@code lat} 段，纬线分 {@code lon} 段。 */
    private static void grid(VertexConsumer buffer, Matrix4fc matrix, Shell shell, int lat, int lon) {
        double width = Math.max(0.2, shell.radius() * 0.004);
        for (int m = 0; m < MERIDIANS; m++) {
            double theta = Math.PI * 2 * m / MERIDIANS;
            // 经线横着往经度增加的方向撑开
            Vec3 side = new Vec3(-Math.sin(theta), 0, Math.cos(theta)).scale(width / 2);
            for (int j = 0; j < lat; j++) {
                double phi0 = BELOW + (Math.PI / 2 - BELOW) * j / lat;
                double phi1 = BELOW + (Math.PI / 2 - BELOW) * (j + 1) / lat;
                vertex(buffer, matrix, shell, theta, phi0, side.scale(-1), 1);
                vertex(buffer, matrix, shell, theta, phi0, side, 1);
                vertex(buffer, matrix, shell, theta, phi1, side, 1);
                vertex(buffer, matrix, shell, theta, phi1, side.scale(-1), 1);
            }
        }
        for (double phi : PARALLELS) {
            for (int i = 0; i < lon; i++) {
                double theta0 = Math.PI * 2 * i / lon;
                double theta1 = Math.PI * 2 * (i + 1) / lon;
                Vec3 up0 = up(shell, theta0, phi, width);
                Vec3 up1 = up(shell, theta1, phi, width);
                vertex(buffer, matrix, shell, theta0, phi, up0.scale(-1), 1);
                vertex(buffer, matrix, shell, theta1, phi, up1.scale(-1), 1);
                vertex(buffer, matrix, shell, theta1, phi, up1, 1);
                vertex(buffer, matrix, shell, theta0, phi, up0, 1);
            }
        }
    }

    /** 纬线往上撑开的方向：沿着外壳往顶上，长度是线宽的一半。 */
    private static Vec3 up(Shell shell, double theta, double phi, double width) {
        Vec3 d = new Vec3(-Math.sin(phi) * Math.cos(theta) * shell.radius(), Math.cos(phi) * shell.height(), -Math.sin(phi) * Math.sin(theta) * shell.radius());
        return d.normalize().scale(width / 2);
    }

    /** 外壳上经度 {@code theta}、高度角 {@code phi} 那一点再挪 {@code offset}；{@code line} 是 1 时画的是经纬线，亮一些。 */
    private static void vertex(VertexConsumer buffer, Matrix4fc matrix, Shell shell, double theta, double phi, Vec3 offset, float line) {
        Vec3 local = shell.point(theta, phi).add(offset);
        double x = shell.rel().x + local.x;
        double y = shell.rel().y + local.y;
        double z = shell.rel().z + local.z;
        // 椭球的法线：(x/r², y/h², z/r²)
        double nx = Math.cos(phi) * Math.cos(theta) / shell.radius();
        double ny = Math.sin(phi) / shell.height();
        double nz = Math.cos(phi) * Math.sin(theta) / shell.radius();
        double n = Math.sqrt(nx * nx + ny * ny + nz * nz);
        double length = Math.sqrt(x * x + y * y + z * z);
        double facing = length < 1e-6 || n < 1e-12 ? 1 : Math.abs((x * nx + y * ny + z * nz) / (length * n));
        double rim = 1 - facing;
        double time = shell.time();
        // 正对着也留一层淡淡的颜色，再叠一层慢慢流动的波纹，看得出罩着东西
        double ripple = 0.5 + 0.5 * Math.sin(theta * 9 + phi * 7 + time * 0.05) * Math.sin(phi * 11 - time * 0.03);
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
            double dx = shell.center().x + local.x - hit.at().x;
            double dy = shell.center().y + local.y - hit.at().y;
            double dz = shell.center().z + local.z - hit.at().z;
            double spread = Math.max(12, shell.radius() * 0.22);
            double w = Math.exp(-(dx * dx + dy * dy + dz * dz) / (spread * spread));
            flash += w * Math.min(1, 0.35 + hit.strength() / 30) * (1 - age) * (1 - age);
        }
        flash = Math.min(1, flash);
        alpha += 0.55 * flash;
        int[] rgb = shell.rgb();
        int r = (int) Mth.lerp(flash, rgb[0], 255);
        int g = (int) Mth.lerp(flash, rgb[1], 255);
        int b = (int) Mth.lerp(flash, rgb[2], 255);
        int a = (int) Mth.clamp(alpha * shell.fade() * 255, 0, 255);
        buffer.addVertex(matrix, (float) x, (float) y, (float) z).setColor(r, g, b, a);
    }
}
