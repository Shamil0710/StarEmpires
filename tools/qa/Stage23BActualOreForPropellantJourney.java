import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.components.*;
import com.spacesim.content.Stage22ShipConsumableCatalogLoader;
import com.spacesim.content.ship.*;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.ship.ProductionEngineeringRuntimeResolver;
import com.spacesim.ui.GeneratedCampaignSupplyUi;
import java.nio.file.*;

/** Conserved ordinary exchange: paid water funds the dock's purchase of already mined ore. */
public final class Stage23BActualOreForPropellantJourney {
    public static void main(String[] args) throws Exception {
        var input = Path.of(args[0]); var output = Path.of(args[1]); String station = args[2];
        if (Files.exists(output) || input.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize()))
            throw new IllegalArgumentException("Preserve previous real checkpoints");
        var saved = Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(input);
        var c = Stage228CampaignAuthority.restore(saved);
        if (!saved.equals(c.captureState())) throw new AssertionError("Non-exact ordinary input");
        var p = c.playerState().orElseThrow(); var r = c.coordinator().runtime(); var w = r.world();
        if (!p.docked() || !c.pilotMarketReference(station).orElseThrow().equals(p.dockedAt()))
            throw new IllegalStateException("Actual saved dock required");
        var fleet = w.findFleet(p.activeFleetId()).orElseThrow();
        var engineering = w.findSession(fleet.systemId()).orElseThrow().getEntityRegistry().require(fleet.localEntityId()).getComponent(EngineeringComponent.class);
        var binding = Stage22ShipConsumableCatalogLoader.loadDefault().getBindings().stream()
                .filter(b -> b.interfaceKind() == ShipEngineeringCatalog.InterfaceKind.REACTION_MASS)
                .filter(b -> engineering.fit.installedModules().stream().anyMatch(m -> m.moduleId().equals(b.moduleId()))).findFirst().orElseThrow();
        var mount = engineering.fit.installedModules().stream().filter(m -> m.moduleId().equals(binding.moduleId())).findFirst().orElseThrow().mountId();
        var port = Stage22FreightStrategicEngineeringCatalogLoader.loadDefault().findModule(binding.moduleId()).interfaces().stream()
                .filter(v -> v.id().equals(binding.interfaceId())).findFirst().orElseThrow();
        if (GeneratedCampaignSupplyUi.rows(c).stream().noneMatch(row -> row.selection().stableId().equals("pilot-supply|" + binding.id() + '|' + mount)))
            throw new AssertionError("Actual servicing interface is absent from production UI");
        var resolver = new ProductionEngineeringRuntimeResolver();
        double initialFuel = resolver.derive(engineering).reactionMassKg();
        double goal = Math.min(Double.parseDouble(System.getProperty("stage23b.exchange.fuelGoalKg", "2000000")), port.capacity() / binding.amountPerKg());
        if (!Double.isFinite(goal) || goal <= initialFuel) throw new IllegalArgumentException("A larger finite actual tank goal is required");
        String ore = "commodity.feedstock.metallic_ore";
        double initialOre = r.freight().cargoHoldSnapshot(p.activeFleetId()).commodityMassByIdKg().getOrDefault(ore, 0d);
        double initialWater = r.infrastructure().endpoint(station).storage().commodityMassKg(binding.commodityId());
        double initialStationOre = r.infrastructure().endpoint(station).storage().commodityMassKg(ore);
        long paid = 0, earned = 0, sold = 0, loaded = 0;
        var dockWallet = w.findSession(p.dockedAt().systemId()).orElseThrow().getEntityRegistry().require(p.dockedAt().entityId()).getComponent(WalletComponent.class);
        System.out.println("ACTUAL EXCHANGE START fuelKg=" + initialFuel + " goalKg=" + goal + " oreKg=" + initialOre + " wallet=" + p.walletMilliCredits());
        try {
            for (int batch = 0; batch < 200 && resolver.derive(engineering).reactionMassKg() + 1d <= goal; batch++) {
                step(c);
                long quote = c.pilotCommodityPrice(station, binding.commodityId(), true);
                int kg = (int) Math.floor(Math.min(40000, Math.min(goal - resolver.derive(engineering).reactionMassKg(),
                        Math.min(c.playerState().orElseThrow().walletMilliCredits() / quote,
                                r.infrastructure().endpoint(station).handlingCapability().massRateKgPerSecond() * c.coordinator().session().fixedStepSeconds()))));
                if (kg <= 0) throw new IllegalStateException("Finite water purchase budget exhausted");
                var before = c.captureState(); var buy = c.previewPilotAction("BUY_CONSUMABLE", binding.id(), mount, kg);
                if (!buy.allowed() || !before.equals(c.captureState())) throw new AssertionError("Paid fuel preview refused or impure");
                long cost = -buy.walletChangeMilliCredits(), cash = dockWallet.getBalanceMilliCredits();
                c.submitPilotAction(buy);
                if (cost <= 0 || dockWallet.getBalanceMilliCredits() != cash + cost) throw new AssertionError("Fuel payment did not reach real market wallet");
                paid = Math.addExact(paid, cost); loaded += kg;
                step(c);
                long oreQuote = c.pilotCommodityPrice(station, ore, false);
                int oreKg = (int) Math.floor(Math.min(initialOre - sold, Math.min(cost / oreQuote,
                        Math.min(dockWallet.getBalanceMilliCredits() / oreQuote,
                                r.infrastructure().endpoint(station).handlingCapability().massRateKgPerSecond() * c.coordinator().session().fixedStepSeconds()))));
                if (oreKg <= 0) throw new IllegalStateException("Existing mined ore or buyer handling budget exhausted");
                before = c.captureState(); var sell = c.previewPilotAction("SELL", station, ore, oreKg);
                if (!sell.allowed() || !before.equals(c.captureState())) throw new AssertionError("Existing ore sale preview refused or impure");
                long income = sell.walletChangeMilliCredits(); cash = dockWallet.getBalanceMilliCredits(); c.submitPilotAction(sell);
                if (income <= 0 || dockWallet.getBalanceMilliCredits() != cash - income) throw new AssertionError("Ore payment exceeded actual buyer wallet");
                sold += oreKg; earned = Math.addExact(earned, income);
                System.out.println("ACTUAL EXCHANGE batch=" + batch + " waterKg=" + kg + " oreKg=" + oreKg
                        + " fuelKg=" + resolver.derive(engineering).reactionMassKg() + " wallet=" + c.playerState().orElseThrow().walletMilliCredits());
            }
            double fuel = resolver.derive(engineering).reactionMassKg();
            if (fuel + 1d < goal || Math.abs(fuel - initialFuel - loaded) > 1e-6
                    || Math.abs(r.freight().cargoHoldSnapshot(p.activeFleetId()).commodityMassByIdKg().getOrDefault(ore, 0d) - initialOre + sold) > 1e-6
                    || Math.abs(r.infrastructure().endpoint(station).storage().commodityMassKg(ore) - initialStationOre - sold) > 1e-6
                    || Math.abs(r.infrastructure().endpoint(station).storage().commodityMassKg(binding.commodityId()) - initialWater + loaded) > 1e-6
                    || c.playerState().orElseThrow().walletMilliCredits() != p.walletMilliCredits() - paid + earned)
                throw new AssertionError("Ordinary exchange did not conserve physical stocks/fuel and money");
            var complete = c.captureState(); Stage228GeneratedCampaignPersistenceCodec.write(output, complete);
            if (!complete.equals(Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(output)).captureState()))
                throw new AssertionError("Non-exact exchanged checkpoint");
            System.out.println("ACTUAL ORE FOR PROPELLANT PASS loadedKg=" + loaded + " soldKg=" + sold + " paid=" + paid + " earned=" + earned
                    + "; actual old berth, existing mined cargo, finite water and market money, common handling ticks, exact reload; no credit or stock grants");
        } finally { Stage228GeneratedCampaignPersistenceCodec.write(output, c.captureState()); }
    }
    private static void step(Stage228CampaignAuthority c) { c.coordinator().setTimeScale(1); c.coordinator().setPaused(false); c.advanceFrame(c.coordinator().session().fixedStepSeconds()); c.coordinator().setPaused(true); }
}
