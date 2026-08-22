package com.spacesim.persistence;

import com.spacesim.economy.Stage18StationStorage.StationStorageSnapshot;
import com.spacesim.persistence.Stage18IndustrialState.FacilityInstallationSnapshot;
import com.spacesim.persistence.Stage18IndustrialState.YardInstallationSnapshot;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState.CanonicalRow;
import com.spacesim.persistence.Stage20IndustrialEntityMaterializer.MaterializedIndustrialRegistry;
import com.spacesim.persistence.Stage20SourceOutpostMaterializer.MaterializedSourceOutpostRegistry;
import com.spacesim.world.Stage20OperationalIndustrialSpecializationPlan.OperationalSpecializationReport;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Composes Stage-20.5A source outposts and Stage-20.5C generated industrial stations over one
 * authoritative Stage-18 industrial persistence state.
 *
 * <p>Each lower-level materializer remains strict about the station identities it owns. Resume
 * therefore supplies the Stage-20.5C materializer with a validated station-owned view while the
 * complete campaign snapshot remains unchanged and continues to own source-outpost state. This is
 * an adapter only; it does not clone simulation state into a second runtime.</p>
 */
public final class Stage20GeneratedIndustrialRuntimeBridge {
    /** Stable composition contract version. */
    public static final String CURRENT_VERSION = "stage20_5.generated-industrial-runtime-bridge.v1";

    private Stage20GeneratedIndustrialRuntimeBridge() {
        throw new AssertionError("No instances");
    }

    /**
     * Performs first materialization from accepted Stage-20F bootstrap authority.
     *
     * @param saved exact Stage-20K campaign snapshot before initial industrial materialization
     * @param specialization matching accepted Stage-20F specialization authority
     * @return live composed industrial/source registry
     */
    public static MaterializedGeneratedIndustrialRuntime materializeBootstrap(
            Stage20GeneratedCampaignPersistentState saved,
            OperationalSpecializationReport specialization) {
        Stage20GeneratedCampaignPersistentState base = Objects.requireNonNull(saved, "saved");
        MaterializedIndustrialRegistry industrial = Stage20IndustrialEntityMaterializer.materializeBootstrap(
                base, Objects.requireNonNull(specialization, "specialization"));
        Stage18IndustrialState withIndustrial = industrial.captureIndustrialState(base.industrialState());
        Stage20GeneratedCampaignPersistentState intermediate = replaceIndustry(base, withIndustrial);
        MaterializedSourceOutpostRegistry sourceOutposts = Stage20SourceOutpostMaterializer.materialize(intermediate);
        return new MaterializedGeneratedIndustrialRuntime(industrial, sourceOutposts);
    }

    /**
     * Restores both industrial station families from one already-materialized campaign snapshot.
     *
     * @param saved exact saved campaign carrying ordinary Stage-18 industrial/source state
     * @return live composed industrial/source registry
     */
    public static MaterializedGeneratedIndustrialRuntime restore(
            Stage20GeneratedCampaignPersistentState saved) {
        Stage20GeneratedCampaignPersistentState base = Objects.requireNonNull(saved, "saved");
        MaterializedSourceOutpostRegistry sourceOutposts = Stage20SourceOutpostMaterializer.materialize(base);
        Stage20GeneratedCampaignPersistentState industrialView = replaceIndustry(
                base, industrialStationView(base));
        MaterializedIndustrialRegistry industrial = Stage20IndustrialEntityMaterializer.restore(industrialView);
        return new MaterializedGeneratedIndustrialRuntime(industrial, sourceOutposts);
    }

