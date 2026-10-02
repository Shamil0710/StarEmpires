package com.spacesim.ui;

import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.SelectionKind;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.UiSelection;
import com.spacesim.ui.GeneratedWorldUiSnapshot.InfoSection;
import com.spacesim.world.*;
import java.util.ArrayList;
import java.util.List;

/** Read-only personal faction controls and immutable drafts for existing shared command services. */
public final class GeneratedCampaignFactionUi {
    private GeneratedCampaignFactionUi() { }
    /**
     * One presentation action; its identifier carries no simulation authority.
     * @param id local action suffix
     * @param label visible Russian verb
     */
    public record Action(String id, String label) { }
    private static final String PREFIX = "player-government|";
    private static final String[] DOCTRINE = {"Торговая открытость","Приоритет безопасности","Стремление к расширению","Чувствительность к суверенитету","Договорная дисциплина","Интервенционизм","Экономическая устойчивость"};
    private static final String[] FISCAL = {"Налог собственных станций","Сбор с иностранных станций","Резерв казны","Резерв кошелька станции","Поддержка станции за решение","Строительные инвестиции за решение"};

    /**
     * Projects the actual player's faction separately from the spectator knowledge viewer.
     * @param c current composed authority
     * @param snapshot existing read-only world labels
     * @return immutable policy/diplomatic/territorial rows; empty for independent players
     */
    public static List<ProductionUiSnapshot.Row> rows(Stage228CampaignAuthority c, GeneratedWorldUiSnapshot snapshot) {
        var player=c.playerState().orElse(null);
        if(player==null||!player.affiliated())return List.of();
        var world=c.coordinator().runtime().world();String own=player.factionContentId();
        var rows=new ArrayList<ProductionUiSnapshot.Row>();
        long[] doctrine=doctrine(world.findFactionStrategicState(own).orElseThrow().doctrine());
        long[] fiscal=fiscal(world.findFactionFiscalPolicy(own).orElseThrow());
        for(int i=0;i<DOCTRINE.length;i++)rows.add(row("doctrine|"+i,DOCTRINE[i],"Моя доктрина",Long.toString(doctrine[i]),
                InfoSection.of("Институциональные предпочтения","Текущее значение",doctrine[i]+" / 100","Шаг изменения","5 пунктов","Последствие","Вес решений; ресурсы и права не предоставляются"),null,world.getAuthoritativeWorldTick()));
        for(int i=0;i<FISCAL.length;i++)rows.add(row("fiscal|"+i,FISCAL[i],"Мои финансы",fiscalValue(i,fiscal[i]),
                InfoSection.of("Фискальные ограничения","Текущее значение",fiscalValue(i,fiscal[i]),"Шаг изменения",i<2?"1 процентный пункт":"1 000 кредитов","Действие","Меняет разрешённые будущие потоки; существующие деньги не создаёт"),null,world.getAuthoritativeWorldTick()));
        for(var other:snapshot.galaxy().factions()) {
            if(other.factionId().equals(own))continue;
            rows.add(row("embargo|"+other.factionId(),"Доступ к рынкам: "+other.displayName(),"Моя дипломатия","Эмбарго или отмена",
                    InfoSection.of("Одностороннее решение","Цель",other.displayName(),"Срок","Бессрочно, до отмены","Последствие","Правовой доступ; без уничтожения товаров и денег"),null,world.getAuthoritativeWorldTick()));
            for(var kind:DiplomaticTreatyClauseState.Kind.values()) {
                if(kind==DiplomaticTreatyClauseState.Kind.CONSTRUCTION_RIGHT) {
                    for(var system:player.discoveredSystemIds())rows.add(offer(other.factionId(),other.displayName(),kind,system,systemName(snapshot,system),world.getAuthoritativeWorldTick()));
                } else rows.add(offer(other.factionId(),other.displayName(),kind,null,"Все системы",world.getAuthoritativeWorldTick()));
            }
        }
        for(var directory:world.getFactionDiplomacyStates())for(var treaty:directory.treaties()) {
            if(!directory.factionContentId().equals(own)&&!treaty.counterpartyFactionContentId().equals(own))continue;
            String other=directory.factionContentId().equals(own)?treaty.counterpartyFactionContentId():directory.factionContentId();
            String name=snapshot.galaxy().factions().stream().filter(f->f.factionId().equals(other)).map(f->f.displayName()).findFirst().orElse("Контрагент");
            rows.add(row("treaty-extra|"+treaty.treatyId(),"Изменить договор: "+name,"Мои договоры",treatyStatus(treaty.status()),
                    InfoSection.of("Дополнительные решения","Нарушение","Прекращает права; оставляет договорную претензию","Продление","Новое предложение тех же условий; требуется согласие","Контрпредложение","Отклоняет входящее предложение и предлагает те же условия от нашей стороны"),null,world.getAuthoritativeWorldTick()));
            rows.add(row("treaty|"+treaty.treatyId(),"Договор: "+name,"Мои договоры",treatyStatus(treaty.status()),
                    InfoSection.of("Фактический договор","Состояние",treatyStatus(treaty.status()),"Принятие","Только получателем предложения","Прекращение","Уведомление за 20 тактов; до срока обязательства действуют","Нарушение","Прекращает права и создаёт обоснованную претензию","Условия",treaty.clauses().stream().map(v->clause(v.kind())).collect(java.util.stream.Collectors.joining(", "))),null,world.getAuthoritativeWorldTick()));
        }
        for(var system:player.discoveredSystemIds()) {
            var claim=world.findFactionStrategicState(own).orElseThrow().claimFor(system);
            rows.add(row("territory|"+system.value(),"Территориальное решение: "+systemName(snapshot,system),"Моя территория",claim==null?"Своей претензии нет":"Своя претензия заявлена",
                    InfoSection.of("Политическая претензия","Претензия",claim==null?"Нет":"Заявлена; прогресс "+claim.stabilizationTicks()+" тактов","Контроль","Требует стабилизации и присутствия фракции; личное владение кораблём само не меняет регистрацию","Отказ от контроля","Доступен только фактическому владельцу"),system,world.getAuthoritativeWorldTick()));
            for(var other:snapshot.galaxy().factions())if(!other.factionId().equals(own)) {
                rows.add(row("recognition|"+system.value()+"|"+other.factionId(),"Признание: "+other.displayName()+" / "+systemName(snapshot,system),"Моя территория","Претензия или контроль",
                        InfoSection.of("Политическое признание","Контрагент",other.displayName(),"Основание","Только существующая претензия или контроль; общая проверка"),system,world.getAuthoritativeWorldTick()));
                rows.add(row("right|"+system.value()+"|"+other.factionId(),"Строительное право: "+other.displayName()+" / "+systemName(snapshot,system),"Моя территория","Предоставить или отозвать",
                        InfoSection.of("Территориальная концессия","Получатель",other.displayName(),"Срок","Бессрочно, до отзыва","Основание","Только действительный собственный контроль; строительство требует реальных ресурсов"),system,world.getAuthoritativeWorldTick()));
            }
        }
        return List.copyOf(rows);
    }
    /**
     * Lists presentation intents for one known row family.
     * @param id stable personal faction row identity
     * @return at most three labelled actions; empty for other rows
     */
    public static List<Action> actions(String id) {
        if(!id.startsWith(PREFIX))return List.of();String kind=id.substring(PREFIX.length()).split("\\|",-1)[0];
        return switch(kind) {
            case "doctrine","fiscal" -> List.of(new Action("less","УМЕНЬШИТЬ"),new Action("more","УВЕЛИЧИТЬ"));
            case "embargo" -> List.of(new Action("impose","ЭМБАРГО"),new Action("revoke","ОТМЕНИТЬ"));
            case "offer" -> List.of(new Action("mutual","ВЗАИМНО"),new Action("give","МЫ ДАЁМ"),new Action("receive","НАМ ДАЮТ"));
            case "treaty" -> List.of(new Action("accept","ПРИНЯТЬ"),new Action("reject","ОТКЛОНИТЬ"),new Action("notice","ПРЕКРАТИТЬ"));
            case "treaty-extra" -> List.of(new Action("breach","НАРУШИТЬ"),new Action("renew","ПРОДЛИТЬ"),new Action("counter","КОНТРПРЕДЛОЖИТЬ"));
            case "territory" -> List.of(new Action("claim","ЗАЯВИТЬ"),new Action("withdraw","ОТОЗВАТЬ"),new Action("relinquish","ОТКАЗАТЬСЯ"));
            case "recognition" -> List.of(new Action("claim","ПРЕТЕНЗИЯ"),new Action("control","КОНТРОЛЬ"));
            case "right" -> List.of(new Action("grant","ДАТЬ ПРАВО"),new Action("revoke","ОТОЗВАТЬ"));
            default -> List.of();
        };
    }
    /**
     * Drafts an ordinary immutable command and asks the shared player authority to validate it.
     * @param c current composed authority
     * @param id selected personal faction row
     * @param action selected permitted action suffix
     * @return pure exact-state command confirmation
     */
    public static Stage228CampaignAuthority.PlayerFactionCommandPreview preview(Stage228CampaignAuthority c,String id,String action) {
        if(actions(id).stream().noneMatch(v->v.id().equals(action)))throw new IllegalArgumentException("Unknown faction presentation action");
        String[] p=id.substring(PREFIX.length()).split("\\|",-1);String own=c.playerState().orElseThrow().factionContentId();var world=c.coordinator().runtime().world();
        return switch(p[0]) {
            case "doctrine" -> {
                long[] v=doctrine(world.findFactionStrategicState(own).orElseThrow().doctrine());int index=Integer.parseInt(p[1]);v[index]+=action.equals("less")?-5:5;
                yield c.previewPlayerFactionPolicy(new FactionPolicyCommand.UpdateDoctrine(new FactionDoctrineState((int)v[0],(int)v[1],(int)v[2],(int)v[3],(int)v[4],(int)v[5],(int)v[6])));
            }
            case "fiscal" -> {
                long[] v=fiscal(world.findFactionFiscalPolicy(own).orElseThrow());int index=Integer.parseInt(p[1]);v[index]=Math.addExact(v[index],(action.equals("less")?-1:1)*(index<2?100L:1_000_000L));
                yield c.previewPlayerFactionPolicy(new FactionPolicyCommand.UpdateFiscalPolicy(new FactionFiscalPolicyState(Math.toIntExact(v[0]),Math.toIntExact(v[1]),v[2],v[3],v[4],v[5])));
            }
            case "embargo" -> c.previewPlayerFactionEmbargo(action.equals("impose")?new DiplomaticEmbargoCommand.Impose(own,p[1],-1,"player.market-policy"):new DiplomaticEmbargoCommand.Revoke(own,p[1]));
            case "offer" -> c.previewPlayerFactionTreaty(new DiplomaticTreatyCommand.Offer(own,p[1],List.of(new DiplomaticTreatyClauseState(DiplomaticTreatyClauseState.Kind.valueOf(p[2]),switch(action){case "give"->DiplomaticTreatyClauseState.Direction.OWNER_TO_COUNTERPARTY;case "receive"->DiplomaticTreatyClauseState.Direction.COUNTERPARTY_TO_OWNER;default->DiplomaticTreatyClauseState.Direction.MUTUAL;},p[3].isEmpty()?null:new StarSystemId(Long.parseLong(p[3])))),-1));
            case "treaty" -> c.previewPlayerFactionTreaty(switch(action){case "accept"->new DiplomaticTreatyCommand.Accept(own,p[1]);case "reject"->new DiplomaticTreatyCommand.Reject(own,p[1]);default->new DiplomaticTreatyCommand.TerminateWithNotice(own,p[1],20);});
            case "treaty-extra" -> {
                var treaty=world.findDiplomaticTreaty(p[1]).orElseThrow();
                yield c.previewPlayerFactionTreaty(switch(action){case "breach"->new DiplomaticTreatyCommand.Breach(own,p[1],"player.explicit-breach");case "renew"->new DiplomaticTreatyCommand.Renew(own,p[1],-1);default->new DiplomaticTreatyCommand.CounterOffer(own,p[1],treaty.clauses().stream().map(DiplomaticTreatyClauseState::relativeToOppositeParty).toList(),-1);});
            }
            case "territory" -> c.previewPlayerFactionTerritory(switch(action){case "claim"->"CLAIM";case "withdraw"->"WITHDRAW";default->"RELINQUISH";},new StarSystemId(Long.parseLong(p[1])),"",-1);
            case "recognition" -> c.previewPlayerFactionTerritory(action.equals("claim")?"RECOGNIZE_CLAIM":"RECOGNIZE_CONTROL",new StarSystemId(Long.parseLong(p[1])),p[2],-1);
            case "right" -> c.previewPlayerFactionTerritory(action.equals("grant")?"GRANT_RIGHT":"REVOKE_RIGHT",new StarSystemId(Long.parseLong(p[1])),p[2],-1);
            default -> throw new IllegalArgumentException("Unsupported personal faction row");
        };
    }
    /**
     * Explains the selected draft, including exact before/after numeric values.
     * @param c current composed authority
     * @param id selected personal faction row
     * @param action selected action suffix
     * @return Russian confirmation text derived from current policy values
     */
    public static String explanation(Stage228CampaignAuthority c,String id,String action) {
        String[] p=id.substring(PREFIX.length()).split("\\|",-1);var world=c.coordinator().runtime().world();String own=c.playerState().orElseThrow().factionContentId();
        if(p[0].equals("doctrine")){int i=Integer.parseInt(p[1]);long v=doctrine(world.findFactionStrategicState(own).orElseThrow().doctrine())[i];return "Проверено: "+DOCTRINE[i]+" "+v+" → "+(v+(action.equals("less")?-5:5))+" / 100.";}
        if(p[0].equals("fiscal")){int i=Integer.parseInt(p[1]);long v=fiscal(world.findFactionFiscalPolicy(own).orElseThrow())[i];return "Проверено: "+FISCAL[i]+" "+fiscalValue(i,v)+" → "+fiscalValue(i,v+(action.equals("less")?-1:1)*(i<2?100L:1_000_000L))+".";}
        return "Проверено: "+actions(id).stream().filter(a->a.id().equals(action)).map(Action::label).findFirst().orElseThrow()+". Условия в сведениях; ресурсы не предоставляются.";
    }

