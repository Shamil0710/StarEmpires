import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.ui.GeneratedCampaignStationMapUi;
import com.spacesim.world.Stage21HPlayerMissionAuthority;
import java.nio.file.Path;

/** Actual dispatcher contact and paid archive cards; destination is selected only after receipt. */
public final class Stage23BActualLocalIndustrialMapJourney {
 public static void main(String[] args) throws Exception {
  var input=Path.of(args[0]);var output=Path.of(args[1]);
  if(input.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize()))throw new IllegalArgumentException("Preserve input");
  var saved=Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(input);
  var c=Stage228CampaignAuthority.restore(saved);
  if(!saved.equals(c.captureState()))throw new AssertionError("Non-exact local map input");
  saved=null;
  var r=c.coordinator().runtime();var w=r.world();var p=c.playerState().orElseThrow();
  if(!p.docked())throw new IllegalStateException("Actual local dispatcher berth required");
  var f=w.findFleet(p.activeFleetId()).orElseThrow();
  var industrial=r.captureState().campaign().industrialState();
  long tick=w.getAuthoritativeWorldTick();int purchases=0;String destination="";
  for(int attempts=0;attempts<20;attempts++) {
   var knowledge=r.discoveryState().knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID);
   var target=knowledge.entries().stream().filter(k->k.knownLocation().isPresent())
     .filter(k->!k.object().resourceObject())
     .filter(k->{try {long price=c.stationSalePriceMilliCredits(k.object().objectId());return price>0&&price<=c.playerState().orElseThrow().walletMilliCredits();}
       catch(IllegalArgumentException notStation){return false;}}).findFirst();
   if(target.isPresent()){
    var known=target.orElseThrow();destination=known.object().objectId();
    var route=new com.spacesim.player.PlayerFleetRoutePlanner(w,c.coordinator().content(),c.playerState().orElseThrow())
      .plan(p.activeFleetId(),f.systemId(),known.object().systemId());
    System.out.println("PERSONAL AFFORDABLE INDUSTRIAL DESTINATION station="+destination+" system="+known.object().systemId()
      +" knownPosition="+known.knownLocation()+" fuelSafeKnownRoute="+route.map(v->v.path().toString()).orElse("UNAVAILABLE"));
    break;
   }
   var cards=GeneratedCampaignStationMapUi.rows(c);if(cards.isEmpty())break;
   var card=cards.get(0);if(card.focusSystem()!=null||card.focusFleet()!=0)throw new AssertionError("Unpaid map coordinates leaked");
   var fields=card.selection().stableId().split("\\|",3);
   var before=c.captureState();var preview=c.previewPilotAction("BUY_STATION_MAP",fields[1],fields[2],1);
   if(!preview.allowed()||!before.equals(c.captureState()))throw new AssertionError("Actual paid card unavailable or impure");
   c.submitPilotAction(preview);purchases++;
   System.out.println("ACTUAL LOCAL MAP PURCHASE label="+card.name()+" costMilliCredits="+-preview.walletChangeMilliCredits());
  }
  if(w.getAuthoritativeWorldTick()!=tick||!industrial.equals(r.captureState().campaign().industrialState())
    ||p.walletMilliCredits()-c.playerState().orElseThrow().walletMilliCredits()!=purchases*100000L
    ||!p.ownedFleetIds().equals(c.playerState().orElseThrow().ownedFleetIds()))throw new AssertionError("Map granted physical assets or work");
  var complete=c.captureState();
  Stage228GeneratedCampaignPersistenceCodec.write(output,complete);
  var restored=Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(output));
  if(!complete.equals(restored.captureState()))throw new AssertionError("Non-exact paid map reload");
  System.out.println("ACTUAL LOCAL INDUSTRIAL NAVIGATION "+(destination.isEmpty()?"PARTIAL":"PASS")+" station="+destination
    +" purchases="+purchases+" walletMilliCredits="+complete.playerState().walletMilliCredits()+"; existing personal archive admitted across systems; flight and station acquisition not claimed");
 }
}
