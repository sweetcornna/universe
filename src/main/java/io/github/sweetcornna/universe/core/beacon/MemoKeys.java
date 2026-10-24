package io.github.sweetcornna.universe.core.beacon;

import java.util.Optional;

import io.github.sweetcornna.universe.core.discovery.DiscoveryKey;
import io.github.sweetcornna.universe.core.galaxy.BodyId;

/**
 * 星球和恒星系备忘的键（ADR-0033 §六）：{@code body:s3/p1}、{@code body:s3/p1/m0}、{@code system:3}。只有发现过的星球和恒星系
 * 能写，键对应到发现日志里的条目。
 */
public final class MemoKeys {
    private static final String BODY = "body:";
    private static final String SYSTEM = "system:";
    public static final int MAX_LENGTH = 32;

    private MemoKeys() {
    }

    public static String body(BodyId body) {
        return BODY + body.path();
    }

    public static String system(int system) {
        return SYSTEM + system;
    }

    /** 键对应的发现日志条目；格式不对时为空。 */
    public static Optional<DiscoveryKey> discovery(String key) {
        if (key == null || key.length() > MAX_LENGTH) {
            return Optional.empty();
        }
        if (key.startsWith(BODY)) {
            return BodyId.parse(key.substring(BODY.length())).map(DiscoveryKey::body);
        }
        if (key.startsWith(SYSTEM)) {
            try {
                int system = Integer.parseInt(key.substring(SYSTEM.length()));
                return system >= 0 ? Optional.of(DiscoveryKey.system(system)) : Optional.empty();
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
