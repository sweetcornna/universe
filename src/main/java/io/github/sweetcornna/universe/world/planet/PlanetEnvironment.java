package io.github.sweetcornna.universe.world.planet;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

import org.joml.Vector3f;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.sky.SkyColors;
import io.github.sweetcornna.universe.core.terraform.Terraform;
import io.github.sweetcornna.universe.core.weather.WeatherCycle;
import io.github.sweetcornna.universe.terraform.Terraforming;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ARGB;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

/**
 * 星球维度按星球变化的环境属性：天色、雾、云、日光颜色、水下雾色、日落光晕……
 *
 * <p>维度类型和生物群系都是登录时同步的注册表，不能每颗星球一份，所以这些值不写进数据包，而是在世界建立
 * 环境属性系统时插进两层（{@code mixin.EnvironmentAttributeSystemMixin}）：
 * <ul>
 *   <li>底色层：在维度和生物群系之后、时间线之前，换成这颗星球正午晴天的值。入夜变暗、下雨变灰仍由
 *       原版的时间线和天气叠加在上面，昼夜长度不同的星球也一样；</li>
 *   <li>收尾层：在时间线和天气之后，给日出日落的光晕换颜色、按大气调强度。</li>
 * </ul>
 * 服务端和客户端都会建：视觉属性只有客户端用，玩法属性（例如水会不会蒸发）两边要一致。
 *
 * <p>星球按现用的样子算（{@link Terraforming}，ADR-0036 §六）。不能改造的星球这几层建好就不变；能改造的星球改成每刻
 * 读一次现用的星球，改造到了门槛，天色、雾、水会不会蒸发跟着变，不用重进世界。
 */
public final class PlanetEnvironment {
    /** 水在多少开尔文以上一放下就蒸发（一个大气压下的沸点）。 */
    private static final double BOILING_POINT = 373.15;
    private static volatile Function<ResourceKey<Level>, Optional<Galaxy>> clientGalaxy = key -> Optional.empty();
    private static volatile ToDoubleFunction<Level> stormIntensity = level -> 0;
    /** 按维度缓存的现用环境，服务端、客户端各一份（单人游戏两边在一个进程里）。星球对象变了就重算。 */
    private static final Map<ResourceKey<Level>, Context> SERVER_CONTEXTS = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Context> CLIENT_CONTEXTS = new ConcurrentHashMap<>();

    private PlanetEnvironment() {
    }

    /** 星球（现用的样子）和它所在的恒星系。 */
    public record Context(Planet planet, StarSystem system, SkyColors colors) {
    }

    /** 客户端入口登记：客户端的世界从这里取星团（服务端直接由世界种子算）。 */
    public static void setClientGalaxy(Function<ResourceKey<Level>, Optional<Galaxy>> lookup) {
        clientGalaxy = lookup;
    }

    /** 客户端入口登记：沙尘一类天气此刻的强度（服务端不需要，视觉属性只有客户端用）。 */
    public static void setStormIntensity(ToDoubleFunction<Level> lookup) {
        stormIntensity = lookup;
    }

    /** 这个世界是星球维度时，返回星球（改造过的按现用的样子）、恒星系和天空参数。 */
    public static Optional<Context> context(Level level) {
        if (level instanceof ServerLevel server) {
            Galaxy galaxy = Planets.galaxy(server.getServer());
            return Planets.generatedPlanet(galaxy, level.dimension())
                    .flatMap(base -> cached(SERVER_CONTEXTS, galaxy, level.dimension(), Terraforming.effective(server.getServer(), base)));
        }
        return clientGalaxy.apply(level.dimension()).flatMap(galaxy -> Planets.generatedPlanet(galaxy, level.dimension())
                .flatMap(base -> cached(CLIENT_CONTEXTS, galaxy, level.dimension(), Terraforming.clientEffective(galaxy, base))));
    }

    private static Optional<Context> cached(Map<ResourceKey<Level>, Context> cache, Galaxy galaxy, ResourceKey<Level> dimension, Planet planet) {
        Context context = cache.get(dimension);
        if (context != null && context.planet() == planet) {
            return Optional.of(context);
        }
        Optional<Context> fresh = context(galaxy, planet);
        fresh.ifPresent(value -> cache.put(dimension, value));
        return fresh;
    }

    /** 星球本来的样子（不管改造）。 */
    public static Optional<Context> context(Galaxy galaxy, ResourceKey<Level> dimension) {
        return Planets.generatedPlanet(galaxy, dimension).flatMap(planet -> context(galaxy, planet));
    }

    private static Optional<Context> context(Galaxy galaxy, Planet planet) {
        return galaxy.system(planet.id().system()).map(system -> new Context(planet, system, SkyColors.of(planet, system.star())));
    }

