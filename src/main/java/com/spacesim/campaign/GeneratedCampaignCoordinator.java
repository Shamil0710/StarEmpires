package com.spacesim.campaign;

import com.spacesim.campaign.GeneratedCampaignAuthorityCheckpoint.RestoredAuthorities;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimeMigration;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.warfare.Stage19ConflictRuntime;
import com.spacesim.world.DiplomaticLifecycleService;
import com.spacesim.world.FactionActorObservationSnapshot;
import com.spacesim.world.FleetCommandGroupService;
import com.spacesim.world.FleetCommandState;
import com.spacesim.world.FleetCommandState.CommandGroupState;
import com.spacesim.world.FleetForceRegistry;
import com.spacesim.world.FleetId;
import com.spacesim.world.Stage21HNpcMissionService;
import com.spacesim.world.StarSystemId;

import java.util.List;
import java.util.Objects;

/**
 * Production orchestration boundary for one complete generated campaign.
 *
 * <p>The coordinator deliberately owns no competing gameplay authority. The mutable physical world,
 * economy, freight and clocks remain inside {@link GeneratedCampaignSession}; Stage-21 autonomous
 * actor state remains inside its accepted owner; Stage-21C diplomacy, Stage-19 conflict state and
 * Stage-21H NPC/mission state are materialized through their accepted mutable services over that
 * same campaign. Stage-21D command metadata is retained only as the latest immutable canonical state
 * returned by its accepted production services. Stage-21E-G canonical values remain exact persisted
 * snapshots until their existing production services are composed. This class only keeps those
 * accepted owners together so the ordinary client creates, advances, saves and restores one coherent
 * campaign instead of saving the Stage-20 runtime in isolation.</p>
 *
 * <p>New campaigns are lifted through the accepted Stage-21 migration path. Existing Stage-20.5
 * saves can therefore enter the same coordinator without ad-hoc default reconstruction, while
 * native Stage-21I saves retain the complete accepted authority chain.</p>
 */
public final class GeneratedCampaignCoordinator {
    /** Accepted Stage-21I workload evidence caps expensive actor reviews at seven per batch. */
    static final int LIVING_ACTOR_REVIEW_BUDGET = 7;

    /**
     * Deterministic medium strategic cadence for ordinary campaign actor reviews.
     *
     * <p>The value is expressed in authoritative simulation ticks and is persisted indirectly by
     * each actor's next-review deadline. Event wakeups may still authorize an earlier review. The
     * coordinator does not poll actors from wall-clock/render time.</p>
     */
    static final long LIVING_ACTOR_REVIEW_CADENCE_TICKS = 1_200L;

    private final RestoredAuthorities authorities;
    private FleetCommandState fleetCommands;

    private GeneratedCampaignCoordinator(RestoredAuthorities authorities) {
        this.authorities = Objects.requireNonNull(authorities, "authorities");
        this.fleetCommands = authorities.commands();
    }

    /**
     * Creates a new generated campaign and composes the accepted Stage-21 authority chain around it.
     *
     * @param rootSeed deterministic generated-world seed
     * @return one production campaign coordinator
     */
    public static GeneratedCampaignCoordinator create(long rootSeed) {
        GeneratedCampaignSession session = GeneratedCampaignSession.create(rootSeed);
        Stage21IGeneratedWorldRuntimePersistentState initial =
                Stage21IGeneratedWorldRuntimeMigration.migrate(session.captureState());
        return restore(initial);
    }

    /**
     * Restores one native or already migrated final Stage-21 checkpoint.
     *
     * @param checkpoint complete accepted campaign checkpoint
     * @return restored coordinator
     */
    public static GeneratedCampaignCoordinator restore(
            Stage21IGeneratedWorldRuntimePersistentState checkpoint) {
        return new GeneratedCampaignCoordinator(
                GeneratedCampaignAuthorityCheckpoint.restore(
                        Objects.requireNonNull(checkpoint, "checkpoint")));
    }

    /**
     * Restores a native Stage-21I payload or migrates a supported Stage-20.5/21A-H payload.
     *
     * @param bytes persisted generated-campaign bytes
     * @return restored coordinator
     */
    public static GeneratedCampaignCoordinator decodeOrMigrate(byte[] bytes) {
        return restore(Stage21IGeneratedWorldRuntimePersistenceCodec.decodeOrMigrate(
                Objects.requireNonNull(bytes, "bytes")));
    }

    /** @return the single mutable ordinary generated-world session used by simulation and UI */
    public GeneratedCampaignSession session() {
        return authorities.session();
    }

    /**
     * Returns the accepted mutable Stage-21C diplomacy owner over this campaign's ordinary world.
     *
     * <p>Player and autonomous callers must use this service rather than mutating persisted
     * diplomatic snapshots or constructing a second lifecycle owner.</p>
     *
     * @return shared campaign diplomacy authority
     */
    public DiplomaticLifecycleService diplomacy() {
        return authorities.diplomacyService();
    }

    /**
     * Returns the accepted mutable Stage-19 actor-known conflict owner shared by diplomacy.
     *
     * <p>The runtime remains an information/policy extension; physical warfare consequences are
     * still owned by their existing simulation and combat authorities.</p>
     *
     * @return shared campaign warfare authority
     */
    public Stage19ConflictRuntime warfare() {
        return authorities.warfareRuntime();
    }

    /**
     * Returns the current canonical Stage-21D command metadata.
     *
     * <p>The returned value is immutable. Physical fleets remain owned by the ordinary world; this
     * coordinator only retains replacements returned by the accepted Stage-21D command services.</p>
     *
     * @return current immutable fleet-command state
     */
    public FleetCommandState fleetCommands() {
        return fleetCommands;
    }

