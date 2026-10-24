package io.github.sweetcornna.universe.client.fluid;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.ClientGalaxy;
import io.github.sweetcornna.universe.core.galaxy.WeatherProfile;
import io.github.sweetcornna.universe.core.survival.Acid;
import io.github.sweetcornna.universe.registry.UniverseFluids;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSource;

/**
 * 酸液的画法（ADR-0030 §一）：贴图是灰度的，颜色由 {@link Acid#color} 按当前星球的液体色算出来，偏黄绿。
 * 不在星球上（比如拿桶倒在主世界）用 {@link Acid#DEFAULT_COLOR}。
 *
 * <p>酸雨的颜色也在这里：原版的雨是写死的白色，{@code mixin.client.WeatherEffectRendererMixin} 把它换成这里的值。
 */
public final class AcidClient {
    private static final Material STILL = new Material(Universe.id("block/acid_still"));
    private static final Material FLOW = new Material(Universe.id("block/acid_flow"));

    private AcidClient() {
    }

    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(RegisterFluidModelsEvent.class, event -> event.register(
                new FluidModel.Unbaked(STILL, FLOW, null, TINT), UniverseFluids.ACID.get(), UniverseFluids.FLOWING_ACID.get()));
        modBus.addListener(RegisterColorHandlersEvent.ItemTintSources.class,
                event -> event.register(Universe.id("acid"), AcidItemTint.MAP_CODEC));
    }

    /** 酸液桶里那层液面的颜色：跟着当前星球走，和方块一样。 */
    public record AcidItemTint() implements ItemTintSource {
        public static final AcidItemTint INSTANCE = new AcidItemTint();
        public static final MapCodec<AcidItemTint> MAP_CODEC = MapCodec.unit(INSTANCE);

        @Override
        public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
            return AcidClient.color();
        }

        @Override
        public MapCodec<AcidItemTint> type() {
            return MAP_CODEC;
        }
    }

    /** 这颗星球上酸液的颜色（不透明 ARGB）。 */
    public static int color() {
        return ARGB.opaque(Acid.color(ClientGalaxy.currentPalette().liquid()));
    }

    /**
     * 此刻雨该是什么颜色（不透明 ARGB）；不下酸雨时返回 0，让原版照旧画白色的雨。
     * 在渲染线程上调用，只读已缓存的星球。
     */
    public static int rainColor() {
        return ClientGalaxy.currentPlanet()
                .filter(planet -> planet.weather().precipitation() == WeatherProfile.Precipitation.ACID_RAIN)
                .map(planet -> ARGB.opaque(Acid.color(planet.palette().liquid())))
                .orElse(0);
    }

    /** 液面和方块都按当前星球的液体色染，和水跟着星球走一样（{@code PlanetTints}）。 */
    private static final FluidTintSource TINT = new FluidTintSource() {
        @Override
        public int color(FluidState state) {
            return AcidClient.color();
        }

        @Override
        public int color(BlockState state) {
            return AcidClient.color();
        }

        @Override
        public int colorInWorld(FluidState fluidState, BlockState blockState, BlockAndTintGetter level, BlockPos pos) {
            return AcidClient.color();
        }

        @Override
        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            return AcidClient.color();
        }
    };
}
