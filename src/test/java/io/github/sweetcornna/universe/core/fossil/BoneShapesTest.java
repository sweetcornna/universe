package io.github.sweetcornna.universe.core.fossil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.fossil.BoneShapes.Piece;
import io.github.sweetcornna.universe.core.fossil.BoneShapes.V;

/** 骨头的立体模型和拼起来的骨架（ADR-0034 §三）。 */
class BoneShapesTest {
    /** 接点离骨头表面最多多远（像素）：巨蠕的环是空心的，接点在环的中轴上，离环壁两格多。 */
    private static final float GAP = 3.2F;

    @Test
    void everyPieceHasBoxesThatFitTheSharedTexture() {
        for (BoneFamily family : BoneFamily.values()) {
            for (Piece piece : Piece.values()) {
                List<BoneBox> boxes = BoneShapes.boxes(family, piece);
                assertFalse(boxes.isEmpty(), family + " " + piece);
                for (BoneBox box : boxes) {
                    assertTrue(box.w() > 0.2F && box.h() > 0.2F && box.d() > 0.2F, family + " " + piece + " 太薄：" + box);
                    assertTrue(box.fitsTexture(), family + " " + piece + " 贴图跑出了自己那一条：" + box);
                    for (float[] corner : box.corners()) {
                        for (float c : corner) {
                            assertTrue(Float.isFinite(c) && Math.abs(c) < 40, family + " " + piece + " 跑得太远：" + box);
                        }
                    }
                }
            }
        }
    }

    @Test
    void skullsHaveEyeSocketsOrAMouthAndChainsReachTheirEnds() {
        for (BoneFamily family : BoneFamily.values()) {
            // 头骨上有窟窿（眼眶、喉咙）
            assertTrue(BoneShapes.skull(family).stream().anyMatch(b -> b.material() == BoneBox.Material.CAVITY), family.id());
            // 头骨往 -z 长，尾巴往 +z 长
            assertTrue(minZ(BoneShapes.skull(family)) < -3, family.id());
            assertTrue(maxZ(BoneShapes.tail(family).boxes()) > 4, family.id());
            // 脖子、尾巴的末端挨着它最后一块骨头：头骨接上去不悬空
            for (BoneShapes.Chain chain : List.of(BoneShapes.neck(family), BoneShapes.tail(family))) {
                assertTrue(nearest(chain.boxes(), chain.end()) < GAP, family + " 末端离骨头太远");
                assertTrue(nearest(chain.boxes(), V.ZERO) < GAP, family + " 根部离原点太远");
            }
            BoneShapes.Body body = BoneShapes.ribcage(family);
            for (V socket : List.of(body.neck(), body.tail(), body.frontLimb(), body.rearLimb())) {
                assertTrue(nearest(body.boxes(), socket) < GAP, family + " 胸廓的接点悬空：" + socket);
            }
        }
    }

    @Test
    void limbsHangDownAndMirrorToTheLeft() {
        for (BoneFamily family : BoneFamily.values()) {
            BoneShapes.Limbs limbs = BoneShapes.limbs(family);
            assertTrue(minY(limbs.rear()) < -3, family + " 后肢不往下长");
            List<BoneBox> right = BoneShapes.boxes(family, Piece.FRONT_RIGHT);
            List<BoneBox> left = BoneShapes.boxes(family, Piece.FRONT_LEFT);
            assertEquals(right.size(), left.size());
            for (int i = 0; i < right.size(); i++) {
                float[][] r = right.get(i).corners();
                float[][] l = left.get(i).corners();
                // 镜像的方块：每个角 x 取反以后能在另一边找到
                for (float[] corner : r) {
                    boolean found = false;
                    for (float[] other : l) {
                        found |= Math.abs(other[0] + corner[0]) < 1e-3 && Math.abs(other[1] - corner[1]) < 1e-3 && Math.abs(other[2] - corner[2]) < 1e-3;
                    }
                    assertTrue(found, family + " 左边不是右边的镜像");
                }
            }
        }
    }

    @Test
    void anyMixStandsOnTheGroundAndConnects() {
        BoneFamily[] families = BoneFamily.values();
        for (BoneFamily body : families) {
            for (BoneFamily other : families) {
                for (BonePart part : BonePart.values()) {
                    Skeleton skeleton = Skeleton.pure(body).with(part, other);
                    SkeletonPose pose = SkeletonPose.of(skeleton);
                    SkeletonPose.Bounds full = pose.full();
                    assertTrue(full.width() > 4 && full.height() > 4 && full.depth() > 10, skeleton + " 太小：" + full);
                    assertTrue(full.depth() < 140 && full.height() < 90, skeleton + " 太大：" + full);
                    assertEquals(BonePart.values().length + 3, pose.placements().size());
                    float[] offset = pose.offset();
                    assertEquals(0, pose.bounds().minY() + offset[1], 1e-3);
                    // 头骨接在脖子末端
                    BoneFamily neck = skeleton.family(BonePart.NECK);
                    BoneFamily ribs = skeleton.family(BonePart.RIBCAGE);
                    V expected = BoneShapes.ribcage(ribs).neck().scale(ribs.size()).add(BoneShapes.neck(neck).end().scale(neck.size()));
                    V head = pose.headSocket();
                    assertEquals(expected.x(), head.x(), 1e-4);
                    assertEquals(expected.y(), head.y(), 1e-4);
                    assertEquals(expected.z(), head.z(), 1e-4);
                    float fit = pose.fit(30, 27, 30, 1);
                    assertTrue(fit > 0.2F && fit <= 1, skeleton + " 缩放 " + fit);
                }
            }
        }
    }

