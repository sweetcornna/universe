package io.github.sweetcornna.universe.core.deepspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.deepspace.DeepSpaceSites.Kind;
import io.github.sweetcornna.universe.core.deepspace.DeepSpaceSites.Site;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.space.SpaceLayout;
import io.github.sweetcornna.universe.core.space.StationLayout;

class DeepSpaceSitesTest {
    private static final Galaxy GALAXY = GalaxyGenerator.generate(12345L, GalaxyGenerator.DEFAULT_SYSTEM_COUNT);

    private static List<Site> sites(StarSystem system) {
        double[] station = StationLayout.site(system).map(StationLayout.Site::center).orElse(null);
        return DeepSpaceSites.of(system.seed(), SpaceLayout.of(system), SpaceLayout.starRadius(system), station);
    }

    @Test
    void sitesAreStableAndClearOfBodiesAndEachOther() {
        int hulks = 0;
        int outposts = 0;
        int debris = 0;
        for (StarSystem system : GALAXY.systems()) {
            List<Site> sites = sites(system);
            assertEquals(sites, sites(system), "同一个星系的兴趣点总在同一个地方");
            List<SpaceLayout.Body> bodies = SpaceLayout.of(system);
            for (int i = 0; i < sites.size(); i++) {
                Site site = sites.get(i);
                assertEquals(i, site.index(), "序号就是在表里的位置");
                assertTrue(site.y() >= DeepSpaceSites.MIN_Y && site.y() <= DeepSpaceSites.MAX_Y, "放得下方块的高度：" + site.y());
                for (SpaceLayout.Body body : bodies) {
                    assertTrue(body.distanceTo(site.x(), site.y(), site.z()) >= body.radius() + DeepSpaceSites.BODY_CLEARANCE,
                            system.name() + " 的 " + site.kind() + " 压到了 " + body.planet().name());
                }
                for (int j = 0; j < i; j++) {
                    Site other = sites.get(j);
                    assertTrue(other.distanceTo(site.x(), site.y(), site.z()) >= DeepSpaceSites.SPACING, "两个兴趣点挨得太近");
                }
                switch (site.kind()) {
                    case HULK -> hulks++;
                    case OUTPOST -> outposts++;
                    case DEBRIS -> debris++;
                }
            }
        }
        int systems = GALAXY.systems().size();
        assertTrue(hulks >= systems * 0.95, "差不多每个星系都有残骸：" + hulks + "/" + systems);
        assertTrue(outposts > systems / 3 && outposts < systems, "前哨站有的星系有、有的没有：" + outposts);
        assertTrue(debris >= systems / 2, "碎片带：" + debris);
    }

    @Test
    void debrisFieldNearTheStation() {
        for (StarSystem system : GALAXY.systems()) {
            var station = StationLayout.site(system);
            if (station.isEmpty()) {
                continue;
            }
            double[] c = station.get().center();
            boolean near = sites(system).stream().anyMatch(site -> site.kind() == Kind.DEBRIS
                    && site.distanceTo(c[0], c[1], c[2]) < 1600);
            if (near) {
                return;
            }
        }
        throw new AssertionError("没有一个空间站外面有碎片带");
    }
}
