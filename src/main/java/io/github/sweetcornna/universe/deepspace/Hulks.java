package io.github.sweetcornna.universe.deepspace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.deepspace.DebrisField;
import io.github.sweetcornna.universe.core.deepspace.DeepSpaceSites.Kind;
import io.github.sweetcornna.universe.core.deepspace.HulkLayout;
import io.github.sweetcornna.universe.core.deepspace.Integrity;
import io.github.sweetcornna.universe.core.deepspace.Integrity.Phase;
import io.github.sweetcornna.universe.core.deepspace.Layout;
import io.github.sweetcornna.universe.core.deepspace.Part;
import io.github.sweetcornna.universe.registry.UniverseSounds;
import io.github.sweetcornna.universe.registry.sound.DeepSpaceSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 拆残骸（ADR-0034 §四）：记着放下了的残骸在哪，残骸方块被拆掉时按部件扣结构完整度；低于 30 报警（灯变红、拉警报），
 * 降到 0 或者反应堆芯被拆下来，20 秒后熔毁：剩下的部分炸掉，24 格内的人受重伤。
 *
 * <p>完整度存在 {@link DeepSpaceData}（每个星系的残骸、开发命令放的残骸）；游戏内测试放的残骸只在内存里。
 */
public final class Hulks {
    /** 离残骸这么近的玩家收得到报警的消息（格）。 */
    static final double NOTICE_RANGE = 160;

    /** 一艘放下了的残骸。 */
    public static final class Wreck {
        final String key;
        final ResourceKey<Level> dimension;
        final Placement placement;
        final Layout layout;
        final BoundingBox box;
        final Integrity integrity;
        /** 存在 {@link DeepSpaceData} 里的（每个星系的、开发命令放的）；测试放的为 false。 */
        final boolean persistent;
        final Vec3 center;
        final Vec3 reactor;
        boolean alarmed;

        Wreck(String key, ResourceKey<Level> dimension, Placement placement, Layout layout, Integrity integrity, boolean persistent) {
            this.key = key;
            this.dimension = dimension;
            this.placement = placement;
            this.layout = layout;
            this.box = placement.box(layout);
            this.integrity = integrity;
            this.persistent = persistent;
            double[] c = layout.center();
            this.center = placement.world(c[0], c[1], c[2]);
            Layout.Cell core = layout.first(Part.REACTOR).orElse(null);
            this.reactor = core == null ? this.center : placement.world(core.x() + 0.5, core.y() + 0.5, core.z() + 0.5);
            this.alarmed = integrity.phase() == Phase.ALARM || integrity.phase() == Phase.MELTDOWN;
        }

        public String key() {
            return this.key;
        }

        public Integrity integrity() {
            return this.integrity;
        }

        public Vec3 center() {
            return this.center;
        }

        public BoundingBox box() {
            return this.box;
        }

        public Placement placement() {
            return this.placement;
        }

        public Layout layout() {
            return this.layout;
        }
    }

    private static final Map<String, Wreck> WRECKS = new LinkedHashMap<>();
    /** 已经把存档里的残骸登记过的维度。 */
    private static final java.util.Set<ResourceKey<Level>> LOADED = new java.util.HashSet<>();

