package io.github.sweetcornna.universe.core.fossil;

/**
 * 骨头模型里的一个方块（ADR-0034 §三），和实体模型的方块一个意思：先在自己的坐标系里摆好
 * （最小角 {@code x, y, z}，大小 {@code w, h, d}），再绕支点 {@code px, py, pz} 依次转 {@code rx}、{@code ry}、{@code rz}（弧度），
 * 和原版 {@code ModelPart} 的转法一样。单位是像素（1/16 格），y 朝上。
 *
 * <p>所有骨头共用一张 {@value #TEXTURE_SIZE}×{@value #TEXTURE_SIZE} 的贴图，按材质分成三块：上半是骨面，
 * 下面两条是眼眶、鼻孔这种深的窟窿，和牙、角、爪。{@code u, v} 是这个方块在贴图上的起点（方块展开的方式和实体模型一样）。
 */
public record BoneBox(float x, float y, float z, float w, float h, float d, float px, float py, float pz, float rx, float ry, float rz,
        Material material, int u, int v) {
    public static final int TEXTURE_SIZE = 64;

    /** 材质：贴图上的哪一条。 */
    public enum Material {
        /** 骨面。 */
        BONE(0, 32),
        /** 眼眶、鼻孔、喉咙这种深的窟窿。 */
        CAVITY(32, 16),
        /** 牙、角、爪、尾刺。 */
        TOOTH(48, 16);

        private final int top;
        private final int height;

        Material(int top, int height) {
            this.top = top;
            this.height = height;
        }

        public int top() {
            return this.top;
        }

        public int height() {
            return this.height;
        }
    }

    /** 展开以后占贴图多宽（和原版实体模型一样：两个侧面加正反两面）。 */
    public int uvWidth() {
        return (int) Math.ceil(2 * (this.w + this.d) - 1e-4);
    }

    /** 展开以后占贴图多高（顶面加侧面）。 */
    public int uvHeight() {
        return (int) Math.ceil(this.d + this.h - 1e-4);
    }

    /** 展开以后落在自己那一条材质里，不跑到别的材质上。 */
    public boolean fitsTexture() {
        return this.u >= 0 && this.u + this.uvWidth() <= TEXTURE_SIZE && this.v >= this.material.top
                && this.v + this.uvHeight() <= this.material.top + this.material.height;
    }

    /** 左右镜像（x 取反）：左边的四肢、对称的眼眶都由右边的镜像出来。 */
    public BoneBox mirrored() {
        return new BoneBox(-this.x - this.w, this.y, this.z, this.w, this.h, this.d, -this.px, this.py, this.pz, this.rx, -this.ry, -this.rz,
                this.material, this.u, this.v);
    }

    /** 八个角在部件坐标系里的位置。 */
    public float[][] corners() {
        float[][] corners = new float[8][];
        int i = 0;
        for (int a = 0; a <= 1; a++) {
            for (int b = 0; b <= 1; b++) {
                for (int c = 0; c <= 1; c++) {
                    float[] p = rotate(this.x + a * this.w, this.y + b * this.h, this.z + c * this.d, this.rx, this.ry, this.rz);
                    corners[i++] = new float[] {p[0] + this.px, p[1] + this.py, p[2] + this.pz};
                }
            }
        }
        return corners;
    }

    /** 先绕 x、再绕 y、再绕 z 转（右手系，和 JOML 的 {@code rotationZYX} 一样）。 */
    public static float[] rotate(float x, float y, float z, float rx, float ry, float rz) {
        double c = Math.cos(rx);
        double s = Math.sin(rx);
        double y1 = y * c - z * s;
        double z1 = y * s + z * c;
        c = Math.cos(ry);
        s = Math.sin(ry);
        double x2 = x * c + z1 * s;
        double z2 = -x * s + z1 * c;
        c = Math.cos(rz);
        s = Math.sin(rz);
        double x3 = x2 * c - y1 * s;
        double y3 = x2 * s + y1 * c;
        return new float[] {(float) x3, (float) y3, (float) z2};
    }
}
