package io.github.sweetcornna.universe.deepspace;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.deepspace.ShipSides;
import io.github.sweetcornna.universe.core.ship.ShipShape;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.entity.ship.ShipStructure;
import io.github.sweetcornna.universe.registry.sound.DeepSpaceSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 出舱（ADR-0034 §四）：飞船在太空里（空间站外面）时，坐在船上的人能出去。船停在原地不漂，人在外面失重；
 * 对着自己的船右键回到座位上。主人不在船上时别人上不了这艘船，也开不走。
 */
public final class ShipEva {
    private ShipEva() {
    }

    /** 出舱：放到船边放得下人的地方。返回出没出去。 */
    public static boolean exit(ShipEntity ship, ServerPlayer player) {
        if (player.getVehicle() != ship) {
            return false;
        }
        if (!ship.inOpenSpace() || ship.isWarping()) {
            player.sendOverlayMessage(Component.translatable("message.universe.eva.unavailable").withStyle(ChatFormatting.GRAY));
            return false;
        }
        if (spot(ship, player) == null) {
            player.sendOverlayMessage(Component.translatable("message.universe.eva.blocked").withStyle(ChatFormatting.GOLD));
            return false;
        }
        // 下船的位置由 ShipEntity#getDismountLocationForPassenger 按同样的规则找
        player.stopRiding();
        player.setDeltaMovement(Vec3.ZERO);
        player.syncVelocity = true;
        player.resetFallDistance();
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), DeepSpaceSounds.HATCH.get(), SoundSource.PLAYERS, 0.9F, 1.0F);
        player.sendSystemMessage(Component.translatable("message.universe.eva.out").withStyle(ChatFormatting.AQUA));
        return true;
    }

    /** 回船：坐回座位上时放一声舱门。 */
    public static void boarded(ShipEntity ship, Player player) {
        if (ship.inOpenSpace()) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), DeepSpaceSounds.HATCH.get(), SoundSource.PLAYERS, 0.9F, 1.1F);
        }
    }

    /** 船边第一个放得下 {@code passenger} 的地方（脚底）；都放不下为空。 */
    public static @Nullable Vec3 spot(ShipEntity ship, Entity passenger) {
        ShipStructure structure = ship.structure();
        if (structure == null) {
            return null;
        }
        ShipShape shape = structure.shape();
        double[] center = {ship.getX(), ship.getY() + shape.height() / 2.0, ship.getZ()};
        for (double[] spot : ShipSides.exitSpots(center, ship.getYRot(), shape.radius(), shape.height())) {
            AABB box = passenger.getDimensions(Pose.STANDING).makeBoundingBox(spot[0], spot[1], spot[2]);
            if (ship.level().noCollision(passenger, box) && !ship.overlapsHull(box)) {
                return new Vec3(spot[0], spot[1], spot[2]);
            }
        }
        return null;
    }

    /** 主人出舱了（不在船上）：别人上不了这艘船。 */
    public static boolean mayBoard(ShipEntity ship, Entity passenger) {
        UUID owner = ship.owner();
        if (owner == null || !ship.inOpenSpace() || !(passenger instanceof Player player) || owner.equals(player.getUUID())) {
            return true;
        }
        return aboard(ship, owner);
    }

    /** 太空里不是主人的驾驶员：主人也在船上才开得动（主人出舱了，别人开不走）。 */
    public static boolean mayPilot(ShipEntity ship, Player pilot) {
        UUID owner = ship.owner();
        return owner == null || !ship.inOpenSpace() || owner.equals(pilot.getUUID()) || aboard(ship, owner);
    }

    private static boolean aboard(ShipEntity ship, UUID player) {
        for (Entity passenger : ship.getPassengers()) {
            if (passenger.getUUID().equals(player)) {
                return true;
            }
        }
        return false;
    }
}
