package com.spacesim.world;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipProtectionCatalog;
import com.spacesim.content.ship.Stage22CorePairProtectionCatalogLoader;
import com.spacesim.content.weapon.Stage22CorePairWeaponRuntimeCatalogLoader;
import com.spacesim.content.weapon.Stage22CorePairWeaponRuntimeCatalogLoader.RuntimeContent;
import com.spacesim.ship.LiveTacticalBattleRuntimeState.ImportedCombatantState;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.Stage19ExactTacticalEncounterResolver;
import com.spacesim.ship.Stage19ExactTacticalEncounterResolver.CombatantResult;
import com.spacesim.ship.Stage19ExactTacticalEncounterResolver.Result;
import com.spacesim.ship.Stage19ExactTacticalEncounterResolver.Termination;
import com.spacesim.world.SmallCraftMissionState.MissionOrder;
import com.spacesim.world.SmallCraftMissionState.MissionStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * M22.8E exact-local tactical bridge for persistent individual small craft.
 *
 * <p>This service owns no combat physics. It binds already deployed {@link SmallCraftId} assets and
 * optional exact external combatants into the accepted Stage-19 exact resolver, validates the
 * detached result against the pre-exchange physical state, then commits only lawful consequences:
 * survivor engineering state returns to the same craft identity, while catastrophically destroyed
 * craft are permanently removed without allocator rewind or replacement. External combatants are
 * returned to the caller for their own existing world authority to commit.</p>
 *
 * <p>Because every participant shares one {@link Stage19ExactTacticalEncounterResolver}, small craft
 * receive no fighter-only movement, sensor, track, weapon, point-defence, EW, decoy, shield or damage
 * rules. The Stage-19 runtime remains the sole tactical authority.</p>
 */
public final class SmallCraftTacticalEncounterService {
    private final SmallCraftRegistry craftRegistry;
    private final SmallCraftHangarRegistry hangars;
    private final TacticalAuthority tactical;

    /**
     * Creates the production M22.8E bridge on the combined Stage-22 physical content universe.
     *
     * @param craftRegistry persistent small-craft identity/engineering authority
     * @param hangars physical embarked-occupancy authority
     * @return exact Stage-19 tactical bridge
     */
    public static SmallCraftTacticalEncounterService production(
            SmallCraftRegistry craftRegistry,
            SmallCraftHangarRegistry hangars) {
        SmallCraftRegistry registry = Objects.requireNonNull(craftRegistry, "craftRegistry");
        RuntimeContent content = Stage22CorePairWeaponRuntimeCatalogLoader.loadCombined();
        if (!registry.engineeringCatalogFingerprint().equals(content.engineering().getFingerprint())) {
            throw new IllegalArgumentException(
                    "Small-craft registry and tactical runtime must use the same engineering catalog");
        }
        ShipProtectionCatalog protection =
                Stage22CorePairProtectionCatalogLoader.project(content.engineering());
        Stage19ExactTacticalEncounterResolver resolver =
                new Stage19ExactTacticalEncounterResolver(
                        content.engineering(),
                        protection,
                        content.ammunition(),
                        content.launchers());
        return new SmallCraftTacticalEncounterService(
                registry,
                Objects.requireNonNull(hangars, "hangars"),
                resolver::resolve);
    }

    SmallCraftTacticalEncounterService(
            SmallCraftRegistry craftRegistry,
            SmallCraftHangarRegistry hangars,
            TacticalAuthority tactical) {
        this.craftRegistry = Objects.requireNonNull(craftRegistry, "craftRegistry");
        this.hangars = Objects.requireNonNull(hangars, "hangars");
        this.tactical = Objects.requireNonNull(tactical, "tactical");
    }

