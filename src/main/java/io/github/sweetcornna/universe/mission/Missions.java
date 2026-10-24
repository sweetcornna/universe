package io.github.sweetcornna.universe.mission;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.advancement.MilestoneTrigger;
import io.github.sweetcornna.universe.civ.Civ;
import io.github.sweetcornna.universe.core.civ.Guild;
import io.github.sweetcornna.universe.core.civ.GuildMissions;
import io.github.sweetcornna.universe.core.civ.Race;
import io.github.sweetcornna.universe.core.civ.Standing;
import io.github.sweetcornna.universe.core.discovery.DiscoveryKey;
import io.github.sweetcornna.universe.core.discovery.Survey;
import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.mission.LandmarkTarget;
import io.github.sweetcornna.universe.core.mission.Mission;
import io.github.sweetcornna.universe.core.mission.MissionBoard;
import io.github.sweetcornna.universe.core.mission.MissionKind;
import io.github.sweetcornna.universe.core.poi.LandmarkLayout;
import io.github.sweetcornna.universe.core.poi.LandmarkType;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;
import io.github.sweetcornna.universe.core.tech.ModuleLoot;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.core.tech.TechModule;
import io.github.sweetcornna.universe.core.trade.Economy;
import io.github.sweetcornna.universe.discovery.Discoveries;
import io.github.sweetcornna.universe.network.MissionActionPayload;
import io.github.sweetcornna.universe.network.OpenMissionBoardPayload;
import io.github.sweetcornna.universe.perf.ServerPerf;
import io.github.sweetcornna.universe.pirate.Pirates;
import io.github.sweetcornna.universe.poi.Landmarks;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.registry.UniverseStats;
import io.github.sweetcornna.universe.registry.sound.WorldSounds;
import io.github.sweetcornna.universe.sentinel.Sentinel;
import io.github.sweetcornna.universe.trade.Credits;
import io.github.sweetcornna.universe.trade.Trading;
import io.github.sweetcornna.universe.world.planet.PlanetCommand;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 空间站的任务板（ADR-0017）：今天的三个任务、接任务、放弃、交付，以及每秒更新进度。
 *
 * <p>任务本身在 {@link MissionBoard} 里由恒星系和游戏日算出来，不存档；玩家身上只存接了的那个和今天做完的。
 */
public final class Missions {
    /** 离任务板多远以内能操作。 */
    private static final double REACH = 6;
    /** 地标任务挑这几种：走得进去、有东西看的。 */
    private static final List<LandmarkType> TARGET_TYPES = List.of(LandmarkType.OBSERVATORY, LandmarkType.ABANDONED_BUILDING,
            LandmarkType.KNOWLEDGE_STONE, LandmarkType.SETTLEMENT);
    /** 地标离着陆点最远多少格。 */
    private static final int TARGET_RANGE = 1000;
    /** 地标任务记"到过"用的标记，放在 {@link MissionState.Active#analyzed()} 里。 */
    private static final String VISITED = "visited";
    /** 每个恒星系的地标任务目标（算一次要走一遍地形），键是星团种子加恒星系。 */
    private static final Map<String, List<LandmarkTarget>> TARGETS = new HashMap<>();

