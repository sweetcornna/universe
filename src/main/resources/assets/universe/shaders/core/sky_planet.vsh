#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

// 光线求交画星球（ADR-0021）。顶点是单位四边形或者包住摄像机的立方体，QuadMat 把它放到摄像机周围的世界方向上；
// 片元着色器拿到的是世界坐标里的视线方向。参数块的说明见 sky_planet.fsh。
layout(std140) uniform PlanetInfo {
    mat4 QuadMat;
    mat4 BodyRot;
    vec4 Center;
    vec4 Sun;
    vec4 SunColor;
    vec4 Style;
    vec4 ColorA;
    vec4 ColorB;
    vec4 ColorC;
    vec4 Atmos;
    vec4 Haze;
    vec4 Rings;
    vec4 RingColor;
    vec4 Extra;
};

layout(location = 0) in vec3 Position;

layout(location = 0) out vec3 worldDir;

void main() {
    vec4 world = QuadMat * vec4(Position, 1.0);
    worldDir = world.xyz;
    gl_Position = ProjMat * ModelViewMat * world;
}
