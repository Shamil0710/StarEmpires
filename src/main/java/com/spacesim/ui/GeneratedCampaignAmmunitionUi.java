package com.spacesim.ui;

import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.ship.ShipAmmunitionSupplyService;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.SelectionKind;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.UiSelection;
import com.spacesim.ui.GeneratedWorldUiSnapshot.InfoSection;
import java.util.List;
import java.util.Locale;

/** Replenishment targets at the active ship's actual personally owned berth. */
public final class GeneratedCampaignAmmunitionUi {
    private GeneratedCampaignAmmunitionUi() { }

    /**
     * Reads installed bound magazines and finite own station stock, without granting ammunition.
     * @param campaign actual personal campaign
     * @return immutable service rows, empty outside an own physical berth
     */
    public static List<ProductionUiSnapshot.Row> rows(Stage228CampaignAuthority campaign) {
        var stationId = campaign.dockedAmmunitionStationId().orElse(null);
        if (stationId == null) return List.of();
        var runtime = campaign.coordinator().runtime(); var world = runtime.world();
        var fleet = world.findFleet(campaign.playerState().orElseThrow().activeFleetId()).orElseThrow();
        var ship = world.findSession(fleet.systemId()).orElseThrow().getEntityRegistry().require(fleet.localEntityId())
                .getComponent(EngineeringComponent.class);
        if (ship == null) return List.of();
        var endpoint = runtime.infrastructure().endpoint(stationId);
        return new ShipAmmunitionSupplyService().feeds(ship).stream().map(feed -> new ProductionUiSnapshot.Row(
                new UiSelection(SelectionKind.SURFACE_ROW, "pilot-ammunition|" + stationId + '|' + feed.mountId()),
                "Боезапас: " + feed.weaponName(), "Снабжение личного корабля", "Пополнение существующего магазина",
                List.of(InfoSection.of("Загрузка боеприпасов", "Оружие", feed.weaponName(),
                        "В магазине", feed.currentRounds() + " шт.",
                        "На собственном складе", endpoint.storage().productCount(feed.productId()) + " шт.",
                        "Свободное место магазина", String.format(Locale.ROOT, "%.0f шт.", Math.floor(feed.freeAmount() / feed.launcher().ammunitionAmountPerShot())),
                        "Масса боеприпаса", String.format(Locale.ROOT, "%.3f кг/шт.", feed.unitMassKg()),
                        "Обработка за такт", String.format(Locale.ROOT, "%.3f кг", endpoint.handlingCapability().openInterval(campaign.coordinator().session().fixedStepSeconds()).remainingMassKg()),
                        "Выбор количества", "Целые штуки; кнопки -/+ меняют число боеприпасов",
                        "Условия", "Собственные корабль и станция, действующая стыковка, свободные магазин и масса корпуса; завершите обслуживание",
                        "Результат", "Склад уменьшается; магазин получает те же боеприпасы без оплаты. Одна обработка за завершённый такт")),
                "Существующий боеприпас, привязанный к установленному оружию; конечный склад собственной станции",
                null, 0, world.getAuthoritativeWorldTick())).toList();
    }
}
