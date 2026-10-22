#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>
#include <minecraft:projection.glsl>

// 光线求交画星球（ADR-0021）：从摄像机沿视线和星球求交，按 NASA 的照片做每种星球的表面，
// 大气做单次散射（地平线上的蓝线、晨昏线发红、越靠边越朦胧），行星环有缝、有影子、逆光时发亮。
//
// 长度以星球（云顶）的半径为单位，摄像机在原点，方向都在世界坐标里。参数块：
//   QuadMat    单位几何 → 摄像机周围的世界坐标（只在顶点着色器里用）
//   BodyRot    世界方向 → 星球自己的坐标（z 是自转轴，已转过自转角），只用左上 3×3
//   Center     xyz 星球中心，w 可见度（地平线、天气、白天冲淡）
//   Sun        xyz 从星球指向恒星，w 光照强度
//   SunColor   rgb 恒星光的颜色（线性），w 白天天光挡住多少（太空里是 1）
//   Style      x 表面画法，y 种子（0–1），z 云量，w 海平面
//   ColorA/B/C rgb 三种颜色（线性），w 依画法而定（A 自发光强度，B 极冠大小，C 地形起伏）
//   Atmos      rgb 瑞利散射的竖直光学厚度，w 大气顶的高度
//   Haze       rgb 雾霾的颜色，w 雾霾的竖直光学厚度
//   Rings      x 内半径，y 外半径，z 不透明度，w 种子
//   RingColor  rgb 环的颜色，w 1 只画环（站在这颗星球上看自己的环）
//   Extra      x 时间（秒），y 极光，z 奇异图案，w 曝光
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

layout(location = 0) in vec3 worldDir;

layout(location = 0) out vec4 fragColor;

const float PI = 3.14159265;

const int EARTH = 1;
const int OCEAN = 2;
const int MOON = 3;
const int MARS = 4;
const int ICE = 5;
const int VOLCANIC = 6;
const int VENUS = 7;
const int HAZE = 8;
const int JUPITER = 9;
const int SATURN = 10;
const int ICE_GIANT = 11;
const int EXOTIC = 12;

// ---------------------------------------------------------------- 噪声

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

vec3 hash33(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx);
}

float hash11(float x) {
    return fract(sin(x * 127.1 + 311.7) * 43758.5453);
}

// 梯度噪声（值噪声在球面上取阈值时会露出方格），结果在 0–1，平均 0.5
float vnoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    float n000 = dot(hash33(i) * 2.0 - 1.0, f);
    float n100 = dot(hash33(i + vec3(1, 0, 0)) * 2.0 - 1.0, f - vec3(1, 0, 0));
    float n010 = dot(hash33(i + vec3(0, 1, 0)) * 2.0 - 1.0, f - vec3(0, 1, 0));
    float n110 = dot(hash33(i + vec3(1, 1, 0)) * 2.0 - 1.0, f - vec3(1, 1, 0));
    float n001 = dot(hash33(i + vec3(0, 0, 1)) * 2.0 - 1.0, f - vec3(0, 0, 1));
    float n101 = dot(hash33(i + vec3(1, 0, 1)) * 2.0 - 1.0, f - vec3(1, 0, 1));
    float n011 = dot(hash33(i + vec3(0, 1, 1)) * 2.0 - 1.0, f - vec3(0, 1, 1));
    float n111 = dot(hash33(i + vec3(1, 1, 1)) * 2.0 - 1.0, f - vec3(1, 1, 1));
    float n = mix(mix(mix(n000, n100, u.x), mix(n010, n110, u.x), u.y), mix(mix(n001, n101, u.x), mix(n011, n111, u.x), u.y), u.z);
    return clamp(n * 0.85 + 0.5, 0.0, 1.0);
}

float noise1(float x) {
    float i = floor(x);
    float f = fract(x);
    return mix(hash11(i), hash11(i + 1.0), f * f * (3.0 - 2.0 * f));
}

// 视线在球面上每个像素跨过的弧度；决定噪声画到第几层，远处不闪
float footprint;

// 频率 f 的噪声在这个距离上能画几层
float octaves(float f) {
    return clamp(log2(0.35 / max(footprint * f, 1e-6)), 1.0, 13.0);
}

// 频率 f 的噪声在这个距离上看不看得出来：0 是糊成一片（干脆不画，远处不闪），1 是看得清
float visibleAt(float f) {
    return clamp(log2(0.35 / max(footprint * f, 1e-6)), 0.0, 1.0);
}

float fbm(vec3 p, float oct) {
    float value = 0.0;
    float amplitude = 0.5;
    float total = 0.0;
    for (int i = 0; i < 13; i++) {
        float w = clamp(oct - float(i), 0.0, 1.0);
        if (w <= 0.0) {
            break;
        }
        value += amplitude * w * vnoise(p);
        total += amplitude * w;
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        amplitude *= 0.5;
    }
    return value / max(total, 1e-4);
}

float ridged(vec3 p, float oct) {
    float value = 0.0;
    float amplitude = 0.5;
    float total = 0.0;
    for (int i = 0; i < 10; i++) {
        float w = clamp(oct - float(i), 0.0, 1.0);
        if (w <= 0.0) {
            break;
        }
        float n = 1.0 - abs(vnoise(p) * 2.0 - 1.0);
        value += amplitude * w * n * n;
        total += amplitude * w;
        p = p * 2.07 + vec3(4.1, 1.3, 7.7);
        amplitude *= 0.5;
    }
    return value / max(total, 1e-4);
}

vec3 rotateAbout(vec3 p, vec3 axis, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return p * c + cross(axis, p) * s + axis * dot(axis, p) * (1.0 - c);
}

vec3 seedOffset(float k) {
    float s = Style.y * 97.0 + k;
    return vec3(hash11(s), hash11(s + 1.7), hash11(s + 3.1)) * 61.0;
}

// 球面上的一个随机方向，种子相同就相同
vec3 seededDirection(float k) {
    vec3 h = hash33(vec3(Style.y * 53.0 + k, k * 1.37, 7.1)) * 2.0 - 1.0;
    return normalize(h + vec3(1e-4));
}

// ---------------------------------------------------------------- 陨石坑

