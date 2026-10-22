package io.github.sweetcornna.universe.core.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class GuideTest {
    @Test
    void chaptersAndPagesHaveUniqueIds() {
        Set<String> chapters = new HashSet<>();
        for (Guide.Chapter chapter : Guide.CHAPTERS) {
            assertTrue(chapters.add(chapter.id()), chapter.id());
            assertTrue(chapter.id().matches("[a-z][a-z_]*"), chapter.id());
            assertTrue(chapter.icon().matches("[a-z_]+:[a-z0-9_]+"), chapter.icon());
            Set<String> pages = new HashSet<>();
            for (Guide.Page page : chapter.pages()) {
                assertTrue(pages.add(page.id()), chapter.id() + "." + page.id());
                assertTrue(page.id().matches("[a-z][a-z_]*"), page.id());
                page.items().forEach(item -> assertTrue(item.matches("[a-z_]+:[a-z0-9_]+"), item));
                assertTrue(page.recipe().isEmpty() || page.recipe().matches("[a-z_]+:[a-z0-9_/]+"), page.recipe());
            }
        }
    }

    @Test
    void logAndEndingAreLastAndBuiltByTheClient() {
        int count = Guide.CHAPTERS.size();
        assertEquals(Guide.LOG, Guide.CHAPTERS.get(count - 2).id());
        assertEquals(Guide.ENDING, Guide.CHAPTERS.get(count - 1).id());
        assertTrue(Guide.chapter(Guide.LOG).pages().isEmpty());
        assertTrue(Guide.chapter(Guide.ENDING).pages().isEmpty());
        Guide.CHAPTERS.subList(0, count - 2).forEach(chapter -> assertFalse(chapter.pages().isEmpty(), chapter.id()));
    }

    @Test
    void fitsInTheChapterList() {
        // 界面左边一列 12 像素一行，从 22 像素起排，216 高的面板（底下留 6 像素）放得下 15 章
        assertTrue(Guide.CHAPTERS.size() <= 15);
    }
}
