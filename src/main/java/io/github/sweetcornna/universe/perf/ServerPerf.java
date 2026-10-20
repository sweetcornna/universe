package io.github.sweetcornna.universe.perf;

import java.util.IdentityHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.perf.PerfStats;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 服务端的分项计时（ADR-0019）。{@code /universe perf start} 打开以后，各子系统每刻的处理都记一笔；没打开时
 * {@link #begin()} 只做一次判断就返回。只记服务器线程上的，单人游戏里客户端那边的同名处理不算。
 *
 * <p>用法：{@code long start = ServerPerf.begin(); ...; ServerPerf.end("belts", start);}，不分配对象；
 * 每刻只调一次的地方也可以用 {@link #time(String, Runnable)}。
 */
public final class ServerPerf {
    private static volatile boolean enabled;
    private static @Nullable Thread serverThread;
    private static PerfStats stats = new PerfStats();
    private static long tickStart;
    private static final Map<Object, String> NAMES = new IdentityHashMap<>();
    /** 实体的 tick 可能嵌套（乘客），用一个小栈记开始时间。 */
    private static final long[] ENTITY_STARTS = new long[16];
    private static int entityDepth;

    private ServerPerf() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(EventPriority.HIGHEST, ServerTickEvent.Pre.class, event -> {
            if (enabled) {
                tickStart = System.nanoTime();
            }
        });
        gameBus.addListener(EventPriority.LOWEST, ServerTickEvent.Post.class, event -> {
            if (enabled && tickStart != 0) {
                stats.tick(System.nanoTime() - tickStart);
            }
        });
        gameBus.addListener(EventPriority.HIGHEST, EntityTickEvent.Pre.class, event -> {
            if (!enabled || Thread.currentThread() != serverThread) {
                return;
            }
            if (entityDepth < ENTITY_STARTS.length) {
                ENTITY_STARTS[entityDepth] = ours(event.getEntity()) ? System.nanoTime() : 0;
            }
            entityDepth++;
        });
        gameBus.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, event -> {
            if (!enabled || Thread.currentThread() != serverThread || entityDepth == 0) {
                return;
            }
            entityDepth--;
            if (entityDepth < ENTITY_STARTS.length && ENTITY_STARTS[entityDepth] != 0) {
                end(name(event.getEntity().getType()), ENTITY_STARTS[entityDepth]);
            }
        });
        gameBus.addListener(ServerStoppedEvent.class, event -> stop());
    }

    private static boolean ours(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace().equals(Universe.MOD_ID);
    }

    /** 开始计一段；没在记录或者不在服务器线程上时返回 0。 */
    public static long begin() {
        return enabled && Thread.currentThread() == serverThread ? System.nanoTime() : 0;
    }

    /** 记下从 {@link #begin()} 到现在的这一段。 */
    public static void end(String section, long start) {
        if (start != 0) {
            stats.add(section, System.nanoTime() - start);
        }
    }

    public static void time(String section, Runnable task) {
        long start = begin();
        task.run();
        end(section, start);
    }

    /** 方块实体的分项名，比如 block/assembler。 */
    public static String name(BlockEntityType<?> type) {
        return NAMES.computeIfAbsent(type, key -> "block/" + path(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type)));
    }

    private static String name(EntityType<?> type) {
        return NAMES.computeIfAbsent(type, key -> "entity/" + path(BuiltInRegistries.ENTITY_TYPE.getKey(type)));
    }

    private static String path(@Nullable Identifier id) {
        return id == null ? "?" : id.getPath();
    }

    static void start(MinecraftServer server) {
        stats = new PerfStats();
        tickStart = 0;
        entityDepth = 0;
        serverThread = server.getRunningThread();
        enabled = true;
    }

    static void stop() {
        enabled = false;
    }

    static boolean running() {
        return enabled;
    }

    static PerfStats stats() {
        return stats;
    }
}
