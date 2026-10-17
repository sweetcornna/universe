package io.github.sweetcornna.universe.registry;

import java.util.List;
import java.util.Optional;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.galaxy.Palette;
import net.minecraft.util.ColorRGBA;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SandBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 方块注册。
 *
 * <p>星球方块（{@code planet_*}）用灰度贴图，客户端按所在星球的配色染色（技术架构 §3.4）：
 * 同一块岩石在紫色星球上是紫的，带回主世界就变成中性灰。这和原版的草方块随群系变色是一个道理。
 */
public final class UniverseBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Universe.MOD_ID);

    public static final DeferredBlock<Block> PLANET_ROCK = BLOCKS.registerSimpleBlock("planet_rock",
            p -> p.mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 6.0F));
    public static final DeferredBlock<Block> PLANET_COBBLESTONE = BLOCKS.registerSimpleBlock("planet_cobblestone",
            p -> p.mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 6.0F));
    public static final DeferredBlock<Block> PLANET_SOIL = BLOCKS.registerSimpleBlock("planet_soil",
            p -> p.mapColor(MapColor.DIRT).strength(0.5F).sound(SoundType.GRAVEL));
    public static final DeferredBlock<Block> PLANET_TURF = BLOCKS.registerSimpleBlock("planet_turf",
            p -> p.mapColor(MapColor.GRASS).strength(0.6F).sound(SoundType.GRASS));
    public static final DeferredBlock<Block> PLANET_REGOLITH = BLOCKS.registerSimpleBlock("planet_regolith",
            p -> p.mapColor(MapColor.COLOR_LIGHT_GRAY).strength(0.5F).sound(SoundType.GRAVEL));
    public static final DeferredBlock<SandBlock> PLANET_SAND = BLOCKS.registerBlock("planet_sand",
            p -> new SandBlock(new ColorRGBA(0xFFD6CDA6), p),
            p -> p.mapColor(MapColor.SAND).instrument(NoteBlockInstrument.SNARE).strength(0.5F).sound(SoundType.SAND));
    public static final DeferredBlock<Block> PLANET_SNOW = BLOCKS.registerSimpleBlock("planet_snow",
            p -> p.mapColor(MapColor.SNOW).strength(0.2F).sound(SoundType.SNOW));
    public static final DeferredBlock<Block> PLANET_ICE = BLOCKS.registerSimpleBlock("planet_ice",
            p -> p.mapColor(MapColor.ICE).instrument(NoteBlockInstrument.CHIME).friction(0.98F).strength(0.5F).sound(SoundType.GLASS));
    public static final DeferredBlock<Block> PLANET_ASH = BLOCKS.registerSimpleBlock("planet_ash",
            p -> p.mapColor(MapColor.COLOR_GRAY).strength(0.5F).sound(SoundType.SAND));
    public static final DeferredBlock<Block> PLANET_CRYSTAL = BLOCKS.registerSimpleBlock("planet_crystal",
            p -> p.mapColor(MapColor.COLOR_PURPLE).strength(1.5F).sound(SoundType.AMETHYST).requiresCorrectToolForDrops()
                    .lightLevel(state -> 10));

    /**
     * 按星球配色染色的方块。
     *
     * @param primary   染色索引 0 用的配色
     * @param secondary 染色索引 1 用的配色（草皮方块的泥土部分）
     */
    public record Tinted(DeferredBlock<? extends Block> block, Palette.Channel primary, Optional<Palette.Channel> secondary) {
        static Tinted of(DeferredBlock<? extends Block> block, Palette.Channel channel) {
            return new Tinted(block, channel, Optional.empty());
        }
    }

    public static final List<Tinted> TINTED = List.of(
            Tinted.of(PLANET_ROCK, Palette.Channel.ROCK),
            Tinted.of(PLANET_COBBLESTONE, Palette.Channel.ROCK),
            Tinted.of(PLANET_SOIL, Palette.Channel.SOIL),
            new Tinted(PLANET_TURF, Palette.Channel.SURFACE, Optional.of(Palette.Channel.SOIL)),
            Tinted.of(PLANET_REGOLITH, Palette.Channel.SURFACE),
            Tinted.of(PLANET_SAND, Palette.Channel.SAND),
            Tinted.of(PLANET_SNOW, Palette.Channel.SURFACE),
            Tinted.of(PLANET_ICE, Palette.Channel.SOIL),
            Tinted.of(PLANET_ASH, Palette.Channel.SURFACE),
            Tinted.of(PLANET_CRYSTAL, Palette.Channel.ACCENT));

    private UniverseBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