// 一层陨石坑：返回反照率的倍数，并在 tilt 里累加让法线倾斜的量（坑壁向内斜、坑缘鼓起，晨昏线附近能看出阴影）
float craterLayer(vec3 q, float scale, float density, float seed, inout vec3 tilt, float strength) {
    vec3 p = q * scale;
    vec3 i = floor(p);
    vec3 f = fract(p);
    float albedo = 1.0;
    for (int k = 0; k < 8; k++) {
        vec3 offset = vec3(float(k & 1), float((k >> 1) & 1), float((k >> 2) & 1)) - step(f, vec3(0.5));
        vec3 cell = i + offset;
        vec3 h = hash33(cell + seed * 17.0);
        if (h.x > density) {
            continue;
        }
        vec3 center = cell + 0.2 + 0.6 * hash33(cell * 1.31 + seed);
        // 大坑少、小坑多
        float radius = 0.12 + 0.33 * h.y * h.y;
        vec3 delta = p - center;
        float d = length(delta) / radius;
        if (d > 1.6) {
            continue;
        }
        // 坑底略暗，坑缘亮，新坑周围一圈溅射物更亮
        float rim = exp(-pow((d - 1.0) / 0.22, 2.0));
        float floorDark = 1.0 - smoothstep(0.45, 0.95, d);
        float fresh = step(0.85, h.z);
        albedo *= 1.0 - 0.06 * floorDark * h.z + 0.05 * rim * (1.0 - h.z) + fresh * 0.22 * (1.0 - smoothstep(0.9, 1.6, d));
        // 坡度：坑里朝中心倾斜，坑缘外朝外倾斜
        float slope = d < 1.0 ? -d * 0.9 : -(d - 1.0) * 2.2 * exp(-(d - 1.0) * 3.0) * (1.0 - smoothstep(1.2, 1.6, d));
        slope += rim * 0.6 * sign(1.0 - d);
        tilt += normalize(delta + vec3(1e-5)) * slope * strength;
    }
    return albedo;
}

// ---------------------------------------------------------------- 表面

struct Surface {
    vec3 albedo;
    vec3 emission;
    float ocean;
    float cloud;
    vec3 tilt;
};

float lat(vec3 q) {
    return q.z;
}

// cloudField 顺带算出来的两个量：云有多厚（0 到 1，厚的地方白，薄的边上发灰、透出下面），
// 和云顶向阳还是背阴（-1 到 1，近处看得出一团团积云的明暗）
float cloudThick;
float cloudRelief;
// 太阳在星球坐标里的方向
vec3 sunBody;

// 云：沿经度拉长。中纬度的温带气旋把云卷成逗号，低纬度的热带气旋卷得紧、中间有眼；
// 赤道和中纬度的风暴带多云，副热带少云。近处云的边缘收紧，碎成一团团积云
float cloudField(vec3 q, float coverage, float stretch, bool shade) {
    vec3 p = q;
    float storm = 0.0;
    for (int k = 0; k < 8; k++) {
        bool tropical = k >= 6;
        float h1 = hash11(Style.y * 13.0 + float(k));
        float h2 = hash11(Style.y * 29.0 + float(k) * 1.7);
        // 南北半球各一半；北半球的气旋逆时针转，南半球顺时针
        float hemi = (k & 1) == 0 ? 1.0 : -1.0;
        float z = hemi * (tropical ? 0.22 + 0.14 * h2 : 0.55 + 0.3 * h2);
        float lon = h1 * 2.0 * PI + float(k) * 2.4;
        vec3 c = vec3(sqrt(1.0 - z * z) * vec2(cos(lon), sin(lon)), z);
        float d2 = dot(p - c, p - c);
        // 温带气旋一千公里上下，热带气旋小一圈
        float size = tropical ? 0.0035 : 0.02 + 0.015 * h1;
        float pull = exp(-d2 / size);
        p = rotateAbout(p, c, (tropical ? 4.5 : 2.5 + 1.5 * h1) * pull * hemi);
        storm += (tropical ? 0.2 : 0.1) * pull;
        if (tropical) {
            storm += 0.3 * exp(-d2 / (size * 0.35));
            // 风眼，几十公里宽
            storm -= 0.9 * exp(-d2 / 0.00004);
        }
    }
    vec3 o = seedOffset(40.0);
    vec3 warp = vec3(fbm(p * 1.6 + o, 3.0), fbm(p * 1.6 + o + 5.3, 3.0), fbm(p * 1.6 + o + 9.1, 3.0)) - 0.5;
    vec3 s = p * vec3(2.4, 2.4, 2.4 * stretch) + warp * 2.2 + o;
    float large = fbm(s, octaves(2.4 * stretch));
    // 赤道辐合带多云，副热带（纬度二三十度）晴，中纬度的风暴带又多云，极地偏少
    float a = abs(lat(q));
    float zonal = 0.12 * exp(-a * a / 0.008) - 0.11 * exp(-pow((a - 0.45) / 0.12, 2.0))
            + 0.07 * exp(-pow((a - 0.78) / 0.1, 2.0)) - 0.04 * smoothstep(0.9, 1.0, a);
    // 积云一团一团的纹理和被风拉出来的丝
    float detail = fbm(p * 13.0 + warp * 3.0 + o * 0.3, octaves(13.0));
    float wisps = ridged(p * vec3(5.0, 5.0, 5.0 * stretch) + warp * 4.0 + o, octaves(5.0 * stretch));
    // 近地轨道上才看得出的积云团，边缘也跟着收紧
    float near = visibleAt(90.0);
    float cells = near > 0.0 ? fbm(p * 90.0 + warp * 8.0 + o * 0.7, octaves(90.0)) : 0.5;
    // 再近：几公里大的积云，一粒一粒的
    float close = visibleAt(600.0);
    float puffs = close > 0.0 ? fbm(p * 600.0 + o * 0.9, octaves(600.0)) : 0.5;
    float v = large + zonal + storm + (detail - 0.5) * 0.5 + (wisps - 0.35) * 0.12
            + (cells - 0.5) * 0.3 * near + (puffs - 0.5) * 0.2 * close;
    float edge = 1.0 - coverage;
    float soft = mix(0.2, 0.1, max(near * 0.6, close));
    float body = smoothstep(edge - 0.02, edge + soft, v);
    cloudThick = clamp((v - edge) / 0.3, 0.0, 1.0);
    // 卷云：高处被风拉成的薄纱，透得过光，多半挨着大片的云
    float cirrus = smoothstep(0.45, 0.75, wisps + (detail - 0.5) * 0.3) * 0.22 * smoothstep(edge - 0.25, edge, v);
    // 卷云是冰晶，薄但白
    cloudThick = max(cloudThick, cirrus > body ? 0.6 : 0.0);
    cloudRelief = 0.0;
    if (shade && near > 0.0) {
        // 云顶的明暗：往太阳那边挪一点再取一次积云，那边更厚说明这里在背阴的一面
        vec3 toSun = sunBody - q * dot(sunBody, q);
        float cellsSun = fbm((p + toSun * 0.004) * 90.0 + warp * 8.0 + o * 0.7, octaves(90.0));
        cloudRelief = clamp((cells - cellsSun) * 6.0, -1.0, 1.0) * near;
    }
    return max(body, cirrus);
}

