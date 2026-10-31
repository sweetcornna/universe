package io.github.sweetcornna.universe.core.microbe;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.github.sweetcornna.universe.core.ecology.Biotope;
import io.github.sweetcornna.universe.core.galaxy.Atmosphere;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Hazard;
import io.github.sweetcornna.universe.core.galaxy.Liquid;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.galaxy.Resource;
import io.github.sweetcornna.universe.core.hazard.GeyserKind;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;

/**
 * 一颗星球上的微生物（ADR-0036 §五），由种子派生，不存盘。母星是固定的一组真实的菌（{@link EarthMicrobes}），
 * 气态巨行星没有。
 *
 * <p>每种来源单独一条随机流（{@code microbiome} 键下再按来源的名字派生），菌的键也按来源和序号起，
 * 以后改了某种来源的规则，别的来源的菌不变。有生命的星球每种来源一两种菌；没有生命的星球只有岩石内部和冰里有，
 * 各一半的机会有一种。垃圾里总是大阪堺菌，是人带过去的。
 *
 * <p>照来源和星球的条件挑：
 *
 * <ul>
 *   <li>热泉：嗜热菌，四成是超嗜热的；化能自养（硫）、产甲烷、异养；有毒星球的热泉是酸的，一半带质子泵（像硫化叶菌）。</li>
 *   <li>酸液：嗜酸菌，七成是化能自养。</li>
 *   <li>冰雪：嗜冷菌；有生命的星球上还有雪藻那样的光合菌。</li>
 *   <li>盐：嗜盐菌，三成是杜氏盐藻那样的光合菌。</li>
 *   <li>岩石内部：辐射星球上八成是耐辐射奇球菌那样的；大气稀薄、没有大气、荒凉的星球上是拟色球藻那样带紫外防护的岩石内生菌。</li>
 *   <li>沉积物：七成是产甲烷古菌。</li>
 *   <li>矿石：化能自养的铁硫氧化菌，七成带质子泵。</li>
 *   <li>土壤：固氮菌、异养菌，一成是土壤结皮里的蓝藻。</li>
 *   <li>水：六成是光合菌；海里的异养菌四成会发光。</li>
 * </ul>
 *
 * <p>星球本身的条件再加一层：有辐射的，地表来源的菌一半带 DNA 修复；有毒的，一部分带质子泵；大气稀薄或没有大气的，
 * 地表的一半带紫外防护；奇异星球上一半多带一个耐受基因，产率高一成半。
 *
 * @param strains 全部的菌，按来源的顺序
 */
public record Microbiome(List<MicrobeStrain> strains) {
    /** 派生种子用的键，已有的派生结果不受影响。 */
    public static final String SEED_KEY = "microbiome";
    public static final Microbiome EMPTY = new Microbiome(List.of());
    public static final Microbiome EARTH = new Microbiome(EarthMicrobes.ALL);
    /** 没有生命的星球上，岩石内部和冰里各有多大机会有一种菌。 */
    public static final double LIFELESS_CHANCE = 0.5;
    /** 冷过这个温度（K）的地表菌是嗜冷的。 */
    static final double COLD_K = 262;
    /** 热过这个温度（K）的地表菌是嗜热的（住在凉一些的地下）。 */
    static final double HOT_K = 325;

    public Microbiome {
        strains = List.copyOf(strains);
    }

    /** 这颗星球上的微生物。 */
    public static Microbiome of(Planet planet) {
        if (!planet.landable()) {
            return EMPTY;
        }
        if (planet.id().equals(GalaxyGenerator.HOME_PLANET_ID)) {
            return EARTH;
        }
        long seed = Seeds.derive(planet.seed(), SEED_KEY);
        boolean lifeless = planet.ecology().lifeless();
        List<MicrobeStrain> strains = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (SampleSource source : sources(planet)) {
            if (source == SampleSource.JUNK) {
                continue;
            }
            Rng rng = Rng.of(Seeds.derive(seed, source.name()));
            int count = lifeless ? (rng.chance(LIFELESS_CHANCE) ? 1 : 0) : rng.range(1, 2);
            for (int i = 0; i < count; i++) {
                strains.add(generate(planet, source, rng, i, names));
            }
        }
        strains.add(EarthMicrobes.IDEONELLA);
        return new Microbiome(strains);
    }