    /**
     * Resolves one bounded exact Stage-19 encounter and commits small-craft consequences.
     *
     * @param missionState current immutable mission authority
     * @param smallCraft deployed persistent small-craft participants
     * @param externalCombatants exact non-small-craft participants committed by another authority
     * @param maximumTicks positive Stage-19 fixed-tick horizon
     * @return updated mission state plus canonical exact encounter outcomes
     */
    public EncounterResult resolve(
            SmallCraftMissionState missionState,
            Collection<SmallCraftParticipant> smallCraft,
            Collection<ExternalCombatant> externalCombatants,
            long maximumTicks) {
        SmallCraftMissionState missions = Objects.requireNonNull(missionState, "missionState");
        Objects.requireNonNull(smallCraft, "smallCraft");
        Objects.requireNonNull(externalCombatants, "externalCombatants");
        if (maximumTicks <= 0L) {
            throw new IllegalArgumentException("maximumTicks must be positive");
        }

        TreeMap<String, BoundParticipant> canonical = new TreeMap<>();
        TreeSet<SmallCraftId> craftIds = new TreeSet<>();
        for (SmallCraftParticipant participant : smallCraft) {
            SmallCraftParticipant checked = Objects.requireNonNull(participant, "small-craft participant");
            if (!craftIds.add(checked.craftId())) {
                throw new IllegalArgumentException(
                        "duplicate small-craft tactical participant: " + checked.craftId());
            }
            SmallCraftState before = craftRegistry.find(checked.craftId()).orElseThrow(
                    () -> new IllegalArgumentException(
                            "unknown small-craft tactical participant: " + checked.craftId()));
            if (hangars.find(checked.craftId()).isPresent()) {
                throw new IllegalStateException(
                        "tactical small craft must be physically deployed outside bay occupancy: "
                                + checked.craftId());
            }
            MissionOrder mission = missions.activeMissionFor(checked.craftId()).orElseThrow(
                    () -> new IllegalStateException(
                            "deployed tactical small craft requires one active mission: "
                                + checked.craftId()));
            if (mission.status() != MissionStatus.ACTIVE
                    && mission.status() != MissionStatus.RETURNING) {
                throw new IllegalStateException(
                        "small-craft tactical materialization requires ACTIVE or RETURNING mission");
            }
            String key = "craft:" + checked.craftId().value();
            canonical.put(key, BoundParticipant.smallCraft(checked, before, mission));
        }

        TreeSet<String> externalIds = new TreeSet<>();
        for (ExternalCombatant participant : externalCombatants) {
            ExternalCombatant checked = Objects.requireNonNull(participant, "external participant");
            if (!externalIds.add(checked.referenceId())) {
                throw new IllegalArgumentException(
                        "duplicate external tactical reference: " + checked.referenceId());
            }
            canonical.put("external:" + checked.referenceId(), BoundParticipant.external(checked));
        }

        if (craftIds.isEmpty()) {
            throw new IllegalArgumentException("M22.8E encounter requires at least one small craft");
        }
        if (canonical.size() < 2) {
            throw new IllegalArgumentException("exact tactical encounter requires at least two combatants");
        }
        boolean alpha = canonical.values().stream().anyMatch(value -> value.side() == Side.ALPHA);
        boolean beta = canonical.values().stream().anyMatch(value -> value.side() == Side.BETA);
        if (!alpha || !beta) {
            throw new IllegalArgumentException("exact tactical encounter requires both physical sides");
        }

        TreeMap<Long, BoundParticipant> byTacticalId = new TreeMap<>();
        ArrayList<ImportedCombatantState> imported = new ArrayList<>(canonical.size());
        long tacticalId = 1L;
        for (BoundParticipant binding : canonical.values()) {
            EngineeringComponent source = binding.engineering();
            EngineeringComponent detached = new EngineeringComponent(
                    source.fit, source.runtimeState, source.instanceState);
            byTacticalId.put(tacticalId, binding);
            imported.add(new ImportedCombatantState(
                    tacticalId,
                    binding.side(),
                    binding.stableFactionId(),
                    detached,
                    binding.flight().xM(),
                    binding.flight().yM(),
                    binding.flight().velocityXMps(),
                    binding.flight().velocityYMps()));
            tacticalId = Math.addExact(tacticalId, 1L);
        }

        Result result = Objects.requireNonNull(
                tactical.resolve(List.copyOf(imported), maximumTicks),
                "tactical result");
        validateResultRoster(byTacticalId, result);

        // Validate every commit target before mutating any persistent small-craft state.
        TreeMap<SmallCraftId, SmallCraftState> survivorStates = new TreeMap<>();
        for (Map.Entry<Long, BoundParticipant> entry : byTacticalId.entrySet()) {
            BoundParticipant binding = entry.getValue();
            CombatantResult after = result.require(entry.getKey());
            if (!after.fit().equals(binding.engineering().fit)) {
                throw new IllegalStateException("Stage-19 tactical result attempted to replace installed fit");
            }
            if (binding.smallCraftId() == null) {
                continue;
            }
            SmallCraftId id = binding.smallCraftId();
            SmallCraftState current = craftRegistry.find(id).orElseThrow(
                    () -> new IllegalStateException(
                            "small craft disappeared during detached tactical resolution: " + id));
            if (!current.equals(binding.beforeState())) {
                throw new IllegalStateException(
                        "small-craft physical state changed during detached tactical resolution: " + id);
            }
            if (hangars.find(id).isPresent()) {
                throw new IllegalStateException(
                        "small craft became embarked during detached tactical resolution: " + id);
            }
            MissionOrder active = missions.activeMissionFor(id).orElseThrow(
                    () -> new IllegalStateException(
                            "small-craft mission disappeared during detached tactical resolution: " + id));
            if (active.id() != binding.missionId()
                    || (active.status() != MissionStatus.ACTIVE
                    && active.status() != MissionStatus.RETURNING)) {
                throw new IllegalStateException(
                        "small-craft mission changed during detached tactical resolution: " + id);
            }
            if (!after.destroyed()) {
                SmallCraftState before = binding.beforeState();
                SmallCraftState candidate = new SmallCraftState(
                        before.id(),
                        before.stableFactionId(),
                        before.designId(),
                        after.fit(),
                        after.runtimeState(),
                        after.instanceState());
                craftRegistry.candidatePhysicalFootprint(candidate);
                survivorStates.put(id, candidate);
            }
        }

        SmallCraftMissionState updatedMissions = missions;
        ArrayList<SmallCraftOutcome> craftOutcomes = new ArrayList<>();
        ArrayList<ExternalOutcome> externalOutcomes = new ArrayList<>();
        for (Map.Entry<Long, BoundParticipant> entry : byTacticalId.entrySet()) {
            long id = entry.getKey();
            BoundParticipant binding = entry.getValue();
            CombatantResult after = result.require(id);
            LocalFlightState finalFlight = new LocalFlightState(
                    after.xM(),
                    after.yM(),
                    after.velocityXMps(),
                    after.velocityYMps());
            if (binding.smallCraftId() != null) {
                SmallCraftId craftId = binding.smallCraftId();
                if (after.destroyed()) {
                    craftRegistry.removeDestroyedCraft(craftId);
                    MissionOrder mission = updatedMissions.requireMission(binding.missionId());
                    updatedMissions = updatedMissions.replace(mission.withStatus(MissionStatus.FAILED));
                } else {
                    craftRegistry.replacePhysicalState(survivorStates.get(craftId));
                }
                craftOutcomes.add(new SmallCraftOutcome(
                        craftId,
                        binding.missionId(),
                        after.destroyed(),
                        finalFlight));
            } else {
                ExternalCombatant external = binding.external();
                EngineeringComponent finalEngineering = new EngineeringComponent(
                        after.fit(), after.runtimeState(), after.instanceState());
                externalOutcomes.add(new ExternalOutcome(
                        external.referenceId(),
                        after.destroyed(),
                        finalEngineering,
                        finalFlight));
            }
        }
        craftOutcomes.sort(Comparator.comparing(SmallCraftOutcome::craftId));
        externalOutcomes.sort(Comparator.comparing(ExternalOutcome::referenceId));
        return new EncounterResult(
                updatedMissions,
                result.ticksExecuted(),
                result.termination(),
                List.copyOf(craftOutcomes),
                List.copyOf(externalOutcomes));
    }

