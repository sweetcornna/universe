package io.github.sweetcornna.universe.core.ecology;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.galaxy.Ecology;
import io.github.sweetcornna.universe.core.galaxy.Liquid;
import io.github.sweetcornna.universe.core.galaxy.Palette;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.procgen.Colors;
import io.github.sweetcornna.universe.core.procgen.NameGenerator;
import io.github.sweetcornna.universe.core.procgen.NameGenerator.Name;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;
import io.github.sweetcornna.universe.core.terrain.BiomeKind;
import io.github.sweetcornna.universe.core.ecology.CreatureSpecies.Body;
import io.github.sweetcornna.universe.core.ecology.CreatureSpecies.Crest;
import io.github.sweetcornna.universe.core.ecology.CreatureSpecies.Dorsal;
import io.github.sweetcornna.universe.core.ecology.CreatureSpecies.Pattern;
import io.github.sweetcornna.universe.core.ecology.CreatureSpecies.Skin;
import io.github.sweetcornna.universe.core.ecology.CreatureSpecies.Voice;

/**
 * 一颗星球的动物（玩法设计 §8）：体型模板 × 部件 × 配色 × 大小，再配上性情和属性。
 *
 * <p>物种由 {@link Ecology#seed()} 派生（派生键 {@code fauna}），客户端和服务端各自算出同样的结果，
 * 实体只需要同步物种序号。
 *
 * <p>有海的星球另有 3–6 种水生动物（ADR-0026），用自己的派生键 {@code fauna/aquatic}，陆地上的物种一个不变。
 * 水生物种的序号从 {@link #MARINE_INDEX} 起，和陆地物种的序号、发现记录不会撞上。
 */
public final class Fauna {
    /** 不在星球上时（例如在主世界用命令召唤）用的一套动物。 */
    public static final Fauna FALLBACK = generate(new Ecology(1, 1, 1, 0, 6, 0x5EED), PlanetType.HABITABLE, Palette.NEUTRAL, Liquid.WATER);
    /** 水生物种的第一个序号。 */
    public static final int MARINE_INDEX = 64;

    private final List<CreatureSpecies> species;
    private final List<CreatureSpecies> marine;

    private Fauna(List<CreatureSpecies> species, List<CreatureSpecies> marine) {
        this.species = List.copyOf(species);
        this.marine = List.copyOf(marine);
    }

    public static Fauna of(Planet planet) {
        return generate(planet.ecology(), planet.type(), planet.palette(), planet.liquid());
    }

    /** 陆地上和天上的物种。 */
    public List<CreatureSpecies> species() {
        return this.species;
    }

    /** 水里的物种，没有海的星球是空的。 */
    public List<CreatureSpecies> marine() {
        return this.marine;
    }

    /** 全部物种：陆地的在前，水里的在后。 */
    public List<CreatureSpecies> all() {
        if (this.marine.isEmpty()) {
            return this.species;
        }
        List<CreatureSpecies> all = new ArrayList<>(this.species);
        all.addAll(this.marine);
        return List.copyOf(all);
    }

    public boolean lifeless() {
        return this.species.isEmpty();
    }

    /** 按序号取物种，越界时取模（存档里的序号对不上时也有个物种可用）。水生的序号找不到水生物种时退回陆地的。 */
    public CreatureSpecies get(int index) {
        if (index >= MARINE_INDEX && !this.marine.isEmpty() || this.species.isEmpty()) {
            return this.marine.get(Math.floorMod(index - MARINE_INDEX, this.marine.size()));
        }
        return this.species.get(Math.floorMod(index, this.species.size()));
    }

    /** 序号正好对得上的物种（发现记录用），没有就是空。 */
    public Optional<CreatureSpecies> find(int index) {
        List<CreatureSpecies> list = index >= MARINE_INDEX ? this.marine : this.species;
        int i = index >= MARINE_INDEX ? index - MARINE_INDEX : index;
        return i >= 0 && i < list.size() ? Optional.of(list.get(i)) : Optional.empty();
    }

    /** 液体是能游泳的海：水，或者冰面下的水。 */
    public static boolean hasSea(Liquid liquid) {
        return liquid == Liquid.WATER || liquid == Liquid.ICE;
    }

