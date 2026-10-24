package io.github.sweetcornna.universe.poi;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.block.landmark.FacilityTerminalBlock;
import io.github.sweetcornna.universe.block.landmark.ReinforcedDoorBlock;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.poi.DoorBreach;
import io.github.sweetcornna.universe.core.poi.FacilityModules;
import io.github.sweetcornna.universe.core.poi.Poi;
import io.github.sweetcornna.universe.core.poi.PoiLayout;
import io.github.sweetcornna.universe.core.poi.PoiType;
import io.github.sweetcornna.universe.core.portal.Glyphs;
import io.github.sweetcornna.universe.core.procgen.Seeds;
import io.github.sweetcornna.universe.core.sentinel.Offense;
import io.github.sweetcornna.universe.core.sentinel.SentinelKind;
import io.github.sweetcornna.universe.core.tech.TechModule;
import io.github.sweetcornna.universe.core.terrain.TerrainModel;
import io.github.sweetcornna.universe.portal.Portals;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.registry.sound.WorldSounds;
import io.github.sweetcornna.universe.sentinel.SentinelPillars;
import io.github.sweetcornna.universe.sentinel.Sentinels;
import io.github.sweetcornna.universe.world.planet.PlanetCommand;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * 交易站、制造设施、观测站里能互动的东西（ADR-0026）：用激光烧开加固门、在制造设施的终端挑模块、门外的守卫、
 * 观测站标出附近没去过的地方。交易站的终端走 {@code Trading}。
 */
public final class OutpostInteractions {
    /** 有人走到制造设施的终端多近，门外就放守卫。 */
    static final double GUARD_TRIGGER = 48;
    /** 守卫离终端多远（在门外）。 */
    static final int GUARD_DISTANCE = 11;
    /** 观测站找多远以内的东西。 */
    public static final int OBSERVATORY_RADIUS = 3000;
    /** 离终端多远还能挑模块。 */
    static final double REACH = 8;
    /** 一扇门最多几格（并排的门一起开）。 */
    private static final int DOOR_LIMIT = 16;
    /** 每个维度里正在被激光烧的门：维度 → 门最小的那格 → 攒的热量。 */
    private static final Map<String, Map<Long, DoorBreach>> DOORS = new HashMap<>();

    private OutpostInteractions() {
    }

    // ---- 加固门

