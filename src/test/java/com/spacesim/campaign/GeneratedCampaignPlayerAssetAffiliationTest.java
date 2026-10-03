package com.spacesim.campaign;

import com.spacesim.components.FactionComponent;
import com.spacesim.persistence.*;
import com.spacesim.player.*;
import com.spacesim.world.*;
import org.junit.jupiter.api.Test;
import java.util.zip.GZIPInputStream;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("slow")
class GeneratedCampaignPlayerAssetAffiliationTest {
    private static final String OWN="faction.player", WATER="commodity.material.purified_water";

    @Test void explicitLocalAffiliationIsPureAndKeepsBootstrapOriginCargoIdsAndAllResources() {
        var c=founded();buyWater(c);var before=c.captureState();var player=c.playerState();
        var old=c.coordinator().runtime().captureState().freight().freighters().stream()
                .filter(f->f.fleetId().equals(player.orElseThrow().activeFleetId())).findFirst().orElseThrow();
        var preview=c.previewPlayerAssetAffiliation();assertTrue(preview.allowed());assertEquals(before,c.captureState());
        var adopted=c.submitPlayerFactionCommand(preview);assertEquals(before,c.captureState());
        assertEquals(player,adopted.playerState());
        var now=adopted.coordinator().runtime().freight().findFreighter(old.fleetId()).orElseThrow();
        assertEquals(old.stableFactionId(),now.stableFactionId());assertEquals(old.ownershipOrdinal(),now.ownershipOrdinal());
        assertEquals(OWN,now.legalFactionId());assertEquals(old.cargoStorage(),now.cargoStorage());
        assertEquals(old.physicalState(),now.physicalState());assertEquals(old.currentSystemId(),now.currentSystemId());
        assertEquals(old.hullId(),now.hullId());assertEquals(old.fitId(),now.fitId());
        assertEquals(before.smallCraft(),adopted.captureState().smallCraft());assertEquals(before.hangars(),adopted.captureState().hangars());
        var placement=adopted.coordinator().runtime().world().findFleet(old.fleetId()).orElseThrow();
        int actual=adopted.coordinator().runtime().world().findSession(placement.systemId()).orElseThrow().getEntityRegistry().require(placement.localEntityId()).getComponent(FactionComponent.class).factionId;
        assertEquals(adopted.coordinator().runtime().world().findFactionRuntimeId(OWN).orElseThrow(),actual);
        assertThrows(IllegalStateException.class,()->c.submitPlayerFactionCommand(preview));roundtrip(adopted);
    }

    @Test void genuineV2FreightAdoptsOnlyTheOriginalLegalMirrorWithoutTouchingInputOrResources() throws Exception {
        byte[] old;try(var in=new GZIPInputStream(getClass().getResourceAsStream("/campaign/stage23b-freight-v2.s20f.gz"))){old=in.readAllBytes();}
        assertEquals(2,java.nio.ByteBuffer.wrap(old).getInt(8));var bytes=old.clone();
        var adopted=Stage20FreightPersistenceCodec.decode(old);assertEquals(3,adopted.schemaVersion());
        assertTrue(adopted.freighters().stream().allMatch(f->f.legalFactionId().equals(f.stableFactionId())));
        assertArrayEquals(bytes,old);assertEquals(adopted,Stage20FreightPersistenceCodec.decode(Stage20FreightPersistenceCodec.encode(adopted)));
        var fresh=Stage228CampaignAuthority.create(1).coordinator().runtime().freight().capture();assertEquals(fresh,adopted);
        var f=adopted.freighters().get(0);var fake=new Stage20FreightPersistentState.FreighterState(f.fleetId(),f.stableFactionId(),f.ownershipOrdinal(),f.hullId(),f.fitId(),f.cargoCapacityKg(),f.currentSystemId(),f.physicalState(),f.phase(),f.activeOrderId(),f.routeIndex(),f.cargoStorage(),OWN);
        var fleets=new java.util.ArrayList<>(adopted.freighters());fleets.set(0,fake);
        assertThrows(IllegalArgumentException.class,()->new Stage20FreightPersistentState(2,adopted.rootSeed(),adopted.generatorVersion(),adopted.worldFingerprint(),adopted.materializationVersion(),adopted.compatibilityAuthorityVersion(),adopted.nextFleetIdValue(),adopted.nextCargoLotOrdinal(),fleets,adopted.cargoLots(),adopted.orders()));
    }

    @Test void neitherWorldOnlyNorFreightOnlyAffiliationCanBeCapturedAsAConsistentSave() {
        var worldOnly=founded();var player=worldOnly.playerState().orElseThrow();
        var runtime=PlayerRuntime.attachToCampaign(worldOnly.coordinator().runtime().world(),worldOnly.coordinator().content(),player);
        new PlayerFactionManagementService(runtime).affiliateOwnedAssets();
        assertThrows(IllegalArgumentException.class,worldOnly::captureState);
        var freightOnly=founded();freightOnly.coordinator().runtime().freight().synchronizeLegalAffiliation(freightOnly.playerState().orElseThrow().activeFleetId(),OWN);
        assertThrows(IllegalArgumentException.class,freightOnly::captureState);
        var independent=Stage228CampaignAuthority.create(1);independent.submitIndependentPilotStart(independent.previewIndependentPilotStart());var before=independent.captureState();
        assertFalse(independent.previewPlayerAssetAffiliation().allowed());assertEquals(before,independent.captureState());
    }

