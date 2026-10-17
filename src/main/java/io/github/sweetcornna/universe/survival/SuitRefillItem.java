package io.github.sweetcornna.universe.survival;

import io.github.sweetcornna.universe.core.survival.LifeSupport;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 氧气罐、防护电池：右键用掉一个补一半；数值低时 {@link PlayerSurvival} 也会自动从背包里取用。
 */
public final class SuitRefillItem extends Item {
    /** 一个补多少（满值 100）。 */
    public static final float AMOUNT = 50;

    public enum Kind {
        OXYGEN,
        HAZARD
    }

    private final Kind kind;

    public SuitRefillItem(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return this.kind;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        // 客户端只有同步过来的读数，服务端才有真正的数值
        if (level.isClientSide()) {
            return player.getData(UniverseAttachments.SUIT_HUD).full(this.kind) ? InteractionResult.FAIL : InteractionResult.SUCCESS;
        }
        LifeSupport support = player.getData(UniverseAttachments.LIFE_SUPPORT);
        boolean full = this.kind == Kind.OXYGEN
                ? support.oxygen() >= LifeSupport.OXYGEN_MAX
                : support.protection().values().stream().allMatch(value -> value >= LifeSupport.HAZARD_MAX);
        if (full) {
            return InteractionResult.FAIL;
        }
        apply(player, player.getItemInHand(hand), this.kind, support);
        return InteractionResult.SUCCESS;
    }

    /** 用掉一个：补数值、播声音、在动作栏提示。 */
    static void apply(Player player, ItemStack stack, Kind kind, LifeSupport support) {
        if (kind == Kind.OXYGEN) {
            support.addOxygen(AMOUNT);
        } else {
            support.rechargeHazards(AMOUNT);
        }
        stack.consume(1, player);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                kind == Kind.OXYGEN ? SoundEvents.BOTTLE_EMPTY : SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F, 1.4F);
        player.sendOverlayMessage(Component.translatable(kind == Kind.OXYGEN
                ? "message.universe.suit.oxygen_used" : "message.universe.suit.cell_used"));
    }
}