    /**
     * 在水里刷哪种动物。
     *
     * @param deep 这里离水面够深（鳗和巨型乌贼只在深水里）
     * @param roll 0–1 的随机数
     * @return 没有合适的物种时返回 null
     */
    public @Nullable CreatureSpecies pickMarine(boolean deep, double roll) {
        List<CreatureSpecies> candidates = new ArrayList<>();
        double total = 0;
        for (CreatureSpecies s : this.marine) {
            if (deep || !deepWater(s)) {
                candidates.add(s);
                total += s.abundance();
            }
        }
        double target = roll * total;
        for (CreatureSpecies s : candidates) {
            target -= s.abundance();
            if (target < 0) {
                return s;
            }
        }
        return candidates.isEmpty() ? null : candidates.get(candidates.size() - 1);
    }

    /** 只住在深水里的：鳗和巨型乌贼。 */
    public static boolean deepWater(CreatureSpecies species) {
        return species.plan() == BodyPlan.EEL || giant(species);
    }

    /** 巨型的（乌贼里少数长到普通的 2–6 倍）。 */
    public static boolean giant(CreatureSpecies species) {
        return species.plan() == BodyPlan.SQUID && species.size() >= GIANT_SIZE;
    }

    /**
     * 在这个群系里刷哪种动物。
     *
     * @param roll 0–1 的随机数
     * @return 这里没有合适的物种时返回 null
     */
    public CreatureSpecies pick(BiomeKind biome, double roll) {
        Biotope biotope = Biotope.of(biome);
        List<CreatureSpecies> candidates = new ArrayList<>();
        double total = 0;
        for (CreatureSpecies s : this.species) {
            // 飞兽也能飞过海面
            if (biotope != null ? s.biotopes().contains(biotope) : s.plan().flying()) {
                candidates.add(s);
                total += s.abundance();
            }
        }
        double target = roll * total;
        for (CreatureSpecies s : candidates) {
            target -= s.abundance();
            if (target < 0) {
                return s;
            }
        }
        return candidates.isEmpty() ? null : candidates.get(candidates.size() - 1);
    }

    /** 没有海的一套动物。 */
    public static Fauna generate(Ecology ecology, PlanetType type, Palette palette) {
        return generate(ecology, type, palette, Liquid.NONE);
    }

    public static Fauna generate(Ecology ecology, PlanetType type, Palette palette, Liquid liquid) {
        List<CreatureSpecies> land = land(ecology, type, palette);
        List<CreatureSpecies> marine = land.isEmpty() || !hasSea(liquid) ? List.of() : marine(ecology, type, palette);
        return new Fauna(land, marine);
    }

    private static List<CreatureSpecies> land(Ecology ecology, PlanetType type, Palette palette) {
        Rng rng = Rng.of(Seeds.derive(ecology.seed(), "fauna"));
        List<Biotope> biotopes = Biotope.forPlanet(type).stream().filter(b -> b != Biotope.WASTE || ecology.fauna() > 0.1).toList();
        if (biotopes.isEmpty()) {
            biotopes = Biotope.forPlanet(type);
        }
        boolean earthlike = type == PlanetType.HABITABLE || type == PlanetType.OCEAN;
        double hostility = switch (type) {
            case TOXIC, IRRADIATED -> 0.45;
            case EXOTIC, SCORCHED -> 0.35;
            case FROZEN -> 0.3;
            default -> 0.2;
        };
        List<CreatureSpecies> list = new ArrayList<>();
        int predators = 0;
        for (int i = 0; i < ecology.animalSpecies() && !biotopes.isEmpty(); i++) {
            Rng r = rng.fork("species/" + i);
            BodyPlan plan = r.weighted(List.of(BodyPlan.values()), p -> switch (p) {
                case QUADRUPED -> 3.0;
                case HEXAPOD -> earthlike ? 0.8 : 2.0;
                case BIPED -> 1.2;
                case SERPENT -> earthlike ? 0.6 : 1.4;
                case FLYER -> 1.3;
                // 水生的另算，权重为零时抽签和以前一样
                case FISH, EEL, JELLY, SQUID -> 0.0;
            });
            Temperament temperament;
            if (i == 0) {
                // 每颗星球至少有一种温顺的动物
                temperament = Temperament.DOCILE;
            } else {
                double roll = r.nextDouble();
                if (roll < hostility * 0.45 && predators < 2 && plan != BodyPlan.FLYER) {
                    temperament = Temperament.PREDATOR;
                } else if (roll < hostility) {
                    temperament = Temperament.TERRITORIAL;
                } else if (roll < hostility + 0.3) {
                    temperament = Temperament.SKITTISH;
                } else {
                    temperament = Temperament.DOCILE;
                }
            }
            if (temperament == Temperament.PREDATOR) {
                predators++;
            }
            Set<Biotope> lives = EnumSet.of(biotopes.get(i % biotopes.size()));
            if (r.chance(0.6)) {
                lives.add(biotopes.get(r.nextInt(biotopes.size())));
            }
            list.add(species(r, list.size(), plan, temperament, palette, earthlike, type, Set.copyOf(lives)));
        }
        return list;
    }