    /**
     * Resolves with the accepted bounded Stage-19 default horizon.
     *
     * @param missionState current immutable small-craft mission authority
     * @param smallCraft deployed persistent small-craft participants
     * @param externalCombatants exact non-small-craft participants committed by another authority
     * @return updated mission state plus canonical exact encounter outcomes
     */
    public EncounterResult resolve(
            SmallCraftMissionState missionState,
            Collection<SmallCraftParticipant> smallCraft,
            Collection<ExternalCombatant> externalCombatants) {
        return resolve(
                missionState,
                smallCraft,
                externalCombatants,
                Stage19ExactTacticalEncounterResolver.DEFAULT_MAXIMUM_TICKS);
    }

    private static void validateResultRoster(
            Map<Long, BoundParticipant> expected,
            Result result) {
        if (result.combatants().size() != expected.size()) {
            throw new IllegalStateException("Stage-19 tactical result changed combatant roster size");
        }
        for (Long tacticalId : expected.keySet()) {
            result.require(tacticalId);
        }
        for (CombatantResult row : result.combatants()) {
            if (!expected.containsKey(row.entityId())) {
                throw new IllegalStateException(
                        "Stage-19 tactical result introduced unknown combatant: " + row.entityId());
            }
        }
    }

    /** Exact deployed local kinematics carried across the tactical boundary. */
    public record LocalFlightState(
            double xM,
            double yM,
            double velocityXMps,
            double velocityYMps) {
        /**
         * Validates finite encounter-local position and velocity.
         *
         * @param xM local x position in meters
         * @param yM local y position in meters
         * @param velocityXMps local x velocity in meters per second
         * @param velocityYMps local y velocity in meters per second
         */
        public LocalFlightState {
            if (!Double.isFinite(xM)
                    || !Double.isFinite(yM)
                    || !Double.isFinite(velocityXMps)
                    || !Double.isFinite(velocityYMps)) {
                throw new IllegalArgumentException("small-craft local flight state must be finite");
            }
        }
    }

