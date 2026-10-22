package io.github.sweetcornna.universe.client.creature;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.sweetcornna.universe.core.ecology.BodyPlan;
import io.github.sweetcornna.universe.core.ecology.CreatureSpecies;
import io.github.sweetcornna.universe.core.ecology.Fauna;
import io.github.sweetcornna.universe.entity.AlienCreature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * 巨型生物的脚步（ADR-0026 §九）：每落一脚咚一声，24 格内站在地上的人画面跟着抖一下，越近抖得越厉害。
 * 全在客户端算，按各自看到的步子来，不发包。抖动的幅度乘上原版的"屏幕效果强度"选项。
 */
public final class GiantFootsteps {
    /** 多远以内能感到地面震动。 */
    public static final double RANGE = 24;
    /** 最大的抖动（度）。 */
    private static final float MAX_PITCH = 1.4F;
    private static final float MAX_ROLL = 0.9F;
    private static final float MAX_YAW = 0.5F;

    /** 每只巨型生物上次走到第几个半步（实体 ID → 计数）。 */
    private static Map<Integer, Integer> steps = new HashMap<>();
    /** 震动的强度 0–1，每一脚往上加，每刻衰减。 */
    private static float trauma;
    private static float traumaOld;

    private GiantFootsteps() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ClientTickEvent.Post.class, event -> tick());
        gameBus.addListener(ViewportEvent.ComputeCameraAngles.class, GiantFootsteps::shake);
    }

    private static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Entity camera = minecraft.getCameraEntity();
        if (minecraft.isPaused()) {
            return;
        }
        traumaOld = trauma;
        trauma = Math.max(0, trauma * 0.8F - 0.01F);
        if (level == null || camera == null) {
            steps.clear();
            return;
        }
        Map<Integer, Integer> seen = new HashMap<>();
        List<AlienCreature> near = level.getEntitiesOfClass(AlienCreature.class, new AABB(camera.blockPosition()).inflate(RANGE + 8),
                c -> Fauna.giant(c.species()));
        for (AlienCreature giant : near) {
            CreatureSpecies species = giant.species();
            int step = Mth.floor(giant.walkAnimation.position() * stride(species) / Mth.PI);
            Integer last = steps.get(giant.getId());
            seen.put(giant.getId(), step);
            if (last == null || step == last || !giant.onGround() || giant.isInWater()) {
                continue;
            }
            stomp(level, camera, giant, species);
        }
        steps = seen;
    }

    /** 落下一脚：脚下咚一声，离得够近、人站在地上（或者就骑在它背上）就抖一下。 */
    private static void stomp(ClientLevel level, Entity camera, AlienCreature giant, CreatureSpecies species) {
        float height = giant.getBbHeight();
        float pace = Math.min(1, giant.walkAnimation.speed() * 2);
        float pitch = Mth.clamp(1.1F - height * 0.08F, 0.55F, 0.8F) + (level.getRandom().nextFloat() - 0.5F) * 0.1F;
        level.playLocalSound(giant.getX(), giant.getY(), giant.getZ(), SoundEvents.RAVAGER_STEP, SoundSource.NEUTRAL,
                1.2F + 0.4F * pace, pitch, false);
        double distance = camera.distanceTo(giant);
        boolean riding = camera.getVehicle() == giant;
        if (distance > RANGE || !riding && !camera.onGround()) {
            return;
        }
        float near = (float) (1 - distance / RANGE);
        float weight = Mth.clamp((height - 3) / 3, 0.5F, 1);
        float kick = near * near * weight * (0.35F + 0.65F * pace) * (riding ? 0.4F : 0.9F);
        trauma = Math.min(1, trauma + kick);
    }

    /**
     * 迈一步对应走路动画走了多少（弧度/动画位置），和 {@code AlienCreatureModel} 里腿摆动的频率一样：
     * 腿越长、个子越大摆得越慢，每半个周期落一次脚（四足的对角一对、六足的三条一起落）。
     */
    static float stride(CreatureSpecies species) {
        int legLength = Math.max(1, species.body().legLength());
        float stride = (float) (0.6662 * Mth.clamp(Math.sqrt(9.0 / legLength), 0.75, 1.9) / Math.max(0.5, species.size()));
        return species.plan() == BodyPlan.HEXAPOD ? stride * 1.4F : stride;
    }

    private static void shake(ViewportEvent.ComputeCameraAngles event) {
        float t = Mth.lerp(event.getPartialTick(), traumaOld, trauma);
        if (t <= 0.001F) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        float scale = minecraft.options.screenEffectScale().get().floatValue();
        if (scale <= 0 || minecraft.level == null) {
            return;
        }
        float amount = t * t * scale;
        // 游戏刻取个余数，免得 float 精度不够、抖动一顿一顿的
        float time = minecraft.level.getGameTime() % 10000 + event.getPartialTick();
        event.setPitch(event.getPitch() + amount * MAX_PITCH * (Mth.sin(time * 2.7F) + 0.5F * Mth.sin(time * 4.3F + 1.1F)) / 1.5F);
        event.setRoll(event.getRoll() + amount * MAX_ROLL * (Mth.sin(time * 3.1F + 2.3F) + 0.5F * Mth.sin(time * 5.2F)) / 1.5F);
        event.setYaw(event.getYaw() + amount * MAX_YAW * Mth.sin(time * 2.2F + 0.7F));
    }
}