Surface earthlike(vec3 q, bool waterWorld) {
    Surface s;
    s.emission = vec3(0.0);
    s.tilt = vec3(0.0);
    vec3 o = seedOffset(0.0);
    vec3 warp = vec3(fbm(q * 1.4 + o, 4.0), fbm(q * 1.4 + o + 5.2, 4.0), fbm(q * 1.4 + o + 9.7, 4.0)) - 0.5;
    float h = fbm(q * 1.7 + warp * 1.1 + o, octaves(1.7));
    float sea = Style.w;
    // 海岸线按一个像素的宽度过渡，远处不出锯齿
    float coast = max(0.0006, footprint * 3.0);
    float land = smoothstep(sea - coast, sea + coast, h);
    float depth = clamp((sea - h) / 0.045, 0.0, 1.0);
    vec3 shallow = mix(ColorA.rgb, vec3(0.06, 0.26, 0.3), 0.35) * 1.3;
    vec3 ocean = mix(shallow, ColorA.rgb * 0.6, sqrt(depth));
    float a = abs(lat(q));
    // 湿度：赤道雨林，南北纬二三十度是沙漠带，中纬度又湿起来
    float moist = fbm(q * 2.6 + o + 3.1, octaves(2.6)) + 0.18 * cos(a * PI * 3.6) - 0.05;
    float elevation = clamp((h - sea) / max(1.0 - sea, 0.01), 0.0, 1.0);
    float ridges = ridged(q * 5.0 + warp * 2.0 + o, octaves(5.0));
    vec3 green = ColorB.rgb;
    vec3 desert = ColorC.rgb;
    vec3 rock = mix(desert * 0.55, vec3(0.09, 0.08, 0.07), 0.45);
    vec3 landColor = mix(desert, green, smoothstep(0.42, 0.56, moist));
    landColor = mix(landColor, mix(green, desert, 0.5) * 0.7, smoothstep(0.55, 0.85, a));
    landColor = mix(landColor, rock, smoothstep(0.2, 0.55, elevation) * smoothstep(0.35, 0.7, ridges));
    landColor *= 0.86 + 0.28 * fbm(q * 22.0 + o, octaves(22.0));
    // 近处的山：山脊的明暗和裸露的岩石。用山的高度场的梯度把法线掰歪，只在一个像素小于十来公里时算
    float relief = visibleAt(40.0) * land;
    if (relief > 0.0) {
        vec3 t1 = normalize(cross(q, abs(q.z) < 0.9 ? vec3(0.0, 0.0, 1.0) : vec3(1.0, 0.0, 0.0)));
        vec3 t2 = cross(q, t1);
        float e = 0.0015;
        vec3 mq = q * 40.0 + warp * 6.0 + o;
        float m0 = ridged(mq, octaves(40.0));
        float m1 = ridged(mq + t1 * (e * 40.0), octaves(40.0));
        float m2 = ridged(mq + t2 * (e * 40.0), octaves(40.0));
        float mountains = 0.25 + 0.75 * smoothstep(0.05, 0.45, elevation + 0.15 * ridges);
        s.tilt = -((m1 - m0) * t1 + (m2 - m0) * t2) / e * 0.0035 * mountains * relief;
        landColor = mix(landColor, rock * 1.1, smoothstep(0.55, 0.85, m0) * mountains * 0.5 * relief);
        landColor *= mix(1.0, 0.8 + 0.4 * fbm(q * 160.0 + o * 0.3, octaves(160.0)), visibleAt(160.0));
    }
    // 极冠和高山的雪
    float capEdge = 1.0 - ColorB.w + (h - 0.5) * 0.22 + (fbm(q * 9.0 + o, octaves(9.0)) - 0.5) * 0.06;
    float cap = smoothstep(capEdge - 0.012, capEdge + 0.012, a);
    float snow = smoothstep(0.55, 0.75, elevation * ridges + a * 0.3) * land;
    vec3 albedo = mix(ocean, landColor, land);
    albedo = mix(albedo, vec3(0.82, 0.86, 0.9), max(cap, snow));
    s.albedo = albedo;
    s.ocean = (1.0 - land) * (1.0 - cap);
    float coverage = Style.z;
    s.cloud = coverage > 0.0 ? cloudField(q, coverage, 2.2, true) : 0.0;
    return s;
}

Surface moonlike(vec3 q) {
    Surface s;
    s.emission = vec3(0.0);
    s.tilt = vec3(0.0);
    s.ocean = 0.0;
    s.cloud = 0.0;
    vec3 o = seedOffset(0.0);
    // 月海：几个大撞击盆地灌满了玄武岩，大体是圆的（像雨海、澄海、危海），边缘被后来的坑和熔岩流啃得弯弯曲曲；
    // 再加一片不规则的大洋（像风暴洋）。月海和高地的分界很清楚
    vec3 warp = vec3(fbm(q * 2.0 + o, 3.0), fbm(q * 2.0 + o + 4.1, 3.0), fbm(q * 2.0 + o + 8.3, 3.0)) - 0.5;
    float ragged = (fbm(q * 7.0 + o, octaves(7.0)) - 0.5) * 0.5 + (fbm(q * 25.0 + o * 0.7, octaves(25.0)) - 0.5) * 0.15;
    float aa = max(0.02, footprint * 1.5);
    float maria = smoothstep(0.585 - aa, 0.585 + aa, fbm(q * 1.1 + warp * 0.7 + o, 5.0) + ragged * 0.12);
    int basins = 2 + int(hash11(Style.y * 41.0) * 4.0);
    for (int k = 0; k < 5; k++) {
        if (k >= basins) {
            break;
        }
        vec3 c = seededDirection(70.0 + float(k));
        float radius = 0.14 + 0.22 * hash11(Style.y * 31.0 + float(k));
        float d = acos(clamp(dot(q, c), -1.0, 1.0)) / radius + ragged;
        maria = max(maria, 1.0 - smoothstep(1.0 - aa / radius, 1.0 + aa / radius, d));
    }
    float tone = fbm(q * 6.0 + o, octaves(6.0));
    float fine = fbm(q * 28.0 + o * 0.5, octaves(28.0));
    // 高地亮，深浅起伏不大；月海暗，不同年代的熔岩流颜色有点差别（含钛多的发蓝，少的发褐）
    vec3 highland = ColorA.rgb * (0.82 + 0.36 * tone) * (0.88 + 0.24 * fine);
    float flow = fbm(q * 3.0 + warp + o + 3.0, octaves(3.0));
    vec3 mare = ColorB.rgb * (0.85 + 0.3 * flow) * mix(vec3(1.04, 1.0, 0.95), vec3(0.95, 0.98, 1.06), smoothstep(0.4, 0.6, flow))
            * (0.95 + 0.1 * fine);
    vec3 albedo = mix(highland, mare, maria);
    // 坑壁二三十度陡，晨昏线附近一个个坑看得很清楚
    float relief = ColorC.w * 3.0;
    float c = craterLayer(q, 5.0, 0.55, 1.0, s.tilt, 0.08 * relief);
    float fade = clamp(octaves(14.0) - 2.0, 0.0, 1.0);
    c *= mix(1.0, craterLayer(q, 14.0, 0.7, 2.0, s.tilt, 0.05 * relief * fade), fade);
    float fade2 = clamp(octaves(38.0) - 2.0, 0.0, 1.0);
    c *= mix(1.0, craterLayer(q, 38.0, 0.8, 3.0, s.tilt, 0.035 * relief * fade2), fade2);
    // 低轨道上：更小、更密的坑，加上月壤的细纹理
    float fade3 = clamp(octaves(100.0) - 2.0, 0.0, 1.0);
    if (fade3 > 0.0) {
        c *= mix(1.0, craterLayer(q, 100.0, 0.85, 4.0, s.tilt, 0.03 * relief * fade3), fade3);
        float fade4 = clamp(octaves(270.0) - 2.0, 0.0, 1.0);
        if (fade4 > 0.0) {
            c *= mix(1.0, craterLayer(q, 270.0, 0.9, 5.0, s.tilt, 0.025 * relief * fade4), fade4);
        }
        c *= mix(1.0, 0.9 + 0.2 * fbm(q * 150.0 + o, octaves(150.0)), fade3);
    }
    // 月海比高地年轻，坑少一些
    c = mix(c, 1.0, maria * 0.5);
    // 年轻的坑拖出细长的放射纹（像第谷坑、哥白尼坑、开普勒坑），满月时最显眼
    for (int k = 0; k < 3; k++) {
        vec3 center = seededDirection(30.0 + float(k));
        float reach = 0.5 / (1.0 + float(k));
        float d = acos(clamp(dot(q, center), -1.0, 1.0));
        if (d > reach * 4.0) {
            continue;
        }
        vec3 t = normalize(q - center * dot(q, center) + vec3(1e-5));
        vec3 ref = normalize(cross(center, vec3(0.3, 0.5, 0.8)));
        // 在一个圆上取噪声，绕一圈首尾接得上
        vec2 around = normalize(vec2(dot(t, ref), dot(t, cross(center, ref))) + vec2(1e-5));
        float streak = pow(vnoise(vec3(around * 6.0, float(k) * 5.0)), 4.0)
                + 0.6 * pow(vnoise(vec3(around * 13.0, 9.0 + float(k) * 5.0)), 6.0);
        // 每一道放射纹长短不一
        float rayLength = reach * (0.35 + 1.1 * vnoise(vec3(around * 4.0, 20.0 + float(k) * 5.0)));
        float rays = streak * exp(-d / rayLength) * smoothstep(0.06 * reach, 0.16 * reach, d) * (0.6 + 0.4 * fbm(q * 9.0, 3.0));
        float bowl = 0.07 * reach;
        albedo *= 1.0 + rays * 1.4 + 0.3 * exp(-d * d / (bowl * bowl));
    }
    s.albedo = albedo * c;
    return s;
}

