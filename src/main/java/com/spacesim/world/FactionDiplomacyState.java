package com.spacesim.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Persistent institutional diplomacy owned by one faction.
 *
 * <p>Directed relations remain in {@link FactionStrategicState} as a backward-compatible summary
 * signal. This aggregate stores the explicit legal/history structures used by Stage 17E.</p>
 *
 * @param factionContentId owner faction stable ID
 * @param standings directed trust/credibility assessments
 * @param grievances explicit directed grievances
 * @param treaties treaty directory entries originated by this faction
 * @param embargoes unilateral embargoes imposed by this faction
 */
public record FactionDiplomacyState(
        String factionContentId,
        List<DiplomaticStandingState> standings,
        List<DiplomaticGrievanceState> grievances,
        List<DiplomaticTreatyState> treaties,
        List<DiplomaticEmbargoState> embargoes) implements Comparable<FactionDiplomacyState> {

    /**
     * Creates an empty neutral diplomacy aggregate for one faction.
     *
     * @param factionContentId stable owner faction ID
     * @return canonical empty diplomacy aggregate
     */
    public static FactionDiplomacyState neutral(String factionContentId) {
        return new FactionDiplomacyState(factionContentId, List.of(), List.of(), List.of(), List.of());
    }

    /**
     * Validates self-references, uniqueness and canonical ordering.
     *
     * @param factionContentId owner faction stable ID
     * @param standings directed trust/credibility assessments
     * @param grievances explicit directed grievances
     * @param treaties treaty directory entries originated by this faction
     * @param embargoes unilateral embargoes imposed by this faction
     */
    public FactionDiplomacyState {
        factionContentId = requireId(factionContentId, "Diplomacy owner faction ID");
        Objects.requireNonNull(standings, "Diplomatic standings not set");
        Objects.requireNonNull(grievances, "Diplomatic grievances not set");
        Objects.requireNonNull(treaties, "Diplomatic treaties not set");
        Objects.requireNonNull(embargoes, "Diplomatic embargoes not set");

        List<DiplomaticStandingState> sortedStandings = new ArrayList<>(standings.size());
        Set<String> standingTargets = new HashSet<>();
        for (DiplomaticStandingState standing : standings) {
            DiplomaticStandingState value = Objects.requireNonNull(standing, "Diplomatic standing not set");
            rejectSelf(factionContentId, value.targetFactionContentId(), "standing");
            if (!standingTargets.add(value.targetFactionContentId())) {
                throw new IllegalArgumentException("Duplicate diplomatic standing target: "
                        + value.targetFactionContentId());
            }
            sortedStandings.add(value);
        }
        sortedStandings.sort(null);
        standings = List.copyOf(sortedStandings);

        List<DiplomaticGrievanceState> sortedGrievances = new ArrayList<>(grievances.size());
        Set<String> grievanceIds = new HashSet<>();
        for (DiplomaticGrievanceState grievance : grievances) {
            DiplomaticGrievanceState value = Objects.requireNonNull(grievance, "Diplomatic grievance not set");
            rejectSelf(factionContentId, value.targetFactionContentId(), "grievance");
            if (!grievanceIds.add(value.grievanceId())) {
                throw new IllegalArgumentException("Duplicate diplomatic grievance ID: " + value.grievanceId());
            }
            sortedGrievances.add(value);
        }
        sortedGrievances.sort(null);
        grievances = List.copyOf(sortedGrievances);

        List<DiplomaticTreatyState> sortedTreaties = new ArrayList<>(treaties.size());
        Set<String> treatyIds = new HashSet<>();
        for (DiplomaticTreatyState treaty : treaties) {
            DiplomaticTreatyState value = Objects.requireNonNull(treaty, "Diplomatic treaty not set");
            rejectSelf(factionContentId, value.counterpartyFactionContentId(), "treaty");
            if (!treatyIds.add(value.treatyId())) {
                throw new IllegalArgumentException("Duplicate treaty ID in faction directory: " + value.treatyId());
            }
            sortedTreaties.add(value);
        }
        sortedTreaties.sort(null);
        treaties = List.copyOf(sortedTreaties);

        List<DiplomaticEmbargoState> sortedEmbargoes = new ArrayList<>(embargoes.size());
        Set<String> embargoKeys = new HashSet<>();
        for (DiplomaticEmbargoState embargo : embargoes) {
            DiplomaticEmbargoState value = Objects.requireNonNull(embargo, "Diplomatic embargo not set");
            rejectSelf(factionContentId, value.targetFactionContentId(), "embargo");
            String key = value.targetFactionContentId() + "\u0000" + value.scope();
            if (!embargoKeys.add(key)) {
                throw new IllegalArgumentException("Duplicate diplomatic embargo: " + key);
            }
            sortedEmbargoes.add(value);
        }
        sortedEmbargoes.sort(null);
        embargoes = List.copyOf(sortedEmbargoes);
    }

    /**
     * Finds an explicit directed standing.
     *
     * @param targetFactionContentId stable target faction ID
     * @return explicit standing or {@code null} when the pair has no history yet
     */
    public DiplomaticStandingState standingToward(String targetFactionContentId) {
        if (targetFactionContentId == null) {
            return null;
        }
        String target = targetFactionContentId.strip();
        for (DiplomaticStandingState standing : standings) {
            if (standing.targetFactionContentId().equals(target)) {
                return standing;
            }
        }
        return null;
    }

    /**
     * Reads directed trust with a neutral fallback.
     *
     * @param targetFactionContentId stable target faction ID
     * @return directed trust, defaulting to zero
     */
    public int trustTo(String targetFactionContentId) {
        DiplomaticStandingState standing = standingToward(targetFactionContentId);
        return standing == null ? 0 : standing.trust();
    }

    /**
     * Reads perceived credibility with a neutral fallback.
     *
     * @param targetFactionContentId stable target faction ID
     * @return perceived credibility, defaulting to the neutral midpoint
     */
    public int credibilityOf(String targetFactionContentId) {
        DiplomaticStandingState standing = standingToward(targetFactionContentId);
        return standing == null ? DiplomaticStandingState.NEUTRAL_CREDIBILITY : standing.credibility();
    }

    /**
     * Checks an active unilateral market-access embargo against a target faction.
     *
     * @param targetFactionContentId stable target faction ID
     * @param worldTick authoritative world tick
     * @return true when an active market-access embargo targets that faction
     */
    public boolean hasActiveMarketEmbargoAgainst(String targetFactionContentId, long worldTick) {
        if (targetFactionContentId == null || worldTick < 0L) {
            return false;
        }
        String target = targetFactionContentId.strip();
        for (DiplomaticEmbargoState embargo : embargoes) {
            if (embargo.scope() == DiplomaticEmbargoState.Scope.MARKET_ACCESS
                    && embargo.targetFactionContentId().equals(target)
                    && embargo.activeAt(worldTick)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int compareTo(FactionDiplomacyState other) {
        return factionContentId.compareTo(
                Objects.requireNonNull(other, "FactionDiplomacyState not set").factionContentId);
    }

    private static void rejectSelf(String owner, String target, String label) {
        if (owner.equals(target)) {
            throw new IllegalArgumentException("Faction cannot create a self-directed diplomatic " + label);
        }
    }

    private static String requireId(String value, String label) {
        String normalized = Objects.requireNonNull(value, label + " not set").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return normalized;
    }
}
