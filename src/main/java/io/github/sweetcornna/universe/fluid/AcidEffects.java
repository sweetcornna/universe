package io.github.sweetcornna.universe.fluid;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.survival.Acid;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.survival.SurvivalDamage;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.fluids.FluidInteractionRegistry;

/**
 * 酸液对东西的作用（ADR-0030 §一，数值在 {@link Acid}）：掉进去的物品慢慢蚀掉（玻璃、金、塑料这些耐酸的不蚀），
 * 别的生物泡着就灼伤。玩家走生命维持（{@code PlayerSurvival}），外骨骼的毒性防护先挡着。只在服务端。
 *
 * <p>岩浆碰到酸液：岩浆源变黑曜石，流动的岩浆变星球岩石（像水一样，只是凝出来的是本地的岩石）。
 */
public final class AcidEffects {
    /** 不怕酸的物品。 */
    public static final TagKey<Item> RESISTANT = TagKey.create(Registries.ITEM, Universe.id("acid_resistant"));
    /** 物品在酸里泡了多少刻，存在实体自己的数据里。 */
    private static final String SOAKED = "universe:acid_soaked";

    private AcidEffects() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        gameBus.addListener(EntityTickEvent.Post.class, event -> tick(event.getEntity()));
        modBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(() -> FluidInteractionRegistry.addInteraction(
                net.neoforged.neoforge.common.NeoForgeMod.LAVA_TYPE.value(),
                new FluidInteractionRegistry.InteractionInformation(AcidFluid.type(), state -> state.isSource()
                        ? Blocks.OBSIDIAN.defaultBlockState() : UniverseBlocks.PLANET_ROCK.get().defaultBlockState()))));
    }

    private static void tick(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level) || entity.getFluidTypeHeight(AcidFluid.type()) <= 0) {
            return;
        }
        if (entity instanceof ItemEntity item) {
            corrode(level, item);
        } else if (entity instanceof LivingEntity living && !(living instanceof Player) && !living.isPassenger()
                && living.tickCount % Acid.BURN_INTERVAL == 0) {
            living.hurtServer(level, level.damageSources().source(SurvivalDamage.ACID), Acid.BURN_DAMAGE);
        }
    }

    private static void corrode(ServerLevel level, ItemEntity item) {
        ItemStack stack = item.getItem();
        if (stack.isEmpty() || stack.is(RESISTANT)) {
            return;
        }
        CompoundTag data = item.getPersistentData();
        int soaked = data.getIntOr(SOAKED, 0) + 1;
        data.putInt(SOAKED, soaked);
        int lost = Acid.corroded(stack.getCount(), soaked);
        if (lost <= 0) {
            if (soaked % 10 == 0) {
                level.sendParticles(ParticleTypes.BUBBLE_POP, item.getX(), item.getY() + 0.2, item.getZ(), 2, 0.1, 0.05, 0.1, 0);
            }
            return;
        }
        level.sendParticles(ParticleTypes.SMOKE, item.getX(), item.getY() + 0.25, item.getZ(), 4, 0.12, 0.05, 0.12, 0.01);
        level.playSound(null, item.getX(), item.getY(), item.getZ(), SoundEvents.LAVA_EXTINGUISH, SoundSource.NEUTRAL, 0.2F,
                1.7F + level.getRandom().nextFloat() * 0.3F);
        if (lost >= stack.getCount()) {
            item.discard();
        } else {
            ItemStack rest = stack.copy();
            rest.shrink(lost);
            item.setItem(rest);
        }
    }
}
