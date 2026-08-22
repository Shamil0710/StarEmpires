package com.spacesim.persistence;

import com.spacesim.content.Stage18ExtractionCatalog.SourceKind;
import com.spacesim.persistence.Stage18IndustrialState.PhysicalSourceSnapshot;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState.CanonicalRow;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState.MaterializedWorldSnapshot;
import com.spacesim.persistence.Stage20SourceOutpostMaterializer.MaterializedSourceOutpostRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Stage-20.5A save boundary for live generated source/outpost state.
 *
 * <p>Stage-20K intentionally stores current natural-source reserve in both the canonical generated
 * resource row and the Stage-18 industrial source snapshot so inconsistent saves fail closed. This
 * boundary updates those two representations atomically from the live source registry, recomputes
 * the canonical world fingerprint and rebinds the discovery sidecar to that exact fingerprint. It
 * never invokes generation and never changes immutable source/site/world decisions.</p>
 */
public final class Stage20SourceOutpostCampaignPersistence {
    private Stage20SourceOutpostCampaignPersistence() {
        throw new AssertionError("No instances");
    }

    /**
     * Captures live Stage-20.5A source/outpost state into a new self-consistent campaign snapshot.
     *
     * @param base exact campaign snapshot from which the live registry was materialized
     * @param registry live source/outpost registry after zero or more ordinary extraction operations
     * @return new campaign state whose dynamic natural reserve and outpost state can be restored exactly
     */
    public static Stage20GeneratedCampaignPersistentState capture(
            Stage20GeneratedCampaignPersistentState base,
            MaterializedSourceOutpostRegistry registry) {
        Stage20GeneratedCampaignPersistentState previous = Objects.requireNonNull(base, "base");
        MaterializedSourceOutpostRegistry live = Objects.requireNonNull(registry, "registry");
        if (live.sources().rootSeed() != previous.generationIdentity().worldSeed()
                || !live.sources().generatorVersion().equals(previous.generationIdentity().generatorVersion())
                || !live.sources().worldFingerprint().equals(previous.materializedWorld().worldFingerprint())) {
            throw new IllegalArgumentException("live source registry does not belong to the supplied campaign snapshot");
        }

        Stage18IndustrialState industry = live.captureIndustrialState(previous.industrialState());
        TreeMap<String, PhysicalSourceSnapshot> natural = new TreeMap<>();
        for (PhysicalSourceSnapshot source : industry.sources()) {
            if (source.sourceKind() == SourceKind.NATURAL_OCCURRENCE) {
                natural.put(source.sourceId(), source);
            }
        }

        ArrayList<CanonicalRow> worldRows = new ArrayList<>(previous.materializedWorld().worldRows().size());
        for (CanonicalRow row : previous.materializedWorld().worldRows()) {
            if (!row.domain().equals("RESOURCE_OCCURRENCE")) {
                worldRows.add(row);
                continue;
            }
            PhysicalSourceSnapshot source = natural.remove(row.stableId());
            if (source == null) {
                throw new IllegalArgumentException(
                        "canonical generated source has no captured natural source state: " + row.stableId());
            }
            List<String> values = row.values();
            if (values.size() < 16) {
                throw new IllegalArgumentException("malformed canonical RESOURCE_OCCURRENCE row: " + row.stableId());
            }
            if (Double.compare(Double.parseDouble(values.get(11)), source.initialAccessibleMassKg()) != 0) {
                throw new IllegalArgumentException(
                        "captured source initial reserve differs from canonical authority: " + row.stableId());
            }
            ArrayList<String> updated = new ArrayList<>(values);
            updated.set(12, Double.toHexString(source.remainingAccessibleMassKg()));
            worldRows.add(new CanonicalRow(row.domain(), row.stableId(), updated));
        }
        if (!natural.isEmpty()) {
            throw new IllegalArgumentException(
                    "captured natural source is absent from canonical generated world: " + natural.firstKey());
        }

        MaterializedWorldSnapshot materialized = MaterializedWorldSnapshot.create(
                worldRows, previous.materializedWorld().qualityRows());
        Stage20DiscoveryPersistentState discovery = new Stage20DiscoveryPersistentState(
                previous.discoveryState().envelopeVersion(),
                previous.discoveryState().rootSeed(),
                previous.discoveryState().worldGenerationVersion(),
                materialized.worldFingerprint(),
                previous.discoveryState().knowledgeStates());
        return new Stage20GeneratedCampaignPersistentState(
                previous.schemaVersion(),
                previous.generationIdentity(),
                materialized,
                previous.materializationState(),
                industry,
                discovery,
                previous.openRuntimeBoundaries());
    }
}
