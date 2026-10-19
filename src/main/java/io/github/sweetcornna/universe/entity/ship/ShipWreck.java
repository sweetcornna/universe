package io.github.sweetcornna.universe.entity.ship;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import io.github.sweetcornna.universe.core.ship.ShipCombat;
import io.github.sweetcornna.universe.core.ship.ShipShape;
import io.github.sweetcornna.universe.core.space.StationLayout;
import io.github.sweetcornna.universe.core.space.StationLayout.Site;
import io.github.sweetcornna.universe.dyson.Dyson;
import io.github.sweetcornna.universe.pirate.Pirates;
import io.github.sweetcornna.universe.station.Stations;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 飞船被击毁（ADR-0017）：原地爆炸（不炸方块、不伤人），货舱里每一组东西丢三成（被海盗抢走），
 * 连人带船拖回本恒星系空间站的机库，船体剩两成五。船太大进不了机库时停在机库门口外面；没有空间站的恒星系就留在原地。
 */
public final class ShipWreck {
    private ShipWreck() {
    }

    static void destroy(ShipEntity ship, ServerLevel level) {
        ShipStructure structure = ship.structure();
        ShipShape shape = structure.shape();
        double r = shape.radius();
        double h = shape.height() / 2.0;
        Vec3 center = ship.position().add(0, h, 0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 10, r * 0.6, h, r * 0.6, 0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y, center.z, 30, r * 0.6, h, r * 0.6, 0.05);
        level.sendParticles(ParticleTypes.LAVA, center.x, center.y, center.z, 16, r * 0.5, h, r * 0.5, 0);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 4.0F, 0.7F);

        int lost = loseCargo(ship, level);
        Pirates.onShipWrecked(ship);
        ship.wrecked();
        Component message = Component.translatable("message.universe.ship.destroyed", lost).withStyle(ChatFormatting.RED);
        for (Entity passenger : ship.getPassengers()) {
            if (passenger instanceof Player player) {
                player.sendSystemMessage(message);
            }
        }
        towToStation(ship, level);
    }

    /** 货舱（船上的箱子、木桶……）里每一组东西丢三成，零头按概率。返回一共丢了几件。 */
    private static int loseCargo(ShipEntity ship, ServerLevel level) {
        ShipStructure structure = ship.structure();
        RegistryAccess registries = level.registryAccess();
        List<ShipStructure.ShipBlock> blocks = new ArrayList<>(structure.blocks());
        int lost = 0;
        boolean changed = false;
        for (int i = 0; i < blocks.size(); i++) {
            ShipStructure.ShipBlock block = blocks.get(i);
            if (block.blockEntity() == null) {
                continue;
            }
            BlockEntity entity = BlockEntity.loadStatic(new BlockPos(block.x(), block.y(), block.z()), block.state(), block.blockEntity(), registries);
            if (!(entity instanceof Container container) || container.isEmpty()) {
                continue;
            }
            int before = lost;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                double share = stack.getCount() * ShipCombat.CARGO_LOSS;
                int take = (int) share + (level.getRandom().nextDouble() < share - (int) share ? 1 : 0);
                if (take > 0) {
                    stack.shrink(take);
                    container.setItem(slot, stack);
                    lost += take;
                }
            }
            if (lost != before) {
                blocks.set(i, new ShipStructure.ShipBlock(block.x(), block.y(), block.z(), block.state(), entity.saveWithFullMetadata(registries)));
                changed = true;
            }
        }
        if (changed) {
            ship.replaceStructure(new ShipStructure(blocks));
        }
        return lost;
    }

    private static void towToStation(ShipEntity ship, ServerLevel level) {
        OptionalInt system = Dyson.systemOf(level.dimension());
        if (system.isEmpty()) {
            return;
        }
        Optional<Site> found = Stations.site(Planets.galaxy(level.getServer()), system.getAsInt());
        if (found.isEmpty()) {
            return;
        }
        Site site = found.get();
        ShipShape shape = ship.structure().shape();
        Vec3 pos;
        if (shape.radius() <= StationLayout.PAD_RADIUS + 0.5 && shape.height() <= StationLayout.CEILING - 2) {
            // 停机坪上方半格，站里有重力，自己落下去
            double[] pad = site.pad();
            pos = new Vec3(pad[0], pad[1] + 0.5, pad[2]);
        } else {
            int[] out = site.toWorld(StationLayout.RADIUS + 4 + (int) Math.ceil(shape.radius()), 2, 0);
            pos = new Vec3(out[0] + 0.5, out[1], out[2] + 0.5);
        }
        ShipTransit.tow(ship, SpaceDimensions.key(system.getAsInt()), pos, noseOut(site));
    }

    /** 船头朝着机库开口的偏航（偏航 0 朝南 +z、90 朝西）。 */
    private static float noseOut(Site site) {
        return switch (site.hangar()) {
            case SOUTH -> 0;
            case WEST -> 90;
            case NORTH -> 180;
            case EAST -> 270;
        };
    }
}
