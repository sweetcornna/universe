package io.github.sweetcornna.universe.entity.ship;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 飞船交易终端今天卖掉了哪几艘（ADR-0024），存在 {@code data/universe/ship_dealer.dat}：每座站每天的货单按种子算，
 * 这里只记"第几个恒星系、第几天、第几艘"卖掉了，同一天别人再来就没有了。两天前的记录自动清掉。
 */
public final class ShipDealerData extends SavedData {
    /** 存档格式版本。改格式时加一，并在读取时迁移旧格式（技术架构 §3.10）。 */
    private static final int FORMAT = 1;

    private static final Codec<ShipDealerData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("format").forGetter(data -> Optional.of(FORMAT)),
            Codec.STRING.listOf().optionalFieldOf("sold", List.of()).forGetter(data -> List.copyOf(data.sold))
    ).apply(i, (format, sold) -> new ShipDealerData(sold)));

    public static final SavedDataType<ShipDealerData> TYPE = new SavedDataType<>(Universe.id("ship_dealer"), ShipDealerData::new, CODEC);

    private final Set<String> sold = new HashSet<>();

    private ShipDealerData() {
    }

    private ShipDealerData(List<String> sold) {
        this.sold.addAll(sold);
    }

    public static ShipDealerData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    private static String key(int system, long day, String what, int slot) {
        return system + "/" + day + "/" + what + "/" + slot;
    }

    public boolean sold(int system, long day, String what, int slot) {
        return this.sold.contains(key(system, day, what, slot));
    }

    public void markSold(int system, long day, String what, int slot) {
        this.sold.removeIf(key -> {
            String[] parts = key.split("/");
            return parts.length < 2 || Long.parseLong(parts[1]) < day - 1;
        });
        this.sold.add(key(system, day, what, slot));
        this.setDirty();
    }
}
