package com.spacesim.persistence;

import com.spacesim.world.Stage20DiscoveryKnowledgeState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Versioned Stage-20G persistent static-discovery sidecar.
 *
 * <p>The sidecar is bound to the exact generated world seed/version/fingerprint. It contains one
 * durable static-knowledge snapshot per actor or intelligence node. Mobile contacts are excluded:
 * local measurements/tracks continue through the Stage-17.5 sensor-knowledge persistence path and
 * must be cleared or rebound at a system identity transition.</p>
 *
 * @param envelopeVersion Stage-20G discovery schema version
 * @param rootSeed authoritative generated-world root seed
 * @param worldGenerationVersion exact generated-world authority version
 * @param worldFingerprint exact generated-world identity/content fingerprint
 * @param knowledgeStates deterministic owner-local persistent knowledge snapshots
 */
public record Stage20DiscoveryPersistentState(
        int envelopeVersion,
        long rootSeed,
        String worldGenerationVersion,
        String worldFingerprint,
        List<Stage20DiscoveryKnowledgeState> knowledgeStates) {
    /** Current Stage-20G discovery-persistence envelope version. */
    public static final int CURRENT_VERSION = 1;

    /**
     * Validates and deterministically orders one discovery sidecar.
     *
     * @param envelopeVersion Stage-20G discovery schema version
     * @param rootSeed authoritative generated-world root seed
     * @param worldGenerationVersion exact generation authority version
     * @param worldFingerprint exact world identity/content fingerprint
     * @param knowledgeStates owner-local knowledge snapshots
     */
    public Stage20DiscoveryPersistentState {
        if (envelopeVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported Stage-20G discovery envelope version: " + envelopeVersion);
        }
        worldGenerationVersion = requireText(worldGenerationVersion, "worldGenerationVersion");
        worldFingerprint = requireText(worldFingerprint, "worldFingerprint");
        ArrayList<Stage20DiscoveryKnowledgeState> copy = new ArrayList<>(
                Objects.requireNonNull(knowledgeStates, "knowledgeStates"));
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("knowledgeStates cannot contain null");
        }
        copy.sort(Comparator.comparing(Stage20DiscoveryKnowledgeState::ownerId));
        Set<String> owners = new HashSet<>();
        for (Stage20DiscoveryKnowledgeState state : copy) {
            if (!owners.add(state.ownerId())) {
                throw new IllegalArgumentException("duplicate discovery knowledge owner: " + state.ownerId());
            }
        }
        knowledgeStates = List.copyOf(copy);
    }

    /**
     * Finds one owner-local knowledge snapshot.
     *
     * @param ownerId stable player/faction/intelligence-network identity
     * @return matching snapshot or an empty snapshot for the requested owner
     */
    public Stage20DiscoveryKnowledgeState knowledgeFor(String ownerId) {
        String checked = requireText(ownerId, "ownerId");
        return knowledgeStates.stream()
                .filter(value -> value.ownerId().equals(checked))
                .findFirst()
                .orElseGet(() -> new Stage20DiscoveryKnowledgeState(checked, List.of()));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
