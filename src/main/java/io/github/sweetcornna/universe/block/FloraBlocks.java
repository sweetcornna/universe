package io.github.sweetcornna.universe.block;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.ecology.PlantForm;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * 星球植物方块共用的方块状态属性和标签。
 *
 * <p>植物方块不按物种注册（物种是运行时按星球生成的），而是按形态注册，物种之间靠变体号区分：
 * 客户端按"当前星球 + 形态 + 变体号"查颜色（{@code core.ecology.Flora}）。
 */
public final class FloraBlocks {
    /** 变体号：同一形态的第几个物种（树木方块是第几种树）。 */
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, PlantForm.VARIANTS - 1);
    /** 会发光的物种。 */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** 星球植物能长在哪些方块上。 */
    public static final TagKey<Block> PLANT_SOIL = TagKey.create(Registries.BLOCK, Universe.id("planet_plant_soil"));

    private FloraBlocks() {
    }
}
