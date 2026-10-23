package io.github.sweetcornna.universe.gametest;

import io.github.sweetcornna.universe.core.dyson.DysonBlueprint;
import io.github.sweetcornna.universe.core.dyson.DysonDesign;
import io.github.sweetcornna.universe.core.dyson.DysonGrid;
import io.github.sweetcornna.universe.core.dyson.DysonLayer;
import io.github.sweetcornna.universe.core.dyson.DysonSphere;
import io.github.sweetcornna.universe.core.dyson.SphereGeometry;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.dyson.Dyson;
import io.github.sweetcornna.universe.network.DysonLayerEditPayload;
import io.github.sweetcornna.universe.network.DysonResultPayload;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;

/**
 * 戴森球自由编辑、上色和蓝图（ADR-0030 §四）的游戏内测试：走的是编辑器那条路（{@link Dyson#editLayer}），
 * 服务端查一遍、存一遍。由 {@link UniverseGameTests} 登记。
 *
 * <p>每个测试自己建一层、用完删掉，免得留给别的测试和存档。
 */
final class DysonEditGameTests {
    /** 画一个四边形：赤道附近四个节点、四段框架、一片球壳。 */
    private static final double[][] QUAD = {{0, 0}, {0, 10}, {10, 10}, {10, 0}};

    private DysonEditGameTests() {
    }

    /** 自己摆的一层：四个节点连成一圈、铺上球壳，服务端收下，网格成了自定义的。 */
    static void freeDesignApplies(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        int layerId = addLayer(server);
        try {
            DysonSphere sphere = Dyson.sphere(server, GalaxyGenerator.HOME_SYSTEM);
            DysonLayer layer = sphere.layer(layerId);
            DysonDesign design = quad(layer);
            helper.assertTrue(design.validate() == null, "自己画的一层不合法：" + design.validate());
            DysonResultPayload result = Dyson.editLayer(server, GalaxyGenerator.HOME_SYSTEM,
                    DysonLayerEditPayload.redesign(layerId, DysonBlueprint.pack(design)));
            helper.assertTrue(result.ok(), "服务端没收下自己画的一层：" + result.key() + " " + result.detail());
            DysonGrid grid = sphere.layer(layerId).grid();
            helper.assertTrue(grid.custom(), "网格还是模板的");
            helper.assertTrue(grid.nodeCount() == 4, "节点数不对：" + grid.nodeCount());
            helper.assertTrue(grid.frameCount() == 4, "框架数不对：" + grid.frameCount());
            helper.assertTrue(grid.shellCount() == 1, "球壳数不对：" + grid.shellCount());
        } finally {
            remove(server, layerId);
        }
        helper.succeed();
    }

    /** 上色：涂过的颜色读得回来，涂成默认色也读得回来；下标过时的改动被拒。 */
    static void paintSticksAndStaleFails(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        int layerId = addLayer(server);
        try {
            DysonSphere sphere = Dyson.sphere(server, GalaxyGenerator.HOME_SYSTEM);
            apply(helper, server, layerId, quad(sphere.layer(layerId)));
            DysonLayer layer = sphere.layer(layerId);
            int revision = layer.revision();
            int color = 0x4C8CF0;
            DysonResultPayload painted = Dyson.editLayer(server, GalaxyGenerator.HOME_SYSTEM,
                    DysonLayerEditPayload.paint(layerId, revision, DysonLayer.Element.FRAME.ordinal(), new int[] {0, 2}, color));
            helper.assertTrue(painted.ok(), "上色没成：" + painted.key());
            helper.assertTrue(layer.frameColor(0) == (0xFF000000 | color), "第一段框架的颜色不对：" + Integer.toHexString(layer.frameColor(0)));
            helper.assertTrue(layer.frameColor(2) == (0xFF000000 | color), "第三段框架的颜色不对：" + Integer.toHexString(layer.frameColor(2)));
            helper.assertTrue(layer.frameColor(1) == 0, "没涂的框架被涂了");
            // 涂回默认色
            Dyson.editLayer(server, GalaxyGenerator.HOME_SYSTEM,
                    DysonLayerEditPayload.paint(layerId, layer.revision(), DysonLayer.Element.FRAME.ordinal(), new int[] {0}, 0));
            helper.assertTrue(layer.frameColor(0) == 0, "涂不回默认色：" + Integer.toHexString(layer.frameColor(0)));
            // 过时的编号：拒掉，别照着错的下标涂
            DysonResultPayload stale = Dyson.editLayer(server, GalaxyGenerator.HOME_SYSTEM,
                    DysonLayerEditPayload.paint(layerId, revision - 1, DysonLayer.Element.FRAME.ordinal(), new int[] {0}, color));
            helper.assertTrue(!stale.ok() && stale.key().equals(DysonSphere.EditResult.STALE.key()), "过时的上色没被拒：" + stale.key());
        } finally {
            remove(server, layerId);
        }
        helper.succeed();
    }

