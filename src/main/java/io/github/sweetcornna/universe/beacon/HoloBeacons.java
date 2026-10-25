package io.github.sweetcornna.universe.beacon;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.beacon.BeaconVisibility;
import io.github.sweetcornna.universe.dyson.Dyson;
import io.github.sweetcornna.universe.network.HoloBeaconPayloads;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 世界里所有的全息信标（ADR-0033 §六），存在 {@code data/universe/holo_beacons.dat}：区块没加载的时候 HUD 和星图也要知道它们在哪。
 * 信标放下、改设置、拆掉时更新，变了就发给所有玩家（一个世界里通常几个到几十个，整张表一起发）。一刻里改了好几回也只在这一刻末尾发一次，
 * 免得有人连着发改设置的包，每个包都让服务端把整张表发给所有人。
 */
public final class HoloBeacons extends SavedData {
    /** 一个信标：在哪、图标、颜色、字、能被看到的范围。 */
    public record Beacon(ResourceKey<Level> dimension, BlockPos pos, String icon, int color, String label, BeaconVisibility visibility) {
        static final Codec<Beacon> CODEC = RecordCodecBuilder.create(i -> i.group(
                Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Beacon::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(Beacon::pos),
                Codec.STRING.optionalFieldOf("icon", "").forGetter(Beacon::icon),
                Codec.INT.optionalFieldOf("color", 0).forGetter(Beacon::color),
                Codec.STRING.optionalFieldOf("label", "").forGetter(Beacon::label),
                Codec.STRING.xmap(BeaconVisibility::byKey, BeaconVisibility::key).optionalFieldOf("visibility", BeaconVisibility.PLANET)
                        .forGetter(Beacon::visibility)
        ).apply(i, Beacon::new));

        String id() {
            return key(this.dimension, this.pos);
        }
    }

    private static final Codec<HoloBeacons> CODEC = Beacon.CODEC.listOf().fieldOf("beacons").codec()
            .xmap(HoloBeacons::new, data -> List.copyOf(data.beacons.values()));
    public static final SavedDataType<HoloBeacons> TYPE = new SavedDataType<>(Universe.id("holo_beacons"), HoloBeacons::new, CODEC);

    private final Map<String, Beacon> beacons = new LinkedHashMap<>();
    /** 变了还没发给大家。 */
    private boolean unsent;

    private HoloBeacons() {
    }

    private HoloBeacons(List<Beacon> list) {
        list.forEach(beacon -> this.beacons.put(beacon.id(), beacon));
    }

    public static HoloBeacons get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                PacketDistributor.sendToPlayer(player, get(player.level().getServer()).payload());
            }
        });
        gameBus.addListener(ServerTickEvent.Post.class, event -> get(event.getServer()).flush(event.getServer()));
    }

    private static String key(ResourceKey<Level> dimension, BlockPos pos) {
        return dimension.identifier() + "|" + pos.asLong();
    }

    public List<Beacon> all() {
        return List.copyOf(this.beacons.values());
    }

    public java.util.Optional<Beacon> at(ResourceKey<Level> dimension, BlockPos pos) {
        return java.util.Optional.ofNullable(this.beacons.get(key(dimension, pos)));
    }

    /** 登记或更新一个信标；变了就在这一刻末尾发给所有人。 */
    public void put(MinecraftServer server, Beacon beacon) {
        Beacon old = this.beacons.put(beacon.id(), beacon);
        if (!beacon.equals(old)) {
            this.setDirty();
            this.unsent = true;
        }
    }

    public void remove(MinecraftServer server, ResourceKey<Level> dimension, BlockPos pos) {
        if (this.beacons.remove(key(dimension, pos)) != null) {
            this.setDirty();
            this.unsent = true;
        }
    }

    /** 还有没发出去的改动（游戏内测试看）。 */
    public boolean unsent() {
        return this.unsent;
    }

    /** 每刻末尾：这一刻变过就把整张表发给所有人，一刻最多一次。 */
    void flush(MinecraftServer server) {
        if (!this.unsent) {
            return;
        }
        this.unsent = false;
        if (server.getPlayerList() != null && !server.getPlayerList().getPlayers().isEmpty()) {
            PacketDistributor.sendToAllPlayers(this.payload());
        }
    }

    HoloBeaconPayloads.All payload() {
        List<HoloBeaconPayloads.Entry> entries = new ArrayList<>();
        for (Beacon beacon : this.beacons.values()) {
            if (entries.size() == HoloBeaconPayloads.All.MAX) {
                break;
            }
            OptionalInt system = Dyson.systemOf(beacon.dimension());
            entries.add(new HoloBeaconPayloads.Entry(beacon.dimension(), beacon.pos(), system.orElse(-1), beacon.icon(), beacon.color(),
                    beacon.label(), beacon.visibility()));
        }
        return new HoloBeaconPayloads.All(entries);
    }
}
