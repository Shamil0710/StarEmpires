package com.spacesim.ui;

import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.content.Stage18FacilityCatalogLoader;
import com.spacesim.content.Stage18FacilityConstructionCatalogLoader;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.economy.Stage18FacilityConstructionRuntime.OrderStatus;
import com.spacesim.economy.Stage18FacilityConstructionWorkQueue;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.SelectionKind;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.UiSelection;
import com.spacesim.ui.GeneratedWorldUiSnapshot.InfoSection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Owner-bound physical facility bills, reserved custody and completed installation evidence. */
public final class GeneratedCampaignConstructionUi {
    private GeneratedCampaignConstructionUi() { }

    /**
     * Projects only existing personal stations and their physical construction orders.
     * @param campaign same live campaign authority
     * @return immutable owner-bound construction rows
     */
    public static List<ProductionUiSnapshot.Row> rows(Stage228CampaignAuthority campaign) {
        if (campaign.playerState().isEmpty()) return List.of();
        var runtime = campaign.coordinator().runtime();
        var catalog = Stage18FacilityConstructionCatalogLoader.loadDefault();
        var facilities = Stage18FacilityCatalogLoader.loadDefault();
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var result = new ArrayList<ProductionUiSnapshot.Row>();
        for (var station : runtime.industry().industrial().stations()) {
            if (!campaign.ownsProductionStation(station.stationId())) continue;
            for (var binding : catalog.getFacilities()) {
                var definition = facilities.findFacility(binding.facilityDefinitionId());
                if (!definition.allowedLocationTags().contains("location.orbital_station")) continue;
                var fields = new ArrayList<String>();
                fields.add("Установка"); fields.add(definition.displayName());
                fields.add("Масса сооружения"); fields.add(String.format(Locale.ROOT, "%.0f кг", binding.installedMassKg()));
                fields.add("Работа"); fields.add(String.format(Locale.ROOT, "%.0f рабочих секунд", catalog.totalWorkSeconds(definition.id())));
                fields.add("Строительная линия"); fields.add(campaign.hasFacilityConstructionLine(station.stationId(), definition.id())
                        ? "Есть действующая совместимая установка" : "Нет действующей совместимой установки");
                catalog.requiredMassByCommodityKg(definition.id()).forEach((commodity, kg) -> {
                    fields.add(ontology.findCommodity(commodity).displayName());
                    fields.add(String.format(Locale.ROOT, "Нужно %.0f кг; доступно %.0f кг", kg, station.storage().commodityMassKg(commodity)));
                });
                fields.add("Условия"); fields.add("Собственная станция, физическая стыковка, весь запас материалов и конечная работа линии");
                fields.add("Ввод в работу"); fields.add("Завершённая установка не получает энергию и персонал автоматически");
                result.add(new ProductionUiSnapshot.Row(new UiSelection(SelectionKind.SURFACE_ROW,
                        "pilot-construction|" + station.stationId() + '|' + definition.id()), definition.displayName(),
                        "Строительство мощностей", "Заказать установку", List.of(InfoSection.of("Физический заказ", fields.toArray(String[]::new))),
                        "Авторский состав сооружения и фактический склад собственной станции", station.systemId(), 0,
                        runtime.world().getAuthoritativeWorldTick()));
            }
            for (var order : runtime.constructionQueue().capture()) {
                if (!Stage18FacilityConstructionWorkQueue.managed(order) || !order.stationId().equals(station.stationId())) continue;
                boolean complete = order.status() == OrderStatus.COMPLETE;
                boolean cancellable = !complete && order.completedWorkSeconds() == 0d;
                String key = (cancellable ? "pilot-construction-cancel|" : "personal-construction|") + station.stationId() + '|' + order.orderId();
                result.add(new ProductionUiSnapshot.Row(new UiSelection(SelectionKind.SURFACE_ROW, key),
                        facilities.findFacility(order.facilityDefinitionId()).displayName(), "Строительный заказ",
                        complete ? "Установка построена" : "Материалы зарезервированы",
                        List.of(InfoSection.of("Фактическое строительство", "Выполнено",
                                String.format(Locale.ROOT, "%.2f %%", 100d * order.completedWorkSeconds() / order.requiredWorkSeconds()),
                                "Материалы", complete ? "В составе построенной установки" : "В заказе; занимают место на складе",
                                "Отмена", cancellable ? "Вернёт весь резерв до начала работы" : complete
                                        ? "Заказ завершён" : "После начала работы требуется разборка с учётом потерь",
                                "Источник работы", "Существующая совместимая установка; изготовление использует тот же бюджет")),
                        "Сохраняемый заказ и завершённые такты физической службы", station.systemId(), 0,
                        runtime.world().getAuthoritativeWorldTick()));
                if (complete) {
                    var installed = station.facilities().stream().filter(f -> f.facilityInstanceId().equals(order.facilityInstanceId()))
                            .findFirst().orElseThrow();
                    result.add(new ProductionUiSnapshot.Row(new UiSelection(SelectionKind.SURFACE_ROW,
                            "pilot-facility-resources|" + station.stationId() + '|' + installed.facilityInstanceId()),
                            "Ресурсы: " + facilities.findFacility(installed.definitionId()).displayName(), "Ввод мощности",
                            installed.enabled() ? "Установка включена" : "Установка отключена",
                            List.of(InfoSection.of("Распределение ресурсов станции", "Энергия",
                                    String.format(Locale.ROOT, "%.0f Вт", installed.allocatedProcessPowerW()),
                                    "Теплоотвод", String.format(Locale.ROOT, "%.0f Вт", installed.availableHeatRejectionW()),
                                    "Персонал", String.format(Locale.ROOT, "%.1f", installed.availableLaborUnits()),
                                    "Обслуживание", String.format(Locale.ROOT, "%.1f рабочих секунд / с", installed.availableMaintenanceWorkRate()),
                                    "Действие", "Перераспределить существующие ресурсы других установок этой станции до штатного уровня",
                                    "Последствие", "Мощность прежних установок уменьшится; при недостатке ресурсов действие отклоняется")),
                            "Существующие распределения собственной станции; новые ресурсы не создаются", station.systemId(), 0,
                            runtime.world().getAuthoritativeWorldTick()));
                }
            }
        }
        return List.copyOf(result);
    }
}
