package io.github.sweetcornna.universe.core.fishing;

import org.jspecify.annotations.Nullable;

/**
 * 钓上来（或者捕鱼笼、鱼塘出）的一样东西（ADR-0034 §一）。
 *
 * @param species 是鱼时是哪种
 * @param weight  是鱼时多重（千克）
 * @param junk    是杂物时是哪样
 */
public record Catch(Kind kind, @Nullable FishSpecies species, double weight, @Nullable Junk junk) {
    public enum Kind {
        FISH,
        JUNK,
        /** 漂流瓶：打开后标出附近的一个兴趣点。 */
        BOTTLE,
        /** 密封补给舱：开出一份兴趣点的补给。 */
        CACHE,
        /** 技术模块：多功能工具或外骨骼的 C、B 档。 */
        MODULE
    }

    public static Catch fish(FishSpecies species, double weight) {
        return new Catch(Kind.FISH, species, weight, null);
    }

    public static Catch junk(Junk junk) {
        return new Catch(Kind.JUNK, null, 0, junk);
    }

    public static Catch of(Kind kind) {
        if (kind == Kind.FISH || kind == Kind.JUNK) {
            throw new IllegalArgumentException(kind + " needs a species or a junk kind");
        }
        return new Catch(kind, null, 0, null);
    }

    public boolean isFish() {
        return this.kind == Kind.FISH;
    }
}
