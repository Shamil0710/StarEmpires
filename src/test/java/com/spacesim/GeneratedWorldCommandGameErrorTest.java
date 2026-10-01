package com.spacesim;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class GeneratedWorldCommandGameErrorTest {
    @Test
    void errorsExplainFailureWithoutExposingPathsOrSavePayload() {
        String privatePayload = "/home/private-user/campaign.s25 wallet-secret-content";
        for (var failure : List.of(
                new IOException(privatePayload),
                new NoSuchFileException(privatePayload),
                new IllegalArgumentException(privatePayload),
                new IllegalStateException(privatePayload),
                new RuntimeException(privatePayload))) {
            String displayed = GeneratedWorldCommandGame.safeMessage(failure);
            assertFalse(displayed.isBlank());
            assertTrue(displayed.length() < 160);
            assertFalse(displayed.contains("/home/"));
            assertFalse(displayed.contains("private-user"));
            assertFalse(displayed.contains("wallet-secret-content"));
        }
    }
}
