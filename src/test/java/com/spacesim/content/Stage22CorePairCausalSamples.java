package com.spacesim.content;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Replays observed min/median/max coordinates and retains physical traces, never synthetic outcomes. */
final class Stage22CorePairCausalSamples {
    private Stage22CorePairCausalSamples() { }

    static void archive(String id, Stage22CorePairMachineEvidenceBatch.ResultVector vector,
            String selectionMetric, Function<Stage22CorePairExperimentProtocol.RunCoordinate, Object> probe) {
        if (!"rc".equals(System.getProperty("stage22.evidenceProfile", "normal"))) return;
        var ordered = vector.observations().stream().sorted(Comparator
                .comparingDouble((Stage22CorePairMachineEvidenceBatch.RunObservation row) ->
                        row.metrics().get(selectionMetric))
                .thenComparingLong(Stage22CorePairMachineEvidenceBatch.RunObservation::seed)
                .thenComparing(row -> row.permutation().name())).toList();
        Map<String, Stage22CorePairMachineEvidenceBatch.RunObservation> selected = new LinkedHashMap<>();
        selected.put("minimum", ordered.get(0));
        selected.put("median", ordered.get((ordered.size() - 1) / 2));
        selected.put("maximum", ordered.get(ordered.size() - 1));
        List<Object> samples = new ArrayList<>();
        selected.forEach((label, row) -> {
            for (var permutation : Stage22CorePairExperimentProtocol.Permutation.values()) {
                var coordinate = new Stage22CorePairExperimentProtocol.RunCoordinate(row.seed(), permutation);
                Object direct = probe.apply(coordinate);
                if (!direct.equals(probe.apply(coordinate))) {
                    throw new AssertionError("Causal trace replay drift: " + coordinate);
                }
                samples.add(Map.of("selection", label, "selectionMetric", selectionMetric,
                        "selectedObservation", row, "replayedCoordinate", coordinate, "trace", direct));
            }
        });
        Stage22CorePairEvidenceArchive.write(id + "-traces", samples,
                "Measured min/median/max on the declared raw diagnostic metric, both mirrored members, with exact repeat equality. Selection is descriptive, not a faction winner or a campaign acceptance verdict.");
    }
}
