package com.spacesim.content;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Deterministic orchestration layer for machine-verifiable M22.6 scenario evidence.
 *
 * <p>The batch deliberately owns no gameplay state and no scenario simulation. A probe must call the
 * already accepted production, combat, diplomacy, territory or persistence authority for the
 * requested scenario and return only observed metrics. The batch then validates paired seed
 * symmetry, freezes raw observations and produces a stable evidence fingerprint.</p>
 *
 * <p>B18-B20 are intentionally rejected because their closure thresholds require recorded human
 * review and cannot be manufactured from machine telemetry.</p>
 */
final class Stage22CorePairMachineEvidenceBatch {
    static final int LAST_MACHINE_SCENARIO = 17;

    private Stage22CorePairMachineEvidenceBatch() {
        throw new AssertionError("utility class");
    }

    static ResultVector runScenario(
            String scenarioId,
            String variantId,
            String profileId,
            List<Stage22CorePairExperimentProtocol.RunCoordinate> schedule,
            ScenarioProbe probe) {
        Stage22CorePairBalanceCatalog.ScenarioDefinition scenario = requireMachineScenario(scenarioId);
        String checkedVariant = requireText(variantId, "variantId");
        String checkedProfile = requireText(profileId, "profileId");
        List<Stage22CorePairExperimentProtocol.RunCoordinate> checkedSchedule = validatePairedSchedule(schedule);
        ScenarioProbe checkedProbe = Objects.requireNonNull(probe, "probe");

        ArrayList<RunObservation> observations = new ArrayList<>(checkedSchedule.size());
        Set<String> metricKeys = null;
        Set<String> guardKeys = null;
        for (Stage22CorePairExperimentProtocol.RunCoordinate coordinate : checkedSchedule) {
            ObservationPayload payload = Objects.requireNonNull(
                    checkedProbe.observe(scenario, checkedVariant, checkedProfile, coordinate),
                    "scenario probe returned null payload");
            if (metricKeys == null) {
                metricKeys = payload.metrics().keySet();
                guardKeys = payload.guardMetrics().keySet();
            } else {
                if (!metricKeys.equals(payload.metrics().keySet())) {
                    throw new IllegalArgumentException("Scenario probe metric keys drift across paired runs");
                }
                if (!guardKeys.equals(payload.guardMetrics().keySet())) {
                    throw new IllegalArgumentException("Scenario probe guard metric keys drift across paired runs");
                }
            }
            observations.add(new RunObservation(
                    coordinate.seed(),
                    coordinate.permutation(),
                    payload.metrics(),
                    payload.guardMetrics(),
                    payload.hardRuleBreaches()));
        }

        Map<String, Double> metricMeans = means(observations, false);
        Map<String, Double> guardMeans = means(observations, true);
        int breachCount = observations.stream().mapToInt(value -> value.hardRuleBreaches().size()).sum();
        ResultVector provisional = new ResultVector(
                scenario.id(),
                scenario.version(),
                checkedVariant,
                checkedProfile,
                checkedSchedule.size() / 2,
                observations.size(),
                List.copyOf(observations),
                metricMeans,
                guardMeans,
                breachCount,
                "");
        return provisional.withFingerprint(fingerprint(provisional));
    }

    private static Stage22CorePairBalanceCatalog.ScenarioDefinition requireMachineScenario(String scenarioId) {
        String checked = requireText(scenarioId, "scenarioId");
        Stage22CorePairBalanceCatalog.ScenarioDefinition scenario = Stage22CorePairBalanceCatalog.find(checked);
        if (scenario == null) {
            throw new IllegalArgumentException("Unknown M22.6 scenario: " + checked);
        }
        int number = Integer.parseInt(checked.substring(1));
        if (number > LAST_MACHINE_SCENARIO) {
            throw new IllegalArgumentException(
                    checked + " requires recorded human evidence and cannot use the machine evidence batch");
        }
        return scenario;
    }

    private static List<Stage22CorePairExperimentProtocol.RunCoordinate> validatePairedSchedule(
            List<Stage22CorePairExperimentProtocol.RunCoordinate> schedule) {
        Objects.requireNonNull(schedule, "schedule");
        if (schedule.isEmpty()) {
            throw new IllegalArgumentException("Machine evidence schedule must not be empty");
        }
        LinkedHashMap<Long, List<Stage22CorePairExperimentProtocol.Permutation>> bySeed = new LinkedHashMap<>();
        for (Stage22CorePairExperimentProtocol.RunCoordinate coordinate : schedule) {
            Stage22CorePairExperimentProtocol.RunCoordinate checked =
                    Objects.requireNonNull(coordinate, "schedule contains null coordinate");
            bySeed.computeIfAbsent(checked.seed(), ignored -> new ArrayList<>()).add(checked.permutation());
        }
        List<Stage22CorePairExperimentProtocol.Permutation> required = List.of(
                Stage22CorePairExperimentProtocol.Permutation.DEFAULT,
                Stage22CorePairExperimentProtocol.Permutation.MIRRORED);
        bySeed.forEach((seed, permutations) -> {
            if (!required.equals(permutations)) {
                throw new IllegalArgumentException(
                        "Seed " + seed + " must contain DEFAULT then MIRRORED exactly once");
            }
        });
        if (Math.multiplyExact(bySeed.size(), 2) != schedule.size()) {
            throw new IllegalArgumentException("Machine evidence schedule contains unpaired coordinates");
        }
        return List.copyOf(schedule);
    }

