#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>

// 恒星（核心层 StarLook）：主序星、红巨星、白矮星、脉冲星、黑洞各有画法。
// 预乘透明度混合：发光的部分 alpha 为 0，等于直接加亮；黑洞的阴影 alpha 为 1，挡住后面的星空。
// 坐标以恒星的半径为单位（黑洞是阴影的半径）。
//   ColorModulator  rgb 恒星的颜色（黑洞是吸积盘外圈的颜色），a 整体亮度（下雨、落到地平线以下时变暗）
//   ModelOffset     x 大气散射强度（真空为 0），y 四边形半宽与半径之比，z 类型
//   TextureMat[0]   rgb 日冕和光晕的颜色，w 时间（秒）
//   TextureMat[1]   x 米粒组织的尺度（白矮星、脉冲星：星云的强度；黑洞：阴影挡住天空的程度），
//                   y 活动性，z 衍射星芒的强度，w 种子
//   TextureMat[2]   脉冲星：x 周期，y 磁轴与自转轴的夹角，z 自转轴与视线的夹角，w 自转轴的方位角
//                   黑洞：x 吸积盘内半径，y 外半径，z 吸积盘法线与视线的夹角，w 方位角
//   TextureMat[3]   rgb 星云外圈的颜色（黑洞：吸积盘内圈的颜色），w 星云的半径（黑洞：吸积盘的转向）
layout(location = 0) in vec2 discCoord;

layout(location = 0) out vec4 fragColor;

const int MAIN_SEQUENCE = 0;
const int RED_GIANT = 1;
const int WHITE_DWARF = 2;
const int PULSAR = 3;
const int BLACK_HOLE = 4;
const float TAU = 6.2831853;

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
    return value / (1.0 - 2.0 * amplitude);
}

vec2 rotate(vec2 p, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return vec2(c * p.x - s * p.y, s * p.x + c * p.y);
}

// 圆盘内外的抗锯齿过渡：圆盘里为 1
float insideDisc(float r) {
    float w = max(fwidth(r), 1e-4);
    return 1.0 - smoothstep(1.0 - w, 1.0 + w, r);
}

// 衍射星芒：十字形的细长光芒，越远越淡
float spikes(vec2 p, float strength) {
    vec2 q = abs(rotate(p, 0.785398));
    float reach = 14.0 * strength;
    float a = exp(-q.y * q.y / (0.02 + 0.004 * q.x)) * exp(-3.0 * q.x / reach);
    float b = exp(-q.x * q.x / (0.02 + 0.004 * q.y)) * exp(-3.0 * q.y / reach);
    return strength * 0.8 * (a + b) * smoothstep(0.8, 1.6, length(p));
}

// 主序星和红巨星的表面：临边昏暗、米粒组织、黑子，慢慢自转
vec3 surface(vec2 p, float r, vec3 tint, float granulation, float activity, float spotDepth, float seed, float time, bool giant) {
    float mu = sqrt(max(1.0 - r * r, 0.0));
    float spin = time * (giant ? 0.003 : 0.015) + seed * TAU;
    vec3 n = vec3(p, mu);
    vec3 q = vec3(n.x * cos(spin) + n.z * sin(spin), n.y, -n.x * sin(spin) + n.z * cos(spin));
    vec3 o = vec3(seed * 31.0, seed * 17.0, seed * 53.0);
    float drift = time * (giant ? 0.006 : 0.03);
    float grain;
    if (giant) {
        // 红巨星的对流斑只有几十个，大得能看出明暗起伏
        grain = 0.45 + 1.1 * smoothstep(0.25, 0.75, fbm(q * 2.6 + o + vec3(drift), 3));
    } else {
        float cells = fbm(q * mix(30.0, 12.0, granulation) + o + vec3(drift), 3);
        grain = 1.0 + (cells - 0.5) * (0.08 + 0.25 * granulation);
    }
    // 黑子成群出现在中低纬度：暗的本影外面围一圈半影
    float spots = 0.0;
    if (activity > 0.0) {
        float belt = 1.0 - smoothstep(0.25, 0.6, abs(q.y));
        float s = fbm(q * 5.0 + o * 1.7, 3);
        float umbra = smoothstep(0.75 - 0.05 * activity, 0.78 - 0.05 * activity, s);
        float penumbra = smoothstep(0.70 - 0.05 * activity, 0.75 - 0.05 * activity, s);
        spots = (0.4 * penumbra + 0.45 * umbra) * belt * spotDepth;
    }
    float limb = giant ? 0.2 + 0.8 * pow(mu, 0.8) : 0.45 + 0.55 * pow(mu, 0.6);
    if (giant) {
        // 红巨星不往白里调，反而压深，整个是橙红色，边缘更红
        return tint * tint * mix(tint, vec3(1.0), mu) * limb * grain * 1.1;
    }
    // 边缘更暗、更偏恒星本身的颜色
    vec3 color = mix(tint, vec3(1.0), 0.1 + 0.35 * mu);
    return color * limb * grain * (1.0 - spots) * 1.15;
}

