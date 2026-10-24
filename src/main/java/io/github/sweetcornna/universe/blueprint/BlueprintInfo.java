package io.github.sweetcornna.universe.blueprint;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;

/**
 * 画好的蓝图记在物品上的东西（ADR-0018）：编号、名字、大小、方块数和材料清单。方块本身存在服务器的全局存档里，
 * 物品只记这些，提示里看得到。
 *
 * @param materials 按数量从多到少排的材料
 * @param meta      作者、版本、说明（ADR-0033 §九）
 */
public record BlueprintInfo(int id, String name, int sizeX, int sizeY, int sizeZ, int blocks, List<Material> materials, BlueprintData.Meta meta) {
    public record Material(Item item, int count) {
        static final Codec<Material> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Material::item),
                Codec.INT.fieldOf("count").forGetter(Material::count)
        ).apply(i, Material::new));
        static final StreamCodec<RegistryFriendlyByteBuf, Material> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.registry(Registries.ITEM), Material::item,
                ByteBufCodecs.VAR_INT, Material::count,
                Material::new);
    }

    public static final Codec<BlueprintInfo> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("id").forGetter(BlueprintInfo::id),
            Codec.STRING.fieldOf("name").forGetter(BlueprintInfo::name),
            Codec.INT.fieldOf("size_x").forGetter(BlueprintInfo::sizeX),
            Codec.INT.fieldOf("size_y").forGetter(BlueprintInfo::sizeY),
            Codec.INT.fieldOf("size_z").forGetter(BlueprintInfo::sizeZ),
            Codec.INT.fieldOf("blocks").forGetter(BlueprintInfo::blocks),
            Material.CODEC.listOf().fieldOf("materials").forGetter(BlueprintInfo::materials),
            BlueprintData.Meta.MAP_CODEC.forGetter(BlueprintInfo::meta)
    ).apply(i, BlueprintInfo::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintInfo> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BlueprintInfo::id,
            ByteBufCodecs.STRING_UTF8, BlueprintInfo::name,
            ByteBufCodecs.VAR_INT, BlueprintInfo::sizeX,
            ByteBufCodecs.VAR_INT, BlueprintInfo::sizeY,
            ByteBufCodecs.VAR_INT, BlueprintInfo::sizeZ,
            ByteBufCodecs.VAR_INT, BlueprintInfo::blocks,
            Material.STREAM_CODEC.apply(ByteBufCodecs.list()), BlueprintInfo::materials,
            BlueprintData.Meta.STREAM_CODEC, BlueprintInfo::meta,
            BlueprintInfo::new);

    public int[] size() {
        return new int[] {this.sizeX, this.sizeY, this.sizeZ};
    }
}
