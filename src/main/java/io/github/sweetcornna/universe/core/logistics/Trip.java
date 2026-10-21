package io.github.sweetcornna.universe.core.logistics;

import java.util.UUID;

/**
 * 一趟运输：载具从自己的站（{@code home}）飞到另一座站（{@code other}），装卸后飞回来。时刻都是派出时算好的
 * 游戏刻，不用实体，不受区块加载影响。
 *
 * @param delivering 自己的站供货：派出时装好货，到了卸下；否则空着去、到了装货带回来
 * @param amount     预订的货量
 * @param cargo      现在载着多少（送货的去程、取货的回程）
 * @param warp       跨恒星系，用了翘曲器
 * @param points     载着的货的增产点数（ADR-0022）
 */
public record Trip(Vehicle vehicle, UUID home, UUID other, String item, int homeSlot, int otherSlot, int amount, int cargo,
        boolean delivering, long departAt, long arriveAt, long returnAt, boolean returning, boolean warp, long points) {

    Trip arrived(int cargo, long points) {
        return new Trip(this.vehicle, this.home, this.other, this.item, this.homeSlot, this.otherSlot, this.amount, cargo,
                this.delivering, this.departAt, this.arriveAt, this.returnAt, true, this.warp, points);
    }

    /** 某一时刻离起点多远（0 在自己的站，1 在对方的站），画载具用。 */
    public double progress(double now) {
        if (now <= this.departAt) {
            return 0;
        }
        if (now < this.arriveAt) {
            return (now - this.departAt) / Math.max(1, this.arriveAt - this.departAt);
        }
        if (now < this.returnAt) {
            return 1 - (now - this.arriveAt) / Math.max(1, this.returnAt - this.arriveAt);
        }
        return 0;
    }
}
