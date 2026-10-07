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
 private static final long MAX_TICKS=Long.getLong("stage23b.journey.maxTicks",Long.MAX_VALUE);
 private static final long MAX_FLIGHT_TICKS=Long.getLong("stage23b.journey.maxFlightTicks",20_000_000L);
 private static final long CHECKPOINT_INTERVAL=Long.getLong("stage23b.journey.checkpointInterval",100_000L);
 private static long startedTick;
 private static boolean budgetActive;
 public static void main(String[] args) throws Exception {
  if(MAX_TICKS<=0 || MAX_FLIGHT_TICKS<=0 || CHECKPOINT_INTERVAL<=0)throw new IllegalArgumentException("Probe tick budgets and checkpoint interval must be positive");
  try { journey(); }
  catch(ProbePaused paused){System.out.println("JOURNEY PAUSED tick="+world().getAuthoritativeWorldTick()+" checkpoint="+CHECKPOINT+"; ordinary mining/sale is not yet proven");}
  finally {if(campaign!=null){campaign.coordinator().setPaused(true);Stage228GeneratedCampaignPersistenceCodec.write(CHECKPOINT,campaign.captureState());}}
 }
 private static void journey() throws Exception {
  if(java.nio.file.Files.exists(CHECKPOINT)) {
   var loaded=Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(CHECKPOINT);
   campaign=Stage228CampaignAuthority.restore(loaded);
   if(!loaded.equals(campaign.captureState()))throw new AssertionError("Non-exact resumed journey checkpoint");
   System.out.println("JOURNEY RESUMED tick="+world().getAuthoritativeWorldTick()+" jump="+world().findFleetJump(fleetId()));
  }
  else {
   campaign=Stage228CampaignAuthority.create(Long.getLong("stage23b.journey.seed",1L)); campaign.submitIndependentPilotStart(campaign.previewIndependentPilotStart());
   String startingDock="hub."+fleet().systemId().value();
   action("DOCK",startingDock,"",0); tick(); action("BUY",startingDock,"commodity.material.purified_water",10);
   action("UNDOCK","","",0);
  }
  campaign.coordinator().setTimeScale(1d);
  startedTick=world().getAuthoritativeWorldTick();
  budgetActive=true;
  var miner=campaign.coordinator().runtime().freight().capture().freighters().stream()
   .filter(f->f.fitId().equals(com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader.UNION_MINING_FREIGHT_STRATEGIC_FIT)).findFirst().orElseThrow();
  String miningDock="hub."+miner.currentSystemId().value();
  var original=miner;
  if(!campaign.playerState().orElseThrow().ownedFleetIds().contains(miner.fleetId())) {
   travel(miner.currentSystemId());
   System.out.println("SELLER ARRIVAL "+physical()+" wallet="+campaign.playerState().orElseThrow().walletMilliCredits());
   fly(campaign.coordinator().runtime().infrastructure().endpoint(miningDock).position(),500);
   if(!campaign.playerState().orElseThrow().docked())action("DOCK",miningDock,"",0);
   long walletBefore=campaign.playerState().orElseThrow().walletMilliCredits();
   long treasuryBefore=world().findFactionEconomicState(miner.stableFactionId()).orElseThrow().treasuryMilliCredits();
   long hullsBefore=world().getFleetPlacements().size();
   long paid=action("PURCHASE",Long.toString(miner.fleetId().value()),"",0);
   if(paid>=0 || campaign.playerState().orElseThrow().walletMilliCredits()!=walletBefore+paid
       || world().findFactionEconomicState(miner.stableFactionId()).orElseThrow().treasuryMilliCredits()!=treasuryBefore-paid
       || world().getFleetPlacements().size()!=hullsBefore)throw new AssertionError("Non-conserved existing miner purchase");
  }
  if(!original.equals(campaign.coordinator().runtime().freight().findFreighter(miner.fleetId()).orElseThrow())) throw new AssertionError("Purchase changed physical mining asset");
  if(!fleetId().equals(miner.fleetId())) {
   if(campaign.playerState().orElseThrow().docked())action("UNDOCK","","",0);
   action("SWITCH",Long.toString(miner.fleetId().value()),"",0);
  }
  System.out.println("MINER PURCHASED "+miner.fleetId()+" wallet="+campaign.playerState().orElseThrow().walletMilliCredits());
  var activeMining=campaign.coordinator().runtime().freight().capture().personalMiningOrders().stream().filter(o->o.fleetId().equals(fleetId())).findFirst();
  boolean hasOre=hasMinedOre();
  if(!hasOre || activeMining.isPresent()) {
   if(campaign.playerState().orElseThrow().docked())action("UNDOCK","","",0);
  var source=campaign.coordinator().runtime().industry().sourceOutposts().sources().sources().stream()
   .filter(s->s.systemId().equals(fleet().systemId()) && s.sourceState().environment()==com.spacesim.content.Stage18ExtractionCatalog.ExtractionEnvironment.FREE_BODY)
   .filter(s->activeMining.isEmpty() || activeMining.orElseThrow().sourceId().equals(s.sourceId()))
   .min(Comparator.comparingDouble(s->physical().position().distanceTo(s.position()))).orElseThrow();
  fly(source.position(),500);
  if(com.spacesim.ui.GeneratedCampaignMiningUi.rows(campaign).stream().noneMatch(row->row.selection().stableId().equals("pilot-mining|"+source.sourceId())))
   throw new AssertionError("Actual source contact is not reachable through production mining UI");
  double sourceBefore=source.sourceState().remainingAccessibleMassKg();
  if(activeMining.isEmpty())action("START_MINING",source.sourceId(),"",1);
  for(int i=0;i<100 && !hasMinedOre();i++)tick();
  if(campaign.coordinator().runtime().freight().capture().personalMiningOrders().stream().anyMatch(o->o.fleetId().equals(fleetId())))action("STOP_MINING","","",0);
  if(!hasMinedOre() || (!hasOre && source.sourceState().remainingAccessibleMassKg()>=sourceBefore))throw new AssertionError("No physical finite-source mining result");
  }
  System.out.println("MINED cargo="+campaign.coordinator().runtime().freight().findFreighter(fleet().id()).orElseThrow().cargoStorage().commodityMassByIdKg());
  var saved=campaign.captureState();
  campaign=Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(Stage228GeneratedCampaignPersistenceCodec.encode(saved)));
  if(!saved.equals(campaign.captureState()))throw new AssertionError("Non-exact ordinary save/load");
  System.out.println("JOURNEY CHECKPOINT PASS tick="+world().getAuthoritativeWorldTick());
  Stage228GeneratedCampaignPersistenceCodec.write(CHECKPOINT,saved);
  fly(campaign.coordinator().runtime().infrastructure().endpoint(miningDock).position(),500);
  if(!campaign.playerState().orElseThrow().docked())action("DOCK",miningDock,"",0);tick();
  var cargo=campaign.coordinator().runtime().freight().findFreighter(fleetId()).orElseThrow().cargoStorage();
  var ore=cargo.commodityMassByIdKg().entrySet().stream().filter(e->e.getKey().startsWith("commodity.feedstock.") && e.getValue()>=1).findFirst().orElseThrow();
  int quantity=(int)Math.min(1000,Math.floor(ore.getValue()));
  double beforeCargo=cargo.commodityMassByIdKg().getOrDefault(ore.getKey(),0d);
  double beforeStock=campaign.coordinator().runtime().infrastructure().endpoint(miningDock).storage().commodityMassKg(ore.getKey());
  long beforeMoney=campaign.playerState().orElseThrow().walletMilliCredits();
  var sell=campaign.previewPilotAction("SELL",miningDock,ore.getKey(),quantity);
  if(!sell.allowed())throw new AssertionError("Ordinary mined cargo cannot be sold at its actual market");
  campaign.submitPilotAction(sell);
  double remainingCargo=campaign.coordinator().runtime().freight().findFreighter(fleetId()).orElseThrow().cargoStorage().commodityMassByIdKg().getOrDefault(ore.getKey(),0d);
  if(Math.abs(remainingCargo-(beforeCargo-quantity))>1e-8
      ||Math.abs(campaign.coordinator().runtime().infrastructure().endpoint(miningDock).storage().commodityMassKg(ore.getKey())-(beforeStock+quantity))>1e-8
      ||campaign.playerState().orElseThrow().walletMilliCredits()!=beforeMoney+sell.walletChangeMilliCredits()
      ||sell.walletChangeMilliCredits()<=0)throw new AssertionError("Non-conserved ordinary ore sale");
  var completed=campaign.captureState();
  var restored=Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(Stage228GeneratedCampaignPersistenceCodec.encode(completed)));
  if(!completed.equals(restored.captureState()))throw new AssertionError("Non-exact sale checkpoint");
  Stage228GeneratedCampaignPersistenceCodec.write(CHECKPOINT.resolveSibling(CHECKPOINT.getFileName()+".sale.s25"),completed);
  System.out.println("ORDINARY MINING SALE PASS quantity="+quantity+" commodity="+ore.getKey()+" incomeMilliCredits="+sell.walletChangeMilliCredits()+" tick="+world().getAuthoritativeWorldTick());
 }
 private static WorldSimulation world(){return campaign.coordinator().runtime().world();}
 private static FleetPlacementState fleet(){return world().findFleet(campaign.playerState().orElseThrow().activeFleetId()).orElseThrow();}
 private static LocalPhysicalKinematics physical(){var f=fleet();return campaign.coordinator().runtime().arrival().materialization(f.systemId()).physicalState(f.localEntityId()).orElseThrow();}
 private static boolean hasMinedOre(){return campaign.coordinator().runtime().freight().findFreighter(fleetId()).orElseThrow().cargoStorage().commodityMassByIdKg().entrySet().stream()
   .anyMatch(e->e.getKey().startsWith("commodity.feedstock.") && e.getValue()>=1);}
 private static long action(String verb,String target,String subject,int amount){
  var p=campaign.previewPilotAction(verb,target,subject,amount);
  if(!p.allowed())throw new IllegalStateException("Rejected "+verb+" target="+target+" at tick="+world().getAuthoritativeWorldTick());
  campaign.submitPilotAction(p); System.out.println("ACTION "+verb+" "+target+" at tick="+world().getAuthoritativeWorldTick());
  return p.walletChangeMilliCredits();
 }
 private static void tick(){
  if(budgetActive && world().getAuthoritativeWorldTick()-startedTick>=MAX_TICKS)throw new ProbePaused();
  campaign.coordinator().setPaused(false);campaign.advanceFrame(campaign.coordinator().session().fixedStepSeconds());campaign.coordinator().setPaused(true);
  if(world().getAuthoritativeWorldTick()%10_000==0)System.out.println("JOURNEY PROGRESS tick="+world().getAuthoritativeWorldTick()+" jump="+world().findFleetJump(fleetId()));
  if(world().getAuthoritativeWorldTick()%CHECKPOINT_INTERVAL==0)try{Stage228GeneratedCampaignPersistenceCodec.write(CHECKPOINT,campaign.captureState());}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
 }
 private static final class ProbePaused extends RuntimeException { }
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
  var initialDisplacement=initial.position().displacementTo(target);
  double initialDistance=Math.hypot(initialDisplacement.deltaXM(),initialDisplacement.deltaYM());
  double ux=initialDistance>0?initialDisplacement.deltaXM()/initialDistance:0;
  double uy=initialDistance>0?initialDisplacement.deltaYM()/initialDistance:0;
  var plan=ENGINEERING.planDeltaV(fitted,Math.hypot(ux*cruise-initial.velocityXMps(),uy*cruise-initial.velocityYMps())+cruise);
  while(!plan.feasible()){
   cruise*=.5;
   if(cruise<.1)throw new IllegalStateException("No safe finite flight/braking plan from checkpoint; preserve it and use a fresh journey checkpoint with a lower cruise limit");
   plan=ENGINEERING.planDeltaV(fitted,Math.hypot(ux*cruise-initial.velocityXMps(),uy*cruise-initial.velocityYMps())+cruise);
  }
  double brakingAcceleration=.25*plan.deliveredDeltaVMps()/plan.burnSeconds();
  if(!Double.isFinite(brakingAcceleration) || brakingAcceleration<=0)throw new IllegalStateException("No finite sustainable braking capability");
  System.out.println("FLIGHT PLAN distance="+initialDistance+" cruise="+cruise+" deltaV="+plan.requiredDeltaVMps()+" burnSeconds="+plan.burnSeconds()+" conservativeBrakingAcceleration="+brakingAcceleration);
  for(long i=0;i<MAX_FLIGHT_TICKS;i++){
   var state=physical();var d=state.position().displacementTo(target);double distance=Math.hypot(d.deltaXM(),d.deltaYM());
   double speed=Math.hypot(state.velocityXMps(),state.velocityYMps());
   if(distance<=radius && speed<=0.01){campaign.setPilotThrust(0,0,false);System.out.println("FLIGHT ARRIVAL distance="+distance+" ticks="+i);return;}
   var f=fleet();var component=world().findSession(f.systemId()).orElseThrow().getEntityRegistry().require(f.localEntityId()).getComponent(EngineeringComponent.class);
   var engineering=ENGINEERING.derive(component);double acceleration=Math.min(engineering.accelerationMps2(),brakingAcceleration);
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
