package io.github.sweetcornna.universe.entity.ship;

import java.util.Locale;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.ship.ShipStats;
import io.github.sweetcornna.universe.core.space.Warp;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 调试飞船用的命令（{@code /universe ship ...}）：从驾驶台组装、强制停船、加满燃料、查看性能、直接跃迁。
 */
public final class ShipCommand {
    private static final SimpleCommandExceptionType NOT_A_HELM = new SimpleCommandExceptionType(Component.literal("那里不是驾驶台"));
    private static final SimpleCommandExceptionType NOT_ON_SHIP = new SimpleCommandExceptionType(Component.literal("你不在飞船上"));
    private static final SimpleCommandExceptionType NO_SYSTEM = new SimpleCommandExceptionType(Component.literal("没有这个编号的恒星系"));

    private ShipCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("ship")
                .then(Commands.literal("assemble")
                        .then(Commands.argument("helm", BlockPosArgument.blockPos())
                                .executes(ShipCommand::assemble)))
                .then(Commands.literal("park")
                        .executes(ShipCommand::park))
                .then(Commands.literal("fuel")
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                .executes(ShipCommand::fuel)))
                .then(Commands.literal("info")
                        .executes(ShipCommand::info))
                .then(Commands.literal("warp")
                        .then(Commands.argument("system", IntegerArgumentType.integer(0))
                                .executes(ShipCommand::warp)));
    }

    private static int assemble(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "helm");
        if (!context.getSource().getLevel().getBlockState(pos).is(UniverseBlocks.SHIP_HELM.get())) {
            throw NOT_A_HELM.create();
        }
        ShipAssembly.assembleAndBoard(context.getSource().getLevel(), pos, player);
        return player.getVehicle() instanceof ShipEntity ? 1 : 0;
    }

    private static ShipEntity ship(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (context.getSource().getPlayerOrException().getVehicle() instanceof ShipEntity ship) {
            return ship;
        }
        throw NOT_ON_SHIP.create();
    }

    private static int park(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        boolean done = ShipAssembly.disassemble(ship(context));
        context.getSource().sendSuccess(() -> Component.literal(done ? "已停船" : "这里放不下飞船"), false);
        return done ? 1 : 0;
    }

    private static int fuel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ShipEntity ship = ship(context);
        double capacity = ship.structure().stats().fuelCapacity();
        double amount = Math.min(capacity, DoubleArgumentType.getDouble(context, "amount"));
        ship.setFuelServer(amount);
        context.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "燃料 %.0f / %.0f", amount, capacity)), false);
        return 1;
    }

    /** 不管射程、翘曲器和引力井，直接跃迁到某个恒星系（编号见 {@code /universe planet list}）。 */
    private static int warp(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ShipEntity ship = ship(context);
        MinecraftServer server = context.getSource().getServer();
        Galaxy galaxy = Planets.galaxy(server);
        int target = IntegerArgumentType.getInteger(context, "system");
        StarSystem to = galaxy.system(target).orElseThrow(NO_SYSTEM::create);
        // 从哪个恒星系出发：太空维度和星球维度都看得出来，主世界算母星系
        int here = SpaceDimensions.system(ship.level().dimension())
                .orElseGet(() -> Planets.bodyId(ship.level().dimension()).map(BodyId::system).orElse(galaxy.home().index()));
        StarSystem from = galaxy.system(here).orElse(galaxy.home());
        double[] arrival = Warp.arrival(from, to, ShipTransit.layout(server, target));
        ShipTransit.warp(ship, target, new Vec3(arrival[0], arrival[1], arrival[2]), (float) arrival[3]);
        context.getSource().sendSuccess(() -> Component.literal("跃迁到 " + to.name().zh() + "（" + to.star().starClass().nameZh() + "）"), false);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ShipEntity ship = ship(context);
        ShipStats s = ship.structure().stats();
        String text = String.format(Locale.ROOT,
                "%d 个方块，%.1f 吨，推进器 %d，燃料舱 %d，脉冲引擎 %d，跃迁引擎 %d，推重比(1g) %.2f，燃料 %.0f/%.0f，位置 %.1f %.1f %.1f，偏航 %.1f，速度 %.3f，%s",
                s.blocks(), s.mass(), s.thrusters(), s.fuelTanks(), s.impulse(), s.warpDrives(), s.thrustToWeight(1), ship.fuelServer(), s.fuelCapacity(),
                ship.getX(), ship.getY(), ship.getZ(), ship.getYRot(), ship.flight().speed(), ship.isLanded() ? "着陆" : "飞行");
        context.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }
}
