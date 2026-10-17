package io.github.sweetcornna.universe.core.galaxy;

import static io.github.sweetcornna.universe.core.procgen.Colors.hsv;

import io.github.sweetcornna.universe.core.procgen.Colors;
import io.github.sweetcornna.universe.core.procgen.Rng;

/**
 * 星球配色。每种类型有自己的基调（冰冻偏白、灼热偏暗红），在基调里随机取色相，
 * 宜居星球的草也不一定是绿的。天空颜色由大气决定，再被恒星的颜色染一层。
 *
 * <p>色相单位是圈（0–1）：0 红、0.17 黄、0.33 绿、0.5 青、0.67 蓝、0.83 品红。
 */
final class PlanetPalettes {
    private PlanetPalettes() {
    }

    static Palette generate(Rng rng, PlanetType type, Atmosphere atmosphere, Liquid liquid, Star star, double temperature) {
        Ground g = ground(rng, type);
        int liquidColor = liquid(rng, liquid, type, g.sand);
        Sky sky = sky(rng, type, atmosphere, star, g.surface);
        return new Palette(g.surface, g.soil, g.rock, g.sand, g.foliage, g.grass, liquidColor,
                sky.sky, sky.fog, sky.cloud, g.accent);
    }

    private record Ground(int surface, int soil, int rock, int sand, int foliage, int grass, int accent) {
    }

    private record Sky(int sky, int fog, int cloud) {
    }

