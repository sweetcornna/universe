package io.github.sweetcornna.universe.core.factory;

/**
 * 矿脉枯竭的提醒（ADR-0033 §八）：采矿机里选不提醒、一个矿点采完时、全部采完时（默认）。同一台机器
 * {@link #COOLDOWN} 刻里只提醒一次。
 */
public final class VeinWarnings {
    /** 5 分钟。 */
    public static final long COOLDOWN = 6000;

    public enum Mode {
        NONE("none"),
        NODE("node"),
        ALL("all");

        private final String key;

        Mode(String key) {
            this.key = key;
        }

        public String key() {
            return this.key;
        }

        public Mode next() {
            Mode[] all = values();
            return all[(this.ordinal() + 1) % all.length];
        }

        public static Mode byOrdinal(int ordinal) {
            Mode[] all = values();
            return ordinal >= 0 && ordinal < all.length ? all[ordinal] : DEFAULT;
        }

        public static Mode byKey(String key) {
            for (Mode mode : values()) {
                if (mode.key.equals(key)) {
                    return mode;
                }
            }
            return DEFAULT;
        }
    }

    public static final Mode DEFAULT = Mode.ALL;

    private VeinWarnings() {
    }

    /**
     * 这次采完要不要提醒。
     *
     * @param allDepleted 范围里一个矿点都不剩了（这时 nodeDepleted 也是真的）
     */
    public static boolean warn(Mode mode, boolean nodeDepleted, boolean allDepleted) {
        return switch (mode) {
            case NONE -> false;
            case NODE -> nodeDepleted || allDepleted;
            case ALL -> allDepleted;
        };
    }

    /**
     * 离上次提醒够不够久。
     *
     * @param last 上次提醒的游戏时间，没提醒过是负数；时间倒回去了（换了存档的时钟）也算够久
     */
    public static boolean cooledDown(long last, long now) {
        return last < 0 || now < last || now - last >= COOLDOWN;
    }
}
