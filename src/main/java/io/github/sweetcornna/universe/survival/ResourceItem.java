package io.github.sweetcornna.universe.survival;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.block.ResourcePlanting;
import io.github.sweetcornna.universe.core.ecology.ResourcePlant;
import io.github.sweetcornna.universe.core.galaxy.Hazard;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * 资源植物的产物（ADR-0026）：右键地面种回去。氧气和钠还能直接给外骨骼补给，见 {@link Refill}。
 */
public class ResourceItem extends Item implements Plantable {
    private final String product;

    public ResourceItem(String product, Properties properties) {
        super(properties);
        this.product = product;
    }

    @Override
    public Optional<ResourcePlant> plant() {
        return ResourcePlant.fromProduct(this.product);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return this.plant().map(plant -> ResourcePlanting.plant(context, plant)).orElse(InteractionResult.PASS);
    }

    /** 氧气、钠：能种，也能右键补外骨骼。 */
    public static class Refill extends SuitRefillItem implements Plantable {
        private final String product;

        public Refill(String product, Kind kind, float amount, @Nullable Hazard only, Item.Properties properties) {
            super(kind, amount, only, properties);
            this.product = product;
        }

        @Override
        public Optional<ResourcePlant> plant() {
            return ResourcePlant.fromProduct(this.product);
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            return this.plant().map(plant -> ResourcePlanting.plant(context, plant)).orElse(InteractionResult.PASS);
        }
    }
}