    /** 蓝图：一层导出成串，导入到另一层，网格和颜色都一样；坏的串给出原因。 */
    static void blueprintRoundTrips(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        int source = addLayer(server);
        int target = addLayer(server);
        try {
            DysonSphere sphere = Dyson.sphere(server, GalaxyGenerator.HOME_SYSTEM);
            apply(helper, server, source, quad(sphere.layer(source)));
            Dyson.editLayer(server, GalaxyGenerator.HOME_SYSTEM, DysonLayerEditPayload.paint(source, sphere.layer(source).revision(),
                    DysonLayer.Element.SHELL.ordinal(), new int[] {0}, 0x58D068));
            String text = DysonBlueprint.export(DysonDesign.of(sphere.layer(source)));
            DysonResultPayload imported = Dyson.editLayer(server, GalaxyGenerator.HOME_SYSTEM, DysonLayerEditPayload.importing(target, text));
            helper.assertTrue(imported.ok(), "蓝图导入失败：" + imported.key() + " " + imported.detail());
            DysonGrid from = sphere.layer(source).grid();
            DysonGrid to = sphere.layer(target).grid();
            helper.assertTrue(to.nodeCount() == from.nodeCount() && to.frameCount() == from.frameCount() && to.shellCount() == from.shellCount(),
                    "导入的网格大小不一样：" + to.nodeCount() + "/" + to.frameCount() + "/" + to.shellCount());
            helper.assertTrue(sphere.layer(target).shellColor(0) == sphere.layer(source).shellColor(0),
                    "导入的颜色不一样：" + Integer.toHexString(sphere.layer(target).shellColor(0)));
            // 坏串：不是蓝图
            DysonResultPayload bad = Dyson.editLayer(server, GalaxyGenerator.HOME_SYSTEM, DysonLayerEditPayload.importing(target, "nonsense"));
            helper.assertTrue(!bad.ok() && bad.key().equals(DysonBlueprint.Error.NOT_BLUEPRINT.key()), "坏串没给出原因：" + bad.key());
        } finally {
            remove(server, source);
            remove(server, target);
        }
        helper.succeed();
    }

    /** 建了结构的节点删不掉：整份设计少了它就被拒，拆过以后才能删。 */
    static void builtNodeCannotBeRemoved(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        int layerId = addLayer(server);
        try {
            DysonSphere sphere = Dyson.sphere(server, GalaxyGenerator.HOME_SYSTEM);
            apply(helper, server, layerId, quad(sphere.layer(layerId)));
            DysonLayer layer = sphere.layer(layerId);
            // 把第一个节点建起来
            layer.restore(built(layer.grid().nodeCount()), new int[layer.grid().frameCount()], new int[layer.grid().shellCount()]);
            helper.assertTrue(layer.started(DysonLayer.Element.NODE, 0), "第一个节点没算建起来");
            DysonDesign without = quad(layer);
            without.removeNodes(0);
            DysonResultPayload refused = Dyson.editLayer(server, GalaxyGenerator.HOME_SYSTEM,
                    DysonLayerEditPayload.redesign(layerId, DysonBlueprint.pack(without)));
            helper.assertTrue(!refused.ok() && refused.key().equals(DysonSphere.EditResult.BUILT.key()), "建了的节点被直接删了：" + refused.key());
            // 先拆，再删
            DysonResultPayload demolished = Dyson.editLayer(server, GalaxyGenerator.HOME_SYSTEM,
                    DysonLayerEditPayload.demolish(layerId, layer.revision(), DysonLayer.Element.NODE.ordinal(), new int[] {0}));
            helper.assertTrue(demolished.ok(), "拆不掉：" + demolished.key());
            helper.assertTrue(!sphere.layer(layerId).started(DysonLayer.Element.NODE, 0), "拆完还算建着");
            DysonDesign again = quad(sphere.layer(layerId));
            again.removeNodes(0);
            DysonResultPayload ok = Dyson.editLayer(server, GalaxyGenerator.HOME_SYSTEM,
                    DysonLayerEditPayload.redesign(layerId, DysonBlueprint.pack(again)));
            helper.assertTrue(ok.ok(), "拆过以后还删不掉：" + ok.key() + " " + ok.detail());
        } finally {
            remove(server, layerId);
        }
        helper.succeed();
    }

    // ---- 搭场子

    private static int addLayer(MinecraftServer server) {
        DysonSphere sphere = Dyson.sphere(server, GalaxyGenerator.HOME_SYSTEM);
        return sphere.addLayer(sphere.maxRadius() * 0.5, 0, 0, DysonGrid.Template.GEODESIC, 1);
    }

    private static void remove(MinecraftServer server, int layerId) {
        Dyson.existing(server, GalaxyGenerator.HOME_SYSTEM).ifPresent(sphere -> sphere.removeLayer(layerId));
    }

    private static void apply(GameTestHelper helper, MinecraftServer server, int layerId, DysonDesign design) {
        DysonResultPayload result = Dyson.editLayer(server, GalaxyGenerator.HOME_SYSTEM,
                DysonLayerEditPayload.redesign(layerId, DysonBlueprint.pack(design)));
        helper.assertTrue(result.ok(), "摆不上这一层：" + result.key() + " " + result.detail());
    }

    /** 四个节点连成一圈、铺一片球壳的设计。 */
    private static DysonDesign quad(DysonLayer layer) {
        DysonDesign design = new DysonDesign(layer.grid().template(), layer.grid().density());
        for (double[] latLon : QUAD) {
            design.rawNode(SphereGeometry.fromLatLon(latLon[0], latLon[1]), 0);
        }
        for (int i = 0; i < 4; i++) {
            design.rawFrame(i, (i + 1) % 4, 0);
        }
        design.rawShell(new int[] {0, 1, 2, 3}, 0);
        return design;
    }

    /** 第一个节点建满、别的不建。 */
    private static int[] built(int nodes) {
        int[] progress = new int[nodes];
        progress[0] = DysonLayer.NODE_SP;
        return progress;
    }
}
