package io.github.sweetcornna.universe.asteroid;

import java.util.Locale;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.github.sweetcornna.universe.core.space.AsteroidField;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /universe asteroids [info|goto|spawn|reset]}：调试小行星（ADR-0017）。{@code info} 列出这个恒星系的小行星带、离最近的带多远、
 * 已经生成了几颗；{@code goto} 把玩家传送到最近的带正中间；{@code spawn [距离]} 在眼前放一颗；{@code reset} 删掉全部小行星、清空冷却。
 */
public final class AsteroidCommand {
    private AsteroidCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("asteroids")
                .executes(AsteroidCommand::info)
                .then(Commands.literal("info").executes(AsteroidCommand::info))
                .then(Commands.literal("goto").executes(AsteroidCommand::teleport))
                .then(Commands.literal("spawn").executes(context -> spawn(context, -1))
                        .then(Commands.argument("distance", IntegerArgumentType.integer(4, 160))
                                .executes(context -> spawn(context, IntegerArgumentType.getInteger(context, "distance")))))
                .then(Commands.literal("reset").executes(context -> {
                    ServerLevel level = context.getSource().getLevel();
                    int n = Asteroids.reset(level);
                    context.getSource().sendSuccess(() -> Component.translatable("commands.universe.asteroids.reset", n), true);
                    return n;
                }));
    }

    private static Asteroids.Field field(CommandContext<CommandSourceStack> context) {
        Asteroids.Field field = Asteroids.field(context.getSource().getLevel());
        if (field == null) {
            context.getSource().sendFailure(Component.translatable("commands.universe.asteroids.not_space"));
        }
        return field;
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        Asteroids.Field field = field(context);
        if (field == null) {
            return 0;
        }
        Vec3 at = context.getSource().getPosition();
        for (AsteroidField.Belt belt : field.belts()) {
            int distance = (int) belt.distanceTo(at.x, at.y, at.z);
            context.getSource().sendSuccess(() -> Component.translatable("commands.universe.asteroids.belt", (int) belt.inner(), (int) belt.outer(),
                    (int) belt.halfHeight(), distance), false);
        }
        int live = Asteroids.live(context.getSource().getLevel());
        String density = String.format("%.2f", AsteroidField.density(field.belts(), at.x, at.y, at.z));
        context.getSource().sendSuccess(() -> Component.translatable("commands.universe.asteroids.info", field.belts().size(), density, live), false);
        return field.belts().size();
    }

    private static int teleport(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Asteroids.Field field = field(context);
        if (field == null) {
            return 0;
        }
        AsteroidField.Belt belt = AsteroidField.nearest(field.belts(), player.getX(), player.getY(), player.getZ());
        double r = Math.sqrt(player.getX() * player.getX() + player.getZ() * player.getZ());
        double angle = r < 1 ? 0 : Math.atan2(player.getZ(), player.getX());
        // 带的正中间，避开天体和空间站：沿着带转，找一个能放小行星的地方
        for (int step = 0; step < 72; step++) {
            double a = angle + step * Math.PI * 2 / 72;
            double x = Math.cos(a) * belt.radius();
            double z = Math.sin(a) * belt.radius();
            if (field.clear(x, 0, z)) {
                player.teleportTo(x, 0, z);
                context.getSource().sendSuccess(() -> Component.translatable("commands.universe.asteroids.goto", (int) x, (int) z), true);
                return 1;
            }
        }
        return 0;
    }

    private static int spawn(CommandContext<CommandSourceStack> context, int distance) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = context.getSource().getLevel();
        long seed = level.getRandom().nextLong();
        // 没给距离就离眼睛 8 格再加上它的直径，大的也不会贴到脸上
        double d = distance > 0 ? distance : 8 + AsteroidField.shape(seed).radius() * 2;
        Vec3 at = player.getEyePosition().add(player.getViewVector(1).scale(d));
        Asteroid asteroid = Asteroid.create(level, seed, Long.MIN_VALUE, at);
        level.addFreshEntity(asteroid);
        context.getSource().sendSuccess(() -> Component.translatable("commands.universe.asteroids.spawned",
                Component.translatable("hud.universe.asteroid." + asteroid.shape().size().name().toLowerCase(Locale.ROOT))), true);
        return 1;
    }
}
