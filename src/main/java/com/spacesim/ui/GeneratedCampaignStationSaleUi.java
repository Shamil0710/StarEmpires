package com.spacesim.ui;

import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.SelectionKind;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.UiSelection;
import com.spacesim.ui.GeneratedWorldUiSnapshot.InfoSection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The pilot's sale terms for water at own stations: the price passing NPC freight pays and the
 * reserve it never gets. Reads never change terms; the rows only select what the stepper sets.
 */
public final class GeneratedCampaignStationSaleUi {
    /** Commodity whose sale terms the pilot sets. */
    public static final String WATER = "commodity.material.purified_water";
    /** Row of the pilot's price. */
    public static final String PRICE_PREFIX = "pilot-station-price|";
    /** Row of the pilot's reserve. */
    public static final String RESERVE_PREFIX = "pilot-station-reserve|";

    private GeneratedCampaignStationSaleUi() { }

    /**
     * @param stableId selected row identity
     * @return the command the row's stepper confirms, or empty
     */
    public static String action(String stableId) {
        return stableId.startsWith(PRICE_PREFIX) ? "SET_STATION_PRICE" : stableId.startsWith(RESERVE_PREFIX) ? "SET_STATION_RESERVE" : "";
    }

    /**
     * @param campaign current campaign
     * @param stableId selected price or reserve row
     * @return the current value the stepper starts from (milli-credits per kilogram, or kilograms)
     */
    public static int currentValue(Stage228CampaignAuthority campaign, String stableId) {
        var pieces = stableId.split("\\|");
        var terms = campaign.stationSalePolicy(pieces[1], pieces[2]);
        long value = stableId.startsWith(PRICE_PREFIX) ? terms.askMilliCreditsPerKg() : terms.reserveKg();
        return (int) Math.min(Integer.MAX_VALUE, value);
    }

    /**
     * Projects the terms of every personally owned station that stores water.
     * @param campaign current campaign
     * @return price and reserve rows
     */
    public static List<ProductionUiSnapshot.Row> rows(Stage228CampaignAuthority campaign) {
        if (campaign.playerState().isEmpty()) return List.of();
        var result = new ArrayList<ProductionUiSnapshot.Row>();
        var runtime = campaign.coordinator().runtime();
        var water = Stage18ResourceOntologyLoader.loadDefault().findCommodity(WATER);
        for (var endpoint : runtime.infrastructure().endpoints()) {
            if (!campaign.ownsProductionStation(endpoint.stationId())
                    || !endpoint.handlingCapability().supportedStorageClassIds().contains(water.storageClassId())) continue;
            var terms = campaign.stationSalePolicy(endpoint.stationId(), WATER);
            double stock = endpoint.storage().commodityMassKg(WATER);
            long market = campaign.pilotCommodityPrice(endpoint.stationId(), WATER, true);
            long ceiling = Stage228CampaignAuthority.npcAcceptableAsk(WATER);
            String price = terms.askMilliCreditsPerKg() == 0 ? "рыночная, сейчас " + credits(market)
                    : credits(terms.askMilliCreditsPerKg()) + (terms.askMilliCreditsPerKg() > ceiling ? " — выше предела, NPC не покупают" : "");
            String[] common = {
                    "Станция", endpoint.stationId(),
                    "Цена для NPC", price,
                    "Предел NPC", credits(ceiling) + " — дороже ни один рынок не продаёт, NPC откажутся",
                    "Неприкосновенный запас", String.format(Locale.ROOT, "%,d кг", terms.reserveKg()),
                    "На складе", String.format(Locale.ROOT, "%,.0f кг", stock),
                    "Доступно NPC", String.format(Locale.ROOT, "%,.0f кг", Math.max(0, stock - terms.reserveKg())),
                    "Ваши корабли", "Заправляются со своего склада бесплатно, запас на них не распространяется"};
            result.add(row(endpoint.stationId(), PRICE_PREFIX, "Цена воды для NPC", common,
                    "Цена в тысячных долях кредита за кг (50 = 0,050 кр./кг); 0 — рыночная цена станции. Кнопки -/+: шаг 1; с Shift — 10; с Ctrl — 100.",
                    endpoint.systemId(), runtime.world().getAuthoritativeWorldTick()));
            result.add(row(endpoint.stationId(), RESERVE_PREFIX, "Запас воды, не продаваемый NPC", common,
                    "Масса в кг, которую NPC не получат. Кнопки -/+: шаг 1 000 кг; с Shift — 100 000; с Ctrl — 1 000 000.",
                    endpoint.systemId(), runtime.world().getAuthoritativeWorldTick()));
        }
        return List.copyOf(result);
    }

    private static ProductionUiSnapshot.Row row(String station, String prefix, String name, String[] common, String stepper,
            com.spacesim.world.StarSystemId system, long tick) {
        var fields = new ArrayList<>(List.of(common));
        fields.add("Настройка");
        fields.add(stepper);
        return new ProductionUiSnapshot.Row(new UiSelection(SelectionKind.SURFACE_ROW, prefix + station + '|' + WATER),
                name, "Продажа со своей станции", "Задать условия",
                List.of(InfoSection.of("Условия продажи воды", fields.toArray(String[]::new))),
                "Условия игрока, реальный склад станции и предел цены NPC", system, 0, tick);
    }

    private static String credits(long milliCreditsPerKg) {
        return String.format(Locale.ROOT, "%.3f кр./кг", milliCreditsPerKg / 1000d);
    }
}
