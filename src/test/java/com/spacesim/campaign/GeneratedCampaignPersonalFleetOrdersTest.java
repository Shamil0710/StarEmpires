package com.spacesim.campaign;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.persistence.*;
import com.spacesim.player.*;
import com.spacesim.world.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("slow")
class GeneratedCampaignPersonalFleetOrdersTest {
    @Test void independentPersonalOrderIsPureSingleUseAndCannotCommandForeignOrActiveAssets() {
        var c=twoShips();var id=inactive(c);var before=c.captureState();
        var model=new com.spacesim.ui.GeneratedWorldUiModel(1,c.coordinator().runtime(),c.coordinator().content());
        var rows=com.spacesim.ui.GeneratedCampaignFleetUi.rows(c,model.capture());assertFalse(rows.isEmpty());
        assertTrue(rows.stream().allMatch(row->row.selection().stableId().split("\\|",-1)[2].equals(Long.toString(id.value()))));
        assertEquals(before,c.captureState());
        var preview=c.previewPlayerFleetOrder(PlayerFleetOrderState.hold(id));assertTrue(preview.allowed());assertEquals(before,c.captureState());
        var adopted=c.submitPlayerFactionCommand(preview);assertEquals(before,c.captureState());
        assertNull(adopted.playerState().orElseThrow().factionContentId());
        assertEquals(PlayerFleetOrderState.hold(id),adopted.playerState().orElseThrow().fleetOrders().get(0));
        assertThrows(IllegalStateException.class,()->c.submitPlayerFactionCommand(preview));roundtrip(adopted);
        assertFalse(c.previewPlayerFleetOrder(PlayerFleetOrderState.hold(c.playerState().orElseThrow().activeFleetId())).allowed());
        var foreign=c.coordinator().runtime().freight().capture().freighters().stream().filter(f->!c.playerState().orElseThrow().ownedFleetIds().contains(f.fleetId())).findFirst().orElseThrow();
        assertFalse(c.previewPlayerFleetOrder(PlayerFleetOrderState.hold(foreign.fleetId())).allowed());
        assertFalse(c.previewPlayerFleetOrder(PlayerFleetOrderState.follow(id,foreign.fleetId())).allowed());
        assertFalse(c.previewPlayerFleetOrder(PlayerFleetOrderState.move(id,c.playerState().orElseThrow().homeSystemId(),123,456)).allowed());
        assertEquals(before,c.captureState());
    }

    @Test void holdBrakesOnlyInactiveHullWithActualFuelAndOneCompletedCampaignClock() {
        var c=twoShips();var id=inactive(c);var r=c.coordinator().runtime();var fleet=r.world().findFleet(id).orElseThrow();
        var material=r.arrival().materialization(fleet.systemId());var old=material.physicalState(fleet.localEntityId()).orElseThrow();
        material.updatePhysicalState(fleet.localEntityId(),new LocalPhysicalKinematics(old.position(),100,0));
        c=c.submitPlayerFactionCommand(c.previewPlayerFleetOrder(PlayerFleetOrderState.hold(id)));r=c.coordinator().runtime();fleet=r.world().findFleet(id).orElseThrow();
        var entity=r.world().findSession(fleet.systemId()).orElseThrow().getEntityRegistry().require(fleet.localEntityId());
        var fit=entity.getComponent(EngineeringComponent.class);double fuel=fuel(fit);long tick=r.world().getAuthoritativeWorldTick();
        var before=r.arrival().materialization(fleet.systemId()).physicalState(fleet.localEntityId()).orElseThrow();
        c.advanceFrame(.01f);assertEquals(tick,r.world().getAuthoritativeWorldTick());
        assertEquals(before,r.arrival().materialization(fleet.systemId()).physicalState(fleet.localEntityId()).orElseThrow());
        assertEquals(fuel,fuel(fit));
        c.advanceFrame(.1f);assertEquals(tick+1,r.world().getAuthoritativeWorldTick());
        var after=r.arrival().materialization(fleet.systemId()).physicalState(fleet.localEntityId()).orElseThrow();
        assertTrue(after.velocityXMps()<100&&after.velocityXMps()>0);assertTrue(after.position().distanceTo(old.position())>0);
        assertTrue(fuel(fit)<fuel);roundtrip(c);
    }

