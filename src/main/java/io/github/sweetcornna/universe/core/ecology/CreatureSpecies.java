package io.github.sweetcornna.universe.core.ecology;

import java.util.Set;

import io.github.sweetcornna.universe.core.procgen.NameGenerator.Name;

/**
 * 一种动物：体型、部件、配色、性情和属性。模型和皮肤由 {@link CreatureAnatomy}、{@link CreatureSkin} 按这些参数生成。
 *
 * @param index       在星球动物列表里的序号，实体靠它找回物种
 * @param name        物种名
 * @param plan        体型模板
 * @param temperament 性情
 * @param size        整体缩放（模型按"一格左右高"设计，再乘这个数）
 * @param body        身体各部分的尺寸（模型像素）
 * @param skin        配色和花纹
 * @param health      生命值
 * @param speed       移动速度（原版属性值，牛是 0.2）
 * @param attack      攻击伤害，不会打架的物种为 0
 * @param groupMin    一群最少几只
 * @param groupMax    一群最多几只
 * @param biotopes    生活在哪些生境
 * @param abundance   相对多度
 * @param voice       叫声
 */
public record CreatureSpecies(
        int index,
        Name name,
        BodyPlan plan,
        Temperament temperament,
        double size,
        Body body,
        Skin skin,
        double health,
        double speed,
        double attack,
        int groupMin,
        int groupMax,
        Set<Biotope> biotopes,
        double abundance,
        Voice voice) {

    /** 头上的装饰。 */
    public enum Crest {
        NONE, EARS, ANTENNAE, FRILL
    }

    /** 背上的装饰。 */
    public enum Dorsal {
        NONE, SPIKES, FIN, PLATES
    }

    /** 花纹。 */
    public enum Pattern {
        NONE, STRIPES, SPOTS, DORSAL_BAND, SADDLE, GRADIENT
    }

    /** 叫声的类型，适配层换成原版的音效再按体型调音高。 */
    public enum Voice {
        LOW, GRUNT, BLEAT, CHIRP, CROAK, HISS, TRILL, SCREECH, HUM
    }

    /**
     * 身体尺寸，单位是模型像素（1/16 格）。
     *
     * @param length       身体长度（节蠕虫是每节的长度）
     * @param width        身体宽度
     * @param height       身体高度
     * @param legLength    腿长
     * @param legWidth     腿粗
     * @param legPairs     几对腿（节蠕虫是每节几对）
     * @param neckLength   脖子长度
     * @param headWidth    头宽
     * @param headHeight   头高
     * @param headDepth    头长
     * @param snoutLength  吻部长度
     * @param tailLength   尾巴总长
     * @param tailSegments 尾巴分几节
     * @param horns        角的数目
     * @param hornLength   角的长度
     * @param crest        头上的装饰
     * @param dorsal       背上的装饰
     * @param eyes         眼睛数目
     * @param wingSpan     单侧翼展（只有飞兽有）
     * @param segments     身体节数（只有节蠕虫多于 1）
     */
    public record Body(
            int length,
            int width,
            int height,
            int legLength,
            int legWidth,
            int legPairs,
            int neckLength,
            int headWidth,
            int headHeight,
            int headDepth,
            int snoutLength,
            int tailLength,
            int tailSegments,
            int horns,
            int hornLength,
            Crest crest,
            Dorsal dorsal,
            int eyes,
            int wingSpan,
            int segments) {
    }

    /**
     * 配色。
     *
     * @param base           主色
     * @param belly          腹部颜色
     * @param pattern        花纹颜色
     * @param eye            眼睛颜色
     * @param patternType    花纹
     * @param glowingPattern 花纹会发光（生物荧光）
     * @param glowingEyes    眼睛会发光
     */
    public record Skin(int base, int belly, int pattern, int eye, Pattern patternType, boolean glowingPattern,
            boolean glowingEyes) {
    }
}
