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

/** Personal finished-product inventory and finite physical handling projection. */
public final class GeneratedCampaignProductTransportUi {
    private GeneratedCampaignProductTransportUi() { }

    /**
     * Projects personal stock and physically contacted civilian product offers.
     * @param campaign live campaign authority
     * @return immutable stock and pending handling rows
     */
    public static List<ProductionUiSnapshot.Row> rows(Stage228CampaignAuthority campaign) {
        if (campaign.playerState().isEmpty()) return List.of();
        var result = new ArrayList<ProductionUiSnapshot.Row>();
        var products = Stage22CivilianMiningProductionPath.loadProducts();
        var engineering = Stage22FreightStrategicEngineeringCatalogLoader.loadDefault();
        var runtime = campaign.coordinator().runtime();
        var fleet = campaign.playerState().orElseThrow().activeFleetId();
        if (fleet == null || runtime.freight().findFreighter(fleet).isEmpty()) return List.of();
        var hold = runtime.freight().moduleCargoStorage(fleet);
        String docked = campaign.dockedModuleStationId().filter(campaign::ownsProductionStation).orElse("");
        for (var station : runtime.industry().industrial().stations()) {
            boolean own = campaign.ownsProductionStation(station.stationId());
            if (!own && !campaign.hasCivilianProductPurchaseContact(station.stationId())) continue;
            var store = runtime.infrastructure().endpoint(station.stationId()).storage();
            for (var entry : store.snapshot().productCountById().entrySet()) {
                var definition = engineering.findModule(entry.getKey());
                String name = definition == null ? "Готовый товар" : definition.displayName();
                var product = products.findProduct(entry.getKey());
                if (!own) {
                    if (store.productCount(entry.getKey()) <= 0) continue;
                    long price = com.spacesim.economy.FinishedProductPurchasePayment.quote(entry.getKey(), 1);
                    result.add(new ProductionUiSnapshot.Row(new UiSelection(SelectionKind.SURFACE_ROW,
                            "pilot-product-buy|" + station.stationId() + '|' + entry.getKey()), name, "Готовые изделия продавца",
                            store.productCount(entry.getKey()) + " шт. доступно", List.of(InfoSection.of("Покупка оборудования",
                            "Цена единицы", String.format(Locale.ROOT, "%.3f кр.", price / 1000d),
                            "Масса единицы", String.format(Locale.ROOT, "%.1f кг", product.unitMassKg()),
                            "Доставка", "Погрузка в личный трюм за игровые такты; товар остаётся у продавца до завершения",
                            "Оплата", "Деньги резервируются при подтверждении и поступают оператору после погрузки",
                            "Отмена", "Возвращает деньги и освобождает товар; выполненная обработка не возвращается")),
                            "Фактический запас гражданского оператора при личной стыковке",
                            station.systemId(), fleet.value(), runtime.world().getAuthoritativeWorldTick()));
                    continue;
                }
                result.add(new ProductionUiSnapshot.Row(new UiSelection(SelectionKind.SURFACE_ROW,
                        "pilot-product-load|" + station.stationId() + '|' + entry.getKey()), name, "Готовые товары на станции",
                        store.productCount(entry.getKey()) + " шт. доступно", List.of(InfoSection.of("Физический запас",
                        "На складе", entry.getValue() + " шт.", "В резерве", (entry.getValue() - store.productCount(entry.getKey())) + " шт.",
                        "Масса единицы", String.format(Locale.ROOT, "%.1f кг", product.unitMassKg()),
                        "Погрузка", "Выберите количество в штуках; требуется стыковка и свободный трюм")),
                        "Реальный запас собственной станции; обработка выполняется на общих тактах",
                        station.systemId(), 0, runtime.world().getAuthoritativeWorldTick()));
            }
        }
        for (var entry : hold.snapshot().productCountById().entrySet()) {
            var definition = engineering.findModule(entry.getKey());
            String name = definition == null ? "Готовый товар" : definition.displayName();
            result.add(new ProductionUiSnapshot.Row(new UiSelection(SelectionKind.SURFACE_ROW,
                    "pilot-product-unload|" + docked + '|' + entry.getKey()), name, "Готовые товары в трюме",
                    hold.productCount(entry.getKey()) + " шт. доступно", List.of(InfoSection.of("Собственный груз",
                    "На борту", entry.getValue() + " шт.", "В резерве", (entry.getValue() - hold.productCount(entry.getKey())) + " шт.",
                    "Выгрузка", docked.isEmpty() ? "Сначала состыкуйтесь на своей станции" : "После подтверждения дождитесь обработки")),
                    "Физический товар собственного корабля; происхождение сохраняется в кампании",
                    runtime.freight().findFreighter(fleet).orElseThrow().currentSystemId(), fleet.value(), runtime.world().getAuthoritativeWorldTick()));
        }
        for (var order : campaign.productTransfers().orders()) {
            if (!order.fleetId().equals(fleet) || !campaign.ownsProductionStation(order.stationId()) && order.purchasePayment() == null) continue;
            double mass = products.findProduct(order.productId()).unitMassKg() * order.count();
            result.add(new ProductionUiSnapshot.Row(new UiSelection(SelectionKind.SURFACE_ROW,
                    "product-transfer-cancel|" + order.stationId() + '|' + order.orderId()),
                    order.purchasePayment() != null ? "Покупка и погрузка оборудования" : order.loading() ? "Погрузка готового товара" : "Выгрузка готового товара", "Обработка товара",
                    String.format(Locale.ROOT, "%.1f %%", order.completedHandlingKg() / mass * 100),
                    List.of(InfoSection.of("Конечная обработка", "Количество", order.count() + " шт.",
                    "Обработано", String.format(Locale.ROOT, "%.1f / %.1f кг", order.completedHandlingKg(), mass),
                    "Источник", order.sourceStorageId(), "Получатель", order.destinationStorageId(),
                    "Денежный резерв", order.purchasePayment() == null ? "Нет" : String.format(Locale.ROOT, "%.3f кр.", order.purchasePayment().reservedMilliCredits() / 1000d),
                    "Отмена", "Освободит товар и вернёт денежный резерв; выполненная обработка не возвращается")),
                    "Товар остаётся у источника до завершения; пауза останавливает работу",
                    runtime.industry().industrial().station(order.stationId()).systemId(), fleet.value(), order.startedAtTick()));
        }
        return List.copyOf(result);
    }
}
