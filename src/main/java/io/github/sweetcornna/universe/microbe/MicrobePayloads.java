package io.github.sweetcornna.universe.microbe;

import java.util.List;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.microbe.GeneLibrary;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 微生物和基因的数据包（ADR-0036 §五）。 */
public final class MicrobePayloads {
    private MicrobePayloads() {
    }

    /** 服务端 → 客户端：整个基因库。登录时发一次，测出新东西时发给所有人。基因编辑台的界面、菌种管的名字读它。 */
    public record Library(List<String> genes, List<String> strains) implements CustomPacketPayload {
        public static final Type<Library> TYPE = new Type<>(Universe.id("gene_library"));
        public static final StreamCodec<ByteBuf, Library> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Library::genes,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Library::strains,
                Library::new);

        public static Library of(GeneLibrary library) {
            return new Library(library.geneNames(), List.copyOf(library.strains()));
        }

        public GeneLibrary library() {
            return GeneLibrary.load(this.genes, this.strains);
        }

        @Override
        public Type<Library> type() {
            return TYPE;
        }
    }
}
