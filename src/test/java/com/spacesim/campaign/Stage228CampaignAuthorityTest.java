package com.spacesim.campaign;

import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;
import com.spacesim.persistence.Stage228FlightDeckPersistentState;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistentState;
import com.spacesim.persistence.Stage228HangarPersistentState;
import com.spacesim.world.ProductionSmallCraftFixture;
import com.spacesim.world.SmallCraftFlightDeckOperations.OperationKind;
import com.spacesim.world.SmallCraftFlightDeckOperations.OperationPhase;
import com.spacesim.world.SmallCraftHangarCapacity.BayDefinition;
import com.spacesim.world.SmallCraftHangarCapacity.BayId;
import com.spacesim.world.SmallCraftHangarCapacity.HostKind;
import com.spacesim.world.SmallCraftHangarCapacity.OccupancyState;
import com.spacesim.world.SmallCraftId;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage228CampaignAuthorityTest {

    @Test
    void newCampaignAndStage21AdoptionNeverSeedFreeSmallCraft() {
        Stage228CampaignAuthority created = Stage228CampaignAuthority.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        assertEquals(0, created.smallCraft().size());
        assertEquals(1L, created.smallCraft().nextIdValue());
        assertEquals(0, created.hangars().size());

        Stage228CampaignAuthority adopted = Stage228CampaignAuthority.restoreStage21(
                created.coordinator().captureState());
        assertEquals(0, adopted.smallCraft().size());
        assertEquals(1L, adopted.smallCraft().nextIdValue());
        assertTrue(adopted.captureState().smallCraft().craft().isEmpty());
        assertTrue(adopted.captureState().hangars().assignments().isEmpty());
        assertTrue(adopted.captureState().flightDeck().profiles().isEmpty());
        assertTrue(adopted.captureState().flightDeck().queued().isEmpty());
        assertTrue(adopted.captureState().flightDeck().active().isEmpty());
    }

    @Test
    void campaignRoundTripPreservesIndividualPhysicalHangarOccupancy() {
        Stage228CampaignAuthority original = Stage228CampaignAuthority.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        SmallCraftId id = original.smallCraft().reserveIdentityForCompletedProduction();
        original.smallCraft().registerProducedCraft(ProductionSmallCraftFixture.craft(
                id, 8L, 80d, 4_000d, 1d, 100d));
        Stage228GeneratedCampaignPersistentState base = original.captureState();
        Stage228HangarPersistentState occupied = new Stage228HangarPersistentState(
                Stage228HangarPersistentState.CURRENT_VERSION,
                Stage228HangarPersistentState.CURRENT_RUNTIME_VERSION,
                Stage228HangarPersistentState.CURRENT_SEMANTIC_CONTRACT,
                java.util.List.of(new Stage228HangarPersistentState.AssignmentState(
                        id,
                        "carrier:authority-test",
                        "mission_primary",
                        HostKind.SHIP,
                        OccupancyState.SERVICING)));
        Stage228GeneratedCampaignPersistentState saved =
                Stage228GeneratedCampaignPersistentState.compose(
                        base.stage21Runtime(), base.smallCraft(), occupied);

        Stage228CampaignAuthority restored = Stage228CampaignAuthority.restore(saved);

        assertEquals(saved, restored.captureState());
        assertEquals(id, restored.hangars().snapshot().get(0).craftId());
        assertEquals(OccupancyState.SERVICING,
                restored.hangars().find(id).orElseThrow().state());
    }

    @Test
    void campaignRoundTripPreservesAwaitingLaunchHandoffWithoutTeleportingCraft() {
        Stage228CampaignAuthority original = Stage228CampaignAuthority.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        SmallCraftId id = original.smallCraft().reserveIdentityForCompletedProduction();
        original.smallCraft().registerProducedCraft(ProductionSmallCraftFixture.craft(
                id, 8L, 80d, 4_000d, 1d, 100d));
        Stage228GeneratedCampaignPersistentState base = original.captureState();
        long currentTick = original.coordinator().runtime().world().getAuthoritativeWorldTick();
        Stage228HangarPersistentState occupied = new Stage228HangarPersistentState(
                Stage228HangarPersistentState.CURRENT_VERSION,
                Stage228HangarPersistentState.CURRENT_RUNTIME_VERSION,
                Stage228HangarPersistentState.CURRENT_SEMANTIC_CONTRACT,
                java.util.List.of(new Stage228HangarPersistentState.AssignmentState(
                        id,
                        "carrier:authority-launch",
                        "mission_primary",
                        HostKind.SHIP,
                        OccupancyState.LAUNCHING)));
        Stage228FlightDeckPersistentState flightDeck = new Stage228FlightDeckPersistentState(
                Stage228FlightDeckPersistentState.CURRENT_VERSION,
                Stage228FlightDeckPersistentState.CURRENT_RUNTIME_VERSION,
                Stage228FlightDeckPersistentState.CURRENT_SEMANTIC_CONTRACT,
                currentTick,
                java.util.List.of(new Stage228FlightDeckPersistentState.DeckProfileState(
                        "carrier:authority-launch",
                        "mission_primary",
                        3d,
                        4d)),
                java.util.List.of(),
                java.util.List.of(new Stage228FlightDeckPersistentState.ActiveState(
                        new Stage228FlightDeckPersistentState.RequestState(
                                id,
                                "carrier:authority-launch",
                                "mission_primary",
                                OperationKind.LAUNCH,
                                currentTick),
                        OperationPhase.AWAITING_HANDOFF,
                        0d,
                        null)));
        Stage228GeneratedCampaignPersistentState saved =
                Stage228GeneratedCampaignPersistentState.compose(
                        base.stage21Runtime(), base.smallCraft(), occupied, flightDeck);

        Stage228CampaignAuthority restored = Stage228CampaignAuthority.restore(saved);

        assertEquals(saved, restored.captureState());
        assertEquals(OccupancyState.LAUNCHING,
                restored.hangars().find(id).orElseThrow().state());
        assertEquals(OperationPhase.AWAITING_HANDOFF,
                restored.flightDeck().activeFor(id).orElseThrow().phase());
    }

    @Test
    void restoreRejectsFlightDeckWatermarkAheadOfCampaignTime() {
        Stage228CampaignAuthority original = Stage228CampaignAuthority.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        Stage228GeneratedCampaignPersistentState base = original.captureState();
        long currentTick = original.coordinator().runtime().world().getAuthoritativeWorldTick();
        Stage228FlightDeckPersistentState futureDeck =
                new Stage228FlightDeckPersistentState(
                        Stage228FlightDeckPersistentState.CURRENT_VERSION,
                        Stage228FlightDeckPersistentState.CURRENT_RUNTIME_VERSION,
                        Stage228FlightDeckPersistentState.CURRENT_SEMANTIC_CONTRACT,
                        currentTick + 1L,
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of());

        assertThrows(IllegalArgumentException.class, () ->
                Stage228CampaignAuthority.restore(
                        Stage228GeneratedCampaignPersistentState.compose(
                                base.stage21Runtime(),
                                base.smallCraft(),
                                base.hangars(),
                                futureDeck)));
    }

    @Test
    void flightDeckAdvancesOnlyOnExistingCampaignFixedTick() {
        Stage228CampaignAuthority seed = Stage228CampaignAuthority.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        SmallCraftId id = seed.smallCraft().reserveIdentityForCompletedProduction();
        seed.smallCraft().registerProducedCraft(ProductionSmallCraftFixture.craft(
                id, 8L, 80d, 4_000d, 1d, 100d));
        Stage228GeneratedCampaignPersistentState base = seed.captureState();
        long currentTick = seed.coordinator().runtime().world().getAuthoritativeWorldTick();
        long requestTick = currentTick + 1L;
        BayId bayId = new BayId("carrier:tick-test", "mission_primary");
        Stage228HangarPersistentState occupied = new Stage228HangarPersistentState(
                Stage228HangarPersistentState.CURRENT_VERSION,
                Stage228HangarPersistentState.CURRENT_RUNTIME_VERSION,
                Stage228HangarPersistentState.CURRENT_SEMANTIC_CONTRACT,
                java.util.List.of(new Stage228HangarPersistentState.AssignmentState(
                        id,
                        bayId.hostStableId(),
                        bayId.bayStableId(),
                        HostKind.SHIP,
                        OccupancyState.READY)));
        Stage228FlightDeckPersistentState flightDeck = new Stage228FlightDeckPersistentState(
                Stage228FlightDeckPersistentState.CURRENT_VERSION,
                Stage228FlightDeckPersistentState.CURRENT_RUNTIME_VERSION,
                Stage228FlightDeckPersistentState.CURRENT_SEMANTIC_CONTRACT,
                currentTick,
                java.util.List.of(new Stage228FlightDeckPersistentState.DeckProfileState(
                        bayId.hostStableId(),
                        bayId.bayStableId(),
                        100d,
                        100d)),
                java.util.List.of(new Stage228FlightDeckPersistentState.RequestState(
                        id,
                        bayId.hostStableId(),
                        bayId.bayStableId(),
                        OperationKind.LAUNCH,
                        requestTick)),
                java.util.List.of());
        Stage228CampaignAuthority authority = Stage228CampaignAuthority.restore(
                Stage228GeneratedCampaignPersistentState.compose(
                        base.stage21Runtime(), base.smallCraft(), occupied, flightDeck));
        float fixedStep = authority.coordinator().session().fixedStepSeconds();
        BayDefinition bay = new BayDefinition(
                bayId,
                HostKind.SHIP,
                new Dimensions3d(1_000d, 1_000d, 1_000d),
                1_000_000_000d,
                1_000_000_000d,
                1d);

        GeneratedCampaignSession.AdvanceReport report = authority.advanceFrame(
                fixedStep,
                tick -> java.util.Map.of(bayId, bay));

        assertEquals(1L, report.fixedTicks());
        assertEquals(requestTick, authority.flightDeck().lastProcessedTick());
        assertEquals(OccupancyState.LAUNCHING,
                authority.hangars().find(id).orElseThrow().state());
        assertEquals(OperationPhase.CYCLING,
                authority.flightDeck().activeFor(id).orElseThrow().phase());
        assertTrue(authority.flightDeck().activeFor(id).orElseThrow()
                .remainingWorkSeconds() < 100d);
    }

    @Test
    void currentEnvelopeRoundTripPreservesAcceptedStage21AndEmptySidecarExactly() {
        Stage228CampaignAuthority original = Stage228CampaignAuthority.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        Stage228GeneratedCampaignPersistentState saved = original.captureState();
        Stage228CampaignAuthority restored = Stage228CampaignAuthority.restore(saved);

        assertEquals(saved, restored.captureState());
        assertEquals(original.coordinator().rootSeed(), restored.coordinator().rootSeed());
        assertEquals(0, restored.smallCraft().size());
        assertEquals(0, restored.hangars().size());
        assertTrue(restored.flightDeck().profiles().isEmpty());
        assertTrue(restored.flightDeck().queued().isEmpty());
        assertTrue(restored.flightDeck().active().isEmpty());
    }
}