    @Test void followAndEscortUseExactSeparationFinitePropulsionAndDurableIntentAfterReload() {
        var c=twoShips();var id=inactive(c);var target=c.playerState().orElseThrow().activeFleetId();var r=c.coordinator().runtime();
        var follower=r.world().findFleet(id).orElseThrow();var leader=r.world().findFleet(target).orElseThrow();var material=r.arrival().materialization(follower.systemId());
        var targetPhysical=material.physicalState(leader.localEntityId()).orElseThrow();
        material.updatePhysicalState(follower.localEntityId(),LocalPhysicalKinematics.stationary(targetPhysical.position().translated(2000,0)));
        c=c.submitPlayerFactionCommand(c.previewPlayerFleetOrder(PlayerFleetOrderState.follow(id,target)));
        var before=c.captureState();var preview=c.previewPlayerFleetOrder(PlayerFleetOrderState.escort(id,target));assertTrue(preview.allowed());assertEquals(before,c.captureState());
        for(int i=0;i<20;i++)c.advanceFrame(.1f);
        var after=c.coordinator().runtime().arrival().materialization(follower.systemId()).physicalState(follower.localEntityId()).orElseThrow();
        assertTrue(after.position().distanceTo(targetPhysical.position())<2000);assertTrue(after.velocityXMps()<0);
        c=roundtrip(c);assertEquals(FleetOrderType.FOLLOW,c.playerState().orElseThrow().fleetOrders().get(0).type());
        c=c.submitPlayerFactionCommand(c.previewPlayerFleetOrder(PlayerFleetOrderState.escort(id,target)));assertEquals(FleetOrderType.ESCORT,c.playerState().orElseThrow().fleetOrders().get(0).type());roundtrip(c);
    }

