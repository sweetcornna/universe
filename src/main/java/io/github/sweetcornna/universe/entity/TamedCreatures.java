package io.github.sweetcornna.universe.entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.perf.ServerPerf;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 驯服的异星动物跟着主人换世界（ADR-0017）：主人走传送门、传送器、命令换了世界，离开前 {@link #RADIUS} 格内跟着的
 * （没待命、没拴绳、没被别的东西驮着）一起过去，落在主人身边的地面上。去太空的不跟（太空里没有地面）；主人落在半空、
 * 下面 {@link #DROP} 格内都没有地面时也不跟，留在原地。
 *
 * <p>不管主人怎么换的世界都适用：每刻记下玩家所在的世界和位置，下一刻发现世界变了，就到旧世界的旧位置找。
 */
public final class TamedCreatures {
    private static final double RADIUS = 24;
    private static final int DROP = 48;
    private static final Map<UUID, Seen> LAST_SEEN = new HashMap<>();

    private record Seen(ResourceKey<Level> dimension, Vec3 position) {
    }

    private TamedCreatures() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(PlayerTickEvent.Post.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                long start = ServerPerf.begin();
                track(player);
                ServerPerf.end("player/tamed", start);
            }
        });
        gameBus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> LAST_SEEN.remove(event.getEntity().getUUID()));
    }

    private static void track(ServerPlayer player) {
        ResourceKey<Level> now = player.level().dimension();
        Seen last = LAST_SEEN.get(player.getUUID());
        if (last != null && last.dimension() != now && !player.isSpectator()) {
            bring(player, last);
        }
        if (last == null || last.dimension() != now || !last.position().equals(player.position())) {
            LAST_SEEN.put(player.getUUID(), new Seen(now, player.position()));
        }
    }

    private static void bring(ServerPlayer player, Seen from) {
        ServerLevel old = player.level().getServer().getLevel(from.dimension());
        if (old != null) {
            bring(player, followers(player, old, from.position()));
        }
    }

    /** 主人在这里的时候跟着他的宠物（没待命、没拴绳、没骑着别的东西也没被骑）。传送器在传送前先记下来。 */
    public static List<AlienCreature> followers(ServerPlayer player, ServerLevel level, Vec3 position) {
        return level.getEntitiesOfClass(AlienCreature.class, AABB.ofSize(position, RADIUS * 2, RADIUS * 2, RADIUS * 2),
                c -> c.isAlive() && c.isOwnedBy(player) && !c.isStaying() && !c.isLeashed() && !c.isPassenger() && !c.isVehicle());
    }

    /** 把这些宠物带到主人现在的身边，落在地面上；找不到地面或者主人在太空里就留在原地。 */
    public static void bring(ServerPlayer player, List<AlienCreature> pets) {
        ServerLevel to = player.level();
        if (SpaceDimensions.isSpace(to.dimension())) {
            return;
        }
        for (AlienCreature pet : pets) {
            if (!pet.isAlive()) {
                continue;
            }
            Vec3 pos = groundNear(to, pet, player.blockPosition());
            if (pos != null) {
                pet.teleport(new TeleportTransition(to, pos, Vec3.ZERO, pet.getYRot(), pet.getXRot(), TeleportTransition.DO_NOTHING));
            }
        }
    }

    /** 一个位置身边（先找一圈，最后才是脚下那一列）往下最近的、放得下这个实体的地面；都没有返回 null。 */
    public static @Nullable Vec3 groundNear(ServerLevel level, Entity pet, BlockPos centre) {
        for (int r = 1; r <= 3; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == r) {
                        Vec3 pos = groundBelow(level, pet, centre.offset(dx, 0, dz));
                        if (pos != null) {
                            return pos;
                        }
                    }
                }
            }
        }
        return groundBelow(level, pet, centre);
    }

    private static @Nullable Vec3 groundBelow(ServerLevel level, Entity pet, BlockPos top) {
        for (int dy = 0; dy <= DROP && top.getY() - dy > level.getMinY(); dy++) {
            BlockPos pos = top.below(dy);
            AABB box = pet.getDimensions(pet.getPose()).makeBoundingBox(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            if (!level.noCollision(pet, box)) {
                // 碰到实心的东西了，这一列的地面在它上面，前面没找到就算了
                return null;
            }
            if (!level.noCollision(pet, box.move(0, -0.5, 0))) {
                return level.containsAnyLiquid(box) ? null : new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            }
        }
        return null;
    }
}
