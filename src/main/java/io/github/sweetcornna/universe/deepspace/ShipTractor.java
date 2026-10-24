package io.github.sweetcornna.universe.deepspace;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import io.github.sweetcornna.universe.core.deepspace.Tractor;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.core.tech.Stat;
import io.github.sweetcornna.universe.core.tech.TechModule;
import io.github.sweetcornna.universe.entity.ship.ShipCargoContainer;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.entity.ship.ShipStructure;
import io.github.sweetcornna.universe.gravity.GravityCoils;
import io.github.sweetcornna.universe.registry.sound.DeepSpaceSounds;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 飞船的牵引光束（ADR-0034 §四）：装在驾驶台上的技术模块，开着时把范围里的掉落物和小块碎片吸过来，
 * 贴到船身就收进货舱（{@link ShipCargoContainer}）；货舱满了就不吸。被引力抓取器抓着的东西不抢，刚抛出去的货不吸回来。
 */
public final class ShipTractor {
    /** 抛出去的货多少刻里不吸回来。 */
    public static final int JETTISON_IGNORE = 200;
    /** 货舱满了的提示隔多久报一次（刻）。 */
    private static final int FULL_NOTICE = 100;
    /** 刚抛出去的掉落物 → 到哪一刻（游戏刻）之前不吸。 */
    private static final Map<ItemEntity, Long> IGNORED = new WeakHashMap<>();
    private static final Map<ShipEntity, Long> FULL_NOTICED = new WeakHashMap<>();
    /** 每艘船的光束这会儿拉着的东西。 */
    private static final Map<ShipEntity, Set<Entity>> TRACKED = new WeakHashMap<>();

    private ShipTractor() {
    }

    /** 船上装着牵引光束：范围（格）；没装是 0。 */
    public static double range(ShipEntity ship) {
        ShipStructure structure = ship.structure();
        if (structure == null) {
            return 0;
        }
        boolean installed = false;
        for (TechModule module : structure.tech().modules()) {
            if (module.type() == ModuleType.TRACTOR_BEAM) {
                installed = true;
                break;
            }
        }
        return installed ? Tractor.range(structure.tech().bonuses().get(Stat.TRACTOR_RANGE)) : 0;
    }

    /** 刚抛出去的东西：{@code until}（游戏刻）之前不吸回来。 */
    public static void ignore(ItemEntity item, long until) {
        IGNORED.put(item, until);
    }

    /** 驾驶员按了牵引光束键。 */
    public static void toggle(ShipEntity ship, Player pilot) {
        if (ship.tractorOn()) {
            ship.setTractor(false);
            ship.playSound(DeepSpaceSounds.TRACTOR_OFF.get(), 0.8F, 1.0F);
            pilot.sendOverlayMessage(Component.translatable("message.universe.tractor.off"));
            return;
        }
        double range = range(ship);
        if (range <= 0) {
            pilot.sendOverlayMessage(Component.translatable("message.universe.tractor.missing").withStyle(ChatFormatting.GRAY));
            return;
        }
        ship.setTractor(true);
        ship.playSound(DeepSpaceSounds.TRACTOR_ON.get(), 0.8F, 1.0F);
        pilot.sendOverlayMessage(Component.translatable("message.universe.tractor.on", Math.round(range)));
    }

