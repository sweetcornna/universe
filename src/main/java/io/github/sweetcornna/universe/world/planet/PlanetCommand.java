package io.github.sweetcornna.universe.world.planet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.ecology.Biotope;
import io.github.sweetcornna.universe.core.ecology.Husbandry;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.Hazard;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.Resource;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.galaxy.TerrainProfile;
import io.github.sweetcornna.universe.core.ecology.CreatureSpecies;
import io.github.sweetcornna.universe.core.ecology.Fauna;
import io.github.sweetcornna.universe.core.ecology.Flora;
import io.github.sweetcornna.universe.core.ecology.PlantSpecies;
import io.github.sweetcornna.universe.core.ecology.TreeSpecies;
import io.github.sweetcornna.universe.core.sky.SkyBody;
import io.github.sweetcornna.universe.core.sky.SkyModel;
import io.github.sweetcornna.universe.core.terrain.TerrainModel;
import io.github.sweetcornna.universe.discovery.Discoveries;
import io.github.sweetcornna.universe.entity.AlienCreature;
import io.github.sweetcornna.universe.entity.AlienFlyer;
import io.github.sweetcornna.universe.entity.CreatureHusbandry;
import io.github.sweetcornna.universe.entity.FaunaSpawner;
import io.github.sweetcornna.universe.world.DimensionTemplate;
import io.github.sweetcornna.universe.world.RuntimeDimensions;
import io.github.sweetcornna.universe.world.planet.gen.PlanetGenSpec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * 调试命令 {@code /universe planet}，开发和测试用。
 *
 * <ul>
 * <li>{@code list [恒星系]}：列出星团里的恒星系，或某个恒星系里的天体
 * <li>{@code info [星球]}：星球参数；省略时取执行者所在的星球
 * <li>{@code tp <星球> [x z]}：传送到星球地表；没给坐标时从原点附近找一块陆地
 * <li>{@code bench <星球> [边长]}：在远处没生成过的地方连续生成一片区块并计时。
 * 星球写成 {@code universe:vanilla_reference} 时用原版主世界地形的对照维度
 * </ul>
 * 星球用维度 ID 指定，例如 {@code universe:s3/p2}；母星是 {@code minecraft:overworld}。
 */
public final class PlanetCommand {
    private static final DynamicCommandExceptionType NOT_A_PLANET =
            new DynamicCommandExceptionType(id -> Component.translatable("commands.universe.planet.not_planet", String.valueOf(id)));

    private static final SuggestionProvider<CommandSourceStack> PLANETS = (context, builder) -> {
        Galaxy galaxy = Planets.galaxy(context.getSource().getServer());
        return SharedSuggestionProvider.suggestResource(galaxy.systems().stream()
                .flatMap(system -> system.bodies().stream())
                .filter(Planet::landable)
                .map(planet -> Planets.dimension(planet.id()).identifier()), builder);
    };

