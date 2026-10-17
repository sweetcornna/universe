package io.github.sweetcornna.universe.core.ecology;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.sweetcornna.universe.core.galaxy.Ecology;
import io.github.sweetcornna.universe.core.galaxy.Palette;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.procgen.Colors;
import io.github.sweetcornna.universe.core.procgen.NameGenerator;
import io.github.sweetcornna.universe.core.procgen.NameGenerator.Name;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;

/**
 * 一颗星球的植物（玩法设计 §8）：地面植物和树各有哪些物种，长什么样、长在哪里。
 *
 * <p>物种由 {@link Ecology#seed()} 派生（派生键 {@code flora}），配色从星球调色板出发、各自偏移，
 * 所以一颗星球的植物彼此协调，又能一眼分出种来。客户端和服务端各自算出同样的结果。
 *
 * <p>方块按"形态 + 变体号"染色：没有物种占用的变体号也给一个由调色板推出的颜色，
 * 玩家把别的星球的植物带过来时不至于没有颜色。
 */
public final class Flora {
    /** 不在任何星球上时用的配色（主世界、物品栏）。 */
    public static final Flora NEUTRAL = generate(Ecology.barren(0), PlanetType.HABITABLE, Palette.NEUTRAL);

    private final List<PlantSpecies> plants;
    private final List<TreeSpecies> trees;
    /** [形态][变体][层]，层 0 主体、层 1 点缀。 */
    private final int[][][] plantColors;
    /** [变体][部位]，部位见 {@link TreePart}。 */
    private final int[][] treeColors;
    private final boolean[][] plantGlow;
    private final boolean[] bloomGlow;

    private Flora(List<PlantSpecies> plants, List<TreeSpecies> trees, int[][][] plantColors, int[][] treeColors,
            boolean[][] plantGlow, boolean[] bloomGlow) {
        this.plants = List.copyOf(plants);
        this.trees = List.copyOf(trees);
        this.plantColors = plantColors;
        this.treeColors = treeColors;
        this.plantGlow = plantGlow;
        this.bloomGlow = bloomGlow;
    }

    /** 树木方块的部位，用来查颜色。 */
    public enum TreePart {
        WOOD, LEAVES, BLOOM
    }

    public static Flora of(Planet planet) {
        return generate(planet.ecology(), planet.type(), planet.palette());
    }

    public List<PlantSpecies> plants() {
        return this.plants;
    }

    public List<TreeSpecies> trees() {
        return this.trees;
    }

    public boolean lifeless() {
        return this.plants.isEmpty() && this.trees.isEmpty();
    }

    /** 某个植物方块状态的颜色。 */
    public int plantColor(PlantForm form, int variant, int layer) {
        return this.plantColors[form.ordinal()][Math.floorMod(variant, PlantForm.VARIANTS)][layer & 1];
    }

    /** 某个植物方块状态会不会发光（没有物种占用的变体号不发光）。 */
    public boolean plantGlows(PlantForm form, int variant) {
        return this.plantGlow[form.ordinal()][Math.floorMod(variant, PlantForm.VARIANTS)];
    }

    /** 某个树木方块状态的颜色。 */
    public int treeColor(TreePart part, int variant) {
        return this.treeColors[Math.floorMod(variant, TreeSpecies.MAX)][part.ordinal()];
    }

    public boolean bloomGlows(int variant) {
        return this.bloomGlow[Math.floorMod(variant, TreeSpecies.MAX)];
    }

