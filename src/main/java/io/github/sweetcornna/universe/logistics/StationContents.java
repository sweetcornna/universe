package io.github.sweetcornna.universe.logistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.core.logistics.Logic;
import io.github.sweetcornna.universe.core.logistics.Station;
import io.github.sweetcornna.universe.core.logistics.StationSlot;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 拆下来的物流站带着的东西（ADR-0015）：各格的物品、数量、上限和供需设置，载具、翘曲器、电量和航程，还有调度的设置。
 * 像潜影盒一样存在掉落的物品里，放下时原样恢复。在外面飞的载具算回来了。
 *
 * <p>{@code owner} 是原来的主人：玩家放下时主人换成放的人，没有玩家放的（比如别的模组的机器）才用它。
 */
public record StationContents(List<Slot> slots, int drones, int vessels, int warpers, long energy, int range, Settings settings,
        Optional<UUID> owner) {
    /** 调度的设置（ADR-0033 §三）：名字、分组、指定配对、运输船最小载货量、点对点的伙伴。编号不带，放下时重新编。 */
    public record Settings(String name, int group, boolean isolated, int minLoad, List<UUID> partners) {
        public static final Settings DEFAULT = new Settings("", 0, false, 0, List.of());
        static final Codec<Settings> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("name", "").forGetter(Settings::name),
                Codec.INT.optionalFieldOf("group", 0).forGetter(Settings::group),
                Codec.BOOL.optionalFieldOf("isolated", false).forGetter(Settings::isolated),
                Codec.INT.optionalFieldOf("min_load", 0).forGetter(Settings::minLoad),
                UUIDUtil.CODEC.listOf().optionalFieldOf("partners", List.of()).forGetter(Settings::partners)
        ).apply(i, Settings::new));
        static final StreamCodec<ByteBuf, Settings> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Settings::name,
                ByteBufCodecs.VAR_INT, Settings::group,
                ByteBufCodecs.BOOL, Settings::isolated,
                ByteBufCodecs.VAR_INT, Settings::minLoad,
                UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list(Station.MAX_PARTNERS)), Settings::partners,
                Settings::new);

        public Settings {
            partners = List.copyOf(partners);
        }

        static Settings of(Station station) {
            return new Settings(station.name(), station.group(), station.isolated(), station.minLoad(), List.copyOf(station.partners()));
        }

        boolean isDefault() {
            return this.equals(DEFAULT);
        }
    }

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
            Codec.INT.optionalFieldOf("range", Station.DEFAULT_RANGE).forGetter(StationContents::range),
            Settings.CODEC.optionalFieldOf("settings", Settings.DEFAULT).forGetter(StationContents::settings),
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(StationContents::owner)
    ).apply(i, StationContents::new));
    public static final StreamCodec<ByteBuf, StationContents> STREAM_CODEC = StreamCodec.composite(
            Slot.STREAM_CODEC.apply(ByteBufCodecs.list(8)), StationContents::slots,
            ByteBufCodecs.VAR_INT, StationContents::drones,
            ByteBufCodecs.VAR_INT, StationContents::vessels,
            ByteBufCodecs.VAR_INT, StationContents::warpers,
            ByteBufCodecs.VAR_LONG, StationContents::energy,
            ByteBufCodecs.VAR_INT, StationContents::range,
            Settings.STREAM_CODEC, StationContents::settings,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), StationContents::owner,
            StationContents::new);

    public StationContents {
        slots = List.copyOf(slots);
    }

    public static StationContents of(Station station) {
        List<Slot> slots = new ArrayList<>();
        for (StationSlot slot : station.slots()) {
            slots.add(new Slot(slot.item(), slot.count(), slot.max(), slot.local().ordinal(), slot.remote().ordinal(), slot.points()));
        }
        return new StationContents(slots, station.drones(), station.vessels(), station.warpers(), station.energy(), station.droneRange(),
                Settings.of(station), station.owner());
    }

    /** 只留每格的物品、上限、供需、无人机范围和分组这些设置（蓝图用，ADR-0018）；名字、伙伴和主人不带。 */
    public StationContents settingsOnly() {
        List<Slot> settings = new ArrayList<>();
        for (Slot slot : this.slots) {
            settings.add(new Slot(slot.item, 0, slot.max, slot.local, slot.remote, 0));
        }
        Settings dispatch = new Settings("", this.settings.group, this.settings.isolated, this.settings.minLoad, List.of());
        return new StationContents(settings, 0, 0, 0, 0, this.range, dispatch, Optional.empty());
    }

    /** 什么都没有（没设物品、没有载具），不用存进物品里。主人不算：玩家放下时换成放的人。 */
    public boolean isEmpty() {
        return this.drones == 0 && this.vessels == 0 && this.warpers == 0 && this.slots.stream().allMatch(slot -> slot.item.isEmpty())
                && this.settings.isDefault();
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
        station.setName(this.settings.name);
        station.setGroup(this.settings.group);
        station.setIsolated(this.settings.isolated);
        station.setMinLoad(this.settings.minLoad);
        this.settings.partners.forEach(station::addPartner);
        this.owner.ifPresent(station::setOwner);
    }
}
