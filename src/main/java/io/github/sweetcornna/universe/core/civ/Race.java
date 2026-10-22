package io.github.sweetcornna.universe.core.civ;

import java.util.Optional;

import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;

/**
 * 外星种族（ADR-0027 §一）。名字、外形、台词都是占位，等剧情讨论后定稿；系统只认 id 和看重什么。
 *
 * @param id    存档和语言键里用的 id
 * @param value 看重什么：对话里选中这一项算对
 * @param color 界面里这个种族的颜色
 */
public enum Race {
    /** 商旅族（占位）：看重利。 */
    TRADER("trader", Value.PROFIT, 0xE8B04A),
    /** 学者族（占位）：看重知。 */
    SCHOLAR("scholar", Value.KNOWLEDGE, 0x7EC8F0),
    /** 戍卫族（占位）：看重勇。 */
    WARDEN("warden", Value.VALOR, 0xE06A50);

    private final String id;
    private final Value value;
    private final int color;

    Race(String id, Value value, int color) {
        this.id = id;
        this.value = value;
        this.color = color;
    }

    public String id() {
        return this.id;
    }

    public Value value() {
        return this.value;
    }

    public int color() {
        return this.color;
    }

    public static Optional<Race> byId(String id) {
        for (Race race : values()) {
            if (race.id.equals(id)) {
                return Optional.of(race);
            }
        }
        return Optional.empty();
    }

    /** 恒星系的主导种族：派生键 {@code civilization}，三个种族各占三分之一；母星系（序号 0）固定是商旅族。 */
    public static Race dominant(StarSystem system) {
        if (system.index() == 0) {
            return TRADER;
        }
        return Rng.of(Seeds.derive(system.seed(), "civilization")).pick(values());
    }
}
