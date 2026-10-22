package io.github.sweetcornna.universe.core.mission;

import java.util.Locale;

/**
 * 任务板上的一个任务（ADR-0017）。由恒星系、游戏日和第几格决定，同一天同一座空间站的任务对所有玩家都一样。
 *
 * @param system     发任务的空间站所在的恒星系，回这里交付
 * @param day        哪一天的任务
 * @param slot       任务板上的第几格；公会使者给的从 {@link MissionBoard#ENVOY_SLOT} 起
 * @param item       送货要的物品 ID；地标任务是 {@link LandmarkTarget#encode()}；别的种类为空串
 * @param count      要多少：件数、架数、颗数或种数
 * @param reward     信用点
 * @param bonus      额外的奖励
 * @param bonusCount 额外奖励的数量
 */
public record Mission(int system, long day, int slot, MissionKind kind, String item, int count, long reward, Bonus bonus, int bonusCount) {
    /** 额外奖励：有时给导航数据或一个技术模块。 */
    public enum Bonus {
        NONE,
        NAVIGATION_DATA,
        MODULE;

        public String key() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    /** 区分任务用：同一个人同一天不能做两次同一个任务。 */
    public String id() {
        return this.system + ":" + this.day + ":" + this.slot;
    }
}
