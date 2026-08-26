package com.spacesim.world;

import com.spacesim.world.Stage21HNpcMissionState.KnowledgeKind;
import com.spacesim.world.Stage21HNpcMissionState.MissionContract;
import com.spacesim.world.Stage21HNpcMissionState.MissionObjective;
import com.spacesim.world.Stage21HNpcMissionState.MissionStatus;
import com.spacesim.world.Stage21HNpcMissionState.MissionTemplate;
import com.spacesim.world.Stage21HNpcMissionState.MissionWakeup;
import com.spacesim.world.Stage21HNpcMissionState.NpcAvailability;
import com.spacesim.world.Stage21HNpcMissionState.NpcKnowledgeFact;
import com.spacesim.world.Stage21HNpcMissionState.NpcRole;
import com.spacesim.world.Stage21HNpcMissionState.NpcState;
import com.spacesim.world.Stage21HNpcMissionState.ObjectiveAuthority;
import com.spacesim.world.Stage21HNpcMissionState.ObjectiveKind;
import com.spacesim.world.Stage21HNpcMissionState.ReputationEvent;
import com.spacesim.world.Stage21HNpcMissionState.ReputationEventKind;
import com.spacesim.world.Stage21HNpcMissionState.ReputationState;
import com.spacesim.world.Stage21HNpcMissionState.StoryChainState;
import com.spacesim.world.Stage21HNpcMissionState.StoryChainStatus;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Deterministic bounded binary codec for standalone Stage-21H NPC/mission state. */
public final class Stage21HNpcMissionStateCodec {
    private static final int MAGIC = 0x5332484d; // S2HM
    private static final int FILE_VERSION = 1;
    private static final int MAX_BYTES = 64 * 1024 * 1024;
    private static final int MAX_NPCS = 100_000;
    private static final int MAX_MISSIONS = 1_000_000;
    private static final int MAX_ROWS = 1_000_000;
    private static final int MAX_NESTED = 100_000;

    private Stage21HNpcMissionStateCodec() {
        throw new AssertionError("No instances");
    }