    @Test
    void walkingLegsHoldTheBodyUp() {
        // 长腿的类群撑得起任何身子；甲壳兽的腿短，只撑得起自己的
        List<Skeleton> standing = new ArrayList<>();
        for (BoneFamily legs : List.of(BoneFamily.TITAN, BoneFamily.PREDATOR, BoneFamily.LONGNECK, BoneFamily.EXOTIC)) {
            for (BoneFamily body : BoneFamily.values()) {
                standing.add(Skeleton.pure(body).with(BonePart.LIMBS, legs));
            }
        }
        standing.add(Skeleton.pure(BoneFamily.ARTHROPOD));
        for (Skeleton skeleton : standing) {
            SkeletonPose pose = SkeletonPose.of(skeleton);
            float bodyBottom = Float.MAX_VALUE;
            for (SkeletonPose.Placement placement : pose.placements()) {
                if (placement.piece() == Piece.RIBCAGE) {
                    for (BoneBox box : BoneShapes.boxes(placement.family(), placement.piece())) {
                        for (float[] corner : box.corners()) {
                            bodyBottom = Math.min(bodyBottom, placement.apply(corner)[1]);
                        }
                    }
                }
            }
            assertTrue(bodyBottom - pose.bounds().minY() > 2, skeleton + " 的腿撑不起身子");
        }
    }

    @Test
    void partialSkeletonsKeepTheirSizeAndPlace() {
        Skeleton full = Skeleton.pure(BoneFamily.LONGNECK);
        Skeleton skull = Skeleton.EMPTY.with(BonePart.SKULL, BoneFamily.LONGNECK);
        SkeletonPose a = SkeletonPose.of(full);
        SkeletonPose b = SkeletonPose.of(skull);
        assertEquals(1, b.placements().size());
        assertEquals(a.fit(30, 27, 30, 1), b.fit(30, 27, 30, 1), 1e-5);
        assertEquals(a.full().centerZ(), b.full().centerZ(), 1e-4);
        SkeletonPose single = SkeletonPose.single(new Bone(BoneFamily.ARTHROPOD, BonePart.LIMBS));
        assertEquals(2, single.placements().size());
        assertTrue(SkeletonPose.of(Skeleton.EMPTY).placements().isEmpty());
    }

    /** 一点到这组方块表面最近的距离（点在方块里面算 0）。 */
    private static float nearest(List<BoneBox> boxes, V point) {
        float best = Float.MAX_VALUE;
        for (BoneBox box : boxes) {
            float[] p = unrotate(new float[] {point.x() - box.px(), point.y() - box.py(), point.z() - box.pz()}, box);
            float dx = Math.max(Math.max(box.x() - p[0], 0), p[0] - (box.x() + box.w()));
            float dy = Math.max(Math.max(box.y() - p[1], 0), p[1] - (box.y() + box.h()));
            float dz = Math.max(Math.max(box.z() - p[2], 0), p[2] - (box.z() + box.d()));
            best = Math.min(best, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
        return best;
    }

    /** {@link BoneBox#rotate} 的反过程：先绕 z、再绕 y、再绕 x 转回去。 */
    private static float[] unrotate(float[] p, BoneBox box) {
        double c = Math.cos(-box.rz());
        double s = Math.sin(-box.rz());
        double x1 = p[0] * c - p[1] * s;
        double y1 = p[0] * s + p[1] * c;
        double z1 = p[2];
        c = Math.cos(-box.ry());
        s = Math.sin(-box.ry());
        double x2 = x1 * c + z1 * s;
        double z2 = -x1 * s + z1 * c;
        c = Math.cos(-box.rx());
        s = Math.sin(-box.rx());
        double y3 = y1 * c - z2 * s;
        double z3 = y1 * s + z2 * c;
        return new float[] {(float) x2, (float) y3, (float) z3};
    }

    private static float minZ(List<BoneBox> boxes) {
        float min = Float.MAX_VALUE;
        for (BoneBox box : boxes) {
            for (float[] c : box.corners()) {
                min = Math.min(min, c[2]);
            }
        }
        return min;
    }

    private static float maxZ(List<BoneBox> boxes) {
        float max = -Float.MAX_VALUE;
        for (BoneBox box : boxes) {
            for (float[] c : box.corners()) {
                max = Math.max(max, c[2]);
            }
        }
        return max;
    }

    private static float minY(List<BoneBox> boxes) {
        float min = Float.MAX_VALUE;
        for (BoneBox box : boxes) {
            for (float[] c : box.corners()) {
                min = Math.min(min, c[1]);
            }
        }
        return min;
    }
}
