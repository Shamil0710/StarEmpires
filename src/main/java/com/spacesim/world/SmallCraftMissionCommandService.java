package com.spacesim.world;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;
import com.spacesim.ship.ProductionEngineeringRuntimeResolver;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationEvidence;
import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.SmallCraftFlightDeckOperations.OperationPhase;
import com.spacesim.world.SmallCraftHangarCapacity.BayDefinition;
import com.spacesim.world.SmallCraftHangarCapacity.OccupancyState;
import com.spacesim.world.SmallCraftMissionState.MissionOrder;
import com.spacesim.world.SmallCraftMissionState.MissionStatus;
import com.spacesim.world.SmallCraftMissionState.MissionTarget;
import com.spacesim.world.SmallCraftMissionState.MissionType;
import com.spacesim.world.SmallCraftMissionState.TargetKind;

import java.util.List;
import java.util.Objects;

/**
 * M22.8D shared PLAYER/AI command-validation boundary for individual small-craft missions.
 *
 * <p>The service consumes only actor-bounded target evidence supplied by the caller and current
 * authoritative craft/hangar/flight-deck state. It never queries hidden world truth. Physical
 * endurance is preflighted through the ordinary Stage-17.5 engineering runtime, while launch and
 * recovery transitions remain delegated to {@link SmallCraftFlightDeckOperations}. Tactical
 * movement, targeting and combat remain deferred to Stage-19/M22.8E.</p>
 */
public final class SmallCraftMissionCommandService {
    private static final double EPSILON = 1e-6d;

    private final SmallCraftRegistry craftRegistry;
    private final SmallCraftHangarRegistry hangars;
    private final SmallCraftFlightDeckOperations flightDeck;
    private final ShipEngineeringCatalog catalog;
    private final ProductionEngineeringRuntimeResolver engineering;

    /**
     * Creates one shared small-craft mission command boundary.
     *
     * @param craftRegistry physical individual-craft authority
     * @param hangars physical bay occupancy authority
     * @param flightDeck deterministic launch/recovery authority
     * @param catalog production engineering catalog used by the registered craft
     */
    public SmallCraftMissionCommandService(
            SmallCraftRegistry craftRegistry,
            SmallCraftHangarRegistry hangars,
            SmallCraftFlightDeckOperations flightDeck,
            ShipEngineeringCatalog catalog) {
        this.craftRegistry = Objects.requireNonNull(craftRegistry, "craftRegistry");
        this.hangars = Objects.requireNonNull(hangars, "hangars");
        this.flightDeck = Objects.requireNonNull(flightDeck, "flightDeck");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.engineering = new ProductionEngineeringRuntimeResolver(List.of(catalog));
    }

    /** Physical deployment projection supplied by the current world/tactical authority. */
    public enum DeploymentState {
        /** Craft is physically assigned to a carrier/station bay. */ EMBARKED,
        /** Craft is physically outside bay occupancy in local flight. */ DEPLOYED
    }

    /**
     * One mission submission from PLAYER or AI through the same validator.
     *
     * @param craftId individual physical craft
     * @param source common Stage-21 PLAYER/AI order source
     * @param type mission family
     * @param target actor-known target
     */
    public record MissionCommand(
            SmallCraftId craftId,
            OrderSource source,
            MissionType type,
            MissionTarget target) {
        /** Validates one command envelope.
         * @param craftId individual craft
         * @param source PLAYER or AI source
         * @param type mission family
         * @param target actor-known target
         */
        public MissionCommand {
            Objects.requireNonNull(craftId, "craftId");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(target, "target");
        }
    }

