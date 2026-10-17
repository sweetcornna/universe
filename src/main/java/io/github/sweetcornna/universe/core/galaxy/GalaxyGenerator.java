package io.github.sweetcornna.universe.core.galaxy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.sweetcornna.universe.core.galaxy.Atmosphere.Kind;
import io.github.sweetcornna.universe.core.galaxy.WeatherProfile.Precipitation;
import io.github.sweetcornna.universe.core.procgen.Colors;
import io.github.sweetcornna.universe.core.procgen.NameGenerator;
import io.github.sweetcornna.universe.core.procgen.NameGenerator.Name;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;

/**
 * 由世界种子生成整个星团（玩法设计 §1）。
 *
 * <p>种子派生：世界种子 → {@code "galaxy"} → 第 i 个恒星系 → 第 j 颗行星 → 第 k 颗卫星。
 * 母星系固定是 0 号，母星是其中的 1 号行星，也就是主世界。
 */
public final class GalaxyGenerator {
    public static final int DEFAULT_SYSTEM_COUNT = 64;
    public static final int MAX_SYSTEM_COUNT = 1024;
    public static final int HOME_SYSTEM = 0;
    public static final int HOME_PLANET = 1;
    public static final BodyId HOME_PLANET_ID = BodyId.planet(HOME_SYSTEM, HOME_PLANET);
    public static final BodyId HOME_MOON_ID = HOME_PLANET_ID.moon(0);

    /** 太阳半径换算成 AU。 */
    private static final double SOLAR_RADIUS_AU = 1 / 215.0;

