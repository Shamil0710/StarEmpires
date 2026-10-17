import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import java.nio.file.Files;
import java.nio.file.Path;

/** Finite ordinary sale of existing mined cargo at the saved berth; no cash, stock or travel fixtures. */
public final class Stage23BActualFiniteOreSales {
    public static void main(String[] args) throws Exception {
        var input = Path.of(args[0]); var output = Path.of(args[1]); String station = args[2];
        if (Files.exists(output) || input.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize()))
            throw new IllegalArgumentException("Preserve existing checkpoint files");
        int limit = Integer.getInteger("stage23b.sale.maxBatches", 100);
        if (limit <= 0 || limit > 1000) throw new IllegalArgumentException("Bounded positive sale attempt budget required");
        var c = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(input));
        var p = c.playerState().orElseThrow(); var r = c.coordinator().runtime();
        if (!p.docked() || !c.pilotMarketReference(station).orElseThrow().equals(p.dockedAt()))
            throw new IllegalStateException("Actual saved physical berth required");
        var fleet = p.activeFleetId(); String commodity = "commodity.feedstock.metallic_ore";
        long sold = 0, income = 0; String stop = "ATTEMPT_BUDGET";
        for (int attempt = 0; attempt < limit; attempt++) {
            c.coordinator().setTimeScale(1); c.coordinator().setPaused(false);
            c.advanceFrame(c.coordinator().session().fixedStepSeconds()); c.coordinator().setPaused(true);
            double cargo = r.freight().cargoHoldSnapshot(fleet).commodityMassByIdKg().getOrDefault(commodity, 0d);
            if (cargo < 1) { stop = "CARGO_EXHAUSTED"; break; }
            var endpoint = r.infrastructure().endpoint(station);
            var ref = c.pilotMarketReference(station).orElseThrow();
            var buyer = r.world().findSession(ref.systemId()).orElseThrow().getEntityRegistry().require(ref.entityId())
                    .getComponent(com.spacesim.components.WalletComponent.class);
            long quote = c.pilotCommodityPrice(station, commodity, false);
            int kg = (int) Math.floor(Math.min(1000, Math.min(cargo, Math.min(buyer.getBalanceMilliCredits() / quote,
                    endpoint.handlingCapability().massRateKgPerSecond() * c.coordinator().session().fixedStepSeconds()))));
            if (kg <= 0) { stop = "FINITE_BUYER_OR_HANDLING_BUDGET"; break; }
            var before = c.captureState(); var offer = c.previewPilotAction("SELL", station, commodity, kg);
            if (!before.equals(c.captureState())) throw new AssertionError("Sale preview changed the checkpoint");
            if (!offer.allowed()) { stop = "ORDINARY_VALIDATION_REFUSED"; break; }
            double stock = endpoint.storage().commodityMassKg(commodity);
            long wallet = c.playerState().orElseThrow().walletMilliCredits(), cash = buyer.getBalanceMilliCredits();
            long payment = offer.walletChangeMilliCredits();
            if (payment <= 0) throw new AssertionError("Sale lacks actual payment");
            c.submitPilotAction(offer);
            if (Math.abs(r.freight().cargoHoldSnapshot(fleet).commodityMassByIdKg().getOrDefault(commodity, 0d) - (cargo - kg)) > 1e-7
                    || Math.abs(endpoint.storage().commodityMassKg(commodity) - (stock + kg)) > 1e-7
                    || c.playerState().orElseThrow().walletMilliCredits() != wallet + payment
                    || buyer.getBalanceMilliCredits() != cash - payment)
                throw new AssertionError("Non-conserved immediate ordinary sale");
            sold += kg; income = Math.addExact(income, payment);
            System.out.println("ACTUAL SALE kg=" + kg + " paidMilliCredits=" + payment + " tick=" + r.world().getAuthoritativeWorldTick());
        }
        var saved = c.captureState(); Stage228GeneratedCampaignPersistenceCodec.write(output, saved);
        if (!saved.equals(Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(output)).captureState()))
            throw new AssertionError("Non-exact sale continuation restore");
        System.out.println("ACTUAL FINITE SALES PASS soldKg=" + sold + " incomeMilliCredits=" + income
                + " walletMilliCredits=" + saved.playerState().walletMilliCredits() + " stop=" + stop
                + "; remaining cargo/finite buyer limits preserved; whole 23B completion not claimed");
    }
}
