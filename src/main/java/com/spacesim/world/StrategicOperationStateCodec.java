package com.spacesim.world;

import com.spacesim.world.FactionActorObservationSnapshot.ObservationChannel;
import com.spacesim.world.StrategicOperationState.ContactState;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationStatus;
import com.spacesim.world.StrategicOperationState.OperationType;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.TacticalEncounterState;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

/** Deterministic bounded binary codec for persistent Stage-21E operation metadata. */
public final class StrategicOperationStateCodec {
    private static final int MAGIC = 0x4F503231; // OP21
    private static final int VERSION = 1;
    private static final int MAX_BYTES = 32 * 1024 * 1024;
    private static final int MAX_OPERATIONS = 65_536;
    private static final int MAX_PARTICIPANTS = 65_536;
    private static final int MAX_TEXT = 16_384;

    private StrategicOperationStateCodec() { throw new AssertionError("No instances"); }

    /**
     * Encodes canonical operation state deterministically.
     *
     * @param state validated persistent Stage-21E operation state
     * @return deterministic bounded operation payload bytes
     */
    public static byte[] encode(StrategicOperationState state) {
        StrategicOperationState checked = Objects.requireNonNull(state, "state");
        if (checked.operations().size() > MAX_OPERATIONS) throw new IllegalArgumentException("too many operations");
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(buffer)) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeLong(checked.nextOperationId());
                out.writeInt(checked.operations().size());
                for (OperationState operation : checked.operations()) writeOperation(out, operation);
            }
            byte[] bytes = buffer.toByteArray();
            if (bytes.length <= 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("operation payload outside bounds");
            return bytes;
        } catch (IOException exception) {
            throw new IllegalStateException("unexpected in-memory operation encoding failure", exception);
        }
    }

    /**
     * Decodes canonical operation state and fails closed on corrupt, future or trailing data.
     *
     * @param bytes encoded Stage-21E operation payload
     * @return decoded and validated persistent operation state
     */
    public static StrategicOperationState decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length <= 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("operation payload outside bounds");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC) throw new IllegalArgumentException("invalid operation payload magic");
            int version = in.readInt();
            if (version != VERSION) throw new IllegalArgumentException("unsupported operation payload version: " + version);
            long nextId = in.readLong();
            int count = boundedCount(in.readInt(), MAX_OPERATIONS, "operation count");
            ArrayList<OperationState> operations = new ArrayList<>(count);
            for (int index = 0; index < count; index++) operations.add(readOperation(in));
            if (in.read() != -1) throw new IllegalArgumentException("trailing bytes after operation payload");
            return new StrategicOperationState(nextId, operations);
        } catch (EOFException exception) {
            throw new IllegalArgumentException("operation payload is truncated", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("cannot decode operation payload", exception);
        }
    }

    private static void writeOperation(DataOutputStream out, OperationState value) throws IOException {
        out.writeLong(value.id());
        out.writeUTF(value.type().name());
        out.writeLong(value.commandGroupId());
        out.writeLong(value.sourceOrderId());
        out.writeInt(value.factionId());
        out.writeInt(value.participantFleetIds().size());
        if (value.participantFleetIds().size() > MAX_PARTICIPANTS) throw new IllegalArgumentException("too many operation participants");
        for (FleetId fleetId : value.participantFleetIds()) out.writeLong(fleetId.value());
        out.writeLong(value.stagingSystemId().value());
        out.writeLong(value.objectiveSystemId().value());
        writeText(out, value.objectiveId());
        out.writeUTF(value.rulesOfEngagement().name());
        out.writeInt(value.supplyPolicy().minimumMissionReadinessBps());
        out.writeInt(value.supplyPolicy().minimumSupplyAccessBps());
        out.writeLong(value.supplyPolicy().maximumUnsupportedTicks());
        out.writeLong(value.withdrawalPolicy().fallbackSystemId().value());
        out.writeInt(value.withdrawalPolicy().withdrawBelowReadinessBps());
        out.writeBoolean(value.withdrawalPolicy().withdrawWhenOutOfAmmunition());
        out.writeBoolean(value.withdrawalPolicy().withdrawWhenOutOfPropellant());
        out.writeUTF(value.status().name());
        out.writeLong(value.createdAtTick());
        out.writeLong(value.lastTransitionTick());
        out.writeLong(value.unsupportedSinceTick());
        out.writeBoolean(value.contact() != null);
        if (value.contact() != null) writeContact(out, value.contact());
        out.writeBoolean(value.encounter() != null);
        if (value.encounter() != null) writeEncounter(out, value.encounter());
    }

    private static OperationState readOperation(DataInputStream in) throws IOException {
        long id = in.readLong();
        OperationType type = enumValue(OperationType.class, in.readUTF(), "operation type");
        long groupId = in.readLong();
        long orderId = in.readLong();
        int factionId = in.readInt();
        int participantCount = boundedCount(in.readInt(), MAX_PARTICIPANTS, "participant count");
        ArrayList<FleetId> participants = new ArrayList<>(participantCount);
        for (int index = 0; index < participantCount; index++) participants.add(new FleetId(in.readLong()));
        StarSystemId staging = new StarSystemId(in.readLong());
        StarSystemId objective = new StarSystemId(in.readLong());
        String objectiveId = readText(in);
        RulesOfEngagement roe = enumValue(RulesOfEngagement.class, in.readUTF(), "rules of engagement");
        SupplyPolicy supply = new SupplyPolicy(in.readInt(), in.readInt(), in.readLong());
        WithdrawalPolicy withdrawal = new WithdrawalPolicy(
                new StarSystemId(in.readLong()), in.readInt(), in.readBoolean(), in.readBoolean());
        OperationStatus status = enumValue(OperationStatus.class, in.readUTF(), "operation status");
        long created = in.readLong();
        long transition = in.readLong();
        long unsupported = in.readLong();
        ContactState contact = in.readBoolean() ? readContact(in) : null;
        TacticalEncounterState encounter = in.readBoolean() ? readEncounter(in) : null;
        return new OperationState(id, type, groupId, orderId, factionId, participants, staging, objective,
                objectiveId, roe, supply, withdrawal, status, created, transition, unsupported, contact, encounter);
    }

    private static void writeContact(DataOutputStream out, ContactState value) throws IOException {
        out.writeLong(value.targetFleetId().value());
        out.writeLong(value.observedSystemId().value());
        out.writeUTF(value.channel().name());
        writeText(out, value.provenanceId());
        out.writeLong(value.observedAtTick());
        out.writeLong(value.freshUntilTick());
    }

    private static ContactState readContact(DataInputStream in) throws IOException {
        return new ContactState(new FleetId(in.readLong()), new StarSystemId(in.readLong()),
                enumValue(ObservationChannel.class, in.readUTF(), "observation channel"), readText(in),
                in.readLong(), in.readLong());
    }

    private static void writeEncounter(DataOutputStream out, TacticalEncounterState value) throws IOException {
        out.writeLong(value.encounterId());
        out.writeLong(value.targetFleetId().value());
        out.writeLong(value.systemId().value());
        out.writeLong(value.materializedAtTick());
        out.writeLong(value.resolvedAtTick());
    }

    private static TacticalEncounterState readEncounter(DataInputStream in) throws IOException {
        return new TacticalEncounterState(in.readLong(), new FleetId(in.readLong()), new StarSystemId(in.readLong()),
                in.readLong(), in.readLong());
    }

    private static void writeText(DataOutputStream out, String value) throws IOException {
        String checked = Objects.requireNonNull(value, "text");
        if (checked.length() > MAX_TEXT) throw new IllegalArgumentException("text exceeds operation codec bound");
        out.writeUTF(checked);
    }

    private static String readText(DataInputStream in) throws IOException {
        String value = in.readUTF();
        if (value.length() > MAX_TEXT) throw new IllegalArgumentException("text exceeds operation codec bound");
        return value;
    }

    private static int boundedCount(int count, int maximum, String label) {
        if (count < 0 || count > maximum) throw new IllegalArgumentException(label + " outside bounds");
        return count;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String label) {
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("unknown " + label + ": " + value, exception); }
    }
}
