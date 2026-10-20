package io.github.sweetcornna.universe.teleport;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import io.github.sweetcornna.universe.advancement.MilestoneTrigger;
import io.github.sweetcornna.universe.core.discovery.DiscoveryKey;
import io.github.sweetcornna.universe.core.discovery.DiscoveryLog;
import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.space.StationLayout.Site;
import io.github.sweetcornna.universe.core.teleport.TeleportRules;
import io.github.sweetcornna.universe.discovery.Discoveries;
import io.github.sweetcornna.universe.entity.AlienCreature;
import io.github.sweetcornna.universe.entity.TamedCreatures;
import io.github.sweetcornna.universe.network.OpenBaseBeaconPayload;
import io.github.sweetcornna.universe.network.OpenTeleporterPayload;
import io.github.sweetcornna.universe.network.OpenTeleporterPayload.Destination;
import io.github.sweetcornna.universe.network.RenameBasePayload;
import io.github.sweetcornna.universe.network.TeleportRequestPayload;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.station.Stations;
import io.github.sweetcornna.universe.world.DimensionTemplate;
import io.github.sweetcornna.universe.world.RuntimeDimensions;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 基地和传送网络（ADR-0017）：登记基地、记下玩家进过的空间站、传送器的目的地列表和传送。
 *
 * <p>传送可能要创建维度，而维度 tick 期间不能增删维度，所以收到请求只登记，到服务器 tick 末尾再传（和飞船跨维度一样）。
 * 跟着主人的宠物一起过去。
 */
public final class Teleporters {
    /** 一次登记好的传送。 */
    private record Pending(UUID player, ResourceKey<Level> from, BlockPos teleporter, byte kind, String id) {
    }

    private static final List<Pending> QUEUE = new ArrayList<>();

