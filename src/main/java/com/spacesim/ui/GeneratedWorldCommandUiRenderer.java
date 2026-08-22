package com.spacesim.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Align;
import com.spacesim.economy.Money;
import com.spacesim.presentation.asset.Stage20MinimumPlayableTextureRenderer;
import com.spacesim.ui.GeneratedWorldUiSnapshot.FreightView;
import com.spacesim.ui.GeneratedWorldUiSnapshot.InfoSection;
import com.spacesim.ui.GeneratedWorldUiSnapshot.LocalObjectView;
import com.spacesim.ui.GeneratedWorldUiSnapshot.ObjectKind;
import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.StarSystemId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Commercial-style scalable renderer and hit-test surface for the generated-world command UI. */
@SuppressWarnings("doclint:missing")
public final class GeneratedWorldCommandUiRenderer {
    private static final Color[] FACTION_COLORS = {
            new Color(0.30f, 0.58f, 0.66f, 1f),
            new Color(0.61f, 0.25f, 0.31f, 1f),
            new Color(0.56f, 0.46f, 0.24f, 1f),
            new Color(0.37f, 0.52f, 0.40f, 1f),
            new Color(0.47f, 0.40f, 0.58f, 1f),
            new Color(0.63f, 0.42f, 0.29f, 1f)
    };

    private final OrthographicCamera camera = new OrthographicCamera();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final Stage20MinimumPlayableTextureRenderer sprites =
            new Stage20MinimumPlayableTextureRenderer();
    private final GlyphLayout glyph = new GlyphLayout();
    private final ArrayList<HitTarget> hitTargets = new ArrayList<>();

    private GeneratedWorldUiFonts fonts;
    private ResponsiveUiMetrics metrics;
    private int width;
    private int height;
    private Rect inspectorRect = Rect.empty();
    private Rect listRect = Rect.empty();
    private boolean disposed;

    /** Top-level production UI surfaces. */
    public enum Tab {
        /** Current physical star system. */ SYSTEM("СИСТЕМА"),
        /** Generated galaxy topology. */ GALAXY("ГАЛАКТИКА"),
        /** Persistent faction economy and control. */ FACTIONS("ФРАКЦИИ"),
        /** Physical freight fleets and orders. */ LOGISTICS("ЛОГИСТИКА");

        private final String label;

        Tab(String label) {
            this.label = label;
        }

        /** @return Russian navigation label */
        public String label() {
            return label;
        }
    }

    /** Selectable target families retained independently from mutable runtime objects. */
    public enum SelectionKind {
        /** Nothing selected. */ NONE,
        /** One local object. */ LOCAL_OBJECT,
        /** One global system. */ SYSTEM,
        /** One faction. */ FACTION,
        /** One physical freighter. */ FREIGHT
    }

    /** Stable current selection. */
    public record UiSelection(SelectionKind kind, String stableId) {
        /**
         * Validates a stable presentation selection.
         *
         * @param kind selected object family
         * @param stableId persistent presentation identity, or empty for {@link SelectionKind#NONE}
         */
        public UiSelection {
            Objects.requireNonNull(kind, "kind");
            stableId = stableId == null ? "" : stableId.strip();
            if ((kind == SelectionKind.NONE) != stableId.isEmpty()) {
                throw new IllegalArgumentException("Selection ID presence must match selection kind");
            }
        }

        /** @return empty selection */
        public static UiSelection none() {
            return new UiSelection(SelectionKind.NONE, "");
        }
    }

    /** Hit-test action kinds returned to the application controller. */
    public enum HitKind {
        /** Switch top-level tab. */ TAB,
        /** Select one local-system object. */ LOCAL_OBJECT,
        /** Select one global system. */ SYSTEM,
        /** Select one faction. */ FACTION,
        /** Select one freighter/order. */ FREIGHT,
        /** Make the selected global system the active inspected system. */ ACTIVATE_SYSTEM
    }

    /** One immutable hit result. */
    public record HitTarget(HitKind kind, String id, Tab tab, Rect bounds) {
        /**
         * Validates one rendered hit target.
         *
         * @param kind hit action family
         * @param id stable target identity, when applicable
         * @param tab target tab for a tab action, otherwise {@code null}
         * @param bounds rendered mouse bounds
         */
        public HitTarget {
            Objects.requireNonNull(kind, "kind");
            id = id == null ? "" : id.strip();
            Objects.requireNonNull(bounds, "bounds");
            if (kind == HitKind.TAB && tab == null) {
                throw new IllegalArgumentException("Tab hit requires a tab");
            }
        }
    }

    /** Allocates graphics resources and establishes initial resolution-dependent fonts. */
    public GeneratedWorldCommandUiRenderer() {
        resize(Math.max(1, Gdx.graphics.getWidth()), Math.max(1, Gdx.graphics.getHeight()));
    }

    /**
     * Rebuilds typography only when the resolved pixel sizes actually change.
     *
     * @param viewportWidth current logical viewport width
     * @param viewportHeight current logical viewport height
     */
    public void resize(int viewportWidth, int viewportHeight) {
        if (disposed || viewportWidth <= 0 || viewportHeight <= 0) {
            return;
        }
        ResponsiveUiMetrics resolved = ResponsiveUiMetrics.resolve(
                viewportWidth, viewportHeight, Math.max(1f, Gdx.graphics.getDensity()));
        boolean rebuildFonts = fonts == null
                || metrics.titleFontPixels() != resolved.titleFontPixels()
                || metrics.bodyFontPixels() != resolved.bodyFontPixels()
                || metrics.smallFontPixels() != resolved.smallFontPixels();
        if (rebuildFonts) {
            if (fonts != null) {
                fonts.dispose();
            }
            fonts = new GeneratedWorldUiFonts(resolved);
        }
        metrics = resolved;
        width = viewportWidth;
        height = viewportHeight;
        camera.setToOrtho(false, viewportWidth, viewportHeight);
        camera.update();
    }

