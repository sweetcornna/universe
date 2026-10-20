package io.github.sweetcornna.universe.client.blueprint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.blueprint.BlueprintInfo;
import io.github.sweetcornna.universe.blueprint.BlueprintItem;
import io.github.sweetcornna.universe.blueprint.BlueprintSelection;
import io.github.sweetcornna.universe.blueprint.Blueprints;
import io.github.sweetcornna.universe.client.UniverseKeys;
import io.github.sweetcornna.universe.core.blueprint.BlueprintGeometry;
import io.github.sweetcornna.universe.network.BlueprintPayloads;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * 蓝图的客户端（ADR-0018）：拿着空白蓝图时画选区的框；拿着画好的蓝图时，在准星对着的地方画半透明的预览，
 * 放不下的格子画成红色。旋转键（默认 R）把蓝图转 90°。蓝图的内容向服务端要一次，记在缓存里。
 */
public final class BlueprintClient {
    /** 预览最多画几个方块，再多只画外框。 */
    public static final int MAX_GHOSTS = 4096;
    private static final ContextKey<Preview> PREVIEW = new ContextKey<>(Universe.id("blueprint_preview"));
    private static final int GHOST = 0x8CBFE4FF;
    private static final int BLOCKED = 0x99FF5A5A;
    private static final int FRAME = 0xFF6FD8FF;
    private static final int FRAME_BAD = 0xFFFF6060;
    private static final Direction[] DIRECTIONS = Direction.values();

    private static final Map<Integer, BlueprintPayloads.Content> CACHE = new HashMap<>();
    private static final Set<Integer> REQUESTED = new HashSet<>();
    private static final Map<BlockState, List<BlockStateModelPart>> MODELS = new HashMap<>();
    private static @Nullable BlockStateModelSet modelSet;

    /** 一个半透明的方块：相对相机的位置、模型、不画的面（相邻的实心预览方块挡住的面）。 */
    private record Ghost(float x, float y, float z, List<BlockStateModelPart> parts, int hidden, int color) {
    }

    private record Preview(AABB frame, int frameColor, List<Ghost> ghosts) {
    }

    /** 上一次放的蓝图：准星落在它的范围里时对齐过去，好原地补上缺的方块。 */
    private record Paste(int id, int turns, BlockPos anchor, String dimension) {
    }

    private static @Nullable Paste lastPaste;

