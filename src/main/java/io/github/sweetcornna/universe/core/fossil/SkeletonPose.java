package io.github.sweetcornna.universe.core.fossil;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.core.fossil.BoneShapes.Piece;
import io.github.sweetcornna.universe.core.fossil.BoneShapes.V;

/**
 * 一副骨架摆出来的样子（ADR-0034 §三）：每一块放在哪、抬多少、按哪个类群的大小缩放。渲染器照这个画，单位是像素。
 *
 * <p>位置都从胸廓的接点算：脖子接在胸廓前面，头骨接在脖子末端，尾巴接在后面，四肢接在肩和胯下面。缺了的部位按胸廓的类群
 * （胸廓也缺就按别的部位里第一个类群）算位置，但是不画，所以先放上去的骨头一开始就在它最后的位置上。
 * 摆好以后整副骨架往上提，画出来的最低点落在原点的高度上（四肢踩在展台上）；前后左右按完整的骨架居中。
 */
public final class SkeletonPose {
    /** 头骨在脖子末端的仰角只跟一部分：长颈兽脖子抬得再高，脑袋也是平着的。 */
    static final float HEAD_FOLLOW = 0.3F;

    /** 一块骨头：在骨架坐标系里的位置、绕 x 轴抬的角度、缩放。 */
    public record Placement(Piece piece, BoneFamily family, float x, float y, float z, float pitch, float scale) {
        /** 部件坐标系里的一点变到骨架坐标系。 */
        float[] apply(float[] p) {
            float[] r = BoneBox.rotate(p[0] * this.scale, p[1] * this.scale, p[2] * this.scale, this.pitch, 0, 0);
            return new float[] {r[0] + this.x, r[1] + this.y, r[2] + this.z};
        }
    }

    /** 一个范围（像素）。 */
    public record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        public float width() {
            return this.maxX - this.minX;
        }

        public float height() {
            return this.maxY - this.minY;
        }

        public float depth() {
            return this.maxZ - this.minZ;
        }

        public float centerX() {
            return (this.minX + this.maxX) / 2;
        }

