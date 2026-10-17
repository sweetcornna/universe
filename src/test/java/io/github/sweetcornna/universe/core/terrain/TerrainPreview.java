package io.github.sweetcornna.universe.core.terrain;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Landform;
import io.github.sweetcornna.universe.core.galaxy.Liquid;
import io.github.sweetcornna.universe.core.galaxy.Palette;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;

/**
 * 离线渲染星球地形，调地形参数时用，不用进游戏。每颗星球出两张图：俯视的晕渲地图和远眺的透视图。
 *
 * <p>用法：{@code java -cp <main 和 test 的 classes> io.github.sweetcornna.universe.core.terrain.TerrainPreview
 * [世界种子] [输出目录] [每像素格数] [只渲染的地貌]}。
 * 默认每种星球类型挑两颗；给了地貌名（如 {@code karst}）就改为挑带这种地貌的星球。
 */
public final class TerrainPreview {
    private static final int SIZE = 384;
    private static final int VIEW_W = 640;
    private static final int VIEW_H = 384;
    private static int blocksPerPixel = 4;

    private TerrainPreview() {
    }

    public static void main(String[] args) throws IOException {
        long worldSeed = args.length > 0 ? Long.parseLong(args[0]) : 20261016L;
        File out = new File(args.length > 1 ? args[1] : "build/terrain-preview");
        blocksPerPixel = args.length > 2 ? Integer.parseInt(args[2]) : 4;
        Landform only = args.length > 3 ? Landform.valueOf(args[3].toUpperCase()) : null;
        if (args.length > 6) {
            // 可选：透视图的相机位置 x、z 和朝向（度，0 指向 +X、90 指向 +Z）
            camX = Double.parseDouble(args[4]);
            camZ = Double.parseDouble(args[5]);
            heading = Math.toRadians(Double.parseDouble(args[6]));
        }
        out.mkdirs();
        Galaxy galaxy = GalaxyGenerator.generate(worldSeed, 64);
        List<Planet> chosen = new ArrayList<>();
        if (only != null) {
            for (StarSystem system : galaxy.systems()) {
                for (Planet planet : system.bodies()) {
                    if (planet.landable() && planet.terrain().strength(only) > 0 && chosen.size() < 8) {
                        chosen.add(planet);
                    }
                }
            }
        } else {
            Map<PlanetType, List<Planet>> byType = new EnumMap<>(PlanetType.class);
            for (StarSystem system : galaxy.systems()) {
                for (Planet planet : system.bodies()) {
                    if (planet.landable()) {
                        byType.computeIfAbsent(planet.type(), t -> new ArrayList<>()).add(planet);
                    }
                }
            }
            for (List<Planet> planets : byType.values()) {
                chosen.add(planets.get(0));
                if (planets.size() > 1) {
                    chosen.add(planets.get(planets.size() - 1));
                }
            }
        }
        int tileW = SIZE + VIEW_W;
        int rowH = Math.max(SIZE, VIEW_H) + 16;
        int columns = 2;
        int sheetRows = (chosen.size() + columns - 1) / columns;
        BufferedImage sheet = new BufferedImage(columns * tileW, sheetRows * rowH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        for (int i = 0; i < chosen.size(); i++) {
            Planet planet = chosen.get(i);
            String landforms = planet.terrain().landforms().stream()
                    .map(f -> f.landform().serializedName() + String.format(" %.1f", f.strength()))
                    .collect(Collectors.joining(", "));
            String label = planet.type() + " " + planet.id().path() + " " + planet.liquid() + " [" + landforms + "]";
            BufferedImage map = render(planet);
            BufferedImage view = perspective(planet);
            String name = planet.id().path().replace('/', '_');
            ImageIO.write(map, "png", new File(out, name + "_map.png"));
            ImageIO.write(view, "png", new File(out, name + "_view.png"));
            int x = (i % columns) * tileW;
            int y = (i / columns) * rowH;
            g.drawImage(map, x, y + 16, null);
            g.drawImage(view, x + SIZE, y + 16, null);
            g.setColor(Color.WHITE);
            g.drawString(label, x + 4, y + 12);
        }
        g.dispose();
        ImageIO.write(sheet, "png", new File(out, "sheet.png"));
        System.out.println("Wrote " + chosen.size() + " planets to " + out.getAbsolutePath());
    }

    private static final int LAVA_GLOW = 0xFF7A1A;
    private static double camX = -700;
    private static double camZ = -700;
    private static double heading = Math.PI / 4;

    static BufferedImage render(Planet planet) {
        TerrainModel model = TerrainModel.of(planet);
        BiomeLayout layout = BiomeLayout.of(planet);
        Palette palette = planet.palette();
        double[][] heights = new double[SIZE][SIZE];
        BiomeKind[][] biomes = new BiomeKind[SIZE][SIZE];
        boolean[][] lava = new boolean[SIZE][SIZE];
        for (int px = 0; px < SIZE; px++) {
            for (int pz = 0; pz < SIZE; pz++) {
                int x = (px - SIZE / 2) * blocksPerPixel;
                int z = (pz - SIZE / 2) * blocksPerPixel;
                TerrainModel.Sample s = model.sample(x, z);
                heights[px][pz] = s.height();
                biomes[px][pz] = layout.pick(s);
                lava[px][pz] = s.lava() > 0.5;
            }
        }
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        int sea = model.seaLevel();
        boolean liquid = planet.liquid() != Liquid.NONE;
        for (int px = 0; px < SIZE; px++) {
            for (int pz = 0; pz < SIZE; pz++) {
                double h = heights[px][pz];
                int color;
                if (lava[px][pz]) {
                    color = LAVA_GLOW;
                } else if (liquid && h < sea) {
                    color = scale(liquidColor(planet), 1 - 0.55 * Math.min(1, (sea - h) / 40));
                } else {
                    color = surfaceColor(layout.top(biomes[px][pz]), palette, (int) h);
                    double hx = heights[Math.max(0, px - 1)][pz];
                    double hz = heights[px][Math.max(0, pz - 1)];
                    double shade = 1 + ((h - hx) + (h - hz)) * 0.04 * 4 / blocksPerPixel;
                    shade *= 0.85 + 0.3 * Math.min(1, Math.max(0, (h - sea + 20) / 160));
                    color = scale(color, Math.max(0.35, Math.min(1.5, shade)));
                }
                image.setRGB(px, pz, color);
            }
        }
        return image;
    }

    /**
     * 远眺透视图：经典的"体素空间"高度图渲染，从近到远逐列投影，用遮挡缓冲去掉被挡住的部分，远处叠加雾色。
     */
    static BufferedImage perspective(Planet planet) {
        TerrainModel model = TerrainModel.of(planet);
        BiomeLayout layout = BiomeLayout.of(planet);
        Palette palette = planet.palette();
        int sea = model.seaLevel();
        boolean liquid = planet.liquid() != Liquid.NONE;
        BufferedImage image = new BufferedImage(VIEW_W, VIEW_H, BufferedImage.TYPE_INT_RGB);
        int sky = palette.sky();
        int fog = palette.fog();
        for (int y = 0; y < VIEW_H; y++) {
            int c = lerpColor(sky, fog, Math.min(1, (double) y / (VIEW_H * 0.45)));
            for (int x = 0; x < VIEW_W; x++) {
                image.setRGB(x, y, c);
            }
        }
        double camH = Math.max(sea, model.sample((int) camX, (int) camZ).height()) + 60;
        double fov = Math.PI / 2.4;
        double horizon = VIEW_H * 0.36;
        double heightScale = VIEW_H * 0.9;
        double maxDistance = 2600;
        int[] yBuffer = new int[VIEW_W];
        Arrays.fill(yBuffer, VIEW_H);
        double distance = 4;
        while (distance < maxDistance) {
            double step = Math.max(1, distance / 150);
            for (int col = 0; col < VIEW_W; col++) {
                double angle = heading + fov * ((double) col / VIEW_W - 0.5);
                int wx = (int) Math.floor(camX + Math.cos(angle) * distance);
                int wz = (int) Math.floor(camZ + Math.sin(angle) * distance);
                TerrainModel.Sample s = model.sample(wx, wz);
                double h = s.height();
                int color;
                if (s.lava() > 0.5) {
                    color = LAVA_GLOW;
                } else if (liquid && h < sea) {
                    h = sea;
                    color = liquidColor(planet);
                } else {
                    BiomeKind biome = layout.pick(s);
                    Surface surface = layout.top(biome);
                    double hx = model.sample(wx + 2, wz).height();
                    double hz = model.sample(wx, wz + 2).height();
                    double slope = Math.max(Math.abs(hx - s.height()), Math.abs(hz - s.height())) / 2;
                    if (slope > 1.2) {
                        surface = biome.stratified() ? Surface.STRATA : Surface.ROCK;
                    }
                    color = surfaceColor(surface, palette, (int) h);
                    double light = 0.7 + 0.55 * clamp((s.height() - hx) * 0.12 + (s.height() - hz) * 0.08 + 0.45, 0, 1);
                    color = scale(color, light);
                }
                color = lerpColor(color, fog, Math.pow(distance / maxDistance, 1.3) * 0.85);
                int screenY = (int) ((camH - h) / distance * heightScale + horizon);
                if (screenY < yBuffer[col]) {
                    for (int y = Math.max(0, screenY); y < yBuffer[col]; y++) {
                        image.setRGB(col, y, color);
                    }
                    yBuffer[col] = Math.max(0, screenY);
                }
            }
            distance += step;
        }
        return image;
    }

    private static int liquidColor(Planet planet) {
        return switch (planet.liquid()) {
            case LAVA -> 0xE0601A;
            case ICE -> 0xC8E0F0;
            default -> planet.palette().liquid();
        };
    }

    private static int surfaceColor(Surface surface, Palette p, int y) {
        return switch (surface) {
            case TURF -> p.grass();
            case SOIL -> p.soil();
            case SAND -> p.sand();
            case ROCK -> p.rock();
            case REGOLITH, ASH -> p.surface();
            case SNOW -> 0xF4F8FF;
            case ICE -> 0xBFD8F2;
            case CRYSTAL -> p.accent();
            case STRATA -> strata(p, y);
        };
    }

    /** 岩层的近似颜色：按高度在岩石、沙、土、点缀色之间循环。 */
    private static int strata(Palette p, int y) {
        int[] bands = {p.rock(), p.sand(), lerpColor(p.soil(), p.accent(), 0.3), p.sand(), lerpColor(p.rock(), p.soil(), 0.5)};
        return bands[Math.floorMod(Math.floorDiv(y, 4), bands.length)];
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int lerpColor(int a, int b, double t) {
        int r = (int) (((a >> 16) & 0xFF) + t * (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)));
        int g = (int) (((a >> 8) & 0xFF) + t * (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)));
        int bl = (int) ((a & 0xFF) + t * ((b & 0xFF) - (a & 0xFF)));
        return (r << 16) | (g << 8) | bl;
    }

    private static int scale(int rgb, double f) {
        int r = (int) Math.min(255, ((rgb >> 16) & 0xFF) * f);
        int g = (int) Math.min(255, ((rgb >> 8) & 0xFF) * f);
        int b = (int) Math.min(255, (rgb & 0xFF) * f);
        return (r << 16) | (g << 8) | b;
    }
}
