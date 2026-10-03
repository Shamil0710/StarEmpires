package com.spacesim.ui;

import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.components.*;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.SelectionKind;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.UiSelection;
import com.spacesim.ui.GeneratedWorldUiSnapshot.InfoSection;
import com.spacesim.world.*;
import java.util.ArrayList;
import java.util.List;

/** Presentation of existing count-based faction policies; physical SI storage remains separate. */
final class GeneratedCampaignStockProductionUi {
    private GeneratedCampaignStockProductionUi() { }

    static List<ProductionUiSnapshot.Row> rows(Stage228CampaignAuthority c) {
        var world=c.coordinator().runtime().world();var content=c.coordinator().content();
        String own=c.playerState().orElseThrow().factionContentId();var policy=world.findFactionStockProductionPolicy(own).orElseThrow();
        var rows=new ArrayList<ProductionUiSnapshot.Row>();
        for(var item:content.getItems()) {
            int floor=policy.stockPolicies().stream().filter(v->v.itemContentId().equals(item.id())).mapToInt(FactionStockPolicyState::targetStockFloor).findFirst().orElse(0);
            rows.add(row(c,"stock|"+item.id(),"Целевой запас: "+item.displayName(),floor==0?"Без своей надбавки":floor+" ед.",
                    InfoSection.of("Цель товарного рынка","Мой минимум",floor+" товарных единиц","Шаг","100 единиц; ноль снимает свою надбавку","Применение","Отдельное решение; минимум ограничен реальной вместимостью склада","Область","Собственные товарные рынки с учётом единиц; физические склады в кг не изменяются","Ресурсы","Только целевой запас; товаров и денег не выдаёт")));
        }
        for(var station:content.getStationArchetypes()) {
            String recipe=policy.productionPolicies().stream().filter(v->v.stationArchetypeContentId().equals(station.id())).map(FactionProductionPolicyState::recipeContentId).findFirst().orElse(null);
            String label=recipe==null?"Без своего предпочтения":content.findRecipe(recipe).displayName();
            rows.add(row(c,"production|"+station.id(),"Рецепт: "+station.displayName(),label,
                    InfoSection.of("Производственное предпочтение","Мой рецепт",label,"Выбор","Предыдущий или следующий рецепт из каталога; сброс снимает предпочтение","Применение","Отдельно к собственным станциям с товарным производством","Последствие","Смена рецепта сбрасывает незавершённый цикл; запасы и деньги сохраняются","Физические установки","Установленные процессы с килограммами и инженерной работой продолжают по своим действительным возможностям")));
        }
        int[] counts=eligible(c);
        rows.add(row(c,"apply-production","Применить запас и рецепт","Рынки: "+counts[0]+"; производства: "+counts[1],
                InfoSection.of("Явное применение настроек","Собственные товарные рынки",Integer.toString(counts[0]),"Собственные товарные производства",Integer.toString(counts[1]),"Основание","Действительная регистрация, склад и действующий производственный узел","Нулевое число","Подходящих собственных объектов нет; настройки остаются сохранённым намерением","Физические склады","Килограммы, запасы источников, установки и работа не меняются")));
        return List.copyOf(rows);
    }

    static List<GeneratedCampaignFactionUi.Action> actions(String kind) {
        return switch(kind) {
            case "stock" -> List.of(new GeneratedCampaignFactionUi.Action("less","УМЕНЬШИТЬ"),new GeneratedCampaignFactionUi.Action("more","УВЕЛИЧИТЬ"),new GeneratedCampaignFactionUi.Action("reset","СБРОСИТЬ"));
            case "production" -> List.of(new GeneratedCampaignFactionUi.Action("less","ПРЕДЫДУЩИЙ"),new GeneratedCampaignFactionUi.Action("more","СЛЕДУЮЩИЙ"),new GeneratedCampaignFactionUi.Action("reset","СБРОСИТЬ"));
            case "apply-production" -> List.of(new GeneratedCampaignFactionUi.Action("apply","ПРИМЕНИТЬ"));
            default -> List.of();
        };
    }

    static Stage228CampaignAuthority.PlayerFactionCommandPreview preview(Stage228CampaignAuthority c,String[] p,String action) {
        return c.previewPlayerFactionPolicy(command(c,p,action));
    }

