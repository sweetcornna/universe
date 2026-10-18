package io.github.sweetcornna.universe.entity.ship;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import io.github.sweetcornna.universe.block.ship.FuelTankBlockEntity;
import io.github.sweetcornna.universe.block.ship.ShipHelmBlock;
import io.github.sweetcornna.universe.block.ship.ShipSeatBlock;
import io.github.sweetcornna.universe.core.ship.ShipAssembler;
import io.github.sweetcornna.universe.core.ship.ShipPart;
import io.github.sweetcornna.universe.core.ship.ShipShape;
import io.github.sweetcornna.universe.core.ship.ShipStats;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.neoforge.common.util.BlockRelocability;

/**
 * 组装和停船：把方块从世界搬到飞船实体上，或者反过来。
 *
 * <p>搬动时不触发掉落、不倒出箱子里的东西、不更新邻居形状（{@link Block#UPDATE_SKIP_ALL_SIDEEFFECTS}），
 * 门和床这类多格方块不会只剩一半。方块实体的数据随船走，停船时原样放回。
 */
public final class ShipAssembly {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MOVE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private ShipAssembly() {
    }

    /** 右键驾驶台：组装飞船，玩家坐上驾驶位。失败时在动作栏说明原因。 */
    public static void assembleAndBoard(ServerLevel level, BlockPos helmPos, ServerPlayer player) {
        if (player.isPassenger()) {
            return;
        }
        ShipAssembler.Result result = ShipAssembler.assemble(
                (x, y, z) -> ShipParts.canAssemble(level, new BlockPos(x, y, z)),
                helmPos.getX(), helmPos.getY(), helmPos.getZ(), ShipAssembler.MAX_BLOCKS);
        if (result.tooLarge()) {
            player.sendOverlayMessage(Component.translatable("message.universe.ship.too_large", ShipAssembler.MAX_BLOCKS));
            return;
        }
        Set<BlockPos> positions = new HashSet<>();
        for (long packed : result.positions()) {
            positions.add(BlockPos.of(packed));
        }
        int helms = 0;
        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            if (state.is(UniverseBlocks.SHIP_HELM.get())) {
                helms++;
            }
            if (state.getRelocability(level, pos) instanceof BlockRelocability.Multiblock multiblock && !multiblock.isRelocatable(positions)) {
                player.sendOverlayMessage(Component.translatable("message.universe.ship.split_multiblock", state.getBlock().getName()));
                return;
            }
        }
        if (helms > 1) {
            player.sendOverlayMessage(Component.translatable("message.universe.ship.two_helms"));
            return;
        }

        ShipShape.Placed placed = ShipShape.fromWorld(result.positions());
        List<ShipStructure.ShipBlock> blocks = new ArrayList<>(positions.size());
        double fuel = 0;
        for (int i = 0; i < result.positions().size(); i++) {
            BlockPos pos = BlockPos.of(result.positions().get(i));
            BlockState state = level.getBlockState(pos);
            BlockEntity blockEntity = level.getBlockEntity(pos);
            CompoundTag data = null;
            if (blockEntity != null) {
                if (blockEntity instanceof FuelTankBlockEntity tank) {
                    fuel += tank.fuel();
                }
                data = blockEntity.saveWithFullMetadata(level.registryAccess());
            }
            blocks.add(new ShipStructure.ShipBlock(placed.shape().x(i), placed.shape().y(i), placed.shape().z(i), state, data));
        }
        ShipStructure structure = new ShipStructure(blocks);
        ShipStats stats = structure.stats();
        if (stats.thrusters() == 0) {
            player.sendOverlayMessage(Component.translatable("message.universe.ship.no_thrusters"));
            return;
        }