// 日冕和光晕：真空里紧贴恒星，有大气时散射出大片光晕（大小由 CPU 按角度定，这里按四边形的比例衰减）
vec3 corona(float r, float extent, float scatter, vec3 glow, bool giant) {
    float d = max(r - 1.0, 0.0);
    float q = d / max(extent - 1.0, 1e-3);
    float c = giant
            ? 0.35 * exp(-d * 3.0) + 0.14 * exp(-d * 0.9) + 0.04 * exp(-q * 4.0)
            : 0.5 * exp(-d * 6.0) + 0.16 * exp(-d * 1.8) + 0.05 * exp(-q * 5.0);
    float halo = scatter * (0.32 * exp(-q * 5.0) + 0.10 * exp(-q * 1.6));
    return glow * (c + halo);
}

// 致密天体的光：一个很亮的小圆盘，外面是缓慢衰减的辉光
vec3 compactGlow(float r, float extent, float scatter, vec3 tint, vec3 glow, float boost) {
    float d = max(r - 1.0, 0.0);
    float q = d / max(extent - 1.0, 1e-3);
    vec3 core = mix(tint, vec3(1.0), 0.6) * 1.6;
    vec3 halo = glow * ((0.9 * exp(-d * 2.2) + 0.25 / (1.0 + d * d * 0.3) * (1.0 - q)) * boost + scatter * 0.3 * exp(-q * 4.0));
    return mix(halo, core, insideDisc(r));
}

// 行星状星云：一层发光的空心球壳投影成的圆环，里面青绿、外圈偏红，带细丝
vec3 planetaryNebula(vec2 p, float radius, vec3 outer, float seed, float time) {
    float r = length(p) / radius;
    float shell = sqrt(max(1.0 - r * r, 0.0)) - sqrt(max(0.3 - r * r, 0.0));
    float n = fbm(vec3(p / radius * 3.0, seed * 7.0 + time * 0.002), 4);
    vec3 color = mix(vec3(0.25, 0.85, 0.75), outer, smoothstep(0.55, 0.95, r));
    return color * shell * (0.4 + 0.9 * n) * 0.35;
}

// 脉冲星星风云（像蟹状星云）：中间一团同步辐射的蓝光，外面缠着橙色的细丝
vec3 windNebula(vec2 p, float radius, vec3 outer, float seed) {
    float r = length(p) / radius;
    float body = exp(-r * r * 2.2);
    float n = fbm(vec3(p / radius * 4.0, seed * 5.0), 5);
    float threads = pow(1.0 - abs(n * 2.0 - 1.0), 6.0);
    vec3 core = vec3(0.45, 0.6, 1.0) * body * 0.45;
    vec3 strands = outer * threads * (1.0 - smoothstep(0.3, 1.1, r)) * 0.6;
    return (core + strands) * 0.5;
}

