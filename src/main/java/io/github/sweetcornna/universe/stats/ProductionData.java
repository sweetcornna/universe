package io.github.sweetcornna.universe.stats;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.LongStream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.stats.ProductionStats;
import io.github.sweetcornna.universe.core.stats.ProductionStats.Ledger;
import io.github.sweetcornna.universe.core.stats.ProductionStats.Series;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 生产统计存在全局存档 {@code data/universe/production.dat}（ADR-0015），保留最近一小时。核心层的类不能依赖
 * 序列化库，这里用记录类转一道。
 */
public final class ProductionData extends SavedData {
    /** 存档格式版本。改格式时加一，并在读取时迁移旧格式（技术架构 §3.10）。 */
    private static final int FORMAT = 1;

    private static final Codec<long[]> LONGS = Codec.LONG_STREAM.xmap(LongStream::toArray, Arrays::stream);

    private static final Codec<Series> SERIES_CODEC = RecordCodecBuilder.create(i -> i.group(
            LONGS.fieldOf("short_stamps").forGetter(Series::shortStamps),
            LONGS.fieldOf("short").forGetter(Series::shortValues),
            LONGS.fieldOf("long_stamps").forGetter(Series::longStamps),
            LONGS.fieldOf("long").forGetter(Series::longValues)
    ).apply(i, Series::restore));

    private static final Codec<Map<String, Series>> ITEMS_CODEC = Codec.unboundedMap(Codec.STRING, SERIES_CODEC);

    private static final Codec<Ledger> LEDGER_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("start").forGetter(Ledger::start),
            ITEMS_CODEC.optionalFieldOf("produced", Map.of()).forGetter(Ledger::produced),
            ITEMS_CODEC.optionalFieldOf("consumed", Map.of()).forGetter(Ledger::consumed),
            SERIES_CODEC.fieldOf("generation").forGetter(Ledger::generation),
            SERIES_CODEC.fieldOf("demand").forGetter(Ledger::demand),
            SERIES_CODEC.fieldOf("supplied").forGetter(Ledger::supplied),
            SERIES_CODEC.optionalFieldOf("charged").forGetter(ledger -> Optional.of(ledger.charged())),
            // M18 加的：星际物流的进出口、各种设备的发电和用电（0.14.0-alpha 的存档里没有）
            ITEMS_CODEC.optionalFieldOf("imports", Map.of()).forGetter(Ledger::imports),
            ITEMS_CODEC.optionalFieldOf("exports", Map.of()).forGetter(Ledger::exports),
            ITEMS_CODEC.optionalFieldOf("generators", Map.of()).forGetter(Ledger::generators),
            ITEMS_CODEC.optionalFieldOf("consumers", Map.of()).forGetter(Ledger::consumers)
    ).apply(i, (start, produced, consumed, generation, demand, supplied, charged, imports, exports, generators, consumers) -> {
        Ledger ledger = new Ledger(start, generation, demand, supplied, charged.orElseGet(Series::new));
        ledger.produced().putAll(produced);
        ledger.consumed().putAll(consumed);
        ledger.imports().putAll(imports);
        ledger.exports().putAll(exports);
        ledger.generators().putAll(generators);
        ledger.consumers().putAll(consumers);
        return ledger;
    }));

    public static final Codec<ProductionData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("format").forGetter(data -> Optional.of(FORMAT)),
            Codec.unboundedMap(Codec.STRING, LEDGER_CODEC).fieldOf("dimensions").forGetter(data -> new LinkedHashMap<>(data.stats.ledgers()))
    ).apply(i, (format, ledgers) -> {
        ProductionData data = new ProductionData();
        ledgers.forEach(data.stats::restore);
        return data;
    }));

    public static final SavedDataType<ProductionData> TYPE = new SavedDataType<>(Universe.id("production"), ProductionData::new, CODEC);

    private final ProductionStats stats = new ProductionStats();

    private ProductionData() {
    }

    public static ProductionData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public ProductionStats stats() {
        return this.stats;
    }
}
