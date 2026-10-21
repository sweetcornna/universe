package io.github.sweetcornna.universe.compat.jade;

import java.util.Locale;

import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.core.factory.Proliferation;
import io.github.sweetcornna.universe.core.logistics.Logic;
import io.github.sweetcornna.universe.dyson.RayReceiverBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.factory.Proliferators;
import io.github.sweetcornna.universe.factory.RecipeMachineBlockEntity;
import io.github.sweetcornna.universe.factory.power.AccumulatorBlockEntity;
import io.github.sweetcornna.universe.factory.power.EnergyExchangerBlockEntity;
import io.github.sweetcornna.universe.factory.power.GeothermalBlockEntity;
import io.github.sweetcornna.universe.factory.resource.MiningMachineBlockEntity;
import io.github.sweetcornna.universe.logistics.StationBlockEntity;
import io.github.sweetcornna.universe.logistics.StationContents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Jade 的客户端提示（ADR-0019）：按 {@link MachineServerData} 发来的数排版，措辞和设备界面一样。 */
enum MachineTooltip implements IBlockComponentProvider {
    INSTANCE;

    // 设备界面数据的前四格（MachineBlockEntity#dataValue）
    private static final int RATIO = 0;
    private static final int POWER_LOW = 1;
    private static final int POWER_HIGH = 2;
    private static final int FLAGS = 3;
    private static final int GENERATOR = 2;
    private static final int DEMAND = 4;

    @Override
    public Identifier getUid() {
        return MachineServerData.UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag tag = accessor.getServerData();
        beltPass(tooltip, tag);
        int[] data = tag.getIntArray(MachineServerData.DATA).orElse(null);
        if (data == null || data.length <= FLAGS) {
            return;
        }
        BlockEntity entity = accessor.getBlockEntity();
        if (entity instanceof RecipeMachineBlockEntity) {
            recipeMachine(tooltip, tag, data);
        } else if (entity instanceof GeothermalBlockEntity && data.length > GeothermalBlockEntity.HEAT) {
            int heat = data[GeothermalBlockEntity.HEAT];
            tooltip.add(heat > 0 ? Component.translatable("gui.universe.geothermal.heat", heat)
                    : Component.translatable("gui.universe.geothermal.no_lava").withStyle(ChatFormatting.GOLD));
        } else if (entity instanceof EnergyExchangerBlockEntity && data.length > EnergyExchangerBlockEntity.LOADED) {
            tooltip.add(Component.translatable(data[EnergyExchangerBlockEntity.MODE] != 0 ? "gui.universe.energy_exchanger.discharge"
                    : "gui.universe.energy_exchanger.charge"));
            if (data[EnergyExchangerBlockEntity.LOADED] != 0) {
                long energy = MachineMenu.join(data[EnergyExchangerBlockEntity.ENERGY], data[EnergyExchangerBlockEntity.ENERGY + 1]);
                tooltip.add(Component.translatable("gui.universe.energy_exchanger.energy", MachineScreen.joules(energy),
                        MachineScreen.joules(AccumulatorBlockEntity.CAPACITY)));
            }
        } else if (entity instanceof MiningMachineBlockEntity) {
            miner(tooltip, data);
        } else if (entity instanceof RayReceiverBlockEntity) {
            receiver(tooltip, tag, data);
        } else if (entity instanceof StationBlockEntity station) {
            station(tooltip, tag, station.kind().remote());
        }
        power(tooltip, data);
    }