    private static Stage18IndustrialState industrialStationView(
            Stage20GeneratedCampaignPersistentState saved) {
        Set<String> stationIds = specializedStationIds(saved);
        List<StationStorageSnapshot> storage = saved.industrialState().stationStorages().stream()
                .filter(value -> stationIds.contains(value.stationId()))
                .toList();
        List<FacilityInstallationSnapshot> facilities = saved.industrialState().facilities().stream()
                .filter(value -> stationIds.contains(value.stationId()))
                .toList();
        List<YardInstallationSnapshot> yards = saved.industrialState().yards().stream()
                .filter(value -> stationIds.contains(value.stationId()))
                .toList();
        var constructionOrders = saved.industrialState().constructionOrders().stream()
                .filter(value -> stationIds.contains(value.stationId()))
                .toList();
        var processOrders = saved.industrialState().processOrders().stream()
                .filter(value -> stationIds.contains(value.stationId()))
                .toList();
        if (storage.size() != stationIds.size()) {
            throw new IllegalArgumentException(
                    "saved industrial station set does not exactly cover canonical specializations");
        }
        return new Stage18IndustrialState(
                Stage18IndustrialState.CURRENT_VERSION,
                saved.industrialState().contentFingerprint(),
                saved.industrialState().simulationTick(),
                saved.industrialState().sources(),
                storage,
                facilities,
                yards,
                constructionOrders,
                processOrders);
    }

    private static Set<String> specializedStationIds(Stage20GeneratedCampaignPersistentState saved) {
        TreeSet<String> ids = new TreeSet<>();
        for (CanonicalRow row : saved.materializedWorld().worldRows()) {
            if (!row.domain().equals("INDUSTRIAL_SPECIALIZATION")) {
                continue;
            }
            if (row.values().size() < 3) {
                throw new IllegalArgumentException("malformed canonical INDUSTRIAL_SPECIALIZATION row");
            }
            ids.add(row.values().get(1));
        }
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("saved world has no canonical industrial stations");
        }
        return Set.copyOf(ids);
    }

    private static Stage20GeneratedCampaignPersistentState replaceIndustry(
            Stage20GeneratedCampaignPersistentState base,
            Stage18IndustrialState industry) {
        return new Stage20GeneratedCampaignPersistentState(
                base.schemaVersion(),
                base.generationIdentity(),
                base.materializedWorld(),
                base.materializationState(),
                industry,
                base.discoveryState(),
                base.openRuntimeBoundaries());
    }

    /**
     * One live composed generated industrial runtime.
     *
     * @param industrial generated Stage-20F station/facility/storage/yard runtime registry
     * @param sourceOutposts generated finite-source extraction outpost registry
     */
    public record MaterializedGeneratedIndustrialRuntime(
            MaterializedIndustrialRegistry industrial,
            MaterializedSourceOutpostRegistry sourceOutposts) {
        /**
         * Validates one composed runtime.
         *
         * @param industrial generated industrial station registry
         * @param sourceOutposts generated source-outpost registry
         */
        public MaterializedGeneratedIndustrialRuntime {
            Objects.requireNonNull(industrial, "industrial");
            Objects.requireNonNull(sourceOutposts, "sourceOutposts");
            if (industrial.rootSeed() != sourceOutposts.sources().rootSeed()
                    || !industrial.generatorVersion().equals(sourceOutposts.sources().generatorVersion())
                    || !industrial.worldFingerprint().equals(sourceOutposts.sources().worldFingerprint())) {
                throw new IllegalArgumentException("composed generated industrial registries differ in world authority");
            }
        }

        /**
         * Captures both station families and finite natural reserves into one self-consistent campaign.
         *
         * @param base campaign snapshot from which this runtime was materialized
         * @return exact new Stage-20K campaign snapshot
         */
        public Stage20GeneratedCampaignPersistentState captureCampaignState(
                Stage20GeneratedCampaignPersistentState base) {
            Stage20GeneratedCampaignPersistentState previous = Objects.requireNonNull(base, "base");
            Stage18IndustrialState withIndustrial = industrial.captureIndustrialState(previous.industrialState());
            Stage20GeneratedCampaignPersistentState intermediate = replaceIndustry(previous, withIndustrial);
            return Stage20SourceOutpostCampaignPersistence.capture(intermediate, sourceOutposts);
        }
    }
}
