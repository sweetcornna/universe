package io.github.sweetcornna.universe.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * 把世界里的点投影到 HUD 上（扫描标记、航点、眩光用）。按当前相机的位置、朝向和视野算，不考虑视角晃动。
 */
public final class ScreenProjection {
    private final Vec3 eye;
    private final double sinYaw;
    private final double cosYaw;
    private final double sinPitch;
    private final double cosPitch;
    private final double focal;
    private final int width;
    private final int height;

    private ScreenProjection(Vec3 eye, double yaw, double pitch, double fov, int width, int height) {
        this.eye = eye;
        this.sinYaw = Math.sin(yaw);
        this.cosYaw = Math.cos(yaw);
        this.sinPitch = Math.sin(pitch);
        this.cosPitch = Math.cos(pitch);
        this.focal = (height / 2.0) / Math.tan(fov / 2);
        this.width = width;
        this.height = height;
    }

    public static ScreenProjection of(Minecraft minecraft, int width, int height) {
        Camera camera = minecraft.gameRenderer.mainCamera();
        // 这一帧实际的视野：飞行、疾跑、跃迁都会改它，用设置里的视野会让标记和眩光偏离目标
        return new ScreenProjection(camera.position(), Math.toRadians(camera.yRot()), Math.toRadians(camera.xRot()),
                Math.toRadians(camera.getFov()), width, height);
    }

    public Vec3 eye() {
        return this.eye;
    }

    /** 相机坐标：x 向右、y 向上、z 向前。 */
    public double[] camera(Vec3 world) {
        Vec3 d = world.subtract(this.eye);
        // 绕 Y 轴转到"前方是 +Z"，再绕 X 轴按俯仰转
        double x1 = d.x * this.cosYaw + d.z * this.sinYaw;
        double z1 = -d.x * this.sinYaw + d.z * this.cosYaw;
        double y2 = d.y * this.cosPitch + z1 * this.sinPitch;
        double z2 = -d.y * this.sinPitch + z1 * this.cosPitch;
        return new double[] {-x1, y2, z2};
    }

    /** 屏幕坐标；点在身后（或几乎贴着眼睛）时为空。 */
    public int[] project(Vec3 world) {
        double[] c = this.camera(world);
        if (c[2] < 0.5) {
            return null;
        }
        return new int[] {(int) Math.round(this.width / 2.0 + c[0] / c[2] * this.focal),
                (int) Math.round(this.height / 2.0 - c[1] / c[2] * this.focal)};
    }

    /** 不取整的屏幕坐标，屏幕外的也算（眩光贴图要跟着恒星平滑移动）；点在身后时为空。 */
    public double[] screen(Vec3 world) {
        double[] c = this.camera(world);
        if (c[2] < 1e-3) {
            return null;
        }
        return new double[] {this.width / 2.0 + c[0] / c[2] * this.focal, this.height / 2.0 - c[1] / c[2] * this.focal};
    }

    /** 屏幕外（或身后）的点：在离屏幕边 {@code margin} 的框上，朝它那个方向的位置。 */
    public int[] edge(Vec3 world, int margin) {
        double[] c = this.camera(world);
        double x = c[0];
        double y = -c[1];
        if (c[2] < 0) {
            x = -x;
            y = -y;
        }
        double halfW = this.width / 2.0 - margin;
        double halfH = this.height / 2.0 - margin;
        double scale = Math.min(halfW / Math.max(1e-6, Math.abs(x)), halfH / Math.max(1e-6, Math.abs(y)));
        return new int[] {(int) Math.round(this.width / 2.0 + x * scale), (int) Math.round(this.height / 2.0 + y * scale)};
    }

    public boolean onScreen(int[] point, int slack) {
        return point != null && point[0] >= -slack && point[0] <= this.width + slack && point[1] >= -slack && point[1] <= this.height + slack;
    }
}
