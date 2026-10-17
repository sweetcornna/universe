package io.github.sweetcornna.universe.world.planet.gen;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.core.galaxy.Landform;
import io.github.sweetcornna.universe.core.galaxy.Liquid;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.galaxy.TerrainProfile;
import io.github.sweetcornna.universe.core.terrain.BiomeLayout;
import io.github.sweetcornna.universe.core.terrain.TerrainModel;

/**
 * 生成一颗星球的地形需要的全部参数，从 {@link Planet} 里摘出来。
 *
 * <p>自定义的密度函数和群系源靠它序列化：原版要求这些对象都有编解码器，
 * 把参数直接写进去，解码时不需要再去生成整个星团。
 */
public record PlanetGenSpec(TerrainProfile terrain, PlanetType type, Liquid liquid, double temperature, double flora,
        double trees, long seed) {
    private static final Codec<Landform.Feature> LANDFORM_CODEC = RecordCodecBuilder.create(i -> i.group(
            enumCodec(Landform.class).fieldOf("landform").forGetter(Landform.Feature::landform),
            Codec.DOUBLE.fieldOf("strength").forGetter(Landform.Feature::strength)
    ).apply(i, Landform.Feature::new));
    private static final Codec<TerrainProfile> TERRAIN_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("sea_level").forGetter(TerrainProfile::seaLevel),
            Codec.INT.fieldOf("base_height").forGetter(TerrainProfile::baseHeight),
            Codec.DOUBLE.fieldOf("continent_scale").forGetter(TerrainProfile::continentScale),
            Codec.DOUBLE.fieldOf("height_variation").forGetter(TerrainProfile::heightVariation),
            Codec.DOUBLE.fieldOf("hilliness").forGetter(TerrainProfile::hilliness),
            Codec.DOUBLE.fieldOf("roughness").forGetter(TerrainProfile::roughness),
            Codec.DOUBLE.fieldOf("ridges").forGetter(TerrainProfile::ridges),
            Codec.DOUBLE.fieldOf("terraces").forGetter(TerrainProfile::terraces),
            Codec.DOUBLE.fieldOf("craters").forGetter(TerrainProfile::craters),
            Codec.DOUBLE.fieldOf("caves").forGetter(TerrainProfile::caves),
            Codec.DOUBLE.fieldOf("overhangs").forGetter(TerrainProfile::overhangs),
            Codec.DOUBLE.fieldOf("spikes").forGetter(TerrainProfile::spikes),
            Codec.BOOL.fieldOf("floating_islands").forGetter(TerrainProfile::floatingIslands),
            LANDFORM_CODEC.listOf().fieldOf("landforms").forGetter(TerrainProfile::landforms)
    ).apply(i, TerrainProfile::new));

    public static final Codec<PlanetGenSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
            TERRAIN_CODEC.fieldOf("terrain").forGetter(PlanetGenSpec::terrain),
            enumCodec(PlanetType.class).fieldOf("type").forGetter(PlanetGenSpec::type),
            enumCodec(Liquid.class).fieldOf("liquid").forGetter(PlanetGenSpec::liquid),
            Codec.DOUBLE.fieldOf("temperature").forGetter(PlanetGenSpec::temperature),
            Codec.DOUBLE.fieldOf("flora").forGetter(PlanetGenSpec::flora),
            Codec.DOUBLE.fieldOf("trees").forGetter(PlanetGenSpec::trees),
            Codec.LONG.fieldOf("seed").forGetter(PlanetGenSpec::seed)
    ).apply(i, PlanetGenSpec::new));

    /** 同一颗星球的密度函数和群系源共用一个地形模型，它们的列缓存才能互相命中。 */
    private static final Map<PlanetGenSpec, TerrainModel> MODELS = new ConcurrentHashMap<>();

    public static PlanetGenSpec of(Planet planet) {
        return new PlanetGenSpec(planet.terrain(), planet.type(), planet.liquid(), planet.temperature(),
                planet.ecology().flora(), planet.ecology().trees(), planet.seed());
    }

    public TerrainModel model() {
        return MODELS.computeIfAbsent(this, spec -> new TerrainModel(spec.terrain, spec.liquid, spec.seed));
    }

    public BiomeLayout layout() {
        return new BiomeLayout(this.type, this.liquid, this.temperature, this.flora, this.trees);
    }

    private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
        return Codec.STRING.xmap(name -> Enum.valueOf(type, name), Enum::name);
    }
}
