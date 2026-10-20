package io.github.sweetcornna.universe.blueprint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.advancement.MilestoneTrigger;
import io.github.sweetcornna.universe.core.blueprint.BlueprintGeometry;
import io.github.sweetcornna.universe.network.BlueprintPayloads;
import io.github.sweetcornna.universe.perf.ServerPerf;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.tech.ExosuitData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 蓝图的服务端（ADR-0018）：框选区、存蓝图、转向、放蓝图。放蓝图排成一个队列，每刻放几个方块，从背包和外骨骼货舱里
 * 扣材料；材料不够、位置被占着的跳过，放完在聊天栏说一声缺了什么。
 */
public final class Blueprints {
    /** 每刻放几个方块。 */
    public static final int PLACE_PER_TICK = 8;
    /** 提示里最多列几种材料。 */
    public static final int LISTED_MATERIALS = 6;
    /** 拿着蓝图时射线能点多远（预览和放置都用这个，比原版的交互距离长）。 */
    public static final double REACH = 64;

    private static final Map<UUID, Job> JOBS = new HashMap<>();

    /** 要放的一个方块；cost 为空的不扣材料（双格方块的上半截、床头）。 */
    private record Placement(BlockPos pos, BlockState state, @Nullable CompoundTag settings, @Nullable Item cost) {
    }

    private static final class Job {
        final ResourceKey<Level> dimension;
        final List<Placement> queue;
        int next;
        int placed;
        int blocked;
        final Map<Item, Integer> missing = new LinkedHashMap<>();

        Job(ResourceKey<Level> dimension, List<Placement> queue) {
            this.dimension = dimension;
            this.queue = queue;
        }
    }

