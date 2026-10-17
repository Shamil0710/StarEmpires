package com.spacesim.ui;

import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.content.Stage22CivilianMiningProductionPath;
import com.spacesim.content.Stage23AmmunitionProduction;
import com.spacesim.economy.Stage18ManufacturingWorkQueue;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.SelectionKind;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.UiSelection;
import com.spacesim.ui.GeneratedWorldUiSnapshot.InfoSection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Own-station ammunition batch bills and actual persisted orders; grants no production line. */
public final class GeneratedCampaignAmmunitionManufacturingUi {
    private GeneratedCampaignAmmunitionManufacturingUi() { }
    private static final com.spacesim.content.Stage18ResourceOntologyCatalog ONTOLOGY =
            com.spacesim.content.Stage18ResourceOntologyLoader.loadDefault();
    /**
     * Tests a personal production row for round-count controls.
     * @param id selected stable row identity
     * @return whether this is a new admitted ammunition batch
     */
    public static boolean isSelection(String id) {
        String[] parts = id.split("\\|", -1);
        return parts.length == 3 && parts[0].equals("pilot-manufacturing") && Stage23AmmunitionProduction.supports(parts[2]);
    }
    /**
     * Projects the selected whole-round bill or the existing order's original whole-round count.
     * @param campaign current personal authority
     * @param selectedUnits positive requested round count
     * @return immutable own-station rows with no foreign stock disclosure
     */
    public static List<ProductionUiSnapshot.Row> rows(Stage228CampaignAuthority campaign, int selectedUnits) {
        if (selectedUnits <= 0) throw new IllegalArgumentException("Positive ammunition batch required");
        if (campaign.playerState().isEmpty() || campaign.playerState().orElseThrow().ownedStations().isEmpty()) return List.of();
        var result = new ArrayList<ProductionUiSnapshot.Row>(); var runtime = campaign.coordinator().runtime();
        var products = com.spacesim.content.Stage23SmallCraftProductionCatalogs.products();
        var catalog = com.spacesim.content.Stage23SmallCraftProductionCatalogs.manufacturing();
        var ontology = ONTOLOGY;
        for (var station : runtime.industry().industrial().stations()) {
            if (!campaign.ownsProductionStation(station.stationId())) continue;
            for (String product : Stage23AmmunitionProduction.products()) {
                var order = runtime.manufacturingQueue().capture().stream().filter(Stage18ManufacturingWorkQueue::managed)
                        .filter(o -> o.stationId().equals(station.stationId()) && o.operationId().equals(product)).findFirst().orElse(null);
                int units = order == null ? selectedUnits : order.requestedUnits();
                double mass = products.findProduct(product).unitMassKg() * units;
                var profile = catalog.findProductProfile(catalog.findProductBinding(product).profileId());
                var fields = new ArrayList<String>();
                fields.add("Партия"); fields.add(units + " шт.");
                fields.add("Общая масса"); fields.add(String.format(Locale.ROOT, "%.3f кг", mass));
                fields.add("На собственном складе"); fields.add(station.storage().productCount(product) + " шт.");
                fields.add("Производственная линия"); fields.add(campaign.hasManufacturingLine(station.stationId(), product)
                        ? "Есть совместимая действующая линия" : "Нет совместимой действующей линии; требуется оружейное производство");
                if (order == null) {
                    for (var input : profile.inputs()) {
                        double needed = input.fractionOfOutputMass() * mass;
                        double available = station.storage().commodityMassKg(input.commodityId());
                        fields.add(ontology.findCommodity(input.commodityId()).displayName());
                        fields.add(String.format(Locale.ROOT, "Нужно %.3f кг; доступно %.3f кг; не хватает %.3f кг", needed, available, Math.max(0, needed - available)));
                    }
                    fields.add("Резерв"); fields.add("Подтверждение резервирует материалы всей партии; план ничего не расходует");
                } else {
                    fields.add("Выполнено"); fields.add(String.format(Locale.ROOT, "%.2f %%", order.completedFraction() * 100));
                    for (var input : order.reservedCommodityMassByIdKg().entrySet()) {
                        fields.add(ontology.findCommodity(input.getKey()).displayName());
                        fields.add(String.format(Locale.ROOT, "В резерве %.3f кг", input.getValue()));
                    }
                    fields.add("Отмена"); fields.add("Вернёт весь материальный резерв при наличии места; выполненная работа не возвращается");
                }
                fields.add("Выпуск"); fields.add("Вся партия поступит на склад только после завершения общей работы; затем её можно загрузить в совместимый магазин");
                fields.add("Время"); fields.add("Пауза не производит патроны; энергия, работа и обслуживание расходуются за реальные общие такты");
                String id = order == null ? "pilot-manufacturing|" + station.stationId() + '|' + product
                        : "pilot-manufacturing-cancel|" + station.stationId() + '|' + order.orderId();
                result.add(new ProductionUiSnapshot.Row(new UiSelection(SelectionKind.SURFACE_ROW, id), Stage23AmmunitionProduction.displayName(product),
                        "Производство боезапаса", order == null ? "Партия " + units + " шт." : "Заказ в работе",
                        List.of(InfoSection.of("Изготовление боеприпасов", fields.toArray(String[]::new))),
                        "Авторский рецепт, фактический собственный склад и установленная производственная линия; новые мощности не выдаются",
                        station.systemId(), 0, runtime.world().getAuthoritativeWorldTick()));
            }
        }
        return List.copyOf(result);
    }
}