    /**
     * Encodes one validated Stage-21H state in canonical order.
     *
     * @param state persistent sidecar
     * @return deterministic bytes
     */
    public static byte[] encode(Stage21HNpcMissionState state) {
        Stage21HNpcMissionState checked = Objects.requireNonNull(state, "state");
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(buffer)) {
                out.writeInt(MAGIC);
                out.writeInt(FILE_VERSION);
                out.writeInt(checked.schemaVersion());
                out.writeLong(checked.simulationTick());
                out.writeLong(checked.nextMissionSequence());
                writeNpcs(out, checked.npcs());
                writeMissions(out, checked.missions());
                writeReputations(out, checked.reputations());
                writeStoryChains(out, checked.storyChains());
            }
            byte[] bytes = buffer.toByteArray();
            if (bytes.length <= 0 || bytes.length > MAX_BYTES) {
                throw new IllegalArgumentException("Stage-21H sidecar exceeds bounded size");
            }
            return bytes;
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in-memory Stage-21H encoding failure", exception);
        }
    }

    /**
     * Decodes and validates one bounded Stage-21H state.
     *
     * @param bytes encoded bytes
     * @return validated sidecar
     */
    public static Stage21HNpcMissionState decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length <= 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Stage-21H sidecar size outside bounded range");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC) {
                throw new IllegalArgumentException("Invalid Stage-21H sidecar magic");
            }
            if (in.readInt() != FILE_VERSION) {
                throw new IllegalArgumentException("Unsupported Stage-21H file version");
            }
            int schemaVersion = in.readInt();
            long tick = in.readLong();
            long nextMission = in.readLong();
            List<NpcState> npcs = readNpcs(in);
            List<MissionContract> missions = readMissions(in);
            List<ReputationState> reputations = readReputations(in);
            List<StoryChainState> chains = readStoryChains(in);
            if (in.read() != -1) {
                throw new IllegalArgumentException("Trailing bytes after Stage-21H sidecar");
            }
            return new Stage21HNpcMissionState(
                    schemaVersion, tick, nextMission, npcs, missions, reputations, chains);
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Stage-21H sidecar is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegal) {
                throw illegal;
            }
            throw new IllegalArgumentException("Cannot decode Stage-21H sidecar", exception);
        }
    }

    private static void writeNpcs(DataOutputStream out, List<NpcState> npcs) throws IOException {
        writeCount(out, npcs.size(), MAX_NPCS, "NPCs");
        for (NpcState npc : npcs) {
            writeText(out, npc.npcId());
            writeText(out, npc.nameKey());
            out.writeByte(npc.role().ordinal());
            writeText(out, npc.factionContentId());
            out.writeLong(npc.locationSystemId().value());
            out.writeByte(npc.availability().ordinal());
            writeCount(out, npc.knowledge().size(), MAX_NESTED, "NPC knowledge");
            for (NpcKnowledgeFact fact : npc.knowledge()) {
                writeText(out, fact.factId());
                writeText(out, fact.subjectId());
                out.writeByte(fact.kind().ordinal());
                writeText(out, fact.claimCode());
                out.writeInt(fact.magnitudeBasisPoints());
                writeText(out, fact.provenanceId());
                out.writeLong(fact.receivedTick());
                out.writeLong(fact.freshUntilTick());
            }
        }
    }

    private static List<NpcState> readNpcs(DataInputStream in) throws IOException {
        int count = readCount(in, MAX_NPCS, "NPCs");
        ArrayList<NpcState> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String npcId = readText(in);
            String nameKey = readText(in);
            NpcRole role = readEnum(in, NpcRole.values(), "NPC role");
            String faction = readText(in);
            StarSystemId system = new StarSystemId(in.readLong());
            NpcAvailability availability = readEnum(in, NpcAvailability.values(), "NPC availability");
            int knowledgeCount = readCount(in, MAX_NESTED, "NPC knowledge");
            ArrayList<NpcKnowledgeFact> knowledge = new ArrayList<>(knowledgeCount);
            for (int k = 0; k < knowledgeCount; k++) {
                knowledge.add(new NpcKnowledgeFact(
                        readText(in),
                        readText(in),
                        readEnum(in, KnowledgeKind.values(), "Knowledge kind"),
                        readText(in),
                        in.readInt(),
                        readText(in),
                        in.readLong(),
                        in.readLong()));
            }
            values.add(new NpcState(npcId, nameKey, role, faction, system, availability, knowledge));
        }
        return List.copyOf(values);
    }

    private static void writeMissions(DataOutputStream out, List<MissionContract> missions) throws IOException {
        writeCount(out, missions.size(), MAX_MISSIONS, "Missions");
        for (MissionContract mission : missions) {
            writeText(out, mission.missionId());
            out.writeByte(mission.template().ordinal());
            out.writeInt(mission.templateVersion());
            writeText(out, mission.issuerNpcId());
            writeText(out, mission.issuerFactionId());
            writeStrings(out, mission.sourceKnowledgeFactIds());
            MissionObjective objective = mission.objective();
            out.writeByte(objective.authority().ordinal());
            out.writeByte(objective.kind().ordinal());
            writeText(out, objective.subjectId());
            out.writeLong(objective.systemId());
            out.writeLong(objective.threshold());
            writeText(out, objective.requiredState());
            out.writeLong(mission.createdTick());
            out.writeLong(mission.deadlineTick());
            out.writeByte(mission.status().ordinal());
            out.writeLong(mission.statusUpdatedTick());
            out.writeLong(mission.rewardMilliCredits());
            out.writeLong(mission.escrowMilliCredits());
            writeText(out, mission.outcomeCode());
            writeCount(out, mission.pendingWakeups().size(), MAX_NESTED, "Mission wakeups");
            for (MissionWakeup wakeup : mission.pendingWakeups()) {
                writeText(out, wakeup.eventId());
                out.writeLong(wakeup.observedTick());
                out.writeLong(wakeup.eligibleTick());
            }
        }
    }

    private static List<MissionContract> readMissions(DataInputStream in) throws IOException {
        int count = readCount(in, MAX_MISSIONS, "Missions");
        ArrayList<MissionContract> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String missionId = readText(in);
            MissionTemplate template = readEnum(in, MissionTemplate.values(), "Mission template");
            int templateVersion = in.readInt();
            String issuerNpcId = readText(in);
            String issuerFactionId = readText(in);
            List<String> facts = readStrings(in);
            MissionObjective objective = new MissionObjective(
                    readEnum(in, ObjectiveAuthority.values(), "Objective authority"),
                    readEnum(in, ObjectiveKind.values(), "Objective kind"),
                    readText(in),
                    in.readLong(),
                    in.readLong(),
                    readText(in));
            long created = in.readLong();
            long deadline = in.readLong();
            MissionStatus status = readEnum(in, MissionStatus.values(), "Mission status");
            long updated = in.readLong();
            long reward = in.readLong();
            long escrow = in.readLong();
            String outcome = readText(in);
            int wakeupCount = readCount(in, MAX_NESTED, "Mission wakeups");
            ArrayList<MissionWakeup> wakeups = new ArrayList<>(wakeupCount);
            for (int w = 0; w < wakeupCount; w++) {
                wakeups.add(new MissionWakeup(readText(in), in.readLong(), in.readLong()));
            }
            values.add(new MissionContract(
                    missionId, template, templateVersion, issuerNpcId, issuerFactionId, facts,
                    objective, created, deadline, status, updated, reward, escrow, outcome, wakeups));
        }
        return List.copyOf(values);
    }

    private static void writeReputations(DataOutputStream out, List<ReputationState> reputations) throws IOException {
        writeCount(out, reputations.size(), MAX_ROWS, "Reputations");
        for (ReputationState reputation : reputations) {
            writeText(out, reputation.ownerId());
            writeText(out, reputation.subjectActorId());
            writeCount(out, reputation.events().size(), MAX_NESTED, "Reputation events");
            for (ReputationEvent event : reputation.events()) {
                writeText(out, event.eventId());
                out.writeByte(event.kind().ordinal());
                out.writeInt(event.delta());
                out.writeLong(event.observedTick());
                writeText(out, event.subjectId());
            }
        }
    }

    private static List<ReputationState> readReputations(DataInputStream in) throws IOException {
        int count = readCount(in, MAX_ROWS, "Reputations");
        ArrayList<ReputationState> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String owner = readText(in);
            String subject = readText(in);
            int eventCount = readCount(in, MAX_NESTED, "Reputation events");
            ArrayList<ReputationEvent> events = new ArrayList<>(eventCount);
            for (int e = 0; e < eventCount; e++) {
                events.add(new ReputationEvent(
                        readText(in),
                        readEnum(in, ReputationEventKind.values(), "Reputation event kind"),
                        in.readInt(),
                        in.readLong(),
                        readText(in)));
            }
            values.add(new ReputationState(owner, subject, events));
        }
        return List.copyOf(values);
    }

    private static void writeStoryChains(DataOutputStream out, List<StoryChainState> chains) throws IOException {
        writeCount(out, chains.size(), MAX_ROWS, "Story chains");
        for (StoryChainState chain : chains) {
            writeText(out, chain.chainId());
            out.writeInt(chain.currentStep());
            out.writeInt(chain.totalSteps());
            out.writeByte(chain.status().ordinal());
            writeStrings(out, chain.missionIds());
        }
    }

    private static List<StoryChainState> readStoryChains(DataInputStream in) throws IOException {
        int count = readCount(in, MAX_ROWS, "Story chains");
        ArrayList<StoryChainState> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(new StoryChainState(
                    readText(in),
                    in.readInt(),
                    in.readInt(),
                    readEnum(in, StoryChainStatus.values(), "Story chain status"),
                    readStrings(in)));
        }
        return List.copyOf(values);
    }

    private static void writeStrings(DataOutputStream out, List<String> values) throws IOException {
        writeCount(out, values.size(), MAX_NESTED, "String list");
        for (String value : values) {
            writeText(out, value);
        }
    }

    private static List<String> readStrings(DataInputStream in) throws IOException {
        int count = readCount(in, MAX_NESTED, "String list");
        ArrayList<String> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(readText(in));
        }
        return List.copyOf(values);
    }

    private static void writeText(DataOutputStream out, String value) throws IOException {
        String checked = Objects.requireNonNull(value, "text");
        if (checked.length() > 32_000) {
            throw new IllegalArgumentException("Stage-21H text field exceeds bounded length");
        }
        out.writeUTF(checked);
    }

    private static String readText(DataInputStream in) throws IOException {
        String value = in.readUTF();
        if (value.length() > 32_000) {
            throw new IllegalArgumentException("Stage-21H text field exceeds bounded length");
        }
        return value;
    }

    private static void writeCount(DataOutputStream out, int count, int maximum, String label) throws IOException {
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException(label + " count outside bounds");
        }
        out.writeInt(count);
    }

    private static int readCount(DataInputStream in, int maximum, String label) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException(label + " count outside bounds");
        }
        return count;
    }

    private static <E extends Enum<E>> E readEnum(DataInputStream in, E[] values, String label) throws IOException {
        int ordinal = in.readUnsignedByte();
        if (ordinal >= values.length) {
            throw new IllegalArgumentException(label + " ordinal outside bounds");
        }
        return values[ordinal];
    }
}
