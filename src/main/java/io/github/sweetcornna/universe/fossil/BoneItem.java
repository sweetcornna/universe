package io.github.sweetcornna.universe.fossil;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import io.github.sweetcornna.universe.core.fossil.Bone;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 一块骨头（ADR-0034 §三）：哪个类群的哪个部位。放在化石底座上、骨架展台对应的格里，或者卖掉、拿去收藏家那里换。
 * 名字下面写类群和稀有度、放在骨架的哪一格。
 */
public class BoneItem extends Item {
    private final Bone bone;

    public BoneItem(Bone bone, Properties properties) {
        super(properties);
        this.bone = bone;
    }

    public Bone bone() {
        return this.bone;
    }

    /** 在物品的注册表里登记 40 种骨头（{@code bone_<类群>_<部位>}），按类群、部位排；稀有的类群名字带颜色。 */
    public static Map<Bone, DeferredItem<BoneItem>> register(DeferredRegister.Items items) {
        Map<Bone, DeferredItem<BoneItem>> bones = new LinkedHashMap<>();
        for (Bone bone : Bone.all()) {
            Rarity rarity = switch (bone.family().rarity()) {
                case COMMON -> Rarity.COMMON;
                case UNCOMMON -> Rarity.UNCOMMON;
                case RARE -> Rarity.RARE;
                case VERY_RARE -> Rarity.EPIC;
            };
            bones.put(bone, items.registerItem(bone.id(), properties -> new BoneItem(bone, properties), properties -> properties.rarity(rarity)));
        }
        return bones;
    }

    /** 这件东西是骨头就返回是哪块，不是返回 null。 */
    public static Bone boneOf(ItemStack stack) {
        return stack.getItem() instanceof BoneItem item ? item.bone : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.universe.bone.family", FossilNames.family(this.bone.family()),
                FossilNames.rarity(this.bone.family().rarity())).withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.universe.bone.slot", FossilNames.part(this.bone)).withStyle(ChatFormatting.DARK_GRAY));
    }
}
