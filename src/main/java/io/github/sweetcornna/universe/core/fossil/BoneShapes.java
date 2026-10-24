package io.github.sweetcornna.universe.core.fossil;

import static io.github.sweetcornna.universe.core.fossil.BoneBox.Material.BONE;
import static io.github.sweetcornna.universe.core.fossil.BoneBox.Material.CAVITY;
import static io.github.sweetcornna.universe.core.fossil.BoneBox.Material.TOOTH;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 40 种骨头的立体模型（ADR-0034 §三）：每个类群每个部位一组方块，像实体模型那样用代码搭。单位是像素，y 朝上，
 * 头朝 -z、尾巴朝 +z；类群的大小（{@link BoneFamily#size()}）在拼骨架时再乘上去。
 *
 * <p>各部位自己的原点就是它接到别的部件上的那一点：
 * <ul>
 *   <li>头骨：后脑勺接脖子的地方，往 -z 长。</li>
 *   <li>颈椎、尾骨：根部，分别往 -z、+z 长；末端（{@link Chain#end()}）接头骨。</li>
 *   <li>胸廓：脊柱的中点。{@link Body} 里记着脖子、尾巴、前后肢接在哪（四肢记右边的，左边镜像）。</li>
 *   <li>四肢：右边的肩、胯，往下长到脚。</li>
 * </ul>
 * 不同类群的部位拼在一起时位置都从这些接点算，大小各按各的类群，所以总是接得上。
 */
public final class BoneShapes {
    private static final Map<BoneFamily, List<BoneBox>> SKULLS = new EnumMap<>(BoneFamily.class);
    private static final Map<BoneFamily, Chain> NECKS = new EnumMap<>(BoneFamily.class);
    private static final Map<BoneFamily, Body> BODIES = new EnumMap<>(BoneFamily.class);
    private static final Map<BoneFamily, Limbs> LIMBS = new EnumMap<>(BoneFamily.class);
    private static final Map<BoneFamily, Chain> TAILS = new EnumMap<>(BoneFamily.class);

    static {
        for (BoneFamily family : BoneFamily.values()) {
            SKULLS.put(family, buildSkull(family));
            NECKS.put(family, buildNeck(family));
            BODIES.put(family, buildBody(family));
            LIMBS.put(family, buildLimbs(family));
            TAILS.put(family, buildTail(family));
        }
    }

    private BoneShapes() {
    }

    /** 一点（像素）。 */
    public record V(float x, float y, float z) {
        public static final V ZERO = new V(0, 0, 0);

        V add(V o) {
            return new V(this.x + o.x, this.y + o.y, this.z + o.z);
        }

        V sub(V o) {
            return new V(this.x - o.x, this.y - o.y, this.z - o.z);
        }

        public V scale(float k) {
            return new V(this.x * k, this.y * k, this.z * k);
        }

        float length() {
            return (float) Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
        }

        V lerp(V o, float t) {
            return new V(this.x + (o.x - this.x) * t, this.y + (o.y - this.y) * t, this.z + (o.z - this.z) * t);
        }

        public V mirrored() {
            return new V(-this.x, this.y, this.z);
        }
    }

    /** 一串骨头（颈椎、尾骨）：方块、末端在哪，末端接上去的头骨抬多少（弧度，正的是鼻子朝上）。 */
    public record Chain(List<BoneBox> boxes, V end, float endPitch) {
    }

    /** 胸廓：方块和各接点。四肢的接点是右边的。 */
    public record Body(List<BoneBox> boxes, V neck, V tail, V frontLimb, V rearLimb) {
    }

    /** 右边的前肢、后肢（原点在肩、胯）。 */
    public record Limbs(List<BoneBox> front, List<BoneBox> rear) {
    }

    public static List<BoneBox> skull(BoneFamily family) {
        return SKULLS.get(family);
    }

    public static Chain neck(BoneFamily family) {
        return NECKS.get(family);
    }

    public static Body ribcage(BoneFamily family) {
        return BODIES.get(family);
    }

    public static Limbs limbs(BoneFamily family) {
        return LIMBS.get(family);
    }

    public static Chain tail(BoneFamily family) {
        return TAILS.get(family);
    }

    /** 模型里的一块（四肢分成四条）。 */
    public enum Piece {
        SKULL(BonePart.SKULL),
        NECK(BonePart.NECK),
        RIBCAGE(BonePart.RIBCAGE),
        FRONT_RIGHT(BonePart.LIMBS),
        FRONT_LEFT(BonePart.LIMBS),
        REAR_RIGHT(BonePart.LIMBS),
        REAR_LEFT(BonePart.LIMBS),
        TAIL(BonePart.TAIL);

        private final BonePart part;

        Piece(BonePart part) {
            this.part = part;
        }

        public BonePart part() {
            return this.part;
        }

        public boolean left() {
            return this == FRONT_LEFT || this == REAR_LEFT;
        }
    }

    /** 某个类群某一块的方块（左边的四肢是右边的镜像）。 */
    public static List<BoneBox> boxes(BoneFamily family, Piece piece) {
        return switch (piece) {
            case SKULL -> skull(family);
            case NECK -> neck(family).boxes();
            case RIBCAGE -> ribcage(family).boxes();
            case FRONT_RIGHT -> limbs(family).front();
            case FRONT_LEFT -> mirror(limbs(family).front());
            case REAR_RIGHT -> limbs(family).rear();
            case REAR_LEFT -> mirror(limbs(family).rear());
            case TAIL -> tail(family).boxes();
        };
    }

    private static List<BoneBox> mirror(List<BoneBox> boxes) {
        return boxes.stream().map(BoneBox::mirrored).toList();
    }

    // ---- 搭模型的工具

    /** 往一组方块里加东西。每加一块按序号在自己的材质那一条里错开一点贴图，免得每块的纹理一模一样。 */
    static final class Builder {
        private final List<BoneBox> boxes = new ArrayList<>();
        private int index;

        /** 中心在 (cx, cy, cz)、不转的方块。 */
        Builder box(BoneBox.Material m, float cx, float cy, float cz, float w, float h, float d) {
            return this.box(m, cx, cy, cz, w, h, d, 0, 0, 0);
        }

        /** 中心在 (cx, cy, cz)、绕中心转过的方块。 */
        Builder box(BoneBox.Material m, float cx, float cy, float cz, float w, float h, float d, float rx, float ry, float rz) {
            return this.add(m, -w / 2, -h / 2, -d / 2, w, h, d, cx, cy, cz, rx, ry, rz);
        }

        /**
         * 绕铰点 (hx, hy, hz) 转 {@code rx} 的一组方块里的一块：{@code (cx, cy, cz)} 是这块在铰点坐标系里的中心。
         * 下颌（绕关节张开）用这个。
         */
        Builder hinged(BoneBox.Material m, float hx, float hy, float hz, float rx, float cx, float cy, float cz, float w, float h, float d) {
            return this.add(m, cx - w / 2, cy - h / 2, cz - d / 2, w, h, d, hx, hy, hz, rx, 0, 0);
        }

        /** 从 a 到 b 的一根骨头，截面 w×h。 */
        Builder rod(BoneBox.Material m, V a, V b, float w, float h) {
            V dir = b.sub(a);
            float length = dir.length();
            if (length < 0.3F) {
                return this;
            }
            float rx = (float) -Math.asin(Math.max(-1, Math.min(1, dir.y() / length)));
            float ry = (float) Math.atan2(dir.x(), dir.z());
            V mid = a.lerp(b, 0.5F);
            return this.box(m, mid.x(), mid.y(), mid.z(), w, h, length, rx, ry, 0);
        }

        Builder rod(BoneBox.Material m, float x1, float y1, float z1, float x2, float y2, float z2, float w, float h) {
            return this.rod(m, new V(x1, y1, z1), new V(x2, y2, z2), w, h);
        }

        /** 套在 z 轴上的一圈（正对着 ±z 的环）：{@code sides} 块拼成，半径到每块的中线。 */
        Builder ringZ(BoneBox.Material m, float cx, float cy, float cz, float radius, float thickness, float depth, int sides) {
            float edge = (float) (2 * radius * Math.tan(Math.PI / sides)) + thickness * 0.4F;
            for (int i = 0; i < sides; i++) {
                double a = Math.PI * 2 * i / sides;
                this.box(m, cx + (float) (Math.cos(a) * radius), cy + (float) (Math.sin(a) * radius), cz, edge, thickness, depth, 0, 0,
                        (float) (a + Math.PI / 2));
            }
            return this;
        }

        /** 套在 y 轴上的一圈（平放的环）。 */
        Builder ringY(BoneBox.Material m, float cx, float cy, float cz, float radius, float thickness, float height, int sides) {
            float edge = (float) (2 * radius * Math.tan(Math.PI / sides)) + thickness * 0.4F;
            for (int i = 0; i < sides; i++) {
                double a = Math.PI * 2 * i / sides;
                this.box(m, cx + (float) (Math.cos(a) * radius), cy, cz + (float) (Math.sin(a) * radius), edge, height, thickness, 0,
                        (float) (-a - Math.PI / 2), 0);
            }
            return this;
        }

        /** 跑一遍 {@code side}（搭右边的），再把这一遍加的方块镜像一份到左边。 */
        Builder mirror(Runnable side) {
            int start = this.boxes.size();
            side.run();
            int end = this.boxes.size();
            for (int i = start; i < end; i++) {
                this.boxes.add(this.boxes.get(i).mirrored());
            }
            return this;
        }

        private Builder add(BoneBox.Material m, float x, float y, float z, float w, float h, float d, float px, float py, float pz,
                float rx, float ry, float rz) {
            int uvWidth = (int) Math.ceil(2 * (w + d) - 1e-4);
            int uvHeight = (int) Math.ceil(d + h - 1e-4);
            int maxU = BoneBox.TEXTURE_SIZE - uvWidth;
            int maxV = m.height() - uvHeight;
            int u = maxU <= 0 ? 0 : this.index * 13 % (maxU + 1);
            int v = m.top() + (maxV <= 0 ? 0 : this.index * 7 % (maxV + 1));
            this.index++;
            this.boxes.add(new BoneBox(x, y, z, w, h, d, px, py, pz, rx, ry, rz, m, u, v));
            return this;
        }

        List<BoneBox> build() {
            return List.copyOf(this.boxes);
        }
    }

    /** 和方向 {@code dir} 垂直、尽量朝上的单位向量（椎骨的棘突朝这边长）。 */
    static V up(V dir) {
        float length = dir.length();
        V d = dir.scale(1 / length);
        V up = new V(0, 1, 0).sub(d.scale(d.y()));
        float l = up.length();
        return l < 1e-3F ? new V(0, 0, -1) : up.scale(1 / l);
    }

    /**
     * 一串椎骨：每两点之间一节，节和节之间留一点缝；粗细从 {@code t0} 渐变到 {@code t1}，背上的棘突高 {@code process} 倍粗细
     * （为 0 不画）。{@code chevron} 不为 0 时肚子下面也挂一根（尾椎的脉弧）。
     */
    static void vertebrae(Builder b, List<V> points, float t0, float t1, float process, float chevron) {
        int n = points.size() - 1;
        for (int i = 0; i < n; i++) {
            V a = points.get(i);
            V c = points.get(i + 1);
            float t = n == 1 ? t0 : t0 + (t1 - t0) * i / (n - 1);
            V dir = c.sub(a);
            V mid = a.lerp(c, 0.5F);
            b.rod(BONE, mid.sub(dir.scale(0.42F)), mid.add(dir.scale(0.42F)), t, t);
            V up = up(dir);
            // 椎体两侧的横突
            b.box(BONE, mid.x(), mid.y(), mid.z(), t * 1.7F, Math.max(0.3F, t * 0.32F), Math.max(0.4F, dir.length() * 0.3F));
            if (process > 0) {
                V base = mid.add(up.scale(t * 0.45F));
                b.rod(BONE, base, base.add(up.scale(t * process)), Math.max(0.3F, t * 0.3F), Math.max(0.4F, dir.length() * 0.45F));
            }
            if (chevron > 0) {
                V base = mid.sub(up.scale(t * 0.45F));
                b.rod(BONE, base, base.sub(up.scale(t * chevron)).add(dir.scale(0.25F)), Math.max(0.3F, t * 0.25F), Math.max(0.35F, dir.length() * 0.3F));
            }
        }
    }

    /** 从原点出发、每节长 {@code step}、按 {@code pitches}（弧度，正的朝上）一节节拐的一串点；{@code forward} 为 -1 往头那边、1 往尾巴那边。 */
    static List<V> path(float step, int forward, float... pitches) {
        List<V> points = new ArrayList<>(pitches.length + 1);
        V at = V.ZERO;
        points.add(at);
        for (float pitch : pitches) {
            at = at.add(new V(0, (float) Math.sin(pitch) * step, forward * (float) Math.cos(pitch) * step));
            points.add(at);
        }
        return points;
    }

    private static float pitchOf(V a, V b, int forward) {
        V d = b.sub(a);
        return (float) Math.atan2(d.y(), forward * d.z());
    }

    // ---- 头骨（后脑勺在原点，往 -z）

    private static List<BoneBox> buildSkull(BoneFamily family) {
        Builder b = new Builder();
        switch (family) {
            case TITAN -> {
                // 角龙那样的大头：颅骨、脸、带钩的喙，鼻角和两只长长的额角，脑后一面骨盾
                b.box(BONE, 0, 1, -3.5F, 7, 6, 7);
                b.box(BONE, 0, -0.5F, -9, 5, 4.5F, 4.5F);
                b.rod(BONE, 0, 0, -11, 0, -2, -13.5F, 2.5F, 2);
                b.rod(TOOTH, 0, 1.6F, -9.5F, 0, 5, -11, 1.4F, 1.4F);
                b.box(BONE, 0, 5.5F, 1.2F, 12, 7, 1.2F, 0.6F, 0, 0);
                for (int i = -2; i <= 2; i++) {
                    b.box(TOOTH, i * 2.5F, 8.4F, 3.1F, 1.2F, 1.2F, 1.2F);
                }
                b.mirror(() -> {
                    b.rod(TOOTH, 2, 3.5F, -5, 3, 8, -9.5F, 1.4F, 1.4F);
                    b.box(CAVITY, 3.35F, 2.2F, -5.5F, 0.8F, 2, 2.2F);
                    b.box(BONE, 3.2F, 3.6F, -5.5F, 1.4F, 0.9F, 3);
                    b.box(BONE, 3.4F, 0.4F, -6, 1.2F, 1, 4);
                    b.box(CAVITY, 2.35F, 0, -10, 0.6F, 1.5F, 1.5F);
                    for (int i = 0; i < 4; i++) {
                        b.box(TOOTH, 2.0F, -2.6F, -7.2F - i, 0.6F, 0.8F, 0.7F);
                    }
                });
                b.hinged(BONE, 0, -2.2F, -2, -0.08F, 0, -0.9F, -4.5F, 5, 1.5F, 9);
            }
            case PREDATOR -> {
                // 深长的头骨：颅骨、长吻，两侧的眶前孔和眼眶，眼睛上一道眉骨，上下两排牙，下颌微微张开
                b.box(BONE, 0, 1, -3, 5, 6.5F, 6);
                b.box(BONE, 0, 0, -9.5F, 4, 4.5F, 7, 0.05F, 0, 0);
                b.box(BONE, 0, -0.5F, -13.5F, 3, 3, 2.5F);
                b.mirror(() -> {
                    b.box(CAVITY, 1.85F, 0.8F, -8.5F, 0.5F, 2.2F, 3.4F);
                    b.box(CAVITY, 2.35F, 2.2F, -4.5F, 0.5F, 2, 1.8F);
                    b.box(BONE, 2.2F, 3.8F, -4.5F, 1.2F, 0.8F, 2.6F);
                    b.box(BONE, 1.5F, 4.6F, -6.5F, 1, 1, 1.5F);
                    for (int i = 0; i < 7; i++) {
                        b.box(TOOTH, 1.5F, -2.7F, -7 - i * 1.0F, 0.5F, 1.4F, 0.6F);
                    }
                    for (int i = 0; i < 6; i++) {
                        b.hinged(TOOTH, 0, -2.4F, -1, -0.25F, 1.3F, 0.9F, -4.5F - i * 1.1F, 0.5F, 1.2F, 0.6F);
                    }
                });
                b.hinged(BONE, 0, -2.4F, -1, -0.25F, 0, -0.3F, -6.5F, 3.6F, 1.8F, 12);
            }
            case LONGNECK -> {
                // 小脑袋：圆鼻头，头顶隆起的鼻拱上一个鼻孔，前面一排钉子似的牙
                b.box(BONE, 0, 1, -2, 3.6F, 3.6F, 4);
                b.box(BONE, 0, 0.2F, -5.5F, 3.2F, 2.4F, 3.4F);
                b.box(BONE, 0, 3.3F, -3.5F, 2, 1.2F, 2.4F);
                b.box(CAVITY, 0, 3.95F, -3.5F, 1.2F, 0.3F, 1.2F);
                b.mirror(() -> {
                    b.box(CAVITY, 1.65F, 1.6F, -2.5F, 0.5F, 1.2F, 1.2F);
                    for (int i = 0; i < 3; i++) {
                        b.box(TOOTH, 0.35F + i * 0.5F, -1.3F, -6.9F, 0.4F, 1.0F, 0.4F);
                    }
                });
                b.hinged(BONE, 0, -1.2F, -0.5F, -0.1F, 0, -0.1F, -3, 3, 1, 6);
            }
            case FLYER -> {
                // 翼兽：小颅骨，头后一片高高的头冠，又长又尖的喙，一个大眼眶
                b.box(BONE, 0, 0.8F, -1.5F, 2.6F, 2.8F, 3);
                b.rod(BONE, 0, 2, -1, 0, 5.5F, 3.5F, 0.5F, 2.2F);
                b.rod(BONE, 0, 0.6F, -3, 0, -0.4F, -11, 1.6F, 1.4F);
                b.rod(BONE, 0, -0.6F, -2.5F, 0, -1.4F, -10.5F, 1.2F, 0.8F);
                b.mirror(() -> {
                    b.box(CAVITY, 1.1F, 1.3F, -1.8F, 0.5F, 1.4F, 1.4F);
                    b.box(CAVITY, 0.65F, 0.3F, -4.5F, 0.4F, 0.8F, 2.5F);
                });
            }
            case WORM -> {
                // 巨蠕的口：一圈骨环，朝里两圈牙，后面是黑洞洞的喉咙，口边四根感觉须的骨刺
                b.ringZ(BONE, 0, 0, -1, 3.6F, 1.2F, 2.4F, 8);
                b.ringZ(BONE, 0, 0, 0.8F, 3.0F, 1, 1.4F, 8);
                b.box(CAVITY, 0, 0, -0.2F, 4.4F, 4.4F, 0.6F);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * 2 * i / 8;
                    float c = (float) Math.cos(a);
                    float s = (float) Math.sin(a);
                    b.rod(TOOTH, 3 * c, 3 * s, -2.0F, 1.4F * c, 1.4F * s, -1.5F, 0.6F, 0.6F);
                    double a2 = a + Math.PI / 8;
                    float c2 = (float) Math.cos(a2);
                    float s2 = (float) Math.sin(a2);
                    b.rod(TOOTH, 2.6F * c2, 2.6F * s2, -0.8F, 1.0F * c2, 1.0F * s2, -0.4F, 0.5F, 0.5F);
                }
                b.mirror(() -> {
                    b.rod(BONE, 3.4F, 2.2F, -2.2F, 5.0F, 3.4F, -3.4F, 0.8F, 0.8F);
                    b.rod(BONE, 3.4F, -2.2F, -2.2F, 5.0F, -3.4F, -3.4F, 0.8F, 0.8F);
                });
            }
            case ARTHROPOD -> {
                // 甲壳兽的头：一块头甲、前面的唇板，两侧复眼的窝，一对往里弯的大颚，头上两根触角的基节
                b.box(BONE, 0, 0.5F, -2.5F, 6, 2.6F, 5);
                b.box(BONE, 0, 0, -5.6F, 4, 1.6F, 1.4F);
                b.box(BONE, 0, 2.0F, -2.5F, 1.2F, 0.6F, 4.4F);
                b.mirror(() -> {
                    b.box(CAVITY, 3.0F, 1.0F, -3.2F, 0.6F, 1.4F, 1.8F);
                    b.box(BONE, 3.0F, 1.95F, -3.2F, 0.9F, 0.5F, 2.2F);
                    b.rod(TOOTH, 1.6F, -0.6F, -5.6F, 0.9F, -0.8F, -8.6F, 0.9F, 0.9F);
                    b.rod(TOOTH, 0.9F, -0.8F, -8.6F, 0.25F, -0.8F, -7.7F, 0.7F, 0.7F);
                    b.rod(BONE, 1.2F, 1.4F, -5, 2.4F, 3.8F, -8, 0.5F, 0.5F);
                });
            }
            case MARINE -> {
                // 鱼龙那样：颅骨上一个很大的眼眶（一圈巩膜骨），细长的吻和下颌，一排细牙
                b.box(BONE, 0, 0.6F, -2.5F, 4, 4, 5);
                b.rod(BONE, 0, 0, -5, 0, -0.3F, -14, 1.8F, 1.6F);
                b.rod(BONE, 0, -1.3F, -1, 0, -1.4F, -13.5F, 1.6F, 0.9F);
                b.mirror(() -> {
                    b.box(CAVITY, 2.05F, 1.0F, -3, 0.5F, 2.6F, 2.6F);
                    b.box(BONE, 2.1F, 2.55F, -3, 0.7F, 0.6F, 3);
                    b.box(BONE, 2.1F, -0.55F, -3, 0.7F, 0.6F, 3);
                    b.box(BONE, 2.1F, 1.0F, -4.55F, 0.7F, 2.4F, 0.6F);
                    b.box(BONE, 2.1F, 1.0F, -1.45F, 0.7F, 2.4F, 0.6F);
                    b.box(CAVITY, 0.85F, 0.3F, -5.6F, 0.3F, 0.6F, 1.2F);
                    for (int i = 0; i < 8; i++) {
                        b.box(TOOTH, 0.75F, -0.95F, -5.6F - i, 0.35F, 0.6F, 0.35F);
                    }
                });
            }
            case EXOTIC -> {
                // 奇异种：高高的颅骨，脸上一排三个眼眶，两只往后弯的角，下颌分成左右两半，各带一颗獠牙
                b.box(BONE, 0, 2, -2.5F, 5, 7, 5);
                b.box(BONE, 0, 0.5F, -5.5F, 4, 4, 2);
                b.box(CAVITY, 0, 3.4F, -5.1F, 1.4F, 1.4F, 0.4F);
                b.rod(TOOTH, 0, 5.5F, -3, 0, 7.5F, -2, 0.7F, 0.7F);
                b.rod(TOOTH, 0, 5.5F, -1, 0, 7, 0.5F, 0.6F, 0.6F);
                b.mirror(() -> {
                    b.box(CAVITY, 1.7F, 2.2F, -5.1F, 1.0F, 1.2F, 0.4F);
                    b.rod(TOOTH, 2, 5, -2, 3.5F, 8, 0, 1.2F, 1.2F);
                    b.rod(TOOTH, 3.5F, 8, 0, 3.8F, 9, 2.5F, 0.9F, 0.9F);
                    b.rod(BONE, 1.2F, -1.6F, -1, 1.0F, -2.8F, -6.5F, 1.1F, 1.1F);
                    b.rod(TOOTH, 1.0F, -2.8F, -6.5F, 1.2F, -1.2F, -7.5F, 0.6F, 0.6F);
                });
            }
        }
        return b.build();
    }

    // ---- 颈椎（根部在原点，往 -z）

    private static Chain buildNeck(BoneFamily family) {
        Builder b = new Builder();
        List<V> points;
        switch (family) {
            case TITAN -> {
                points = path(2.0F, -1, 0.25F, 0.3F, 0.3F);
                vertebrae(b, points, 3.0F, 2.6F, 0.6F, 0);
            }
            case PREDATOR -> {
                // S 形：先往上弯，再放平
                points = path(1.6F, -1, 0.9F, 1.0F, 0.8F, 0.4F, 0.15F);
                vertebrae(b, points, 2.0F, 1.6F, 0.4F, 0);
            }
            case LONGNECK -> {
                points = path(2.2F, -1, 0.55F, 0.7F, 0.8F, 0.85F, 0.85F, 0.8F, 0.7F, 0.55F, 0.35F);
                vertebrae(b, points, 2.6F, 1.4F, 0.5F, 0);
            }
            case FLYER -> {
                points = path(1.5F, -1, 0.8F, 0.9F, 0.7F, 0.4F);
                vertebrae(b, points, 1.2F, 0.9F, 0.3F, 0);
            }
            case WORM -> {
                // 巨蠕的"脖子"是三节环，底下一条韧带连着
                for (int i = 0; i < 3; i++) {
                    b.ringZ(BONE, 0, 0, -1.2F - i * 1.8F, 3.0F + i * 0.2F, 1.0F, 1.2F, 8);
                }
                b.rod(BONE, 0, -3.0F, 0, 0, -3.3F, -5.6F, 0.8F, 0.8F);
                b.rod(BONE, 0, 3.0F, 0, 0, 3.3F, -5.6F, 0.6F, 0.6F);
                points = List.of(V.ZERO, new V(0, 0, -6.2F));
            }
            case ARTHROPOD -> {
                // 两节宽宽的甲片，下面一块腹板
                b.box(BONE, 0, 0.4F, -1, 4.4F, 1.6F, 2);
                b.box(BONE, 0, 0.6F, -2.8F, 4.8F, 1.6F, 2);
                b.box(BONE, 0, -0.8F, -1.9F, 3, 0.6F, 3.4F);
                points = List.of(V.ZERO, new V(0, 0.5F, -3.8F));
            }
            case MARINE -> {
                points = path(1.7F, -1, 0.25F, 0.25F, 0.22F, 0.2F, 0.18F, 0.15F, 0.12F);
                vertebrae(b, points, 1.9F, 1.4F, 0.3F, 0);
            }
            case EXOTIC -> {
                points = path(1.8F, -1, 0.5F, 0.45F, 0.35F, 0.25F);
                vertebrae(b, points, 2.0F, 1.6F, 0, 0);
                // 每节两侧一根往上翘的骨刺
                for (int i = 1; i < points.size(); i++) {
                    V q = points.get(i - 1).lerp(points.get(i), 0.5F);
                    b.mirror(() -> b.rod(TOOTH, q.x() + 0.8F, q.y() + 0.6F, q.z(), q.x() + 2.4F, q.y() + 2.2F, q.z() + 0.6F, 0.5F, 0.5F));
                }
            }
            default -> throw new IllegalStateException();
        }
        V end = points.get(points.size() - 1);
        float pitch = points.size() > 1 ? pitchOf(points.get(points.size() - 2), end, -1) : 0;
        return new Chain(b.build(), end, pitch);
    }

    // ---- 尾骨（根部在原点，往 +z）

    private static Chain buildTail(BoneFamily family) {
        Builder b = new Builder();
        List<V> points;
        switch (family) {
            case TITAN -> {
                // 粗短的尾巴往下垂，末端一个骨锤，锤上四个疙瘩
                points = path(1.8F, 1, -0.15F, -0.2F, -0.25F, -0.3F, -0.3F, -0.35F);
                vertebrae(b, points, 2.6F, 1.4F, 0.5F, 0.3F);
                V end = points.get(points.size() - 1);
                b.box(BONE, end.x(), end.y(), end.z() + 1.2F, 3.6F, 2.4F, 3.4F);
                b.mirror(() -> {
                    b.box(TOOTH, end.x() + 2.0F, end.y() + 0.5F, end.z() + 1.2F, 0.9F, 1.0F, 1.2F);
                    b.box(TOOTH, end.x() + 1.6F, end.y() - 0.9F, end.z() + 2.2F, 0.9F, 0.9F, 1.0F);
                });
            }
            case PREDATOR -> {
                points = path(1.7F, 1, -0.04F, -0.06F, -0.08F, -0.08F, -0.1F, -0.1F, -0.1F, -0.12F, -0.12F, -0.12F);
                vertebrae(b, points, 1.8F, 0.6F, 0.4F, 0.5F);
            }
            case LONGNECK -> {
                // 鞭子似的长尾：先往下，再慢慢平
                float[] pitches = new float[13];
                for (int i = 0; i < pitches.length; i++) {
                    pitches[i] = -0.45F + 0.5F * i / (pitches.length - 1);
                }
                points = path(1.7F, 1, pitches);
                vertebrae(b, points, 2.4F, 0.45F, 0.35F, 0.3F);
            }
            case FLYER -> {
                // 细尾巴，末端一片菱形的尾翼
                points = path(1.1F, 1, -0.05F, -0.05F, -0.05F, -0.05F, -0.05F, -0.05F);
                vertebrae(b, points, 0.8F, 0.4F, 0, 0);
                V end = points.get(points.size() - 1);
                b.box(BONE, end.x(), end.y(), end.z() + 1.2F, 0.3F, 2.2F, 2.2F, 0.785F, 0, 0);
            }
            case WORM -> {
                // 越来越细的几节环，末端一根尖刺
                float[] radius = {2.8F, 2.3F, 1.8F, 1.3F};
                for (int i = 0; i < radius.length; i++) {
                    b.ringZ(BONE, 0, -0.3F * i, 1.2F + i * 1.5F, radius[i], 0.9F, 1.1F, 8);
                }
                b.rod(BONE, 0, -2.8F, 0, 0, -2.2F, 5.6F, 0.7F, 0.7F);
                b.rod(TOOTH, 0, -1.0F, 5.8F, 0, -1.6F, 8.4F, 0.8F, 0.8F);
                points = List.of(V.ZERO, new V(0, -1.6F, 8.4F));
            }
            case ARTHROPOD -> {
                // 蝎子那样往上卷过来的尾节，末端一个毒囊和尾刺
                points = List.of(V.ZERO, new V(0, 0.6F, 2), new V(0, 2, 3.8F), new V(0, 4, 4.8F), new V(0, 6, 4.6F), new V(0, 7.4F, 3.4F));
                for (int i = 0; i < points.size() - 1; i++) {
                    float w = 2.6F - i * 0.25F;
                    V a = points.get(i);
                    V c = points.get(i + 1);
                    V dir = c.sub(a);
                    V mid = a.lerp(c, 0.5F);
                    b.rod(BONE, mid.sub(dir.scale(0.47F)), mid.add(dir.scale(0.47F)), w, 1.6F);
                }
                V end = points.get(points.size() - 1);
                b.box(BONE, end.x(), end.y(), end.z(), 1.6F, 1.6F, 1.6F);
                b.rod(TOOTH, end.x(), end.y() - 0.3F, end.z() - 0.5F, end.x(), end.y() - 1.6F, end.z() - 2.2F, 0.6F, 0.6F);
            }
            case MARINE -> {
                // 往下弯的尾椎，末端上下两片尾鳍的骨
                points = path(1.6F, 1, -0.1F, -0.15F, -0.2F, -0.25F, -0.35F, -0.45F, -0.55F, -0.6F, -0.6F);
                vertebrae(b, points, 1.8F, 0.7F, 0.3F, 0.3F);
                V end = points.get(points.size() - 1);
                b.rod(BONE, end, end.add(new V(0, 3.5F, 2.5F)), 0.5F, 1.6F);
                b.rod(BONE, end, end.add(new V(0, -2.4F, 2.8F)), 0.5F, 1.4F);
            }
            case EXOTIC -> {
                // 几节带侧刺的尾椎，末端分成两股，各带一个尖
                points = path(1.8F, 1, -0.2F, -0.2F, -0.2F, -0.2F, -0.2F);
                vertebrae(b, points, 1.8F, 1.0F, 0.4F, 0);
                for (int i = 1; i < points.size(); i++) {
                    V q = points.get(i - 1).lerp(points.get(i), 0.5F);
                    b.mirror(() -> b.rod(TOOTH, q.x() + 0.7F, q.y(), q.z(), q.x() + 2.0F, q.y() + 0.9F, q.z() + 0.8F, 0.45F, 0.45F));
                }
                V end = points.get(points.size() - 1);
                b.mirror(() -> {
                    b.rod(BONE, end, end.add(new V(1.6F, 0.8F, 3.6F)), 0.8F, 0.8F);
                    b.rod(TOOTH, end.add(new V(1.6F, 0.8F, 3.6F)), end.add(new V(1.9F, 1.6F, 5.4F)), 0.55F, 0.55F);
                });
            }
            default -> throw new IllegalStateException();
        }
        V end = points.get(points.size() - 1);
        float pitch = points.size() > 1 ? pitchOf(points.get(points.size() - 2), end, 1) : 0;
        return new Chain(b.build(), end, pitch);
    }

    // ---- 胸廓（脊柱的中点在原点）

    /** 一般的胸廓：脊柱、一根根肋骨（中间的最深）、肩胛骨、骨盆。 */
    private static Body ribbed(Builder b, float length, float width, float height, int ribs, float spine, float process, float rib) {
        int segments = 6;
        List<V> points = new ArrayList<>();
        for (int i = 0; i <= segments; i++) {
            float z = -length / 2 + length * i / segments;
            // 脊柱在中间稍微拱起
            float y = (float) Math.sin(Math.PI * i / segments) * 0.6F;
            points.add(new V(0, y, z));
        }
        vertebrae(b, points, spine, spine * 0.9F, process, 0);
        float front = -length / 2 + length * 0.12F;
        float back = length / 2 - length * 0.22F;
        b.mirror(() -> {
            for (int k = 0; k < ribs; k++) {
                float t = (k + 0.5F) / ribs;
                float z = front + (back - front) * t;
                // 中间的肋骨最长，两头短
                float depth = height * (0.62F + 0.38F * (float) Math.sin(Math.PI * t));
                float out = width / 2 * (0.8F + 0.2F * (float) Math.sin(Math.PI * t));
                V root = new V(spine * 0.45F, 0.1F, z);
                V shoulder = new V(out, -depth * 0.35F, z + 0.35F);
                V tip = new V(out * 0.72F, -depth, z + 0.8F);
                b.rod(BONE, root, shoulder, rib, rib);
                b.rod(BONE, shoulder, tip, rib * 0.85F, rib * 0.85F);
            }
            // 肩胛骨：从肩往上斜着贴在肋骨外面
            V shoulder = new V(width * 0.46F, -height * 0.5F, -length / 2 + length * 0.18F);
            b.rod(BONE, shoulder, new V(width * 0.48F, 0.2F, shoulder.z() + length * 0.12F), rib * 1.4F, rib * 0.6F);
            // 骨盆：髂骨一块板，耻骨朝前下、坐骨朝后下
            V hip = new V(width * 0.4F, -height * 0.45F, length / 2 - length * 0.15F);
            b.box(BONE, width * 0.3F, -height * 0.12F, hip.z(), rib * 1.1F, height * 0.42F, length * 0.26F);
            b.rod(BONE, hip, hip.add(new V(-width * 0.25F, -height * 0.4F, -length * 0.12F)), rib, rib);
            b.rod(BONE, hip, hip.add(new V(-width * 0.25F, -height * 0.3F, length * 0.14F)), rib, rib);
        });
        return new Body(null, new V(0, 0.3F, -length / 2 - 0.4F), new V(0, 0, length / 2 + 0.4F),
                new V(width * 0.46F, -height * 0.5F, -length / 2 + length * 0.18F), new V(width * 0.4F, -height * 0.45F, length / 2 - length * 0.15F));
    }

    private static Body buildBody(BoneFamily family) {
        Builder b = new Builder();
        Body sockets = switch (family) {
            case TITAN -> ribbed(b, 16, 12, 10, 7, 3.0F, 1.4F, 1.1F);
            case PREDATOR -> {
                Body body = ribbed(b, 11, 7, 8, 6, 2.2F, 1.0F, 0.8F);
                // 肚子下面一排腹肋
                b.mirror(() -> {
                    for (int k = 0; k < 5; k++) {
                        float z = -2.5F + k * 1.3F;
                        b.rod(BONE, 0.2F, -7.4F, z, 2.3F, -6.6F, z + 0.3F, 0.5F, 0.5F);
                    }
                });
                // 前肢的肩高一些：小短手挂在胸前
                yield new Body(null, body.neck(), body.tail(), new V(2.8F, -3.2F, -3.6F), body.rearLimb());
            }
            case LONGNECK -> ribbed(b, 16, 11, 11, 7, 2.8F, 0.9F, 1.0F);
            case FLYER -> {
                Body body = ribbed(b, 10, 7, 6.5F, 6, 1.4F, 0.5F, 0.65F);
                // 龙骨突：胸骨下面一片竖着的板，飞行的肌肉长在这里
                b.box(BONE, 0, -5.6F, -1.5F, 0.8F, 3.0F, 6);
                b.box(BONE, 0, -4.6F, -1.5F, 4.6F, 0.6F, 6);
                yield new Body(null, body.neck(), body.tail(), new V(3.1F, -1.6F, -3.0F), body.rearLimb());
            }
            case WORM -> {
                // 一节节的环，底下一条韧带
                for (int i = 0; i < 7; i++) {
                    float z = -8.1F + i * 2.7F;
                    b.ringZ(BONE, 0, 0, z, 3.4F, 1.1F, 1.4F, 8);
                }
                b.rod(BONE, 0, -3.5F, -9, 0, -3.5F, 9, 0.8F, 0.8F);
                b.rod(BONE, 0, 3.5F, -9, 0, 3.5F, 9, 0.6F, 0.6F);
                yield new Body(null, new V(0, 0, -9.4F), new V(0, 0, 9.4F), new V(2.4F, -3.2F, -5.5F), new V(2.4F, -3.2F, 5.5F));
            }
            case ARTHROPOD -> {
                // 外骨骼：一片片叠着的背甲，两侧往下斜的侧板，下面的腹板
                for (int k = 0; k < 6; k++) {
                    float z = -5 + k * 2;
                    float arch = (float) Math.sin(Math.PI * (k + 0.5F) / 6);
                    b.box(BONE, 0, 0.4F + arch * 0.6F, z, 7 + arch * 1.4F, 1.2F, 2.3F);
                    b.box(BONE, 0, -3.8F, z, 4.2F, 0.6F, 1.8F);
                    float half = (7 + arch * 1.4F) / 2;
                    b.mirror(() -> b.box(BONE, half, -0.9F, z, 1.0F, 2.2F, 2.0F, 0, 0, -0.5F));
                }
                yield new Body(null, new V(0, 0.4F, -6.2F), new V(0, 0.3F, 6.2F), new V(3.6F, -3.0F, -3.6F), new V(3.6F, -3.0F, 3.6F));
            }
            case MARINE -> {
                Body body = ribbed(b, 15, 8, 8, 8, 2.0F, 0.6F, 0.7F);
                // 宽的肩带：鳍肢挂在下面
                b.box(BONE, 0, -4.4F, -5.0F, 6.4F, 0.8F, 2.6F);
                yield new Body(null, body.neck(), body.tail(), new V(3.6F, -4.6F, -4.5F), new V(3.3F, -4.2F, 4.5F));
            }
            case EXOTIC -> {
                // 往外翻、末端朝上勾的肋骨，像一顶冠；脊柱上一排晶刺，胸前两根胸骨
                List<V> points = new ArrayList<>();
                for (int i = 0; i <= 6; i++) {
                    points.add(new V(0, (float) Math.sin(Math.PI * i / 6) * 0.8F, -6 + 2 * i));
                }
                vertebrae(b, points, 2.0F, 1.8F, 0, 0);
                for (int i = 0; i < 6; i++) {
                    float z = -5 + 2 * i;
                    b.rod(TOOTH, 0, 1.0F, z, 0, 3.2F + (i % 2) * 0.8F, z + 0.6F, 0.6F, 0.6F);
                }
                b.mirror(() -> {
                    for (int k = 0; k < 6; k++) {
                        float z = -4.6F + k * 1.8F;
                        V root = new V(0.9F, 0.2F, z);
                        V out = new V(5.0F, -2.6F, z + 0.3F);
                        V low = new V(5.4F, -6.8F, z + 0.6F);
                        b.rod(BONE, root, out, 0.8F, 0.8F);
                        b.rod(BONE, out, low, 0.7F, 0.7F);
                        b.rod(TOOTH, low, new V(6.8F, -5.6F, z + 0.6F), 0.5F, 0.5F);
                    }
                    b.rod(BONE, 1.2F, -7.4F, -4.6F, 1.2F, -7.4F, 4.6F, 0.8F, 0.8F);
                });
                yield new Body(null, new V(0, 0.4F, -6.4F), new V(0, 0.2F, 6.4F), new V(4.6F, -4.5F, -3.8F), new V(4.6F, -4.5F, 3.8F));
            }
        };
        return new Body(b.build(), sockets.neck(), sockets.tail(), sockets.frontLimb(), sockets.rearLimb());
    }

    // ---- 四肢（右边，原点在肩、胯，往下）

    /** 一条腿：一串关节，每两个关节之间一根骨头，粗细依次取 {@code thickness}。 */
    private static void leg(Builder b, float[] thickness, V... joints) {
        for (int i = 0; i < joints.length - 1; i++) {
            float t = thickness[Math.min(i, thickness.length - 1)];
            b.rod(BONE, joints[i], joints[i + 1], t, t);
        }
    }

    private static Limbs buildLimbs(BoneFamily family) {
        Builder front = new Builder();
        Builder rear = new Builder();
        switch (family) {
            case TITAN -> {
                // 柱子一样的粗腿，脚下一块肉垫的骨，前面三个钝爪
                leg(front, new float[] {2.8F, 2.4F}, V.ZERO, new V(0.4F, -5.5F, -0.6F), new V(0.2F, -10, 0));
                pad(front, 0.2F, -10.6F, -0.4F, 3.6F);
                leg(rear, new float[] {3.2F, 2.6F}, V.ZERO, new V(0.3F, -5.5F, 0.8F), new V(0.2F, -10.4F, 0));
                pad(rear, 0.2F, -11, -0.4F, 4);
            }
            case PREDATOR -> {
                // 前肢小短手，三根爪；后肢是长腿：大腿朝前、小腿朝后、跖骨又朝前，三根脚趾
                leg(front, new float[] {0.9F, 0.7F}, V.ZERO, new V(0.4F, -2.4F, -1.0F), new V(0.3F, -4, -2.2F));
                front.rod(TOOTH, 0.3F, -4, -2.2F, 0.6F, -4.9F, -2.9F, 0.4F, 0.4F);
                front.rod(TOOTH, 0.3F, -4, -2.2F, 0.0F, -5.0F, -2.8F, 0.4F, 0.4F);
                leg(rear, new float[] {2.2F, 1.8F, 1.3F}, V.ZERO, new V(0.6F, -5.5F, -2.0F), new V(0.5F, -11, 0.6F), new V(0.5F, -14, -0.6F));
                V foot = new V(0.5F, -14.2F, -0.6F);
                rear.rod(TOOTH, foot, new V(1.4F, -14.6F, -3.2F), 0.6F, 0.6F);
                rear.rod(TOOTH, foot, new V(0.5F, -14.6F, -3.6F), 0.6F, 0.6F);
                rear.rod(TOOTH, foot, new V(-0.4F, -14.6F, -3.2F), 0.6F, 0.6F);
                rear.rod(TOOTH, foot, new V(0.5F, -14.4F, 0.8F), 0.5F, 0.5F);
            }
            case LONGNECK -> {
                // 前腿比后腿长，四条都是直直的柱子
                leg(front, new float[] {2.8F, 2.3F}, V.ZERO, new V(0.3F, -6.5F, -0.3F), new V(0.2F, -12, 0));
                pad(front, 0.2F, -12.6F, -0.3F, 3.2F);
                leg(rear, new float[] {3.4F, 2.8F}, V.ZERO, new V(0.3F, -6, 0.6F), new V(0.2F, -11, 0));
                pad(rear, 0.2F, -11.6F, -0.3F, 3.6F);
            }
            case FLYER -> {
                // 前肢是收拢的翼：肱骨往外上，前臂朝前，长长的翼指往后折下来，腕上一个小爪
                leg(front, new float[] {1.1F, 0.9F, 0.7F, 0.55F}, V.ZERO, new V(2.5F, 2, 0.5F), new V(6, 3, -2), new V(9, 1.5F, 4),
                        new V(10, 0, 9));
                front.rod(TOOTH, 6, 3, -2, 5.6F, 2.2F, -3.2F, 0.4F, 0.4F);
                front.rod(BONE, 6, 3, -2, 4.6F, 2.6F, -1.2F, 0.4F, 0.4F);
                // 后肢细小
                leg(rear, new float[] {0.8F, 0.6F}, V.ZERO, new V(0.4F, -2.6F, 0.6F), new V(0.4F, -5, 0));
                V foot = new V(0.4F, -5, 0);
                rear.rod(TOOTH, foot, new V(0.9F, -5.3F, -1.4F), 0.35F, 0.35F);
                rear.rod(TOOTH, foot, new V(0.1F, -5.3F, -1.4F), 0.35F, 0.35F);
            }
            case WORM -> {
                // 体节环：一节节往下摞的环，中间一根骨柱，底下一块垫
                for (Builder b : List.of(front, rear)) {
                    b.rod(BONE, 0.3F, 0, 0, 0.3F, -4.6F, 0, 0.6F, 0.6F);
                    b.ringY(BONE, 0.3F, -1.2F, 0, 1.3F, 0.6F, 1.0F, 6);
                    b.ringY(BONE, 0.3F, -2.8F, 0, 1.1F, 0.6F, 1.0F, 6);
                    b.ringY(BONE, 0.3F, -4.2F, 0, 0.9F, 0.6F, 0.9F, 6);
                    b.box(BONE, 0.3F, -4.9F, 0, 2.0F, 0.6F, 2.0F);
                }
            }
            case ARTHROPOD -> {
                // 节肢：基节往外，腿节往上翘出一个高高的膝，胫节往下外撑到地，末端一根爪；每边前后各两条
                for (int k = 0; k < 2; k++) {
                    float spread = k == 0 ? 1 : 0.35F;
                    arthropodLeg(front, -spread, k * 0.9F);
                    arthropodLeg(rear, spread, -k * 0.9F);
                }
            }
            case MARINE -> {
                // 鳍肢：一截短的肱骨，后面三排扇开的指骨，一节一节
                flipper(front, new V(1.2F, -1.4F, 0.4F), 1.6F, new V(5, -4.2F, 3.6F), new V(5.6F, -3.4F, 2.2F), new V(5.2F, -2.6F, 1.0F));
                flipper(rear, new V(1.0F, -1.2F, 0.4F), 1.4F, new V(3.8F, -3.4F, 3.2F), new V(4.3F, -2.8F, 2.0F), new V(4.0F, -2.2F, 1.0F));
            }
            case EXOTIC -> {
                // 细长的腿，膝上一根骨刺，脚是一根尖
                for (int k = 0; k < 2; k++) {
                    Builder b = k == 0 ? front : rear;
                    float z = k == 0 ? -1 : 1;
                    leg(b, new float[] {1.2F, 1.0F}, V.ZERO, new V(2.5F, -4, z), new V(2.0F, -10, z * 1.5F));
                    b.rod(TOOTH, 2.0F, -10, z * 1.5F, 2.2F, -12, z * 2.2F, 0.7F, 0.7F);
                    b.rod(TOOTH, 2.5F, -4, z, 4.2F, -2.6F, z * 0.6F, 0.6F, 0.6F);
                }
            }
        }
        return new Limbs(front.build(), rear.build());
    }

    /** 脚底的骨垫和前面三个钝爪。 */
    private static void pad(Builder b, float x, float y, float z, float size) {
        b.box(BONE, x, y, z, size, 1.2F, size);
        for (int i = -1; i <= 1; i++) {
            b.box(TOOTH, x + i * size * 0.3F, y - 0.1F, z - size / 2 - 0.35F, 0.7F, 0.8F, 0.8F);
        }
    }

    /** 甲壳兽的一条腿：{@code dir} 为负往前伸、为正往后伸，{@code offset} 是在接点前后错开多少。 */
    private static void arthropodLeg(Builder b, float dir, float offset) {
        V base = new V(0, 0, offset);
        V coxa = base.add(new V(1.6F, -0.4F, 0.8F * dir));
        V knee = base.add(new V(4.2F, 3.0F, 2.6F * dir));
        V foot = base.add(new V(6.2F, -5.6F, 4.2F * dir));
        leg(b, new float[] {1.0F, 0.9F, 0.7F}, base, coxa, knee, foot);
        b.rod(TOOTH, foot, base.add(new V(6.8F, -7.2F, 4.6F * dir)), 0.5F, 0.5F);
    }

    /** 鳍肢：肱骨从原点到 {@code elbow}，再朝三个指尖各排三节指骨。 */
    private static void flipper(Builder b, V elbow, float humerus, V... tips) {
        b.rod(BONE, V.ZERO, elbow, humerus, humerus);
        for (V tip : tips) {
            for (int i = 0; i < 3; i++) {
                V a = elbow.lerp(tip, i / 3F + 0.03F);
                V c = elbow.lerp(tip, (i + 1) / 3F - 0.03F);
                b.rod(BONE, a, c, 0.9F - i * 0.15F, 0.5F);
            }
        }
    }
}