    /**
     * Actor-bounded/physical validation projection for one submission.
     *
     * <p>{@code requiredMissionDeltaVMps} must come from the ordinary local-flight/mission planner;
     * this command layer does not invent distance-to-delta-v conversion. {@code targetEvidence}
     * must already have reached the issuing actor through an allowed Stage-17.5/20/21 channel.</p>
     *
     * @param issuingFactionId stable faction issuing the command
     * @param authoritativeTick exact submission tick
     * @param deploymentState current physical embarked/deployed projection
     * @param commandNodeDistanceM actor-known distance used only for command-link range validation
     * @param commandRangeM current physical command/datalink range
     * @param commandLinkAvailable whether a lawful command link currently exists
     * @param requiredMissionDeltaVMps physical maneuver budget required by the planned mission
     * @param evidencedTargetReferenceId actor-known target identity supported by the supplied evidence
     * @param targetEvidence provenance/freshness of the target knowledge
     */
    public record MissionContext(
            String issuingFactionId,
            long authoritativeTick,
            DeploymentState deploymentState,
            double commandNodeDistanceM,
            double commandRangeM,
            boolean commandLinkAvailable,
            double requiredMissionDeltaVMps,
            String evidencedTargetReferenceId,
            ObservationEvidence targetEvidence) {
        /** Validates one bounded command context.
         * @param issuingFactionId stable issuing faction
         * @param authoritativeTick exact current tick
         * @param deploymentState physical deployment state
         * @param commandNodeDistanceM actor-known target distance
         * @param commandRangeM current command-link range
         * @param commandLinkAvailable current link availability
         * @param requiredMissionDeltaVMps required physical maneuver delta-v
         * @param evidencedTargetReferenceId target identity supported by the evidence
         * @param targetEvidence actor-known evidence
         */
        public MissionContext {
            issuingFactionId = requireText(issuingFactionId, "issuingFactionId");
            if (authoritativeTick < 0L) {
                throw new IllegalArgumentException("authoritativeTick cannot be negative");
            }
            Objects.requireNonNull(deploymentState, "deploymentState");
            requireNonNegative(commandNodeDistanceM, "commandNodeDistanceM");
            requireNonNegative(commandRangeM, "commandRangeM");
            requireNonNegative(requiredMissionDeltaVMps, "requiredMissionDeltaVMps");
            evidencedTargetReferenceId = requireText(
                    evidencedTargetReferenceId, "evidencedTargetReferenceId");
            Objects.requireNonNull(targetEvidence, "targetEvidence");
        }
    }

    /**
     * Accepted mission submission result.
     *
     * @param state updated immutable mission state
     * @param mission accepted mission
     * @param supersededMissionId previous active mission cancelled by a deployed retask, or zero
     */
    public record SubmissionResult(
            SmallCraftMissionState state,
            MissionOrder mission,
            long supersededMissionId) {
        /** Validates a submission result.
         * @param state updated state
         * @param mission accepted mission
         * @param supersededMissionId previous mission ID or zero
         */
        public SubmissionResult {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(mission, "mission");
            if (supersededMissionId < 0L) {
                throw new IllegalArgumentException("supersededMissionId cannot be negative");
            }
        }
    }

    /**
     * Validates and accepts one PLAYER or AI command through the same path.
     *
     * @param state current mission state
     * @param command submitted mission intent
     * @param context actor-bounded knowledge plus physical planning evidence
     * @return updated immutable state and accepted mission
     */
    public SubmissionResult submit(
            SmallCraftMissionState state,
            MissionCommand command,
            MissionContext context) {
        SmallCraftMissionState current = Objects.requireNonNull(state, "state");
        MissionCommand requested = Objects.requireNonNull(command, "command");
        MissionContext observed = Objects.requireNonNull(context, "context");

        SmallCraftState craft = craftRegistry.find(requested.craftId()).orElseThrow(
                () -> new IllegalArgumentException(
                        "unknown small craft: " + requested.craftId()));
        if (!craft.stableFactionId().equals(observed.issuingFactionId())) {
            throw new IllegalArgumentException(
                    "issuing faction does not own small craft: " + requested.craftId());
        }

        validateTarget(requested.type(), requested.target(), observed);
        validatePhysicalDeployment(requested, observed);
        validateCapability(craft, requested.type());
        validateEndurance(requested.craftId(), observed.requiredMissionDeltaVMps());

        SmallCraftMissionState next = current;
        long superseded = 0L;
        var existing = current.activeMissionFor(requested.craftId());
        if (existing.isPresent()) {
            if (observed.deploymentState() != DeploymentState.DEPLOYED) {
                throw new IllegalStateException(
                        "embarked craft already has an active mission: " + requested.craftId());
            }
            MissionOrder previous = existing.orElseThrow();
            next = next.replace(previous.withStatus(MissionStatus.CANCELLED));
            superseded = previous.id();
        }

        MissionStatus initialStatus;
        if (observed.deploymentState() == DeploymentState.EMBARKED) {
            var assignment = hangars.find(requested.craftId()).orElseThrow();
            flightDeck.requestLaunch(
                    requested.craftId(),
                    assignment.bayId(),
                    observed.authoritativeTick());
            initialStatus = MissionStatus.LAUNCH_QUEUED;
        } else {
            initialStatus = requested.type() == MissionType.RETURN
                    || requested.type() == MissionType.RECOVER
                    || requested.type() == MissionType.DIVERT
                    ? MissionStatus.RETURNING
                    : MissionStatus.ACTIVE;
        }

        MissionOrder mission = new MissionOrder(
                next.nextMissionId(),
                requested.craftId(),
                requested.source(),
                requested.type(),
                requested.target(),
                observed.authoritativeTick(),
                initialStatus);
        SmallCraftMissionState accepted = next.add(mission);
        return new SubmissionResult(accepted, mission, superseded);
    }