    public static Flora generate(Ecology ecology, PlanetType type, Palette palette) {
        Rng rng = Rng.of(Seeds.derive(ecology.seed(), "flora"));
        List<Biotope> biotopes = Biotope.forPlanet(type);
        boolean earthlike = type == PlanetType.HABITABLE || type == PlanetType.OCEAN;
        double glowChance = switch (type) {
            case EXOTIC -> 0.45;
            case TOXIC -> 0.35;
            case IRRADIATED -> 0.3;
            case FROZEN -> 0.15;
            default -> 0.08;
        };

        // 默认颜色：每个形态、每个变体号都有，由调色板推出
        int[][][] plantColors = new int[PlantForm.values().length][PlantForm.VARIANTS][2];
        boolean[][] plantGlow = new boolean[PlantForm.values().length][PlantForm.VARIANTS];
        Rng fill = Rng.of(Seeds.derive(ecology.seed(), "flora/fill"));
        for (PlantForm form : PlantForm.values()) {
            for (int v = 0; v < PlantForm.VARIANTS; v++) {
                int body = bodyColor(fill, form, palette);
                plantColors[form.ordinal()][v][0] = body;
                plantColors[form.ordinal()][v][1] = detailColor(fill, form, palette, body, false);
            }
        }
        int[][] treeColors = new int[TreeSpecies.MAX][TreePart.values().length];
        boolean[] bloomGlow = new boolean[TreeSpecies.MAX];
        for (int v = 0; v < TreeSpecies.MAX; v++) {
            treeColors[v][TreePart.WOOD.ordinal()] = vary(fill, Colors.adjust(palette.soil(), 0, 0.9, 0.75), 0.04, 0.2, 0.15);
            treeColors[v][TreePart.LEAVES.ordinal()] = vary(fill, palette.foliage(), 0.06, 0.15, 0.12);
            treeColors[v][TreePart.BLOOM.ordinal()] = bright(vary(fill, palette.accent(), 0.2, 0.1, 0.1));
        }

        List<PlantSpecies> plants = new ArrayList<>();
        if (!biotopes.isEmpty()) {
            Map<PlantForm, Integer> used = new EnumMap<>(PlantForm.class);
            for (int i = 0; i < ecology.plantSpecies(); i++) {
                Biotope home = biotopes.get(i % biotopes.size());
                List<PlantForm> options = Arrays.stream(PlantForm.values())
                        .filter(f -> f.affinity(home) > 0 && used.getOrDefault(f, 0) < PlantForm.VARIANTS)
                        .toList();
                if (options.isEmpty()) {
                    continue;
                }
                PlantForm form = rng.weighted(options, f -> f.affinity(home) * alienFactor(f.alien(), earthlike)
                        // 同一形态已经有物种的，降低再选中的机会，让形态更多样
                        / (1 + 1.5 * used.getOrDefault(f, 0)));
                int variant = used.getOrDefault(form, 0);
                used.put(form, variant + 1);
                Set<Biotope> lives = EnumSet.of(home);
                Biotope second = biotopes.get(rng.nextInt(biotopes.size()));
                if (rng.chance(0.5) && form.affinity(second) > 0) {
                    lives.add(second);
                }
                boolean glowing = form.canGlow() && rng.chance(form == PlantForm.CRYSTAL_BLOOM ? 0.8
                        : form == PlantForm.FUNGUS ? Math.min(1, glowChance * 1.5) : glowChance);
                int body = bodyColor(rng, form, palette);
                int detail = detailColor(rng, form, palette, body, glowing);
                // 一个生境里的第一种是优势种
                double abundance = rng.range(0.5, 1.5) * (i < biotopes.size() ? 1.6 : 1);
                Name name = speciesName(rng, form.nameZh(), form.nameEn());
                plants.add(new PlantSpecies(plants.size(), name, form, variant, body, detail, glowing, Set.copyOf(lives), abundance));
                plantColors[form.ordinal()][variant][0] = body;
                plantColors[form.ordinal()][variant][1] = detail;
                plantGlow[form.ordinal()][variant] = glowing;
            }
        }

        List<TreeSpecies> trees = new ArrayList<>();
        int treeCount = treeSpeciesCount(ecology);
        List<Biotope> treeBiotopes = biotopes.stream().filter(b -> b != Biotope.WASTE || ecology.trees() > 0.05).toList();
        for (int i = 0; i < treeCount && !treeBiotopes.isEmpty(); i++) {
            Biotope home = treeBiotopes.get(i % treeBiotopes.size());
            List<TreeShape> options = Arrays.stream(TreeShape.values()).filter(s -> s.affinity(home) > 0).toList();
            List<TreeShape> taken = trees.stream().map(TreeSpecies::shape).toList();
            TreeShape shape = rng.weighted(options, s -> s.affinity(home) * alienFactor(s.alien(), earthlike)
                    * (taken.contains(s) ? 0.2 : 1));
            Set<Biotope> lives = EnumSet.of(home);
            Biotope second = treeBiotopes.get(rng.nextInt(treeBiotopes.size()));
            if (rng.chance(0.4) && shape.affinity(second) > 0) {
                lives.add(second);
            }
            TreeSpecies tree = treeSpecies(rng, trees.size(), shape, palette, earthlike, Set.copyOf(lives));
            trees.add(tree);
            treeColors[tree.index()][TreePart.WOOD.ordinal()] = tree.wood();
            treeColors[tree.index()][TreePart.LEAVES.ordinal()] = tree.leaves();
            treeColors[tree.index()][TreePart.BLOOM.ordinal()] = tree.bloom();
            bloomGlow[tree.index()] = tree.bloomGlows();
        }
        return new Flora(plants, trees, plantColors, treeColors, plantGlow, bloomGlow);
    }

