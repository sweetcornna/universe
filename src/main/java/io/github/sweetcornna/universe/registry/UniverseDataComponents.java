package io.github.sweetcornna.universe.registry;

import java.util.Locale;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.blueprint.BlueprintInfo;
import io.github.sweetcornna.universe.blueprint.BlueprintSelection;
import io.github.sweetcornna.universe.core.survival.MultitoolClass;
import io.github.sweetcornna.universe.core.survival.MultitoolSpec;
import io.github.sweetcornna.universe.core.survival.ToolMode;
import io.github.sweetcornna.universe.core.tech.Grade;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.core.tech.TechModule;
import io.github.sweetcornna.universe.entity.CreatureEggs;
import io.github.sweetcornna.universe.exocraft.VehicleRefiner;
import io.github.sweetcornna.universe.factory.belt.StorageTankBlockEntity;
import io.github.sweetcornna.universe.logistics.StationContents;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 物品的数据组件。 */
public final class UniverseDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Universe.MOD_ID);

    /** 技术模块按名字存（飞船驾驶台的技术摘要也用它，ADR-0024）。 */
    public static final Codec<TechModule> TECH_MODULE_CODEC = RecordCodecBuilder.create(i -> i.group(
            enumCodec(ModuleType.class).fieldOf("type").forGetter(TechModule::type),
            enumCodec(Grade.class).fieldOf("grade").forGetter(TechModule::grade),
            Codec.DOUBLE.listOf().fieldOf("values").forGetter(TechModule::values)
    ).apply(i, TechModule::new));
    private static final StreamCodec<ByteBuf, TechModule> TECH_MODULE_STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(index -> ModuleType.values()[index], ModuleType::ordinal), TechModule::type,
            ByteBufCodecs.idMapper(index -> Grade.values()[index], Grade::ordinal), TechModule::grade,
            ByteBufCodecs.DOUBLE.apply(ByteBufCodecs.list(4)), TechModule::values,
            TechModule::new);

    /** 蓄电器拆下来时带着的电量（焦耳）。 */
    public static final Supplier<DataComponentType<Long>> STORED_ENERGY = COMPONENTS.registerComponentType("stored_energy",
            builder -> builder.persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG));
    /** 储液罐拆下来时带着的流体（ADR-0015）。 */
    public static final Supplier<DataComponentType<StorageTankBlockEntity.Contents>> TANK_CONTENTS = COMPONENTS.registerComponentType(
            "tank_contents", builder -> builder.persistent(StorageTankBlockEntity.Contents.CODEC)
                    .networkSynchronized(StorageTankBlockEntity.Contents.STREAM_CODEC));
    /** 物流站拆下来时带着的物品、载具和设置（ADR-0015）。 */
    public static final Supplier<DataComponentType<StationContents>> STATION_CONTENTS = COMPONENTS.registerComponentType(
            "station_contents", builder -> builder.persistent(StationContents.CODEC).networkSynchronized(StationContents.STREAM_CODEC));
    /** 探索车收起来时油箱里的燃料（ADR-0017）。 */
    public static final Supplier<DataComponentType<Integer>> EXOCRAFT_FUEL = COMPONENTS.registerComponentType("exocraft_fuel",
            builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    /** 技术模块的种类、品级和数值（ADR-0014）。 */
    public static final Supplier<DataComponentType<TechModule>> TECH_MODULE = COMPONENTS.registerComponentType("tech_module",
            builder -> builder.persistent(TECH_MODULE_CODEC).networkSynchronized(TECH_MODULE_STREAM_CODEC));
    /** 多功能工具上装着的模块（ADR-0014）。 */
    public static final Supplier<DataComponentType<ItemContainerContents>> TOOL_MODULES = COMPONENTS.registerComponentType("tool_modules",
            builder -> builder.persistent(ItemContainerContents.CODEC).networkSynchronized(ItemContainerContents.STREAM_CODEC).cacheEncoding());

    /** 多功能工具的模式：采矿或战斗（ADR-0014）。 */
    public static final Supplier<DataComponentType<ToolMode>> TOOL_MODE = COMPONENTS.registerComponentType("tool_mode",
            builder -> builder.persistent(enumCodec(ToolMode.class))
                    .networkSynchronized(ByteBufCodecs.idMapper(index -> ToolMode.values()[Math.floorMod(index, ToolMode.values().length)],
                            ToolMode::ordinal)));
    /** 多功能工具的类型、档次和坏了几格（ADR-0026）；没有这个组件的旧工具按 A 档手枪算。 */
    public static final Supplier<DataComponentType<MultitoolSpec>> TOOL_SPEC = COMPONENTS.registerComponentType("tool_spec",
            builder -> builder.persistent(RecordCodecBuilder.<MultitoolSpec>create(i -> i.group(
                            enumCodec(MultitoolClass.class).fieldOf("type").forGetter(MultitoolSpec::type),
                            enumCodec(Grade.class).fieldOf("grade").forGetter(MultitoolSpec::grade),
                            Codec.INT.optionalFieldOf("broken", 0).forGetter(MultitoolSpec::broken)
                    ).apply(i, MultitoolSpec::new)))
                    .networkSynchronized(StreamCodec.composite(
                            ByteBufCodecs.idMapper(index -> MultitoolClass.values()[index], MultitoolClass::ordinal), MultitoolSpec::type,
                            ByteBufCodecs.idMapper(index -> Grade.values()[index], Grade::ordinal), MultitoolSpec::grade,
                            ByteBufCodecs.VAR_INT, MultitoolSpec::broken,
                            MultitoolSpec::new)));

    /** 空白蓝图框的选区（ADR-0018）。 */
    public static final Supplier<DataComponentType<BlueprintSelection>> BLUEPRINT_SELECTION = COMPONENTS.registerComponentType(
            "blueprint_selection", builder -> builder.persistent(BlueprintSelection.CODEC).networkSynchronized(BlueprintSelection.STREAM_CODEC));
    /** 画好的蓝图：编号、名字、大小和材料（ADR-0018）。 */
    public static final Supplier<DataComponentType<BlueprintInfo>> BLUEPRINT = COMPONENTS.registerComponentType("blueprint",
            builder -> builder.persistent(BlueprintInfo.CODEC).networkSynchronized(BlueprintInfo.STREAM_CODEC).cacheEncoding());
    /** 喷过增产剂的物品：每个物品的增产点数，1–4（ADR-0022）。 */
    public static final Supplier<DataComponentType<Integer>> PROLIFERATION = COMPONENTS.registerComponentType("proliferation",
            builder -> builder.persistent(Codec.intRange(1, 4)).networkSynchronized(ByteBufCodecs.VAR_INT));
    /** 蓝图转了几个 90°（ADR-0018）。 */
    public static final Supplier<DataComponentType<Integer>> BLUEPRINT_ROTATION = COMPONENTS.registerComponentType("blueprint_rotation",
            builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    /** 重型车收成物品时，车尾精炼机里的东西和选好的配方（ADR-0026）。 */
    public static final Supplier<DataComponentType<VehicleRefiner.Contents>> VEHICLE_REFINER = COMPONENTS.registerComponentType(
            "vehicle_refiner", builder -> builder.persistent(VehicleRefiner.Contents.CODEC)
                    .networkSynchronized(VehicleRefiner.Contents.STREAM_CODEC).cacheEncoding());
    /** 生物卵上记着的物种、性状和主人（ADR-0026 §九）。 */
    public static final Supplier<DataComponentType<CreatureEggs.Egg>> CREATURE_EGG = COMPONENTS.registerComponentType("creature_egg",
            builder -> builder.persistent(CreatureEggs.Egg.CODEC).networkSynchronized(CreatureEggs.Egg.STREAM_CODEC).cacheEncoding());

    private UniverseDataComponents() {
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }

    public static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
        return Codec.STRING.comapFlatMap(name -> {
            try {
                return DataResult.success(Enum.valueOf(type, name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                return DataResult.error(() -> "Unknown " + type.getSimpleName() + ": " + name);
            }
        }, value -> value.name().toLowerCase(Locale.ROOT));
    }
}
