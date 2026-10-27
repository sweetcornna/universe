package io.github.sweetcornna.universe.terraform;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import io.github.sweetcornna.universe.core.chem.Gas;
import io.github.sweetcornna.universe.core.discovery.DiscoveryKey;
import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.Hazard;
import io.github.sweetcornna.universe.core.microbe.Gene;
import io.github.sweetcornna.universe.core.terraform.EffectiveEnvironment;
import io.github.sweetcornna.universe.core.terraform.Terraform;
import io.github.sweetcornna.universe.core.terraform.TerraformState;
import io.github.sweetcornna.universe.discovery.Discoveries;
import io.github.sweetcornna.universe.perf.ServerPerf;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 星球改造的服务端和客户端入口（ADR-0036 §六）。
 *
 * <p>星球本来的数据（{@link Planets}）不动，世界生成也还用它；生存、天气、危害、风力、天空这些读"现用的星球"的地方都走这里：
 * {@link #planet(ServerLevel)}、{@link #environment(ServerLevel)}，客户端是 {@link #clientEffective}。没动过的星球返回的就是
 * 星球本来的那个对象，和以前一模一样。现用的环境按星球缓存，状态一变就重算。
 *
 * <p>服务端每 {@value #ADVANCE_INTERVAL} 刻推进一次动过的星球（生物量、大气化学，按上次推进的时间补齐），状态变了就存盘、
 * 到了新阶段全服提示一次，再把这颗星球的状态发给所有玩家（每颗星球每 5 秒最多一次）；玩家登录时收到全部。
 */
public final class Terraforming {
    /** 多久推进一次（刻）。 */
    public static final int ADVANCE_INTERVAL = 200;
    /** 同一颗星球两次同步最少隔多久（刻）。 */
    public static final int SYNC_INTERVAL = 100;
    /** 多久看一次有没有该补发的同步（刻）。 */
    private static final int FLUSH_INTERVAL = 20;

    private static final Side SERVER = new Side();
    private static final Side CLIENT = new Side();
    /** 客户端收到的各星球状态。 */
    private static final Map<BodyId, TerraformState> CLIENT_STATES = new ConcurrentHashMap<>();
    /** 服务端：每颗星球上次同步的时间（主世界的游戏时间），和变了还没发出去的星球。 */
    private static final Map<String, Long> SENT = new ConcurrentHashMap<>();
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();

    /** 一颗星球缓存的现用环境：星球本来的对象、算的时候的状态，以及现用的星球（没动过就是本来的那个）。 */
    private record Cached(Planet base, int revision, TerraformState state, EffectiveEnvironment environment) {
    }

    /** 服务端或客户端那一边的缓存。两边在单人游戏里同在一个进程，各管各的。 */
    private static final class Side {
        final Map<BodyId, Cached> cache = new ConcurrentHashMap<>();
        final AtomicInteger revision = new AtomicInteger();

        Cached lookup(Supplier<Galaxy> galaxy, Planet base, StateSource source) {
            BodyId id = base.id();
            int revision = this.revision.get();
            Cached cached = this.cache.get(id);
            if (cached != null && cached.base == base && cached.revision == revision) {
                return cached;
            }
            TerraformState state = Terraform.terraformable(base) ? source.state(id) : TerraformState.EMPTY;
            if (cached != null && cached.base == base && cached.state.equals(state)) {
                cached = new Cached(base, revision, cached.state, cached.environment);
            } else {
                cached = new Cached(base, revision, state, Terraform.effective(galaxy.get(), base, state));
            }
            this.cache.put(id, cached);
            return cached;
        }

        void clear() {
            this.cache.clear();
            this.revision.incrementAndGet();
        }
    }

    @FunctionalInterface
    private interface StateSource {
        TerraformState state(BodyId id);
    }

    private Terraforming() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        gameBus.addListener(ServerTickEvent.Post.class, event -> ServerPerf.time("terraform", () -> tick(event.getServer())));
        gameBus.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                sendAll(player);
            }
        });
        gameBus.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                sendCurrent(player);
            }
        });
        gameBus.addListener(ServerStoppedEvent.class, event -> {
            SERVER.clear();
            SENT.clear();
            PENDING.clear();
        });
        gameBus.addListener(RegisterCommandsEvent.class, event -> TerraformCommand.register(event.getDispatcher()));
    }

    // ---- 现用的星球（服务端）

    /** 这颗星球现在的状态；没动过、不能改造是 {@link TerraformState#EMPTY}。 */
    public static TerraformState state(MinecraftServer server, BodyId id) {
        return TerraformData.get(server).state(id.path());
    }

    /** 现用的环境（气体的量、成分、气压、温度、辐射系数……）。 */
    public static EffectiveEnvironment environment(MinecraftServer server, Planet base) {
        return SERVER.lookup(() -> Planets.galaxy(server), base, id -> state(server, id)).environment();
    }

    /** 现用的星球；没动过的就是 {@code base} 本身。 */
    public static Planet effective(MinecraftServer server, Planet base) {
        if (!Terraform.terraformable(base)) {
            return base;
        }
        return environment(server, base).planet();
    }

    /** 星球维度（不含主世界）现用的星球。代替读环境的地方原来用的 {@link Planets#planet(ServerLevel)}。 */
    public static Optional<Planet> planet(ServerLevel level) {
        return Planets.planet(level).map(base -> effective(level.getServer(), base));
    }

    /** 这个世界的天体现用的样子，包括母星（主世界，母星不改造）。代替 {@link Planets#body(ServerLevel)}。 */
    public static Optional<Planet> body(ServerLevel level) {
        return Planets.body(level).map(base -> effective(level.getServer(), base));
    }

    /** 星球维度（不含主世界）现用的环境；生存用。 */
    public static Optional<EffectiveEnvironment> planetEnvironment(ServerLevel level) {
        return Planets.planet(level).map(base -> environment(level.getServer(), base));
    }

    /** 现用环境的危害和强度，辐射乘上系数。见 {@link TerraformRules#hazards}。 */
    public static Map<Hazard, Double> hazards(EffectiveEnvironment environment) {
        return TerraformRules.hazards(environment);
    }

    /** 这个世界的天体现用的环境，包括母星；大气提取器、大气读数用。 */
    public static Optional<EffectiveEnvironment> environment(ServerLevel level) {
        return Planets.body(level).map(base -> environment(level.getServer(), base));
    }

    /** 服务端的计数器，客户端是另一个：哪一边有星球的状态变了就加一，缓存现用环境的地方拿它对比。 */
    public static int revision(boolean client) {
        return (client ? CLIENT : SERVER).revision.get();
    }

    // ---- 现用的星球（客户端）

    /** 客户端收到的这颗星球的状态。 */
    public static TerraformState clientState(BodyId id) {
        return CLIENT_STATES.getOrDefault(id, TerraformState.EMPTY);
    }

    public static EffectiveEnvironment clientEnvironment(Galaxy galaxy, Planet base) {
        return CLIENT.lookup(() -> galaxy, base, Terraforming::clientState).environment();
    }

    /** 客户端现用的星球；没动过的就是 {@code base} 本身。 */
    public static Planet clientEffective(Galaxy galaxy, Planet base) {
        if (!Terraform.terraformable(base) || CLIENT_STATES.isEmpty()) {
            return base;
        }
        return clientEnvironment(galaxy, base).planet();
    }

    /** 客户端收到同步。 */
    public static void receive(TerraformPayloads.Sync sync) {
        if (sync.replace()) {
            CLIENT_STATES.clear();
        }
        for (TerraformPayloads.Entry entry : sync.entries()) {
            BodyId.parse(entry.path()).ifPresent(id -> CLIENT_STATES.put(id, entry.state()));
        }
        CLIENT.revision.incrementAndGet();
    }

    /** 客户端退出世界。 */
    public static void clearClient() {
        CLIENT_STATES.clear();
        CLIENT.clear();
    }

    // ---- 改动

    /**
     * 改一颗星球的状态：存盘，新到的阶段全服提示一次，再同步给客户端。不能改造的星球不改。
     *
     * @return 改完的状态
     */
    public static TerraformState update(MinecraftServer server, BodyId id, UnaryOperator<TerraformState> change) {
        Optional<Planet> found = Planets.galaxy(server).body(id).filter(Terraform::terraformable);
        if (found.isEmpty()) {
            return TerraformState.EMPTY;
        }
        Planet base = found.get();
        TerraformData data = TerraformData.get(server);
        String path = id.path();
        TerraformState before = data.state(path);
        TerraformState after = change.apply(before);
        Terraform.Announcement announcement = Terraform.announce(base, compactStar(server, base), after);
        after = announcement.state();
        if (after.equals(before)) {
            return before;
        }
        data.put(path, after);
        if (after.withLastUpdate(0).equals(before.withLastUpdate(0))) {
            // 只是记了推进到的时间：环境没变，不用重算、不用同步
            return after;
        }
        SERVER.revision.incrementAndGet();
        announce(server, base, announcement.stages());
        changed(server, path);
        return after;
    }

    /** 放进（正）或抽走（负）一种气体；抽走的不会比星球上有的多。 */
    public static TerraformState addGas(MinecraftServer server, BodyId id, Gas gas, double units) {
        Optional<Planet> base = Planets.galaxy(server).body(id);
        return base.isEmpty() ? TerraformState.EMPTY : update(server, id, state -> Terraform.addGas(base.get(), state, gas, units));
    }

    /**
     * 大气提取器从这个世界的大气里取走 {@code units} 个单位的 {@code gas}。能改造的星球上从大气里扣掉，最多扣到这种气体没了，
     * 返回实际取走的；母星、气态巨行星这些不改造的大气取之不尽，原样返回。不在星球上返回 0。
     */
    public static double withdraw(ServerLevel level, Gas gas, double units) {
        Optional<Planet> body = Planets.body(level);
        if (body.isEmpty() || units <= 0) {
            return 0;
        }
        Planet base = body.get();
        if (!Terraform.terraformable(base)) {
            return units;
        }
        MinecraftServer server = level.getServer();
        double taken = Math.min(units, Terraform.available(base, state(server, base.id()), gas));
        if (taken <= 0) {
            return 0;
        }
        update(server, base.id(), state -> Terraform.addGas(base, state, gas, -taken));
        return taken;
    }

    /** 这颗星球在不在致密恒星（白矮星、中子星、黑洞）旁边。 */
    static boolean compactStar(MinecraftServer server, Planet base) {
        return Planets.galaxy(server).system(base.id().system()).map(system -> system.star().starClass().isCompact()).orElse(false);
    }

    // ---- 轨道反射镜

    /** 登记一面反射镜（已经登记过的不再算一次）。 */
    public static void addMirror(ServerLevel level, BlockPos pos, BodyId planet, boolean shade) {
        TerraformData data = TerraformData.get(level.getServer());
        String dimension = level.dimension().identifier().toString();
        if (data.mirror(dimension, pos.asLong()).isPresent()) {
            return;
        }
        data.putMirror(new TerraformData.Mirror(dimension, pos.asLong(), planet.path(), shade));
        update(level.getServer(), planet, state -> shade ? state.withShades(state.shades() + 1) : state.withMirrors(state.mirrors() + 1));
    }

    /** 反射镜拆掉了：这颗星球少一面。 */
    public static void removeMirror(ServerLevel level, BlockPos pos) {
        TerraformData data = TerraformData.get(level.getServer());
        data.removeMirror(level.dimension().identifier().toString(), pos.asLong()).ifPresent(mirror -> BodyId.parse(mirror.planet())
                .ifPresent(planet -> update(level.getServer(), planet, state -> mirror.shade()
                        ? state.withShades(state.shades() - 1)
                        : state.withMirrors(state.mirrors() - 1))));
    }

    /** 切成遮阳板（或者切回反射镜）。没登记过的不管。 */
    public static void setShade(ServerLevel level, BlockPos pos, boolean shade) {
        TerraformData data = TerraformData.get(level.getServer());
        String dimension = level.dimension().identifier().toString();
        Optional<TerraformData.Mirror> found = data.mirror(dimension, pos.asLong());
        if (found.isEmpty() || found.get().shade() == shade) {
            return;
        }
        TerraformData.Mirror mirror = found.get();
        data.putMirror(new TerraformData.Mirror(mirror.dimension(), mirror.pos(), mirror.planet(), shade));
        BodyId.parse(mirror.planet()).ifPresent(planet -> update(level.getServer(), planet, state -> shade
                ? state.withMirrors(state.mirrors() - 1).withShades(state.shades() + 1)
                : state.withShades(state.shades() - 1).withMirrors(state.mirrors() + 1)));
    }

    /** 这一格登记的反射镜。 */
    public static Optional<TerraformData.Mirror> mirror(ServerLevel level, BlockPos pos) {
        return TerraformData.get(level.getServer()).mirror(level.dimension().identifier().toString(), pos.asLong());
    }

    // ---- 生态播种站

    /** 现在播这组基因会怎样（不改状态）：每个条件对不对得上、种不种得活。 */
    public static Terraform.Seeding seeding(MinecraftServer server, BodyId id, Set<Gene> genes) {
        Optional<Planet> base = Planets.galaxy(server).body(id);
        TerraformState state = state(server, id);
        if (base.isEmpty()) {
            return new Terraform.Seeding(false, List.of(), state);
        }
        return Terraform.seed(base.get(), compactStar(server, base.get()), state, genes);
    }

    /**
     * 播一份先锋生物（{@link Terraform#seed}）：环境对得上就记下这组基因、生物量加一点。
     *
     * @return 种活了
     */
    public static boolean seed(MinecraftServer server, BodyId id, Set<Gene> genes) {
        Optional<Planet> base = Planets.galaxy(server).body(id);
        if (base.isEmpty() || !seeding(server, id, genes).viable()) {
            return false;
        }
        boolean compact = compactStar(server, base.get());
        update(server, id, state -> Terraform.seed(base.get(), compact, state, genes).state());
        return true;
    }

    /** 登记一座在浇水的播种站（已经登记在别的星球上的换过来），重算两颗星球的灌溉。 */
    public static void addSeeder(ServerLevel level, BlockPos pos, BodyId planet) {
        TerraformData data = TerraformData.get(level.getServer());
        String dimension = level.dimension().identifier().toString();
        Optional<TerraformData.Seeder> old = data.seeder(dimension, pos.asLong());
        if (old.isPresent() && old.get().planet().equals(planet.path())) {
            return;
        }
        data.putSeeder(new TerraformData.Seeder(dimension, pos.asLong(), planet.path()));
        old.flatMap(seeder -> BodyId.parse(seeder.planet())).ifPresent(id -> irrigate(level.getServer(), id));
        irrigate(level.getServer(), planet);
    }

    /** 播种站没水了或者拆掉了：不再算它浇的水。 */
    public static void removeSeeder(ServerLevel level, BlockPos pos) {
        TerraformData data = TerraformData.get(level.getServer());
        data.removeSeeder(level.dimension().identifier().toString(), pos.asLong())
                .flatMap(seeder -> BodyId.parse(seeder.planet()))
                .ifPresent(id -> irrigate(level.getServer(), id));
    }

    /** 这一格登记着的在浇水的播种站。 */
    public static Optional<TerraformData.Seeder> seeder(ServerLevel level, BlockPos pos) {
        return TerraformData.get(level.getServer()).seeder(level.dimension().identifier().toString(), pos.asLong());
    }

    /** 给这颗星球浇水的站有几座。 */
    public static int seeders(MinecraftServer server, BodyId id) {
        return TerraformData.get(server).seeders(id.path());
    }

    /** 按在浇水的站数重算一颗星球的灌溉（{@link TerraformRules#irrigation}）。 */
    private static void irrigate(MinecraftServer server, BodyId id) {
        double irrigation = TerraformRules.irrigation(seeders(server, id));
        update(server, id, state -> state.withIrrigation(irrigation));
    }

    // ---- 推进

    private static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (now % ADVANCE_INTERVAL == 0) {
            advance(server, now);
        }
        if (now % FLUSH_INTERVAL == 0 && !PENDING.isEmpty()) {
            flush(server, now);
        }
    }

    /**
     * 动过的星球按上次推进的时间补齐：生物量长或死、光合作用换氧，大气化学按条件去掉毒气（{@link Terraform#advance}）。
     * 没动过的星球不推进。
     */
    static void advance(MinecraftServer server, long now) {
        TerraformData data = TerraformData.get(server);
        Galaxy galaxy = Planets.galaxy(server);
        for (Map.Entry<String, TerraformState> entry : List.copyOf(data.states().entrySet())) {
            TerraformState state = entry.getValue();
            if (state.isEmpty() && state.pioneers().isEmpty() && state.biomass() <= 0) {
                continue;
            }
            Optional<BodyId> id = BodyId.parse(entry.getKey());
            Optional<Planet> base = id.flatMap(galaxy::body).filter(Terraform::terraformable);
            if (base.isEmpty()) {
                continue;
            }
            long last = state.lastUpdate();
            long ticks = last <= 0 || last > now ? ADVANCE_INTERVAL : now - last;
            boolean compact = compactStar(server, base.get());
            update(server, id.get(), current -> Terraform.advance(current, base.get(), compact, ticks).withLastUpdate(now));
        }
    }

    // ---- 阶段提示

    private static void announce(MinecraftServer server, Planet base, EnumSet<Terraform.Stage> stages) {
        if (stages.isEmpty()) {
            return;
        }
        Component name = Discoveries.name(server, DiscoveryKey.body(base.id()));
        for (Terraform.Stage stage : stages) {
            server.getPlayerList().broadcastSystemMessage(Component.translatable("message.universe.terraform.stage." + TerraformRules.stageKey(base, stage,
                    compactStar(server, base)), name), false);
        }
        if (stages.contains(Terraform.Stage.BREATHABLE)) {
            // 改造全服共享，在线的人都拿到进度
            server.getPlayerList().getPlayers().forEach(player ->
                    io.github.sweetcornna.universe.advancement.MilestoneTrigger.reach(player, io.github.sweetcornna.universe.advancement.MilestoneTrigger.TERRAFORMED));
        }
    }

    // ---- 同步

    /** 这颗星球的状态变了：隔够了马上发，不够的等到够了再发。 */
    private static void changed(MinecraftServer server, String path) {
        PENDING.add(path);
        long now = server.overworld().getGameTime();
        Long sent = SENT.get(path);
        if (sent == null || now - sent >= SYNC_INTERVAL || now < sent) {
            flush(server, now);
        }
    }

    private static void flush(MinecraftServer server, long now) {
        TerraformData data = TerraformData.get(server);
        List<TerraformPayloads.Entry> entries = new ArrayList<>();
        for (String path : List.copyOf(PENDING)) {
            Long sent = SENT.get(path);
            if (sent != null && now - sent < SYNC_INTERVAL && now >= sent) {
                continue;
            }
            PENDING.remove(path);
            SENT.put(path, now);
            entries.add(TerraformPayloads.Entry.of(path, data.state(path)));
        }
        if (!entries.isEmpty()) {
            PacketDistributor.sendToAllPlayers(new TerraformPayloads.Sync(false, entries));
        }
    }

    /** 登录：发全部动过的星球。 */
    private static void sendAll(ServerPlayer player) {
        List<TerraformPayloads.Entry> entries = new ArrayList<>();
        TerraformData.get(player.level().getServer()).states().forEach((path, state) -> {
            if (!TerraformData.blank(state)) {
                entries.add(TerraformPayloads.Entry.of(path, state));
            }
        });
        PacketDistributor.sendToPlayer(player, new TerraformPayloads.Sync(true, entries));
    }

    /** 换了维度：再发一次脚下这颗星球的，免得错过了节流期间的变化。 */
    private static void sendCurrent(ServerPlayer player) {
        Optional<BodyId> id = Planets.bodyId(player.level().dimension());
        if (id.isEmpty()) {
            return;
        }
        String path = id.get().path();
        TerraformState state = TerraformData.get(player.level().getServer()).state(path);
        if (!TerraformData.blank(state)) {
            PacketDistributor.sendToPlayer(player, new TerraformPayloads.Sync(false, List.of(TerraformPayloads.Entry.of(path, state))));
        }
    }
}