    private BlueprintClient() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> {
            event.register(BlueprintPayloads.OpenSave.TYPE, (payload, context) -> context.enqueueWork(
                    () -> Minecraft.getInstance().gui.setScreen(new BlueprintSaveScreen(payload.sizeX(), payload.sizeY(), payload.sizeZ()))));
            event.register(BlueprintPayloads.Content.TYPE, (payload, context) -> context.enqueueWork(() -> {
                CACHE.put(payload.id(), payload);
                REQUESTED.remove(payload.id());
            }));
        });
        gameBus.addListener(ClientTickEvent.Post.class, event -> {
            Minecraft minecraft = Minecraft.getInstance();
            while (UniverseKeys.ROTATE_BLUEPRINT.consumeClick()) {
                if (minecraft.gui.screen() == null && minecraft.player != null
                        && minecraft.player.getMainHandItem().has(UniverseDataComponents.BLUEPRINT.get())) {
                    ClientPacketDistributor.sendToServer(new BlueprintPayloads.Rotate());
                }
            }
        });
        gameBus.addListener(InputEvent.InteractionKeyMappingTriggered.class, BlueprintClient::onUse);
        gameBus.addListener(ExtractLevelRenderStateEvent.class, BlueprintClient::extract);
        gameBus.addListener(SubmitCustomGeometryEvent.class, BlueprintClient::submit);
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            CACHE.clear();
            REQUESTED.clear();
            MODELS.clear();
            lastPaste = null;
        });
    }

    /** 拿着蓝图看着的方块，最远 {@link Blueprints#REACH} 格。 */
    private static @Nullable BlockHitResult target(Player player, float partialTick) {
        return player.pick(Blueprints.REACH, partialTick, false) instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    /** 拿着蓝图右键：自己算远处的目标发给服务端，不走原版的交互距离。潜行右键（保存、清空）照旧。 */
    private static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        Player player = Minecraft.getInstance().player;
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND || player == null || player.isShiftKeyDown()
                || !(player.getMainHandItem().getItem() instanceof BlueprintItem)) {
            return;
        }
        BlockHitResult hit = target(player, 1.0F);
        if (hit == null) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        BlueprintInfo info = held.get(UniverseDataComponents.BLUEPRINT.get());
        BlockPos pos = hit.getBlockPos();
        if (info != null) {
            int turns = held.getOrDefault(UniverseDataComponents.BLUEPRINT_ROTATION.get(), 0);
            pos = anchor(info, turns, player.level(), hit);
            lastPaste = new Paste(info.id(), turns, pos, player.level().dimension().identifier().toString());
        }
        ClientPacketDistributor.sendToServer(new BlueprintPayloads.Click(pos));
        event.setCanceled(true);
    }

    /** 画好的蓝图放在哪一格；准星落在上一次放的范围里（同一张、同一个朝向）时用上一次的位置。 */
    private static BlockPos anchor(BlueprintInfo info, int turns, Level level, BlockHitResult hit) {
        BlockPos anchor = BlueprintItem.anchor(level, hit.getBlockPos(), hit.getDirection());
        Paste last = lastPaste;
        if (last == null || last.id != info.id() || last.turns != turns || !last.dimension.equals(level.dimension().identifier().toString())) {
            return anchor;
        }
        int[] rotated = BlueprintGeometry.rotatedSize(info.size(), turns);
        BlockPos origin = BlueprintItem.origin(last.anchor, rotated);
        AABB box = new AABB(Vec3.atLowerCornerOf(origin), Vec3.atLowerCornerOf(origin).add(rotated[0], rotated[1], rotated[2]));
        return box.contains(Vec3.atCenterOf(hit.getBlockPos())) || box.contains(Vec3.atCenterOf(anchor)) ? last.anchor : anchor;
    }

    private static void extract(ExtractLevelRenderStateEvent event) {
        LevelRenderState state = event.getRenderState();
        state.setRenderData(PREVIEW, null);
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        ClientLevel level = event.getLevel();
        if (player == null || level == null) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof BlueprintItem)) {
            return;
        }
        Vec3 camera = event.getCamera().position();
        BlockHitResult hit = target(player, event.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        BlueprintInfo info = held.get(UniverseDataComponents.BLUEPRINT.get());
        Preview preview = info == null ? selection(held, level, hit, camera) : placement(info, held, level, hit, camera);
        state.setRenderData(PREVIEW, preview);
    }

    /** 空白蓝图：第一个角到第二个角（还没定时到准星对着的方块）。 */
    private static @Nullable Preview selection(ItemStack held, ClientLevel level, @Nullable BlockHitResult hit, Vec3 camera) {
        BlueprintSelection selection = held.get(UniverseDataComponents.BLUEPRINT_SELECTION.get());
        if (selection == null || selection.first().isEmpty() || !selection.dimension().equals(level.dimension().identifier().toString())) {
            return null;
        }
        BlockPos a = selection.first().get();
        BlockPos b = selection.second().orElse(hit != null ? hit.getBlockPos() : a);
        int[] size = BlueprintGeometry.size(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
        AABB box = new AABB(Vec3.atLowerCornerOf(BlockPos.min(a, b)), Vec3.atLowerCornerOf(BlockPos.max(a, b)).add(1, 1, 1));
        return new Preview(box.move(camera.reverse()), BlueprintGeometry.fits(size) ? FRAME : FRAME_BAD, List.of());
    }

    /** 画好的蓝图：准星对着的地方放下去是什么样。 */
    private static @Nullable Preview placement(BlueprintInfo info, ItemStack held, ClientLevel level, @Nullable BlockHitResult hit, Vec3 camera) {
        if (hit == null) {
            return null;
        }
        BlueprintPayloads.Content content = CACHE.get(info.id());
        if (content == null && REQUESTED.add(info.id())) {
            ClientPacketDistributor.sendToServer(new BlueprintPayloads.Request(info.id()));
        }
        int turns = held.getOrDefault(UniverseDataComponents.BLUEPRINT_ROTATION.get(), 0);
        int[] rotated = BlueprintGeometry.rotatedSize(info.size(), turns);
        BlockPos origin = BlueprintItem.origin(anchor(info, turns, level, hit), rotated);
        AABB box = new AABB(Vec3.atLowerCornerOf(origin), Vec3.atLowerCornerOf(origin).add(rotated[0], rotated[1], rotated[2]));
        List<Ghost> ghosts = content == null || content.positions().size() > MAX_GHOSTS ? List.of() : ghosts(content, origin, turns, level, camera);
        boolean blocked = ghosts.stream().anyMatch(ghost -> ghost.color == BLOCKED);
        return new Preview(box.move(camera.reverse()), blocked ? FRAME_BAD : FRAME, ghosts);
    }

    private static List<Ghost> ghosts(BlueprintPayloads.Content content, BlockPos origin, int turns, ClientLevel level, Vec3 camera) {
        BlockStateModelSet models = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
        if (models != modelSet) {
            // 资源包重新加载过，模型要重新取
            MODELS.clear();
            modelSet = models;
        }
        Rotation rotation = Blueprints.rotation(turns);
        int count = content.positions().size();
        Map<BlockPos, BlockState> placed = new HashMap<>(count * 2);
        for (int i = 0; i < count; i++) {
            int packed = content.positions().get(i);
            int[] xz = BlueprintGeometry.rotate(BlueprintGeometry.x(packed), BlueprintGeometry.z(packed), content.sizeX(), content.sizeZ(), turns);
            placed.put(origin.offset(xz[0], BlueprintGeometry.y(packed), xz[1]), content.palette().get(content.states().get(i)).rotate(rotation));
        }
        List<Ghost> ghosts = new ArrayList<>(count);
        for (Map.Entry<BlockPos, BlockState> entry : placed.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState state = entry.getValue();
            BlockState current = level.getBlockState(pos);
            if (current == state) {
                continue;
            }
            int hidden = 0;
            for (Direction direction : DIRECTIONS) {
                BlockState neighbor = placed.get(pos.relative(direction));
                if (neighbor != null && neighbor.canOcclude()) {
                    hidden |= 1 << direction.ordinal();
                }
            }
            List<BlockStateModelPart> parts = MODELS.computeIfAbsent(state, key -> {
                List<BlockStateModelPart> list = new ArrayList<>();
                models.get(key).collectParts(RandomSource.create(42), list);
                return list;
            });
            int color = current.canBeReplaced() && level.isUnobstructed(state, pos, CollisionContext.empty()) ? GHOST : BLOCKED;
            ghosts.add(new Ghost((float) (pos.getX() - camera.x), (float) (pos.getY() - camera.y), (float) (pos.getZ() - camera.z), parts, hidden,
                    color));
        }
        return ghosts;
    }

    private static void submit(SubmitCustomGeometryEvent event) {
        Preview preview = event.getLevelRenderState().getRenderData(PREVIEW);
        if (preview == null) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        float width = Minecraft.getInstance().getWindow().getAppropriateLineWidth() * 1.5F;
        event.getSubmitNodeCollector().submitShapeOutline(pose, Shapes.create(preview.frame), RenderTypes.linesTranslucent(), preview.frameColor,
                width, false);
        for (Ghost ghost : preview.ghosts) {
            pose.pushPose();
            pose.translate(ghost.x, ghost.y, ghost.z);
            event.getSubmitNodeCollector().submitCustomGeometry(pose, RenderTypes.translucentMovingBlock(), (p, buffer) -> {
                QuadInstance instance = new QuadInstance();
                instance.setColor(ghost.color);
                for (BlockStateModelPart part : ghost.parts) {
                    for (BakedQuad quad : part.getQuads(null)) {
                        buffer.putBakedQuad(p, quad, instance);
                    }
                    for (Direction direction : DIRECTIONS) {
                        if ((ghost.hidden & 1 << direction.ordinal()) != 0) {
                            continue;
                        }
                        for (BakedQuad quad : part.getQuads(direction)) {
                            buffer.putBakedQuad(p, quad, instance);
                        }
                    }
                }
            });
            pose.popPose();
        }
    }
}