    /**
     * Cancels a mission only while its physical launch is still cancellable.
     *
     * <p>Deployed craft cannot disappear because a command was cancelled; they must instead receive
     * a lawful RETURN/RECOVER/DIVERT retask.</p>
     *
     * @param state current mission state
     * @param missionId mission to cancel
     * @return updated immutable state
     */
    public SmallCraftMissionState cancelQueuedLaunch(
            SmallCraftMissionState state,
            long missionId) {
        SmallCraftMissionState current = Objects.requireNonNull(state, "state");
        MissionOrder mission = current.requireMission(missionId);
        if (mission.status() != MissionStatus.LAUNCH_QUEUED) {
            throw new IllegalStateException(
                    "only a queued/cycling pre-handoff launch mission is cancellable");
        }
        if (!flightDeck.cancelLaunch(mission.craftId())) {
            throw new IllegalStateException(
                    "physical launch already crossed the cancellable boundary");
        }
        return current.replace(mission.withStatus(MissionStatus.CANCELLED));
    }

    /**
     * Completes the physical launch handoff after M22.8E materializes the same craft locally.
     *
     * <p>Package-level visibility prevents UI/command submission code from bypassing the physical
     * handoff boundary.</p>
     *
     * @param state current mission state
     * @param missionId launch mission
     * @return updated state with ACTIVE mission
     */
    SmallCraftMissionState confirmPhysicalLaunch(
            SmallCraftMissionState state,
            long missionId) {
        SmallCraftMissionState current = Objects.requireNonNull(state, "state");
        MissionOrder mission = current.requireMission(missionId);
        if (mission.status() != MissionStatus.LAUNCH_QUEUED) {
            throw new IllegalStateException("physical launch requires LAUNCH_QUEUED mission");
        }
        var operation = flightDeck.activeFor(mission.craftId()).orElseThrow(
                () -> new IllegalStateException(
                        "launch mission has no physical flight-deck operation"));
        if (operation.phase() != OperationPhase.AWAITING_HANDOFF) {
            throw new IllegalStateException(
                    "physical launch handoff requires completed deck cycle");
        }
        flightDeck.confirmLaunchHandoff(mission.craftId());
        return current.replace(mission.withStatus(MissionStatus.ACTIVE));
    }

    /**
     * Offers one returning physical craft to M22.8C recovery and records that pending boundary.
     *
     * @param state current mission state
     * @param missionId active/returning mission
     * @param bay current physical recovery bay
     * @param authoritativeTick current world tick
     * @return updated state with recovery pending
     */
    SmallCraftMissionState offerPhysicalRecovery(
            SmallCraftMissionState state,
            long missionId,
            BayDefinition bay,
            long authoritativeTick) {
        SmallCraftMissionState current = Objects.requireNonNull(state, "state");
        MissionOrder mission = current.requireMission(missionId);
        if (mission.status() != MissionStatus.ACTIVE
                && mission.status() != MissionStatus.RETURNING) {
            throw new IllegalStateException(
                    "recovery offer requires an active/returning deployed mission");
        }
        if (hangars.find(mission.craftId()).isPresent()) {
            throw new IllegalStateException("recovery craft is already embarked");
        }
        flightDeck.offerPhysicalRecovery(
                mission.craftId(),
                Objects.requireNonNull(bay, "bay"),
                authoritativeTick);
        return current.replace(mission.withStatus(MissionStatus.RECOVERY_PENDING));
    }

    /**
     * Marks a mission complete only after C has physically recovered the craft into SERVICING.
     *
     * @param state current mission state
     * @param missionId recovery-pending mission
     * @return updated state with completed mission
     */
    SmallCraftMissionState completePhysicalRecovery(
            SmallCraftMissionState state,
            long missionId) {
        SmallCraftMissionState current = Objects.requireNonNull(state, "state");
        MissionOrder mission = current.requireMission(missionId);
        if (mission.status() != MissionStatus.RECOVERY_PENDING) {
            throw new IllegalStateException(
                    "mission completion requires RECOVERY_PENDING state");
        }
        var assignment = hangars.find(mission.craftId()).orElseThrow(
                () -> new IllegalStateException(
                        "recovery completion requires physical bay occupancy"));
        if (assignment.state() != OccupancyState.SERVICING
                || flightDeck.activeFor(mission.craftId()).isPresent()) {
            throw new IllegalStateException(
                    "recovery completion requires finished physical deck cycle");
        }
        return current.replace(mission.withStatus(MissionStatus.COMPLETE));
    }