Surface marslike(vec3 q) {
    Surface s;
    s.emission = vec3(0.0);
    s.tilt = vec3(0.0);
    s.ocean = 0.0;
    vec3 o = seedOffset(0.0);
    float dark = smoothstep(0.5, 0.62, fbm(q * 1.5 + o, octaves(1.5)) + 0.08 * fbm(q * 5.0 + o, 3.0));
    float dust = fbm(q * 4.0 + o + 2.0, octaves(4.0));
    vec3 albedo = mix(ColorA.rgb, ColorB.rgb, dark * 0.85) * (0.85 + 0.3 * dust);
    // 一条横贯赤道附近的大峡谷
    vec3 axis = normalize(vec3(0.2, 0.3, 1.0));
    float band = abs(dot(q, axis) - 0.12);
    float canyon = (1.0 - smoothstep(0.01, 0.05, band + 0.03 * (fbm(q * 12.0 + o, 3.0) - 0.5))) * smoothstep(0.45, 0.65, fbm(q * 2.0 + o + 9.0, 3.0));
    albedo *= 1.0 - 0.3 * canyon;
    float c = craterLayer(q, 7.0, 0.35, 1.0, s.tilt, 0.05 * ColorC.w);
    float fade = clamp(octaves(20.0) - 2.0, 0.0, 1.0);
    c *= mix(1.0, craterLayer(q, 20.0, 0.5, 2.0, s.tilt, 0.03 * ColorC.w * fade), fade);
    float a = abs(lat(q));
    float capEdge = 1.0 - ColorB.w + (fbm(q * 6.0 + o, 4.0) - 0.5) * 0.08;
    float cap = smoothstep(capEdge - 0.01, capEdge + 0.01, a);
    s.albedo = mix(albedo * c, ColorC.rgb, cap);
    s.cloud = Style.z > 0.0 ? cloudField(q, Style.z * 0.6, 3.0, true) * 0.6 : 0.0;
    return s;
}

Surface icy(vec3 q) {
    Surface s;
    s.emission = vec3(0.0);
    s.tilt = vec3(0.0);
    s.ocean = 0.0;
    s.cloud = 0.0;
    vec3 o = seedOffset(0.0);
    float mottle = smoothstep(0.5, 0.68, fbm(q * 2.2 + o, octaves(2.2)));
    vec3 albedo = mix(ColorA.rgb, mix(ColorA.rgb, ColorB.rgb, 0.55), mottle);
    // 木卫二的条纹：好几层交错的长裂缝，颜色发红褐
    float lines = 0.0;
    for (int k = 0; k < 4; k++) {
        float f = 2.0 + float(k) * 2.6;
        vec3 p = rotateAbout(q, seededDirection(60.0 + float(k)), float(k) * 1.3) * f + o + float(k) * 11.0;
        float n = vnoise(p) + 0.5 * vnoise(p * 2.1) - 0.75;
        float width = 0.018 + 0.01 * float(k) + footprint * f * 1.5;
        lines = max(lines, (1.0 - smoothstep(0.0, width, abs(n))) * (0.55 + 0.45 * vnoise(p * 0.7)));
    }
    albedo = mix(albedo, ColorB.rgb, lines * 0.75);
    albedo *= 0.92 + 0.16 * fbm(q * 16.0 + o, octaves(16.0));
    float c = craterLayer(q, 6.0, 0.08, 4.0, s.tilt, 0.03);
    s.albedo = albedo * c;
    return s;
}