    private static FactionPolicyCommand command(Stage228CampaignAuthority c,String[] p,String action) {
        if(p[0].equals("apply-production"))return new FactionPolicyCommand.ApplyStrategicPolicy();
        var content=c.coordinator().content();var old=c.coordinator().runtime().world().findFactionStockProductionPolicy(c.playerState().orElseThrow().factionContentId()).orElseThrow();
        var stocks=new ArrayList<>(old.stockPolicies());var production=new ArrayList<>(old.productionPolicies());
        if(p[0].equals("stock")) {
            if(content.findItem(p[1])==null)throw new IllegalArgumentException("Unknown stock item");
            int value=stocks.stream().filter(v->v.itemContentId().equals(p[1])).mapToInt(FactionStockPolicyState::targetStockFloor).findFirst().orElse(0);
            int next=action.equals("reset")?0:Math.addExact(value,action.equals("less")?-100:100);
            if(next<0)throw new IllegalArgumentException("Target stock floor cannot be negative");
            stocks.removeIf(v->v.itemContentId().equals(p[1]));if(next>0)stocks.add(new FactionStockPolicyState(p[1],next));
        } else {
            if(content.findStationArchetype(p[1])==null)throw new IllegalArgumentException("Unknown production archetype");
            String current=production.stream().filter(v->v.stationArchetypeContentId().equals(p[1])).map(FactionProductionPolicyState::recipeContentId).findFirst().orElse(null);
            production.removeIf(v->v.stationArchetypeContentId().equals(p[1]));
            if(!action.equals("reset")) {
                var recipes=content.getRecipes();if(recipes.isEmpty())throw new IllegalArgumentException("No authored recipes");
                int index=-1;for(int i=0;i<recipes.size();i++)if(recipes.get(i).id().equals(current))index=i;
                index=index<0?(action.equals("less")?recipes.size()-1:0):Math.floorMod(index+(action.equals("less")?-1:1),recipes.size());
                production.add(new FactionProductionPolicyState(p[1],recipes.get(index).id()));
            }
        }
        return new FactionPolicyCommand.UpdateStockProductionPolicy(new FactionStockProductionPolicyState(stocks,production));
    }

    static String explanation(Stage228CampaignAuthority c,String[] p,String action) {
        var command=command(c,p,action);
        if(command instanceof FactionPolicyCommand.ApplyStrategicPolicy) {var n=eligible(c);return "Применение к собственным объектам: товарных рынков "+n[0]+", производств "+n[1]+". Реальная смена рецепта сбросит незавершённый цикл.";}
        var policy=((FactionPolicyCommand.UpdateStockProductionPolicy)command).policy();
        if(p[0].equals("stock")) {int n=policy.stockPolicies().stream().filter(v->v.itemContentId().equals(p[1])).mapToInt(FactionStockPolicyState::targetStockFloor).findFirst().orElse(0);return "Новый целевой минимум: "+n+" товарных единиц. Для применения нужно отдельное решение.";}
        String recipe=policy.productionPolicies().stream().filter(v->v.stationArchetypeContentId().equals(p[1])).map(FactionProductionPolicyState::recipeContentId).findFirst().orElse(null);
        return "Новое предпочтение: "+(recipe==null?"своё предпочтение снято":c.coordinator().content().findRecipe(recipe).displayName())+". Для применения нужно отдельное решение.";
    }

    private static int[] eligible(Stage228CampaignAuthority c) {
        var w=c.coordinator().runtime().world();int id=w.findFactionRuntimeId(c.playerState().orElseThrow().factionContentId()).orElseThrow();int markets=0,production=0;
        for(var s:w.getTopology().systems())for(var e:w.findSession(s.id()).orElseThrow().getEngine().getEntities()) {
            var f=e.getComponent(FactionComponent.class);if(f==null||f.factionId!=id)continue;
            if(e.getComponent(MarketComponent.class)!=null&&e.getComponent(InventoryComponent.class)!=null)markets++;
            if(e.getComponent(ArchetypeComponent.class)!=null&&e.getComponent(ProductionComponent.class)!=null)production++;
        }
        return new int[]{markets,production};
    }

    private static ProductionUiSnapshot.Row row(Stage228CampaignAuthority c,String id,String name,String summary,InfoSection info) {
        return new ProductionUiSnapshot.Row(new UiSelection(SelectionKind.SURFACE_ROW,"player-government|"+id),name,"Мои запасы и производство",summary,List.of(info),"Действительные настройки собственной фракции; применение ограничено собственными подходящими объектами",null,0,c.coordinator().runtime().world().getAuthoritativeWorldTick());
    }
}
