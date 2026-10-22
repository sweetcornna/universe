package io.github.sweetcornna.universe.discovery;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import io.github.sweetcornna.universe.advancement.MilestoneTrigger;
import io.github.sweetcornna.universe.block.FloraBlocks;
import io.github.sweetcornna.universe.block.PlanetBloomBlock;
import io.github.sweetcornna.universe.block.PlanetLeavesBlock;
import io.github.sweetcornna.universe.block.PlanetLogBlock;
import io.github.sweetcornna.universe.block.PlanetPlantBlock;
import io.github.sweetcornna.universe.core.discovery.Catalog;
import io.github.sweetcornna.universe.core.discovery.DiscoveryKey;
import io.github.sweetcornna.universe.core.discovery.DiscoveryLog;
import io.github.sweetcornna.universe.core.discovery.Survey;
import io.github.sweetcornna.universe.core.ecology.Flora;
import io.github.sweetcornna.universe.core.ecology.PlantSpecies;
import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.procgen.NameGenerator.Name;
import io.github.sweetcornna.universe.entity.AlienCreature;
import io.github.sweetcornna.universe.entity.AquaticCreature;
import io.github.sweetcornna.universe.mission.Missions;
import io.github.sweetcornna.universe.network.AnalyzePayload;
import io.github.sweetcornna.universe.network.DiscoveryPayload;
import io.github.sweetcornna.universe.network.RenamePayload;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 发现日志的服务端逻辑（ADR-0012）：飞进恒星系、降落到星球时自动记下，分析目镜分析完了记下，
 * 第一个发现的玩家拿勘探数据；改名；同步给所有玩家。
 */
public final class Discoveries {
    /** 分析目镜够得着多远：动物、方块（格）。 */
    public static final double ENTITY_RANGE = 40;
    public static final double BLOCK_RANGE = 20;
    private static final TagKey<Block> ORES = Tags.BlockItems.ORES.block();