Surface volcanic(vec3 q) {
    Surface s;
    s.tilt = vec3(0.0);
    s.ocean = 0.0;
    s.cloud = 0.0;
    vec3 o = seedOffset(0.0);
    float region = fbm(q * 1.8 + o, octaves(1.8));
    float a = abs(lat(q));
    vec3 albedo = mix(ColorA.rgb, ColorB.rgb, smoothstep(0.45, 0.65, region) * 0.7);
    // 两极偏红褐
    albedo = mix(albedo, ColorB.rgb * 0.7, smoothstep(0.55, 0.9, a) * 0.8);
    // 白色的二氧化硫霜
    float frost = smoothstep(0.55, 0.75, fbm(q * 3.0 + o + 4.0, octaves(3.0)) + 0.1 * (fbm(q * 11.0 + o, octaves(11.0)) - 0.5));
    albedo = mix(albedo, ColorC.rgb, frost * 0.5);
    // 火山口：黑点外面一圈红色的喷发物
    vec3 p = q * 6.0;
    vec3 i = floor(p);
    vec3 f = fract(p);
    float glow = 0.0;
    for (int k = 0; k < 8; k++) {
        vec3 offset = vec3(float(k & 1), float((k >> 1) & 1), float((k >> 2) & 1)) - step(f, vec3(0.5));
        vec3 cell = i + offset;
        vec3 h = hash33(cell + 5.0);
        if (h.x > 0.55) {
            continue;
        }
        vec3 center = cell + 0.25 + 0.5 * hash33(cell * 1.7);
        float d = length(p - center);
        float r = 0.05 + 0.08 * h.y;
        float ring = exp(-pow((d - r * 3.2) / (r * 1.4), 2.0)) * step(0.5, h.z);
        albedo = mix(albedo, mix(ColorB.rgb, vec3(0.6, 0.15, 0.05), 0.4), ring * 0.6);
        float spot = 1.0 - smoothstep(r * 0.6, r, d);
        albedo = mix(albedo, vec3(0.04, 0.03, 0.02), spot);
        glow += spot * (0.5 + 0.5 * h.y);
    }
    albedo *= 0.88 + 0.24 * fbm(q * 20.0 + o, octaves(20.0));
    s.albedo = albedo;
    s.emission = vec3(1.0, 0.35, 0.08) * glow * ColorA.w;
    return s;
}

Surface cloudDeck(vec3 q, bool venus) {
    Surface s;
    s.emission = vec3(0.0);
    s.tilt = vec3(0.0);
    s.ocean = 0.0;
    s.cloud = 0.0;
    vec3 o = seedOffset(0.0);
    float t = Extra.x * 0.004;
    vec3 p = rotateAbout(q, vec3(0.0, 0.0, 1.0), t);
    float turb = fbm(p * vec3(2.5, 2.5, 5.0) + o, octaves(5.0));
    float l = lat(q);
    float bands;
    if (venus) {
        // 金星：赤道一带的 Y 形条纹
        float lon = atan(p.y, p.x);
        bands = sin(l * 7.0 + sin(lon * 1.0 + l * 3.0) * 1.2 + turb * 3.0);
    } else {
        bands = sin(l * 9.0 + turb * 2.0);
    }
    vec3 albedo = mix(ColorA.rgb, ColorB.rgb, smoothstep(-1.0, 1.0, bands) * 0.45 + (turb - 0.5) * 0.3);
    s.albedo = albedo * (0.94 + 0.12 * fbm(p * 14.0 + o, octaves(14.0)));
    return s;
}

// 气态巨行星的云带：纬度决定带，风切变把边缘撕成漩涡
Surface giant(vec3 q, int style) {
    Surface s;
    s.emission = vec3(0.0);
    s.tilt = vec3(0.0);
    s.ocean = 0.0;
    s.cloud = 0.0;
    vec3 o = seedOffset(0.0);
    float l = lat(q);
    float t = Extra.x * 0.002;
    vec3 p = rotateAbout(q, vec3(0.0, 0.0, 1.0), t * (1.0 + 0.4 * sin(l * 13.0)));
    float contrast = style == SATURN ? 0.55 : style == ICE_GIANT ? 0.22 : 1.0;
    float bandCount = style == SATURN ? 16.0 : style == ICE_GIANT ? 6.0 : 11.0 + 5.0 * Style.y;
    vec3 shear = vec3(fbm(p * vec3(3.0, 3.0, 14.0) + o, 4.0), fbm(p * vec3(3.0, 3.0, 14.0) + o + 3.3, 4.0), 0.0) - 0.5;
    // octaves 至少画一层，远处和边缘一个像素盖住好几条细纹时干脆淡掉，不出摩尔纹
    float turb = mix(0.5, fbm(p * vec3(5.0, 5.0, 24.0) + shear * 2.2 + o, octaves(24.0)), visibleAt(24.0));
    float jet = l * bandCount + 0.6 * sin(l * 3.1 + Style.y * 6.0) + (noise1(l * 40.0 + Style.y * 9.0) - 0.5) * 0.8 * visibleAt(40.0);
    float band = sin(jet * PI + (turb - 0.5) * 3.2 * contrast);
    vec3 color = mix(ColorB.rgb, ColorA.rgb, smoothstep(-0.5, 0.5, band));
    color = mix(mix(ColorA.rgb, ColorB.rgb, 0.5), color, contrast);
    // 细的丝状结构
    float filaments = fbm(p * vec3(14.0, 14.0, 60.0) + shear * 4.0 + o, octaves(60.0));
    color *= 1.0 + (filaments - 0.5) * 0.35 * mix(0.4, 1.0, contrast) * (style == SATURN ? 0.6 : 1.0) * visibleAt(60.0);
    // 大红斑一样的大风暴，周围的云绕着它转
    vec3 spot = normalize(vec3(cos(Style.y * 6.28), sin(Style.y * 6.28), style == ICE_GIANT ? -0.35 : -0.38));
    vec3 local = p - spot * dot(p, spot);
    vec3 east = normalize(cross(vec3(0.0, 0.0, 1.0), spot));
    vec3 north = cross(spot, east);
    vec2 e = vec2(dot(local, east) / 0.13, dot(local, north) / 0.075);
    // 背面的半球不算（不然那半边整个落在风暴里）
    float r = dot(p, spot) > 0.0 ? length(e) : 1e3;
    if (r < 1.6) {
        float swirl = fbm(vec3(e * 3.0, Style.y * 10.0) + vec3(atan(e.y, e.x) * 0.6, 0.0, r * 2.0), 4.0);
        float inside = 1.0 - smoothstep(0.75, 1.0, r + (swirl - 0.5) * 0.25);
        vec3 stormColor = style == ICE_GIANT ? ColorB.rgb * 0.45 : mix(ColorC.rgb, ColorB.rgb, 0.25 + 0.5 * swirl);
        color = mix(color, stormColor, inside * (style == SATURN ? 0.4 : 0.9));
        if (style == ICE_GIANT) {
            // 海王星的暗斑边上有亮白的伴云
            float companion = exp(-pow((r - 1.2) / 0.12, 2.0)) * step(0.0, e.y) * 0.8;
            color = mix(color, vec3(0.9), companion);
        } else {
            float ring = exp(-pow((r - 1.0) / 0.14, 2.0));
            color = mix(color, ColorA.rgb * 1.08, ring * 0.5);
        }
    }
    // 几个白色的小卵形风暴和亮云
    for (int k = 0; k < 5; k++) {
        vec3 c = seededDirection(20.0 + float(k));
        c.z = (hash11(Style.y * 7.0 + float(k)) - 0.5) * 1.4;
        c = normalize(c);
        vec3 lc = p - c * dot(p, c);
        vec3 ce = normalize(cross(vec3(0.0, 0.0, 1.0), c));
        vec3 cn = cross(c, ce);
        float size = style == ICE_GIANT ? 0.08 : 0.035;
        vec2 ec = vec2(dot(lc, ce) / (size * (style == ICE_GIANT ? 3.5 : 1.6)), dot(lc, cn) / size);
        float d = dot(p, c) > 0.0 ? length(ec) : 1e3;
        color = mix(color, vec3(0.93, 0.92, 0.88), (1.0 - smoothstep(0.6, 1.0, d)) * (style == SATURN ? 0.3 : 0.75));
    }
    float a = abs(l);
    if (style == JUPITER) {
        // 两极：发蓝灰的斑驳区域，里面挤着一群气旋
        float polar = smoothstep(0.78, 0.92, a);
        vec3 pc = mix(ColorB.rgb, vec3(0.32, 0.36, 0.42), 0.6) * (0.75 + 0.5 * fbm(p * 30.0 + o, octaves(30.0)));
        color = mix(color, pc, polar);
    } else if (style == SATURN && l > 0.86) {
        // 土星北极的六边形
        float angle = atan(p.y, p.x);
        float hex = cos(PI / 6.0) / cos(mod(angle + Style.y, PI / 3.0) - PI / 6.0);
        float rr = sqrt(max(1.0 - l * l, 0.0));
        float inside = 1.0 - smoothstep(0.42 * hex, 0.45 * hex, rr);
        color = mix(color, mix(color, vec3(0.3, 0.38, 0.45), 0.5), inside);
    }
    s.albedo = color;
    return s;
}

