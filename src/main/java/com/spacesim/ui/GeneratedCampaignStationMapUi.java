package com.spacesim.ui;

import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.SelectionKind;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.UiSelection;
import com.spacesim.ui.GeneratedWorldUiSnapshot.InfoSection;
import java.util.ArrayList;
import java.util.List;

/** Paid civilian station coordinates from a contacted operator's existing archive. */
public final class GeneratedCampaignStationMapUi {
    private GeneratedCampaignStationMapUi() { }

    /**
     * Lists new station maps without disclosing unpaid coordinates or private station services.
     * @param campaign current personal campaign
     * @return immutable offers at actual local contacts
     */
    public static List<ProductionUiSnapshot.Row> rows(Stage228CampaignAuthority campaign) {
        var result = new ArrayList<ProductionUiSnapshot.Row>();
        var runtime = campaign.coordinator().runtime();
        int ordinal = 0;
        for (var npc : campaign.coordinator().npcMissions().npcs()) {
            if (!npc.npcId().startsWith("npc.stage23b.dispatcher:") || !campaign.canContactNpc(npc.npcId())) continue;
            for (var entry : runtime.discoveryState().knowledgeFor(npc.factionContentId()).entries()) {
                if (!campaign.canPurchaseStationMap(npc.npcId(), entry.object().objectId())) continue;
                result.add(new ProductionUiSnapshot.Row(new UiSelection(SelectionKind.SURFACE_ROW,
                        "pilot-station-map|" + npc.npcId() + '|' + entry.object().objectId()),
                        "Навигационная карта станции " + ++ordinal, "Карты станций", "Архивные сведения местного оператора",
                        List.of(InfoSection.of("Передача карты станции", "Цена", "100 кредитов",
                                "Оплата", "Личный кошелёк → казна оператора",
                                "Содержимое", "Сохранённое положение и классификация гражданской станции",
                                "Актуальность", "Время исходного наблюдения сохраняется",
                                "Доступ", "Карта не даёт владения, права удалённой торговли или сведений о запасах")),
                        "Существующий архив физически доступного диспетчера; неоплаченные координаты не раскрываются",
                        null, 0, runtime.world().getAuthoritativeWorldTick()));
            }
        }
        return List.copyOf(result);
    }
}
