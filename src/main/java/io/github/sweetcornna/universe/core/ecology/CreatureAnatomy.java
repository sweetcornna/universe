package io.github.sweetcornna.universe.core.ecology;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import io.github.sweetcornna.universe.core.ecology.CreatureSpecies.Body;

/**
 * 按物种的身体参数拼出模型：若干部件（可以嵌套、可以转动），每个部件由几个长方体组成。
 *
 * <p>坐标和原版实体模型一致：单位是像素（1/16 格），y 朝下，地面在 y = 24，动物面朝 -z。子部件的轴心相对父部件。
 * 贴图坐标也在这里分配好（按原版长方体的展开方式），{@link CreatureSkin} 按同样的布局上色。
 */
public final class CreatureAnatomy {
    /** 地面的 y。 */
    public static final float GROUND = 24;

    private final List<Part> parts = new ArrayList<>();
    private int textureWidth;
    private int textureHeight;
    private float hitWidth;
    private float hitHeight;

    /** 部件在动画里的角色。 */
    public enum Role {
        BODY, HEAD, LEG, ARM, TAIL, WING, SEGMENT, DECOR
    }

    /**
     * 一个长方体：原点（部件坐标）、尺寸、贴图坐标，以及它属于哪类表面（上色用）。
     *
     * @param surface 0 普通皮肤，1 角、爪这类骨质，2 翼膜
     */
    public record Box(float x, float y, float z, int w, int h, int d, int u, int v, int surface) {
        Box withUv(int u, int v) {
            return new Box(this.x, this.y, this.z, this.w, this.h, this.d, u, v, this.surface);
        }

        /** 展开后在贴图上占的宽、高。 */
        int uvWidth() {
            return 2 * (this.w + this.d);
        }

        int uvHeight() {
            return this.d + this.h;
        }
    }

    /**
     * 一个部件。
     *
     * @param parent   父部件的名字，null 表示直接挂在模型根上
     * @param px       轴心（相对父部件）
     * @param xRot     静止时的转角（弧度）
     * @param role     动画角色
     * @param index    同类部件的序号（第几条腿、第几节尾巴）
     * @param side     左右：-1、0、1
     * @param phase    行走动画的相位（弧度）
     */
    public record Part(String name, String parent, float px, float py, float pz, float xRot, float yRot, float zRot,
            List<Box> boxes, Role role, int index, int side, float phase) {
    }

    private CreatureAnatomy() {
    }

    public List<Part> parts() {
        return this.parts;
    }

    public int textureWidth() {
        return this.textureWidth;
    }

    public int textureHeight() {
        return this.textureHeight;
    }

    /** 碰撞箱的宽（格，未乘物种大小）。 */
    public float hitWidth() {
        return this.hitWidth;
    }

    /** 碰撞箱的高（格，未乘物种大小）。 */
    public float hitHeight() {
        return this.hitHeight;
    }

    public Part part(String name) {
        for (Part part : this.parts) {
            if (part.name().equals(name)) {
                return part;
            }
        }
        return null;
    }

    /** 部件轴心在模型里的绝对位置（不计转角）。 */
    public float[] origin(Part part) {
        float x = part.px();
        float y = part.py();
        float z = part.pz();
        String parent = part.parent();
        while (parent != null) {
            Part p = this.part(parent);
            x += p.px();
            y += p.py();
            z += p.pz();
            parent = p.parent();
        }
        return new float[] {x, y, z};
    }

    public static CreatureAnatomy of(CreatureSpecies species) {
        CreatureAnatomy a = new CreatureAnatomy();
        switch (species.plan()) {
            case SERPENT -> a.serpent(species);
            case FLYER -> a.flyer(species);
            default -> a.walker(species);
        }
        a.packUv();
        a.measure();
        return a;
    }

    // ---------------------------------------------------------------- 体型

