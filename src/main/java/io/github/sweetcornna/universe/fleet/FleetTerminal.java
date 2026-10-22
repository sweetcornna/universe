package io.github.sweetcornna.universe.fleet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.discovery.DiscoveryKey;
import io.github.sweetcornna.universe.core.fleet.Expedition;
import io.github.sweetcornna.universe.core.fleet.Fleet;
import io.github.sweetcornna.universe.core.fleet.FreighterRules;
import io.github.sweetcornna.universe.core.fleet.Frigate;
import io.github.sweetcornna.universe.core.fleet.HiveSortie;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.procgen.NameGenerator.Name;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.darkfog.hive.DarkFogHives;
import io.github.sweetcornna.universe.discovery.Discoveries;
import io.github.sweetcornna.universe.entity.ship.ShipDealer;
import io.github.sweetcornna.universe.network.FleetPayloads;
import io.github.sweetcornna.universe.portal.PortalGlyphs;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.trade.Credits;
import io.github.sweetcornna.universe.world.freighter.FreighterDimensions;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 舰队指挥室的终端（ADR-0024）：看护卫舰、修受损的、解散；接今天的远征任务、挑最多 5 艘出发（按燃料消耗付发射燃料）；
 * 看在外面的远征一段段的结果，回来了就领奖励。只有货船的主人能开。远征按主世界的游戏时间走，服务端开着就走。
 *
 * <p>清剿页（ADR-0028 §八）：挑一座去过的恒星系里的黑雾巢穴派护卫舰去打（{@link HiveSortie}），不占每天的远征任务。
 * 领的时候才按赢的段数打掉巢穴的单位和建筑（{@link DarkFogHives#strike}），带回信用点和黑雾的掉落。
 */
public final class FleetTerminal {
    private static final double REACH = 8;

    private record Session(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private FleetTerminal() {
    }

    static void clear() {
        SESSIONS.clear();
    }

    public static void open(ServerPlayer player, ServerLevel level, BlockPos pos) {
        OptionalInt id = FreighterDimensions.id(level.dimension());
        if (id.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.universe.trade.no_signal"));
            return;
        }
        Optional<Map.Entry<UUID, FleetData.Entry>> owner = Freighters.byId(level.getServer(), id.getAsInt());
        if (owner.isEmpty() || !owner.get().getKey().equals(player.getUUID())) {
            player.sendOverlayMessage(Component.translatable("message.universe.freighter.not_yours"));
            return;
        }
        SESSIONS.put(player.getUUID(), new Session(level.dimension(), pos.immutable()));
        send(player, true, null);
    }

    private static boolean inSession(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !player.isAlive() || player.level().dimension() != session.dimension()
                || player.distanceToSqr(Vec3.atCenterOf(session.pos())) > REACH * REACH
                || !player.level().getBlockState(session.pos()).is(UniverseBlocks.FLEET_TERMINAL.get())) {
            SESSIONS.remove(player.getUUID());
            return false;
        }
        return true;
    }

    static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    private static void send(ServerPlayer player, boolean open, @Nullable Component notice) {
        MinecraftServer server = player.level().getServer();
        Fleet fleet = FleetData.get(server).entry(player.getUUID()).fleet();
        long now = now(server);
        long day = ShipDealer.day(server);
        List<FleetPayloads.FrigateInfo> frigates = new ArrayList<>();
        for (Frigate frigate : fleet.frigates()) {
            frigates.add(FleetPayloads.FrigateInfo.of(frigate, fleet.busy(frigate.id())));
        }
        List<FleetPayloads.OfferInfo> offers = new ArrayList<>();
        fleet.freighter().ifPresent(freighter -> {
            List<Expedition.Offer> today = Freighters.offers(freighter, day);
            for (int i = 0; i < today.size(); i++) {
                Expedition.Offer offer = today.get(i);
                offers.add(new FleetPayloads.OfferInfo(offer.kind().ordinal(), offer.difficulty(), offer.minutes(), fleet.taken(day, i)));
            }
        });
        List<FleetPayloads.VoyageInfo> voyages = new ArrayList<>();
        for (Fleet.Voyage voyage : fleet.voyages()) {
            List<FleetPayloads.EventInfo> events = new ArrayList<>();
            int revealed = voyage.revealed(now);
            for (int i = 0; i < revealed; i++) {
                Expedition.Event event = voyage.events().get(i);
                events.add(new FleetPayloads.EventInfo(event.area().ordinal(), event.success(), event.frigate(), event.outcome().ordinal()));
            }
            voyages.add(new FleetPayloads.VoyageInfo(voyage.offer().kind().ordinal(), voyage.offer().difficulty(), voyage.start(), voyage.end(),
                    voyage.team(), events, voyage.events().size(), voyage.hive()));
        }
        PacketDistributor.sendToPlayer(player, new FleetPayloads.Terminal(open, Credits.get(player), fuel(player.getInventory()), now, frigates,
                offers, voyages, hives(server, player.getUUID()), Optional.ofNullable(notice)));
    }

    /** 清剿页最多列几座巢穴（左边一列放得下的）。 */
    static final int MAX_HIVES = 4;

    /**
     * 能去清剿的巢穴：核心还在、恒星系去过的（记在发现日志里），离货船近的在前，最多 {@link #MAX_HIVES} 座。
     * 货船没停在哪个恒星系时按母星系算远近。
     */
    static List<FleetPayloads.HiveInfo> hives(MinecraftServer server, UUID owner) {
        Galaxy galaxy = Planets.galaxy(server);
        int here = FleetData.get(server).entry(owner).placed().map(FleetData.Placed::system).orElse(GalaxyGenerator.HOME_SYSTEM);
        Optional<StarSystem> from = galaxy.system(here);
        List<FleetPayloads.HiveInfo> hives = new ArrayList<>();
        List<Double> distances = new ArrayList<>();
        for (DarkFogHives.Summary summary : DarkFogHives.summaries(server)) {
            Optional<StarSystem> system = galaxy.system(summary.system());
            if (!summary.alive() || system.isEmpty() || !Discoveries.log(server).contains(DiscoveryKey.system(summary.system()))) {
                continue;
            }
            Name name = Discoveries.resolve(server, DiscoveryKey.system(summary.system()), system.get().name());
            Expedition.Offer offer = HiveSortie.offer(summary.level());
            FleetPayloads.HiveInfo info = new FleetPayloads.HiveInfo(summary.system(), name.zh(), name.en(), summary.level(), offer.difficulty(),
                    offer.minutes());
            double distance = from.map(f -> f.distanceTo(system.get())).orElse(0.0);
            int at = 0;
            while (at < distances.size() && distances.get(at) <= distance) {
                at++;
            }
            hives.add(at, info);
            distances.add(at, distance);
        }
        return hives.subList(0, Math.min(MAX_HIVES, hives.size()));
    }

    public static void handleAction(FleetPayloads.Action payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !inSession(player)) {
            return;
        }
        Component notice = switch (payload.action()) {
            case FleetPayloads.Action.LAUNCH -> launch(player, payload.value(), payload.team());
            case FleetPayloads.Action.CLAIM -> claim(player, payload.value());
            case FleetPayloads.Action.REPAIR -> repair(player, payload.value());
            case FleetPayloads.Action.DISMISS -> dismiss(player, payload.value());
            case FleetPayloads.Action.SORTIE -> sortie(player, payload.value(), payload.team());
            default -> null;
        };
        send(player, false, notice);
    }

    private static Component problem(Fleet.Problem problem) {
        return Component.translatable("message.universe.fleet.problem." + problem.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static Component launch(ServerPlayer player, int slot, List<Integer> team) {
        MinecraftServer server = player.level().getServer();
        FleetData data = FleetData.get(server);
        Fleet fleet = data.entry(player.getUUID()).fleet();
        Fleet.Freighter freighter = fleet.freighter().orElse(null);
        long day = ShipDealer.day(server);
        if (freighter == null) {
            return problem(Fleet.Problem.NO_FREIGHTER);
        }
        List<Expedition.Offer> offers = Freighters.offers(freighter, day);
        if (slot < 0 || slot >= offers.size()) {
            return problem(Fleet.Problem.TAKEN);
        }
        Fleet.Problem problem = fleet.canLaunch(day, slot, team);
        if (problem != null) {
            return problem(problem);
        }
        int fuel = Expedition.fuelItems(fleet.team(team));
        if (!player.hasInfiniteMaterials()) {
            if (fuel(player.getInventory()) < fuel) {
                return Component.translatable("message.universe.fleet.no_fuel", fuel);
            }
            takeFuel(player.getInventory(), fuel);
        }
        Rng rng = Rng.of(player.getRandom().nextLong());
        Fleet.Voyage voyage = fleet.launch(day, slot, offers.get(slot), team, now(server), rng);
        data.changed();
        Freighters.refreshFrigates(server, player.getUUID());
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.8F, 1.3F);
        return Component.translatable("message.universe.fleet.launched", voyage.team().size(), voyage.offer().minutes());
    }

    /** 派去清剿 {@code system} 的巢穴：那座巢穴得在清剿页的名单里（核心还在、去过那个恒星系）。 */
    private static Component sortie(ServerPlayer player, int system, List<Integer> team) {
        MinecraftServer server = player.level().getServer();
        FleetData data = FleetData.get(server);
        Fleet fleet = data.entry(player.getUUID()).fleet();
        Fleet.Problem problem = fleet.canSortie(team);
        if (problem != null) {
            return problem(problem);
        }
        Optional<FleetPayloads.HiveInfo> hive = hives(server, player.getUUID()).stream().filter(h -> h.system() == system).findFirst();
        if (hive.isEmpty()) {
            return Component.translatable("message.universe.fleet.problem.no_hive");
        }
        int fuel = Expedition.fuelItems(fleet.team(team));
        if (!player.hasInfiniteMaterials()) {
            if (fuel(player.getInventory()) < fuel) {
                return Component.translatable("message.universe.fleet.no_fuel", fuel);
            }
            takeFuel(player.getInventory(), fuel);
        }
        Rng rng = Rng.of(player.getRandom().nextLong());
        Fleet.Voyage voyage = fleet.sortie(system, HiveSortie.offer(hive.get().level()), team, now(server), rng);
        data.changed();
        Freighters.refreshFrigates(server, player.getUUID());
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.8F, 1.1F);
        return Component.translatable("message.universe.fleet.launched", voyage.team().size(), voyage.offer().minutes());
    }

    /**
     * 远征带回来的东西。
     *
     * @param items  物品（技术模块已经抽好）
     * @param glyphs 教几个符文
     */
    public record Haul(List<ItemStack> items, long credits, int glyphs) {
        public int count() {
            return this.items.stream().mapToInt(ItemStack::getCount).sum();
        }
    }

    /**
     * 清剿的结算。
     *
     * @param hive    巢穴那边打掉了什么（巢穴没了、不是清剿时是 {@link DarkFogHives.Strike#NONE}）
     * @param rewards 领到的全部奖励：原来的信用点加上黑雾的掉落
     */
    public record Strike(DarkFogHives.Strike hive, List<Expedition.Reward> rewards) {
    }

    /** 清剿领回来时巢穴才挨打：赢的段数交给巢穴那边，打掉的单位、建筑按黑雾的掉落加进奖励。普通的远征原样返回。 */
    public static Strike strike(MinecraftServer server, Fleet.Voyage voyage, List<Expedition.Reward> rewards) {
        if (!voyage.sortie()) {
            return new Strike(DarkFogHives.Strike.NONE, rewards);
        }
        DarkFogHives.Strike hive = DarkFogHives.strike(server, voyage.hive(), HiveSortie.successes(voyage.events()));
        List<Expedition.Reward> all = new ArrayList<>(rewards);
        if (hive.found()) {
            all.addAll(HiveSortie.loot(hive.level(), hive.unitsKilled(), hive.buildingsDestroyed(), Rng.of(voyage.seed() ^ 0x9E3779B97F4A7C15L)));
        }
        return new Strike(hive, all);
    }

    /** 把奖励换成物品和信用点（技术模块按远征的种子抽）。认不出的物品 ID 丢掉。 */
    public static Haul haul(Fleet.Voyage voyage, List<Expedition.Reward> rewards) {
        Rng rng = Rng.of(voyage.seed() ^ 0x5DEECE66DL);
        List<ItemStack> items = new ArrayList<>();
        long credits = 0;
        int glyphs = 0;
        for (Expedition.Reward reward : rewards) {
            switch (reward.key()) {
                case Expedition.CREDITS -> credits += reward.count();
                case Expedition.SHIP_MODULE -> {
                    for (int i = 0; i < reward.count(); i++) {
                        items.add(ShipDealer.moduleStack(Fleet.rewardModule(voyage.offer().difficulty(), rng)));
                    }
                }
                case Expedition.GLYPH -> glyphs += reward.count();
                default -> {
                    Optional<Item> item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(reward.key()));
                    item.ifPresent(value -> items.add(new ItemStack(value, reward.count())));
                }
            }
        }
        return new Haul(items, credits, glyphs);
    }

    private static Component claim(ServerPlayer player, int index) {
        MinecraftServer server = player.level().getServer();
        FleetData data = FleetData.get(server);
        Fleet fleet = data.entry(player.getUUID()).fleet();
        List<Fleet.Voyage> voyages = fleet.voyages();
        if (index < 0 || index >= voyages.size() || !voyages.get(index).done(now(server))) {
            return Component.translatable("message.universe.fleet.not_back");
        }
        Fleet.Voyage voyage = voyages.get(index);
        Fleet.Claim claim = fleet.claim(voyage);
        data.changed();
        Strike strike = strike(server, voyage, claim.rewards());
        Haul haul = haul(voyage, strike.rewards());
        for (ItemStack stack : haul.items()) {
            give(player, stack);
        }
        if (haul.credits() > 0) {
            Credits.add(player, haul.credits());
        }
        Rng rng = Rng.of(voyage.seed() ^ 0x2545F4914F6CDD1DL);
        for (int i = 0; i < haul.glyphs(); i++) {
            PortalGlyphs.learn(player, rng);
        }
        Freighters.refreshFrigates(server, player.getUUID());
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.6F, 1.2F);
        long won = voyage.events().stream().filter(Expedition.Event::success).count();
        if (voyage.sortie()) {
            return Component.translatable("message.universe.fleet.claimed_hive", won, voyage.events().size(), strike.hive().unitsKilled(),
                    strike.hive().buildingsDestroyed(), Credits.format(haul.credits()), haul.count(), claim.lost().size(), claim.damaged().size(),
                    claim.promoted().size());
        }
        return Component.translatable("message.universe.fleet.claimed", won, voyage.events().size(), Credits.format(haul.credits()), haul.count(),
                claim.lost().size(), claim.damaged().size(), claim.promoted().size());
    }

    private static Component repair(ServerPlayer player, int id) {
        MinecraftServer server = player.level().getServer();
        FleetData data = FleetData.get(server);
        Fleet fleet = data.entry(player.getUUID()).fleet();
        Frigate frigate = fleet.frigate(id);
        if (frigate == null || !frigate.damaged() || fleet.busy(id)) {
            return Component.translatable("message.universe.fleet.cannot_repair");
        }
        if (!Credits.spend(player, FreighterRules.REPAIR_COST)) {
            return Component.translatable("message.universe.dealer.poor", Credits.format(FreighterRules.REPAIR_COST - Credits.get(player)));
        }
        fleet.repair(id);
        data.changed();
        player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6F, 1.3F);
        return Component.translatable("message.universe.fleet.repaired", frigate.designation());
    }

    private static Component dismiss(ServerPlayer player, int id) {
        MinecraftServer server = player.level().getServer();
        FleetData data = FleetData.get(server);
        Fleet fleet = data.entry(player.getUUID()).fleet();
        Frigate frigate = fleet.frigate(id);
        if (frigate == null || !fleet.dismiss(id)) {
            return Component.translatable("message.universe.fleet.cannot_dismiss");
        }
        data.changed();
        Freighters.refreshFrigates(server, player.getUUID());
        return Component.translatable("message.universe.fleet.dismissed", frigate.designation());
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
    }

    private static int fuel(Inventory inventory) {
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(UniverseItems.LAUNCH_FUEL.get())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void takeFuel(Inventory inventory, int amount) {
        for (int i = 0; i < inventory.getContainerSize() && amount > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(UniverseItems.LAUNCH_FUEL.get())) {
                int taken = Math.min(amount, stack.getCount());
                stack.shrink(taken);
                amount -= taken;
            }
        }
        inventory.setChanged();
    }
}