    /**
     * Draws one complete interface frame and rebuilds its mouse hit map.
     *
     * @param snapshot current read-only generated-world projection
     * @param tab active top-level tab
     * @param selection current stable selection
     * @param detailScrollRows number of inspector rows skipped from the top
     * @param listScrollRows number of faction/logistics rows skipped from the top
     * @param paused whether all world clocks are paused
     * @param timeScale current common simulation time scale
     * @param status transient user-facing operation result
     */
    public void render(
            GeneratedWorldUiSnapshot snapshot,
            Tab tab,
            UiSelection selection,
            int detailScrollRows,
            int listScrollRows,
            boolean paused,
            double timeScale,
            String status) {
        if (disposed || snapshot == null || tab == null || selection == null) {
            return;
        }
        hitTargets.clear();
        listRect = Rect.empty();
        beginFrame();
        drawFrameChrome(snapshot, tab, paused, timeScale, status);
        switch (tab) {
            case SYSTEM -> drawSystem(snapshot, selection, detailScrollRows);
            case GALAXY -> drawGalaxy(snapshot, selection, detailScrollRows);
            case FACTIONS -> drawFactions(snapshot, selection, detailScrollRows, listScrollRows);
            case LOGISTICS -> drawLogistics(snapshot, selection, detailScrollRows, listScrollRows);
        }
    }

    /**
     * Returns the topmost current hit target at one bottom-left-origin UI coordinate.
     *
     * @param x horizontal UI coordinate
     * @param y vertical UI coordinate
     * @return topmost target, or {@code null} when the point is not interactive
     */
    public HitTarget hitTest(float x, float y) {
        for (int index = hitTargets.size() - 1; index >= 0; index--) {
            HitTarget target = hitTargets.get(index);
            if (target.bounds().contains(x, y)) {
                return target;
            }
        }
        return null;
    }

    /**
     * @param x horizontal UI coordinate
     * @param y vertical UI coordinate
     * @return whether a bottom-left-origin point lies over the inspector panel
     */
    public boolean isInspectorPoint(float x, float y) {
        return inspectorRect.contains(x, y);
    }

    /**
     * @param x horizontal UI coordinate
     * @param y vertical UI coordinate
     * @return whether a bottom-left-origin point lies over a scrollable list
     */
    public boolean isListPoint(float x, float y) {
        return listRect.contains(x, y);
    }

    private void beginFrame() {
        Gdx.gl.glClearColor(
                ImperialUiPalette.GRAPHITE.r,
                ImperialUiPalette.GRAPHITE.g,
                ImperialUiPalette.GRAPHITE.b,
                1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);
    }

    private void drawFrameChrome(
            GeneratedWorldUiSnapshot snapshot,
            Tab active,
            boolean paused,
            double timeScale,
            String status) {
        float topY = height - metrics.topBarHeight();
        float statusHeight = metrics.statusBarHeight();
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(ImperialUiPalette.MIDNIGHT);
        shapes.rect(0f, topY, width, metrics.topBarHeight());
        shapes.setColor(ImperialUiPalette.GUNMETAL);
        shapes.rect(0f, topY, width, Math.max(1f, metrics.scale() * 2f));
        shapes.setColor(ImperialUiPalette.MIDNIGHT);
        shapes.rect(0f, 0f, width, statusHeight);
        shapes.end();

        float titleWidth = metrics.compact(width) ? 185f * metrics.scale() : 260f * metrics.scale();
        float tabStart = titleWidth;
        float tabWidth = Math.min(190f * metrics.scale(), (width - tabStart - 14f) / Tab.values().length);
        for (int index = 0; index < Tab.values().length; index++) {
            Tab tab = Tab.values()[index];
            float x = tabStart + index * tabWidth;
            Rect bounds = new Rect(x, topY, tabWidth, metrics.topBarHeight());
            if (tab == active) {
                shapes.begin(ShapeRenderer.ShapeType.Filled);
                shapes.setColor(ImperialUiPalette.BURGUNDY);
                shapes.rect(x, topY, tabWidth, metrics.topBarHeight());
                shapes.setColor(ImperialUiPalette.BRASS);
                shapes.rect(x, topY, tabWidth, Math.max(2f, 3f * metrics.scale()));
                shapes.end();
            }
            hitTargets.add(new HitTarget(HitKind.TAB, "", tab, bounds));
        }

        batch.begin();
        fonts.title().setColor(ImperialUiPalette.IVORY);
        fonts.title().draw(batch, "STAR EMPIRES", metrics.outerMargin(), height - 22f * metrics.scale());
        fonts.small().setColor(ImperialUiPalette.BRASS);
        fonts.small().draw(batch, "КОМАНДНЫЙ КОНТУР", metrics.outerMargin(),
                height - 48f * metrics.scale());
        for (int index = 0; index < Tab.values().length; index++) {
            float x = tabStart + index * tabWidth;
            fonts.body().setColor(Tab.values()[index] == active
                    ? ImperialUiPalette.IVORY : ImperialUiPalette.MUTED_TEXT);
            fonts.body().draw(batch, Tab.values()[index].label(), x, topY + metrics.topBarHeight() * 0.58f,
                    tabWidth, Align.center, false);
        }
        fonts.small().setColor(ImperialUiPalette.MUTED_TEXT);
        String clock = "SEED " + snapshot.worldSeed()
                + "   TICK " + snapshot.worldTick()
                + "   " + (paused ? "ПАУЗА" : String.format(Locale.ROOT, "×%.0f", timeScale));
        fonts.small().draw(batch, clock, width - metrics.outerMargin() - 360f * metrics.scale(),
                height - 24f * metrics.scale(), 350f * metrics.scale(), Align.right, false);
        fonts.small().setColor(ImperialUiPalette.IVORY);
        fonts.small().draw(batch,
                status == null || status.isBlank()
                        ? "F1–F4 вкладки  •  ЛКМ выбрать  •  колесо прокрутить  •  SPACE пауза  •  F5/F9 сохранить/загрузить"
                        : status,
                metrics.outerMargin(), statusHeight * 0.68f,
                width - metrics.outerMargin() * 2f, Align.left, false);
        batch.end();
    }