    /** 这颗星球上能采到菌的来源（不含组织），按枚举的顺序。 */
    public static List<SampleSource> sources(Planet planet) {
        List<SampleSource> sources = new ArrayList<>();
        if (!planet.landable()) {
            return sources;
        }
        if (planet.id().equals(GalaxyGenerator.HOME_PLANET_ID)) {
            for (SampleSource source : SampleSource.values()) {
                if (!EARTH.in(source).isEmpty()) {
                    sources.add(source);
                }
            }
            return sources;
        }
        PlanetType type = planet.type();
        boolean alive = !planet.ecology().lifeless();
        Liquid liquid = planet.liquid();
        List<Biotope> biotopes = Biotope.forPlanet(type);
        if (alive) {
            sources.add(SampleSource.SOIL);
        }
        if (alive && (liquid == Liquid.WATER || liquid == Liquid.ICE || type == PlanetType.OCEAN)) {
            sources.add(SampleSource.WATER);
        }
        if (alive && (hotSprings(type) || liquid == Liquid.LAVA)) {
            sources.add(SampleSource.HOT_SPRING);
        }
        if (alive && (liquid == Liquid.ACID || type == PlanetType.TOXIC)) {
            sources.add(SampleSource.ACID);
        }
        if (type == PlanetType.FROZEN || liquid == Liquid.ICE || planet.temperature() < 273 || alive && biotopes.contains(Biotope.COLD)) {
            sources.add(SampleSource.ICE);
        }
        if (alive && (biotopes.contains(Biotope.ARID) || planet.abundance(Resource.SALT) > 0)) {
            sources.add(SampleSource.SALT);
        }
        sources.add(SampleSource.ROCK);
        if (alive && (biotopes.contains(Biotope.WETLAND) || type == PlanetType.OCEAN)) {
            sources.add(SampleSource.SEDIMENT);
        }
        if (alive && planet.abundance(Resource.SULFUR) + planet.abundance(Resource.COPPER) + planet.abundance(Resource.IRON) > 0) {
            sources.add(SampleSource.ORE);
        }
        sources.add(SampleSource.JUNK);
        return sources;
    }

    /** 冒滚烫水汽的间歇泉：灼热、毒雾、奇异星球（冰冻星球的是冷的）。 */
    private static boolean hotSprings(PlanetType type) {
        return GeyserKind.of(type).filter(kind -> kind != GeyserKind.COLD).isPresent();
    }

    /** 从这种来源能采到的菌。 */
    public List<MicrobeStrain> in(SampleSource source) {
        List<MicrobeStrain> list = new ArrayList<>();
        for (MicrobeStrain strain : this.strains) {
            if (strain.sources().contains(source)) {
                list.add(strain);
            }
        }
        return list;
    }

    public Optional<MicrobeStrain> byKey(String key) {
        for (MicrobeStrain strain : this.strains) {
            if (strain.key().equals(key)) {
                return Optional.of(strain);
            }
        }
        return Optional.empty();
    }

    /** 耐温的类别。 */
    enum Thermal {
        PSYCHRO,
        MESO,
        THERMO,
        HYPER
    }

