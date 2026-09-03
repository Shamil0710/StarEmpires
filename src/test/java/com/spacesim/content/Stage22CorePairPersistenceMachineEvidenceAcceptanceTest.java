package com.spacesim.content;

import com.spacesim.LargeDemoGalaxyFactory;
import com.spacesim.content.Stage22IndustrialUnionProductionState.YardSeriesState;
import com.spacesim.persistence.Stage22FactionProfileBindingCodec;
import com.spacesim.persistence.Stage22FactionProfileBindingState;
import com.spacesim.persistence.Stage22IndustrialUnionProductionStateCodec;
import com.spacesim.world.FactionIdentityResolver;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * B01 M22.6 machine evidence for current Stage-22 persistence and deterministic continuation.
 *
 * <p>This acceptance deliberately composes the already accepted profile-binding and Industrial Union
 * production sidecar codecs. It does not introduce a new save authority. The replay check resumes the
 * same finite M22.4 retool transition from both the live checkpoint and its decoded copy and requires
 * the resulting state to be byte-identical.</p>
 */
class Stage22CorePairPersistenceMachineEvidenceAcceptanceTest {
    private static final String TARGET_FAMILY = "ship_family.industrial_union.corvette";

    @Test
    void b01CorePairProfileAndProductionSidecarsRoundTripAndContinueDeterministically() {
        var schedule = Stage22CorePairExperimentProtocol.pairedSchedule(1);
        var first = run(schedule);
        var replay = run(schedule);

        assertEquals(1, first.pairedSeedCount());
        assertEquals(2, first.runCount());
        assertEquals(0, first.hardRuleBreachCount());
        assertEquals(1d, first.guardMetricMeans().get("profile_binding_byte_stable"));
        assertEquals(1d, first.guardMetricMeans().get("profile_binding_identity_bound"));
        assertEquals(1d, first.guardMetricMeans().get("union_checkpoint_byte_stable"));
        assertEquals(1d, first.guardMetricMeans().get("union_checkpoint_package_bound"));
        assertEquals(1d, first.guardMetricMeans().get("continued_replay_byte_equal"));
        assertFalse(first.evidenceFingerprint().isBlank());
        assertEquals(first.evidenceFingerprint(), replay.evidenceFingerprint());
    }

    private static Stage22CorePairMachineEvidenceBatch.ResultVector run(
            List<Stage22CorePairExperimentProtocol.RunCoordinate> schedule) {
        return Stage22CorePairMachineEvidenceBatch.runScenario(
                "B01",
                "profile_binding_and_union_mid_retool",
                "stage22.current",
                schedule,
                (scenario, variant, profile, coordinate) -> observe(coordinate));
    }

