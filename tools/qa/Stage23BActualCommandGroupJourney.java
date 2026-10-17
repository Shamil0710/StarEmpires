import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import java.nio.file.Path;
import java.util.List;

/** Actual player government -> legal registration -> Stage-21D group, without physical grants. */
public final class Stage23BActualCommandGroupJourney {
 public static void main(String[] args) throws Exception {
  Path input=Path.of(args[0]),output=Path.of(args[1]);
  if(input.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize()))throw new IllegalArgumentException("Preserve input");
  var saved=Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(input);
  var c=Stage228CampaignAuthority.restore(saved);
  if(!saved.equals(c.captureState()))throw new AssertionError("Non-exact government input");
  saved=null;
  var p=c.playerState().orElseThrow();var fleets=p.ownedFleetIds();long wallet=p.walletMilliCredits();
  long tick=c.coordinator().runtime().world().getAuthoritativeWorldTick();
  int hulls=c.coordinator().runtime().world().getFleetPlacements().size();
  if(p.factionContentId()==null) {
   var before=c.captureState();var foundation=c.previewPlayerFactionFoundation("faction.player","Содружество пилота");
   if(!foundation.allowed()||!before.equals(c.captureState()))throw new AssertionError("Ordinary foundation preview rejected or impure");
   c=c.submitPlayerFactionFoundation(foundation);
  }
  var before=c.captureState();var affiliation=c.previewPlayerAssetAffiliation();
  if(!affiliation.allowed()||!before.equals(c.captureState()))throw new AssertionError("Ordinary legal registration rejected or impure");
  c=c.submitPlayerFactionCommand(affiliation);
  before=null;affiliation=null;
  var fleet=c.playerState().orElseThrow().activeFleetId();
  var home=c.coordinator().runtime().world().findFleet(fleet).orElseThrow().systemId();
  var physical=c.coordinator().runtime().captureState();
  var group=c.previewPlayerCommandGroup("Группа шахтёра",List.of(fleet),home);
  if(!group.allowed()||!physical.equals(c.coordinator().runtime().captureState()))throw new AssertionError("Group preview rejected or changed assets");
  c=c.submitPlayerFactionCommand(group);
  if(!physical.equals(c.coordinator().runtime().captureState())||c.playerState().orElseThrow().walletMilliCredits()!=wallet
    ||!c.playerState().orElseThrow().ownedFleetIds().equals(fleets)||c.coordinator().runtime().world().getAuthoritativeWorldTick()!=tick
    ||c.coordinator().runtime().world().getFleetPlacements().size()!=hulls)throw new AssertionError("Government/group created money, movement or assets");
  int groups=c.coordinator().commands().groups().size();
  var complete=c.captureState();c=null;physical=null;group=null;
  Stage228GeneratedCampaignPersistenceCodec.write(output,complete);
  var restored=Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(output));
  if(!complete.equals(restored.captureState()))throw new AssertionError("Non-exact government/group native reload");
  System.out.println("ACTUAL COMMAND GROUP PASS tick="+tick+" walletMilli="+wallet+" fleet="+fleet+" home="+home
    +" groups="+groups+"; ordinary foundation, legal registration, pure group preview, exact reload; no crew/readiness/asset grants or combat claim");
 }
}