    /** 底色层，由 mixin 在维度层和生物群系层之后调用。 */
    public static void addBaseLayers(EnvironmentAttributeSystem.Builder builder, LevelAccessor accessor) {
        if (!(accessor instanceof Level level)) {
            // 世界生成时的区域视图，不需要天空
            return;
        }
        context(level).ifPresent(context -> {
            if (Terraform.terraformable(context.planet())) {
                addLiveBaseLayers(builder, level, new Live(level, context));
                return;
            }
            SkyColors colors = context.colors();
            Vector3f sky = ARGB.vector3fFromRGB24(colors.daySky());
            Vector3f fog = ARGB.vector3fFromRGB24(colors.dayFog());
            Vector4f cloud = ARGB.vector4fFromARGB32(ARGB.color((int) Math.round(colors.cloudAlpha() * 255), colors.cloud()));
            Vector3f skyLight = ARGB.vector3fFromRGB24(colors.skyLight());
            Vector3f waterFog = ARGB.vector3fFromRGB24(colors.waterFog());
            float stars = (float) (colors.daytimeStars() * 0.8);
            builder.addConstantLayer(EnvironmentAttributes.CLOUD_COLOR, value -> cloud);
            builder.addConstantLayer(EnvironmentAttributes.SKY_LIGHT_COLOR, value -> skyLight);
            builder.addConstantLayer(EnvironmentAttributes.WATER_FOG_COLOR, value -> waterFog);
            // 时间线对星星亮度取最大值，这里给的是白天的下限
            builder.addConstantLayer(EnvironmentAttributes.STAR_BRIGHTNESS, value -> Math.max(value, stars));
            float fogStart = (float) colors.fogStart();
            float fogEnd = (float) colors.fogEnd();
            if (context.planet().weather().precipitation().falls() || !WeatherCycle.hasWeather(context.planet().weather())) {
                builder.addConstantLayer(EnvironmentAttributes.SKY_COLOR, value -> sky);
                builder.addConstantLayer(EnvironmentAttributes.FOG_COLOR, value -> fog);
                if (fogEnd < SkyColors.NO_FOG) {
                    builder.addConstantLayer(EnvironmentAttributes.FOG_START_DISTANCE, value -> fogStart);
                    builder.addConstantLayer(EnvironmentAttributes.FOG_END_DISTANCE, value -> fogEnd);
                }
            } else {
                // 沙尘、火山灰、晶尘：天色和雾色趋向风暴的颜色，雾拉近。放在时间线之前，入夜照样变暗
                Vector3f storm = ARGB.vector3fFromRGB24(colors.stormColor());
                Vector3f stormSky = new Vector3f(storm).mul(0.8F);
                builder.addTimeBasedLayer(EnvironmentAttributes.SKY_COLOR,
                        (value, tick) -> mix(sky, stormSky, intensity(level)));
                builder.addTimeBasedLayer(EnvironmentAttributes.FOG_COLOR,
                        (value, tick) -> mix(fog, storm, intensity(level)));
                builder.addTimeBasedLayer(EnvironmentAttributes.FOG_START_DISTANCE,
                        (value, tick) -> fogStart * (1 - intensity(level)));
                builder.addTimeBasedLayer(EnvironmentAttributes.FOG_END_DISTANCE, (value, tick) -> stormFogEnd(fogEnd, intensity(level)));
            }
            if (context.planet().temperature() > BOILING_POINT) {
                builder.addConstantLayer(EnvironmentAttributes.WATER_EVAPORATES, value -> true);
            }
        });
    }

    /** 能改造的星球：和上面一样的几层，只是每刻按现用的星球取值。 */
    private static void addLiveBaseLayers(EnvironmentAttributeSystem.Builder builder, Level level, Live live) {
        builder.addTimeBasedLayer(EnvironmentAttributes.CLOUD_COLOR, (value, tick) -> live.look().cloud());
        builder.addTimeBasedLayer(EnvironmentAttributes.SKY_LIGHT_COLOR, (value, tick) -> live.look().skyLight());
        builder.addTimeBasedLayer(EnvironmentAttributes.WATER_FOG_COLOR, (value, tick) -> live.look().waterFog());
        builder.addTimeBasedLayer(EnvironmentAttributes.STAR_BRIGHTNESS, (value, tick) -> Math.max(value, live.look().stars()));
        builder.addTimeBasedLayer(EnvironmentAttributes.SKY_COLOR, (value, tick) -> {
            Look look = live.look();
            return look.clear() ? look.sky() : mix(look.sky(), look.stormSky(), intensity(level));
        });
        builder.addTimeBasedLayer(EnvironmentAttributes.FOG_COLOR, (value, tick) -> {
            Look look = live.look();
            return look.clear() ? look.fog() : mix(look.fog(), look.storm(), intensity(level));
        });
        builder.addTimeBasedLayer(EnvironmentAttributes.FOG_START_DISTANCE, (value, tick) -> {
            Look look = live.look();
            if (look.clear()) {
                return look.fogEnd() < SkyColors.NO_FOG ? look.fogStart() : value;
            }
            return look.fogStart() * (1 - intensity(level));
        });
        builder.addTimeBasedLayer(EnvironmentAttributes.FOG_END_DISTANCE, (value, tick) -> {
            Look look = live.look();
            if (look.clear()) {
                return look.fogEnd() < SkyColors.NO_FOG ? look.fogEnd() : value;
            }
            return stormFogEnd(look.fogEnd(), intensity(level));
        });
        builder.addTimeBasedLayer(EnvironmentAttributes.WATER_EVAPORATES, (value, tick) -> live.look().evaporates() || value);
    }