    private static ProductionUiSnapshot.Row offer(String target,String name,DiplomaticTreatyClauseState.Kind kind,StarSystemId system,String scope,long tick){return row("offer|"+target+"|"+kind+"|"+(system==null?"":system.value()),"Предложить: "+clause(kind)+" / "+name+(system==null?"":" / "+scope),"Мои предложения","Нужно согласие контрагента",InfoSection.of("Предлагаемое право","Условие",clause(kind),"Область",scope,"Срок","Бессрочно, до прекращения","Направление","Выберите взаимно, мы даём или нам дают","Согласие","Отправка не активирует договор; контрагент должен принять"),system,tick);}
    private static ProductionUiSnapshot.Row row(String id,String name,String category,String summary,InfoSection info,StarSystemId system,long tick){return new ProductionUiSnapshot.Row(new UiSelection(SelectionKind.SURFACE_ROW,PREFIX+id),name,category,summary,List.of(info),"Фактическая собственная фракция и общие правила политики, договоров и территорий; подтверждение не создаёт ресурсов",system,0,tick);}
    private static long[] doctrine(FactionDoctrineState v){return new long[]{v.tradeOpenness(),v.securityPosture(),v.expansionPreference(),v.sovereigntySensitivity(),v.treatyLegalism(),v.interventionism(),v.economicResiliencePriority()};}
    private static long[] fiscal(FactionFiscalPolicyState v){return new long[]{v.stationTaxBasisPoints(),v.foreignTerritoryLevyBasisPoints(),v.treasuryReserveFloorMilliCredits(),v.stationLiquidityReserveMilliCredits(),v.maxLiquiditySupportPerDecisionMilliCredits(),v.maxConstructionInvestmentPerDecisionMilliCredits()};}
    private static String fiscalValue(int i,long v){return String.format(java.util.Locale.ROOT,i<2?"%.2f %%":"%.2f кр.",i<2?v/100d:v/1000d);}
    private static String systemName(GeneratedWorldUiSnapshot s,StarSystemId id){return s.galaxy().systems().stream().filter(v->v.id().equals(id)).map(v->v.name()).findFirst().orElse("Известная система");}
    private static String clause(DiplomaticTreatyClauseState.Kind k){return switch(k){case MARKET_ACCESS->"Доступ к рынкам";case CONSTRUCTION_RIGHT->"Строительное право";case TRANSIT_RIGHT->"Транзит";case CUSTOMS_TARIFF_EXEMPTION->"Освобождение от пошлин";case GUARANTEE->"Гарантия безопасности";};}
    private static String treatyStatus(DiplomaticTreatyState.Status s){return switch(s){case PROPOSED->"Предложен";case ACTIVE->"Действует";case TERMINATING->"Срок уведомления";case BREACHED->"Нарушен";case EXPIRED->"Истёк";case REJECTED->"Отклонён";};}
}
