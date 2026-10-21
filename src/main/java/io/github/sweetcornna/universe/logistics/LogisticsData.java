package io.github.sweetcornna.universe.logistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.logistics.Location;
import io.github.sweetcornna.universe.core.logistics.Logic;
import io.github.sweetcornna.universe.core.logistics.LogisticsNetwork;
import io.github.sweetcornna.universe.core.logistics.Station;
import io.github.sweetcornna.universe.core.logistics.StationKind;
import io.github.sweetcornna.universe.core.logistics.StationSlot;
import io.github.sweetcornna.universe.core.logistics.Trip;
import io.github.sweetcornna.universe.core.logistics.Vehicle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 物流站的账本和在途的运输，存在全局存档 {@code data/universe/logistics.dat}（ADR-0015）。核心层的类不能依赖
 * 序列化库，这里用记录类转一道。
 */
public final class LogisticsData extends SavedData {
    /** 存档格式版本。改格式时加一，并在读取时迁移旧格式（技术架构 §3.10）。 */
    private static final int FORMAT = 1;

    private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
        return Codec.STRING.xmap(name -> Enum.valueOf(type, name), Enum::name);
    }

    private static final Codec<StationSlot> SLOT_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("item", "").forGetter(StationSlot::item),
            Codec.INT.optionalFieldOf("count", 0).forGetter(StationSlot::count),
            Codec.INT.fieldOf("max").forGetter(StationSlot::max),
            enumCodec(Logic.class).optionalFieldOf("local", Logic.STORAGE).forGetter(StationSlot::local),
            enumCodec(Logic.class).optionalFieldOf("remote", Logic.STORAGE).forGetter(StationSlot::remote),
            Codec.INT.optionalFieldOf("incoming", 0).forGetter(StationSlot::incoming),
            Codec.INT.optionalFieldOf("outgoing", 0).forGetter(StationSlot::outgoing),
            Codec.LONG.optionalFieldOf("points", 0L).forGetter(StationSlot::points)
    ).apply(i, StationSlot::new));

    private static final Codec<Location> LOCATION_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("system").forGetter(Location::system),
            Codec.DOUBLE.fieldOf("au").forGetter(Location::au),
            Codec.DOUBLE.fieldOf("star_x").forGetter(Location::starX),
            Codec.DOUBLE.fieldOf("star_y").forGetter(Location::starY),
            Codec.DOUBLE.fieldOf("star_z").forGetter(Location::starZ),
            Codec.STRING.fieldOf("body").forGetter(Location::body)
    ).apply(i, Location::new));

    /** 载具的数量：总数、空闲的。 */
    private record Fleet(int drones, int idleDrones, int vessels, int idleVessels, int warpers) {
        static final Codec<Fleet> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("drones", 0).forGetter(Fleet::drones),
                Codec.INT.optionalFieldOf("idle_drones", 0).forGetter(Fleet::idleDrones),
                Codec.INT.optionalFieldOf("vessels", 0).forGetter(Fleet::vessels),
                Codec.INT.optionalFieldOf("idle_vessels", 0).forGetter(Fleet::idleVessels),
                Codec.INT.optionalFieldOf("warpers", 0).forGetter(Fleet::warpers)
        ).apply(i, Fleet::new));
    }

    private static final Codec<Station> STATION_CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Station::id),
            enumCodec(StationKind.class).fieldOf("kind").forGetter(Station::kind),
            Codec.STRING.fieldOf("dimension").forGetter(Station::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(station -> new BlockPos(station.x(), station.y(), station.z())),
            LOCATION_CODEC.fieldOf("location").forGetter(Station::location),
            SLOT_CODEC.listOf().fieldOf("slots").forGetter(Station::slots),
            Fleet.CODEC.fieldOf("fleet").forGetter(station -> new Fleet(station.drones(), station.idleDrones(), station.vessels(),
                    station.idleVessels(), station.warpers())),
            Codec.LONG.optionalFieldOf("energy", 0L).forGetter(Station::energy),
            Codec.INT.optionalFieldOf("range", Station.DEFAULT_RANGE).forGetter(Station::droneRange),
            Codec.DOUBLE.listOf().optionalFieldOf("production", List.of()).forGetter(station -> doubles(station, true)),
            Codec.DOUBLE.listOf().optionalFieldOf("accumulated", List.of()).forGetter(station -> doubles(station, false))
    ).apply(i, LogisticsData::station));

    private static List<Double> doubles(Station station, boolean production) {
        List<Double> list = new ArrayList<>();
        for (int k = 0; k < station.kind().slots(); k++) {
            list.add(production ? station.production(k) : station.accumulated(k));
        }
        return list;
    }

    private static Station station(UUID id, StationKind kind, String dimension, BlockPos pos, Location location, List<StationSlot> slots,
            Fleet fleet, long energy, int range, List<Double> production, List<Double> accumulated) {
        Station station = new Station(id, kind, dimension, pos.getX(), pos.getY(), pos.getZ(), location);
        for (int k = 0; k < Math.min(slots.size(), kind.slots()); k++) {
            station.restoreSlot(k, slots.get(k));
        }
        station.restoreVehicles(fleet.drones(), fleet.idleDrones(), fleet.vessels(), fleet.idleVessels());
        station.setWarpers(fleet.warpers());
        station.setEnergy(energy);
        station.setDroneRange(range);
        for (int k = 0; k < Math.min(production.size(), kind.slots()); k++) {
            station.setProduction(k, production.get(k));
        }
        for (int k = 0; k < Math.min(accumulated.size(), kind.slots()); k++) {
            station.setAccumulated(k, accumulated.get(k));
        }
        return station;
    }

    /** 行程的时刻和货物。 */
    private record Times(long departAt, long arriveAt, long returnAt) {
        static final Codec<Times> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("depart").forGetter(Times::departAt),
                Codec.LONG.fieldOf("arrive").forGetter(Times::arriveAt),
                Codec.LONG.fieldOf("return").forGetter(Times::returnAt)
        ).apply(i, Times::new));
    }

    private static final Codec<Trip> TRIP_CODEC = RecordCodecBuilder.create(i -> i.group(
            enumCodec(Vehicle.class).fieldOf("vehicle").forGetter(Trip::vehicle),
            UUIDUtil.CODEC.fieldOf("home").forGetter(Trip::home),
            UUIDUtil.CODEC.fieldOf("other").forGetter(Trip::other),
            Codec.STRING.fieldOf("item").forGetter(Trip::item),
            Codec.INT.fieldOf("home_slot").forGetter(Trip::homeSlot),
            Codec.INT.fieldOf("other_slot").forGetter(Trip::otherSlot),
            Codec.INT.fieldOf("amount").forGetter(Trip::amount),
            Codec.INT.fieldOf("cargo").forGetter(Trip::cargo),
            Codec.BOOL.fieldOf("delivering").forGetter(Trip::delivering),
            Times.CODEC.fieldOf("times").forGetter(trip -> new Times(trip.departAt(), trip.arriveAt(), trip.returnAt())),
            Codec.BOOL.fieldOf("returning").forGetter(Trip::returning),
            Codec.BOOL.optionalFieldOf("warp", false).forGetter(Trip::warp),
            Codec.LONG.optionalFieldOf("points", 0L).forGetter(Trip::points)
    ).apply(i, (vehicle, home, other, item, homeSlot, otherSlot, amount, cargo, delivering, times, returning, warp, points) ->
            new Trip(vehicle, home, other, item, homeSlot, otherSlot, amount, cargo, delivering, times.departAt(), times.arriveAt(),
                    times.returnAt(), returning, warp, points)));

    private static final Codec<LogisticsData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("format").forGetter(data -> Optional.of(FORMAT)),
            STATION_CODEC.listOf().fieldOf("stations").forGetter(data -> List.copyOf(data.network.stations())),
            TRIP_CODEC.listOf().fieldOf("trips").forGetter(data -> data.network.trips())
    ).apply(i, (format, stations, trips) -> {
        LogisticsData data = new LogisticsData();
        stations.forEach(data.network::add);
        trips.forEach(data.network::restoreTrip);
        return data;
    }));

    public static final SavedDataType<LogisticsData> TYPE = new SavedDataType<>(Universe.id("logistics"), LogisticsData::new, CODEC);

    private final LogisticsNetwork network = new LogisticsNetwork();

    private LogisticsData() {
    }

    public static LogisticsData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public LogisticsNetwork network() {
        return this.network;
    }
}
