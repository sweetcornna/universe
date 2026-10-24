package io.github.sweetcornna.universe.entity.ship;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.discovery.DiscoveryKey;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.StarClass;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.procgen.NameGenerator.Name;
import io.github.sweetcornna.universe.core.space.DriveTier;
import io.github.sweetcornna.universe.core.space.SpaceLayout;
import io.github.sweetcornna.universe.core.space.Warp;
import io.github.sweetcornna.universe.discovery.Discoveries;
import io.github.sweetcornna.universe.network.WarpPayload;
import io.github.sweetcornna.universe.network.WarpRequestPayload;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.registry.sound.ShipSounds;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 跃迁（ADR-0012）：驾驶员在星图上选好目标，服务端检查后开始充能；充能满了扣一个空间翘曲器，把飞船送进目标恒星系的太空。
 */
public final class ShipWarp {
    private ShipWarp() {
    }

    /** 星图上点了"跃迁"。 */
    public static void handleRequest(WarpRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ShipEntity ship = player.getVehicle() instanceof ShipEntity vehicle && vehicle.pilot() == player ? vehicle : null;
        Component problem = problem(player, ship, payload.system());
        if (problem != null) {
            player.sendOverlayMessage(problem);
            return;
        }
        ship.startWarp(payload.system());
        // 充能声 4.4 秒，3.45 秒处撕开空间，正好在充能满（SPOOL_TICKS）跳出去的那一下
        ship.level().playSound(null, ship.getX(), ship.getY(), ship.getZ(), ShipSounds.WARP_CHARGE.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
        sendToPassengers(ship, new WarpPayload(WarpPayload.Stage.SPOOL, Warp.SPOOL_TICKS, payload.system()));
    }

    /** 不能跃迁的原因；能跃迁时返回 null。 */
    static @Nullable Component problem(ServerPlayer player, @Nullable ShipEntity ship, int target) {
        if (ship == null || ship.structure() == null) {
            return Component.translatable("message.universe.warp.not_piloting");
        }
        OptionalInt here = SpaceDimensions.system(ship.level().dimension());
        if (here.isEmpty()) {
            return Component.translatable("message.universe.warp.not_in_space");
        }
        if (ship.isWarping()) {
            return Component.translatable("message.universe.warp.busy");
        }
        int drives = ship.structure().stats().warpDrives();
        if (drives == 0) {
            return Component.translatable("message.universe.warp.no_drive");
        }
        MinecraftServer server = player.level().getServer();
        Galaxy galaxy = Planets.galaxy(server);
        Optional<StarSystem> from = galaxy.system(here.getAsInt());
        Optional<StarSystem> to = galaxy.system(target);
        if (from.isEmpty() || to.isEmpty() || from.get().index() == to.get().index()) {
            return Component.translatable("message.universe.warp.bad_target");
        }
        double range = ship.structure().stats().warpRange();
        if (!Warp.inReach(from.get(), to.get(), range)) {
            return Component.translatable("message.universe.warp.out_of_range",
                    String.format(Locale.ROOT, "%.1f", from.get().distanceTo(to.get())), (int) range);
        }
        Component drive = missingDrive(ship.structure().stats().drive(), to.get());
        if (drive != null) {
            return drive;
        }
        List<SpaceLayout.Body> bodies = ShipTransit.layout(server, here.getAsInt());
        switch (Warp.check(bodies, ship.getX(), ship.getY(), ship.getZ())) {
            case NEAR_STAR -> {
                return Component.translatable("message.universe.warp.near_star");
            }
            case NEAR_BODY -> {
                SpaceLayout.Body body = Warp.blocker(bodies, ship.getX(), ship.getY(), ship.getZ()).orElseThrow();
                Name name = Discoveries.resolve(player.level().getServer(), DiscoveryKey.body(body.id()), body.planet().name());
                return Component.translatable("message.universe.warp.near_body", name.zh(), name.en());
            }
            case OK -> {
            }
        }
        if (!player.hasInfiniteMaterials() && count(player.getInventory()) == 0) {
            return Component.translatable("message.universe.warp.no_warper");
        }
        return null;
    }

    /** 驱动的档次不够去那颗恒星时说缺哪一档（ADR-0024）；够时返回 null。星图上也用这一句。 */
    public static @Nullable Component missingDrive(DriveTier drive, StarSystem target) {
        StarClass star = target.star().starClass();
        if (drive.reaches(star)) {
            return null;
        }
        return Component.translatable("message.universe.warp.needs_drive",
                Component.translatable("ship.universe.drive." + DriveTier.required(star).name().toLowerCase(Locale.ROOT)),
                star.nameZh(), star.nameEn());
    }

    /** 充能满了：再查一遍翘曲器，扣掉一个，登记传送。 */
    static void jump(ShipEntity ship, ServerLevel level, int target) {
        Player pilot = ship.pilot();
        OptionalInt here = SpaceDimensions.system(level.dimension());
        Galaxy galaxy = Planets.galaxy(level.getServer());
        Optional<StarSystem> from = here.isPresent() ? galaxy.system(here.getAsInt()) : Optional.empty();
        Optional<StarSystem> to = galaxy.system(target);
        if (pilot == null || from.isEmpty() || to.isEmpty()) {
            cancel(ship, Component.translatable("message.universe.warp.cancelled"));
            return;
        }
        if (!pilot.hasInfiniteMaterials() && !consume(pilot.getInventory())) {
            cancel(ship, Component.translatable("message.universe.warp.no_warper"));
            return;
        }
        double[] arrival = Warp.arrival(from.get(), to.get(), ShipTransit.layout(level.getServer(), target));
        warpAway(ship, level);
        ShipTransit.warp(ship, target, new Vec3(arrival[0], arrival[1], arrival[2]), (float) arrival[3]);
    }

    /** 船外的人听到船跃迁离开；船上的人在充能声的尾巴里已经听到了，不再放给他们。 */
    private static void warpAway(ShipEntity ship, ServerLevel level) {
        Holder<SoundEvent> sound = ShipSounds.WARP;
        double range = sound.value().getRange(1.0F);
        long seed = level.getRandom().nextLong();
        for (ServerPlayer player : level.players()) {
            if (player.getRootVehicle() != ship && player.distanceToSqr(ship) < range * range) {
                player.connection.send(new ClientboundSoundPacket(sound, SoundSource.NEUTRAL, ship.getX(), ship.getY(), ship.getZ(), 1.0F, 1.0F, seed));
            }
        }
    }

    /** 跃迁中断：告诉船上的人，画面恢复，掐掉充能声（不然还会撕开空间）。 */
    static void cancel(ShipEntity ship, Component reason) {
        for (Entity passenger : ship.getPassengers()) {
            if (passenger instanceof ServerPlayer player) {
                player.sendOverlayMessage(reason);
                player.connection.send(new ClientboundStopSoundPacket(ShipSounds.WARP_CHARGE.getId(), SoundSource.NEUTRAL));
            }
        }
        sendToPassengers(ship, new WarpPayload(WarpPayload.Stage.CANCEL, 0, -1));
    }

    /** 到了：放一声、发画面效果。 */
    static void arrived(ShipEntity ship, int system) {
        ship.level().playSound(null, ship.getX(), ship.getY(), ship.getZ(), ShipSounds.WARP_EXIT.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
        sendToPassengers(ship, new WarpPayload(WarpPayload.Stage.ARRIVE, Warp.ARRIVAL_TICKS, system));
        // 传送会保留乘客相对船头的朝向；出来时让大家都看向船头，一眼看到前面那颗行星
        for (Entity passenger : ship.getPassengers()) {
            if (passenger instanceof ServerPlayer player) {
                player.forceSetRotation(ship.getYRot(), false, 0, false);
            }
        }
    }

    static void sendToPassengers(ShipEntity ship, WarpPayload payload) {
        for (Entity passenger : ship.getPassengers()) {
            if (passenger instanceof ServerPlayer player) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    private static int count(Inventory inventory) {
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(UniverseItems.SPACE_WARPER.get())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean consume(Inventory inventory) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(UniverseItems.SPACE_WARPER.get())) {
                stack.shrink(1);
                inventory.setChanged();
                return true;
            }
        }
        return false;
    }
}
