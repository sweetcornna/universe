package io.github.sweetcornna.universe.client;

import java.util.HashMap;
import java.util.Map;

import io.github.sweetcornna.universe.core.galaxy.Hazard;
import io.github.sweetcornna.universe.core.mecha.MechaEnergy;
import io.github.sweetcornna.universe.core.survival.LifeSupport;
import io.github.sweetcornna.universe.core.survival.ToolMode;
import io.github.sweetcornna.universe.core.survival.ToolWeapon;
import io.github.sweetcornna.universe.core.tech.Stat;
import io.github.sweetcornna.universe.client.audio.Loops;
import io.github.sweetcornna.universe.client.ship.ShipCargoClient;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.registry.sound.GearSounds;
import io.github.sweetcornna.universe.survival.Multitool;
import io.github.sweetcornna.universe.survival.MultitoolItem;
import io.github.sweetcornna.universe.survival.SuitHud;
import io.github.sweetcornna.universe.tech.SuitTech;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * 玩家装备的持续声音（ADR-0029）：采矿激光和烈焰标枪的蓄力（自己的和附近别人的，按"正在用、什么模式"起停），
 * 喷气背包（自己的在背后，附近别人的跟着他，按服务端转发的喷火状态），生命维持和告警（只放给自己，在头盔里）。
 */
public final class GearAudio {
    /** 别人的工具多远以内听得见。 */
    private static final double RANGE = 32;
    /** 告警多久响一次（刻）；氧气见底时快一倍。 */
    private static final int ALARM_PERIOD = 40;

    private enum Tool {
        MINING, JAVELIN
    }

    private static final Map<Integer, ToolLoop> TOOLS = new HashMap<>();
    /** 附近别人的喷气背包（实体 id）。 */
    private static final Map<Integer, JetLoop> JETS = new HashMap<>();
    private static @Nullable SuitLoop lifeSupport;
    private static @Nullable SuitLoop jetpack;
    private static int alarmCooldown;

