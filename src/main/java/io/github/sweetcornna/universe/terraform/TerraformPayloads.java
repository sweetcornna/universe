package io.github.sweetcornna.universe.terraform;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.terraform.TerraformState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 星球改造的数据包（ADR-0036 §六）。 */
public final class TerraformPayloads {
    private TerraformPayloads() {
    }

    /** 一颗星球的改造状态，和存档里的一样，只是不带推进到的时间。 */
    public record Entry(String path, Map<String, Double> gases, double biomass, int mirrors, int shades, List<List<String>> pioneers,
            double irrigation, int announced) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Entry::path,
                ByteBufCodecs.<ByteBuf, String, Double, Map<String, Double>>map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.DOUBLE),
                Entry::gases,
                ByteBufCodecs.DOUBLE, Entry::biomass,
                ByteBufCodecs.VAR_INT, Entry::mirrors,
                ByteBufCodecs.VAR_INT, Entry::shades,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).apply(ByteBufCodecs.list()), Entry::pioneers,
                ByteBufCodecs.DOUBLE, Entry::irrigation,
                ByteBufCodecs.VAR_INT, Entry::announced,
                Entry::new);

        public static Entry of(String path, TerraformState state) {
            return new Entry(path, state.storedGases(), state.biomass(), state.mirrors(), state.shades(), state.storedPioneers(),
                    state.irrigation(), state.announced());
        }

        public TerraformState state() {
            return TerraformState.fromStored(this.gases, this.biomass, this.mirrors, this.shades, this.pioneers, this.irrigation, 0,
                    this.announced);
        }
    }

    /**
     * 服务端 → 客户端：几颗星球的改造状态。登录时发全部（{@code replace}，客户端先清空），之后哪颗变了发哪颗，
     * 每颗星球每 5 秒最多发一次。
     */
    public record Sync(boolean replace, List<Entry> entries) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(Universe.id("terraform_sync"));
        public static final StreamCodec<ByteBuf, Sync> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Sync::replace,
                Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), Sync::entries,
                Sync::new);

        @Override
        public Type<Sync> type() {
            return TYPE;
        }
    }
}
