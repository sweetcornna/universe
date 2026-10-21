package io.github.sweetcornna.universe.client.sky;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.ClientGalaxy;
import io.github.sweetcornna.universe.client.galaxy.GalaxyText;
import io.github.sweetcornna.universe.client.galaxy.StarMapScreen;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * 坐飞船进出大气层、跃迁到达时的过场（ADR-0021）。
 *
 * <p>加载维度的那一两秒换成自己的画面：进大气是橙红色的等离子光从中间往外涌，出大气是天色从蓝变黑、星星往下掠，
 * 跃迁到达是紫色的星光拉线往回收。到了以后还有一小段：进大气时画面边上烧着橙光、飞船周围窜火苗，几秒后散掉；
 * 出大气时一层蓝白的光很快退掉。
 *
 * <p>只有坐着飞船换维度才这样，死亡复活、命令传送还是原版的加载画面。
 */
public final class AtmosphereTransitions {
    private static final Identifier GLOW = Universe.id("textures/gui/flare/glow.png");
    /** 进大气之后烧多久（毫秒）。 */
    private static final long ENTRY_AFTERGLOW = 6000;
    /** 出大气之后那层光退掉要多久（毫秒）。 */
    private static final long EXIT_AFTERGLOW = 1800;

    enum Kind {
        ENTRY(2000),
        EXIT(1600),
        WARP(1400);

        /** 过场至少放多久（毫秒）：加载往往不到半秒就完了，一闪而过看不清。 */
        final long minimumMillis;

        Kind(long minimumMillis) {
            this.minimumMillis = minimumMillis;
        }
    }

    private static @Nullable ResourceKey<Level> lastDimension;
    /** 上一刻是不是坐着飞船。换维度时客户端的玩家是新建的，过场的工厂被调用时还没坐上去，只能看这个。 */
    private static boolean ridingShip;
    private static @Nullable Kind arriving;
    private static long arrivedAt;

