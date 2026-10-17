package io.github.sweetcornna.universe.world.planet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.Hazard;
import io.github.sweetcornna.universe.core.galaxy.Planet;
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
import io.github.sweetcornna.universe.entity.AlienCreature;
import io.github.sweetcornna.universe.entity.AlienFlyer;
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
 * 调试命令 {@code /universe planet}，开发和测试用，提示文字不做翻译。
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
            new DynamicCommandExceptionType(id -> Component.literal("不是可着陆的星球：" + id));

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
        StringBuilder text = new StringBuilder("星团共 " + galaxy.systems().size() + " 个恒星系、" + galaxy.bodyCount() + " 个天体：");
        for (StarSystem system : galaxy.systems()) {
            long landable = system.bodies().stream().filter(Planet::landable).count();
            text.append(String.format(Locale.ROOT, "\n  %d %s（%s）%s，天体 %d 个，可着陆 %d 个",
                    system.index(), system.name().zh(), system.name().en(), system.star().starClass().nameZh(),
                    system.bodies().size(), landable));
        }
        source.sendSuccess(() -> Component.literal(text.toString()), false);
        return galaxy.systems().size();
    }

    private static int listBodies(CommandSourceStack source, int index) {
        Galaxy galaxy = Planets.galaxy(source.getServer());
        Optional<StarSystem> system = galaxy.system(index);
        if (system.isEmpty()) {
            source.sendFailure(Component.literal("没有编号为 " + index + " 的恒星系"));
            return 0;
        }
        StringBuilder text = new StringBuilder(system.get().name().zh() + " 恒星系：");
        for (Planet planet : system.get().bodies()) {
            text.append(String.format(Locale.ROOT, "\n  %s %s（%s）%s，%.0f °C，重力 %.2f g%s",
                    Planets.dimension(planet.id()).identifier(), planet.name().zh(), planet.name().en(),
                    planet.type().nameZh(), planet.temperatureCelsius(), planet.gravity(),
                    planet.landable() ? "" : "，不可着陆"));
        }
        source.sendSuccess(() -> Component.literal(text.toString()), false);
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
        source.sendSuccess(() -> Component.literal("星球天气设为 " + name + "，持续 " + seconds + " 秒"), true);
        return 1;
    }

    /** 当前所在星球的植物物种。 */
    private static int flora(CommandSourceStack source) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        Planet planet = Planets.planet(level).orElseThrow(() -> NOT_A_PLANET.create(level.dimension().identifier()));
        Flora flora = Flora.of(planet);
        StringBuilder text = new StringBuilder(String.format(Locale.ROOT, "%s 的植物：地面植物 %d 种，树 %d 种",
                planet.name().zh(), flora.plants().size(), flora.trees().size()));
        for (PlantSpecies plant : flora.plants()) {
            text.append(String.format(Locale.ROOT, "\n  %s（%s #%d）%s 生境 %s，多度 %.2f，颜色 %06X / %06X",
                    plant.name().zh(), plant.form().nameZh(), plant.variant(), plant.glowing() ? "发光，" : "",
                    plant.biotopes(), plant.abundance(), plant.body(), plant.detail()));
        }
        for (TreeSpecies tree : flora.trees()) {
            text.append(String.format(Locale.ROOT, "\n  %s（%s #%d）高 %d–%d，冠 %d%s，生境 %s，%s",
                    tree.name().zh(), tree.shape().nameZh(), tree.index(), tree.height(), tree.height() + tree.heightRange(),
                    tree.canopy(), tree.thick() ? "，粗干" : "", tree.biotopes(),
                    tree.bloomChance() > 0 ? (tree.bloomGlows() ? "开发光的花" : "开花") : "不开花"));
        }
        source.sendSuccess(() -> Component.literal(text.toString()), false);
        return 1;
    }

    /** 当前所在星球的动物物种，以及周围现有多少只。 */
    private static int fauna(CommandSourceStack source) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        Planet planet = Planets.planet(level).orElseThrow(() -> NOT_A_PLANET.create(level.dimension().identifier()));
        Fauna fauna = Fauna.of(planet);
        List<AlienCreature> around = FaunaSpawner.around(level, source.getPosition().x, source.getPosition().z);
        long flyers = around.stream().filter(c -> c instanceof AlienFlyer).count();
        StringBuilder text = new StringBuilder(String.format(Locale.ROOT, "%s 的动物：%d 种；周围 64 格内 %d 只（飞兽 %d），上限 %d",
                planet.name().zh(), fauna.species().size(), around.size(), flyers, FaunaSpawner.cap(planet)));
        for (CreatureSpecies s : fauna.species()) {
            text.append(String.format(Locale.ROOT, "\n  #%d %s（%s，%s）大小 %.2f，生命 %.0f，速度 %.2f%s，%d–%d 只一群，生境 %s，花纹 %s%s%s",
                    s.index(), s.name().zh(), s.plan().nameZh(), s.temperament().nameZh(), s.size(), s.health(), s.speed(),
                    s.attack() > 0 ? String.format(Locale.ROOT, "，攻击 %.1f", s.attack()) : "", s.groupMin(), s.groupMax(),
                    s.biotopes(), s.skin().patternType(), s.skin().glowingPattern() ? "（发光）" : "",
                    s.skin().glowingEyes() ? "，眼睛发光" : ""));
        }
        source.sendSuccess(() -> Component.literal(text.toString()), false);
        return 1;
    }

    /** 在面前刷几只指定物种的动物（不在星球上时用备用的动物表）。 */
    private static int spawnCreature(CommandSourceStack source, int index, int count) {
        ServerLevel level = source.getLevel();
        Fauna fauna = Planets.fauna(level).orElse(Fauna.FALLBACK);
        if (index >= fauna.species().size()) {
            source.sendFailure(Component.literal("这里只有 " + fauna.species().size() + " 种动物（编号 0–" + (fauna.species().size() - 1) + "）"));
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
        source.sendSuccess(() -> Component.literal("刷出了 " + done + " 只 " + species.name().zh()), true);
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
        source.sendSuccess(() -> Component.literal("排出了 " + done + "/" + fauna.species().size() + " 种动物"), true);
        return done;
    }

    /** 当前所在星球此刻的天空：每个天体的方向、视大小和亮面比例。 */
    private static int sky(CommandSourceStack source) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        PlanetEnvironment.Context context = PlanetEnvironment.context(level)
                .orElseThrow(() -> NOT_A_PLANET.create(level.dimension().identifier()));
        double days = level.getGameTime() / 24000.0;
        StringBuilder text = new StringBuilder(String.format(Locale.ROOT, "%s 的天空（第 %.2f 天）：",
                context.planet().name().zh(), days));
        for (SkyBody body : SkyModel.bodies(context.system(), context.planet(), days)) {
            String name = body.planet() == null ? context.system().star().starClass().nameZh() + "恒星" : body.planet().name().zh();
            text.append(String.format(Locale.ROOT, "\n  %s %s：经度 %.0f°，纬度 %.1f°，视直径 %.1f°，亮面 %.0f%%",
                    body.kind(), name, Math.toDegrees(body.longitude()), Math.toDegrees(body.latitude()),
                    body.angularDiameter(), body.illumination() * 100));
        }
        source.sendSuccess(() -> Component.literal(text.toString()), false);
        return 1;
    }

    private static int info(CommandSourceStack source, ResourceKey<Level> dimension) throws CommandSyntaxException {
        Galaxy galaxy = Planets.galaxy(source.getServer());
        Planet planet = Planets.bodyId(dimension).flatMap(galaxy::body)
                .orElseThrow(() -> NOT_A_PLANET.create(dimension.identifier()));
        TerrainProfile t = planet.terrain();
        String hazards = planet.hazards().isEmpty() ? "无"
                : planet.hazards().stream().map(Hazard::nameZh).collect(Collectors.joining("、"));
        String resources = planet.resources().stream()
                .map(d -> String.format(Locale.ROOT, "%s %.0f%%", d.resource().nameZh(), d.abundance() * 100))
                .collect(Collectors.joining("、"));
        String text = String.format(Locale.ROOT, """
                %s（%s）  %s  %s
                半径 %.2f 倍母星，重力 %.2f g，大气 %s %.2f atm，温度 %.0f °C（昼夜 ±%.0f），液体 %s
                昼夜 %s，天气 %s（频率 %.2f，风 %.2f%s），危害 %s
                地形：海平面 %d，基准 %d，大陆尺度 %.0f，起伏 %.0f，丘陵 %.0f，粗糙 %.2f，山脊 %.2f，台地 %.2f，陨坑 %.2f，洞穴 %.2f，悬垂 %.2f，尖塔 %.2f%s
                生态：植被 %.2f，树木 %.2f，动物 %.2f，植物 %d 种，动物 %d 种
                资源：%s""",
                planet.name().zh(), planet.name().en(), dimension.identifier(), planet.type().nameZh(),
                planet.radius(), planet.gravity(), planet.atmosphere().kind().nameZh(), planet.atmosphere().pressure(),
                planet.temperatureCelsius(), planet.temperatureSwing(), planet.liquid().nameZh(),
                planet.dayCycle().serializedName(), planet.weather().precipitation().nameZh(), planet.weather().frequency(),
                planet.weather().wind(), planet.weather().lightning() ? "，有雷暴" : "", hazards,
                t.seaLevel(), t.baseHeight(), t.continentScale(), t.heightVariation(), t.hilliness(), t.roughness(),
                t.ridges(), t.terraces(), t.craters(), t.caves(), t.overhangs(), t.spikes(),
                t.floatingIslands() ? "，有浮空岛" : "",
                planet.ecology().flora(), planet.ecology().trees(), planet.ecology().fauna(),
                planet.ecology().plantSpecies(), planet.ecology().animalSpecies(), resources);
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int teleport(CommandContext<CommandSourceStack> context, Optional<int[]> target) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Entity entity = source.getEntityOrException();
        ResourceKey<Level> key = dimension(context);
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
            source.sendFailure(Component.literal("传送失败：" + id));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "已传送到 %s (%d, %d, %d)", id, x, y, z)), false);
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
            source.sendSuccess(() -> Component.literal("创建维度 " + id + " 用时 " + millis + " ms"), false);
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
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "%s：生成 %d 个区块用时 %.0f ms，平均 %.1f ms/区块（区块坐标 %d, %d 起）",
                level.dimension().identifier(), count, millis, millis / count, originX, originZ)), false);
        return count;
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
