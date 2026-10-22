package io.github.sweetcornna.universe.client.civ;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.civ.Race;
import io.github.sweetcornna.universe.entity.civ.AlienNpc;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * 外星 NPC 的渲染器（ADR-0027 §一）：每个种族一副模型、一张贴图和一层发光（眼睛、纹样、面罩），提交之前把 {@link #model}
 * 换成这个种族的。每个人按种子乘一点色差，同一个种族站一排也不会一模一样。
 */
public class AlienNpcRenderer extends MobRenderer<AlienNpc, AlienNpcRenderState, AlienNpcModel> {
    /** 个体的色差：都很淡，只是看得出不是同一个人。 */
    private static final int[] TINTS = {0xFFFFFF, 0xF2F6FF, 0xFFF2E6, 0xEEFFF2, 0xF6F0FF, 0xFFFFEE};
    private final Map<Race, AlienNpcModel> models = new EnumMap<>(Race.class);

    public AlienNpcRenderer(EntityRendererProvider.Context context) {
        super(context, AlienNpcModel.create(Race.TRADER), 0.4F);
        for (Race race : Race.values()) {
            this.models.put(race, AlienNpcModel.create(race));
        }
        this.addLayer(new GlowLayer(this));
    }

    private static Identifier skin(Race race) {
        return Universe.id("textures/entity/alien_npc_" + race.id() + ".png");
    }

    private static Identifier glow(Race race) {
        return Universe.id("textures/entity/alien_npc_" + race.id() + "_glow.png");
    }

    @Override
    public AlienNpcRenderState createRenderState() {
        return new AlienNpcRenderState();
    }

    @Override
    public void extractRenderState(AlienNpc entity, AlienNpcRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.race = entity.race();
        state.gesture = entity.gesture(partialTicks) / AlienNpc.GESTURE_TICKS;
        long seed = entity.seed();
        state.phase = Math.floorMod(seed, 1000) / 1000F * Mth.TWO_PI * 8;
        state.tint = TINTS[(int) Math.floorMod(seed >>> 7, (long) TINTS.length)];
    }

    @Override
    protected int getModelTint(AlienNpcRenderState state) {
        return 0xFF000000 | state.tint;
    }

    @Override
    public void submit(AlienNpcRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        this.model = this.models.get(state.race);
        super.submit(state, poseStack, collector, camera);
    }

    @Override
    public Identifier getTextureLocation(AlienNpcRenderState state) {
        return skin(state.race);
    }

    @Override
    protected float getShadowRadius(AlienNpcRenderState state) {
        return state.race == Race.WARDEN ? 0.55F : 0.4F;
    }

    /** 眼睛、纹样、面罩：不受光照影响的发光层，颜色画在贴图里。 */
    static final class GlowLayer extends RenderLayer<AlienNpcRenderState, AlienNpcModel> {
        GlowLayer(RenderLayerParent<AlienNpcRenderState, AlienNpcModel> renderer) {
            super(renderer);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AlienNpcRenderState state, float yRot,
                float xRot) {
            if (state.isInvisible) {
                return;
            }
            collector.order(1).submitModel(this.getParentModel(), state, poseStack, RenderTypes.eyes(glow(state.race)), lightCoords,
                    OverlayTexture.NO_OVERLAY, state.outlineColor);
        }
    }
}