    private static MicrobeStrain generate(Planet planet, SampleSource source, Rng rng, int index, Set<String> taken) {
        PlanetType type = planet.type();
        boolean lifeless = planet.ecology().lifeless();
        boolean toxic = planet.hasHazard(Hazard.TOXIC) || planet.atmosphere().kind() == Atmosphere.Kind.TOXIC;
        boolean exposed = planet.atmosphere().kind() == Atmosphere.Kind.NONE || planet.atmosphere().kind() == Atmosphere.Kind.THIN;
        EnumSet<Gene> genes = EnumSet.noneOf(Gene.class);
        Thermal thermal = surface(planet);
        Metabolism metabolism;
        switch (source) {
            case HOT_SPRING -> {
                thermal = rng.chance(0.4) ? Thermal.HYPER : Thermal.THERMO;
                metabolism = pick(rng, Metabolism.CHEMOLITHOTROPH, 0.45, Metabolism.METHANOGEN, 0.25, Metabolism.HETEROTROPH, 0.3);
                if (toxic && rng.chance(0.5)) {
                    genes.add(Gene.PROTON_PUMP);
                }
            }
            case ACID -> {
                genes.add(Gene.PROTON_PUMP);
                metabolism = rng.chance(0.7) ? Metabolism.CHEMOLITHOTROPH : Metabolism.HETEROTROPH;
            }
            case ICE -> {
                thermal = Thermal.PSYCHRO;
                metabolism = lifeless
                        ? (rng.chance(0.6) ? Metabolism.HETEROTROPH : Metabolism.METHANOGEN)
                        : pick(rng, Metabolism.HETEROTROPH, 0.6, Metabolism.METHANOGEN, 0.25, Metabolism.OXYGENIC_PHOTOTROPH, 0.15);
            }
            case SALT -> {
                genes.add(Gene.COMPATIBLE_SOLUTES);
                metabolism = rng.chance(0.7) ? Metabolism.HETEROTROPH : Metabolism.OXYGENIC_PHOTOTROPH;
            }
            case ROCK -> {
                if (planet.hasHazard(Hazard.RADIATION) && rng.chance(0.8)) {
                    genes.add(Gene.DNA_REPAIR);
                    metabolism = Metabolism.HETEROTROPH;
                } else if (exposed || lifeless || harsh(type)) {
                    genes.add(Gene.UV_SHIELD);
                    genes.add(Gene.COMPATIBLE_SOLUTES);
                    metabolism = rng.chance(0.6) ? Metabolism.OXYGENIC_PHOTOTROPH : Metabolism.HETEROTROPH;
                } else {
                    metabolism = rng.chance(0.5) ? Metabolism.CHEMOLITHOTROPH : Metabolism.HETEROTROPH;
                }
            }
            case SEDIMENT -> metabolism = rng.chance(0.7) ? Metabolism.METHANOGEN : Metabolism.HETEROTROPH;
            case ORE -> {
                metabolism = Metabolism.CHEMOLITHOTROPH;
                if (rng.chance(0.7)) {
                    genes.add(Gene.PROTON_PUMP);
                }
            }
            case SOIL -> metabolism = pick(rng, Metabolism.DIAZOTROPH, 0.45, Metabolism.HETEROTROPH, 0.45, Metabolism.OXYGENIC_PHOTOTROPH, 0.1);
            case WATER -> {
                if (planet.liquid() == Liquid.ICE) {
                    thermal = Thermal.PSYCHRO;
                }
                metabolism = pick(rng, Metabolism.OXYGENIC_PHOTOTROPH, 0.6, Metabolism.HETEROTROPH, 0.3, Metabolism.DIAZOTROPH, 0.1);
                if (metabolism == Metabolism.HETEROTROPH && (type == PlanetType.OCEAN || planet.liquid() == Liquid.WATER) && rng.chance(0.4)) {
                    genes.add(Gene.LUCIFERASE);
                }
            }
            default -> throw new IllegalArgumentException("no strains live in " + source);
        }
        if (metabolism.signature() != null) {
            genes.add(metabolism.signature());
        }
        if (metabolism == Metabolism.OXYGENIC_PHOTOTROPH && rng.chance(0.5)) {
            genes.add(Gene.CARBON_CONCENTRATING);
        }
        boolean surfaceSource = source == SampleSource.SOIL || source == SampleSource.ROCK || source == SampleSource.SALT
                || source == SampleSource.WATER;
        if (surfaceSource && planet.hasHazard(Hazard.RADIATION) && rng.chance(0.5)) {
            genes.add(Gene.DNA_REPAIR);
        }
        if (toxic && (source == SampleSource.SOIL || source == SampleSource.WATER || source == SampleSource.SEDIMENT) && rng.chance(0.4)) {
            genes.add(Gene.PROTON_PUMP);
        }
        if (exposed && (source == SampleSource.SOIL || source == SampleSource.ROCK || source == SampleSource.SALT) && rng.chance(0.5)) {
            genes.add(Gene.UV_SHIELD);
        }
        double productivity = rng.range(0.8, 1.2);
        if (type == PlanetType.EXOTIC) {
            productivity *= 1.15;
            if (rng.chance(0.5)) {
                List<Gene> extra = new ArrayList<>();
                for (Gene gene : List.of(Gene.DNA_REPAIR, Gene.PROTON_PUMP, Gene.COMPATIBLE_SOLUTES, Gene.UV_SHIELD)) {
                    if (!genes.contains(gene)) {
                        extra.add(gene);
                    }
                }
                if (!extra.isEmpty()) {
                    genes.add(rng.pick(extra));
                }
            }
        }

        double opt;
        double min;
        double max;
        // 基数温度：最适温度离最高温度近、离最低温度远，照真实的生长曲线（也是 CTMI 的要求）
        switch (thermal) {
            case PSYCHRO -> {
                genes.add(Gene.ANTIFREEZE_PROTEIN);
                min = rng.range(MicrobeStrain.MIN_LIFE_K, 263.0);
                opt = rng.range(278.0, 290.0);
                max = opt + rng.range(5.0, 9.0);
            }
            case THERMO -> {
                genes.add(Gene.THERMOSTABLE_ENZYMES);
                opt = rng.range(330.0, 350.0);
                min = opt - rng.range(16.0, 22.0);
                max = opt + rng.range(6.0, 12.0);
            }
            case HYPER -> {
                genes.add(Gene.THERMOSTABLE_ENZYMES);
                opt = rng.range(360.0, 380.0);
                min = opt - rng.range(18.0, 25.0);
                max = Math.min(MicrobeStrain.MAX_LIFE_K, opt + rng.range(8.0, 15.0));
            }
            default -> {
                opt = Math.clamp(planet.temperature() + 10, 295.0, 315.0) + rng.range(-3.0, 3.0);
                min = opt - rng.range(18.0, 24.0);
                max = opt + rng.range(6.0, 12.0);
            }
        }

        double doubling = doublingHours(metabolism, thermal, genes, rng);

        Morphology forced = forcedMorphology(metabolism, thermal, genes, rng);
        Morphology morphology = forced != null ? forced : randomMorphology(metabolism, rng);
        StrainNames.Name name = StrainNames.name(metabolism, morphology, genes, planet.name().zh(), planet.name().en());
        for (int attempt = 0; forced == null && taken.contains(name.zh()) && attempt < 4; attempt++) {
            morphology = randomMorphology(metabolism, rng);
            name = StrainNames.name(metabolism, morphology, genes, planet.name().zh(), planet.name().en());
        }
        String zh = name.zh();
        String en = name.en();
        for (int n = 2; taken.contains(zh); n++) {
            zh = name.zh() + " " + n + " 号";
            en = name.en() + " " + n;
        }
        taken.add(zh);
        String key = planet.id().path() + "#" + source.id() + "/" + index;
        return new MicrobeStrain(key, zh, en, metabolism, morphology, genes, min, opt, max, doubling, productivity, Set.of(source));
    }