    private Teleporters() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ServerTickEvent.Post.class, event -> process(event.getServer()));
        gameBus.addListener(PlayerTickEvent.Post.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0) {
                visit(player);
            }
        });
        gameBus.addListener(ServerStoppedEvent.class, event -> QUEUE.clear());
    }

    // ---------------------------------------------------------------- 基地

    static void registerBase(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.level();
        BaseData.Base base = BaseData.get(level.getServer()).register(player.getUUID(), level.dimension(), pos, level.getGameTime());
        player.sendOverlayMessage(Component.translatable("message.universe.base.registered", name(level.getServer(), base)));
    }

    /** 基地显示的名字：起过名就用它，否则是"星球名 · 基地 N"。 */
    public static Component name(MinecraftServer server, BaseData.Base base) {
        if (!base.name().isEmpty()) {
            return Component.literal(base.name());
        }
        return Component.translatable("gui.universe.base.default_name", place(server, base.dimension()), base.number());
    }

    /** 维度显示的名字：星球用发现日志里的名字，太空是"恒星系名 太空"，别的维度用维度 ID。 */
    private static Component place(MinecraftServer server, ResourceKey<Level> dimension) {
        Optional<BodyId> body = body(dimension);
        if (body.isPresent()) {
            return Discoveries.name(server, DiscoveryKey.body(body.get()));
        }
        OptionalInt system = SpaceDimensions.system(dimension);
        if (system.isPresent()) {
            return Component.translatable("gui.universe.stats.space", Discoveries.name(server, DiscoveryKey.system(system.getAsInt())));
        }
        return Component.literal(dimension.identifier().getPath());
    }

    /** 维度对应的天体：主世界是母星。 */
    static Optional<BodyId> body(ResourceKey<Level> dimension) {
        return dimension == Level.OVERWORLD ? Optional.of(GalaxyGenerator.HOME_PLANET_ID) : Planets.bodyId(dimension);
    }

    private static int system(ResourceKey<Level> dimension) {
        Optional<BodyId> body = body(dimension);
        if (body.isPresent()) {
            return body.get().system();
        }
        return SpaceDimensions.system(dimension).orElse(-1);
    }

    static void openBeacon(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.level();
        BaseData data = BaseData.get(level.getServer());
        Optional<BaseData.Base> base = data.at(level.dimension(), pos);
        if (base.isEmpty()) {
            // 老的、或者用命令放的信标：谁先右键就登记给谁
            registerBase(player, pos);
            return;
        }
        if (!base.get().owner().equals(player.getUUID())) {
            String owner = level.getServer().services().nameToIdCache().get(base.get().owner())
                    .map(profile -> profile.name()).orElse("?");
            player.sendOverlayMessage(Component.translatable("message.universe.base.owned", owner));
            return;
        }
        PacketDistributor.sendToPlayer(player, new OpenBaseBeaconPayload(pos, base.get().name(), base.get().number(),
                body(level.dimension()).map(BodyId::path).orElse("")));
    }

    public static void handleRename(RenameBasePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.level();
        if (!TeleportRules.inReach(player.distanceToSqr(Vec3.atCenterOf(payload.pos())))) {
            return;
        }
        BaseData data = BaseData.get(level.getServer());
        Optional<BaseData.Base> base = data.at(level.dimension(), payload.pos()).filter(b -> b.owner().equals(player.getUUID()));
        Optional<String> clean = DiscoveryLog.cleanName(payload.name());
        if (base.isEmpty()) {
            return;
        }
        if (clean.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.universe.discovery.rename_invalid_name"));
            return;
        }
        data.rename(base.get().id(), clean.get());
        player.sendOverlayMessage(Component.translatable("message.universe.discovery.renamed",
                name(level.getServer(), data.get(base.get().id()).orElseThrow())));
    }

    // ---------------------------------------------------------------- 空间站

    /** 每秒看一眼玩家是不是在空间站里：记下来，顺手给老存档的空间站补上传送器和任务板。 */
    private static void visit(ServerPlayer player) {
        ServerLevel level = player.level();
        OptionalInt system = SpaceDimensions.system(level.dimension());
        if (system.isEmpty() || !Stations.inside(level, player.position())) {
            return;
        }
        List<Integer> visited = player.getData(UniverseAttachments.VISITED_STATIONS);
        if (!visited.contains(system.getAsInt())) {
            List<Integer> updated = new ArrayList<>(visited);
            updated.add(system.getAsInt());
            player.setData(UniverseAttachments.VISITED_STATIONS, List.copyOf(updated));
        }
        Stations.site(level.getServer(), level.dimension()).ifPresent(site -> Stations.ensureFixtures(level, site));
    }

    // ---------------------------------------------------------------- 传送器

    static void open(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.level();
        if (!(level.getBlockEntity(pos) instanceof TeleporterBlockEntity teleporter)) {
            return;
        }
        List<Destination> destinations = new ArrayList<>();
        MinecraftServer server = level.getServer();
        for (BaseData.Base base : BaseData.get(server).of(player.getUUID())) {
            boolean here = base.dimension().equals(level.dimension()) && base.pos().distSqr(pos) <= 32 * 32;
            destinations.add(new Destination(OpenTeleporterPayload.BASE, base.id().toString(), base.name(), base.number(),
                    body(base.dimension()).map(BodyId::path).orElse(""), base.dimension().identifier().toString(),
                    system(base.dimension()), here));
        }
        OptionalInt currentSystem = SpaceDimensions.system(level.dimension());
        List<Integer> stations = new ArrayList<>(player.getData(UniverseAttachments.VISITED_STATIONS));
        stations.sort(null);
        for (int system : stations) {
            boolean here = teleporter.free() && currentSystem.isPresent() && currentSystem.getAsInt() == system;
            destinations.add(new Destination(OpenTeleporterPayload.STATION, String.valueOf(system), "", 0, "",
                    SpaceDimensions.key(system).identifier().toString(), system, here));
        }
        PacketDistributor.sendToPlayer(player, new OpenTeleporterPayload(pos, teleporter.free(), teleporter.energy(),
                teleporter.free() || teleporter.connected(), destinations));
    }

    public static void handleRequest(TeleportRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        Optional<Component> problem = check(player, payload.pos());
        if (problem.isPresent()) {
            player.sendOverlayMessage(problem.get());
            return;
        }
        if (QUEUE.stream().anyMatch(pending -> pending.player().equals(player.getUUID()))) {
            return;
        }
        QUEUE.add(new Pending(player.getUUID(), player.level().dimension(), payload.pos().immutable(), payload.kind(), payload.id()));
    }

    /** 能不能用这台传送器：离得够近、充满了电（创造模式不用）。 */
    private static Optional<Component> check(ServerPlayer player, BlockPos pos) {
        if (!TeleportRules.inReach(player.distanceToSqr(Vec3.atCenterOf(pos)))
                || !(player.level().getBlockEntity(pos) instanceof TeleporterBlockEntity teleporter)) {
            return Optional.of(Component.translatable("message.universe.teleport.too_far"));
        }
        if (!teleporter.charged() && !player.isCreative()) {
            return Optional.of(Component.translatable("message.universe.teleport.not_charged"));
        }
        return Optional.empty();
    }

    private static void process(MinecraftServer server) {
        if (QUEUE.isEmpty()) {
            return;
        }
        List<Pending> batch = List.copyOf(QUEUE);
        QUEUE.clear();
        for (Pending pending : batch) {
            ServerPlayer player = server.getPlayerList().getPlayer(pending.player());
            if (player == null || !player.isAlive() || player.level().dimension() != pending.from()) {
                continue;
            }
            Optional<Component> problem = check(player, pending.teleporter());
            if (problem.isPresent()) {
                player.sendOverlayMessage(problem.get());
                continue;
            }
            travel(server, player, pending);
        }
    }

    private record Arrival(ServerLevel level, Vec3 pos, float yaw, Component name) {
    }

    private static void travel(MinecraftServer server, ServerPlayer player, Pending pending) {
        Optional<Arrival> arrival = pending.kind() == OpenTeleporterPayload.STATION ? station(server, player, pending.id())
                : base(server, player, pending.id());
        if (arrival.isEmpty()) {
            return;
        }
        ServerLevel from = player.level();
        Vec3 departure = player.position();
        List<AlienCreature> pets = TamedCreatures.followers(player, from, departure);
        if (from.getBlockEntity(pending.teleporter()) instanceof TeleporterBlockEntity teleporter && !player.isCreative()) {
            teleporter.discharge();
        }
        player.stopRiding();
        Arrival to = arrival.get();
        if (player.teleport(new TeleportTransition(to.level(), to.pos(), Vec3.ZERO, to.yaw(), 0, TeleportTransition.PLACE_PORTAL_TICKET)) == null) {
            return;
        }
        TamedCreatures.bring(player, pets);
        from.playSound(null, departure.x, departure.y, departure.z, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 1, 1);
        from.sendParticles(ParticleTypes.PORTAL, departure.x, departure.y + 1, departure.z, 40, 0.4, 0.8, 0.4, 0.3);
        to.level().playSound(null, to.pos().x, to.pos().y, to.pos().z, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 1, 1);
        to.level().sendParticles(ParticleTypes.REVERSE_PORTAL, to.pos().x, to.pos().y + 1, to.pos().z, 40, 0.4, 0.8, 0.4, 0.05);
        player.sendOverlayMessage(Component.translatable("message.universe.teleport.arrived", to.name()));
        MilestoneTrigger.reach(player, MilestoneTrigger.TELEPORT);
    }

    private static Optional<Arrival> station(MinecraftServer server, ServerPlayer player, String id) {
        int system;
        try {
            system = Integer.parseInt(id);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        if (!player.getData(UniverseAttachments.VISITED_STATIONS).contains(system)) {
            return Optional.empty();
        }
        Optional<Site> site = Stations.site(Planets.galaxy(server), system);
        if (site.isEmpty()) {
            return Optional.empty();
        }
        ServerLevel level = SpaceDimensions.getOrCreate(server, system);
        // 区块读进来（第一次去时会在这里生成），老存档的空间站补上传送器和任务板
        level.getChunk(Stations.teleporter(site.get()));
        Stations.ensureFixtures(level, site.get());
        Component name = Component.translatable("gui.universe.trade.station", Discoveries.name(server, DiscoveryKey.system(system)));
        return Optional.of(new Arrival(level, Stations.arrival(site.get()), Stations.arrivalYaw(site.get()), name));
    }

    private static Optional<Arrival> base(MinecraftServer server, ServerPlayer player, String id) {
        UUID baseId;
        try {
            baseId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        BaseData data = BaseData.get(server);
        Optional<BaseData.Base> found = data.get(baseId).filter(base -> base.owner().equals(player.getUUID()));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        BaseData.Base base = found.get();
        ServerLevel level = level(server, base.dimension());
        if (level == null) {
            player.sendOverlayMessage(Component.translatable("message.universe.teleport.unreachable"));
            return Optional.empty();
        }
        level.getChunk(base.pos());
        if (!level.getBlockState(base.pos()).is(UniverseBlocks.BASE_BEACON.get())) {
            // 信标被拆了（爆炸、命令这些没走正常的拆除流程）
            data.remove(base.id());
            player.sendOverlayMessage(Component.translatable("message.universe.teleport.beacon_missing", name(server, base)));
            return Optional.empty();
        }
        Vec3 spot = TamedCreatures.groundNear(level, player, base.pos().above(2));
        if (spot == null) {
            player.sendOverlayMessage(Component.translatable("message.universe.teleport.no_room"));
            return Optional.empty();
        }
        // 到了面朝信标
        double dx = base.pos().getX() + 0.5 - spot.x;
        double dz = base.pos().getZ() + 0.5 - spot.z;
        float yaw = dx * dx + dz * dz < 0.01 ? player.getYRot() : (float) Math.toDegrees(Math.atan2(-dx, dz));
        return Optional.of(new Arrival(level, spot, yaw, name(server, base)));
    }

    private static ServerLevel level(MinecraftServer server, ResourceKey<Level> key) {
        if (SpaceDimensions.isSpace(key)) {
            return SpaceDimensions.getOrCreate(server, SpaceDimensions.system(key).getAsInt());
        }
        if (Planets.bodyId(key).isPresent()) {
            return RuntimeDimensions.getOrCreate(server, key, DimensionTemplate.PLANET);
        }
        return server.getLevel(key);
    }
}
