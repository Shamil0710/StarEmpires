package com.spacesim.economy;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.DemonstratorFitDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.content.ship.Stage22CorePairEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage22CorePairOffensiveProjectionEnvelope;
import com.spacesim.content.ship.Stage22CorePairStrategicMobilityProjection;
import com.spacesim.ship.ShipEngineeringRuntime;
import com.spacesim.ship.ShipEngineeringRuntime.OperatingCommand;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B10 physical tanker-to-destroyer reaction-mass replenishment acceptance for both core factions. */
class Stage22CorePairReactionMassReplenishmentAcceptanceTest {
    private static final String REPLENISHMENT_TRANSFER = "replenishment_transfer";
    private static final String PROPELLANT_FEED = "propellant_feed";

    private static final List<ProjectionPackage> PACKAGES = List.of(
            new ProjectionPackage(
                    Stage22CorePairStrategicMobilityProjection.EMPIRE_DESTROYER_STRATEGIC_FIT,
                    Stage22CorePairStrategicMobilityProjection.EMPIRE_TANKER_STRATEGIC_FIT,
                    Stage22CorePairStrategicMobilityProjection.EMPIRE_SUPPORT_STRATEGIC_FIT),
            new ProjectionPackage(
                    Stage22CorePairStrategicMobilityProjection.UNION_DESTROYER_STRATEGIC_FIT,
                    Stage22CorePairStrategicMobilityProjection.UNION_TANKER_STRATEGIC_FIT,
                    Stage22CorePairStrategicMobilityProjection.UNION_SUPPORT_STRATEGIC_FIT));

    @Test
    void b10FiniteTankerStoreTransfersIntoExactStrategicDestroyerAndOrdinaryPropulsionConsumesIt() {
        ShipEngineeringCatalog catalog = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        Stage19FleetReplenishmentService replenishment = new Stage19FleetReplenishmentService(catalog);

        for (ProjectionPackage projectionPackage : PACKAGES) {
            DemonstratorFitDefinition destroyerDefinition = requireFit(catalog, projectionPackage.destroyerFitId());
            DemonstratorFitDefinition tankerDefinition = requireFit(catalog, projectionPackage.tankerFitId());
            InstalledFit destroyerFit = InstalledFit.fromDemonstrator(destroyerDefinition);
            InstalledFit tankerFit = InstalledFit.fromDemonstrator(tankerDefinition);
            ConsumableState tanker = reactionMassState(catalog, tankerDefinition, true);
            ConsumableState destroyer = reactionMassState(catalog, destroyerDefinition, false);

            var envelope = Stage22CorePairOffensiveProjectionEnvelope.evaluate(
                    catalog,
                    projectionPackage.destroyerFitId(),
                    projectionPackage.tankerFitId(),
                    projectionPackage.supportFitId(),
                    1,
                    1,
                    1,
                    1);
            double tankerTransferBefore = amount(tanker, REPLENISHMENT_TRANSFER);
            double destroyerCapacity = interfaceCapacity(catalog, destroyerDefinition, PROPELLANT_FEED);
            assertEquals(envelope.tankerReplenishmentKg(), tankerTransferBefore, 0d,
                    "B10 envelope must use the exact physical tanker transfer store");
            assertEquals(envelope.destroyerPropellantKg(), destroyerCapacity, 0d,
                    "B10 envelope must use the exact physical destroyer propellant feed");

            var transferred = replenishment.transferReactionMass(
                    tankerFit,
                    tanker,
                    destroyerFit,
                    destroyer,
                    destroyerCapacity);
            assertEquals(Stage19FleetReplenishmentService.Status.TRANSFERRED, transferred.status());
            assertEquals(destroyerCapacity, transferred.transferredMassKg(), 0d);
            assertEquals(tankerTransferBefore - destroyerCapacity,
                    amount(transferred.sourceConsumables(), REPLENISHMENT_TRANSFER), 1e-9d);
            assertEquals(destroyerCapacity,
                    amount(transferred.targetConsumables(), PROPELLANT_FEED), 1e-9d);
            assertEquals(tankerTransferBefore,
                    amount(transferred.sourceConsumables(), REPLENISHMENT_TRANSFER)
                            + amount(transferred.targetConsumables(), PROPELLANT_FEED),
                    1e-9d,
                    "ship-to-ship replenishment must conserve the transferred physical mass");
            assertEquals(envelope.remainingReplenishmentKg(),
                    amount(transferred.sourceConsumables(), REPLENISHMENT_TRANSFER), 1e-9d,
                    "one committed refill must land on the same finite B10 overextension envelope");

            ShipEngineeringRuntime runtime = new ShipEngineeringRuntime(catalog);
            var operating = runtime.initialize(
                    destroyerFit,
                    transferred.targetConsumables(),
                    DamageState.pristine());
            double propellantBeforeBurn = amount(operating.consumables(), PROPELLANT_FEED);
            var burn = runtime.advance(
                    destroyerFit,
                    operating,
                    DamageState.pristine(),
                    new OperatingCommand(Map.of("core_drive", 1d), Map.of(), Set.of()),
                    1d);

            assertTrue(burn.actualThrustN() > 0d,
                    "replenished strategic destroyer must produce ordinary physical thrust");
            assertTrue(burn.massFlowKgPerS() > 0d,
                    "ordinary propulsion must consume reaction mass after replenishment");
            assertTrue(amount(burn.state().consumables(), PROPELLANT_FEED) < propellantBeforeBurn,
                    "the transferred mass must be consumed by the existing engineering authority");
        }
    }

