package com.spacesim.world;

import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationEvidence;
import com.spacesim.world.FleetCommandState.CommandGroupState;
import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.SmallCraftHangarCapacity.OccupancyState;
import com.spacesim.world.SmallCraftMissionCommandService.DeploymentState;
import com.spacesim.world.SmallCraftMissionCommandService.MissionCommand;
import com.spacesim.world.SmallCraftMissionCommandService.MissionContext;
import com.spacesim.world.SmallCraftMissionState.MissionOrder;
import com.spacesim.world.SmallCraftMissionState.MissionStatus;
import com.spacesim.world.SmallCraftMissionState.MissionTarget;
import com.spacesim.world.SmallCraftMissionState.MissionType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * M22.8F carrier-group AI doctrine and one-step mission scheduler.
 *
 * <p>The service is deliberately a policy/orchestration layer, not a physical authority. Carrier
 * standoff and withdrawal are returned as directives for the ordinary Stage-21 command/operation
 * path. Small-craft actions are accepted only through the shared M22.8D
 * {@link SmallCraftMissionCommandService} with {@link OrderSource#AI}; C remains the physical
 * launch/recovery queue and E/Stage-19 remain the exact tactical authorities.</p>
 *
 * <p>Every hostile decision uses caller-supplied actor-bounded evidence. A stale or future-dated
 * threat cannot trigger intercept, strike, standoff or threat-driven withdrawal. Own carrier,
 * escort and wing readiness may still independently require withdrawal because those are own
 * physical states rather than hidden enemy truth.</p>
 */
public final class CarrierGroupAiDoctrineService {
    private static final double EPSILON = 1e-9d;

    private final SmallCraftRegistry craftRegistry;
    private final SmallCraftHangarRegistry hangars;
    private final SmallCraftMissionCommandService missionCommands;

    /**
     * Creates the doctrine scheduler over existing A/B/C/D authorities.
     */
    public CarrierGroupAiDoctrineService(
            SmallCraftRegistry craftRegistry,
            SmallCraftHangarRegistry hangars,
            SmallCraftMissionCommandService missionCommands) {
        this.craftRegistry = Objects.requireNonNull(craftRegistry, "craftRegistry");
        this.hangars = Objects.requireNonNull(hangars, "hangars");
        this.missionCommands = Objects.requireNonNull(missionCommands, "missionCommands");
    }

    /**
     * Evaluates carrier-group doctrine and submits at most one AI small-craft mission.
     *
     * <p>One accepted mission per call makes the scheduling boundary deterministic and prevents a
     * later invalid opportunity from partially mutating several deck queues. Repeated authoritative
     * ticks naturally create sequential/multi-axis packages while M22.8C supplies finite launch
     * sequencing.</p>
     *
     * @param commandState existing Stage-21 command groups
     * @param missionState current D mission state
     * @param observation actor-bounded carrier-group/hostile observation
     * @param opportunities lawful mission opportunities for this exact tick
     * @param policy authored doctrine thresholds
     * @return updated mission state plus non-authoritative carrier/escort directives
     */
    public DecisionResult advance(
            FleetCommandState commandState,
            SmallCraftMissionState missionState,
            CarrierGroupObservation observation,
            Collection<MissionOpportunity> opportunities,
            DoctrinePolicy policy) {
        Objects.requireNonNull(commandState, "commandState");
        SmallCraftMissionState current = Objects.requireNonNull(missionState, "missionState");
        CarrierGroupObservation observed = Objects.requireNonNull(observation, "observation");
        DoctrinePolicy doctrine = Objects.requireNonNull(policy, "policy");
        List<MissionOpportunity> candidates = canonicalOpportunities(
                Objects.requireNonNull(opportunities, "opportunities"),
                observed.authoritativeTick());

        validateCommandGroup(commandState, observed);
        WingReadiness wing = deriveWingReadiness(observed.wingCraftIds(), current, doctrine);
        FreshThreat threat = freshThreat(observed);
        DispositionDecision disposition = disposition(observed, wing, threat, doctrine);

        MissionOpportunity selected = selectRecovery(
                observed, current, candidates, doctrine, disposition.disposition());
        if (selected == null && disposition.disposition() != CarrierDisposition.WITHDRAW) {
            selected = selectIntercept(observed, current, candidates, threat, doctrine);
        }
        if (selected == null && disposition.disposition() != CarrierDisposition.WITHDRAW) {
            selected = selectCap(observed, current, candidates, doctrine);
        }
        if (selected == null && disposition.disposition() == CarrierDisposition.HOLD) {
            selected = selectStrike(observed, current, candidates, threat, doctrine);
        }

        EscortDirective escort = escortDirective(disposition.disposition(), selected);
        if (selected == null) {
            return new DecisionResult(
                    current,
                    new DoctrineDecision(
                            disposition.disposition(),
                            escort,
                            disposition.reason(),
                            wing.readyBps(),
                            null),
                    0L,
                    0L);
        }

        var accepted = missionCommands.submit(
                current,
                new MissionCommand(
                        selected.craftId(),
                        OrderSource.AI,
                        selected.type(),
                        selected.target()),
                selected.context());
        return new DecisionResult(
                accepted.state(),
                new DoctrineDecision(
                        disposition.disposition(),
                        escort,
                        disposition.reason(),
                        wing.readyBps(),
                        selected),
                accepted.mission().id(),
                accepted.supersededMissionId());
    }

    private static void validateCommandGroup(
            FleetCommandState commandState,
            CarrierGroupObservation observation) {
        CommandGroupState group = commandState.requireGroup(observation.commandGroupId());
        if (!group.memberFleetIds().contains(observation.carrierFleetId())) {
            throw new IllegalArgumentException(
                    "carrier is not an ordinary member of the supplied Stage-21 command group");
        }
        for (FleetId escort : observation.escortFleetIds()) {
            if (!group.memberFleetIds().contains(escort)) {
                throw new IllegalArgumentException(
                        "escort is not an ordinary member of the supplied Stage-21 command group: "
                                + escort);
            }
        }
    }

    private WingReadiness deriveWingReadiness(
            List<SmallCraftId> wingCraftIds,
            SmallCraftMissionState missions,
            DoctrinePolicy policy) {
        int operational = 0;
        int ready = 0;
        int depleted = 0;
        for (SmallCraftId id : wingCraftIds) {
            Optional<SmallCraftState> state = craftRegistry.find(id);
            if (state.isEmpty()) {
                continue;
            }
            operational++;
            boolean needsRecovery = needsRecovery(state.orElseThrow(), policy);
            if (needsRecovery) {
                depleted++;
            }
            Optional<SmallCraftHangarRegistry.Assignment> assignment = hangars.find(id);
            Optional<MissionOrder> active = missions.activeMissionFor(id);
            if (assignment.isPresent()
                    && assignment.orElseThrow().state() == OccupancyState.READY
                    && active.isEmpty()
                    && !needsRecovery) {
                ready++;
            } else if (assignment.isEmpty()
                    && active.isPresent()
                    && active.orElseThrow().status() == MissionStatus.ACTIVE
                    && !needsRecovery) {
                ready++;
            }
        }
        int denominator = Math.max(1, wingCraftIds.size());
        int readyBps = (int) Math.min(
                FleetReadinessState.FULL,
                Math.round(ready * (double) FleetReadinessState.FULL / denominator));
        return new WingReadiness(operational, ready, depleted, readyBps);
    }

    private static FreshThreat freshThreat(CarrierGroupObservation observation) {
        ThreatObservation threat = observation.threat();
        if (threat == null || !threat.evidence().currentAt(observation.authoritativeTick())) {
            return FreshThreat.absent();
        }
        return new FreshThreat(true, threat);
    }

    private static DispositionDecision disposition(
            CarrierGroupObservation observation,
            WingReadiness wing,
            FreshThreat freshThreat,
            DoctrinePolicy policy) {
        if (observation.carrierReadiness().overallBps() < policy.carrierWithdrawReadinessBps()) {
            return new DispositionDecision(
                    CarrierDisposition.WITHDRAW,
                    DecisionReason.CARRIER_READINESS);
        }
        if (!observation.escortFleetIds().isEmpty()
                && observation.escortReadiness().overallBps()
                < policy.escortWithdrawReadinessBps()) {
            return new DispositionDecision(
                    CarrierDisposition.WITHDRAW,
                    DecisionReason.ESCORT_READINESS);
        }
        if (wing.readyBps() < policy.wingWithdrawReadinessBps()) {
            return new DispositionDecision(
                    CarrierDisposition.WITHDRAW,
                    DecisionReason.WING_DEGRADED);
        }
        if (!freshThreat.present()) {
            return new DispositionDecision(
                    CarrierDisposition.HOLD,
                    DecisionReason.NO_FRESH_THREAT);
        }
        ThreatObservation threat = freshThreat.threat();
        if (threat.distanceM() + EPSILON < threat.effectiveThreatRangeM()
                && threat.severityBps() >= policy.severeThreatWithdrawBps()) {
            return new DispositionDecision(
                    CarrierDisposition.WITHDRAW,
                    DecisionReason.SEVERE_THREAT_INSIDE_RANGE);
        }
        double minimumStandoff =
                threat.effectiveThreatRangeM() * policy.standoffRangeMultiplier();
        if (threat.distanceM() + EPSILON < minimumStandoff) {
            return new DispositionDecision(
                    CarrierDisposition.STANDOFF,
                    DecisionReason.THREAT_INSIDE_STANDOFF);
        }
        return new DispositionDecision(
                CarrierDisposition.HOLD,
                DecisionReason.SAFE_STANDOFF);
    }

    private MissionOpportunity selectRecovery(
            CarrierGroupObservation observation,
            SmallCraftMissionState missions,
            List<MissionOpportunity> opportunities,
            DoctrinePolicy policy,
            CarrierDisposition disposition) {
        for (MissionOpportunity opportunity : opportunities) {
            if (!observation.wingCraftIds().contains(opportunity.craftId())
                    || !recoveryMission(opportunity.type())
                    || opportunity.context().deploymentState() != DeploymentState.DEPLOYED) {
                continue;
            }
            Optional<SmallCraftState> physical = craftRegistry.find(opportunity.craftId());
            Optional<MissionOrder> active = missions.activeMissionFor(opportunity.craftId());
            if (physical.isEmpty() || active.isEmpty() || hangars.find(opportunity.craftId()).isPresent()) {
                continue;
            }
            if (active.orElseThrow().status() == MissionStatus.RETURNING) {
                continue;
            }
            if (disposition == CarrierDisposition.WITHDRAW
                    || needsRecovery(physical.orElseThrow(), policy)) {
                return opportunity;
            }
        }
        return null;
    }

    private MissionOpportunity selectIntercept(
            CarrierGroupObservation observation,
            SmallCraftMissionState missions,
            List<MissionOpportunity> opportunities,
            FreshThreat freshThreat,
            DoctrinePolicy policy) {
        if (!freshThreat.present()
                || freshThreat.threat().severityBps() < policy.interceptThreatSeverityBps()) {
            return null;
        }
        String threatId = freshThreat.threat().referenceId();
        int active = activeMissionCount(
                missions,
                observation.wingCraftIds(),
                Set.of(MissionType.QRA, MissionType.INTERCEPTION));
        if (active >= policy.maxActiveInterceptCraft()) {
            return null;
        }
        for (MissionOpportunity opportunity : opportunities) {
            if ((opportunity.type() == MissionType.QRA
                    || opportunity.type() == MissionType.INTERCEPTION)
                    && opportunity.target().referenceId().equals(threatId)
                    && availableForLaunch(opportunity.craftId(), missions, observation.wingCraftIds())) {
                return opportunity;
            }
        }
        return null;
    }

    private MissionOpportunity selectCap(
            CarrierGroupObservation observation,
            SmallCraftMissionState missions,
            List<MissionOpportunity> opportunities,
            DoctrinePolicy policy) {
        int activeCap = activeMissionCount(
                missions,
                observation.wingCraftIds(),
                Set.of(MissionType.CAP));
        if (activeCap >= policy.minimumCapCraft()) {
            return null;
        }
        for (MissionOpportunity opportunity : opportunities) {
            if (opportunity.type() == MissionType.CAP
                    && availableForLaunch(opportunity.craftId(), missions, observation.wingCraftIds())) {
                return opportunity;
            }
        }
        return null;
    }

    private MissionOpportunity selectStrike(
            CarrierGroupObservation observation,
            SmallCraftMissionState missions,
            List<MissionOpportunity> opportunities,
            FreshThreat freshThreat,
            DoctrinePolicy policy) {
        if (!freshThreat.present()) {
            return null;
        }
        ThreatObservation threat = freshThreat.threat();
        if (threat.severityBps() < policy.strikeThreatSeverityBps()
                || threat.distanceM() > threat.wingStrikeReachM() + EPSILON) {
            return null;
        }
        int activeStrike = activeMissionCount(
                missions,
                observation.wingCraftIds(),
                Set.of(MissionType.ANTI_SHIP_STRIKE));
        if (activeStrike >= policy.maxActiveStrikeCraft()) {
            return null;
        }
        for (MissionOpportunity opportunity : opportunities) {
            if (opportunity.type() == MissionType.ANTI_SHIP_STRIKE
                    && opportunity.target().referenceId().equals(threat.referenceId())
                    && availableForLaunch(opportunity.craftId(), missions, observation.wingCraftIds())) {
                return opportunity;
            }
        }
        return null;
    }

    private boolean availableForLaunch(
            SmallCraftId craftId,
            SmallCraftMissionState missions,
            List<SmallCraftId> wingCraftIds) {
        if (!wingCraftIds.contains(craftId) || craftRegistry.find(craftId).isEmpty()) {
            return false;
        }
        Optional<SmallCraftHangarRegistry.Assignment> assignment = hangars.find(craftId);
        return assignment.isPresent()
                && assignment.orElseThrow().state() == OccupancyState.READY
                && missions.activeMissionFor(craftId).isEmpty();
    }

    private static int activeMissionCount(
            SmallCraftMissionState missions,
            List<SmallCraftId> wingCraftIds,
            Set<MissionType> types) {
        int count = 0;
        for (SmallCraftId id : wingCraftIds) {
            Optional<MissionOrder> active = missions.activeMissionFor(id);
            if (active.isPresent() && types.contains(active.orElseThrow().type())) {
                count++;
            }
        }
        return count;
    }

    private static boolean needsRecovery(
            SmallCraftState craft,
            DoctrinePolicy policy) {
        boolean ammunitionDependent = craft.runtimeState().consumables().interfaceLoads().stream()
                .anyMatch(load -> load.kind() == InterfaceKind.AMMUNITION);
        long ammunition = craft.runtimeState().consumables().interfaceLoads().stream()
                .filter(load -> load.kind() == InterfaceKind.AMMUNITION)
                .mapToLong(load -> load.itemCount())
                .sum();
        if (policy.recoverWhenOutOfAmmunition()
                && ammunitionDependent
                && ammunition <= 0L) {
            return true;
        }
        if (craft.runtimeState().consumables().reactionMassKg()
                <= policy.minimumRecoveryReactionMassKg() + EPSILON) {
            return true;
        }
        double meanStructure = craft.instanceState().damage()
                .compartmentIntegrityById().values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(1d);
        if (meanStructure + EPSILON < policy.minimumRecoveryStructureFraction()) {
            return true;
        }
        double minimumModule = craft.instanceState().damage().moduleDamage()
                .moduleIntegrityByMount().values().stream()
                .mapToDouble(Double::doubleValue)
                .min()
                .orElse(1d);
        return minimumModule + EPSILON < policy.minimumRecoveryModuleFraction();
    }

    private static List<MissionOpportunity> canonicalOpportunities(
            Collection<MissionOpportunity> opportunities,
            long tick) {
        ArrayList<MissionOpportunity> result = new ArrayList<>(opportunities.size());
        Set<String> identities = new HashSet<>();
        for (MissionOpportunity opportunity : opportunities) {
            MissionOpportunity checked = Objects.requireNonNull(opportunity, "mission opportunity");
            if (checked.context().authoritativeTick() != tick) {
                throw new IllegalArgumentException(
                        "AI mission opportunity tick differs from carrier-group observation tick");
            }
            String identity = checked.craftId().value()
                    + "|" + checked.type()
                    + "|" + checked.target().kind()
                    + "|" + checked.target().referenceId()
                    + "|" + checked.axisId();
            if (!identities.add(identity)) {
                throw new IllegalArgumentException("duplicate AI mission opportunity: " + identity);
            }
            result.add(checked);
        }
        result.sort(Comparator
                .comparingInt(MissionOpportunity::priorityBps).reversed()
                .thenComparing(MissionOpportunity::craftId)
                .thenComparing(value -> value.type().name())
                .thenComparing(MissionOpportunity::axisId)
                .thenComparing(value -> value.target().referenceId()));
        return List.copyOf(result);
    }

    private static boolean recoveryMission(MissionType type) {
        return type == MissionType.RETURN
                || type == MissionType.RECOVER
                || type == MissionType.DIVERT;
    }

    private static EscortDirective escortDirective(
            CarrierDisposition disposition,
            MissionOpportunity selected) {
        if (disposition == CarrierDisposition.WITHDRAW) {
            return EscortDirective.COVER_WITHDRAWAL;
        }
        if (selected == null) {
            return EscortDirective.SCREEN_CARRIER;
        }
        if (selected.type() == MissionType.QRA
                || selected.type() == MissionType.INTERCEPTION) {
            return EscortDirective.INTERCEPT_SCREEN;
        }
        if (selected.type() == MissionType.ANTI_SHIP_STRIKE) {
            return EscortDirective.STRIKE_SCREEN;
        }
        return EscortDirective.SCREEN_CARRIER;
    }

    /** Carrier positioning intent; physical movement remains Stage-21/H owned. */
    public enum CarrierDisposition {
        /** Current actor-known geometry and own readiness do not require repositioning. */ HOLD,
        /** Increase separation while preserving carrier-group cohesion through ordinary orders. */ STANDOFF,
        /** Withdraw through ordinary Stage-21 movement/order authority. */ WITHDRAW
    }

    /** Escort doctrine intent; actual escort movement/combat remains ordinary command/tactical authority. */
    public enum EscortDirective {
        /** Preserve a defensive screen around the carrier. */ SCREEN_CARRIER,
        /** Support ordinary interception of an actor-known threat. */ INTERCEPT_SCREEN,
        /** Screen the carrier while the wing executes an ordinary strike mission. */ STRIKE_SCREEN,
        /** Cover carrier/wing withdrawal without synthetic combat effects. */ COVER_WITHDRAWAL
    }

    /** Stable doctrine reason for diagnostics and later UI. */
    public enum DecisionReason {
        /** Own carrier physical readiness crossed the authored withdrawal threshold. */ CARRIER_READINESS,
        /** Own escort aggregate readiness crossed the authored withdrawal threshold. */ ESCORT_READINESS,
        /** Persistent wing losses/depletion crossed the authored readiness threshold. */ WING_DEGRADED,
        /** Fresh severe threat is already inside its actor-known effective range. */ SEVERE_THREAT_INSIDE_RANGE,
        /** Fresh threat is inside the authored carrier standoff envelope. */ THREAT_INSIDE_STANDOFF,
        /** Fresh threat remains outside the authored standoff envelope. */ SAFE_STANDOFF,
        /** No current actor-known threat may lawfully drive hostile doctrine. */ NO_FRESH_THREAT
    }

    /**
     * One actor-known hostile projection. Distances/ranges are evidence inputs, not hidden world truth.
     */
    public record ThreatObservation(
            String referenceId,
            double distanceM,
            double effectiveThreatRangeM,
            double wingStrikeReachM,
            int severityBps,
            ObservationEvidence evidence) {
        public ThreatObservation {
            referenceId = requireText(referenceId, "referenceId");
            requireNonNegative(distanceM, "distanceM");
            requireNonNegative(effectiveThreatRangeM, "effectiveThreatRangeM");
            requireNonNegative(wingStrikeReachM, "wingStrikeReachM");
            requireBps(severityBps, "severityBps");
            Objects.requireNonNull(evidence, "evidence");
        }
    }

    /**
     * Read-only carrier-group snapshot for one authoritative doctrine tick.
     */
    public record CarrierGroupObservation(
            long authoritativeTick,
            long commandGroupId,
            FleetId carrierFleetId,
            List<FleetId> escortFleetIds,
            List<SmallCraftId> wingCraftIds,
            FleetReadinessState carrierReadiness,
            FleetReadinessState escortReadiness,
            ThreatObservation threat) {
        public CarrierGroupObservation {
            if (authoritativeTick < 0L) {
                throw new IllegalArgumentException("authoritativeTick must be non-negative");
            }
            if (commandGroupId <= 0L) {
                throw new IllegalArgumentException("commandGroupId must be positive");
            }
            Objects.requireNonNull(carrierFleetId, "carrierFleetId");
            escortFleetIds = canonicalUnique(escortFleetIds, "escortFleetIds");
            wingCraftIds = canonicalUnique(wingCraftIds, "wingCraftIds");
            Objects.requireNonNull(carrierReadiness, "carrierReadiness");
            Objects.requireNonNull(escortReadiness, "escortReadiness");
        }
    }

    /**
     * One potential AI mission passed to the shared D command validator.
     *
     * @param axisId actor-authored scheduling/approach axis identity; no combat modifier
     */
    public record MissionOpportunity(
            SmallCraftId craftId,
            MissionType type,
            MissionTarget target,
            MissionContext context,
            String axisId,
            int priorityBps) {
        public MissionOpportunity {
            Objects.requireNonNull(craftId, "craftId");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(context, "context");
            axisId = requireText(axisId, "axisId");
            requireBps(priorityBps, "priorityBps");
        }
    }

    /**
     * Authored doctrine thresholds. They change decisions only and never physical performance.
     */
    public record DoctrinePolicy(
            int carrierWithdrawReadinessBps,
            int escortWithdrawReadinessBps,
            int wingWithdrawReadinessBps,
            int minimumCapCraft,
            int maxActiveInterceptCraft,
            int maxActiveStrikeCraft,
            int interceptThreatSeverityBps,
            int strikeThreatSeverityBps,
            int severeThreatWithdrawBps,
            double standoffRangeMultiplier,
            boolean recoverWhenOutOfAmmunition,
            double minimumRecoveryReactionMassKg,
            double minimumRecoveryStructureFraction,
            double minimumRecoveryModuleFraction) {
        public DoctrinePolicy {
            requireBps(carrierWithdrawReadinessBps, "carrierWithdrawReadinessBps");
            requireBps(escortWithdrawReadinessBps, "escortWithdrawReadinessBps");
            requireBps(wingWithdrawReadinessBps, "wingWithdrawReadinessBps");
            if (minimumCapCraft < 0
                    || maxActiveInterceptCraft < 0
                    || maxActiveStrikeCraft < 0) {
                throw new IllegalArgumentException("craft-count doctrine thresholds must be non-negative");
            }
            requireBps(interceptThreatSeverityBps, "interceptThreatSeverityBps");
            requireBps(strikeThreatSeverityBps, "strikeThreatSeverityBps");
            requireBps(severeThreatWithdrawBps, "severeThreatWithdrawBps");
            if (!Double.isFinite(standoffRangeMultiplier) || standoffRangeMultiplier < 1d) {
                throw new IllegalArgumentException(
                        "standoffRangeMultiplier must be finite and at least 1");
            }
            requireNonNegative(
                    minimumRecoveryReactionMassKg,
                    "minimumRecoveryReactionMassKg");
            requireFraction(
                    minimumRecoveryStructureFraction,
                    "minimumRecoveryStructureFraction");
            requireFraction(
                    minimumRecoveryModuleFraction,
                    "minimumRecoveryModuleFraction");
        }

        /** Conservative production baseline; values are policy only, not stat bonuses. */
        public static DoctrinePolicy standard() {
            return new DoctrinePolicy(
                    3_500,
                    2_500,
                    2_000,
                    1,
                    2,
                    4,
                    3_000,
                    4_500,
                    8_000,
                    1.25d,
                    true,
                    1d,
                    0.45d,
                    0.35d);
        }
    }

    /** Read-only derived wing readiness diagnostics. */
    public record WingReadiness(
            int operationalCraft,
            int readyCraft,
            int depletedCraft,
            int readyBps) {
        public WingReadiness {
            if (operationalCraft < 0 || readyCraft < 0 || depletedCraft < 0) {
                throw new IllegalArgumentException("wing readiness counts must be non-negative");
            }
            requireBps(readyBps, "readyBps");
        }
    }

    /** One selected carrier doctrine state and optional next AI mission. */
    public record DoctrineDecision(
            CarrierDisposition carrierDisposition,
            EscortDirective escortDirective,
            DecisionReason reason,
            int wingReadyBps,
            MissionOpportunity selectedMission) {
        public DoctrineDecision {
            Objects.requireNonNull(carrierDisposition, "carrierDisposition");
            Objects.requireNonNull(escortDirective, "escortDirective");
            Objects.requireNonNull(reason, "reason");
            requireBps(wingReadyBps, "wingReadyBps");
        }

        public Optional<MissionOpportunity> selectedMissionOptional() {
            return Optional.ofNullable(selectedMission);
        }
    }

    /** Result after at most one real shared-path AI mission submission. */
    public record DecisionResult(
            SmallCraftMissionState missionState,
            DoctrineDecision decision,
            long acceptedMissionId,
            long supersededMissionId) {
        public DecisionResult {
            Objects.requireNonNull(missionState, "missionState");
            Objects.requireNonNull(decision, "decision");
            if (acceptedMissionId < 0L || supersededMissionId < 0L) {
                throw new IllegalArgumentException("mission IDs cannot be negative");
            }
            if ((acceptedMissionId == 0L) != decision.selectedMissionOptional().isEmpty()) {
                throw new IllegalArgumentException(
                        "acceptedMissionId must exist exactly when a mission was selected");
            }
        }
    }

    private record FreshThreat(boolean present, ThreatObservation threat) {
        static FreshThreat absent() {
            return new FreshThreat(false, null);
        }

        private FreshThreat {
            if (present != (threat != null)) {
                throw new IllegalArgumentException("fresh-threat presence mismatch");
            }
        }
    }

    private record DispositionDecision(
            CarrierDisposition disposition,
            DecisionReason reason) {
        private DispositionDecision {
            Objects.requireNonNull(disposition, "disposition");
            Objects.requireNonNull(reason, "reason");
        }
    }

    private static <T extends Comparable<? super T>> List<T> canonicalUnique(
            Collection<T> values,
            String label) {
        Objects.requireNonNull(values, label);
        ArrayList<T> canonical = new ArrayList<>(values.size());
        Set<T> unique = new HashSet<>();
        for (T value : values) {
            T checked = Objects.requireNonNull(value, label + " item");
            if (!unique.add(checked)) {
                throw new IllegalArgumentException("duplicate " + label + " item: " + checked);
            }
            canonical.add(checked);
        }
        canonical.sort(Comparator.naturalOrder());
        return List.copyOf(canonical);
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label).strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }

    private static void requireBps(int value, String label) {
        if (value < 0 || value > FleetReadinessState.FULL) {
            throw new IllegalArgumentException(label + " must be in 0..10000");
        }
    }

    private static void requireNonNegative(double value, String label) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }

    private static void requireFraction(double value, String label) {
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException(label + " must be in [0,1]");
        }
    }
}
