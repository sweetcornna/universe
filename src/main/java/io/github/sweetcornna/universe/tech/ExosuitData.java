package io.github.sweetcornna.universe.tech;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.core.survival.Shield;
import io.github.sweetcornna.universe.core.tech.SuitSlots;
import io.github.sweetcornna.universe.core.tech.TechBonuses;
import io.github.sweetcornna.universe.core.tech.TechModule;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

/**
 * 玩家外骨骼上的东西（ADR-0014）：修过几个掉落舱（决定技术槽和货舱的格数）、技术槽里的模块、货舱里的物品，
 * 以及个人护盾的当前值（不存档）。挂在玩家身上，死亡后保留（货舱的东西由 {@link SuitTech} 在死亡时掉出来）。
 */
public final class ExosuitData {
    public static final MapCodec<ExosuitData> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("pods", 0).forGetter(ExosuitData::pods),
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("modules", List.of()).forGetter(data -> data.modules),
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("cargo", List.of()).forGetter(data -> data.cargo)
    ).apply(i, ExosuitData::new));

    private int pods;
    private final NonNullList<ItemStack> modules = NonNullList.withSize(SuitSlots.TECH_MAX, ItemStack.EMPTY);
    private final NonNullList<ItemStack> cargo = NonNullList.withSize(SuitSlots.CARGO_MAX, ItemStack.EMPTY);
    private final Shield shield = new Shield();
    private TechBonuses bonuses;

    public ExosuitData() {
    }

    private ExosuitData(int pods, List<ItemStack> modules, List<ItemStack> cargo) {
        this.pods = Math.max(0, pods);
        for (int i = 0; i < Math.min(modules.size(), this.modules.size()); i++) {
            this.modules.set(i, modules.get(i));
        }
        for (int i = 0; i < Math.min(cargo.size(), this.cargo.size()); i++) {
            this.cargo.set(i, cargo.get(i));
        }
    }

    public int pods() {
        return this.pods;
    }

    public int techSlots() {
        return SuitSlots.tech(this.pods);
    }

    public int cargoSlots() {
        return SuitSlots.cargo(this.pods);
    }

    /** 修好一个掉落舱。 */
    public void addPod() {
        this.pods++;
    }

    /** 技术槽（全部 12 格，超出已解锁格数的不生效）。 */
    public NonNullList<ItemStack> modules() {
        return this.modules;
    }

    public NonNullList<ItemStack> cargo() {
        return this.cargo;
    }

    public Shield shield() {
        return this.shield;
    }

    /** 已解锁的技术槽里装着的模块合起来的加成（缓存，模块变了要调 {@link #changed}）。 */
    public TechBonuses bonuses() {
        if (this.bonuses == null) {
            List<TechModule> list = new ArrayList<>();
            for (int i = 0; i < this.techSlots(); i++) {
                ItemStack stack = this.modules.get(i);
                if (stack.getItem() instanceof TechModuleItem item) {
                    list.add(item.module(stack));
                }
            }
            this.bonuses = TechBonuses.of(list);
        }
        return this.bonuses;
    }

    public void changed() {
        this.bonuses = null;
    }
}
