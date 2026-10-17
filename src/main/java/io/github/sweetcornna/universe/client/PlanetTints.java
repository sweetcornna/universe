package io.github.sweetcornna.universe.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.galaxy.Palette;
import io.github.sweetcornna.universe.core.terrain.TerrainModel;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * 星球方块按当前星球的配色染色（技术架构 §3.4）。方块用 {@link BlockTintSource}，
 * 物品模型里写 {@code {"type": "universe:planet_palette", "channel": "rock"}} 这样的染色来源。
 */
public final class PlanetTints {
    public static final Codec<Palette.Channel> CHANNEL_CODEC = Codec.STRING.comapFlatMap(name -> {
        try {
            return DataResult.success(Palette.Channel.valueOf(name.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "Unknown palette channel: " + name);
        }
    }, channel -> channel.name().toLowerCase(Locale.ROOT));

    private PlanetTints() {
    }

    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(RegisterColorHandlersEvent.BlockTintSources.class, event -> {
            for (UniverseBlocks.Tinted tinted : UniverseBlocks.TINTED) {
                List<BlockTintSource> sources = new ArrayList<>();
                sources.add(block(tinted.primary()));
                tinted.secondary().ifPresent(channel -> sources.add(block(channel)));
                if (tinted.block() == UniverseBlocks.PLANET_STRATA) {
                    sources.set(0, STRATA);
                }
                event.register(sources, tinted.block().get());
            }
        });
        modBus.addListener(RegisterColorHandlersEvent.ItemTintSources.class,
                event -> event.register(Universe.id("planet_palette"), PaletteItemTint.MAP_CODEC));
    }

    /** 岩层：按方块的 Y 查当前星球的层序表。 */
    private static final BlockTintSource STRATA = new BlockTintSource() {
        @Override
        public int color(BlockState state) {
            return ARGB.opaque(ClientGalaxy.currentPalette().rock());
        }

        @Override
        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            int[] strata = ClientGalaxy.currentStrata();
            return ARGB.opaque(strata[Math.clamp(pos.getY() - TerrainModel.MIN_Y, 0, strata.length - 1)]);
        }
    };

    public static BlockTintSource block(Palette.Channel channel) {
        return new BlockTintSource() {
            @Override
            public int color(BlockState state) {
                return ARGB.opaque(channel.of(ClientGalaxy.currentPalette()));
            }

            @Override
            public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
                return this.color(state);
            }
        };
    }

    /** 物品染色：和方块一样跟着当前星球走。 */
    public record PaletteItemTint(Palette.Channel channel) implements ItemTintSource {
        public static final MapCodec<PaletteItemTint> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                CHANNEL_CODEC.fieldOf("channel").forGetter(PaletteItemTint::channel)
        ).apply(i, PaletteItemTint::new));

        @Override
        public int calculate(ItemStack itemStack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
            return ARGB.opaque(this.channel.of(ClientGalaxy.currentPalette()));
        }

        @Override
        public MapCodec<PaletteItemTint> type() {
            return MAP_CODEC;
        }
    }
}