Surface exotic(vec3 q) {
    Surface s;
    s.tilt = vec3(0.0);
    s.ocean = 0.0;
    s.cloud = 0.0;
    vec3 o = seedOffset(0.0);
    // 一块块拼起来的板，缝里透出光
    vec3 p = q * (5.0 + 4.0 * Extra.z);
    vec3 i = floor(p);
    vec3 f = fract(p);
    float best = 9.0;
    float second = 9.0;
    vec3 bestCell = vec3(0.0);
    for (int k = 0; k < 8; k++) {
        vec3 offset = vec3(float(k & 1), float((k >> 1) & 1), float((k >> 2) & 1)) - step(f, vec3(0.5));
        vec3 cell = i + offset;
        vec3 center = cell + hash33(cell + o);
        float d = length(p - center);
        if (d < best) {
            second = best;
            best = d;
            bestCell = cell;
        } else if (d < second) {
            second = d;
        }
    }
    float seam = 1.0 - smoothstep(0.0, 0.06 + footprint * 8.0, second - best);
    float tone = hash13(bestCell + o);
    vec3 albedo = mix(ColorA.rgb, ColorB.rgb, tone);
    albedo *= 0.8 + 0.4 * fbm(q * 18.0 + o, octaves(18.0));
    s.albedo = mix(albedo, ColorC.rgb * 0.3, seam);
    s.emission = ColorC.rgb * seam * ColorA.w;
    return s;
}

Surface surfaceAt(vec3 q, int style) {
    if (style == EARTH) {
        return earthlike(q, false);
    } else if (style == OCEAN) {
        return earthlike(q, true);
    } else if (style == MARS) {
        return marslike(q);
    } else if (style == ICE) {
        return icy(q);
    } else if (style == VOLCANIC) {
        return volcanic(q);
    } else if (style == VENUS) {
        return cloudDeck(q, true);
    } else if (style == HAZE) {
        return cloudDeck(q, false);
    } else if (style == JUPITER || style == SATURN || style == ICE_GIANT) {
        return giant(q, style);
    } else if (style == EXOTIC) {
        return exotic(q);
    }
    return moonlike(q);
}

// ---------------------------------------------------------------- 大气

// 从半径 x（以标高为单位）沿天顶角余弦 mu 往外走，积到无穷远的相对光学厚度（以标高为单位，Schüler 的近似）
float chapman(float x, float mu) {
    float c = sqrt(x);
    if (mu >= 0.0) {
        return c / (c * mu + 1.0);
    }
    float x0 = sqrt(max(1.0 - mu * mu, 0.0)) * x;
    float c0 = sqrt(x0);
    return 2.0 * c0 * exp(min(x - x0, 60.0)) - c / (1.0 - c * mu);
}

vec3 extinction() {
    return Atmos.rgb + vec3(Haze.w * 1.1);
}

// 从星球中心 p 处（以星球半径为单位）往恒星方向的透射率
vec3 sunTransmittance(vec3 p, vec3 L) {
    float top = Atmos.w;
    if (top <= 0.0) {
        return vec3(1.0);
    }
    float H = top / 6.0;
    float r = length(p);
    float mu = dot(p, L) / r;
    // 被星球挡住：地影
    float sinChi = sqrt(max(1.0 - mu * mu, 0.0));
    float shadow = mu < 0.0 ? smoothstep(0.995, 1.004, r * sinChi) : 1.0;
    float density = exp(-max(r - 1.0, 0.0) / H);
    vec3 tau = extinction() * density * chapman(r / H, mu);
    return exp(-tau) * shadow;
}

// 沿视线 [ta, tb] 积分散射进来的光，transmittance 返回这段的透射率
vec3 inscatter(vec3 c, vec3 d, float ta, float tb, vec3 L, out vec3 transmittance) {
    float top = Atmos.w;
    transmittance = vec3(1.0);
    if (top <= 0.0 || tb <= ta) {
        return vec3(0.0);
    }
    float H = top / 6.0;
    // 竖直光学厚度 = β × H，换算成每单位长度的系数
    vec3 betaR = Atmos.rgb / H;
    float betaM = Haze.w / H;
    vec3 betaE = betaR + vec3(betaM * 1.1);
    const int STEPS = 14;
    float segment = (tb - ta) / float(STEPS);
    vec3 optical = vec3(0.0);
    vec3 sumR = vec3(0.0);
    vec3 sumM = vec3(0.0);
    for (int i = 0; i < STEPS; i++) {
        float t = ta + (float(i) + 0.5) * segment;
        vec3 p = d * t - c;
        float r = length(p);
        float density = exp(-max(r - 1.0, 0.0) / H) * segment;
        vec3 stepTau = betaE * density;
        vec3 sun = sunTransmittance(p, L);
        vec3 attenuation = exp(-(optical + stepTau * 0.5)) * sun;
        sumR += attenuation * density;
        sumM += attenuation * density;
        optical += stepTau;
    }
    transmittance = exp(-optical);
    float mu = dot(d, L);
    float phaseR = 3.0 / (16.0 * PI) * (1.0 + mu * mu);
    float g = 0.72;
    float phaseM = (1.0 - g * g) / (4.0 * PI * pow(1.0 + g * g - 2.0 * g * mu, 1.5));
    return (sumR * betaR * phaseR + sumM * betaM * Haze.rgb * phaseM) * 3.5;
}

