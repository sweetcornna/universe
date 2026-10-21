package io.github.sweetcornna.universe.compat.jade;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.dyson.RayReceiver;
import io.github.sweetcornna.universe.core.research.Upgrades;
import io.github.sweetcornna.universe.dyson.RayReceiverBlockEntity;
import io.github.sweetcornna.universe.core.factory.Proliferation;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.belt.SprayCoaterBlockEntity;
import io.github.sweetcornna.universe.factory.belt.TrafficMonitorBlockEntity;
import io.github.sweetcornna.universe.factory.RecipeMachineBlockEntity;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipe;
import io.github.sweetcornna.universe.factory.research.Research;
import io.github.sweetcornna.universe.logistics.StationBlockEntity;
import io.github.sweetcornna.universe.logistics.StationContents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.crafting.RecipeHolder;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * Jade 的服务端数据（ADR-0019）：设备界面同步的那组数，外加客户端算不了的几样（在做的东西、射线接收站算上升级的效率、
 * 物流站每格的物品）。客户端 {@link MachineTooltip} 只管排版。
 */
enum MachineServerData implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    static final Identifier UID = Universe.id("machine");
    static final String DATA = "UniverseData";
    static final String MAKING = "UniverseMaking";
    static final String EFFICIENCY = "UniverseEfficiency";
    static final String STATION = "UniverseStation";
    /** 喷涂机：装的是哪档增产剂（没有是 0）、还能喷几个；流速监测器：最近一分钟的个数、下限、报警（ADR-0022）。 */
    static final String COATER = "UniverseCoater";
    static final String FLOW = "UniverseFlow";

    @Override
    public Identifier getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof SprayCoaterBlockEntity coater) {
            Proliferation.Tier tier = coater.tier();
            tag.putIntArray(COATER, new int[] {tier == null ? 0 : tier.ordinal() + 1, coater.remaining()});
            return;
        }
        if (accessor.getBlockEntity() instanceof TrafficMonitorBlockEntity monitor) {
            tag.putIntArray(FLOW, new int[] {monitor.perMinute(), monitor.threshold(), monitor.alarm() ? 1 : 0, monitor.measured() ? 1 : 0});
            return;
        }
        if (!(accessor.getBlockEntity() instanceof MachineBlockEntity machine)) {
            return;
        }
        ContainerData data = machine.data();
        int[] values = new int[data.getCount()];
        for (int i = 0; i < values.length; i++) {
            values[i] = data.get(i);
        }
        tag.putIntArray(DATA, values);
        if (machine instanceof RecipeMachineBlockEntity recipeMachine) {
            RecipeHolder<MachineRecipe> recipe = recipeMachine.recipe();
            if (recipe != null && !recipe.value().results().isEmpty()) {
                tag.putString(MAKING, BuiltInRegistries.ITEM.getKey(recipe.value().results().getFirst().create().getItem()).toString());
            }
        } else if (machine instanceof RayReceiverBlockEntity) {
            double continuity = values[RayReceiverBlockEntity.CONTINUITY] / 1000.0;
            double loss = Upgrades.rayLoss(Research.level(accessor.getLevel(), Upgrades.RAY_EFFICIENCY));
            tag.putInt(EFFICIENCY, (int) Math.round(RayReceiver.efficiency(continuity, loss) * 100));
        } else if (machine instanceof StationBlockEntity station && station.station() != null) {
            tag.store(STATION, StationContents.CODEC, StationContents.of(station.station()));
        }
    }
}