    private static final int CACHE_SIZE = 4;
    private static final Map<Long, Galaxy> CACHE = new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Galaxy> eldest) {
            return this.size() > CACHE_SIZE;
        }
    };

    private GalaxyGenerator() {
    }

    /** 由世界种子取得星团，结果会缓存。线程安全。 */
    public static Galaxy get(long worldSeed, int systemCount) {
        return fromGalaxySeed(galaxySeed(worldSeed), systemCount);
    }

    /** 世界种子派生出的星团种子。客户端只拿到星团种子，算不出世界种子（多人服务器不泄露主世界种子）。 */
    public static long galaxySeed(long worldSeed) {
        return Seeds.derive(worldSeed, "galaxy");
    }

    /** 由星团种子取得星团，结果会缓存。线程安全。 */
    public static Galaxy fromGalaxySeed(long galaxySeed, int systemCount) {
        long key = Seeds.derive(galaxySeed, systemCount);
        synchronized (CACHE) {
            Galaxy cached = CACHE.get(key);
            if (cached != null) {
                return cached;
            }
        }
        Galaxy galaxy = generateFromGalaxySeed(galaxySeed, systemCount);
        synchronized (CACHE) {
            CACHE.put(key, galaxy);
        }
        return galaxy;
    }

    /** 由世界种子生成星团，不经过缓存。 */
    public static Galaxy generate(long worldSeed, int systemCount) {
        return generateFromGalaxySeed(galaxySeed(worldSeed), systemCount);
    }

    private static Galaxy generateFromGalaxySeed(long galaxySeed, int systemCount) {
        if (systemCount < 1 || systemCount > MAX_SYSTEM_COUNT) {
            throw new IllegalArgumentException("system count out of range: " + systemCount);
        }
        double[][] positions = placeSystems(Rng.of(Seeds.derive(galaxySeed, "positions")), systemCount);
        List<StarSystem> systems = new ArrayList<>(systemCount);
        for (int i = 0; i < systemCount; i++) {
            long systemSeed = Seeds.derive(galaxySeed, i);
            double[] p = positions[i];
            systems.add(i == HOME_SYSTEM ? homeSystem(systemSeed) : system(i, systemSeed, p[0], p[1], p[2]));
        }
        return new Galaxy(galaxySeed, systems);
    }

    /**
     * 恒星系的位置：母星系在原点，其余散布在一个扁平的椭球里（看星图时像个星盘），彼此至少相隔 2.5 光年。
     * 椭球大小随数量增长，平均最近邻距离保持在 5 光年左右。
     */
    private static double[][] placeSystems(Rng rng, int count) {
        double radius = 5.5 * Math.cbrt(count);
        double minDistance = 2.5;
        double[][] positions = new double[count][];
        positions[0] = new double[] {0, 0, 0};
        for (int i = 1; i < count; i++) {
            double[] candidate = null;
            for (int attempt = 0; attempt < 400; attempt++) {
                double x;
                double y;
                double z;
                do {
                    x = rng.range(-1.0, 1.0);
                    y = rng.range(-1.0, 1.0);
                    z = rng.range(-1.0, 1.0);
                } while (x * x + y * y + z * z > 1);
                candidate = new double[] {x * radius, y * radius * 0.35, z * radius};
                // 放不下时逐渐放宽间距，保证总能结束
                double required = attempt < 200 ? minDistance : minDistance * 0.5;
                if (farEnough(positions, i, candidate, required)) {
                    break;
                }
            }
            positions[i] = candidate;
        }
        return positions;
    }

    private static boolean farEnough(double[][] positions, int count, double[] p, double minDistance) {
        for (int j = 0; j < count; j++) {
            double dx = positions[j][0] - p[0];
            double dy = positions[j][1] - p[1];
            double dz = positions[j][2] - p[2];
            if (dx * dx + dy * dy + dz * dz < minDistance * minDistance) {
                return false;
            }
        }
        return true;
    }

    static Star star(Rng rng) {
        StarClass starClass = rng.weighted(List.of(StarClass.values()), StarClass::weight);
        // 同一类型里，光度、温度、质量、半径大体同步变化：主序星越亮越热、越重越大
        double f = rng.nextDouble();
        double luminosity = logLerp(starClass.minLuminosity(), starClass.maxLuminosity(), f);
        double temperature = lerp(starClass.minTemperature(), starClass.maxTemperature(), jitter(rng, f));
        double mass = lerp(starClass.minMass(), starClass.maxMass(), jitter(rng, f));
        double radius = logLerp(starClass.minRadius(), starClass.maxRadius(), jitter(rng, f));
        int color = switch (starClass) {
            case BLACK_HOLE -> 0xFFA040; // 吸积盘
            case NEUTRON_STAR -> 0xC8D8FF;
            default -> Colors.blackbody(temperature);
        };
        return new Star(starClass, luminosity, mass, radius, temperature, color);
    }

    private static StarSystem system(int index, long seed, double x, double y, double z) {
        Star star = star(Rng.of(Seeds.derive(seed, "star")));
        Name name = NameGenerator.generate(Rng.of(Seeds.derive(seed, "name")), 2, 3);
        Rng rng = Rng.of(Seeds.derive(seed, "orbits"));
        int planetCount = switch (star.starClass()) {
            case M, RED_GIANT -> rng.range(1, 4);
            case K, G, F -> rng.range(2, 6);
            case A -> rng.range(1, 5);
            case B, O -> rng.range(1, 4);
            case WHITE_DWARF, NEUTRON_STAR, BLACK_HOLE -> rng.range(1, 3);
        };
        double a = star.starClass().isCompact()
                ? rng.range(0.15, 0.6)
                : rng.range(0.3, 0.9) * Math.max(star.habitableZone(), 0.08);
        if (star.starClass() == StarClass.RED_GIANT) {
            // 膨胀的红巨星吞掉了内侧的行星
            a = Math.max(a, star.radius() * SOLAR_RADIUS_AU * 3);
        }
        List<Planet> planets = new ArrayList<>(planetCount);
        for (int j = 0; j < planetCount; j++) {
            if (j > 0) {
                a *= rng.range(1.4, 1.9);
            }
            boolean gasGiant = star.starClass().isCompact() ? rng.chance(0.15)
                    : a > star.frostLine() ? rng.chance(0.55) : rng.chance(0.06);
            Orbit orbit = new Orbit(a, orbitalPeriod(a, star.mass()), rng.nextDouble(), rng.range(-6.0, 6.0));
            BodyId id = BodyId.planet(index, j);
            planets.add(PlanetGenerator.generate(new PlanetGenerator.Spec(
                    id, Seeds.derive(seed, j), star, orbit, a, gasGiant, null, false, false)));
        }
        return new StarSystem(index, seed, name, x, y, z, star, planets);
    }

    /**
     * 母星系（玩法设计 §1.2）：类太阳恒星，四颗行星由内到外是灼热或荒芜的内行星、母星（带月球）、
     * 荒芜的外行星、带卫星的气态巨行星。类型固定，具体参数由种子决定。
     */
    private static StarSystem homeSystem(long seed) {
        Star star = Star.HOME;
        Name name = NameGenerator.generate(Rng.of(Seeds.derive(seed, "name")), 2, 3);
        Rng rng = Rng.of(Seeds.derive(seed, "orbits"));
        List<Planet> planets = new ArrayList<>(4);

        double inner = rng.range(0.35, 0.5);
        PlanetType innerType = rng.chance(0.5) ? PlanetType.SCORCHED : PlanetType.BARREN;
        planets.add(homeBody(seed, 0, star, inner, rng, false, innerType));
        planets.add(homePlanet(seed, star));
        double outer = rng.range(1.4, 1.9);
        planets.add(homeBody(seed, 2, star, outer, rng, false, PlanetType.BARREN));
        double giant = rng.range(4.5, 6.0);
        planets.add(homeBody(seed, 3, star, giant, rng, true, PlanetType.GAS_GIANT));
        return new StarSystem(HOME_SYSTEM, seed, name, 0, 0, 0, star, planets);
    }

    private static Planet homeBody(long systemSeed, int index, Star star, double a, Rng rng, boolean gasGiant, PlanetType type) {
        Orbit orbit = new Orbit(a, orbitalPeriod(a, star.mass()), rng.nextDouble(), rng.range(-4.0, 4.0));
        return PlanetGenerator.generate(new PlanetGenerator.Spec(
                BodyId.planet(HOME_SYSTEM, index), Seeds.derive(systemSeed, index), star, orbit, a, gasGiant, type, false, false));
    }

    /** 母星就是主世界：参数固定，不参与地形生成，只在星图、天空里用到。 */
    private static Planet homePlanet(long systemSeed, Star star) {
        long seed = Seeds.derive(systemSeed, HOME_PLANET);
        Orbit orbit = new Orbit(1.0, 365.25, 0, 0);
        // 月球：真空、荒芜，参数由种子决定
        Orbit moonOrbit = new Orbit(0.00257, 29.5, 0, 5.1);
        Planet moon = PlanetGenerator.generate(new PlanetGenerator.Spec(
                HOME_MOON_ID, Seeds.derive(seed, 0), star, moonOrbit, 1.0, false, PlanetType.BARREN, false, true))
                .withName(new Name("Moon", "月球"));
        List<Resource.Deposit> resources = List.of(
                new Resource.Deposit(Resource.IRON, 0.6),
                new Resource.Deposit(Resource.COPPER, 0.5),
                new Resource.Deposit(Resource.COAL, 0.6),
                new Resource.Deposit(Resource.CRUDE_OIL, 0.3),
                new Resource.Deposit(Resource.SILICON, 0.2));
        return new Planet(HOME_PLANET_ID, seed, new Name("Homeworld", "母星"), PlanetType.HABITABLE, orbit,
                1.0, 1.0, new Atmosphere(Kind.BREATHABLE, 1.0), 288, 10, List.of(), Liquid.WATER, DayCycle.STANDARD,
                new WeatherProfile(Precipitation.RAIN, 0.3, 0.3, 0.4, true), TerrainProfile.HOME,
                new Ecology(1, 0.7, 1, 0, 0, Seeds.derive(seed, "species")), Palette.HOME, resources,
                0, false, List.of(moon));
    }

    /** 开普勒第三定律：P（年）= √(a³ / M)，换算成母星日。 */
    static double orbitalPeriod(double au, double solarMasses) {
        return 365.25 * Math.sqrt(au * au * au / solarMasses);
    }

    private static double jitter(Rng rng, double f) {
        return Math.max(0, Math.min(1, f + rng.gaussian() * 0.08));
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double logLerp(double a, double b, double t) {
        return Math.exp(lerp(Math.log(a), Math.log(b), t));
    }
}
