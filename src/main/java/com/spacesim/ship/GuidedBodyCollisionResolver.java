package com.spacesim.ship;

import java.util.List;
import java.util.Objects;

/**
 * Deterministic provisional physical response for interceptor-versus-guided-body contact.
 *
 * <p>The exact-local Stage-19I gate needs a physical consequence after swept body-body contact, but
 * the project does not yet own a fragmentation/explosive debris solver. This resolver therefore uses
 * a perfectly inelastic center-of-mass velocity while preserving both original material/geometry
 * identities as two ordinary unguided {@link ProjectileBody} residuals. Total physical mass and
 * linear momentum are conserved. The missing kinetic energy represents unresolved deformation,
 * fragmentation and heat and is deliberately not credited as abstract ship damage.</p>
 */
public final class GuidedBodyCollisionResolver {
    private static final long THREAT_RESIDUAL_NAMESPACE = 1_300_000_000L;
    private static final long INTERCEPTOR_RESIDUAL_NAMESPACE = 1_400_000_000L;

    /**
     * Result of one physical guided-body collision.
     *
     * @param interceptorResidual unguided residual preserving interceptor body identity/material
     * @param threatResidual unguided residual preserving incoming threat identity/material
     * @param dissipatedKineticEnergyJ non-negative kinetic energy removed by the inelastic response
     */
    public record ResidualPair(
            ProjectileBody interceptorResidual,
            ProjectileBody threatResidual,
            double dissipatedKineticEnergyJ) {
        /**
         * Validates one immutable collision result.
         *
         * @param interceptorResidual unguided interceptor residual
         * @param threatResidual unguided threat residual
         * @param dissipatedKineticEnergyJ non-negative unresolved deformation/fragmentation energy
         */
        public ResidualPair {
            Objects.requireNonNull(interceptorResidual, "interceptorResidual");
            Objects.requireNonNull(threatResidual, "threatResidual");
            if (!Double.isFinite(dissipatedKineticEnergyJ) || dissipatedKineticEnergyJ < 0d) {
                throw new IllegalArgumentException("dissipatedKineticEnergyJ must be finite and non-negative");
            }
        }

        /** @return both ordinary residual bodies in deterministic interceptor/threat order */
        public List<ProjectileBody> residuals() {
            return List.of(interceptorResidual, threatResidual);
        }
    }

    /**
     * Resolves first physical contact as a perfectly inelastic body-body response.
     *
     * @param interceptor physical interceptor state at contact
     * @param threat physical guided threat state at contact
     * @param collisionXM world x coordinate of the contact center of mass
     * @param collisionYM world y coordinate of the contact center of mass
     * @param tick authoritative collision tick
     * @return two ordinary residual bodies sharing the conserved center-of-mass velocity
     */
    public ResidualPair resolve(
            GuidedWeaponBody interceptor,
            GuidedWeaponBody threat,
            double collisionXM,
            double collisionYM,
            long tick) {
        GuidedWeaponBody checkedInterceptor = Objects.requireNonNull(interceptor, "interceptor");
        GuidedWeaponBody checkedThreat = Objects.requireNonNull(threat, "threat");
        if (checkedInterceptor.bodyId() == checkedThreat.bodyId()) {
            throw new IllegalArgumentException("interceptor and threat must be distinct physical bodies");
        }
        if (!Double.isFinite(collisionXM) || !Double.isFinite(collisionYM)) {
            throw new IllegalArgumentException("collision position must be finite");
        }
        if (tick < 0L) {
            throw new IllegalArgumentException("tick must be non-negative");
        }

        double interceptorMass = checkedInterceptor.currentMassKg();
        double threatMass = checkedThreat.currentMassKg();
        double totalMass = interceptorMass + threatMass;
        double velocityX = (interceptorMass * checkedInterceptor.velocityXMps()
                + threatMass * checkedThreat.velocityXMps()) / totalMass;
        double velocityY = (interceptorMass * checkedInterceptor.velocityYMps()
                + threatMass * checkedThreat.velocityYMps()) / totalMass;
        double kineticBefore = checkedInterceptor.kineticEnergyJ() + checkedThreat.kineticEnergyJ();
        double kineticAfter = 0.5d * totalMass * (velocityX * velocityX + velocityY * velocityY);
        double dissipated = Math.max(0d, kineticBefore - kineticAfter);

        ProjectileBody interceptorResidual = residual(
                Math.addExact(INTERCEPTOR_RESIDUAL_NAMESPACE, checkedInterceptor.bodyId()),
                checkedInterceptor,
                collisionXM,
                collisionYM,
                velocityX,
                velocityY,
                tick);
        ProjectileBody threatResidual = residual(
                Math.addExact(THREAT_RESIDUAL_NAMESPACE, checkedThreat.bodyId()),
                checkedThreat,
                collisionXM,
                collisionYM,
                velocityX,
                velocityY,
                tick);
        return new ResidualPair(interceptorResidual, threatResidual, dissipated);
    }

    private static ProjectileBody residual(
            long projectileId,
            GuidedWeaponBody source,
            double xM,
            double yM,
            double velocityXMps,
            double velocityYMps,
            long tick) {
        return new ProjectileBody(
                projectileId,
                source.sourceEntityId(),
                tick,
                source.materialId(),
                source.shape(),
                source.lengthM(),
                source.diameterM(),
                source.currentMassKg(),
                xM,
                yM,
                velocityXMps,
                velocityYMps);
    }
}
