package com.spacesim.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PresentationPipelineTest {
    @Test
    void executesLayersInStableOrder() {
        PresentationPipeline<String> pipeline = new PresentationPipeline<>();
        List<String> calls = new ArrayList<>();

        pipeline.register(PresentationLayer.UI, "hud", frame -> calls.add("hud:" + frame));
        pipeline.register(PresentationLayer.WORLD, "ships", frame -> calls.add("ships:" + frame));
        pipeline.register(PresentationLayer.BACKGROUND, "stars", frame -> calls.add("stars:" + frame));
        pipeline.register(PresentationLayer.WORLD, "stations", frame -> calls.add("stations:" + frame));
        pipeline.register(PresentationLayer.EFFECTS, "engines", frame -> calls.add("engines:" + frame));
        pipeline.register(PresentationLayer.OVERLAY, "selection", frame -> calls.add("selection:" + frame));

        pipeline.render("frame");

        assertEquals(
                List.of(
                        "stars:frame",
                        "ships:frame",
                        "stations:frame",
                        "engines:frame",
                        "selection:frame",
                        "hud:frame"),
                calls);
        assertEquals(
                List.of("stars", "ships", "stations", "engines", "selection", "hud"),
                pipeline.passIds());
        assertEquals(6, pipeline.size());
    }

    @Test
    void disabledPipelineIsExplicitNoOp() {
        PresentationPipeline<String> pipeline = new PresentationPipeline<>();
        List<String> calls = new ArrayList<>();
        pipeline.register(PresentationLayer.WORLD, "world", calls::add);

        assertTrue(pipeline.isEnabled());
        pipeline.setEnabled(false);
        assertFalse(pipeline.isEnabled());

        pipeline.render(null);
        assertTrue(calls.isEmpty());

        pipeline.setEnabled(true);
        pipeline.render("visible");
        assertEquals(List.of("visible"), calls);
    }

    @Test
    void rejectsInvalidRegistrationAndEnabledNullFrame() {
        PresentationPipeline<String> pipeline = new PresentationPipeline<>();
        pipeline.register(PresentationLayer.WORLD, "world", frame -> { });

        assertThrows(
                IllegalArgumentException.class,
                () -> pipeline.register(PresentationLayer.UI, " world ", frame -> { }));
        assertThrows(
                IllegalArgumentException.class,
                () -> pipeline.register(PresentationLayer.UI, "   ", frame -> { }));
        assertThrows(
                NullPointerException.class,
                () -> pipeline.register(null, "other", frame -> { }));
        assertThrows(
                NullPointerException.class,
                () -> pipeline.register(PresentationLayer.UI, null, frame -> { }));
        assertThrows(
                NullPointerException.class,
                () -> pipeline.register(PresentationLayer.UI, "other", null));
        assertThrows(NullPointerException.class, () -> pipeline.render(null));
    }

    @Test
    void returnedPassIdListIsImmutable() {
        PresentationPipeline<String> pipeline = new PresentationPipeline<>();
        pipeline.register(PresentationLayer.WORLD, "world", frame -> { });

        List<String> ids = pipeline.passIds();

        assertThrows(UnsupportedOperationException.class, () -> ids.add("mutated"));
        assertEquals(List.of("world"), pipeline.passIds());
    }
}
