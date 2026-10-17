package io.github.sweetcornna.universe.core.ecology;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
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
 */
public final class Fauna {
    /** 不在星球上时（例如在主世界用命令召唤）用的一套动物。 */
    public static final Fauna FALLBACK = generate(new Ecology(1, 1, 1, 0, 6, 0x5EED), PlanetType.HABITABLE, Palette.NEUTRAL);

    private final List<CreatureSpecies> species;

    private Fauna(List<CreatureSpecies> species) {
        this.species = List.copyOf(species);
    }

    public static Fauna of(Planet planet) {
        return generate(planet.ecology(), planet.type(), planet.palette());
    }

    public List<CreatureSpecies> species() {
        return this.species;
    }

    public boolean lifeless() {
        return this.species.isEmpty();
    }

    /** 按序号取物种，越界时取模（存档里的序号对不上时也有个物种可用）。 */
    public CreatureSpecies get(int index) {
        return this.species.get(Math.floorMod(index, this.species.size()));
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

    public static Fauna generate(Ecology ecology, PlanetType type, Palette palette) {
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
        return new Fauna(list);
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
        };
    }
}