    /** One persistent small craft admitted to the exact local encounter. */
    public record SmallCraftParticipant(
            SmallCraftId craftId,
            Side side,
            LocalFlightState flight) {
        /**
         * Validates one persistent small-craft encounter participant.
         *
         * @param craftId persistent craft identity
         * @param side exact Stage-19 combat side
         * @param flight exact encounter-local kinematics
         */
        public SmallCraftParticipant {
            Objects.requireNonNull(craftId, "craftId");
            Objects.requireNonNull(side, "side");
            Objects.requireNonNull(flight, "flight");
        }
    }

    /**
     * Exact external participant such as a carrier, escort or other ordinary Stage-19-capable actor.
     *
     * <p>The service never commits this participant. Its final state is returned to the caller so the
     * pre-existing world authority can apply the same stale-safe commit rules it already owns.</p>
     */
    public record ExternalCombatant(
            String referenceId,
            Side side,
            String stableFactionId,
            EngineeringComponent engineering,
            LocalFlightState flight) {
        /**
         * Validates one exact external Stage-19 participant.
         *
         * @param referenceId caller-owned stable participant reference
         * @param side exact Stage-19 combat side
         * @param stableFactionId stable owning faction identity
         * @param engineering detached exact engineering state
         * @param flight exact encounter-local kinematics
         */
        public ExternalCombatant {
            referenceId = requireText(referenceId, "referenceId");
            Objects.requireNonNull(side, "side");
            stableFactionId = requireText(stableFactionId, "stableFactionId");
            Objects.requireNonNull(engineering, "engineering");
            Objects.requireNonNull(engineering.fit, "engineering.fit");
            Objects.requireNonNull(engineering.runtimeState, "engineering.runtimeState");
            Objects.requireNonNull(engineering.instanceState, "engineering.instanceState");
            Objects.requireNonNull(flight, "flight");
        }
    }

