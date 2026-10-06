package com.spacesim.ui;

import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.content.Stage22CivilianMiningProductionPath;
import com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.SelectionKind;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.UiSelection;
import com.spacesim.ui.GeneratedWorldUiSnapshot.InfoSection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Private station inventory projection for individual removed equipment. */
public final class GeneratedCampaignModuleCustodyUi {
    private GeneratedCampaignModuleCustodyUi() { }

    /**
     * Projects exact equipment condition only for actual personally owned stations.
     * @param campaign authoritative live campaign
     * @return read-only individual equipment rows
     */
    public static List<ProductionUiSnapshot.Row> rows(Stage228CampaignAuthority campaign) {
        if (campaign.moduleCustody().modules().isEmpty() || campaign.playerState().isEmpty()) return List.of();
        var result = new ArrayList<ProductionUiSnapshot.Row>();
        var products = Stage22CivilianMiningProductionPath.loadProducts();
        var engineering = Stage22FreightStrategicEngineeringCatalogLoader.loadDefault();
        for (var module : campaign.moduleCustody().modules()) {
            var carrier = campaign.moduleCarrier(module.stationId());
            if (!campaign.ownsStoredModule(module.custodyId())) continue;
            var condition = module.condition();
            var definition = engineering.findModule(condition.assignment().moduleId());
            var product = products.findProduct(condition.assignment().moduleId());
            var system = carrier.isPresent() ? campaign.coordinator().runtime().freight().findFreighter(carrier.get()).orElseThrow().currentSystemId()
                    : campaign.coordinator().runtime().industry().industrial().station(module.stationId()).systemId();
            var section = InfoSection.of("Снятое оборудование",
                    "Место хранения", carrier.isPresent() ? "Трюм корабля " + carrier.get().value() : module.stationId(),
                    "Резерв", campaign.isStoredModuleReserved(module.custodyId()) ? "Зарезервировано для физической работы" : "Свободно",
                    "Перевозка", carrier.isPresent() ? "На борту корабля " + carrier.get().value() + "; выгрузка после стыковки на своей станции"
                            : "Погрузка на собственный корабль после подтверждения и обработки",
                    "Повторная установка", carrier.isPresent() ? "Сначала выгрузите модуль на свою станцию" : campaign.hasPersonalStoredModuleRefitAccess(module.custodyId())
                            ? "Доступна после подтверждения; повреждения и возраст сохранятся" : "Проверьте компоновку, стыковку и доступ к верфи",
                    "Целостность", String.format(Locale.ROOT, "%.1f %%", condition.integrity() * 100),
                    "Возраст обслуживания", String.format(Locale.ROOT, "%.1f с", condition.secondsSinceService()),
                    carrier.isPresent() ? "Занято в трюме" : "Занято на складе", String.format(Locale.ROOT, "%.1f кг", product.unitMassKg()),
                    "Исходный корабль", Long.toString(module.sourceAssetId()),
                    "Момент снятия", "Такт " + module.removedAtTick());
            result.add(new ProductionUiSnapshot.Row(new UiSelection(SelectionKind.SURFACE_ROW,
                    (carrier.isPresent() ? "carried-module|" : "stored-module|") + module.custodyId()), definition == null ? "Снятый модуль" : definition.displayName(),
                    carrier.isPresent() ? "Оборудование в трюме" : "Оборудование на хранении", "Состояние сохранено", List.of(section),
                    "Индивидуальный модуль занимает место и сохраняет повреждения и возраст обслуживания",
                    system, 0, module.removedAtTick()));
        }
        for (var order : campaign.moduleTransfers().orders()) {
            String stationId = campaign.moduleCarrier(order.source().stationId()).isPresent() ? order.destinationStorageId() : order.source().stationId();
            if (!campaign.ownsProductionStation(stationId)) continue;
            double mass = products.findProduct(order.source().condition().assignment().moduleId()).unitMassKg();
            result.add(new ProductionUiSnapshot.Row(new UiSelection(SelectionKind.SURFACE_ROW,
                    "module-transfer-cancel|" + stationId + '|' + order.orderId()), "Обработка снятого модуля", "Погрузка / выгрузка",
                    String.format(Locale.ROOT, "%.1f %%", order.completedHandlingKg() / mass * 100),
                    List.of(InfoSection.of("Конечная обработка", "Источник", order.source().stationId(),
                            "Получатель", order.destinationStorageId(), "Обработано", String.format(Locale.ROOT, "%.1f / %.1f кг", order.completedHandlingKg(), mass),
                            "ID модуля", order.source().custodyId())), "До завершения модуль остаётся у источника; отмена освобождает резерв",
                    campaign.coordinator().runtime().industry().industrial().station(stationId).systemId(), 0, order.startedAtTick()));
        }
        return List.copyOf(result);
    }
}
