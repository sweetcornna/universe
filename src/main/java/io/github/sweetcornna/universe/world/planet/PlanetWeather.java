package io.github.sweetcornna.universe.world.planet;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.WeatherProfile;
import io.github.sweetcornna.universe.core.weather.WeatherCycle;
import io.github.sweetcornna.universe.network.PlanetWeatherPayload;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 每颗星球自己的天气（游戏设计 §2.1）。
 *
 * <p>原版的天气是全服共用的一份，而且每个能下雨的维度每刻都会推进一次计时，星球一多主世界的天气就会乱。
 * 所以星球维度不走原版的 {@code advanceWeatherCycle}（{@code mixin.ServerLevelMixin} 把它换成这里的
 * {@link #tick}），各自按 {@link WeatherProfile} 轮换，状态存在维度自己的存档数据里：
 * <ul>
 *   <li>雨、雪、酸雨：设置这个维度的降雨、雷暴强度，原版负责画雨雪、积雪、熄火、打雷；</li>
 *   <li>沙尘、火山灰、晶尘：不能算下雨，强度用 {@link PlanetWeatherPayload} 单独同步，客户端加雾和粒子。</li>
 * </ul>
 * 没有大气的星球天气参数是平静，永远晴天。原版的 {@code /weather} 只管主世界，星球用
 * {@code /universe planet weather}。
 */
public final class PlanetWeather extends SavedData {
    private static final Codec<PlanetWeather> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.fieldOf("active").forGetter(w -> w.cycle.active()),
            Codec.BOOL.fieldOf("storm").forGetter(w -> w.cycle.storm()),
            Codec.INT.fieldOf("remaining").forGetter(w -> w.cycle.remaining()),
            Codec.FLOAT.optionalFieldOf("intensity", 0.0F).forGetter(w -> w.intensity)
    ).apply(i, (active, storm, remaining, intensity) -> {
        PlanetWeather weather = new PlanetWeather(new WeatherCycle(active, storm, remaining));
        weather.intensity = intensity;
        return weather;
    }));
    private static final SavedDataType<PlanetWeather> TYPE = new SavedDataType<>(
            Universe.id("planet_weather"),
            level -> new PlanetWeather(null),
            level -> CODEC);
    private WeatherCycle cycle;
    /** 沙尘一类天气的强度。 */
    private float intensity;
    /** 上次发给客户端的强度。 */
    private float sentIntensity = -1;

    private PlanetWeather(WeatherCycle cycle) {
        this.cycle = cycle;
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> sync(event.getEntity()));
        gameBus.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, event -> sync(event.getEntity()));
        gameBus.addListener(PlayerEvent.PlayerRespawnEvent.class, event -> sync(event.getEntity()));
    }

    private static PlanetWeather of(ServerLevel level, WeatherProfile profile) {
        PlanetWeather weather = level.getDataStorage().computeIfAbsent(TYPE);
        if (weather.cycle == null) {
            weather.cycle = WeatherCycle.start(profile, level.getRandom()::nextDouble);
            weather.setDirty();
        }
        return weather;
    }

    /**
     * 代替原版的天气推进。
     *
     * @return 这个世界是星球维度、已经处理过天气时返回 true，原版的就不再执行
     */
    public static boolean tick(ServerLevel level) {
        Optional<Planet> planet = Planets.planet(level);
        if (planet.isEmpty()) {
            return false;
        }
        WeatherProfile profile = planet.get().weather();
        PlanetWeather weather = of(level, profile);
        boolean wasRaining = level.isRaining();
        if (level.getGameRules().get(GameRules.ADVANCE_WEATHER)) {
            weather.cycle.tick(profile, level.getRandom()::nextDouble);
            weather.setDirty();
        }
        float target = weather.cycle.targetIntensity(profile);
        boolean falls = profile.precipitation().falls();
        float rain = falls ? target : 0;
        float thunder = weather.cycle.thundering(profile) ? 1 : 0;
        float newRain = WeatherCycle.approach(level.rainLevel, rain);
        float newThunder = WeatherCycle.approach(level.thunderLevel, thunder);
        level.oRainLevel = level.rainLevel;
        level.oThunderLevel = level.thunderLevel;
        level.rainLevel = newRain;
        level.thunderLevel = newThunder;
        var players = level.getServer().getPlayerList();
        if (level.oRainLevel != level.rainLevel) {
            players.broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, level.rainLevel), level.dimension());
        }
        if (level.oThunderLevel != level.thunderLevel) {
            players.broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, level.thunderLevel), level.dimension());
        }
        if (wasRaining != level.isRaining()) {
            players.broadcastAll(new ClientboundGameEventPacket(
                    wasRaining ? ClientboundGameEventPacket.STOP_RAINING : ClientboundGameEventPacket.START_RAINING, 0.0F), level.dimension());
            players.broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, level.rainLevel), level.dimension());
            players.broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, level.thunderLevel), level.dimension());
        }
        float dust = falls ? 0 : target;
        weather.intensity = WeatherCycle.approach(weather.intensity, dust);
        // 每变化 0.02 或到达两端时发一次，不用每刻都发
        if (Math.abs(weather.intensity - weather.sentIntensity) >= 0.02F
                || (weather.intensity != weather.sentIntensity && (weather.intensity == 0 || weather.intensity == dust))) {
            weather.sentIntensity = weather.intensity;
            PacketDistributor.sendToPlayersInDimension(level, new PlanetWeatherPayload(weather.intensity));
        }
        return true;
    }

    /** 星球生存用：0 晴，1 雨雪或沙尘，2 风暴（雷暴、强沙尘暴）。不是星球维度时为 0。 */
    public static int severity(ServerLevel level) {
        Optional<Planet> planet = Planets.planet(level);
        if (planet.isEmpty()) {
            return 0;
        }
        PlanetWeather weather = of(level, planet.get().weather());
        float strength = Math.max(level.rainLevel, weather.intensity);
        if (strength < 0.2F) {
            return 0;
        }
        return level.thunderLevel > 0.5F || weather.cycle.storm() && strength > 0.5F ? 2 : 1;
    }

    /** 命令用：设成晴天、普通天气或风暴，持续 {@code ticks} 刻。不是星球维度时返回 false。 */
    public static boolean force(ServerLevel level, boolean active, boolean storm, int ticks) {
        Optional<Planet> planet = Planets.planet(level);
        if (planet.isEmpty()) {
            return false;
        }
        PlanetWeather weather = of(level, planet.get().weather());
        weather.cycle.force(active && WeatherCycle.hasWeather(planet.get().weather()), storm, ticks);
        weather.setDirty();
        return true;
    }

    private static void sync(Player player) {
        if (player instanceof ServerPlayer serverPlayer && player.level() instanceof ServerLevel level) {
            float intensity = Planets.planet(level).map(planet -> of(level, planet.weather()).intensity).orElse(0.0F);
            PacketDistributor.sendToPlayer(serverPlayer, new PlanetWeatherPayload(intensity));
        }
    }
}
