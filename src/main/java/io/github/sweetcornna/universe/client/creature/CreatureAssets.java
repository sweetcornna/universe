package io.github.sweetcornna.universe.client.creature;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.NativeImage;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.ecology.CreatureAnatomy;
import io.github.sweetcornna.universe.core.ecology.CreatureSkin;
import io.github.sweetcornna.universe.core.ecology.CreatureSpecies;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * 每个物种的模型和贴图，第一次画到时生成。贴图是运行时画的 {@link DynamicTexture}，
 * 换星球后旧物种的贴图在缓存满了时释放。只在渲染线程调用。
 */
final class CreatureAssets {
    private static final int CAPACITY = 64;
    private static final Map<CreatureSpecies, Assets> CACHE = new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<CreatureSpecies, Assets> eldest) {
            if (this.size() > CAPACITY) {
                eldest.getValue().release();
                return true;
            }
            return false;
        }
    };
    private static int counter;

    /**
     * @param hitWidth 碰撞箱宽（格，未乘大小），影子用
     * @param glow     发光层贴图，没有发光部分时为 null
     */
    record Assets(AlienCreatureModel model, Identifier skin, @Nullable Identifier glow, float hitWidth) {
        void release() {
            Minecraft.getInstance().getTextureManager().release(this.skin);
            if (this.glow != null) {
                Minecraft.getInstance().getTextureManager().release(this.glow);
            }
        }
    }

    private CreatureAssets() {
    }

    static Assets get(CreatureSpecies species) {
        Assets assets = CACHE.get(species);
        if (assets == null) {
            assets = create(species);
            CACHE.put(species, assets);
        }
        return assets;
    }

    private static Assets create(CreatureSpecies species) {
        CreatureAnatomy anatomy = CreatureAnatomy.of(species);
        CreatureSkin skin = CreatureSkin.paint(species, anatomy);
        int id = counter++;
        Identifier skinId = upload("creature/" + id, skin.width(), skin.height(), skin.pixels());
        Identifier glowId = skin.hasGlow() ? upload("creature/" + id + "_glow", skin.width(), skin.height(), skin.glow()) : null;
        return new Assets(AlienCreatureModel.create(species, anatomy), skinId, glowId, anatomy.hitWidth());
    }

    private static Identifier upload(String path, int width, int height, int[] argb) {
        NativeImage image = new NativeImage(width, height, true);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setPixel(x, y, argb[y * width + x]);
            }
        }
        Identifier id = Universe.id(path);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));
        return id;
    }
}