    private static CreatureSpecies species(Rng r, int index, BodyPlan plan, Temperament temperament, Palette palette,
            boolean earthlike, PlanetType type, Set<Biotope> biotopes) {
        Body body = body(r, plan, temperament, earthlike);
        double size = switch (plan) {
            case QUADRUPED -> r.range(0.5, 1.9);
            case HEXAPOD -> r.range(0.45, 1.5);
            case BIPED -> r.range(0.6, 2.0);
            case SERPENT -> r.range(0.45, 1.4);
            case FLYER -> r.range(0.4, 1.1);
            case FISH, EEL, JELLY, SQUID -> throw new IllegalArgumentException("aquatic plan " + plan);
        };
        if (temperament == Temperament.PREDATOR) {
            size = Math.max(size, 0.9);
        }
        Skin skin = skin(r, palette, temperament, earthlike, type);
        double health = Math.round(Math.min(60, Math.max(4, 4 + 14 * size * size)));
        double speed = switch (plan) {
            case QUADRUPED -> r.range(0.18, 0.28);
            case HEXAPOD -> r.range(0.22, 0.32);
            case BIPED -> r.range(0.22, 0.33);
            case SERPENT -> r.range(0.14, 0.22);
            case FLYER -> r.range(0.25, 0.4);
            case FISH, EEL, JELLY, SQUID -> throw new IllegalArgumentException("aquatic plan " + plan);
        } + (temperament == Temperament.PREDATOR ? 0.03 : 0);
        // 原版的狼咬一口 4 点，僵尸 3 点；捕食者按个子在这附近
        double attack = switch (temperament) {
            case PREDATOR -> Math.round((1.5 + 3 * size) * 2) / 2.0;
            case TERRITORIAL -> Math.round((1 + 2 * size) * 2) / 2.0;
            default -> 0;
        };
        int groupMin;
        int groupMax;
        switch (temperament) {
            case DOCILE -> {
                groupMin = 2;
                groupMax = r.range(3, 6);
            }
            case SKITTISH -> {
                groupMin = 1;
                groupMax = r.range(2, 4);
            }
            case TERRITORIAL -> {
                groupMin = 1;
                groupMax = r.range(1, 3);
            }
            default -> {
                groupMin = 1;
                groupMax = r.range(1, 2);
            }
        }
        double abundance = r.range(0.6, 1.4) * (temperament == Temperament.PREDATOR ? 0.4 : 1);
        Voice voice = voice(r, plan, size, temperament);
        Name base = NameGenerator.generate(r, 2, 3);
        Name name = new Name(base.en() + " " + plan.nameEn(), base.zh() + plan.nameZh());
        return new CreatureSpecies(index, name, plan, temperament, size, body, skin, health, speed, attack, groupMin, groupMax,
                biotopes, abundance, voice);
    }