    private Blueprints() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ServerTickEvent.Post.class, event -> ServerPerf.time("blueprints", () -> tick(event.getServer())));
        gameBus.addListener(ServerStoppedEvent.class, event -> JOBS.clear());
    }

    // ---- 框选区

    /** 空白蓝图右键一个方块：没有角或者已经框完时从头开始，有一个角时定第二个角。 */
    static void select(ServerPlayer player, ItemStack stack, BlockPos pos) {
        String dimension = player.level().dimension().identifier().toString();
        BlueprintSelection old = stack.get(UniverseDataComponents.BLUEPRINT_SELECTION.get());
        BlueprintSelection next;
        if (old == null || old.complete() || old.first().isEmpty() || !old.dimension().equals(dimension)) {
            next = new BlueprintSelection(Optional.of(pos.immutable()), Optional.empty(), dimension);
            player.sendOverlayMessage(Component.translatable("message.universe.blueprint.first", pos.getX(), pos.getY(), pos.getZ()));
        } else {
            next = new BlueprintSelection(old.first(), Optional.of(pos.immutable()), dimension);
            int[] size = next.size().orElseThrow();
            player.sendOverlayMessage(BlueprintGeometry.fits(size)
                    ? Component.translatable("message.universe.blueprint.second", size[0], size[1], size[2])
                    : tooBig());
        }
        stack.set(UniverseDataComponents.BLUEPRINT_SELECTION.get(), next);
    }

    private static Component tooBig() {
        return Component.translatable("message.universe.blueprint.too_big", BlueprintGeometry.MAX_SIZE).withStyle(ChatFormatting.RED);
    }

    /** 空白蓝图潜行右键：框好了就打开保存界面。 */
    static void requestSave(ServerPlayer player, ItemStack stack) {
        BlueprintSelection selection = stack.get(UniverseDataComponents.BLUEPRINT_SELECTION.get());
        Component problem = selectionProblem(player, selection);
        if (problem != null) {
            player.sendOverlayMessage(problem);
            return;
        }
        int[] size = selection.size().orElseThrow();
        PacketDistributor.sendToPlayer(player, new BlueprintPayloads.OpenSave(size[0], size[1], size[2]));
    }

    private static @Nullable Component selectionProblem(ServerPlayer player, @Nullable BlueprintSelection selection) {
        if (selection == null || !selection.complete()) {
            return Component.translatable("message.universe.blueprint.need_selection");
        }
        if (!selection.dimension().equals(player.level().dimension().identifier().toString())) {
            return Component.translatable("message.universe.blueprint.other_dimension").withStyle(ChatFormatting.RED);
        }
        if (!BlueprintGeometry.fits(selection.size().orElseThrow())) {
            return tooBig();
        }
        return null;
    }

    // ---- 存蓝图

    public static void handleSave(BlueprintPayloads.Save payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            save(player, payload.name());
        }
    }

    private static void save(ServerPlayer player, String rawName) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof BlueprintItem) || stack.has(UniverseDataComponents.BLUEPRINT.get())) {
            return;
        }
        BlueprintSelection selection = stack.get(UniverseDataComponents.BLUEPRINT_SELECTION.get());
        Component problem = selectionProblem(player, selection);
        if (problem != null) {
            player.sendOverlayMessage(problem);
            return;
        }
        String name = rawName.strip();
        if (name.length() > BlueprintPayloads.Save.MAX_NAME) {
            name = name.substring(0, BlueprintPayloads.Save.MAX_NAME);
        }
        if (name.isEmpty()) {
            name = Component.translatable("item.universe.blueprint.unnamed").getString();
        }
        BlockPos a = selection.first().orElseThrow();
        BlockPos b = selection.second().orElseThrow();
        BlueprintData.Stored stored = capture(player.level(), BlockPos.min(a, b), selection.size().orElseThrow(), name);
        if (stored.blocks() == 0) {
            player.sendOverlayMessage(Component.translatable("message.universe.blueprint.empty_selection").withStyle(ChatFormatting.RED));
            return;
        }
        int id = BlueprintData.get(player.level().getServer()).add(stored);
        stack.remove(UniverseDataComponents.BLUEPRINT_SELECTION.get());
        stack.set(UniverseDataComponents.BLUEPRINT.get(), info(id, stored));
        player.sendSystemMessage(Component.translatable("message.universe.blueprint.saved", name, stored.blocks()));
        MilestoneTrigger.reach(player, MilestoneTrigger.BLUEPRINT);
        player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1, 1);
    }

    /** 把选区里的方块和设备设置记下来；空气、拿不到物品的方块（流体之类）不记，含水的方块记成不含水的。 */
    static BlueprintData.Stored capture(ServerLevel level, BlockPos min, int[] size, String name) {
        List<BlockState> palette = new ArrayList<>();
        Map<BlockState, Integer> index = new HashMap<>();
        List<Integer> positions = new ArrayList<>();
        List<Integer> states = new ArrayList<>();
        Map<Integer, CompoundTag> settings = new HashMap<>();
        for (int y = 0; y < size[1]; y++) {
            for (int x = 0; x < size[0]; x++) {
                for (int z = 0; z < size[2]; z++) {
                    BlockPos pos = min.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || !free(state) && state.getBlock().asItem() == Items.AIR) {
                        continue;
                    }
                    if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
                        state = state.setValue(BlockStateProperties.WATERLOGGED, false);
                    }
                    int paletteIndex = index.computeIfAbsent(state, key -> {
                        palette.add(key);
                        return palette.size() - 1;
                    });
                    if (level.getBlockEntity(pos) instanceof BlueprintSettings target) {
                        CompoundTag tag = target.blueprintSettings();
                        if (tag != null) {
                            settings.put(positions.size(), tag);
                        }
                    }
                    positions.add(BlueprintGeometry.pack(x, y, z));
                    states.add(paletteIndex);
                }
            }
        }
        return new BlueprintData.Stored(name, size[0], size[1], size[2], palette, positions.stream().mapToInt(Integer::intValue).toArray(),
                states.stream().mapToInt(Integer::intValue).toArray(), settings);
    }

    /** 物品上记的东西：材料按数量从多到少。 */
    static BlueprintInfo info(int id, BlueprintData.Stored stored) {
        Map<Item, Integer> materials = new HashMap<>();
        for (int state : stored.states()) {
            Item cost = cost(stored.palette().get(state));
            if (cost != null) {
                materials.merge(cost, 1, Integer::sum);
            }
        }
        List<BlueprintInfo.Material> list = materials.entrySet().stream()
                .sorted(Map.Entry.<Item, Integer>comparingByValue().reversed())
                .map(entry -> new BlueprintInfo.Material(entry.getKey(), entry.getValue()))
                .toList();
        return new BlueprintInfo(id, stored.name(), stored.sizeX(), stored.sizeY(), stored.sizeZ(), stored.blocks(), list);
    }

    /** 不花材料、跟着另一半一起放的方块：双格方块的上半截和床头。 */
    static boolean free(BlockState state) {
        return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER
                || state.hasProperty(BlockStateProperties.BED_PART) && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD;
    }

    /** 放这个方块要扣的物品；不扣时为空。 */
    static @Nullable Item cost(BlockState state) {
        if (free(state)) {
            return null;
        }
        Item item = state.getBlock().asItem();
        return item == Items.AIR ? null : item;
    }

    // ---- 远处右键、转向、预览

    public static void handleClick(BlueprintPayloads.Click payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        BlockPos pos = payload.pos();
        ServerLevel level = player.level();
        double reach = REACH + 2;
        if (!(stack.getItem() instanceof BlueprintItem) || !level.isLoaded(pos)
                || player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > reach * reach) {
            return;
        }
        if (stack.has(UniverseDataComponents.BLUEPRINT.get())) {
            place(player, stack, pos);
        } else {
            select(player, stack, pos);
        }
    }

    public static void handleRotate(BlueprintPayloads.Rotate payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            ItemStack stack = player.getMainHandItem();
            if (stack.getItem() instanceof BlueprintItem && stack.has(UniverseDataComponents.BLUEPRINT.get())) {
                int rotation = (stack.getOrDefault(UniverseDataComponents.BLUEPRINT_ROTATION.get(), 0) + 1) % 4;
                stack.set(UniverseDataComponents.BLUEPRINT_ROTATION.get(), rotation);
                player.sendOverlayMessage(Component.translatable("message.universe.blueprint.rotated", rotation * 90));
            }
        }
    }

    public static void handleRequest(BlueprintPayloads.Request payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        BlueprintData.Stored stored = BlueprintData.get(player.level().getServer()).get(payload.id());
        PacketDistributor.sendToPlayer(player, stored == null
                ? new BlueprintPayloads.Content(payload.id(), 0, 0, 0, List.of(), List.of(), List.of())
                : new BlueprintPayloads.Content(payload.id(), stored.sizeX(), stored.sizeY(), stored.sizeZ(), stored.palette(),
                        java.util.Arrays.stream(stored.positions()).boxed().toList(), java.util.Arrays.stream(stored.states()).boxed().toList()));
    }

    // ---- 放蓝图

    /** 画好的蓝图右键：从 anchor 开始放（底面中心对着 anchor）。 */
    static void place(ServerPlayer player, ItemStack stack, BlockPos anchor) {
        BlueprintInfo info = stack.get(UniverseDataComponents.BLUEPRINT.get());
        if (info == null) {
            return;
        }
        if (JOBS.containsKey(player.getUUID())) {
            player.sendOverlayMessage(Component.translatable("message.universe.blueprint.busy"));
            return;
        }
        BlueprintData.Stored stored = BlueprintData.get(player.level().getServer()).get(info.id());
        if (stored == null) {
            player.sendOverlayMessage(Component.translatable("message.universe.blueprint.missing_data").withStyle(ChatFormatting.RED));
            return;
        }
        int turns = stack.getOrDefault(UniverseDataComponents.BLUEPRINT_ROTATION.get(), 0);
        JOBS.put(player.getUUID(), new Job(player.level().dimension(), placements(stored, anchor, turns)));
    }

    /** 蓝图里每个方块转过以后放在哪：从下往上，同一层里先放要花材料的，再放跟着另一半的。 */
    private static List<Placement> placements(BlueprintData.Stored stored, BlockPos anchor, int turns) {
        int[] rotated = BlueprintGeometry.rotatedSize(new int[] {stored.sizeX(), stored.sizeY(), stored.sizeZ()}, turns);
        BlockPos origin = BlueprintItem.origin(anchor, rotated);
        Rotation rotation = rotation(turns);
        List<Placement> list = new ArrayList<>();
        for (int i = 0; i < stored.blocks(); i++) {
            int packed = stored.positions()[i];
            int[] xz = BlueprintGeometry.rotate(BlueprintGeometry.x(packed), BlueprintGeometry.z(packed), stored.sizeX(), stored.sizeZ(), turns);
            BlockState state = stored.palette().get(stored.states()[i]).rotate(rotation);
            list.add(new Placement(origin.offset(xz[0], BlueprintGeometry.y(packed), xz[1]), state, stored.settings().get(i), cost(state)));
        }
        list.sort(Comparator.comparingInt((Placement p) -> p.pos.getY()).thenComparingInt(p -> p.cost == null ? 1 : 0)
                .thenComparingInt(p -> p.pos.getX()).thenComparingInt(p -> p.pos.getZ()));
        return list;
    }

    public static Rotation rotation(int turns) {
        return switch (Math.floorMod(turns, 4)) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    private static void tick(MinecraftServer server) {
        if (JOBS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Job>> iterator = JOBS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Job> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Job job = entry.getValue();
            ServerLevel level = server.getLevel(job.dimension);
            if (player == null || level == null) {
                iterator.remove();
                continue;
            }
            for (int n = 0; n < PLACE_PER_TICK && job.next < job.queue.size(); n++) {
                placeOne(player, level, job, job.queue.get(job.next++));
            }
            if (job.next >= job.queue.size()) {
                finish(player, job);
                iterator.remove();
            }
        }
    }

    private static void placeOne(ServerPlayer player, ServerLevel level, Job job, Placement placement) {
        BlockPos pos = placement.pos;
        if (!level.isLoaded(pos) || !level.isInWorldBounds(pos)) {
            job.blocked++;
            return;
        }
        BlockState current = level.getBlockState(pos);
        if (current == placement.state) {
            return;
        }
        if (!current.canBeReplaced() || !level.isUnobstructed(placement.state, pos, CollisionContext.empty())) {
            // 有方块，或者会卡住玩家、生物
            job.blocked++;
            return;
        }
        if (placement.cost == null && !partnerPlaced(level, pos, placement.state)) {
            // 另一半没放成（缺材料或被占着），这一半也不放
            return;
        }
        if (placement.cost != null && !player.isCreative() && !take(player, placement.cost)) {
            job.missing.merge(placement.cost, 1, Integer::sum);
            return;
        }
        level.setBlock(pos, placement.state, 3);
        if (placement.settings != null && level.getBlockEntity(pos) instanceof BlueprintSettings target) {
            target.applyBlueprintSettings(placement.settings);
        }
        job.placed++;
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.01);
        if (job.placed % 4 == 1) {
            level.playSound(null, pos, placement.state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.5F, 1.1F);
        }
    }

    /** 上半截下面、床头后面是不是已经有同一种方块。 */
    private static boolean partnerPlaced(ServerLevel level, BlockPos pos, BlockState state) {
        BlockPos partner;
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            partner = pos.below();
        } else if (state.hasProperty(BlockStateProperties.BED_PART)) {
            Direction facing = state.getValue(BedBlock.FACING);
            partner = pos.relative(facing.getOpposite());
        } else {
            return true;
        }
        return level.getBlockState(partner).is(state.getBlock());
    }

    /** 从背包、再从外骨骼货舱里拿一个。 */
    private static boolean take(ServerPlayer player, Item item) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item) && !(stack.getItem() instanceof BlueprintItem)) {
                stack.shrink(1);
                return true;
            }
        }
        ExosuitData data = player.getData(UniverseAttachments.EXOSUIT);
        for (int i = 0; i < data.cargoSlots(); i++) {
            ItemStack stack = data.cargo().get(i);
            if (stack.is(item)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static void finish(ServerPlayer player, Job job) {
        player.sendSystemMessage(Component.translatable("message.universe.blueprint.done", job.placed).withStyle(ChatFormatting.GREEN));
        if (!job.missing.isEmpty()) {
            MutableComponent list = Component.empty();
            int shown = 0;
            for (Map.Entry<Item, Integer> entry : job.missing.entrySet()) {
                if (shown == LISTED_MATERIALS) {
                    list.append(Component.translatable("message.universe.blueprint.more", job.missing.size() - shown));
                    break;
                }
                if (shown > 0) {
                    list.append(Component.translatable("gui.universe.tech_tree.separator"));
                }
                list.append(Component.translatable("message.universe.blueprint.count", new ItemStack(entry.getKey()).getHoverName(),
                        entry.getValue()));
                shown++;
            }
            player.sendSystemMessage(Component.translatable("message.universe.blueprint.missing", list).withStyle(ChatFormatting.YELLOW));
        }
        if (job.blocked > 0) {
            player.sendSystemMessage(Component.translatable("message.universe.blueprint.blocked", job.blocked).withStyle(ChatFormatting.GRAY));
        }
    }
}
