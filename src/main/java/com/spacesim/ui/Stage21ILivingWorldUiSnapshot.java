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
        List<OverlayRow> overlays,
        List<TimelineRow> timeline,
        List<NpcMissionRow> npcMissions) {

    /**
     * Validates and freezes the complete actor-bounded UI snapshot.
     *
     * @param viewerFactionId faction whose bounded knowledge is being projected
     * @param simulationTick authoritative simulation tick represented by the snapshot
     * @param factions faction-facing overview rows visible to the viewer
     * @param military viewer-owned military command-group rows
     * @param overlays actor-bounded strategic-map overlay rows
     * @param timeline actor-bounded causality timeline rows
     * @param npcMissions viewer-authorized NPC and mission inspection rows
     */
    public Stage21ILivingWorldUiSnapshot {
        viewerFactionId = requireText(viewerFactionId, "viewerFactionId");
        if (simulationTick < 0L) throw new IllegalArgumentException("simulationTick cannot be negative");
        factions = immutable(factions, "factions");
        military = immutable(military, "military");
        overlays = immutable(overlays, "overlays");
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
         * @param factionId stable faction identifier
         * @param displayName player-facing faction name
         * @param relation viewer-relative diplomatic relation summary
         * @param interests interests the viewer is authorized to observe
         * @param treaties viewer-visible treaty summaries
         * @param crises viewer-visible crisis summaries
         * @param wars viewer-visible war summaries
         * @param goals goals the viewer is authorized to observe
         * @param decisionEvidence bounded evidence explaining authoritative faction decisions
         * @param authorityRef provenance reference for the projected row
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
         * @param commandGroupId stable positive command-group identifier
         * @param commandGroupName player-facing command-group name
         * @param fleetIds authoritative fleet identifiers belonging to the group
         * @param order currently accepted strategic order
         * @param readiness projected readiness state
         * @param route current authoritative route projection
         * @param supply projected supply state
         * @param operation current operation summary
         * @param destination current destination summary
         * @param authorityRef provenance reference for the projected military state
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

    /** One global-map overlay fact with explicit authority provenance and actor-bounded visibility. */
    public record OverlayRow(
            String kind,
            String subjectId,
            String actorId,
            String state,
            List<String> details,
            String visibility,
            String authorityRef) implements Comparable<OverlayRow> {
        /**
         * Validates and freezes one authoritative overlay presentation row.
         *
         * @param kind overlay category
         * @param subjectId stable identifier of the projected subject
         * @param actorId authoritative actor associated with the overlay fact
         * @param state player-facing state summary
         * @param details bounded supporting details
         * @param visibility visibility classification applied to the row
         * @param authorityRef provenance reference for the projected overlay fact
         */
        public OverlayRow {
            kind = requireText(kind, "kind");
            subjectId = requireText(subjectId, "subjectId");
            actorId = requireText(actorId, "actorId");
            state = requireText(state, "state");
            details = immutable(details, "details");
            visibility = requireText(visibility, "visibility");
            authorityRef = requireText(authorityRef, "authorityRef");
        }

        @Override
        public int compareTo(OverlayRow other) {
            int kindOrder = kind.compareTo(other.kind);
            if (kindOrder != 0) return kindOrder;
            int subjectOrder = subjectId.compareTo(other.subjectId);
            if (subjectOrder != 0) return subjectOrder;
            int actorOrder = actorId.compareTo(other.actorId);
            if (actorOrder != 0) return actorOrder;
            return state.compareTo(other.state);
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
         * Validates one actor-bounded timeline row and its evidence reference.
         *
         * @param tick authoritative simulation tick at which the event occurred
         * @param visibility visibility classification applied to the event
         * @param actorId authoritative actor associated with the event
         * @param eventType stable event category
         * @param summary bounded player-facing event summary
         * @param evidenceRef provenance reference supporting the timeline row
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
         * Validates and freezes one viewer-visible NPC and optional mission row.
         *
         * @param npcId stable NPC identifier
         * @param npcNameKey localization key for the NPC name
         * @param npcRole authoritative NPC role
         * @param availability current viewer-visible availability state
         * @param locationSystemId current viewer-visible system location
         * @param knownFacts actor-bounded facts known about the NPC
         * @param missionId stable mission identifier, or an empty value when no mission is exposed
         * @param missionTemplate mission template identifier, or an empty value when absent
         * @param missionStatus viewer-visible mission status, or an empty value when absent
         * @param objective authoritative mission objective projection, or an empty value when absent
         * @param deadlineTick mission deadline tick, or -1 when no deadline is exposed
         * @param escrowMilliCredits mission escrow expressed in milli-credits
         * @param authorityRef provenance reference for the projected NPC/mission state
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
