package com.spacesim.ui;

import com.spacesim.world.Stage20DiscoveryKnowledgeState;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.*;
import com.spacesim.world.Stage21HPlayerMissionAuthority;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.persistence.Stage20DiscoveryPersistenceCodec;
import com.spacesim.persistence.Stage20DiscoveryPersistentState;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PersonalResourceUiProjectionTest {
    private static final String PLAYER = Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID;
    private static final StaticObjectRef SOURCE = new StaticObjectRef(new StarSystemId(1), StaticObjectKind.RESOURCE_OCCURRENCE, "source");

    @Test void unknownOrSampleOnlyEvidenceCannotProduceQuantitativeResourceClaims() {
        var unknown = PersonalResourceUiProjection.section(new Stage20DiscoveryKnowledgeState(PLAYER, List.of()), SOURCE, 5);
        assertEquals("Оценка отсутствует", value(unknown, "Извлекаемая масса"));
        var sample = knowledge(PLAYER, new ResourceKnowledge(ResourceKnowledgeLevel.RESOURCE_INDICATION, Optional.empty(), Optional.empty()));
        var projected = PersonalResourceUiProjection.section(sample, SOURCE, 6);
        assertEquals("Оценка отсутствует", value(projected, "Содержание"));
        assertEquals("Оценка отсутствует", value(projected, "Извлекаемая масса"));
        assertTrue(value(projected, "Свидетельство").contains("личное наблюдение"));
        assertEquals(1, sample.entries().size());
    }

    @Test void actualBoundedEvidenceRetainsOriginalRangesTimeAndExpiredFreshnessWithoutBecomingTruth() {
        var measured = knowledge(PLAYER, new ResourceKnowledge(ResourceKnowledgeLevel.ESTIMATED_GRADE_RESERVE,
                Optional.of("family"), Optional.of(new ResourceEstimate(.2, .4, 100, 300, .7))));
        var current = PersonalResourceUiProjection.section(measured, SOURCE, 7);
        assertEquals("20.0% — 40.0%", value(current, "Содержание по оценке"));
        assertEquals("100.0 кг — 300.0 кг", value(current, "Извлекаемая масса по оценке"));
        assertEquals("70.0%", value(current, "Уверенность наблюдения"));
        assertEquals("5.0 с кампании", value(current, "Время сведений"));
        var expired = PersonalResourceUiProjection.section(measured, SOURCE, 11);
        assertTrue(value(expired, "Актуальность").contains("истёк"));
        assertEquals(value(current, "Извлекаемая масса по оценке"), value(expired, "Извлекаемая масса по оценке"));
        assertEquals(value(current, "Время сведений"), value(expired, "Время сведений"));
        var other = new StaticObjectRef(new StarSystemId(1), StaticObjectKind.RESOURCE_OCCURRENCE, "other");
        assertEquals("Оценка отсутствует", value(PersonalResourceUiProjection.section(measured, other, 11), "Извлекаемая масса"));
    }

    @Test void foreignViewerAndInvalidTimesCannotBeUsedAsPersonalMeasurementAuthority() {
        var foreign = knowledge("foreign", new ResourceKnowledge(ResourceKnowledgeLevel.ESTIMATED_GRADE_RESERVE,
                Optional.of("family"), Optional.of(new ResourceEstimate(.2, .4, 100, 300, .7))));
        assertThrows(IllegalArgumentException.class, () -> PersonalResourceUiProjection.section(foreign, SOURCE, 6));
        var empty = new Stage20DiscoveryKnowledgeState(PLAYER, List.of());
        assertThrows(IllegalArgumentException.class, () -> PersonalResourceUiProjection.section(empty, SOURCE, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> PersonalResourceUiProjection.section(empty, SOURCE, -1));
    }

    @Test void intelligenceAndInspectorRetainTheSameMeasuredRangesAfterSaveLoadAndExpiry() {
        var measured = knowledge(PLAYER, new ResourceKnowledge(ResourceKnowledgeLevel.ESTIMATED_GRADE_RESERVE,
                Optional.of("family"), Optional.of(new ResourceEstimate(.2, .4, 100, 300, .7))));
        var foreign = knowledge("foreign", new ResourceKnowledge(ResourceKnowledgeLevel.SURVEYED_DEPOSIT,
                Optional.of("family"), Optional.of(new ResourceEstimate(.8, .9, 9000, 10000, .99))));
        var sidecar = new Stage20DiscoveryPersistentState(1, 1L, "generation", "fingerprint", List.of(measured, foreign));
        var restored = Stage20DiscoveryPersistenceCodec.decode(Stage20DiscoveryPersistenceCodec.encode(sidecar));
        assertEquals(sidecar, restored);
        var rows = PersonalResourceUiProjection.rows(restored.knowledgeFor(PLAYER), 11, 110);
        assertEquals(1, rows.size());
        assertEquals(PersonalResourceUiProjection.section(measured, SOURCE, 11), rows.get(0).sections().get(0));
        assertTrue(value(rows.get(0).sections().get(0), "Актуальность").contains("истёк"));
        assertEquals("100.0 кг — 300.0 кг", value(rows.get(0).sections().get(0), "Извлекаемая масса по оценке"));
        assertEquals(SOURCE.systemId(), rows.get(0).focusSystem());
        assertThrows(UnsupportedOperationException.class, rows::clear);
        assertEquals(rows, PersonalResourceUiProjection.rows(measured, 11, 110));
        assertThrows(IllegalArgumentException.class, () -> PersonalResourceUiProjection.rows(foreign, 11, 110));
    }

    @Test void detectionsCannotInventLocationsAndResourceKeysDistinguishSystemsAndKinds() {
        var indication = ResourceKnowledge.none();
        var evidence = List.of(new DiscoveryEvidence(DiscoverySource.PASSIVE_SENSOR, "actual-contact", 5, OptionalDouble.of(10)));
        var refs = List.of(SOURCE,
                new StaticObjectRef(new StarSystemId(2), StaticObjectKind.RESOURCE_OCCURRENCE, "source"),
                new StaticObjectRef(SOURCE.systemId(), StaticObjectKind.RESOURCE_HOST, "source"));
        var detections = new Stage20DiscoveryKnowledgeState(PLAYER, refs.stream().map(ref -> new StaticKnowledge(
                ref, DiscoveryState.DETECTED, Optional.empty(), Optional.empty(), indication, evidence, 5, 5)).toList());
        var rows = PersonalResourceUiProjection.rows(detections, 6, 60);
        assertEquals(3, rows.size());
        assertEquals(3L, rows.stream().map(ProductionUiSnapshot.Row::selection).distinct().count());
        assertTrue(rows.stream().allMatch(row -> row.focusSystem() == null));
        assertTrue(rows.stream().allMatch(row -> value(row.sections().get(0), "Извлекаемая масса").equals("Оценка отсутствует")));
        var empty = new Stage20DiscoveryKnowledgeState(PLAYER, List.of());
        assertTrue(PersonalResourceUiProjection.rows(empty, 6, 60).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> PersonalResourceUiProjection.rows(empty, Double.NaN, 60));
        assertThrows(IllegalArgumentException.class, () -> PersonalResourceUiProjection.rows(empty, 6, -1));
    }

    private static Stage20DiscoveryKnowledgeState knowledge(String owner, ResourceKnowledge resource) {
        String classification = resource.resourceFamilyId().orElse("sample-material");
        var row = new StaticKnowledge(SOURCE, DiscoveryState.KNOWN_STATIC_LOCATION, Optional.of(classification),
                Optional.of(LocalPhysicalPosition.origin()), resource, List.of(new DiscoveryEvidence(
                DiscoverySource.PHYSICAL_VISIT_OR_SURVEY, "explicit-measurement-fixture", 5, OptionalDouble.of(10))), 5, 5);
        return new Stage20DiscoveryKnowledgeState(owner, List.of()).withKnowledge(row);
    }

    private static String value(GeneratedWorldUiSnapshot.InfoSection section, String label) {
        return section.lines().stream().filter(l -> l.label().equals(label)).findFirst().orElseThrow().value();
    }
}
