package io.github.sweetcornna.universe.beacon;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.beacon.BeaconStyle;
import io.github.sweetcornna.universe.core.beacon.BeaconVisibility;
import io.github.sweetcornna.universe.core.beacon.Memo;
import io.github.sweetcornna.universe.network.HoloBeaconPayloads;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 全息信标的设置（ADR-0033 §六）：图标（一个物品）、一行字、颜色、能被看到的范围、一页备忘。设置同步到客户端，渲染器画图标和光柱，
 * 界面也读这里。
 */
public class HoloBeaconBlockEntity extends BlockEntity {
    /** 改设置时人离信标多远以内。 */
    private static final double REACH = 8;

    private String icon = "";
    private String label = "";
    private int color;
    private BeaconVisibility visibility = BeaconVisibility.PLANET;
    private Memo memo = Memo.EMPTY;

    public HoloBeaconBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.HOLO_BEACON.get(), pos, state);
    }

    public String icon() {
        return this.icon;
    }

    /** 图标物品；没设或者物品不存在了是空。 */
    public @Nullable Item iconItem() {
        Identifier id = this.icon.isEmpty() ? null : Identifier.tryParse(this.icon);
        Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
        return item == Items.AIR ? null : item;
    }

    public String label() {
        return this.label;
    }

    public int color() {
        return this.color;
    }

    public BeaconVisibility visibility() {
        return this.visibility;
    }

    public Memo memo() {
        return this.memo;
    }

    /** 改设置：都整理一遍，存档、同步给客户端、更新登记表。 */
    public void apply(String label, String icon, int color, BeaconVisibility visibility, Memo memo) {
        this.label = BeaconStyle.cleanLabel(label);
        this.icon = validIcon(icon);
        this.color = BeaconStyle.clampColor(color);
        this.visibility = visibility;
        this.memo = Memo.clean(memo.lines());
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
        this.register();
    }

    private static String validIcon(String icon) {
        Identifier id = icon == null || icon.isEmpty() ? null : Identifier.tryParse(icon);
        return id == null || BuiltInRegistries.ITEM.getValue(id) == Items.AIR ? "" : id.toString();
    }

    /** 把自己登记进 {@link HoloBeacons}（只在服务端）。 */
    public void register() {
        if (this.level instanceof ServerLevel level) {
            HoloBeacons.get(level.getServer()).put(level.getServer(), new HoloBeacons.Beacon(level.dimension(), this.worldPosition, this.icon,
                    this.color, this.label, this.visibility));
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // 登记表丢了（换了存档的 data 目录）或者是旧版本放的，区块加载时补上
        if (this.level instanceof ServerLevel level && HoloBeacons.get(level.getServer()).at(level.dimension(), this.worldPosition).isEmpty()) {
            this.register();
        }
    }

    public static void handleUpdate(HoloBeaconPayloads.Update payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.level();
        if (!level.isLoaded(payload.pos()) || player.position().distanceToSqr(Vec3.atCenterOf(payload.pos())) > REACH * REACH) {
            return;
        }
        if (level.getBlockEntity(payload.pos()) instanceof HoloBeaconBlockEntity beacon) {
            beacon.apply(payload.label(), payload.icon(), payload.color(), payload.visibility(), payload.memo());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.icon = input.getStringOr("Icon", "");
        this.label = input.getStringOr("Label", "");
        this.color = BeaconStyle.clampColor(input.getIntOr("Color", 0));
        this.visibility = BeaconVisibility.byKey(input.getStringOr("Visibility", BeaconVisibility.PLANET.key()));
        this.memo = input.read("Memo", MemoCodecs.CODEC).orElse(Memo.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("Icon", this.icon);
        output.putString("Label", this.label);
        output.putInt("Color", this.color);
        output.putString("Visibility", this.visibility.key());
        output.store("Memo", MemoCodecs.CODEC, this.memo);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }
}
