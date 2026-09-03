package com.spacesim.content;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Versioned M22.6 scenario contract for the Empire/Industrial Union core-pair freeze.
 *
 * <p>This catalog is diagnostic metadata only. It does not execute a scenario, choose a winner,
 * mutate simulation state or grant a faction modifier. Scenario evidence must be produced by the
 * already accepted common authorities named by each definition.</p>
 */
public final class Stage22CorePairBalanceCatalog {
    /** Semantic version of the first executable core-pair scenario contract. */
    public static final String SUITE_VERSION = "stage22.core_pair_balance_suite.v1";

    private static final List<ScenarioDefinition> SCENARIOS = buildScenarios();
    private static final Map<String, ScenarioDefinition> BY_ID = index(SCENARIOS);

    private Stage22CorePairBalanceCatalog() {
        throw new AssertionError("utility class");
    }

    /** @return immutable B00-B20 scenario contract in canonical ID order */
    public static List<ScenarioDefinition> scenarios() {
        return SCENARIOS;
    }

    /**
     * Finds one canonical scenario.
     *
     * @param id canonical B00-B20 scenario ID
     * @return scenario definition, or {@code null} when absent
     */
    public static ScenarioDefinition find(String id) {
        return BY_ID.get(id);
    }

    private static List<ScenarioDefinition> buildScenarios() {
        List<ScenarioDefinition> values = List.of(
                scenario("B00", "Catalog/authority audit", "L0", "Stage22 package/profile validators"),
                scenario("B01", "Save/load/replay round-trip", "L0", "Stage22 persistence codecs"),
                scenario("B02", "Viable cold start", "L1-L4", "Stage18 manufacturing/shipyard authorities"),
                scenario("B03", "Planned expansion", "L1-L5", "Stage18 station/facility construction authorities"),
                scenario("B04", "Critical-material shortage", "L1/L4", "Stage18 finite material/manufacturing inputs"),
                scenario("B05", "Single hub/route loss", "L4/L6", "Stage20 freight + Stage21 recovery authorities"),
                scenario("B06", "Distributed low-intensity raids", "L3-L6", "Stage21 operations + Stage19 combat/supply"),
                scenario("B07", "Equal-burden patrol contest", "L2/L3", "Stage17.5 engineering + Stage19 tactical stack"),
                scenario("B08", "Convoy escort/interdiction", "L2-L5", "Stage21E traffic/operations + Stage18 freight"),
                scenario("B09", "Prepared-system defense", "L3-L6", "Stage21 operations/control + Stage19 warfare supply"),
                scenario("B10", "Forced offensive projection", "L3-L6", "Stage21 fleet mobility + finite support fleet"),
                scenario("B11", "Degraded command and sensors", "L2/L3/L5", "Stage20 discovery + bounded knowledge"),
                scenario("B12", "Magazine-limited engagement", "L2-L4", "Stage19 ammunition/weapon runtime"),
                scenario("B13", "Long war / rolling attrition", "L1-L6", "Stage18 production/repair + Stage21 recovery"),
                scenario("B14", "Post-war recovery", "L4/L6", "Stage21G recovery + Stage18 repair/salvage"),
                scenario("B15", "Territory occupation", "L5/L6", "Stage21F territorial transition/control"),
                scenario("B16", "Treaty/market access shock", "L4/L5", "Stage17 diplomacy/market/tariff authorities"),
                scenario("B17", "New enemy adaptation", "L1-L5", "Stage17 policy review + finite Stage22 retool"),
                scenario("B18", "Player-causal explanation", "L7", "UI projection over authoritative cause traces"),
                scenario("B19", "Grayscale ship blind test", "L7", "Stage22 exact-fit production visual manifests"),
                scenario("B20", "Character style blind test", "L7", "Character Master Prompt + faction visual overlays"));

        ArrayList<ScenarioDefinition> ordered = new ArrayList<>(values);
        ordered.sort(Comparator.comparing(ScenarioDefinition::id));
        if (ordered.size() != 21) {
            throw new IllegalStateException("M22.6 canonical suite must contain exactly B00-B20");
        }
        for (int index = 0; index < ordered.size(); index++) {
            String expected = "B" + String.format(java.util.Locale.ROOT, "%02d", index);
            if (!expected.equals(ordered.get(index).id())) {
                throw new IllegalStateException("M22.6 scenario suite is not contiguous at " + expected);
            }
        }
        return List.copyOf(ordered);
    }

    private static ScenarioDefinition scenario(String id, String title, String layer, String authority) {
        return new ScenarioDefinition(id, SUITE_VERSION + "." + id.toLowerCase(java.util.Locale.ROOT),
                title, layer, Requirement.REQUIRED, authority);
    }

    private static Map<String, ScenarioDefinition> index(List<ScenarioDefinition> values) {
        LinkedHashMap<String, ScenarioDefinition> result = new LinkedHashMap<>();
        for (ScenarioDefinition value : values) {
            if (result.putIfAbsent(value.id(), value) != null) {
                throw new IllegalStateException("Duplicate M22.6 scenario ID: " + value.id());
            }
        }
        return Map.copyOf(result);
    }

    /** M22.6 closure requirement for one scenario. */
    public enum Requirement {
        /** Scenario is required for the current core-pair freeze. */ REQUIRED
    }

    /**
     * Immutable canonical scenario metadata.
     *
     * @param id canonical B00-B20 identifier
     * @param version immutable scenario version ID used by freeze evidence
     * @param title canonical scenario title
     * @param primaryLayer principal validation layer(s)
     * @param requirement M22.6 closure requirement
     * @param authorityEvidence existing common authority expected to produce evidence
     */
    public record ScenarioDefinition(
            String id,
            String version,
            String title,
            String primaryLayer,
            Requirement requirement,
            String authorityEvidence) {
        /** Validates one canonical scenario definition. */
        public ScenarioDefinition {
            id = requireText(id, "id");
            if (!id.matches("B(?:0[0-9]|1[0-9]|20)")) {
                throw new IllegalArgumentException("Unsupported balance scenario ID: " + id);
            }
            version = requireText(version, "version");
            title = requireText(title, "title");
            primaryLayer = requireText(primaryLayer, "primaryLayer");
            requirement = Objects.requireNonNull(requirement, "requirement");
            authorityEvidence = requireText(authorityEvidence, "authorityEvidence");
        }
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return checked;
    }
}