    /** 四足、六足、两足：水平的身体加若干对腿。 */
    private void walker(CreatureSpecies s) {
        Body b = s.body();
        int len = b.length();
        int w = b.width();
        int h = b.height();
        int legLen = b.legLength();
        float bodyY = GROUND - legLen - h / 2f;
        List<Box> bodyBoxes = new ArrayList<>();
        bodyBoxes.add(box(-w / 2f, -h / 2f, -len / 2f, w, h, len));
        this.dorsal(b, bodyBoxes, -h / 2f, len);
        this.add("body", null, 0, bodyY, 0, 0, 0, 0, bodyBoxes, Role.BODY, 0, 0, 0);

        int pairs = b.legPairs();
        int lw = b.legWidth();
        boolean hexapod = s.plan() == BodyPlan.HEXAPOD;
        for (int i = 0; i < pairs; i++) {
            float z;
            if (pairs == 1) {
                z = len * 0.1f;
            } else {
                z = -len / 2f + lw / 2f + 1 + i * (len - lw - 2f) / (pairs - 1);
            }
            for (int side = -1; side <= 1; side += 2) {
                // 六足虫的腿伸到身体外侧
                float x = side * (hexapod ? w / 2f + lw / 2f : w / 2f - lw / 2f);
                // 对角步态：四足同侧前后相反；六足三角步态
                float phase = (float) (((i + (side > 0 ? 1 : 0)) % 2) * Math.PI);
                this.add("leg" + i + (side < 0 ? "l" : "r"), null, x, GROUND - legLen, z, 0, 0, 0,
                        List.of(box(-lw / 2f, 0, -lw / 2f, lw, legLen, lw)), Role.LEG, i, side, phase);
            }
        }
        if (s.plan() == BodyPlan.BIPED) {
            // 两足兽的小前肢
            int armLen = Math.max(2, h / 2 + 1);
            for (int side = -1; side <= 1; side += 2) {
                this.add("arm" + (side < 0 ? "l" : "r"), "body", side * (w / 2f), h * 0.1f, -len / 2f + 2, -0.7f, 0, 0,
                        List.of(box(-1, 0, -1, 2, armLen, 2)), Role.ARM, 0, side, (float) (side > 0 ? Math.PI : 0));
            }
        }
        this.head(s, "body", 0, -h / 2f + 1, -len / 2f + 1);
        this.tail(b, "body", -h / 2f + Math.min(h / 2f, 2), len / 2f);
    }

    /** 节蠕虫：一串身体节，每节可以有一对短腿，最前面是头。 */
    private void serpent(CreatureSpecies s) {
        Body b = s.body();
        int segs = b.segments();
        int segLen = b.length();
        int legLen = b.legLength();
        float total = segs * segLen;
        String parent = null;
        for (int i = 0; i < segs; i++) {
            // 后面几节逐渐变细
            int taper = Math.max(0, i - (segs - 3));
            int w = Math.max(2, b.width() - taper);
            int h = Math.max(2, b.height() - taper / 2);
            float y = i == 0 ? GROUND - legLen - b.height() / 2f : 0;
            float z = i == 0 ? -total / 2f : segLen;
            float yOffset = (b.height() - h) / 2f;
            String name = "segment" + i;
            this.add(name, parent, 0, y, z, 0, 0, 0, List.of(box(-w / 2f, -h / 2f + yOffset, 0, w, h, segLen)),
                    Role.SEGMENT, i, 0, 0);
            if (legLen > 0) {
                for (int side = -1; side <= 1; side += 2) {
                    float phase = (float) (i * Math.PI / 2 + (side > 0 ? Math.PI : 0));
                    this.add(name + (side < 0 ? "l" : "r"), name, side * (w / 2f), b.height() / 2f - 0.01f, segLen / 2f, 0, 0,
                            -side * 0.4f, List.of(box(-1, 0, -1, 2, legLen + 1, 2)), Role.LEG, i, side, phase);
                }
            }
            parent = name;
        }
        this.head(s, "segment0", 0, 0, 0);
    }

    /** 飞兽：小身体、一对翅膀、短腿。 */
    private void flyer(CreatureSpecies s) {
        Body b = s.body();
        int len = b.length();
        int w = b.width();
        int h = b.height();
        int legLen = b.legLength();
        float bodyY = GROUND - legLen - h / 2f;
        this.add("body", null, 0, bodyY, 0, 0, 0, 0, List.of(box(-w / 2f, -h / 2f, -len / 2f, w, h, len)), Role.BODY, 0, 0, 0);
        int span = b.wingSpan();
        int wingDepth = Math.max(3, len - 1);
        for (int side = -1; side <= 1; side += 2) {
            float x0 = side > 0 ? 0 : -span;
            this.add("wing" + (side < 0 ? "l" : "r"), "body", side * (w / 2f), -h / 2f + 1, -1, 0, 0, 0,
                    List.of(new Box(x0, 0, -wingDepth / 2f, span, 1, wingDepth, 0, 0, 2)), Role.WING, 0, side, 0);
            this.add("leg0" + (side < 0 ? "l" : "r"), null, side * Math.max(1, w / 2f - 1), GROUND - legLen, 1, 0, 0, 0,
                    List.of(box(-0.5f, 0, -0.5f, 1, legLen, 1)), Role.LEG, 0, side, (float) (side > 0 ? Math.PI : 0));
        }
        this.head(s, "body", 0, -h / 2f + 1, -len / 2f + 1);
        if (b.tailLength() > 0) {
            int fan = Math.max(2, w);
            this.add("tail0", "body", 0, -h / 2f + 1, len / 2f, -0.15f, 0, 0,
                    List.of(new Box(-fan / 2f, -0.5f, 0, fan, 1, b.tailLength(), 0, 0, 2)), Role.TAIL, 0, 0, 0);
        }
    }

