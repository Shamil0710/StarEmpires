package com.spacesim.persistence;

import com.spacesim.world.DiplomaticEmbargoState;
import com.spacesim.world.DiplomaticGrievanceState;
import com.spacesim.world.DiplomaticStandingState;
import com.spacesim.world.DiplomaticTreatyClauseState;
import com.spacesim.world.DiplomaticTreatyState;
import com.spacesim.world.FactionDiplomacyState;
import com.spacesim.world.StarSystemId;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Stage-17E bounded file-format trailer for institutional diplomatic state. */
final class WorldDiplomacyBinary {
    private static final int MAX_FACTIONS = 10_000;
    private static final int MAX_STATES_PER_FACTION = 100_000;
    private static final int MAX_CLAUSES_PER_TREATY = 1_000;

    private WorldDiplomacyBinary() {
        throw new AssertionError("Utility class");
    }

    static void write(DataOutputStream out, List<FactionDiplomacyState> states) throws IOException {
        WorldIoSupport.writeCount(out, states.size(), MAX_FACTIONS, "diplomacyFactions");
        for (FactionDiplomacyState state : states) {
            WorldIoSupport.writeString(out, state.factionContentId());

            WorldIoSupport.writeCount(out, state.standings().size(), MAX_STATES_PER_FACTION, "diplomaticStandings");
            for (DiplomaticStandingState standing : state.standings()) {
                WorldIoSupport.writeString(out, standing.targetFactionContentId());
                out.writeInt(standing.trust());
                out.writeInt(standing.credibility());
                out.writeLong(standing.lastUpdatedTick());
            }

            WorldIoSupport.writeCount(out, state.grievances().size(), MAX_STATES_PER_FACTION, "diplomaticGrievances");
            for (DiplomaticGrievanceState grievance : state.grievances()) {
                WorldIoSupport.writeString(out, grievance.grievanceId());
                WorldIoSupport.writeString(out, grievance.targetFactionContentId());
                WorldIoSupport.writeString(out, grievance.kind().name());
                out.writeInt(grievance.severity());
                out.writeLong(grievance.createdTick());
                out.writeLong(grievance.expiresTick());
                WorldIoSupport.writeString(out, grievance.subjectKey());
            }

            WorldIoSupport.writeCount(out, state.treaties().size(), MAX_STATES_PER_FACTION, "diplomaticTreaties");
            for (DiplomaticTreatyState treaty : state.treaties()) {
                WorldIoSupport.writeString(out, treaty.treatyId());
                WorldIoSupport.writeString(out, treaty.counterpartyFactionContentId());
                WorldIoSupport.writeString(out, treaty.status().name());
                out.writeLong(treaty.createdTick());
                out.writeLong(treaty.effectiveTick());
                out.writeLong(treaty.expiresTick());
                WorldIoSupport.writeCount(out, treaty.clauses().size(), MAX_CLAUSES_PER_TREATY, "treatyClauses");
                for (DiplomaticTreatyClauseState clause : treaty.clauses()) {
                    WorldIoSupport.writeString(out, clause.kind().name());
                    WorldIoSupport.writeString(out, clause.direction().name());
                    out.writeBoolean(clause.systemId() != null);
                    if (clause.systemId() != null) {
                        out.writeLong(clause.systemId().value());
                    }
                }
            }

            WorldIoSupport.writeCount(out, state.embargoes().size(), MAX_STATES_PER_FACTION, "diplomaticEmbargoes");
            for (DiplomaticEmbargoState embargo : state.embargoes()) {
                WorldIoSupport.writeString(out, embargo.targetFactionContentId());
                WorldIoSupport.writeString(out, embargo.scope().name());
                out.writeLong(embargo.imposedTick());
                out.writeLong(embargo.expiresTick());
                WorldIoSupport.writeString(out, embargo.reasonKey());
            }
        }
    }