    private static Stage22CorePairMachineEvidenceBatch.ObservationPayload observe(
            Stage22CorePairExperimentProtocol.RunCoordinate coordinate) {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        var world = LargeDemoGalaxyFactory.createState(coordinate.seed(), content);
        Stage22FactionProfileCatalog profiles = Stage22FactionProfileLoader.loadDefault();
        FactionIdentityResolver identities = FactionIdentityResolver.createDefault(content, world.factionIdentities());

        Stage22FactionProfileBindingState bindings = Stage22FactionProfileBindingState.capture(profiles, identities);
        byte[] bindingBytes = Stage22FactionProfileBindingCodec.encode(bindings);
        Stage22FactionProfileBindingState restoredBindings = Stage22FactionProfileBindingCodec.decode(bindingBytes);
        restoredBindings.validateAgainst(profiles, identities);
        boolean bindingByteStable = Arrays.equals(bindingBytes, Stage22FactionProfileBindingCodec.encode(restoredBindings));
        Set<String> boundFactions = restoredBindings.bindings().stream()
                .map(Stage22FactionProfileBindingState.Binding::stableFactionId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        boolean identityBound = boundFactions.contains(Stage22CorePairBalanceEvidence.EMPIRE_FACTION_ID)
                && boundFactions.contains(Stage22CorePairBalanceEvidence.UNION_FACTION_ID);

        String packageFingerprint = Stage22IndustrialUnionPackageValidator.validateDefault().packageFingerprint();
        YardSeriesState pending = Stage22IndustrialUnionIndustrialProgram.beginRetool(
                Stage22IndustrialUnionProductionState.unqualifiedYard(Stage22IndustrialUnionIndustrialProgram.YARD_ID),
                TARGET_FAMILY);
        pending = Stage22IndustrialUnionIndustrialProgram.applyRetoolInputs(
                pending,
                Math.max(1L, pending.retoolWorkRemainingSeconds() / 3L),
                Math.max(1L, pending.retoolEnergyRemainingJ() / 3L));
        Stage22IndustrialUnionProductionState checkpoint = new Stage22IndustrialUnionProductionState(
                Stage22IndustrialUnionProductionState.CURRENT_VERSION,
                Stage22IndustrialUnionProductionState.STABLE_FACTION_ID,
                packageFingerprint,
                0L,
                List.of(pending));
        byte[] checkpointBytes = Stage22IndustrialUnionProductionStateCodec.encode(checkpoint);
        Stage22IndustrialUnionProductionState restoredCheckpoint =
                Stage22IndustrialUnionProductionStateCodec.decode(checkpointBytes);
        boolean checkpointByteStable = Arrays.equals(
                checkpointBytes,
                Stage22IndustrialUnionProductionStateCodec.encode(restoredCheckpoint));
        boolean packageBound = restoredCheckpoint.stableFactionId().equals(Stage22CorePairBalanceEvidence.UNION_FACTION_ID)
                && restoredCheckpoint.packageFingerprint().equals(packageFingerprint);

        Stage22IndustrialUnionProductionState continuedLive = continueFrom(checkpoint);
        Stage22IndustrialUnionProductionState continuedRestored = continueFrom(restoredCheckpoint);
        byte[] continuedLiveBytes = Stage22IndustrialUnionProductionStateCodec.encode(continuedLive);
        byte[] continuedRestoredBytes = Stage22IndustrialUnionProductionStateCodec.encode(continuedRestored);
        boolean continuationEqual = continuedLive.equals(continuedRestored)
                && Arrays.equals(continuedLiveBytes, continuedRestoredBytes)
                && continuedRestored.sequence() == checkpoint.sequence() + 1L
                && continuedRestored.findYard(Stage22IndustrialUnionIndustrialProgram.YARD_ID).commonalityStreak() == 1;

        ArrayList<String> breaches = new ArrayList<>();
        if (!bindingByteStable) breaches.add("profile_binding_round_trip_drift");
        if (!identityBound) breaches.add("profile_binding_core_pair_identity_drift");
        if (!checkpointByteStable) breaches.add("union_checkpoint_round_trip_drift");
        if (!packageBound) breaches.add("union_checkpoint_package_identity_drift");
        if (!continuationEqual) breaches.add("union_checkpoint_continuation_replay_drift");

        return new Stage22CorePairMachineEvidenceBatch.ObservationPayload(
                Map.of(
                        "profile_binding_bytes", (double) bindingBytes.length,
                        "profile_binding_count", (double) restoredBindings.bindings().size(),
                        "union_checkpoint_bytes", (double) checkpointBytes.length,
                        "union_retool_work_remaining_seconds", (double) pending.retoolWorkRemainingSeconds(),
                        "union_retool_energy_remaining_j", (double) pending.retoolEnergyRemainingJ()),
                Map.of(
                        "profile_binding_byte_stable", bindingByteStable ? 1d : 0d,
                        "profile_binding_identity_bound", identityBound ? 1d : 0d,
                        "union_checkpoint_byte_stable", checkpointByteStable ? 1d : 0d,
                        "union_checkpoint_package_bound", packageBound ? 1d : 0d,
                        "continued_replay_byte_equal", continuationEqual ? 1d : 0d),
                breaches);
    }

    private static Stage22IndustrialUnionProductionState continueFrom(
            Stage22IndustrialUnionProductionState checkpoint) {
        YardSeriesState pending = checkpoint.findYard(Stage22IndustrialUnionIndustrialProgram.YARD_ID);
        YardSeriesState paid = Stage22IndustrialUnionIndustrialProgram.applyRetoolInputs(
                pending,
                pending.retoolWorkRemainingSeconds(),
                pending.retoolEnergyRemainingJ());
        YardSeriesState qualified = Stage22IndustrialUnionIndustrialProgram.completeRetool(paid);
        YardSeriesState completed = Stage22IndustrialUnionIndustrialProgram.recordCompletedUnit(qualified, TARGET_FAMILY);
        return checkpoint.withYard(completed);
    }
}