    // ---------------------------------------------------------------- 部件

    /** 脖子、头、吻部、角、头饰。轴心在脖子根部。 */
    private void head(CreatureSpecies s, String parent, float px, float py, float pz) {
        Body b = s.body();
        int neck = b.neckLength();
        int hw = b.headWidth();
        int hh = b.headHeight();
        int hd = b.headDepth();
        List<Box> boxes = new ArrayList<>();
        float hy0;
        float hz0;
        if (neck > 0) {
            int nw = Math.max(2, Math.min(hw - 1, b.width() / 2 + 1));
            boxes.add(box(-nw / 2f, -neck, -nw, nw, neck + 1, nw));
            hy0 = -neck - hh + 2;
            hz0 = -nw - hd + 2;
        } else {
            hy0 = -hh / 2f - 1;
            hz0 = -hd + 1;
        }
        boxes.add(box(-hw / 2f, hy0, hz0, hw, hh, hd));
        int snout = b.snoutLength();
        if (snout > 0) {
            int sw = Math.max(2, hw - 2);
            int sh = Math.max(2, hh / 2);
            boxes.add(box(-sw / 2f, hy0 + hh - sh, hz0 - snout, sw, sh, snout));
        }
        switch (b.crest()) {
            case EARS -> {
                boxes.add(box(-hw / 2f - 1, hy0 - 2, hz0 + hd - 3, 2, 3, 1));
                boxes.add(box(hw / 2f - 1, hy0 - 2, hz0 + hd - 3, 2, 3, 1));
            }
            case FRILL -> boxes.add(box(-hw / 2f - 2, hy0 - 2, hz0 + hd - 1, hw + 4, hh + 3, 1));
            default -> {
            }
        }
        this.add("head", parent, px, py, pz, 0, 0, 0, boxes, Role.HEAD, 0, 0, 0);
        // 角和触角各自是子部件，可以斜着长
        for (int i = 0; i < b.horns(); i++) {
            float x = b.horns() == 1 ? 0 : -hw / 2f + 1 + i * (hw - 2f) / (b.horns() - 1);
            float tilt = b.horns() == 1 ? -0.3f : (x < 0 ? -0.35f : x > 0 ? 0.35f : 0);
            this.add("horn" + i, "head", x, hy0 + 0.5f, hz0 + hd / 2f, 0.45f, 0, tilt,
                    List.of(new Box(-0.5f, -b.hornLength(), -0.5f, 1, b.hornLength(), 1, 0, 0, 1)), Role.DECOR, i, 0, 0);
        }
        if (b.crest() == CreatureSpecies.Crest.ANTENNAE) {
            for (int side = -1; side <= 1; side += 2) {
                this.add("antenna" + (side < 0 ? "l" : "r"), "head", side * (hw / 2f - 1), hy0 + 0.5f, hz0 + 1, -0.6f, 0, side * 0.3f,
                        List.of(box(-0.5f, -hh - 2, -0.5f, 1, hh + 2, 1)), Role.DECOR, 0, side, 0);
            }
        }
    }

    /** 尾巴：一串逐节变细的长方体，越往后越下垂。 */
    private void tail(Body b, String parent, float py, float pz) {
        if (b.tailLength() <= 0) {
            return;
        }
        int segments = Math.max(1, b.tailSegments());
        int segLen = Math.max(2, Math.round((float) b.tailLength() / segments));
        int width = Math.max(1, Math.min(4, b.width() / 3));
        String previous = parent;
        for (int i = 0; i < segments; i++) {
            int tw = Math.max(1, width - i);
            float x0 = -tw / 2f;
            this.add("tail" + i, previous, 0, i == 0 ? py : 0, i == 0 ? pz - 1 : segLen - 0.5f, i == 0 ? -0.35f : -0.15f, 0, 0,
                    List.of(box(x0, -tw / 2f, 0, tw, tw, segLen)), Role.TAIL, i, 0, 0);
            previous = "tail" + i;
        }
    }