    private static Ground ground(Rng rng, PlanetType type) {
        return switch (type) {
            case HABITABLE, OCEAN -> {
                // 一半多的宜居星球是绿色系，其余随机：紫草原、红森林、蓝苔原都可能出现
                double hue = rng.chance(0.55) ? rng.range(0.2, 0.38) : rng.nextDouble();
                double saturation = rng.range(0.35, 0.65);
                double value = rng.range(0.5, 0.75);
                int surface = hsv(hue, saturation, value);
                int grass = hsv(hue + rng.range(-0.03, 0.03), saturation + 0.05, value + 0.05);
                double foliageShift = type == PlanetType.OCEAN ? rng.range(0, 0.12) : rng.range(-0.08, 0.08);
                int foliage = hsv(hue + foliageShift, rng.range(0.45, 0.75), rng.range(0.35, 0.6));
                int soil = hsv(rng.range(0.05, 0.1), rng.range(0.35, 0.6), rng.range(0.3, 0.45));
                int rock = hsv(rng.nextDouble(), rng.range(0, 0.1), rng.range(0.45, 0.6));
                double sandValue = type == PlanetType.OCEAN ? rng.range(0.82, 0.95) : rng.range(0.75, 0.9);
                int sand = hsv(rng.range(0.1, 0.15), rng.range(0.2, 0.45), sandValue);
                int accent = hsv(hue + 0.5 + rng.range(-0.15, 0.15), rng.range(0.6, 0.9), rng.range(0.8, 1));
                yield new Ground(surface, soil, rock, sand, foliage, grass, accent);
            }
            case BARREN -> {
                double hue = rng.chance(0.4) ? rng.range(0, 0.1) : rng.nextDouble();
                double saturation = rng.chance(0.4) ? rng.range(0, 0.08) : rng.range(0.15, 0.45);
                double value = rng.range(0.45, 0.7);
                int surface = hsv(hue, saturation, value);
                int soil = hsv(hue, saturation * 0.9, value - 0.1);
                int rock = hsv(hue + rng.range(-0.03, 0.03), saturation * 0.6, rng.range(0.3, 0.45));
                int sand = hsv(hue, saturation * 0.7, rng.range(0.6, 0.8));
                int lichen = hsv(hue + 0.1, 0.2, 0.4);
                int accent = hsv(rng.nextDouble(), 0.5, 0.8);
                yield new Ground(surface, soil, rock, sand, lichen, lichen, accent);
            }
            case FROZEN -> {
                double hue = rng.range(0.5, 0.66);
                int surface = hsv(hue, rng.range(0.03, 0.18), rng.range(0.85, 0.98));
                int soil = hsv(hue, rng.range(0.1, 0.25), rng.range(0.65, 0.8));
                int rock = hsv(hue + rng.range(-0.05, 0.05), rng.range(0.05, 0.2), rng.range(0.35, 0.55));
                int sand = hsv(hue, rng.range(0.05, 0.15), rng.range(0.7, 0.85));
                double plantHue = rng.range(0.4, 0.8);
                int foliage = hsv(plantHue, rng.range(0.3, 0.5), rng.range(0.4, 0.6));
                int grass = hsv(plantHue + rng.range(-0.05, 0.05), rng.range(0.25, 0.45), rng.range(0.5, 0.7));
                int accent = hsv(rng.range(0.45, 0.6), rng.range(0.5, 0.8), rng.range(0.9, 1));
                yield new Ground(surface, soil, rock, sand, foliage, grass, accent);
            }
            case SCORCHED -> {
                int surface = hsv(rng.range(0, 0.08), rng.range(0.1, 0.4), rng.range(0.15, 0.32));
                int soil = hsv(rng.range(0, 0.08), rng.range(0.15, 0.4), rng.range(0.12, 0.25));
                int rock = hsv(rng.range(0, 0.06), rng.range(0.05, 0.3), rng.range(0.08, 0.2));
                // 硫磺沙偏黄，火山灰偏褐
                double sandHue = rng.chance(0.4) ? rng.range(0.13, 0.16) : rng.range(0.05, 0.12);
                int sand = hsv(sandHue, rng.range(0.3, 0.6), rng.range(0.35, 0.55));
                int foliage = hsv(rng.range(0, 0.1), rng.range(0.6, 0.9), rng.range(0.3, 0.5));
                int grass = hsv(rng.range(0, 0.1), rng.range(0.5, 0.8), rng.range(0.35, 0.55));
                int accent = hsv(rng.range(0.02, 0.12), rng.range(0.8, 1), rng.range(0.9, 1));
                yield new Ground(surface, soil, rock, sand, foliage, grass, accent);
            }
            case TOXIC -> {
                double hue = rng.chance(0.6) ? rng.range(0.12, 0.3) : rng.range(0.72, 0.92);
                int surface = hsv(hue, rng.range(0.35, 0.7), rng.range(0.35, 0.6));
                int soil = hsv(hue + rng.range(-0.05, 0.05), rng.range(0.3, 0.5), rng.range(0.25, 0.4));
                int rock = hsv(hue + rng.range(-0.1, 0.1), rng.range(0.1, 0.25), rng.range(0.3, 0.45));
                int sand = hsv(hue, rng.range(0.25, 0.45), rng.range(0.55, 0.75));
                double fungusHue = hue + rng.range(-0.15, 0.15);
                int foliage = hsv(fungusHue, rng.range(0.5, 0.85), rng.range(0.35, 0.65));
                int grass = hsv(fungusHue + rng.range(-0.05, 0.05), rng.range(0.45, 0.8), rng.range(0.4, 0.7));
                int accent = hsv(hue + 0.5, rng.range(0.7, 1), rng.range(0.8, 1));
                yield new Ground(surface, soil, rock, sand, foliage, grass, accent);
            }
            case IRRADIATED -> {
                double hue = rng.chance(0.5) ? rng.range(0.15, 0.28) : rng.range(0.42, 0.55);
                int surface = hsv(hue, rng.range(0.25, 0.5), rng.range(0.35, 0.55));
                int soil = hsv(hue, rng.range(0.2, 0.4), rng.range(0.25, 0.4));
                int rock = hsv(hue + rng.range(-0.05, 0.05), rng.range(0.08, 0.2), rng.range(0.25, 0.4));
                int sand = hsv(hue, rng.range(0.15, 0.35), rng.range(0.5, 0.7));
                int foliage = hsv(hue + rng.range(-0.1, 0.1), rng.range(0.6, 0.9), rng.range(0.55, 0.85));
                int grass = hsv(hue + rng.range(-0.1, 0.1), rng.range(0.55, 0.85), rng.range(0.6, 0.9));
                int accent = hsv(hue, rng.range(0.8, 1), rng.range(0.9, 1));
                yield new Ground(surface, soil, rock, sand, foliage, grass, accent);
            }
            case EXOTIC -> {
                double hue = rng.nextDouble();
                int surface = hsv(hue, rng.range(0.4, 0.85), rng.range(0.45, 0.85));
                int soil = hsv(hue + rng.range(-0.1, 0.1), rng.range(0.35, 0.7), rng.range(0.3, 0.55));
                int rock = hsv(rng.nextDouble(), rng.range(0.15, 0.5), rng.range(0.3, 0.6));
                int sand = hsv(rng.nextDouble(), rng.range(0.3, 0.6), rng.range(0.6, 0.9));
                double plantHue = rng.nextDouble();
                int foliage = hsv(plantHue, rng.range(0.5, 0.9), rng.range(0.4, 0.8));
                int grass = hsv(plantHue + rng.range(-0.1, 0.1), rng.range(0.5, 0.9), rng.range(0.45, 0.85));
                int accent = hsv(rng.nextDouble(), rng.range(0.8, 1), rng.range(0.9, 1));
                yield new Ground(surface, soil, rock, sand, foliage, grass, accent);
            }
            case GAS_GIANT -> {
                // 云带：暖色像木星，冷色像海王星，偶尔是别的颜色
                double roll = rng.nextDouble();
                double hue = roll < 0.45 ? rng.range(0.05, 0.12) : roll < 0.8 ? rng.range(0.5, 0.65) : rng.nextDouble();
                int band1 = hsv(hue, rng.range(0.3, 0.6), rng.range(0.7, 0.9));
                int band2 = hsv(hue + rng.range(-0.05, 0.05), rng.range(0.4, 0.7), rng.range(0.5, 0.7));
                int band3 = hsv(hue + rng.range(-0.1, 0.1), rng.range(0.2, 0.5), rng.range(0.8, 0.95));
                int ring = hsv(rng.range(0.08, 0.14), rng.range(0.1, 0.3), rng.range(0.7, 0.9));
                yield new Ground(band1, band2, band3, band1, band2, band3, ring);
            }
        };
    }

