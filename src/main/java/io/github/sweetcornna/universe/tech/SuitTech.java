package io.github.sweetcornna.universe.tech;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.survival.Shield;
import io.github.sweetcornna.universe.core.tech.Stat;
import io.github.sweetcornna.universe.core.tech.TechBonuses;
import io.github.sweetcornna.universe.core.tech.TechModule;
import io.github.sweetcornna.universe.mecha.combat.MechaCombat;
import io.github.sweetcornna.universe.network.OpenTechPayload;
import io.github.sweetcornna.universe.perf.ServerPerf;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.survival.MultitoolItem;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.registry.sound.GearSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.TriState;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 外骨骼和多功能工具的模块在服务端的效果（ADR-0014）：动力伺服的移速、个人护盾、货舱（背包满了自动收进货舱，
 * 死亡时掉出来），以及打开技术配置界面。生命维持、危害、喷气背包、采矿激光、扫描的加成在各自的代码里读
 * {@link #suit}、{@link #tool}。
 */
public final class SuitTech {
    private static final Identifier SERVO = Universe.id("servo");

    private SuitTech() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(PlayerTickEvent.Post.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                long start = ServerPerf.begin();
                tick(player);
                ServerPerf.end("player/suit", start);
            }
        });
        gameBus.addListener(EventPriority.HIGH, LivingIncomingDamageEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                shield(player, event);
            }
        });
        gameBus.addListener(ItemEntityPickupEvent.Pre.class, event -> {
            if (event.getPlayer() instanceof ServerPlayer player) {
                overflow(player, event);
            }
        });
        // 优先级高一点：墓碑之类的模组在默认优先级收掉落物时，货舱的东西已经在里面了
        gameBus.addListener(EventPriority.HIGH, LivingDropsEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                dropCargo(player, event.getDrops());
            }
        });
    }

    /** 外骨骼模块的加成；没穿胸甲时没有效果。 */
    public static TechBonuses suit(Player player) {
        if (!player.getItemBySlot(EquipmentSlot.CHEST).is(UniverseItems.EXOSUIT_CHESTPLATE)) {
            return TechBonuses.NONE;
        }
        return player.getData(UniverseAttachments.EXOSUIT).bonuses();
    }

    /**
     * 多功能工具的加成：装着的模块加上工具类型的专长（ADR-0026）。槽里的武器（{@link ToolWeaponItem}）不算模块。
     * 不是多功能工具时没有加成。
     */
    public static TechBonuses tool(ItemStack tool) {
        if (!(tool.getItem() instanceof MultitoolItem)) {
            return TechBonuses.NONE;
        }
        ItemContainerContents contents = tool.getOrDefault(UniverseDataComponents.TOOL_MODULES.get(), ItemContainerContents.EMPTY);
        List<TechModule> modules = new ArrayList<>();
        contents.nonEmptyItemCopyStream().forEach(stack -> {
            if (stack.getItem() instanceof TechModuleItem item) {
                modules.add(item.module(stack));
            }
        });
        return TechBonuses.of(modules).with(MultitoolItem.spec(tool).specialty());
    }

    /** 客户端按了技术配置键。 */
    public static void handleOpen(OpenTechPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.isAlive()) {
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> TechMenu.server(id, inventory, player),
                    Component.translatable("gui.universe.tech.title")), buf -> TechMenu.writeOpenData(player, buf));
        }
    }

    private static void tick(ServerPlayer player) {
        TechBonuses bonuses = suit(player);
        // 护盾：模块那部分自己回充，机甲核心再用电补（ADR-0033 §二）
        MechaCombat.tickShield(player);
        if (player.tickCount % 10 == 0) {
            AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
            double amount = bonuses.get(Stat.SPEED);
            if (speed != null) {
                AttributeModifier current = speed.getModifier(SERVO);
                if (current == null || Math.abs(current.amount() - amount) > 1e-6) {
                    speed.removeModifier(SERVO);
                    if (amount > 0) {
                        speed.addTransientModifier(new AttributeModifier(SERVO, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
                    }
                }
            }
        }
    }

    private static void shield(ServerPlayer player, LivingIncomingDamageEvent event) {
        event.setAmount(absorb(player, event.getSource(), event.getAmount()));
    }

    /**
     * 护盾挡一次伤害（GameTest 也直接调）。只挡战斗伤害：来自生物、投射物、爆炸；缺氧、危害、摔落、饥饿不挡。
     *
     * @return 挡不住、还要落到玩家身上的部分
     */
    public static float absorb(ServerPlayer player, DamageSource source, float amount) {
        boolean combat = source.getEntity() != null || source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypeTags.IS_EXPLOSION);
        if (!combat || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return amount;
        }
        Shield shield = player.getData(UniverseAttachments.EXOSUIT).shield();
        if (shield.capacity() <= 0) {
            return amount;
        }
        boolean had = shield.value() > 0;
        float left = shield.absorb(amount);
        ServerLevel level = player.level();
        if (had) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    shield.value() > 0 ? GearSounds.SHIELD_HIT : GearSounds.SHIELD_BREAK, SoundSource.PLAYERS, 0.8F,
                    0.95F + player.getRandom().nextFloat() * 0.1F);
        }
        return left;
    }

    /** 背包放不下这件东西时收进货舱。 */
    private static void overflow(ServerPlayer player, ItemEntityPickupEvent.Pre event) {
        ItemEntity entity = event.getItemEntity();
        ItemStack stack = entity.getItem();
        Inventory inventory = player.getInventory();
        if (entity.hasPickUpDelay() || stack.isEmpty() || inventory.getFreeSlot() >= 0 || inventory.getSlotWithRemainingSpace(stack) >= 0) {
            return;
        }
        ExosuitData data = player.getData(UniverseAttachments.EXOSUIT);
        int before = stack.getCount();
        for (int i = 0; i < data.cargoSlots() && !stack.isEmpty(); i++) {
            ItemStack slot = data.cargo().get(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack) && slot.getCount() < slot.getMaxStackSize()) {
                int moved = Math.min(stack.getCount(), slot.getMaxStackSize() - slot.getCount());
                slot.grow(moved);
                stack.shrink(moved);
            }
        }
        for (int i = 0; i < data.cargoSlots() && !stack.isEmpty(); i++) {
            if (data.cargo().get(i).isEmpty()) {
                data.cargo().set(i, stack.split(stack.getMaxStackSize()));
            }
        }
        int taken = before - stack.getCount();
        if (taken <= 0) {
            return;
        }
        player.take(entity, taken);
        player.awardStat(Stats.ITEM_PICKED_UP.get(stack.getItem()), taken);
        player.sendOverlayMessage(Component.translatable("hud.universe.cargo.stored", stack.getHoverName(), taken));
        if (stack.isEmpty()) {
            entity.discard();
        } else {
            entity.setItem(stack);
        }
        event.setCanPickup(TriState.FALSE);
    }

    /** 货舱的东西和背包一样撒在死亡的地方，算进死亡掉落物（开了死亡不掉落时保留）。 */
    private static void dropCargo(ServerPlayer player, Collection<ItemEntity> drops) {
        if (player.level().getGameRules().get(GameRules.KEEP_INVENTORY) || player.isSpectator()) {
            return;
        }
        ExosuitData data = player.getData(UniverseAttachments.EXOSUIT);
        for (int i = 0; i < data.cargo().size(); i++) {
            ItemStack stack = data.cargo().get(i);
            if (!stack.isEmpty()) {
                ItemEntity drop = player.createItemStackToDrop(stack, true, false);
                if (drop != null) {
                    drops.add(drop);
                }
                data.cargo().set(i, ItemStack.EMPTY);
            }
        }
    }
}