        public float centerZ() {
            return (this.minZ + this.maxZ) / 2;
        }
    }

    private final List<Placement> shown;
    private final Bounds full;
    private final Bounds bounds;

    private SkeletonPose(List<Placement> shown, Bounds full, Bounds bounds) {
        this.shown = shown;
        this.full = full;
        this.bounds = bounds;
    }

    /** 骨架展台上的骨架。 */
    public static SkeletonPose of(Skeleton skeleton) {
        BoneFamily fallback = skeleton.family(BonePart.RIBCAGE);
        if (fallback == null) {
            for (Bone bone : skeleton.bones()) {
                fallback = bone.family();
                break;
            }
        }
        if (fallback == null) {
            fallback = BoneFamily.TITAN;
        }
        List<Placement> all = new ArrayList<>();
        List<Placement> shown = new ArrayList<>();
        BoneFamily bodyFamily = or(skeleton.family(BonePart.RIBCAGE), fallback);
        BoneShapes.Body body = BoneShapes.ribcage(bodyFamily);
        float sb = bodyFamily.size();
        add(all, shown, skeleton.has(BonePart.RIBCAGE), new Placement(Piece.RIBCAGE, bodyFamily, 0, 0, 0, 0, sb));

        BoneFamily neckFamily = or(skeleton.family(BonePart.NECK), fallback);
        BoneShapes.Chain neck = BoneShapes.neck(neckFamily);
        V neckBase = body.neck().scale(sb);
        add(all, shown, skeleton.has(BonePart.NECK), new Placement(Piece.NECK, neckFamily, neckBase.x(), neckBase.y(), neckBase.z(), 0,
                neckFamily.size()));
        V headBase = neckBase.add(neck.end().scale(neckFamily.size()));
        BoneFamily skullFamily = or(skeleton.family(BonePart.SKULL), fallback);
        add(all, shown, skeleton.has(BonePart.SKULL), new Placement(Piece.SKULL, skullFamily, headBase.x(), headBase.y(), headBase.z(),
                neck.endPitch() * HEAD_FOLLOW, skullFamily.size()));

        BoneFamily tailFamily = or(skeleton.family(BonePart.TAIL), fallback);
        V tailBase = body.tail().scale(sb);
        add(all, shown, skeleton.has(BonePart.TAIL), new Placement(Piece.TAIL, tailFamily, tailBase.x(), tailBase.y(), tailBase.z(), 0,
                tailFamily.size()));

        BoneFamily limbFamily = or(skeleton.family(BonePart.LIMBS), fallback);
        boolean limbs = skeleton.has(BonePart.LIMBS);
        float sl = limbFamily.size();
        V front = body.frontLimb().scale(sb);
        V rear = body.rearLimb().scale(sb);
        add(all, shown, limbs, new Placement(Piece.FRONT_RIGHT, limbFamily, front.x(), front.y(), front.z(), 0, sl));
        add(all, shown, limbs, new Placement(Piece.FRONT_LEFT, limbFamily, -front.x(), front.y(), front.z(), 0, sl));
        add(all, shown, limbs, new Placement(Piece.REAR_RIGHT, limbFamily, rear.x(), rear.y(), rear.z(), 0, sl));
        add(all, shown, limbs, new Placement(Piece.REAR_LEFT, limbFamily, -rear.x(), rear.y(), rear.z(), 0, sl));
        Bounds full = bounds(all);
        return new SkeletonPose(List.copyOf(shown), full, shown.isEmpty() ? full : bounds(shown));
    }

    /** 化石底座上的一块骨头：四肢摆一条前肢、一条后肢，一前一后。 */
    public static SkeletonPose single(Bone bone) {
        BoneFamily family = bone.family();
        float s = family.size();
        List<Placement> placements = new ArrayList<>();
        switch (bone.part()) {
            case SKULL -> placements.add(new Placement(Piece.SKULL, family, 0, 0, 0, 0, s));
            case NECK -> placements.add(new Placement(Piece.NECK, family, 0, 0, 0, 0, s));
            case RIBCAGE -> placements.add(new Placement(Piece.RIBCAGE, family, 0, 0, 0, 0, s));
            case TAIL -> placements.add(new Placement(Piece.TAIL, family, 0, 0, 0, 0, s));
            case LIMBS -> {
                placements.add(new Placement(Piece.FRONT_RIGHT, family, 0, 0, -3 * s, 0, s));
                placements.add(new Placement(Piece.REAR_RIGHT, family, 0, 0, 3 * s, 0, s));
            }
        }
        Bounds bounds = bounds(placements);
        return new SkeletonPose(List.copyOf(placements), bounds, bounds);
    }

    private static BoneFamily or(BoneFamily family, BoneFamily fallback) {
        return family != null ? family : fallback;
    }

    private static void add(List<Placement> all, List<Placement> shown, boolean present, Placement placement) {
        all.add(placement);
        if (present) {
            shown.add(placement);
        }
    }

    /** 要画的几块，按部位的顺序。 */
    public List<Placement> placements() {
        return this.shown;
    }

    /** 完整的骨架（缺的按胸廓的类群补上）占多大。 */
    public Bounds full() {
        return this.full;
    }

    /** 要画的那几块占多大。 */
    public Bounds bounds() {
        return this.bounds;
    }

    /** 渲染前的平移：前后左右按完整的骨架居中，画出来的最低点落到 0。 */
    public float[] offset() {
        return new float[] {-this.full.centerX(), -this.bounds.minY(), -this.full.centerZ()};
    }

    /**
     * 塞进 {@code width}×{@code height}×{@code depth}（像素）的缩放，按完整的骨架算（放一块、放五块大小不变），
     * 最多放大到 {@code max} 倍。
     */
    public float fit(float width, float height, float depth, float max) {
        float k = max;
        k = Math.min(k, width / Math.max(1e-3F, this.full.width()));
        k = Math.min(k, height / Math.max(1e-3F, this.full.height()));
        k = Math.min(k, depth / Math.max(1e-3F, this.full.depth()));
        return k;
    }

    private static Bounds bounds(List<Placement> placements) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        for (Placement placement : placements) {
            for (BoneBox box : BoneShapes.boxes(placement.family(), placement.piece())) {
                for (float[] corner : box.corners()) {
                    float[] p = placement.apply(corner);
                    minX = Math.min(minX, p[0]);
                    minY = Math.min(minY, p[1]);
                    minZ = Math.min(minZ, p[2]);
                    maxX = Math.max(maxX, p[0]);
                    maxY = Math.max(maxY, p[1]);
                    maxZ = Math.max(maxZ, p[2]);
                }
            }
        }
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** 头骨接在哪（骨架坐标系，像素），测试用。 */
    V headSocket() {
        for (Placement placement : this.shown) {
            if (placement.piece() == Piece.SKULL) {
                return new V(placement.x(), placement.y(), placement.z());
            }
        }
        return null;
    }
}
