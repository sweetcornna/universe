package io.github.sweetcornna.universe.client.sky;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;

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

    /** 星球的参数块（布局见 {@code sky_planet.fsh}）。 */
    static final BindGroupLayout PLANET_INFO = BindGroupLayout.builder().withUniform("PlanetInfo", UniformType.UNIFORM_BUFFER).build();

    /** 光线求交画的星球、大气和环（ADR-0021）：预乘透明度，大气只加亮，星球本体挡住后面的星星。 */
    static final RenderPipeline PLANET = RenderPipeline.builder(SKY_SNIPPET)
            .withLocation(Universe.id("pipeline/sky_planet"))
            .withVertexShader(Universe.id("core/sky_planet"))
            .withFragmentShader(Universe.id("core/sky_planet"))
            .withBindGroupLayout(PLANET_INFO)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
            .withVertexBinding(0, DefaultVertexFormat.POSITION)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .build();

    private SkyPipelines() {
    }

    static void register(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(POINTS);
        event.registerPipeline(STAR);
        event.registerPipeline(PLANET);
    }
}
