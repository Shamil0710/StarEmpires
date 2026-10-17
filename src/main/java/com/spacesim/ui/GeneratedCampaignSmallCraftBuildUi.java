package com.spacesim.ui;

import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.content.Stage23SmallCraftProductionCatalogs;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.SelectionKind;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.UiSelection;
import com.spacesim.ui.GeneratedWorldUiSnapshot.InfoSection;
import java.util.*;

/** Owner-local full craft bills and actual retained build work, without preview mutations. */
public final class GeneratedCampaignSmallCraftBuildUi {
    private GeneratedCampaignSmallCraftBuildUi() { }
    /**
     * Projects bills and existing jobs only at personally owned stations.
     * @param campaign live authority
     * @return immutable personal production rows
     */
    public static List<ProductionUiSnapshot.Row> rows(Stage228CampaignAuthority campaign) {
        if (campaign.playerState().isEmpty()) return List.of();
        var rows = new ArrayList<ProductionUiSnapshot.Row>();
        long tick = campaign.coordinator().runtime().world().getAuthoritativeWorldTick();
        for (var station : campaign.coordinator().runtime().industry().industrial().stations()) {
            if (!campaign.ownsProductionStation(station.stationId())) continue;
            for (var design : Stage23SmallCraftProductionCatalogs.designIds()) {
                var fields = new ArrayList<String>();
                var fit = Stage23SmallCraftProductionCatalogs.fit(design);
                var hull = Stage23SmallCraftProductionCatalogs.shipyards().findHullProfile(fit.hullId());
                for (var input : hull.buildInputsKg()) {
                    fields.add(Names.ONTOLOGY.findCommodity(input.commodityId()).displayName()); fields.add(String.format(Locale.ROOT, "Корпус: %.1f кг; доступно %.1f кг",
                            input.massKg(), station.storage().commodityMassKg(input.commodityId())));
                }
                var modules = new TreeMap<String, Integer>(); fit.installedModules().forEach(m -> modules.merge(m.moduleId(), 1, Math::addExact));
                modules.forEach((id, n) -> { fields.add(Names.ENGINEERING.findModule(id).displayName()); fields.add("Готовых модулей: " + n + "; доступно " + station.storage().productCount(id)); });
                boolean available = station.yardCapabilities().stream().filter(y -> y.active()
                        && campaign.smallCraftBuilds().orders().stream().noneMatch(o -> o.stationId().equals(station.stationId()) && o.yardInstanceId().equals(y.yardInstanceId())))
                        .anyMatch(y -> com.spacesim.economy.ShipyardSmallCraftBuildWorkQueue.plan(design, y).feasibility().feasible());
                fields.add("Совместимая верфь"); fields.add(available ? "Есть свободная действующая линия" : "Нет свободной действующей линии");
                fields.add("Работа"); fields.add("Общий бюджет установленной верфи с ремонтом и переоснащением; на паузе работы нет");
                fields.add("После завершения"); fields.add("Передача по отдельному подтверждению; пустые баки и магазины; физическая доставка ещё требуется");
                rows.add(row("pilot-craft-build|" + station.stationId() + '|' + design, name(design), "Строительство малого аппарата",
                        "Полный корпус и готовые модули резервируются сразу", fields, station.systemId(), tick));
            }
        }
        for (var o : campaign.smallCraftBuilds().orders()) if (campaign.ownsProductionStation(o.stationId())) {
            var fields = List.of("Выполненная работа", String.format(Locale.ROOT, "%.3f / %.3f рабочих секунд", o.completedWorkSeconds(), o.requiredWorkSeconds()),
                    "Материалы", "Находятся в резерве и занимают прежний складской объём", "Передача",
                    "Готовый аппарат не получает расходники, ангар или боеготовность автоматически");
            rows.add(row((o.ready() ? "pilot-craft-build-claim|" : "pilot-craft-build-progress|") + o.stationId() + '|' + o.orderId(),
                    name(o.designId()), "Заказ малого аппарата", o.ready() ? "Готов к передаче" : "Работа идёт на общих тактах", fields, null, tick));
            if (o.completedWorkSeconds() == 0) rows.add(row("pilot-craft-build-cancel|" + o.stationId() + '|' + o.orderId(),
                    "Отменить заказ", "Строительство малого аппарата", "До начала работы возвращается полный резерв", fields, null, tick));
        }
        return List.copyOf(rows);
    }
    private static String name(String id) {
        String role = id.contains(".interceptor_") ? "Перехватчик" : id.contains(".defence_") ? "Аппарат обороны" : "Ударный аппарат";
        return role + (id.startsWith("fit.empire.") ? " Империи" : " Союза");
    }
    private static final class Names {
        private static final com.spacesim.content.Stage18ResourceOntologyCatalog ONTOLOGY = com.spacesim.content.Stage18ResourceOntologyLoader.loadDefault();
        private static final com.spacesim.content.ship.ShipEngineeringCatalog ENGINEERING = com.spacesim.content.ship.Stage228SmallCraftEngineeringCatalogLoader.loadDefault();
    }
    private static ProductionUiSnapshot.Row row(String id, String title, String group, String status,
            List<String> fields, com.spacesim.world.StarSystemId system, long tick) {
        return new ProductionUiSnapshot.Row(new UiSelection(SelectionKind.SURFACE_ROW, id), title, group, status,
                List.of(InfoSection.of("Оплаченное строительство", fields.toArray(String[]::new))),
                "Личная станция, реальные готовые модули, материалы и выполненная работа", system, 0, tick);
    }
}
