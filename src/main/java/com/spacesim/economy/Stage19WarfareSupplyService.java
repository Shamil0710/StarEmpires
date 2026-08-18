package com.spacesim.economy;

import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ManufacturingProductRegistry.ProductDefinition;
import com.spacesim.content.Stage18ManufacturingProductRegistry.ProductKind;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.WeaponDefinition.Launcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Stage-19F physical warfare-supply bridge for countable ammunition.
 *
 * <p>Stage 18 already owns commodity servicing, repair settlement and shipyard construction. This
 * service fills the remaining ammunition boundary: an authored Stage-18 finished ammunition product
 * is removed from canonical station storage and added to the same Stage-17.5 {@link ConsumableState}
 * that {@code AmmunitionRuntime} consumes when a weapon fires. No parallel magazine, readiness
 * currency or docking refill is introduced.</p>
 */
public final class Stage19WarfareSupplyService {
    private static final double EPSILON = 1.0e-9d;

    private final Stage18ManufacturingProductRegistry products;

    /**
     * Creates the physical ammunition servicing boundary.
     *
     * @param products authoritative Stage-18 manufactured-product registry
     */
    public Stage19WarfareSupplyService(Stage18ManufacturingProductRegistry products) {
        this.products = Objects.requireNonNull(products, "products");
    }

    /** Stable ammunition servicing outcome. */
    public enum Status {
        /** Physical rounds were removed from station storage and loaded into the ship feed. */ LOADED,
        /** Request arguments are invalid. */ INVALID_REQUEST,
        /** Requested finished-product identity is unknown. */ PRODUCT_NOT_FOUND,
        /** Requested finished product is not physical ammunition. */ NOT_AMMUNITION,
        /** Launcher and fitted physical ammunition interface do not agree. */ INTERFACE_MISMATCH,
        /** Loading the requested rounds would exceed the physical feed capacity. */ INTERFACE_CAPACITY_EXCEEDED,
        /** Canonical Stage-18 storage lacks enough finished ammunition units. */ INSUFFICIENT_STOCK
    }

    /**
     * Immutable result of one ammunition servicing request.
     *
     * @param status stable outcome
     * @param productId attempted ammunition product ID
     * @param loadedRoundCount number of physical rounds committed
     * @param loadedMassKg physical ammunition mass committed to the ship
     * @param consumables resulting central ship consumable state
     */
    public record AmmunitionLoadResult(
            Status status,
            String productId,
            int loadedRoundCount,
            double loadedMassKg,
            ConsumableState consumables) {
        /**
         * Validates one immutable servicing result.
         *
         * @param status stable outcome
         * @param productId attempted product ID
         * @param loadedRoundCount committed round count
         * @param loadedMassKg committed physical mass
         * @param consumables resulting ship consumables
         */
        public AmmunitionLoadResult {
            Objects.requireNonNull(status, "status");
            productId = productId == null ? "" : productId;
            if (loadedRoundCount < 0) {
                throw new IllegalArgumentException("loadedRoundCount must be non-negative");
            }
            if (!Double.isFinite(loadedMassKg) || loadedMassKg < 0d) {
                throw new IllegalArgumentException("loadedMassKg must be finite and non-negative");
            }
            Objects.requireNonNull(consumables, "consumables");
        }

        /** @return whether canonical station and ship state were physically committed */
        public boolean committed() {
            return status == Status.LOADED;
        }
    }