    private void drawSystem(
            GeneratedWorldUiSnapshot snapshot,
            UiSelection selection,
            int detailScrollRows) {
        Layout layout = splitMapAndInspector();
        inspectorRect = layout.inspector();
        panel(layout.map(), ImperialUiPalette.MAP_SURFACE, ImperialUiPalette.GUNMETAL);
        panel(layout.inspector(), ImperialUiPalette.PANEL_SURFACE, ImperialUiPalette.GUNMETAL);
        drawGrid(layout.map());

        Map<String, Point> points = projectLocal(snapshot.localObjects(), inset(layout.map(), 28f * metrics.scale()));
        ArrayList<LocalObjectView> spriteObjects = new ArrayList<>();
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (LocalObjectView object : snapshot.localObjects()) {
            Point point = points.get(object.stableId());
            if (point == null) {
                continue;
            }
            boolean selected = selection.kind() == SelectionKind.LOCAL_OBJECT
                    && selection.stableId().equals(object.stableId());
            float marker = markerSize(object.kind());
            shapes.setColor(selected ? ImperialUiPalette.BRASS : factionColor(object.factionId()));
            shapes.circle(point.x(), point.y(), marker * 0.58f, 24);
            shapes.setColor(ImperialUiPalette.GRAPHITE);
            shapes.circle(point.x(), point.y(), marker * 0.47f, 24);
            if (object.sprite() == null) {
                drawMarkerShape(object.kind(), point, marker * 0.34f);
            } else {
                spriteObjects.add(object);
            }
            hitTargets.add(new HitTarget(
                    HitKind.LOCAL_OBJECT,
                    object.stableId(),
                    null,
                    centered(point, Math.max(metrics.hitRadius() * 2f, marker * 1.35f))));
        }
        shapes.end();

        batch.begin();
        for (LocalObjectView object : spriteObjects) {
            Point point = points.get(object.stableId());
            float marker = markerSize(object.kind());
            sprites.draw(batch, object.sprite(), point.x(), point.y(), marker * 1.22f, marker * 0.92f, 0f);
        }
        batch.end();

        drawMapLabels(snapshot.localObjects(), points, selection, layout.map());
        batch.begin();
        fonts.title().setColor(ImperialUiPalette.IVORY);
        fonts.title().draw(batch, snapshot.activeSystemName(), layout.map().x() + 18f * metrics.scale(),
                layout.map().top() - 16f * metrics.scale());
        fonts.small().setColor(ImperialUiPalette.MUTED_TEXT);
        fonts.small().draw(batch,
                snapshot.localObjects().size() + " объектов  •  позиции Stage 20 SI  •  разнесение совпадающих маркеров только визуальное",
                layout.map().x() + 18f * metrics.scale(), layout.map().top() - 46f * metrics.scale());
        batch.end();

        LocalObjectView selected = snapshot.localObjects().stream()
                .filter(value -> selection.kind() == SelectionKind.LOCAL_OBJECT
                        && value.stableId().equals(selection.stableId()))
                .findFirst().orElse(null);
        if (selected == null) {
            drawEmptyInspector(layout.inspector(), "ОБЪЕКТ НЕ ВЫБРАН",
                    "Выберите корабль, станцию, ресурс, навигационный якорь или особую локацию на карте.");
        } else {
            drawInspector(layout.inspector(), selected.name(), selected.subtitle(),
                    selected.factionName(), selected.sections(), detailScrollRows);
        }
    }