    /**
     * 最适条件下翻一倍要几小时（现实时间），照真实的同类菌：
     *
     * <ul>
     *   <li>异养菌 0.3–2 小时：需钠弧菌 10 分钟、大肠杆菌 20 分钟、枯草芽孢杆菌 30 分钟。耐辐射的照耐辐射奇球菌 1.5–2 小时，
     *       嗜盐的照盐沼盐杆菌 4–8 小时。</li>
     *   <li>固氮菌 2–4 小时（快生型根瘤菌、固氮菌属）；光合菌 2–8 小时（聚球藻），住在岩石里的照拟色球藻慢得多，10–30 小时。</li>
     *   <li>化能自养菌 6–12 小时（嗜酸氧化亚铁硫杆菌）；产甲烷古菌 8–20 小时（巴氏甲烷八叠球菌）。</li>
     *   <li>嗜冷菌在自己的最适温度下也慢两到四倍；嗜热菌快三成；超嗜热菌更快，激烈火球菌 37 分钟，
     *       超嗜热的产甲烷古菌（詹氏甲烷暖球菌 26 分钟）也比常温的快得多，取 0.5–2 小时。</li>
     * </ul>
     */
    static double doublingHours(Metabolism metabolism, Thermal thermal, Set<Gene> genes, Rng rng) {
        if (thermal == Thermal.HYPER && metabolism == Metabolism.METHANOGEN) {
            return rng.range(0.5, 2.0);
        }
        double base;
        if (metabolism == Metabolism.HETEROTROPH && genes.contains(Gene.DNA_REPAIR)) {
            base = rng.range(1.5, 2.0);
        } else if (metabolism == Metabolism.HETEROTROPH && genes.contains(Gene.COMPATIBLE_SOLUTES) && !genes.contains(Gene.UV_SHIELD)) {
            base = rng.range(4.0, 8.0);
        } else if (metabolism == Metabolism.OXYGENIC_PHOTOTROPH && genes.contains(Gene.UV_SHIELD)) {
            base = rng.range(10.0, 30.0);
        } else {
            base = switch (metabolism) {
                case HETEROTROPH -> rng.logRange(0.3, 2.0);
                case DIAZOTROPH -> rng.range(2.0, 4.0);
                case OXYGENIC_PHOTOTROPH -> rng.range(2.0, 8.0);
                case CHEMOLITHOTROPH -> rng.range(6.0, 12.0);
                case METHANOGEN -> rng.range(8.0, 20.0);
            };
        }
        return base * switch (thermal) {
            case PSYCHRO -> rng.range(2.0, 4.0);
            case MESO -> 1.0;
            case THERMO -> 0.7;
            case HYPER -> 0.6;
        };
    }

