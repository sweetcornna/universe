package io.github.sweetcornna.universe.core.mecha;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * 一个玩家的建造无人机（ADR-0023）。无人机停在玩家身上，有活时一架带一批相邻的任务飞出去，到了一个一个建好，
 * 建完飞回玩家身边。
 *
 * <p>这里只管无人机在哪、在干什么。哪些虚影能建、扣材料、放方块由适配层通过 {@link Planner} 做：
 * {@link Planner#next} 给一批任务并认领下来，{@link Planner#build} 在无人机到了以后放方块，
 * {@link Planner#release} 把没建的任务还回去。
 */
public final class DroneFleet {
    /** 无人机能飞到离玩家多远（水平距离）。 */
    public static final double REACH = 64;
    /** 一批任务里，其他任务离第一个最多几格（每个轴）。 */
    public static final int BATCH_RADIUS = 4;
    /** 到了一个任务以后停几刻再放方块。 */
    public static final int BUILD_TICKS = 4;
    /** 离目标多近算到了。 */
    static final double ARRIVE = 0.6;

    public enum State {
        /** 停在玩家身上。 */
        DOCKED,
        /** 飞向下一个任务。 */
        OUTBOUND,
        /** 在任务旁边建。 */
        BUILDING,
        /** 飞回玩家身边。 */
        RETURNING
    }

    /** 一个任务：一格虚影。site 和 index 由适配层解释。 */
    public record Task(int site, int index, int x, int y, int z) {
        double cx() {
            return this.x + 0.5;
        }

        double cy() {
            return this.y + 0.5;
        }

        double cz() {
            return this.z + 0.5;
        }
    }

    /** 适配层：选任务、放方块。 */
    public interface Planner {
        /** 给一批任务（最多 max 个，离玩家最近的那个和它旁边的），并认领下来；没有活时返回空表。 */
        List<Task> next(double x, double y, double z, int max);

        /** 无人机到了：放方块。返回假时这一格没建成（被占了、缺材料），任务已经还回去了。 */
        boolean build(Task task);

        /** 没建的任务还回去。 */
        void release(Task task);
    }

    /** 状态变了的无人机（适配层据此同步给客户端画）。 */
    public record Change(int drone, State state, double x, double y, double z, Task target) {
    }

    public static final class Drone {
        private State state = State.DOCKED;
        private double x;
        private double y;
        private double z;
        private final ArrayDeque<Task> tasks = new ArrayDeque<>();
        private int timer;

        public State state() {
            return this.state;
        }

        public double x() {
            return this.x;
        }

        public double y() {
            return this.y;
        }

        public double z() {
            return this.z;
        }

        public List<Task> tasks() {
            return List.copyOf(this.tasks);
        }
    }

    private final List<Drone> drones = new ArrayList<>();

    public List<Drone> drones() {
        return this.drones;
    }

    /** 不在玩家身上的无人机有几架。 */
    public int flying() {
        int n = 0;
        for (Drone drone : this.drones) {
            if (drone.state != State.DOCKED) {
                n++;
            }
        }
        return n;
    }

    /** 从一串按距离排好的候选任务里挑一批：第一个，加上离它 {@link #BATCH_RADIUS} 格以内的，最多 max 个。 */
    public static List<Task> batch(List<Task> candidates, int max) {
        if (candidates.isEmpty() || max <= 0) {
            return List.of();
        }
        Task first = candidates.getFirst();
        List<Task> batch = new ArrayList<>();
        batch.add(first);
        for (int i = 1; i < candidates.size() && batch.size() < max; i++) {
            Task task = candidates.get(i);
            if (Math.abs(task.x - first.x) <= BATCH_RADIUS && Math.abs(task.y - first.y) <= BATCH_RADIUS
                    && Math.abs(task.z - first.z) <= BATCH_RADIUS) {
                batch.add(task);
            }
        }
        // 从下往上建，同一层按离第一个的远近
        batch.sort((a, b) -> a.y != b.y ? Integer.compare(a.y, b.y)
                : Integer.compare(Math.abs(a.x - first.x) + Math.abs(a.z - first.z), Math.abs(b.x - first.x) + Math.abs(b.z - first.z)));
        return batch;
    }

    /**
     * 推进一刻。
     *
     * @param count 这名玩家有几架无人机
     * @param speed 速度（格/刻）
     * @param tasksPerTrip 一趟最多带几个任务
     * @param launch 能不能起飞（穿着胸甲、核心有电）
     * @return 这一刻状态变了的无人机
     */
    public List<Change> tick(double px, double py, double pz, int count, double speed, int tasksPerTrip, boolean launch, Planner planner) {
        while (this.drones.size() < count) {
            Drone drone = new Drone();
            drone.x = px;
            drone.y = py;
            drone.z = pz;
            this.drones.add(drone);
        }
        List<Change> changes = new ArrayList<>();
        for (int i = 0; i < this.drones.size(); i++) {
            Drone drone = this.drones.get(i);
            switch (drone.state) {
                case DOCKED -> {
                    drone.x = px;
                    drone.y = py;
                    drone.z = pz;
                    // 多出来的无人机（数量变少时）停着不再飞
                    if (launch && i < count) {
                        List<Task> batch = planner.next(px, py, pz, tasksPerTrip);
                        if (!batch.isEmpty()) {
                            drone.tasks.addAll(batch);
                            drone.state = State.OUTBOUND;
                            changes.add(new Change(i, State.OUTBOUND, drone.x, drone.y, drone.z, drone.tasks.peekFirst()));
                        }
                    }
                }
                case OUTBOUND -> {
                    Task task = drone.tasks.peekFirst();
                    if (task == null) {
                        this.goHome(i, drone, changes);
                    } else if (step(drone, task.cx(), task.cy(), task.cz(), speed)) {
                        drone.state = State.BUILDING;
                        drone.timer = BUILD_TICKS;
                        changes.add(new Change(i, State.BUILDING, drone.x, drone.y, drone.z, task));
                    }
                }
                case BUILDING -> {
                    if (--drone.timer > 0) {
                        break;
                    }
                    Task task = drone.tasks.pollFirst();
                    if (task != null) {
                        planner.build(task);
                    }
                    Task next = drone.tasks.peekFirst();
                    if (next == null) {
                        this.goHome(i, drone, changes);
                    } else {
                        drone.state = State.OUTBOUND;
                        changes.add(new Change(i, State.OUTBOUND, drone.x, drone.y, drone.z, next));
                    }
                }
                case RETURNING -> {
                    if (step(drone, px, py, pz, speed)) {
                        drone.state = State.DOCKED;
                        changes.add(new Change(i, State.DOCKED, px, py, pz, null));
                    }
                }
            }
        }
        return changes;
    }

    private void goHome(int index, Drone drone, List<Change> changes) {
        drone.state = State.RETURNING;
        changes.add(new Change(index, State.RETURNING, drone.x, drone.y, drone.z, null));
    }

    /** 叫回来：没建的任务还回去，飞着的都往回飞。 */
    public List<Change> recall(Planner planner) {
        List<Change> changes = new ArrayList<>();
        for (int i = 0; i < this.drones.size(); i++) {
            Drone drone = this.drones.get(i);
            while (!drone.tasks.isEmpty()) {
                planner.release(drone.tasks.pollFirst());
            }
            if (drone.state == State.OUTBOUND || drone.state == State.BUILDING) {
                this.goHome(i, drone, changes);
            }
        }
        return changes;
    }

    /** 玩家下线、换维度：任务还回去，无人机直接回到身上。 */
    public void dock(Planner planner) {
        for (Drone drone : this.drones) {
            while (!drone.tasks.isEmpty()) {
                planner.release(drone.tasks.pollFirst());
            }
            drone.state = State.DOCKED;
        }
    }

    /** 朝目标飞一步，到了返回真。 */
    static boolean step(Drone drone, double tx, double ty, double tz, double speed) {
        double dx = tx - drone.x;
        double dy = ty - drone.y;
        double dz = tz - drone.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance <= Math.max(ARRIVE, speed)) {
            drone.x = tx;
            drone.y = ty;
            drone.z = tz;
            return true;
        }
        double s = speed / distance;
        drone.x += dx * s;
        drone.y += dy * s;
        drone.z += dz * s;
        return false;
    }
}
