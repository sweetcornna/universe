package io.github.sweetcornna.universe.hazard;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.hazard.GeyserKind;
import io.github.sweetcornna.universe.core.hazard.HazardKind;
import io.github.sweetcornna.universe.core.hazard.Vortex;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /universe hazard ...}：调试局部危害（ADR-0034 §六）。在面前马上放一处，不管这里是什么星球、频率是多少；
 * 放在基地信标旁边的云和尘卷风会很快散掉。
 *
 * <ul>
 *   <li>{@code geyser}：面前 4 格放一个这颗星球那种的喷口（别的星球放灼热的），马上咕嘟、喷发；</li>
 *   <li>{@code eruption}：脚下这一带火山喷发；</li>
 *   <li>{@code spore_cloud}：面前 7 格一团孢子云；</li>
 *   <li>{@code dust_devil}：面前 9 格一个尘卷风，朝自己走过来；</li>
 *   <li>{@code clear}：附近的云、尘卷风都散掉，喷发停下。</li>
 * </ul>
 */
public final class HazardCommand {
    private static final double CLEAR_RANGE = 256;

    private HazardCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> hazard = Commands.literal("hazard")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
        for (HazardKind kind : HazardKind.values()) {
            hazard.then(Commands.literal(kind.id()).executes(context -> trigger(context.getSource(), kind)));
        }
        hazard.then(Commands.literal("clear").executes(context -> clear(context.getSource())));
        dispatcher.register(Commands.literal(Universe.MOD_ID)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(hazard));
    }

    private static int trigger(CommandSourceStack source, HazardKind kind) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.level();
        Rng rng = Rng.of(player.getRandom().nextLong());
        boolean done = switch (kind) {
            case GEYSER -> geyser(level, player);
            case ERUPTION -> Hazards.start(level, HazardKind.ERUPTION, player, rng);
            case SPORE_CLOUD -> {
                Vec3 at = ahead(level, player, 7);
                Planet planet = Planets.planet(level).orElse(null);
                yield SporeCloud.spawn(level, at, planet == null ? level.getSeed() : planet.seed(), planet == null ? 0.5 : planet.weather().wind(),
                        rng) != null;
            }
            case DUST_DEVIL -> {
                Vec3 at = ahead(level, player, 9);
                yield DustDevil.spawn(level, at, Vortex.heading(at.x - player.getX(), at.z - player.getZ(), rng), rng) != null;
            }
        };
        if (!done) {
            source.sendFailure(Component.translatable("commands.universe.hazard.failed", Component.translatable("hazard.universe.kind." + kind.id())));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.universe.hazard.started", Component.translatable("hazard.universe.kind." + kind.id())),
                true);
        return 1;
    }

    /** 面前放一个喷口，马上开始响。 */
    private static boolean geyser(ServerLevel level, ServerPlayer player) {
        Vec3 at = ahead(level, player, 4);
        BlockPos pos = BlockPos.containing(at).below();
        GeyserKind kind = Planets.planet(level).flatMap(planet -> GeyserKind.of(planet.type())).orElse(GeyserKind.HOT);
        level.setBlockAndUpdate(pos, UniverseBlocks.GEYSER_VENTS.get(kind).get().defaultBlockState());
        if (level.getBlockEntity(pos) instanceof GeyserVentBlockEntity vent) {
            vent.erupt(false);
            return true;
        }
        return false;
    }

    /** 面前 {@code distance} 格的地面。 */
    private static Vec3 ahead(ServerLevel level, ServerPlayer player, double distance) {
        Vec3 look = player.getLookAngle();
        double length = Math.max(1e-4, Math.hypot(look.x, look.z));
        double x = player.getX() + look.x / length * distance;
        double z = player.getZ() + look.z / length * distance;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
        return new Vec3(x, y, z);
    }

    private static int clear(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.level();
        AABB box = player.getBoundingBox().inflate(CLEAR_RANGE);
        int count = Hazards.stopEruptions(level);
        for (SporeCloud cloud : level.getEntitiesOfClass(SporeCloud.class, box)) {
            cloud.discard();
            count++;
        }
        for (DustDevil devil : level.getEntitiesOfClass(DustDevil.class, box)) {
            devil.discard();
            count++;
        }
        int cleared = count;
        source.sendSuccess(() -> Component.translatable("commands.universe.hazard.cleared", cleared), true);
        return cleared;
    }
}