    private static Body body(Rng r, BodyPlan plan, Temperament temperament, boolean earthlike) {
        int eyes = earthlike || r.chance(0.5) ? 2 : r.pick(new Integer[] {1, 3, 4, 4, 6});
        Crest crest = r.weighted(List.of(Crest.values()), c -> switch (c) {
            case NONE -> 1.5;
            case EARS -> plan == BodyPlan.QUADRUPED ? 1.5 : 0.3;
            case ANTENNAE -> plan == BodyPlan.HEXAPOD || plan == BodyPlan.SERPENT ? 2.0 : 0.3;
            case FRILL -> 0.4;
        });
        Dorsal dorsal = r.weighted(List.of(Dorsal.values()), d -> d == Dorsal.NONE ? 2.0 : plan == BodyPlan.FLYER ? 0 : 0.5);
        int horns = r.chance(plan == BodyPlan.QUADRUPED ? 0.35 : 0.15) ? r.range(1, 3) : 0;
        boolean hunter = temperament == Temperament.PREDATOR;
        return switch (plan) {
            case QUADRUPED -> new Body(r.range(9, 16), r.range(5, 10), r.range(5, 9), r.range(4, 11), r.range(2, 4), 2,
                    r.range(0, 6), r.range(4, 7), r.range(4, 6), r.range(4, 7), r.chance(hunter ? 0.8 : 0.5) ? r.range(2, 4) : 0,
                    r.chance(0.8) ? r.range(3, 12) : 0, r.range(1, 3), horns, r.range(2, 5), crest, dorsal, eyes, 0, 1);
            case HEXAPOD -> new Body(r.range(10, 18), r.range(6, 11), r.range(4, 7), r.range(3, 7), 2, 3,
                    0, r.range(4, 7), r.range(3, 5), r.range(3, 6), r.chance(0.3) ? r.range(1, 3) : 0,
                    r.chance(0.4) ? r.range(3, 7) : 0, 1, horns, r.range(2, 4), crest, dorsal, eyes, 0, 1);
            case BIPED -> new Body(r.range(7, 12), r.range(5, 8), r.range(6, 9), r.range(8, 14), r.range(2, 4), 1,
                    r.range(2, 8), r.range(4, 6), r.range(4, 6), r.range(4, 7), r.chance(hunter ? 0.9 : 0.5) ? r.range(2, 5) : 0,
                    r.range(6, 14), r.range(2, 3), horns, r.range(2, 4), crest, dorsal, eyes, 0, 1);
            case SERPENT -> new Body(r.range(4, 6), r.range(4, 7), r.range(3, 5), r.chance(0.5) ? r.range(1, 3) : 0, 2, 1,
                    0, r.range(4, 6), r.range(3, 5), r.range(4, 6), r.chance(0.4) ? r.range(1, 3) : 0,
                    0, 1, horns, r.range(2, 3), crest, dorsal, eyes, 0, r.range(4, 7));
            case FLYER -> new Body(r.range(4, 7), r.range(3, 5), r.range(3, 5), r.range(2, 3), 1, 1,
                    r.range(0, 2), r.range(3, 5), r.range(3, 4), r.range(3, 5), r.chance(0.6) ? r.range(1, 3) : 0,
                    r.range(2, 8), 1, 0, 2, crest == Crest.FRILL ? Crest.NONE : crest, Dorsal.NONE, eyes, r.range(6, 13), 1);
            case FISH, EEL, JELLY, SQUID -> throw new IllegalArgumentException("aquatic plan " + plan);
        };
    }

    private static Skin skin(Rng r, Palette palette, Temperament temperament, boolean earthlike, PlanetType type) {
        // 动物多是土色系，奇异的星球上鲜艳一些
        int base;
        double pick = r.nextDouble();
        if (earthlike) {
            base = pick < 0.4 ? palette.soil() : pick < 0.7 ? palette.sand() : pick < 0.85 ? palette.rock() : palette.foliage();
            base = Colors.adjust(base, r.range(-0.05, 0.05), r.range(0.7, 1.2), r.range(0.8, 1.15));
        } else {
            base = pick < 0.35 ? palette.accent() : pick < 0.6 ? palette.foliage() : pick < 0.8 ? palette.surface() : palette.soil();
            base = Colors.adjust(base, r.range(-0.12, 0.12), r.range(0.8, 1.3), r.range(0.8, 1.2));
        }
        int belly = Colors.adjust(base, 0, 0.6, 1.3);
        Pattern patternType = r.weighted(List.of(Pattern.values()), p -> p == Pattern.NONE ? 1.2 : 1);
        int pattern = r.chance(0.5) ? Colors.adjust(base, 0, 1.1, 0.55)
                : Colors.adjust(palette.accent(), r.range(-0.15, 0.15), 1.1, 1.0);
        int eye;
        if (temperament == Temperament.PREDATOR) {
            eye = r.chance(0.5) ? 0xFF3A1E : 0xFFB020;
        } else {
            eye = r.pick(new Integer[] {0x1A1A1A, 0x2B1B10, 0xE8D040, 0x40C8E8, 0x60E060});
        }
        boolean glowing = !earthlike && patternType != Pattern.NONE && r.chance(type == PlanetType.EXOTIC || type == PlanetType.TOXIC ? 0.35 : 0.15);
        if (glowing) {
            pattern = Colors.adjust(palette.accent(), r.range(-0.2, 0.2), 1.0, 2.0);
        }
        boolean glowingEyes = temperament == Temperament.PREDATOR ? r.chance(0.5) : !earthlike && r.chance(0.15);
        if (glowingEyes && Colors.luminance(eye) < 0.4) {
            eye = 0x80F0FF;
        }
        return new Skin(base, belly, pattern, eye, patternType, glowing, glowingEyes);
    }

