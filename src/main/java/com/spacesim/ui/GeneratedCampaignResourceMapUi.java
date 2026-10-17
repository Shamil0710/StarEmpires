package com.spacesim.ui;

import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.SelectionKind;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.UiSelection;
import com.spacesim.ui.GeneratedWorldUiSnapshot.InfoSection;
import java.util.ArrayList;
import java.util.List;

/** Public resource-report offers at the actual dispatcher contact, without unpaid coordinates. */
public final class GeneratedCampaignResourceMapUi {
    private GeneratedCampaignResourceMapUi() { }

    /**
     * Lists existing local resource records available from a contacted civilian operator.
     * @param campaign current human campaign
     * @return immutable offers; no coordinates, grades or reserves are disclosed before purchase
     */
    public static List<ProductionUiSnapshot.Row> rows(Stage228CampaignAuthority campaign) {
        var result = new ArrayList<ProductionUiSnapshot.Row>();
        var runtime = campaign.coordinator().runtime();
        var ontology = com.spacesim.content.Stage18ResourceOntologyLoader.loadDefault();
        int ordinal = 0;
        for (var npc : campaign.coordinator().npcMissions().npcs()) {
            if (!npc.npcId().startsWith("npc.stage23b.dispatcher:") || !campaign.canContactNpc(npc.npcId())) continue;
            for (var entry : runtime.discoveryState().knowledgeFor(npc.factionContentId()).entries()) {
                if (!entry.object().resourceObject() || !campaign.canPurchaseResourceMap(npc.npcId(), entry.object().objectId())) continue;
                var commodity = entry.classificationId().map(ontology::findCommodity).orElse(null);
                String offeredMaterial = commodity == null ? "Состав не указан в предложении" : commodity.displayName();
                result.add(new ProductionUiSnapshot.Row(new UiSelection(SelectionKind.SURFACE_ROW,
                        "pilot-resource-map|" + npc.npcId() + '|' + entry.object().objectId()),
                        commodity == null ? "Карта ресурсного участка " + ++ordinal : "Карта: " + offeredMaterial,
                        "Сведения местного оператора", "Ресурс подтверждён архивным наблюдением",
                        List.of(InfoSection.of("Передача карты", "Вид сырья в предложении", offeredMaterial,
                                "Цена", "100 кредитов",
                                "Оплата", "Личный кошелёк → казна оператора",
                                "Содержимое", "Сохранённое положение и сведения об образце; новых измерений нет",
                                "Актуальность", "Сохраняются время и срок исходного наблюдения",
                                "Оборудование", "Пригодность вашего оборудования для добычи проверяется отдельно")),
                        "Существующий архив физически доступного диспетчера; неоплаченные координаты не раскрываются",
                        null, 0, runtime.world().getAuthoritativeWorldTick()));
            }
        }
        return List.copyOf(result);
    }
}