    /** 树种数：树木越密种类越多；没有树但有植被的星球也给一种灌木一类的。 */
    static int treeSpeciesCount(Ecology ecology) {
        if (ecology.lifeless()) {
            return 0;
        }
        if (ecology.trees() > 0.05) {
            return (int) Math.max(1, Math.min(TreeSpecies.MAX, Math.round(1 + ecology.trees() * 3)));
        }
        return ecology.flora() > 0.2 ? 1 : 0;
    }

    private static double alienFactor(boolean alien, boolean earthlike) {
        if (earthlike) {
            return alien ? 0.3 : 1;
        }
        return alien ? 1.6 : 0.7;
    }

    private static TreeSpecies treeSpecies(Rng rng, int index, TreeShape shape, Palette palette, boolean earthlike,
            Set<Biotope> biotopes) {
        int height;
        int range;
        int canopy;
        double lean = rng.range(0, 0.25);
        boolean thick = false;
        switch (shape) {
            case SHRUB -> {
                height = rng.range(0, 1);
                range = 1;
                canopy = rng.range(1, 2);
            }
            case ROUND -> {
                height = rng.range(4, 7);
                range = 3;
                canopy = rng.range(2, 4);
                thick = canopy >= 4 && rng.chance(0.4);
            }
            case CONIFER -> {
                height = rng.range(7, 12);
                range = 5;
                canopy = rng.range(2, 4);
                thick = height >= 11 && rng.chance(0.4);
            }
            case UMBRELLA -> {
                height = rng.range(6, 10);
                range = 3;
                canopy = rng.range(3, 5);
            }
            case PALM -> {
                height = rng.range(6, 10);
                range = 3;
                canopy = rng.range(3, 5);
                lean = rng.range(0.3, 0.6);
            }
            case WEEPING -> {
                height = rng.range(4, 7);
                range = 2;
                canopy = rng.range(3, 4);
                thick = rng.chance(0.3);
            }
            case MUSHROOM -> {
                height = rng.range(4, 8);
                range = 3;
                canopy = rng.range(3, 5);
                thick = canopy >= 4;
            }
            case BAOBAB -> {
                height = rng.range(5, 8);
                range = 2;
                canopy = rng.range(2, 3);
                thick = true;
            }
            case SPIRAL -> {
                height = rng.range(6, 11);
                range = 3;
                canopy = rng.range(1, 2);
            }
            case COLUMN -> {
                height = rng.range(4, 7);
                range = 2;
                canopy = rng.range(1, 3);
                lean = 0;
            }
            case CRYSTAL -> {
                height = rng.range(6, 11);
                range = 3;
                canopy = rng.range(2, 3);
            }
            default -> throw new IllegalStateException(shape.name());
        }
        double leafDensity = rng.range(0.65, 1);

        int wood;
        if (shape == TreeShape.MUSHROOM) {
            wood = vary(rng, Colors.lerp(palette.soil(), 0xFFFFFF, 0.55), 0.05, 0.2, 0.08);
        } else if (!earthlike && rng.chance(0.3)) {
            wood = vary(rng, Colors.adjust(palette.accent(), 0, 0.6, 0.6), 0.1, 0.2, 0.15);
        } else if (rng.chance(0.2)) {
            // 浅色树皮，像白桦
            wood = vary(rng, Colors.lerp(palette.soil(), 0xFFFFFF, 0.6), 0.04, 0.2, 0.08);
        } else {
            wood = vary(rng, Colors.adjust(palette.soil(), 0, 0.9, 0.75), 0.05, 0.2, 0.15);
        }
        int leaves = switch (shape) {
            case MUSHROOM -> rng.chance(0.6) ? vary(rng, palette.accent(), 0.15, 0.15, 0.1)
                    : vary(rng, Colors.adjust(palette.soil(), 0, 1.3, 1.25), 0.1, 0.15, 0.1);
            case COLUMN -> vary(rng, Colors.adjust(palette.foliage(), 0.04, 0.85, 1.0), 0.05, 0.15, 0.1);
            case CONIFER -> vary(rng, Colors.adjust(palette.foliage(), 0, 1.0, 0.8), 0.06, 0.15, 0.1);
            case CRYSTAL -> vary(rng, palette.accent(), 0.08, 0.1, 0.1);
            default -> vary(rng, palette.foliage(), 0.07, 0.18, 0.14);
        };
        int bloom = rng.chance(0.5) ? bright(vary(rng, palette.accent(), 0.25, 0.1, 0.1))
                : bright(Colors.adjust(leaves, 0.5 + rng.range(-0.1, 0.1), 1.2, 1.2));
        boolean blooms = switch (shape) {
            case MUSHROOM, SPIRAL, CRYSTAL -> rng.chance(0.75);
            case COLUMN -> rng.chance(0.6);
            default -> rng.chance(earthlike ? 0.35 : 0.55);
        };
        double bloomChance = blooms ? rng.range(0.03, 0.12) : 0;
        boolean bloomGlows = blooms && rng.chance(switch (shape) {
            case MUSHROOM, SPIRAL, CRYSTAL -> 0.7;
            default -> earthlike ? 0.1 : 0.5;
        });
        double abundance = rng.range(0.6, 1.4) * (index == 0 ? 1.5 : 1);
        Name name = speciesName(rng, shape.nameZh(), shape.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '));
        return new TreeSpecies(index, name, shape, height, range, canopy, leafDensity, lean, thick, wood, leaves, bloom,
                bloomChance, bloomGlows, biotopes, abundance);
    }

    private static int bodyColor(Rng rng, PlantForm form, Palette palette) {
        int base = switch (form) {
            case TUFT, BLADES -> palette.grass();
            case FERN, BULB, TENDRIL -> palette.foliage();
            case FLOWER -> Colors.adjust(palette.grass(), 0, 1.0, 0.85);
            case FUNGUS -> Colors.lerp(palette.soil(), 0xFFFFFF, 0.55);
            case REED -> Colors.lerp(palette.grass(), palette.sand(), 0.3);
            case SUCCULENT -> Colors.adjust(palette.foliage(), 0.05, 0.8, 1.1);
            case LICHEN -> Colors.lerp(palette.surface(), palette.grass(), 0.5);
            case CRYSTAL_BLOOM -> palette.accent();
        };
        return vary(rng, base, 0.06, 0.15, 0.12);
    }

    private static int detailColor(Rng rng, PlantForm form, Palette palette, int body, boolean glowing) {
        int color = switch (form) {
            case FLOWER, BULB -> rng.chance(0.5) ? vary(rng, palette.accent(), 0.2, 0.15, 0.1)
                    : Colors.adjust(body, 0.5 + rng.range(-0.1, 0.1), 1.3, 1.2);
            case FUNGUS -> rng.chance(0.5) ? vary(rng, palette.accent(), 0.15, 0.15, 0.1)
                    : vary(rng, Colors.adjust(palette.soil(), 0, 1.3, 1.2), 0.1, 0.15, 0.1);
            case TENDRIL -> vary(rng, palette.accent(), 0.15, 0.15, 0.1);
            case TUFT, BLADES -> Colors.lerp(body, palette.sand(), 0.6);
            case FERN -> Colors.adjust(body, 0, 0.8, 1.25);
            case REED -> vary(rng, Colors.adjust(palette.soil(), 0, 1.1, 0.8), 0.05, 0.1, 0.1);
            case SUCCULENT -> vary(rng, palette.accent(), 0.25, 0.15, 0.1);
            case LICHEN -> Colors.lerp(palette.accent(), palette.surface(), 0.4);
            case CRYSTAL_BLOOM -> Colors.adjust(palette.accent(), rng.range(-0.05, 0.05), 0.5, 1.3);
        };
        return glowing ? Colors.adjust(color, 0, 0.9, 2) : color;
    }

    /** 物种名：音译的名字加形态，例如"卡洛尔草丛"。 */
    private static Name speciesName(Rng rng, String formZh, String formEn) {
        Name base = NameGenerator.generate(rng, 2, 3);
        return new Name(base.en() + " " + formEn.toLowerCase(java.util.Locale.ROOT), base.zh() + formZh);
    }

    private static int vary(Rng rng, int base, double hue, double saturation, double value) {
        return Colors.adjust(base, rng.range(-hue, hue), 1 + rng.range(-saturation, saturation), 1 + rng.range(-value, value));
    }

    /** 花果要醒目：明度至少 0.75。 */
    private static int bright(int color) {
        double[] hsv = Colors.toHsv(color);
        return Colors.hsv(hsv[0], Math.max(hsv[1], 0.45), Math.max(hsv[2], 0.75));
    }
}