    private Discoveries() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                chartHome(player.level().getServer());
                PacketDistributor.sendToPlayer(player, DiscoveryPayload.of(true, log(player.level().getServer()).entries(), 0));
                explore(player, player.level().dimension());
            }
        });
        gameBus.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                explore(player, event.getTo());
            }
        });
    }

    public static DiscoveryLog log(MinecraftServer server) {
        return DiscoveryData.get(server).log();
    }

    /** 母星系、母星和它的卫星开局就在日志里，没有发现者。 */
    private static void chartHome(MinecraftServer server) {
        DiscoveryData data = DiscoveryData.get(server);
        StarSystem home = Planets.galaxy(server).home();
        boolean changed = data.log().discover(DiscoveryKey.system(home.index()), null, "", 0).isPresent();
        for (Planet planet : home.bodies()) {
            if (planet.id().parent().equals(GalaxyGenerator.HOME_PLANET_ID)) {
                changed |= data.log().discover(DiscoveryKey.body(planet.id()), null, "", 0).isPresent();
            }
        }
        if (changed) {
            data.setDirty();
        }
    }

    /** 到了一个维度：太空记下恒星系，星球记下恒星系和星球。 */
    private static void explore(ServerPlayer player, ResourceKey<Level> dimension) {
        OptionalInt space = SpaceDimensions.system(dimension);
        if (space.isPresent()) {
            discover(player, DiscoveryKey.system(space.getAsInt()));
            return;
        }
        Planets.bodyId(dimension).ifPresent(id -> discover(player, DiscoveryKey.body(id)));
    }

    /**
     * 记下一次发现（没发现过的上一级也一起记下）。第一个发现的玩家拿勘探数据，所有玩家收到同步。
     *
     * @return 是不是新发现
     */
    public static boolean discover(ServerPlayer player, DiscoveryKey key) {
        MinecraftServer server = player.level().getServer();
        Galaxy galaxy = Planets.galaxy(server);
        if (Catalog.name(key, galaxy).isEmpty() && key.kind() != DiscoveryKey.Kind.MINERAL) {
            return false;
        }
        key.parent().ifPresent(parent -> discover(player, parent));
        DiscoveryData data = DiscoveryData.get(server);
        long day = server.overworld().getGameTime() / 24000L;
        Optional<DiscoveryLog.Entry> entry = data.log().discover(key, player.getUUID(), player.getGameProfile().name(), day);
        if (entry.isEmpty()) {
            return false;
        }
        data.setDirty();
        int reward = Survey.reward(key, galaxy);
        Component name = name(server, key);
        player.sendSystemMessage(Component.translatable("message.universe.discovery.new",
                Component.translatable("discovery.universe.kind." + kindName(key)), name.copy().withStyle(ChatFormatting.AQUA), reward)
                .withStyle(ChatFormatting.GRAY));
        // 一颗星球上的动植物全部发现了
        if (key.kind() == DiscoveryKey.Kind.CREATURE || key.kind() == DiscoveryKey.Kind.PLANT || key.kind() == DiscoveryKey.Kind.TREE) {
            MilestoneTrigger.reach(player, MilestoneTrigger.SPECIES);
            DiscoveryKey body = DiscoveryKey.body(key.body());
            Survey.Census census = Catalog.planet(key, galaxy).map(Survey::census).orElse(Survey.Census.NONE);
            if (Survey.complete(data.log(), body, census)) {
                reward += Survey.COMPLETION_BONUS;
                MilestoneTrigger.reach(player, MilestoneTrigger.SURVEY);
                player.sendSystemMessage(Component.translatable("message.universe.discovery.complete", name(server, body),
                        Survey.COMPLETION_BONUS).withStyle(ChatFormatting.GOLD));
            }
        }
        give(player, reward);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.4F);
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(other, DiscoveryPayload.of(false, List.of(entry.get()), other == player ? reward : 0));
        }
        return true;
    }

    private static void give(ServerPlayer player, int count) {
        if (count <= 0) {
            return;
        }
        ItemStack stack = new ItemStack(UniverseItems.SURVEY_DATA.get(), count);
        player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
    }

    private static String kindName(DiscoveryKey key) {
        return key.kind().name().toLowerCase(java.util.Locale.ROOT);
    }

    /** 显示用的名字：改过名就用新名字，否则用程序生成的名字（中英文两套），矿物用方块的名字。 */
    public static Component name(MinecraftServer server, DiscoveryKey key) {
        Optional<String> custom = log(server).get(key).map(DiscoveryLog.Entry::customName);
        if (custom.isPresent()) {
            return Component.literal(custom.get());
        }
        if (key.kind() == DiscoveryKey.Kind.MINERAL) {
            return Optional.ofNullable(net.minecraft.resources.Identifier.tryParse(key.mineral()))
                    .flatMap(BuiltInRegistries.BLOCK::getOptional)
                    .map(Block::getName)
                    .orElse(Component.literal(key.mineral()));
        }
        return Catalog.name(key, Planets.galaxy(server)).map(Discoveries::bilingual).orElse(Component.literal(key.id()));
    }

    /** 程序生成的名字换成发现日志里的名字（改过名的两种语言都用新名字）。 */
    public static Name resolve(MinecraftServer server, DiscoveryKey key, Name generated) {
        return log(server).get(key).map(DiscoveryLog.Entry::customName).map(custom -> new Name(custom, custom)).orElse(generated);
    }

    /** 中文界面显示中文名、其他语言显示英文名（语言文件里 {@code universe.name} 只取对应的那个参数）。 */
    public static Component bilingual(Name name) {
        return Component.translatable("universe.name", name.zh(), name.en());
    }

    /** 分析目镜分析完了一样东西。 */
    public static void handleAnalyze(AnalyzePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !player.getMainHandItem().is(UniverseItems.MULTITOOL.get())) {
            return;
        }
        ServerLevel level = player.level();
        Optional<DiscoveryKey> key = switch (payload.target()) {
            case ENTITY -> creature(player, level, payload.entity());
            case BLOCK -> block(player, level, payload.pos());
            case BODY -> BodyId.parse(payload.body())
                    .filter(id -> currentSystem(level).orElse(-1) == id.system())
                    .map(DiscoveryKey::body);
            case STAR -> currentSystem(level).stream().boxed().findFirst().map(DiscoveryKey::system);
        };
        if (key.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.universe.discovery.unknown"));
            return;
        }
        Missions.onAnalyzed(player, key.get());
        if (!discover(player, key.get())) {
            DiscoveryLog.Entry entry = log(level.getServer()).get(key.get()).orElse(null);
            Component by = entry == null || entry.discoverer() == null
                    ? Component.translatable("discovery.universe.charted")
                    : Component.literal(entry.discovererName());
            player.sendOverlayMessage(Component.translatable("message.universe.discovery.known", name(level.getServer(), key.get()), by));
        }
    }

    private static Optional<DiscoveryKey> creature(ServerPlayer player, ServerLevel level, int id) {
        Entity entity = level.getEntity(id);
        if (entity == null || player.distanceTo(entity) > ENTITY_RANGE + 4) {
            return Optional.empty();
        }
        // 水生动物（ADR-0026）的物种序号从 Fauna.MARINE_INDEX 起，和陆地的不会撞上
        int index;
        if (entity instanceof AlienCreature creature) {
            index = creature.species().index();
        } else if (entity instanceof AquaticCreature creature) {
            index = creature.species().index();
        } else {
            return Optional.empty();
        }
        return Planets.bodyId(level.dimension()).map(body -> DiscoveryKey.creature(body, index));
    }

    private static Optional<DiscoveryKey> block(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || player.distanceToSqr(Vec3.atCenterOf(pos)) > (BLOCK_RANGE + 4) * (BLOCK_RANGE + 4)) {
            return Optional.empty();
        }
        Optional<BodyId> body = Planets.bodyId(level.dimension());
        if (body.isEmpty()) {
            return Optional.empty();
        }
        BlockState state = level.getBlockState(pos);
        if (state.is(ORES)) {
            return Optional.of(DiscoveryKey.mineral(body.get(), BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()));
        }
        Optional<Planet> planet = Planets.generatedPlanet(Planets.galaxy(level.getServer()), level.dimension());
        if (planet.isEmpty()) {
            return Optional.empty();
        }
        Flora flora = Flora.of(planet.get());
        if (state.getBlock() instanceof PlanetPlantBlock plant) {
            int variant = state.getValue(FloraBlocks.VARIANT);
            for (PlantSpecies species : flora.plants()) {
                if (species.form() == plant.form() && species.variant() == variant) {
                    return Optional.of(DiscoveryKey.plant(body.get(), species.index()));
                }
            }
            return Optional.empty();
        }
        if (state.getBlock() instanceof PlanetLogBlock || state.getBlock() instanceof PlanetLeavesBlock
                || state.getBlock() instanceof PlanetBloomBlock) {
            int variant = state.getValue(FloraBlocks.VARIANT);
            return variant < flora.trees().size() ? Optional.of(DiscoveryKey.tree(body.get(), variant)) : Optional.empty();
        }
        return Optional.empty();
    }

    /** 玩家所在的恒星系：太空维度、星球维度，主世界算母星系。 */
    public static OptionalInt currentSystem(Level level) {
        OptionalInt space = SpaceDimensions.system(level.dimension());
        if (space.isPresent()) {
            return space;
        }
        return Planets.bodyId(level.dimension()).map(id -> OptionalInt.of(id.system())).orElse(OptionalInt.empty());
    }

    /** 发现者改名。 */
    public static void handleRename(RenamePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        Optional<DiscoveryKey> key = DiscoveryKey.parse(payload.key());
        if (key.isEmpty()) {
            return;
        }
        DiscoveryData data = DiscoveryData.get(server);
        DiscoveryLog.RenameResult result = data.log().rename(key.get(), player.getUUID(), payload.name());
        if (result != DiscoveryLog.RenameResult.OK) {
            player.sendOverlayMessage(Component.translatable("message.universe.discovery.rename_" + result.name().toLowerCase(java.util.Locale.ROOT)));
            return;
        }
        data.setDirty();
        List<DiscoveryLog.Entry> changed = new ArrayList<>();
        data.log().get(key.get()).ifPresent(changed::add);
        PacketDistributor.sendToAllPlayers(DiscoveryPayload.of(false, changed, 0));
        player.sendOverlayMessage(Component.translatable("message.universe.discovery.renamed", name(server, key.get())));
    }
}