    /**
     * Forms one Stage-21D command group over caller-provided read-only reconstruction of ordinary fleets.
     *
     * <p>This is a production orchestration seam, not a second fleet authority: validation and identity
     * allocation are delegated to {@link FleetCommandGroupService}, while the supplied registry remains
     * a read-only projection of the same physical world. No fleet is moved, created or reassigned here.</p>
     *
     * @param forces read-only reconstruction of ordinary physical fleets
     * @param factionId owning dense faction identifier
     * @param name display name
     * @param memberFleetIds ordinary fleet identities to wrap
     * @param homeSystemId designated home system
     * @param reserve whether the group is held as reserve
     * @param homeDefense whether the group is restricted to home-defense offensive commitments
     * @param maxStrategicRiskBps maximum accepted route risk in basis points
     * @return newly allocated canonical command-group metadata
     */
    public CommandGroupState formFleetCommandGroup(
            FleetForceRegistry forces,
            int factionId,
            String name,
            List<FleetId> memberFleetIds,
            StarSystemId homeSystemId,
            boolean reserve,
            boolean homeDefense,
            int maxStrategicRiskBps) {
        FleetCommandGroupService.FormationResult formed = new FleetCommandGroupService(
                session().runtime().world().getTopology()).form(
                        fleetCommands,
                        Objects.requireNonNull(forces, "forces"),
                        factionId,
                        name,
                        memberFleetIds,
                        homeSystemId,
                        reserve,
                        homeDefense,
                        maxStrategicRiskBps);
        fleetCommands = formed.state();
        return formed.group();
    }

    /**
     * Returns the accepted mutable Stage-21H NPC/mission/reputation/story owner.
     *
     * <p>Mission callers must continue to pass ordinary world, freight, industry, discovery and
     * operation authorities required by the service. This accessor does not create a parallel truth
     * source; it exposes the service that owns only the accepted Stage-21H RPG sidecar.</p>
     *
     * @return shared campaign Stage-21H mission authority
     */
    public Stage21HNpcMissionService npcMissions() {
        return authorities.npcMissionService();
    }

    /**
     * Advances the ordinary physical campaign and then runs the due Stage-21A actor lifecycle at
     * the resulting authoritative world tick.
     *
     * <p>This method is the production orchestration seam intentionally absent from the Stage-21A
     * physical runtime bridge. Actor review stays outside Stage-20 simulation because publishing
     * actor knowledge is an explicit information-boundary step. The current composed client has no
     * accepted Stage-20-to-Stage-21 observation publisher yet, so this handoff publishes an honest
     * empty actor-bounded snapshot rather than manufacturing omniscient observations. That advances
     * persisted lifecycle/deadline state without inventing strategic evidence; later M22.7 slices
     * can replace the publisher with accepted delivered knowledge while retaining the same runtime
     * owner and scheduler.</p>
     *
     * <p>No living-world review executes when the physical session advances zero fixed ticks, so a
     * paused campaign or a zero-delta render cannot mutate Stage-21 state after load.</p>
     *
     * @param realDeltaSeconds finite non-negative presentation delta
     * @return the ordinary physical campaign advance diagnostics
     */
    public GeneratedCampaignSession.AdvanceReport advanceFrame(float realDeltaSeconds) {
        GeneratedCampaignSession.AdvanceReport report = authorities.session().advanceFrame(realDeltaSeconds);
        if (report.fixedTicks() == 0L) {
            return report;
        }

        long nowTick = authorities.session().runtime().world().getAuthoritativeWorldTick();
        authorities.actors().reviewDue(
                nowTick,
                LIVING_ACTOR_REVIEW_BUDGET,
                LIVING_ACTOR_REVIEW_CADENCE_TICKS,
                factionId -> emptyActorSnapshot(factionId, nowTick));
        return report;
    }

    /**
     * Captures one native Stage-21I checkpoint from the currently composed accepted authorities.
     *
     * @return complete campaign checkpoint
     */
    public Stage21IGeneratedWorldRuntimePersistentState captureState() {
        return GeneratedCampaignAuthorityCheckpoint.capture(
                authorities.session(),
                authorities.actors(),
                authorities.strategicIntents(),
                authorities.diplomacy(),
                authorities.warfare(),
                fleetCommands,
                authorities.operations(),
                authorities.transitions(),
                authorities.recovery(),
                authorities.npcMissions());
    }

    /**
     * Encodes the complete current campaign with the accepted final Stage-21 codec.
     *
     * @return deterministic native Stage-21I bytes
     */
    public byte[] encode() {
        return Stage21IGeneratedWorldRuntimePersistenceCodec.encode(captureState());
    }

    /**
     * Compatibility helper used by migration tests to prove that an old Stage-20.5 checkpoint enters
     * exactly the same production composition path as a native save.
     *
     * @param session ordinary campaign whose accepted Stage-20.5 state should be migrated
     * @return coordinator restored through the public migration codec boundary
     */
    static GeneratedCampaignCoordinator migrateStage20(GeneratedCampaignSession session) {
        byte[] legacy = Stage20GeneratedWorldRuntimePersistenceCodec.encode(
                Objects.requireNonNull(session, "session").captureState());
        return decodeOrMigrate(legacy);
    }

    /**
     * Exposes the accepted handoff only to package integration code and tests. Gameplay systems must
     * continue to mutate their original owners rather than this orchestration object.
     *
     * @return currently composed authority references and snapshots
     */
    RestoredAuthorities authorities() {
        return authorities;
    }

    private static FactionActorObservationSnapshot emptyActorSnapshot(String factionId, long nowTick) {
        return new FactionActorObservationSnapshot(
                factionId,
                nowTick,
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }
}
