package com.spacesim.content;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22AuthoredResourceFragmentsTest {
    @Test
    void missingFragmentFailsClosed() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                Stage22AuthoredResourceFragments.read(
                        Stage22AuthoredResourceFragmentsTest.class,
                        List.of("data/content/definitely-missing-stage22-fragment"),
                        "missing acceptance fixture"));
        assertTrue(exception.getMessage().contains("Missing"));
    }
}