    /** 背上的刺、鳍、骨板，直接加在身体上。 */
    private void dorsal(Body b, List<Box> boxes, float top, int len) {
        switch (b.dorsal()) {
            case SPIKES -> {
                for (float z = -len / 2f + 2; z < len / 2f - 1; z += 3) {
                    boxes.add(new Box(-0.5f, top - 2, z, 1, 2, 1, 0, 0, 1));
                }
            }
            case FIN -> {
                int finLen = Math.max(3, len * 2 / 3);
                boxes.add(new Box(-0.5f, top - 3, -finLen / 2f, 1, 3, finLen, 0, 0, 2));
            }
            case PLATES -> {
                for (float z = -len / 2f + 1; z < len / 2f - 2; z += 4) {
                    boxes.add(new Box(-1, top - 2, z, 2, 2, 2, 0, 0, 1));
                }
            }
            default -> {
            }
        }
    }

    private void add(String name, String parent, float px, float py, float pz, float xRot, float yRot, float zRot,
            List<Box> boxes, Role role, int index, int side, float phase) {
        this.parts.add(new Part(name, parent, px, py, pz, xRot, yRot, zRot, new ArrayList<>(boxes), role, index, side, phase));
    }

    private static Box box(float x, float y, float z, int w, int h, int d) {
        return new Box(x, y, z, w, h, d, 0, 0, 0);
    }

    // ---------------------------------------------------------------- 贴图布局和尺寸

    /** 把所有长方体的展开图按高度排成行，放进 64 或 128 宽的贴图。 */
    private void packUv() {
        record Slot(Part part, int index, Box box) {
        }
        List<Slot> slots = new ArrayList<>();
        for (Part part : this.parts) {
            for (int i = 0; i < part.boxes().size(); i++) {
                slots.add(new Slot(part, i, part.boxes().get(i)));
            }
        }
        slots.sort(Comparator.comparingInt((Slot s) -> s.box().uvHeight()).reversed());
        for (int width : new int[] {64, 128, 256}) {
            int x = 0;
            int y = 0;
            int row = 0;
            boolean fits = true;
            int[][] uv = new int[slots.size()][];
            for (int i = 0; i < slots.size(); i++) {
                Box box = slots.get(i).box();
                if (box.uvWidth() > width) {
                    fits = false;
                    break;
                }
                if (x + box.uvWidth() > width) {
                    x = 0;
                    y += row;
                    row = 0;
                }
                uv[i] = new int[] {x, y};
                x += box.uvWidth();
                row = Math.max(row, box.uvHeight());
            }
            int height = y + row;
            if (!fits || height > width) {
                continue;
            }
            for (int i = 0; i < slots.size(); i++) {
                Slot slot = slots.get(i);
                slot.part().boxes().set(slot.index(), slot.box().withUv(uv[i][0], uv[i][1]));
            }
            this.textureWidth = width;
            this.textureHeight = Math.max(16, Integer.highestOneBit(Math.max(1, height - 1)) << 1);
            return;
        }
        throw new IllegalStateException("creature texture does not fit");
    }

    private void measure() {
        float minY = GROUND;
        float minX = 0;
        float maxX = 0;
        float minZ = 0;
        float maxZ = 0;
        for (Part part : this.parts) {
            if (part.role() == Role.WING || part.role() == Role.DECOR) {
                continue;
            }
            float[] o = this.origin(part);
            for (Box box : part.boxes()) {
                minY = Math.min(minY, o[1] + box.y());
                minX = Math.min(minX, o[0] + box.x());
                maxX = Math.max(maxX, o[0] + box.x() + box.w());
                minZ = Math.min(minZ, o[2] + box.z());
                maxZ = Math.max(maxZ, o[2] + box.z() + box.d());
            }
        }
        // 原版的碰撞箱是正方形：取宽和（打折的）长里大的一个
        this.hitWidth = Math.max(maxX - minX, (maxZ - minZ) * 0.6f) / 16f;
        this.hitHeight = (GROUND - minY) / 16f;
    }
}