    /**
     * Loads countable manufactured ammunition into one physical launcher feed.
     *
     * <p>The supplied interface must be the fitted module interface resolved by the caller from the
     * authoritative Stage-17.5 engineering catalog. Capacity is therefore taken from physical
     * content, while per-shot interface usage comes from the production launcher definition.</p>
     *
     * @param productId Stage-18 finished ammunition product / Stage-17.5 ammunition content ID
     * @param mountId fitted weapon-module mount receiving the rounds
     * @param requestedRounds requested positive round count
     * @param launcher authoritative physical launcher/feed definition
     * @param ammunitionInterface authoritative fitted module ammunition interface
     * @param current current central ship consumable state
     * @param station canonical Stage-18 source storage
     * @return immutable result; rejected requests mutate neither station nor ship state
     */
    public AmmunitionLoadResult loadAmmunition(
            String productId,
            String mountId,
            int requestedRounds,
            Launcher launcher,
            InterfaceDefinition ammunitionInterface,
            ConsumableState current,
            Stage18StationStorage station) {
        ConsumableState checkedCurrent = Objects.requireNonNull(current, "current");
        Stage18StationStorage checkedStation = Objects.requireNonNull(station, "station");
        Launcher checkedLauncher = Objects.requireNonNull(launcher, "launcher");
        InterfaceDefinition checkedInterface = Objects.requireNonNull(ammunitionInterface, "ammunitionInterface");
        if (productId == null || productId.isBlank() || mountId == null || mountId.isBlank()
                || requestedRounds <= 0) {
            return rejected(Status.INVALID_REQUEST, productId, checkedCurrent);
        }
        ProductDefinition product = products.findProduct(productId);
        if (product == null) {
            return rejected(Status.PRODUCT_NOT_FOUND, productId, checkedCurrent);
        }
        if (product.kind() != ProductKind.AMMUNITION) {
            return rejected(Status.NOT_AMMUNITION, productId, checkedCurrent);
        }
        if (checkedInterface.kind() != InterfaceKind.AMMUNITION
                || !checkedInterface.id().equals(checkedLauncher.ammunitionInterfaceId())) {
            return rejected(Status.INTERFACE_MISMATCH, productId, checkedCurrent);
        }

        ConsumableLoad existing = checkedCurrent.interfaceLoads().stream()
                .filter(value -> value.kind() == InterfaceKind.AMMUNITION)
                .filter(value -> value.mountId().equals(mountId))
                .filter(value -> value.interfaceId().equals(checkedInterface.id()))
                .findFirst()
                .orElse(null);
        double currentAmount = existing == null ? 0d : existing.amount();
        double addedAmount = checkedLauncher.ammunitionAmountPerShot() * requestedRounds;
        if (!Double.isFinite(addedAmount)
                || currentAmount + addedAmount > checkedInterface.capacity() + EPSILON) {
            return rejected(Status.INTERFACE_CAPACITY_EXCEEDED, productId, checkedCurrent);
        }
        if (checkedStation.productCount(productId) < requestedRounds) {
            return rejected(Status.INSUFFICIENT_STOCK, productId, checkedCurrent);
        }

        double addedMassKg = product.unitMassKg() * requestedRounds;
        long nextCount = Math.addExact(existing == null ? 0L : existing.itemCount(), requestedRounds);
        List<ConsumableLoad> loads = new ArrayList<>();
        for (ConsumableLoad load : checkedCurrent.interfaceLoads()) {
            if (load != existing) {
                loads.add(load);
            }
        }
        loads.add(new ConsumableLoad(
                mountId,
                checkedInterface.id(),
                InterfaceKind.AMMUNITION,
                currentAmount + addedAmount,
                (existing == null ? 0d : existing.massKg()) + addedMassKg,
                nextCount));

        checkedStation.removeProduct(productId, requestedRounds);
        ConsumableState updated = new ConsumableState(
                checkedCurrent.cargoMassKg(),
                checkedCurrent.storesMassKg(),
                checkedCurrent.missionPayloadMassKg(),
                checkedCurrent.missionIntegrationVolumeM3(),
                loads);
        return new AmmunitionLoadResult(
                Status.LOADED,
                productId,
                requestedRounds,
                addedMassKg,
                updated);
    }

    private static AmmunitionLoadResult rejected(Status status, String productId, ConsumableState current) {
        return new AmmunitionLoadResult(status, productId, 0, 0d, current);
    }
}
