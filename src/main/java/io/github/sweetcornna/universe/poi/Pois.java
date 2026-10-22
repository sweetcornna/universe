package io.github.sweetcornna.universe.poi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.StarClass;
import io.github.sweetcornna.universe.core.poi.Poi;
import io.github.sweetcornna.universe.core.poi.PoiLayout;
import io.github.sweetcornna.universe.core.poi.PoiType;
import io.github.sweetcornna.universe.core.procgen.Seeds;
import io.github.sweetcornna.universe.core.tech.ModuleLoot;
import io.github.sweetcornna.universe.perf.ServerPerf;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 兴趣点在服务端的部分（ADR-0014）：每颗星球的分布、玩家走近时记下"去过"、到达航点、信号增强器定位。
 */
public final class Pois {
    /** 信号增强器的定位半径。 */
    public static final int LOCATE_RADIUS = 2000;
    /** 离兴趣点中心多近算去过（加上兴趣点的半径）。 */
    private static final int VISIT_MARGIN = 8;
    /** 星球种子 → 分布，几颗星球同时生成时各用各的。 */
    private static final Map<Long, PoiLayout> LAYOUTS = new LinkedHashMap<>(8, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, PoiLayout> eldest) {
            return this.size() > 8;
        }
    };

    private Pois() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(PlayerTickEvent.Post.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 7) {
                long start = ServerPerf.begin();
                tick(player);
                ServerPerf.end("player/poi", start);
            }
        });
    }

    /** 星球上兴趣点的分布；不能登陆的星球、母星没有。 */
    public static Optional<PoiLayout> layout(Planet planet) {
        if (!planet.landable()) {
            return Optional.empty();
        }
        synchronized (LAYOUTS) {
            return Optional.of(LAYOUTS.computeIfAbsent(planet.seed(), seed -> PoiLayout.of(planet)));
        }
    }

    public static Optional<PoiLayout> layout(ServerLevel level) {
        return Planets.planet(level).flatMap(Pois::layout);
    }

    /** 这颗星球（太空里是这个恒星系）的恒星的稀有程度，决定模块品级的概率（{@link ModuleLoot#rarity}）。 */
    public static int rarity(ServerLevel level) {
        OptionalInt system = Planets.planet(level).map(planet -> OptionalInt.of(planet.id().system()))
                .orElseGet(() -> SpaceDimensions.system(level.dimension()));
        if (system.isEmpty()) {
            return ModuleLoot.rarity(StarClass.G);
        }
        return Planets.galaxy(level.getServer()).system(system.getAsInt())
                .map(star -> ModuleLoot.rarity(star.star().starClass()))
                .orElse(ModuleLoot.rarity(StarClass.G));
    }

    /** 某个方块（终端、石碑）的领奖编号：维度和位置派生，每个方块一个。 */
    public static long claimKey(ServerLevel level, BlockPos pos) {
        return Seeds.derive(Seeds.derive(0x5EEDL, level.dimension().identifier().toString()), pos.asLong());
    }

    /** 兴趣点中心的地面高度：区块已经加载时取真实地面，否则用地形模型估计。 */
    public static int groundY(ServerLevel level, PoiLayout layout, Poi poi) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(poi.x() >> 4, poi.z() >> 4);
        if (chunk != null) {
            return chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, poi.x() & 15, poi.z() & 15);
        }
        return layout.estimatedY(poi) + 1;
    }

    private static void tick(ServerPlayer player) {
        ServerLevel level = player.level();
        Waypoint waypoint = player.getData(UniverseAttachments.WAYPOINT);
        if (waypoint.active() && waypoint.dimension() == level.dimension()
                && horizontalDistanceSq(player, waypoint.pos()) < Waypoint.ARRIVE * Waypoint.ARRIVE) {
            player.setData(UniverseAttachments.WAYPOINT, Waypoint.NONE);
            player.sendOverlayMessage(Component.translatable("message.universe.poi.arrived", waypoint.name()));
        }
        Optional<PoiLayout> layout = layout(level);
        if (layout.isEmpty() || player.isSpectator()) {
            return;
        }
        PoiProgress progress = player.getData(UniverseAttachments.POI_PROGRESS);
        for (Poi poi : layout.get().within(player.getX(), player.getZ(), 13 + VISIT_MARGIN)) {
            if (poi.distance(player.getX(), player.getZ()) <= poi.type().radius() + VISIT_MARGIN && progress.visit(poi.key())) {
                player.sendOverlayMessage(Component.translatable("message.universe.poi.found", name(poi.type())));
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                        SoundSource.PLAYERS, 1.0F, 0.8F);
            }
        }
    }

    private static double horizontalDistanceSq(ServerPlayer player, BlockPos pos) {
        double dx = pos.getX() + 0.5 - player.getX();
        double dz = pos.getZ() + 0.5 - player.getZ();
        return dx * dx + dz * dz;
    }

    public static Component name(PoiType type) {
        return Component.translatable("poi.universe." + type.serializedName());
    }

    /** 信号增强器能定位的种类（不包括信号增强器自己）。 */
    public static List<PoiType> locatable() {
        return List.of(PoiType.CRASHED_SHIP, PoiType.DROP_POD, PoiType.OUTPOST, PoiType.RUINS, PoiType.BURIED_TECH);
    }

    /**
     * 客户端在信号增强器界面里选了一种兴趣点：消耗一份导航数据，定位 2000 格内这个玩家没去过的最近一个，设成航点。
     * 找不到时不消耗。
     */
    public static void locate(ServerPlayer player, BlockPos booster, PoiType type) {
        ServerLevel level = player.level();
        if (!locatable().contains(type) || player.distanceToSqr(Vec3.atCenterOf(booster)) > 64
                || !level.getBlockState(booster).is(UniverseBlocks.SIGNAL_BOOSTER.get())) {
            return;
        }
        Optional<PoiLayout> layout = layout(level);
        if (layout.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.universe.poi.no_signal"));
            return;
        }
        Inventory inventory = player.getInventory();
        int slot = findNavigationData(inventory);
        if (slot < 0 && !player.isCreative()) {
            player.sendOverlayMessage(Component.translatable("message.universe.poi.need_navigation_data"));
            return;
        }
        PoiProgress progress = player.getData(UniverseAttachments.POI_PROGRESS);
        Optional<Poi> found = layout.get().nearest(booster.getX() + 0.5, booster.getZ() + 0.5, LOCATE_RADIUS,
                poi -> poi.type() == type && !progress.visited(poi.key()));
        if (found.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.universe.poi.none_found", name(type), LOCATE_RADIUS));
            level.playSound(null, booster, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.6F, 1.4F);
            return;
        }
        if (slot >= 0 && !player.isCreative()) {
            inventory.getItem(slot).shrink(1);
        }
        Poi poi = found.get();
        BlockPos target = new BlockPos(poi.x(), groundY(level, layout.get(), poi), poi.z());
        player.setData(UniverseAttachments.WAYPOINT, new Waypoint(level.dimension(), target, type.serializedName()));
        int distance = (int) Math.round(poi.distance(player.getX(), player.getZ()));
        player.sendSystemMessage(Component.translatable("message.universe.poi.located", name(type),
                Component.translatable("direction.universe." + direction(player, poi)), distance));
        level.playSound(null, booster, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.8F, 1.6F);
    }

    private static int findNavigationData(Inventory inventory) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(UniverseItems.NAVIGATION_DATA.get())) {
                return i;
            }
        }
        return -1;
    }

    /** 八个方位：东 0°，按逆时针（和开局坠毁点的提示一致）。 */
    static String direction(ServerPlayer player, Poi poi) {
        String[] directions = {"east", "southeast", "south", "southwest", "west", "northwest", "north", "northeast"};
        double dx = poi.x() + 0.5 - player.getX();
        double dz = poi.z() + 0.5 - player.getZ();
        return directions[Math.floorMod(Math.round((float) Math.toDegrees(Math.atan2(dz, dx)) / 45F), 8)];
    }
}
