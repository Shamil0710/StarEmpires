package com.spacesim.content;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.io.IOException;
import java.io.InputStream;
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
import java.util.stream.Collectors;

/**
 * Non-authoritative evidence metadata for Stage-22.0 faction identity disposition records.
 *
 * <p>The mutable/runtime identity authority remains {@code ContentCatalog + WorldFactionIdentityState
 * + FactionIdentityResolver}. This class only closes the roadmap requirement that every disposition
 * names deterministic telemetry and acceptance-fixture evidence.</p>
 */
public final class Stage22FactionIdentityEvidence {
    private final List<EvidenceRecord> records;
    private final Map<String, EvidenceRecord> byStableId;
    private final String fingerprint;

    private Stage22FactionIdentityEvidence(List<EvidenceRecord> records) {
        ArrayList<EvidenceRecord> ordered = new ArrayList<>(Objects.requireNonNull(records, "records"));
        ordered.replaceAll(value -> Objects.requireNonNull(value, "evidence record"));
        ordered.sort(Comparator.comparing(EvidenceRecord::stableFactionId));
        LinkedHashMap<String, EvidenceRecord> index = new LinkedHashMap<>();
        for (EvidenceRecord record : ordered) {
            if (index.putIfAbsent(record.stableFactionId(), record) != null) {
                throw new IllegalArgumentException("Duplicate Stage-22 identity evidence: " + record.stableFactionId());
            }
        }
        this.records = List.copyOf(ordered);
        this.byStableId = java.util.Collections.unmodifiableMap(index);
        this.fingerprint = fingerprint(this.records);
    }

    /**
     * Loads evidence from the same versioned governance resource used by Stage-22.0.
     *
     * @return validated immutable faction identity evidence
     */
    public static Stage22FactionIdentityEvidence loadDefault() {
        ClassLoader loader = Stage22FactionIdentityEvidence.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(Stage22ContentGovernanceLoader.DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Stage-22 governance resource");
            }
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            Stage22FactionIdentityEvidence evidence = parse(json);
            Stage22ContentGovernanceCatalog governance = Stage22ContentGovernanceLoader.loadDefault();
            Set<String> governed = governance.getFactionIdentities().stream()
                    .map(Stage22ContentGovernanceCatalog.FactionIdentityDefinition::stableFactionId)
                    .collect(Collectors.toUnmodifiableSet());
            Set<String> evidenced = evidence.records.stream()
                    .map(EvidenceRecord::stableFactionId)
                    .collect(Collectors.toUnmodifiableSet());
            if (!governed.equals(evidenced)) {
                throw new IllegalStateException(
                        "Stage-22 identity evidence must cover every governed identity exactly: governed="
                                + governed + " evidenced=" + evidenced);
            }
            return evidence;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Stage-22 identity evidence", exception);
        }
    }

    /**
     * Parses evidence from one governance JSON document.
     *
     * @param json governance JSON containing identity evidence
     * @return validated immutable faction identity evidence
     */
    public static Stage22FactionIdentityEvidence parse(String json) {
        Objects.requireNonNull(json, "json");
        JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid Stage-22 identity evidence JSON", exception);
        }
        JsonValue array = root.get("identityEvidence");
        if (array == null || !array.isArray()) {
            throw new IllegalArgumentException("identityEvidence must be an array");
        }
        ArrayList<EvidenceRecord> records = new ArrayList<>();
        for (JsonValue node = array.child; node != null; node = node.next) {
            records.add(new EvidenceRecord(
                    required(node, "stableFactionId"),
                    required(node, "telemetryEvent"),
                    required(node, "fixture")));
        }
        return new Stage22FactionIdentityEvidence(records);
    }

    /** @return immutable evidence records ordered by stable faction ID */
    public List<EvidenceRecord> records() {
        return records;
    }

    /**
     * Finds evidence for one governed stable faction ID.
     *
     * @param stableFactionId stable runtime/save faction ID
     * @return evidence for the stable faction ID, or {@code null}
     */
    public EvidenceRecord find(String stableFactionId) {
        return byStableId.get(stableFactionId);
    }

    /** @return deterministic SHA-256 fingerprint of evidence metadata */
    public String fingerprint() {
        return fingerprint;
    }

    private static String required(JsonValue node, String field) {
        JsonValue value = node.get(field);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-blank string");
        }
        return value.asString().strip();
    }

    private static String fingerprint(List<EvidenceRecord> records) {
        StringBuilder canonical = new StringBuilder();
        for (EvidenceRecord record : records) {
            canonical.append(record.stableFactionId()).append('|')
                    .append(record.telemetryEvent()).append('|')
                    .append(record.fixture()).append('\n');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide mandatory SHA-256", exception);
        }
    }

    /** One roadmap-required evidence record attached to a governed identity disposition. */
    public record EvidenceRecord(String stableFactionId, String telemetryEvent, String fixture) {
        /** Validates one identity evidence record. */
        public EvidenceRecord {
            stableFactionId = Stage22ContentGovernanceCatalog.requireFactionId(stableFactionId);
            telemetryEvent = Stage22ContentGovernanceCatalog.requireContentId(
                    telemetryEvent, "Identity telemetry event");
            fixture = Stage22ContentGovernanceCatalog.requireNonBlank(fixture, "Identity fixture evidence");
        }
    }
}
