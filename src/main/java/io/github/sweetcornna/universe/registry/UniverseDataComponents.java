package io.github.sweetcornna.universe.registry;

import java.util.Locale;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.blueprint.BlueprintInfo;
import io.github.sweetcornna.universe.blueprint.BlueprintSelection;
import io.github.sweetcornna.universe.core.survival.ToolMode;
import io.github.sweetcornna.universe.core.tech.Grade;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.core.tech.TechModule;
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

    private static final Codec<TechModule> TECH_MODULE_CODEC = RecordCodecBuilder.create(i -> i.group(
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

    /** 空白蓝图框的选区（ADR-0018）。 */
    public static final Supplier<DataComponentType<BlueprintSelection>> BLUEPRINT_SELECTION = COMPONENTS.registerComponentType(
            "blueprint_selection", builder -> builder.persistent(BlueprintSelection.CODEC).networkSynchronized(BlueprintSelection.STREAM_CODEC));
    /** 画好的蓝图：编号、名字、大小和材料（ADR-0018）。 */
    public static final Supplier<DataComponentType<BlueprintInfo>> BLUEPRINT = COMPONENTS.registerComponentType("blueprint",
            builder -> builder.persistent(BlueprintInfo.CODEC).networkSynchronized(BlueprintInfo.STREAM_CODEC).cacheEncoding());
    /** 蓝图转了几个 90°（ADR-0018）。 */
    public static final Supplier<DataComponentType<Integer>> BLUEPRINT_ROTATION = COMPONENTS.registerComponentType("blueprint_rotation",
            builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    private UniverseDataComponents() {
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }

    private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
        return Codec.STRING.comapFlatMap(name -> {
            try {
                return DataResult.success(Enum.valueOf(type, name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                return DataResult.error(() -> "Unknown " + type.getSimpleName() + ": " + name);
            }
        }, value -> value.name().toLowerCase(Locale.ROOT));
    }
}
