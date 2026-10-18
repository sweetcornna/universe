package io.github.sweetcornna.universe.core.galaxy;

/**
 * 星图的环绕相机（玩法设计 §1.1）：绕一个焦点转动、缩放、平移，把星团坐标（光年）透视投影到屏幕上。
 *
 * <p>偏航 0° 时相机在焦点的 +z 方向看向 -z；俯仰为正时从上往下看。焦点平滑地移向目标，
 * 换焦点时画面不会一下跳过去。
 */
public final class StarMapCamera {
    public static final double MIN_DISTANCE = 4;
    public static final double MAX_DISTANCE = 180;
    public static final double MIN_PITCH = -85;
    public static final double MAX_PITCH = 85;
    /** 竖直方向的视角（度）。 */
    public static final double FOV = 60;
    /** 比这更近的点不画（在相机后面或贴着镜头）。 */
    private static final double NEAR = 0.05;

    private double focusX;
    private double focusY;
    private double focusZ;
    private double targetX;
    private double targetY;
    private double targetZ;
    private double yaw = 30;
    private double pitch = 35;
    private double distance = 34;

    /** 屏幕上的一个投影点。{@code depth} 是沿视线的距离，{@code scale} 是 1 光年在这里有多少屏幕单位。 */
    public record Projected(double x, double y, double depth, double scale) {
    }

    public double yaw() {
        return this.yaw;
    }

    public double pitch() {
        return this.pitch;
    }

    public double distance() {
        return this.distance;
    }

    public double focusX() {
        return this.focusX;
    }

    public double focusY() {
        return this.focusY;
    }

    public double focusZ() {
        return this.focusZ;
    }

    /** 拖动转动视角（度）。 */
    public void rotate(double deltaYaw, double deltaPitch) {
        this.yaw = ((this.yaw + deltaYaw) % 360 + 360) % 360;
        this.pitch = Math.clamp(this.pitch + deltaPitch, MIN_PITCH, MAX_PITCH);
    }

    /** 滚轮缩放：正数拉近。 */
    public void zoom(double steps) {
        this.distance = Math.clamp(this.distance * Math.pow(0.88, steps), MIN_DISTANCE, MAX_DISTANCE);
    }

    public void setDistance(double distance) {
        this.distance = Math.clamp(distance, MIN_DISTANCE, MAX_DISTANCE);
    }

    /** 平滑地把焦点移到这里。 */
    public void focus(double x, double y, double z) {
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
    }

    /** 立即把焦点放在这里（第一次打开时）。 */
    public void jumpTo(double x, double y, double z) {
        this.focus(x, y, z);
        this.focusX = x;
        this.focusY = y;
        this.focusZ = z;
    }

    /**
     * 在屏幕平面上平移焦点（拖动右键）：屏幕上移动的距离按焦点处的比例换成光年，沿水平面移动。
     *
     * @param dx 屏幕上向右移动了多少（屏幕单位）
     * @param dy 屏幕上向下移动了多少
     * @param screenHeight 屏幕高度，用来换算比例
     */
    public void pan(double dx, double dy, double screenHeight) {
        double unitsPerLy = screenHeight / 2 / Math.tan(Math.toRadians(FOV / 2)) / this.distance;
        double yawRad = Math.toRadians(this.yaw);
        // 水平面上屏幕的"右"和"上"
        double rightX = Math.cos(yawRad);
        double rightZ = -Math.sin(yawRad);
        double upX = -Math.sin(yawRad);
        double upZ = -Math.cos(yawRad);
        double mx = (-dx * rightX + dy * upX) / unitsPerLy;
        double mz = (-dx * rightZ + dy * upZ) / unitsPerLy;
        this.focus(this.targetX + mx, this.targetY, this.targetZ + mz);
        this.focusX += mx;
        this.focusZ += mz;
    }

    /** 每帧调用：焦点向目标靠拢。 */
    public void update(double seconds) {
        double t = 1 - Math.exp(-seconds * 8);
        this.focusX += (this.targetX - this.focusX) * t;
        this.focusY += (this.targetY - this.focusY) * t;
        this.focusZ += (this.targetZ - this.focusZ) * t;
    }

    /** 相机的位置。 */
    public double[] eye() {
        double yawRad = Math.toRadians(this.yaw);
        double pitchRad = Math.toRadians(this.pitch);
        double horizontal = Math.cos(pitchRad) * this.distance;
        return new double[] {
                this.focusX + Math.sin(yawRad) * horizontal,
                this.focusY + Math.sin(pitchRad) * this.distance,
                this.focusZ + Math.cos(yawRad) * horizontal};
    }

    /**
     * 把星团里的一点投影到屏幕上。
     *
     * @param centerX 画面中心
     * @param height  画面高度（决定视角对应多少屏幕单位）
     * @return 在相机后面时返回 null
     */
    public Projected project(double x, double y, double z, double centerX, double centerY, double height) {
        double yawRad = Math.toRadians(this.yaw);
        double pitchRad = Math.toRadians(this.pitch);
        double[] eye = this.eye();
        double dx = x - eye[0];
        double dy = y - eye[1];
        double dz = z - eye[2];
        // 相机的三个轴：前（指向焦点）、右、上
        double fx = -Math.sin(yawRad) * Math.cos(pitchRad);
        double fy = -Math.sin(pitchRad);
        double fz = -Math.cos(yawRad) * Math.cos(pitchRad);
        double rx = Math.cos(yawRad);
        double rz = -Math.sin(yawRad);
        // 上 = 右 × 前
        double upX = -rz * fy;
        double upY = rz * fx - rx * fz;
        double upZ = rx * fy;
        double depth = dx * fx + dy * fy + dz * fz;
        if (depth < NEAR) {
            return null;
        }
        double right = dx * rx + dz * rz;
        double up = dx * upX + dy * upY + dz * upZ;
        double focal = height / 2 / Math.tan(Math.toRadians(FOV / 2));
        double scale = focal / depth;
        return new Projected(centerX + right * scale, centerY - up * scale, depth, scale);
    }
}
