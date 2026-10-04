package com.spacesim.ui;

import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.player.*;
import com.spacesim.world.*;
import java.util.*;

/** Personal inactive-fleet commands; presentation never borrows spectator faction authority. */
public final class GeneratedCampaignFleetUi {
    private static final String PREFIX="player-fleet|";
    private GeneratedCampaignFleetUi() { }

    /**
     * Projects orders for existing owned inactive freight with known destinations and owned targets.
     * @param c current campaign
     * @param snapshot read-only public system labels
     * @return immutable personal command rows
     */
    public static List<ProductionUiSnapshot.Row> rows(Stage228CampaignAuthority c, GeneratedWorldUiSnapshot snapshot) {
        var p=c.playerState().orElse(null);if(p==null)return List.of();
        var world=c.coordinator().runtime().world();var rows=new ArrayList<ProductionUiSnapshot.Row>();
        for(var id:p.ownedFleetIds()) {
            if(id.equals(p.activeFleetId()))continue;
            var freight=c.coordinator().runtime().freight().findFreighter(id).orElse(null);
            if(freight==null||freight.phase()!=com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase.IDLE)continue;
            var fleet=world.findFleet(id).orElseThrow();String name=ship(p,id);
            String order=p.fleetOrders().stream().filter(o->o.fleetId().equals(id)).map(o->kind(o.type())).findFirst().orElse("Удержание по умолчанию");
            String readiness=world.previewFittedFleetJump(id).map(plan->readiness(plan.failure())).orElse("В перелёте или без установленного FTL");
            rows.add(row("hold|"+id.value(),name+": удержание",order,fleet.systemId(),id,world.getAuthoritativeWorldTick(),readiness));
            for(var system:p.discoveredSystemIds()) {
                String systemName=snapshot.galaxy().systems().stream().filter(s->s.id().equals(system)).map(s->s.name()).findFirst().orElse("Известная система");
                rows.add(row("move|"+id.value()+"|"+system.value(),name+": перейти в "+systemName,order,system,id,world.getAuthoritativeWorldTick(),readiness));
                if(!system.equals(fleet.systemId())&&p.discoveredSystemIds().contains(fleet.systemId()))
                    rows.add(row("patrol|"+id.value()+"|"+fleet.systemId().value()+"|"+system.value(),name+": патруль / "+systemName,order,system,id,world.getAuthoritativeWorldTick(),readiness));
            }
            for(var target:p.ownedFleetIds())if(!target.equals(id))
                rows.add(row("follow|"+id.value()+"|"+target.value(),name+": сопровождать "+ship(p,target),order,fleet.systemId(),id,world.getAuthoritativeWorldTick(),readiness));
        }
        return List.copyOf(rows);
    }

    /**
     * Returns available presentation verbs for a personal fleet row.
     * @param id selected stable row identity
     * @return at most two immutable action labels
     */
    public static List<GeneratedCampaignFactionUi.Action> actions(String id) {
        if(id==null||!id.startsWith(PREFIX))return List.of();
        return switch(id.substring(PREFIX.length()).split("\\|",-1)[0]) {
            case "hold" -> List.of(new GeneratedCampaignFactionUi.Action("hold","УДЕРЖИВАТЬ"));
            case "move" -> List.of(new GeneratedCampaignFactionUi.Action("move","ПЕРЕЙТИ"));
            case "patrol" -> List.of(new GeneratedCampaignFactionUi.Action("patrol","ПАТРУЛИРОВАТЬ"));
            case "follow" -> List.of(new GeneratedCampaignFactionUi.Action("follow","СЛЕДОВАТЬ"),new GeneratedCampaignFactionUi.Action("escort","ЭСКОРТ"));
            default -> List.of();
        };
    }

    /**
     * Drafts the same durable player order validated by the shared ownership/knowledge service.
     * @param c current campaign
     * @param id selected row
     * @param action selected permitted verb
     * @return pure exact-state confirmation
     */
    public static Stage228CampaignAuthority.PlayerFactionCommandPreview preview(Stage228CampaignAuthority c,String id,String action) {
        if(actions(id).stream().noneMatch(a->a.id().equals(action)))throw new IllegalArgumentException("Unknown fleet action");
        var parts=id.substring(PREFIX.length()).split("\\|",-1);var fleet=new FleetId(Long.parseLong(parts[1]));
        var order=switch(action) {
            case "hold" -> PlayerFleetOrderState.hold(fleet);
            case "move" -> PlayerFleetOrderState.move(fleet,new StarSystemId(Long.parseLong(parts[2])),LocalSystemCoordinates.ARRIVAL_X,LocalSystemCoordinates.ARRIVAL_Y);
            case "patrol" -> PlayerFleetOrderState.patrol(fleet,List.of(new StarSystemId(Long.parseLong(parts[2])),new StarSystemId(Long.parseLong(parts[3]))));
            case "follow" -> PlayerFleetOrderState.follow(fleet,new FleetId(Long.parseLong(parts[2])));
            case "escort" -> PlayerFleetOrderState.escort(fleet,new FleetId(Long.parseLong(parts[2])));
            default -> throw new IllegalArgumentException("Unknown fleet order");
        };
        return c.previewPlayerFleetOrder(order);
    }
    private static String readiness(com.spacesim.ship.ShipEngineeringRuntime.JumpFailure failure){return switch(failure){
        case NONE->"Готов";case COOLDOWN_ACTIVE->"Ожидает окончания охлаждения";
        case STORED_ENERGY_UNAVAILABLE->"Недостаточно запасённой энергии";case CHARGE_POWER_UNAVAILABLE->"Недостаточно мощности для заряда";
        case THERMAL_LIMIT->"Тепловой предел";case TRANSLATED_MASS_EXCEEDED->"Масса превышает предел привода";
        case NO_FTL_MODULE->"Нет действующего привода";case INVALID_CAPABILITY->"Установленный привод недоступен";
    };}
    private static String ship(PlayerState p,FleetId id){return "Мой корабль "+(p.ownedFleetIds().indexOf(id)+1);}
    private static String kind(FleetOrderType t){return switch(t){case HOLD->"Удерживать";case MOVE->"Переход";case FOLLOW->"Следовать";case ESCORT->"Эскорт";case PATROL->"Патруль";default->"Другой сохранённый приказ";};}
    private static ProductionUiSnapshot.Row row(String id,String name,String order,StarSystemId system,FleetId fleet,long tick,String readiness){
        return new ProductionUiSnapshot.Row(new GeneratedWorldCommandUiRenderer.UiSelection(GeneratedWorldCommandUiRenderer.SelectionKind.SURFACE_ROW,PREFIX+id),name,"Мой флот",order,
                List.of(GeneratedWorldUiSnapshot.InfoSection.of("Личный автопилот","Текущий приказ",order,"Готовность FTL",readiness,"Удержание","Конечное торможение, расход настоящего топлива","Переход","По лично известным системам; штатный подход и переход, только бортовое топливо","Патруль","Непрерывный цикл двух известных систем","Следование / эскорт","Личный корабль; дистанция 500 / 1 000 м, сближение до 100 м/с относительно цели, конечная тяга","Приоритет","Активный корабль управляется вручную; приказ возобновляется после смены управления","Ресурсы","Не выдаются; нет автоматической заправки или гарантии боевой защиты")),
                "Личное владение, сохранённый приказ, фактические положение и состояние привода; общие правила маршрута и перехода",system,fleet.value(),tick);
    }
}
