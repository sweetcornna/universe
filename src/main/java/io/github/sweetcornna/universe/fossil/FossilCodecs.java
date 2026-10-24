package io.github.sweetcornna.universe.fossil;

import com.mojang.serialization.Codec;

import io.github.sweetcornna.universe.core.fossil.Skeleton;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** 骨架（ADR-0034 §三）存档和同步的写法：五个类群的序号，空的写 -1。展台的方块实体和化石展品的数据组件都用它。 */
public final class FossilCodecs {
    public static final Codec<Skeleton> SKELETON = Codec.INT.listOf().xmap(Skeleton::decode, Skeleton::codes);
    public static final StreamCodec<ByteBuf, Skeleton> SKELETON_STREAM = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(8))
            .map(Skeleton::decode, Skeleton::codes);

    private FossilCodecs() {
    }
}
