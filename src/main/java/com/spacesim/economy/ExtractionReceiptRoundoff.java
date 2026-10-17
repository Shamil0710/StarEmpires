package com.spacesim.economy;

/** Clears only a certified numerical residue after every real receipt of a commodity is consumed. */
public final class ExtractionReceiptRoundoff {
    private ExtractionReceiptRoundoff() { }
    /**
     * The caller must prove that its physical receipt ledger is empty before invoking this boundary.
     * @param storage actual carrying hold
     * @param commodity actual commodity whose receipts were completely consumed
     * @param certifiedBoundKg bound derived from original positive receipt additions
     */
    public static void clearEmptyLedgerResidue(Stage18StationStorage storage, String commodity, double certifiedBoundKg) {
        if (!Double.isFinite(certifiedBoundKg) || certifiedBoundKg < 0) throw new IllegalArgumentException("Invalid receipt roundoff bound");
        double residue = storage.commodityMassKg(commodity);
        if (residue > certifiedBoundKg) throw new IllegalStateException("Physical cargo remains after receipt ledger consumption");
        if (residue > 0) storage.removeCommodity(commodity, residue);
    }
}
