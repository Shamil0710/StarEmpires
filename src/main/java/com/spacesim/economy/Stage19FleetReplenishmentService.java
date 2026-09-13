package com.spacesim.economy;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Stage-19 physical ship-to-ship reaction-mass replenishment boundary.
 *
 * <p>The source must expose the authored {@code replenishment_transfer} reaction-mass interface and
 * the receiver must expose the ordinary {@code propellant_feed} reaction-mass interface. Successful
 * transfers move the same finite mass between the two central {@link ConsumableState} instances;
 * they do not create fuel, readiness points or a faction-specific replenishment reserve.</p>
 *
 * <p>This service deliberately owns only the inventory handoff. Navigation, rendezvous, docking and
 * order admission remain responsibilities of their existing world/fleet authorities.</p>
 */
public final class Stage19FleetReplenishmentService {
    private static final double EPSILON = 1.0e-9d;
    private static final String SOURCE_INTERFACE_ID = "replenishment_transfer";
    private static final String TARGET_INTERFACE_ID = "propellant_feed";

    private final ShipEngineeringCatalog engineering;

    /**
     * Creates the common physical fleet-replenishment boundary.
     *
     * @param engineering authoritative Stage-17.5 engineering catalog
     */
    public Stage19FleetReplenishmentService(ShipEngineeringCatalog engineering) {
        this.engineering = Objects.requireNonNull(engineering, "engineering");
    }

    /** Stable outcome of one reaction-mass transfer request. */
    public enum Status {
        /** Finite reaction mass was removed from the source store and loaded into the receiver. */ TRANSFERRED,
        /** Request arguments or aliased source/receiver state are invalid. */ INVALID_REQUEST,
        /** Source fit does not expose exactly one authored replenishment-transfer interface. */ SOURCE_INTERFACE_NOT_FOUND,
        /** Receiver fit does not expose exactly one authored propellant-feed interface. */ TARGET_INTERFACE_NOT_FOUND,
        /** Source physical replenishment store contains less mass than requested. */ SOURCE_INSUFFICIENT_MASS,
        /** Receiver physical propellant feed lacks capacity for the requested mass. */ TARGET_CAPACITY_EXCEEDED
    }

    /**
     * Immutable result of one physical reaction-mass transfer.
     *
     * @param status stable transfer outcome
     * @param transferredMassKg physical mass committed between ship states
     * @param sourceConsumables resulting source consumable state
     * @param targetConsumables resulting receiver consumable state
     */
    public record TransferResult(
            Status status,
            double transferredMassKg,
            ConsumableState sourceConsumables,
            ConsumableState targetConsumables) { }

