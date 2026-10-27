package io.github.sweetcornna.universe.microbe;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.microbe.Gene;
import io.github.sweetcornna.universe.core.microbe.SampleSource;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 一管样本（ADR-0036 §五）：在哪颗星球、从什么地方采的、采样处多少度。组织样本另外记着是什么生物、测得出哪几个基因。
 * 存在样本物品的数据组件 {@code universe:sample} 里。
 *
 * @param body        采样的天体（{@link BodyId#path()}）
 * @param source      来源
 * @param temperature 采样处的温度（K）
 * @param tissueGenes 组织样本测得出的基因；不是组织为空
 * @param nameZh      组织样本是什么生物；不是组织为空串
 * @param nameEn      同上，英文
 */
public record Sample(String body, SampleSource source, double temperature, List<Gene> tissueGenes, String nameZh, String nameEn) {
    private static final Codec<SampleSource> SOURCE = Codec.STRING.comapFlatMap(name -> {
        SampleSource source = SampleSource.byName(name.toUpperCase(Locale.ROOT));
        return source == null ? DataResult.error(() -> "Unknown sample source: " + name) : DataResult.success(source);
    }, source -> source.name().toLowerCase(Locale.ROOT));

    public static final Codec<Sample> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("body").forGetter(Sample::body),
            SOURCE.fieldOf("source").forGetter(Sample::source),
            Codec.DOUBLE.optionalFieldOf("temperature", 288.0).forGetter(Sample::temperature),
            MicrobeCodecs.GENE.listOf().optionalFieldOf("genes", List.of()).forGetter(Sample::tissueGenes),
            Codec.STRING.optionalFieldOf("name_zh", "").forGetter(Sample::nameZh),
            Codec.STRING.optionalFieldOf("name_en", "").forGetter(Sample::nameEn)
    ).apply(i, Sample::new));
    public static final StreamCodec<ByteBuf, Sample> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    public Sample {
        tissueGenes = List.copyOf(tissueGenes);
    }

    /** 土、水、岩石这些地方的样本。 */
    public static Sample of(BodyId body, SampleSource source, double temperature) {
        return new Sample(body.path(), source, temperature, List.of(), "", "");
    }

    /** 动物、鱼、植物的组织。 */
    public static Sample tissue(BodyId body, SampleSource source, double temperature, Collection<Gene> genes, String nameZh, String nameEn) {
        EnumSet<Gene> sorted = EnumSet.noneOf(Gene.class);
        sorted.addAll(genes);
        return new Sample(body.path(), source, temperature, List.copyOf(sorted), nameZh, nameEn);
    }

    /** 组织样本：不能培养，直接测序。 */
    public boolean tissue() {
        return this.source.tissue();
    }

    public Set<Gene> genes() {
        EnumSet<Gene> set = EnumSet.noneOf(Gene.class);
        set.addAll(this.tissueGenes);
        return set;
    }
}
