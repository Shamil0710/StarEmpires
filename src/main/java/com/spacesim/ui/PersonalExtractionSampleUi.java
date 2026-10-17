package com.spacesim.ui;

import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.persistence.Stage20FreightPersistentState;
import com.spacesim.persistence.Stage20FreightPersistentState.CargoLotState;
import com.spacesim.ui.GeneratedWorldUiSnapshot.InfoSection;
import com.spacesim.world.FleetId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Own remaining excavation cargo, without reading physical occurrence truth or expanding receipts. */
final class PersonalExtractionSampleUi {
    private PersonalExtractionSampleUi() { }

    static List<ProductionUiSnapshot.Row> rows(com.spacesim.world.Stage20DiscoveryKnowledgeState knowledge,
            double nowSeconds, long tick, Collection<FleetId> owned, List<CargoLotState> lots,
            Stage18ResourceOntologyCatalog ontology) {
        var observed = PersonalResourceUiProjection.rows(knowledge, nowSeconds, tick);
        var samples = sections(owned, lots, ontology);
        var entries = new TreeMap<String, com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticKnowledge>();
        for (var entry : knowledge.entries()) {
            var object = entry.object();
            entries.put("personal-resource:" + object.systemId().value() + ':' + object.kind() + ':' + object.objectId(), entry);
        }
        var result = new ArrayList<ProductionUiSnapshot.Row>();
        for (var row : observed) {
            var entry = entries.get(row.selection().stableId());
            var details = new ArrayList<>(row.sections());
            if (entry.object().kind() == com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectKind.RESOURCE_OCCURRENCE
                    && entry.resourceKnowledge().level() != com.spacesim.world.Stage20DiscoveryKnowledgeState.ResourceKnowledgeLevel.NONE) {
                var cargo = samples.getOrDefault(entry.object().objectId(), List.of());
                details.addAll(cargo);
                if (cargo.isEmpty() && entry.evidence().stream().anyMatch(e -> e.provenanceId().startsWith("personal-extraction-sample:")))
                    details.add(InfoSection.of("Добытый образец", "Свидетельство", "Образец ранее получен",
                            "Собственные трюмы", "Груз этого образца отсутствует; ранее сохранённое наблюдение остаётся в архиве"));
            }
            result.add(new ProductionUiSnapshot.Row(row.selection(), row.name(), row.category(), row.summary(),
                    details, row.provenance(), row.focusSystem(), row.focusFleet(), row.chronologicalTick()));
        }
        return List.copyOf(result);
    }

    static Map<String, List<InfoSection>> sections(Collection<FleetId> owned,
            List<CargoLotState> lots, Stage18ResourceOntologyCatalog ontology) {
        var owners = Set.copyOf(owned);
        var sources = new TreeMap<String, Map<String, Sample>>();
        for (var lot : lots) {
            if (!owners.contains(lot.fleetId())
                    || !lot.orderId().equals(Stage20FreightPersistentState.personalExtractionOrderId(lot.fleetId()))
                    || !lot.sourceProvenanceId().equals("player-extraction:" + lot.sourceEndpointId())) continue;
            var sample = sources.computeIfAbsent(lot.sourceEndpointId(), ignored -> new TreeMap<>())
                    .computeIfAbsent(lot.commodityId(), ignored -> new Sample());
            sample.mass += lot.massKg();
            sample.first = Math.min(sample.first, lot.loadedAtSimulationSeconds());
            double last = lot.receiptSeries() == null ? lot.loadedAtSimulationSeconds()
                    : lot.receiptSeries().lastTick() * lot.receiptSeries().fixedStepSeconds();
            sample.last = Math.max(sample.last, last);
        }
        var result = new TreeMap<String, List<InfoSection>>();
        sources.forEach((source, commodities) -> {
            var sections = new ArrayList<InfoSection>();
            commodities.forEach((commodityId, sample) -> {
                var commodity = ontology.findCommodity(commodityId);
                sections.add(InfoSection.of("Добытый образец в собственных трюмах",
                        "Материал", commodity == null ? "Материал вне текущего каталога" : commodity.displayName(),
                        "Оставшаяся масса", String.format(Locale.ROOT, "%.3f кг", sample.mass),
                        "Самая ранняя сохранившаяся добыча", String.format(Locale.ROOT, "%.1f с кампании", sample.first),
                        "Самая поздняя сохранившаяся добыча", String.format(Locale.ROOT, "%.1f с кампании", sample.last),
                        "Основание", "Квитанции фактической добычи на собственных кораблях; проданный и выгруженный груз исключён",
                        "Границы измерения", "Масса оставшегося груза; содержание и запас месторождения не определены"));
            });
            result.put(source, List.copyOf(sections));
        });
        return Map.copyOf(result);
    }

    private static final class Sample {
        double mass;
        double first = Double.POSITIVE_INFINITY;
        double last;
    }
}
