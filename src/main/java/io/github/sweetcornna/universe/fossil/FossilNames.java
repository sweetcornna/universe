package io.github.sweetcornna.universe.fossil;

import io.github.sweetcornna.universe.core.fossil.Bone;
import io.github.sweetcornna.universe.core.fossil.BoneFamily;
import io.github.sweetcornna.universe.core.fossil.BonePart;
import io.github.sweetcornna.universe.core.fossil.Skeleton;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 化石的各种名字（ADR-0034 §三），物品、展台、界面都从这里拿。语言键：
 * <ul>
 *   <li>{@code fossil.universe.family.<类群>}：类群名（巨兽）。</li>
 *   <li>{@code fossil.universe.family.<类群>.prefix}、{@code .noun}：骨架名字里的修饰（长颈）和名词（掠食兽）。</li>
 *   <li>{@code fossil.universe.part.<部位>}：部位名，巨蠕的四肢另有 {@code fossil.universe.part.limbs.worm}（体节环）。</li>
 *   <li>{@code fossil.universe.skeleton_name.<修饰个数>}：拼名字的格式（中文不加空格，英文加）。</li>
 * </ul>
 */
public final class FossilNames {
    private FossilNames() {
    }

    public static MutableComponent family(BoneFamily family) {
        return Component.translatable("fossil.universe.family." + family.id());
    }

    public static MutableComponent part(BonePart part) {
        return Component.translatable("fossil.universe.part." + part.id());
    }

    /** 这块骨头的部位叫什么：巨蠕的四肢叫体节环。 */
    public static MutableComponent part(Bone bone) {
        return bone.family() == BoneFamily.WORM && bone.part() == BonePart.LIMBS ? Component.translatable("fossil.universe.part.limbs.worm")
                : part(bone.part());
    }

    public static MutableComponent rarity(BoneFamily.Rarity rarity) {
        ChatFormatting color = switch (rarity) {
            case COMMON -> ChatFormatting.GRAY;
            case UNCOMMON -> ChatFormatting.GREEN;
            case RARE -> ChatFormatting.AQUA;
            case VERY_RARE -> ChatFormatting.LIGHT_PURPLE;
        };
        return Component.translatable("fossil.universe.rarity." + rarity.id()).withStyle(color);
    }

    /** 完整骨架的名字（长颈掠食兽）；不完整的写"残缺的骨架"。 */
    public static MutableComponent skeleton(Skeleton skeleton) {
        Skeleton.Name name = skeleton.name();
        if (name == null) {
            return Component.translatable("fossil.universe.skeleton_name.partial", skeleton.count(), BonePart.values().length);
        }
        Object[] args = new Object[name.modifiers().size() + 1];
        for (int i = 0; i < name.modifiers().size(); i++) {
            args[i] = Component.translatable("fossil.universe.family." + name.modifiers().get(i).id() + ".prefix");
        }
        args[args.length - 1] = Component.translatable("fossil.universe.family." + name.noun().id() + ".noun");
        return Component.translatable("fossil.universe.skeleton_name." + name.modifiers().size(), args);
    }
}
