package io.github.sweetcornna.universe.client.tech;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import io.github.sweetcornna.universe.core.survival.MultitoolClass;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.survival.MultitoolItem;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 物品模型按多功能工具的类型选贴图（ADR-0026）：{@code "property": "universe:multitool_class"}，值是类型的小写名字。
 * 没有类型组件的旧工具算手枪。
 */
public record MultitoolClassProperty() implements SelectItemModelProperty<MultitoolClass> {
    public static final Codec<MultitoolClass> VALUE_CODEC = UniverseDataComponents.enumCodec(MultitoolClass.class);
    public static final SelectItemModelProperty.Type<MultitoolClassProperty, MultitoolClass> TYPE = SelectItemModelProperty.Type.create(
            MapCodec.unit(new MultitoolClassProperty()), VALUE_CODEC);

    @Override
    public @Nullable MultitoolClass get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed,
            ItemDisplayContext displayContext) {
        return MultitoolItem.spec(stack).type();
    }

    @Override
    public SelectItemModelProperty.Type<MultitoolClassProperty, MultitoolClass> type() {
        return TYPE;
    }

    @Override
    public Codec<MultitoolClass> valueCodec() {
        return VALUE_CODEC;
    }
}
