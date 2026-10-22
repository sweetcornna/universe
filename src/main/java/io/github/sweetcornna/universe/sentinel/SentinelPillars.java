package io.github.sweetcornna.universe.sentinel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.procgen.Seeds;
import io.github.sweetcornna.universe.core.sentinel.PillarLayout;
import io.github.sweetcornna.universe.core.sentinel.SentinelLevel;
import io.github.sweetcornna.universe.core.survival.MultitoolSpec;
import io.github.sweetcornna.universe.core.tech.Grade;
import io.github.sweetcornna.universe.poi.PoiProgress;
import io.github.sweetcornna.universe.poi.Pois;
import io.github.sweetcornna.universe.poi.Waypoint;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.survival.MultitoolItem;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 守卫塔（ADR-0026）：每颗有守卫机的星球一座，位置见 {@link PillarLayout}。
 *
 * <ul>
 * <li>和传送门一样，玩家走到 {@value #PLACE_RANGE} 格内、那里的区块已经加载时才放下，老存档的星球也有。放下的位置和高度存进
 * {@link PillarSites}（{@code pillars.dat}）。
 * <li>塔下的终端关停这颗星球的守卫机 {@value #SHUTDOWN_MINUTES} 分钟，一个游戏日一次；被通缉时不响应。
 * <li>拿着步行机主脑右键终端（或者潜行右键、主脑在背包里），交出主脑，取出一把守卫机型多功能工具（ADR-0026 §六）：
 * 档次看星球的守卫强度，一半的格子是坏的。每座塔每人一次。
 * <li>打光五级通缉以后，{@link #reveal} 把塔设成玩家的航点。
 * </ul>
 */
public final class SentinelPillars {
    public static final double PLACE_RANGE = 96;
    public static final int SHUTDOWN_MINUTES = 20;
    public static final long SHUTDOWN_TICKS = SHUTDOWN_MINUTES * 60 * 20L;
    private static final int CHECK_INTERVAL = 40;
    /** 走到塔前多近算"发现了守卫塔"。 */
    private static final double SEEN_RANGE = 24;
    private static final long DAY_TICKS = 24000L;

    /** 一座塔：底座中心那一格，终端朝哪。 */
    public record Site(BlockPos origin, Direction front) {
        public BlockPos terminal() {
            return PillarStructure.terminal(this.origin, this.front);
        }
    }

    /** 终端被右键的结果。 */
    public enum Use {
        /** 守卫机关停了。 */
        SHUT_DOWN,
        /** 被通缉中，不响应。 */
        WANTED,
        /** 今天已经关停过了。 */
        USED_TODAY
    }

    /** 还没放下的塔打算放在哪 {x, z, 终端的朝向}；没有守卫塔的星球是空的。 */
    private static final Map<ResourceKey<Level>, Optional<int[]>> PLANNED = new HashMap<>();

    private SentinelPillars() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ServerTickEvent.Post.class, event -> tick(event.getServer()));
        gameBus.addListener(ServerStoppedEvent.class, event -> PLANNED.clear());
    }

    private static String key(ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    /** 已经放下的塔。 */
    public static Optional<Site> site(ServerLevel level) {
        return PillarSites.get(level.getServer()).find(key(level))
                .map(p -> new Site(new BlockPos(p.x(), p.y(), p.z()), Direction.from2DDataValue(p.front())));
    }

    /**
     * 这颗星球的守卫塔在哪（底座中心那一格）：放下了用存下的位置，还没放下用算出来的位置和估计的高度。
     * 没有守卫机的星球、母星为空。一颗星球只有一座塔，{@code from} 只是为了以后有多座时挑最近的。
     */
    public static Optional<BlockPos> nearest(ServerLevel level, BlockPos from) {
        Optional<Site> site = site(level);
        if (site.isPresent()) {
            return Optional.of(site.get().origin());
        }
        return planned(level).flatMap(plan -> Planets.planet(level)
                .map(planet -> new BlockPos(plan[0], PillarLayout.estimatedY(planet, plan[0], plan[1]), plan[1])));
    }

    /** 打光五级通缉以后：把塔设成玩家的航点，提示塔在哪个方向、多远。 */
    public static void reveal(ServerPlayer player) {
        ServerLevel level = player.level();
        Optional<BlockPos> pillar = nearest(level, player.blockPosition());
        if (pillar.isEmpty()) {
            return;
        }
        BlockPos pos = pillar.get();
        Waypoint waypoint = new Waypoint(level.dimension(), pos, Waypoint.SENTINEL_PILLAR);
        player.setData(UniverseAttachments.WAYPOINT, waypoint);
        double dx = pos.getX() + 0.5 - player.getX();
        double dz = pos.getZ() + 0.5 - player.getZ();
        int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        player.sendSystemMessage(Component.translatable("message.universe.poi.located", waypoint.name(),
                Component.translatable("direction.universe." + direction(dx, dz)), distance));
    }

    /** 八个方位：东 0°，按逆时针（和信号增强器的提示一致）。 */
    private static String direction(double dx, double dz) {
        String[] directions = {"east", "southeast", "south", "southwest", "west", "northwest", "north", "northeast"};
        return directions[Math.floorMod(Math.round((float) Math.toDegrees(Math.atan2(dz, dx)) / 45F), 8)];
    }

    private static Optional<int[]> planned(ServerLevel level) {
        return PLANNED.computeIfAbsent(level.dimension(), key -> Planets.planet(level).flatMap(planet -> {
            int[] landing = PlanetCommand.landingColumn(planet);
            return PillarLayout.site(planet, landing).map(site -> {
                // 终端朝着降落点
                int dx = landing[0] - site[0];
                int dz = landing[1] - site[1];
                Direction front = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST)
                        : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
                return new int[] {site[0], site[1], front.get2DDataValue()};
            });
        }));
    }

    private static Site place(ServerLevel level, int[] plan) {
        int x = plan[0];
        int z = plan[1];
        Direction front = Direction.from2DDataValue(plan[2]);
        // 底座压在整块底座底下地表（水面、熔岩面也算）高度的中位数上，一棵树、一块石头不会把塔顶起来
        List<Integer> heights = new ArrayList<>();
        for (int dx = -PillarStructure.BASE; dx <= PillarStructure.BASE; dx++) {
            for (int dz = -PillarStructure.BASE; dz <= PillarStructure.BASE; dz++) {
                BlockPos column = new BlockPos(x + dx, 0, z + dz);
                level.getChunk(column.getX() >> 4, column.getZ() >> 4);
                heights.add(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()) - 1);
            }
        }
        heights.sort(null);
        int y = heights.get(heights.size() / 2);
        y = Math.max(level.getMinY() + 1, Math.min(y, level.getMaxY() - PillarStructure.HEIGHT - 3));
        BlockPos origin = new BlockPos(x, y, z);
        PillarStructure.build(level, origin, front);
        PillarSites.get(level.getServer()).put(key(level), new PillarSites.Placed(x, y, z, front.get2DDataValue()));
        return new Site(origin, front);
    }

    private static void tick(MinecraftServer server) {
        if (server.overworld().getGameTime() % CHECK_INTERVAL != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            approach(player, player.level());
        }
    }

    /** 走近还没放下的塔就放下；第一次走到塔前提示一下。 */
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
            if (dx * dx + dz * dz > PLACE_RANGE * PLACE_RANGE || !level.hasChunkAt(new BlockPos(x, 0, z))) {
                return;
            }
            site = Optional.of(place(level, plan.get()));
        }
        if (player.isSpectator() || player.distanceToSqr(Vec3.atCenterOf(site.get().origin())) > SEEN_RANGE * SEEN_RANGE) {
            return;
        }
        Optional<Planet> planet = Planets.planet(level);
        if (planet.isEmpty()) {
            return;
        }
        PoiProgress progress = player.getData(UniverseAttachments.POI_PROGRESS);
        if (progress.claim(Seeds.derive(planet.get().seed(), "pillar_seen"))) {
            player.sendOverlayMessage(Component.translatable("message.universe.pillar.found"));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 1.0F, 0.6F);
        }
    }

    // ---- 终端

    /** 第几个游戏日（按主世界的游戏时间，不受改时间影响）。 */
    static long day(ServerLevel level) {
        return level.getServer().overworld().getGameTime() / DAY_TICKS;
    }

    /**
     * 用终端关停这个维度的守卫机。一个游戏日一次（同一颗星球的塔共用）；被通缉时不响应。GameTest 也用。
     *
     * @param wanted 用终端的人正被通缉
     */
    public static Use use(ServerLevel level, boolean wanted) {
        if (wanted) {
            return Use.WANTED;
        }
        PillarSites sites = PillarSites.get(level.getServer());
        long today = day(level);
        if (sites.shutdownDay(key(level)) == today) {
            return Use.USED_TODAY;
        }
        sites.markShutdown(key(level), today);
        Sentinels.shutdown(level, SHUTDOWN_TICKS);
        return Use.SHUT_DOWN;
    }

    // ---- 守卫机型多功能工具

    /** 从终端取工具的结果。 */
    public enum ToolResult {
        /** 交出主脑，拿到了工具。 */
        GIVEN,
        /** 被通缉中，不响应。 */
        WANTED,
        /** 这座塔已经取过了。 */
        TAKEN,
        /** 身上没有步行机主脑。 */
        NO_BRAIN
    }

    /** 守卫机型工具的档次：守卫越警觉，给的越好。 */
    public static Grade toolGrade(SentinelLevel sentinels) {
        return switch (sentinels) {
            case NONE -> Grade.C;
            case LOW -> Grade.B;
            case NORMAL -> Grade.A;
            case AGGRESSIVE -> Grade.S;
        };
    }

    /** 取工具：查通缉、查这座塔取过没有、找一个主脑交掉，把工具放进背包。不发消息，GameTest 也用。 */
    public static ToolResult takeTool(ServerPlayer player, ServerLevel level, BlockPos terminal) {
        if (player.getData(UniverseAttachments.SENTINEL_ALERT).wanted().wanted()) {
            return ToolResult.WANTED;
        }
        long key = Seeds.derive(Pois.claimKey(level, terminal), "sentinel_multitool");
        PoiProgress progress = player.getData(UniverseAttachments.POI_PROGRESS);
        if (progress.claimed(key)) {
            return ToolResult.TAKEN;
        }
        ItemStack brain = brain(player);
        if (brain.isEmpty()) {
            return ToolResult.NO_BRAIN;
        }
        brain.shrink(1);
        progress.claim(key);
        ItemStack tool = MultitoolItem.create(MultitoolSpec.sentinel(toolGrade(Sentinels.level(level))));
        if (!player.getInventory().add(tool)) {
            player.drop(tool, false, Prediction.SERVER_ONLY);
        }
        return ToolResult.GIVEN;
    }

    /** 身上的一个步行机主脑：先看主手，再翻背包；没有是空的。 */
    private static ItemStack brain(ServerPlayer player) {
        if (player.getMainHandItem().is(UniverseItems.WALKER_BRAIN.get())) {
            return player.getMainHandItem();
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(UniverseItems.WALKER_BRAIN.get())) {
                return inventory.getItem(i);
            }
        }
        return ItemStack.EMPTY;
    }

    /** 玩家拿着主脑右键（或潜行右键）终端：取工具。 */
    public static void useTerminalForTool(ServerPlayer player, ServerLevel level, BlockPos pos) {
        ToolResult result = takeTool(player, level, pos);
        Vec3 center = Vec3.atCenterOf(pos);
        switch (result) {
            case WANTED -> {
                player.sendOverlayMessage(Component.translatable("message.universe.pillar.wanted"));
                level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.8F, 1.6F);
            }
            case TAKEN -> {
                player.sendOverlayMessage(Component.translatable("message.universe.pillar.tool_taken"));
                level.playSound(null, pos, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.8F, 1.2F);
            }
            case NO_BRAIN -> {
                player.sendOverlayMessage(Component.translatable("message.universe.pillar.tool_needs_brain"));
                level.playSound(null, pos, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.8F, 1.2F);
            }
            case GIVEN -> {
                player.sendOverlayMessage(Component.translatable("message.universe.pillar.tool", toolGrade(Sentinels.level(level)).letter()));
                level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0F, 0.6F);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 16, 0.3, 0.3, 0.3, 0.1);
            }
        }
    }

    /** 玩家右键终端。 */
    public static void useTerminal(ServerPlayer player, ServerLevel level, BlockPos pos) {
        boolean wanted = player.getData(UniverseAttachments.SENTINEL_ALERT).wanted().wanted();
        Use result = use(level, wanted);
        Vec3 center = Vec3.atCenterOf(pos);
        switch (result) {
            case WANTED -> {
                player.sendOverlayMessage(Component.translatable("message.universe.pillar.wanted"));
                level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.8F, 1.6F);
            }
            case USED_TODAY -> {
                long ticksLeft = DAY_TICKS - level.getServer().overworld().getGameTime() % DAY_TICKS;
                player.sendOverlayMessage(Component.translatable("message.universe.pillar.used", (ticksLeft + 1199) / 1200));
                level.playSound(null, pos, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.8F, 1.2F);
            }
            case SHUT_DOWN -> {
                player.sendOverlayMessage(Component.translatable("message.universe.pillar.shutdown", SHUTDOWN_MINUTES));
                level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.5F, 0.5F);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 24, 0.4, 0.4, 0.4, 0.2);
            }
        }
    }
}
