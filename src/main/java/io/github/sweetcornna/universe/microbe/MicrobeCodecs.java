package io.github.sweetcornna.universe.microbe;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.core.ecology.ResourcePlant;
import io.github.sweetcornna.universe.core.microbe.EngineeredPlant;
import io.github.sweetcornna.universe.core.microbe.Gene;
import io.github.sweetcornna.universe.core.microbe.Metabolism;
import io.github.sweetcornna.universe.core.microbe.MicrobeStrain;
import io.github.sweetcornna.universe.core.microbe.Morphology;
import io.github.sweetcornna.universe.core.microbe.Organism;
import io.github.sweetcornna.universe.core.microbe.SampleSource;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 菌和改造植物在物品、方块实体里的存法（ADR-0036 §五）。菌整份存进去，不靠星球重新派生：
 * 以后星球微生物组的算法变了，手里的菌种也不变。枚举按名字存，只往后加（ADR-0035）。
 */
public final class MicrobeCodecs {
    public static final Codec<Gene> GENE = enumCodec(Gene.class);

    public static final Codec<MicrobeStrain> STRAIN = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("key").forGetter(MicrobeStrain::key),
            Codec.STRING.fieldOf("name_zh").forGetter(MicrobeStrain::nameZh),
            Codec.STRING.fieldOf("name_en").forGetter(MicrobeStrain::nameEn),
            enumCodec(Metabolism.class).fieldOf("metabolism").forGetter(MicrobeStrain::metabolism),
            enumCodec(Morphology.class).fieldOf("morphology").forGetter(MicrobeStrain::morphology),
            GENE.listOf().fieldOf("genes").forGetter(strain -> List.copyOf(strain.genes())),
            Codec.DOUBLE.fieldOf("min_k").forGetter(MicrobeStrain::minK),
            Codec.DOUBLE.fieldOf("opt_k").forGetter(MicrobeStrain::optK),
            Codec.DOUBLE.fieldOf("max_k").forGetter(MicrobeStrain::maxK),
            Codec.DOUBLE.fieldOf("doubling_hours").forGetter(MicrobeStrain::doublingHours),
            Codec.DOUBLE.optionalFieldOf("productivity", 1.0).forGetter(MicrobeStrain::productivity),
            enumCodec(SampleSource.class).listOf().optionalFieldOf("sources", List.of()).forGetter(strain -> List.copyOf(strain.sources()))
    ).apply(i, (key, nameZh, nameEn, metabolism, morphology, genes, minK, optK, maxK, doubling, productivity, sources) ->
            new MicrobeStrain(key, nameZh, nameEn, metabolism, morphology, set(genes, Gene.class), minK, optK, maxK, doubling, productivity,
                    set(sources, SampleSource.class))));

    public static final Codec<Organism> ORGANISM = RecordCodecBuilder.create(i -> i.group(
            STRAIN.fieldOf("strain").forGetter(Organism::strain),
            GENE.listOf().optionalFieldOf("added", List.of()).forGetter(Organism::added),
            Codec.DOUBLE.optionalFieldOf("domestication", 0.0).forGetter(Organism::domestication)
    ).apply(i, Organism::new));
    public static final StreamCodec<ByteBuf, Organism> ORGANISM_STREAM = ByteBufCodecs.fromCodec(ORGANISM);

    public static final Codec<EngineeredPlant> ENGINEERED_PLANT = RecordCodecBuilder.create(i -> i.group(
            enumCodec(ResourcePlant.class).fieldOf("plant").forGetter(EngineeredPlant::plant),
            GENE.listOf().optionalFieldOf("added", List.of()).forGetter(EngineeredPlant::added)
    ).apply(i, EngineeredPlant::new));
    public static final StreamCodec<ByteBuf, EngineeredPlant> ENGINEERED_PLANT_STREAM = ByteBufCodecs.fromCodec(ENGINEERED_PLANT);

    private MicrobeCodecs() {
    }

    private static <E extends Enum<E>> Set<E> set(List<E> values, Class<E> type) {
        EnumSet<E> set = EnumSet.noneOf(type);
        set.addAll(values);
        return set;
    }

    private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
        return Codec.STRING.comapFlatMap(name -> {
            try {
                return DataResult.success(Enum.valueOf(type, name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                return DataResult.error(() -> "Unknown " + type.getSimpleName() + ": " + name);
            }
        }, value -> value.name().toLowerCase(Locale.ROOT));
    }
}
