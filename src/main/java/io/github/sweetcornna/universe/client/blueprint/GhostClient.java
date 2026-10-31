package io.github.sweetcornna.universe.client.blueprint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.FrustumIntersection;
import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.blueprint.BlueprintGeometry;
import io.github.sweetcornna.universe.network.GhostsPayload;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 工地的虚影（ADR-0023）：服务端发来附近的工地，这里把还没建的格子画成半透明的方块。已经是那个方块的格子不画，
 * 被别的方块占着的画成红色。
 */
public final class GhostClient {
    private static final ContextKey<List<BlueprintClient.Ghost>> GHOSTS = new ContextKey<>(Universe.id("site_ghosts"));
    /** 只画这个距离以内的虚影，最多这么多个。 */
    private static final double VIEW_DISTANCE = 96;
    private static final int MAX_GHOSTS = 6000;
    /** 工地的虚影比拿着蓝图时的预览淡一点。 */
    private static final int SITE = 0x70A8E8FF;

    /** 一个工地：每格的位置、方块，和被相邻实心虚影挡住的面；{@code box}、{@code bounds} 罩住全部格子，整个看不见时一格都不用查。 */
    private static final class Site {
        final BlockPos[] positions;
        final BlockState[] states;
        final @Nullable BoundingBox box;
        final @Nullable AABB bounds;
        BitSet remaining;
        int[] hidden;

        Site(BlockPos[] positions, BlockState[] states, BitSet remaining) {
            this.positions = positions;
            this.states = states;
            this.box = BoundingBox.encapsulatingPositions(Arrays.asList(positions)).orElse(null);
            this.bounds = this.box == null ? null : AABB.of(this.box);
            this.setRemaining(remaining);
        }

        void setRemaining(BitSet remaining) {
            this.remaining = remaining;
            Map<Long, BlockState> map = new HashMap<>();
            for (int i = remaining.nextSetBit(0); i >= 0; i = remaining.nextSetBit(i + 1)) {
                map.put(this.positions[i].asLong(), this.states[i]);
            }
            this.hidden = new int[this.positions.length];
            for (int i = remaining.nextSetBit(0); i >= 0; i = remaining.nextSetBit(i + 1)) {
                int mask = 0;
                for (Direction direction : BlueprintClient.DIRECTIONS) {
                    BlockState neighbour = map.get(this.positions[i].relative(direction).asLong());
                    if (neighbour != null && neighbour.canOcclude()) {
                        mask |= 1 << direction.ordinal();
                    }
                }
                this.hidden[i] = mask;
            }
        }
    }

    private static final Map<Integer, Site> SITES = new HashMap<>();

    private GhostClient() {
    }

    /** 测试和截图用：客户端知道的工地里还剩几格没建。 */
    public static int remaining() {
        int count = 0;
        for (Site site : SITES.values()) {
            count += site.remaining.cardinality();
        }
        return count;
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(GhostsPayload.TYPE, GhostClient::handle));
        gameBus.addListener(ExtractLevelRenderStateEvent.class, GhostClient::extract);
        gameBus.addListener(SubmitCustomGeometryEvent.class, event -> {
            List<BlueprintClient.Ghost> ghosts = event.getLevelRenderState().getRenderData(GHOSTS);
            if (ghosts != null) {
                BlueprintClient.submitGhosts(event, ghosts);
            }
        });
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> SITES.clear());
    }

    private static void handle(GhostsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.reset()) {
                SITES.clear();
            }
            for (GhostsPayload.Site site : payload.sites()) {
                BlockState[] palette = new BlockState[site.palette().length];
                for (int i = 0; i < palette.length; i++) {
                    palette[i] = Block.stateById(site.palette()[i]);
                }
                int n = site.positions().length;
                BlockPos[] positions = new BlockPos[n];
                BlockState[] states = new BlockState[n];
                for (int i = 0; i < n; i++) {
                    int packed = site.positions()[i];
                    positions[i] = site.origin().offset(BlueprintGeometry.x(packed), BlueprintGeometry.y(packed), BlueprintGeometry.z(packed));
                    int state = site.states()[i];
                    states[i] = state >= 0 && state < palette.length ? palette[state] : Block.stateById(0);
                }
                SITES.put(site.id(), new Site(positions, states, BitSet.valueOf(site.remaining())));
            }
            for (GhostsPayload.Progress progress : payload.progress()) {
                Site site = SITES.get(progress.id());
                if (site != null) {
                    site.setRemaining(BitSet.valueOf(progress.remaining()));
                }
            }
            payload.removed().forEach(SITES::remove);
        });
    }

    private static void extract(ExtractLevelRenderStateEvent event) {
        LevelRenderState state = event.getRenderState();
        ClientLevel level = event.getLevel();
        if (SITES.isEmpty() || level == null) {
            state.setRenderData(GHOSTS, null);
            return;
        }
        Vec3 camera = event.getCamera().position();
        Frustum frustum = event.getFrustum();
        List<BlueprintClient.Ghost> ghosts = new ArrayList<>();
        double view = VIEW_DISTANCE * VIEW_DISTANCE;
        for (Site site : SITES.values()) {
            // 整个工地都在视野外或者太远时跳过；整个都在视野里时不用一格格查（ADR-0038）
            if (site.box == null || site.bounds == null || site.bounds.distanceToSqr(camera) > view) {
                continue;
            }
            int visibility = frustum.cubeInFrustum(site.box);
            if (visibility != FrustumIntersection.INSIDE && visibility != FrustumIntersection.INTERSECT) {
                continue;
            }
            boolean inside = visibility == FrustumIntersection.INSIDE;
            for (int i = site.remaining.nextSetBit(0); i >= 0 && ghosts.size() < MAX_GHOSTS; i = site.remaining.nextSetBit(i + 1)) {
                BlockPos pos = site.positions[i];
                double dx = pos.getX() + 0.5 - camera.x;
                double dy = pos.getY() + 0.5 - camera.y;
                double dz = pos.getZ() + 0.5 - camera.z;
                if (dx * dx + dy * dy + dz * dz > view || !inside && !frustum.isVisible(new AABB(pos))) {
                    continue;
                }
                BlockState target = site.states[i];
                BlockState current = level.getBlockState(pos);
                if (current == target) {
                    continue;
                }
                int color = current.canBeReplaced() ? SITE : BlueprintClient.BLOCKED;
                ghosts.add(new BlueprintClient.Ghost((float) (pos.getX() - camera.x), (float) (pos.getY() - camera.y), (float) (pos.getZ() - camera.z),
                        BlueprintClient.parts(target), site.hidden[i], color));
            }
        }
        state.setRenderData(GHOSTS, ghosts.isEmpty() ? null : ghosts);
    }
}
