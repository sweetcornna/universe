package io.github.sweetcornna.universe.datagen;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.galaxy.DayCycle;
import io.github.sweetcornna.universe.world.planet.PlanetDimensionTypes;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;

/**
 * 星球的时间线和维度类型。
 *
 * <p>从原版的主世界维度类型和 {@code minecraft:day} 时间线改出来，不手抄原版那张很长的关键帧表：
 * <ul>
 *   <li>昼夜长度不同的星球：所有关键帧和周期按比例拉伸；</li>
 *   <li>潮汐锁定的星球：在锁定的时刻对原版时间线取值，每条轨道只留一个关键帧，太阳就停在那里。</li>
 * </ul>
 * 原版文件从类路径上的 Minecraft 资源里读，升级版本后重跑数据生成就会跟着变。
 */
final class PlanetEnvironmentProvider implements DataProvider {
    private static final int VANILLA_DAY = 24000;
    private final PackOutput.PathProvider timelines;
    private final PackOutput.PathProvider dimensionTypes;

    PlanetEnvironmentProvider(PackOutput output) {
        this.timelines = output.createPathProvider(PackOutput.Target.DATA_PACK, "timeline");
        this.dimensionTypes = output.createPathProvider(PackOutput.Target.DATA_PACK, "dimension_type");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        JsonObject day = readVanilla("data/minecraft/timeline/day.json");
        JsonObject overworld = readVanilla("data/minecraft/dimension_type/overworld.json");
        List<CompletableFuture<?>> writes = new ArrayList<>();
        for (DayCycle cycle : DayCycle.values()) {
            List<String> timelineIds = new ArrayList<>();
            if (cycle == DayCycle.STANDARD) {
                timelineIds.add("minecraft:day");
                timelineIds.add("minecraft:moon");
            } else if (cycle.locked()) {
                Identifier id = Universe.id("locked_" + cycle.serializedName().substring("locked_".length()));
                writes.add(DataProvider.saveStable(cache, frozen(day, cycle.lockedTime()), this.timelines.json(id)));
                timelineIds.add(id.toString());
            } else {
                Identifier id = Universe.id("day_" + cycle.lengthTicks());
                writes.add(DataProvider.saveStable(cache, scaled(day, cycle.lengthTicks()), this.timelines.json(id)));
                timelineIds.add(id.toString());
                timelineIds.add("minecraft:moon");
            }
            writes.add(DataProvider.saveStable(cache, dimensionType(overworld, timelineIds),
                    this.dimensionTypes.json(PlanetDimensionTypes.forCycle(cycle).identifier())));
        }
        writes.add(DataProvider.saveStable(cache, spaceType(overworld), this.dimensionTypes.json(SpaceDimensions.TYPE.identifier())));
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    /**
     * 太空的维度类型：没有时间线（不分昼夜）、天和雾都是黑的、不出云，天空由太空渲染器画；
     * 有天空光照，飞船不会黑成一团。上下各 256 格，飞船在黄道面附近活动。
     */
    private static JsonObject spaceType(JsonObject overworld) {
        JsonObject type = overworld.deepCopy();
        type.add("timelines", new JsonArray());
        type.addProperty("min_y", -256);
        type.addProperty("height", 512);
        type.addProperty("logical_height", 512);
        JsonObject attributes = type.getAsJsonObject("attributes");
        attributes.remove("minecraft:gameplay/nether_portal_spawns_piglin");
        attributes.remove("minecraft:audio/ambient_sounds");
        attributes.addProperty("minecraft:visual/sky_color", "#000000");
        attributes.addProperty("minecraft:visual/fog_color", "#000000");
        attributes.addProperty("minecraft:visual/cloud_color", "#00ffffff");
        JsonObject bedRule = new JsonObject();
        bedRule.addProperty("can_set_spawn", "never");
        bedRule.addProperty("can_sleep", "never");
        JsonObject message = new JsonObject();
        message.addProperty("translate", "universe.bed.no_sleep_in_space");
        bedRule.add("error_message", message);
        attributes.add("minecraft:gameplay/bed_rule", bedRule);
        // 太空里放末地的音乐
        JsonObject music = new JsonObject();
        JsonObject track = new JsonObject();
        track.addProperty("sound", "minecraft:music.end");
        track.addProperty("min_delay", 6000);
        track.addProperty("max_delay", 24000);
        music.add("default", track);
        attributes.add("minecraft:audio/background_music", music);
        attributes.addProperty("neoforge:custom_skybox", Universe.id("space").toString());
        return type;
    }

    @Override
    public String getName() {
        return "Universe planet environments";
    }

    private static JsonObject readVanilla(String path) {
        try (InputStream in = PlanetEnvironmentProvider.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Vanilla resource not found on classpath: " + path);
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
    }

    /**
     * 星球的维度类型：主世界的副本，换上星球的时间线；星球上的床只能设重生点，不能睡觉跳过夜晚；
     * 天空换成本模组的渲染器。
     */
    private static JsonObject dimensionType(JsonObject overworld, List<String> timelineIds) {
        JsonObject type = overworld.deepCopy();
        JsonArray timelines = new JsonArray();
        timelineIds.forEach(timelines::add);
        type.add("timelines", timelines);
        JsonObject attributes = type.getAsJsonObject("attributes");
        attributes.remove("minecraft:gameplay/nether_portal_spawns_piglin");
        JsonObject bedRule = new JsonObject();
        bedRule.addProperty("can_set_spawn", "always");
        bedRule.addProperty("can_sleep", "never");
        JsonObject message = new JsonObject();
        message.addProperty("translate", "universe.bed.no_sleep_on_planet");
        bedRule.add("error_message", message);
        attributes.add("minecraft:gameplay/bed_rule", bedRule);
        // 天空由客户端的 PlanetSkyRenderer 画；天色、雾色这些按星球变化的值不在这里，见 PlanetEnvironment
        attributes.addProperty("neoforge:custom_skybox", Universe.id("planet").toString());
        return type;
    }

    /** 周期和所有关键帧按比例拉伸。 */
    private static JsonObject scaled(JsonObject day, int period) {
        double factor = (double) period / VANILLA_DAY;
        JsonObject timeline = day.deepCopy();
        timeline.addProperty("period_ticks", period);
        // 时间标记（日出、正午……）在同一个时钟上不能重名，已经由原版的 minecraft:day 定义了
        timeline.remove("time_markers");
        for (Map.Entry<String, JsonElement> track : timeline.getAsJsonObject("tracks").entrySet()) {
            for (JsonElement keyframe : track.getValue().getAsJsonObject().getAsJsonArray("keyframes")) {
                JsonObject object = keyframe.getAsJsonObject();
                object.addProperty("ticks", scale(object.get("ticks").getAsInt(), factor));
            }
        }
        return timeline;
    }

    private static int scale(int ticks, double factor) {
        return (int) Math.round(ticks * factor);
    }

    /**
     * 在 {@code tick} 时刻对每条轨道取值，得到只有一个关键帧、没有周期的时间线。
     * 数值和颜色在相邻关键帧之间线性插值（忽略缓动），其他类型取前一个关键帧的值。
     */
    private static JsonObject frozen(JsonObject day, int tick) {
        JsonObject timeline = new JsonObject();
        timeline.add("clock", day.get("clock"));
        JsonObject tracks = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : day.getAsJsonObject("tracks").entrySet()) {
            JsonObject source = entry.getValue().getAsJsonObject();
            JsonObject track = new JsonObject();
            if (source.has("modifier")) {
                track.add("modifier", source.get("modifier"));
            }
            JsonObject keyframe = new JsonObject();
            keyframe.addProperty("ticks", 0);
            keyframe.add("value", sample(source.getAsJsonArray("keyframes"), tick));
            JsonArray keyframes = new JsonArray();
            keyframes.add(keyframe);
            track.add("keyframes", keyframes);
            tracks.add(entry.getKey(), track);
        }
        timeline.add("tracks", tracks);
        return timeline;
    }

    private static JsonElement sample(JsonArray keyframes, int tick) {
        int count = keyframes.size();
        int previous = -1;
        for (int i = 0; i < count; i++) {
            if (ticks(keyframes, i) <= tick) {
                previous = i;
            }
        }
        double previousTick;
        if (previous < 0) {
            // 早于第一个关键帧：前一个是上一周期的最后一个
            previous = count - 1;
            previousTick = ticks(keyframes, previous) - VANILLA_DAY;
        } else {
            previousTick = ticks(keyframes, previous);
        }
        int next = (previous + 1) % count;
        double nextTick = ticks(keyframes, next) + (next <= previous ? VANILLA_DAY : 0);
        if (nextTick < previousTick) {
            nextTick += VANILLA_DAY;
        }
        double f = nextTick > previousTick ? (tick - previousTick) / (nextTick - previousTick) : 0;
        JsonElement a = keyframes.get(previous).getAsJsonObject().get("value");
        JsonElement b = keyframes.get(next).getAsJsonObject().get("value");
        if (a.isJsonPrimitive() && b.isJsonPrimitive()) {
            JsonPrimitive pa = a.getAsJsonPrimitive();
            JsonPrimitive pb = b.getAsJsonPrimitive();
            if (pa.isNumber() && pb.isNumber()) {
                return new JsonPrimitive((float) (pa.getAsDouble() + f * (pb.getAsDouble() - pa.getAsDouble())));
            }
            if (pa.isString() && pb.isString() && pa.getAsString().startsWith("#") && pb.getAsString().startsWith("#")) {
                return new JsonPrimitive(lerpColor(pa.getAsString(), pb.getAsString(), f));
            }
        }
        return a.deepCopy();
    }

    private static int ticks(JsonArray keyframes, int index) {
        return keyframes.get(index).getAsJsonObject().get("ticks").getAsInt();
    }

    /** "#rrggbb" 或 "#aarrggbb" 逐通道插值，保持原来的位数。 */
    private static String lerpColor(String a, String b, double f) {
        int digits = a.length() - 1;
        long ca = Long.parseLong(a.substring(1), 16);
        long cb = Long.parseLong(b.substring(1), 16);
        long result = 0;
        for (int shift = 0; shift < digits * 4; shift += 8) {
            long va = (ca >> shift) & 0xFF;
            long vb = (cb >> shift) & 0xFF;
            result |= Math.round(va + f * (vb - va)) << shift;
        }
        return String.format("#%0" + digits + "x", result);
    }
}
