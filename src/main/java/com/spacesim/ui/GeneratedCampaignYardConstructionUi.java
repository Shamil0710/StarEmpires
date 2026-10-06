package com.spacesim.ui;

import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage22CivilianMiningProductionPath;
import com.spacesim.content.Stage23YardConstructionCatalog;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.SelectionKind;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.UiSelection;
import com.spacesim.ui.GeneratedWorldUiSnapshot.InfoSection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Physical yard bills and paid work visible only to the actual personal station owner. */
public final class GeneratedCampaignYardConstructionUi {
    private GeneratedCampaignYardConstructionUi() { }

    /**
     * Projects authored materials and actual retained work without granting an operating yard.
     * @param campaign same live physical authority
     * @return immutable owner-bound rows
     */
    public static List<ProductionUiSnapshot.Row> rows(Stage228CampaignAuthority campaign) {
        if (campaign.playerState().isEmpty()) return List.of();
        var catalog = Stage23YardConstructionCatalog.loadDefault();
        var yards = Stage22CivilianMiningProductionPath.loadRuntimeShipyards();
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var result = new ArrayList<ProductionUiSnapshot.Row>();
        long tick = campaign.coordinator().runtime().world().getAuthoritativeWorldTick();
        for (var station : campaign.coordinator().runtime().industry().industrial().stations()) {
            if (!campaign.ownsProductionStation(station.stationId())) continue;
            for (var installed : station.yards()) {
                result.add(new ProductionUiSnapshot.Row(new UiSelection(SelectionKind.SURFACE_ROW,
                        "pilot-yard-resources|" + station.stationId() + '|' + installed.yardInstanceId()),
                        "Ресурсы: " + yards.findYard(installed.yardDefinitionId()).displayName(), "Ввод верфи в работу",
                        installed.enabled() ? "Верфь включена; возможности зависят от опор" : "Верфь отключена",
                        List.of(InfoSection.of("Существующее распределение", "Энергия", String.format(Locale.ROOT, "%.0f Вт", installed.allocatedIntegrationPowerW()),
                                "Работа", String.format(Locale.ROOT, "%.2f рабочих секунд / с", installed.availableIntegrationWorkRate()),
                                "Персонал", Integer.toString(installed.availableLaborCapacity()),
                                "Автоматизация", Integer.toString(installed.availableAutomationCapacity()),
                                "Действие", "Перенести существующие ресурсы других верфей этой станции до штатного уровня",
                                "Последствие", "Прежние верфи потеряют часть мощности; нехватка ресурсов или действующих опор отклоняет действие")),
                        "Фактические распределения собственной станции", station.systemId(), 0, tick));
            }
            for (var spec : catalog.specifications()) {
                var yard = yards.findYard(spec.yardDefinitionId());
                if (!yard.allowedLocationTags().contains(station.stationNode().locationTag())) continue;
                var fields = new ArrayList<String>();
                fields.add("Масса сооружения"); fields.add(String.format(Locale.ROOT, "%.0f кг", spec.installedMassKg()));
                fields.add("Работа"); fields.add(String.format(Locale.ROOT, "%.0f рабочих секунд", spec.requiredWorkSeconds()));
                fields.add("Строительная линия"); fields.add(campaign.hasYardConstructionLine(station.stationId(), spec.yardDefinitionId())
                        ? "Есть действующая совместимая установка" : "Нет действующей совместимой установки");
                spec.requiredMassByCommodityKg().forEach((id, kg) -> {
                    fields.add(ontology.findCommodity(id).displayName());
                    fields.add(String.format(Locale.ROOT, "Нужно %.0f кг; доступно %.0f кг", kg, station.storage().commodityMassKg(id)));
                });
                fields.add("Условия"); fields.add("Собственная станция, физическая стыковка и действующая строительная линия");
                fields.add("Доставка"); fields.add("После подтверждения доступные материалы автоматически перевозятся на стройплощадку общим бюджетом погрузки станции");
                fields.add("Начало работы"); fields.add("Только после доставки всего состава; недостающие материалы нужно привезти на склад станции");
                fields.add("После постройки"); fields.add("Верфь отключена; энергия, персонал и рабочая мощность требуют отдельного распределения");
                result.add(new ProductionUiSnapshot.Row(new UiSelection(SelectionKind.SURFACE_ROW,
                        "pilot-yard-construction|" + station.stationId() + '|' + spec.yardDefinitionId()), yard.displayName(),
                        "Строительство верфи", "Заказать структуру", List.of(InfoSection.of("Физический заказ", fields.toArray(String[]::new))),
                        "Авторская спецификация и фактический склад собственной станции", station.systemId(), 0, tick));
            }
            for (var order : campaign.yardConstruction().orders()) {
                if (!order.stationId().equals(station.stationId())) continue;
                var spec = catalog.find(order.yardDefinitionId());
                boolean complete = order.completedWorkSeconds() == spec.requiredWorkSeconds();
                boolean cancellable = !complete && order.completedWorkSeconds() == 0d;
                String prefix = cancellable ? "pilot-yard-construction-cancel|" : "personal-yard-construction|";
                result.add(new ProductionUiSnapshot.Row(new UiSelection(SelectionKind.SURFACE_ROW,
                        prefix + station.stationId() + '|' + order.orderId()), yards.findYard(order.yardDefinitionId()).displayName(),
                        "Строительный заказ", complete ? "Структура верфи построена" : order.stagedAtSite() ? "Доставка материалов и работа" : "Материалы зарезервированы",
                        List.of(InfoSection.of("Фактическое строительство", "Выполнено",
                                String.format(Locale.ROOT, "%.2f %%", 100d * order.completedWorkSeconds() / spec.requiredWorkSeconds()),
                                "Материалы", complete ? "В составе структуры" : order.stagedAtSite()
                                        ? String.format(Locale.ROOT, "%.0f из %.0f кг на стройплощадке; только состав этого заказа",
                                                order.deliveredMassByCommodityKg().values().stream().mapToDouble(Double::doubleValue).sum(), spec.installedMassKg())
                                        : "В заказе; занимают место на складе",
                                "Отмена", cancellable ? "Вернёт доставленный резерв до начала работы, если на складе достаточно места" : "После начала работы требуется разборка с учётом потерь",
                                "Источник работы", "Существующая установка; общий бюджет с изготовлением и строительством установок")),
                        "Сохраняемый заказ и завершённые такты физической службы", station.systemId(), 0, tick));
            }
        }
        return List.copyOf(result);
    }
}
