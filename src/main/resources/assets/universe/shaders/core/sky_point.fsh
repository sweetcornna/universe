#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>

layout(location = 0) in vec2 offset;
layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    float r = length(offset);
    float falloff = exp(-r * r * 4.0) * (1.0 - smoothstep(0.8, 1.0, r));
    vec4 color = vertexColor * ColorModulator;
    fragColor = vec4(color.rgb, color.a * falloff);
}
