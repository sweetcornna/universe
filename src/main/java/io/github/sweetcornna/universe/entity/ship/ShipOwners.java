package io.github.sweetcornna.universe.entity.ship;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 每名玩家的主力船（ADR-0024）：最后驾驶的那艘自己的船在哪个维度、哪个位置，存在 {@code data/universe/ship_owners.dat}。
 * 召唤时按这里找船：船所在的区块没加载时先加载，再按编号找实体。停成方块的船记一笔"停了"，叫不来。
 */
public final class ShipOwners extends SavedData {
    /** 存档格式版本。改格式时加一，并在读取时迁移旧格式（技术架构 §3.10）。 */
    private static final int FORMAT = 1;
    /** 开着船时每隔这么多刻记一次位置。 */
    public static final int RECORD_INTERVAL = 20;

    /**
     * 一名玩家的主力船。
     *
     * @param ship   船的实体编号
     * @param parked 停成了方块
     */
    public record MainShip(UUID ship, Identifier dimension, double x, double y, double z, boolean parked) {
        static final Codec<MainShip> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("ship").forGetter(MainShip::ship),
                Identifier.CODEC.fieldOf("dimension").forGetter(MainShip::dimension),
                Codec.DOUBLE.fieldOf("x").forGetter(MainShip::x),
                Codec.DOUBLE.fieldOf("y").forGetter(MainShip::y),
                Codec.DOUBLE.fieldOf("z").forGetter(MainShip::z),
                Codec.BOOL.optionalFieldOf("parked", false).forGetter(MainShip::parked)
        ).apply(i, MainShip::new));
    }

    private static final Codec<ShipOwners> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("format").forGetter(data -> Optional.of(FORMAT)),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, MainShip.CODEC).optionalFieldOf("ships", Map.of()).forGetter(data -> data.ships)
    ).apply(i, (format, ships) -> new ShipOwners(ships)));

    public static final SavedDataType<ShipOwners> TYPE = new SavedDataType<>(Universe.id("ship_owners"), ShipOwners::new, CODEC);

    private final Map<UUID, MainShip> ships = new HashMap<>();

    private ShipOwners() {
    }

    private ShipOwners(Map<UUID, MainShip> ships) {
        this.ships.putAll(ships);
    }

    public static ShipOwners get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public Optional<MainShip> main(UUID player) {
        return Optional.ofNullable(this.ships.get(player));
    }

    /** 玩家开着自己的船：这艘就是主力船。 */
    public void piloted(UUID player, ShipEntity ship) {
        this.record(player, ship, false);
    }

    /** 主力船换了位置（召唤过来、跨了维度）。 */
    public void moved(ShipEntity ship) {
        UUID owner = ship.owner();
        MainShip main = owner == null ? null : this.ships.get(owner);
        // 跨维度时新实体从旧实体读档，编号不变
        if (main != null && !main.parked() && main.ship().equals(ship.getUUID())) {
            this.record(owner, ship, false);
        }
    }

    /** 船停成了方块：是谁的主力船就记一笔，叫不来了。 */
    public void parked(ShipEntity ship) {
        UUID owner = ship.owner();
        MainShip main = owner == null ? null : this.ships.get(owner);
        if (main != null && main.ship().equals(ship.getUUID())) {
            this.ships.put(owner, new MainShip(main.ship(), main.dimension(), main.x(), main.y(), main.z(), true));
            this.setDirty();
        }
    }

    /** 船没了（卖掉了）：谁的主力船是它，就不再有主力船。 */
    public void forget(UUID ship) {
        if (this.ships.values().removeIf(main -> main.ship().equals(ship))) {
            this.setDirty();
        }
    }

    private void record(UUID player, ShipEntity ship, boolean parked) {
        MainShip main = new MainShip(ship.getUUID(), ship.level().dimension().identifier(), ship.getX(), ship.getY(), ship.getZ(), parked);
        if (!main.equals(this.ships.get(player))) {
            this.ships.put(player, main);
            this.setDirty();
        }
    }
}
