package io.github.sweetcornna.universe.darkfog.shield;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.darkfog.ShieldMath;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;

/**
 * 行星护盾发生器（ADR-0028 §八），存在 {@code data/universe/planetary_shields.dat}：每个维度按放下的先后记着每台发生器的位置、
 * 存的能量、穹顶展开没有。能量以这里为准，方块实体每刻来这里充、维持；区块卸载了、星球维度没加载也查得到穹顶
 * （中继站落地前要问）。一个维度里排在前 {@value ShieldMath#MAX_PER_PLANET} 台的才工作。
 */
public final class ShieldData extends SavedData {
    /** 存档格式版本。改格式时加一，并在读取时迁移旧格式（技术架构 §3.10）。 */
    private static final int FORMAT = 1;

    /** 一台发生器。 */
    public static final class Generator {
        static final Codec<Generator> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("pos").forGetter(g -> g.pos.asLong()),
                Codec.DOUBLE.optionalFieldOf("energy", 0.0).forGetter(g -> g.energy),
                Codec.BOOL.optionalFieldOf("raised", false).forGetter(g -> g.raised)
        ).apply(i, (pos, energy, raised) -> new Generator(BlockPos.of(pos), energy, raised)));

        private final BlockPos pos;
        private double energy;
        private boolean raised;
        /** 排在前 {@value ShieldMath#MAX_PER_PLANET} 台里；不存档，增删时重算。 */
        private boolean working;

        Generator(BlockPos pos, double energy, boolean raised) {
            this.pos = pos.immutable();
            this.energy = Math.max(0, Math.min(ShieldMath.CAPACITY, energy));
            this.raised = raised && this.energy > 0;
        }

        public BlockPos pos() {
            return this.pos;
        }

        /** 穹顶的球心：发生器下半截的顶面中心。 */
        public Vec3 center() {
            return new Vec3(this.pos.getX() + 0.5, this.pos.getY() + 1, this.pos.getZ() + 0.5);
        }

        public double energy() {
            return this.energy;
        }

        public boolean raised() {
            return this.raised && this.working;
        }

        public boolean working() {
            return this.working;
        }

        public ShieldMath.State state() {
            return new ShieldMath.State(this.energy, this.raised);
        }

        /** 展开着时的半径；没展开是 0。 */
        public double radius() {
            return this.raised() ? ShieldMath.radius(this.energy) : 0;
        }
    }

    public static final Codec<ShieldData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("format").forGetter(data -> Optional.of(FORMAT)),
            Codec.unboundedMap(Codec.STRING, Generator.CODEC.listOf()).optionalFieldOf("dimensions", Map.of())
                    .forGetter(ShieldData::encode)
    ).apply(i, (format, dimensions) -> new ShieldData(dimensions)));

    public static final SavedDataType<ShieldData> TYPE = new SavedDataType<>(Universe.id("planetary_shields"), ShieldData::new, CODEC);

    /** 维度 → 位置 → 发生器，按放下的先后排。 */
    private final Map<String, LinkedHashMap<Long, Generator>> dimensions = new HashMap<>();

    private ShieldData() {
    }

    private ShieldData(Map<String, List<Generator>> dimensions) {
        dimensions.forEach((key, list) -> {
            LinkedHashMap<Long, Generator> map = new LinkedHashMap<>();
            list.forEach(generator -> map.put(generator.pos.asLong(), generator));
            this.dimensions.put(key, map);
            recount(map);
        });
    }

    private Map<String, List<Generator>> encode() {
        Map<String, List<Generator>> map = new HashMap<>();
        this.dimensions.forEach((key, generators) -> {
            if (!generators.isEmpty()) {
                map.put(key, List.copyOf(generators.values()));
            }
        });
        return map;
    }

    public static ShieldData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    private static String key(ResourceKey<Level> dimension) {
        return dimension.identifier().toString();
    }

    /** 排在前面的那些在工作。 */
    private static void recount(LinkedHashMap<Long, Generator> generators) {
        int index = 0;
        for (Generator generator : generators.values()) {
            generator.working = index++ < ShieldMath.MAX_PER_PLANET;
        }
    }

    /** 这个维度里所有的发生器，按放下的先后。 */
    public Collection<Generator> generators(ResourceKey<Level> dimension) {
        LinkedHashMap<Long, Generator> map = this.dimensions.get(key(dimension));
        return map == null ? List.of() : Collections.unmodifiableCollection(map.values());
    }

    /** 这个维度里展开着的穹顶。 */
    public List<Generator> raised(ResourceKey<Level> dimension) {
        List<Generator> list = new ArrayList<>();
        for (Generator generator : this.generators(dimension)) {
            if (generator.raised()) {
                list.add(generator);
            }
        }
        return list;
    }

    public Optional<Generator> find(ResourceKey<Level> dimension, BlockPos pos) {
        LinkedHashMap<Long, Generator> map = this.dimensions.get(key(dimension));
        return map == null ? Optional.empty() : Optional.ofNullable(map.get(pos.asLong()));
    }

    /** 发生器加载了：已经记着就接着用存的能量，没有就排到最后。 */
    public Generator register(ResourceKey<Level> dimension, BlockPos pos) {
        LinkedHashMap<Long, Generator> map = this.dimensions.computeIfAbsent(key(dimension), key -> new LinkedHashMap<>());
        Generator generator = map.get(pos.asLong());
        if (generator == null) {
            generator = new Generator(pos, 0, false);
            map.put(pos.asLong(), generator);
            recount(map);
            this.setDirty();
        }
        return generator;
    }

    /** 发生器拆掉了：后面排着的往前挪，可能有一台开始工作。 */
    public boolean remove(ResourceKey<Level> dimension, BlockPos pos) {
        LinkedHashMap<Long, Generator> map = this.dimensions.get(key(dimension));
        if (map == null || map.remove(pos.asLong()) == null) {
            return false;
        }
        recount(map);
        this.setDirty();
        return true;
    }

    /** 这个维度里在工作的有几台。 */
    public int workingCount(ResourceKey<Level> dimension) {
        return Math.min(this.generators(dimension).size(), ShieldMath.MAX_PER_PLANET);
    }

    /** 改一台的能量和展开状态。 */
    public void set(Generator generator, ShieldMath.State state) {
        if (generator.energy != state.energy() || generator.raised != state.raised()) {
            generator.energy = state.energy();
            generator.raised = state.raised();
            this.setDirty();
        }
    }

    /** 有发生器的维度。 */
    public List<String> dimensionKeys() {
        return List.copyOf(this.dimensions.keySet());
    }
}