    private static Map<String, Double> means(List<RunObservation> observations, boolean guards) {
        TreeMap<String, Double> sums = new TreeMap<>();
        for (RunObservation observation : observations) {
            Map<String, Double> source = guards ? observation.guardMetrics() : observation.metrics();
            source.forEach((key, value) -> sums.merge(key, value, Double::sum));
        }
        LinkedHashMap<String, Double> result = new LinkedHashMap<>();
        sums.forEach((key, value) -> result.put(key, value / observations.size()));
        return Map.copyOf(result);
    }

    private static String fingerprint(ResultVector vector) {
        StringBuilder canonical = new StringBuilder()
                .append(vector.scenarioId()).append('|')
                .append(vector.scenarioVersion()).append('|')
                .append(vector.variantId()).append('|')
                .append(vector.profileId()).append('|')
                .append(vector.pairedSeedCount()).append('|')
                .append(vector.runCount()).append('|');
        for (RunObservation observation : vector.observations()) {
            canonical.append(observation.seed()).append(':')
                    .append(observation.permutation()).append(':');
            appendMap(canonical, observation.metrics());
            canonical.append(':');
            appendMap(canonical, observation.guardMetrics());
            canonical.append(':');
            observation.hardRuleBreaches().stream().sorted().forEach(value -> canonical.append(value).append(','));
            canonical.append('|');
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide mandatory SHA-256", exception);
        }
    }

    private static void appendMap(StringBuilder target, Map<String, Double> values) {
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> target.append(entry.getKey())
                        .append('=')
                        .append(Double.toHexString(entry.getValue()))
                        .append(','));
    }

    private static Map<String, Double> freezeFiniteMap(Map<String, Double> source, String label) {
        Objects.requireNonNull(source, label);
        TreeMap<String, Double> ordered = new TreeMap<>();
        source.forEach((key, value) -> {
            String checkedKey = requireText(key, label + " key");
            Double checkedValue = Objects.requireNonNull(value, label + " value for " + checkedKey);
            if (!Double.isFinite(checkedValue)) {
                throw new IllegalArgumentException(label + " value must be finite for " + checkedKey);
            }
            ordered.put(checkedKey, checkedValue);
        });
        return Map.copyOf(ordered);
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return checked;
    }

    @FunctionalInterface
    interface ScenarioProbe {
        ObservationPayload observe(
                Stage22CorePairBalanceCatalog.ScenarioDefinition scenario,
                String variantId,
                String profileId,
                Stage22CorePairExperimentProtocol.RunCoordinate coordinate);
    }

    record ObservationPayload(
            Map<String, Double> metrics,
            Map<String, Double> guardMetrics,
            List<String> hardRuleBreaches) {
        ObservationPayload {
            metrics = freezeFiniteMap(metrics, "metrics");
            guardMetrics = freezeFiniteMap(guardMetrics, "guardMetrics");
            Objects.requireNonNull(hardRuleBreaches, "hardRuleBreaches");
            ArrayList<String> checkedBreaches = new ArrayList<>(hardRuleBreaches.size());
            for (String breach : hardRuleBreaches) {
                checkedBreaches.add(requireText(breach, "hardRuleBreach"));
            }
            checkedBreaches.sort(Comparator.naturalOrder());
            hardRuleBreaches = List.copyOf(checkedBreaches);
        }
    }

    record RunObservation(
            long seed,
            Stage22CorePairExperimentProtocol.Permutation permutation,
            Map<String, Double> metrics,
            Map<String, Double> guardMetrics,
            List<String> hardRuleBreaches) {
        RunObservation {
            if (seed < 0L) {
                throw new IllegalArgumentException("seed must be non-negative");
            }
            permutation = Objects.requireNonNull(permutation, "permutation");
            metrics = freezeFiniteMap(metrics, "metrics");
            guardMetrics = freezeFiniteMap(guardMetrics, "guardMetrics");
            hardRuleBreaches = List.copyOf(Objects.requireNonNull(hardRuleBreaches, "hardRuleBreaches"));
        }
    }

    record ResultVector(
            String scenarioId,
            String scenarioVersion,
            String variantId,
            String profileId,
            int pairedSeedCount,
            int runCount,
            List<RunObservation> observations,
            Map<String, Double> metricMeans,
            Map<String, Double> guardMetricMeans,
            int hardRuleBreachCount,
            String evidenceFingerprint) {
        ResultVector {
            scenarioId = requireText(scenarioId, "scenarioId");
            scenarioVersion = requireText(scenarioVersion, "scenarioVersion");
            variantId = requireText(variantId, "variantId");
            profileId = requireText(profileId, "profileId");
            if (pairedSeedCount <= 0 || runCount != pairedSeedCount * 2) {
                throw new IllegalArgumentException("result vector must contain exact paired runs");
            }
            observations = List.copyOf(Objects.requireNonNull(observations, "observations"));
            metricMeans = freezeFiniteMap(metricMeans, "metricMeans");
            guardMetricMeans = freezeFiniteMap(guardMetricMeans, "guardMetricMeans");
            if (hardRuleBreachCount < 0) {
                throw new IllegalArgumentException("hardRuleBreachCount must be non-negative");
            }
            evidenceFingerprint = Objects.requireNonNull(evidenceFingerprint, "evidenceFingerprint");
        }

        private ResultVector withFingerprint(String fingerprint) {
            return new ResultVector(
                    scenarioId,
                    scenarioVersion,
                    variantId,
                    profileId,
                    pairedSeedCount,
                    runCount,
                    observations,
                    metricMeans,
                    guardMetricMeans,
                    hardRuleBreachCount,
                    fingerprint);
        }
    }
}
