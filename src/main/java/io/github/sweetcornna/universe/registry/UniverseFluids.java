package io.github.sweetcornna.universe.registry;

import java.util.function.Supplier;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.fluid.AcidFluid;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.PathType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * 流体注册（ADR-0030 §一）。ID 是固定的：模组互通按 ID 找（{@code universe:acid}、{@code universe:flowing_acid}）。
 *
 * <p>酸液照浓硫酸：比水重（1.84 g/cm³）、稠一些、流得慢一点（水 5 刻流一格，这里 8 刻）。浓硫酸实际比水稠二十几倍，
 * 照搬就成了岩浆，这里只取"稍稠"。人能在里面游、会淹死，算类水的液体（潜艇照样开、火灭得掉），但不是水：
 * 不在 {@code minecraft:water} 标签里，水生生物和水生植物都不认它。生物寻路当它伤人，绕着走。木船沉。
 */
public final class UniverseFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Universe.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, Universe.MOD_ID);

    public static final DeferredHolder<FluidType, FluidType> ACID_TYPE = FLUID_TYPES.register("acid", () -> new FluidType(FluidType.Properties.create()
            .density(1840)
            .viscosity(2500)
            .temperature(300)
            .motionScale(Entity.WATER_FLOW_SCALE * 0.75)
            .fallDistanceModifier(0.5F)
            .canSwim(true)
            .canDrown(true)
            .canExtinguish(true)
            .canConvertToSource(true)
            .canHydrate(false)
            .supportsBoating(false)
            .isWaterLike(true)
            .pathType(PathType.DAMAGING)
            .adjacentPathType(PathType.DAMAGING_IN_NEIGHBOR)
            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)
            .sound(SoundActions.FLUID_VAPORIZE, SoundEvents.FIRE_EXTINGUISH)));
    public static final DeferredHolder<Fluid, AcidFluid.Source> ACID = FLUIDS.register("acid", () -> new AcidFluid.Source(properties()));
    public static final DeferredHolder<Fluid, AcidFluid.Flowing> FLOWING_ACID = FLUIDS.register("flowing_acid",
            () -> new AcidFluid.Flowing(properties()));

    private static BaseFlowingFluid.Properties properties;

    private UniverseFluids() {
    }

    private static synchronized BaseFlowingFluid.Properties properties() {
        if (properties == null) {
            Supplier<FluidType> type = ACID_TYPE::get;
            properties = new BaseFlowingFluid.Properties(type, ACID::get, FLOWING_ACID::get)
                    .bucket(UniverseItems.ACID_BUCKET::get)
                    .block(UniverseBlocks.ACID::get)
                    .tickRate(8)
                    .slopeFindDistance(4)
                    .levelDecreasePerBlock(1)
                    .explosionResistance(100);
        }
        return properties;
    }

    public static void register(IEventBus modBus) {
        FLUID_TYPES.register(modBus);
        FLUIDS.register(modBus);
    }
}
