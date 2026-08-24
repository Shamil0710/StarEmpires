package com.spacesim.ship;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.weapon.Stage175ICombatTestWeaponPack;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog.GuidedEngagementRole;
import com.spacesim.content.weapon.WeaponLauncherCatalog;
import com.spacesim.ship.LiveTacticalBattleRuntimeState.CombatantRuntime;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Stage-19I physical guided-decoy deployment over ordinary fitted launcher/feed state.
 *
 * <p>A decoy is not a synthetic sensor record. It is an authored {@link GuidedEngagementRole#DECOY}
 * round carried in the central physical ammunition state, launched from an ordinary compatible guided
 * mount, charged against the ordinary launcher cycle and materialized as a real guided body with mass,
 * geometry, velocity and finite propellant. This runtime advances no battle clock and owns no ship
 * combat state; it only advances its decoy bodies to the wrapped authoritative ordnance tick.</p>
 *
 * <p>The current foundation accepts an explicit deployment direction from its caller. It does not yet
 * choose when or where AI should deploy decoys. Sensor/defense integration may observe and physically
 * collide with these bodies, but cannot bypass this owner when a decoy is destroyed.</p>
 */
public final class LiveTacticalBattleDecoyRuntime {
    private static final double TICK_SECONDS = LiveTacticalBattleControlRuntime.TICK_SECONDS;
    private static final double EPSILON = 1e-9d;

    private final LiveTacticalBattleOrdnanceRuntime ordnanceRuntime;
    private final WeaponAmmunitionCatalog ammunitionCatalog;
    private final WeaponLauncherCatalog launcherCatalog;
    private final DerivedShipCalculator calculator;
    private final ShipGuidedWeaponEngineeringAdapter guidedAdapter;
    private final AmmunitionRuntime ammunitionRuntime;
    private final WeaponMountRuntime weaponMountRuntime;
    private final List<DecoyRuntime> decoys = new ArrayList<>();
    private final TreeMap<Long, Long> deploymentsByEntityId = new TreeMap<>();

    private long nextBodyId = 199_000L;
    private long lastAdvancedTick;

    /**
     * Creates physical decoy deployment over one authoritative guided-ordnance runtime.
     *
     * @param ordnanceRuntime wrapped authoritative battle/ordnance state
     */
    public LiveTacticalBattleDecoyRuntime(LiveTacticalBattleOrdnanceRuntime ordnanceRuntime) {
        this.ordnanceRuntime = Objects.requireNonNull(ordnanceRuntime, "ordnanceRuntime");
        ammunitionCatalog = Stage175ICombatTestWeaponPack.loadAmmunition();
        launcherCatalog = Stage175ICombatTestWeaponPack.loadLaunchers();
        calculator = new DerivedShipCalculator(battleState().engineeringCatalog());
        guidedAdapter = new ShipGuidedWeaponEngineeringAdapter();
        ammunitionRuntime = new AmmunitionRuntime();
        weaponMountRuntime = new WeaponMountRuntime();
        lastAdvancedTick = ordnanceRuntime.tick();
        for (CombatantRuntime combatant : battleState().combatants()) {
            deploymentsByEntityId.put(combatant.spec().entityId(), 0L);
        }
    }

    /**
     * Attempts one physical decoy deployment from an explicitly selected fitted mount.
     *
     * <p>The direction is caller policy only; the physical burn remains bounded by the authored decoy
     * propulsion state. A denied deployment consumes no ammunition and changes no launcher cycle.
     * Existing decoys are first synchronized to the current authoritative tick, so a newly launched
     * body can never be retroactively advanced through time that occurred before its deployment.</p>
     *
     * @param sourceEntityId deploying combatant identity
     * @param mountId fitted guided mount selected for deployment
     * @param directionX desired autonomous separation/burn x direction
     * @param directionY desired autonomous separation/burn y direction
     * @return true only when one physical round was consumed and a body materialized
     */
    public boolean deployOne(
            long sourceEntityId,
            String mountId,
            double directionX,
            double directionY) {
        CombatantRuntime source = battleState().requireCombatant(sourceEntityId);
        requireNonBlank(mountId, "mountId");
        requireFinite(directionX, "directionX");
        requireFinite(directionY, "directionY");
        double magnitude = Math.hypot(directionX, directionY);
        if (magnitude <= EPSILON) {
            throw new IllegalArgumentException("decoy deployment direction must be non-zero");
        }
        double unitX = directionX / magnitude;
        double unitY = directionY / magnitude;
        advanceToCurrentTick();

        ShipGuidedWeaponEngineeringAdapter.FittedGuidedMount mount = decoyMounts(source).stream()
                .filter(value -> value.mountId().equals(mountId))
                .findFirst()
                .orElse(null);
        if (mount == null) {
            return false;
        }
        EngineeringComponent engineering = source.engineering();
        ShipInstanceRuntimeState instance = engineering.instanceState;
        if (!weaponMountRuntime.ready(instance.weaponMountRuntime(), mount.mountId())) {
            return false;
        }
        var plan = ammunitionRuntime.planOne(
                engineering.runtimeState.consumables(),
                mount.mountId(),
                mount.launcher(),
                mount.ammunition().wetMassKg());
        if (!plan.allowed()) {
            return false;
        }

        GuidedWeaponBody body = GuidedWeaponBody.launch(
                nextBodyId,
                sourceEntityId,
                sourceEntityId,
                mount.ammunition().toRuntimeWeapon(),
                mount.ammunition().materialId(),
                mount.ammunition().shape(),
                mount.ammunition().lengthM(),
                mount.ammunition().diameterM(),
                mount.ammunition().impactPayloadId(),
                source.transform().position.x,
                source.transform().position.y,
                source.transform().velocity.x,
                source.transform().velocity.y);
        var consumption = ammunitionRuntime.consumeOne(
                engineering.runtimeState.consumables(),
                mount.mountId(),
                mount.launcher(),
                mount.ammunition().wetMassKg());
        WeaponMountRuntime.RuntimeState weaponState = weaponMountRuntime.commitShot(
                instance.weaponMountRuntime(),
                mount.mountId(),
                mount.launcher());
        replaceConsumables(engineering, consumption.consumables());
        replaceWeaponRuntime(engineering, weaponState);

        decoys.add(new DecoyRuntime(
                body,
                sourceEntityId,
                mount.mountId(),
                unitX,
                unitY));
        nextBodyId = Math.addExact(nextBodyId, 1L);
        deploymentsByEntityId.compute(
                sourceEntityId,
                (ignored, count) -> Math.addExact(Objects.requireNonNull(count, "deployment count"), 1L));
        return true;
    }

    /**
     * Advances all physical decoys to the wrapped authoritative battle tick without advancing that clock.
     *
     * <p>If the caller skipped multiple battle ticks, each missed fixed step is replayed in order so
     * the result is identical to calling once per authoritative tick.</p>
     */
    public void advanceToCurrentTick() {
        long targetTick = ordnanceRuntime.tick();
        if (targetTick < lastAdvancedTick) {
            throw new IllegalStateException("decoy runtime tick moved backwards");
        }
        while (lastAdvancedTick < targetTick) {
            advanceOnePhysicalStep();
            lastAdvancedTick++;
        }
    }

    /** @return wrapped authoritative guided-ordnance runtime */
    public LiveTacticalBattleOrdnanceRuntime ordnanceRuntime() {
        return ordnanceRuntime;
    }

    /** @return immutable current physical decoy bodies in launch order */
    public List<GuidedWeaponBody> decoyBodies() {
        return decoys.stream().map(DecoyRuntime::body).toList();
    }

    GuidedWeaponBody removeDecoyBody(long bodyId) {
        for (int index = 0; index < decoys.size(); index++) {
            DecoyRuntime value = decoys.get(index);
            if (value.body().bodyId() == bodyId) {
                decoys.remove(index);
                return value.body();
            }
        }
        return null;
    }

    /**
     * Returns total physical deployments by one combatant.
     *
     * @param entityId stable combatant identity
     * @return non-negative deployment count
     */
    public long deployments(long entityId) {
        battleState().requireCombatant(entityId);
        return deploymentsByEntityId.get(entityId);
    }

    /**
     * Deterministic physical/resource projection for acceptance and later saturation profiling.
     *
     * @return immutable decoy runtime fingerprint
     */
    public DecoyFingerprint fingerprint() {
        List<BodyFingerprint> bodies = decoys.stream()
                .map(value -> new BodyFingerprint(
                        value.body().bodyId(),
                        value.sourceEntityId(),
                        value.mountId(),
                        value.body().xM(),
                        value.body().yM(),
                        value.body().velocityXMps(),
                        value.body().velocityYMps(),
                        value.body().remainingPropellantKg(),
                        value.body().remainingPoweredBurnSeconds()))
                .toList();
        return new DecoyFingerprint(
                ordnanceRuntime.tick(),
                new TreeMap<>(deploymentsByEntityId),
                bodies);
    }

    private void advanceOnePhysicalStep() {
        for (int index = 0; index < decoys.size(); index++) {
            DecoyRuntime current = decoys.get(index);
            GuidedWeaponBody body = current.body();
            if (body.remainingPropellantKg() > EPSILON
                    && body.remainingPoweredBurnSeconds() > EPSILON) {
                body = body.burn(current.directionX(), current.directionY(), TICK_SECONDS);
            }
            body = body.advanceBallistic(TICK_SECONDS);
            decoys.set(index, current.withBody(body));
        }
    }

    private List<ShipGuidedWeaponEngineeringAdapter.FittedGuidedMount> decoyMounts(CombatantRuntime combatant) {
        EngineeringComponent engineering = combatant.engineering();
        return guidedAdapter.deriveGuidedMounts(
                derive(combatant),
                ammunitionCatalog,
                launcherCatalog,
                engineering.instanceState.weaponLoadout(),
                GuidedEngagementRole.DECOY);
    }

    private DerivedShipState derive(CombatantRuntime combatant) {
        EngineeringComponent engineering = combatant.engineering();
        return calculator.derive(
                combatant.hull(),
                engineering.fit,
                engineering.runtimeState.consumables(),
                engineering.instanceState.damage().moduleDamage());
    }

    private LiveTacticalBattleRuntimeState battleState() {
        return ordnanceRuntime.battleState();
    }

    private static void replaceConsumables(
            EngineeringComponent engineering,
            ShipEngineeringState.ConsumableState consumables) {
        RuntimeState state = engineering.runtimeState;
        engineering.setRuntimeState(new RuntimeState(
                Objects.requireNonNull(consumables, "consumables"),
                state.sharedBusEnergyJ(),
                state.shipHeatStoredJ(),
                state.localHeatJByMount(),
                state.thrustLimitNByMount(),
                state.coolantBusCapacityW(),
                state.ftlCooldownSecondsByMount()));
    }

    private static void replaceWeaponRuntime(
            EngineeringComponent engineering,
            WeaponMountRuntime.RuntimeState weaponState) {
        ShipInstanceRuntimeState instance = engineering.instanceState;
        engineering.setInstanceState(new ShipInstanceRuntimeState(
                instance.damage(),
                instance.shieldStatesByMount(),
                instance.maintenance(),
                instance.weaponLoadout(),
                Objects.requireNonNull(weaponState, "weaponState")));
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
    }

    private static void requireFinite(double value, String label) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(label + " must be finite");
        }
    }

    private record DecoyRuntime(
            GuidedWeaponBody body,
            long sourceEntityId,
            String mountId,
            double directionX,
            double directionY) {
        private DecoyRuntime {
            Objects.requireNonNull(body, "body");
            if (sourceEntityId <= 0L) {
                throw new IllegalArgumentException("sourceEntityId must be positive");
            }
            requireNonBlank(mountId, "mountId");
            requireFinite(directionX, "directionX");
            requireFinite(directionY, "directionY");
        }

        private DecoyRuntime withBody(GuidedWeaponBody nextBody) {
            return new DecoyRuntime(nextBody, sourceEntityId, mountId, directionX, directionY);
        }
    }

    /**
     * Equality-friendly physical projection of one active decoy body.
     *
     * @param bodyId stable body identity
     * @param sourceEntityId launching combatant identity
     * @param mountId physical launcher mount
     * @param xM current x position
     * @param yM current y position
     * @param velocityXMps current x velocity
     * @param velocityYMps current y velocity
     * @param remainingPropellantKg remaining physical propellant
     * @param remainingPoweredBurnSeconds remaining powered-burn lifetime
     */
    public record BodyFingerprint(
            long bodyId,
            long sourceEntityId,
            String mountId,
            double xM,
            double yM,
            double velocityXMps,
            double velocityYMps,
            double remainingPropellantKg,
            double remainingPoweredBurnSeconds) {
        /**
         * Validates one immutable physical decoy projection.
         *
         * @param bodyId stable body identity
         * @param sourceEntityId launching combatant identity
         * @param mountId physical launcher mount
         * @param xM current x position
         * @param yM current y position
         * @param velocityXMps current x velocity
         * @param velocityYMps current y velocity
         * @param remainingPropellantKg remaining physical propellant
         * @param remainingPoweredBurnSeconds remaining powered-burn lifetime
         */
        public BodyFingerprint {
            if (bodyId <= 0L || sourceEntityId <= 0L) {
                throw new IllegalArgumentException("decoy identities must be positive");
            }
            requireNonBlank(mountId, "mountId");
            requireFinite(xM, "xM");
            requireFinite(yM, "yM");
            requireFinite(velocityXMps, "velocityXMps");
            requireFinite(velocityYMps, "velocityYMps");
            if (!Double.isFinite(remainingPropellantKg) || remainingPropellantKg < -EPSILON
                    || !Double.isFinite(remainingPoweredBurnSeconds) || remainingPoweredBurnSeconds < -EPSILON) {
                throw new IllegalArgumentException("invalid remaining decoy propulsion state");
            }
        }
    }

    /**
     * Whole-runtime deterministic decoy projection.
     *
     * @param tick wrapped authoritative battle tick
     * @param deploymentsByEntityId total physical deployments by combatant
     * @param bodies current physical decoy bodies
     */
    public record DecoyFingerprint(
            long tick,
            Map<Long, Long> deploymentsByEntityId,
            List<BodyFingerprint> bodies) {
        /**
         * Validates and freezes one deterministic decoy projection.
         *
         * @param tick wrapped authoritative battle tick
         * @param deploymentsByEntityId total physical deployments by combatant
         * @param bodies current physical decoy bodies
         */
        public DecoyFingerprint {
            if (tick < 0L) {
                throw new IllegalArgumentException("tick must be non-negative");
            }
            deploymentsByEntityId = Map.copyOf(new TreeMap<>(Objects.requireNonNull(
                    deploymentsByEntityId, "deploymentsByEntityId")));
            bodies = List.copyOf(Objects.requireNonNull(bodies, "bodies"));
        }
    }
}