    /** 每刻：开着就吸。每 {@link Tractor#SCAN_INTERVAL} 刻扫一遍范围里有什么，中间几刻接着拉扫到的那些。 */
    public static void tick(ShipEntity ship, ServerLevel level) {
        Set<Entity> tracked = TRACKED.get(ship);
        if (!ship.tractorOn()) {
            if (tracked != null) {
                letGo(level, tracked);
                TRACKED.remove(ship);
            }
            return;
        }
        ShipStructure structure = ship.structure();
        double range = range(ship);
        if (structure == null || range <= 0) {
            // 模块拆掉了
            ship.setTractor(false);
            return;
        }
        double hull = structure.shape().radius();
        Vec3 center = ship.position().add(0, structure.shape().height() / 2.0, 0);
        long now = level.getGameTime();
        if (tracked == null) {
            tracked = new HashSet<>();
            TRACKED.put(ship, tracked);
        }
        if (ship.tickCount % Tractor.SCAN_INTERVAL == 0) {
            Set<Entity> found = new HashSet<>(level.getEntities(ship, new AABB(center, center).inflate(range),
                    e -> e.isAlive() && pullable(e, now) && e.getBoundingBox().getCenter().distanceTo(center) <= range));
            Set<Entity> lost = new HashSet<>(tracked);
            lost.removeAll(found);
            letGo(level, lost);
            tracked.clear();
            tracked.addAll(found);
        }
        ShipCargoContainer cargo = null;
        boolean full = false;
        int stowed = 0;
        for (Iterator<Entity> it = tracked.iterator(); it.hasNext();) {
            Entity entity = it.next();
            if (!entity.isAlive() || entity.level() != level || !pullable(entity, now)) {
                it.remove();
                continue;
            }
            Vec3 at = entity.getBoundingBox().getCenter();
            Vec3 offset = center.subtract(at);
            double distance = offset.length();
            if (Tractor.caught(distance, hull)) {
                if (cargo == null) {
                    cargo = ShipCargoContainer.forShip(ship, level.registryAccess());
                }
                if (entity instanceof ItemEntity item) {
                    ItemStack stack = item.getItem().copy();
                    int moved = cargo.insert(stack);
                    stowed += moved;
                    if (stack.isEmpty()) {
                        item.discard();
                        it.remove();
                        continue;
                    }
                    if (moved > 0) {
                        item.setItem(stack);
                    }
                    full = true;
                } else if (entity instanceof DebrisChunk chunk) {
                    int moved = chunk.stow(level, ship, cargo);
                    if (moved >= 0) {
                        stowed += moved;
                        it.remove();
                        continue;
                    }
                    full = true;
                }
                // 装不下：停在船边，不再往里拉
                entity.setDeltaMovement(entity.getDeltaMovement().scale(0.5));
                entity.needsSync = true;
                continue;
            }
            if (full) {
                continue;
            }
            Vec3 v = entity.getDeltaMovement();
            double[] pulled = Tractor.pull(new double[] {offset.x, offset.y, offset.z}, new double[] {v.x, v.y, v.z}, hull);
            entity.setDeltaMovement(pulled[0], pulled[1], pulled[2]);
            entity.setNoGravity(true);
            entity.needsSync = true;
            if (entity instanceof ItemEntity item) {
                item.setPickUpDelay(10);
            }
            if ((ship.tickCount + entity.getId()) % 4 == 0) {
                level.sendParticles(new DustParticleOptions(0x7FE7FF, 0.6F), at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0);
            }
        }
        if (cargo != null) {
            cargo.setChanged();
        }
        if (stowed > 0) {
            level.playSound(null, center.x, center.y, center.z, DeepSpaceSounds.TRACTOR_STOW.get(), SoundSource.NEUTRAL, 0.7F,
                    0.9F + level.getRandom().nextFloat() * 0.2F);
        }
        if (full) {
            Long last = FULL_NOTICED.get(ship);
            Player pilot = ship.pilot();
            if (pilot != null && (last == null || now - last > FULL_NOTICE)) {
                FULL_NOTICED.put(ship, now);
                pilot.sendOverlayMessage(Component.translatable("message.universe.tractor.full").withStyle(ChatFormatting.GOLD));
            }
        }
    }

    /** 不拉了：星球上的东西重新往下掉（太空里本来就不掉）。 */
    private static void letGo(ServerLevel level, Set<Entity> entities) {
        boolean space = SpaceDimensions.isSpace(level.dimension());
        for (Entity entity : entities) {
            if (entity.isAlive() && !space && !GravityCoils.held(entity) && !(entity instanceof DebrisChunk)) {
                entity.setNoGravity(false);
            }
        }
    }

    /** 光束这会儿拉着的东西（测试用）。 */
    public static Set<Entity> tracked(ShipEntity ship) {
        Set<Entity> tracked = TRACKED.get(ship);
        return tracked == null ? Set.of() : Set.copyOf(tracked);
    }

    /** 吸不吸：掉落物（刚抛出去的不吸）、小块碎片；被引力抓取器抓着的不抢。 */
    private static boolean pullable(Entity entity, long now) {
        if (GravityCoils.held(entity)) {
            return false;
        }
        if (entity instanceof ItemEntity item) {
            Long until = IGNORED.get(item);
            return until == null || now >= until;
        }
        return entity instanceof DebrisChunk chunk && chunk.small();
    }

    /** 光束够得着的那类东西（客户端画光束用）：掉落物、小块碎片。 */
    public static boolean pullable(Entity entity) {
        return entity instanceof ItemEntity || entity instanceof DebrisChunk chunk && chunk.small();
    }
}
