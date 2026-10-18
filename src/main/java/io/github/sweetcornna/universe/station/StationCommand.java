package io.github.sweetcornna.universe.station;

import java.util.Optional;
import java.util.OptionalInt;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import io.github.sweetcornna.universe.core.space.StationLayout.Site;
import io.github.sweetcornna.universe.dyson.Dyson;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /universe station [恒星系]}：传送到恒星系空间站的大厅（不写恒星系就是现在所在的恒星系），开发和验收用。
 * {@code /universe station info} 显示空间站的坐标。
 */
public final class StationCommand {
    private static final SimpleCommandExceptionType NO_STATION = new SimpleCommandExceptionType(Component.literal("这个恒星系没有空间站"));
    private static final SimpleCommandExceptionType NO_SYSTEM = new SimpleCommandExceptionType(Component.literal("这里不属于任何恒星系"));

    private StationCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("station")
                .executes(context -> teleport(context, current(context)))
                .then(Commands.literal("info")
                        .executes(StationCommand::info))
                .then(Commands.argument("system", IntegerArgumentType.integer(0))
                        .executes(context -> teleport(context, IntegerArgumentType.getInteger(context, "system"))));
    }

    private static int current(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        OptionalInt system = Dyson.systemOf(context.getSource().getLevel().dimension());
        if (system.isEmpty()) {
            throw NO_SYSTEM.create();
        }
        return system.getAsInt();
    }

    private static Site site(MinecraftServer server, int system) throws CommandSyntaxException {
        Optional<Site> site = Stations.site(server, SpaceDimensions.key(system));
        if (site.isEmpty()) {
            throw NO_STATION.create();
        }
        return site.get();
    }

    private static int teleport(CommandContext<CommandSourceStack> context, int system) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Site site = site(server, system);
        Entity entity = source.getEntityOrException();
        ServerLevel space = SpaceDimensions.getOrCreate(server, system);
        // 大厅里交易终端前面，面朝终端
        int[] spot = site.toWorld(-11, 1, 0);
        float yaw = switch (site.hangar()) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> -90;
            case NORTH -> 0;
        };
        TeleportTransition transition = new TeleportTransition(space, new Vec3(spot[0] + 0.5, spot[1], spot[2] + 0.5), Vec3.ZERO, yaw, 0,
                TeleportTransition.PLACE_PORTAL_TICKET);
        if (entity.teleport(transition) == null) {
            source.sendFailure(Component.literal("传送失败"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("已传送到恒星系 " + system + " 的空间站"), false);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int system = current(context);
        Site site = site(context.getSource().getServer(), system);
        context.getSource().sendSuccess(() -> Component.literal("恒星系 " + system + " 的空间站：" + site.x() + ", " + site.y() + ", " + site.z()
                + "，机库朝 " + site.hangar().name().toLowerCase(java.util.Locale.ROOT)), false);
        return 1;
    }
}
