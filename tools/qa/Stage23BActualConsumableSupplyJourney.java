import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.Stage22ShipConsumableCatalogLoader;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.ship.ProductionEngineeringRuntimeResolver;
import com.spacesim.ui.GeneratedCampaignSupplyUi;
import java.nio.file.Path;

/** Real saved berth, finite paid water purchase and installed tank replenishment, without fixtures. */
public final class Stage23BActualConsumableSupplyJourney {
 public static void main(String[] args) throws Exception {
  var input=Path.of(args[0]); var output=Path.of(args[1]); String station=args[2];
  if(input.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize()))
   throw new IllegalArgumentException("Preserve the input checkpoint");
  int kg=Integer.getInteger("stage23b.supply.kg",10);
  if(kg<=0)throw new IllegalArgumentException("Positive paid mass required");
  var saved=Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(input);
  var c=Stage228CampaignAuthority.restore(saved);
  if(!saved.equals(c.captureState()))throw new AssertionError("Non-exact supply input");
  saved=null;
  var p=c.playerState().orElseThrow(); var r=c.coordinator().runtime(); var w=r.world();
  if(!p.docked() || !c.pilotMarketReference(station).orElseThrow().equals(p.dockedAt()))
   throw new IllegalStateException("Actual saved berth required");
  var fleet=w.findFleet(p.activeFleetId()).orElseThrow();
  var engineering=w.findSession(fleet.systemId()).orElseThrow().getEntityRegistry().require(fleet.localEntityId())
    .getComponent(EngineeringComponent.class);
  var resolver=new ProductionEngineeringRuntimeResolver();
  double fuel=resolver.derive(engineering).reactionMassKg();
  var binding=Stage22ShipConsumableCatalogLoader.loadDefault().getBindings().stream()
    .filter(b->b.interfaceKind()==InterfaceKind.REACTION_MASS)
    .filter(b->engineering.fit.installedModules().stream().anyMatch(m->m.moduleId().equals(b.moduleId())))
    .findFirst().orElseThrow();
  var mount=engineering.fit.installedModules().stream().filter(m->m.moduleId().equals(binding.moduleId()))
    .findFirst().orElseThrow().mountId();
  if(GeneratedCampaignSupplyUi.rows(c).stream().noneMatch(row->row.selection().stableId()
    .equals("pilot-supply|"+binding.id()+"|"+mount)))throw new AssertionError("Supply UI interface unavailable");
  long tick=w.getAuthoritativeWorldTick();
  // The ore sale consumed the saved interval's handling allowance. Open the next real interval.
  c.coordinator().setTimeScale(1); c.coordinator().setPaused(false);
  c.advanceFrame(c.coordinator().session().fixedStepSeconds()); c.coordinator().setPaused(true);
  double stock=r.infrastructure().endpoint(station).storage().commodityMassKg(binding.commodityId());
  double cargo=r.freight().cargoHoldSnapshot(p.activeFleetId()).commodityMassByIdKg().getOrDefault(binding.commodityId(),0d);
  long wallet=p.walletMilliCredits();
  if(!Boolean.getBoolean("stage23b.supply.fromCargo")) {
   var baseline=c.captureState(); var purchase=c.previewPilotAction("BUY_CONSUMABLE",binding.id(),mount,kg);
   if(!purchase.allowed() || !baseline.equals(c.captureState()))throw new AssertionError("Paid station-to-tank preview unavailable or impure");
   long paid=-purchase.walletChangeMilliCredits(); c.submitPilotAction(purchase); purchase=null; baseline=null;
   double replenished=resolver.derive(engineering).reactionMassKg();
   if(paid<=0 || c.playerState().orElseThrow().walletMilliCredits()!=wallet-paid
     ||r.infrastructure().endpoint(station).storage().commodityMassKg(binding.commodityId())!=stock-kg
     ||Math.abs(replenished-fuel-kg)>1e-7
     ||r.freight().cargoHoldSnapshot(p.activeFleetId()).commodityMassByIdKg().getOrDefault(binding.commodityId(),0d)!=cargo
     ||w.getAuthoritativeWorldTick()!=tick+1)throw new AssertionError("Non-conserved direct physical service");
   var complete=c.captureState(); c=null;
   Stage228GeneratedCampaignPersistenceCodec.write(output,complete);
   var restored=Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(output));
   if(!complete.equals(restored.captureState()))throw new AssertionError("Non-exact directly supplied checkpoint");
   System.out.println("ACTUAL DIRECT CONSUMABLE SUPPLY PASS kg="+kg+" paidMilliCredits="+paid+" reactionMassKg="+replenished
     +" tick="+(tick+1)+"; actual berth, paid finite station stock, installed tank, no cargo capacity grant, exact reload; route sufficiency not claimed");
   return;
  }
  var before=c.captureState(); var buy=c.previewPilotAction("BUY",station,binding.commodityId(),kg);
  if(!buy.allowed()) {
   var diagnostic=Stage228CampaignAuthority.restore(before);
   var execute=Stage228CampaignAuthority.class.getDeclaredMethod("executePilotAction",String.class,String.class,String.class,int.class);
   execute.setAccessible(true);
   try { execute.invoke(diagnostic,"BUY",station,binding.commodityId(),kg); diagnostic.captureState(); }
   catch(java.lang.reflect.InvocationTargetException rejected) {
    throw new IllegalStateException("Isolated diagnosis of refused purchase; original input unchanged",rejected.getCause());
   }
  }
  if(!buy.allowed() || !before.equals(c.captureState()))throw new AssertionError("Paid physical purchase unavailable or impure");
  long paid=-buy.walletChangeMilliCredits(); c.submitPilotAction(buy); buy=null; before=null;
  if(paid<=0 || c.playerState().orElseThrow().walletMilliCredits()!=wallet-paid
    ||r.infrastructure().endpoint(station).storage().commodityMassKg(binding.commodityId())!=stock-kg)
    throw new AssertionError("Non-conserved paid water purchase");
  c.coordinator().setTimeScale(1); c.coordinator().setPaused(false);
  c.advanceFrame(c.coordinator().session().fixedStepSeconds()); c.coordinator().setPaused(true);
  before=c.captureState(); var load=c.previewPilotAction("LOAD_CONSUMABLE",binding.id(),mount,kg);
  if(!load.allowed() || !before.equals(c.captureState()))throw new AssertionError("Installed tank loading unavailable or impure");
  c.submitPilotAction(load); load=null; before=null;
  double replenished=resolver.derive(engineering).reactionMassKg();
  if(Math.abs(replenished-fuel-kg)>1e-7
    ||r.freight().cargoHoldSnapshot(p.activeFleetId()).commodityMassByIdKg().getOrDefault(binding.commodityId(),0d)!=cargo
    ||c.playerState().orElseThrow().walletMilliCredits()!=wallet-paid || w.getAuthoritativeWorldTick()!=tick+2)
    throw new AssertionError("Supply duplicated mass, payment or time");
  var complete=c.captureState(); c=null;
  Stage228GeneratedCampaignPersistenceCodec.write(output,complete);
  var restored=Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(output));
  if(!complete.equals(restored.captureState()))throw new AssertionError("Non-exact supplied checkpoint");
  System.out.println("ACTUAL CONSUMABLE SUPPLY PASS kg="+kg+" paidMilliCredits="+paid+" reactionMassKg="+replenished
    +" tick="+(tick+2)+"; actual berth, paid finite stock, installed interface and exact reload; no route sufficiency claim");
 }
}
