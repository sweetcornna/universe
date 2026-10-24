package io.github.sweetcornna.universe.client.gravity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.item.component.SwingAnimation;
import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.client.deepspace.Beams;
import io.github.sweetcornna.universe.client.dev.DevScript;
import io.github.sweetcornna.universe.core.gravity.GravityRules;
import io.github.sweetcornna.universe.gravity.GravityCoils;
import io.github.sweetcornna.universe.gravity.GravityPayloads;
import io.github.sweetcornna.universe.registry.sound.DeepSpaceSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * 引力抓取器的客户端部分（ADR-0034 §七）：抓着东西时从工具口到东西画一股扭动的紫色引力束、响着低沉的嗡声；
 * 抓着时按攻击键扔出去，潜行按攻击键往前推（服务端判定）。推的时候画一圈往前冲的冲击波。
 */
public final class GravityClient {
    private static final int BEAM_COLOR = 0xB58CFF;
    /** 谁（实体编号）抓着什么。 */
    private static final Map<Integer, Integer> BEAMS = new HashMap<>();
    private static boolean attackWasDown;
    private static @Nullable HoldSound holdSound;

    private GravityClient() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> {
            event.register(GravityPayloads.Beam.TYPE, (payload, context) -> context.enqueueWork(() -> {
                if (payload.target() < 0) {
                    BEAMS.remove(payload.holder());
                } else {
                    BEAMS.put(payload.holder(), payload.target());
                }
            }));
            event.register(GravityPayloads.Pushed.TYPE, (payload, context) -> context.enqueueWork(() -> pushed(payload.holder())));
        });
        gameBus.addListener(ClientTickEvent.Post.class, event -> tick());
        gameBus.addListener(InputEvent.InteractionKeyMappingTriggered.class, GravityClient::interceptAttack);
        gameBus.addListener(SubmitCustomGeometryEvent.class, GravityClient::submit);
        DevScript.EXTRA_VERBS.put("grab", args -> grab(Minecraft.getInstance()));
        DevScript.EXTRA_VERBS.put("drop", args -> Minecraft.getInstance().options.keyUse.setDown(false));
        DevScript.EXTRA_VERBS.put("throw", args -> ClientPacketDistributor.sendToServer(new GravityPayloads.Action(false)));
        // 不叫 push：开发脚本自己的 push 是按界面按钮，先匹配上，这个永远轮不到
        DevScript.EXTRA_VERBS.put("shove", args -> ClientPacketDistributor.sendToServer(new GravityPayloads.Action(true)));
    }

    /** 本地玩家抓着的东西；没抓为空。 */
    public static @Nullable Entity holding(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            return null;
        }
        Integer target = BEAMS.get(minecraft.player.getId());
        return target == null ? null : minecraft.level.getEntity(target);
    }

    // ---- 输入

    /** 没抓东西时潜行按攻击键：往前推一下，不挖方块。 */
    private static void interceptAttack(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (!event.isAttack() || player == null || !GravityCoils.active(player)) {
            return;
        }
        if (player.isShiftKeyDown()) {
            ClientPacketDistributor.sendToServer(new GravityPayloads.Action(true));
            event.setCanceled(true);
            event.setSwingHand(true);
        }
    }

    private static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            BEAMS.clear();
            attackWasDown = false;
            return;
        }
        BEAMS.entrySet().removeIf(entry -> minecraft.level.getEntity(entry.getKey()) == null || minecraft.level.getEntity(entry.getValue()) == null);
        // 按着右键时原版把攻击键吞了：自己看按下的那一下，抓着东西就扔出去
        boolean attackDown = minecraft.options.keyAttack.isDown();
        if (attackDown && !attackWasDown && player.isUsingItem() && GravityCoils.active(player) && holding(minecraft) != null) {
            ClientPacketDistributor.sendToServer(new GravityPayloads.Action(false));
            player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        }
        attackWasDown = attackDown;
        Entity held = holding(minecraft);
        if (held != null && (holdSound == null || holdSound.isStopped())) {
            holdSound = new HoldSound();
            minecraft.getSoundManager().play(holdSound);
        }
        // 抓着的东西身上飘一点紫光
        if (held != null && player.tickCount % 3 == 0) {
            Vec3 c = held.getBoundingBox().getCenter();
            minecraft.level.addParticle(new DustParticleOptions(BEAM_COLOR, 0.6F), c.x + (player.getRandom().nextDouble() - 0.5) * held.getBbWidth(),
                    c.y + (player.getRandom().nextDouble() - 0.5) * held.getBbHeight(), c.z + (player.getRandom().nextDouble() - 0.5) * held.getBbWidth(),
                    0, 0, 0);
        }
    }

    /** 开发脚本：转向最近一样抓得动的东西，按住右键。 */
    private static void grab(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        Entity best = null;
        double bestDistance = GravityRules.REACH;
        for (Entity entity : minecraft.level.getEntities(player, player.getBoundingBox().inflate(GravityRules.REACH), GravityClient::grabbable)) {
            double distance = entity.distanceTo(player);
            if (distance < bestDistance) {
                best = entity;
                bestDistance = distance;
            }
        }
        if (best != null) {
            Vec3 d = best.getBoundingBox().getCenter().subtract(player.getEyePosition());
            player.setYRot((float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F);
            player.setXRot((float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG));
        }
        minecraft.options.keyUse.setDown(true);
    }

    /** 客户端粗看一眼抓不抓得动（真正的判定在服务端）。 */
    private static boolean grabbable(Entity entity) {
        if (entity instanceof Player || !entity.isAlive()) {
            return false;
        }
        if (entity instanceof LivingEntity living) {
            return GravityRules.liftable(living.getBbWidth(), living.getBbHeight());
        }
        return entity instanceof net.minecraft.world.entity.item.ItemEntity
                || entity instanceof io.github.sweetcornna.universe.deepspace.DebrisChunk chunk && chunk.small();
    }

    // ---- 画

    /** 工具口在哪：视线往前、往右下偏一点（第一人称看着从手里出去）。 */
    private static Vec3 muzzle(Entity holder, float partial) {
        Vec3 eye = holder.getEyePosition(partial);
        Vec3 look = holder.getViewVector(partial);
        float yaw = Mth.lerp(partial, holder.yRotO, holder.getYRot()) * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        return eye.add(look.scale(0.7)).add(right.scale(0.32)).add(0, -0.28, 0);
    }

    private static void submit(SubmitCustomGeometryEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (BEAMS.isEmpty() || minecraft.level == null) {
            return;
        }
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double time = minecraft.level.getGameTime() + partial;
        List<Vec3[]> beams = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : BEAMS.entrySet()) {
            Entity holder = minecraft.level.getEntity(entry.getKey());
            Entity target = minecraft.level.getEntity(entry.getValue());
            if (holder == null || target == null) {
                continue;
            }
            Vec3 to = target.getPosition(partial).add(0, target.getBbHeight() / 2.0, 0);
            beams.add(new Vec3[] {muzzle(holder, partial).subtract(camera), to.subtract(camera)});
        }
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), Beams.RENDER_TYPE, (pose, buffer) -> {
            for (Vec3[] beam : beams) {
                Beams.wavy(pose, buffer, beam[0], beam[1], Vec3.ZERO, BEAM_COLOR, 0.16F, 0.12, 10, time * 1.6);
                Beams.wavy(pose, buffer, beam[0], beam[1], Vec3.ZERO, 0x7A4CFF, 0.09F, 0.2, 10, time * 2.3 + 17);
            }
        });
    }

    /** 推了一下：从工具口往前喷一圈紫色的冲击波。 */
    private static void pushed(int holderId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Entity holder = minecraft.level.getEntity(holderId);
        if (holder == null) {
            return;
        }
        Vec3 from = muzzle(holder, 1.0F);
        Vec3 look = holder.getLookAngle();
        RandomSource random = holder.getRandom();
        double spread = Math.tan(GravityRules.PUSH_HALF_ANGLE);
        for (int i = 0; i < 36; i++) {
            Vec3 jitter = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).scale(spread * 0.5);
            Vec3 dir = look.add(jitter.subtract(look.scale(jitter.dot(look)))).normalize();
            double speed = 0.5 + random.nextDouble() * 0.6;
            minecraft.level.addParticle(new DustParticleOptions(i % 3 == 0 ? 0xFFFFFF : BEAM_COLOR, 0.9F), from.x, from.y, from.z,
                    dir.x * speed, dir.y * speed, dir.z * speed);
        }
    }

    /** 抓着东西时的嗡声：跟着抓着的东西走，松开就停。 */
    private static final class HoldSound extends AbstractTickableSoundInstance {
        HoldSound() {
            super(DeepSpaceSounds.GRAVITY_HOLD.get(), SoundSource.PLAYERS, RandomSource.create());
            this.looping = true;
            this.delay = 0;
            this.volume = 0.05F;
            this.pitch = 1.0F;
            this.relative = false;
            this.place();
        }

        @Override
        public void tick() {
            Minecraft minecraft = Minecraft.getInstance();
            Entity held = holding(minecraft);
            if (held == null) {
                this.volume *= 0.6F;
                if (this.volume < 0.02F) {
                    this.stop();
                }
                return;
            }
            this.volume = Mth.lerp(0.25F, this.volume, 0.55F);
            double speed = held.getDeltaMovement().length();
            this.pitch = Mth.lerp(0.2F, this.pitch, (float) Mth.clamp(0.9 + speed * 0.6, 0.9, 1.4));
            this.place();
        }

        private void place() {
            Minecraft minecraft = Minecraft.getInstance();
            Entity held = holding(minecraft);
            Entity at = held != null ? held : minecraft.player;
            if (at != null) {
                this.x = at.getX();
                this.y = at.getY();
                this.z = at.getZ();
            }
        }
    }
}
