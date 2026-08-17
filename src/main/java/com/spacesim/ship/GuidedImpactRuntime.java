package com.spacesim.ship;

import java.util.Objects;

/**
 * Converts one surviving guided body into the same physical impact representation used by kinetic weapons.
 *
 * <p>This runtime intentionally models only kinetic terminal bodies. A guided body with a non-null
 * {@link GuidedWeaponBody#impactPayloadId()} requires a separate authored payload resolver and is
 * rejected here rather than silently granting explosive damage. Guidance loss therefore cannot
 * delete the body: whatever mass and velocity survive to contact remain ordinary kinetic physics.</p>
 */
public final class GuidedImpactRuntime {
    /**
     * Materializes a kinetic impact body from one guided missile/interceptor at contact.
     *
     * @param body authoritative surviving guided body
     * @param impactTick deterministic simulation tick of contact
     * @return physical projectile carrying the guided body's current mass, geometry, position and velocity
     */
    public ProjectileBody toKineticImpact(GuidedWeaponBody body, long impactTick) {
        GuidedWeaponBody checked = Objects.requireNonNull(body, "body");
        if (impactTick < 0L) {
            throw new IllegalArgumentException("impactTick must be non-negative");
        }
        if (checked.impactPayloadId() != null) {
            throw new IllegalArgumentException(
                    "guided body has an authored impact payload but no payload resolver was supplied: "
                            + checked.impactPayloadId());
        }
        return new ProjectileBody(
                checked.bodyId(),
                checked.sourceEntityId(),
                impactTick,
                checked.materialId(),
                checked.shape(),
                checked.lengthM(),
                checked.diameterM(),
                checked.currentMassKg(),
                checked.xM(),
                checked.yM(),
                checked.velocityXMps(),
                checked.velocityYMps());
    }
}
