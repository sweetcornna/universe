package io.github.sweetcornna.universe.client.audio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

import io.github.sweetcornna.universe.core.audio.Soundscape;
import io.github.sweetcornna.universe.factory.MachineBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 机器的运转声（ADR-0029 §三）：每 10 刻看一遍周围 24 格内在运转的机器，按声音分组，每组放一条循环声。
 *
 * <p>哪种机器放哪条声音在 {@link MachineSoundTable} 里登记；分组和音量的算法在 {@link Soundscape}。
 * 循环声不按原版的距离衰减，音量和位置每刻往目标挪一点，开关时半秒淡入淡出。
 */
public final class MachineSoundscape {
    public static final double RADIUS = 24;
    public static final int MAX_VOICES = 8;
    private static final int SCAN_INTERVAL = 10;
    /** 每刻音量最多变这么多：0 到 1 要半秒。 */
    private static final float FADE_STEP = 0.1F;

    /**
     * 一种机器怎么响。
     *
     * @param loop     循环声
     * @param volume   这组的基础音量
     * @param pitch    音高（同一条声音、不同音高的算两组）
     * @param loudness 这台此刻有多响，0 是不响；默认看方块状态的"运转"
     */
    public record Binding(Holder<SoundEvent> loop, float volume, float pitch, ToDoubleFunction<BlockEntity> loudness) {
        String key() {
            return this.loop.getRegisteredName() + "@" + this.pitch;
        }
    }

    private static final Map<Block, Binding> BINDINGS = new HashMap<>();
    private static final Map<String, Voice> VOICES = new HashMap<>();
    private static boolean bound;
    private static int ticks;
    private static int lastSources;

    private MachineSoundscape() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ClientTickEvent.Post.class, event -> tick());
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> clear());
    }

    public static void bind(Block block, Holder<SoundEvent> loop, float volume, float pitch) {
        bind(block, loop, volume, pitch, MachineSoundscape::working);
    }

    public static void bind(Block block, Holder<SoundEvent> loop, float volume, float pitch, ToDoubleFunction<BlockEntity> loudness) {
        BINDINGS.put(block, new Binding(loop, volume, pitch, loudness));
    }

    /** 默认的响度：方块状态带"运转"而且为真时是 1。 */
    public static double working(BlockEntity blockEntity) {
        BlockState state = blockEntity.getBlockState();
        return state.hasProperty(MachineBlock.WORKING) && state.getValue(MachineBlock.WORKING) ? 1 : 0;
    }

    private static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            clear();
            return;
        }
        if (!bound) {
            // 方块和声音都注册完了才能登记
            MachineSoundTable.bindAll();
            bound = true;
        }
        if (++ticks % SCAN_INTERVAL != 0) {
            return;
        }
        Vec3 ear = minecraft.gameRenderer.mainCamera().position();
        List<Soundscape.Source> sources = new ArrayList<>();
        Map<String, Binding> groups = new HashMap<>();
        int r = (int) Math.ceil(RADIUS);
        int minX = Mth.floor(ear.x - r) >> 4;
        int maxX = Mth.floor(ear.x + r) >> 4;
        int minZ = Mth.floor(ear.z - r) >> 4;
        int maxZ = Mth.floor(ear.z + r) >> 4;
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    Binding binding = BINDINGS.get(blockEntity.getBlockState().getBlock());
                    if (binding == null) {
                        continue;
                    }
                    double loudness = binding.loudness().applyAsDouble(blockEntity);
                    if (loudness <= 0) {
                        continue;
                    }
                    BlockPos pos = blockEntity.getBlockPos();
                    String key = binding.key();
                    groups.putIfAbsent(key, binding);
                    sources.add(new Soundscape.Source(key, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, loudness));
                }
            }
        }
        lastSources = sources.size();
        Set<String> live = new HashSet<>();
        for (Soundscape.Voice mixed : Soundscape.mix(sources, ear.x, ear.y, ear.z, RADIUS, MAX_VOICES)) {
            Binding binding = groups.get(mixed.group());
            Voice voice = VOICES.get(mixed.group());
            if (!Loops.playing(voice)) {
                voice = new Voice(binding, mixed.x(), mixed.y(), mixed.z());
                VOICES.put(mixed.group(), voice);
                minecraft.getSoundManager().play(voice);
            }
            voice.aim(mixed.x(), mixed.y(), mixed.z(), (float) mixed.volume() * binding.volume());
            live.add(mixed.group());
        }
        for (Iterator<Map.Entry<String, Voice>> it = VOICES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Voice> entry = it.next();
            if (!Loops.playing(entry.getValue())) {
                it.remove();
            } else if (!live.contains(entry.getKey())) {
                entry.getValue().aim(entry.getValue().getX(), entry.getValue().getY(), entry.getValue().getZ(), 0);
            }
        }
    }

    private static void clear() {
        VOICES.values().forEach(Voice::end);
        VOICES.clear();
    }

    /** 开发脚本用：在响的组、音量和机器台数。 */
    public static String describe() {
        StringBuilder out = new StringBuilder("machine soundscape: " + lastSources + " machines");
        VOICES.forEach((key, voice) -> out.append(String.format(", %s %.2f at %.1f %.1f %.1f", key, voice.getVolume(),
                voice.getX(), voice.getY(), voice.getZ())));
        return out.toString();
    }

    /** 一组的循环声：位置和音量每刻往目标挪，音量落到 0 就停。 */
    private static final class Voice extends AbstractTickableSoundInstance {
        private double tx;
        private double ty;
        private double tz;
        private float target;

        Voice(Binding binding, double x, double y, double z) {
            super(binding.loop().value(), SoundSource.BLOCKS, RandomSource.create());
            this.looping = true;
            this.delay = 0;
            this.volume = 0.0F;
            this.pitch = binding.pitch();
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.x = this.tx = x;
            this.y = this.ty = y;
            this.z = this.tz = z;
        }

        void aim(double x, double y, double z, float volume) {
            this.tx = x;
            this.ty = y;
            this.tz = z;
            this.target = volume;
        }

        void end() {
            this.stop();
        }

        @Override
        public void tick() {
            this.x += (this.tx - this.x) * 0.2;
            this.y += (this.ty - this.y) * 0.2;
            this.z += (this.tz - this.z) * 0.2;
            this.volume += Mth.clamp(this.target - this.volume, -FADE_STEP, FADE_STEP);
            if (this.target <= 0 && this.volume <= 0.001F) {
                this.stop();
            }
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
