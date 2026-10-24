package io.github.sweetcornna.universe.deepspace;

import io.github.sweetcornna.universe.core.deepspace.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * 残骸和前哨站的方块（ADR-0034 §四）：每种对应一种 {@link Part}。拆掉时（不管是挖的、炸的还是烧的）报给 {@link Hulks}，
 * 在残骸里就扣结构完整度。没有物品，只能在太空里拆到。
 */
public class DerelictBlock extends Block {
    private final Part part;

    public DerelictBlock(Part part, BlockBehaviour.Properties properties) {
        super(properties);
        this.part = part;
    }

    public Part part() {
        return this.part;
    }

    /** 残骸方块共用的属性：金属、镐拆、推不动、不刷怪。 */
    public static BlockBehaviour.Properties derelict(BlockBehaviour.Properties properties, float strength) {
        return properties.mapColor(MapColor.METAL).strength(strength, strength * 2).sound(SoundType.METAL).requiresCorrectToolForDrops()
                .pushReaction(PushReaction.IMMOVEABLE).isValidSpawn(Blocks::never);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        Hulks.removed(level, pos, this.part);
    }
}
