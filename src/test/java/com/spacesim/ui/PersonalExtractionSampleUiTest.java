package com.spacesim.ui;

import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.persistence.ExtractionReceiptSeries;
import com.spacesim.persistence.Stage20FreightPersistentState;
import com.spacesim.persistence.Stage20FreightPersistentState.CargoLotState;
import com.spacesim.world.FleetId;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import com.spacesim.world.Stage20DiscoveryKnowledgeState;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.*;
import com.spacesim.world.Stage20DiscoveryKnowledgeRuntime;
import com.spacesim.world.Stage21HPlayerMissionAuthority;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.LocalPhysicalPosition;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PersonalExtractionSampleUiTest {
    private static final FleetId OWN = new FleetId(1);
    private static final FleetId OTHER_OWN = new FleetId(2);
    private static final FleetId FOREIGN = new FleetId(3);

    @Test void fifoReportsOnlyRemainingOwnMassAndReceiptTimesWithoutExpandingHistory() {
        var series = new ExtractionReceiptSeries(10, 100, .5, 2, 1_000_000, 2)
                .dropReceipts(2).orElseThrow().consumeHead(.5).orElseThrow();
        var lot = new CargoLotState("freight-lot:" + series.firstOrdinal(), OWN,
                Stage20FreightPersistentState.personalExtractionOrderId(OWN), "commodity.feedstock.conductor_ore",
                series.remainingMassKg(), "source", "player-extraction:source", series.receipt(0).loadedAtSeconds(), series);
        var rows = PersonalExtractionSampleUi.sections(List.of(OWN, OTHER_OWN), List.of(lot,
                point(OTHER_OWN, "source", 3, 7), point(FOREIGN, "source", 900, 1),
                point(OWN, "elsewhere", 100, 2),
                new CargoLotState("trade", OWN, "trade-order", "commodity.feedstock.conductor_ore", 400,
                        "source", "player-extraction:source", 1)), Stage18ResourceOntologyLoader.loadDefault());
        var section = rows.get("source").get(0);
        assertEquals(Stage18ResourceOntologyLoader.loadDefault().findCommodity("commodity.feedstock.conductor_ore").displayName(),
                value(section, "Материал"));
        assertEquals("1999998.500 кг", value(section, "Оставшаяся масса"));
        assertEquals("7.0 с кампании", value(section, "Самая ранняя сохранившаяся добыча"));
        assertEquals("500049.5 с кампании", value(section, "Самая поздняя сохранившаяся добыча"));
        var fifoOnly = PersonalExtractionSampleUi.sections(List.of(OWN), List.of(lot), Stage18ResourceOntologyLoader.loadDefault());
        assertEquals("51.0 с кампании", value(fifoOnly.get("source").get(0), "Самая ранняя сохранившаяся добыча"));
        assertTrue(value(section, "Границы измерения").contains("не определены"));
        assertEquals(1, rows.get("source").size());
    }

    @Test void transferredOrUnownedCargoCannotClaimAnOwnExtractionSample() {
        var transferred = new CargoLotState("transfer", OWN,
                Stage20FreightPersistentState.personalExtractionOrderId(OWN), "commodity.feedstock.conductor_ore", 10,
                "source", "station-stock", 5);
        assertTrue(PersonalExtractionSampleUi.sections(List.of(OWN),
                List.of(transferred, point(FOREIGN, "source", 5, 3)), Stage18ResourceOntologyLoader.loadDefault()).isEmpty());
        assertTrue(PersonalExtractionSampleUi.sections(List.of(OWN), List.of(), Stage18ResourceOntologyLoader.loadDefault()).isEmpty());
    }

    @Test void cargoDoesNotCreateResourceKnowledgeAndSpentSampleRetainsItsOriginalEvidence() {
        var empty = new Stage20DiscoveryKnowledgeState(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID, List.of());
        var lots = List.of(point(OWN, "source", 123, 5));
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        assertTrue(PersonalExtractionSampleUi.rows(empty, 6, 12, List.of(OWN), lots, ontology).isEmpty());
        var object = new StaticObjectRef(new StarSystemId(1), StaticObjectKind.RESOURCE_OCCURRENCE, "source");
        var none = new Stage20DiscoveryKnowledgeRuntime().observe(empty,
                new Stage20DiscoveryKnowledgeRuntime.StaticObservation(object, DiscoveryState.KNOWN_STATIC_LOCATION,
                        Optional.of("commodity.feedstock.conductor_ore"), Optional.of(new LocalPhysicalPosition(0, 0, 1, 2)), ResourceKnowledge.none(),
                        new DiscoveryEvidence(DiscoverySource.ACTIVE_SCAN, "contact", 3, OptionalDouble.empty())));
        assertTrue(PersonalExtractionSampleUi.rows(none, 6, 12, List.of(OWN), lots, ontology).get(0)
                .sections().stream().noneMatch(section -> section.title().contains("Добытый образец")));
        var sampled = new Stage20DiscoveryKnowledgeRuntime().observe(none,
                new Stage20DiscoveryKnowledgeRuntime.StaticObservation(object, DiscoveryState.KNOWN_STATIC_LOCATION,
                        Optional.of("commodity.feedstock.conductor_ore"), Optional.of(new LocalPhysicalPosition(0, 0, 1, 2)),
                        new ResourceKnowledge(ResourceKnowledgeLevel.RESOURCE_INDICATION, Optional.empty(), Optional.empty()),
                        new DiscoveryEvidence(DiscoverySource.PHYSICAL_VISIT_OR_SURVEY,
                                "personal-extraction-sample:1:source", 5, OptionalDouble.empty())));
        var withCargo = PersonalExtractionSampleUi.rows(sampled, 6, 12, List.of(OWN), lots, ontology).get(0);
        assertTrue(withCargo.sections().stream().anyMatch(section -> section.title().equals("Добытый образец в собственных трюмах")));
        var spent = PersonalExtractionSampleUi.rows(sampled, 100, 200, List.of(OWN), List.of(), ontology).get(0);
        assertEquals("Получен физический образец", spent.summary());
        assertEquals("Образец ранее получен", value(spent.sections().get(2), "Свидетельство"));
        assertTrue(sampled.knowledge(object).orElseThrow().resourceKnowledge().estimate().isEmpty());
        assertEquals(5, sampled.knowledge(object).orElseThrow().lastUpdatedSeconds());
    }

    private static CargoLotState point(FleetId fleet, String source, double mass, double time) {
        return new CargoLotState("lot-" + fleet.value() + source, fleet,
                Stage20FreightPersistentState.personalExtractionOrderId(fleet), "commodity.feedstock.conductor_ore", mass,
                source, "player-extraction:" + source, time);
    }

    private static String value(GeneratedWorldUiSnapshot.InfoSection section, String label) {
        return section.lines().stream().filter(line -> line.label().equals(label)).findFirst().orElseThrow().value();
    }
}
