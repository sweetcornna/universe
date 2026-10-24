package io.github.sweetcornna.universe.mecha.fleet;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.core.mecha.fleet.Squadrons;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

/**
 * 玩家机甲里的编队（ADR-0033 §一）：每个格子里放着的单位物品，被打掉的那格原来是什么（自动补员补这种），每个编队的开关。
 * 只在服务端，存档，死亡不丢。出动时物品不离开格子，单位实体只是它在外面的样子，召回、下线、服务器关掉都不会丢东西。
 */
public final class FleetData {
    public static final MapCodec<FleetData> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("slots", List.of()).forGetter(data -> List.copyOf(data.slots)),
            Codec.STRING.listOf().optionalFieldOf("pending", List.of()).forGetter(data -> List.of(data.pending)),
            Codec.BOOL.listOf().optionalFieldOf("replenish", List.of()).forGetter(data -> bools(data.replenish)),
            Codec.BOOL.listOf().optionalFieldOf("buildings", List.of()).forGetter(data -> bools(data.buildings)),
            Codec.BOOL.listOf().optionalFieldOf("auto_deploy", List.of()).forGetter(data -> bools(data.autoDeploy))
    ).apply(i, FleetData::new));

    private final NonNullList<ItemStack> slots = NonNullList.withSize(Squadrons.TOTAL_SLOTS, ItemStack.EMPTY);
    /** 被打掉的那架是什么（物品 ID）；空的是不用补。 */
    private final String[] pending = new String[Squadrons.TOTAL_SLOTS];
    private final boolean[] replenish = new boolean[Squadrons.COUNT];
    private final boolean[] buildings = new boolean[Squadrons.COUNT];
    private final boolean[] autoDeploy = new boolean[Squadrons.COUNT];

    public FleetData() {
        this(List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private FleetData(List<ItemStack> slots, List<String> pending, List<Boolean> replenish, List<Boolean> buildings, List<Boolean> autoDeploy) {
        for (int i = 0; i < Squadrons.TOTAL_SLOTS; i++) {
            this.slots.set(i, i < slots.size() ? slots.get(i) : ItemStack.EMPTY);
            this.pending[i] = i < pending.size() ? pending.get(i) : "";
        }
        for (int i = 0; i < Squadrons.COUNT; i++) {
            // 自动补员默认开着，另外两个默认关着
            this.replenish[i] = i >= replenish.size() || replenish.get(i);
            this.buildings[i] = i < buildings.size() && buildings.get(i);
            this.autoDeploy[i] = i < autoDeploy.size() && autoDeploy.get(i);
        }
    }

    private static List<Boolean> bools(boolean[] values) {
        List<Boolean> list = new ArrayList<>(values.length);
        for (boolean value : values) {
            list.add(value);
        }
        return list;
    }

    /** 所有格子，按 {@link Squadrons#flatIndex} 排。 */
    public NonNullList<ItemStack> slots() {
        return this.slots;
    }

    public ItemStack slot(int squadron, int slot) {
        return this.slots.get(Squadrons.flatIndex(squadron, slot));
    }

    public void setSlot(int squadron, int slot, ItemStack stack) {
        this.slots.set(Squadrons.flatIndex(squadron, slot), stack);
    }

    public String pending(int index) {
        return this.pending[index];
    }

    public void setPending(int index, String item) {
        this.pending[index] = item == null ? "" : item;
    }

    /** 编队里有几架。 */
    public int count(int squadron) {
        int count = 0;
        for (int slot = 0; slot < Squadrons.slots(squadron); slot++) {
            if (!this.slot(squadron, slot).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    public boolean replenish(int squadron) {
        return this.replenish[squadron];
    }

    public void setReplenish(int squadron, boolean on) {
        this.replenish[squadron] = on;
    }

    /** 开着"攻击黑雾建筑"：关着只打单位。 */
    public boolean buildings(int squadron) {
        return this.buildings[squadron];
    }

    public void setBuildings(int squadron, boolean on) {
        this.buildings[squadron] = on;
    }

    /** 开着"受到攻击时自动出动"。 */
    public boolean autoDeploy(int squadron) {
        return this.autoDeploy[squadron];
    }

    public void setAutoDeploy(int squadron, boolean on) {
        this.autoDeploy[squadron] = on;
    }
}
