package io.github.sweetcornna.universe.core.ecology;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import io.github.sweetcornna.universe.core.galaxy.Ecology;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Palette;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;

/**
 * 陆地动物的指纹：一批星球、一批合成的生态上生成的陆地物种（全部字段、拼出的模型、画出的贴图、驯养属性）
 * 拼成文本再取 FNV-1a 64 位哈希。陆地物种属于存档格式（实体只存序号，驯服的动物、发现记录都靠它），
 * 加水生动物（ADR-0026）时要保证它一点不变，{@code FaunaTest} 用这里的值钉住。
 *
 * <p>只用 1.0 以来就有的公开方法，改动前的代码也能编译它来算基准值。
 */
final class LandFaunaFingerprint {
    private static final long FNV_OFFSET = 0xCBF29CE484222325L;
    private static final long FNV_PRIME = 0x100000001B3L;

    private LandFaunaFingerprint() {
    }

    static long compute() {
        long hash = FNV_OFFSET;
        Galaxy galaxy = GalaxyGenerator.generate(20261016L, 64);
        List<Planet> landable = galaxy.systems().stream().flatMap(s -> s.bodies().stream()).filter(Planet::landable).toList();
        for (Planet planet : landable) {
            hash = mix(hash, "planet " + planet.id());
            hash = species(hash, Fauna.of(planet).species());
        }
        List<Palette> palettes = landable.stream().map(Planet::palette).limit(12).collect(Collectors.toList());
        palettes.add(Palette.NEUTRAL);
        long seed = 77;
        for (PlanetType type : PlanetType.values()) {
            for (int i = 0; i < 12; i++) {
                seed = seed * 6364136223846793005L + 1442695040888963407L;
                Ecology ecology = new Ecology(0.5 + (i % 5) * 0.1, 0.3, (i % 7) * 0.15, 4, 1 + i % 9, seed);
                hash = mix(hash, "synthetic " + type + " " + i);
                hash = species(hash, Fauna.generate(ecology, type, palettes.get(i % palettes.size())).species());
            }
        }
        hash = mix(hash, "fallback");
        return species(hash, Fauna.FALLBACK.species());
    }

    private static long species(long hash, List<CreatureSpecies> list) {
        for (CreatureSpecies s : list) {
            // 生境是 Set.copyOf 出来的，遍历顺序每次启动 JVM 都可能不同：排好序再拼
            String biotopes = s.biotopes().stream().map(Enum::name).sorted().collect(Collectors.joining(","));
            hash = mix(hash, s.index() + "|" + s.name().en() + "|" + s.name().zh() + "|" + s.plan() + "|" + s.temperament() + "|" + s.size()
                    + "|" + s.body() + "|" + s.skin() + "|" + s.health() + "|" + s.speed() + "|" + s.attack() + "|" + s.groupMin() + "|"
                    + s.groupMax() + "|" + biotopes + "|" + s.abundance() + "|" + s.voice());
            CreatureAnatomy anatomy = CreatureAnatomy.of(s);
            hash = mix(hash, anatomy.textureWidth() + "x" + anatomy.textureHeight() + "|" + anatomy.hitWidth() + "x" + anatomy.hitHeight()
                    + "|" + anatomy.saddleHeight());
            for (CreatureAnatomy.Part part : anatomy.parts()) {
                hash = mix(hash, part.toString());
            }
            CreatureSkin skin = CreatureSkin.paint(s, anatomy);
            hash = mix(hash, skin.width() + "x" + skin.height() + "|" + Arrays.hashCode(skin.pixels()) + "|"
                    + (skin.hasGlow() ? Arrays.hashCode(skin.glow()) : 0));
            hash = mix(hash, Husbandry.diet(s.temperament()) + "|" + Husbandry.tameChance(s.temperament()) + "|" + Husbandry.product(s) + "|"
                    + Husbandry.productCount(s) + "|" + Husbandry.rideable(s) + "|" + Husbandry.rideSpeed(s) + "|" + Husbandry.rideJump(s));
        }
        return hash;
    }

    private static long mix(long hash, String text) {
        for (byte b : (text + "\n").getBytes(StandardCharsets.UTF_8)) {
            hash ^= b & 0xFF;
            hash *= FNV_PRIME;
        }
        return hash;
    }
}