    /** 地表的菌按星球的平均温度定耐温的类别。 */
    static Thermal surface(Planet planet) {
        double t = planet.temperature();
        if (t < COLD_K || planet.hasHazard(Hazard.COLD)) {
            return Thermal.PSYCHRO;
        }
        if (t > HOT_K || planet.hasHazard(Hazard.HEAT)) {
            return Thermal.THERMO;
        }
        return Thermal.MESO;
    }

    private static boolean harsh(PlanetType type) {
        return type == PlanetType.BARREN || type == PlanetType.SCORCHED || type == PlanetType.IRRADIATED || type == PlanetType.FROZEN;
    }

    /** 由代谢和特点定死的形态；没有定死的为 {@code null}。 */
    private static Morphology forcedMorphology(Metabolism metabolism, Thermal thermal, Set<Gene> genes, Rng rng) {
        if (metabolism == Metabolism.METHANOGEN) {
            return Morphology.ARCHAEON;
        }
        if (metabolism == Metabolism.OXYGENIC_PHOTOTROPH) {
            return null;
        }
        if (thermal == Thermal.HYPER && rng.chance(0.7)) {
            return Morphology.ARCHAEON;
        }
        if (metabolism == Metabolism.HETEROTROPH && genes.contains(Gene.COMPATIBLE_SOLUTES) && !genes.contains(Gene.UV_SHIELD)
                && rng.chance(0.6)) {
            // 嗜盐古菌
            return Morphology.ARCHAEON;
        }
        if (metabolism == Metabolism.HETEROTROPH && genes.contains(Gene.DNA_REPAIR)) {
            // 奇球菌
            return Morphology.COCCUS;
        }
        return null;
    }

    private static Morphology randomMorphology(Metabolism metabolism, Rng rng) {
        if (metabolism == Metabolism.CHEMOLITHOTROPH) {
            return rng.chance(0.6) ? Morphology.BACILLUS : Morphology.SPIRILLUM;
        }
        return rng.pick(List.of(Morphology.COCCUS, Morphology.BACILLUS, Morphology.SPIRILLUM, Morphology.FILAMENT));
    }

    private static Metabolism pick(Rng rng, Metabolism a, double wa, Metabolism b, double wb, Metabolism c, double wc) {
        double roll = rng.nextDouble() * (wa + wb + wc);
        if (roll < wa) {
            return a;
        }
        return roll < wa + wb ? b : c;
    }
}
