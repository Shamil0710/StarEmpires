package com.spacesim.persistence;

import com.spacesim.player.PlayerJournalState;
import com.spacesim.player.PlayerJournalState.Kind;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerJournalPersistenceCodecTest {
    @Test void exactEvidenceAcknowledgementAndNewNotificationsSurviveDeterministicBytes() {
        var journal = PlayerJournalState.empty().append(4, Kind.COMMAND, "BUY", "station", "water", 3, -1250, 7)
                .append(4, Kind.MANUFACTURING_COMPLETED, "MANUFACTURING_COMPLETED", "station", "module", 1, 0, 0);
        assertEquals(2, journal.unreadCount());
        var read = journal.acknowledge(1);
        assertEquals(1, read.unreadCount());
        assertEquals(journal.entries(), read.entries());
        var bytes = PlayerJournalPersistenceCodec.encode(read);
        assertEquals(read, PlayerJournalPersistenceCodec.decode(bytes));
        assertArrayEquals(bytes, PlayerJournalPersistenceCodec.encode(PlayerJournalPersistenceCodec.decode(bytes)));
        var next = read.append(5, Kind.MINING_STOPPED, "SOURCE_DEPLETED", "source", "method", 0, 0, 7);
        assertEquals(2, next.unreadCount());
        assertEquals(1, next.acknowledge(0).acknowledgedThroughSequence());
        assertThrows(IllegalArgumentException.class, () -> next.acknowledge(next.nextSequence()));
    }

    @Test void boundedRetentionNeverReusesIdentityAndAcknowledgementCannotHideFutureEvents() {
        var journal = PlayerJournalState.empty();
        for (int i = 0; i < PlayerJournalState.CAPACITY + 10; i++)
            journal = journal.append(i, Kind.COMMAND, "DOCK", "station", "", 0, 0, 7);
        assertEquals(PlayerJournalState.CAPACITY, journal.entries().size());
        assertEquals(11, journal.entries().get(0).sequence());
        assertEquals(PlayerJournalState.CAPACITY + 11, journal.nextSequence());
        var read = journal.acknowledge(journal.nextSequence() - 1);
        assertEquals(0, read.unreadCount());
        assertEquals(1, read.append(3000, Kind.COMMAND, "UNDOCK", "", "", 0, 0, 7).unreadCount());
        assertEquals(read, PlayerJournalPersistenceCodec.decode(PlayerJournalPersistenceCodec.encode(read)));
    }

    @Test void corruptedLayoutsAndFutureAcknowledgementsRejectBeforeAdoption() {
        var journal = PlayerJournalState.empty().append(4, Kind.COMMAND, "BUY", "station", "water", 1, -1, 7);
        var bytes = PlayerJournalPersistenceCodec.encode(journal);
        assertThrows(IllegalArgumentException.class, () -> PlayerJournalPersistenceCodec.decode(Arrays.copyOf(bytes, bytes.length - 1)));
        assertThrows(IllegalArgumentException.class, () -> PlayerJournalPersistenceCodec.decode(Arrays.copyOf(bytes, bytes.length + 1)));
        var excessive = bytes.clone(); ByteBuffer.wrap(excessive).putInt(20, Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> PlayerJournalPersistenceCodec.decode(excessive));
        var wrongSchema = bytes.clone(); ByteBuffer.wrap(wrongSchema).putInt(0, 2);
        assertThrows(IllegalArgumentException.class, () -> PlayerJournalPersistenceCodec.decode(wrongSchema));
        assertThrows(IllegalArgumentException.class, () -> new PlayerJournalState(3, 0, journal.entries()));
        assertThrows(IllegalArgumentException.class, () -> new PlayerJournalState(2, 2, journal.entries()));
        assertThrows(IllegalArgumentException.class, () -> new PlayerJournalState(2, 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> journal.append(3, Kind.COMMAND, "BUY", "", "", 1, 0, 7));
        assertThrows(IllegalArgumentException.class, () -> journal.append(5, Kind.COMMAND, "BUY", "x".repeat(513), "", 1, 0, 7));
    }
}