    @Test void realTransitAffiliationReloadAndArrivalRetainCanonicalLegalOwnerAndOriginalCargo() {
        var c=founded();buyWater(c);c.submitPilotAction(c.previewPilotAction("UNDOCK","","",0));
        var id=c.playerState().orElseThrow().activeFleetId();var r=c.coordinator().runtime();var fleet=r.world().findFleet(id).orElseThrow();
        var original=r.freight().findFreighter(id).orElseThrow();var destination=r.world().getTopology().neighbors(fleet.systemId()).get(0);
        // Explicit departure geometry fixture, followed by the ordinary live jump FSM.
        r.arrival().materialization(fleet.systemId()).updatePhysicalState(fleet.localEntityId(),LocalPhysicalKinematics.stationary(r.arrival().resolve(destination,fleet.systemId()).physicalState().position()));
        c.submitPilotAction(c.previewPilotAction("JUMP",Long.toString(destination.value()),"",0));
        boolean transit=false;for(int i=0;i<2000;i++){c.advanceFrame(.25f);var jump=c.coordinator().runtime().world().findFleetJump(id).orElseThrow();if(jump.phase()==FleetJumpPhase.IN_TRANSIT){transit=true;break;}}
        assertTrue(transit);var before=c.captureState();var preview=c.previewPlayerAssetAffiliation();assertTrue(preview.allowed());assertEquals(before,c.captureState());
        c=c.submitPlayerFactionCommand(preview);c=roundtrip(c);
        assertEquals(OWN,c.coordinator().runtime().freight().findFreighter(id).orElseThrow().legalFactionId());
        for(int i=0;i<2000&&c.coordinator().runtime().world().findFleetJump(id).isPresent();i++)c.advanceFrame(.25f);
        assertEquals(destination,c.coordinator().runtime().world().findFleet(id).orElseThrow().systemId());
        var arrived=c.coordinator().runtime().freight().findFreighter(id).orElseThrow();assertEquals(OWN,arrived.legalFactionId());
        assertEquals(original.stableFactionId(),arrived.stableFactionId());assertEquals(original.cargoStorage(),arrived.cargoStorage());roundtrip(c);
    }
    @Test void destructionRetainsHistoricalRegistrationWithoutGrantingAReplacement() {
        var c=founded();c=c.submitPlayerFactionCommand(c.previewPlayerAssetAffiliation());
        var before=c.playerState().orElseThrow();
        c.coordinator().runtime().destroyLocalFreighter(before.activeFleetId(),DestructionPolicy.destroyAll());
        c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        var after=c.playerState().orElseThrow();
        assertTrue(after.ownedFleetIds().isEmpty());assertNull(after.activeFleetId());
        assertEquals(before.walletMilliCredits(),after.walletMilliCredits());
        var lost=c.coordinator().runtime().freight().findFreighter(before.activeFleetId()).orElseThrow();
        assertEquals(Stage20FreightPersistentState.FreightPhase.DESTROYED,lost.phase());
        assertEquals(OWN,lost.legalFactionId());
        var saved=roundtrip(c).captureState();
        assertThrows(IllegalArgumentException.class,()->Stage228GeneratedCampaignPersistentState.compose(saved.stage21Runtime(),saved.smallCraft(),saved.hangars(),saved.flightDeck(),saved.operations(),null));
    }

    @Test void explicitAffiliationCannotOutliveThePersistedPersonalOwnerEvenWithAnEmptyHold() {
        var c=founded();c=c.submitPlayerFactionCommand(c.previewPlayerAssetAffiliation());var saved=c.captureState();
        assertTrue(c.coordinator().runtime().freight().findFreighter(c.playerState().orElseThrow().activeFleetId()).orElseThrow().cargoStorage().commodityMassByIdKg().isEmpty());
        assertThrows(IllegalArgumentException.class,()->Stage228GeneratedCampaignPersistentState.compose(saved.stage21Runtime(),saved.smallCraft(),saved.hangars(),saved.flightDeck(),saved.operations(),null));
        roundtrip(c);
    }

    private static Stage228CampaignAuthority founded(){return FoundedCampaignFixture.restore();}
    private static void buyWater(Stage228CampaignAuthority c){var r=c.coordinator().runtime();var p=r.world().findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();var e=r.infrastructure().endpoints().stream().filter(v->v.systemId().equals(p.systemId())&&v.storage().commodityMassKg(WATER)>0).findFirst().orElseThrow();r.arrival().materialization(p.systemId()).updatePhysicalState(p.localEntityId(),LocalPhysicalKinematics.stationary(e.position()));c.submitPilotAction(c.previewPilotAction("DOCK",e.stationId(),"",0));c.advanceFrame(c.coordinator().session().fixedStepSeconds());c.submitPilotAction(c.previewPilotAction("BUY",e.stationId(),WATER,1));}
    private static Stage228CampaignAuthority roundtrip(Stage228CampaignAuthority c){var state=c.captureState();var r=Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(Stage228GeneratedCampaignPersistenceCodec.encode(state)));assertEquals(state,r.captureState());return r;}
}
