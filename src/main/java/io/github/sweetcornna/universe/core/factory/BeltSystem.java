package io.github.sweetcornna.universe.core.factory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * 一组传送带线路的结算（一般一个维度一份）。
 *
 * <p>每刻按"下游先算"的顺序推进：物品交给下一条线路时，那条线路这一刻已经走过了，交过去的物品不会多走一步，
 * 吞吐也不会因为先后顺序打折。环线没法完全排好，环上会有一处的物品偶尔多走一步，不影响间距。
 */
public final class BeltSystem {
    private final List<BeltLine> lines = new ArrayList<>();
    private BeltLine[] order = new BeltLine[0];
    private boolean dirty;

    public void add(BeltLine line) {
        this.lines.add(line);
        this.dirty = true;
    }

    /** 拿掉一条线路；接到它上面的去处一并断开。 */
    public void remove(BeltLine line) {
        if (this.lines.remove(line)) {
            for (BeltLine other : this.lines) {
                if (successor(other) == line) {
                    other.setOutput(null);
                }
            }
            this.dirty = true;
        }
    }

    /** 设置线路的去处（{@code null} 是断开）。 */
    public void connect(BeltLine line, @Nullable BeltOutput output) {
        line.setOutput(output);
        this.dirty = true;
    }

    public List<BeltLine> lines() {
        return Collections.unmodifiableList(this.lines);
    }

    /** 所有线路上和起点排队的物品总数。 */
    public int itemCount() {
        int n = 0;
        for (BeltLine line : this.lines) {
            n += line.count() + line.waitingCount();
        }
        return n;
    }

    public void tick() {
        if (this.dirty) {
            this.order = this.buildOrder();
            this.dirty = false;
        }
        for (BeltLine line : this.order) {
            line.tick();
        }
    }

    private static @Nullable BeltLine successor(BeltLine line) {
        return line.output() instanceof BeltOutput.LineEntry entry ? entry.target() : null;
    }

    /** 每条线路排在它的下游之后。每条线路最多一个下游，顺着链往下走就能排好。 */
    private BeltLine[] buildOrder() {
        Map<BeltLine, Boolean> placed = new IdentityHashMap<>();
        Map<BeltLine, Boolean> member = new IdentityHashMap<>();
        for (BeltLine line : this.lines) {
            member.put(line, Boolean.TRUE);
        }
        List<BeltLine> order = new ArrayList<>(this.lines.size());
        List<BeltLine> chain = new ArrayList<>();
        for (BeltLine start : this.lines) {
            chain.clear();
            BeltLine current = start;
            while (current != null && member.containsKey(current) && !placed.containsKey(current)) {
                placed.put(current, Boolean.TRUE);
                chain.add(current);
                current = successor(current);
            }
            for (int i = chain.size() - 1; i >= 0; i--) {
                order.add(chain.get(i));
            }
        }
        return order.toArray(new BeltLine[0]);
    }
}