    @Test void moveUsesPersonallyDiscoveredRouteAndOrdinaryTransitReloadWithoutTeleportOrCargoGrant() {
        var c=twoShips();var id=inactive(c);var home=c.playerState().orElseThrow().homeSystemId();var destination=c.coordinator().runtime().world().getTopology().neighbors(home).get(0);
        assertFalse(c.previewPlayerFleetOrder(PlayerFleetOrderState.move(id,destination,LocalSystemCoordinates.ARRIVAL_X,LocalSystemCoordinates.ARRIVAL_Y)).allowed());
        c.submitPilotAction(c.previewPilotAction("UNDOCK","","",0));departureFixture(c,c.playerState().orElseThrow().activeFleetId(),destination);
        c.submitPilotAction(c.previewPilotAction("JUMP",Long.toString(destination.value()),"",0));awaitArrival(c,c.playerState().orElseThrow().activeFleetId(),destination);
        assertTrue(c.playerState().orElseThrow().discoveredSystemIds().contains(destination));
        departureFixture(c,id,destination);var old=c.coordinator().runtime().captureState().freight().freighters().stream().filter(f->f.fleetId().equals(id)).findFirst().orElseThrow();
        c=c.submitPlayerFactionCommand(c.previewPlayerFleetOrder(PlayerFleetOrderState.move(id,destination,LocalSystemCoordinates.ARRIVAL_X,LocalSystemCoordinates.ARRIVAL_Y)));
        c.advanceFrame(.1f);assertTrue(c.coordinator().runtime().world().findFleetJump(id).isPresent());
        boolean transit=false;for(int i=0;i<2000;i++){c.advanceFrame(.25f);if(c.coordinator().runtime().world().findFleetJump(id).filter(j->j.phase()==FleetJumpPhase.IN_TRANSIT).isPresent()){transit=true;break;}}
        assertTrue(transit);c=roundtrip(c);awaitArrival(c,id,destination);
        var now=c.coordinator().runtime().freight().findFreighter(id).orElseThrow();assertEquals(old.cargoStorage(),now.cargoStorage());assertEquals(old.stableFactionId(),now.stableFactionId());
        assertEquals(FleetOrderType.MOVE,c.playerState().orElseThrow().fleetOrders().get(0).type());roundtrip(c);
        var atDestination=c.coordinator().runtime().world().findFleet(id).orElseThrow();
        var fitted=c.coordinator().runtime().world().findSession(destination).orElseThrow().getEntityRegistry().require(atDestination.localEntityId()).getComponent(EngineeringComponent.class);
        double cooldown=fitted.runtimeState.ftlCooldownSecondsByMount().values().stream().mapToDouble(Double::doubleValue).max().orElseThrow();
        assertTrue(cooldown>0);long tick=c.coordinator().runtime().world().getAuthoritativeWorldTick();
        c.advanceFrame(.1f);
        double elapsed=(c.coordinator().runtime().world().getAuthoritativeWorldTick()-tick)*(double)c.coordinator().session().fixedStepSeconds();
        assertEquals(Math.max(0,cooldown-elapsed),fitted.runtimeState.ftlCooldownSecondsByMount().values().stream().mapToDouble(Double::doubleValue).max().orElseThrow(),1e-9,"One common engineering interval, without doubled cooldown recovery");
        departureFixture(c,id,home);
        c=c.submitPlayerFactionCommand(c.previewPlayerFleetOrder(PlayerFleetOrderState.patrol(id,java.util.List.of(home,destination))));
        c.advanceFrame(.1f);assertTrue(c.coordinator().runtime().world().findFleetJump(id).isEmpty(),"Real FTL cooldown must delay the patrol");
        for(int i=0;i<2000&&c.coordinator().runtime().world().findFleetJump(id).isEmpty();i++)c.advanceFrame(.25f);
        assertEquals(home,c.coordinator().runtime().world().findFleetJump(id).orElseThrow().destinationSystemId());
        c=roundtrip(c);assertEquals(FleetOrderType.PATROL,c.playerState().orElseThrow().fleetOrders().get(0).type());
    }

    @Test void patrolRequiresKnownSystemsAndDepletedPropulsionCannotInventAStopOrAJump() {
        var c=twoShips();var id=inactive(c);var home=c.playerState().orElseThrow().homeSystemId();var neighbor=c.coordinator().runtime().world().getTopology().neighbors(home).get(0);
        assertFalse(c.previewPlayerFleetOrder(PlayerFleetOrderState.patrol(id,java.util.List.of(home,neighbor))).allowed());
        assertFalse(c.previewPlayerFleetOrder(PlayerFleetOrderState.hold(new FleetId(99999))).allowed());
        var r=c.coordinator().runtime();var fleet=r.world().findFleet(id).orElseThrow();var entity=r.world().findSession(home).orElseThrow().getEntityRegistry().require(fleet.localEntityId());var fit=entity.getComponent(EngineeringComponent.class);
        // Explicit depleted-store fixture; ordinary propulsion must coast rather than grant counter-thrust.
        var old=fit.runtimeState;var stores=old.consumables();var loads=stores.interfaceLoads().stream().map(v->v.kind()==com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind.REACTION_MASS?new com.spacesim.ship.ShipEngineeringState.ConsumableLoad(v.mountId(),v.interfaceId(),v.kind(),0,0,v.itemCount()):v).toList();
        fit.setRuntimeState(new com.spacesim.ship.ShipEngineeringRuntime.RuntimeState(new com.spacesim.ship.ShipEngineeringState.ConsumableState(stores.cargoMassKg(),stores.storesMassKg(),stores.missionPayloadMassKg(),stores.missionIntegrationVolumeM3(),loads),old.sharedBusEnergyJ(),old.shipHeatStoredJ(),old.localHeatJByMount(),old.thrustLimitNByMount(),old.coolantBusCapacityW(),old.ftlCooldownSecondsByMount()));
        var material=r.arrival().materialization(home);var physical=material.physicalState(fleet.localEntityId()).orElseThrow();material.updatePhysicalState(fleet.localEntityId(),new LocalPhysicalKinematics(physical.position(),100,0));
        c.advanceFrame(.1f);var after=material.physicalState(fleet.localEntityId()).orElseThrow();assertEquals(100d,after.velocityXMps());assertEquals(100d*c.coordinator().session().fixedStepSeconds(),physical.position().distanceTo(after.position()),1e-7);assertTrue(r.world().findFleetJump(id).isEmpty());roundtrip(c);
    }

