import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import java.nio.file.Path;

/** Read-only exact audit of actual checkpoint receipt identities and compact row counts. */
public final class Stage23BActualExtractionSeriesInspect {
 public static void main(String[] args)throws Exception {
  var saved=Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(Path.of(args[0]));
  var campaign=Stage228CampaignAuthority.restore(saved);
  if(!saved.equals(campaign.captureState()))throw new AssertionError("Restore changed actual checkpoint");
  var runtime=campaign.coordinator().runtime();
  var freight=runtime.freight().capture();
  long virtual=0, seriesRows=0, largest=0;
  for(var lot:freight.cargoLots())if(lot.receiptSeries()!=null){
   var s=lot.receiptSeries();virtual=Math.addExact(virtual,s.receiptCount());seriesRows++;
   largest=Math.max(largest,s.receiptCount());
   if(!lot.lotId().equals("freight-lot:"+s.receipt(0).ordinal()))throw new AssertionError("Wrong first identity");
   if(Double.doubleToLongBits(lot.loadedAtSimulationSeconds())!=Double.doubleToLongBits(s.receipt(0).loadedAtSeconds()))throw new AssertionError("Wrong first date");
  }
  var bytes=com.spacesim.persistence.Stage20FreightPersistenceCodec.encode(freight);
  if(!freight.equals(com.spacesim.persistence.Stage20FreightPersistenceCodec.decode(bytes)))throw new AssertionError("Freight round trip changed provenance");
  System.out.println("ACTUAL EXTRACTION SERIES rows="+freight.cargoLots().size()+" seriesRows="+seriesRows
   +" virtualReceipts="+virtual+" largestSeries="+largest+" freightBytes="+bytes.length+" allocator="+freight.nextCargoLotOrdinal());
  var player=campaign.playerState().orElseThrow();
  System.out.println("ACTUAL PLAYER tick="+runtime.world().getAuthoritativeWorldTick()
   +" docked="+player.docked()+" walletMilli="+player.walletMilliCredits()+" ownCargo="
   +runtime.freight().findFreighter(player.activeFleetId()).orElseThrow().cargoStorage().commodityMassByIdKg());
  if(!saved.equals(campaign.captureState()))throw new AssertionError("Audit mutated actual checkpoint");
  if(args.length>2 && args[2].equals("markets")) {
   var fleet=runtime.world().findFleet(player.activeFleetId()).orElseThrow();
   var knowledge=runtime.discoveryState().knowledgeFor(com.spacesim.world.Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID);
   for(var endpoint:runtime.infrastructure().endpoints()) {
    var known=knowledge.entries().stream().filter(k->k.object().objectId().equals(endpoint.stationId())&&k.knownLocation().isPresent()).findFirst();
    if(known.isEmpty())continue;
    var ref=campaign.pilotMarketReference(endpoint.stationId()).orElseThrow();
    var entity=runtime.world().findSession(ref.systemId()).orElseThrow().getEntityRegistry().require(ref.entityId());
    var faction=runtime.world().findFactionStableId(entity.getComponent(com.spacesim.components.FactionComponent.class).factionId).orElseThrow();
    var route=new com.spacesim.player.PlayerFleetRoutePlanner(runtime.world(),campaign.coordinator().content(),player)
     .plan(player.activeFleetId(),fleet.systemId(),endpoint.systemId());
    System.out.println("KNOWN MARKET id="+endpoint.stationId()+" system="+endpoint.systemId()+" archetype="+endpoint.stationArchetypeId()
     +" coordinate="+known.orElseThrow().knownLocation()+" handling="+endpoint.handlingCapability().supportedStorageClassIds()
     +" diagnosticWalletMilli="+entity.getComponent(com.spacesim.components.WalletComponent.class).getBalanceMilliCredits()
     +" operatorEconomy="+runtime.world().findFactionEconomicState(faction)+" personalRoute="+route);
   }
   if(!saved.equals(campaign.captureState()))throw new AssertionError("Market audit mutated actual checkpoint");
  }
  if(args.length>1&&virtual<Long.parseLong(args[1]))throw new AssertionError("Actual receipt threshold not reached");
  System.out.println("ACTUAL EXTRACTION SERIES AUDIT PASS; no acquisition or sale claimed");
 }
}
