package io.github.sweetcornna.universe.client.darkfog;

import com.mojang.blaze3d.vertex.PoseStack;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogUnit;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 黑雾单位的渲染器：突袭者、游骑兵、守卫者各一个模型和贴图。发光层是暗红的（守卫者偏紫），打人、打设施时更亮；
 * 突袭者放电时从放电叉画一道电弧连到目标上。
 */
public class DarkFogUnitRenderer extends MobRenderer<DarkFogUnit, DarkFogUnitRenderState, DarkFogUnitModel> {
    static final int RED = 0xFF2A48;
    static final int VIOLET = 0xC040FF;
    private static final int ARC_COLOR = 0xFF5AB4;
    private final Identifier texture;
    private final Identifier glow;
    private final int color;

    private DarkFogUnitRenderer(EntityRendererProvider.Context context, DarkFogUnitModel model, String name, float shadow, int color) {
        super(context, model, shadow);
        this.texture = Universe.id("textures/entity/" + name + ".png");
        this.glow = Universe.id("textures/entity/" + name + "_glow.png");
        this.color = color;
        this.addLayer(new GlowLayer(this));
    }

    public static DarkFogUnitRenderer raider(EntityRendererProvider.Context context) {
        return new DarkFogUnitRenderer(context, DarkFogUnitModel.raider(), "dark_fog_raider", 0.6F, RED);
    }

    public static DarkFogUnitRenderer ranger(EntityRendererProvider.Context context) {
        return new DarkFogUnitRenderer(context, DarkFogUnitModel.ranger(), "dark_fog_ranger", 0.4F, RED);
    }

    public static DarkFogUnitRenderer guardian(EntityRendererProvider.Context context) {
        return new DarkFogUnitRenderer(context, DarkFogUnitModel.guardian(), "dark_fog_guardian", 0.7F, VIOLET);
    }

    @Override
    public DarkFogUnitRenderState createRenderState() {
        return new DarkFogUnitRenderState();
    }

    @Override
    public void extractRenderState(DarkFogUnit entity, DarkFogUnitRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.kind = entity.kind();
        state.bobPhase = (entity.getId() * 37 % 100) / 100F * Mth.TWO_PI;
        state.angry = entity.attackingFacility() || entity.arcEntity() >= 0 || entity.arcBlock().isPresent();
        state.arc = arcEnd(entity, partialTicks);
    }

    /** 电弧的终点，相对于单位的位置（加上了机身中间的高度）。 */
    private static Vec3 arcEnd(DarkFogUnit unit, float partialTicks) {
        Vec3 origin = unit.getPosition(partialTicks);
        int id = unit.arcEntity();
        if (id >= 0) {
            Entity target = unit.level().getEntity(id);
            if (target != null) {
                return target.getPosition(partialTicks).add(0, target.getBbHeight() * 0.5, 0).subtract(origin);
            }
        }
        return unit.arcBlock().map(pos -> Vec3.atCenterOf(pos).subtract(origin)).orElse(null);
    }

    @Override
    protected AABB getBoundingBoxForCulling(DarkFogUnit entity, float partialTicks) {
        AABB box = super.getBoundingBoxForCulling(entity, partialTicks);
        Vec3 arc = arcEnd(entity, partialTicks);
        return arc == null ? box : box.minmax(new AABB(entity.position(), entity.position().add(arc)));
    }

    @Override
    public void submit(DarkFogUnitRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        super.submit(state, poseStack, collector, camera);
        if (state.arc != null) {
            Vec3 origin = new Vec3(state.x, state.y, state.z);
            Vec3 from = new Vec3(0, state.boundingBoxHeight * 0.45, 0);
            DarkFogBeams.submit(poseStack, collector, from, state.arc, camera.pos.subtract(origin), ARC_COLOR, 0.18F, true, state.ageInTicks);
        }
    }

    @Override
    public Identifier getTextureLocation(DarkFogUnitRenderState state) {
        return this.texture;
    }

    @Override
    protected int getBlockLightLevel(DarkFogUnit entity, BlockPos pos) {
        return Math.max(6, super.getBlockLightLevel(entity, pos));
    }

    private static final class GlowLayer extends RenderLayer<DarkFogUnitRenderState, DarkFogUnitModel> {
        private final DarkFogUnitRenderer renderer;

        GlowLayer(DarkFogUnitRenderer renderer) {
            super(renderer);
            this.renderer = renderer;
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, DarkFogUnitRenderState state, float yRot, float xRot) {
            if (state.isInvisible) {
                return;
            }
            float pulse = state.angry ? 0.85F + 0.15F * Mth.sin(state.ageInTicks * 0.9F) : 0.65F + 0.1F * Mth.sin(state.ageInTicks * 0.15F);
            int color = scale(this.renderer.color, pulse);
            collector.order(1).submitModel(this.getParentModel(), state, poseStack, RenderTypes.eyes(this.renderer.glow),
                    lightCoords, OverlayTexture.NO_OVERLAY, 0xFF000000 | color, null, state.outlineColor);
        }
    }

    static int scale(int rgb, float f) {
        int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * f));
        int g = Math.min(255, (int) (((rgb >> 8) & 0xFF) * f));
        int b = Math.min(255, (int) ((rgb & 0xFF) * f));
        return (r << 16) | (g << 8) | b;
    }
}
