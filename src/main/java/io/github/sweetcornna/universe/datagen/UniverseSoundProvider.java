package io.github.sweetcornna.universe.datagen;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.registry.UniverseSounds;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.SoundDefinition;
import net.neoforged.neoforge.common.data.SoundDefinitionsProvider;

/**
 * {@code sounds.json}（ADR-0029）：每个声音列出它的变体文件和字幕。
 *
 * <p>同时对一遍 {@code src/main/resources/assets/universe/sounds} 下的文件：登记了的变体缺文件，
 * 或者有文件没被登记，都让数据生成失败（多半是 {@code tools/sounds} 的配方和注册的变体数没对上）。
 */
public final class UniverseSoundProvider extends SoundDefinitionsProvider {
    private final Path soundsDir;

    public UniverseSoundProvider(PackOutput output) {
        super(output, Universe.MOD_ID);
        // 输出在 src/generated/resources，音效文件在 src/main/resources
        Path src = output.getOutputFolder().getParent().getParent();
        this.soundsDir = src.resolve("main/resources/assets").resolve(Universe.MOD_ID).resolve("sounds");
    }

    @Override
    public void registerSounds() {
        Set<Path> expected = new HashSet<>();
        List<String> missing = new ArrayList<>();
        for (UniverseSounds.Spec spec : UniverseSounds.specs()) {
            SoundDefinition definition = definition().subtitle(spec.subtitle());
            for (int i = 1; i <= spec.variants(); i++) {
                String file = spec.path() + "_" + i;
                SoundDefinition.Sound sound = sound(Universe.id(file));
                if (spec.range() > UniverseSounds.DEFAULT_RANGE) {
                    sound.attenuationDistance(spec.range());
                }
                definition.with(sound);
                Path path = this.soundsDir.resolve(file + ".ogg").normalize();
                expected.add(path);
                if (!Files.isRegularFile(path)) {
                    missing.add(file);
                }
            }
            this.add(spec.holder(), definition);
        }
        List<String> orphans = new ArrayList<>();
        if (Files.isDirectory(this.soundsDir)) {
            try (Stream<Path> files = Files.walk(this.soundsDir)) {
                files.filter(p -> p.toString().endsWith(".ogg"))
                        .map(Path::normalize)
                        .filter(p -> !expected.contains(p))
                        .forEach(p -> orphans.add(this.soundsDir.relativize(p).toString().replace('\\', '/')));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        if (!missing.isEmpty() || !orphans.isEmpty()) {
            throw new IllegalStateException("音效文件和注册的不一致：缺 " + missing + "，多出 " + orphans
                    + "（用 tools/sounds/build.py 重新生成，或者改注册的变体数）");
        }
    }
}