    private Missions() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(PlayerTickEvent.Post.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 7) {
                long start = ServerPerf.begin();
                update(player);
                ServerPerf.end("player/missions", start);
            }
        });
        gameBus.addListener(LivingDeathEvent.class, Missions::onDeath);
    }

    /** 地上的守卫机被玩家打掉（手上的武器、开着的载具都算）：记一架，守卫机任务数这个。 */
    private static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Sentinel) || event.getEntity().level().isClientSide()) {
            return;
        }
        Entity source = event.getSource().getEntity();
        ServerPlayer killer = source != null && source.getControllingPassenger() instanceof ServerPlayer driver ? driver
                : Pirates.killer(event.getSource());
        if (killer != null) {
            killer.awardStat(UniverseStats.SENTINELS_DESTROYED.get());
        }
    }

    public static MissionState state(ServerPlayer player) {
        return player.getData(UniverseAttachments.MISSIONS);
    }

    private static void set(ServerPlayer player, MissionState state) {
        player.setData(UniverseAttachments.MISSIONS, state);
    }

    private static long day(MinecraftServer server) {
        return MissionBoard.day(server.overworld().getGameTime());
    }

    /** 今天这座空间站的任务。 */
    public static List<Mission> offers(MinecraftServer server, int system) {
        Optional<StarSystem> star = Planets.galaxy(server).system(system);
        if (star.isEmpty()) {
            return List.of();
        }
        return MissionBoard.offers(star.get().seed(), system, Economy.of(star.get()), day(server), Trading.catalog(server), species(star.get()));
    }

    /** 恒星系里能分析的动植物一共多少种。 */
    private static int species(StarSystem star) {
        int total = 0;
        for (Planet planet : star.bodies()) {
            total += Survey.census(planet).total();
        }
        return total;
    }

    // ---- 公会使者（ADR-0027 §六）

    /** 公会使者今天给的任务：种类、目标对谁都一样，报酬看玩家在这个公会的档。 */
    public static List<Mission> envoyOffers(MinecraftServer server, int system, Guild guild, Standing.Tier tier) {
        Optional<StarSystem> star = Planets.galaxy(server).system(system);
        if (star.isEmpty()) {
            return List.of();
        }
        return MissionBoard.envoyOffers(star.get().seed(), system, Economy.of(star.get()), day(server), Trading.catalog(server),
                species(star.get()), GuildMissions.kinds(guild), guild.id(), GuildMissions.rewardFactor(tier),
                () -> landmarkTargets(server, star.get()));
    }

    /** 地标任务能去的地方：这个恒星系里能登陆的星球（母星没有地标）上，着陆点附近每种能走进去的地标最近的一处。 */
    private static List<LandmarkTarget> landmarkTargets(MinecraftServer server, StarSystem star) {
        String key = Planets.galaxySeed(server) + "/" + star.index();
        synchronized (TARGETS) {
            List<LandmarkTarget> cached = TARGETS.get(key);
            if (cached != null) {
                return cached;
            }
        }
        List<LandmarkTarget> targets = new ArrayList<>();
        for (Planet planet : star.bodies()) {
            if (!planet.landable() || planet.id().equals(GalaxyGenerator.HOME_PLANET_ID)) {
                continue;
            }
            Optional<LandmarkLayout> layout = Landmarks.layout(planet);
            if (layout.isEmpty()) {
                continue;
            }
            int[] landing = PlanetCommand.landingColumn(planet);
            for (LandmarkType type : TARGET_TYPES) {
                layout.get().nearest(type, landing[0], landing[1], TARGET_RANGE).ifPresent(landmark -> targets.add(new LandmarkTarget(
                        planet.id().path(), type.id(), landmark.x(), landmark.z(),
                        (int) Math.round(Math.sqrt(landmark.distanceSq(landing[0], landing[1]))))));
            }
        }
        List<LandmarkTarget> result = List.copyOf(targets);
        synchronized (TARGETS) {
            TARGETS.put(key, result);
        }
        return result;
    }

    /** 今天做完的任务：昨天以前的记录清掉。 */
    private static List<String> doneToday(MissionState state, long day) {
        List<String> done = new ArrayList<>();
        for (String id : state.done()) {
            String[] parts = id.split(":");
            if (parts.length == 3 && parts[1].equals(Long.toString(day))) {
                done.add(id);
            }
        }
        return done;
    }

    private static Component stationName(MinecraftServer server, int system) {
        return Component.translatable("gui.universe.trade.station", Discoveries.name(server, DiscoveryKey.system(system)));
    }

    // ---- 任务板

    /** 右键任务板。 */
    public static void open(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.level();
        OptionalInt system = SpaceDimensions.system(level.dimension());
        if (system.isEmpty()) {
            return;
        }
        List<Mission> offers = offers(level.getServer(), system.getAsInt());
        List<Integer> states = states(player, offers);
        PacketDistributor.sendToPlayer(player, new OpenMissionBoardPayload(pos, system.getAsInt(), offers, states, state(player).active(), have(player)));
    }

    public static void handleAction(MissionActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.level();
        OptionalInt system = SpaceDimensions.system(level.dimension());
        if (system.isEmpty() || player.distanceToSqr(Vec3.atCenterOf(payload.pos())) > REACH * REACH
                || !level.getBlockState(payload.pos()).is(UniverseBlocks.MISSION_BOARD.get())) {
            return;
        }
        Component message = switch (payload.action()) {
            case MissionActionPayload.ACCEPT -> accept(player, system.getAsInt(), payload.slot());
            case MissionActionPayload.ABANDON -> abandon(player);
            case MissionActionPayload.HAND_IN -> handIn(player, system.getAsInt());
            default -> null;
        };
        if (message != null) {
            player.sendOverlayMessage(message);
        }
        open(player, payload.pos());
    }

    private static Component accept(ServerPlayer player, int system, int slot) {
        List<Mission> offers = offers(player.level().getServer(), system);
        if (slot < 0 || slot >= offers.size()) {
            return null;
        }
        return accept(player, offers.get(slot));
    }

    /** 接一个任务（任务板和公会使者共用）：手上有任务、今天做过的不能接。返回提示。 */
    public static Component accept(ServerPlayer player, Mission mission) {
        MinecraftServer server = player.level().getServer();
        MissionState state = state(player);
        if (state.active().isPresent()) {
            return Component.translatable("message.universe.mission.busy");
        }
        if (doneToday(state, day(server)).contains(mission.id())) {
            return Component.translatable("message.universe.mission.done_today");
        }
        long baseline = switch (mission.kind()) {
            case BOUNTY -> stat(player, UniverseStats.PIRATES_DESTROYED.get());
            case MINING -> stat(player, UniverseStats.ASTEROIDS_MINED.get());
            case SENTINELS -> sentinels(player);
            default -> 0;
        };
        MissionState.Active active = new MissionState.Active(mission, baseline, List.of(), 0);
        set(player, state.withActive(Optional.of(active.withProgress(progress(player, active)))));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), WorldSounds.MISSION_ACCEPT, SoundSource.PLAYERS, 0.8F, 1.0F);
        return Component.translatable("message.universe.mission.accepted");
    }

    /** 这些任务今天的状态（{@link OpenMissionBoardPayload#AVAILABLE} 这些），顺手清掉昨天以前的记录。 */
    public static List<Integer> states(ServerPlayer player, List<Mission> offers) {
        MinecraftServer server = player.level().getServer();
        MissionState state = state(player);
        List<String> done = doneToday(state, day(server));
        if (done.size() != state.done().size()) {
            state = new MissionState(state.active(), done);
            set(player, state);
        }
        String active = state.active().map(a -> a.mission().id()).orElse("");
        List<Integer> states = new ArrayList<>();
        for (Mission mission : offers) {
            states.add(mission.id().equals(active) ? OpenMissionBoardPayload.ACTIVE
                    : done.contains(mission.id()) ? OpenMissionBoardPayload.DONE : OpenMissionBoardPayload.AVAILABLE);
        }
        return states;
    }

    /** 手上是送货任务时背包里有几件要送的东西，界面上显示进度用。 */
    public static int have(ServerPlayer player) {
        return state(player).active().filter(a -> a.mission().kind() == MissionKind.DELIVERY).map(a -> count(player, a.mission())).orElse(0);
    }

    /** 放弃手上的任务。 */
    public static Component abandon(ServerPlayer player) {
        MissionState state = state(player);
        if (state.active().isEmpty()) {
            return null;
        }
        set(player, state.withActive(Optional.empty()));
        return Component.translatable("message.universe.mission.abandoned");
    }

    /**
     * 在这个恒星系的空间站交付手上的任务（任务板和公会使者共用）。做完了给报酬、额外奖励，这个任务的公会声望 +3、
     * 恒星系主导种族的声望 +2（ADR-0027 §六）。返回提示；交付成功时提示走聊天栏，返回空。
     */
    public static @Nullable Component handIn(ServerPlayer player, int system) {
        MinecraftServer server = player.level().getServer();
        MissionState state = state(player);
        if (state.active().isEmpty()) {
            return null;
        }
        MissionState.Active active = state.active().get();
        Mission mission = active.mission();
        if (mission.system() != system) {
            return Component.translatable("message.universe.mission.wrong_station", stationName(server, mission.system()));
        }
        if (mission.kind() == MissionKind.DELIVERY) {
            Optional<Item> item = item(mission);
            int have = count(player, mission);
            if (item.isEmpty() || have < mission.count()) {
                return Component.translatable("message.universe.mission.missing", mission.count() - have,
                        item.map(i -> new ItemStack(i).getHoverName()).orElse(Component.literal(mission.item())));
            }
            take(player, item.get(), mission.count());
        } else {
            int progress = progress(player, active);
            if (progress < mission.count()) {
                return Component.translatable("message.universe.mission.unfinished", progress, mission.count());
            }
        }
        Credits.add(player, mission.reward());
        // GameTest 的模拟玩家没有连接：不触发进度（进度的奖励会发包）、不发消息
        if (player.connection != null) {
            MilestoneTrigger.reach(player, MilestoneTrigger.MISSION);
        }
        tell(player, Component.translatable("message.universe.mission.complete", Credits.format(mission.reward())).withStyle(ChatFormatting.GOLD));
        giveBonus(player, mission);
        List<String> done = doneToday(state, day(server));
        done.add(mission.id());
        set(player, new MissionState(Optional.empty(), done));
        raiseStanding(player, mission);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), WorldSounds.MISSION_COMPLETE, SoundSource.PLAYERS, 0.9F, 1.0F);
        return null;
    }

    /** 做完一个任务：这个任务的公会声望 +3，发任务的恒星系的主导种族声望 +2。 */
    private static void raiseStanding(ServerPlayer player, Mission mission) {
        Guild guild = GuildMissions.guild(mission.kind());
        Civ.changeStanding(player, guild, Standing.GUILD_MISSION);
        Optional<Race> race = Planets.galaxy(player.level().getServer()).system(mission.system()).map(Race::dominant);
        race.ifPresent(r -> Civ.changeStanding(player, r, Standing.MISSION_RACE));
        MutableComponent text = race.isPresent()
                ? Component.translatable("message.universe.mission.standing_both", Civ.guildName(guild), Standing.GUILD_MISSION,
                        Civ.raceName(race.get()), Standing.MISSION_RACE)
                : Component.translatable("message.universe.mission.standing", Civ.guildName(guild), Standing.GUILD_MISSION);
        tell(player, text.withStyle(ChatFormatting.AQUA));
    }

    private static void tell(ServerPlayer player, Component message) {
        if (player.connection != null) {
            player.sendSystemMessage(message);
        }
    }

    private static void giveBonus(ServerPlayer player, Mission mission) {
        ItemStack stack = switch (mission.bonus()) {
            case NAVIGATION_DATA -> new ItemStack(UniverseItems.NAVIGATION_DATA.get(), mission.bonusCount());
            case MODULE -> {
                MinecraftServer server = player.level().getServer();
                int rarity = Planets.galaxy(server).system(mission.system()).map(star -> ModuleLoot.rarity(star.star().starClass())).orElse(0);
                Rng rng = Rng.of(Seeds.derive(Seeds.derive(player.getUUID().getLeastSignificantBits(), "mission_module"), mission.id()));
                TechModule module = ModuleLoot.roll(rng, ModuleType.personal(), rarity, 0);
                ItemStack moduleStack = new ItemStack(UniverseItems.MODULES.get(module.type()).get());
                moduleStack.set(UniverseDataComponents.TECH_MODULE.get(), module);
                yield moduleStack;
            }
            case NONE -> ItemStack.EMPTY;
        };
        if (!stack.isEmpty()) {
            tell(player, Component.translatable("message.universe.mission.bonus", stack.getCount(), stack.getHoverName()).withStyle(ChatFormatting.GOLD));
            player.getInventory().placeItemBackInInventory(stack, player.connection != null, Prediction.SERVER_ONLY);
        }
    }

    // ---- 进度

    private static long stat(ServerPlayer player, Identifier stat) {
        return player.getStats().getValue(Stats.CUSTOM.get(stat));
    }

    private static Optional<Item> item(Mission mission) {
        Identifier id = Identifier.tryParse(mission.item());
        return id == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(id);
    }

    /** 背包里（不含盔甲和副手）有几件要送的东西。 */
    private static int count(ServerPlayer player, Mission mission) {
        Optional<Item> item = item(mission);
        if (item.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(item.get())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void take(ServerPlayer player, Item item, int count) {
        int remaining = count;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (remaining <= 0) {
                break;
            }
            if (stack.is(item)) {
                int n = Math.min(remaining, stack.getCount());
                stack.shrink(n);
                remaining -= n;
            }
        }
        player.getInventory().setChanged();
    }

    /** 打掉的守卫机：地上的加上太空里的拦截机。 */
    private static long sentinels(ServerPlayer player) {
        return stat(player, UniverseStats.SENTINELS_DESTROYED.get()) + stat(player, UniverseStats.INTERCEPTORS_DESTROYED.get());
    }

    private static int progress(ServerPlayer player, MissionState.Active active) {
        Mission mission = active.mission();
        return switch (mission.kind()) {
            case BOUNTY -> MissionBoard.progress(mission, stat(player, UniverseStats.PIRATES_DESTROYED.get()), active.baseline(), 0);
            case MINING -> MissionBoard.progress(mission, stat(player, UniverseStats.ASTEROIDS_MINED.get()), active.baseline(), 0);
            case SENTINELS -> MissionBoard.progress(mission, sentinels(player), active.baseline(), 0);
            case SURVEY, LANDMARK -> MissionBoard.progress(mission, 0, 0, active.analyzed().size());
            case DELIVERY -> Math.min(mission.count(), count(player, mission));
        };
    }

    /** 地标任务：人在那颗星球上、走到地标跟前了。 */
    private static boolean arrived(ServerPlayer player, Mission mission) {
        Optional<LandmarkTarget> target = LandmarkTarget.parse(mission.item());
        if (target.isEmpty()) {
            return false;
        }
        Optional<BodyId> body = BodyId.parse(target.get().body());
        return body.isPresent() && player.level().dimension() == Planets.dimension(body.get()) && target.get().reached(player.getX(), player.getZ());
    }

    /** 每秒一次：进度变了就更新（同步给客户端画 HUD），刚做完时提示回去交付。 */
    private static void update(ServerPlayer player) {
        MissionState state = state(player);
        if (state.active().isEmpty()) {
            return;
        }
        MissionState.Active active = state.active().get();
        if (active.mission().kind() == MissionKind.LANDMARK && active.analyzed().isEmpty() && arrived(player, active.mission())) {
            active = new MissionState.Active(active.mission(), active.baseline(), List.of(VISITED), active.progress());
        }
        int progress = progress(player, active);
        if (progress != active.progress()) {
            changed(player, state, active.withProgress(progress));
        }
    }

    private static void changed(ServerPlayer player, MissionState state, MissionState.Active updated) {
        boolean finished = updated.complete() && !state.active().map(MissionState.Active::complete).orElse(false);
        set(player, state.withActive(Optional.of(updated)));
        if (finished) {
            if (player.connection != null) {
                player.sendOverlayMessage(Component.translatable("message.universe.mission.ready",
                        stationName(player.level().getServer(), updated.mission().system())));
            }
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), WorldSounds.MISSION_UPDATE, SoundSource.PLAYERS, 0.8F, 1.0F);
        }
    }

    /** 分析目镜分析完一样东西：测绘任务记下这个恒星系里的动植物。 */
    public static void onAnalyzed(ServerPlayer player, DiscoveryKey key) {
        if (key.kind() != DiscoveryKey.Kind.CREATURE && key.kind() != DiscoveryKey.Kind.PLANT && key.kind() != DiscoveryKey.Kind.TREE) {
            return;
        }
        MissionState state = state(player);
        if (state.active().isEmpty()) {
            return;
        }
        MissionState.Active active = state.active().get();
        if (active.mission().kind() != MissionKind.SURVEY || active.mission().system() != key.system() || active.analyzed().contains(key.id())
                || active.complete()) {
            return;
        }
        List<String> analyzed = new ArrayList<>(active.analyzed());
        analyzed.add(key.id());
        MissionState.Active added = new MissionState.Active(active.mission(), active.baseline(), List.copyOf(analyzed), 0);
        changed(player, state, added.withProgress(progress(player, added)));
    }

    // ---- 调试命令用

    /** 当前任务往前推 n（送货不算）。 */
    public static boolean advance(ServerPlayer player, int n) {
        MissionState state = state(player);
        if (state.active().isEmpty() || state.active().get().mission().kind() == MissionKind.DELIVERY) {
            return false;
        }
        MissionState.Active active = state.active().get();
        MissionState.Active moved = switch (active.mission().kind()) {
            case LANDMARK -> new MissionState.Active(active.mission(), active.baseline(), List.of(VISITED), 0);
            case SURVEY -> {
                List<String> analyzed = new ArrayList<>(active.analyzed());
                for (int i = 0; i < n; i++) {
                    analyzed.add("debug:" + analyzed.size());
                }
                yield new MissionState.Active(active.mission(), active.baseline(), List.copyOf(analyzed), 0);
            }
            default -> new MissionState.Active(active.mission(), active.baseline() - n, active.analyzed(), 0);
        };
        changed(player, state, moved.withProgress(progress(player, moved)));
        return true;
    }

    /** 清掉手上的任务和今天的记录。 */
    public static void reset(ServerPlayer player) {
        set(player, MissionState.NONE);
    }
}
