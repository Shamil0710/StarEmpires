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
            var placement=world.findFleet(id).orElseThrow();
            if(p.factionContentId()!=null && c.coordinator().commands().groups().stream().noneMatch(g->!g.retired()&&g.memberFleetIds().contains(id))
                    && placement.locationKind()==FleetLocationKind.IN_SYSTEM && p.discoveredSystemIds().contains(placement.systemId()))
                rows.add(groupRow(c,p,id,placement,snapshot));
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
        if(p.factionContentId()!=null) for(var group:c.coordinator().commands().groups()) {
            if(world.findFactionStableId(group.factionId()).filter(p.factionContentId()::equals).isEmpty())continue;
            String members=group.memberFleetIds().stream().map(id->p.ownedFleetIds().contains(id) ? ship(p,id) : "Корабль прежнего состава")
                    .collect(java.util.stream.Collectors.joining(", "));
            for (var operation : c.coordinator().operations().operations()) if (operation.commandGroupId() == group.id()
                    && c.isPlayerGroupGuardOperation(operation) && !operation.status().active())
                rows.add(guardRow(c, group, "group-guard-history|" + group.id() + '|' + operation.id(),
                        "История локальной охраны", members,
                        operation.status() == StrategicOperationState.OperationStatus.COMPLETED ? "Охрана завершена" : "Охрана прервана"));
            if(group.retired()) {
                long completed=c.coordinator().commands().orders().stream().filter(o->o.commandGroupId()==group.id()
                        &&o.status()==FleetCommandState.OrderStatus.COMPLETE).count();
                long ended=c.coordinator().commands().orders().stream().filter(o->o.commandGroupId()==group.id()
                        &&(o.status()==FleetCommandState.OrderStatus.CANCELLED||o.status()==FleetCommandState.OrderStatus.FAILED)).count();
                rows.add(new ProductionUiSnapshot.Row(new GeneratedWorldCommandUiRenderer.UiSelection(
                        GeneratedWorldCommandUiRenderer.SelectionKind.SURFACE_ROW,PREFIX+"group-history|"+group.id()),
                        group.name(),"История командных групп","Группа расформирована",
                        List.of(GeneratedWorldUiSnapshot.InfoSection.of("Сохранённая история","Прежний состав",members,
                                "Завершённые приказы",Long.toString(completed),"Отменённые или прерванные",Long.toString(ended),
                                "Полномочия","Новые приказы этой группе не выдаются; оставшиеся корабли можно включить в новую группу")),
                        "Сохранённые состав и конечные статусы реальных приказов. История не восстанавливает потерянные корабли.",
                        null,0,world.getAuthoritativeWorldTick()));
                continue;
            }
            var active=c.coordinator().commands().activeOrderFor(group.id());
            String status=active.isPresent() ? "Есть исполняемый приказ" : "Нет активного приказа";
            if(active.isPresent()) {
                if (c.hasPlayerGroupGuard(group.id()))
                    rows.add(guardRow(c, group, "group-guard-stop|" + group.id(), "Завершить охрану", members, "Локальная самооборона"));
                else rows.add(groupMovementRow(c,group,"group-cancel|"+group.id(),"Отменить приказ",members,status));
            }
            else {
                rows.add(guardRow(c, group, "group-guard|" + group.id(), "Охранять здесь", members, "Проверить готовность группы"));
                for(var system:p.discoveredSystemIds()) {
                    String destination=snapshot.galaxy().systems().stream().filter(s->s.id().equals(system))
                            .map(s->s.name()).findFirst().orElse("Известная система");
                    rows.add(groupMovementRow(c,group,"group-stage|"+group.id()+"|"+system.value(),"Собраться: "+destination,members,status));
                }
                rows.add(groupMovementRow(c,group,"group-return|"+group.id(),"Вернуться домой",members,status));
            }
            rows.add(new ProductionUiSnapshot.Row(new GeneratedWorldCommandUiRenderer.UiSelection(
                    GeneratedWorldCommandUiRenderer.SelectionKind.SURFACE_ROW,PREFIX+"retire-group|"+group.id()),
                    group.name()+": расформировать","Командная группа",status,
                    List.of(GeneratedWorldUiSnapshot.InfoSection.of("Расформирование","Состав",members,
                            "Приказ",status,"Результат","Будущие действия группы отменяются; состав и приказы остаются в истории",
                            "Полёт","Уже начатый физический прыжок продолжается без отмены и перемещения корабля")),
                    "Собственная фракция и существующая командная группа. Корабли остаются в собственности и доступны для новой группы.",
                    null,0,world.getAuthoritativeWorldTick()));
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
            case "group" -> List.of(new GeneratedCampaignFactionUi.Action("group","СФОРМИРОВАТЬ"));
            case "retire-group" -> List.of(new GeneratedCampaignFactionUi.Action("retire-group","РАСФОРМИРОВАТЬ"));
            case "group-stage" -> List.of(new GeneratedCampaignFactionUi.Action("group-stage","СОБРАТЬ"));
            case "group-return" -> List.of(new GeneratedCampaignFactionUi.Action("group-return","ВЕРНУТЬ ДОМОЙ"));
            case "group-guard" -> List.of(new GeneratedCampaignFactionUi.Action("group-guard","ОХРАНЯТЬ ЗДЕСЬ"));
            case "group-guard-stop" -> List.of(new GeneratedCampaignFactionUi.Action("group-guard-stop","ЗАВЕРШИТЬ ОХРАНУ"));
            case "group-cancel" -> List.of(new GeneratedCampaignFactionUi.Action("group-cancel","ОТМЕНИТЬ ПРИКАЗ"));
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
        var parts=id.substring(PREFIX.length()).split("\\|",-1);
        if(action.equals("group-guard"))return c.previewPlayerGroupGuard(Long.parseLong(parts[1]));
        if(action.equals("group-guard-stop"))return c.previewPlayerGroupGuardStop(Long.parseLong(parts[1]));
        if(action.equals("retire-group"))return c.previewPlayerCommandGroupRetirement(Long.parseLong(parts[1]));
        if(action.equals("group-cancel"))return c.previewPlayerGroupMovementCancellation(Long.parseLong(parts[1]));
        if(action.equals("group-stage"))return c.previewPlayerGroupMovement(Long.parseLong(parts[1]),FleetCommandState.OrderType.STAGE,new StarSystemId(Long.parseLong(parts[2])));
        if(action.equals("group-return"))return c.previewPlayerGroupMovement(Long.parseLong(parts[1]),FleetCommandState.OrderType.RETURN,c.coordinator().commands().requireGroup(Long.parseLong(parts[1])).homeSystemId());
        var fleet=new FleetId(Long.parseLong(parts[1]));
        if(action.equals("group")) return c.previewPlayerCommandGroup("Группа: "+ship(c.playerState().orElseThrow(),fleet),
                List.of(fleet),c.coordinator().runtime().world().findFleet(fleet).orElseThrow().systemId());
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
    private static ProductionUiSnapshot.Row groupRow(Stage228CampaignAuthority c,PlayerState p,FleetId id,
            FleetPlacementState placement,GeneratedWorldUiSnapshot snapshot) {
        var world=c.coordinator().runtime().world();
        var legal=world.findSession(placement.systemId()).orElseThrow().getEntityRegistry().require(placement.localEntityId())
                .getComponent(com.spacesim.components.FactionComponent.class);
        boolean registered=legal!=null && world.findFactionStableId(legal.factionId).filter(p.factionContentId()::equals).isPresent();
        String status=registered ? "Зарегистрирован в собственной фракции" : "Требуется юридическая регистрация";
        String home=snapshot.galaxy().systems().stream().filter(s->s.id().equals(placement.systemId()))
                .map(s->s.name()).findFirst().orElse("Известная система");
        var crew=c.observedPlayerFleetCrew(id);
        String staffing=crew.isPresent() ? Integer.toString(crew.getAsInt())
                : id.equals(p.activeFleetId()) ? "Пилот на борту; остальной состав не подтверждён" : "Нет подтверждённого наблюдения";
        return new ProductionUiSnapshot.Row(new GeneratedWorldCommandUiRenderer.UiSelection(
                GeneratedWorldCommandUiRenderer.SelectionKind.SURFACE_ROW,PREFIX+"group|"+id.value()),
                ship(p,id)+": сформировать командную группу","Командование",status,
                List.of(GeneratedWorldUiSnapshot.InfoSection.of("Формирование группы","Состав",ship(p,id),
                        "Регистрация",status,"Домашняя система",home,"Предельный стратегический риск","25 %",
                        "Подтверждённый экипаж",staffing,
                        "Готовность к приказу","Экипаж и снабжение проверяются отдельно")),
                "Личный состав кораблей и их юридическая регистрация. Формирование группы сохраняет текущие приказы и положение кораблей.",
                placement.systemId(),id.value(),world.getAuthoritativeWorldTick());
    }
    private static ProductionUiSnapshot.Row groupMovementRow(Stage228CampaignAuthority c,FleetCommandState.CommandGroupState group,
            String key,String title,String members,String status) {
        return new ProductionUiSnapshot.Row(new GeneratedWorldCommandUiRenderer.UiSelection(
                GeneratedWorldCommandUiRenderer.SelectionKind.SURFACE_ROW,PREFIX+key),group.name()+": "+title,"Групповой приказ",status,
                List.of(GeneratedWorldUiSnapshot.InfoSection.of("Управление группой","Состав",members,"Приказ",status,
                        "Маршрут","Только лично известные системы и разрешённый транзит",
                        "Топливо","Бортовой запас каждого корабля; заправка оплачивается отдельно",
                        "Отмена","Будущие переходы прекращаются; начатый прыжок продолжается",
                        "Ограничение риска","Доля стоимости риска в оценке маршрута до 25 %; это не вероятность боя")),
                "Обычная физика, экипаж и установленные приводы каждого корабля. Ручная тяга отменяет будущие действия группы.",
                null,0,c.coordinator().runtime().world().getAuthoritativeWorldTick());
    }
    private static ProductionUiSnapshot.Row guardRow(Stage228CampaignAuthority c, FleetCommandState.CommandGroupState group,
            String key, String title, String members, String status) {
        return new ProductionUiSnapshot.Row(new GeneratedWorldCommandUiRenderer.UiSelection(
                GeneratedWorldCommandUiRenderer.SelectionKind.SURFACE_ROW, PREFIX + key), group.name() + ": " + title,
                "Локальная охрана", status, List.of(GeneratedWorldUiSnapshot.InfoSection.of("Самооборона группы",
                        "Состав", members, "Место", "Только текущая известная система; переход не выполняется",
                        "Готовность", "Боевые требования к корпусу, экипажу, датчикам и боезапасу проверяются постоянно",
                        "Снабжение", "Только бортовое состояние; внешняя заправка и обслуживание выполняются отдельно",
                        "Результат", "Нет автоматического перехвата, урона или территориального контроля",
                        "Остановка", "Ручная тяга завершает охрану; потеря готовности прерывает её")),
                "Существующие корабли и наблюдаемый экипаж. Готовность к самообороне не гарантирует победу или полное обслуживание.",
                null, 0, c.coordinator().runtime().world().getAuthoritativeWorldTick());
    }
    private static String ship(PlayerState p,FleetId id){return "Мой корабль "+(p.ownedFleetIds().indexOf(id)+1);}
    private static String kind(FleetOrderType t){return switch(t){case HOLD->"Удерживать";case MOVE->"Переход";case FOLLOW->"Следовать";case ESCORT->"Эскорт";case PATROL->"Патруль";default->"Другой сохранённый приказ";};}
    private static ProductionUiSnapshot.Row row(String id,String name,String order,StarSystemId system,FleetId fleet,long tick,String readiness){
        return new ProductionUiSnapshot.Row(new GeneratedWorldCommandUiRenderer.UiSelection(GeneratedWorldCommandUiRenderer.SelectionKind.SURFACE_ROW,PREFIX+id),name,"Мой флот",order,
                List.of(GeneratedWorldUiSnapshot.InfoSection.of("Личный автопилот","Текущий приказ",order,"Готовность FTL",readiness,"Удержание","Конечное торможение, расход настоящего топлива","Переход","По лично известным системам; штатный подход и переход, только бортовое топливо","Патруль","Непрерывный цикл двух известных систем","Следование / эскорт","Личный корабль; дистанция 500 / 1 000 м, сближение до 100 м/с относительно цели, конечная тяга","Приоритет","Активный корабль управляется вручную; приказ возобновляется после смены управления","Ресурсы","Не выдаются; нет автоматической заправки или гарантии боевой защиты")),
                "Личное владение, сохранённый приказ, фактические положение и состояние привода; общие правила маршрута и перехода",system,fleet.value(),tick);
    }
}