    private static Voice voice(Rng r, BodyPlan plan, double size, Temperament temperament) {
        return switch (plan) {
            case QUADRUPED -> temperament == Temperament.PREDATOR ? Voice.GRUNT
                    : size > 1.2 ? r.pick(new Voice[] {Voice.LOW, Voice.GRUNT}) : r.pick(new Voice[] {Voice.BLEAT, Voice.GRUNT, Voice.TRILL});
            case HEXAPOD -> r.pick(new Voice[] {Voice.HISS, Voice.CHIRP, Voice.HUM});
            case BIPED -> temperament == Temperament.PREDATOR ? Voice.SCREECH : r.pick(new Voice[] {Voice.TRILL, Voice.CHIRP, Voice.CROAK});
            case SERPENT -> r.pick(new Voice[] {Voice.HISS, Voice.CROAK});
            case FLYER -> r.pick(new Voice[] {Voice.CHIRP, Voice.SCREECH, Voice.TRILL});
            // 水里的不出声，叫声只是占位
            case FISH, EEL, JELLY, SQUID -> Voice.HUM;
        };
    }

    // ---------------------------------------------------------------- 水生动物（ADR-0026）

    /** 乌贼长到这么大（普通的两倍起）算巨型。 */
    static final double GIANT_SIZE = 1.9;

    /** 有海的星球上的水生物种：第一种是成群的鱼，第二种是水母或乌贼，其余随机，3–6 种（海洋星球至少 5 种）。 */
    private static List<CreatureSpecies> marine(Ecology ecology, PlanetType type, Palette palette) {
        Rng rng = Rng.of(Seeds.derive(ecology.seed(), "fauna/aquatic"));
        int count = rng.range(type == PlanetType.OCEAN ? 5 : 3, 6);
        boolean earthlike = type == PlanetType.HABITABLE || type == PlanetType.OCEAN;
        List<CreatureSpecies> list = new ArrayList<>();
        int eels = 0;
        for (int i = 0; i < count; i++) {
            Rng r = rng.fork("species/" + i);
            BodyPlan plan;
            if (i == 0) {
                plan = BodyPlan.FISH;
            } else if (i == 1) {
                plan = r.chance(0.5) ? BodyPlan.JELLY : BodyPlan.SQUID;
            } else {
                int eelsSoFar = eels;
                plan = r.weighted(List.of(BodyPlan.FISH, BodyPlan.EEL, BodyPlan.JELLY, BodyPlan.SQUID), p -> switch (p) {
                    case FISH -> 2.5;
                    case EEL -> eelsSoFar < 2 ? 1.2 : 0;
                    default -> 1.0;
                });
            }
            if (plan == BodyPlan.EEL) {
                eels++;
            }
            list.add(marineSpecies(r, MARINE_INDEX + i, plan, palette, earthlike, type));
        }
        return list;
    }

