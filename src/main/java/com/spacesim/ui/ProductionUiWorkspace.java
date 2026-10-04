package com.spacesim.ui;

import com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.UiSelection;
import com.spacesim.ui.ProductionUiSnapshot.Row;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Bounded presentation navigation with per-surface selection/query/scroll restoration. */
@SuppressWarnings("doclint:missing")
public final class ProductionUiWorkspace {
    private static final int HISTORY_LIMIT = 64;
    private static final int QUERY_LIMIT = 160;
    private final EnumMap<Tab, ViewState> views = new EnumMap<>(Tab.class);
    private final ArrayDeque<Tab> back = new ArrayDeque<>();
    private Tab tab = Tab.SYSTEM;
    private Density density = Density.STANDARD;
    private boolean searching;

    /** Row spacing only; inspector information is never discarded. */
    public enum Density {
        /** Smaller row spacing. */ COMPACT,
        /** Default row spacing. */ STANDARD,
        /** Larger row spacing. */ RELAXED
    }
    /** Stable visible ordering with internal identity as a tie breaker. */
    public enum Sort {
        /** Display-name ordering. */ NAME,
        /** Category followed by name. */ CATEGORY,
        /** Most recent event first. */ RECENT
    }

    /**
     * Independent presentation preferences for one surface.
     *
     * @param selection retained inspector selection
     * @param query bounded search text
     * @param category exact category filter, or empty for all
     * @param sort stable ordering mode
     * @param listScroll requested first list row
     * @param detailScroll inspector offset in body-font rows
     */
    public record ViewState(UiSelection selection, String query, String category, Sort sort,
            int listScroll, int detailScroll) {
    /**
     * Validates bounded navigation values.
     *
     * @param selection retained inspector selection
     * @param query bounded search text
     * @param category exact category filter, or empty for all
     * @param sort stable ordering mode
     * @param listScroll requested first list row
     * @param detailScroll inspector offset in body-font rows
     */
        public ViewState {
            Objects.requireNonNull(selection);
            query = Objects.requireNonNull(query);
            category = Objects.requireNonNull(category);
            Objects.requireNonNull(sort);
            if (query.length() > QUERY_LIMIT || listScroll < 0 || detailScroll < 0) {
                throw new IllegalArgumentException("invalid view state");
            }
        }
        static ViewState initial() {
            return new ViewState(UiSelection.none(), "", "", Sort.NAME, 0, 0);
        }
    }

    /**
     * A bounded visible window over a read-only collection.
     *
     * @param rows visible virtual page only
     * @param total filtered row count
     * @param offset clamped page start
     * @param categories all available exact category filters
     */
    public record Page(List<Row> rows, int total, int offset, List<String> categories) {
    /**
     * Validates and freezes one virtual page.
     *
     * @param rows visible virtual page only
     * @param total filtered row count
     * @param offset clamped page start
     * @param categories all available exact category filters
     */
        public Page {
            rows = List.copyOf(rows);
            categories = List.copyOf(categories);
            if (total < 0 || offset < 0 || offset > total || offset + rows.size() > total) {
                throw new IllegalArgumentException("invalid virtual page");
            }
        }
    }

    /**
     * Reads the active surface.
     *
     * @return active surface
     */
    public Tab tab() { return tab; }
    /**
     * Reads retained preferences for the active surface.
     *
     * @return current presentation state
     */
    public ViewState view() { return views.getOrDefault(tab, ViewState.initial()); }
    /**
     * Reads current row spacing.
     *
     * @return density preset
     */
    public Density density() { return density; }
    /**
     * Reads text-editing focus.
     *
     * @return whether search owns keyboard input
     */
    public boolean searching() { return searching; }
    /**
     * Checks the bounded navigation stack.
     *
     * @return whether a prior surface exists
     */
    public boolean canGoBack() { return !back.isEmpty(); }
    /**
     * Reads navigation retention size.
     *
     * @return retained entries, at most 64
     */
    public int historySize() { return back.size(); }