    /**
     * 采矿激光照到了一格加固门（{@code Multitool} 每刻调）。攒够 10 秒就把整扇门烧开，开门的人三级通缉。
     *
     * @param player 照门的人；游戏内测试里可以为空
     * @return 这一格是不是加固门（是的话采矿激光不再往下处理）
     */
    public static boolean laser(@Nullable ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof ReinforcedDoorBlock)) {
            return false;
        }
        List<BlockPos> door = door(level, pos);
        long key = door.stream().mapToLong(BlockPos::asLong).min().orElse(pos.asLong());
        long now = level.getGameTime();
        Map<Long, DoorBreach> doors = DOORS.computeIfAbsent(level.dimension().identifier().toString(), k -> new HashMap<>());
        doors.values().removeIf(breach -> breach.stale(now));
        DoorBreach breach = doors.computeIfAbsent(key, k -> new DoorBreach());
        if (breach.hit(now)) {
            doors.remove(key);
            open(player, level, door);
            return true;
        }
        int stage = Math.clamp((int) (breach.progress() * 10), 0, 9);
        for (BlockPos block : door) {
            level.destroyBlockProgress(crackId(block), block, stage);
        }
        Vec3 center = Vec3.atCenterOf(pos);
        level.sendParticles(ParticleTypes.LAVA, center.x, center.y, center.z, 1, 0.3, 0.3, 0.3, 0);
        if (now % 8 == 0) {
            level.sendParticles(ParticleTypes.SMOKE, center.x, center.y + 0.4, center.z, 3, 0.25, 0.2, 0.25, 0.01);
        }
        if (player != null && now % 5 == 0) {
            player.sendOverlayMessage(Component.translatable("message.universe.reinforced_door.heat", Math.round(breach.progress() * 100)));
        }
        return true;
    }

    /** 这一格门连着的所有门（上下两半、并排的几扇）。 */
    static List<BlockPos> door(ServerLevel level, BlockPos start) {
        List<BlockPos> found = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start.immutable());
        seen.add(start.immutable());
        while (!queue.isEmpty() && found.size() < DOOR_LIMIT) {
            BlockPos pos = queue.poll();
            if (!level.getBlockState(pos).is(UniverseBlocks.REINFORCED_DOOR.get())) {
                continue;
            }
            found.add(pos);
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return found;
    }

    private static void open(@Nullable ServerPlayer player, ServerLevel level, List<BlockPos> door) {
        door.sort(Comparator.comparingInt((BlockPos pos) -> pos.getY()).reversed());
        for (BlockPos pos : door) {
            level.destroyBlockProgress(crackId(pos), pos, -1);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.02);
            level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.02);
        }
        BlockPos first = door.isEmpty() ? BlockPos.ZERO : door.getLast();
        level.playSound(null, first, WorldSounds.DOOR_BREACH.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        if (player != null) {
            player.sendOverlayMessage(Component.translatable("message.universe.reinforced_door.open"));
            Sentinels.offend(player, Offense.FACILITY_DOOR);
        }
    }

    /** 门上裂纹的编号：和玩家挖方块的裂纹（实体编号）分开。 */
    private static int crackId(BlockPos pos) {
        return Integer.MIN_VALUE + (pos.hashCode() & 0x3FFFFFFF);
    }

    // ---- 制造设施

    /** 右键制造设施的终端：没挑过的打开三选一的界面。 */
    public static void useFacilityTerminal(ServerPlayer player, ServerLevel level, BlockPos pos) {
        long key = Pois.claimKey(level, pos);
        PoiProgress progress = player.getData(UniverseAttachments.POI_PROGRESS);
        if (progress.claimed(key)) {
            player.sendOverlayMessage(Component.translatable("message.universe.facility_terminal.used"));
            level.playSound(null, pos, WorldSounds.TERMINAL_DENY.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
            return;
        }
        List<TechModule> modules = FacilityModules.offers(key, Pois.rarity(level));
        SimpleContainer offers = new SimpleContainer(FacilityTerminalMenu.OFFERS);
        for (int i = 0; i < modules.size(); i++) {
            TechModule module = modules.get(i);
            ItemStack stack = new ItemStack(UniverseItems.MODULES.get(module.type()).get());
            stack.set(UniverseDataComponents.TECH_MODULE.get(), module);
            offers.setItem(i, stack);
        }
        level.playSound(null, pos, WorldSounds.TERMINAL_ACCESS.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new FacilityTerminalMenu(id, inventory, offers,
                viewer -> viewer.distanceToSqr(Vec3.atCenterOf(pos)) <= REACH * REACH && level.getBlockState(pos).is(UniverseBlocks.FACILITY_TERMINAL.get()),
                () -> {
                    progress.claim(key);
                    level.playSound(null, pos, WorldSounds.TERMINAL_CONFIRM.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
                }), Component.translatable("container.universe.facility_terminal")), buf -> {
                });
    }

    /**
     * 制造设施的终端附近来了人：门外放 2 架无人机和 1 台四足机巡逻（不敌对）。守卫停着的星球先不放。
     *
     * @return 放了没有（放过就不再放）
     */
    public static boolean postGuards(ServerLevel level, BlockPos terminal, BlockState state) {
        if (!Sentinels.level(level).present() || Sentinels.dormant(level)) {
            return false;
        }
        if (level.getNearestPlayer(terminal.getX() + 0.5, terminal.getY(), terminal.getZ() + 0.5, GUARD_TRIGGER,
                EntitySelector.NO_SPECTATORS) == null) {
            return false;
        }
        Direction front = state.hasProperty(FacilityTerminalBlock.FACING) ? state.getValue(FacilityTerminalBlock.FACING) : Direction.NORTH;
        Direction side = front.getClockWise();
        guard(level, terminal.relative(front, GUARD_DISTANCE).relative(side, -3), SentinelKind.DRONE);
        guard(level, terminal.relative(front, GUARD_DISTANCE).relative(side, 3), SentinelKind.DRONE);
        guard(level, terminal.relative(front, GUARD_DISTANCE + 3), SentinelKind.QUADRUPED);
        return true;
    }

    private static void guard(ServerLevel level, BlockPos column, SentinelKind kind) {
        if (!level.hasChunkAt(column)) {
            return;
        }
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        for (int lift = 0; lift < 4; lift++) {
            double y = ground + (kind.flying() ? 4 : 0) + lift;
            if (Sentinels.spawnAt(level, new Vec3(column.getX() + 0.5, y, column.getZ() + 0.5), kind, null) != null) {
                return;
            }
        }
    }

    // ---- 观测站

    /** 观测站能标出的一处地方。 */
    private record Target(BlockPos pos, String type, Component name) {
    }

    /** 右键观测站的终端：标出附近最近的一处没去过的遗迹、传送门或守卫塔，设成航点。每人每台一次，找不到不算。 */
    public static void useObservatory(ServerPlayer player, ServerLevel level, BlockPos pos) {
        long key = Pois.claimKey(level, pos);
        PoiProgress progress = player.getData(UniverseAttachments.POI_PROGRESS);
        if (progress.claimed(key)) {
            player.sendOverlayMessage(Component.translatable("message.universe.observatory.used"));
            level.playSound(null, pos, WorldSounds.TERMINAL_DENY.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
            return;
        }
        Optional<Target> found = targets(level, pos, progress).stream()
                .min(Comparator.comparingDouble(target -> horizontalSq(pos, target.pos())));
        if (found.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.universe.observatory.nothing", OBSERVATORY_RADIUS));
            level.playSound(null, pos, WorldSounds.TERMINAL_DENY.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
            return;
        }
        progress.claim(key);
        Target target = found.get();
        player.setData(UniverseAttachments.WAYPOINT, new Waypoint(level.dimension(), target.pos(), target.type()));
        int distance = (int) Math.round(Math.sqrt(horizontalSq(player.blockPosition(), target.pos())));
        player.sendSystemMessage(Component.translatable("message.universe.observatory.located", target.name(),
                Component.translatable("direction.universe." + direction(player, target.pos())), distance));
        level.playSound(null, pos, WorldSounds.SIGNAL.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 20, 0.2, 0.6, 0.2, 0.05);
    }

    /** 候选：没去过的先驱者遗迹、还没走到跟前过的传送门、守卫塔。 */
    private static List<Target> targets(ServerLevel level, BlockPos from, PoiProgress progress) {
        List<Target> targets = new ArrayList<>();
        Pois.layout(level).ifPresent(layout -> layout.nearest(from.getX() + 0.5, from.getZ() + 0.5, OBSERVATORY_RADIUS,
                poi -> poi.type() == PoiType.RUINS && !progress.visited(poi.key()))
                .ifPresent(poi -> targets.add(ruin(level, layout, poi))));
        Optional<Planet> planet = Planets.planet(level);
        if (planet.isPresent() && !progress.claimed(Seeds.derive(planet.get().seed(), "portal_seen"))) {
            portal(level, planet.get()).filter(portal -> horizontalSq(from, portal) <= (double) OBSERVATORY_RADIUS * OBSERVATORY_RADIUS)
                    .ifPresent(portal -> targets.add(new Target(portal, Waypoint.PORTAL, Component.translatable("waypoint.universe.portal"))));
        }
        SentinelPillars.nearest(level, from).filter(pillar -> horizontalSq(from, pillar) <= (double) OBSERVATORY_RADIUS * OBSERVATORY_RADIUS)
                .ifPresent(pillar -> targets.add(new Target(pillar, Waypoint.SENTINEL_PILLAR,
                        Component.translatable("waypoint.universe.sentinel_pillar"))));
        return targets;
    }

    private static Target ruin(ServerLevel level, PoiLayout layout, Poi poi) {
        return new Target(new BlockPos(poi.x(), Pois.groundY(level, layout, poi), poi.z()), PoiType.RUINS.serializedName(),
                Pois.name(PoiType.RUINS));
    }

    /** 这颗星球的传送门：放下了用真实的位置，没放下的按着陆点和符文偏移算（和 {@code Portals} 一样），高度用地形估计。 */
    private static Optional<BlockPos> portal(ServerLevel level, Planet planet) {
        Optional<Portals.Site> site = Portals.site(level);
        if (site.isPresent()) {
            return Optional.of(site.get().origin());
        }
        if (level.dimension() == Level.OVERWORLD || !planet.landable()) {
            return Optional.empty();
        }
        int[] landing = PlanetCommand.landingColumn(planet);
        int[] offset = Glyphs.offset(planet.seed());
        int x = landing[0] + offset[0];
        int z = landing[1] + offset[1];
        int y = (int) Math.round(TerrainModel.of(planet).sample(x, z).height());
        return Optional.of(new BlockPos(x, y, z));
    }

    private static double horizontalSq(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /** 八个方位（和 {@link Pois} 的一致：东 0°，按逆时针）。 */
    private static String direction(ServerPlayer player, BlockPos target) {
        String[] directions = {"east", "southeast", "south", "southwest", "west", "northwest", "north", "northeast"};
        double dx = target.getX() + 0.5 - player.getX();
        double dz = target.getZ() + 0.5 - player.getZ();
        return directions[Math.floorMod(Math.round((float) Math.toDegrees(Math.atan2(dz, dx)) / 45F), 8)];
    }
}
