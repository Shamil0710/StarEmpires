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
    private static final class SmallCraftNames {
        private static final com.spacesim.content.ship.ShipEngineeringCatalog ENGINEERING = com.spacesim.content.ship.Stage228SmallCraftEngineeringCatalogLoader.loadDefault();
    }
    /**
     * Projects only actual personally owned stations and their jobs.
     * @param campaign same live campaign
     * @return owner-bound manufacturing rows
     */
    public static List<ProductionUiSnapshot.Row> rows(Stage228CampaignAuthority campaign) {
        if (campaign.playerState().isEmpty() || campaign.playerState().orElseThrow().ownedStations().isEmpty()) return List.of();
        var result = new ArrayList<ProductionUiSnapshot.Row>();
        var runtime = campaign.coordinator().runtime();
        var catalog = com.spacesim.content.Stage23SmallCraftProductionCatalogs.manufacturing();
        var ontology = com.spacesim.content.Stage18ResourceOntologyLoader.loadDefault();
        var offered = new ArrayList<>(com.spacesim.content.Stage23SmallCraftProductionCatalogs.moduleIds());
        offered.addAll(List.of(Stage22CivilianMiningEngineeringCatalogLoader.MINING_MODULE_ID,
                Stage22CivilianMiningEngineeringCatalogLoader.FREIGHT_MINING_MODULE_ID,
                "module.industrial_union_cargo_section_v1"));
        for (String product : offered) {
        boolean cargo = product.equals("module.industrial_union_cargo_section_v1");
        var profile = catalog.findProductProfile(catalog.findProductBinding(product).profileId());
        double mass = com.spacesim.content.Stage23SmallCraftProductionCatalogs.products().findProduct(product).unitMassKg();
        for (var station : runtime.industry().industrial().stations()) {
            if (!campaign.ownsProductionStation(station.stationId())) continue;
            var order = runtime.manufacturingQueue().capture().stream().filter(Stage18ManufacturingWorkQueue::managed)
                    .filter(o -> o.stationId().equals(station.stationId()) && o.operationId().equals(product)).findFirst().orElse(null);
            boolean compatible = campaign.hasManufacturingLine(station.stationId(), product);
            var fields = new ArrayList<String>();
            fields.add("Выпуск"); fields.add((cargo ? "Одна грузовая секция; " : com.spacesim.content.Stage23SmallCraftProductionCatalogs.moduleIds().contains(product) ? "Один модуль малого аппарата; " : "Одна добывающая секция; ") + String.format(Locale.ROOT, "%.0f кг", mass));
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
            var sections = new ArrayList<InfoSection>();
            sections.add(section);
            if (order == null) {
                var plan = com.spacesim.economy.Stage18ProductionSupplyPlan.forProduct(product,
                        station.storage().snapshotCommodityMassByIdKg(), com.spacesim.content.Stage23SmallCraftProductionCatalogs.products(),
                        catalog, com.spacesim.content.Stage18RefiningCatalogLoader.loadDefault());
                var processing = new ArrayList<String>();
                for (var batch : plan.batches()) {
                    boolean refining = batch.process() == com.spacesim.economy.Stage18ProductionSupplyPlan.Process.REFINING;
                    boolean line = refining ? campaign.hasRefiningLine(station.stationId(), batch.recipeId())
                            : campaign.hasComponentLine(station.stationId(), batch.recipeId());
                    processing.add(ontology.findCommodity(batch.outputCommodityId()).displayName());
                    processing.add(String.format(Locale.ROOT, "%s: партия %d кг; выход %.3f кг; %s",
                            refining ? "Переработка сырья" : "Изготовление компонентов", batch.kilograms(), batch.outputMassKg(),
                            line ? "линия действует" : "нет совместимой действующей линии"));
                }
                processing.add("Порядок");
                processing.add("Доставьте сырьё на свой склад; выполните переработку, затем компоненты и заказ оборудования. Партии округлены вверх до целых кг; остаток остаётся на складе.");
                processing.add("Граница плана");
                processing.add("Только доступный запас этой станции. Груз, чужие склады и незавершённый выпуск не учтены. План не резервирует материалы и не создаёт заказы.");
                sections.add(InfoSection.of("Подготовка материалов для одного изделия", processing.toArray(String[]::new)));
                var supply = new ArrayList<String>();
                for (var input : plan.feedstockDemand().entrySet()) {
                    supply.add(ontology.findCommodity(input.getKey()).displayName());
                    supply.add(String.format(Locale.ROOT, "Для всей цепочки %.3f кг; на складе %.3f кг; доставить %.3f кг",
                            input.getValue(), station.storage().commodityMassKg(input.getKey()),
                            plan.missingFeedstock().getOrDefault(input.getKey(), 0d)));
                }
                if (supply.isEmpty()) { supply.add("Сырьё"); supply.add("Переработка не требуется: материалы уже доступны"); }
                sections.add(InfoSection.of("Общее сырьё без повторного счёта", supply.toArray(String[]::new)));
            }
            String key = order == null ? "pilot-manufacturing|" + station.stationId() + '|' + product
                    : "pilot-manufacturing-cancel|" + station.stationId() + '|' + order.orderId();
            result.add(new ProductionUiSnapshot.Row(new UiSelection(SelectionKind.SURFACE_ROW, key),
                    com.spacesim.content.Stage23SmallCraftProductionCatalogs.moduleIds().contains(product)
                            ? SmallCraftNames.ENGINEERING.findModule(product).displayName()
                            : cargo ? "Грузовая секция для грузового корабля" : product.equals(Stage22CivilianMiningEngineeringCatalogLoader.FREIGHT_MINING_MODULE_ID)
                            ? "Добывающая секция для грузового корабля" : "Добывающая секция", "Личное производство", order == null ? "Заказать на своей станции" : "Заказ в работе",
                    sections, "Реальный склад, установленные мощности и сохраняемый резерв собственной станции",
                    station.systemId(), 0, runtime.world().getAuthoritativeWorldTick()));
        }
        }
        return List.copyOf(result);
    }
}
