package com.spacesim.ui;

import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.Stage18ExtractionCatalog.ExtractionEnvironment;
import com.spacesim.ship.ProductionEngineeringRuntimeResolver;
import com.spacesim.ship.ShipMiningEngineeringAdapter;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.SelectionKind;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.UiSelection;
import com.spacesim.ui.GeneratedWorldUiSnapshot.InfoSection;
import com.spacesim.world.FleetLocationKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Personal physical contact projection; never enumerates remote deposits or their reserves. */
public final class GeneratedCampaignMiningUi {
    private GeneratedCampaignMiningUi() { }
    private static final ProductionEngineeringRuntimeResolver ENGINEERING = new ProductionEngineeringRuntimeResolver();

    /**
     * Projects actual local excavation contacts and the active ship's stop command.
     * @param campaign same live campaign
     * @return immutable personal mining rows
     */
    public static List<ProductionUiSnapshot.Row> rows(Stage228CampaignAuthority campaign) {
        var p = campaign.playerState().orElse(null);
        if (p == null || p.activeFleetId() == null || !p.ownedFleetIds().contains(p.activeFleetId())) return List.of();
        var runtime = campaign.coordinator().runtime(); var world = runtime.world();
        var fleet = world.findFleet(p.activeFleetId()).orElse(null);
        if (fleet == null || fleet.locationKind() != FleetLocationKind.IN_SYSTEM) return List.of();
        var result = new ArrayList<ProductionUiSnapshot.Row>();
        var order = runtime.freight().personalMiningOrders().stream().filter(o -> o.fleetId().equals(fleet.fleetId())).findFirst().orElse(null);
        if (order != null) result.add(new ProductionUiSnapshot.Row(new UiSelection(SelectionKind.SURFACE_ROW,
                "pilot-mining-stop|" + order.sourceId()), "Остановить добычу", "Личная добыча", "Задание выполняется с течением времени",
                List.of(InfoSection.of("Текущая работа", "Последний обработанный такт", Long.toString(order.lastProcessedTick()),
                        "Остановка", "Сохранит уже добытый груз и прекратит дальнейшую работу")),
                "Сохранённое задание собственного корабля", fleet.systemId(), 0, world.getAuthoritativeWorldTick()));
        if (p.docked() || world.findFleetJump(fleet.fleetId()).isPresent()) return List.copyOf(result);
        var fitted = world.findSession(fleet.systemId()).orElseThrow().getEntityRegistry().require(fleet.localEntityId())
                .getComponent(EngineeringComponent.class);
        if (fitted == null) return List.copyOf(result);
        var capability = new ShipMiningEngineeringAdapter().derive(ENGINEERING.derive(fitted)).orElse(null);
        if (capability == null) return List.copyOf(result);
        var physical = runtime.arrival().materialization(fleet.systemId()).physicalState(fleet.localEntityId()).orElse(null);
        if (physical == null) return List.copyOf(result);
        int ordinal = 0;
        for (var source : runtime.industry().sourceOutposts().sources().sources()) {
            if (!source.systemId().equals(fleet.systemId()) || source.sourceState().environment() != ExtractionEnvironment.FREE_BODY) continue;
            double distance = physical.position().distanceTo(source.position());
            if (distance > capability.workingRangeM()) continue;
            result.add(new ProductionUiSnapshot.Row(new UiSelection(SelectionKind.SURFACE_ROW, "pilot-mining|" + source.sourceId()),
                    "Участок добычи " + ++ordinal, "Личная добыча", order == null ? "В пределах работы оборудования" : "Уже назначено задание",
                    List.of(InfoSection.of("Физическая работа", "Расстояние", String.format(Locale.ROOT, "%.1f м", distance),
                            "Рабочая дальность", String.format(Locale.ROOT, "%.0f м", capability.workingRangeM()),
                            "Допустимая скорость", String.format(Locale.ROOT, "%.1f м/с", capability.maximumDriftMps()),
                            "Производительность оборудования", String.format(Locale.ROOT, "%.2f кг сырья/с", capability.maximumSourceKgPerSecond()),
                            "Время", "Добыча идёт только при продолжении времени; запуск на паузе груза не создаёт",
                            "Запас и состав", "Точный запас не исследован; добытые материалы поступят в реальный трюм",
                            "Остановка", "Потеря контакта, нехватка возможностей или полный трюм прекращают работу")),
                    "Текущий физический контакт собственного добывающего оборудования; удалённые запасы не раскрываются",
                    fleet.systemId(), 0, world.getAuthoritativeWorldTick()));
        }
        return List.copyOf(result);
    }
}
