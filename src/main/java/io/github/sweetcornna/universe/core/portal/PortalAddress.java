package io.github.sweetcornna.universe.core.portal;

import java.util.Optional;

import io.github.sweetcornna.universe.core.procgen.Seeds;

/**
 * 先驱者传送门的地址（ADR-0024）：{@link #LENGTH} 个符文，每个是 {@link #GLYPHS} 种里的一种，和星球一一对应。
 *
 * <p>星球的编号（恒星系、行星、卫星）拼成 16 位，再接 8 位校验，24 位整数按星团种子做一次可逆的乘加打乱，
 * 拆成 6 个 4 位的符文。不同的星团地址不同；随手拨出来的地址校验对不上的概率是 255/256。
 *
 * @param system 恒星系编号（0–255）
 * @param planet 行星序号（0–15）
 * @param moon   卫星序号，-1 表示行星本身（-1–14）
 */
public record PortalAddress(int system, int planet, int moon) {
    public static final int GLYPHS = 16;
    public static final int LENGTH = 6;
    private static final int BITS = 24;
    private static final int MASK = (1 << BITS) - 1;

    public PortalAddress {
        if (system < 0 || system > 255 || planet < 0 || planet > 15 || moon < -1 || moon > 14) {
            throw new IllegalArgumentException("out of range: " + system + "/" + planet + "/" + moon);
        }
    }

    /** 符文，从左到右。 */
    public int[] glyphs(long galaxySeed) {
        int index = this.system << 8 | this.planet << 4 | (this.moon + 1);
        int value = index << 8 | check(galaxySeed, index);
        int mixed = (int) ((value * multiplier(galaxySeed) + offset(galaxySeed)) & MASK);
        int[] glyphs = new int[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            glyphs[i] = mixed >>> (4 * (LENGTH - 1 - i)) & 0xF;
        }
        return glyphs;
    }

    /** 从符文还原；校验对不上返回空。 */
    public static Optional<PortalAddress> decode(long galaxySeed, int[] glyphs) {
        if (glyphs.length != LENGTH) {
            return Optional.empty();
        }
        long mixed = 0;
        for (int glyph : glyphs) {
            if (glyph < 0 || glyph >= GLYPHS) {
                return Optional.empty();
            }
            mixed = mixed << 4 | glyph;
        }
        int value = (int) (((mixed - offset(galaxySeed)) * inverse(multiplier(galaxySeed))) & MASK);
        int index = value >>> 8;
        if ((value & 0xFF) != check(galaxySeed, index)) {
            return Optional.empty();
        }
        return Optional.of(new PortalAddress(index >>> 8, index >>> 4 & 0xF, (index & 0xF) - 1));
    }

    private static int check(long galaxySeed, int index) {
        return (int) (Seeds.derive(Seeds.derive(galaxySeed, "portal_check"), index) & 0xFF);
    }

    /** 奇数乘子：在 2^24 的模下可逆。 */
    private static long multiplier(long galaxySeed) {
        return (Seeds.derive(galaxySeed, "portal_mul") & MASK) | 1;
    }

    private static long offset(long galaxySeed) {
        return Seeds.derive(galaxySeed, "portal_add") & MASK;
    }

    /** 奇数在 2^24 的模下的逆元（牛顿迭代，每轮精度翻倍）。 */
    private static long inverse(long a) {
        long x = a;
        for (int i = 0; i < 5; i++) {
            x = x * (2 - a * x) & MASK;
        }
        return x & MASK;
    }
}
