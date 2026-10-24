package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * 服务端 → 客户端：一台采矿机的矿采完了（ADR-0033 §八），HUD 上标出它一会儿。
 *
 * @param all 范围里一个矿点都不剩了；否则是 item 这种矿的一个矿点采完了
 */
public record VeinAlertPayload(BlockPos pos, Block machine, Item item, boolean all) implements CustomPacketPayload {
    public static final Type<VeinAlertPayload> TYPE = new Type<>(Universe.id("vein_alert"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VeinAlertPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, VeinAlertPayload::pos,
            ByteBufCodecs.registry(Registries.BLOCK), VeinAlertPayload::machine,
            ByteBufCodecs.registry(Registries.ITEM), VeinAlertPayload::item,
            ByteBufCodecs.BOOL, VeinAlertPayload::all,
            VeinAlertPayload::new);

    @Override
    public Type<VeinAlertPayload> type() {
        return TYPE;
    }
}
