import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.components.WalletComponent;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.world.FleetId;
import java.nio.file.Path;
import java.util.List;

/** Audits actual mining/sale checkpoints without supplying stock, money, geometry or actor state. */
public final class Stage23BActualFundingIntegrity {
    private static final String STATION = "hub.14";
    private static final String ORE = "commodity.feedstock.metallic_ore";
    private record Facts(long wallet, long marketWallet, long treasury, long tick, long nextSequence,
            List<FleetId> ownedFleets, double heldOre) { }

    private static Facts before(Path path) throws Exception {
        var c = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(path));
        var p = c.playerState().orElseThrow(); var w = c.coordinator().runtime().world();
        var ref = c.pilotMarketReference(STATION).orElseThrow();
        long market = w.findSession(ref.systemId()).orElseThrow().getEntityRegistry().require(ref.entityId()).getComponent(WalletComponent.class).getBalanceMilliCredits();
        return new Facts(p.walletMilliCredits(), market, w.findFactionEconomicState("faction.beta").orElseThrow().treasuryMilliCredits(),
                w.getAuthoritativeWorldTick(), c.playerJournal().nextSequence(), p.ownedFleetIds(),
                c.coordinator().runtime().freight().cargoHoldSnapshot(p.activeFleetId()).commodityMassByIdKg().getOrDefault(ORE, 0d));
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Expected original sale and actual funding checkpoint");
        var before = before(Path.of(args[0]));
        var c = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(Path.of(args[1])));
        var p = c.playerState().orElseThrow(); var runtime = c.coordinator().runtime(); var w = runtime.world();
        var ref = c.pilotMarketReference(STATION).orElseThrow();
        if (!ref.equals(p.dockedAt()) || !before.ownedFleets().equals(p.ownedFleetIds()) || w.getAuthoritativeWorldTick() <= before.tick())
            throw new AssertionError("Funding did not retain the actual owned ships and market berth");
        long income = 0, sold = 0;
        for (var e : c.playerJournal().entries()) if (e.sequence() >= before.nextSequence() && e.action().equals("SELL")
                && e.endpoint().equals(STATION) && e.subject().equals(ORE)) {
            income = Math.addExact(income, e.walletDeltaMilliCredits()); sold = Math.addExact(sold, e.quantity());
        }
        long market = w.findSession(ref.systemId()).orElseThrow().getEntityRegistry().require(ref.entityId()).getComponent(WalletComponent.class).getBalanceMilliCredits();
        long treasury = w.findFactionEconomicState("faction.beta").orElseThrow().treasuryMilliCredits();
        if (sold <= 0 || income <= 0 || p.walletMilliCredits() != before.wallet() + income
                || before.marketWallet() + before.treasury() - market - treasury != income)
            throw new AssertionError("Actual sales, market cash, operator treasury and personal wallet do not conserve money");
        long price = c.stationSalePriceMilliCredits("industry.2");
        if (price <= 0 || p.walletMilliCredits() < price) throw new AssertionError("The known existing station is still unaffordable");
        var snapshot = c.captureState();
        var restored = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(Stage228GeneratedCampaignPersistenceCodec.encode(snapshot)));
        if (!snapshot.equals(restored.captureState())) throw new AssertionError("Non-exact actual funding checkpoint");
        double remaining = runtime.freight().cargoHoldSnapshot(p.activeFleetId()).commodityMassByIdKg().getOrDefault(ORE, 0d);
        System.out.println("ACTUAL STATION FUNDING PASS soldKg=" + sold + " incomeMilliCredits=" + income
                + " walletMilliCredits=" + p.walletMilliCredits() + " stationPriceMilliCredits=" + price
                + " remainingOreKg=" + remaining + " recoveredOreKg=" + (sold + remaining - before.heldOre())
                + " marketMilliCredits=" + market + " operatorTreasuryMilliCredits=" + treasury
                + " tick=" + w.getAuthoritativeWorldTick() + "; no stock, money, geometry or actor fixtures");
    }
}
