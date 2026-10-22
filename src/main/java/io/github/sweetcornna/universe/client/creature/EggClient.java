package io.github.sweetcornna.universe.client.creature;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.CreatureEggBlockEntity;
import io.github.sweetcornna.universe.core.ecology.CreatureTraits;
import io.github.sweetcornna.universe.entity.CreatureEggs;
import io.github.sweetcornna.universe.entity.CreatureEggs.Egg;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseMenus;
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
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** 生物卵和卵序列仪的客户端（ADR-0026 §九）：卵按性状里的颜色染色，序列仪的界面。 */
public final class EggClient {
    /** 地上的卵：颜色从方块实体读，物品栏里的图标按物品上的。 */
    private static final BlockTintSource EGG_TINT = new BlockTintSource() {
        @Override
        public int color(BlockState state) {
            return ARGB.opaque(CreatureTraits.NO_TINT);
        }

        @Override
        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            return ARGB.opaque(level.getBlockEntity(pos) instanceof CreatureEggBlockEntity egg ? egg.tint() : CreatureTraits.NO_TINT);
        }
    };

    private EggClient() {
    }

    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(RegisterMenuScreensEvent.class,
                event -> event.register(UniverseMenus.EGG_SEQUENCER.menuType(), EggSequencerScreen::new));
        modBus.addListener(RegisterColorHandlersEvent.BlockTintSources.class,
                event -> event.register(List.of(EGG_TINT), UniverseBlocks.CREATURE_EGG.get()));
        modBus.addListener(RegisterColorHandlersEvent.ItemTintSources.class,
                event -> event.register(Universe.id("creature_egg"), EggItemTint.MAP_CODEC));
    }

    /** 卵物品的颜色：性状里的颜色，空卵不染。 */
    public record EggItemTint() implements ItemTintSource {
        public static final EggItemTint INSTANCE = new EggItemTint();
        public static final MapCodec<EggItemTint> MAP_CODEC = MapCodec.unit(INSTANCE);

        @Override
        public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
            Egg egg = CreatureEggs.egg(stack);
            return ARGB.opaque(egg == null ? CreatureTraits.NO_TINT : egg.traits().tint());
        }

        @Override
        public MapCodec<EggItemTint> type() {
            return MAP_CODEC;
        }
    }
}
