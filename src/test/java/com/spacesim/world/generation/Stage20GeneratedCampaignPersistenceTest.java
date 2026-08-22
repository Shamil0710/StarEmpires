package com.spacesim.world.generation;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.Stage18IndustrialState;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistence;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistenceCodec;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState.GenerationIdentity;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState.MaterializedWorldSnapshot;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState.OpenRuntimeBoundary;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState.RegenerationPolicy;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState.ResumePolicy;
import com.spacesim.persistence.Stage20MaterializationPersistence;
import com.spacesim.persistence.Stage20MaterializationPersistentState;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.simulation.Stage20MaterializationService;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.Stage20DiscoveryKnowledgeState;
import com.spacesim.world.Stage20OperationalIndustrialSpecializationPlan.RuntimeBridgeRequirement;
import com.spacesim.world.Stage20SpecialLocationGenerator;
import com.spacesim.world.Stage20SpecialLocationWorld;
import com.spacesim.world.generation.Stage20OperationalIndustrialSpecializationProductionIntegrationTest
        .CadenceFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20GeneratedCampaignPersistenceTest {
    private static volatile CadenceFixture sharedCadenceFixture;
    private static volatile Stage20GeneratedCampaignPersistentState sharedState;

    @TempDir
    Path temporaryDirectory;

    @Test
    void sameSeedVersionProfileAndContentProduceEquivalentWorldAndQualityHeadlessly() {
        CadenceFixture first = cadenceFixture();
        var secondResolved = Stage20ResolvedGeneratedWorldProductionProbe.runCurrent(1L);
        Stage20SpecialLocationWorld firstSpecials =
                Stage20SpecialLocationGenerator.generateCurrent(first.resolved());
        Stage20SpecialLocationWorld secondSpecials =
                Stage20SpecialLocationGenerator.generateCurrent(secondResolved);
        Stage18IndustrialState industry = Stage18IndustrialState.empty(0L);

        MaterializedWorldSnapshot firstSnapshot =
                Stage20GeneratedCampaignPersistence.captureMaterializedWorld(
                        first.resolved(), firstSpecials, first.specialization(), industry);
        MaterializedWorldSnapshot secondSnapshot =
                Stage20GeneratedCampaignPersistence.captureMaterializedWorld(
                        secondResolved, secondSpecials, first.specialization(), industry);

        assertEquals(firstSnapshot.worldFingerprint(), secondSnapshot.worldFingerprint());
        assertEquals(firstSnapshot.qualityFingerprint(), secondSnapshot.qualityFingerprint());
        assertEquals(firstSnapshot.worldRows(), secondSnapshot.worldRows());
        assertEquals(firstSnapshot.qualityRows(), secondSnapshot.qualityRows());

        ArrayList<Stage20GeneratedCampaignPersistentState.CanonicalRow> reversed =
                new ArrayList<>(firstSnapshot.worldRows());
        java.util.Collections.reverse(reversed);
        MaterializedWorldSnapshot reordered = MaterializedWorldSnapshot.create(
                reversed, firstSnapshot.qualityRows());
        assertEquals(firstSnapshot.worldFingerprint(), reordered.worldFingerprint());
        assertEquals(firstSnapshot.worldRows(), reordered.worldRows());
    }

    @Test
    void aggregateRoundTripRetainsAuthorityKnowledgeFarKinematicsAndOpenRuntimeSeams()
            throws Exception {
        CadenceFixture fixture = cadenceFixture();
        Stage20SpecialLocationWorld specials =
                Stage20SpecialLocationGenerator.generateCurrent(fixture.resolved());
        SimulationSession session = SimulationSession.createDemo(1L);
        Stage20MaterializationService materialization = Stage20MaterializationService.forSession(session);
        Entity entity = firstPersistentEntity(session);
        EntityId entityId = entity.getComponent(EntityIdComponent.class).id;
        LocalPhysicalKinematics farState = new LocalPhysicalKinematics(
                new LocalPhysicalPosition(
                        8_500_000_000_000L, -7_250_000_000_000L, 48_125.5d, -31_250.25d),
                12_750.125d,
                -9_500.875d);
        materialization.registerPhysicalState(entityId, farState);
        materialization.dematerialize(entityId);
        Stage20MaterializationPersistentState physical =
                Stage20MaterializationPersistence.capture(session, materialization);
        Stage18IndustrialState industrial = Stage18IndustrialState.empty(0L);
        String owner = fixture.specialization().specializations().get(0).key().stableFactionId();

        Stage20GeneratedCampaignPersistentState captured =
                Stage20GeneratedCampaignPersistence.capture(
                        fixture.resolved(),
                        specials,
                        fixture.specialization(),
                        physical,
                        industrial,
                        List.of(new Stage20DiscoveryKnowledgeState(owner, List.of())));
        byte[] first = Stage20GeneratedCampaignPersistenceCodec.encode(captured);
        byte[] second = Stage20GeneratedCampaignPersistenceCodec.encode(captured);
        Stage20GeneratedCampaignPersistentState decoded =
                Stage20GeneratedCampaignPersistenceCodec.decode(first);

        assertArrayEquals(first, second);
        assertEquals(captured, decoded);
        assertEquals(owner, decoded.discoveryState().knowledgeStates().get(0).ownerId());
        assertEquals(farState, decoded.materializationState().physicalEntities().stream()
                .filter(value -> value.id().equals(entityId))
                .findFirst().orElseThrow().physicalState());
        assertEquals(Set.of(RuntimeBridgeRequirement.values()),
                decoded.stage20fRuntimeBridgeRequirements());
        assertEquals(Set.of(OpenRuntimeBoundary.values()), Set.copyOf(decoded.openRuntimeBoundaries()));
        assertTrue(hasDomain(decoded, "JUMP_EDGE"));
        assertTrue(hasDomain(decoded, "RESOURCE_OCCURRENCE"));
        assertTrue(hasDomain(decoded, "INITIAL_EXTRACTION_SITE"));
        assertTrue(hasDomain(decoded, "FREIGHT_OWNERSHIP_SLOT"));
        assertTrue(hasDomain(decoded, "INDUSTRIAL_PROCESS_PLAN"));
        assertTrue(hasDomain(decoded, "INDUSTRIAL_YARD_PLAN"));
        assertTrue(hasDomain(decoded, "SPECIAL_LOCATION"));

        Path save = temporaryDirectory.resolve("stage20k-campaign.bin");
        Stage20GeneratedCampaignPersistenceCodec.write(save, captured);
        assertEquals(captured, Stage20GeneratedCampaignPersistenceCodec.read(save));
    }

    @Test
    void changedGeneratorIdentityPreservesSnapshotAndRequiresExplicitPolicy() {
        Stage20GeneratedCampaignPersistentState state = fixtureState();
        GenerationIdentity saved = state.generationIdentity();
        GenerationIdentity changed = new GenerationIdentity(
                saved.worldSeed(),
                saved.generatorVersion() + ".next",
                saved.sourceGeneratorVersion(),
                saved.generationProfile(),
                saved.contentFingerprint());

        var decision = state.resumeAgainst(changed);

        assertEquals(ResumePolicy.PRESERVE_SAVED_MATERIALIZED_WORLD, decision.resumePolicy());
        assertFalse(decision.generationIdentityMatches());
        assertEquals(
                RegenerationPolicy.EXPLICIT_MIGRATION_OR_NEW_WORLD_REQUIRED,
                decision.regenerationPolicy());
        assertTrue(state.resumeAgainst(saved).generationIdentityMatches());
    }

    @Test
    void decoderRejectsTruncationTrailingBytesAndFingerprintTampering() {
        Stage20GeneratedCampaignPersistentState state = fixtureState();
        byte[] bytes = Stage20GeneratedCampaignPersistenceCodec.encode(state);
        byte[] truncated = Arrays.copyOf(bytes, bytes.length - 1);
        byte[] trailing = Arrays.copyOf(bytes, bytes.length + 1);

        assertThrows(IllegalArgumentException.class,
                () -> Stage20GeneratedCampaignPersistenceCodec.decode(truncated));
        assertThrows(IllegalArgumentException.class,
                () -> Stage20GeneratedCampaignPersistenceCodec.decode(trailing));
        assertThrows(IllegalArgumentException.class, () -> new MaterializedWorldSnapshot(
                state.materializedWorld().snapshotVersion(),
                state.materializedWorld().worldRows(),
                state.materializedWorld().qualityRows(),
                "0".repeat(64),
                state.materializedWorld().qualityFingerprint()));
    }

    private static Stage20GeneratedCampaignPersistentState fixtureState() {
        Stage20GeneratedCampaignPersistentState existing = sharedState;
        if (existing != null) {
            return existing;
        }
        CadenceFixture fixture = cadenceFixture();
        SimulationSession session = SimulationSession.createDemo(1L);
        Stage20MaterializationPersistentState physical = Stage20MaterializationPersistence.capture(
                session, Stage20MaterializationService.forSession(session));
        Stage20GeneratedCampaignPersistentState created = Stage20GeneratedCampaignPersistence.capture(
                fixture.resolved(),
                Stage20SpecialLocationGenerator.generateCurrent(fixture.resolved()),
                fixture.specialization(),
                physical,
                Stage18IndustrialState.empty(0L),
                List.of(new Stage20DiscoveryKnowledgeState("faction.persistence", List.of())));
        sharedState = created;
        return created;
    }

    private static synchronized CadenceFixture cadenceFixture() {
        if (sharedCadenceFixture == null) {
            sharedCadenceFixture =
                    Stage20OperationalIndustrialSpecializationProductionIntegrationTest.cadenceFixture();
        }
        return sharedCadenceFixture;
    }

    private static boolean hasDomain(
            Stage20GeneratedCampaignPersistentState state, String domain) {
        return state.materializedWorld().worldRows().stream()
                .anyMatch(row -> row.domain().equals(domain));
    }

    private static Entity firstPersistentEntity(SimulationSession session) {
        for (Entity entity : session.getEngine().getEntities()) {
            if (entity.getComponent(EntityIdComponent.class) != null) {
                return entity;
            }
        }
        throw new AssertionError("demo session has no persistent entity");
    }
}
