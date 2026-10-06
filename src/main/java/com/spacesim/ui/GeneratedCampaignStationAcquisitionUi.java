package com.spacesim.ui;

import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.SelectionKind;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.UiSelection;
import com.spacesim.ui.GeneratedWorldUiSnapshot.InfoSection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Disclosed offers for existing civilian stations, without exposing foreign internal stock. */
public final class GeneratedCampaignStationAcquisitionUi {
    private GeneratedCampaignStationAcquisitionUi() { }

    /**
     * Projects current known offers and their physical purchase conditions.
     * @param campaign actual personal campaign authority
     * @return immutable offer rows
     */
    public static List<ProductionUiSnapshot.Row> rows(Stage228CampaignAuthority campaign) {
        if (campaign.playerState().isEmpty()) return List.of();
        var result = new ArrayList<ProductionUiSnapshot.Row>();
        var runtime = campaign.coordinator().runtime();
        for (var station : runtime.industry().industrial().stations()) {
            long price = campaign.stationSalePriceMilliCredits(station.stationId()); if (price <= 0) continue;
            var endpoint = runtime.infrastructure().endpoint(station.stationId());
            String name = switch (station.stationArchetypeId()) {
                case "station.infrastructure.volatile_depot" -> "Склад летучих веществ";
                case "station.infrastructure.refinery_complex" -> "Перерабатывающий комплекс";
                case "station.infrastructure.industrial_station" -> "Промышленная станция";
                case "station.infrastructure.high_tech_hub" -> "Высокотехнологичная станция";
                case "station.infrastructure.trade_logistics_hub" -> "Торговая и логистическая станция";
                default -> "Многоцелевая пограничная станция";
            };
            result.add(new ProductionUiSnapshot.Row(new UiSelection(SelectionKind.SURFACE_ROW, "pilot-station-purchase|" + station.stationId()),
                    name, "Приобретение станции", String.format(Locale.ROOT, "%.3f кр.", price / 1000d),
                    List.of(InfoSection.of("Предложение продавца", "Цена", String.format(Locale.ROOT, "%.3f кр.", price / 1000d),
                    "Условия", "Личный корабль физически состыкован здесь; деньги переводятся продавцу",
                    "Приобретение", "Существующий объект со складом и установленной инфраструктурой; ресурсы не пополняются",
                    "Ресурсы", "Установки сохраняют текущие повреждения, мощность и запасы; покупка не вводит их в работу",
                    "Правовой статус", "Личное владение отдельно от фракционной регистрации; территориальные права сохраняются",
                    "Доступность", "Гражданские объекты по открытому предложению; военные и добывающие объекты не продаются")),
                    "Открытое предложение владельца существующей гражданской станции",
                    endpoint.systemId(), 0, runtime.world().getAuthoritativeWorldTick()));
        }
        return List.copyOf(result);
    }
}
