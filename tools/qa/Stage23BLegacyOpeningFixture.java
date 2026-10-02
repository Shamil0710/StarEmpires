import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
/** Run against the packaged 8444116 ancestor to reproduce the ordinary v1 historical checkpoint. */
public class Stage23BLegacyOpeningFixture {
 public static void main(String[] args) throws Exception {
  var campaign=Stage228CampaignAuthority.create(1);campaign.submitIndependentPilotStart(campaign.previewIndependentPilotStart());
  var runtime=campaign.coordinator().runtime();var own=runtime.world().findFleet(campaign.playerState().orElseThrow().activeFleetId()).orElseThrow();
  long markets=runtime.infrastructure().endpoints().stream().filter(e->campaign.pilotMarketReference(e.stationId()).isPresent()).count();
  long home=runtime.infrastructure().endpoints().stream().filter(e->e.systemId().equals(own.systemId())).count();
  if(markets!=home)throw new AssertionError("This is not the accepted home-only opening profile");
  for(var endpoint:runtime.infrastructure().endpoints()){
   var ref=campaign.pilotMarketReference(endpoint.stationId());if(ref.isEmpty())continue;
   var entity=runtime.world().findSession(ref.get().systemId()).orElseThrow().getEntityRegistry().require(ref.get().entityId());
   if(!entity.getComponent(com.spacesim.components.IdentityComponent.class).name.equals("Generated market "+endpoint.stationId()))throw new AssertionError("Not an unversioned v1 market");
  }
  byte[] data=Stage228GeneratedCampaignPersistenceCodec.encode(campaign.captureState());
  try(var out=new GZIPOutputStream(Files.newOutputStream(Path.of(args[0])))){out.write(data);}
  System.out.println("Historical v1 checkpoint: "+data.length+" bytes; markets="+markets+"; total endpoints="+runtime.infrastructure().endpoints().size());
 }
}
