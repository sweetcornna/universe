package io.github.sweetcornna.universe.core.procgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ColorsAndNamesTest {
    @Test
    void hsvGivesPrimaryColors() {
        assertEquals(0xFF0000, Colors.hsv(0, 1, 1));
        assertEquals(0x00FF00, Colors.hsv(1 / 3.0, 1, 1));
        assertEquals(0x0000FF, Colors.hsv(2 / 3.0, 1, 1));
        assertEquals(0xFFFFFF, Colors.hsv(0.4, 0, 1));
        assertEquals(0x000000, Colors.hsv(0.4, 1, 0));
        // 色相超出 0–1 时按圈取余
        assertEquals(Colors.hsv(0.25, 0.5, 0.5), Colors.hsv(1.25, 0.5, 0.5));
    }

    @Test
    void blackbodyGoesFromRedToBlue() {
        int red = Colors.blackbody(3000);
        int sun = Colors.blackbody(5778);
        int blue = Colors.blackbody(30000);
        assertTrue(Colors.red(red) > Colors.blue(red));
        assertTrue(Colors.blue(blue) > Colors.red(blue));
        assertTrue(Colors.red(sun) == 255 && Colors.green(sun) > 220 && Colors.blue(sun) > 200);
    }

    @Test
    void lerpAndMultiply() {
        assertEquals(0x808080, Colors.lerp(0x000000, 0xFFFFFF, 128 / 255.0));
        assertEquals(0x123456, Colors.multiply(0x123456, 0xFFFFFF));
        assertEquals(0x000000, Colors.multiply(0x123456, 0x000000));
    }

    @Test
    void namesAreDeterministicAndVaried() {
        NameGenerator.Name first = NameGenerator.generate(Rng.of(1), 2, 3);
        assertEquals(first, NameGenerator.generate(Rng.of(1), 2, 3));

        Set<String> names = new HashSet<>();
        Rng rng = Rng.of(2);
        for (int i = 0; i < 1000; i++) {
            NameGenerator.Name name = NameGenerator.generate(rng, 2, 3);
            assertTrue(Character.isUpperCase(name.en().charAt(0)), name.en());
            assertTrue(name.en().length() >= 2);
            assertTrue(name.zh().length() >= 2 && name.zh().length() <= 5, name.zh());
            names.add(name.en());
        }
        assertTrue(names.size() > 950, "名字重复太多：" + names.size());
    }
}
