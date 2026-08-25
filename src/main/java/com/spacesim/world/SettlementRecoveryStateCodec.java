package com.spacesim.world;

import com.spacesim.world.SettlementRecoveryState.DemobilizationDirective;
import com.spacesim.world.SettlementRecoveryState.FleetLossRecord;
import com.spacesim.world.SettlementRecoveryState.ObligationStatus;
import com.spacesim.world.SettlementRecoveryState.PaymentObligation;
import com.spacesim.world.SettlementRecoveryState.ReplacementDemand;
import com.spacesim.world.SettlementRecoveryState.ReplacementStatus;
import com.spacesim.world.SettlementRecoveryState.Settlement;
import com.spacesim.world.SettlementRecoveryState.SettlementStatus;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Deterministic bounded binary codec for {@link SettlementRecoveryState}. */
public final class SettlementRecoveryStateCodec {
    private static final int MAGIC = 0x53323147; // S21G
    private static final int FILE_VERSION = 1;
    private static final int MAX_BYTES = 64 * 1024 * 1024;
    private static final int MAX_ROWS = 1_000_000;
    private static final int MAX_TEXT_BYTES = 16 * 1024;

    private SettlementRecoveryStateCodec() {
        throw new AssertionError("No instances");
    }

    /**
     * Encodes a canonical Stage-21G settlement state.
     *
     * @param state validated state
     * @return deterministic bounded bytes
     */
    public static byte[] encode(SettlementRecoveryState state) {
        SettlementRecoveryState checked = Objects.requireNonNull(state, "state");
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(buffer)) {
                out.writeInt(MAGIC);
                out.writeInt(FILE_VERSION);
                out.writeInt(checked.schemaVersion());
                out.writeLong(checked.simulationTick());
                out.writeLong(checked.nextSettlementId());
                out.writeLong(checked.nextReplacementDemandId());
                writeSettlements(out, checked.settlements());
                writePayments(out, checked.payments());
                writeDemobilizations(out, checked.demobilizations());
                writeLosses(out, checked.losses());
                writeReplacementDemands(out, checked.replacementDemands());
            }
            byte[] result = buffer.toByteArray();
            if (result.length <= 0 || result.length > MAX_BYTES) {
                throw new IllegalArgumentException("Stage-21G settlement payload exceeds bounded size");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in-memory Stage-21G encoding failure", exception);
        }
    }

    /**
     * Decodes and validates a Stage-21G settlement state.
     *
     * @param bytes encoded bytes
     * @return validated canonical state
     */
    public static SettlementRecoveryState decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length <= 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Stage-21G settlement payload size outside bounds");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC) throw new IllegalArgumentException("Invalid Stage-21G settlement magic");
            int fileVersion = in.readInt();
            if (fileVersion != FILE_VERSION) {
                throw new IllegalArgumentException("Unsupported Stage-21G settlement file version: " + fileVersion);
            }
            SettlementRecoveryState result = new SettlementRecoveryState(
                    in.readInt(),
                    in.readLong(),
                    in.readLong(),
                    in.readLong(),
                    readSettlements(in),
                    readPayments(in),
                    readDemobilizations(in),
                    readLosses(in),
                    readReplacementDemands(in));
            if (in.read() != -1) throw new IllegalArgumentException("Trailing bytes after Stage-21G settlement state");
            return result;
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Stage-21G settlement state is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegal) throw illegal;
            throw new IllegalArgumentException("Cannot decode Stage-21G settlement state", exception);
        }
    }

    private static void writeSettlements(DataOutputStream out, List<Settlement> rows) throws IOException {
        writeCount(out, rows.size());
        for (Settlement row : rows) {
            out.writeLong(row.id());
            writeText(out, row.proposalId());
            writeText(out, row.warId());
            writeText(out, row.factionA());
            writeText(out, row.factionB());
            out.writeLong(row.openedTick());
            out.writeLong(row.updatedTick());
            out.writeInt(row.status().ordinal());
            out.writeBoolean(row.memoryRecorded());
        }
    }

    private static List<Settlement> readSettlements(DataInputStream in) throws IOException {
        int count = readCount(in);
        ArrayList<Settlement> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new Settlement(
                    in.readLong(), readText(in), readText(in), readText(in), readText(in),
                    in.readLong(), in.readLong(), readEnum(in, SettlementStatus.values()), in.readBoolean()));
        }
        return rows;
    }

    private static void writePayments(DataOutputStream out, List<PaymentObligation> rows) throws IOException {
        writeCount(out, rows.size());
        for (PaymentObligation row : rows) {
            out.writeLong(row.settlementId());
            out.writeInt(row.ordinal());
            writeText(out, row.payerFactionId());
            writeText(out, row.recipientFactionId());
            out.writeLong(row.amountMilliCredits());
            out.writeInt(row.status().ordinal());
            out.writeLong(row.completedTick());
        }
    }

    private static List<PaymentObligation> readPayments(DataInputStream in) throws IOException {
        int count = readCount(in);
        ArrayList<PaymentObligation> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new PaymentObligation(
                    in.readLong(), in.readInt(), readText(in), readText(in), in.readLong(),
                    readEnum(in, ObligationStatus.values()), in.readLong()));
        }
        return rows;
    }

    private static void writeDemobilizations(DataOutputStream out, List<DemobilizationDirective> rows)
            throws IOException {
        writeCount(out, rows.size());
        for (DemobilizationDirective row : rows) {
            out.writeLong(row.settlementId());
            out.writeLong(row.commandGroupId());
            writeText(out, row.factionContentId());
            out.writeLong(row.returnOrderId());
            out.writeInt(row.status().ordinal());
            out.writeLong(row.updatedTick());
        }
    }

    private static List<DemobilizationDirective> readDemobilizations(DataInputStream in) throws IOException {
        int count = readCount(in);
        ArrayList<DemobilizationDirective> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new DemobilizationDirective(
                    in.readLong(), in.readLong(), readText(in), in.readLong(),
                    readEnum(in, ObligationStatus.values()), in.readLong()));
        }
        return rows;
    }

    private static void writeLosses(DataOutputStream out, List<FleetLossRecord> rows) throws IOException {
        writeCount(out, rows.size());
        for (FleetLossRecord row : rows) {
            out.writeLong(row.settlementId());
            out.writeLong(row.operationId());
            out.writeLong(row.lostFleetId().value());
            writeText(out, row.factionContentId());
            out.writeLong(row.recordedTick());
        }
    }

    private static List<FleetLossRecord> readLosses(DataInputStream in) throws IOException {
        int count = readCount(in);
        ArrayList<FleetLossRecord> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new FleetLossRecord(
                    in.readLong(), in.readLong(), new FleetId(in.readLong()), readText(in), in.readLong()));
        }
        return rows;
    }

    private static void writeReplacementDemands(DataOutputStream out, List<ReplacementDemand> rows)
            throws IOException {
        writeCount(out, rows.size());
        for (ReplacementDemand row : rows) {
            out.writeLong(row.id());
            out.writeLong(row.settlementId());
            out.writeLong(row.lostFleetId().value());
            writeText(out, row.factionContentId());
            writeText(out, row.targetFitFingerprint());
            out.writeLong(row.createdTick());
            out.writeLong(row.updatedTick());
            out.writeInt(row.status().ordinal());
            out.writeLong(row.completedAssetIdValue());
            out.writeLong(row.commissionedFleetId() == null ? 0L : row.commissionedFleetId().value());
        }
    }

    private static List<ReplacementDemand> readReplacementDemands(DataInputStream in) throws IOException {
        int count = readCount(in);
        ArrayList<ReplacementDemand> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long id = in.readLong();
            long settlementId = in.readLong();
            FleetId lostFleetId = new FleetId(in.readLong());
            String factionId = readText(in);
            String fingerprint = readText(in);
            long createdTick = in.readLong();
            long updatedTick = in.readLong();
            ReplacementStatus status = readEnum(in, ReplacementStatus.values());
            long assetId = in.readLong();
            long commissioned = in.readLong();
            rows.add(new ReplacementDemand(
                    id, settlementId, lostFleetId, factionId, fingerprint, createdTick, updatedTick,
                    status, assetId, commissioned == 0L ? null : new FleetId(commissioned)));
        }
        return rows;
    }

    private static void writeText(DataOutputStream out, String value) throws IOException {
        byte[] bytes = Objects.requireNonNull(value, "value").getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= 0 || bytes.length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException("Stage-21G text length outside bounds");
        }
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readText(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length <= 0 || length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException("Stage-21G text length outside bounds");
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeCount(DataOutputStream out, int count) throws IOException {
        if (count < 0 || count > MAX_ROWS) throw new IllegalArgumentException("Stage-21G row count outside bounds");
        out.writeInt(count);
    }

    private static int readCount(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > MAX_ROWS) throw new IllegalArgumentException("Stage-21G row count outside bounds");
        return count;
    }

    private static <E extends Enum<E>> E readEnum(DataInputStream in, E[] values) throws IOException {
        int ordinal = in.readInt();
        if (ordinal < 0 || ordinal >= values.length) throw new IllegalArgumentException("Stage-21G enum ordinal outside bounds");
        return values[ordinal];
    }
}