    /**
     * Transfers finite tanker reaction mass into one receiver's physical drive feed.
     *
     * <p>All validation occurs before either returned state changes. Rejected requests therefore
     * return the exact source and receiver state objects supplied by the caller.</p>
     *
     * @param sourceFit fitted donor ship exposing {@code replenishment_transfer}
     * @param sourceConsumables donor central consumable state
     * @param targetFit fitted receiver ship exposing {@code propellant_feed}
     * @param targetConsumables receiver central consumable state
     * @param requestedMassKg positive physical reaction mass to move
     * @return immutable committed or fail-closed transfer result
     */
    public TransferResult transferReactionMass(
            InstalledFit sourceFit,
            ConsumableState sourceConsumables,
            InstalledFit targetFit,
            ConsumableState targetConsumables,
            double requestedMassKg) {
        InstalledFit checkedSourceFit = Objects.requireNonNull(sourceFit, "sourceFit");
        ConsumableState checkedSource = Objects.requireNonNull(sourceConsumables, "sourceConsumables");
        InstalledFit checkedTargetFit = Objects.requireNonNull(targetFit, "targetFit");
        ConsumableState checkedTarget = Objects.requireNonNull(targetConsumables, "targetConsumables");
        if (!Double.isFinite(requestedMassKg) || requestedMassKg <= 0d || checkedSource == checkedTarget) {
            return rejected(Status.INVALID_REQUEST, checkedSource, checkedTarget);
        }

        ResolvedInterface sourceInterface = resolveInterface(checkedSourceFit, SOURCE_INTERFACE_ID);
        if (sourceInterface == null) {
            return rejected(Status.SOURCE_INTERFACE_NOT_FOUND, checkedSource, checkedTarget);
        }
        ResolvedInterface targetInterface = resolveInterface(checkedTargetFit, TARGET_INTERFACE_ID);
        if (targetInterface == null) {
            return rejected(Status.TARGET_INTERFACE_NOT_FOUND, checkedSource, checkedTarget);
        }

        ConsumableLoad sourceLoad = findLoad(checkedSource, sourceInterface);
        double sourceAmount = sourceLoad == null ? 0d : sourceLoad.amount();
        double sourceMassKg = sourceLoad == null ? 0d : sourceLoad.massKg();
        if (sourceAmount + EPSILON < requestedMassKg || sourceMassKg + EPSILON < requestedMassKg) {
            return rejected(Status.SOURCE_INSUFFICIENT_MASS, checkedSource, checkedTarget);
        }

        ConsumableLoad targetLoad = findLoad(checkedTarget, targetInterface);
        double targetAmount = targetLoad == null ? 0d : targetLoad.amount();
        double targetMassKg = targetLoad == null ? 0d : targetLoad.massKg();
        if (targetAmount + requestedMassKg > targetInterface.definition().capacity() + EPSILON) {
            return rejected(Status.TARGET_CAPACITY_EXCEEDED, checkedSource, checkedTarget);
        }

        ConsumableState nextSource = replaceLoad(
                checkedSource,
                sourceLoad,
                new ConsumableLoad(
                        sourceInterface.mountId(),
                        SOURCE_INTERFACE_ID,
                        InterfaceKind.REACTION_MASS,
                        Math.max(0d, sourceAmount - requestedMassKg),
                        Math.max(0d, sourceMassKg - requestedMassKg),
                        sourceLoad == null ? 0L : sourceLoad.itemCount()));
        ConsumableState nextTarget = replaceLoad(
                checkedTarget,
                targetLoad,
                new ConsumableLoad(
                        targetInterface.mountId(),
                        TARGET_INTERFACE_ID,
                        InterfaceKind.REACTION_MASS,
                        targetAmount + requestedMassKg,
                        targetMassKg + requestedMassKg,
                        targetLoad == null ? 0L : targetLoad.itemCount()));
        return new TransferResult(Status.TRANSFERRED, requestedMassKg, nextSource, nextTarget);
    }

    private ResolvedInterface resolveInterface(InstalledFit fit, String interfaceId) {
        ResolvedInterface resolved = null;
        for (InstalledModuleDefinition assignment : fit.installedModules()) {
            ModuleDefinition module = engineering.findModule(assignment.moduleId());
            if (module == null) {
                throw new IllegalStateException("Installed fit references missing module: " + assignment.moduleId());
            }
            for (InterfaceDefinition definition : module.interfaces()) {
                if (definition.kind() != InterfaceKind.REACTION_MASS || !interfaceId.equals(definition.id())) {
                    continue;
                }
                if (resolved != null) {
                    throw new IllegalStateException("Fit exposes duplicate reaction-mass interface: " + interfaceId);
                }
                resolved = new ResolvedInterface(assignment.mountId(), definition);
            }
        }
        return resolved;
    }

    private static ConsumableLoad findLoad(ConsumableState state, ResolvedInterface physicalInterface) {
        return state.interfaceLoads().stream()
                .filter(load -> load.kind() == InterfaceKind.REACTION_MASS)
                .filter(load -> load.mountId().equals(physicalInterface.mountId()))
                .filter(load -> load.interfaceId().equals(physicalInterface.definition().id()))
                .findFirst()
                .orElse(null);
    }

    private static ConsumableState replaceLoad(
            ConsumableState current,
            ConsumableLoad existing,
            ConsumableLoad replacement) {
        List<ConsumableLoad> loads = new ArrayList<>();
        for (ConsumableLoad load : current.interfaceLoads()) {
            if (existing != null && load == existing) {
                continue;
            }
            loads.add(load);
        }
        loads.add(replacement);
        return new ConsumableState(
                current.cargoMassKg(),
                current.storesMassKg(),
                current.missionPayloadMassKg(),
                current.missionIntegrationVolumeM3(),
                loads);
    }

    private static TransferResult rejected(
            Status status,
            ConsumableState source,
            ConsumableState target) {
        return new TransferResult(status, 0d, source, target);
    }

    private record ResolvedInterface(String mountId, InterfaceDefinition definition) { }
}
