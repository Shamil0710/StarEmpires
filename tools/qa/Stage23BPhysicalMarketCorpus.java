import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
/** Fixed seeds 1–16 for the explicitly funded global market opening; no substitutions or retries. */
public class Stage23BPhysicalMarketCorpus {
 public static void main(String[] args) {
  int failures=0;
  for(long seed=1;seed<=16;seed++){
   boolean generated=false;
   try{
    var campaign=Stage228CampaignAuthority.create(seed);generated=true;
    var before=campaign.captureState();var preview=campaign.previewIndependentPilotStart();
    if(!preview.allowed()||!before.equals(campaign.captureState()))throw new AssertionError("Opening preview not pure/admitted");
    var player=campaign.submitIndependentPilotStart(preview);
    if(player.walletMilliCredits()!=75_000_000L||player.ownedFleetIds().size()!=1||player.factionContentId()!=null)throw new AssertionError("Wrong existing reserve/personal payment");
    long capital=0;var runtime=campaign.coordinator().runtime();
    for(var endpoint:runtime.infrastructure().endpoints()){
     var ref=campaign.pilotMarketReference(endpoint.stationId()).orElseThrow();
     var entity=runtime.world().findSession(ref.systemId()).orElseThrow().getEntityRegistry().require(ref.entityId());
     if(!entity.getComponent(IdentityComponent.class).name.startsWith(Stage228CampaignAuthority.PILOT_MARKET_V2_IDENTITY_PREFIX)
       ||entity.getComponent(InventoryComponent.class)!=null)throw new AssertionError("Wrong policy or duplicate inventory");
     long money=entity.getComponent(WalletComponent.class).getBalanceMilliCredits();if(money!=10_000_000L)throw new AssertionError("Wrong finite market funding");capital+=money;
    }
    var saved=campaign.captureState();var restored=Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(Stage228GeneratedCampaignPersistenceCodec.encode(saved)));
    if(!saved.equals(restored.captureState())||restored.canStartIndependentPilot())throw new AssertionError("Restore changed or recommissioned the world");
    System.out.println("{\"seed\":"+seed+",\"result\":\"PASS\",\"markets\":"+runtime.infrastructure().endpoints().size()+",\"marketWorkingCapitalMilliCredits\":"+capital+"}");
   }catch(RuntimeException|AssertionError exception){failures++;System.out.println("{\"seed\":"+seed+",\"result\":\""+(generated?"OPENING_FAIL":"GENERATION_REJECTED")+"\",\"exception\":\""+exception.getClass().getSimpleName()+"\"}");exception.printStackTrace(System.err);}
  }
  if(failures!=0)throw new AssertionError("Fixed corpus non-passes: "+failures);
 }
}