// ---------------------------------------------------------------- 行星环

float ringDensity(float r) {
    float inner = Rings.x;
    float outer = Rings.y;
    float t = (r - inner) / (outer - inner);
    if (t <= 0.0 || t >= 1.0) {
        return 0.0;
    }
    float seed = Rings.w * 13.0;
    float d = 0.62 + 0.18 * sin(t * 47.0 + seed) + 0.22 * (noise1(t * 140.0 + seed) - 0.5) + 0.16 * (noise1(t * 520.0 + seed * 3.0) - 0.5);
    // 内侧的暗环淡，中间最密
    d *= mix(0.25, 1.0, smoothstep(0.12, 0.3, t));
    d *= mix(1.0, 0.7, smoothstep(0.75, 1.0, t));
    // 卡西尼缝和恩克缝
    float cassini = 0.6 + 0.08 * hash11(seed);
    d *= 1.0 - 0.92 * (1.0 - smoothstep(0.0, 0.03, abs(t - cassini)));
    d *= 1.0 - 0.75 * (1.0 - smoothstep(0.0, 0.006, abs(t - 0.92)));
    d *= smoothstep(0.0, 0.02, t) * (1.0 - smoothstep(0.985, 1.0, t));
    return clamp(d, 0.0, 1.0) * Rings.z;
}

vec3 ringAlbedo(float r) {
    float t = (r - Rings.x) / (Rings.y - Rings.x);
    float n = noise1(t * 60.0 + Rings.w * 5.0);
    return RingColor.rgb * (0.75 + 0.4 * n) * mix(vec3(0.75, 0.72, 0.7), vec3(1.0), smoothstep(0.1, 0.4, t));
}

// ---------------------------------------------------------------- 合成

vec2 raySphere(vec3 c, vec3 d, float radius) {
    float b = dot(c, d);
    vec3 h = c - b * d;
    float h2 = dot(h, h);
    float r2 = radius * radius;
    if (h2 > r2) {
        return vec2(1e9, -1e9);
    }
    float k = sqrt(r2 - h2);
    return vec2(b - k, b + k);
}

