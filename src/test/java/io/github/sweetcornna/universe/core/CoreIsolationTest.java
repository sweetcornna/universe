package io.github.sweetcornna.universe.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** core 包必须保持纯 Java，见 docs/adr/0003-core-decoupling.md。 */
class CoreIsolationTest {
    private static final Path CORE_SOURCES = Path.of("src/main/java/io/github/sweetcornna/universe/core");
    private static final List<String> FORBIDDEN_PACKAGES = List.of("net.minecraft.", "com.mojang.", "net.neoforged.");

    @Test
    void coreDoesNotImportGameClasses() throws IOException {
        assertTrue(Files.isDirectory(CORE_SOURCES), "找不到 core 源码目录：" + CORE_SOURCES.toAbsolutePath());

        List<String> violations;
        try (Stream<Path> files = Files.walk(CORE_SOURCES)) {
            violations = files
                    .filter(file -> file.toString().endsWith(".java"))
                    .flatMap(CoreIsolationTest::forbiddenImports)
                    .toList();
        }

        assertTrue(violations.isEmpty(),
                "core 包不能依赖 Minecraft 或 NeoForge：\n" + String.join("\n", violations));
    }

    private static Stream<String> forbiddenImports(Path file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return lines.stream()
                .map(String::strip)
                .filter(line -> line.startsWith("import "))
                .filter(line -> FORBIDDEN_PACKAGES.stream().anyMatch(line::contains))
                .map(line -> CORE_SOURCES.relativize(file) + ": " + line);
    }
}