    private void drawGalaxy(
            GeneratedWorldUiSnapshot snapshot,
            UiSelection selection,
            int detailScrollRows) {
        Layout layout = splitMapAndInspector();
        inspectorRect = layout.inspector();
        panel(layout.map(), ImperialUiPalette.MAP_SURFACE, ImperialUiPalette.GUNMETAL);
        panel(layout.inspector(), ImperialUiPalette.PANEL_SURFACE, ImperialUiPalette.GUNMETAL);
        drawGrid(layout.map());
        Rect mapContent = inset(layout.map(), 42f * metrics.scale());
        Map<StarSystemId, Point> points = projectSystems(snapshot.galaxy(), mapContent);

        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (GalaxyStrategicMapSnapshot.EdgeView edge : snapshot.galaxy().edges()) {
            Point first = points.get(edge.first());
            Point second = points.get(edge.second());
            if (first != null && second != null) {
                shapes.setColor(edge.touchesActiveSystem() ? ImperialUiPalette.CYAN : ImperialUiPalette.GRID);
                shapes.line(first.x(), first.y(), second.x(), second.y());
            }
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (GalaxyStrategicMapSnapshot.SystemView system : snapshot.galaxy().systems()) {
            Point point = points.get(system.id());
            boolean selected = selection.kind() == SelectionKind.SYSTEM
                    && selection.stableId().equals(Long.toString(system.id().value()));
            shapes.setColor(selected ? ImperialUiPalette.BRASS
                    : system.active() ? ImperialUiPalette.CYAN
                    : factionColor(system.controllerFactionId()));
            shapes.circle(point.x(), point.y(), selected ? 9f * metrics.scale() : 6f * metrics.scale(), 24);
            shapes.setColor(ImperialUiPalette.GRAPHITE);
            shapes.circle(point.x(), point.y(), 3f * metrics.scale(), 16);
            hitTargets.add(new HitTarget(
                    HitKind.SYSTEM,
                    Long.toString(system.id().value()),
                    null,
                    centered(point, metrics.hitRadius() * 2f)));
        }
        shapes.end();

        batch.begin();
        fonts.title().setColor(ImperialUiPalette.IVORY);
        fonts.title().draw(batch, snapshot.galaxy().galaxyName(), layout.map().x() + 18f * metrics.scale(),
                layout.map().top() - 16f * metrics.scale());
        fonts.small().setColor(ImperialUiPalette.MUTED_TEXT);
        fonts.small().draw(batch, "Физическая jump-топология • цвет узла = контроль • cyan = активная система",
                layout.map().x() + 18f * metrics.scale(), layout.map().top() - 46f * metrics.scale());
        for (GalaxyStrategicMapSnapshot.SystemView system : snapshot.galaxy().systems()) {
            Point point = points.get(system.id());
            boolean important = system.active() || selection.stableId().equals(Long.toString(system.id().value()))
                    || snapshot.galaxy().systems().size() <= 30;
            if (important) {
                fonts.small().setColor(system.active() ? ImperialUiPalette.CYAN : ImperialUiPalette.IVORY);
                fonts.small().draw(batch, system.name(), point.x() + 8f * metrics.scale(),
                        point.y() + 5f * metrics.scale());
            }
        }
        batch.end();

        GalaxyStrategicMapSnapshot.SystemView selected = snapshot.galaxy().systems().stream()
                .filter(value -> selection.kind() == SelectionKind.SYSTEM
                        && selection.stableId().equals(Long.toString(value.id().value())))
                .findFirst().orElse(null);
        if (selected == null) {
            drawEmptyInspector(layout.inspector(), "СИСТЕМА НЕ ВЫБРАНА",
                    "Выберите звёздную систему, чтобы увидеть сектор, контролирующую фракцию и прямые переходы.");
        } else {
            List<InfoSection> sections = List.of(
                    InfoSection.of(
                            "Система",
                            "Название", selected.name(),
                            "System ID", Long.toString(selected.id().value()),
                            "Сектор", selected.sectorName(),
                            "Контроль", selected.controllerDisplayName(),
                            "Прямые переходы", Integer.toString(selected.neighborCount()),
                            "Координаты галактики", format(selected.galaxyX()) + ", " + format(selected.galaxyY())),
                    InfoSection.of(
                            "Статус",
                            "Активная симуляция", selected.active() ? "Да" : "Нет",
                            "Переход к просмотру", "Кнопка ниже не телепортирует флоты"));
            drawInspector(layout.inspector(), selected.name(), "Звёздная система",
                    selected.controllerDisplayName(), sections, detailScrollRows);
            float buttonHeight = 44f * metrics.scale();
            Rect button = new Rect(
                    layout.inspector().x() + 16f * metrics.scale(),
                    layout.inspector().y() + 16f * metrics.scale(),
                    layout.inspector().width() - 32f * metrics.scale(),
                    buttonHeight);
            button(button, selected.active() ? "СИСТЕМА УЖЕ АКТИВНА" : "ОТКРЫТЬ СИСТЕМУ",
                    !selected.active());
            if (!selected.active()) {
                hitTargets.add(new HitTarget(
                        HitKind.ACTIVATE_SYSTEM,
                        Long.toString(selected.id().value()),
                        null,
                        button));
            }
        }
    }

    private void drawFactions(
            GeneratedWorldUiSnapshot snapshot,
            UiSelection selection,
            int detailScrollRows,
            int listScrollRows) {
        Layout layout = splitListAndInspector();
        inspectorRect = layout.inspector();
        listRect = layout.map();
        panel(layout.map(), ImperialUiPalette.MAP_SURFACE, ImperialUiPalette.GUNMETAL);
        panel(layout.inspector(), ImperialUiPalette.PANEL_SURFACE, ImperialUiPalette.GUNMETAL);
        drawListHeader(layout.map(), "ФРАКЦИИ", snapshot.galaxy().factions().size() + " активных субъектов");
        float y = layout.map().top() - 76f * metrics.scale();
        float rowHeight = 72f * metrics.scale();
        int startIndex = Math.min(listScrollRows, snapshot.galaxy().factions().size());
        for (int index = startIndex; index < snapshot.galaxy().factions().size(); index++) {
            GalaxyStrategicMapSnapshot.FactionView faction = snapshot.galaxy().factions().get(index);
            if (y - rowHeight < layout.map().y() + 12f * metrics.scale()) {
                break;
            }
            Rect row = new Rect(
                    layout.map().x() + 12f * metrics.scale(), y - rowHeight,
                    layout.map().width() - 24f * metrics.scale(), rowHeight - 6f * metrics.scale());
            boolean selected = selection.kind() == SelectionKind.FACTION
                    && selection.stableId().equals(faction.factionId());
            listRow(row, selected, factionColor(faction.factionId()));
            batch.begin();
            fonts.body().setColor(ImperialUiPalette.IVORY);
            fonts.body().draw(batch, faction.displayName(), row.x() + 14f * metrics.scale(),
                    row.top() - 13f * metrics.scale());
            fonts.small().setColor(ImperialUiPalette.MUTED_TEXT);
            fonts.small().draw(batch,
                    faction.controlledSystems() + " систем  •  казна "
                            + String.format(Locale.ROOT, "%,.0f cr", Money.toCredits(faction.treasuryMilliCredits())),
                    row.x() + 14f * metrics.scale(), row.y() + 18f * metrics.scale());
            batch.end();
            hitTargets.add(new HitTarget(HitKind.FACTION, faction.factionId(), null, row));
            y -= rowHeight;
        }
        drawListScrollHint(layout.map(), startIndex, snapshot.galaxy().factions().size());
        GalaxyStrategicMapSnapshot.FactionView selected = snapshot.galaxy().factions().stream()
                .filter(value -> selection.kind() == SelectionKind.FACTION
                        && selection.stableId().equals(value.factionId()))
                .findFirst().orElse(null);
        if (selected == null) {
            drawEmptyInspector(layout.inspector(), "ФРАКЦИЯ НЕ ВЫБРАНА",
                    "Выберите фракцию, чтобы увидеть экономику, налоги, контроль, договоры и стратегические записи.");
        } else {
            long fleets = snapshot.freight().stream()
                    .filter(value -> value.factionId().equals(selected.factionId())).count();
            List<InfoSection> sections = List.of(
                    InfoSection.of(
                            "Идентификация",
                            "Название", selected.displayName(),
                            "Faction ID", selected.factionId(),
                            "Контролируемые системы", Integer.toString(selected.controlledSystems()),
                            "Физические транспорты", Long.toString(fleets)),
                    InfoSection.of(
                            "Экономика",
                            "Казна", String.format(Locale.ROOT, "%,.2f cr",
                                    Money.toCredits(selected.treasuryMilliCredits())),
                            "Налог станций", basisPoints(selected.stationTaxBasisPoints()),
                            "Транзитный тариф", basisPoints(selected.territorialTariffBasisPoints()),
                            "Таможенный тариф", basisPoints(selected.customsTariffBasisPoints())),
                    InfoSection.of(
                            "Стратегия и дипломатия",
                            "Территориальные претензии", Integer.toString(selected.activeClaims()),
                            "Стратегические цели", Integer.toString(selected.strategicGoals()),
                            "Договорные записи", Integer.toString(selected.treatyRecords()),
                            "Эмбарго", Integer.toString(selected.embargoRecords())));
            drawInspector(layout.inspector(), selected.displayName(), "Фракционный субъект",
                    selected.factionId(), sections, detailScrollRows);
        }
    }

    private void drawLogistics(
            GeneratedWorldUiSnapshot snapshot,
            UiSelection selection,
            int detailScrollRows,
            int listScrollRows) {
        Layout layout = splitListAndInspector();
        inspectorRect = layout.inspector();
        listRect = layout.map();
        panel(layout.map(), ImperialUiPalette.MAP_SURFACE, ImperialUiPalette.GUNMETAL);
        panel(layout.inspector(), ImperialUiPalette.PANEL_SURFACE, ImperialUiPalette.GUNMETAL);
        long moving = snapshot.freight().stream()
                .filter(value -> value.phase().contains("Следует") || value.phase().contains("Возвращается"))
                .count();
        drawListHeader(layout.map(), "ЛОГИСТИКА",
                snapshot.freight().size() + " транспортов  •  " + moving + " в пути");
        float y = layout.map().top() - 76f * metrics.scale();
        float rowHeight = 88f * metrics.scale();
        int startIndex = Math.min(listScrollRows, snapshot.freight().size());
        for (int index = startIndex; index < snapshot.freight().size(); index++) {
            FreightView freight = snapshot.freight().get(index);
            if (y - rowHeight < layout.map().y() + 12f * metrics.scale()) {
                break;
            }
            Rect row = new Rect(
                    layout.map().x() + 12f * metrics.scale(), y - rowHeight,
                    layout.map().width() - 24f * metrics.scale(), rowHeight - 6f * metrics.scale());
            boolean selected = selection.kind() == SelectionKind.FREIGHT
                    && selection.stableId().equals(Long.toString(freight.fleetId()));
            listRow(row, selected, factionColor(freight.factionId()));
            batch.begin();
            fonts.body().setColor(ImperialUiPalette.IVORY);
            fonts.body().draw(batch, freight.name(), row.x() + 14f * metrics.scale(),
                    row.top() - 12f * metrics.scale());
            fonts.small().setColor(ImperialUiPalette.CYAN);
            fonts.small().draw(batch, freight.phase(), row.x() + 14f * metrics.scale(),
                    row.top() - 38f * metrics.scale());
            fonts.small().setColor(ImperialUiPalette.MUTED_TEXT);
            String route = freight.destinationName().equals("—")
                    ? "Резервный транспорт"
                    : freight.commodityId() + "  •  " + freight.sourceName() + " → " + freight.destinationName();
            fonts.small().draw(batch, route, row.x() + 14f * metrics.scale(),
                    row.y() + 14f * metrics.scale(), row.width() - 28f * metrics.scale(), Align.left, false);
            batch.end();
            progressBar(new Rect(
                    row.right() - 126f * metrics.scale(), row.top() - 32f * metrics.scale(),
                    108f * metrics.scale(), 8f * metrics.scale()),
                    freight.cargoMassKg() / freight.cargoCapacityKg());
            hitTargets.add(new HitTarget(
                    HitKind.FREIGHT, Long.toString(freight.fleetId()), null, row));
            y -= rowHeight;
        }
        drawListScrollHint(layout.map(), startIndex, snapshot.freight().size());
        FreightView selected = snapshot.freight().stream()
                .filter(value -> selection.kind() == SelectionKind.FREIGHT
                        && selection.stableId().equals(Long.toString(value.fleetId())))
                .findFirst().orElse(null);
        if (selected == null) {
            drawEmptyInspector(layout.inspector(), "ТРАНСПОРТ НЕ ВЫБРАН",
                    "Выберите физический транспорт, чтобы увидеть владельца, корпус, фит, груз и полный маршрут.");
        } else {
            drawInspector(layout.inspector(), selected.name(), selected.phase(),
                    selected.factionName(), selected.sections(), detailScrollRows);
        }
    }

    private Layout splitMapAndInspector() {
        float gap = 12f * metrics.scale();
        float bottom = metrics.statusBarHeight() + metrics.outerMargin();
        float top = height - metrics.topBarHeight() - metrics.outerMargin();
        float panelHeight = Math.max(100f, top - bottom);
        float inspectorWidth = Math.min(metrics.inspectorWidth(), width * 0.42f);
        Rect inspector = new Rect(
                width - metrics.outerMargin() - inspectorWidth,
                bottom,
                inspectorWidth,
                panelHeight);
        Rect map = new Rect(
                metrics.outerMargin(),
                bottom,
                inspector.x() - gap - metrics.outerMargin(),
                panelHeight);
        return new Layout(map, inspector);
    }

    private Layout splitListAndInspector() {
        float gap = 12f * metrics.scale();
        float bottom = metrics.statusBarHeight() + metrics.outerMargin();
        float top = height - metrics.topBarHeight() - metrics.outerMargin();
        float panelHeight = Math.max(100f, top - bottom);
        float listWidth = Math.min(metrics.listWidth(), width * 0.48f);
        Rect list = new Rect(metrics.outerMargin(), bottom, listWidth, panelHeight);
        Rect inspector = new Rect(
                list.right() + gap,
                bottom,
                width - metrics.outerMargin() - list.right() - gap,
                panelHeight);
        return new Layout(list, inspector);
    }

    private void drawInspector(
            Rect rect,
            String title,
            String subtitle,
            String context,
            List<InfoSection> sections,
            int scrollRows) {
        float padding = 18f * metrics.scale();
        float x = rect.x() + padding;
        float widthAvailable = rect.width() - padding * 2f;
        float y = rect.top() - padding;
        batch.begin();
        fonts.title().setColor(ImperialUiPalette.IVORY);
        GlyphLayout titleLayout = fonts.title().draw(batch, title, x, y, widthAvailable, Align.left, true);
        y -= titleLayout.height + 10f * metrics.scale();
        fonts.body().setColor(ImperialUiPalette.CYAN);
        GlyphLayout subtitleLayout = fonts.body().draw(batch, subtitle, x, y, widthAvailable, Align.left, true);
        y -= subtitleLayout.height + 8f * metrics.scale();
        fonts.small().setColor(ImperialUiPalette.MUTED_TEXT);
        GlyphLayout contextLayout = fonts.small().draw(batch, context, x, y, widthAvailable, Align.left, true);
        y -= contextLayout.height + 18f * metrics.scale();
        batch.end();

        int rowIndex = 0;
        int hiddenBelow = 0;
        for (InfoSection section : sections) {
            if (rowIndex++ < scrollRows) {
                continue;
            }
            float sectionHeight = 30f * metrics.scale();
            if (y - sectionHeight < rect.y() + 34f * metrics.scale()) {
                hiddenBelow++;
                continue;
            }
            batch.begin();
            fonts.body().setColor(ImperialUiPalette.BRASS);
            fonts.body().draw(batch, section.title().toUpperCase(Locale.ROOT), x, y);
            batch.end();
            y -= sectionHeight;
            for (var line : section.lines()) {
                if (rowIndex++ < scrollRows) {
                    continue;
                }
                if (y - fonts.body().getLineHeight() * 1.8f < rect.y() + 34f * metrics.scale()) {
                    hiddenBelow++;
                    continue;
                }
                batch.begin();
                if (!line.label().isEmpty()) {
                    fonts.small().setColor(ImperialUiPalette.MUTED_TEXT);
                    fonts.small().draw(batch, line.label(), x, y);
                    y -= fonts.small().getLineHeight() + 2f * metrics.scale();
                }
                fonts.body().setColor(ImperialUiPalette.IVORY);
                GlyphLayout valueLayout = fonts.body().draw(
                        batch, line.value(), x, y, widthAvailable, Align.left, true);
                y -= valueLayout.height + 10f * metrics.scale();
                batch.end();
            }
            y -= 4f * metrics.scale();
        }
        if (scrollRows > 0 || hiddenBelow > 0) {
            batch.begin();
            fonts.small().setColor(ImperialUiPalette.AMBER);
            fonts.small().draw(batch,
                    "Колесо мыши: прокрутка сведений" + (scrollRows > 0 ? "  •  выше: " + scrollRows : ""),
                    x, rect.y() + 14f * metrics.scale(), widthAvailable, Align.left, false);
            batch.end();
        }
    }

    private void drawEmptyInspector(Rect rect, String title, String body) {
        float padding = 20f * metrics.scale();
        batch.begin();
        fonts.title().setColor(ImperialUiPalette.BRASS);
        fonts.title().draw(batch, title, rect.x() + padding, rect.top() - padding,
                rect.width() - padding * 2f, Align.left, true);
        fonts.body().setColor(ImperialUiPalette.MUTED_TEXT);
        fonts.body().draw(batch, body, rect.x() + padding,
                rect.top() - padding - 58f * metrics.scale(),
                rect.width() - padding * 2f, Align.left, true);
        batch.end();
    }

    private void drawListHeader(Rect rect, String title, String subtitle) {
        batch.begin();
        fonts.title().setColor(ImperialUiPalette.IVORY);
        fonts.title().draw(batch, title, rect.x() + 18f * metrics.scale(),
                rect.top() - 16f * metrics.scale());
        fonts.small().setColor(ImperialUiPalette.MUTED_TEXT);
        fonts.small().draw(batch, subtitle, rect.x() + 18f * metrics.scale(),
                rect.top() - 46f * metrics.scale());
        batch.end();
    }

    private void drawListScrollHint(Rect rect, int startIndex, int totalRows) {
        if (startIndex <= 0 && totalRows <= 1) {
            return;
        }
        batch.begin();
        fonts.small().setColor(ImperialUiPalette.AMBER);
        fonts.small().draw(batch,
                "Колесо: список  •  показано с " + (startIndex + 1) + " из " + totalRows,
                rect.x() + 16f * metrics.scale(), rect.y() + 10f * metrics.scale(),
                rect.width() - 32f * metrics.scale(), Align.left, false);
        batch.end();
    }

    private void panel(Rect rect, Color fill, Color border) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(fill);
        shapes.rect(rect.x(), rect.y(), rect.width(), rect.height());
        shapes.setColor(ImperialUiPalette.BURGUNDY);
        shapes.rect(rect.x(), rect.top() - 3f * metrics.scale(), rect.width(), 3f * metrics.scale());
        shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(border);
        shapes.rect(rect.x(), rect.y(), rect.width(), rect.height());
        shapes.end();
    }