    private static int liquid(Rng rng, Liquid liquid, PlanetType type, int sand) {
        return switch (liquid) {
            case WATER -> type != PlanetType.EXOTIC && rng.chance(0.7)
                    ? hsv(rng.range(0.52, 0.64), rng.range(0.55, 0.8), rng.range(0.55, 0.85))
                    : hsv(rng.nextDouble(), rng.range(0.5, 0.8), rng.range(0.5, 0.8));
            case LAVA -> hsv(rng.range(0.02, 0.1), rng.range(0.85, 1), rng.range(0.95, 1));
            case ACID -> hsv(rng.range(0.15, 0.32), rng.range(0.65, 0.9), rng.range(0.6, 0.9));
            case ICE -> hsv(rng.range(0.5, 0.6), rng.range(0.2, 0.4), rng.range(0.85, 0.95));
            case NONE -> sand;
        };
    }

    private static Sky sky(Rng rng, PlanetType type, Atmosphere atmosphere, Star star, int surface) {
        int sky;
        int fog;
        int cloud;
        switch (atmosphere.kind()) {
            case NONE -> {
                // 真空里天空是黑的，远处的地面也没有雾气遮挡
                sky = hsv(rng.range(0.6, 0.7), 0.3, 0.02);
                fog = hsv(rng.range(0.6, 0.7), 0.1, 0.05);
                cloud = 0x000000;
            }
            case THIN -> {
                double hue = rng.chance(0.5) ? rgbHue(surface) : rgbHue(star.color());
                sky = hsv(hue, rng.range(0.25, 0.5), rng.range(0.15, 0.35));
                fog = Colors.lerp(sky, surface, 0.35);
                cloud = Colors.lerp(sky, 0xFFFFFF, 0.5);
            }
            case BREATHABLE -> {
                double hue = rng.chance(0.7) ? rng.range(0.53, 0.62) : rng.nextDouble();
                sky = hsv(hue, rng.range(0.35, 0.6), rng.range(0.85, 1));
                fog = Colors.lerp(sky, 0xFFFFFF, 0.4);
                cloud = Colors.lerp(0xFFFFFF, sky, 0.15);
            }
            case TOXIC -> {
                double hue = rng.chance(0.6) ? rng.range(0.1, 0.3) : rgbHue(surface);
                sky = hsv(hue, rng.range(0.35, 0.65), rng.range(0.55, 0.85));
                fog = Colors.lerp(sky, surface, 0.25);
                cloud = Colors.lerp(sky, 0xFFFFFF, 0.5);
            }
            case DENSE -> {
                double hue = type == PlanetType.EXOTIC ? rng.nextDouble() : rng.range(0.02, 0.12);
                sky = hsv(hue, rng.range(0.45, 0.75), rng.range(0.55, 0.8));
                fog = Colors.lerp(sky, 0xFFFFFF, 0.15);
                cloud = Colors.lerp(sky, 0xFFFFFF, 0.3);
            }
            default -> throw new IllegalStateException();
        }
        if (atmosphere.present()) {
            // 星光的颜色透过大气：红矮星下的天空偏暖，蓝巨星下偏冷
            int tint = Colors.multiply(sky, star.color());
            sky = Colors.lerp(sky, tint, 0.35);
            fog = Colors.lerp(fog, Colors.multiply(fog, star.color()), 0.25);
        }
        return new Sky(sky, fog, cloud);
    }

    /** RGB 的色相（圈）。 */
    static double rgbHue(int color) {
        double r = Colors.red(color) / 255.0;
        double g = Colors.green(color) / 255.0;
        double b = Colors.blue(color) / 255.0;
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double delta = max - min;
        if (delta < 1e-9) {
            return 0;
        }
        double hue;
        if (max == r) {
            hue = ((g - b) / delta) % 6;
        } else if (max == g) {
            hue = (b - r) / delta + 2;
        } else {
            hue = (r - g) / delta + 4;
        }
        hue /= 6;
        return hue < 0 ? hue + 1 : hue;
    }
}