    private Hulks() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ServerTickEvent.Post.class, event -> tick(event.getServer()));
        gameBus.addListener(ServerStoppedEvent.class, event -> {
            WRECKS.clear();
            LOADED.clear();
        });
    }

    // ---- 放和找

    /**
     * 放一艘残骸。
     *
     * @param length 长度；0 表示按种子定
     * @param state  存档里的那一项（每个星系的、开发命令放的）；为空时只在内存里（游戏内测试）
     */
    public static Wreck place(ServerLevel level, Placement placement, long seed, int length, DeepSpaceData.@Nullable State state) {
        return place(level, placement, layout(seed, length), seed, length, state);
    }

    /** 种子 {@code seed}、长度 {@code length}（0 表示按种子定）的残骸的样子。 */
    public static Layout layout(long seed, int length) {
        return length > 0 ? HulkLayout.generate(seed, length) : HulkLayout.generate(seed);
    }

    /** 同上，样子已经算好了（{@code layout} 必须就是 {@link #layout}{@code (seed, length)}）。 */
    public static Wreck place(ServerLevel level, Placement placement, Layout layout, long seed, int length, DeepSpaceData.@Nullable State state) {
        Structures.place(level, placement, layout, false);
        String key;
        Integrity integrity;
        if (state != null) {
            state.dimension = level.dimension().identifier().toString();
            state.origin = placement.origin().asLong();
            state.rotation = placement.rotation();
            state.seed = seed;
            state.length = length;
            state.built = true;
            DeepSpaceData.get(level.getServer()).changed();
            key = state.key;
            integrity = state.integrity;
        } else {
            key = "test/" + Long.toHexString(seed) + "@" + placement.origin().asLong();
            integrity = new Integrity();
        }
        Wreck wreck = new Wreck(key, level.dimension(), placement, layout, integrity, state != null);
        WRECKS.put(key, wreck);
        return wreck;
    }

    /** 这个维度里还没炸的残骸（第一次问的时候把存档里的登记进来）。 */
    public static List<Wreck> wrecks(ServerLevel level) {
        if (LOADED.add(level.dimension())) {
            String dimension = level.dimension().identifier().toString();
            for (DeepSpaceData.State state : DeepSpaceData.get(level.getServer()).all()) {
                if (state.kind == Kind.HULK && state.built && !state.integrity.gone() && state.dimension.equals(dimension)
                        && !WRECKS.containsKey(state.key)) {
                    Layout layout = layout(state.seed, state.length);
                    WRECKS.put(state.key, new Wreck(state.key, level.dimension(), new Placement(BlockPos.of(state.origin), state.rotation),
                            layout, state.integrity, true));
                }
            }
        }
        List<Wreck> list = new ArrayList<>();
        for (Wreck wreck : WRECKS.values()) {
            if (wreck.dimension == level.dimension() && !wreck.integrity.gone()) {
                list.add(wreck);
            }
        }
        return list;
    }

    public static @Nullable Wreck at(ServerLevel level, BlockPos pos) {
        for (Wreck wreck : wrecks(level)) {
            if (wreck.box.isInside(pos)) {
                return wreck;
            }
        }
        return null;
    }

    public static @Nullable Wreck find(String key) {
        return WRECKS.get(key);
    }

    /** 不再管这艘残骸（开发命令清掉了、游戏内测试做完了）。 */
    public static void forget(Wreck wreck) {
        WRECKS.remove(wreck.key);
    }

    // ---- 拆

    /** 残骸方块被拆掉了（挖的、炸的、烧的都算）：在哪艘残骸里，就按部件扣它的完整度。 */
    public static void removed(ServerLevel level, BlockPos pos, Part part) {
        Wreck wreck = at(level, pos);
        if (wreck == null || wreck.integrity.gone()) {
            return;
        }
        Phase before = wreck.integrity.phase();
        Phase after = wreck.integrity.remove(part);
        save(level, wreck);
        if (part.cost() >= 5 || level.getRandom().nextInt(5) == 0) {
            level.playSound(null, pos, DeepSpaceSounds.GROAN.get(), SoundSource.BLOCKS, part.cost() >= 5 ? 1.2F : 0.6F,
                    0.8F + level.getRandom().nextFloat() * 0.3F);
        }
        if (part == Part.CONDUIT) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 16, 0.4, 0.4, 0.4, 0.3);
        }
        if (part == Part.REACTOR) {
            notify(level, wreck, Component.translatable("message.universe.hulk.reactor_taken").withStyle(ChatFormatting.RED), true);
        } else if (after == Phase.MELTDOWN && before != Phase.MELTDOWN) {
            notify(level, wreck, Component.translatable("message.universe.hulk.meltdown").withStyle(ChatFormatting.RED), true);
        } else if (after == Phase.ALARM && before == Phase.STABLE) {
            notify(level, wreck, Component.translatable("message.universe.hulk.alarm").withStyle(ChatFormatting.GOLD), true);
        }
        if ((after == Phase.ALARM || after == Phase.MELTDOWN) && !wreck.alarmed) {
            alarmLights(level, wreck);
        }
    }

    private static void save(ServerLevel level, Wreck wreck) {
        if (wreck.persistent) {
            DeepSpaceData.get(level.getServer()).changed();
        }
    }

    /** 灯全变成闪红光的警报灯。 */
    private static void alarmLights(ServerLevel level, Wreck wreck) {
        wreck.alarmed = true;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Layout.Cell cell : wreck.layout.cells()) {
            if (cell.part() != Part.LIGHT) {
                continue;
            }
            cursor.set(wreck.placement.world(cell));
            BlockState state = level.getBlockState(cursor);
            if (state.getBlock() instanceof DerelictLightBlock) {
                level.setBlock(cursor, state.setValue(DerelictLightBlock.ALARM, true).setValue(DerelictLightBlock.LIT, true), Block.UPDATE_CLIENTS);
            }
        }
    }

    private static void notify(ServerLevel level, Wreck wreck, Component message, boolean chat) {
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceTo(wreck.center) < NOTICE_RANGE) {
                player.sendOverlayMessage(message);
                if (chat) {
                    player.sendSystemMessage(message);
                }
            }
        }
    }

    // ---- 每刻

    private static void tick(MinecraftServer server) {
        if (WRECKS.isEmpty()) {
            return;
        }
        for (Wreck wreck : List.copyOf(WRECKS.values())) {
            Phase phase = wreck.integrity.phase();
            if (phase == Phase.GONE) {
                WRECKS.remove(wreck.key);
                continue;
            }
            if (phase == Phase.STABLE) {
                continue;
            }
            ServerLevel level = server.getLevel(wreck.dimension);
            if (level == null) {
                continue;
            }
            long time = level.getGameTime();
            if (time % wreck.integrity.sirenInterval() == 0) {
                level.playSound(null, wreck.center.x, wreck.center.y, wreck.center.z, DeepSpaceSounds.SIREN.get(), SoundSource.BLOCKS, 1.6F,
                        phase == Phase.MELTDOWN ? 1.15F : 1.0F);
            }
            if (phase != Phase.MELTDOWN) {
                continue;
            }
            int left = wreck.integrity.countdown();
            if (left % 20 == 0 && left > 0) {
                notify(level, wreck, Component.translatable("message.universe.hulk.countdown", left / 20).withStyle(ChatFormatting.RED), false);
            }
            // 越到最后越不安生：堆芯冒烟，船身呻吟
            if (level.getRandom().nextInt(left < 100 ? 2 : 6) == 0) {
                Vec3 r = wreck.reactor;
                level.sendParticles(ParticleTypes.LARGE_SMOKE, r.x, r.y, r.z, 6, 1.2, 1.2, 1.2, 0.05);
                level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF9FE8FF), r.x, r.y, r.z, 1, 0, 0, 0, 0);
            }
            if (level.getRandom().nextInt(left < 100 ? 10 : 40) == 0) {
                level.playSound(null, wreck.center.x, wreck.center.y, wreck.center.z, DeepSpaceSounds.GROAN.get(), SoundSource.BLOCKS, 1.4F, 0.7F);
            }
            if (wreck.integrity.tick()) {
                explode(level, wreck);
                save(level, wreck);
            }
        }
    }

    /** 熔毁：残骸剩下的部分炸掉，24 格内的人受重伤，飞出一些碎片。 */
    static void explode(ServerLevel level, Wreck wreck) {
        Vec3 r = wreck.reactor;
        level.playSound(null, r.x, r.y, r.z, DeepSpaceSounds.MELTDOWN.get(), SoundSource.BLOCKS, 4.0F, 1.0F);
        level.playSound(null, r.x, r.y, r.z, UniverseSounds.EXPLOSION_LARGE.get(), SoundSource.BLOCKS, 3.0F, 0.7F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, r.x, r.y, r.z, 3, 2, 2, 2, 0);
        level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFBFF4FF), r.x, r.y, r.z, 4, 1, 1, 1, 0);
        // 沿着船身再炸几处
        List<Layout.Cell> cells = wreck.layout.cells();
        for (int i = 0; i < 8 && !cells.isEmpty(); i++) {
            Layout.Cell cell = cells.get(level.getRandom().nextInt(cells.size()));
            Vec3 at = wreck.placement.world(cell.x() + 0.5, cell.y() + 0.5, cell.z() + 0.5);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 30, 2, 2, 2, 0.1);
        }
        // 24 格内的人受重伤，被冲开
        AABB area = new AABB(r, r).inflate(Integrity.BLAST_RADIUS);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAlive)) {
            double distance = entity.position().distanceTo(r);
            float damage = (float) Integrity.blastDamage(distance);
            if (damage <= 0) {
                continue;
            }
            entity.hurtServer(level, level.damageSources().explosion(null, null), damage);
            Vec3 push = entity.position().subtract(r);
            if (push.lengthSqr() > 1e-4) {
                entity.push(push.normalize().scale(1.6 * (1 - distance / Integrity.BLAST_RADIUS)));
                entity.syncVelocity = true;
            }
        }
        Structures.clear(level, wreck.placement, wreck.layout);
        // 几块碎片飞出去（不属于碎片带，飘远了就没了）
        for (int i = 0; i < 10; i++) {
            DebrisField.Size size = i < 6 ? DebrisField.Size.SMALL : DebrisField.Size.MEDIUM;
            DebrisChunk chunk = DebrisChunk.create(level, level.getRandom().nextLong(), size, r, null, -1);
            Vec3 dir = new Vec3(level.getRandom().nextGaussian(), level.getRandom().nextGaussian() * 0.5, level.getRandom().nextGaussian());
            chunk.setDeltaMovement(dir.normalize().scale(0.4 + level.getRandom().nextDouble() * 0.6));
            level.addFreshEntity(chunk);
        }
        notify(level, wreck, Component.translatable("message.universe.hulk.exploded").withStyle(ChatFormatting.DARK_RED), true);
        WRECKS.remove(wreck.key);
    }

    /** 开发命令：把残骸当场炸掉（不等倒计时）。 */
    static void detonate(ServerLevel level, Wreck wreck) {
        wreck.integrity.remove(Part.REACTOR);
        while (!wreck.integrity.tick()) {
            // 倒计时走完
        }
        explode(level, wreck);
        save(level, wreck);
    }

    static ResourceKey<Level> dimension(String id) {
        return ResourceKey.create(Registries.DIMENSION, Identifier.parse(id));
    }
}