        // 方块搬走：先拆方块实体，免得箱子把东西倒出来
        for (BlockPos pos : positions) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), MOVE_FLAGS);
        }
        float yaw = structure.helmFacing().toYRot();
        ShipEntity ship = ShipEntity.create(level, structure, placed.originX() + 0.5, placed.originY(), placed.originZ() + 0.5, yaw, fuel);
        ship.departures().putAll(player.getData(UniverseAttachments.DEPARTURES));
        level.addFreshEntity(ship);
        player.startRiding(ship);
        level.playSound(null, helmPos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8F, 1.6F);
        double twr = stats.thrustToWeight(ShipEnvironment.of(level).space() ? 0 : ShipEnvironment.of(level).gravity());
        ship.noticeLater(Component.translatable("message.universe.ship.assembled",
                stats.blocks(), String.format("%.0f", stats.mass()), Double.isInfinite(twr) ? "∞" : String.format("%.2f", twr),
                Math.round(fuel), Math.round(stats.fuelCapacity())));
        if (!stats.canLift(ShipEnvironment.of(level).gravity())) {
            player.sendSystemMessage(Component.translatable("message.universe.ship.too_heavy"));
        }
    }

    /**
     * 停船：船转到最近的 90° 倍数，方块放回原位附近的网格上，驾驶员站到驾驶台上。
     *
     * @return 是否成功；落点被别的方块挡住时返回 {@code false}，船保持原样
     */
    public static boolean disassemble(ShipEntity ship) {
        ShipStructure structure = ship.structure();
        if (structure == null || !(ship.level() instanceof ServerLevel level)) {
            return false;
        }
        int turns = ShipShape.quarterTurns(ship.getYRot() - ship.baseYaw());
        Rotation rotation = switch (turns) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        int ox = (int) Math.floor(ship.getX());
        int oz = (int) Math.floor(ship.getZ());
        int baseY = (int) Math.round(ship.getY());
        List<BlockPos> targets = null;
        // 落点挡住了就往上挪一两格再试
        for (int lift = 0; lift <= 2 && targets == null; lift++) {
            targets = targets(structure, ox, baseY + lift, oz, turns, level);
        }
        if (targets == null) {
            return false;
        }

        List<Entity> passengers = List.copyOf(ship.getPassengers());
        ship.ejectPassengers();
        List<BlockPos> tanks = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            ShipStructure.ShipBlock block = structure.block(i);
            BlockPos pos = targets.get(i);
            BlockState state = block.state().rotate(level, pos, rotation);
            level.setBlock(pos, state, PLACE_FLAGS);
            if (block.blockEntity() != null) {
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity != null) {
                    try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(blockEntity.problemPath(), LOGGER)) {
                        blockEntity.loadWithComponents(TagValueInput.create(reporter, level.registryAccess(), block.blockEntity()));
                    }
                    blockEntity.setChanged();
                }
            }
            ShipPart part = ShipParts.of(block.state());
            if (part == ShipPart.FUEL_TANK || part == ShipPart.FUEL_TANK_MK2) {
                tanks.add(pos);
            }
        }
        // 放完再统一通知邻居，红石之类的能接上
        for (int i = 0; i < targets.size(); i++) {
            level.updateNeighborsAt(targets.get(i), level.getBlockState(targets.get(i)).getBlock());
        }
        // 剩下的燃料按容量分回各个燃料舱
        double fuel = ship.fuelServer();
        double capacity = 0;
        for (BlockPos pos : tanks) {
            if (level.getBlockEntity(pos) instanceof FuelTankBlockEntity tank) {
                capacity += tank.capacity();
            }
        }
        for (BlockPos pos : tanks) {
            if (level.getBlockEntity(pos) instanceof FuelTankBlockEntity tank) {
                tank.setFuel(capacity <= 0 ? 0 : fuel * tank.capacity() / capacity);
            }
        }

        // 驾驶员站到驾驶台上，乘客站到座椅上
        int seatIndex = 0;
        for (int i = 0; i < passengers.size(); i++) {
            Entity passenger = passengers.get(i);
            ShipStructure.ShipBlock seatBlock;
            double height;
            if (i == 0 || structure.seatCount() == 0) {
                seatBlock = structure.helm();
                height = ShipHelmBlock.SEAT_HEIGHT;
            } else {
                seatBlock = structure.seat(Math.min(seatIndex++, structure.seatCount() - 1));
                height = ShipSeatBlock.SEAT_HEIGHT;
            }
            BlockPos pos = targets.get(structure.blocks().indexOf(seatBlock));
            passenger.teleportTo(pos.getX() + 0.5, pos.getY() + height, pos.getZ() + 0.5);
        }
        level.playSound(null, BlockPos.containing(ship.position()), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.8F, 1.6F);
        ship.discard();
        return true;
    }

    /** 每个方块的落点；有落点被挡住时返回 {@code null}。 */
    private static List<BlockPos> targets(ShipStructure structure, int ox, int oy, int oz, int turns, ServerLevel level) {
        List<BlockPos> targets = new ArrayList<>(structure.blocks().size());
        for (ShipStructure.ShipBlock block : structure.blocks()) {
            int[] r = ShipShape.rotateQuarter(block.x(), block.z(), turns);
            BlockPos pos = new BlockPos(ox + r[0], oy + block.y(), oz + r[1]);
            if (!level.isInWorldBounds(pos)) {
                return null;
            }
            BlockState existing = level.getBlockState(pos);
            if (!existing.isAir() && !existing.canBeReplaced()) {
                return null;
            }
            targets.add(pos);
        }
        return targets;
    }
}
