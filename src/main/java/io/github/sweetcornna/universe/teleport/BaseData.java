package io.github.sweetcornna.universe.teleport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.teleport.TeleportRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 玩家的基地存在全局存档 {@code data/universe/bases.dat}（ADR-0017）：放下基地信标就登记一处，拆掉就删掉。
 * 名字留空时显示"星球名 · 基地 N"，N 是这个主人在这颗星球上没用过的最小编号。
 */
public final class BaseData extends SavedData {
    /** 存档格式版本。改格式时加一，并在读取时迁移旧格式（技术架构 §3.10）。 */
    private static final int FORMAT = 1;

    /**
     * 一处基地。
     *
     * @param name   玩家起的名字；空串表示用默认名
     * @param number 默认名里的编号
     */
    public record Base(UUID id, UUID owner, ResourceKey<Level> dimension, BlockPos pos, String name, int number, long created) {
        static final Codec<Base> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Base::id),
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Base::owner),
                ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Base::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(Base::pos),
                Codec.STRING.optionalFieldOf("name", "").forGetter(Base::name),
                Codec.INT.fieldOf("number").forGetter(Base::number),
                Codec.LONG.optionalFieldOf("created", 0L).forGetter(Base::created)
        ).apply(i, Base::new));

        Base withName(String name) {
            return new Base(this.id, this.owner, this.dimension, this.pos, name, this.number, this.created);
        }
    }

    private static final Codec<BaseData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("format").forGetter(data -> Optional.of(FORMAT)),
            Base.CODEC.listOf().fieldOf("bases").forGetter(data -> List.copyOf(data.bases.values()))
    ).apply(i, (format, bases) -> {
        BaseData data = new BaseData();
        bases.forEach(base -> data.bases.put(base.id(), base));
        return data;
    }));

    public static final SavedDataType<BaseData> TYPE = new SavedDataType<>(Universe.id("bases"), BaseData::new, CODEC);

    private final Map<UUID, Base> bases = new LinkedHashMap<>();

    private BaseData() {
    }

    public static BaseData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /** 在这里登记一处新基地；同一个位置已经有了就换成新主人。 */
    public Base register(UUID owner, ResourceKey<Level> dimension, BlockPos pos, long now) {
        this.remove(dimension, pos);
        List<Integer> used = this.bases.values().stream()
                .filter(base -> base.owner().equals(owner) && base.dimension().equals(dimension))
                .map(Base::number).toList();
        Base base = new Base(UUID.randomUUID(), owner, dimension, pos.immutable(), "", TeleportRules.nextBaseNumber(used), now);
        this.bases.put(base.id(), base);
        this.setDirty();
        return base;
    }

    public Optional<Base> at(ResourceKey<Level> dimension, BlockPos pos) {
        return this.bases.values().stream().filter(base -> base.dimension().equals(dimension) && base.pos().equals(pos)).findFirst();
    }

    public Optional<Base> get(UUID id) {
        return Optional.ofNullable(this.bases.get(id));
    }

    public void remove(ResourceKey<Level> dimension, BlockPos pos) {
        if (this.bases.values().removeIf(base -> base.dimension().equals(dimension) && base.pos().equals(pos))) {
            this.setDirty();
        }
    }

    public void remove(UUID id) {
        if (this.bases.remove(id) != null) {
            this.setDirty();
        }
    }

    public void rename(UUID id, String name) {
        Base base = this.bases.get(id);
        if (base != null) {
            this.bases.put(id, base.withName(name));
            this.setDirty();
        }
    }

    /** 一个主人的全部基地，按登记先后。 */
    public List<Base> of(UUID owner) {
        List<Base> out = new ArrayList<>();
        for (Base base : this.bases.values()) {
            if (base.owner().equals(owner)) {
                out.add(base);
            }
        }
        out.sort(Comparator.comparingLong(Base::created));
        return out;
    }
}
