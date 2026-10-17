package io.github.sweetcornna.universe.registry;

import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.galaxy.Hazard;
import io.github.sweetcornna.universe.core.survival.LifeSupport;
import io.github.sweetcornna.universe.survival.SuitHud;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** 挂在实体、世界上的附加数据。 */
public final class UniverseAttachments {
    private static final Codec<Hazard> HAZARD_CODEC = Codec.STRING.xmap(
            name -> Hazard.valueOf(name.toUpperCase(Locale.ROOT)), hazard -> hazard.name().toLowerCase(Locale.ROOT));
    private static final MapCodec<LifeSupport> LIFE_SUPPORT_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.FLOAT.fieldOf("oxygen").forGetter(LifeSupport::oxygen),
            Codec.unboundedMap(HAZARD_CODEC, Codec.FLOAT).fieldOf("protection").forGetter(LifeSupport::protection),
            Codec.FLOAT.fieldOf("jet").forGetter(LifeSupport::jet)
    ).apply(i, LifeSupport::new));
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Universe.MOD_ID);

    /**
     * 玩家在每颗星球上最后一次驾船起飞的位置（维度 ID → 打包坐标）。记在玩家身上，停船后重新组装也不会忘，
     * 飞回来时降落在起飞点附近。
     */
    public static final Supplier<AttachmentType<Map<Identifier, Long>>> DEPARTURES = ATTACHMENT_TYPES.register("departures",
            () -> AttachmentType.builder(() -> Map.<Identifier, Long>of())
                    .serialize(Codec.unboundedMap(Identifier.CODEC, Codec.LONG).fieldOf("departures"))
                    .copyOnDeath()
                    .build());

    /** 外骨骼的氧气、危害防护、喷气背包能量（只在服务端，存档；死亡后重置为满）。 */
    public static final Supplier<AttachmentType<LifeSupport>> LIFE_SUPPORT = ATTACHMENT_TYPES.register("life_support",
            () -> AttachmentType.builder(() -> new LifeSupport()).serialize(LIFE_SUPPORT_CODEC).build());
    /** 发给玩家自己的外骨骼读数，HUD 用（不存档）。 */
    public static final Supplier<AttachmentType<SuitHud>> SUIT_HUD = ATTACHMENT_TYPES.register("suit_hud",
            () -> AttachmentType.builder(() -> SuitHud.FULL).sync((holder, player) -> holder == player, SuitHud.STREAM_CODEC).build());

    private UniverseAttachments() {
    }

    public static void register(IEventBus modBus) {
        ATTACHMENT_TYPES.register(modBus);
    }
}