vec3 aces(vec3 x) {
    return clamp(x * (2.51 * x + 0.03) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

// 按亮度压曲线、保住色相（逐通道压会把颜色洗白），只有很亮的地方才往白色靠
vec3 tonemap(vec3 x) {
    x = max(x * Extra.w, vec3(0.0));
    float peak = max(x.r, max(x.g, x.b));
    vec3 hue = peak > 1e-5 ? x * (aces(vec3(peak)).r / peak) : vec3(0.0);
    return pow(mix(hue, aces(x), 0.3), vec3(1.0 / 2.2));
}

void main() {
    vec3 d = normalize(worldDir);
    vec3 c = Center.xyz;
    vec3 L = normalize(Sun.xyz);
    vec3 sunLight = SunColor.rgb * Sun.w;
    int style = int(Style.x + 0.5);
    bool ringsOnly = RingColor.w > 0.5;
    float top = Atmos.w;
    float distanceToCenter = length(c);

    // 一个像素对着多少弧度
    float pixel = 2.0 / (ProjMat[1][1] * ScreenSize.y);
    float angularRadius = asin(clamp(1.0 / max(distanceToCenter, 1.0), 0.0, 1.0));

    // 太远、不到两个像素：画成一个亮点，亮度按亮面比例
    if (!ringsOnly && angularRadius < pixel * 1.5 && distanceToCenter > 2.0) {
        vec3 toBody = normalize(c);
        float theta = acos(clamp(dot(d, toBody), -1.0, 1.0));
        float spread = max(pixel * 1.1, angularRadius);
        float glow = exp(-pow(theta / spread, 2.0));
        float lit = 0.5 + 0.5 * dot(-toBody, L);
        vec3 mean = (ColorA.rgb + ColorB.rgb) * 0.5;
        vec3 color = tonemap(mean * sunLight * lit * 1.6 + vec3(0.02));
        float a = glow * Center.w;
        fragColor = vec4(color * a, 0.0);
        return;
    }

    vec3 rgbAccum = vec3(0.0);
    float alphaAccum = 0.0;

    // 星球本体和大气
    vec2 hit = ringsOnly ? vec2(1e9, -1e9) : raySphere(c, d, 1.0);
    vec2 shell = (ringsOnly || top <= 0.0) ? vec2(1e9, -1e9) : raySphere(c, d, 1.0 + top);
    bool surfaceHit = hit.x < hit.y && hit.y > 0.0;
    float tSurface = surfaceHit ? max(hit.x, 0.0) : 1e9;

    // 行星环和视线的交点
    float tRing = 1e9;
    float ringD = 0.0;
    float ringR = 0.0;
    vec3 ringPoint = vec3(0.0);
    vec3 axis = vec3(BodyRot[0][2], BodyRot[1][2], BodyRot[2][2]);
    if (Rings.y > 0.0) {
        float denom = dot(d, axis);
        if (abs(denom) > 1e-5) {
            float t = dot(c, axis) / denom;
            if (t > 0.0) {
                vec3 p = d * t - c;
                float r = length(p);
                float density = ringDensity(r);
                if (density > 0.0) {
                    tRing = t;
                    ringD = density;
                    ringR = r;
                    ringPoint = p;
                }
            }
        }
    }

    vec3 planetRgb = vec3(0.0);
    float planetA = 0.0;
    if (surfaceHit) {
        vec3 p = d * tSurface - c;
        vec3 n = normalize(p);
        mat3 toBody = mat3(BodyRot);
        vec3 q = toBody * n;
        // 一个像素在球面上跨过的弧度（星球半径为 1）：斜着看的边缘一个像素盖住的地面更多，细节相应少画几层，不闪
        footprint = pixel * tSurface / max(abs(dot(n, -d)), 0.05);
        sunBody = toBody * L;
        cloudThick = 0.0;
        cloudRelief = 0.0;
        Surface s = surfaceAt(q, style);
        float thick = cloudThick;
        float cloudTop = cloudRelief;
        // 陨石坑让法线倾斜（在星球坐标里算的，换回世界坐标）
        vec3 shading = normalize(n + transpose(toBody) * s.tilt);
        vec3 tSun = sunTransmittance(n * 1.0005, L);
        float ndl = dot(shading, L);
        float mu0 = max(ndl, 0.0);
        float mu = max(dot(shading, -d), 0.02);
        vec3 albedo = s.albedo;
        float brdf;
        if (top <= 0.0 && style != JUPITER && style != SATURN && style != ICE_GIANT) {
            // 没有大气的星球：月面那样边缘不变暗（Lommel–Seeliger 加一点朗伯）
            brdf = mix(mu0, 2.0 * mu0 / (mu0 + mu), 0.75);
        } else if (style == JUPITER || style == SATURN || style == ICE_GIANT) {
            // 气态巨行星边缘变暗
            brdf = mu0 * pow(mu, 0.18);
        } else {
            brdf = mu0;
        }
        // 晨昏线：没有大气的一刀切，有大气的过渡宽一点
        brdf *= smoothstep(-0.02 - top * 2.0, 0.02, ndl);
        // 云和云影
        float cloud = s.cloud;
        float cloudShadow = 0.0;
        if (cloud > 0.0 || Style.z > 0.0) {
            // 云影：往太阳的方向挪一点再取一次云
            vec3 shadowDir = toBody * normalize(n + L * 0.012);
            cloudShadow = (style == EARTH || style == OCEAN) ? cloudField(shadowDir, Style.z, 2.2, false) : 0.0;
        }
        // 环的影子落在星球上
        float ringShadow = 1.0;
        if (Rings.y > 0.0) {
            float denom = dot(L, axis);
            if (abs(denom) > 1e-4) {
                float t = -dot(n, axis) / denom;
                if (t > 0.0) {
                    ringShadow = 1.0 - 0.85 * ringDensity(length(n + L * t));
                }
            }
        }
        vec3 direct = sunLight * tSun * ringShadow;
        vec3 ground = albedo * brdf * (1.0 - cloudShadow * 0.6 * (1.0 - cloud));
        // 海面反光：海浪把太阳的倒影摊成一大片，中间亮、往外慢慢变暗（Beckmann 分布，坡度方差 0.02，
        // 相当于七八米每秒的风）；越靠近边缘反射率越高，所以晨昏线和边上的反光更亮
        if (s.ocean > 0.0 && ndl > 0.0) {
            vec3 h = normalize(L - d);
            float c2 = max(dot(n, h), 1e-3);
            c2 *= c2;
            float m2 = 0.02;
            float beckmann = exp((c2 - 1.0) / (c2 * m2)) / (PI * m2 * c2 * c2);
            float fresnel = 0.02 + 0.98 * pow(1.0 - max(dot(h, -d), 0.0), 5.0);
            float spec = min(PI * beckmann * fresnel / (4.0 * mu) * 1.5, 4.0);
            ground += vec3(spec) * s.ocean * (1.0 - cloud);
        }
        vec3 cloudSun = top > 0.0 ? sunTransmittance(n * (1.0 + top * 0.25), L) : vec3(1.0);
        // 厚的云白，薄的边上灰一些；近处积云向阳的一面亮、背阴的一面暗
        float cloudAlbedo = mix(0.72, 0.95, sqrt(thick)) * (1.0 + 0.3 * cloudTop);
        vec3 cloudLit = vec3(cloudAlbedo) * (max(ndl, 0.0) * smoothstep(-0.05, 0.05, ndl) * cloudSun
                + Atmos.rgb * 1.5 * clamp(ndl + 0.2, 0.0, 1.0)) * sunLight * ringShadow;
        vec3 lit = mix(ground * direct, cloudLit, cloud);
        // 大气里的天光把暗面边上照亮一点
        if (top > 0.0) {
            lit += albedo * (Atmos.rgb + vec3(Haze.w * 0.3)) * sunLight * clamp(ndl + 0.25, 0.0, 1.0) * 0.18;
        }
        // 自发光：熔岩、奇异星球的缝；白天看不出来
        lit += s.emission * (1.0 - clamp(mu0 * 3.0, 0.0, 1.0) * 0.8);
        // 极光：两极的夜面
        if (Extra.y > 0.0) {
            float polar = smoothstep(0.82, 0.9, abs(q.z)) * (1.0 - smoothstep(0.93, 0.98, abs(q.z)));
            float curtain = fbm(vec3(atan(q.y, q.x) * 6.0, Extra.x * 0.05, Style.y * 10.0), 3.0);
            lit += vec3(0.2, 1.0, 0.45) * polar * curtain * Extra.y * (1.0 - clamp(mu0 * 4.0, 0.0, 1.0)) * 0.6;
        }
        // 视线穿过大气：远处的地面被散射的光盖住、颜色变淡
        vec3 viewTrans = vec3(1.0);
        vec3 air = vec3(0.0);
        if (top > 0.0) {
            float ta = max(shell.x, 0.0);
            air = inscatter(c, d, ta, tSurface, L, viewTrans);
        }
        planetRgb = lit * viewTrans + air * sunLight;
        planetA = 1.0;
    } else if (shell.x < shell.y && shell.y > 0.0) {
        // 只穿过大气：星球边上的光环
        vec3 trans;
        vec3 air = inscatter(c, d, max(shell.x, 0.0), shell.y, L, trans);
        planetRgb = air * sunLight;
        planetA = 1.0 - dot(trans, vec3(1.0 / 3.0));
    }

    // 环
    vec3 ringRgb = vec3(0.0);
    float ringA = 0.0;
    if (ringD > 0.0) {
        vec3 albedo = ringAlbedo(ringR);
        float viewerSide = sign(dot(-c, axis));
        float sunSide = sign(dot(L, axis));
        float cosSun = abs(dot(L, axis));
        float mu = dot(d, L);
        float light;
        if (viewerSide == sunSide) {
            // 迎光面：很多颗冰粒叠在一起，太阳很斜也不怎么暗（卡西尼号拍的时候太阳离环面只有十几度）；
            // 再按 Lommel–Seeliger 带一点斜着看更亮
            float cosView = abs(dot(d, axis));
            light = 1.4 * pow(cosSun, 0.35) * mix(1.0, 2.0 * cosSun / (cosSun + cosView + 1e-3), 0.3) + 0.06;
        } else {
            // 背光面：薄的地方透光，朝着恒星看时前向散射特别亮
            float forward = pow(max(mu, 0.0), 6.0) * 4.0 + 0.15;
            light = forward * (1.0 - ringD) * 1.4 + 0.05;
        }
        // 星球的影子落在环上
        float along = dot(ringPoint, L);
        float shadow = along < 0.0 ? smoothstep(0.98, 1.02, length(ringPoint - along * L)) : 1.0;
        ringRgb = albedo * light * shadow * sunLight;
        ringA = ringD;
    }

    // 按远近合成（预乘透明度）：大气只加亮，透明度用来挡住后面的星星和环
    vec4 planetLayer = vec4(tonemap(planetRgb), planetA);
    vec4 ringLayer = vec4(tonemap(ringRgb) * ringA, ringA);
    float tPlanet = surfaceHit ? tSurface : max(shell.x, 0.0);
    vec4 front = planetLayer;
    vec4 back = ringLayer;
    if (ringA > 0.0 && tRing < tPlanet) {
        front = ringLayer;
        back = planetLayer;
    }
    vec3 rgb = front.rgb + back.rgb * (1.0 - front.a);
    float alpha = front.a + back.a * (1.0 - front.a);
    float visibility = Center.w;
    fragColor = vec4(rgb * visibility, alpha * SunColor.w * visibility);
}
