package com.spacesim.campaign;

import com.spacesim.components.FactionComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.persistence.Stage20FreightRuntimeMaterializer.ReserveLoadoutPolicy;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.world.Stage21HPlayerMissionAuthority;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
class GeneratedCampaignInitialStationLegalCapitalIntegrationTest {
    @Test void explicitFreshManifestPreservesCapitalAndLegacyOwnerAbsence() throws Exception {
        var baseline = Stage228CampaignAuthority.create(1, List.of(), ReserveLoadoutPolicy.BASELINE);
        var current = Stage228CampaignAuthority.create(1, List.of(), ReserveLoadoutPolicy.BASELINE,
                Stage228CampaignAuthority.StationLegalCapitalPolicy.CIVILIAN_PROCESSING_CAPITAL);
        var old = baseline.coordinator().runtime(); var runtime = current.coordinator().runtime();
        var plan = GeneratedCampaignInitialStationLegalCapital.plan(old,
                Stage228CampaignAuthority.StationLegalCapitalPolicy.CIVILIAN_PROCESSING_CAPITAL);
        assertFalse(plan.assignments().isEmpty(), "Seed 1 must expose existing eligible civilian assets");
        assertEquals(0, baseline.initialCivilianStationAssociationCount());
        assertEquals(plan.assignments().size(), current.initialCivilianStationAssociationCount());
        assertTrue(plan.assignments().size() <= current.coordinator().actors().capture().size() * 2);
        assertEquals(old.captureState().campaign().materializedWorld(), runtime.captureState().campaign().materializedWorld());
        assertEquals(old.captureState().campaign().industrialState(), runtime.captureState().campaign().industrialState());
        assertEquals(old.freight().capture(), runtime.freight().capture());
        assertEquals(old.world().snapshot(), runtime.world().snapshot(), "Legal plan cannot create economic or territorial capital");
        for (var archive : old.discoveryState().knowledgeStates()) {
            var actual = runtime.discoveryState().knowledgeFor(archive.ownerId());
            for (var prior : archive.entries()) assertEquals(prior, actual.knowledge(prior.object()).orElseThrow());
            var additions = actual.entries().stream().filter(k -> archive.knowledge(k.object()).isEmpty()).toList();
            assertEquals(plan.assignments().entrySet().stream().filter(e -> e.getValue().equals(archive.ownerId())).count(), additions.size());
            for (var addition : additions) {
                assertEquals(archive.ownerId(), plan.assignments().get(addition.object().objectId()));
                assertEquals(com.spacesim.world.Stage20DiscoveryKnowledgeState.ResourceKnowledge.none(), addition.resourceKnowledge());
                assertTrue(addition.evidence().stream().allMatch(e -> e.provenanceId().startsWith(GeneratedCampaignInitialStationLegalCapital.VERSION)));
            }
        }
        assertEquals(old.discoveryState().knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID),
                runtime.discoveryState().knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID));
        var beforeArchives = runtime.discoveryState();
        runtime.authorInitialStationOwnershipKnowledge(plan.assignments(), GeneratedCampaignInitialStationLegalCapital.VERSION);
        assertEquals(beforeArchives, runtime.discoveryState(), "Owner report must be idempotent");
        var beforePreview = current.captureState(); var preview = current.previewIndependentPilotStart();
        assertExact(current);
        assertTrue(preview.allowed()); assertEquals(beforePreview, current.captureState());
        baseline.submitIndependentPilotStart(baseline.previewIndependentPilotStart());
        current.submitIndependentPilotStart(preview);
        assertEquals(old.discoveryState().knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID),
                runtime.discoveryState().knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID),
                "Initial NPC legal capital cannot extend the pilot's seller briefing");
        for (var entry : plan.assignments().entrySet()) {
            var ref = current.pilotMarketReference(entry.getKey()).orElseThrow();
            var entity = runtime.world().findSession(ref.systemId()).orElseThrow().getEntityRegistry().require(ref.entityId());
            assertEquals(entry.getValue(), runtime.world().findFactionStableId(entity.getComponent(FactionComponent.class).factionId).orElseThrow());
            var oldRef = baseline.pilotMarketReference(entry.getKey()).orElseThrow();
            var oldEntity = old.world().findSession(oldRef.systemId()).orElseThrow().getEntityRegistry().require(oldRef.entityId());
            assertNull(oldEntity.getComponent(FactionComponent.class));
            assertEquals(oldEntity.getComponent(WalletComponent.class).getBalanceMilliCredits(), entity.getComponent(WalletComponent.class).getBalanceMilliCredits());
            assertEquals(old.infrastructure().endpoint(entry.getKey()).storage().snapshot(), runtime.infrastructure().endpoint(entry.getKey()).storage().snapshot());
            var npc = current.coordinator().npcMissions().npcs().stream().filter(n -> n.factionContentId().equals(entry.getValue())
                    && n.knowledge().stream().anyMatch(k -> k.factId().equals(n.npcId() + ":posting") && k.subjectId().equals(entry.getKey()))).findFirst();
            assertTrue(npc.isPresent(), "Assigned legal owner must have its archived local dispatcher");
        }
        for (var actor : current.coordinator().actors().capture())
            assertEquals(old.world().findFactionEconomicState(actor.factionContentId()), runtime.world().findFactionEconomicState(actor.factionContentId()));
        assertExact(current); assertExact(baseline);
        var historical = Stage228CampaignAuthority.restore(baseline.captureState());
        for (var station : plan.assignments().keySet()) {
            var ref = historical.pilotMarketReference(station).orElseThrow();
            assertNull(historical.coordinator().runtime().world().findSession(ref.systemId()).orElseThrow()
                    .getEntityRegistry().require(ref.entityId()).getComponent(FactionComponent.class), "Restore cannot assign an absent seller");
        }
        assertFalse(historical.canStartIndependentPilot());

        // An explicit capital association survives a later territorial fallback; this resolver
        // fixture supplies a conflicting controller without running a territorial campaign.
        var controlled = old.infrastructure().endpoints().stream().filter(e -> !e.generatedIndustrial()
                && old.world().controllingFaction(e.systemId()).isPresent()).findFirst().orElseThrow();
        String other = current.coordinator().actors().capture().stream().map(a -> a.factionContentId())
                .filter(id -> !id.equals(old.world().controllingFaction(controlled.systemId()).orElseThrow())).findFirst().orElseThrow();
        assertEquals(Optional.of(other), GeneratedCampaignInitialStationLegalCapital.owner(old, controlled,
                new GeneratedCampaignInitialStationLegalCapital.Plan(Map.of(controlled.stationId(), other), List.of())));

        // Explicit funding and physical berth isolate the purchase boundary. Seller and asset
        // are entirely production-authored; no legal component is added by this fixture.
        var entry = plan.assignments().entrySet().iterator().next();
        var endpoint = runtime.infrastructure().endpoint(entry.getKey());
        var p = current.playerState().orElseThrow(); long funded = 500_000_000L;
        runtime.world().findSession(runtime.world().getActiveSystemId()).orElseThrow().getLedger()
                .recordMoneySource("PLAYER", funded - p.walletMilliCredits(), "explicit-initial-capital-purchase-test");
        var field = Stage228CampaignAuthority.class.getDeclaredField("playerState"); field.setAccessible(true);
        field.set(current, new com.spacesim.player.PlayerState(funded, p.factionContentId(), p.reputations(), p.ownedFleetIds(),
                p.activeFleetId(), p.discoveredSystemIds(), p.discoveredObjects(), p.homeSystemId(), p.dockedAt(),
                p.fleetOrders(), p.threatIntel(), p.ownedConstructionProjectIds(), p.ownedStations()));
        var fleet = runtime.world().findFleet(p.activeFleetId()).orElseThrow();
        if (!fleet.systemId().equals(endpoint.systemId())) {
            runtime.world().beginFleetTransfer(fleet.id(), endpoint.systemId()); runtime.world().completeFleetTransfer(fleet.id(), 0, 0);
            fleet = runtime.world().findFleet(fleet.id()).orElseThrow();
        }
        var local = runtime.arrival().materialization(fleet.systemId());
        if (local.physicalState(fleet.localEntityId()).isEmpty()) local.registerPhysicalState(fleet.localEntityId(),
                com.spacesim.world.LocalPhysicalKinematics.stationary(endpoint.position()));
        local.updatePhysicalState(fleet.localEntityId(), com.spacesim.world.LocalPhysicalKinematics.stationary(endpoint.position()));
        current.coordinator().setPaused(false); current.advanceFrame(current.coordinator().session().fixedStepSeconds()); current.coordinator().setPaused(true);
        assertThrows(IllegalStateException.class, () -> runtime.authorInitialStationOwnershipKnowledge(plan.assignments(), GeneratedCampaignInitialStationLegalCapital.VERSION));
        current.submitPilotAction(current.previewPilotAction("DOCK", entry.getKey(), "", 0));
        long price = current.stationSalePriceMilliCredits(entry.getKey()); assertTrue(price > 0);
        long treasury = runtime.world().findFactionEconomicState(entry.getValue()).orElseThrow().treasuryMilliCredits();
        var beforePurchase = current.captureState();
        var purchase = current.previewPilotAction("PURCHASE_STATION", entry.getKey(), "", 0);
        assertTrue(purchase.allowed()); assertEquals(beforePurchase, current.captureState()); current.submitPilotAction(purchase);
        assertEquals(funded - price, current.playerState().orElseThrow().walletMilliCredits());
        assertEquals(treasury + price, runtime.world().findFactionEconomicState(entry.getValue()).orElseThrow().treasuryMilliCredits());
        assertTrue(current.ownsProductionStation(entry.getKey())); assertExact(current);
    }

    private static void assertExact(Stage228CampaignAuthority campaign) throws Exception {
        var saved = campaign.captureState();
        assertEquals(saved, Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(saved))).captureState());
    }
}
