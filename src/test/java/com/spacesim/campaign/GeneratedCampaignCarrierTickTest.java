package com.spacesim.campaign;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.content.ship.Stage228SmallCraftEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage228SmallCraftProductionProjection;
import com.spacesim.persistence.*;
import com.spacesim.ship.*;
import com.spacesim.world.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
class GeneratedCampaignCarrierTickTest {
    @Test void ordinaryTicksUseLiveDamageAndRetainPhysicalHandoffAndReloadBoundary() throws Exception {
        var c = FoundedCampaignFixture.restore();
        var world = c.coordinator().runtime().world();
        var carrier = world.findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        var entity = world.findSession(carrier.systemId()).orElseThrow().getEntityRegistry().require(carrier.localEntityId());
        var originalEquipment = entity.getComponent(EngineeringComponent.class);
        var catalog = Stage228SmallCraftEngineeringCatalogLoader.loadDefault();
        var authored = catalog.getDemonstratorFits().stream().filter(f -> f.installedModules().stream().anyMatch(m ->
                catalog.findModule(m.moduleId()).family() == com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily.HANGAR_SMALL_CRAFT))
                .findFirst().orElseThrow();
        var hostFit = ShipEngineeringState.InstalledFit.fromDemonstrator(authored);
        var fitted = new EngineeringComponent(hostFit, new ShipEngineeringRuntime(catalog).initialize(hostFit, ShipEngineeringState.ConsumableState.empty()));
        // Explicit physical carrier/craft fixture. Ordinary acquisition and catalog admission remain separate gates.
        entity.add(fitted);
        var bay = new SmallCraftBayResolver(catalog).resolveShipBays(carrier.id().toString(), fitted.fit, fitted.instanceState).get(0);
        String faction = world.findFactionStableId(entity.getComponent(FactionComponent.class).factionId).orElseThrow();
        var design = catalog.findDemonstratorFit(Stage228SmallCraftProductionProjection.EMPIRE_INTERCEPTOR_FIT_ID);
        var fit = ShipEngineeringState.InstalledFit.fromDemonstrator(design);
        var id = c.smallCraft().reserveIdentityForCompletedProduction();
        c.smallCraft().registerProducedCraft(new SmallCraftState(id, faction, design.id(), fit,
                new ShipEngineeringRuntime(catalog).initialize(fit, ShipEngineeringState.ConsumableState.empty()), ShipInstanceRuntimeState.legacyNeutral()));
        var assign = SmallCraftHangarRegistry.class.getDeclaredMethod("assign", SmallCraftId.class,
                SmallCraftHangarCapacity.BayDefinition.class, SmallCraftHangarCapacity.OccupancyState.class);
        assign.setAccessible(true);
        assign.invoke(c.hangars(), id, bay, SmallCraftHangarCapacity.OccupancyState.READY);
        c.commitCarrierWings(List.of(new CarrierWingStrategicReadinessService.CarrierWingAssignment(
                carrier.id(), carrier.id().toString(), faction, List.of(id))));
        // No personal pilot is needed for NPC deck work; presentation permissions do not govern simulation.
        var playerField = Stage228CampaignAuthority.class.getDeclaredField("playerState");
        playerField.setAccessible(true); playerField.set(c, null);
        float step = c.coordinator().session().fixedStepSeconds();
        var deck = new SmallCraftFlightDeckOperations(c.hangars(), List.of(
                new SmallCraftFlightDeckOperations.DeckProfile(bay.id(), step * 2d, step * 2d)));
        deck.requestLaunch(id, bay.id(), world.getAuthoritativeWorldTick());
        var deckField = Stage228CampaignAuthority.class.getDeclaredField("flightDeck");
        deckField.setAccessible(true); deckField.set(c, deck);

        c.coordinator().setPaused(true);
        var queued = Stage228FlightDeckPersistenceMapper.capture(deck);
        assertEquals(0, c.advanceFrame(step).fixedTicks());
        assertEquals(queued, Stage228FlightDeckPersistenceMapper.capture(deck));
        c.coordinator().setPaused(false);
        setCondition(fitted, bay.id().bayStableId(), .5);
        assertEquals(.5, GeneratedCampaignCarrierBays.resolveAll(c).get(bay.id()).conditionFraction());
        assertEquals(1, c.advanceFrame(step).fixedTicks());
        assertEquals(step * 1.5d, deck.activeFor(id).orElseThrow().remainingWorkSeconds(), 1e-9);
        entity.add(originalEquipment);
        assertTrue(GeneratedCampaignCarrierBays.resolveAll(c).isEmpty());
        assertEquals(1, c.advanceFrame(step).fixedTicks());
        assertEquals(step * 1.5d, deck.activeFor(id).orElseThrow().remainingWorkSeconds(), 1e-9);
        entity.add(fitted);

        setCondition(fitted, bay.id().bayStableId(), 0);
        assertEquals(1, c.advanceFrame(step).fixedTicks());
        assertEquals(step * 1.5d, deck.activeFor(id).orElseThrow().remainingWorkSeconds(), 1e-9);
        // Replacing the sidecar restores pending work/watermark against the same actual occupancy.
        var saved = Stage228FlightDeckPersistenceMapper.capture(deck);
        var decoded = Stage228FlightDeckPersistenceCodec.decode(Stage228FlightDeckPersistenceCodec.encode(saved));
        deck = Stage228FlightDeckPersistenceMapper.restore(decoded, c.hangars());
        deckField.set(c, deck);
        assertEquals(saved, Stage228FlightDeckPersistenceMapper.capture(deck));
        setCondition(fitted, bay.id().bayStableId(), 1);
        assertEquals(1, c.advanceFrame(step).fixedTicks());
        assertEquals(step * .5d, deck.activeFor(id).orElseThrow().remainingWorkSeconds(), 1e-9);
        assertEquals(1, c.advanceFrame(step).fixedTicks());
        assertEquals(SmallCraftFlightDeckOperations.OperationPhase.AWAITING_HANDOFF, deck.activeFor(id).orElseThrow().phase());
        assertEquals(SmallCraftHangarCapacity.OccupancyState.LAUNCHING, c.hangars().find(id).orElseThrow().state());
        assertEquals(1, c.smallCraft().snapshot().size());
    }

    private static void setCondition(EngineeringComponent fitted, String mount, double integrity) {
        var prior = fitted.instanceState;
        fitted.instanceState = new ShipInstanceRuntimeState(new ShipDamageRuntime.Snapshot(
                prior.damage().compartmentIntegrityById(), new ShipEngineeringState.DamageState(Map.of(mount, integrity))),
                prior.shieldStatesByMount(), prior.maintenance(), prior.weaponLoadout(), prior.weaponMountRuntime());
    }
}
