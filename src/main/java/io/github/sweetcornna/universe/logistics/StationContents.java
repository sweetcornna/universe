package io.github.sweetcornna.universe.logistics;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.core.logistics.Logic;
import io.github.sweetcornna.universe.core.logistics.Station;
import io.github.sweetcornna.universe.core.logistics.StationSlot;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 拆下来的物流站带着的东西（ADR-0015）：各格的物品、数量、上限和供需设置，载具、翘曲器、电量和航程。
 * 像潜影盒一样存在掉落的物品里，放下时原样恢复。在外面飞的载具算回来了。
 */
public record StationContents(List<Slot> slots, int drones, int vessels, int warpers, long energy, int range) {
    public record Slot(String item, int count, int max, int local, int remote, long points) {
        static final Codec<Slot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("item", "").forGetter(Slot::item),
                Codec.INT.optionalFieldOf("count", 0).forGetter(Slot::count),
                Codec.INT.optionalFieldOf("max", 0).forGetter(Slot::max),
                Codec.INT.optionalFieldOf("local", 0).forGetter(Slot::local),
                Codec.INT.optionalFieldOf("remote", 0).forGetter(Slot::remote),
                Codec.LONG.optionalFieldOf("points", 0L).forGetter(Slot::points)
        ).apply(i, Slot::new));
        static final StreamCodec<ByteBuf, Slot> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Slot::item,
                ByteBufCodecs.VAR_INT, Slot::count,
                ByteBufCodecs.VAR_INT, Slot::max,
                ByteBufCodecs.VAR_INT, Slot::local,
                ByteBufCodecs.VAR_INT, Slot::remote,
                ByteBufCodecs.VAR_LONG, Slot::points,
                Slot::new);
    }

    public static final Codec<StationContents> CODEC = RecordCodecBuilder.create(i -> i.group(
            Slot.CODEC.listOf().fieldOf("slots").forGetter(StationContents::slots),
            Codec.INT.optionalFieldOf("drones", 0).forGetter(StationContents::drones),
            Codec.INT.optionalFieldOf("vessels", 0).forGetter(StationContents::vessels),
            Codec.INT.optionalFieldOf("warpers", 0).forGetter(StationContents::warpers),
            Codec.LONG.optionalFieldOf("energy", 0L).forGetter(StationContents::energy),
            Codec.INT.optionalFieldOf("range", Station.DEFAULT_RANGE).forGetter(StationContents::range)
    ).apply(i, StationContents::new));
    public static final StreamCodec<ByteBuf, StationContents> STREAM_CODEC = StreamCodec.composite(
            Slot.STREAM_CODEC.apply(ByteBufCodecs.list(8)), StationContents::slots,
            ByteBufCodecs.VAR_INT, StationContents::drones,
            ByteBufCodecs.VAR_INT, StationContents::vessels,
            ByteBufCodecs.VAR_INT, StationContents::warpers,
            ByteBufCodecs.VAR_LONG, StationContents::energy,
            ByteBufCodecs.VAR_INT, StationContents::range,
            StationContents::new);

    public StationContents {
        slots = List.copyOf(slots);
    }

    public static StationContents of(Station station) {
        List<Slot> slots = new ArrayList<>();
        for (StationSlot slot : station.slots()) {
            slots.add(new Slot(slot.item(), slot.count(), slot.max(), slot.local().ordinal(), slot.remote().ordinal(), slot.points()));
        }
        return new StationContents(slots, station.drones(), station.vessels(), station.warpers(), station.energy(), station.droneRange());
    }

    /** 只留每格的物品、上限、供需和无人机范围（蓝图用，ADR-0018）。 */
    public StationContents settingsOnly() {
        List<Slot> settings = new ArrayList<>();
        for (Slot slot : this.slots) {
            settings.add(new Slot(slot.item, 0, slot.max, slot.local, slot.remote, 0));
        }
        return new StationContents(settings, 0, 0, 0, 0, this.range);
    }

    /** 什么都没有（没设物品、没有载具），不用存进物品里。 */
    public boolean isEmpty() {
        return this.drones == 0 && this.vessels == 0 && this.warpers == 0 && this.slots.stream().allMatch(slot -> slot.item.isEmpty());
    }

    /** 物品总数，提示里用。 */
    public int totalItems() {
        return this.slots.stream().mapToInt(Slot::count).sum();
    }

    /** 恢复到一座刚建好的站上。 */
    public void applyTo(Station station) {
        for (int k = 0; k < Math.min(this.slots.size(), station.kind().slots()); k++) {
            Slot slot = this.slots.get(k);
            Logic[] logics = Logic.values();
            station.restoreSlot(k, new StationSlot(slot.item, slot.count, slot.max, logics[Math.floorMod(slot.local, logics.length)],
                    logics[Math.floorMod(slot.remote, logics.length)], 0, 0, slot.points));
        }
        station.addDrones(this.drones);
        station.addVessels(this.vessels);
        station.setWarpers(this.warpers);
        station.setEnergy(this.energy);
        station.setDroneRange(this.range);
    }
}
