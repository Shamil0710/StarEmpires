package com.spacesim.ui;

import com.spacesim.ui.GeneratedWorldCommandUiRenderer.SelectionKind;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.UiSelection;
import com.spacesim.ui.GeneratedWorldUiSnapshot.InfoSection;
import com.spacesim.ui.ProductionUiSnapshot.Row;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class ProductionUiWorkspaceTest {
    private static Row row(String id, String name, String category, long tick) {
        return new Row(new UiSelection(SelectionKind.SURFACE_ROW, id), name, category, "Доступно",
                List.of(InfoSection.of("Состояние", "Тепло", "42 kW")),
                "Измеренная мощность; kW = киловатт", null, 0, tick);
    }

    private static ProductionUiSnapshot snapshot(List<Row> rows) {
        return new ProductionUiSnapshot(20, "Империя", Map.of(Tab.SHIPS, rows, Tab.HISTORY, rows));
    }

    @Test
    void backRestoresSelectionQueriesAndBothScrollPositions() {
        var ui = new ProductionUiWorkspace();
        ui.navigate(Tab.SHIPS);
        ui.select(row("ship", "Корабль", "Флот", 1).selection());
        ui.setQuery("корабль");
        ui.cycleSort();
        ui.scrollList(8);
        ui.scrollDetail(12);
        var expected = ui.view();
        ui.navigate(Tab.CONTACTS);
        ui.beginSearch();
        ui.type('x');
        assertTrue(ui.goBack());
        assertEquals(Tab.SHIPS, ui.tab());
        assertEquals(expected, ui.view());
        assertFalse(ui.searching());
        assertTrue(ui.breadcrumb(snapshot(List.of(row("ship", "Корабль", "Флот", 1)))).endsWith("Корабль"));
    }

    @Test
    void repeatedNavigationIsBoundedAndSameSurfaceDoesNotAddHistory() {
        var ui = new ProductionUiWorkspace();
        for (int i = 0; i < 300; i++) ui.navigate(i % 2 == 0 ? Tab.SHIPS : Tab.CONTACTS);
        assertEquals(64, ui.historySize());
        ui.navigate(ui.tab());
        assertEquals(64, ui.historySize());
        for (int i = 0; i < 64; i++) assertTrue(ui.goBack());
        assertFalse(ui.canGoBack());
        assertFalse(ui.goBack());
    }

    @Test
    void searchIncludesInspectorValuesCaseInsensitivelyAndDoesNotEraseSelection() {
        var ui = new ProductionUiWorkspace();
        ui.navigate(Tab.SHIPS);
        var selected = row("ship", "Север", "Флот", 1);
        ui.select(selected.selection());
        ui.setQuery("42 KW");
        assertEquals(List.of(selected), ui.page(snapshot(List.of(selected)), 4).rows());
        ui.setQuery("несуществующее");
        assertEquals(0, ui.page(snapshot(List.of(selected)), 4).total());
        assertEquals(selected.selection(), ui.view().selection());
        ui.setQuery("");
        assertEquals(List.of(selected), ui.page(snapshot(List.of(selected)), 4).rows());
    }

    @Test
    void categoryAndQueryComposeAndAbsentCategoriesCanBeCleared() {
        var ui = new ProductionUiWorkspace();
        ui.navigate(Tab.SHIPS);
        var data = snapshot(List.of(row("a", "Альфа", "A", 1), row("b", "Бета", "B", 2)));
        ui.cycleCategory(data);
        assertEquals("A", ui.view().category());
        assertEquals(1, ui.page(data, 10).total());
        ui.cycleCategory(data);
        assertEquals("B", ui.view().category());
        ui.setQuery("Альфа");
        assertEquals(0, ui.page(data, 10).total());
        ui.cycleCategory(data);
        assertEquals("", ui.view().category());
        assertEquals(1, ui.page(data, 10).total());
        ui.cycleCategory(data);
        ui.cycleCategory(snapshot(List.of()));
        assertEquals("", ui.view().category());
    }

    @Test
    void sortedVirtualPagesAreDeterministicUnderInputPermutationAndDataShrink() {
        var rows = new ArrayList<Row>();
        for (int i = 0; i < 1000; i++) rows.add(row(String.format("%04d", i), "Одинаковое имя", "Флот", i));
        var ui = new ProductionUiWorkspace();
        ui.navigate(Tab.SHIPS);
        ui.scrollList(987);
        var page = ui.page(snapshot(rows), 5);
        assertEquals(5, page.rows().size());
        assertEquals(1000, page.total());
        Collections.reverse(rows);
        assertEquals(page, ui.page(snapshot(rows), 5));
        var shrunk = ui.page(snapshot(rows.subList(0, 2)), 5);
        assertEquals(0, shrunk.offset());
        assertEquals(2, shrunk.rows().size());
        ui.scrollList(Integer.MAX_VALUE);
        assertEquals(995, ui.page(snapshot(rows), 5).offset());
        ui.scrollList(Integer.MIN_VALUE);
        assertEquals(0, ui.view().listScroll());
    }

    @Test
    void keyboardSelectionKeepsChosenRowInsideViewportAndToleratesRemoval() {
        var rows = new ArrayList<Row>();
        for (int i = 0; i < 40; i++) rows.add(row("s" + i, String.format("Ship %02d", i), "Флот", i));
        var data = snapshot(rows);
        var ui = new ProductionUiWorkspace();
        ui.navigate(Tab.SHIPS);
        ui.moveSelection(data, 1, 5);
        assertEquals(rows.get(0).selection(), ui.view().selection());
        ui.moveSelection(data, 22, 5);
        assertEquals(rows.get(22).selection(), ui.view().selection());
        assertTrue(ui.page(data, 5).rows().stream().anyMatch(row -> row.selection().equals(ui.view().selection())));
        ui.moveSelection(data, -30, 5);
        assertEquals(rows.get(0).selection(), ui.view().selection());
        ui.select(new UiSelection(SelectionKind.SURFACE_ROW, "deleted"));
        ui.moveSelection(data, -1, 5);
        assertEquals(rows.get(39).selection(), ui.view().selection());
        var old = ui.view();
        ui.moveSelection(snapshot(List.of()), 1, 5);
        assertEquals(old, ui.view());
    }

    @Test
    void densityChangesDoNotDiscardInspectionInformationOrSelection() {
        var ui = new ProductionUiWorkspace();
        ui.navigate(Tab.SHIPS);
        var row = row("a", "Альфа", "Флот", 1);
        var data = snapshot(List.of(row));
        ui.select(row.selection());
        var selected = data.find(Tab.SHIPS, ui.view().selection()).orElseThrow();
        var sections = selected.explainedSections();
        for (int i = 0; i < 3; i++) {
            ui.cycleDensity();
            assertEquals(sections, data.find(Tab.SHIPS, ui.view().selection()).orElseThrow().explainedSections());
        }
        assertEquals(ProductionUiWorkspace.Density.STANDARD, ui.density());
        assertEquals(2, sections.size());
        assertEquals("Источник сведений", sections.get(1).title());
    }

    @Test
    void sortsHaveStableNameCategoryAndNewestModes() {
        var alpha = row("a", "Альфа", "Z", 2);
        var beta = row("b", "Бета", "A", 8);
        var data = snapshot(List.of(beta, alpha));
        var ui = new ProductionUiWorkspace();
        ui.navigate(Tab.SHIPS);
        assertEquals(alpha, ui.page(data, 5).rows().get(0));
        ui.cycleSort();
        assertEquals(beta, ui.page(data, 5).rows().get(0));
        ui.cycleSort();
        assertEquals(beta, ui.page(data, 5).rows().get(0));
        ui.cycleSort();
        assertEquals(alpha, ui.page(data, 5).rows().get(0));
    }

    @Test
    void historyDefaultsToNewestAndPreservesCommitOrderWithinOneTick() {
        var latest = row("personal-event:3", "Альфа", "Личный журнал", 8);
        var lastSameTick = row("personal-event:2", "Янтарь", "Личный журнал", 4);
        var firstSameTick = row("personal-event:1", "Бета", "Личный журнал", 4);
        var data = snapshot(List.of(latest, lastSameTick, firstSameTick));
        var ui = new ProductionUiWorkspace(); ui.navigate(Tab.HISTORY);
        assertEquals(ProductionUiWorkspace.Sort.RECENT, ui.view().sort());
        assertEquals(List.of(latest, lastSameTick, firstSameTick), ui.page(data, 5).rows());
        ui.cycleSort();
        assertEquals(ProductionUiWorkspace.Sort.NAME, ui.view().sort());
        assertEquals(List.of(latest, firstSameTick, lastSameTick), ui.page(data, 5).rows());
        ui.navigate(Tab.SHIPS); assertEquals(ProductionUiWorkspace.Sort.NAME, ui.view().sort());
        ui.goBack(); assertEquals(ProductionUiWorkspace.Sort.NAME, ui.view().sort());
    }

    @Test
    void textEditingAndResetAreBoundedPresentationOnlyOperations() {
        var ui = new ProductionUiWorkspace();
        ui.type('a');
        assertEquals("", ui.view().query());
        ui.navigate(Tab.HISTORY);
        ui.beginSearch();
        for (char c : "Test ship".toCharArray()) ui.type(c);
        ui.type('\b');
        ui.type('\n');
        assertEquals("Test shi", ui.view().query());
        ui.setQuery("x".repeat(1000));
        assertEquals(160, ui.view().query().length());
        ui.type('y');
        assertEquals(160, ui.view().query().length());
        ui.endSearch();
        ui.scrollDetail(Integer.MAX_VALUE);
        ui.scrollDetail(1);
        assertEquals(Integer.MAX_VALUE, ui.view().detailScroll());
        ui.reset();
        assertEquals(Tab.SYSTEM, ui.tab());
        assertEquals(UiSelection.none(), ui.view().selection());
        assertFalse(ui.canGoBack());
        assertEquals("", ui.view().query());
    }

    @Test
    void corruptSnapshotsAndInvalidViewportRequestsFailClosed() {
        var row = row("a", "Альфа", "Флот", 1);
        assertThrows(IllegalArgumentException.class, () -> snapshot(List.of(row, row)));
        assertThrows(IllegalArgumentException.class, () -> new ProductionUiSnapshot(-1, "Империя", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new Row(UiSelection.none(), "x", "x", "x", List.of(), "x", null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Row(row.selection(), "x", "x", "x", List.of(), "", null, 0, 0));
        var ui = new ProductionUiWorkspace();
        assertThrows(IllegalArgumentException.class, () -> ui.page(snapshot(List.of()), 0));
        assertThrows(IllegalArgumentException.class, () -> ui.moveSelection(snapshot(List.of()), 1, -1));
        assertEquals(List.of(), snapshot(List.of()).rows(Tab.CONTACTS));
        assertTrue(snapshot(List.of()).find(Tab.SHIPS, row.selection()).isEmpty());
    }
}
