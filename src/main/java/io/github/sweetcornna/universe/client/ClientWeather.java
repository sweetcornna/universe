package io.github.sweetcornna.universe.client;

import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.WeatherProfile;
import io.github.sweetcornna.universe.core.procgen.Colors;
import io.github.sweetcornna.universe.core.weather.WeatherCycle;
import io.github.sweetcornna.universe.network.PlanetWeatherPayload;
import io.github.sweetcornna.universe.world.planet.PlanetEnvironment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端的星球天气：沙尘、火山灰、晶尘的强度（服务端由 {@code PlanetWeather} 同步），
 * 交给环境属性层加雾、改天色，并在玩家周围生成随风飘的粒子。雨雪走原版。
 *
 * <p>只在客户端主线程上读写。
 */
public final class ClientWeather {
    /** 玩家周围多大范围里生成粒子。 */
    private static final int PARTICLE_RADIUS = 18;
    private static float target;
    private static float intensity;

    private ClientWeather() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class,
                event -> event.register(PlanetWeatherPayload.TYPE, ClientWeather::handle));
        // 换维度时从晴天开始，等服务端发来这个维度的强度
        gameBus.addListener(LevelEvent.Load.class, event -> {
            if (event.getLevel() instanceof ClientLevel) {
                target = 0;
                intensity = 0;
            }
        });
        gameBus.addListener(ClientTickEvent.Post.class, event -> tick());
        PlanetEnvironment.setStormIntensity(level -> level instanceof ClientLevel ? intensity : 0);
    }

    /** 沙尘一类天气此刻的强度 0–1。 */
    public static float intensity() {
        return intensity;
    }

    private static void handle(PlanetWeatherPayload payload, IPayloadContext context) {
        target = Mth.clamp(payload.intensity(), 0, 1);
    }

    private static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused()) {
            return;
        }
        intensity = WeatherCycle.approach(intensity, target);
        ClientLevel level = minecraft.level;
        Player player = minecraft.player;
        if (level == null || player == null || intensity <= 0) {
            return;
        }
        ClientGalaxy.currentPlanet().ifPresent(planet -> spawnParticles(level, player, planet));
    }

    private static void spawnParticles(ClientLevel level, Player player, Planet planet) {
        WeatherProfile profile = planet.weather();
        if (profile.precipitation().falls()) {
            return;
        }
        RandomSource random = level.getRandom();
        // 风向随星球固定，风力决定粒子飘多快
        double windAngle = (planet.seed() & 0xFFFF) / 65536.0 * Math.PI * 2;
        double wind = 0.15 + 0.6 * profile.wind();
        double windX = Math.cos(windAngle) * wind;
        double windZ = Math.sin(windAngle) * wind;
        int count = (int) (intensity * intensity * 45);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < count; i++) {
            double x = player.getX() + random.nextGaussian() * PARTICLE_RADIUS * 0.6;
            double y = player.getY() + random.nextDouble() * 12 - 3;
            double z = player.getZ() + random.nextGaussian() * PARTICLE_RADIUS * 0.6;
            pos.set(x, y, z);
            if (!level.canSeeSky(pos)) {
                continue;
            }
            ParticleOptions particle = particle(profile.precipitation(), planet, random);
            level.addParticle(particle, x, y, z, windX, -0.02 - random.nextDouble() * 0.03, windZ);
        }
    }

    private static ParticleOptions particle(WeatherProfile.Precipitation precipitation, Planet planet, RandomSource random) {
        return switch (precipitation) {
            case ASH -> random.nextInt(3) == 0 ? ParticleTypes.WHITE_ASH : ParticleTypes.ASH;
            case CRYSTAL -> random.nextInt(12) == 0 ? ParticleTypes.END_ROD
                    : new DustParticleOptions(Colors.lerp(planet.palette().accent(), 0xFFFFFF, 0.4), 0.3F + random.nextFloat() * 0.4F);
            default -> new DustParticleOptions(Colors.lerp(planet.palette().sand(), 0xFFFFFF, random.nextFloat() * 0.3),
                    0.3F + random.nextFloat() * 0.5F);
        };
    }
}
