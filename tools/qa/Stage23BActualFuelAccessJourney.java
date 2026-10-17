import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.ui.GeneratedCampaignStationMapUi;
import com.spacesim.world.Stage21HPlayerMissionAuthority;
import java.nio.file.Path;

/** Paid local navigation acquisition; never reads unpaid card coordinates to choose an offer. */
public final class Stage23BActualFuelAccessJourney {
 public static void main(String[] args)throws Exception{
  Path input=Path.of(args[0]),output=Path.of(args[1]);
  if(input.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize()))throw new IllegalArgumentException("Preserve input");
  var saved=Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(input);
  var c=Stage228CampaignAuthority.restore(saved);
  if(!saved.equals(c.captureState()))throw new AssertionError("Non-exact input");
  var r=c.coordinator().runtime();var w=r.world();var p=c.playerState().orElseThrow();
  long tick=w.getAuthoritativeWorldTick(),wallet=p.walletMilliCredits();
  var industrial=r.captureState().campaign().industrialState();
  if(!p.docked()){
   var preview=c.previewPilotAction("DOCK","industry.2","",0);
   if(!preview.allowed())throw new IllegalStateException("Actual own berth unavailable");
   c.submitPilotAction(preview);
  }
  int count=0;boolean found=false;
  while(count<20){
   var knowledge=r.discoveryState().knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID);
   var active=w.findFleet(p.activeFleetId()).orElseThrow();
   var local=knowledge.entries().stream().filter(k->k.knownLocation().isPresent()&&k.object().systemId().equals(active.systemId()))
     .filter(k->r.infrastructure().endpoints().stream().anyMatch(e->e.stationId().equals(k.object().objectId())&&e.handlingCapability().supportedStorageClassIds().contains("storage.liquid_tank"))).findFirst();
   if(local.isPresent()){found=true;System.out.println("PERSONAL LOCAL LIQUID DOCK id="+local.orElseThrow().object().objectId()+" coordinate="+local.orElseThrow().knownLocation());break;}
   var offers=GeneratedCampaignStationMapUi.rows(c);
   if(offers.isEmpty())break;
   var card=offers.get(0);if(card.focusSystem()!=null||card.focusFleet()!=0)throw new AssertionError("Unpaid card navigation");
   String[] parts=card.selection().stableId().split("\\|",3);
   var before=c.captureState();var preview=c.previewPilotAction("BUY_STATION_MAP",parts[1],parts[2],1);
   if(!preview.allowed()||!before.equals(c.captureState()))throw new AssertionError("Impure paid station preview");
   c.submitPilotAction(preview);count++;
   System.out.println("ACTUAL STATION CARD PURCHASE publicLabel="+card.name()+" costMilli="+-preview.walletChangeMilliCredits());
  }
  if(tick!=w.getAuthoritativeWorldTick()||!industrial.equals(r.captureState().campaign().industrialState())
      ||wallet-c.playerState().orElseThrow().walletMilliCredits()!=count*100000L||!p.ownedFleetIds().equals(c.playerState().orElseThrow().ownedFleetIds()))throw new AssertionError("Navigation granted physical assets or stock");
  var complete=c.captureState();c=null;saved=null;
  Stage228GeneratedCampaignPersistenceCodec.write(output,complete);
  var restored=Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(output));
  if(!complete.equals(restored.captureState()))throw new AssertionError("Non-exact fuel access reload");
  System.out.println("ACTUAL FUEL NAVIGATION "+(found?"PASS":"PARTIAL")+" purchases="+count+" wallet="+complete.playerState().walletMilliCredits()+"; refueling and flight not claimed");
 }
}