    /** 沙尘里的雾的远端：按指数插值，弱沙尘也明显变朦胧。 */
    private static float stormFogEnd(float fogEnd, float s) {
        return (float) (fogEnd * Math.pow(SkyColors.STORM_FOG_END / fogEnd, Math.sqrt(s)));
    }

    /** 收尾层，由 mixin 在时间线层和天气层之后调用。 */
    public static void addFinishLayers(EnvironmentAttributeSystem.Builder builder, Level level) {
        context(level).ifPresent(context -> {
            if (Terraform.terraformable(context.planet())) {
                Live live = new Live(level, context);
                builder.addTimeBasedLayer(EnvironmentAttributes.SUNRISE_SUNSET_COLOR,
                        (value, tick) -> sunset(value, live.look().sunset(), live.look().sunsetStrength(), level));
                builder.addTimeBasedLayer(EnvironmentAttributes.STAR_BRIGHTNESS,
                        (value, tick) -> live.look().falls() ? value : value * (1 - intensity(level)));
                return;
            }
            SkyColors colors = context.colors();
            Vector3f sunset = ARGB.vector3fFromRGB24(colors.sunset());
            float strength = (float) colors.sunsetAlpha();
            // 时间线给出原版日出日落的颜色变化和强度，这里保留变化、换掉色调；沙尘里看不到日落
            builder.addTimeBasedLayer(EnvironmentAttributes.SUNRISE_SUNSET_COLOR, (value, tick) -> sunset(value, sunset, strength, level));
            if (!context.planet().weather().precipitation().falls()) {
                builder.addTimeBasedLayer(EnvironmentAttributes.STAR_BRIGHTNESS,
                        (value, tick) -> value * (1 - intensity(level)));
            }
        });
    }

    private static Vector4f sunset(Vector4fc value, Vector3f sunset, float strength, Level level) {
        return new Vector4f(
                value.x() + (sunset.x() - value.x()) * 0.7F,
                value.y() + (sunset.y() - value.y()) * 0.7F,
                value.z() + (sunset.z() - value.z()) * 0.7F,
                value.w() * strength * (1 - 0.9F * intensity(level)));
    }

    private static float intensity(Level level) {
        return (float) stormIntensity.applyAsDouble(level);
    }

    private static Vector3f mix(Vector3f a, Vector3f b, float t) {
        return new Vector3f(a).lerp(b, t);
    }

    /** 环境属性要用的几样，从天空参数换算好，每次取值不用再算。 */
    private record Look(Context context, Vector3f sky, Vector3f fog, Vector4f cloud, Vector3f skyLight, Vector3f waterFog, float stars,
            boolean clear, boolean falls, float fogStart, float fogEnd, Vector3f storm, Vector3f stormSky, boolean evaporates, Vector3f sunset,
            float sunsetStrength) {
        static Look of(Context context) {
            SkyColors colors = context.colors();
            Planet planet = context.planet();
            Vector3f storm = ARGB.vector3fFromRGB24(colors.stormColor());
            boolean falls = planet.weather().precipitation().falls();
            return new Look(context, ARGB.vector3fFromRGB24(colors.daySky()), ARGB.vector3fFromRGB24(colors.dayFog()),
                    ARGB.vector4fFromARGB32(ARGB.color((int) Math.round(colors.cloudAlpha() * 255), colors.cloud())),
                    ARGB.vector3fFromRGB24(colors.skyLight()), ARGB.vector3fFromRGB24(colors.waterFog()), (float) (colors.daytimeStars() * 0.8),
                    falls || !WeatherCycle.hasWeather(planet.weather()), falls, (float) colors.fogStart(), (float) colors.fogEnd(), storm,
                    new Vector3f(storm).mul(0.8F), planet.temperature() > BOILING_POINT, ARGB.vector3fFromRGB24(colors.sunset()),
                    (float) colors.sunsetAlpha());
        }
    }

    /** 一个世界现用的样子：改造的状态变了（{@link Terraforming#revision}）才重新取一次。 */
    private static final class Live {
        private final Level level;
        private volatile Look look;
        private volatile int revision;

        Live(Level level, Context context) {
            this.level = level;
            this.revision = Terraforming.revision(level.isClientSide());
            this.look = Look.of(context);
        }

        Look look() {
            int now = Terraforming.revision(this.level.isClientSide());
            if (now != this.revision) {
                this.revision = now;
                context(this.level).filter(context -> context != this.look.context()).ifPresent(context -> this.look = Look.of(context));
            }
            return this.look;
        }
    }
}
