package io.github.sweetcornna.universe.core.audio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 机器的环境声（ADR-0029 §三）：周围运转的机器按声音分组，每组只放一条循环声。
 *
 * <p>一组的位置是按距离加权的中心（近的占大头），音量随台数增加、越来越慢地逼近 1：
 * 一台贴着放约 0.63，四台约 0.98，一百台和四台差不多。一片车间听着是一片，不是一百条一样的声音叠在一起。
 */
public final class Soundscape {
    /** 声源：group 是分组的键（同一个声音、同一个音高），loudness 是这台机器单独放时的响度（一般是 1）。 */
    public record Source(String group, double x, double y, double z, double loudness) {
    }

    /** 一组的循环声：放在 (x, y, z)，音量 volume（0–1），离听者 distance 格。 */
    public record Voice(String group, double x, double y, double z, double volume, double distance) {
    }

    private Soundscape() {
    }

    /**
     * @param radius    多远以内的机器算数，到这里贡献降到 0
     * @param maxVoices 同时最多放几组，响的先放
     */
    public static List<Voice> mix(List<Source> sources, double lx, double ly, double lz, double radius, int maxVoices) {
        Map<String, double[]> groups = new LinkedHashMap<>();
        for (Source source : sources) {
            double dx = source.x() - lx;
            double dy = source.y() - ly;
            double dz = source.z() - lz;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double falloff = 1 - distance / radius;
            if (falloff <= 0) {
                continue;
            }
            double contribution = source.loudness() * falloff;
            double weight = contribution * falloff;
            // [Σ贡献, Σ权重, Σ权重·x, Σ权重·y, Σ权重·z]
            double[] acc = groups.computeIfAbsent(source.group(), k -> new double[5]);
            acc[0] += contribution;
            acc[1] += weight;
            acc[2] += weight * source.x();
            acc[3] += weight * source.y();
            acc[4] += weight * source.z();
        }
        List<Voice> voices = new ArrayList<>();
        for (Map.Entry<String, double[]> entry : groups.entrySet()) {
            double[] acc = entry.getValue();
            if (acc[1] <= 0) {
                continue;
            }
            double x = acc[2] / acc[1];
            double y = acc[3] / acc[1];
            double z = acc[4] / acc[1];
            double distance = Math.sqrt((x - lx) * (x - lx) + (y - ly) * (y - ly) + (z - lz) * (z - lz));
            voices.add(new Voice(entry.getKey(), x, y, z, volume(acc[0]), distance));
        }
        voices.sort(Comparator.comparingDouble(Voice::volume).reversed());
        return voices.size() > maxVoices ? List.copyOf(voices.subList(0, maxVoices)) : voices;
    }

    /** 几台机器的贡献加起来以后的音量：1 − e^(−总和)。 */
    public static double volume(double contribution) {
        return 1 - Math.exp(-Math.max(0, contribution));
    }
}
