package io.github.sweetcornna.universe.core.galaxy;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 天体编号：第几个恒星系的第几颗行星，以及（卫星的话）第几颗卫星。
 *
 * <p>{@link #path()} 用作星球维度 ID 的路径，例如 {@code universe:s3/p2}、{@code universe:s3/p2/m0}，
 * 所以编号规则属于存档格式。
 *
 * @param moon 卫星序号；行星为 {@link #NONE}
 */
public record BodyId(int system, int planet, int moon) {
    public static final int NONE = -1;
    private static final Pattern PATH = Pattern.compile("s(\\d+)/p(\\d+)(?:/m(\\d+))?");

    public BodyId {
        if (system < 0 || planet < 0 || moon < NONE) {
            throw new IllegalArgumentException("invalid body id: " + system + ", " + planet + ", " + moon);
        }
    }

    public static BodyId planet(int system, int planet) {
        return new BodyId(system, planet, NONE);
    }

    public static BodyId moon(int system, int planet, int moon) {
        return new BodyId(system, planet, moon);
    }

    public boolean isMoon() {
        return this.moon != NONE;
    }

    /** 卫星所属的行星；行星返回自身。 */
    public BodyId parent() {
        return this.isMoon() ? planet(this.system, this.planet) : this;
    }

    public BodyId moon(int moon) {
        return moon(this.system, this.planet, moon);
    }

    public String path() {
        return this.isMoon()
                ? "s" + this.system + "/p" + this.planet + "/m" + this.moon
                : "s" + this.system + "/p" + this.planet;
    }

    public static Optional<BodyId> parse(String path) {
        Matcher matcher = PATH.matcher(path);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            int system = Integer.parseInt(matcher.group(1));
            int planet = Integer.parseInt(matcher.group(2));
            int moon = matcher.group(3) == null ? NONE : Integer.parseInt(matcher.group(3));
            return Optional.of(new BodyId(system, planet, moon));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    @Override
    public String toString() {
        return this.path();
    }
}
