package io.github.sweetcornna.universe.exocraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 载具名册（ADR-0026），存在 {@code data/universe/vehicle_roster.dat}：每个人放在世界里的载具在哪个维度、在哪儿、是哪种。
 * 召唤台按它列出你的车、找到车再叫过来。载具每隔几秒自己来更新一次位置；收起来、被毁了就划掉（{@link UniverseVehicle#remove}）。
 * 车在没加载的区块里时，名册上是它最后一次登记的位置，召唤台先把那里的区块加载起来再找。
 */
public final class VehicleRoster extends SavedData {
    /** 存档格式版本。改格式时加一，并在读取时迁移旧格式（技术架构 §3.10）。 */
    private static final int FORMAT = 1;

    /** 名册上的一辆车。{@code name} 是玩家起的名字，没起是空的。 */
    public record Entry(String kind, String dimension, BlockPos pos, String name) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("kind").forGetter(Entry::kind),
                Codec.STRING.fieldOf("dimension").forGetter(Entry::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
                Codec.STRING.optionalFieldOf("name", "").forGetter(Entry::name)
        ).apply(i, Entry::new));
    }

    private static final Codec<Map<String, Map<String, Entry>>> OWNERS = Codec.unboundedMap(Codec.STRING,
            Codec.unboundedMap(Codec.STRING, Entry.CODEC));

    public static final Codec<VehicleRoster> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("format").forGetter(data -> Optional.of(FORMAT)),
            OWNERS.optionalFieldOf("owners", Map.of()).forGetter(data -> data.owners)
    ).apply(i, (format, owners) -> new VehicleRoster(owners)));

    public static final SavedDataType<VehicleRoster> TYPE = new SavedDataType<>(Universe.id("vehicle_roster"), VehicleRoster::new, CODEC);

    /** 主人的 UUID → 载具的 UUID → 登记。 */
    private final Map<String, Map<String, Entry>> owners = new HashMap<>();

    private VehicleRoster() {
    }

    private VehicleRoster(Map<String, Map<String, Entry>> owners) {
        owners.forEach((owner, vehicles) -> this.owners.put(owner, new HashMap<>(vehicles)));
    }

    public static VehicleRoster get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /** 登记或更新一辆车的位置；和上次一样就不动存档。 */
    public void update(UUID owner, UniverseVehicle vehicle) {
        Component custom = vehicle.getCustomName();
        Entry entry = new Entry(vehicle.kind().id(), vehicle.level().dimension().identifier().toString(), vehicle.blockPosition(),
                custom == null ? "" : custom.getString());
        Entry old = this.owners.computeIfAbsent(owner.toString(), key -> new HashMap<>()).put(vehicle.getUUID().toString(), entry);
        if (!entry.equals(old)) {
            this.setDirty();
        }
    }

    public void remove(UUID owner, UUID vehicle) {
        Map<String, Entry> vehicles = this.owners.get(owner.toString());
        if (vehicles != null && vehicles.remove(vehicle.toString()) != null) {
            if (vehicles.isEmpty()) {
                this.owners.remove(owner.toString());
            }
            this.setDirty();
        }
    }

    public @Nullable Entry entry(UUID owner, UUID vehicle) {
        Map<String, Entry> vehicles = this.owners.get(owner.toString());
        return vehicles == null ? null : vehicles.get(vehicle.toString());
    }

    /** 这个人的车：载具的 UUID 和登记。 */
    public List<Map.Entry<UUID, Entry>> vehicles(UUID owner) {
        List<Map.Entry<UUID, Entry>> list = new ArrayList<>();
        Map<String, Entry> vehicles = this.owners.get(owner.toString());
        if (vehicles != null) {
            vehicles.forEach((id, entry) -> list.add(Map.entry(UUID.fromString(id), entry)));
        }
        return list;
    }
}
