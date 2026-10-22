package io.github.sweetcornna.universe.entity;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.ecology.Companion;
import io.github.sweetcornna.universe.core.ecology.Husbandry;
import io.github.sweetcornna.universe.network.CompanionPayloads;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 伙伴界面（ADR-0026 §九）：信任、饱食、心情这些读数走 {@link ContainerData}，四个按钮走 {@link #clickMenuButton}，
 * 改名走 {@link CompanionPayloads.Rename}。名字、物种、体型由客户端从实体本身读（打开时带上实体的编号）。
 * 主人走开 8 格、伙伴死了或者放生了，界面自己关上。
 */
public class CompanionMenu extends AbstractContainerMenu {
    public static final int FOLLOW = 0;
    public static final int RIDE = 1;
    public static final int DIG = 2;
    public static final int RELEASE = 3;

    public static final int TRUST = 0;
    /** 饱食的百分数。 */
    public static final int SATIETY = 1;
    public static final int MOOD = 2;
    public static final int STAYING = 3;
    /** 还要多少秒才能再挖，0 是能挖。 */
    public static final int DIG_WAIT = 4;
    /** 还要刨多少刻，0 是没在刨。 */
    public static final int DIGGING = 5;
    /** 长大的百分数，成年的是 100。 */
    public static final int GROWTH = 6;
    /** 下一个卵还要多少秒（条件够的时候）。 */
    public static final int LAY_WAIT = 7;
    /** 下蛋的条件：见 {@link #LAY_OK} 这几个。 */
    public static final int LAY_STATE = 8;
    /** 骑乘：见 {@link #RIDE_OK} 这几个。 */
    public static final int RIDE_STATE = 9;
    public static final int DATA_COUNT = 10;

    public static final int LAY_OK = 0;
    public static final int LAY_HUNGRY = 1;
    public static final int LAY_TRUST = 2;
    public static final int LAY_YOUNG = 3;
    public static final int RIDE_OK = 0;
    public static final int RIDE_SMALL = 1;
    public static final int RIDE_YOUNG = 2;

    private static final double REACH = 8;

    private final int entityId;
    private final @Nullable AlienCreature creature;
    private final ContainerData data;

    /** 客户端：实体按编号在客户端的世界里找。 */
    public CompanionMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readVarInt(), new SimpleContainerData(DATA_COUNT));
    }

    /** 服务端。 */
    CompanionMenu(int id, Inventory inventory, AlienCreature creature) {
        this(id, inventory, creature.getId(), new Readings(creature));
    }

    private CompanionMenu(int id, Inventory inventory, int entityId, ContainerData data) {
        super(UniverseMenus.COMPANION.get(), id);
        this.entityId = entityId;
        this.creature = inventory.player.level().getEntity(entityId) instanceof AlienCreature c ? c : null;
        this.data = data;
        this.addDataSlots(data);
    }

    /** 界面对着的伙伴；客户端没加载到（离得太远）时是 null。 */
    public @Nullable AlienCreature creature() {
        return this.creature;
    }

    public int entityId() {
        return this.entityId;
    }

    public int get(int index) {
        return this.data.get(index);
    }

    public Companion.Mood mood() {
        Companion.Mood[] moods = Companion.Mood.values();
        return moods[Math.clamp(this.data.get(MOOD), 0, moods.length - 1)];
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        AlienCreature creature = this.creature;
        if (!(player instanceof ServerPlayer server) || creature == null || !this.stillValid(player)) {
            return false;
        }
        switch (id) {
            case FOLLOW -> creature.toggleStay(server);
            case RIDE -> {
                if (!creature.canBeRidden() || creature.isVehicle() || server.isPassenger()) {
                    return false;
                }
                server.closeContainer();
                return creature.mount(server);
            }
            case DIG -> {
                AlienCreature.DigResult result = creature.startDig(server.level());
                if (result == AlienCreature.DigResult.NOT_READY) {
                    server.sendOverlayMessage(Component.translatable("message.universe.companion.dig_tomorrow",
                            creature.getDisplayName()));
                }
                return result == AlienCreature.DigResult.STARTED;
            }
            case RELEASE -> {
                creature.release(server);
                server.closeContainer();
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        AlienCreature creature = this.creature;
        return player.isAlive() && creature != null && creature.isAlive() && creature.isOwnedBy(player)
                && player.distanceToSqr(creature) <= REACH * REACH;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /** 客户端 → 服务端：改名。只认这个人正开着的伙伴界面。 */
    public static void handleRename(CompanionPayloads.Rename payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof CompanionMenu menu
                && menu.containerId == payload.containerId() && menu.stillValid(player) && menu.creature != null) {
            menu.creature.rename(player, payload.name());
        }
    }

    /** 服务端的读数：每次同步时现读。 */
    private static final class Readings implements ContainerData {
        private final AlienCreature creature;

        Readings(AlienCreature creature) {
            this.creature = creature;
        }

        @Override
        public int get(int index) {
            Companion companion = this.creature.companion();
            if (companion == null) {
                return 0;
            }
            long time = this.creature.level().getGameTime();
            return switch (index) {
                case TRUST -> companion.trust();
                case SATIETY -> (int) Math.round(companion.satietyShare() * 100);
                case MOOD -> companion.mood().ordinal();
                case STAYING -> this.creature.isStaying() ? 1 : 0;
                case DIG_WAIT -> (int) Math.min(Short.MAX_VALUE, (companion.digWait(time) + 19) / 20);
                case DIGGING -> this.creature.digTicks();
                case GROWTH -> (int) Math.floor(this.creature.growth() * 100);
                case LAY_WAIT -> (Companion.LAY_INTERVAL - companion.layProgress() + 19) / 20;
                case LAY_STATE -> this.creature.isYoung() ? LAY_YOUNG
                        : companion.satiety() <= Companion.FULL / 2 ? LAY_HUNGRY
                        : companion.trust() < Companion.LAY_TRUST ? LAY_TRUST : LAY_OK;
                case RIDE_STATE -> !Husbandry.rideable(this.creature.species()) ? RIDE_SMALL : this.creature.isYoung() ? RIDE_YOUNG : RIDE_OK;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    }
}
