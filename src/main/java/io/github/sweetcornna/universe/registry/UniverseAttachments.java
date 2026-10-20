package io.github.sweetcornna.universe.registry;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.mission.MissionState;
import io.github.sweetcornna.universe.sentinel.SentinelHud;
import io.github.sweetcornna.universe.sentinel.SentinelAlert;
import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.galaxy.Hazard;
import io.github.sweetcornna.universe.core.survival.LifeSupport;
import io.github.sweetcornna.universe.poi.PoiProgress;
import io.github.sweetcornna.universe.poi.Waypoint;
import io.github.sweetcornna.universe.survival.SuitHud;
import io.github.sweetcornna.universe.tech.ExosuitData;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** 挂在实体、世界上的附加数据。 */
public final class UniverseAttachments {
    private static final Codec<Hazard> HAZARD_CODEC = Codec.STRING.xmap(
            name -> Hazard.valueOf(name.toUpperCase(Locale.ROOT)), hazard -> hazard.name().toLowerCase(Locale.ROOT));
    private static final MapCodec<LifeSupport> LIFE_SUPPORT_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.FLOAT.fieldOf("oxygen").forGetter(LifeSupport::oxygen),
            Codec.unboundedMap(HAZARD_CODEC, Codec.FLOAT).fieldOf("protection").forGetter(LifeSupport::protection),
            Codec.FLOAT.fieldOf("jet").forGetter(LifeSupport::jet)
    ).apply(i, LifeSupport::new));
    /** 打包的方块坐标存成字符串（NBT 复合标签的键只能是字符串）。 */
    private static final Codec<Long> PACKED_POS = Codec.STRING.comapFlatMap(text -> {
        try {
            return DataResult.success(Long.parseLong(text));
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "not a packed position: " + text);
        }
    }, String::valueOf);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Universe.MOD_ID);

    /**
     * 玩家在每颗星球上最后一次驾船起飞的位置（维度 ID → 打包坐标）。记在玩家身上，停船后重新组装也不会忘，
     * 飞回来时降落在起飞点附近。
     */
    public static final Supplier<AttachmentType<Map<Identifier, Long>>> DEPARTURES = ATTACHMENT_TYPES.register("departures",
            () -> AttachmentType.builder(() -> Map.<Identifier, Long>of())
                    .serialize(Codec.unboundedMap(Identifier.CODEC, Codec.LONG).fieldOf("departures"))
                    .copyOnDeath()
                    .build());

    /** 外骨骼的氧气、危害防护、喷气背包能量（只在服务端，存档；死亡后重置为满）。 */
    public static final Supplier<AttachmentType<LifeSupport>> LIFE_SUPPORT = ATTACHMENT_TYPES.register("life_support",
            () -> AttachmentType.builder(() -> new LifeSupport()).serialize(LIFE_SUPPORT_CODEC).build());
    /** 发给玩家自己的外骨骼读数，HUD 用（不存档）。 */
    public static final Supplier<AttachmentType<SuitHud>> SUIT_HUD = ATTACHMENT_TYPES.register("suit_hud",
            () -> AttachmentType.builder(() -> SuitHud.FULL).sync((holder, player) -> holder == player, SuitHud.STREAM_CODEC).build());

    /** 外骨骼的技术槽、货舱和修过的掉落舱（ADR-0014），死亡后保留。 */
    public static final Supplier<AttachmentType<ExosuitData>> EXOSUIT = ATTACHMENT_TYPES.register("exosuit",
            () -> AttachmentType.builder(() -> new ExosuitData()).serialize(ExosuitData.CODEC).copyOnDeath().build());
    /** 玩家在兴趣点上的进度：去过哪些、领过哪些奖励、先驱者线索讲到第几段（ADR-0014）。 */
    public static final Supplier<AttachmentType<PoiProgress>> POI_PROGRESS = ATTACHMENT_TYPES.register("poi_progress",
            () -> AttachmentType.builder(() -> new PoiProgress()).serialize(PoiProgress.CODEC).copyOnDeath().build());
    /** 信号增强器定位出来的航点，同步给本人在 HUD 上画（ADR-0014）。 */
    public static final Supplier<AttachmentType<Waypoint>> WAYPOINT = ATTACHMENT_TYPES.register("waypoint",
            () -> AttachmentType.builder(() -> Waypoint.NONE).serialize(Waypoint.CODEC).copyOnDeath()
                    .sync((holder, player) -> holder == player, Waypoint.STREAM_CODEC).build());

    /** 玩家在当前星球上的警戒值和通缉级别（ADR-0014），存档；死亡后清零。 */
    public static final Supplier<AttachmentType<SentinelAlert>> SENTINEL_ALERT = ATTACHMENT_TYPES.register("sentinel_alert",
            () -> AttachmentType.builder(() -> new SentinelAlert()).serialize(SentinelAlert.CODEC).build());
    /** 发给玩家自己的守卫机读数，HUD 用（不存档）。 */
    public static final Supplier<AttachmentType<SentinelHud>> SENTINEL_HUD = ATTACHMENT_TYPES.register("sentinel_hud",
            () -> AttachmentType.builder(() -> SentinelHud.NONE).sync((holder, player) -> holder == player, SentinelHud.STREAM_CODEC).build());

    /** 信用点（ADR-0017）：死亡不丢，同步给本人，交易界面和 HUD 上显示。 */
    public static final Supplier<AttachmentType<Long>> CREDITS = ATTACHMENT_TYPES.register("credits",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG.fieldOf("credits")).copyOnDeath()
                    .sync((holder, player) -> holder == player, ByteBufCodecs.VAR_LONG).build());

    /** 进过的空间站（恒星系序号，ADR-0017）：传送器能传过去。死亡不丢。 */
    public static final Supplier<AttachmentType<List<Integer>>> VISITED_STATIONS = ATTACHMENT_TYPES.register("visited_stations",
            () -> AttachmentType.builder(() -> List.<Integer>of()).serialize(Codec.INT.listOf().fieldOf("systems")).copyOnDeath().build());

    /** 空间站任务（ADR-0017）：接了的任务和今天做完的，死亡不丢，同步给本人画 HUD。 */
    public static final Supplier<AttachmentType<MissionState>> MISSIONS = ATTACHMENT_TYPES.register("missions",
            () -> AttachmentType.builder(() -> MissionState.NONE).serialize(MissionState.CODEC).copyOnDeath()
                    .sync((holder, player) -> holder == player, MissionState.STREAM_CODEC).build());

    /** 太空维度的区块存过盘（有过方块，ADR-0017）：以后方块拆光了也要再存一次。 */
    public static final Supplier<AttachmentType<Boolean>> SPACE_BUILT = ATTACHMENT_TYPES.register("space_built",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL.fieldOf("built")).build());

    /** 区块里开采过的矿脉节点和原油涌泉：打包坐标 → 开采了多少（ADR-0011）。没碰过的不记。 */
    public static final Supplier<AttachmentType<Map<Long, Integer>>> VEIN_USAGE = ATTACHMENT_TYPES.register("vein_usage",
            () -> AttachmentType.<Map<Long, Integer>>builder(() -> new HashMap<>())
                    .serialize(Codec.unboundedMap(PACKED_POS, Codec.INT).<Map<Long, Integer>>xmap(HashMap::new, map -> map).fieldOf("usage"))
                    .build());

    private UniverseAttachments() {
    }

    public static void register(IEventBus modBus) {
        ATTACHMENT_TYPES.register(modBus);
    }
}
