package com.spacesim.ui;

import com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.UiSelection;
import com.spacesim.ui.GeneratedWorldUiSnapshot.InfoSection;
import com.spacesim.world.StarSystemId;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Read-only consolidated presentation; never a source of simulation or ownership authority. */
@SuppressWarnings("doclint:missing")
public record ProductionUiSnapshot(long tick, String viewerName, Map<Tab, List<Row>> surfaces) {
    /**
     * Validates and freezes unique per-surface stable selection identities.
     *
     * @param tick authoritative checkpoint tick
     * @param viewerName display name of the knowledge viewer
     * @param surfaces rows keyed by production surface
     */
    public ProductionUiSnapshot {
        if (tick < 0) throw new IllegalArgumentException("negative tick");
        viewerName = requireText(viewerName);
        EnumMap<Tab, List<Row>> copy = new EnumMap<>(Tab.class);
        Objects.requireNonNull(surfaces).forEach((tab, rows) -> {
            List<Row> frozen = List.copyOf(rows);
            HashSet<UiSelection> identities = new HashSet<>();
            for (Row row : frozen) {
                if (!identities.add(row.selection())) {
                    throw new IllegalArgumentException("duplicate presentation selection in " + tab);
                }
            }
            copy.put(Objects.requireNonNull(tab), frozen);
        });
        surfaces = Map.copyOf(copy);
    }

    /**
     * Reads a surface, treating absent authority data as an empty list.
     *
     * @param tab requested surface
     * @return immutable visible rows
     */
    public List<Row> rows(Tab tab) {
        return surfaces.getOrDefault(Objects.requireNonNull(tab), List.of());
    }

    /**
     * Resolves an inspector independently of search/filter visibility.
     *
     * @param tab owning surface
     * @param selection stable presentation selection
     * @return matching row when it still exists
     */
    public Optional<Row> find(Tab tab, UiSelection selection) {
        return rows(tab).stream().filter(row -> row.selection().equals(selection)).findFirst();
    }

    /**
     * Display label and explanation remain separate from the internal selection identity.
     *
     * @param selection stable internal selection key
     * @param name primary display label
     * @param category searchable player-facing category
     * @param summary displayed current-state summary
     * @param sections complete inspector values
     * @param provenance explanation of sources, units and derivation
     * @param focusSystem local camera target, or null when unavailable
     * @param focusFleet ordinary fleet to revalidate at navigation time, or zero
     * @param chronologicalTick source event or observation tick
     */
    public record Row(UiSelection selection, String name, String category, String summary,
            List<InfoSection> sections, String provenance, StarSystemId focusSystem,
            long focusFleet, long chronologicalTick) {
        /**
     * Validates one read-only presentation row.
     *
     * @param selection stable internal selection key
     * @param name primary display label
     * @param category searchable player-facing category
     * @param summary displayed current-state summary
     * @param sections complete inspector values
     * @param provenance explanation of sources, units and derivation
     * @param focusSystem local camera target, or null when unavailable
     * @param focusFleet ordinary fleet to revalidate at navigation time, or zero
     * @param chronologicalTick source event or observation tick
     */
        public Row {
            Objects.requireNonNull(selection);
            if (selection.stableId().isEmpty()) throw new IllegalArgumentException("row needs identity");
            name = requireText(name);
            category = requireText(category);
            summary = requireText(summary);
            provenance = requireText(provenance);
            sections = List.copyOf(sections);
            if (focusFleet < 0 || chronologicalTick < 0) throw new IllegalArgumentException("negative identity/tick");
        }

    /**
     * Appends a source explanation without discarding inspector values.
     *
     * @return complete inspector sections plus provenance
     */
        public List<InfoSection> explainedSections() {
            var result = new java.util.ArrayList<>(sections);
            result.add(InfoSection.of("Источник сведений", "Основание", provenance));
            return List.copyOf(result);
        }
    }

    private static String requireText(String value) {
        String checked = Objects.requireNonNull(value).strip();
        if (checked.isEmpty()) throw new IllegalArgumentException("empty presentation text");
        return checked;
    }
}