    /**
     * Visits a surface while retaining each surface's independent preferences.
     *
     * @param target destination surface
     */
    public void navigate(Tab target) {
        Objects.requireNonNull(target);
        if (target == tab) return;
        if (back.size() == HISTORY_LIMIT) back.removeFirst();
        back.addLast(tab);
        tab = target;
        searching = false;
    }

    /**
     * Restores the previous surface without clearing its selection.
     *
     * @return whether navigation occurred
     */
    public boolean goBack() {
        if (back.isEmpty()) return false;
        tab = back.removeLast();
        searching = false;
        return true;
    }

    /**
     * Clears transient navigation after replacing the campaign.
     */
    public void reset() {
        views.clear();
        back.clear();
        tab = Tab.SYSTEM;
        searching = false;
    }

    /**
     * Retains selection and restarts the inspector at its top.
     *
     * @param selection stable selected identity
     */
    public void select(UiSelection selection) {
        ViewState old = view();
        put(new ViewState(selection, old.query(), old.category(), old.sort(), old.listScroll(), 0));
    }

    /**
     * Changes bounded search text without clearing selection.
     *
     * @param query desired search text
     */
    public void setQuery(String query) {
        ViewState old = view();
        String safe = Objects.requireNonNull(query).strip();
        if (safe.length() > QUERY_LIMIT) safe = safe.substring(0, QUERY_LIMIT);
        put(new ViewState(old.selection(), safe, old.category(), old.sort(), 0, old.detailScroll()));
    }

    /**
     * Gives text editing exclusive keyboard focus.
     */
    public void beginSearch() { searching = true; }
    /**
     * Returns keyboard focus to navigation.
     */
    public void endSearch() { searching = false; }
    /**
     * Edits a bounded search value; ignores control characters except backspace.
     *
     * @param character typed character
     */
    public void type(char character) {
        if (!searching) return;
        String query = view().query();
        if (character == '\b') {
            if (!query.isEmpty()) setQuery(query.substring(0, query.offsetByCodePoints(query.length(), -1)));
        } else if (!Character.isISOControl(character) && query.length() < QUERY_LIMIT) {
            // Preserve spaces while typing; normalization belongs to query matching.
            ViewState old = view();
            put(new ViewState(old.selection(), query + character, old.category(), old.sort(), 0, old.detailScroll()));
        }
    }

    /**
     * Cycles ordering and resets the list window.
     */
    public void cycleSort() {
        ViewState old = view();
        put(new ViewState(old.selection(), old.query(), old.category(),
                Sort.values()[(old.sort().ordinal() + 1) % Sort.values().length], 0, old.detailScroll()));
    }

    /**
     * Cycles visible categories followed by the unfiltered state.
     *
     * @param snapshot current visible data
     */
    public void cycleCategory(ProductionUiSnapshot snapshot) {
        List<String> categories = categories(snapshot.rows(tab));
        ViewState old = view();
        int index = categories.indexOf(old.category());
        String next = index + 1 >= categories.size() ? "" : categories.get(index + 1);
        put(new ViewState(old.selection(), old.query(), next, old.sort(), 0, old.detailScroll()));
    }

    /**
     * Changes spacing without removing any inspector data.
     */
    public void cycleDensity() {
        density = Density.values()[(density.ordinal() + 1) % Density.values().length];
    }

    /**
     * Changes list offset with overflow-safe bounds.
     *
     * @param delta signed row displacement
     */
    public void scrollList(int delta) {
        ViewState old = view();
        put(new ViewState(old.selection(), old.query(), old.category(), old.sort(),
                (int) Math.max(0, Math.min(Integer.MAX_VALUE, (long) old.listScroll() + delta)), old.detailScroll()));
    }

