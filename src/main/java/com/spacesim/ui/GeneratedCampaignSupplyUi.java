package com.spacesim.ui;

import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.Stage22ShipConsumableCatalogLoader;
import com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.SelectionKind;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.UiSelection;
import com.spacesim.ui.GeneratedWorldUiSnapshot.InfoSection;
import com.spacesim.world.FleetLocationKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Read-only physical interface supply from existing personally purchased cargo. */
public final class GeneratedCampaignSupplyUi {
    private GeneratedCampaignSupplyUi() { }

    /**
     * Projects authored consumable interfaces of the active personally owned idle freight.
     * @param campaign ordinary campaign authority
     * @return immutable servicing rows; no commodity or charge is issued
     */
    public static List<ProductionUiSnapshot.Row> rows(Stage228CampaignAuthority campaign) {
        var p = campaign.playerState().orElse(null);
        if (p == null || p.activeFleetId() == null) return List.of();
        var runtime = campaign.coordinator().runtime();
        var freight = runtime.freight().findFreighter(p.activeFleetId()).orElse(null);
        var fleet = runtime.world().findFleet(p.activeFleetId()).orElse(null);
        if (freight == null || fleet == null || fleet.locationKind() != FleetLocationKind.IN_SYSTEM
                || freight.phase() != com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase.IDLE) return List.of();
        var fitted = runtime.world().findSession(fleet.systemId()).orElseThrow().getEntityRegistry().require(fleet.localEntityId())
                .getComponent(EngineeringComponent.class);
        if (fitted == null) return List.of();
        var result = new ArrayList<ProductionUiSnapshot.Row>();
        for (var installed : fitted.fit.installedModules()) {
            for (var binding : Catalog.BINDINGS.getBindings()) {
                if (!installed.moduleId().equals(binding.moduleId())) continue;
                var module = Catalog.ENGINEERING.findModule(installed.moduleId());
                if (module == null) continue;
                var port = module.interfaces().stream().filter(i -> i.id().equals(binding.interfaceId())
                        && i.kind() == binding.interfaceKind()).findFirst().orElse(null);
                if (port == null) continue;
                var load = fitted.runtimeState.consumables().interfaceLoads().stream()
                        .filter(l -> l.mountId().equals(installed.mountId()) && l.interfaceId().equals(binding.interfaceId()))
                        .findFirst().orElse(null);
                double amount = load == null ? 0d : load.amount();
                String name = binding.interfaceKind() == com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind.REACTION_MASS
                        ? "Реактивная масса из трюма" : "Расходники из трюма";
                result.add(new ProductionUiSnapshot.Row(new UiSelection(SelectionKind.SURFACE_ROW,
                        "pilot-supply|" + binding.id() + "|" + installed.mountId()), name, "Снабжение личного корабля",
                        p.docked() ? "Можно проверить загрузку" : "Требуется физическая стыковка",
                        List.of(InfoSection.of("Физическое снабжение", "Источник", "Ранее купленный груз в собственном трюме",
                                "Товар", binding.commodityId().equals("commodity.material.purified_water") ? "Очищенная вода" : binding.commodityId(),
                                "В трюме", String.format(Locale.ROOT, "%.2f кг", freight.cargoStorage().commodityMassByIdKg().getOrDefault(binding.commodityId(), 0d)),
                                "Преобразование", String.format(Locale.ROOT, "1 кг → %.2f единиц интерфейса", binding.amountPerKg()),
                                "Загружено", String.format(Locale.ROOT, "%.2f единиц интерфейса", amount),
                                "Свободная ёмкость", String.format(Locale.ROOT, "%.2f кг товара", Math.max(0d, port.capacity() - amount) / binding.amountPerKg()),
                                "Оплата", "Покупка груза оплачена ранее; повторной покупки нет",
                                "Условия", "Текущая стыковка: не далее 1 км, скорость ≤ 1 м/с; установленный совместимый модуль и свободный бак",
                                "Результат", "Груз и его партии уменьшаются, расходник в установленном модуле увеличивается; деньги и тепло сохраняются")),
                        "Обычный Stage18ShipConsumableService; авторское соответствие товара интерфейсу и реальные партии личного груза",
                        null, 0, runtime.world().getAuthoritativeWorldTick()));
            }
        }
        return List.copyOf(result);
    }

    private static final class Catalog {
        private static final com.spacesim.content.Stage18ShipConsumableCatalog BINDINGS = Stage22ShipConsumableCatalogLoader.loadDefault();
        private static final com.spacesim.content.ship.ShipEngineeringCatalog ENGINEERING = Stage22FreightStrategicEngineeringCatalogLoader.loadDefault();
    }
}