    private AtmosphereTransitions() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        gameBus.addListener(ClientTickEvent.Post.class, event -> tick());
        modBus.addListener(RegisterGuiLayersEvent.class,
                event -> event.registerBelowAll(Universe.id("atmosphere_afterglow"), AtmosphereTransitions::afterglow));
    }

    /** 维度过场的工厂：坐着飞船从太空进星球、从星球进太空、在太空之间跃迁时用自己的画面。 */
    public static LevelLoadingScreen create(LevelLoadTracker tracker, LevelLoadingScreen.Reason reason, ResourceKey<Level> target) {
        Kind kind = kind(target);
        if (kind == null) {
            return new LevelLoadingScreen(tracker, LevelLoadingScreen.Reason.END_PORTAL);
        }
        arriving = kind;
        arrivedAt = Long.MAX_VALUE;
        return new TransitionScreen(tracker, reason, kind, title(kind), subtitle(kind, target));
    }

    private static @Nullable Kind kind(ResourceKey<Level> target) {
        if (!ridingShip || lastDimension == null || lastDimension == target) {
            return null;
        }
        boolean fromSpace = SpaceDimensions.isSpace(lastDimension);
        boolean toSpace = SpaceDimensions.isSpace(target);
        if (fromSpace && toSpace) {
            return Kind.WARP;
        }
        if (fromSpace && Planets.bodyId(target).isPresent()) {
            return Kind.ENTRY;
        }
        if (toSpace && Planets.bodyId(lastDimension).isPresent()) {
            return Kind.EXIT;
        }
        return null;
    }

    private static Component title(Kind kind) {
        return Component.translatable(switch (kind) {
            case ENTRY -> "screen.universe.transition.entry";
            case EXIT -> "screen.universe.transition.exit";
            case WARP -> "screen.universe.transition.warp";
        });
    }

    private static Component subtitle(Kind kind, ResourceKey<Level> target) {
        if (kind == Kind.ENTRY) {
            Planet planet = planetFor(target);
            return planet == null ? Component.empty() : Component.literal(GalaxyText.planet(planet));
        }
        StarSystem system = systemFor(target);
        return system == null ? Component.empty() : Component.literal(GalaxyText.system(system));
    }

    private static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level != null && minecraft.player != null) {
            lastDimension = level.dimension();
            ridingShip = minecraft.player.getVehicle() instanceof ShipEntity;
        }
        if (arriving != null && arrivedAt == Long.MAX_VALUE && !(minecraft.gui.screen() instanceof TransitionScreen) && level != null) {
            arrivedAt = Util.getMillis();
        }
        if (arriving == Kind.ENTRY && level != null) {
            float heat = heat();
            if (heat > 0 && minecraft.player != null && minecraft.player.getVehicle() instanceof ShipEntity ship) {
                flames(level, ship, heat);
            }
        }
    }

    /** 进大气以后的热度：刚到时 1，几秒内降到 0。 */
    private static float heat() {
        if (arriving != Kind.ENTRY || arrivedAt == Long.MAX_VALUE) {
            return 0;
        }
        float t = (Util.getMillis() - arrivedAt) / (float) ENTRY_AFTERGLOW;
        return t >= 1 ? 0 : (1 - t) * (1 - t);
    }

    /** 飞船迎着气流的那一面窜火苗：往船身下方和前方喷，热度越高越多。 */
    private static void flames(ClientLevel level, Entity ship, float heat) {
        Vec3 motion = ship.getDeltaMovement();
        Vec3 ahead = motion.lengthSqr() > 1e-4 ? motion.normalize() : new Vec3(0, -1, 0);
        double width = ship.getBbWidth() * 0.6 + 1;
        int count = (int) (12 * heat) + 1;
        for (int i = 0; i < count; i++) {
            double ox = (level.getRandom().nextDouble() - 0.5) * width * 2;
            double oy = (level.getRandom().nextDouble() - 0.5) * ship.getBbHeight();
            double oz = (level.getRandom().nextDouble() - 0.5) * width * 2;
            Vec3 at = ship.position().add(ox, oy + ship.getBbHeight() * 0.5, oz).add(ahead.scale(width));
            Vec3 back = ahead.scale(-0.35 - 0.4 * level.getRandom().nextDouble());
            level.addParticle(level.getRandom().nextFloat() < 0.3F ? ParticleTypes.LAVA : ParticleTypes.FLAME, at.x, at.y, at.z, back.x, back.y,
                    back.z);
        }
    }

    /** 到了以后的余光：进大气时边上一圈橙红，出大气时整个画面一层很快退掉的蓝白。 */
    private static void afterglow(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        if (arriving == null || arrivedAt == Long.MAX_VALUE) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        long elapsed = Util.getMillis() - arrivedAt;
        if (arriving == Kind.ENTRY) {
            float heat = heat();
            if (heat <= 0) {
                arriving = null;
                return;
            }
            // 一闪一闪的热浪
            float flicker = 0.85F + 0.15F * Mth.sin(elapsed * 0.025F) * Mth.sin(elapsed * 0.011F + 1.3F);
            int edge = ARGB.color((int) (200 * heat * flicker), 0xFF5A14);
            int band = Math.max(8, height / 4);
            graphics.fillGradient(0, 0, width, band, edge, 0);
            graphics.fillGradient(0, height - band, width, height, 0, edge);
            graphics.fillGradient(0, 0, width, height, ARGB.color((int) (50 * heat), 0xFF8030), ARGB.color((int) (90 * heat), 0xFF4010));
            StarMapScreen.sprite(graphics, GLOW, width / 2.0, height * 1.05, height * 1.4, ARGB.color((int) (170 * heat * flicker), 0xFFB060));
        } else {
            float t = elapsed / (float) (arriving == Kind.EXIT ? EXIT_AFTERGLOW : EXIT_AFTERGLOW / 2);
            if (t >= 1) {
                arriving = null;
                return;
            }
            int color = arriving == Kind.EXIT ? 0xB8D4FF : 0xD8C4FF;
            graphics.fill(0, 0, width, height, ARGB.color((int) (170 * (1 - t) * (1 - t)), color));
        }
    }

    /** 加载维度时的画面。 */
    static final class TransitionScreen extends LevelLoadingScreen {
        private final Kind kind;
        private final Component heading;
        private final Component destination;
        private final long started = Util.getMillis();

        TransitionScreen(LevelLoadTracker tracker, LevelLoadingScreen.Reason reason, Kind kind, Component heading, Component destination) {
            super(tracker, reason);
            this.kind = kind;
            this.heading = heading;
            this.destination = destination;
        }

        @Override
        public void tick() {
            // 原版在这里一看地形加载好了就关掉画面；没放够就再等等
            if (Util.getMillis() - this.started >= this.kind.minimumMillis) {
                super.tick();
            }
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            int width = this.width;
            int height = this.height;
            double seconds = (Util.getMillis() - this.started) / 1000.0;
            double cx = width / 2.0;
            double cy = height / 2.0;
            double reach = Math.hypot(cx, cy);
            switch (this.kind) {
                case ENTRY -> {
                    graphics.fill(0, 0, width, height, 0xFF140402);
                    float pulse = 0.85F + 0.15F * (float) Math.sin(seconds * 9.0);
                    StarMapScreen.sprite(graphics, GLOW, cx, cy, height * 2.6, ARGB.color((int) (235 * pulse), 0xFF5A18));
                    StarMapScreen.sprite(graphics, GLOW, cx, cy, height * 1.2, ARGB.color((int) (200 * pulse), 0xFFD08A));
                    streaks(graphics, cx, cy, reach, seconds, 70, 0xFFE0A0, 0xFF7A30, 1.4);
                    int edge = ARGB.color(220, 0x200000);
                    graphics.fillGradient(0, 0, width, height / 5, edge, 0);
                    graphics.fillGradient(0, height * 4 / 5, width, height, 0, edge);
                }
                case EXIT -> {
                    // 往上飞：天色从下往上变黑，星星一颗颗亮起来往下掠
                    float t = (float) Mth.clamp(seconds / 1.6, 0, 1);
                    int sky = ARGB.srgbLerp(t, 0xFF3A6EB4, 0xFF02040A);
                    graphics.fillGradient(0, 0, width, height, 0xFF000000, sky);
                    // 星星是一个个亮点，跟着视线慢慢往下沉，越亮的越近、沉得越快；天越黑露出来的越多
                    for (int i = 0; i < 140; i++) {
                        double x = hash(i * 3) * width;
                        double depth = hash(i * 3 + 1);
                        double y = ((hash(i * 3 + 2) + seconds * (0.04 + 0.12 * depth)) % 1.0) * height;
                        float appear = (float) Mth.clamp(t * 2.2 - hash(i * 5) * 1.2, 0, 1);
                        int alpha = (int) (255 * appear * (0.35 + 0.65 * depth));
                        if (alpha > 4) {
                            double size = 0.6 + 1.4 * depth * depth;
                            StarMapScreen.line(graphics, x, y, x, y + size, (float) size, ARGB.color(alpha, hash(i * 11) < 0.2 ? 0xFFE4C8 : 0xEAF2FF));
                        }
                    }
                }
                case WARP -> {
                    graphics.fill(0, 0, width, height, 0xFF04020A);
                    streaks(graphics, cx, cy, reach, seconds, 90, 0xEAF2FF, 0xC9A6FF, 0.9);
                }
            }
        }

        /** 从画面中心往外跑的流光，越往外越长越亮。 */
        private static void streaks(GuiGraphicsExtractor graphics, double cx, double cy, double reach, double seconds, int count, int hot, int warm,
                double speedScale) {
            for (int i = 0; i < count; i++) {
                double angle = hash(i * 3 + 1) * Math.PI * 2;
                double speed = (0.4 + hash(i * 3 + 2)) * speedScale;
                double phase = (hash(i * 3) + seconds * speed) % 1.0;
                double r0 = (0.04 + phase * phase) * reach;
                double length = (0.05 + 0.4 * phase) * reach;
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);
                int alpha = (int) (230 * Math.min(1, phase * 3));
                int color = ARGB.color(alpha, hash(i * 7) < 0.3 ? hot : warm);
                StarMapScreen.line(graphics, cx + cos * r0, cy + sin * r0, cx + cos * (r0 + length), cy + sin * (r0 + length), 1.2F, color);
            }
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            int x = this.width / 2;
            int y = this.height / 3;
            var pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(x, y);
            pose.scale(2.0F);
            graphics.centeredText(this.font, this.heading, 0, 0, 0xFFFFFFFF);
            pose.popMatrix();
            if (!this.destination.getString().isEmpty()) {
                graphics.centeredText(this.font, this.destination, x, y + 26, 0xFFD8E0EA);
            }
        }
    }

    /** 0..1 的伪随机数，同一个序号永远一样。 */
    private static double hash(int i) {
        long h = i * 0x9E3779B97F4A7C15L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
    }

    private static @Nullable Planet planetFor(ResourceKey<Level> dimension) {
        return Planets.bodyId(dimension).flatMap(id -> ClientGalaxy.galaxy().flatMap(galaxy -> galaxy.body(id))).orElse(null);
    }

    private static @Nullable StarSystem systemFor(ResourceKey<Level> dimension) {
        return SpaceDimensions.system(dimension).stream().boxed().findFirst()
                .flatMap(index -> ClientGalaxy.galaxy().flatMap(galaxy -> galaxy.system(index))).orElse(null);
    }
}