    private void listRow(Rect rect, boolean selected, Color accent) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(selected
                ? new Color(ImperialUiPalette.BURGUNDY.r, ImperialUiPalette.BURGUNDY.g,
                ImperialUiPalette.BURGUNDY.b, 0.70f)
                : new Color(ImperialUiPalette.MIDNIGHT.r, ImperialUiPalette.MIDNIGHT.g,
                ImperialUiPalette.MIDNIGHT.b, 0.88f));
        shapes.rect(rect.x(), rect.y(), rect.width(), rect.height());
        shapes.setColor(selected ? ImperialUiPalette.BRASS : accent);
        shapes.rect(rect.x(), rect.y(), 4f * metrics.scale(), rect.height());
        shapes.end();
    }

    private void button(Rect rect, String label, boolean enabled) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(enabled ? ImperialUiPalette.BURGUNDY : ImperialUiPalette.GUNMETAL);
        shapes.rect(rect.x(), rect.y(), rect.width(), rect.height());
        shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(enabled ? ImperialUiPalette.BRASS : ImperialUiPalette.MUTED_TEXT);
        shapes.rect(rect.x(), rect.y(), rect.width(), rect.height());
        shapes.end();
        batch.begin();
        fonts.body().setColor(enabled ? ImperialUiPalette.IVORY : ImperialUiPalette.MUTED_TEXT);
        fonts.body().draw(batch, label, rect.x(), rect.y() + rect.height() * 0.63f,
                rect.width(), Align.center, false);
        batch.end();
    }

    private void progressBar(Rect rect, double progress) {
        float safe = (float) Math.max(0d, Math.min(1d, progress));
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(ImperialUiPalette.GUNMETAL);
        shapes.rect(rect.x(), rect.y(), rect.width(), rect.height());
        shapes.setColor(safe > 0.9f ? ImperialUiPalette.AMBER : ImperialUiPalette.CYAN);
        shapes.rect(rect.x(), rect.y(), rect.width() * safe, rect.height());
        shapes.end();
    }

    private void drawGrid(Rect map) {
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(ImperialUiPalette.GRID);
        for (int index = 1; index < 10; index++) {
            float x = map.x() + map.width() * index / 10f;
            float y = map.y() + map.height() * index / 10f;
            shapes.line(x, map.y(), x, map.top());
            shapes.line(map.x(), y, map.right(), y);
        }
        shapes.end();
    }

    private void drawMarkerShape(ObjectKind kind, Point point, float size) {
        switch (kind) {
            case JUMP_ANCHOR -> {
                shapes.setColor(ImperialUiPalette.CYAN);
                shapes.circle(point.x(), point.y(), size, 18);
                shapes.setColor(ImperialUiPalette.GRAPHITE);
                shapes.circle(point.x(), point.y(), size * 0.62f, 18);
            }
            case RESOURCE_ANCHOR, RESOURCE -> {
                shapes.setColor(ImperialUiPalette.AMBER);
                shapes.triangle(point.x(), point.y() + size,
                        point.x() - size, point.y() - size,
                        point.x() + size, point.y() - size);
            }
            case SPECIAL_LOCATION -> {
                shapes.setColor(ImperialUiPalette.RED);
                shapes.triangle(point.x(), point.y() + size,
                        point.x() - size, point.y(),
                        point.x(), point.y() - size);
                shapes.triangle(point.x(), point.y() + size,
                        point.x() + size, point.y(),
                        point.x(), point.y() - size);
            }
            default -> {
                shapes.setColor(ImperialUiPalette.IVORY);
                shapes.circle(point.x(), point.y(), size, 12);
            }
        }
    }

    private void drawMapLabels(
            List<LocalObjectView> objects,
            Map<String, Point> points,
            UiSelection selection,
            Rect map) {
        batch.begin();
        for (LocalObjectView object : objects) {
            boolean selected = selection.kind() == SelectionKind.LOCAL_OBJECT
                    && selection.stableId().equals(object.stableId());
            boolean important = selected || object.kind() == ObjectKind.STATION
                    || object.kind() == ObjectKind.FLEET || objects.size() <= 24;
            if (!important) {
                continue;
            }
            Point point = points.get(object.stableId());
            if (point == null || !map.contains(point.x(), point.y())) {
                continue;
            }
            fonts.small().setColor(selected ? ImperialUiPalette.BRASS : ImperialUiPalette.IVORY);
            fonts.small().draw(batch, object.name(), point.x() + markerSize(object.kind()) * 0.65f,
                    point.y() + fonts.small().getLineHeight() * 0.35f);
        }
        batch.end();
    }

    private Map<String, Point> projectLocal(List<LocalObjectView> objects, Rect rect) {
        HashMap<String, Point> result = new HashMap<>();
        if (objects.isEmpty()) {
            return result;
        }
        LocalPhysicalPosition reference = objects.get(0).position();
        HashMap<String, RawPoint> raw = new HashMap<>();
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (LocalObjectView object : objects) {
            var displacement = reference.displacementTo(object.position());
            raw.put(object.stableId(), new RawPoint(displacement.deltaXM(), displacement.deltaYM()));
            minX = Math.min(minX, displacement.deltaXM());
            maxX = Math.max(maxX, displacement.deltaXM());
            minY = Math.min(minY, displacement.deltaYM());
            maxY = Math.max(maxY, displacement.deltaYM());
        }
        double spanX = Math.max(1d, maxX - minX);
        double spanY = Math.max(1d, maxY - minY);
        HashMap<String, Integer> occupancy = new HashMap<>();
        for (LocalObjectView object : objects) {
            RawPoint value = raw.get(object.stableId());
            float x = rect.x() + (float) ((value.x() - minX) / spanX) * rect.width();
            float y = rect.y() + (float) ((value.y() - minY) / spanY) * rect.height();
            String bucket = Math.round(x / metrics.markerSize()) + ":" + Math.round(y / metrics.markerSize());
            int ordinal = occupancy.merge(bucket, 1, Integer::sum) - 1;
            if (ordinal > 0) {
                double angle = ordinal * 2.399963229728653d;
                float radius = metrics.markerSize() * (0.46f + 0.18f * ordinal);
                x += (float) Math.cos(angle) * radius;
                y += (float) Math.sin(angle) * radius;
            }
            result.put(object.stableId(), new Point(x, y));
        }
        return result;
    }

    private static Map<StarSystemId, Point> projectSystems(
            GalaxyStrategicMapSnapshot snapshot,
            Rect rect) {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (GalaxyStrategicMapSnapshot.SystemView system : snapshot.systems()) {
            minX = Math.min(minX, system.galaxyX());
            maxX = Math.max(maxX, system.galaxyX());
            minY = Math.min(minY, system.galaxyY());
            maxY = Math.max(maxY, system.galaxyY());
        }
        double spanX = Math.max(1d, maxX - minX);
        double spanY = Math.max(1d, maxY - minY);
        Map<StarSystemId, Point> result = new HashMap<>();
        for (GalaxyStrategicMapSnapshot.SystemView system : snapshot.systems()) {
            result.put(system.id(), new Point(
                    rect.x() + (float) ((system.galaxyX() - minX) / spanX) * rect.width(),
                    rect.y() + (float) ((system.galaxyY() - minY) / spanY) * rect.height()));
        }
        return result;
    }

    private float markerSize(ObjectKind kind) {
        return switch (kind) {
            case STATION -> metrics.markerSize() * 1.35f;
            case FLEET -> metrics.markerSize() * 1.10f;
            case EXTRACTION_OUTPOST -> metrics.markerSize() * 1.18f;
            default -> metrics.markerSize();
        };
    }

    private static Rect centered(Point point, float size) {
        return new Rect(point.x() - size * 0.5f, point.y() - size * 0.5f, size, size);
    }

    private static Rect inset(Rect rect, float amount) {
        return new Rect(
                rect.x() + amount,
                rect.y() + amount,
                Math.max(1f, rect.width() - amount * 2f),
                Math.max(1f, rect.height() - amount * 2f));
    }

    private static Color factionColor(String factionId) {
        if (factionId == null || factionId.isBlank()) {
            return ImperialUiPalette.GUNMETAL;
        }
        return FACTION_COLORS[Math.floorMod(factionId.hashCode(), FACTION_COLORS.length)];
    }

    private static String basisPoints(int value) {
        return String.format(Locale.ROOT, "%.2f%%", value / 100d);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%,.2f", value);
    }

    /** Releases all owned OpenGL/font resources exactly once. */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (fonts != null) {
            fonts.dispose();
        }
        sprites.dispose();
        batch.dispose();
        shapes.dispose();
    }

    /** Simple immutable UI rectangle in bottom-left-origin logical coordinates. */
    public record Rect(float x, float y, float width, float height) {
        /**
         * Validates finite non-negative rectangle geometry.
         *
         * @param x left edge
         * @param y bottom edge
         * @param width non-negative width
         * @param height non-negative height
         */
        public Rect {
            if (!Float.isFinite(x) || !Float.isFinite(y)
                    || !Float.isFinite(width) || !Float.isFinite(height)
                    || width < 0f || height < 0f) {
                throw new IllegalArgumentException("UI rectangle must be finite and non-negative");
            }
        }

        /** @return right edge */
        public float right() {
            return x + width;
        }

        /** @return top edge */
        public float top() {
            return y + height;
        }

        /**
         * @param pointX horizontal point coordinate
         * @param pointY vertical point coordinate
         * @return whether point lies inside inclusive bounds
         */
        public boolean contains(float pointX, float pointY) {
            return pointX >= x && pointX <= right() && pointY >= y && pointY <= top();
        }

        /** @return zero-area rectangle */
        public static Rect empty() {
            return new Rect(0f, 0f, 0f, 0f);
        }
    }

    private record Layout(Rect map, Rect inspector) {
    }

    private record Point(float x, float y) {
    }

    private record RawPoint(double x, double y) {
    }
}