    private static CreatureSpecies marineSpecies(Rng r, int index, BodyPlan plan, Palette palette, boolean earthlike, PlanetType type) {
        boolean giant = plan == BodyPlan.SQUID && r.chance(0.3);
        double size = switch (plan) {
            case FISH -> r.range(0.35, 0.8);
            case EEL -> r.range(0.9, 1.6);
            case JELLY -> r.range(0.5, 1.2);
            default -> giant ? r.range(GIANT_SIZE + 0.1, 6.0) : r.range(0.7, 1.3);
        };
        Temperament temperament = switch (plan) {
            case FISH -> r.chance(0.6) ? Temperament.SKITTISH : Temperament.DOCILE;
            case EEL -> Temperament.PREDATOR;
            case JELLY -> Temperament.DOCILE;
            default -> giant ? Temperament.TERRITORIAL : r.chance(0.5) ? Temperament.SKITTISH : Temperament.DOCILE;
        };
        Body body = marineBody(r, plan, earthlike);
        Skin skin = marineSkin(r, plan, palette, earthlike, type);
        // 巨型乌贼的血按个子长，最多 200（和巨型生物一样）
        double health = switch (plan) {
            case FISH -> Math.round(3 + 4 * size);
            case EEL -> Math.round(8 + 10 * size);
            case JELLY -> Math.round(4 + 5 * size);
            default -> Math.round(Math.min(200, 12 * Math.pow(size, 1.4)));
        };
        // 游速是原版移动速度属性的值，乘上游泳的加速度（鱼约 2 格/秒，鳗冲起来更快，水母只是漂）
        double speed = switch (plan) {
            case FISH -> r.range(0.9, 1.3);
            case EEL -> r.range(1.1, 1.5);
            case JELLY -> r.range(0.25, 0.4);
            default -> giant ? r.range(0.5, 0.7) : r.range(0.8, 1.1);
        };
        // 鳗咬一口和狼差不多；水母蜇一下 2–3 点；巨型乌贼被惹急了用触手抽人
        double attack = switch (plan) {
            case EEL -> Math.round((2 + 2.5 * size) * 2) / 2.0;
            case JELLY -> Math.round((1.5 + size) * 2) / 2.0;
            case SQUID -> giant ? Math.round(3 + 1.5 * size) : 0;
            default -> 0;
        };
        int groupMin = switch (plan) {
            case FISH -> 4;
            case JELLY -> 2;
            default -> 1;
        };
        int groupMax = switch (plan) {
            case FISH -> r.range(6, 10);
            case JELLY -> r.range(3, 5);
            case SQUID -> giant ? 1 : r.range(1, 3);
            default -> 1;
        };
        double abundance = switch (plan) {
            case FISH -> r.range(1.5, 2.5);
            case EEL -> r.range(0.4, 0.7);
            case JELLY -> r.range(0.7, 1.2);
            default -> giant ? 0.25 : r.range(0.6, 1.0);
        };
        Name base = NameGenerator.generate(r, 2, 3);
        Name name = giant ? new Name(base.en() + " Giant " + plan.nameEn(), base.zh() + "巨型" + plan.nameZh())
                : new Name(base.en() + " " + plan.nameEn(), base.zh() + plan.nameZh());
        return new CreatureSpecies(index, name, plan, temperament, size, body, skin, health, speed, attack, groupMin, groupMax,
                Set.of(), abundance, Voice.HUM);
    }

    /**
     * 水生动物的身体（模型像素）。几个字段换了用处：鱼的翼展是胸鳍长、尾巴是尾鳍长；水母的长、宽是钟口直径，腿是触手，
     * 头是钟下的口腕；乌贼的长是套膜，腿是腕，翼展是套膜尖上的鳍。
     */
    private static Body marineBody(Rng r, BodyPlan plan, boolean earthlike) {
        int eyes = earthlike || r.chance(0.7) ? 2 : 4;
        return switch (plan) {
            case FISH -> {
                int w = r.range(2, 4);
                int h = r.range(3, 7);
                Dorsal dorsal = r.weighted(List.of(Dorsal.NONE, Dorsal.FIN, Dorsal.SPIKES), d -> d == Dorsal.FIN ? 2.0 : d == Dorsal.NONE ? 1.0 : 0.4);
                yield new Body(r.range(6, 12), w, h, 0, 1, 0, 0, w, Math.max(2, h - 1), r.range(2, 3), r.chance(0.3) ? r.range(1, 2) : 0,
                        r.range(3, 6), 1, 0, 2, r.chance(0.15) ? Crest.ANTENNAE : Crest.NONE, dorsal, eyes, r.range(2, 4), 1);
            }
            case EEL -> {
                int w = r.range(2, 3);
                int h = r.range(2, 4);
                yield new Body(r.range(3, 4), w, h, 0, 1, 0, 0, w + 1, h + 1, r.range(3, 5), r.range(1, 3), r.range(2, 4), 1, 0, 2,
                        Crest.NONE, r.chance(0.6) ? Dorsal.FIN : Dorsal.NONE, eyes, 0, r.range(6, 10));
            }
            case JELLY -> {
                int bell = r.range(6, 12);
                int core = Math.max(2, bell / 3);
                yield new Body(bell, bell, r.range(3, 7), r.range(8, 18), 1, r.range(3, 5), 0, core, r.range(3, 6), core, 0, 0, 1, 0, 2,
                        Crest.NONE, Dorsal.NONE, 0, 0, 1);
            }
            default -> {
                int w = r.range(4, 7);
                int h = r.range(4, 7);
                yield new Body(r.range(9, 15), w, h, r.range(6, 12), 1, 4, 0, w, h, r.range(3, 4), 0, 0, 1, 0, 2,
                        Crest.NONE, Dorsal.NONE, 2, r.range(2, 4), 1);
            }
        };
    }

