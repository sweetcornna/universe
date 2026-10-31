package io.github.sweetcornna.universe.portal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.portal.Glyphs;
import io.github.sweetcornna.universe.core.portal.PortalAddress;
import io.github.sweetcornna.universe.core.portal.PortalLayout;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;
import io.github.sweetcornna.universe.discovery.Discoveries;
import io.github.sweetcornna.universe.core.discovery.DiscoveryKey;
import io.github.sweetcornna.universe.core.procgen.NameGenerator.Name;
import io.github.sweetcornna.universe.block.portal.PortalGlyphBlock;
import io.github.sweetcornna.universe.network.PortalPayloads;
import io.github.sweetcornna.universe.perf.ServerPerf;
import io.github.sweetcornna.universe.poi.PoiProgress;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.sound.WorldSounds;
import io.github.sweetcornna.universe.world.DimensionTemplate;
import io.github.sweetcornna.universe.world.RuntimeDimensions;
import io.github.sweetcornna.universe.world.planet.PlanetCommand;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 先驱者传送门（ADR-0024）的服务端。
 *
 * <ul>
 * <li>每颗能着陆的星球（母星也有）一座：着陆点加上 {@link Glyphs#offset} 的偏移，门前朝着着陆点。玩家走到
 * {@link Glyphs#PLACE_RANGE} 格内、那里的区块已经加载时才放下，老存档的星球也有；拨号去的那一头当场放。
 * <li>石环上刻着这座门的地址。第一次走到门前教一个没学过的符文。
 * <li>拨号台上拨六个认识的符文：门开 {@link Glyphs#OPEN_TICKS} 刻，走进去到那颗星球的门前；那边的门也打开、指回这里，
 * 同样的时间里能原路回来。船不跟着过去。
 * </ul>
 */
public final class Portals {
    private static final int CHECK_INTERVAL = 40;
    /** 走到门前多近算"见到了这座门"。 */
    private static final double SEEN_RANGE = 12;
    /** 离拨号台多远以内能拨号。 */
    private static final double DIAL_RANGE = 8;
    /** 穿过门以后多久不再被传送（刻），免得来回弹。 */
    private static final int COOLDOWN = 60;

    /** 一座门：石台中心的台面那一格，门前朝哪。 */
    public record Site(BlockPos origin, Direction front) {
        public BlockPos at(int x, int y, int z) {
            int[] d = PortalLayout.rotate(x, z, this.front.getStepX(), this.front.getStepZ());
            return this.origin.offset(d[0], y, d[1]);
        }

        public BlockPos dial() {
            int[] d = PortalLayout.dial();
            return this.at(d[0], d[1], d[2]);
        }
    }

    /** 开着的门：拨到哪颗星球，主世界的游戏时间到哪一刻关上。 */
    private record Open(BodyId target, long closes) {
    }

    /** 还没放下的门打算放在哪 {x, z, 门前的朝向}；不是能着陆的星球就是空的。 */
    private static final Map<ResourceKey<Level>, Optional<int[]>> PLANNED = new HashMap<>();
    private static final Map<ResourceKey<Level>, Open> OPEN = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();

    private Portals() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ServerTickEvent.Post.class, event -> ServerPerf.time("portals", () -> tick(event.getServer())));
        gameBus.addListener(ServerStoppedEvent.class, event -> {
            PLANNED.clear();
            OPEN.clear();
            COOLDOWNS.clear();
        });
    }

    private static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    /** 这个维度是哪颗能着陆的星球（主世界是母星）。 */
    public static Optional<Planet> planet(ServerLevel level) {
        Galaxy galaxy = Planets.galaxy(level.getServer());
        return Planets.bodyId(level.dimension()).flatMap(galaxy::body).filter(Planet::landable);
    }

    /** 这颗星球的门的地址。 */
    public static int[] address(MinecraftServer server, BodyId id) {
        return new PortalAddress(id.system(), id.planet(), id.moon()).glyphs(Planets.galaxy(server).seed());
    }

    /** 已经放下的门。 */
    public static Optional<Site> site(ServerLevel level) {
        return PortalSites.get(level.getServer()).find(level.dimension().identifier().toString())
                .map(p -> new Site(new BlockPos(p.x(), p.y(), p.z()), Direction.from2DDataValue(p.front())));
    }

    /** 门在哪：没放就当场放下（会生成那里的区块）。不是能着陆的星球返回空。 */
    public static Optional<Site> ensure(ServerLevel level) {
        Optional<Site> site = site(level);
        if (site.isPresent()) {
            return site;
        }
        return planned(level).map(plan -> place(level, plan));
    }

    private static Optional<int[]> planned(ServerLevel level) {
        return PLANNED.computeIfAbsent(level.dimension(), key -> planet(level).map(planet -> {
            int[] center;
            if (level.dimension() == Level.OVERWORLD) {
                BlockPos spawn = level.getRespawnData().pos();
                center = new int[] {spawn.getX(), spawn.getZ()};
            } else {
                center = PlanetCommand.landingColumn(planet);
            }
            int[] offset = Glyphs.offset(planet.seed());
            int x = center[0] + offset[0];
            int z = center[1] + offset[1];
            // 门前朝着着陆点
            int dx = center[0] - x;
            int dz = center[1] - z;
            Direction front = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
            return new int[] {x, z, front.get2DDataValue()};
        }));
    }

    private static Site place(ServerLevel level, int[] plan) {
        int x = plan[0];
        int z = plan[1];
        Direction front = Direction.from2DDataValue(plan[2]);
        Site site = new Site(new BlockPos(x, 0, z), front);
        // 台面压在整块台子底下地表高度的中位数上（一棵树、一块石头不会把门顶起来），上面清空，下面垫到实心的地面
        List<Integer> heights = new ArrayList<>();
        for (int dx = -PortalLayout.PLATFORM_HALF_WIDTH; dx <= PortalLayout.PLATFORM_HALF_WIDTH; dx++) {
            for (int dz = -PortalLayout.PLATFORM_HALF_DEPTH; dz <= PortalLayout.PLATFORM_HALF_DEPTH; dz++) {
                BlockPos column = site.at(dx, 0, dz);
                level.getChunk(column.getX() >> 4, column.getZ() >> 4);
                heights.add(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()) - 1);
            }
        }
        heights.sort(null);
        int y = heights.get(heights.size() / 2);
        y = Math.max(level.getMinY() + 1, Math.min(y, level.getMaxY() - PortalLayout.CLEAR_HEIGHT - 2));
        site = new Site(new BlockPos(x, y, z), front);
        Optional<BodyId> id = Planets.bodyId(level.dimension());
        int[] glyphs = id.map(b -> address(level.getServer(), b)).orElse(new int[PortalAddress.LENGTH]);
        build(level, site, glyphs);
        PortalSites.get(level.getServer()).put(level.dimension().identifier().toString(), new PortalSites.Placed(x, y, z, front.get2DDataValue()));
        return site;
    }

    /** 按 {@link PortalLayout} 摆方块（门关着，环里是空气）。GameTest 也用。 */
    public static void build(ServerLevel level, Site site, int[] glyphs) {
        BlockState stone = UniverseBlocks.PORTAL_STONE.get().defaultBlockState();
        for (int x = -PortalLayout.PLATFORM_HALF_WIDTH; x <= PortalLayout.PLATFORM_HALF_WIDTH; x++) {
            for (int z = -PortalLayout.PLATFORM_HALF_DEPTH; z <= PortalLayout.PLATFORM_HALF_DEPTH; z++) {
                for (int y = 1; y <= PortalLayout.CLEAR_HEIGHT; y++) {
                    level.setBlock(site.at(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                for (int y = -1; y >= -PortalLayout.SUPPORT_DEPTH; y--) {
                    BlockPos below = site.at(x, y, z);
                    BlockState there = level.getBlockState(below);
                    if (below.getY() < level.getMinY() || there.isSolidRender() && there.getFluidState().isEmpty()) {
                        break;
                    }
                    level.setBlock(below, stone, Block.UPDATE_CLIENTS);
                }
            }
        }
        for (PortalLayout.Cell cell : PortalLayout.cells()) {
            BlockState state = switch (cell.part()) {
                case STONE -> stone;
                case GLYPH -> UniverseBlocks.PORTAL_GLYPH.get().defaultBlockState().setValue(PortalGlyphBlock.GLYPH, glyphs[cell.glyph()]);
                case DIAL -> UniverseBlocks.PORTAL_DIAL.get().defaultBlockState();
                case FIELD -> Blocks.AIR.defaultBlockState();
            };
            level.setBlock(site.at(cell.x(), cell.y(), cell.z()), state, Block.UPDATE_ALL);
        }
    }

    /** 拆掉这座门（只拆传送门的方块），忘掉它的位置；下次走近时按现在的规则重新放。调试命令用。 */
    public static void demolish(ServerLevel level, Site site) {
        OPEN.remove(level.dimension());
        for (int x = -PortalLayout.PLATFORM_HALF_WIDTH; x <= PortalLayout.PLATFORM_HALF_WIDTH; x++) {
            for (int z = -PortalLayout.PLATFORM_HALF_DEPTH; z <= PortalLayout.PLATFORM_HALF_DEPTH; z++) {
                for (int y = -PortalLayout.SUPPORT_DEPTH; y <= PortalLayout.CLEAR_HEIGHT; y++) {
                    BlockPos pos = site.at(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(UniverseBlocks.PORTAL_STONE.get()) || state.is(UniverseBlocks.PORTAL_GLYPH.get())
                            || state.is(UniverseBlocks.PORTAL_DIAL.get()) || state.is(UniverseBlocks.PORTAL_FIELD.get())) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        PortalSites.get(level.getServer()).remove(level.dimension().identifier().toString());
        PLANNED.remove(level.dimension());
    }

    private static void tick(MinecraftServer server) {
        long now = now(server);
        if (now % CHECK_INTERVAL != 0) {
            return;
        }
        // 到点的门关上
        List<ResourceKey<Level>> expired = new ArrayList<>();
        OPEN.forEach((key, open) -> {
            if (open.closes() <= now) {
                expired.add(key);
            }
        });
        for (ResourceKey<Level> key : expired) {
            ServerLevel level = server.getLevel(key);
            if (level != null) {
                close(level);
            } else {
                OPEN.remove(key);
            }
        }
        COOLDOWNS.values().removeIf(until -> until <= now);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() instanceof ServerLevel level) {
                approach(player, level);
            }
        }
    }

    /** 走近还没放下的门就放下；走到门前教一个符文（每座门每人一次）。 */
    private static void approach(ServerPlayer player, ServerLevel level) {
        Optional<Site> site = site(level);
        if (site.isEmpty()) {
            Optional<int[]> plan = planned(level);
            if (plan.isEmpty()) {
                return;
            }
            int x = plan.get()[0];
            int z = plan.get()[1];
            double dx = player.getX() - x;
            double dz = player.getZ() - z;
            if (dx * dx + dz * dz > Glyphs.PLACE_RANGE * Glyphs.PLACE_RANGE || !level.hasChunkAt(new BlockPos(x, 0, z))) {
                return;
            }
            site = Optional.of(place(level, plan.get()));
        }
        BlockPos origin = site.get().origin();
        if (player.distanceToSqr(Vec3.atCenterOf(origin)) > SEEN_RANGE * SEEN_RANGE) {
            return;
        }
        Optional<Planet> planet = planet(level);
        if (planet.isEmpty()) {
            return;
        }
        long key = Seeds.derive(planet.get().seed(), "portal_seen");
        PoiProgress progress = player.getData(UniverseAttachments.POI_PROGRESS);
        if (progress.claim(key)) {
            PortalGlyphs.learn(player, Rng.of(key ^ player.getUUID().getLeastSignificantBits()));
        }
    }

    // ---- 拨号

    /** 右键拨号台：打开拨号盘。 */
    public static void openDial(ServerPlayer player, ServerLevel level, BlockPos dial) {
        Optional<Site> site = site(level);
        Optional<BodyId> here = Planets.bodyId(level.dimension());
        if (site.isEmpty() || here.isEmpty() || !site.get().dial().equals(dial)) {
            player.sendOverlayMessage(Component.translatable("message.universe.portal.dormant"));
            return;
        }
        List<Integer> own = new ArrayList<>();
        for (int glyph : address(level.getServer(), here.get())) {
            own.add(glyph);
        }
        PacketDistributor.sendToPlayer(player, new PortalPayloads.Open(dial, own, PortalGlyphs.known(player), player.isCreative()));
    }

    public static void handleDial(PortalPayloads.Dial payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Optional<Site> site = site(level);
        if (site.isEmpty() || !site.get().dial().equals(payload.dial())
                || player.distanceToSqr(Vec3.atCenterOf(payload.dial())) > DIAL_RANGE * DIAL_RANGE) {
            return;
        }
        int[] glyphs = payload.glyphs().stream().mapToInt(Integer::intValue).toArray();
        if (glyphs.length != PortalAddress.LENGTH) {
            return;
        }
        if (!player.isCreative() && !Glyphs.canDial(PortalGlyphs.known(player), glyphs)) {
            player.sendOverlayMessage(Component.translatable("message.universe.portal.unknown_glyph"));
            return;
        }
        Component problem = dial(level, site.get(), glyphs);
        if (problem != null) {
            player.sendOverlayMessage(problem);
            level.playSound(null, payload.dial(), WorldSounds.PORTAL_DENY.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    /** 拨这个地址：通了就开门，返回 null；不通返回原因。 */
    public static Component dial(ServerLevel level, Site site, int[] glyphs) {
        MinecraftServer server = level.getServer();
        Optional<PortalAddress> address = PortalAddress.decode(Planets.galaxy(server).seed(), glyphs);
        Optional<BodyId> target = address.map(a -> new BodyId(a.system(), a.planet(), a.moon()))
                .filter(id -> Planets.galaxy(server).body(id).filter(Planet::landable).isPresent());
        if (target.isEmpty()) {
            return Component.translatable("message.universe.portal.no_answer");
        }
        if (Planets.bodyId(level.dimension()).equals(target)) {
            return Component.translatable("message.universe.portal.same");
        }
        open(level, site, target.get(), now(server) + Glyphs.OPEN_TICKS);
        Planet planet = Planets.galaxy(server).body(target.get()).orElseThrow();
        Name name = Discoveries.resolve(server, DiscoveryKey.body(planet.id()), planet.name());
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(Vec3.atCenterOf(site.origin())) < 32 * 32) {
                player.sendOverlayMessage(Component.translatable("message.universe.portal.opened", name.zh(), name.en(), Glyphs.OPEN_TICKS / 20));
            }
        }
        return null;
    }

    /** 开门：环里填上那层光。 */
    static void open(ServerLevel level, Site site, BodyId target, long closes) {
        BlockState field = UniverseBlocks.PORTAL_FIELD.get().defaultBlockState();
        for (int[] c : PortalLayout.field()) {
            level.setBlock(site.at(c[0], c[1], c[2]), field, Block.UPDATE_CLIENTS);
        }
        OPEN.put(level.dimension(), new Open(target, closes));
        BlockPos center = site.at(0, 3, 0);
        level.playSound(null, center, WorldSounds.PORTAL_OPEN.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.END_ROD, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 60, 1.6, 1.6, 0.3, 0.05);
    }

    /** 关门：那层光撤掉。 */
    public static void close(ServerLevel level) {
        OPEN.remove(level.dimension());
        site(level).ifPresent(site -> close(level, site));
    }

    /** 撤掉这座门环里的光。GameTest 也用。 */
    public static void close(ServerLevel level, Site site) {
        OPEN.remove(level.dimension());
        for (int[] c : PortalLayout.field()) {
            BlockPos pos = site.at(c[0], c[1], c[2]);
            if (level.getBlockState(pos).is(UniverseBlocks.PORTAL_FIELD.get())) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        level.playSound(null, site.at(0, 3, 0), WorldSounds.PORTAL_CLOSE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    /** 这个维度的门开着没有（那层光查自己该不该还在）。 */
    public static boolean isOpen(ServerLevel level, BlockPos pos) {
        return OPEN.containsKey(level.dimension());
    }

    /** 走进那层光：到拨通的那颗星球的门前，那边的门也开着、指回这里。 */
    public static void enter(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Open open = OPEN.get(level.dimension());
        if (open == null) {
            level.removeBlock(pos, false);
            return;
        }
        MinecraftServer server = level.getServer();
        long now = now(server);
        Long until = COOLDOWNS.get(player.getUUID());
        if (player.isPassenger() || until != null && until > now) {
            return;
        }
        ResourceKey<Level> key = Planets.dimension(open.target());
        ServerLevel target = key == Level.OVERWORLD ? server.overworld() : RuntimeDimensions.getOrCreate(server, key, DimensionTemplate.PLANET);
        Optional<Site> there = ensure(target);
        Optional<BodyId> here = Planets.bodyId(level.dimension());
        if (there.isEmpty() || here.isEmpty()) {
            return;
        }
        if (!OPEN.containsKey(target.dimension())) {
            open(target, there.get(), here.get(), Math.max(open.closes(), now + Glyphs.OPEN_TICKS));
        }
        int[] a = PortalLayout.arrival();
        BlockPos arrival = there.get().at(a[0], a[1], a[2]);
        Direction front = there.get().front();
        float yaw = (float) Math.toDegrees(Math.atan2(-front.getStepX(), front.getStepZ()));
        COOLDOWNS.put(player.getUUID(), now + COOLDOWN);
        player.teleport(new TeleportTransition(target, Vec3.atBottomCenterOf(arrival), Vec3.ZERO, yaw, 0,
                TeleportTransition.PLACE_PORTAL_TICKET.then(Portals::arrived)));
        Planet planet = Planets.galaxy(server).body(open.target()).orElseThrow();
        Name name = Discoveries.resolve(server, DiscoveryKey.body(planet.id()), planet.name());
        player.sendOverlayMessage(Component.translatable("message.universe.portal.arrived", name.zh(), name.en()));
    }

    /** 从门里出来：在门前放一声穿过传送门的声音，旁边的人也听得见。 */
    private static void arrived(Entity entity) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), WorldSounds.PORTAL_TRAVEL, SoundSource.PLAYERS, 1.0F, 1.0F);
    }
}
