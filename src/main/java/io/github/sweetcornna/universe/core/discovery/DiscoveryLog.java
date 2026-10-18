package io.github.sweetcornna.universe.core.discovery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 全服共享的发现日志（ADR-0012）：谁第一个发现了什么、改了什么名字。
 */
public final class DiscoveryLog {
    /** 名字最长几个字（按 Unicode 码点算，一个汉字算一个）。 */
    public static final int MAX_NAME = 24;

    private final Map<DiscoveryKey, Entry> entries = new LinkedHashMap<>();

    /**
     * 一条记录。
     *
     * @param discoverer     发现者；开局就在日志里的（母星系）为 null
     * @param discovererName 发现者当时的名字
     * @param day            第几天发现的（游戏日）
     * @param customName     改过的名字；没改过为 null
     */
    public record Entry(DiscoveryKey key, UUID discoverer, String discovererName, long day, String customName) {
        public Entry {
            discovererName = discovererName == null ? "" : discovererName;
        }

        /** 只有发现者能改名；开局就测绘好的不能改。 */
        public boolean renamableBy(UUID player) {
            return this.discoverer != null && this.discoverer.equals(player);
        }

        public Entry withName(String name) {
            return new Entry(this.key, this.discoverer, this.discovererName, this.day, name);
        }
    }

    public enum RenameResult {
        OK,
        /** 日志里还没有这一条。 */
        UNKNOWN,
        /** 不是发现者。 */
        NOT_DISCOVERER,
        /** 名字太长或带控制字符。 */
        INVALID_NAME
    }

    public Optional<Entry> get(DiscoveryKey key) {
        return Optional.ofNullable(this.entries.get(key));
    }

    public boolean contains(DiscoveryKey key) {
        return this.entries.containsKey(key);
    }

    /**
     * 记下一次发现。已经有人发现过时什么也不做。
     *
     * @return 新记下的那一条；已经有了时为空
     */
    public Optional<Entry> discover(DiscoveryKey key, UUID player, String playerName, long day) {
        if (this.entries.containsKey(key)) {
            return Optional.empty();
        }
        Entry entry = new Entry(key, player, playerName, day, null);
        this.entries.put(key, entry);
        return Optional.of(entry);
    }

    /** 改名；名字是空串（或只有空白）时恢复程序生成的名字。 */
    public RenameResult rename(DiscoveryKey key, UUID player, String name) {
        Entry entry = this.entries.get(key);
        if (entry == null) {
            return RenameResult.UNKNOWN;
        }
        if (!entry.renamableBy(player)) {
            return RenameResult.NOT_DISCOVERER;
        }
        Optional<String> clean = cleanName(name);
        if (clean.isEmpty()) {
            return RenameResult.INVALID_NAME;
        }
        this.entries.put(key, entry.withName(clean.get().isEmpty() ? null : clean.get()));
        return RenameResult.OK;
    }

    /**
     * 整理玩家输入的名字：去掉首尾空白、把连续空白并成一个。带控制字符或超过 {@link #MAX_NAME} 个字时不合法。
     *
     * @return 整理后的名字（可能是空串，表示恢复原名）；不合法时为空
     */
    public static Optional<String> cleanName(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        if (raw.codePoints().anyMatch(Character::isISOControl)) {
            return Optional.empty();
        }
        String name = raw.strip().replaceAll("\\s+", " ");
        if (name.codePointCount(0, name.length()) > MAX_NAME) {
            return Optional.empty();
        }
        return Optional.of(name);
    }

    /** 读档、同步时直接放进去（覆盖同一样东西的旧记录）。 */
    public void put(Entry entry) {
        this.entries.put(entry.key(), entry);
    }

    public void clear() {
        this.entries.clear();
    }

    public Collection<Entry> entries() {
        return Collections.unmodifiableCollection(this.entries.values());
    }

    /** 某个天体（或恒星系）下面已经发现的东西。 */
    public List<Entry> children(DiscoveryKey parent) {
        List<Entry> children = new ArrayList<>();
        for (Entry entry : this.entries.values()) {
            if (entry.key().parent().filter(parent::equals).isPresent()) {
                children.add(entry);
            }
        }
        return children;
    }

    public int count(DiscoveryKey.Kind kind) {
        int count = 0;
        for (DiscoveryKey key : this.entries.keySet()) {
            if (key.kind() == kind) {
                count++;
            }
        }
        return count;
    }

    /** 某个天体上某一类东西已经发现了几种。 */
    public int countOn(DiscoveryKey body, DiscoveryKey.Kind kind) {
        int count = 0;
        for (DiscoveryKey key : this.entries.keySet()) {
            if (key.kind() == kind && key.body() != null && body.body() != null && key.body().equals(body.body())) {
                count++;
            }
        }
        return count;
    }
}
