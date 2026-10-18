package io.github.sweetcornna.universe.client.dyson;

import java.util.List;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.dyson.RayReceiverBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

/**
 * 射线接收站：碟面（连同接收针）转向恒星，最多倾斜 75°；恒星落山后慢慢转回朝天。在接收时，接收针顶上射出一道
 * 指向恒星的光束（借用信标光束的画法）：发电模式是金色，光子模式是紫色。
 */
public class ReceiverRenderer implements BlockEntityRenderer<RayReceiverBlockEntity, ReceiverRenderer.State> {
    public static final StandaloneModelKey<BlockStateModelPart> DISH = new StandaloneModelKey<>(() -> "universe:ray_receiver_dish");
    private static final float PIVOT_Y = 10.5F / 16;
    /** 接收针顶端离转轴多远（沿碟面法线）。 */
    private static final float CORE = 6.25F / 16;
    private static final float MAX_TILT = 75;
    private static final int POWER_BEAM = 0xFFFFD27A;
    private static final int PHOTON_BEAM = 0xFFC08CFF;

    public ReceiverRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static void registerModels(ModelEvent.RegisterStandalone event) {
        event.register(DISH, SimpleUnbakedStandaloneModel.simpleModelWrapper(Universe.id("block/ray_receiver_dish")));
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(RayReceiverBlockEntity blockEntity, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        Level level = blockEntity.getLevel();
        if (level == null) {
            return;
        }
        Vec3 sun = ClientSun.direction(level, blockEntity.getBlockPos());
        state.rotation = facing(sun, ClientSun.space(level));
        state.receiving = blockEntity.receiving();
        state.photon = blockEntity.photonMode();
        state.animationTime = Math.floorMod(level.getGameTime(), 40) + partialTicks;
        state.lightCoords = LightCoordsUtil.getLightCoords(level, blockEntity.getBlockPos().above());
    }

    /** 把碟面的法线（+Y）转到哪个方向：对着恒星，倾角有上限；恒星低于 15° 以后渐渐转回朝天。 */
    static Quaternionf facing(Vec3 sun, boolean space) {
        Vector3f up = new Vector3f(0, 1, 0);
        Vector3f horizontal = new Vector3f((float) sun.x, 0, (float) sun.z);
        if (horizontal.lengthSquared() < 1.0E-6F) {
            return new Quaternionf();
        }
        horizontal.normalize();
        double elevation = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, sun.y))));
        double tilt;
        if (space) {
            tilt = Math.min(100, 90 - elevation);
        } else if (elevation >= 90 - MAX_TILT) {
            tilt = 90 - elevation;
        } else {
            // 15° 到 -30°：从最大倾角平滑地回到 0
            double t = Math.max(0, Math.min(1, (elevation + 30) / (90 - MAX_TILT + 30)));
            tilt = MAX_TILT * t * t * (3 - 2 * t);
        }
        Vector3f axis = new Vector3f(up).cross(horizontal).normalize();
        return new Quaternionf().rotationAxis((float) Math.toRadians(tilt), axis);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        BlockStateModelPart dish = Minecraft.getInstance().getModelManager().getStandaloneModel(DISH);
        if (dish == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5F, PIVOT_Y, 0.5F);
        poseStack.rotate(state.rotation);
        poseStack.pushPose();
        poseStack.translate(-0.5F, -PIVOT_Y, -0.5F);
        collector.submitBlockModel(poseStack, RenderTypes.cutoutMovingBlock(), List.of(dish), new int[0], state.lightCoords,
                OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
        if (state.receiving) {
            poseStack.translate(-0.5F, CORE, -0.5F);
            BeaconRenderer.submitBeaconBeam(poseStack, collector, BeaconRenderer.BEAM_LOCATION, 1.0F, state.animationTime, 0, 384,
                    state.photon ? PHOTON_BEAM : POWER_BEAM, 0.05F, 0.11F);
        }
        poseStack.popPose();
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(RayReceiverBlockEntity blockEntity) {
        // 光束指向恒星，伸得很远
        return blockEntity.receiving() ? AABB.INFINITE : new AABB(blockEntity.getBlockPos()).inflate(0.5, 0, 0.5).expandTowards(0, 0.5, 0);
    }

    public static class State extends BlockEntityRenderState {
        Quaternionf rotation = new Quaternionf();
        boolean receiving;
        boolean photon;
        float animationTime;
    }
}