    private GearAudio() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ClientTickEvent.Post.class, event -> tick());
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> clear());
    }

    private static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer self = minecraft.player;
        if (self == null || minecraft.level == null) {
            clear();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        for (AbstractClientPlayer player : minecraft.level.players()) {
            if (player.isSpectator() || player.distanceToSqr(self) > RANGE * RANGE) {
                continue;
            }
            Tool tool = tool(player);
            ToolLoop loop = TOOLS.get(player.getId());
            if (tool != null && (!Loops.playing(loop) || loop.tool != tool)) {
                if (loop != null) {
                    loop.end();
                }
                loop = new ToolLoop(player, tool, player == self);
                TOOLS.put(player.getId(), loop);
                minecraft.getSoundManager().play(loop);
            }
            if (player != self && ShipCargoClient.flaming(player.getId()) && !Loops.playing(JETS.get(player.getId()))) {
                JetLoop jet = new JetLoop(player);
                JETS.put(player.getId(), jet);
                minecraft.getSoundManager().play(jet);
            }
        }
        TOOLS.values().removeIf(loop -> !Loops.playing(loop));
        JETS.values().removeIf(loop -> !Loops.playing(loop));
        suit(minecraft, self);
    }

    /** 这个玩家手上的工具在出什么声音：按着使用键、采矿模式（自己过热时不算）或者在给标枪蓄力。 */
    private static @Nullable Tool tool(Player player) {
        if (!player.isUsingItem() || !(player.getUseItem().getItem() instanceof MultitoolItem)) {
            return null;
        }
        ToolMode mode = Multitool.activeMode(player.getUseItem());
        if (mode == ToolMode.MINING) {
            boolean overheated = player instanceof LocalPlayer local && local.getData(UniverseAttachments.SUIT_HUD).has(SuitHud.OVERHEATED);
            return overheated ? null : Tool.MINING;
        }
        return mode == ToolMode.BLAZE_JAVELIN ? Tool.JAVELIN : null;
    }

    // ---- 宇航服

    private static void suit(Minecraft minecraft, LocalPlayer self) {
        boolean alive = self.isAlive() && !self.isSpectator() && !self.isCreative();
        SuitHud hud = self.getData(UniverseAttachments.SUIT_HUD);
        boolean exposed = false;
        for (Hazard hazard : Hazard.values()) {
            exposed |= hud.exposed(hazard);
        }
        // 真空、有毒的大气里宇航服在耗生命维持；飞船里有供给时不算
        boolean breathing = alive && (hud.has(SuitHud.NEEDS_OXYGEN) && !hud.has(SuitHud.SUPPLIED) || exposed);
        lifeSupport = keep(minecraft, lifeSupport, breathing, GearSounds.LIFE_SUPPORT.get(), 0.35F, 0.05F, 0.05F, 1.0F);
        float rise = (float) Mth.clamp(self.getDeltaMovement().y / 0.5, 0, 1);
        jetpack = keep(minecraft, jetpack, !self.isSpectator() && SuitClient.thrusting(), GearSounds.JETPACK.get(), 0.7F, 0.35F, 0.2F,
                0.95F + 0.1F * rise);
        alarm(minecraft, hud, alive);
    }

    private static @Nullable SuitLoop keep(Minecraft minecraft, @Nullable SuitLoop loop, boolean on, SoundEvent event, float volume,
            float fadeIn, float fadeOut, float pitch) {
        if (loop != null && !Loops.playing(loop)) {
            loop = null;
        }
        if (loop == null && on) {
            loop = new SuitLoop(event, volume, fadeIn, fadeOut);
            minecraft.getSoundManager().play(loop);
        }
        if (loop != null) {
            loop.on = on;
            loop.targetPitch = pitch;
        }
        return loop;
    }

    /** 和读数面板的警告同一套条件：氧气不到四分之一、飞行时能量见底、暴露在危害里而防护不到两成。 */
    private static void alarm(Minecraft minecraft, SuitHud hud, boolean alive) {
        SoundEvent beep = null;
        int period = ALARM_PERIOD;
        if (alive) {
            if (hud.has(SuitHud.NEEDS_OXYGEN) && hud.oxygen() < LifeSupport.OXYGEN_MAX * 0.25F) {
                beep = GearSounds.WARNING_OXYGEN.get();
                period = hud.oxygen() <= 0 ? ALARM_PERIOD / 2 : ALARM_PERIOD;
            } else if (hud.has(SuitHud.FLYING) && hud.energy() < hud.energyMax() * MechaEnergy.LOW) {
                beep = GearSounds.WARNING_HAZARD.get();
                period = ALARM_PERIOD * 3 / 2;
            } else {
                for (Hazard hazard : Hazard.values()) {
                    if (hud.exposed(hazard) && hud.protection(hazard) < LifeSupport.HAZARD_MAX * 0.2F) {
                        beep = GearSounds.WARNING_HAZARD.get();
                        break;
                    }
                }
            }
        }
        if (beep == null) {
            alarmCooldown = 0;
            return;
        }
        if (alarmCooldown > 0) {
            alarmCooldown--;
            return;
        }
        alarmCooldown = period;
        minecraft.getSoundManager().play(new SimpleSoundInstance(beep.location(), SoundSource.PLAYERS, 0.6F, 1.0F, RandomSource.create(),
                false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
    }

    private static void clear() {
        TOOLS.values().forEach(ToolLoop::end);
        TOOLS.clear();
        JETS.values().forEach(JetLoop::end);
        JETS.clear();
        if (lifeSupport != null) {
            lifeSupport.on = false;
            lifeSupport = null;
        }
        if (jetpack != null) {
            jetpack.on = false;
            jetpack = null;
        }
        alarmCooldown = 0;
    }

    /** 工具的循环：自己的贴在耳边，别人的跟着他；不用了就很快淡出。标枪的音高随蓄力从 0.7 升到 1.4。 */
    private static final class ToolLoop extends AbstractTickableSoundInstance {
        private final Player player;
        private final Tool tool;
        private final boolean own;
        private boolean ending;

        ToolLoop(Player player, Tool tool, boolean own) {
            super(tool == Tool.MINING ? GearSounds.MINING_BEAM.get() : GearSounds.JAVELIN_CHARGE.get(), SoundSource.PLAYERS,
                    RandomSource.create());
            this.player = player;
            this.tool = tool;
            this.own = own;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
            this.pitch = this.chargePitch();
            if (own) {
                this.relative = true;
                this.attenuation = SoundInstance.Attenuation.NONE;
            } else {
                this.place();
            }
        }

        void end() {
            this.ending = true;
        }

        @Override
        public void tick() {
            if (this.player.isRemoved() || tool(this.player) != this.tool) {
                this.ending = true;
            }
            float target = this.ending ? 0 : this.tool == Tool.MINING ? 0.55F : 0.5F;
            this.volume += Mth.clamp(target - this.volume, -0.25F, 0.15F);
            if (this.ending) {
                if (this.volume <= 0.01F) {
                    this.stop();
                }
                return;
            }
            this.pitch = this.chargePitch();
            if (!this.own) {
                this.place();
            }
        }

        private float chargePitch() {
            if (this.tool != Tool.JAVELIN) {
                return 1.0F;
            }
            int full = ToolWeapon.chargeTicks(SuitTech.tool(this.player.getUseItem()).boost(Stat.FIRE_RATE));
            float share = Mth.clamp(this.player.getTicksUsingItem() / (float) full, 0, 1);
            return 0.7F + 0.7F * share;
        }

        private void place() {
            this.x = this.player.getX();
            this.y = this.player.getEyeY() - 0.3;
            this.z = this.player.getZ();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }

    /** 别人的喷气背包：跟着他，服务端说熄火了就淡出；走远了、看不见了也停。 */
    private static final class JetLoop extends AbstractTickableSoundInstance {
        private final Player player;
        private boolean ending;

        JetLoop(Player player) {
            super(GearSounds.JETPACK.get(), SoundSource.PLAYERS, RandomSource.create());
            this.player = player;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
            this.place();
        }

        void end() {
            this.ending = true;
        }

        @Override
        public void tick() {
            if (this.player.isRemoved() || !ShipCargoClient.flaming(this.player.getId())) {
                this.ending = true;
            }
            this.volume += Mth.clamp((this.ending ? 0 : 0.7F) - this.volume, -0.2F, 0.35F);
            float rise = (float) Mth.clamp(this.player.getDeltaMovement().y / 0.5, 0, 1);
            this.pitch = Mth.lerp(0.2F, this.pitch, 0.95F + 0.1F * rise);
            this.place();
            if (this.ending && this.volume <= 0.01F) {
                this.stop();
            }
        }

        private void place() {
            this.x = this.player.getX();
            this.y = this.player.getY() + 0.9;
            this.z = this.player.getZ();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }

    /** 宇航服的循环：在头盔里（跟着自己），开着往 volume 淡入，关了淡出再停。 */
    private static final class SuitLoop extends AbstractTickableSoundInstance {
        private final float max;
        private final float fadeIn;
        private final float fadeOut;
        boolean on = true;
        float targetPitch = 1.0F;

        SuitLoop(SoundEvent event, float max, float fadeIn, float fadeOut) {
            super(event, SoundSource.PLAYERS, RandomSource.create());
            this.max = max;
            this.fadeIn = fadeIn;
            this.fadeOut = fadeOut;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override
        public void tick() {
            float target = this.on ? this.max : 0;
            this.volume += Mth.clamp(target - this.volume, -this.fadeOut, this.fadeIn);
            this.pitch = Mth.lerp(0.2F, this.pitch, this.targetPitch);
            if (!this.on && this.volume <= 0.01F) {
                this.stop();
            }
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