    private static Skin marineSkin(Rng r, BodyPlan plan, Palette palette, boolean earthlike, PlanetType type) {
        double pick = r.nextDouble();
        int base = switch (plan) {
            // 鱼背上多是水的颜色或银灰，少数鲜艳
            case FISH -> Colors.adjust(pick < 0.35 ? palette.liquid() : pick < 0.65 ? 0x9FB2BC : pick < 0.85 ? palette.accent() : palette.sand(),
                    r.range(-0.08, 0.08), r.range(0.6, 1.2), r.range(0.8, 1.1));
            // 鳗住在深处，颜色暗
            case EEL -> Colors.adjust(pick < 0.5 ? palette.rock() : palette.foliage(), r.range(-0.05, 0.05), r.range(0.6, 1.0), r.range(0.5, 0.75));
            // 水母淡而透亮
            case JELLY -> Colors.adjust(pick < 0.5 ? palette.accent() : palette.liquid(), r.range(-0.15, 0.15), r.range(0.4, 0.8), r.range(1.2, 1.5));
            // 乌贼偏红褐，奇异的星球上用点缀色
            default -> Colors.adjust(earthlike && pick < 0.6 ? 0xB4584A : palette.accent(), r.range(-0.08, 0.08), r.range(0.7, 1.1), r.range(0.75, 1.05));
        };
        // 水里的动物多是背深腹浅
        int belly = Colors.adjust(base, 0, 0.4, plan == BodyPlan.JELLY ? 1.15 : 1.45);
        Pattern patternType = switch (plan) {
            case FISH -> r.weighted(List.of(Pattern.values()), p -> switch (p) {
                case GRADIENT -> 2.0;
                case STRIPES, SPOTS, DORSAL_BAND -> 1.0;
                case NONE -> 0.8;
                case SADDLE -> 0.3;
            });
            case JELLY -> r.pick(new Pattern[] {Pattern.SPOTS, Pattern.STRIPES, Pattern.NONE});
            default -> r.pick(new Pattern[] {Pattern.SPOTS, Pattern.GRADIENT, Pattern.DORSAL_BAND, Pattern.NONE});
        };
        int pattern = r.chance(0.5) ? Colors.adjust(base, 0, 1.1, 0.55) : Colors.adjust(palette.accent(), r.range(-0.15, 0.15), 1.1, 1.0);
        // 生物荧光：水母多半会发光，深水的鳗、乌贼有时也会
        double glowChance = switch (plan) {
            case JELLY -> 0.65;
            case EEL, SQUID -> 0.3;
            default -> earthlike ? 0.05 : type == PlanetType.EXOTIC || type == PlanetType.TOXIC ? 0.3 : 0.15;
        };
        boolean glowing = patternType != Pattern.NONE && r.chance(glowChance);
        if (glowing) {
            pattern = Colors.adjust(r.chance(0.5) ? palette.accent() : 0x40E8FF, r.range(-0.2, 0.2), 1.0, 2.0);
        }
        int eye = plan == BodyPlan.EEL ? (r.chance(0.5) ? 0xFFB020 : 0xE8E040) : r.pick(new Integer[] {0x1A1A1A, 0x101820, 0xE8D040, 0x40C8E8});
        boolean glowingEyes = plan == BodyPlan.EEL ? r.chance(0.5) : plan == BodyPlan.SQUID && r.chance(0.25);
        if (glowingEyes && Colors.luminance(eye) < 0.4) {
            eye = 0x80F0FF;
        }
        return new Skin(base, belly, pattern, eye, patternType, glowing, glowingEyes);
    }
}
