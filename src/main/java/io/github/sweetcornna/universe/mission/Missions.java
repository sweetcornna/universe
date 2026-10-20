package io.github.sweetcornna.universe.mission;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import io.github.sweetcornna.universe.advancement.MilestoneTrigger;
import io.github.sweetcornna.universe.core.discovery.DiscoveryKey;
import io.github.sweetcornna.universe.core.discovery.Survey;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.mission.Mission;
import io.github.sweetcornna.universe.core.mission.MissionBoard;
import io.github.sweetcornna.universe.core.mission.MissionKind;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;
import io.github.sweetcornna.universe.core.tech.ModuleLoot;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.core.tech.TechModule;
import io.github.sweetcornna.universe.core.trade.Economy;
import io.github.sweetcornna.universe.discovery.Discoveries;
import io.github.sweetcornna.universe.network.MissionActionPayload;
import io.github.sweetcornna.universe.network.OpenMissionBoardPayload;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.registry.UniverseStats;
import io.github.sweetcornna.universe.trade.Credits;
import io.github.sweetcornna.universe.trade.Trading;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
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

    private Missions() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(PlayerTickEvent.Post.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 7) {
                update(player);
            }
        });
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
        MinecraftServer server = level.getServer();
        MissionState state = state(player);
        List<String> done = doneToday(state, day(server));
        if (done.size() != state.done().size()) {
            state = new MissionState(state.active(), done);
            set(player, state);
        }
        List<Mission> offers = offers(server, system.getAsInt());
        List<Integer> states = new ArrayList<>();
        String active = state.active().map(a -> a.mission().id()).orElse("");
        for (Mission mission : offers) {
            states.add(mission.id().equals(active) ? OpenMissionBoardPayload.ACTIVE
                    : done.contains(mission.id()) ? OpenMissionBoardPayload.DONE : OpenMissionBoardPayload.AVAILABLE);
        }
        int have = state.active().filter(a -> a.mission().kind() == MissionKind.DELIVERY).map(a -> count(player, a.mission())).orElse(0);
        PacketDistributor.sendToPlayer(player, new OpenMissionBoardPayload(pos, system.getAsInt(), offers, states, state.active(), have));
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
        MinecraftServer server = player.level().getServer();
        MissionState state = state(player);
        if (state.active().isPresent()) {
            return Component.translatable("message.universe.mission.busy");
        }
        List<Mission> offers = offers(server, system);
        if (slot < 0 || slot >= offers.size()) {
            return null;
        }
        Mission mission = offers.get(slot);
        if (doneToday(state, day(server)).contains(mission.id())) {
            return Component.translatable("message.universe.mission.done_today");
        }
        long baseline = switch (mission.kind()) {
            case BOUNTY -> stat(player, UniverseStats.PIRATES_DESTROYED.get());
            case MINING -> stat(player, UniverseStats.ASTEROIDS_MINED.get());
            default -> 0;
        };
        MissionState.Active active = new MissionState.Active(mission, baseline, List.of(), 0);
        set(player, state.withActive(Optional.of(active.withProgress(progress(player, active)))));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 0.8F, 1.2F);
        return Component.translatable("message.universe.mission.accepted");
    }

    private static Component abandon(ServerPlayer player) {
        MissionState state = state(player);
        if (state.active().isEmpty()) {
            return null;
        }
        set(player, state.withActive(Optional.empty()));
        return Component.translatable("message.universe.mission.abandoned");
    }

    private static Component handIn(ServerPlayer player, int system) {
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
        MilestoneTrigger.reach(player, MilestoneTrigger.MISSION);
        player.sendSystemMessage(Component.translatable("message.universe.mission.complete", Credits.format(mission.reward()))
                .withStyle(ChatFormatting.GOLD));
        giveBonus(player, mission);
        List<String> done = doneToday(state, day(server));
        done.add(mission.id());
        set(player, new MissionState(Optional.empty(), done));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.4F);
        return null;
    }

    private static void giveBonus(ServerPlayer player, Mission mission) {
        ItemStack stack = switch (mission.bonus()) {
            case NAVIGATION_DATA -> new ItemStack(UniverseItems.NAVIGATION_DATA.get(), mission.bonusCount());
            case MODULE -> {
                MinecraftServer server = player.level().getServer();
                int rarity = Planets.galaxy(server).system(mission.system()).map(star -> ModuleLoot.rarity(star.star().starClass())).orElse(0);
                Rng rng = Rng.of(Seeds.derive(Seeds.derive(player.getUUID().getLeastSignificantBits(), "mission_module"), mission.id()));
                TechModule module = ModuleLoot.roll(rng, List.of(ModuleType.values()), rarity, 0);
                ItemStack moduleStack = new ItemStack(UniverseItems.MODULES.get(module.type()).get());
                moduleStack.set(UniverseDataComponents.TECH_MODULE.get(), module);
                yield moduleStack;
            }
            case NONE -> ItemStack.EMPTY;
        };
        if (!stack.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.universe.mission.bonus", stack.getCount(), stack.getHoverName())
                    .withStyle(ChatFormatting.GOLD));
            player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
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

    private static int progress(ServerPlayer player, MissionState.Active active) {
        Mission mission = active.mission();
        return switch (mission.kind()) {
            case BOUNTY -> MissionBoard.progress(mission, stat(player, UniverseStats.PIRATES_DESTROYED.get()), active.baseline(), 0);
            case MINING -> MissionBoard.progress(mission, stat(player, UniverseStats.ASTEROIDS_MINED.get()), active.baseline(), 0);
            case SURVEY -> MissionBoard.progress(mission, 0, 0, active.analyzed().size());
            case DELIVERY -> Math.min(mission.count(), count(player, mission));
        };
    }

    /** 每秒一次：进度变了就更新（同步给客户端画 HUD），刚做完时提示回去交付。 */
    private static void update(ServerPlayer player) {
        MissionState state = state(player);
        if (state.active().isEmpty()) {
            return;
        }
        MissionState.Active active = state.active().get();
        int progress = progress(player, active);
        if (progress != active.progress()) {
            changed(player, state, active.withProgress(progress));
        }
    }

    private static void changed(ServerPlayer player, MissionState state, MissionState.Active updated) {
        boolean finished = updated.complete() && !state.active().map(MissionState.Active::complete).orElse(false);
        set(player, state.withActive(Optional.of(updated)));
        if (finished) {
            player.sendOverlayMessage(Component.translatable("message.universe.mission.ready",
                    stationName(player.level().getServer(), updated.mission().system())));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8F, 1.5F);
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
