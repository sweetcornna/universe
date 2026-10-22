package io.github.sweetcornna.universe.portal;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.portal.Glyphs;
import io.github.sweetcornna.universe.core.portal.PortalLayout;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * 先驱者传送门的调试命令（ADR-0024），验收和排查用：
 * <ul>
 * <li>{@code /universe portal glyphs all|none}：一下子认识全部符文 / 全忘掉
 * <li>{@code /universe portal goto}：传送到这颗星球的门前（还没放就当场放）
 * <li>{@code /universe portal rebuild}：拆掉这颗星球的门，按现在的规则重新放
 * <li>{@code /universe portal dial <星球>}：这颗星球的门拨到那颗星球（不看认不认识符文）
 * <li>{@code /universe portal address [星球]}：那颗星球（默认这颗）的地址，符文按 1–16 编号
 * </ul>
 */
public final class PortalCommand {
    private static final SimpleCommandExceptionType NO_PORTAL =
            new SimpleCommandExceptionType(Component.translatable("commands.universe.portal.no_portal"));
    private static final DynamicCommandExceptionType UNKNOWN_BODY =
            new DynamicCommandExceptionType(path -> Component.translatable("commands.universe.portal.unknown_body", String.valueOf(path)));

    private PortalCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(Universe.MOD_ID)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("portal")
                        .then(Commands.literal("glyphs")
                                .then(Commands.literal("all").executes(context -> glyphs(context.getSource(), Glyphs.ALL)))
                                .then(Commands.literal("none").executes(context -> glyphs(context.getSource(), 0))))
                        .then(Commands.literal("goto").executes(context -> go(context.getSource())))
                        .then(Commands.literal("rebuild").executes(context -> rebuild(context.getSource())))
                        .then(Commands.literal("dial")
                                .then(Commands.argument("body", StringArgumentType.greedyString())
                                        .executes(context -> dial(context.getSource(), StringArgumentType.getString(context, "body")))))
                        .then(Commands.literal("address")
                                .executes(context -> address(context.getSource(), null))
                                .then(Commands.argument("body", StringArgumentType.greedyString())
                                        .executes(context -> address(context.getSource(), StringArgumentType.getString(context, "body")))))));
    }

    private static int glyphs(CommandSourceStack source, int known) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        player.setData(UniverseAttachments.GLYPHS, known);
        source.sendSuccess(() -> Component.translatable("commands.universe.portal.glyphs", Glyphs.count(known)), false);
        return Glyphs.count(known);
    }

    private static Portals.Site here(CommandSourceStack source) throws CommandSyntaxException {
        if (!(source.getLevel() instanceof ServerLevel level)) {
            throw NO_PORTAL.create();
        }
        return Portals.ensure(level).orElseThrow(NO_PORTAL::create);
    }

    private static int go(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Portals.Site site = here(source);
        // 站在门前几格，面朝门
        BlockPos stand = site.at(0, 1, PortalLayout.PLATFORM_HALF_DEPTH);
        float yaw = (float) Math.toDegrees(Math.atan2(site.front().getStepX(), -site.front().getStepZ()));
        player.resetFallDistance();
        player.teleport(new TeleportTransition(source.getLevel(), Vec3.atBottomCenterOf(stand), Vec3.ZERO, yaw, 0, TeleportTransition.DO_NOTHING));
        source.sendSuccess(() -> Component.translatable("commands.universe.portal.went",
                site.origin().getX() + " " + site.origin().getY() + " " + site.origin().getZ()), false);
        return 1;
    }

    private static int rebuild(CommandSourceStack source) throws CommandSyntaxException {
        if (!(source.getLevel() instanceof ServerLevel level)) {
            throw NO_PORTAL.create();
        }
        Portals.site(level).ifPresent(site -> Portals.demolish(level, site));
        return go(source);
    }

    private static BodyId body(CommandSourceStack source, String path) throws CommandSyntaxException {
        Optional<BodyId> id = BodyId.parse(path);
        if (id.isEmpty() || Planets.galaxy(source.getServer()).body(id.get()).filter(Planet::landable).isEmpty()) {
            throw UNKNOWN_BODY.create(path);
        }
        return id.get();
    }

    private static int dial(CommandSourceStack source, String path) throws CommandSyntaxException {
        Portals.Site site = here(source);
        int[] glyphs = Portals.address(source.getServer(), body(source, path));
        Component problem = Portals.dial(source.getLevel(), site, glyphs);
        if (problem != null) {
            source.sendFailure(problem);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.universe.portal.dialed", path), false);
        return 1;
    }

    private static int address(CommandSourceStack source, String path) throws CommandSyntaxException {
        BodyId id = path == null ? Planets.bodyId(source.getLevel().dimension()).orElseThrow(NO_PORTAL::create) : body(source, path);
        String glyphs = Arrays.stream(Portals.address(source.getServer(), id)).mapToObj(g -> String.valueOf(g + 1)).collect(Collectors.joining(" "));
        source.sendSuccess(() -> Component.translatable("commands.universe.portal.address", id.path(), glyphs), false);
        return 1;
    }
}
