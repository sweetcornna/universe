package io.github.sweetcornna.universe.registry;

import java.util.Map;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** 挂在实体、世界上的附加数据。 */
public final class UniverseAttachments {
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

    private UniverseAttachments() {
    }

    public static void register(IEventBus modBus) {
        ATTACHMENT_TYPES.register(modBus);
    }
}
