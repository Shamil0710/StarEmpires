package com.spacesim.world;

import com.spacesim.world.FactionActorObservationSnapshot.ActorObservation;
import com.spacesim.world.FactionActorObservationSnapshot.InterestKind;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Deterministic Stage-21A interest aggregation and conflict ordering. */
public final class FactionInterestResolver {
    private FactionInterestResolver() {
        throw new AssertionError("Utility class");
    }

    /**
     * Resolves current actor-bounded evidence into a deterministic priority order.
     *
     * <p>Inputs are grouped by stable interest kind and target identity. The strongest current
     * observation defines priority; ties resolve by enum order and target ID. No doctrine, combat,
     * production, or hidden-world statistic is added to the score.</p>
     *
     * @param snapshot actor-bounded observation snapshot
     * @return immutable decision trace suitable for tests and UI explanation
     */
    public static DecisionTrace resolve(FactionActorObservationSnapshot snapshot) {
        FactionActorObservationSnapshot checked = Objects.requireNonNull(snapshot, "Observation snapshot not set");
        Map<Key, List<ActorObservation>> grouped = new LinkedHashMap<>();
        for (ActorObservation observation : checked.currentObservations()) {
            grouped.computeIfAbsent(new Key(observation.interestKind(), observation.targetId()), ignored -> new ArrayList<>())
                    .add(observation);
        }

        List<FactionInterestEvidence> ordered = grouped.entrySet().stream()
                .map(entry -> evidence(entry.getKey(), entry.getValue()))
                .sorted()
                .toList();
        Optional<FactionInterestEvidence> primary = ordered.stream().findFirst();
        List<TraceEntry> entries = new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            FactionInterestEvidence item = ordered.get(index);
            entries.add(new TraceEntry(
                    index + 1,
                    item.kind(),
                    item.targetId(),
                    item.priorityBasisPoints(),
                    item.supportingObservations().size(),
                    index == 0));
        }
        return new DecisionTrace(
                checked.factionContentId(),
                checked.observedAtTick(),
                ordered,
                primary,
                entries);
    }

    private static FactionInterestEvidence evidence(Key key, List<ActorObservation> rows) {
        List<ActorObservation> sorted = rows.stream().sorted().distinct().toList();
        int priority = sorted.stream().mapToInt(ActorObservation::severityBasisPoints).max().orElseThrow();
        return new FactionInterestEvidence(key.kind(), key.targetId(), priority, sorted);
    }

    private record Key(InterestKind kind, String targetId) {
    }

    /**
     * One ranked trace row.
     *
     * @param rank one-based deterministic rank
     * @param kind interest family
     * @param targetId stable target identity
     * @param priorityBasisPoints evidence-derived priority
     * @param supportingReportCount number of retained supporting observations
     * @param primary whether this row won deterministic conflict ordering
     */
    public record TraceEntry(
            int rank,
            InterestKind kind,
            String targetId,
            int priorityBasisPoints,
            int supportingReportCount,
            boolean primary) {

        /** Validates one explanation row. */
        public TraceEntry {
            if (rank <= 0) {
                throw new IllegalArgumentException("Trace rank must be positive");
            }
            Objects.requireNonNull(kind, "Trace interest kind not set");
            targetId = requireText(targetId, "Trace target ID");
            if (priorityBasisPoints < 0 || priorityBasisPoints > 10_000) {
                throw new IllegalArgumentException("Trace priority must be in [0,10000]");
            }
            if (supportingReportCount <= 0) {
                throw new IllegalArgumentException("Trace support count must be positive");
            }
        }
    }

    /**
     * Immutable explanation of one actor observation review.
     *
     * @param factionContentId stable actor identity
     * @param observationTick actor-bounded snapshot tick
     * @param orderedEvidence deterministic evidence order
     * @param primaryInterest highest-priority current evidence when present
     * @param traceEntries compact ranked explanation rows
     */
    public record DecisionTrace(
            String factionContentId,
            long observationTick,
            List<FactionInterestEvidence> orderedEvidence,
            Optional<FactionInterestEvidence> primaryInterest,
            List<TraceEntry> traceEntries) {

        /** Validates canonical decision-trace shape. */
        public DecisionTrace {
            factionContentId = requireText(factionContentId, "Faction content ID");
            if (observationTick < 0L) {
                throw new IllegalArgumentException("Observation tick cannot be negative");
            }
            orderedEvidence = List.copyOf(Objects.requireNonNull(orderedEvidence, "Ordered evidence not set"));
            primaryInterest = Objects.requireNonNull(primaryInterest, "Primary interest optional not set");
            traceEntries = List.copyOf(Objects.requireNonNull(traceEntries, "Trace entries not set"));
            List<FactionInterestEvidence> canonical = orderedEvidence.stream().sorted().toList();
            if (!canonical.equals(orderedEvidence)) {
                throw new IllegalArgumentException("Ordered evidence is not canonical");
            }
            if (primaryInterest.isPresent() != !orderedEvidence.isEmpty()) {
                throw new IllegalArgumentException("Primary interest presence must match evidence presence");
            }
            if (primaryInterest.isPresent() && !primaryInterest.orElseThrow().equals(orderedEvidence.get(0))) {
                throw new IllegalArgumentException("Primary interest must be the first ordered evidence row");
            }
            if (traceEntries.size() != orderedEvidence.size()) {
                throw new IllegalArgumentException("Trace rows must correspond one-to-one with ordered evidence");
            }
        }

        /**
         * Serializes the decision explanation into stable UTF-8 bytes for replay/acceptance tests.
         *
         * @return deterministic canonical bytes independent of input collection ordering
         */
        public byte[] canonicalBytes() {
            StringBuilder builder = new StringBuilder();
            builder.append("stage21a-decision-v1\n")
                    .append(escape(factionContentId)).append('\t')
                    .append(observationTick).append('\n');
            for (TraceEntry entry : traceEntries) {
                builder.append(entry.rank()).append('\t')
                        .append(entry.kind().name()).append('\t')
                        .append(escape(entry.targetId())).append('\t')
                        .append(entry.priorityBasisPoints()).append('\t')
                        .append(entry.supportingReportCount()).append('\t')
                        .append(entry.primary()).append('\n');
            }
            for (FactionInterestEvidence evidence : orderedEvidence) {
                for (ActorObservation observation : evidence.supportingObservations()) {
                    builder.append("E\t")
                            .append(evidence.kind().name()).append('\t')
                            .append(escape(evidence.targetId())).append('\t')
                            .append(observation.domain().name()).append('\t')
                            .append(observation.severityBasisPoints()).append('\t')
                            .append(observation.evidence().channel().name()).append('\t')
                            .append(escape(observation.evidence().provenanceId())).append('\t')
                            .append(observation.evidence().observedAtTick()).append('\t')
                            .append(observation.evidence().freshUntilTick()).append('\n');
                }
            }
            return builder.toString().getBytes(StandardCharsets.UTF_8);
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n");
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}
