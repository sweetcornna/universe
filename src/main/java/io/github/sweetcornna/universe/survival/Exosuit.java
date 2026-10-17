package io.github.sweetcornna.universe.survival;

import java.util.Map;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.survival.Suit;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * 外骨骼（玩法设计 §3.1）：四件套盔甲，护甲值和铁甲差不多、稍微耐用一点。生命维持和危害防护的效果不在盔甲上，
 * 由 {@link PlayerSurvival} 按穿了哪几件结算。外观在 {@code assets/universe/equipment/exosuit.json}。
 */
public final class Exosuit {
    public static final ResourceKey<EquipmentAsset> ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, Universe.id("exosuit"));
    public static final ArmorMaterial MATERIAL = new ArmorMaterial(20,
            Map.of(ArmorType.BOOTS, 2, ArmorType.LEGGINGS, 5, ArmorType.CHESTPLATE, 6, ArmorType.HELMET, 2, ArmorType.BODY, 5),
            12, SoundEvents.ARMOR_EQUIP_IRON, 1.0F, 0.0F, ItemTags.REPAIRS_IRON_ARMOR, ASSET);

    private Exosuit() {
    }

    public static Suit of(Player player) {
        return new Suit(
                player.getItemBySlot(EquipmentSlot.HEAD).is(UniverseItems.EXOSUIT_HELMET),
                player.getItemBySlot(EquipmentSlot.CHEST).is(UniverseItems.EXOSUIT_CHESTPLATE),
                player.getItemBySlot(EquipmentSlot.LEGS).is(UniverseItems.EXOSUIT_LEGGINGS),
                player.getItemBySlot(EquipmentSlot.FEET).is(UniverseItems.EXOSUIT_BOOTS));
    }
}
