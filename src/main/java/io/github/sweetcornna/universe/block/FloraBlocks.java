package io.github.sweetcornna.universe.block;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.ecology.Biotope;
import io.github.sweetcornna.universe.core.ecology.Flora;
import io.github.sweetcornna.universe.core.ecology.FloraLayout;
import io.github.sweetcornna.universe.core.ecology.PlacedFlora;
import io.github.sweetcornna.universe.core.ecology.PlantForm;
import io.github.sweetcornna.universe.core.terrain.BiomeKind;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.planet.gen.PlanetBiomes;
import io.github.sweetcornna.universe.world.planet.gen.PlanetFloraFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * 星球植物方块共用的方块状态属性和标签。
 *
 * <p>植物方块不按物种注册（物种是运行时按星球生成的），而是按形态注册，物种之间靠变体号区分：
 * 客户端按"当前星球 + 形态 + 变体号"查颜色（{@code core.ecology.Flora}）。玩家放下时按所在星球挑物种
 * （{@link PlacedFlora}，ADR-0030 §六）。
 */
public final class FloraBlocks {
    /** 变体号：同一形态的第几个物种（树木方块是第几种树）。 */
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, PlantForm.VARIANTS - 1);
    /** 会发光的物种。 */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** 星球植物能长在哪些方块上。 */
    public static final TagKey<Block> PLANT_SOIL = TagKey.create(Registries.BLOCK, Universe.id("planet_plant_soil"));

    /** 客户端：玩家所在星球的植物（不在星球上时没有物种）。由客户端初始化时设置，放方块时预测用。 */
    public static Supplier<Flora> clientFlora = () -> Flora.NEUTRAL;

    private FloraBlocks() {
    }

    /** 这个世界的植物：服务端按星球查，客户端用当前星球的；不是星球、星球上没有生命时没有物种。 */
    static Flora flora(Level level) {
        if (level instanceof ServerLevel server) {
            return Planets.planet(server).flatMap(PlanetFloraFeature::layout).map(FloraLayout::flora).orElse(Flora.NEUTRAL);
        }
        return clientFlora.get();
    }

    /** 这一格的生境；不是星球的群系、不长陆生植物的群系为 null。 */
    static @Nullable Biotope biotope(Level level, BlockPos pos) {
        BiomeKind kind = PlanetBiomes.kind(level.getBiome(pos));
        return kind == null ? null : Biotope.of(kind);
    }

    static long hash(BlockPos pos) {
        return PlacedFlora.hash(pos.getX(), pos.getY(), pos.getZ());
    }
}