    static List<FactionDiplomacyState> read(DataInputStream in) throws IOException {
        int factionCount = WorldIoSupport.readCount(in, MAX_FACTIONS, "diplomacyFactions");
        List<FactionDiplomacyState> result = new ArrayList<>(factionCount);
        Set<String> factionIds = new HashSet<>();
        for (int factionIndex = 0; factionIndex < factionCount; factionIndex++) {
            String factionId = WorldIoSupport.readString(in);
            if (!factionIds.add(factionId)) {
                throw new IllegalArgumentException("Duplicate diplomacy faction trailer: " + factionId);
            }

            int standingCount = WorldIoSupport.readCount(in, MAX_STATES_PER_FACTION, "diplomaticStandings");
            List<DiplomaticStandingState> standings = new ArrayList<>(standingCount);
            for (int index = 0; index < standingCount; index++) {
                standings.add(new DiplomaticStandingState(
                        WorldIoSupport.readString(in),
                        in.readInt(),
                        in.readInt(),
                        in.readLong()));
            }

            int grievanceCount = WorldIoSupport.readCount(in, MAX_STATES_PER_FACTION, "diplomaticGrievances");
            List<DiplomaticGrievanceState> grievances = new ArrayList<>(grievanceCount);
            for (int index = 0; index < grievanceCount; index++) {
                grievances.add(new DiplomaticGrievanceState(
                        WorldIoSupport.readString(in),
                        WorldIoSupport.readString(in),
                        readGrievanceKind(in),
                        in.readInt(),
                        in.readLong(),
                        in.readLong(),
                        WorldIoSupport.readString(in)));
            }

            int treatyCount = WorldIoSupport.readCount(in, MAX_STATES_PER_FACTION, "diplomaticTreaties");
            List<DiplomaticTreatyState> treaties = new ArrayList<>(treatyCount);
            for (int index = 0; index < treatyCount; index++) {
                String treatyId = WorldIoSupport.readString(in);
                String counterparty = WorldIoSupport.readString(in);
                DiplomaticTreatyState.Status status = readTreatyStatus(in);
                long createdTick = in.readLong();
                long effectiveTick = in.readLong();
                long expiresTick = in.readLong();
                int clauseCount = WorldIoSupport.readCount(in, MAX_CLAUSES_PER_TREATY, "treatyClauses");
                List<DiplomaticTreatyClauseState> clauses = new ArrayList<>(clauseCount);
                for (int clauseIndex = 0; clauseIndex < clauseCount; clauseIndex++) {
                    DiplomaticTreatyClauseState.Kind kind = readClauseKind(in);
                    DiplomaticTreatyClauseState.Direction direction = readClauseDirection(in);
                    StarSystemId systemId = in.readBoolean() ? new StarSystemId(in.readLong()) : null;
                    clauses.add(new DiplomaticTreatyClauseState(kind, direction, systemId));
                }
                treaties.add(new DiplomaticTreatyState(
                        treatyId,
                        counterparty,
                        status,
                        createdTick,
                        effectiveTick,
                        expiresTick,
                        clauses));
            }

            int embargoCount = WorldIoSupport.readCount(in, MAX_STATES_PER_FACTION, "diplomaticEmbargoes");
            List<DiplomaticEmbargoState> embargoes = new ArrayList<>(embargoCount);
            for (int index = 0; index < embargoCount; index++) {
                embargoes.add(new DiplomaticEmbargoState(
                        WorldIoSupport.readString(in),
                        readEmbargoScope(in),
                        in.readLong(),
                        in.readLong(),
                        WorldIoSupport.readString(in)));
            }

            result.add(new FactionDiplomacyState(factionId, standings, grievances, treaties, embargoes));
        }
        result.sort(null);
        return List.copyOf(result);
    }

    private static DiplomaticGrievanceState.Kind readGrievanceKind(DataInputStream in) throws IOException {
        try {
            return DiplomaticGrievanceState.Kind.valueOf(WorldIoSupport.readString(in));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown diplomatic grievance kind", exception);
        }
    }

    private static DiplomaticTreatyState.Status readTreatyStatus(DataInputStream in) throws IOException {
        try {
            return DiplomaticTreatyState.Status.valueOf(WorldIoSupport.readString(in));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown diplomatic treaty status", exception);
        }
    }

    private static DiplomaticTreatyClauseState.Kind readClauseKind(DataInputStream in) throws IOException {
        try {
            return DiplomaticTreatyClauseState.Kind.valueOf(WorldIoSupport.readString(in));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown diplomatic treaty clause kind", exception);
        }
    }

    private static DiplomaticTreatyClauseState.Direction readClauseDirection(DataInputStream in) throws IOException {
        try {
            return DiplomaticTreatyClauseState.Direction.valueOf(WorldIoSupport.readString(in));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown diplomatic treaty clause direction", exception);
        }
    }

    private static DiplomaticEmbargoState.Scope readEmbargoScope(DataInputStream in) throws IOException {
        try {
            return DiplomaticEmbargoState.Scope.valueOf(WorldIoSupport.readString(in));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown diplomatic embargo scope", exception);
        }
    }
}
