#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>

// 恒星：带临边昏暗的圆盘、日冕和大气散射出的光晕，叠加混合。
// ColorModulator：rgb 是恒星的颜色，a 是整体亮度（下雨、落到地平线以下时变暗）
// ModelOffset：x 是大气散射强度（真空为 0），y 是四边形半宽与恒星半径之比
layout(location = 0) in vec2 discCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    float extent = ModelOffset.y;
    float scatter = ModelOffset.x;
    float r = length(discCoord) * extent;
    vec3 tint = ColorModulator.rgb;

    // 临边昏暗：边缘更暗、更偏恒星本身的颜色
    float mu = sqrt(max(1.0 - r * r, 0.0));
    float limb = 0.45 + 0.55 * pow(mu, 0.6);
    vec3 core = mix(tint, vec3(1.0), 0.35 + 0.45 * mu) * limb * 1.6;

    float d = max(r - 1.0, 0.0);
    // 光晕按四边形的比例衰减（大小由 CPU 按角度定），日冕按恒星半径衰减
    float q = d / max(extent - 1.0, 1e-3);
    // 真空里是紧贴的日冕和一点眩光；有大气时散射出大片光晕
    float corona = 0.5 * exp(-d * 6.0) + 0.16 * exp(-d * 1.8) + 0.05 * exp(-q * 5.0);
    float halo = scatter * (0.32 * exp(-q * 5.0) + 0.10 * exp(-q * 1.6));
    vec3 glow = tint * (corona + halo);

    float w = max(fwidth(r), 1e-4);
    float inside = 1.0 - smoothstep(1.0 - w, 1.0 + w, r);
    vec3 color = mix(glow, core, inside);

    // 四边形的边缘淡出，看不出方框
    float edge = 1.0 - smoothstep(0.7, 1.0, length(discCoord));
    fragColor = vec4(color * edge * ColorModulator.a, 1.0);
}
