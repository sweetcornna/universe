package io.github.sweetcornna.universe.registry;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.advancement.MilestoneTrigger;
import net.minecraft.advancements.triggers.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 本模组的进度触发器（ADR-0018）。 */
public final class UniverseTriggers {
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, Universe.MOD_ID);

    public static final DeferredHolder<CriterionTrigger<?>, MilestoneTrigger> MILESTONE = TRIGGERS.register("milestone", MilestoneTrigger::new);

    private UniverseTriggers() {
    }

    public static void register(IEventBus modBus) {
        TRIGGERS.register(modBus);
    }
}