    @Test
    void b10ReplenishmentFailsClosedWithoutFiniteSourceMassOrReceiverCapacity() {
        ShipEngineeringCatalog catalog = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        Stage19FleetReplenishmentService replenishment = new Stage19FleetReplenishmentService(catalog);

        for (ProjectionPackage projectionPackage : PACKAGES) {
            DemonstratorFitDefinition destroyerDefinition = requireFit(catalog, projectionPackage.destroyerFitId());
            DemonstratorFitDefinition tankerDefinition = requireFit(catalog, projectionPackage.tankerFitId());
            InstalledFit destroyerFit = InstalledFit.fromDemonstrator(destroyerDefinition);
            InstalledFit tankerFit = InstalledFit.fromDemonstrator(tankerDefinition);
            ConsumableState emptyTanker = reactionMassState(catalog, tankerDefinition, false);
            ConsumableState emptyDestroyer = reactionMassState(catalog, destroyerDefinition, false);

            var noStock = replenishment.transferReactionMass(
                    tankerFit, emptyTanker, destroyerFit, emptyDestroyer, 1d);
            assertEquals(Stage19FleetReplenishmentService.Status.SOURCE_INSUFFICIENT_MASS, noStock.status());
            assertSame(emptyTanker, noStock.sourceConsumables());
            assertSame(emptyDestroyer, noStock.targetConsumables());

            ConsumableState fullTanker = reactionMassState(catalog, tankerDefinition, true);
            ConsumableState fullDestroyer = reactionMassState(catalog, destroyerDefinition, true);
            var noCapacity = replenishment.transferReactionMass(
                    tankerFit, fullTanker, destroyerFit, fullDestroyer, 1d);
            assertEquals(Stage19FleetReplenishmentService.Status.TARGET_CAPACITY_EXCEEDED, noCapacity.status());
            assertSame(fullTanker, noCapacity.sourceConsumables());
            assertSame(fullDestroyer, noCapacity.targetConsumables());

            var noTransferHardware = replenishment.transferReactionMass(
                    destroyerFit, fullDestroyer, destroyerFit, emptyDestroyer, 1d);
            assertEquals(Stage19FleetReplenishmentService.Status.SOURCE_INTERFACE_NOT_FOUND,
                    noTransferHardware.status());
            assertSame(fullDestroyer, noTransferHardware.sourceConsumables());
            assertSame(emptyDestroyer, noTransferHardware.targetConsumables());
        }
    }

    private static DemonstratorFitDefinition requireFit(ShipEngineeringCatalog catalog, String fitId) {
        DemonstratorFitDefinition definition = catalog.findDemonstratorFit(fitId);
        if (definition == null) {
            throw new AssertionError("Missing exact B10 strategic fit: " + fitId);
        }
        return definition;
    }

    private static ConsumableState reactionMassState(
            ShipEngineeringCatalog catalog,
            DemonstratorFitDefinition fit,
            boolean full) {
        List<ConsumableLoad> loads = new ArrayList<>();
        for (InstalledModuleDefinition assignment : fit.installedModules()) {
            ModuleDefinition module = catalog.findModule(assignment.moduleId());
            for (InterfaceDefinition definition : module.interfaces()) {
                if (definition.kind() != InterfaceKind.REACTION_MASS) {
                    continue;
                }
                double massKg = full ? definition.capacity() : 0d;
                loads.add(new ConsumableLoad(
                        assignment.mountId(),
                        definition.id(),
                        InterfaceKind.REACTION_MASS,
                        massKg,
                        massKg,
                        0L));
            }
        }
        return new ConsumableState(0d, 0d, 0d, 0d, loads);
    }

    private static double interfaceCapacity(
            ShipEngineeringCatalog catalog,
            DemonstratorFitDefinition fit,
            String interfaceId) {
        double capacity = 0d;
        int matches = 0;
        for (InstalledModuleDefinition assignment : fit.installedModules()) {
            ModuleDefinition module = catalog.findModule(assignment.moduleId());
            for (InterfaceDefinition definition : module.interfaces()) {
                if (definition.kind() == InterfaceKind.REACTION_MASS && definition.id().equals(interfaceId)) {
                    capacity += definition.capacity();
                    matches++;
                }
            }
        }
        if (matches != 1) {
            throw new AssertionError("Expected exactly one reaction-mass interface: " + interfaceId);
        }
        return capacity;
    }

    private static double amount(ConsumableState state, String interfaceId) {
        return state.interfaceLoads().stream()
                .filter(load -> load.kind() == InterfaceKind.REACTION_MASS)
                .filter(load -> load.interfaceId().equals(interfaceId))
                .mapToDouble(ConsumableLoad::amount)
                .sum();
    }

    private record ProjectionPackage(String destroyerFitId, String tankerFitId, String supportFitId) { }
}