    private static Stage228CampaignAuthority twoShips() {
        return Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(TwoShips.BYTES));
    }

    private static final class TwoShips {
        private static final byte[] BYTES = Stage228GeneratedCampaignPersistenceCodec.encode(createTwoShips().captureState());
    }

    private static Stage228CampaignAuthority createTwoShips(){var c=Stage228CampaignAuthority.create(1L);c.submitIndependentPilotStart(c.previewIndependentPilotStart());var r=c.coordinator().runtime();var p=r.world().findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();var origin=r.freight().findFreighter(p.fleetId()).orElseThrow().stableFactionId();var endpoint=r.infrastructure().endpoints().stream().filter(e->e.systemId().equals(p.systemId())&&r.world().findSession(p.systemId()).orElseThrow().getEntityRegistry().require(c.pilotMarketReference(e.stationId()).orElseThrow().entityId()).getComponent(com.spacesim.components.FactionComponent.class).factionId==r.world().findFactionRuntimeId(origin).orElseThrow()).findFirst().orElseThrow();
        // Explicit docking geometry; purchase is ordinary conserved seller authority.
        r.arrival().materialization(p.systemId()).updatePhysicalState(p.localEntityId(),LocalPhysicalKinematics.stationary(endpoint.position()));c.submitPilotAction(c.previewPilotAction("DOCK",endpoint.stationId(),"",0));
        var offer=r.freight().capture().freighters().stream().filter(f->!f.fleetId().equals(p.fleetId())&&f.stableFactionId().equals(origin)&&f.currentSystemId().equals(p.systemId())&&f.phase()==Stage20FreightPersistentState.FreightPhase.IDLE).findFirst().orElseThrow();c.submitPilotAction(c.previewPilotAction("PURCHASE",Long.toString(offer.fleetId().value()),"",0));return c;}
    private static FleetId inactive(Stage228CampaignAuthority c){var p=c.playerState().orElseThrow();return p.ownedFleetIds().stream().filter(id->!id.equals(p.activeFleetId())).findFirst().orElseThrow();}
    private static double fuel(EngineeringComponent f){return f.runtimeState.consumables().interfaceLoads().stream().mapToDouble(v->v.amount()).sum();}
    private static void departureFixture(Stage228CampaignAuthority c,FleetId id,StarSystemId to){var r=c.coordinator().runtime();var p=r.world().findFleet(id).orElseThrow();r.arrival().materialization(p.systemId()).updatePhysicalState(p.localEntityId(),LocalPhysicalKinematics.stationary(r.arrival().resolve(to,p.systemId()).physicalState().position()));}
    private static void awaitArrival(Stage228CampaignAuthority c,FleetId id,StarSystemId to){for(int i=0;i<2000&&(c.coordinator().runtime().world().findFleetJump(id).isPresent()||!c.coordinator().runtime().world().findFleet(id).orElseThrow().systemId().equals(to));i++)c.advanceFrame(.25f);assertEquals(to,c.coordinator().runtime().world().findFleet(id).orElseThrow().systemId());assertTrue(c.coordinator().runtime().world().findFleetJump(id).isEmpty());}
    private static Stage228CampaignAuthority roundtrip(Stage228CampaignAuthority c){var state=c.captureState();var r=Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(Stage228GeneratedCampaignPersistenceCodec.encode(state)));assertEquals(state,r.captureState());return r;}
}
