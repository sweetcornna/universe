package io.github.sweetcornna.universe.logistics;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.galaxy.Resource;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.logistics.Logic;
import io.github.sweetcornna.universe.core.logistics.Station;
import io.github.sweetcornna.universe.core.logistics.StationSlot;
import io.github.sweetcornna.universe.core.space.SpaceLayout;
import io.github.sweetcornna.universe.factory.MachineBlock;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 轨道采集器（ADR-0015）：只能放在太空里、气态巨行星的大气球外 {@value #RANGE} 格以内，按行星的丰度源源不断地
 * 采氢和重氢。自己不派载具，等星际物流站的运输船来取（两格都是远程供应）。
 */
public class OrbitalCollectorBlock extends MachineBlock {
    /** 离大气球表面多远以内能放。 */
    public static final double RANGE = 200;
    /** 丰度为 1 时每秒的产量。 */
    public static final double HYDROGEN_RATE = 4;
    public static final double DEUTERIUM_RATE = 0.2;

    public OrbitalCollectorBlock(Properties properties, Supplier<? extends BlockEntityType<? extends MachineBlockEntity>> type) {
        super(properties, type);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getLevel() instanceof ServerLevel level && target(level, context.getClickedPos()).isEmpty()) {
            if (context.getPlayer() != null) {
                context.getPlayer().sendOverlayMessage(why(level, context.getClickedPos()));
            }
            return null;
        }
        return super.getStateForPlacement(context);
    }

    /** 放不下时告诉玩家原因：不在太空里、这个星系没有气态巨行星，或者离最近的那颗还有多远、在哪个方向。 */
    private static Component why(ServerLevel level, BlockPos pos) {
        if (SpaceDimensions.system(level.dimension()).isEmpty()) {
            return Component.translatable("message.universe.orbital_collector.not_in_space");
        }
        Optional<SpaceLayout.Body> giant = nearestGiant(level, pos);
        if (giant.isEmpty()) {
            return Component.translatable("message.universe.orbital_collector.no_giant");
        }
        SpaceLayout.Body body = giant.get();
        double distance = body.distanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) - body.radius();
        // 八个方位，和坠毁点、兴趣点的提示一样
        String[] directions = {"east", "southeast", "south", "southwest", "west", "northwest", "north", "northeast"};
        double angle = Math.toDegrees(Math.atan2(body.z() - pos.getZ(), body.x() - pos.getX()));
        String direction = directions[Math.floorMod(Math.round((float) angle / 45F), 8)];
        return Component.translatable("message.universe.orbital_collector.too_far", body.planet().name().zh(), body.planet().name().en(),
                (int) Math.ceil(distance - RANGE), Component.translatable("direction.universe." + direction), (int) RANGE);
    }

    /** 采集的对象：大气层外 {@value #RANGE} 格以内的气态巨行星（有几颗时取最近的）。 */
    public static Optional<SpaceLayout.Body> target(ServerLevel level, BlockPos pos) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        return nearestGiant(level, pos).filter(body -> body.distanceTo(x, y, z) - body.radius() <= RANGE);
    }

    /** 这个星系里离得最近的气态巨行星（按到大气层的距离）。 */
    private static Optional<SpaceLayout.Body> nearestGiant(ServerLevel level, BlockPos pos) {
        OptionalInt system = SpaceDimensions.system(level.dimension());
        if (system.isEmpty()) {
            return Optional.empty();
        }
        StarSystem star = Planets.galaxy(level.getServer()).systems().get(system.getAsInt());
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        SpaceLayout.Body best = null;
        double bestDistance = Double.MAX_VALUE;
        for (SpaceLayout.Body body : SpaceLayout.of(star)) {
            double distance = body.distanceTo(x, y, z) - body.radius();
            if (body.planet().type() == PlanetType.GAS_GIANT && distance < bestDistance) {
                bestDistance = distance;
                best = body;
            }
        }
        return Optional.ofNullable(best);
    }

    /** 新建的采集器：两格设成氢和重氢、远程供应，按丰度定产量。 */
    static void setup(ServerLevel level, BlockPos pos, Station station) {
        String[] items = {StationBlockEntity.idOf(UniverseItems.HYDROGEN.get()), StationBlockEntity.idOf(UniverseItems.DEUTERIUM.get())};
        for (int i = 0; i < items.length; i++) {
            StationSlot slot = station.slot(i);
            if (slot.isEmpty() || slot.canChangeItem()) {
                slot.setItem(items[i]);
            }
            slot.setLocal(Logic.STORAGE);
            slot.setRemote(Logic.SUPPLY);
            slot.setMax(station.kind().slotCapacity());
        }
        Optional<SpaceLayout.Body> body = target(level, pos);
        station.setProduction(0, body.map(b -> HYDROGEN_RATE * b.planet().abundance(Resource.HYDROGEN)).orElse(0.0));
        station.setProduction(1, body.map(b -> DEUTERIUM_RATE * b.planet().abundance(Resource.DEUTERIUM)).orElse(0.0));
    }

    /** 比较器按账本里的存量出信号，不按投放口。 */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(this.machinePos(state, pos)) instanceof StationBlockEntity station ? station.analogSignal() : 0;
    }
}
