package io.github.sweetcornna.universe.mecha;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.core.mecha.LogisticsSlot;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 机甲面板里玩家自己设的东西（ADR-0023）：物流栏位和自动补燃料。存档，死亡不丢，同步给本人画面板。
 */
public record MechaSettings(List<LogisticsSlot> slots, boolean autoRefuel) {
    /** 物流栏位的格数。 */
    public static final int SLOTS = 12;
    public static final MechaSettings DEFAULT = new MechaSettings(List.of(), true);

    private static final Codec<LogisticsSlot> SLOT_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("item", "").forGetter(LogisticsSlot::item),
            Codec.INT.optionalFieldOf("min", 0).forGetter(LogisticsSlot::min),
            Codec.INT.optionalFieldOf("max", 0).forGetter(LogisticsSlot::max)
    ).apply(i, LogisticsSlot::new));
    public static final MapCodec<MechaSettings> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            SLOT_CODEC.listOf().optionalFieldOf("logistics", List.of()).forGetter(MechaSettings::slots),
            Codec.BOOL.optionalFieldOf("auto_refuel", true).forGetter(MechaSettings::autoRefuel)
    ).apply(i, MechaSettings::new));
    private static final StreamCodec<ByteBuf, LogisticsSlot> SLOT_STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, LogisticsSlot::item,
            ByteBufCodecs.VAR_INT, LogisticsSlot::min,
            ByteBufCodecs.VAR_INT, LogisticsSlot::max,
            LogisticsSlot::new);
    public static final StreamCodec<ByteBuf, MechaSettings> STREAM_CODEC = StreamCodec.composite(
            SLOT_STREAM_CODEC.apply(ByteBufCodecs.list(SLOTS)), MechaSettings::slots,
            ByteBufCodecs.BOOL, MechaSettings::autoRefuel,
            MechaSettings::new);

    public MechaSettings {
        List<LogisticsSlot> padded = new ArrayList<>(SLOTS);
        for (int i = 0; i < SLOTS; i++) {
            padded.add(i < slots.size() ? slots.get(i) : LogisticsSlot.EMPTY);
        }
        slots = List.copyOf(padded);
    }

    public LogisticsSlot slot(int index) {
        return this.slots.get(index);
    }

    public MechaSettings withSlot(int index, LogisticsSlot slot) {
        List<LogisticsSlot> list = new ArrayList<>(this.slots);
        list.set(index, slot);
        return new MechaSettings(list, this.autoRefuel);
    }

    public MechaSettings withAutoRefuel(boolean autoRefuel) {
        return new MechaSettings(this.slots, autoRefuel);
    }
}