    private void validateTarget(
            MissionType type,
            MissionTarget target,
            MissionContext context) {
        boolean kindAllowed = switch (type) {
            case CAP -> target.kind() == TargetKind.AREA || target.kind() == TargetKind.HOST;
            case QRA, INTERCEPTION, ANTI_SHIP_STRIKE -> target.kind() == TargetKind.TRACK;
            case ESCORT -> target.kind() == TargetKind.OWNED_ASSET;
            case RECONNAISSANCE -> target.kind() == TargetKind.AREA
                    || target.kind() == TargetKind.TRACK;
            case EW_SUPPORT -> target.kind() == TargetKind.AREA
                    || target.kind() == TargetKind.TRACK
                    || target.kind() == TargetKind.OWNED_ASSET;
            case RETURN, RECOVER -> target.kind() == TargetKind.HOST;
            case DIVERT -> target.kind() == TargetKind.HOST || target.kind() == TargetKind.AREA;
        };
        if (!kindAllowed) {
            throw new IllegalArgumentException(
                    "mission target kind is invalid for " + type + ": " + target.kind());
        }
        if (!target.referenceId().equals(context.evidencedTargetReferenceId())) {
            throw new IllegalArgumentException(
                    "mission target is not supported by supplied actor-known evidence");
        }
        if (!context.targetEvidence().currentAt(context.authoritativeTick())) {
            throw new IllegalArgumentException("mission target evidence is stale or future-dated");
        }
    }

    private void validatePhysicalDeployment(
            MissionCommand command,
            MissionContext context) {
        var assignment = hangars.find(command.craftId());
        if (!context.commandLinkAvailable()) {
            throw new IllegalArgumentException(
                    "small-craft command requires lawful command/datalink connectivity");
        }
        if (context.commandNodeDistanceM() > context.commandRangeM() + EPSILON) {
            throw new IllegalArgumentException(
                    "small craft is outside current command/datalink range");
        }
        if (context.deploymentState() == DeploymentState.EMBARKED) {
            if (assignment.isEmpty()) {
                throw new IllegalArgumentException(
                        "embarked mission context requires physical bay assignment");
            }
            if (assignment.orElseThrow().state() != OccupancyState.READY) {
                throw new IllegalArgumentException(
                        "embarked mission launch requires READY craft");
            }
            if (!command.type().deploymentMission()) {
                throw new IllegalArgumentException(
                        "RETURN/RECOVER/DIVERT require physically deployed craft");
            }
            return;
        }

        if (assignment.isPresent()) {
            throw new IllegalArgumentException(
                    "deployed mission context cannot reference embarked craft");
        }

    }

    private void validateCapability(SmallCraftState craft, MissionType type) {
        boolean weapon = false;
        boolean sensorEw = false;
        for (var installed : craft.fit().installedModules()) {
            var module = catalog.findModule(installed.moduleId());
            if (module == null) {
                throw new IllegalStateException(
                        "validated craft references missing module: " + installed.moduleId());
            }
            double integrity = craft.instanceState().damage().moduleDamage()
                    .moduleIntegrityByMount().getOrDefault(installed.mountId(), 1d);
            if (integrity <= EPSILON) {
                continue;
            }
            if (module.family() == ModuleFamily.WEAPON_AMMUNITION) {
                weapon = true;
            }
            if (module.family() == ModuleFamily.SENSOR_EW_FIRE_CONTROL) {
                sensorEw = true;
            }
        }
        if (type.requiresWeapons() && !weapon) {
            throw new IllegalArgumentException(
                    "mission requires an authored weapon-capable fit: " + type);
        }
        if (type.requiresSensorEw() && !sensorEw) {
            throw new IllegalArgumentException(
                    "mission requires an authored sensor/EW-capable fit: " + type);
        }
    }

    private void validateEndurance(
            SmallCraftId craftId,
            double requiredMissionDeltaVMps) {
        EngineeringComponent physical =
                craftRegistry.materializeEngineering(Objects.requireNonNull(craftId, "craftId"));
        var plan = engineering.planDeltaV(physical, requiredMissionDeltaVMps);
        if (!plan.feasible()) {
            throw new IllegalArgumentException(
                    "current physical engineering state cannot satisfy mission delta-v");
        }
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label).strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }

    private static void requireNonNegative(double value, String label) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }
}
