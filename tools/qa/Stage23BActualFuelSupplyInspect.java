import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.world.*;
import java.nio.file.Path;
import java.util.List;

/** Read-only fuel audit. Remote stocks are diagnostic, never navigation or permission to trade. */
public final class Stage23BActualFuelSupplyInspect {
 public static void main(String[] args)throws Exception{
  var saved=Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(Path.of(args[0]));
  var c=Stage228CampaignAuthority.restore(saved);
  if(!saved.equals(c.captureState()))throw new AssertionError("Non-exact fuel audit input");
  var p=c.playerState().orElseThrow();var r=c.coordinator().runtime();var w=r.world();
  var f=w.findFleet(p.activeFleetId()).orElseThrow();
  var physical=r.arrival().materialization(f.systemId()).physicalState(f.localEntityId()).orElseThrow();
  var fitted=w.findSession(f.systemId()).orElseThrow().getEntityRegistry().require(f.localEntityId()).getComponent(com.spacesim.components.EngineeringComponent.class);
  System.out.println("ACTUAL ENGINEERING "+new com.spacesim.ship.ProductionEngineeringRuntimeResolver().derive(fitted).reactionMassKg()+" kg reaction mass; own stations="+p.ownedStations());
  System.out.println("ACTUAL OWN CARGO "+r.freight().findFreighter(p.activeFleetId()).map(s->s.cargoStorage().commodityMassByIdKg()));
  System.out.println("ACTUAL FUEL AUDIT wallet="+p.walletMilliCredits()+" fleet="+p.activeFleetId()+" physical="+physical+" dock="+p.dockedAt());
  for(var id:p.ownedFleetIds()){
   var owned=w.findFleet(id).orElseThrow();
   System.out.println("ACTUAL OWNED SHIP id="+id+" system="+owned.systemId()+" location="+owned.locationKind()
     +" physical="+(owned.locationKind()==FleetLocationKind.IN_SYSTEM?r.arrival().materialization(owned.systemId()).physicalState(owned.localEntityId()):"transit"));
  }
  var knowledge=r.discoveryState().knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID);
  for(var e:r.infrastructure().endpoints()){
   var known=knowledge.entries().stream().filter(k->k.object().objectId().equals(e.stationId())&&k.knownLocation().isPresent()).findFirst();
   double water=e.storage().commodityMassKg("commodity.material.purified_water");
   if(e.systemId().equals(f.systemId())){
    String price;try{price=Long.toString(c.pilotCommodityPrice(e.stationId(),"commodity.material.purified_water",true));}catch(IllegalArgumentException rejected){price="unavailable:"+rejected.getMessage();}
    System.out.println("LOCAL WATER station="+e.stationId()+" waterKg="+water+" personallyKnown="+known.isPresent()+" distanceM="+physical.position().distanceTo(e.position())+" priceMilli="+price);
   }
  }
  for(var k:knowledge.entries())if(k.object().resourceObject()&&k.knownLocation().isPresent())System.out.println("PERSONAL RAW SOURCE id="+k.object().objectId()+" system="+k.object().systemId()+" classification="+k.classificationId()+" position="+k.knownLocation());
  if(f.systemId().value()==2)System.out.println("PROJECTED FUEL JOURNEY "+w.planFleetPropellantJourney(p.activeFleetId(),List.of(new StarSystemId(2),new StarSystemId(7),new StarSystemId(8),new StarSystemId(3))));
  if(!saved.equals(c.captureState()))throw new AssertionError("Fuel audit mutated actual campaign");
  System.out.println("ACTUAL FUEL SUPPLY AUDIT PASS; no refueling or material acquisition claimed");
 }
}