    private static void power(ITooltip tooltip, int[] data) {
        int ratio = data[RATIO];
        int flags = data[FLAGS];
        String watts = MachineScreen.watts(MachineMenu.join(data[POWER_LOW], data[POWER_HIGH]));
        if (ratio < 0) {
            if ((flags & (GENERATOR | DEMAND)) != 0) {
                tooltip.add(Component.translatable("jade.universe.power.none").withStyle(ChatFormatting.RED));
            }
        } else if ((flags & GENERATOR) != 0) {
            tooltip.add(Component.translatable("jade.universe.power.generating", watts, percent(ratio)));
        } else if ((flags & DEMAND) != 0) {
            tooltip.add(Component.translatable("jade.universe.power.using", watts, percent(ratio))
                    .withStyle(ratio < 1000 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        }
    }

    private static String percent(int permille) {
        return String.valueOf(Math.round(permille / 10.0));
    }

    private static void recipeMachine(ITooltip tooltip, CompoundTag tag, int[] data) {
        if (data.length <= RecipeMachineBlockEntity.STATUS) {
            return;
        }
        Item making = item(tag.getStringOr(MachineServerData.MAKING, ""));
        if (making != null) {
            tooltip.add(Component.translatable("jade.universe.making", new ItemStack(making).getHoverName(),
                    percent(data[RecipeMachineBlockEntity.PROGRESS])));
        }
        int status = data[RecipeMachineBlockEntity.STATUS];
        String key = switch (status) {
            case RecipeMachineBlockEntity.NO_RECIPE -> "no_recipe";
            case RecipeMachineBlockEntity.NO_INPUT -> "no_input";
            case RecipeMachineBlockEntity.OUTPUT_FULL -> "output_full";
            case RecipeMachineBlockEntity.NO_POWER -> "no_power";
            case RecipeMachineBlockEntity.LOCKED -> "locked";
            default -> null;
        };
        if (key != null) {
            tooltip.add(Component.translatable("gui.universe.recipe_machine." + key).withStyle(ChatFormatting.GOLD));
        }
        // 原料喷过增产剂时的加成（ADR-0022）
        if (data.length > RecipeMachineBlockEntity.PROLIFERATION) {
            int value = data[RecipeMachineBlockEntity.PROLIFERATION];
            int level = value >> 1 & 7;
            if (level > 0) {
                boolean speedup = (value & 16) == 0 || (value & 1) != 0;
                Component mode = Component.translatable(speedup ? "gui.universe.proliferation.speedup" : "gui.universe.proliferation.extra");
                tooltip.add(Component.translatable("gui.universe.proliferation.bonus", mode, MachineScreen.percent(speedup
                        ? Proliferation.speedup(level) : Proliferation.extraProducts(level))).withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
    }

    /** 喷涂机和流速监测器（ADR-0022）：不是用电设备，数据单独发过来。 */
    private static void beltPass(ITooltip tooltip, CompoundTag tag) {
        int[] coater = tag.getIntArray(MachineServerData.COATER).orElse(null);
        if (coater != null && coater.length >= 2) {
            tooltip.add(coater[0] == 0 ? Component.translatable("message.universe.spray_coater.empty").withStyle(ChatFormatting.GOLD)
                    : Component.translatable("message.universe.spray_coater.status",
                            new ItemStack(Proliferators.item(Proliferation.Tier.values()[coater[0] - 1])).getHoverName(), coater[1]));
        }
        int[] flow = tag.getIntArray(MachineServerData.FLOW).orElse(null);
        if (flow != null && flow.length >= 3) {
            tooltip.add(Component.translatable("gui.universe.traffic_monitor.count", flow[0]));
            if (flow[1] > 0) {
                boolean measured = flow.length < 4 || flow[3] != 0;
                tooltip.add(flow[2] != 0 ? Component.translatable("gui.universe.traffic_monitor.alarm", flow[1]).withStyle(ChatFormatting.RED)
                        : Component.translatable(measured ? "gui.universe.traffic_monitor.ok" : "gui.universe.traffic_monitor.warming", flow[1]));
            }
        }
    }

    private static void miner(ITooltip tooltip, int[] data) {
        if (data.length <= MiningMachineBlockEntity.SPEED) {
            return;
        }
        int nodes = data[MiningMachineBlockEntity.NODES];
        long id = MachineMenu.join(data[MiningMachineBlockEntity.ITEM], data[MiningMachineBlockEntity.ITEM + 1]) - 1;
        if (nodes == 0 || id < 0) {
            return;
        }
        Item item = BuiltInRegistries.ITEM.byId((int) id);
        tooltip.add(Component.translatable("gui.universe.mining_machine.vein", new ItemStack(item).getHoverName(), nodes));
        double perMinute = nodes * 1200.0 / MiningMachineBlockEntity.TICKS_PER_ITEM * data[MiningMachineBlockEntity.SPEED] / 100;
        tooltip.add(Component.translatable("gui.universe.mining_machine.rate", String.format(Locale.ROOT, "%.0f", perMinute)));
        long remaining = MachineMenu.join(data[MiningMachineBlockEntity.REMAINING], data[MiningMachineBlockEntity.REMAINING + 1]);
        tooltip.add(Component.translatable("gui.universe.mining_machine.remaining", String.format(Locale.ROOT, "%,d", remaining)));
    }

    private static void receiver(ITooltip tooltip, CompoundTag tag, int[] data) {
        if (data.length <= RayReceiverBlockEntity.STATUS) {
            return;
        }
        tooltip.add(Component.translatable("gui.universe.receiver.mode." + data[RayReceiverBlockEntity.MODE]));
        int status = data[RayReceiverBlockEntity.STATUS];
        tooltip.add(Component.translatable("gui.universe.receiver.status." + status)
                .withStyle(status == RayReceiverBlockEntity.Status.RECEIVING.ordinal() ? ChatFormatting.GRAY : ChatFormatting.GOLD));
        tooltip.add(Component.translatable("gui.universe.receiver.continuity", percent(data[RayReceiverBlockEntity.CONTINUITY]),
                tag.getIntOr(MachineServerData.EFFICIENCY, 0)));
    }

    private static void station(ITooltip tooltip, CompoundTag tag, boolean remote) {
        StationContents contents = tag.read(MachineServerData.STATION, StationContents.CODEC).orElse(null);
        if (contents == null) {
            return;
        }
        for (StationContents.Slot slot : contents.slots()) {
            Item item = item(slot.item());
            if (item == null) {
                continue;
            }
            Component local = logic(slot.local());
            Component line = remote
                    ? Component.translatable("jade.universe.station.slot_remote", new ItemStack(item).getHoverName(), count(slot.count()),
                            count(slot.max()), local, logic(slot.remote()))
                    : Component.translatable("jade.universe.station.slot", new ItemStack(item).getHoverName(), count(slot.count()),
                            count(slot.max()), local);
            tooltip.add(line);
        }
    }

    private static Component logic(int ordinal) {
        Logic logic = Logic.values()[Math.floorMod(ordinal, Logic.values().length)];
        return Component.translatable("gui.universe.station.logic." + logic.name().toLowerCase(Locale.ROOT));
    }

    private static String count(int value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static @Nullable Item item(String id) {
        Identifier key = Identifier.tryParse(id);
        if (id.isEmpty() || key == null) {
            return null;
        }
        return BuiltInRegistries.ITEM.getOptional(key).orElse(null);
    }
}
