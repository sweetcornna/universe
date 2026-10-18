package io.github.sweetcornna.universe.client.sky;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/**
 * 星球天空用的渲染管线，着色器在 {@code assets/universe/shaders/core/}。都不读写深度，背面不剔除
 * （面向观察者的四边形经过天球转动后朝向不定）。
 */
final class SkyPipelines {
    private static final RenderPipeline.Snippet SKY_SNIPPET = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withCull(false)
            .buildSnippet();

    /** 星星和银河光带：圆形的柔光点，叠加混合。 */
    static final RenderPipeline POINTS = RenderPipeline.builder(SKY_SNIPPET)
            .withLocation(Universe.id("pipeline/sky_points"))
            .withVertexShader(Universe.id("core/sky_point"))
            .withFragmentShader(Universe.id("core/sky_point"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .build();

    /** 恒星：圆盘、日冕和光晕。预乘透明度，发光的部分 alpha 为 0 等于直接加亮，黑洞的阴影挡住后面的星空。 */
    static final RenderPipeline STAR = RenderPipeline.builder(SKY_SNIPPET)
            .withLocation(Universe.id("pipeline/sky_star"))
            .withVertexShader(Universe.id("core/sky_body"))
            .withFragmentShader(Universe.id("core/sky_star"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .build();

    /** 行星和卫星：预乘透明度，暗面能挡住后面的星星，亮面叠加在天空上。 */
    static final RenderPipeline BODY = RenderPipeline.builder(SKY_SNIPPET)
            .withLocation(Universe.id("pipeline/sky_body"))
            .withVertexShader(Universe.id("core/sky_body"))
            .withFragmentShader(Universe.id("core/sky_body"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .build();

    private SkyPipelines() {
    }

    static void register(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(POINTS);
        event.registerPipeline(STAR);
        event.registerPipeline(BODY);
    }
}
