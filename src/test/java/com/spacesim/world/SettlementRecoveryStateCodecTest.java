package com.spacesim.world;

import com.spacesim.world.SettlementRecoveryState.DemobilizationDirective;
import com.spacesim.world.SettlementRecoveryState.FleetLossRecord;
import com.spacesim.world.SettlementRecoveryState.ObligationStatus;
import com.spacesim.world.SettlementRecoveryState.PaymentObligation;
import com.spacesim.world.SettlementRecoveryState.ReplacementDemand;
import com.spacesim.world.SettlementRecoveryState.ReplacementStatus;
import com.spacesim.world.SettlementRecoveryState.Settlement;
import com.spacesim.world.SettlementRecoveryState.SettlementStatus;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettlementRecoveryStateCodecTest {

    @Test
    void roundTripIsDeterministicAndCanonicalAcrossInputOrdering() {
        Settlement a = new Settlement(1L, "proposal.1", "war.1", "faction.a", "faction.b",
                10L, 40L, SettlementStatus.EXECUTING, false);
        Settlement b = new Settlement(2L, "proposal.2", "war.2", "faction.c", "faction.d",
                20L, 40L, SettlementStatus.PENDING, false);
        PaymentObligation payment = new PaymentObligation(
                1L, 0, "faction.a", "faction.b", 5_000L, ObligationStatus.COMPLETE, 30L);
        DemobilizationDirective demobilization = new DemobilizationDirective(
                1L, 7L, "faction.a", 11L, ObligationStatus.COMPLETE, 35L);
        FleetLossRecord loss = new FleetLossRecord(1L, 9L, new FleetId(80L), "faction.a", 25L);
        ReplacementDemand demand = new ReplacementDemand(
                1L, 1L, new FleetId(80L), "faction.a", "fit.sha",
                26L, 40L, ReplacementStatus.COMMISSIONED, 900L, new FleetId(81L));
        SettlementRecoveryState first = new SettlementRecoveryState(
                SettlementRecoveryState.CURRENT_VERSION, 40L, 3L, 2L,
                List.of(b, a), List.of(payment), List.of(demobilization), List.of(loss), List.of(demand));
        SettlementRecoveryState second = new SettlementRecoveryState(
                SettlementRecoveryState.CURRENT_VERSION, 40L, 3L, 2L,
                List.of(a, b), List.of(payment), List.of(demobilization), List.of(loss), List.of(demand));

        byte[] firstBytes = SettlementRecoveryStateCodec.encode(first);
        byte[] secondBytes = SettlementRecoveryStateCodec.encode(second);

        assertArrayEquals(firstBytes, secondBytes);
        assertEquals(first, SettlementRecoveryStateCodec.decode(firstBytes));
    }

    @Test
    void completedPaymentAtTickZeroRoundTripsWithoutAmbiguousNonCompleteTimestamp() {
        Settlement settlement = new Settlement(
                1L, "proposal.zero", "war.zero", "faction.a", "faction.b",
                0L, 0L, SettlementStatus.COMPLETE, false);
        PaymentObligation completedAtZero = new PaymentObligation(
                1L, 0, "faction.a", "faction.b", 1_000L, ObligationStatus.COMPLETE, 0L);
        SettlementRecoveryState state = new SettlementRecoveryState(
                SettlementRecoveryState.CURRENT_VERSION, 0L, 2L, 1L,
                List.of(settlement), List.of(completedAtZero), List.of(), List.of(), List.of());

        assertEquals(state, SettlementRecoveryStateCodec.decode(SettlementRecoveryStateCodec.encode(state)));
        assertThrows(IllegalArgumentException.class, () -> new PaymentObligation(
                1L, 0, "faction.a", "faction.b", 1_000L, ObligationStatus.PENDING, 1L));
        assertThrows(IllegalArgumentException.class, () -> new PaymentObligation(
                1L, 0, "faction.a", "faction.b", 1_000L, ObligationStatus.COMPLETE, -1L));
    }

    @Test
    void duplicateLossReplacementAndFutureStateFailClosed() {
        Settlement settlement = new Settlement(1L, "proposal.1", "war.1", "faction.a", "faction.b",
                10L, 20L, SettlementStatus.PENDING, false);
        FleetLossRecord loss = new FleetLossRecord(1L, 5L, new FleetId(10L), "faction.a", 15L);
        assertThrows(IllegalArgumentException.class, () -> new SettlementRecoveryState(
                1, 20L, 2L, 1L, List.of(settlement), List.of(), List.of(), List.of(loss, loss), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new SettlementRecoveryState(
                1, 19L, 2L, 1L, List.of(settlement), List.of(), List.of(), List.of(loss), List.of()));
    }

    @Test
    void corruptTruncatedAndTrailingPayloadsAreRejected() {
        byte[] bytes = SettlementRecoveryStateCodec.encode(SettlementRecoveryState.empty(5L));
        byte[] corruptMagic = bytes.clone();
        corruptMagic[0] ^= 0x01;
        assertThrows(IllegalArgumentException.class, () -> SettlementRecoveryStateCodec.decode(corruptMagic));
        assertThrows(IllegalArgumentException.class,
                () -> SettlementRecoveryStateCodec.decode(Arrays.copyOf(bytes, bytes.length - 1)));
        byte[] trailing = Arrays.copyOf(bytes, bytes.length + 1);
        trailing[trailing.length - 1] = 1;
        assertThrows(IllegalArgumentException.class, () -> SettlementRecoveryStateCodec.decode(trailing));
    }
}
