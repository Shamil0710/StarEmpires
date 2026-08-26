package com.spacesim.ui;

import java.util.List;
import java.util.Objects;

/**
 * Read-only Stage-21I living-world projection for one actor-bounded viewer.
 *
 * <p>The snapshot contains presentation values and explicit provenance only. It owns no simulation
 * authority and exposes no mutation command. Callers must submit changes through the existing
 * validated world/strategic command boundaries.</p>
 */
public record Stage21ILivingWorldUiSnapshot(
        String viewerFactionId,
        long simulationTick,
        List<FactionRow> factions,
        List<MilitaryRow> military,
        List<TimelineRow> timeline,
        List<NpcMissionRow> npcMissions) {

    /**
     * Validates and freezes all top-level actor-bounded presentation collections.
     *
     * @param viewerFactionId stable faction id of the requesting viewer
     * @param simulationTick authoritative simulation tick represented by the projection
     * @param factions actor-bounded faction rows
     * @param military viewer-owned military rows
     * @param timeline actor-bounded event rows
     * @param npcMissions viewer-visible NPC and mission rows
     */
    public Stage21ILivingWorldUiSnapshot {
        viewerFactionId = requireText(viewerFactionId, "viewerFactionId");
        if (simulationTick < 0L) throw new IllegalArgumentException("simulationTick cannot be negative");
        factions = immutable(factions, "factions");
        military = immutable(military, "military");
        timeline = immutable(timeline, "timeline");
        npcMissions = immutable(npcMissions, "npcMissions");
    }

    /** One faction-facing row. Private interests/goals are emitted only for the viewer faction. */
    public record FactionRow(
            String factionId,
            String displayName,
            String relation,
            List<String> interests,
            List<String> treaties,
            List<String> crises,
            List<String> wars,
            List<String> goals,
            List<String> decisionEvidence,
            String authorityRef) {
        /**
         * Validates and freezes one faction-facing presentation row.
         *
         * @param factionId stable faction identity
         * @param displayName actor-visible display name
         * @param relation actor-visible relation summary
         * @param interests actor-visible interests
         * @param treaties actor-visible treaty summaries
         * @param crises actor-visible diplomatic crises
         * @param wars actor-visible active wars
         * @param goals private goals when the row belongs to the viewer, otherwise empty
         * @param decisionEvidence bounded causal evidence visible to the viewer
         * @param authorityRef provenance reference for the projection
         */
        public FactionRow {
            factionId = requireText(factionId, "factionId");
            displayName = requireText(displayName, "displayName");
            relation = requireText(relation, "relation");
            interests = immutable(interests, "interests");
            treaties = immutable(treaties, "treaties");
            crises = immutable(crises, "crises");
            wars = immutable(wars, "wars");
            goals = immutable(goals, "goals");
            decisionEvidence = immutable(decisionEvidence, "decisionEvidence");
            authorityRef = requireText(authorityRef, "authorityRef");
        }
    }

    /** One viewer-owned command group with its current accepted strategic order. */
    public record MilitaryRow(
            long commandGroupId,
            String commandGroupName,
            List<String> fleetIds,
            String order,
            String readiness,
            List<String> route,
            String supply,
            String operation,
            String destination,
            String authorityRef) {
        /**
         * Validates and freezes one viewer-owned military presentation row.
         *
         * @param commandGroupId stable command-group id
         * @param commandGroupName display label of the command group
         * @param fleetIds member fleet ids
         * @param order accepted strategic order summary
         * @param readiness simulation-backed readiness summary
         * @param route ordered route waypoints
         * @param supply simulation-backed supply summary
         * @param operation active strategic operation summary
         * @param destination current destination summary
         * @param authorityRef provenance reference for the projection
         */
        public MilitaryRow {
            if (commandGroupId <= 0L) throw new IllegalArgumentException("commandGroupId must be positive");
            commandGroupName = requireText(commandGroupName, "commandGroupName");
            fleetIds = immutable(fleetIds, "fleetIds");
            order = requireText(order, "order");
            readiness = requireText(readiness, "readiness");
            route = immutable(route, "route");
            supply = requireText(supply, "supply");
            operation = requireText(operation, "operation");
            destination = requireText(destination, "destination");
            authorityRef = requireText(authorityRef, "authorityRef");
        }
    }

    /** Actor-bounded event row. */
    public record TimelineRow(
            long tick,
            String visibility,
            String actorId,
            String eventType,
            String summary,
            String evidenceRef) {
        /**
         * Validates one actor-bounded timeline presentation row.
         *
         * @param tick event observation tick
         * @param visibility actor-bounded visibility classification
         * @param actorId stable actor id associated with the event
         * @param eventType stable event type
         * @param summary presentation summary derived from visible evidence
         * @param evidenceRef provenance reference for the event
         */
        public TimelineRow {
            if (tick < 0L) throw new IllegalArgumentException("timeline tick cannot be negative");
            visibility = requireText(visibility, "visibility");
            actorId = requireText(actorId, "actorId");
            eventType = requireText(eventType, "eventType");
            summary = requireText(summary, "summary");
            evidenceRef = requireText(evidenceRef, "evidenceRef");
        }
    }

    /** Viewer-visible NPC/mission inspection row retaining ordinary-authority objective provenance. */
    public record NpcMissionRow(
            String npcId,
            String npcNameKey,
            String npcRole,
            String availability,
            String locationSystemId,
            List<String> knownFacts,
            String missionId,
            String missionTemplate,
            String missionStatus,
            String objective,
            long deadlineTick,
            long escrowMilliCredits,
            String authorityRef) {
        /**
         * Validates and freezes one NPC/mission inspection row.
         *
         * @param npcId stable NPC id
         * @param npcNameKey localization key for the NPC name
         * @param npcRole stable NPC role
         * @param availability current NPC availability
         * @param locationSystemId current NPC system id
         * @param knownFacts knowledge facts already received by this NPC
         * @param missionId active or historical mission id, or an empty string when absent
         * @param missionTemplate mission template id, or an empty string when absent
         * @param missionStatus mission lifecycle status, or an empty string when absent
         * @param objective mission objective summary, or an empty string when absent
         * @param deadlineTick mission deadline, or {@code -1} when absent
         * @param escrowMilliCredits currently reserved mission reward
         * @param authorityRef provenance reference for NPC and objective truth
         */
        public NpcMissionRow {
            npcId = requireText(npcId, "npcId");
            npcNameKey = requireText(npcNameKey, "npcNameKey");
            npcRole = requireText(npcRole, "npcRole");
            availability = requireText(availability, "availability");
            locationSystemId = requireText(locationSystemId, "locationSystemId");
            knownFacts = immutable(knownFacts, "knownFacts");
            missionId = Objects.requireNonNull(missionId, "missionId");
            missionTemplate = Objects.requireNonNull(missionTemplate, "missionTemplate");
            missionStatus = Objects.requireNonNull(missionStatus, "missionStatus");
            objective = Objects.requireNonNull(objective, "objective");
            if (deadlineTick < -1L) throw new IllegalArgumentException("deadlineTick cannot be less than -1");
            if (escrowMilliCredits < 0L) throw new IllegalArgumentException("escrow cannot be negative");
            authorityRef = requireText(authorityRef, "authorityRef");
        }
    }

    private static <T> List<T> immutable(List<T> source, String label) {
        List<T> copy = List.copyOf(Objects.requireNonNull(source, label));
        if (copy.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException(label + " contains null");
        return copy;
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label).strip();
        if (checked.isEmpty()) throw new IllegalArgumentException(label + " cannot be blank");
        return checked;
    }
}
