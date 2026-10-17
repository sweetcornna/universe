package io.github.sweetcornna.universe.core.ecology;

import io.github.sweetcornna.universe.core.ecology.CreatureAnatomy.Box;
import io.github.sweetcornna.universe.core.ecology.CreatureAnatomy.Part;
import io.github.sweetcornna.universe.core.ecology.CreatureAnatomy.Role;
import io.github.sweetcornna.universe.core.ecology.CreatureSpecies.Body;
import io.github.sweetcornna.universe.core.ecology.CreatureSpecies.Skin;
import io.github.sweetcornna.universe.core.procgen.Colors;
import io.github.sweetcornna.universe.core.procgen.Seeds;
import io.github.sweetcornna.universe.core.procgen.SimplexNoise;

/**
 * 按 {@link CreatureAnatomy} 的贴图布局给动物上色：每个像素先换算回它在身上的三维位置，
 * 再按位置算花纹，所以条纹、斑点能跨过长方体的接缝连起来。
 *
 * <p>结果是两张 ARGB 图：皮肤，以及只有发光部分（眼睛、荧光花纹）不透明的发光层。
 * 展开方式和原版 {@code ModelPart.Cube} 一致：顶面在 (u+d, v)，底面在 (u+d+w, v)，
 * 第二行依次是 -x 侧、正面（-z）、+x 侧、背面。
 */
public final class CreatureSkin {
    private static final int BONE = 0xEDE6D0;
    private static final int TOOTH = 0xF0EEE0;

    private final int width;
    private final int height;
    private final int[] pixels;
    private final int[] glow;
    private boolean glowing;

