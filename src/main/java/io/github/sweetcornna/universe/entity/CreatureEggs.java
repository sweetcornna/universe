package io.github.sweetcornna.universe.entity;

import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.core.ecology.CreatureSpecies;
import io.github.sweetcornna.universe.core.ecology.CreatureTraits;
import io.github.sweetcornna.universe.core.ecology.GeneEdit;
import io.github.sweetcornna.universe.core.ecology.Temperament;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.Tags;

/**
 * 伙伴下的生物卵（ADR-0026 §九）：卵上记着物种、性状和主人，放在地上孵出幼崽。卵序列仪用材料改卵上的性状，
 * 哪种材料改什么在这里（规则本身在 {@link GeneEdit}）。
 */
public final class CreatureEggs {
    public static final Codec<CreatureTraits> TRAITS_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("size").forGetter(CreatureTraits::size),
            Codec.INT.optionalFieldOf("tint", CreatureTraits.NO_TINT).forGetter(CreatureTraits::tint),
            UniverseDataComponents.enumCodec(Temperament.class).fieldOf("temperament").forGetter(CreatureTraits::temperament)
    ).apply(i, CreatureTraits::new));
    public static final StreamCodec<ByteBuf, CreatureTraits> TRAITS_STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, CreatureTraits::size,
            ByteBufCodecs.INT, CreatureTraits::tint,
            ByteBufCodecs.idMapper(index -> Temperament.values()[index], Temperament::ordinal), CreatureTraits::temperament,
            CreatureTraits::new);

    private CreatureEggs() {
    }

    /**
     * 一个卵。物种表按出生地在服务端查，客户端查不到，所以物种名两种语言都记在卵上。
     *
     * @param home    出生地，见 {@link AlienCreature#homeKey()}
     * @param species 物种在出生地动物表里的序号
     * @param owner   母亲的主人；没有时孵出来归放下卵的人
     */
    public record Egg(String home, int species, CreatureTraits traits, Optional<UUID> owner, String nameZh, String nameEn) {
        public static final Codec<Egg> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("home").forGetter(Egg::home),
                Codec.INT.fieldOf("species").forGetter(Egg::species),
                TRAITS_CODEC.fieldOf("traits").forGetter(Egg::traits),
                UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(Egg::owner),
                Codec.STRING.fieldOf("name_zh").forGetter(Egg::nameZh),
                Codec.STRING.fieldOf("name_en").forGetter(Egg::nameEn)
        ).apply(i, Egg::new));
        public static final StreamCodec<ByteBuf, Egg> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Egg::home,
                ByteBufCodecs.VAR_INT, Egg::species,
                TRAITS_STREAM_CODEC, Egg::traits,
                ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), Egg::owner,
                ByteBufCodecs.STRING_UTF8, Egg::nameZh,
                ByteBufCodecs.STRING_UTF8, Egg::nameEn,
                Egg::new);

        public Egg withTraits(CreatureTraits traits) {
            return new Egg(this.home, this.species, traits, this.owner, this.nameZh, this.nameEn);
        }

        public Egg withOwner(UUID owner) {
            return new Egg(this.home, this.species, this.traits, Optional.of(owner), this.nameZh, this.nameEn);
        }

        /** 物种名，由语言文件挑一种语言显示。 */
        public Component speciesName() {
            return Component.translatable("entity.universe.species", this.nameZh, this.nameEn);
        }
    }

    /** 这只动物下的卵：物种、性状随母亲，主人是母亲的主人。 */
    public static Egg of(AlienCreature mother) {
        CreatureSpecies species = mother.species();
        EntityReference<LivingEntity> owner = mother.getOwnerReference();
        return new Egg(mother.homeKey(), mother.speciesIndex(), mother.traits(), Optional.ofNullable(owner).map(EntityReference::getUUID),
                species.name().zh(), species.name().en());
    }

    public static ItemStack stack(Egg egg) {
        ItemStack stack = new ItemStack(UniverseItems.CREATURE_EGG.get());
        stack.set(UniverseDataComponents.CREATURE_EGG.get(), egg);
        return stack;
    }

    /** 物品上的卵；没有记物种的（命令给的空卵）为 null。 */
    public static @Nullable Egg egg(ItemStack stack) {
        return stack.get(UniverseDataComponents.CREATURE_EGG.get());
    }

    /** 吃饱了、信任够的伙伴下一个卵，掉在它脚边。由伙伴的推进调用。 */
    public static void lay(ServerLevel level, AlienCreature mother) {
        mother.spawnAtLocation(level, stack(of(mother)));
        mother.playSound(SoundEvents.TURTLE_LAY_EGG, 1, 0.9F + mother.getRandom().nextFloat() * 0.2F);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, mother.getX(), mother.getY() + 0.3, mother.getZ(), 6,
                mother.getBbWidth() * 0.4, 0.2, mother.getBbWidth() * 0.4, 0);
    }

    // ---- 卵序列仪的材料

    /**
     * 这份材料对卵做什么：生物胶质变大、骨粉变小、染料换颜色、草食饲料变温顺、生肉变得有领地意识。不是材料的为空。
     */
    public static Optional<GeneEdit> edit(ItemStack material) {
        if (material.isEmpty()) {
            return Optional.empty();
        }
        if (material.is(UniverseItems.BIO_GEL.get())) {
            return Optional.of(GeneEdit.GROW);
        }
        if (material.is(Items.BONE_MEAL)) {
            return Optional.of(GeneEdit.SHRINK);
        }
        DyeColor dye = material.get(DataComponents.DYE);
        if (dye != null) {
            return Optional.of(new GeneEdit.Recolor(dye.getTextureDiffuseColor()));
        }
        if (material.is(UniverseItems.HERBIVORE_FEED.get())) {
            return Optional.of(GeneEdit.CALM);
        }
        if (material.is(Tags.Items.FOODS_RAW_MEAT)) {
            return Optional.of(GeneEdit.ROUSE);
        }
        return Optional.empty();
    }
}