// 脉冲星：磁轴绕自转轴转，两道光束跟着扫过天空；光束扫到观察者时一闪
vec3 pulsar(vec2 p, float r, float extent, float scatter, vec3 tint, vec3 glow, float time, vec4 axis) {
    float tilt = axis.z;
    vec3 s = vec3(sin(tilt) * cos(axis.w), sin(tilt) * sin(axis.w), cos(tilt));
    vec3 u = normalize(cross(s, abs(s.z) < 0.9 ? vec3(0.0, 0.0, 1.0) : vec3(1.0, 0.0, 0.0)));
    vec3 v = cross(s, u);
    float phase = time / axis.x * TAU;
    vec3 m = cos(axis.y) * s + sin(axis.y) * (cos(phase) * u + sin(phase) * v);
    float flash = pow(abs(m.z), 24.0);
    // 光束投到画面上，朝着观察者时缩短
    float shown = max(length(m.xy), 1e-3);
    vec2 dir = m.xy / shown;
    float along = abs(dot(p, dir));
    vec2 offset = p - dot(p, dir) * dir;
    float width = 0.35 + 0.07 * along;
    float reach = max(36.0 * shown, 0.5);
    float beam = exp(-dot(offset, offset) / (width * width)) * exp(-2.5 * along / reach) * smoothstep(0.5, 2.0, along);
    vec3 beamColor = mix(glow, vec3(0.75, 0.55, 1.0), 0.4);
    return compactGlow(r, extent, scatter, tint, glow, 1.0 + 2.0 * flash) + beamColor * beam * (0.5 + 1.5 * flash);
}

// 吸积盘上一点：内圈热、发白，外圈冷、发红；条纹按开普勒速度旋转，越靠里转得越快
vec3 disk(float rd, float phi, float inner, float outer, vec3 hot, vec3 cool, float spin, float time, float seed) {
    if (rd < inner * 0.85 || rd > outer * 1.05) {
        return vec3(0.0);
    }
    float t = clamp((rd - inner) / (outer - inner), 0.0, 1.0);
    vec3 color = mix(hot, cool, smoothstep(0.0, 0.8, t));
    float bright = 1.7 * pow(1.0 - t, 1.6) + 0.1;
    float omega = 0.5 * pow(max(rd / inner, 1.0), -1.5);
    float a = phi - spin * time * omega;
    float streak = fbm(vec3(rd * 3.0 + seed * 13.0, cos(a) * 1.6, sin(a) * 1.6), 3);
    float fade = smoothstep(inner * 0.85, inner * 1.08, rd) * (1.0 - smoothstep(outer * 0.75, outer * 1.05, rd));
    return color * bright * (0.45 + 1.1 * streak) * fade;
}

// 多普勒效应：转向观察者的一侧更亮
float doppler(float side, float spin) {
    float g = 1.0 - 0.45 * spin * side;
    return g * g;
}

// 黑洞：黑色的阴影、贴着阴影的光子环；吸积盘接近侧视，近的一半横在阴影前面，
// 远的一半被引力透镜弯到阴影上方（下方还有一道细的）
void blackHole(vec2 p, vec3 cool, vec3 hot, vec4 shape, float spin, float time, float seed, float occlusion,
        out vec3 color, out float alpha) {
    float inner = shape.x;
    float outer = shape.y;
    // 四边形的 v 轴朝下，翻过来让 y 朝上
    vec2 q = rotate(p, -shape.w) * vec2(1.0, -1.0);
    float rho = length(q);
    float ci = max(cos(shape.z), 0.06);

    // 盘面在画面上是个压扁的椭圆，展开回盘面上的坐标
    vec2 dp = vec2(q.x, q.y / ci);
    float rd = length(dp);
    float phi = atan(dp.y, dp.x);
    vec3 primary = disk(rd, phi, inner, outer, hot, cool, spin, time, seed) * doppler(dp.x / max(rd, 1e-3), spin);
    float sheet = smoothstep(outer * 1.05, outer * 0.85, rd) * smoothstep(inner * 0.8, inner, rd);
    float near = step(q.y, 0.0);

    // 弯过来的远侧：阴影上方一道宽弧，下方一道窄弧，两边并进盘面
    float theta = atan(q.x, q.y);
    float up = cos(theta);
    float span = (outer - inner) / 4.5;
    float height = span * (up > 0.0 ? 0.7 * pow(up, 0.5) : 0.25 * pow(-up, 0.5)) + 0.06;
    float t = (rho - 1.04) / height;
    vec3 arch = vec3(0.0);
    if (t > 0.0 && t < 1.0) {
        float lensed = inner + t * (outer - inner) * 0.65;
        arch = disk(lensed, theta + 1.5708, inner, outer, hot, cool, spin, time, seed) * doppler(q.x / max(rho, 1e-3), spin)
                * smoothstep(1.0, 0.7, t) * 0.85;
    }

    float ring = exp(-pow((rho - 1.025) / 0.018, 2.0)) * (0.3 + 0.9 * doppler(q.x / max(rho, 1e-3), spin));
    float shadow = insideDisc(rho);
    vec3 behind = (arch + primary * (1.0 - near)) * (1.0 - shadow) + hot * ring * 1.2;
    float nearOpacity = near * sheet * 0.9;
    color = primary * near + behind * (1.0 - nearOpacity);
    // 盘外一圈淡淡的散射光
    color += cool * 0.06 * exp(-max(rho - 1.0, 0.0) * 0.8) * (1.0 - shadow);
    alpha = max(shadow, nearOpacity * 0.5) * occlusion;
}