    /** Final committed outcome for one persistent small craft. */
    public record SmallCraftOutcome(
            SmallCraftId craftId,
            long missionId,
            boolean destroyed,
            LocalFlightState finalFlight) {
        /**
         * Validates one committed persistent small-craft outcome.
         *
         * @param craftId persistent craft identity
         * @param missionId owning mission identity
         * @param destroyed whether exact Stage-19 resolution destroyed the craft
         * @param finalFlight final encounter-local kinematics
         */
        public SmallCraftOutcome {
            Objects.requireNonNull(craftId, "craftId");
            if (missionId <= 0L) {
                throw new IllegalArgumentException("missionId must be positive");
            }
            Objects.requireNonNull(finalFlight, "finalFlight");
        }
    }

    /** Exact detached result for one non-small-craft participant. */
    public record ExternalOutcome(
            String referenceId,
            boolean destroyed,
            EngineeringComponent engineering,
            LocalFlightState finalFlight) {
        /**
         * Validates one detached external-combatant outcome.
         *
         * @param referenceId caller-owned stable participant reference
         * @param destroyed whether exact Stage-19 resolution destroyed the participant
         * @param engineering final detached exact engineering state
         * @param finalFlight final encounter-local kinematics
         */
        public ExternalOutcome {
            referenceId = requireText(referenceId, "referenceId");
            Objects.requireNonNull(engineering, "engineering");
            Objects.requireNonNull(finalFlight, "finalFlight");
        }
    }

    /** Complete M22.8E commit result. */
    public record EncounterResult(
            SmallCraftMissionState missionState,
            long ticksExecuted,
            Termination termination,
            List<SmallCraftOutcome> smallCraft,
            List<ExternalOutcome> externalCombatants) {
        /**
         * Validates and freezes one complete tactical commit result.
         *
         * @param missionState updated persistent mission state
         * @param ticksExecuted exact Stage-19 ticks executed
         * @param termination bounded encounter termination reason
         * @param smallCraft committed small-craft outcomes
         * @param externalCombatants detached external-combatant outcomes
         */
        public EncounterResult {
            Objects.requireNonNull(missionState, "missionState");
            if (ticksExecuted < 0L) {
                throw new IllegalArgumentException("ticksExecuted must be non-negative");
            }
            Objects.requireNonNull(termination, "termination");
            smallCraft = List.copyOf(Objects.requireNonNull(smallCraft, "smallCraft"));
            externalCombatants =
                    List.copyOf(Objects.requireNonNull(externalCombatants, "externalCombatants"));
        }
    }

    @FunctionalInterface
    interface TacticalAuthority {
        Result resolve(List<ImportedCombatantState> imported, long maximumTicks);
    }

    private record BoundParticipant(
            SmallCraftId smallCraftId,
            long missionId,
            SmallCraftState beforeState,
            ExternalCombatant external,
            Side side,
            String stableFactionId,
            EngineeringComponent engineering,
            LocalFlightState flight) {
        static BoundParticipant smallCraft(
                SmallCraftParticipant participant,
                SmallCraftState before,
                MissionOrder mission) {
            EngineeringComponent engineering =
                    SmallCraftEngineeringMaterializationBridge.materialize(before);
            return new BoundParticipant(
                    participant.craftId(),
                    mission.id(),
                    before,
                    null,
                    participant.side(),
                    before.stableFactionId(),
                    engineering,
                    participant.flight());
        }

        static BoundParticipant external(ExternalCombatant participant) {
            EngineeringComponent engineering = participant.engineering();
            return new BoundParticipant(
                    null,
                    0L,
                    null,
                    participant,
                    participant.side(),
                    participant.stableFactionId(),
                    engineering,
                    participant.flight());
        }
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label).strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}
