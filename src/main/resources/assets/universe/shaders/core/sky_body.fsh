#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>

// 行星和卫星：按真实的光照方向画出相位，表面按星球类型程序生成，可带大气辉光和行星环。
// 坐标系：x、y 在圆盘平面内，z 指向观察者，长度以星球半径为单位。
//
// 参数（每个天体一次绘制，用 DynamicTransforms 里的空位传进来）：
//   ColorModulator    rgb 主色，a 可见度（地平线、下雨、白天冲淡）
//   ModelOffset       x 遮挡度（白天大气里暗面不挡天空），y 四边形半宽与星球半径之比，z 自转角
//   TextureMat[0]     xyz 指向恒星的方向，w 表面类型
//   TextureMat[1]     rgb 第二种颜色，w 种子（0–1）
//   TextureMat[2]     xyz 自转轴（也是环面的法线），w 环的内半径（0 表示没有环）
//   TextureMat[3]     rgb 大气辉光的颜色（已乘强度，没有大气为 0），w 环的外半径
layout(location = 0) in vec2 discCoord;

layout(location = 0) out vec4 fragColor;

const int ROCKY = 1;
const int OCEAN = 2;
const int GAS = 3;
const int ICE = 4;
const int LAVA = 5;

float hash(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}

float noise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(mix(hash(i), hash(i + vec3(1.0, 0.0, 0.0)), f.x),
            mix(hash(i + vec3(0.0, 1.0, 0.0)), hash(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
        mix(mix(hash(i + vec3(0.0, 0.0, 1.0)), hash(i + vec3(1.0, 0.0, 1.0)), f.x),
            mix(hash(i + vec3(0.0, 1.0, 1.0)), hash(i + vec3(1.0, 1.0, 1.0)), f.x), f.y),
        f.z);
}

float fbm(vec3 p, int octaves) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < octaves; i++) {
        value += amplitude * noise(p);
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        amplitude *= 0.5;
    }
    // 各层振幅之和是 1 - 2 * amplitude，除掉后结果在 0–1，平均 0.5
    return value / (1.0 - 2.0 * amplitude);
}

// 观察方向上的球面法线换到星球自己的坐标系：z 沿自转轴，绕轴转过自转角
vec3 bodyFrame(vec3 n, vec3 axis, float spin) {
    vec3 b = normalize(abs(axis.z) < 0.9 ? cross(axis, vec3(0.0, 0.0, 1.0)) : cross(axis, vec3(1.0, 0.0, 0.0)));
    vec3 c = cross(axis, b);
    float x = dot(n, b);
    float y = dot(n, c);
    float cs = cos(spin);
    float sn = sin(spin);
    return vec3(x * cs - y * sn, x * sn + y * cs, dot(n, axis));
}

vec3 surfaceColor(vec3 q, int mode, vec3 mainColor, vec3 band, float seed, out float glow) {
    glow = 0.0;
    vec3 o = vec3(seed * 37.0, seed * 91.0, seed * 13.0);
    if (mode == GAS) {
        // 云带沿纬度排开，湍流把边缘搅乱
        float turbulence = fbm(q * vec3(2.2, 2.2, 0.7) + o, 4);
        float stripes = sin(q.z * (12.0 + seed * 10.0) + turbulence * 4.5);
        float fine = 0.3 * sin(q.z * 45.0 + turbulence * 10.0);
        vec3 color = mix(mainColor, band, smoothstep(-0.7, 0.7, stripes + fine));
        // 一个大风暴
        float angle = seed * 6.2831853;
        vec3 spot = normalize(vec3(cos(angle), sin(angle), -0.3 - 0.3 * seed));
        float storm = 1.0 - smoothstep(0.0, 0.16, distance(q, spot) + 0.04 * (turbulence - 0.5));
        return mix(color, mix(band, vec3(0.85, 0.45, 0.3), 0.5), storm * 0.8);
    }
    float height = fbm(q * 2.3 + o, 5);
    if (mode == OCEAN) {
        float land = smoothstep(0.53, 0.56, height);
        vec3 color = mix(mainColor, band, land);
        // 近岸的浅海亮一些
        float shallow = (1.0 - land) * smoothstep(0.47, 0.53, height);
        color = mix(color, mix(mainColor, vec3(0.8, 0.9, 1.0), 0.35), shallow * 0.6);
        float cap = smoothstep(0.8, 0.88, abs(q.z) + (height - 0.5) * 0.25);
        return mix(color, vec3(0.95), cap);
    }
    if (mode == ICE) {
        float crack = 1.0 - smoothstep(0.0, 0.025, abs(fbm(q * 3.5 + o, 4) - 0.5));
        vec3 color = mix(mainColor, vec3(0.97), smoothstep(0.4, 0.7, height) * 0.5);
        return mix(color, band, crack * 0.6);
    }
    if (mode == LAVA) {
        // 暗色岩壳上的熔岩裂缝
        glow = 1.0 - smoothstep(0.0, 0.035, abs(fbm(q * 2.0 + o, 4) - 0.5));
        return mainColor * (0.55 + 0.6 * height);
    }
    // 岩质：高地和低地两种颜色，加一些陨石坑似的暗斑
    vec3 color = mix(band, mainColor, smoothstep(0.38, 0.62, height));
    float crater = smoothstep(0.66, 0.72, noise(q * 9.0 + o));
    return color * (1.0 - 0.18 * crater);
}

