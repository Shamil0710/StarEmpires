package com.spacesim.world;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** Immutable completed-route metadata carried by an actual destination observation. */
public final class Stage23FreightRouteReport {
    private static final String PREFIX = "delivered-route:v1:";
    private static final String SHARED_PREFIX = "shared-route:v1:";
    private Stage23FreightRouteReport() { }

    /**
     * Frozen route at a committed destination cargo transfer, independent of future rerouting.
     * @param orderId exact observed order identity
     * @param systems actually completed source-to-destination path
     */
    public record Report(String orderId, List<StarSystemId> systems) {
        /**
         * Validates and freezes historical path data.
         * @param orderId nonblank source order identity
         * @param systems completed path with at least one hop
         */
        public Report {
            Objects.requireNonNull(orderId);
            if (orderId.isBlank()) throw new IllegalArgumentException("Missing route order");
            systems = List.copyOf(systems);
            if (systems.size() < 2) throw new IllegalArgumentException("Route requires a completed hop");
            for (int i = 1; i < systems.size(); i++) if (systems.get(i).equals(systems.get(i - 1)))
                throw new IllegalArgumentException("Route repeats the same system in a hop");
        }
    }

    /**
     * Encodes a frozen path in the existing persistent evidence provenance field.
     * @param report actual completed route
     * @return canonical evidence identity, safely distinct from ordinary station-visit evidence
     */
    public static String provenance(Report report) {
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(report.orderId().getBytes(StandardCharsets.UTF_8))
                + ':' + report.systems().stream().map(s -> Long.toString(s.value())).collect(Collectors.joining(","));
    }

    /**
     * Reads only canonical actual-visit route evidence bound to its observed destination.
     * Old delivery counters and plain station observations are not route reports.
     * @param destination real static infrastructure observation
     * @param evidence source observation evidence
     * @return immutable historical route, or none for another/invalid evidence kind
     */
    public static Optional<Report> decode(Stage20DiscoveryKnowledgeState.StaticObjectRef destination,
            Stage20DiscoveryKnowledgeState.DiscoveryEvidence evidence) {
        if (destination.kind() != Stage20DiscoveryKnowledgeState.StaticObjectKind.INFRASTRUCTURE
                || evidence.source() != Stage20DiscoveryKnowledgeState.DiscoverySource.PHYSICAL_VISIT_OR_SURVEY
                || !evidence.provenanceId().startsWith(PREFIX)) return Optional.empty();
        try {
            String payload = evidence.provenanceId().substring(PREFIX.length());
            int separator = payload.indexOf(':');
            if (separator < 1) return Optional.empty();
            String order = new String(Base64.getUrlDecoder().decode(payload.substring(0, separator)), StandardCharsets.UTF_8);
            var systems = java.util.Arrays.stream(payload.substring(separator + 1).split(",", -1))
                    .map(s -> new StarSystemId(Long.parseLong(s))).toList();
            var report = new Report(order, systems);
            return systems.get(systems.size() - 1).equals(destination.systemId())
                    && provenance(report).equals(evidence.provenanceId()) ? Optional.of(report) : Optional.empty();
        } catch (IllegalArgumentException failure) { return Optional.empty(); }
    }

    /**
     * Names a paid copy without claiming that its recipient personally flew the route.
     * @param sellerNpc exact contacted source of the copy
     * @param report immutable operator report
     * @return canonical shared-report provenance
     */
    public static String sharedProvenance(String sellerNpc, Report report) {
        if (sellerNpc == null || sellerNpc.isBlank()) throw new IllegalArgumentException("Missing route seller");
        return SHARED_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(sellerNpc.getBytes(StandardCharsets.UTF_8))
                + ':' + provenance(report);
    }

    /**
     * Identifies a report at its exact receiver, including different stations in the same system.
     * @param destination actual receiver object
     * @param evidenceId immutable original report provenance
     * @return opaque stable contact-offer identity
     */
    public static String offerKey(Stage20DiscoveryKnowledgeState.StaticObjectRef destination, String evidenceId) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(destination.objectId().getBytes(StandardCharsets.UTF_8))
                + ':' + evidenceId;
    }

    /**
     * Reads a saved shared chart while retaining its original historical observation time.
     * @param destination actual observed receiver station
     * @param evidence paid/shared copy evidence
     * @return copied historical path, or none for another/noncanonical evidence kind
     */
    public static Optional<Report> decodeShared(Stage20DiscoveryKnowledgeState.StaticObjectRef destination,
            Stage20DiscoveryKnowledgeState.DiscoveryEvidence evidence) {
        if (evidence.source() != Stage20DiscoveryKnowledgeState.DiscoverySource.PURCHASED_OR_SHARED_MAP_DATA
                || !evidence.provenanceId().startsWith(SHARED_PREFIX)) return Optional.empty();
        try {
            String payload = evidence.provenanceId().substring(SHARED_PREFIX.length());
            int separator = payload.indexOf(':');
            if (separator < 1) return Optional.empty();
            String seller = new String(Base64.getUrlDecoder().decode(payload.substring(0, separator)), StandardCharsets.UTF_8);
            var report = decode(destination, new Stage20DiscoveryKnowledgeState.DiscoveryEvidence(
                    Stage20DiscoveryKnowledgeState.DiscoverySource.PHYSICAL_VISIT_OR_SURVEY,
                    payload.substring(separator + 1), evidence.observedAtSeconds(), evidence.freshUntilSeconds()));
            return report.filter(r -> sharedProvenance(seller, r).equals(evidence.provenanceId()));
        } catch (IllegalArgumentException failure) { return Optional.empty(); }
    }
}