    private CreatureSkin(int width, int height) {
        this.width = width;
        this.height = height;
        this.pixels = new int[width * height];
        this.glow = new int[width * height];
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    /** 皮肤，ARGB，行优先；没用到的地方全透明。 */
    public int[] pixels() {
        return this.pixels;
    }

    /** 发光层，ARGB，不发光的地方全透明。 */
    public int[] glow() {
        return this.glow;
    }

    /** 有没有发光的部分（没有就不必画发光层）。 */
    public boolean hasGlow() {
        return this.glowing;
    }

    public static CreatureSkin paint(CreatureSpecies species, CreatureAnatomy anatomy) {
        CreatureSkin skin = new CreatureSkin(anatomy.textureWidth(), anatomy.textureHeight());
        Painter painter = new Painter(species, anatomy);
        for (Part part : anatomy.parts()) {
            float[] origin = anatomy.origin(part);
            for (Box box : part.boxes()) {
                skin.paintBox(painter, part, origin, box);
            }
        }
        Part head = anatomy.part("head");
        if (head != null) {
            skin.paintFace(species, head);
        }
        return skin;
    }

    /** 按原版长方体的展开方式逐面上色。 */
    private void paintBox(Painter painter, Part part, float[] origin, Box box) {
        int u = box.u();
        int v = box.v();
        int w = box.w();
        int h = box.h();
        int d = box.d();
        float x0 = origin[0] + box.x();
        float y0 = origin[1] + box.y();
        float z0 = origin[2] + box.z();
        for (int j = 0; j < d; j++) {
            float z = z0 + d - j - 0.5f;
            for (int i = 0; i < w; i++) {
                // 顶面：靠下一行贴着正面
                this.texel(painter, u + d + i, v + j, part, box, x0 + i + 0.5f, y0, z, Face.TOP);
                // 底面
                this.texel(painter, u + d + w + i, v + j, part, box, x0 + i + 0.5f, y0 + h, z, Face.BOTTOM);
            }
        }
        for (int j = 0; j < h; j++) {
            float y = y0 + j + 0.5f;
            for (int i = 0; i < d; i++) {
                // -x 侧：右边缘贴着正面
                this.texel(painter, u + i, v + d + j, part, box, x0, y, z0 + d - i - 0.5f, Face.SIDE);
                // +x 侧：左边缘贴着正面
                this.texel(painter, u + d + w + i, v + d + j, part, box, x0 + w, y, z0 + i + 0.5f, Face.SIDE);
            }
            for (int i = 0; i < w; i++) {
                this.texel(painter, u + d + i, v + d + j, part, box, x0 + i + 0.5f, y, z0, Face.FRONT);
                this.texel(painter, u + 2 * d + w + i, v + d + j, part, box, x0 + w - i - 0.5f, y, z0 + d, Face.BACK);
            }
        }
    }

    private void texel(Painter painter, int tx, int ty, Part part, Box box, float x, float y, float z, Face face) {
        int color = painter.color(part, box, x, y, z, face);
        // 毛色、鳞片的细小明暗
        long hash = Seeds.derive(painter.seed, (long) ty * this.width + tx);
        double jitter = ((hash >>> 40) & 0xFF) / 255.0 - 0.5;
        color = Colors.adjust(color, 0, 1, 1 + jitter * 0.14);
        this.set(tx, ty, color);
        if (painter.glowing) {
            this.setGlow(tx, ty, color);
        }
    }

    /** 眼睛、嘴、鼻孔：画在头部的长方体上。 */
    private void paintFace(CreatureSpecies species, Part head) {
        Body body = species.body();
        int skullIndex = body.neckLength() > 0 ? 1 : 0;
        if (head.boxes().size() <= skullIndex) {
            return;
        }
        Box skull = head.boxes().get(skullIndex);
        Skin skin = species.skin();
        int w = skull.w();
        int h = skull.h();
        int d = skull.d();
        int frontU = skull.u() + d;
        int frontV = skull.v() + d;
        int eyeRow = Math.max(0, Math.min(h - 2, Math.round(h * 0.35f)));
        // 猎手和多眼的外星动物眼睛朝前；食草动物的眼睛长在头两侧
        boolean frontEyes = species.temperament().fights() || body.eyes() != 2 || species.plan() == BodyPlan.BIPED;
        if (frontEyes) {
            for (int[] spot : eyeSpots(body.eyes(), w, eyeRow)) {
                int ex = Math.max(0, Math.min(w - 1, spot[0]));
                int ey = Math.max(0, Math.min(h - 1, spot[1]));
                this.eye(frontU + ex, frontV + ey, skin);
            }
        } else {
            int row = frontV + eyeRow;
            this.eye(skull.u() + Math.max(0, d - 2), row, skin);
            this.eye(skull.u() + d + w + Math.min(1, d - 1), row, skin);
        }
        int mouth = Colors.adjust(skin.base(), 0, 1, 0.4);
        if (body.snoutLength() > 0 && head.boxes().size() > skullIndex + 1) {
            // 吻部：最下一行是嘴，顶上一行两个鼻孔
            Box snout = head.boxes().get(skullIndex + 1);
            int su = snout.u() + snout.d();
            int sv = snout.v() + snout.d();
            for (int i = 0; i < snout.w(); i++) {
                this.set(su + i, sv + snout.h() - 1, mouth);
            }
            if (snout.w() >= 3) {
                this.set(su, sv, mouth);
                this.set(su + snout.w() - 1, sv, mouth);
            }
            if (species.temperament() == Temperament.PREDATOR && snout.w() >= 3 && snout.h() >= 2) {
                this.set(su, sv + snout.h() - 2, TOOTH);
                this.set(su + snout.w() - 1, sv + snout.h() - 2, TOOTH);
            }
        } else if (h >= 3) {
            for (int i = 1; i < w - 1; i++) {
                this.set(frontU + i, frontV + h - 1, mouth);
            }
            if (species.temperament() == Temperament.PREDATOR && w >= 4) {
                this.set(frontU + 1, frontV + h - 1, TOOTH);
                this.set(frontU + w - 2, frontV + h - 1, TOOTH);
            }
        }
    }

    private static int[][] eyeSpots(int eyes, int w, int row) {
        int c = w / 2;
        int centre = c - (w + 1) % 2;
        int off = Math.max(1, w / 4);
        return switch (eyes) {
            case 1 -> new int[][] {{centre, row}};
            case 3 -> new int[][] {{c - off - 1, row + 1}, {centre, row}, {c + off, row + 1}};
            case 4 -> new int[][] {{c - off - 1, row}, {c + off, row}, {c - off, row + 1}, {c + off - 1, row + 1}};
            case 6 -> new int[][] {{c - off - 1, row}, {centre, row}, {c + off, row},
                {c - off - 1, row + 1}, {centre, row + 1}, {c + off, row + 1}};
            default -> new int[][] {{c - off - 1, row}, {c + off, row}};
        };
    }

    private void eye(int x, int y, Skin skin) {
        this.set(x, y, skin.eye());
        if (skin.glowingEyes()) {
            this.setGlow(x, y, skin.eye());
        }
    }

    private void set(int x, int y, int rgb) {
        if (x >= 0 && x < this.width && y >= 0 && y < this.height) {
            this.pixels[y * this.width + x] = 0xFF000000 | rgb;
        }
    }

    private void setGlow(int x, int y, int rgb) {
        if (x >= 0 && x < this.width && y >= 0 && y < this.height) {
            this.glow[y * this.width + x] = 0xFF000000 | rgb;
            this.glowing = true;
        }
    }

    private enum Face {
        TOP, BOTTOM, SIDE, FRONT, BACK
    }

    /** 按身体位置算颜色。 */
    private static final class Painter {
        private final Skin skin;
        private final CreatureAnatomy anatomy;
        private final SimplexNoise noise;
        private final long seed;
        private final float spine;
        private final float bandWidth;
        private final float bodyTop;
        private final float bodyBottom;
        private final float bodyFront;
        private final float bodyBack;
        /** 刚算出的颜色是不是发光的花纹。 */
        private boolean glowing;

        Painter(CreatureSpecies species, CreatureAnatomy anatomy) {
            this.skin = species.skin();
            this.anatomy = anatomy;
            this.seed = Seeds.derive(Seeds.derive(species.index(), species.name().en()), "skin");
            this.noise = new SimplexNoise(this.seed);
            Part body = anatomy.part("body") != null ? anatomy.part("body") : anatomy.part("segment0");
            float[] o = anatomy.origin(body);
            Box b = body.boxes().get(0);
            this.spine = o[0];
            this.bandWidth = Math.max(1.2f, b.w() * 0.22f);
            this.bodyTop = o[1] + b.y();
            this.bodyBottom = o[1] + b.y() + b.h();
            this.bodyFront = o[2] + b.z();
            this.bodyBack = species.plan() == BodyPlan.SERPENT ? this.bodyFront + b.d() * species.body().segments()
                    : this.bodyFront + b.d();
        }

        int color(Part part, Box box, float x, float y, float z, Face face) {
            this.glowing = false;
            float[] o = this.anatomy.origin(part);
            if (box.surface() == 1) {
                // 角、刺、骨板：骨白色，根部深一些
                double t = clamp((y - (o[1] + box.y())) / Math.max(1, box.h()));
                return Colors.adjust(Colors.lerp(this.skin.belly(), BONE, 0.6), 0, 1, 1.05 - 0.3 * t);
            }
            if (box.surface() == 2) {
                // 翼膜、鳍：主色和花纹色之间，带几道深色翼脉
                int membrane = Colors.adjust(Colors.lerp(this.skin.base(), this.skin.pattern(), 0.35), 0, 0.9, 1.12);
                boolean vein = part.role() == Role.WING ? Math.floorMod((int) Math.floor(x), 4) == 0
                        : Math.floorMod((int) Math.floor(z), 3) == 0;
                return vein ? Colors.adjust(membrane, 0, 1.1, 0.7) : membrane;
            }
            boolean trunk = part.role() == Role.BODY || part.role() == Role.SEGMENT;
            double mid = (this.bodyTop + this.bodyBottom) / 2;
            double halfHeight = Math.max(1, (this.bodyBottom - this.bodyTop) / 2);
            boolean belly = face == Face.BOTTOM && part.role() != Role.LEG
                    || face != Face.TOP && trunk && y > mid + halfHeight * 0.35;
            int color;
            if (belly) {
                color = this.skin.belly();
            } else if (this.pattern(part, face, x, y, z)) {
                color = this.skin.pattern();
                this.glowing = this.skin.glowingPattern();
            } else {
                color = this.skin.base();
            }
            if (part.role() == Role.LEG || part.role() == Role.ARM) {
                // 腿越往下越深，最下一行是蹄子或爪子
                double t = clamp((y - (o[1] + box.y())) / Math.max(1, box.h()));
                color = Colors.adjust(color, 0, 1, 1 - 0.3 * t);
                if (y > o[1] + box.y() + box.h() - 1) {
                    color = Colors.lerp(Colors.adjust(this.skin.base(), 0, 0.6, 0.35), BONE, 0.15);
                    this.glowing = false;
                }
            }
            return color;
        }

        private boolean pattern(Part part, Face face, float x, float y, float z) {
            double along = (z - this.bodyFront) / Math.max(1, this.bodyBack - this.bodyFront);
            boolean limb = part.role() == Role.LEG || part.role() == Role.ARM;
            return switch (this.skin.patternType()) {
                case NONE -> false;
                // 身上是横纹，腿上是环纹
                case STRIPES -> part.role() != Role.HEAD && (limb
                        ? Math.sin(y * 1.3) > 0.55
                        : Math.sin(z * 0.9 + this.noise.noise(x * 0.15, y * 0.15) * 1.5) > 0.45);
                case SPOTS -> this.noise.noise(x * 0.45, y * 0.45, z * 0.45) > 0.42;
                case DORSAL_BAND -> face == Face.TOP && !limb && Math.abs(x - this.spine) < this.bandWidth;
                case SADDLE -> part.role() == Role.BODY && along > 0.3 && along < 0.72 && y < this.bodyTop + 2.5;
                case GRADIENT -> !limb && y < this.bodyTop + (this.bodyBottom - this.bodyTop) * 0.4
                        + this.noise.noise(x * 0.3, z * 0.3) * 1.2;
            };
        }

        private static double clamp(double t) {
            return Math.max(0, Math.min(1, t));
        }
    }
}