    /**
     * Changes inspector offset with overflow-safe bounds.
     *
     * @param delta signed body-font-row displacement
     */
    public void scrollDetail(int delta) {
        ViewState old = view();
        put(new ViewState(old.selection(), old.query(), old.category(), old.sort(), old.listScroll(),
                (int) Math.max(0, Math.min(Integer.MAX_VALUE, (long) old.detailScroll() + delta))));
    }

    /**
     * Computes a deterministic filtered virtual page and clamps after data shrink.
     *
     * @param snapshot current actor-visible data
     * @param capacity positive viewport row capacity
     * @return bounded visible page
     */
    public Page page(ProductionUiSnapshot snapshot, int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        List<Row> filtered = filtered(snapshot);
        int offset = Math.min(view().listScroll(), Math.max(0, filtered.size() - capacity));
        return new Page(filtered.subList(offset, Math.min(filtered.size(), offset + capacity)),
                filtered.size(), offset, categories(snapshot.rows(tab)));
    }

    /**
     * Moves keyboard selection and ensures the chosen row is inside the viewport.
     *
     * @param snapshot current actor-visible data
     * @param delta signed row displacement
     * @param capacity positive visible row capacity
     */
    public void moveSelection(ProductionUiSnapshot snapshot, int delta, int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        List<Row> filtered = filtered(snapshot);
        if (filtered.isEmpty()) return;
        ViewState old = view();
        int current = -1;
        for (int i = 0; i < filtered.size(); i++) {
            if (filtered.get(i).selection().equals(old.selection())) current = i;
        }
        int next = current < 0 ? (delta < 0 ? filtered.size() - 1 : 0)
                : (int) Math.max(0, Math.min(filtered.size() - 1, (long) current + delta));
        int offset = Math.min(old.listScroll(), Math.max(0, filtered.size() - capacity));
        if (next < offset) offset = next;
        if (next >= offset + capacity) offset = next - capacity + 1;
        put(new ViewState(filtered.get(next).selection(), old.query(), old.category(), old.sort(), offset, 0));
    }

    /**
     * Builds a display-name breadcrumb without requiring internal IDs.
     *
     * @param snapshot current inspector data
     * @return campaign, surface and optional object label
     */
    public String breadcrumb(ProductionUiSnapshot snapshot) {
        return "Кампания / " + tab.label() + snapshot.find(tab, view().selection())
                .map(row -> " / " + row.name()).orElse("");
    }

    private List<Row> filtered(ProductionUiSnapshot snapshot) {
        ViewState state = view();
        String query = state.query().strip().toLowerCase(Locale.ROOT);
        Comparator<Row> names = Comparator.comparing((Row row) -> row.name().toLowerCase(Locale.ROOT))
                .thenComparing(row -> row.selection().kind().name())
                .thenComparing(row -> row.selection().stableId());
        Comparator<Row> sort = switch (state.sort()) {
            case NAME -> names;
            case CATEGORY -> Comparator.comparing(Row::category).thenComparing(names);
            case RECENT -> Comparator.comparingLong(Row::chronologicalTick).reversed().thenComparing(names);
        };
        return snapshot.rows(tab).stream()
                .filter(row -> state.category().isEmpty() || state.category().equals(row.category()))
                .filter(row -> query.isEmpty() || searchable(row).contains(query))
                .sorted(sort).toList();
    }

    private static String searchable(Row row) {
        StringBuilder text = new StringBuilder(row.name()).append(' ').append(row.category())
                .append(' ').append(row.summary());
        row.sections().forEach(section -> {
            text.append(' ').append(section.title());
            section.lines().forEach(line -> text.append(' ').append(line.label()).append(' ').append(line.value()));
        });
        return text.toString().toLowerCase(Locale.ROOT);
    }

    private static List<String> categories(List<Row> rows) {
        return rows.stream().map(Row::category).distinct().sorted().toList();
    }

    private void put(ViewState state) { views.put(tab, state); }
}
