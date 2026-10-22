package io.github.sweetcornna.universe.survival;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.galaxy.Hazard;
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
 * 给外骨骼补给的东西：氧气罐、防护电池各补一半；采来的氧气、钠补得少（ADR-0026），防护凝胶只补一条防护。
 * 右键用掉一个；数值低时 {@link PlayerSurvival} 也会自动从背包里取用，补得少的先用。
 */
public class SuitRefillItem extends Item {
    /** 氧气罐、防护电池一个补多少（满值 100）。 */
    public static final float AMOUNT = 50;

    public enum Kind {
        OXYGEN,
        HAZARD
    }

    private final Kind kind;
    private final float amount;
    /** 防护凝胶只补的那一条；为空时每条都补。 */
    private final @Nullable Hazard only;

    public SuitRefillItem(Kind kind, Properties properties) {
        this(kind, AMOUNT, null, properties);
    }

    public SuitRefillItem(Kind kind, float amount, @Nullable Hazard only, Properties properties) {
        super(properties);
        this.kind = kind;
        this.amount = amount;
        this.only = only;
    }

    public Kind kind() {
        return this.kind;
    }

    public float amount() {
        return this.amount;
    }

    public @Nullable Hazard only() {
        return this.only;
    }

    /** 能不能补上 {@code hazard} 这一条。 */
    public boolean covers(Hazard hazard) {
        return this.kind == Kind.HAZARD && (this.only == null || this.only == hazard);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        // 客户端只有同步过来的读数，服务端才有真正的数值
        if (level.isClientSide()) {
            return player.getData(UniverseAttachments.SUIT_HUD).full(this.kind) ? InteractionResult.FAIL : InteractionResult.SUCCESS;
        }
        LifeSupport support = player.getData(UniverseAttachments.LIFE_SUPPORT);
        if (this.full(support)) {
            return InteractionResult.FAIL;
        }
        apply(player, player.getItemInHand(hand), support);
        return InteractionResult.SUCCESS;
    }

    private boolean full(LifeSupport support) {
        if (this.kind == Kind.OXYGEN) {
            return support.oxygen() >= LifeSupport.OXYGEN_MAX;
        }
        if (this.only != null) {
            return support.protection(this.only) >= LifeSupport.HAZARD_MAX;
        }
        return support.protection().values().stream().allMatch(value -> value >= LifeSupport.HAZARD_MAX);
    }

    /** 用掉一个：补数值、播声音、在动作栏提示。 */
    static void apply(Player player, ItemStack stack, LifeSupport support) {
        if (!(stack.getItem() instanceof SuitRefillItem item)) {
            return;
        }
        Component name = stack.getHoverName();
        String amount = String.format(Locale.ROOT, "%.0f", item.amount);
        Component message;
        if (item.kind == Kind.OXYGEN) {
            support.addOxygen(item.amount);
            message = Component.translatable("message.universe.suit.oxygen_used", name, amount);
        } else if (item.only == null) {
            support.rechargeHazards(item.amount);
            message = Component.translatable("message.universe.suit.cell_used", name, amount);
        } else {
            support.rechargeHazard(item.only, item.amount);
            message = Component.translatable("message.universe.suit.gel_used", name,
                    Component.translatable("hazard.universe." + item.only.name().toLowerCase(Locale.ROOT)), amount);
        }
        stack.consume(1, player);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                item.kind == Kind.OXYGEN ? SoundEvents.BOTTLE_EMPTY : SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F, 1.4F);
        player.sendOverlayMessage(message);
    }
}