void main() {
    vec3 light = TextureMat[0].xyz;
    int mode = int(TextureMat[0].w + 0.5);
    vec3 band = TextureMat[1].rgb;
    float seed = TextureMat[1].w;
    vec3 axis = TextureMat[2].xyz;
    float ringInner = TextureMat[2].w;
    vec3 rim = TextureMat[3].rgb;
    float ringOuter = TextureMat[3].w;
    vec3 mainColor = ColorModulator.rgb;
    float visibility = ColorModulator.a;
    float occlusion = ModelOffset.x;
    float extent = ModelOffset.y;
    float spin = ModelOffset.z;
    float rimStrength = length(rim);

    vec2 p = discCoord * extent;
    float r2 = dot(p, p);
    float r = sqrt(r2);
    float w = max(fwidth(r), 1e-4);

    // 球面
    vec3 sphereRgb = vec3(0.0);
    float sphereA = 1.0 - smoothstep(1.0 - w, 1.0 + w, r);
    float zs = -1e9;
    if (sphereA > 0.0) {
        vec3 n = normalize(vec3(p, sqrt(max(1.0 - r2, 0.0))));
        zs = n.z;
        vec3 q = bodyFrame(n, axis, spin);
        float glow;
        vec3 albedo = surfaceColor(q, mode, mainColor, band, seed, glow);
        if (mode == OCEAN) {
            // 云层比地面转得快一点
            vec3 cq = bodyFrame(n, axis, spin * 1.15 + 0.7);
            float clouds = smoothstep(0.5, 0.72, fbm(cq * 3.2 + vec3(seed * 5.0), 5));
            albedo = mix(albedo, vec3(1.0), clouds * 0.85);
        }
        float ndl = dot(n, light);
        float diffuse = clamp(ndl * 1.05 + 0.03, 0.0, 1.0) * smoothstep(-0.04, 0.06, ndl);
        vec3 color = albedo * (diffuse + 0.035);
        // 熔岩的光在夜面最显眼
        color += band * glow * (0.35 + 0.65 * (1.0 - diffuse)) * 1.2;
        // 大气：边缘偏向大气的颜色，只在亮面看得出
        float fresnel = pow(1.0 - n.z, 3.0);
        color = mix(color, rim / max(rimStrength, 1e-4) * 1.4,
                clamp(fresnel * rimStrength * 1.5, 0.0, 1.0) * smoothstep(-0.2, 0.3, ndl));
        sphereRgb = color;
    }

    // 圆盘外的一圈大气辉光：只加亮，不遮挡后面的星星
    vec3 haloRgb = vec3(0.0);
    if (r > 1.0 && rimStrength > 0.0) {
        float halo = exp(-(r - 1.0) / 0.035);
        float lit = clamp(dot(normalize(vec3(p, 0.0)), light) * 0.6 + 0.4, 0.0, 1.0);
        haloRgb = rim * halo * lit;
    }

    // 行星环
    vec3 ringRgb = vec3(0.0);
    float ringA = 0.0;
    float zr = -1e9;
    if (ringOuter > 0.0 && abs(axis.z) > 1e-3) {
        zr = -(axis.x * p.x + axis.y * p.y) / axis.z;
        vec3 point = vec3(p, zr);
        float rr = length(point);
        if (rr > ringInner && rr < ringOuter) {
            float t = (rr - ringInner) / (ringOuter - ringInner);
            float density = 0.55 + 0.45 * sin(t * 37.0 + seed * 20.0) * sin(t * 13.0 + 1.3);
            density *= smoothstep(0.0, 0.06, t) * (1.0 - smoothstep(0.9, 1.0, t));
            // 一道明显的环缝
            density *= 1.0 - 0.85 * (1.0 - smoothstep(0.0, 0.02, abs(t - (0.55 + 0.15 * seed))));
            // 行星挡住阳光投在环上的影子
            float along = dot(point, light);
            float shadow = (along < 0.0 && length(point - along * light) < 1.0) ? 0.15 : 1.0;
            float lit = (0.35 + 0.65 * abs(dot(axis, light))) * shadow;
            vec3 ringColor = mix(band, vec3(0.92, 0.88, 0.8), 0.45);
            float aa = 1.0 - smoothstep(ringOuter - w, ringOuter + w, rr);
            ringA = clamp(density, 0.0, 1.0) * 0.85 * aa;
            ringRgb = ringColor * lit;
        }
    }

    // 合成（预乘透明度）：环在球前面就盖住球，在后面就被球挡住
    vec3 rgb;
    float a;
    if (ringA > 0.0 && zr > zs) {
        rgb = ringRgb * ringA + sphereRgb * sphereA * (1.0 - ringA);
        a = ringA + sphereA * (1.0 - ringA);
    } else {
        rgb = sphereRgb * sphereA + ringRgb * ringA * (1.0 - sphereA);
        a = sphereA + ringA * (1.0 - sphereA);
    }
    rgb += haloRgb * (1.0 - a);
    // 白天有大气时，暗面被前面散射的天光盖过：降低遮挡，亮面照常叠加
    float edge = 1.0 - smoothstep(0.9, 1.0, max(abs(discCoord.x), abs(discCoord.y)));
    fragColor = vec4(rgb, a * occlusion) * (visibility * edge);
}