void main() {
    float extent = ModelOffset.y;
    float scatter = ModelOffset.x;
    int style = int(ModelOffset.z + 0.5);
    vec2 p = discCoord * extent;
    float r = length(p);
    vec3 tint = ColorModulator.rgb;
    vec3 glow = TextureMat[0].rgb;
    float time = TextureMat[0].w;
    float first = TextureMat[1].x;
    float seed = TextureMat[1].w;

    vec3 color = vec3(0.0);
    // 恒星的圆盘挡住后面的星空和星球
    float alpha = insideDisc(r);
    if (style == BLACK_HOLE) {
        blackHole(p, tint, TextureMat[3].rgb, TextureMat[2], TextureMat[3].w, time, seed, first, color, alpha);
    } else if (style == PULSAR) {
        color = pulsar(p, r, extent, scatter, tint, glow, time, TextureMat[2]);
        color += windNebula(p, TextureMat[3].w, TextureMat[3].rgb, seed) * first;
    } else if (style == WHITE_DWARF) {
        color = compactGlow(r, extent, scatter, tint, glow, 1.0);
        if (first > 0.0) {
            color += planetaryNebula(p, TextureMat[3].w, TextureMat[3].rgb, seed, time) * first;
        }
    } else {
        bool giant = style == RED_GIANT;
        // 我的世界的样子：圆盘按像素块画，边缘是一级级的台阶，像原版的太阳贴图；外面的日冕和光晕还是平滑的（ADR-0021）。
        // 一块三四个像素，取 2 的幂，圆盘至少分成十六块宽
        float cell = exp2(ceil(log2(max(max(fwidth(p.x), fwidth(p.y)) * 3.5, 1e-5))));
        cell = min(cell, 0.125);
        vec2 pq = (floor(p / cell) + 0.5) * cell;
        float rq = length(pq);
        float edgeRadius = r;
        float edgeBlock = rq;
        if (giant) {
            // 红巨星的边缘不是正圆，被对流顶得起伏
            vec2 around = pq / max(rq, 1e-3);
            float wobble = fbm(vec3(around * 2.0 + seed * 9.0, time * 0.01), 3) - 0.5;
            edgeRadius = r / (1.0 + 0.05 * wobble);
            edgeBlock = rq / (1.0 + 0.05 * wobble);
        }
        // 真空里直接看恒星，圆盘亮得发白，黑子几乎看不出；隔着大气才看得清
        float spotDepth = mix(0.2, 1.0, clamp(scatter * 2.0, 0.0, 1.0));
        vec3 core = surface(pq, min(edgeBlock, 1.0), tint, first, TextureMat[1].y, spotDepth, seed, time, giant);
        alpha = step(edgeBlock, 1.0);
        color = mix(corona(max(edgeRadius, 1.0), extent, scatter, glow, giant), core, alpha);
    }
    if (TextureMat[1].z > 0.0) {
        color += mix(glow, vec3(1.0), 0.5) * spikes(p, TextureMat[1].z);
    }

    // 四边形的边缘淡出，看不出方框
    float edge = 1.0 - smoothstep(0.7, 1.0, length(discCoord));
    float brightness = ColorModulator.a * edge;
    fragColor = vec4(color * brightness, alpha * brightness);
}
