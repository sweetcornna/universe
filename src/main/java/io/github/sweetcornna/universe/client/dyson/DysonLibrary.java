package io.github.sweetcornna.universe.client.dyson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import io.github.sweetcornna.universe.core.dyson.DysonBlueprint;
import net.minecraft.client.Minecraft;

/**
 * 本地的戴森球蓝图库（ADR-0030 §四）：导出的蓝图串存成 {@code universe/dyson/<名字>.txt}，和存档无关，换世界也还在。
 * 文字本身就是蓝图（{@link DysonBlueprint#export}），直接贴到聊天里、网上也能用。
 *
 * <p>名字里只留字母、数字、下划线和连字符，别的换成下划线；重名的后面加序号。
 */
final class DysonLibrary {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SUFFIX = ".txt";
    /** 最多列多少条，免得目录里文件太多时界面卡住。 */
    private static final int MAX_ENTRIES = 200;
    private static final int MAX_NAME = 48;

    /** 库里的一条。 */
    record Entry(String name, Path path) {
    }

    private DysonLibrary() {
    }

    private static Path directory() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("universe").resolve("dyson");
    }

    /** 库里的蓝图，按名字排。读不了目录时是空的。 */
    static List<Entry> list() {
        Path directory = directory();
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<Entry> entries = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().endsWith(SUFFIX)).forEach(p -> {
                String file = p.getFileName().toString();
                entries.add(new Entry(file.substring(0, file.length() - SUFFIX.length()), p));
            });
        } catch (IOException e) {
            LOGGER.warn("读不了戴森球蓝图库 {}", directory, e);
            return List.of();
        }
        entries.sort(Comparator.comparing(entry -> entry.name.toLowerCase(Locale.ROOT)));
        return entries.size() > MAX_ENTRIES ? entries.subList(0, MAX_ENTRIES) : entries;
    }

    /** 存一条，返回实际用的名字；存不下返回 null。 */
    static String save(String name, String blueprint) {
        String base = sanitize(name);
        Path directory = directory();
        try {
            Files.createDirectories(directory);
            String unique = base;
            for (int i = 2; Files.exists(directory.resolve(unique + SUFFIX)); i++) {
                unique = base + "_" + i;
            }
            Files.writeString(directory.resolve(unique + SUFFIX), blueprint, StandardCharsets.UTF_8);
            return unique;
        } catch (IOException e) {
            LOGGER.warn("存不下戴森球蓝图 {}", base, e);
            return null;
        }
    }

    /** 读一条；读不了返回 null。 */
    static String read(Path path) {
        try {
            if (Files.size(path) > DysonBlueprint.MAX_TEXT) {
                return null;
            }
            return Files.readString(path, StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            LOGGER.warn("读不了戴森球蓝图 {}", path, e);
            return null;
        }
    }

    /** 删一条。 */
    static boolean delete(Path path) {
        try {
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            LOGGER.warn("删不掉戴森球蓝图 {}", path, e);
            return false;
        }
    }

    private static String sanitize(String name) {
        StringBuilder out = new StringBuilder();
        for (char c : name.strip().toCharArray()) {
            if (out.length() >= MAX_NAME) {
                break;
            }
            out.append(c == '_' || c == '-' || Character.isLetterOrDigit(c) ? c : '_');
        }
        while (!out.isEmpty() && out.charAt(out.length() - 1) == '_') {
            out.setLength(out.length() - 1);
        }
        return out.isEmpty() ? "layer" : out.toString();
    }
}
