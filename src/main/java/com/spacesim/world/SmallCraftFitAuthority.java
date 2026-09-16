package com.spacesim.world;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.DemonstratorFitDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullDefinition;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipEngineeringState.ValidationSeverity;
import com.spacesim.ship.ShipFittingValidator;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * M22.8A content/fitting authority for one individual small-craft engineering state.
 *
 * <p>The authority deliberately reuses the ordinary Stage-17.5 engineering catalog and
 * {@link ShipFittingValidator}. A small craft cannot introduce an ad-hoc hull, module set, virtual
 * fit budget or class-name bonus: its {@code designId} must resolve to an authored fit in the
 * supplied production catalog and its persisted installed fit must match that authored definition
 * exactly before the ordinary physical fitting budgets are evaluated.</p>
 */
public final class SmallCraftFitAuthority {
    private final ShipEngineeringCatalog catalog;
    private final ShipFittingValidator validator;

    /**
     * Creates a small-craft fitting boundary over one immutable production engineering catalog.
     *
     * @param catalog production Stage-17.5 engineering catalog
     */
    public SmallCraftFitAuthority(ShipEngineeringCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.validator = new ShipFittingValidator(catalog);
    }

    /**
     * Requires one craft state to resolve to an authored fit and satisfy ordinary Stage-17.5 budgets.
     *
     * @param state individual physical craft state to validate
     * @throws IllegalArgumentException when the design is unknown, the installed fit differs from
     *         authored content, or the ordinary fitting validator reports a hard error
     */
    public void requireValid(SmallCraftState state) {
        SmallCraftState checked = Objects.requireNonNull(state, "state");
        DemonstratorFitDefinition authored = catalog.findDemonstratorFit(checked.designId());
        if (authored == null) {
            throw new IllegalArgumentException(
                    "Unknown small-craft production fit: " + checked.designId());
        }

        InstalledFit authoredFit = InstalledFit.fromDemonstrator(authored);
        if (!authoredFit.equals(checked.fit())) {
            throw new IllegalArgumentException(
                    "Small-craft installed fit differs from authored design: " + checked.designId());
        }

        HullDefinition hull = catalog.findHull(authored.hullId());
        if (hull == null) {
            throw new IllegalStateException(
                    "Authored small-craft fit references missing hull: " + authored.hullId());
        }

        var result = validator.validate(
                hull,
                checked.fit(),
                checked.runtimeState().consumables(),
                checked.instanceState().damage().moduleDamage());
        if (!result.isValid()) {
            String diagnostics = result.issues().stream()
                    .filter(issue -> issue.severity() == ValidationSeverity.ERROR)
                    .map(issue -> issue.code().name() + "@" + issue.subject())
                    .collect(Collectors.joining(","));
            throw new IllegalArgumentException(
                    "Small-craft fit violates Stage-17.5 fitting authority: " + diagnostics);
        }
    }

    /** @return semantic fingerprint of the production engineering catalog used for validation */
    public String catalogFingerprint() {
        return catalog.getFingerprint();
    }
}
