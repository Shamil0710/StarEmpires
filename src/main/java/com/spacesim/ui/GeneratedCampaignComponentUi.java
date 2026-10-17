package com.spacesim.ui;

import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.content.Stage22CivilianMiningProductionPath;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.economy.Stage18ManufacturingWorkQueue;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.SelectionKind;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.UiSelection;
import com.spacesim.ui.GeneratedWorldUiSnapshot.InfoSection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Owner-bound component recipes and actual reserved material/work; reads never create stock. */
public final class GeneratedCampaignComponentUi {
    private GeneratedCampaignComponentUi() { }

    /**
     * Projects real owned stores, authored yields and installed line access.
     * @param campaign current campaign
     * @return component and cancellation rows
     */
    public static List<ProductionUiSnapshot.Row> rows(Stage228CampaignAuthority campaign) {
        return rows(campaign, 100);
    }

    /**
     * Projects the exact material deficit for the selected output mass.
     * @param campaign current campaign
     * @param kilograms selected output mass
     * @return component and cancellation rows
     */
    public static List<ProductionUiSnapshot.Row> rows(Stage228CampaignAuthority campaign, int kilograms) {
        if (kilograms <= 0) throw new IllegalArgumentException("Positive component mass required");
        if (campaign.playerState().isEmpty()) return List.of();
        var result = new ArrayList<ProductionUiSnapshot.Row>();
        var runtime = campaign.coordinator().runtime();
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        for (var station : runtime.industry().industrial().stations()) {
            if (!campaign.ownsProductionStation(station.stationId())) continue;
            for (var recipe : com.spacesim.content.Stage23SmallCraftProductionCatalogs.manufacturing().getComponentRecipes()) {
                var order = runtime.manufacturingQueue().capture().stream()
                        .filter(o -> o.orderId().startsWith(Stage18ManufacturingWorkQueue.COMPONENT_PREFIX)
                                && o.stationId().equals(station.stationId()) && o.operationId().equals(recipe.id()))
                        .findFirst().orElse(null);
                var fields = new ArrayList<String>();
                fields.add("Станция"); fields.add(station.stationId());
                fields.add("Масса партии"); fields.add("Кнопки -/+ кг: шаг 1; с Shift — 100; с Ctrl — 10 000. Масса указана на кнопке заказа.");
                double batchMass = order == null ? kilograms : order.requestedAmount();
                fields.add("Выбранный выпуск"); fields.add(String.format(Locale.ROOT, "%.3f кг", batchMass));
                fields.add("Линия"); fields.add(campaign.hasComponentLine(station.stationId(), recipe.id())
                        ? "Есть совместимая действующая линия" : "Нет совместимой действующей линии");
                fields.add("Выход на 1 кг партии"); fields.add(String.format(Locale.ROOT, "%.3f кг — %s",
                        1d, ontology.findCommodity(recipe.outputCommodityId()).displayName()));
                for (var input : recipe.inputs()) {
                    fields.add(ontology.findCommodity(input.commodityId()).displayName());
                    double required = batchMass * input.fractionOfOutputMass();
                    double available = station.storage().commodityMassKg(input.commodityId());
                    fields.add(order == null
                            ? String.format(Locale.ROOT, "Для партии: %.3f кг; доступно %.3f кг; не хватает %.3f кг",
                                    required, available, Math.max(0, required - available))
                            : String.format(Locale.ROOT, "В резерве партии: %.3f кг; доступно на складе %.3f кг",
                                    order.reservedCommodityMassByIdKg().getOrDefault(input.commodityId(), 0d), available));
                }
                if (order != null) {
                    fields.add("Партия выпуска"); fields.add(String.format(Locale.ROOT, "%.3f кг", order.requestedAmount()));
                    fields.add("Работа"); fields.add(String.format(Locale.ROOT, "%.2f %%", order.completedFraction() * 100));
                }
                fields.add("Порядок"); fields.add("Выберите массу компонентов и подтвердите резерв материалов. Выпуск требует общих игровых тактов; отмена возвращает материалы.");
                String id = order == null ? "pilot-components|" + station.stationId() + '|' + recipe.id()
                        : "pilot-manufacturing-cancel|" + station.stationId() + '|' + order.orderId();
                result.add(new ProductionUiSnapshot.Row(new UiSelection(SelectionKind.SURFACE_ROW, id),
                        recipe.displayName(), "Производство компонентов", order == null ? "Заказать компоненты" : "Работа зарезервированной партии",
                        List.of(InfoSection.of("Изготовление компонентов", fields.toArray(String[]::new))),
                        "Рецепт, реальные запасы собственной станции и сохраняемая очередь",
                        station.systemId(), 0, runtime.world().getAuthoritativeWorldTick()));
            }
        }
        return List.copyOf(result);
    }
}
