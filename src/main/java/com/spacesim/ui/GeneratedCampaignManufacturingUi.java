package com.spacesim.ui;

import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.content.Stage22CivilianMiningProductionPath;
import com.spacesim.content.ship.Stage22CivilianMiningEngineeringCatalogLoader;
import com.spacesim.economy.Stage18ManufacturingWorkQueue;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.SelectionKind;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.UiSelection;
import com.spacesim.ui.GeneratedWorldUiSnapshot.InfoSection;
import java.util.*;

/** Personal manufacturing projection over actual reserved-material orders and owned stations. */
public final class GeneratedCampaignManufacturingUi {
    private GeneratedCampaignManufacturingUi() { }
    /**
     * Projects only actual personally owned stations and their jobs.
     * @param campaign same live campaign
     * @return owner-bound manufacturing rows
     */
    public static List<ProductionUiSnapshot.Row> rows(Stage228CampaignAuthority campaign) {
        if (campaign.playerState().isEmpty() || campaign.playerState().orElseThrow().ownedStations().isEmpty()) return List.of();
        var result = new ArrayList<ProductionUiSnapshot.Row>();
        var runtime = campaign.coordinator().runtime();
        var catalog = Stage22CivilianMiningProductionPath.loadManufacturing();
        var ontology = com.spacesim.content.Stage18ResourceOntologyLoader.loadDefault();
        for (String product : List.of(Stage22CivilianMiningEngineeringCatalogLoader.MINING_MODULE_ID,
                Stage22CivilianMiningEngineeringCatalogLoader.FREIGHT_MINING_MODULE_ID)) {
        var profile = catalog.findProductProfile(catalog.findProductBinding(product).profileId());
        double mass = Stage22CivilianMiningProductionPath.loadProducts().findProduct(product).unitMassKg();
        for (var station : runtime.industry().industrial().stations()) {
            if (!campaign.ownsProductionStation(station.stationId())) continue;
            var order = runtime.manufacturingQueue().capture().stream().filter(Stage18ManufacturingWorkQueue::managed)
                    .filter(o -> o.stationId().equals(station.stationId()) && o.operationId().equals(product)).findFirst().orElse(null);
            boolean compatible = campaign.hasManufacturingLine(station.stationId(), product);
            var fields = new ArrayList<String>();
            fields.add("Выпуск"); fields.add("Одна добывающая секция; " + String.format(Locale.ROOT, "%.0f кг", mass));
            fields.add("Производственная линия"); fields.add(compatible ? "Есть совместимая действующая линия" : "Нет совместимой действующей линии");
            if (order != null) {
                fields.add("Выполнено"); fields.add(String.format(Locale.ROOT, "%.2f %%", order.completedFraction() * 100));
                fields.add("Резерв материалов"); fields.add("Изъят со склада в физический заказ; повторно расходовать нельзя");
                fields.add("Отмена"); fields.add("Вернёт материалы при наличии места; выполненная работа не возвращается");
            } else for (var input : profile.inputs()) {
                fields.add(ontology.findCommodity(input.commodityId()).displayName()); fields.add(String.format(Locale.ROOT, "Нужно %.1f кг; на складе %.1f кг",
                        input.fractionOfOutputMass() * mass, station.storage().commodityMassKg(input.commodityId())));
            }
            fields.add("Время"); fields.add("Работа идёт за завершённые игровые такты; на паузе изделие не появляется");
            var section = InfoSection.of("Изготовление оборудования", fields.toArray(String[]::new));
            String key = order == null ? "pilot-manufacturing|" + station.stationId() + '|' + product
                    : "pilot-manufacturing-cancel|" + station.stationId() + '|' + order.orderId();
            result.add(new ProductionUiSnapshot.Row(new UiSelection(SelectionKind.SURFACE_ROW, key),
                    product.equals(Stage22CivilianMiningEngineeringCatalogLoader.FREIGHT_MINING_MODULE_ID)
                            ? "Добывающая секция для грузового корабля" : "Добывающая секция", "Личное производство", order == null ? "Заказать на своей станции" : "Заказ в работе",
                    List.of(section), "Реальный склад, установленные мощности и сохраняемый резерв собственной станции",
                    station.systemId(), 0, runtime.world().getAuthoritativeWorldTick()));
        }
        }
        return List.copyOf(result);
    }
}
