package io.github.sweetcornna.universe.core.discovery;

import java.util.Locale;
import java.util.Optional;

import io.github.sweetcornna.universe.core.galaxy.BodyId;

/**
 * 发现日志里的一样东西（ADR-0012）：恒星系、天体，或者某个天体上的一种动物、植物、树、矿物。
 *
 * <p>{@link #id()} 的字符串形式存进存档、发给客户端，属于存档格式：{@code system:12}、{@code body:s12/p0/m1}、
 * {@code creature:s12/p0#3}、{@code mineral:s12/p0#universe:titanium_ore}、{@code microbe:s12/p0#hot_spring/0}。
 *
 * @param kind    种类
 * @param system  恒星系序号
 * @param body    天体；恒星系为 null
 * @param index   物种序号；不是物种时为 -1
 * @param mineral 矿石方块的 ID；微生物是菌的键在 # 后面的那段（{@code hot_spring/0}）；别的为空串
 */
public record DiscoveryKey(Kind kind, int system, BodyId body, int index, String mineral) {
    public enum Kind {
        SYSTEM,
        BODY,
        CREATURE,
        PLANT,
        TREE,
        MINERAL,
        /** 测序仪第一次测出的外星菌（ADR-0036 §五）。 */
        MICROBE;

        String prefix() {
            return this.name().toLowerCase(Locale.ROOT);
        }

        /** 是不是长在某个天体上的东西（物种、矿物）。 */
        public boolean onBody() {
            return this != SYSTEM && this != BODY;
        }
    }

    public DiscoveryKey {
        if ((kind == Kind.SYSTEM) != (body == null)) {
            throw new IllegalArgumentException(kind + " with body " + body);
        }
        if (body != null && body.system() != system) {
            throw new IllegalArgumentException(body + " is not in system " + system);
        }
        mineral = mineral == null ? "" : mineral;
    }

    public static DiscoveryKey system(int system) {
        return new DiscoveryKey(Kind.SYSTEM, system, null, -1, "");
    }

    public static DiscoveryKey body(BodyId body) {
        return new DiscoveryKey(Kind.BODY, body.system(), body, -1, "");
    }

    public static DiscoveryKey creature(BodyId body, int species) {
        return new DiscoveryKey(Kind.CREATURE, body.system(), body, species, "");
    }

    public static DiscoveryKey plant(BodyId body, int species) {
        return new DiscoveryKey(Kind.PLANT, body.system(), body, species, "");
    }

    public static DiscoveryKey tree(BodyId body, int species) {
        return new DiscoveryKey(Kind.TREE, body.system(), body, species, "");
    }

    public static DiscoveryKey mineral(BodyId body, String block) {
        return new DiscoveryKey(Kind.MINERAL, body.system(), body, -1, block);
    }

    /** 一种外星菌：{@code strainKey} 是 {@code MicrobeStrain#key()}，形如 {@code s3/p1#hot_spring/0}，前半截是天体。 */
    public static Optional<DiscoveryKey> microbe(String strainKey) {
        int hash = strainKey.indexOf('#');
        if (hash < 0 || hash == strainKey.length() - 1) {
            return Optional.empty();
        }
        return BodyId.parse(strainKey.substring(0, hash))
                .map(body -> new DiscoveryKey(Kind.MICROBE, body.system(), body, -1, strainKey.substring(hash + 1)));
    }

    /** 微生物条目对应的菌的键；别的种类为空串。 */
    public String strainKey() {
        return this.kind == Kind.MICROBE ? this.body.path() + "#" + this.mineral : "";
    }

    /** 物种和矿物所在的天体、天体所在的恒星系。恒星系自己没有上一级。 */
    public Optional<DiscoveryKey> parent() {
        return switch (this.kind) {
            case SYSTEM -> Optional.empty();
            case BODY -> Optional.of(system(this.system));
            default -> Optional.of(body(this.body));
        };
    }

    public String id() {
        String prefix = this.kind.prefix() + ":";
        return switch (this.kind) {
            case SYSTEM -> prefix + this.system;
            case BODY -> prefix + this.body.path();
            case MINERAL, MICROBE -> prefix + this.body.path() + "#" + this.mineral;
            default -> prefix + this.body.path() + "#" + this.index;
        };
    }

    public static Optional<DiscoveryKey> parse(String id) {
        int colon = id.indexOf(':');
        if (colon < 0) {
            return Optional.empty();
        }
        Kind kind;
        try {
            kind = Kind.valueOf(id.substring(0, colon).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        String rest = id.substring(colon + 1);
        try {
            if (kind == Kind.SYSTEM) {
                int system = Integer.parseInt(rest);
                return system < 0 ? Optional.empty() : Optional.of(system(system));
            }
            if (kind == Kind.BODY) {
                return BodyId.parse(rest).map(DiscoveryKey::body);
            }
            int hash = rest.indexOf('#');
            if (hash < 0) {
                return Optional.empty();
            }
            Optional<BodyId> body = BodyId.parse(rest.substring(0, hash));
            String tail = rest.substring(hash + 1);
            if (body.isEmpty() || tail.isEmpty()) {
                return Optional.empty();
            }
            if (kind == Kind.MINERAL) {
                return Optional.of(mineral(body.get(), tail));
            }
            if (kind == Kind.MICROBE) {
                return Optional.of(new DiscoveryKey(Kind.MICROBE, body.get().system(), body.get(), -1, tail));
            }
            int index = Integer.parseInt(tail);
            return index < 0 ? Optional.empty() : Optional.of(new DiscoveryKey(kind, body.get().system(), body.get(), index, ""));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
