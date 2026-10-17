package io.github.sweetcornna.universe.core.procgen;

import java.nio.charset.StandardCharsets;

/**
 * 程序生成的种子派生。
 *
 * <p>星系、恒星系、行星、物种都从上一级种子派生，同一个世界种子总会得到同一个宇宙；
 * 服务端和客户端也能各自算出相同的结果，不必在网络上传输生成数据。
 *
 * <p>派生结果属于存档格式：改动这里的算法，已有存档里的宇宙会整体变样。
 */
public final class Seeds {
    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;
    private static final long FNV_OFFSET_BASIS = 0xCBF29CE484222325L;
    private static final long FNV_PRIME = 0x100000001B3L;

    private Seeds() {
    }

    /** 按名称派生子种子，例如 {@code derive(worldSeed, "galaxy")}。 */
    public static long derive(long parent, String key) {
        return mix(mix(parent) ^ fnv1a64(key));
    }

    /** 按序号派生子种子，例如某个恒星系里第 {@code index} 颗行星。 */
    public static long derive(long parent, long index) {
        return mix(mix(parent) ^ (GOLDEN_GAMMA * (index + 1)));
    }

    /** SplitMix64 的输出混合函数：是双射，输入改动一位，大约一半的输出位会跟着变。 */
    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static long fnv1a64(String key) {
        long hash = FNV_OFFSET_BASIS;
        for (byte b : key.getBytes(StandardCharsets.UTF_8)) {
            hash ^= b & 0xFF;
            hash *= FNV_PRIME;
        }
        return hash;
    }
}