    private PlanetCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("planet")
                .then(Commands.literal("list")
                        .executes(context -> listSystems(context.getSource()))
                        .then(Commands.argument("system", IntegerArgumentType.integer(0))
                                .executes(context -> listBodies(context.getSource(), IntegerArgumentType.getInteger(context, "system")))))
                .then(Commands.literal("info")
                        .executes(context -> info(context.getSource(), context.getSource().getLevel().dimension()))
                        .then(Commands.argument("planet", IdentifierArgument.id())
                                .suggests(PLANETS)
                                .executes(context -> info(context.getSource(), dimension(context)))))
                .then(Commands.literal("flora")
                        .executes(context -> flora(context.getSource())))
                .then(Commands.literal("fauna")
                        .executes(context -> fauna(context.getSource()))
                        .then(Commands.literal("lineup")
                                .executes(context -> lineup(context.getSource())))
                        .then(Commands.literal("spawn")
                                .then(Commands.argument("species", IntegerArgumentType.integer(0))
                                        .executes(context -> spawnCreature(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "species"), 1))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 32))
                                                .executes(context -> spawnCreature(context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "species"),
                                                        IntegerArgumentType.getInteger(context, "count")))))))
                .then(Commands.literal("sky")
                        .executes(context -> sky(context.getSource())))
                .then(Commands.literal("weather")
                        .then(weather("clear", false, false))
                        .then(weather("normal", true, false))
                        .then(weather("storm", true, true)))
                .then(Commands.literal("tp")
                        .then(Commands.argument("planet", IdentifierArgument.id())
                                .suggests(PLANETS)
                                .executes(context -> teleport(context, Optional.empty()))
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(context -> teleport(context, Optional.of(new int[] {
                                                        IntegerArgumentType.getInteger(context, "x"),
                                                        IntegerArgumentType.getInteger(context, "z")})))))))
                .then(Commands.literal("bench")
                        .then(Commands.argument("planet", IdentifierArgument.id())
                                .suggests(PLANETS)
                                .executes(context -> bench(context, 6))
                                .then(Commands.argument("size", IntegerArgumentType.integer(1, 24))
                                        .executes(context -> bench(context, IntegerArgumentType.getInteger(context, "size"))))));
    }

    private static ResourceKey<Level> dimension(CommandContext<CommandSourceStack> context) {
        return RuntimeDimensions.key(IdentifierArgument.getId(context, "planet"));
    }

    private static int listSystems(CommandSourceStack source) {
        Galaxy galaxy = Planets.galaxy(source.getServer());
        MutableComponent text = Component.translatable("commands.universe.planet.systems", galaxy.systems().size(), galaxy.bodyCount());
        for (StarSystem system : galaxy.systems()) {
            long landable = system.bodies().stream().filter(Planet::landable).count();
            text.append("\n  ").append(Component.translatable("commands.universe.planet.systems.entry",
                    system.index(), system.name().zh(), system.name().en(),
                    name(system.star().starClass().nameZh(), system.star().starClass().nameEn()), system.bodies().size(), landable));
        }
        source.sendSuccess(() -> text, false);
        return galaxy.systems().size();
    }

    private static int listBodies(CommandSourceStack source, int index) {
        Galaxy galaxy = Planets.galaxy(source.getServer());
        Optional<StarSystem> system = galaxy.system(index);
        if (system.isEmpty()) {
            source.sendFailure(Component.translatable("commands.universe.planet.no_system", index));
            return 0;
        }
        MutableComponent text = Component.translatable("commands.universe.planet.bodies", Discoveries.bilingual(system.get().name()));
        for (Planet planet : system.get().bodies()) {
            text.append("\n  ").append(Component.translatable("commands.universe.planet.bodies.entry",
                    Component.translationArg(Planets.dimension(planet.id()).identifier()), planet.name().zh(), planet.name().en(),
                    name(planet.type().nameZh(), planet.type().nameEn()), number(planet.temperatureCelsius(), 0), number(planet.gravity(), 2),
                    planet.landable() ? Component.empty() : Component.translatable("commands.universe.planet.bodies.not_landable")));
        }
        source.sendSuccess(() -> text, false);
        return system.get().bodies().size();
    }

    /** {@code weather <名称> [秒数]}：设置当前星球的天气，默认持续 10 分钟。 */
    private static LiteralArgumentBuilder<CommandSourceStack> weather(String name, boolean active, boolean storm) {
        return Commands.literal(name)
                .executes(context -> setWeather(context.getSource(), name, active, storm, 600))
                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 1_000_000))
                        .executes(context -> setWeather(context.getSource(), name, active, storm,
                                IntegerArgumentType.getInteger(context, "seconds"))));
    }

    private static int setWeather(CommandSourceStack source, String name, boolean active, boolean storm, int seconds)
            throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        if (!PlanetWeather.force(level, active, storm, seconds * 20)) {
            throw NOT_A_PLANET.create(level.dimension().identifier());
        }
        source.sendSuccess(() -> Component.translatable("commands.universe.planet.weather",
                Component.translatable("commands.universe.planet.weather." + name), seconds), true);
        return 1;
    }

    /** 当前所在星球的植物物种。 */
    private static int flora(CommandSourceStack source) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        Planet planet = Planets.planet(level).orElseThrow(() -> NOT_A_PLANET.create(level.dimension().identifier()));
        Flora flora = Flora.of(planet);
        MutableComponent text = Component.translatable("commands.universe.planet.flora",
                Discoveries.bilingual(planet.name()), flora.plants().size(), flora.trees().size());
        for (PlantSpecies plant : flora.plants()) {
            text.append("\n  ").append(Component.translatable("commands.universe.planet.flora.plant",
                    Discoveries.bilingual(plant.name()), name(plant.form().nameZh(), plant.form().nameEn()), plant.variant(),
                    plant.glowing() ? Component.translatable("commands.universe.planet.flora.glowing") : Component.empty(),
                    biotopes(plant.biotopes()), number(plant.abundance(), 2),
                    String.format(Locale.ROOT, "%06X", plant.body()), String.format(Locale.ROOT, "%06X", plant.detail())));
        }
        for (TreeSpecies tree : flora.trees()) {
            String bloom = tree.bloomChance() > 0 ? (tree.bloomGlows() ? "glowing" : "plain") : "none";
            text.append("\n  ").append(Component.translatable("commands.universe.planet.flora.tree",
                    Discoveries.bilingual(tree.name()),
                    Component.translatable("discovery.universe.tree_shape." + tree.shape().name().toLowerCase(Locale.ROOT)), tree.index(),
                    tree.height(), tree.height() + tree.heightRange(), tree.canopy(),
                    tree.thick() ? Component.translatable("commands.universe.planet.flora.thick") : Component.empty(),
                    biotopes(tree.biotopes()), Component.translatable("commands.universe.planet.flora.bloom." + bloom)));
        }
        source.sendSuccess(() -> text, false);
        return 1;
    }

    /** 当前所在星球的动物物种，以及周围现有多少只。 */
    private static int fauna(CommandSourceStack source) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        Planet planet = Planets.planet(level).orElseThrow(() -> NOT_A_PLANET.create(level.dimension().identifier()));
        Fauna fauna = Fauna.of(planet);
        List<AlienCreature> around = FaunaSpawner.around(level, source.getPosition().x, source.getPosition().z);
        long flyers = around.stream().filter(c -> c instanceof AlienFlyer).count();
        MutableComponent text = Component.translatable("commands.universe.planet.fauna",
                Discoveries.bilingual(planet.name()), fauna.species().size(), around.size(), flyers, FaunaSpawner.cap(planet));
        for (CreatureSpecies s : fauna.species()) {
            text.append("\n  ").append(Component.translatable("commands.universe.planet.fauna.species",
                    s.index(), Discoveries.bilingual(s.name()), name(s.plan().nameZh(), s.plan().nameEn()),
                    Component.translatable("discovery.universe.temperament." + s.temperament().name().toLowerCase(Locale.ROOT)),
                    number(s.size(), 2), number(s.health(), 0), number(s.speed(), 2),
                    s.attack() > 0 ? Component.translatable("commands.universe.planet.fauna.attack", number(s.attack(), 1)) : Component.empty(),
                    s.groupMin(), s.groupMax(), biotopes(s.biotopes()), enumName("commands.universe.planet.pattern.", s.skin().patternType()),
                    s.skin().glowingPattern() ? Component.translatable("commands.universe.planet.fauna.glowing_pattern") : Component.empty(),
                    s.skin().glowingEyes() ? Component.translatable("commands.universe.planet.fauna.glowing_eyes") : Component.empty()));
            // 喂养（ADR-0017）
            text.append(Component.translatable("commands.universe.planet.fauna.husbandry",
                    CreatureHusbandry.dietName(Husbandry.diet(s.temperament())), CreatureHusbandry.product(s).getHoverName(),
                    Husbandry.productCount(s),
                    Husbandry.rideable(s) ? Component.translatable("commands.universe.planet.fauna.rideable") : Component.empty()));
        }
        source.sendSuccess(() -> text, false);
        return 1;
    }

    /** 在面前刷几只指定物种的动物（不在星球上时用备用的动物表）。 */
    private static int spawnCreature(CommandSourceStack source, int index, int count) {
        ServerLevel level = source.getLevel();
        Fauna fauna = Planets.fauna(level).orElse(Fauna.FALLBACK);
        if (index >= fauna.species().size()) {
            source.sendFailure(Component.translatable("commands.universe.planet.fauna.no_species", fauna.species().size(), fauna.species().size() - 1));
            return 0;
        }
        CreatureSpecies species = fauna.get(index);
        Vec3 look = source.getRotation() == null ? Vec3.ZERO : Vec3.directionFromRotation(0, source.getRotation().y);
        Vec3 front = source.getPosition().add(look.scale(4 + species.size() * 2));
        int spawned = 0;
        for (int i = 0; i < count * 4 && spawned < count; i++) {
            int x = (int) Math.floor(front.x) + (i == 0 ? 0 : level.getRandom().nextInt(7) - 3);
            int z = (int) Math.floor(front.z) + (i == 0 ? 0 : level.getRandom().nextInt(7) - 3);
            BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            if (FaunaSpawner.spawn(level, species, pos, level.getRandom()) != null) {
                spawned++;
            }
        }
        int done = spawned;
        source.sendSuccess(() -> Component.translatable("commands.universe.planet.fauna.spawned", done, Discoveries.bilingual(species.name())), true);
        return done;
    }

    /** 把这里的每种动物在面前排成一排（不动），方便对照外观。 */
    private static int lineup(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Fauna fauna = Planets.fauna(level).orElse(Fauna.FALLBACK);
        float yaw = source.getRotation() == null ? 0 : source.getRotation().y;
        Vec3 forward = Vec3.directionFromRotation(0, yaw);
        Vec3 right = Vec3.directionFromRotation(0, yaw + 90);
        double width = 0;
        for (CreatureSpecies species : fauna.species()) {
            width += species.size() * 1.6 + 1;
        }
        double offset = -width / 2;
        int spawned = 0;
        for (CreatureSpecies species : fauna.species()) {
            double half = (species.size() * 1.6 + 1) / 2;
            offset += half;
            Vec3 at = source.getPosition().add(forward.scale(7)).add(right.scale(offset));
            offset += half;
            BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at.x, 0, at.z));
            AlienCreature creature = FaunaSpawner.spawn(level, species, pos, level.getRandom());
            if (creature != null) {
                creature.setNoAi(true);
                // 面朝玩家
                creature.setYRot(yaw + 180);
                creature.setYHeadRot(yaw + 180);
                creature.setYBodyRot(yaw + 180);
                spawned++;
            }
        }
        int done = spawned;
        source.sendSuccess(() -> Component.translatable("commands.universe.planet.fauna.lineup", done, fauna.species().size()), true);
        return done;
    }

    /** 当前所在星球此刻的天空：每个天体的方向、视大小和亮面比例。 */
    private static int sky(CommandSourceStack source) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        PlanetEnvironment.Context context = PlanetEnvironment.context(level)
                .orElseThrow(() -> NOT_A_PLANET.create(level.dimension().identifier()));
        double days = level.getGameTime() / 24000.0;
        MutableComponent text = Component.translatable("commands.universe.planet.sky", Discoveries.bilingual(context.planet().name()), number(days, 2));
        for (SkyBody body : SkyModel.bodies(context.system(), context.planet(), days)) {
            Component name = body.planet() == null
                    ? name(context.system().star().starClass().nameZh(), context.system().star().starClass().nameEn())
                    : Discoveries.bilingual(body.planet().name());
            text.append("\n  ").append(Component.translatable("commands.universe.planet.sky.body",
                    enumName("commands.universe.planet.sky.kind.", body.kind()), name, number(Math.toDegrees(body.longitude()), 0), number(Math.toDegrees(body.latitude()), 1),
                    number(body.angularDiameter(), 1), number(body.illumination() * 100, 0)));
        }
        source.sendSuccess(() -> text, false);
        return 1;
    }

    private static int info(CommandSourceStack source, ResourceKey<Level> dimension) throws CommandSyntaxException {
        Galaxy galaxy = Planets.galaxy(source.getServer());
        Planet planet = Planets.bodyId(dimension).flatMap(galaxy::body)
                .orElseThrow(() -> NOT_A_PLANET.create(dimension.identifier()));
        TerrainProfile t = planet.terrain();
        Component separator = Component.translatable("commands.universe.planet.info.separator");
        Component hazards = planet.hazards().isEmpty() ? Component.translatable("commands.universe.planet.info.no_hazards")
                : ComponentUtils.formatList(planet.hazards(), separator, (Hazard hazard) -> name(hazard.nameZh(), hazard.nameEn()));
        Component resources = ComponentUtils.formatList(planet.resources(), separator,
                (Resource.Deposit d) -> Component.translatable("commands.universe.planet.info.resource",
                        name(d.resource().nameZh(), d.resource().nameEn()), number(d.abundance() * 100, 0)));
        MutableComponent text = Component.translatable("commands.universe.planet.info.title",
                planet.name().zh(), planet.name().en(), Component.translationArg(dimension.identifier()),
                name(planet.type().nameZh(), planet.type().nameEn()));
        text.append("\n").append(Component.translatable("commands.universe.planet.info.climate",
                number(planet.radius(), 2), number(planet.gravity(), 2),
                name(planet.atmosphere().kind().nameZh(), planet.atmosphere().kind().nameEn()), number(planet.atmosphere().pressure(), 2),
                number(planet.temperatureCelsius(), 0), number(planet.temperatureSwing(), 0),
                name(planet.liquid().nameZh(), planet.liquid().nameEn())));
        text.append("\n").append(Component.translatable("commands.universe.planet.info.weather",
                Component.translatable("commands.universe.planet.day_cycle." + planet.dayCycle().serializedName()),
                name(planet.weather().precipitation().nameZh(), planet.weather().precipitation().nameEn()),
                number(planet.weather().frequency(), 2), number(planet.weather().wind(), 2),
                planet.weather().lightning() ? Component.translatable("commands.universe.planet.info.lightning") : Component.empty(), hazards));
        text.append("\n").append(Component.translatable("commands.universe.planet.info.terrain",
                t.seaLevel(), t.baseHeight(), number(t.continentScale(), 0), number(t.heightVariation(), 0), number(t.hilliness(), 0),
                number(t.roughness(), 2), number(t.ridges(), 2), number(t.terraces(), 2), number(t.craters(), 2), number(t.caves(), 2),
                number(t.overhangs(), 2), number(t.spikes(), 2),
                t.floatingIslands() ? Component.translatable("commands.universe.planet.info.floating_islands") : Component.empty()));
        text.append("\n").append(Component.translatable("commands.universe.planet.info.ecology",
                number(planet.ecology().flora(), 2), number(planet.ecology().trees(), 2), number(planet.ecology().fauna(), 2),
                planet.ecology().plantSpecies(), planet.ecology().animalSpecies()));
        text.append("\n").append(Component.translatable("commands.universe.planet.info.resources", resources));
        source.sendSuccess(() -> text, false);
        return 1;
    }

    private static int teleport(CommandContext<CommandSourceStack> context, Optional<int[]> target) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Entity entity = source.getEntityOrException();
        // 母星的天体编号（universe:s0/p1 这种）也指主世界，不能另建一个同样的维度
        ResourceKey<Level> typed = dimension(context);
        ResourceKey<Level> key = Planets.bodyId(typed).map(Planets::dimension).orElse(typed);
        Identifier id = key.identifier();
        Galaxy galaxy = Planets.galaxy(server);
        ServerLevel level = level(source, key);
        BlockPos spot;
        if (key == Level.OVERWORLD) {
            BlockPos respawn = level.getRespawnData().pos();
            spot = surface(level, target.map(t -> t[0]).orElse(respawn.getX()), target.map(t -> t[1]).orElse(respawn.getZ()));
        } else {
            Planet planet = Planets.generatedPlanet(galaxy, key).orElseThrow(() -> NOT_A_PLANET.create(id));
            spot = target.isPresent() ? surface(level, target.get()[0], target.get()[1])
                    : findLanding(level, PlanetGenSpec.of(planet).model());
        }
        int x = spot.getX();
        int y = spot.getY();
        int z = spot.getZ();
        TeleportTransition transition = new TeleportTransition(level, new Vec3(x + 0.5, y, z + 0.5), Vec3.ZERO,
                entity.getYRot(), entity.getXRot(), TeleportTransition.PLACE_PORTAL_TICKET);
        if (entity.teleport(transition) == null) {
            source.sendFailure(Component.translatable("commands.universe.planet.tp.failed", Component.translationArg(id)));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.universe.planet.tp", Component.translationArg(id), x, y, z), false);
        return 1;
    }

    /** 原版地形的对照维度，只给 bench 用。 */
    private static final Identifier VANILLA_REFERENCE = Universe.id("vanilla_reference");

    /** 主世界或星球维度；星球维度没加载时先创建。 */
    private static ServerLevel level(CommandSourceStack source, ResourceKey<Level> key) throws CommandSyntaxException {
        MinecraftServer server = source.getServer();
        if (key == Level.OVERWORLD) {
            return server.overworld();
        }
        if (key.identifier().equals(VANILLA_REFERENCE)) {
            return RuntimeDimensions.getOrCreate(server, key, DimensionTemplate.VANILLA);
        }
        Identifier id = key.identifier();
        Planets.generatedPlanet(Planets.galaxy(server), key).orElseThrow(() -> NOT_A_PLANET.create(id));
        long startNanos = System.nanoTime();
        ServerLevel level = RuntimeDimensions.getOrCreate(server, key, DimensionTemplate.PLANET);
        long millis = (System.nanoTime() - startNanos) / 1_000_000;
        if (millis > 50) {
            source.sendSuccess(() -> Component.translatable("commands.universe.planet.created", Component.translationArg(id), millis), false);
        }
        return level;
    }

    /**
     * 生成速度基准：在离原点很远、没生成过的地方同步生成 size × size 个完整区块。
     * 计时包含为它们生成的邻近区块（地物阶段要用到周围一圈），所以只用来横向对比各维度。
     */
    private static int bench(CommandContext<CommandSourceStack> context, int size) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = level(source, dimension(context));
        // 每次换一块新地方：区块坐标 3000 起，按时间错开
        int originX = 3000 + (int) Math.floorMod(System.nanoTime() / 1000, 2000L) * 40;
        int originZ = 3000;
        long start = System.nanoTime();
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                level.getChunk(originX + dx, originZ + dz);
            }
        }
        double millis = (System.nanoTime() - start) / 1e6;
        int count = size * size;
        source.sendSuccess(() -> Component.translatable("commands.universe.planet.bench", Component.translationArg(level.dimension().identifier()),
                count, number(millis, 0), number(millis / count, 1), originX, originZ), false);
        return count;
    }

    /** 中英文两个名字都传过去，客户端按语言挑一个。 */
    private static Component name(String zh, String en) {
        return Component.translatable("universe.name", zh, en);
    }

    /** 没有中英文名字的枚举，按"前缀 + 小写的常量名"找语言键。 */
    private static Component enumName(String prefix, Enum<?> value) {
        return Component.translatable(prefix + value.name().toLowerCase(Locale.ROOT));
    }

    private static Component biotopes(Set<Biotope> biotopes) {
        return ComponentUtils.formatList(biotopes, Component.translatable("commands.universe.planet.info.separator"),
                (Biotope biotope) -> enumName("commands.universe.planet.biotope.", biotope));
    }

    private static String number(double value, int digits) {
        return String.format(Locale.ROOT, "%." + digits + "f", value);
    }

    /** 生成该列所在区块，返回地表上方第一格。 */
    private static BlockPos surface(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
    }

    /**
     * 找一个能站的落点：先用地形模型挑出高出液面、不太陡的候选列（不生成区块），
     * 再生成区块确认地表是实心方块（避开洞口竖井、水面）。
     */
    private static BlockPos findLanding(ServerLevel level, TerrainModel model) {
        int tries = 0;
        for (int[] candidate : landCandidates(model)) {
            BlockPos pos = surface(level, candidate[0], candidate[1]);
            BlockState ground = level.getBlockState(pos.below());
            boolean solid = ground.getFluidState().isEmpty() && ground.isSolid();
            if (solid && Math.abs(pos.getY() - model.sample(candidate[0], candidate[1]).height()) < 6) {
                return pos;
            }
            if (++tries >= 12) {
                break;
            }
        }
        return surface(level, 0, 0);
    }

    /** 星球上离原点最近的一块陆地（不生成区块）；找不到就是原点。飞船第一次降落时以它为中心。 */
    public static int[] landingColumn(Planet planet) {
        List<int[]> candidates = landCandidates(PlanetGenSpec.of(planet).model());
        return candidates.isEmpty() ? new int[] {0, 0} : candidates.get(0);
    }

    /** 从原点向外螺旋搜索高出液面、不太陡的陆地列。 */
    static List<int[]> landCandidates(TerrainModel model) {
        List<int[]> result = new ArrayList<>();
        for (int ring = 0; ring <= 64; ring++) {
            int r = ring * 48;
            int steps = Math.max(1, ring * 8);
            for (int i = 0; i < steps; i++) {
                double angle = Math.PI * 2 * i / steps;
                int x = (int) Math.round(Math.cos(angle) * r);
                int z = (int) Math.round(Math.sin(angle) * r);
                TerrainModel.Sample s = model.sample(x, z);
                if (s.elevation() > 3 && s.elevation() < 90
                        && Math.abs(model.sample(x + 4, z).height() - s.height()) < 3
                        && Math.abs(model.sample(x, z + 4).height() - s.height()) < 3) {
                    result.add(new int[] {x, z});
                    if (result.size() >= 16) {
                        return result;
                    }
                }
            }
        }
        return result;
    }
}
