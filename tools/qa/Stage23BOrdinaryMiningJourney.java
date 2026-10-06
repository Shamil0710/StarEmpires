import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.ship.ProductionEngineeringRuntimeResolver;
import com.spacesim.world.*;
import java.util.*;

/** Physical journey probe. Guidance chooses itinerary coordinates; it never changes world geometry or assets. */
public final class Stage23BOrdinaryMiningJourney {
 private static Stage228CampaignAuthority campaign;
 private static final ProductionEngineeringRuntimeResolver ENGINEERING=new ProductionEngineeringRuntimeResolver();
 private static final java.nio.file.Path CHECKPOINT=java.nio.file.Path.of(System.getProperty("stage23b.journey.checkpoint","target/stage23b-ordinary-mining-journey.s25"));
 private static final double CRUISE_LIMIT_MPS=Double.parseDouble(System.getProperty("stage23b.journey.cruiseMps","1500"));
 public static void main(String[] args) throws Exception {
  if(java.nio.file.Files.exists(CHECKPOINT)) campaign=Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(CHECKPOINT));
  else {
   campaign=Stage228CampaignAuthority.create(1); campaign.submitIndependentPilotStart(campaign.previewIndependentPilotStart());
   action("DOCK","hub.16","",0); tick(); action("BUY","hub.16","commodity.material.purified_water",10);
   action("UNDOCK","","",0);
  }
  travel(new StarSystemId(14));
  System.out.println("BETA ARRIVAL "+physical()+" wallet="+campaign.playerState().orElseThrow().walletMilliCredits());
  fly(campaign.coordinator().runtime().infrastructure().endpoint("hub.14").position(),500);
  if(!campaign.playerState().orElseThrow().docked())action("DOCK","hub.14","",0);
  System.out.println("BETA DOCK tick="+world().getAuthoritativeWorldTick());
  var miner=campaign.coordinator().runtime().freight().capture().freighters().stream()
   .filter(f->f.fitId().equals(com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader.UNION_MINING_FREIGHT_STRATEGIC_FIT)).findFirst().orElseThrow();
  var original=miner;
  if(!campaign.playerState().orElseThrow().ownedFleetIds().contains(miner.fleetId()))action("PURCHASE",Long.toString(miner.fleetId().value()),"",0);
  if(!original.equals(campaign.coordinator().runtime().freight().findFreighter(miner.fleetId()).orElseThrow())) throw new AssertionError("Purchase changed physical mining asset");
  action("UNDOCK","","",0); if(!fleetId().equals(miner.fleetId()))action("SWITCH",Long.toString(miner.fleetId().value()),"",0);
  System.out.println("MINER PURCHASED "+miner.fleetId()+" wallet="+campaign.playerState().orElseThrow().walletMilliCredits());
  var source=campaign.coordinator().runtime().industry().sourceOutposts().sources().sources().stream()
   .filter(s->s.systemId().equals(fleet().systemId()) && s.sourceState().environment()==com.spacesim.content.Stage18ExtractionCatalog.ExtractionEnvironment.FREE_BODY)
   .min(Comparator.comparingDouble(s->physical().position().distanceTo(s.position()))).orElseThrow();
  fly(source.position(),500);
  action("START_MINING",source.sourceId(),"",1);
  for(int i=0;i<100;i++)tick();
  action("STOP_MINING","","",0);
  System.out.println("MINED cargo="+campaign.coordinator().runtime().freight().findFreighter(fleet().id()).orElseThrow().cargoStorage().commodityMassByIdKg());
  var saved=campaign.captureState();
  campaign=Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(Stage228GeneratedCampaignPersistenceCodec.encode(saved)));
  if(!saved.equals(campaign.captureState()))throw new AssertionError("Non-exact ordinary save/load");
  System.out.println("JOURNEY CHECKPOINT PASS tick="+world().getAuthoritativeWorldTick());
  Stage228GeneratedCampaignPersistenceCodec.write(CHECKPOINT,saved);
  fly(campaign.coordinator().runtime().infrastructure().endpoint("hub.14").position(),500);
  action("DOCK","hub.14","",0);tick();
  var cargo=campaign.coordinator().runtime().freight().findFreighter(fleetId()).orElseThrow().cargoStorage();
  var ore=cargo.commodityMassByIdKg().entrySet().stream().filter(e->e.getValue()>=1).findFirst().orElseThrow();
  int quantity=(int)Math.min(1000,Math.floor(ore.getValue()));
  double beforeCargo=cargo.commodityMassByIdKg().getOrDefault(ore.getKey(),0d);
  double beforeStock=campaign.coordinator().runtime().infrastructure().endpoint("hub.14").storage().commodityMassKg(ore.getKey());
  long beforeMoney=campaign.playerState().orElseThrow().walletMilliCredits();
  var sell=campaign.previewPilotAction("SELL","hub.14",ore.getKey(),quantity);
  if(!sell.allowed())throw new AssertionError("Ordinary mined cargo cannot be sold at its actual market");
  campaign.submitPilotAction(sell);
  double remainingCargo=campaign.coordinator().runtime().freight().findFreighter(fleetId()).orElseThrow().cargoStorage().commodityMassByIdKg().getOrDefault(ore.getKey(),0d);
  if(Math.abs(remainingCargo-(beforeCargo-quantity))>1e-8
      ||Math.abs(campaign.coordinator().runtime().infrastructure().endpoint("hub.14").storage().commodityMassKg(ore.getKey())-(beforeStock+quantity))>1e-8
      ||campaign.playerState().orElseThrow().walletMilliCredits()!=beforeMoney+sell.walletChangeMilliCredits()
      ||sell.walletChangeMilliCredits()<=0)throw new AssertionError("Non-conserved ordinary ore sale");
  var completed=campaign.captureState();
  var restored=Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(Stage228GeneratedCampaignPersistenceCodec.encode(completed)));
  if(!completed.equals(restored.captureState()))throw new AssertionError("Non-exact sale checkpoint");
  Stage228GeneratedCampaignPersistenceCodec.write(java.nio.file.Path.of("target/stage23b-ordinary-mining-sale.s25"),completed);
  System.out.println("ORDINARY MINING SALE PASS quantity="+quantity+" commodity="+ore.getKey()+" incomeMilliCredits="+sell.walletChangeMilliCredits()+" tick="+world().getAuthoritativeWorldTick());
 }
 private static WorldSimulation world(){return campaign.coordinator().runtime().world();}
 private static FleetPlacementState fleet(){return world().findFleet(campaign.playerState().orElseThrow().activeFleetId()).orElseThrow();}
 private static LocalPhysicalKinematics physical(){var f=fleet();return campaign.coordinator().runtime().arrival().materialization(f.systemId()).physicalState(f.localEntityId()).orElseThrow();}
 private static void action(String verb,String target,String subject,int amount){
  var p=campaign.previewPilotAction(verb,target,subject,amount);
  if(!p.allowed())throw new IllegalStateException("Rejected "+verb+" target="+target+" at tick="+world().getAuthoritativeWorldTick());
  campaign.submitPilotAction(p); System.out.println("ACTION "+verb+" "+target+" at tick="+world().getAuthoritativeWorldTick());
 }
 private static void tick(){
  campaign.coordinator().setPaused(false);campaign.advanceFrame(campaign.coordinator().session().fixedStepSeconds());campaign.coordinator().setPaused(true);
  if(world().getAuthoritativeWorldTick()%20_000==0)try{Stage228GeneratedCampaignPersistenceCodec.write(CHECKPOINT,campaign.captureState());}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
 }
 private static void travel(StarSystemId destination){
  int resumedTicks=0;
  while(world().findFleetJump(fleetId()).isPresent()&&resumedTicks++<50_000_000)tick();
  if(world().findFleetJump(fleetId()).isPresent())throw new IllegalStateException("Resumed jump exceeded finite probe budget");
  var paths=new HashMap<StarSystemId,List<StarSystemId>>();var pending=new ArrayDeque<StarSystemId>();
  paths.put(fleet().systemId(),List.of(fleet().systemId()));pending.add(fleet().systemId());
  while(!pending.isEmpty()&&!paths.containsKey(destination)){var current=pending.remove();for(var next:world().getTopology().neighbors(current))if(!paths.containsKey(next)){
   var path=new ArrayList<>(paths.get(current));path.add(next);paths.put(next,path);pending.add(next);}}
  for(var hop:paths.get(destination).subList(1,paths.get(destination).size())){
   for(int wait=0;!world().previewFittedFleetJump(fleetId()).orElseThrow().allowed()&&wait<10_000;wait++)tick();
   if(world().findFleetJump(fleetId()).isEmpty())action("JUMP",Long.toString(hop.value()),"",0);
   int ticks=0;while(world().findFleetJump(fleetId()).isPresent()&&ticks++<50_000_000){tick();if(ticks%10000==0)System.out.println("JUMP PROGRESS "+hop+" ticks="+ticks+" world="+world().getAuthoritativeWorldTick()+" state="+world().findFleetJump(fleetId()));}
   if(world().findFleetJump(fleetId()).isPresent()||!fleet().systemId().equals(hop))throw new IllegalStateException("Travel stalled "+hop+" world="+world().getAuthoritativeWorldTick()+" state="+world().findFleetJump(fleetId()));
   System.out.println("ARRIVAL "+hop+" tick="+world().getAuthoritativeWorldTick());
  }
 }
 private static FleetId fleetId(){return campaign.playerState().orElseThrow().activeFleetId();}
 private static void fly(LocalPhysicalPosition target,double radius){
  if(!Double.isFinite(CRUISE_LIMIT_MPS)||CRUISE_LIMIT_MPS<=0)throw new IllegalArgumentException("Cruise limit must be positive and finite");
  var initial=physical();var placement=fleet();
  if(initial.position().distanceTo(target)<=radius && Math.hypot(initial.velocityXMps(),initial.velocityYMps())<=.01){campaign.setPilotThrust(0,0,false);return;}
  var fitted=world().findSession(placement.systemId()).orElseThrow().getEntityRegistry().require(placement.localEntityId()).getComponent(EngineeringComponent.class);
  double cruise=CRUISE_LIMIT_MPS;
  double initialSpeed=Math.hypot(initial.velocityXMps(),initial.velocityYMps());
  while(!ENGINEERING.planDeltaV(fitted,initialSpeed+2*cruise).feasible()){
   cruise*=.5;
   if(cruise<.1)throw new IllegalStateException("No safe finite flight/braking plan from checkpoint; preserve it and use a fresh journey checkpoint with a lower cruise limit");
  }
  for(int i=0;i<2_000_000;i++){
   var state=physical();var d=state.position().displacementTo(target);double distance=Math.hypot(d.deltaXM(),d.deltaYM());
   double speed=Math.hypot(state.velocityXMps(),state.velocityYMps());
   if(distance<=radius && speed<=0.01){campaign.setPilotThrust(0,0,false);System.out.println("FLIGHT ARRIVAL distance="+distance+" ticks="+i);return;}
   var f=fleet();var component=world().findSession(f.systemId()).orElseThrow().getEntityRegistry().require(f.localEntityId()).getComponent(EngineeringComponent.class);
   var engineering=ENGINEERING.derive(component);double acceleration=engineering.accelerationMps2();
   double approach=Math.min(cruise,Math.sqrt(acceleration*Math.max(0,distance-radius*0.5)));
   double dx=(distance>0?d.deltaXM()/distance*approach:0)-state.velocityXMps();
   double dy=(distance>0?d.deltaYM()/distance*approach:0)-state.velocityYMps();
   double dv=Math.hypot(dx,dy);double throttle=ENGINEERING.throttleForDeltaV(component,dv,campaign.coordinator().session().fixedStepSeconds());
   campaign.setPilotThrust((float)(dv>0?dx/dv*throttle:0),(float)(dv>0?dy/dv*throttle:0),false);tick();
   if(i%10000==0)System.out.println("FLIGHT PROGRESS distance="+distance+" speed="+speed+" tick="+world().getAuthoritativeWorldTick());
   if(acceleration<=0 || engineering.reactionMassKg()<=0)throw new IllegalStateException("No finite flight capability");
  }
  throw new IllegalStateException("Flight stalled target="+target);
 }
}
