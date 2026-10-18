package com.spacesim.campaign;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.player.PlayerFleetRoutePlanner;
import com.spacesim.player.PlayerState;
import com.spacesim.ship.ProductionEngineeringRuntimeResolver;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.WorldSimulation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Drives a campaign only through ordinary player inputs: previewed pilot actions, pilot thrust and
 * fixed-step frames. It never moves ships, grants assets or edits state, so a scenario that passes
 * proves the ordinary path for its starting conditions.
 */
final class ScenarioPilot {
    private static final ProductionEngineeringRuntimeResolver ENGINEERING = new ProductionEngineeringRuntimeResolver();

    private Stage228CampaignAuthority campaign;
    private long tickBudget;

    ScenarioPilot(Stage228CampaignAuthority campaign, long tickBudget) {
        this.campaign = Objects.requireNonNull(campaign, "campaign");
        this.tickBudget = tickBudget;
    }

    Stage228CampaignAuthority campaign() { return campaign; }

    WorldSimulation world() { return campaign.coordinator().runtime().world(); }

    PlayerState player() { return campaign.playerState().orElseThrow(); }

    FleetId fleetId() { return player().activeFleetId(); }

    FleetPlacementState fleet() { return world().findFleet(fleetId()).orElseThrow(); }

    LocalPhysicalKinematics physical() {
        var f = fleet();
        return campaign.coordinator().runtime().arrival().materialization(f.systemId()).physicalState(f.localEntityId()).orElseThrow();
    }

    EngineeringComponent engineering() {
        var f = fleet();
        return world().findSession(f.systemId()).orElseThrow().getEntityRegistry().require(f.localEntityId())
                .getComponent(EngineeringComponent.class);
    }

    double reactionMassKg() { return ENGINEERING.derive(engineering()).reactionMassKg(); }

    /** Submits one previewed pilot action; a rejection fails the scenario with the authority's reason. */
    long action(String verb, String target, String subject, int amount) {
        var preview = campaign.previewPilotAction(verb, target, subject, amount);
        if (!preview.allowed()) {
            String reason = rejectionReason(verb, target, subject, amount);
            String checkpoint = saveFailureCheckpoint();
            throw new AssertionError("Rejected " + verb + " target=" + target + " subject=" + subject
                    + " amount=" + amount + " at tick=" + world().getAuthoritativeWorldTick() + ": " + reason
                    + " (checkpoint " + checkpoint + ")");
        }
        campaign.submitPilotAction(preview);
        return preview.walletChangeMilliCredits();
    }

    boolean allowed(String verb, String target, String subject, int amount) {
        return campaign.previewPilotAction(verb, target, subject, amount).allowed();
    }

    /** Diagnostic only: repeats the rejected action on an isolated restored copy to surface its exception. */
    private String rejectionReason(String verb, String target, String subject, int amount) {
        try {
            var isolated = Stage228CampaignAuthority.restore(campaign.captureState());
            var execute = Stage228CampaignAuthority.class.getDeclaredMethod("executePilotAction",
                    String.class, String.class, String.class, int.class);
            execute.setAccessible(true);
            execute.invoke(isolated, verb, target, subject, amount);
            // The preview also captures the result; its persistence invariants can reject what execution allowed.
            isolated.captureState();
            return "no exception on isolated copy";
        } catch (java.lang.reflect.InvocationTargetException e) {
            return String.valueOf(e.getCause());
        } catch (ReflectiveOperationException e) {
            return "diagnostic unavailable: " + e;
        } catch (RuntimeException e) {
            return "capture after execution failed: " + e;
        }
    }

    /** Writes the failing state so a short probe can reproduce the rejection without replaying the scenario. */
    private String saveFailureCheckpoint() {
        try {
            var path = java.nio.file.Path.of("target", "scenario-failure-" + world().getAuthoritativeWorldTick() + ".s25");
            java.nio.file.Files.createDirectories(path.getParent());
            Stage228GeneratedCampaignPersistenceCodec.write(path, campaign.captureState());
            return path.toString();
        } catch (java.io.IOException | RuntimeException e) {
            return "not saved: " + e;
        }
    }

    void tick() {
        if (tickBudget-- <= 0) throw new AssertionError("Scenario tick budget exhausted at tick=" + world().getAuthoritativeWorldTick());
        campaign.coordinator().setPaused(false);
        campaign.advanceFrame(campaign.coordinator().session().fixedStepSeconds());
        campaign.coordinator().setPaused(true);
    }

    void ticks(int count) {
        for (int i = 0; i < count; i++) tick();
    }

    /** Saves through the production codec, restores, asserts exact equality and continues on the restored copy. */
    void saveAndReload() {
        var saved = campaign.captureState();
        var restored = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(saved)));
        assertEquals(saved, restored.captureState(), "save/load must be exact");
        campaign = restored;
    }

    /** Jumps hop by hop along the player's own fuel-safe route plan. */
    List<StarSystemId> travel(StarSystemId destination) {
        var route = knownRoute(destination)
                .orElseThrow(() -> new AssertionError("No personally known fuel-safe route " + fleet().systemId() + " -> "
                        + destination + "; topology path would be " + topologyPath(destination)));
        for (var hop : route.subList(1, route.size())) jump(hop);
        return route;
    }

    private java.util.Optional<List<StarSystemId>> knownRoute(StarSystemId destination) {
        return new PlayerFleetRoutePlanner(world(), campaign.coordinator().content(), player())
                .plan(fleetId(), fleet().systemId(), destination)
                .map(view -> view.path());
    }

    private void jump(StarSystemId hop) {
        long start = world().getAuthoritativeWorldTick();
        var from = fleet().systemId();
        double fuel = reactionMassKg();
        int wait = 0;
        for (; !world().previewFittedFleetJump(fleetId()).orElseThrow().allowed() && wait < 10_000; wait++) tick();
        action("JUMP", Long.toString(hop.value()), "", 0);
        long departed = world().getAuthoritativeWorldTick();
        var state = world().findFleetJump(fleetId());
        while (world().findFleetJump(fleetId()).isPresent()) tick();
        System.out.printf("SCENARIO JUMP %d -> %d wait=%d jumpTicks=%d fuelUsed=%.0f kg state=%s%n", from.value(), hop.value(), wait,
                world().getAuthoritativeWorldTick() - departed, fuel - reactionMassKg(), state.map(Object::toString).orElse("-"));
        assertEquals(hop, fleet().systemId(), "jump must arrive at its hop");
    }

    /**
     * Reaches a personally known system the way a player without a chart would: depth-first blind
     * jumps through the "unknown direction" exits the production UI offers, switching to the own
     * route plan as soon as the discovered systems connect to the destination.
     *
     * @return systems visited in order
     */
    List<StarSystemId> explore(StarSystemId destination, int maxJumps) {
        var visited = new ArrayList<StarSystemId>();
        var trail = new ArrayDeque<StarSystemId>();
        var tried = new java.util.HashSet<StarSystemId>();
        visited.add(fleet().systemId());
        trail.push(fleet().systemId());
        int jumps = 0;
        while (!fleet().systemId().equals(destination)) {
            var known = knownRoute(destination);
            if (known.isPresent()) {
                for (var hop : known.orElseThrow().subList(1, known.orElseThrow().size())) { jump(hop); visited.add(hop); }
                break;
            }
            if (jumps++ >= maxJumps) throw new AssertionError("Exploration exceeded " + maxJumps + " jumps; visited " + visited);
            var next = unknownUiDirections().stream().filter(id -> !tried.contains(id)).findFirst();
            if (next.isPresent()) {
                tried.add(next.orElseThrow());
                jump(next.orElseThrow());
                trail.push(next.orElseThrow());
            } else {
                trail.pop();
                if (trail.isEmpty()) throw new AssertionError("Exploration exhausted all UI directions; visited " + visited);
                jump(trail.peek());
            }
            visited.add(fleet().systemId());
        }
        return visited;
    }

    private List<StarSystemId> unknownUiDirections() {
        var current = campaign;
        var model = new com.spacesim.ui.GeneratedWorldUiModel(campaign.coordinator().rootSeed(), campaign.coordinator().runtime(),
                campaign.coordinator().content(), com.spacesim.ui.GeneratedCampaignCarrierUi.source(campaign),
                () -> current.playerState().orElse(null));
        return new com.spacesim.ui.ProductionUiProjector("faction.alpha").capture(campaign, model.capture())
                .rows(com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab.SHIPS).stream()
                .filter(row -> row.selection().stableId().startsWith("pilot-jump|") && row.name().startsWith("Неизвестное направление"))
                .map(row -> new StarSystemId(Long.parseLong(row.selection().stableId().substring("pilot-jump|".length()))))
                .toList();
    }

    private List<StarSystemId> topologyPath(StarSystemId destination) {
        var paths = new HashMap<StarSystemId, List<StarSystemId>>();
        var pending = new ArrayDeque<StarSystemId>();
        paths.put(fleet().systemId(), List.of(fleet().systemId()));
        pending.add(fleet().systemId());
        while (!pending.isEmpty() && !paths.containsKey(destination)) {
            var current = pending.remove();
            for (var next : world().getTopology().neighbors(current)) {
                if (paths.containsKey(next)) continue;
                var path = new ArrayList<>(paths.get(current));
                path.add(next);
                paths.put(next, path);
                pending.add(next);
            }
        }
        return paths.getOrDefault(destination, List.of());
    }

    /**
     * Flies with ordinary pilot thrust to within {@code radius} metres and brakes to rest.
     *
     * @return ticks spent
     */
    long fly(LocalPhysicalPosition target, double radius, double cruiseLimitMps) {
        long start = world().getAuthoritativeWorldTick();
        var initial = physical();
        if (initial.position().distanceTo(target) <= radius && speed(initial) <= .01) {
            campaign.setPilotThrust(0, 0, false);
            return 0;
        }
        var fitted = engineering();
        var displacement = initial.position().displacementTo(target);
        double distance0 = Math.hypot(displacement.deltaXM(), displacement.deltaYM());
        double ux = distance0 > 0 ? displacement.deltaXM() / distance0 : 0;
        double uy = distance0 > 0 ? displacement.deltaYM() / distance0 : 0;
        double cruise = cruiseLimitMps;
        var plan = ENGINEERING.planDeltaV(fitted, Math.hypot(ux * cruise - initial.velocityXMps(), uy * cruise - initial.velocityYMps()) + cruise);
        while (!plan.feasible()) {
            cruise *= .5;
            if (cruise < .1) throw new AssertionError("No finite flight and braking plan");
            plan = ENGINEERING.planDeltaV(fitted, Math.hypot(ux * cruise - initial.velocityXMps(), uy * cruise - initial.velocityYMps()) + cruise);
        }
        double brakingAcceleration = .8 * plan.deliveredDeltaVMps() / plan.burnSeconds();
        float step = campaign.coordinator().session().fixedStepSeconds();
        while (true) {
            var state = physical();
            var d = state.position().displacementTo(target);
            double distance = Math.hypot(d.deltaXM(), d.deltaYM());
            if (distance <= radius && speed(state) <= .01) {
                campaign.setPilotThrust(0, 0, false);
                return world().getAuthoritativeWorldTick() - start;
            }
            var component = engineering();
            var derived = ENGINEERING.derive(component);
            double acceleration = Math.min(derived.accelerationMps2(), brakingAcceleration);
            if (acceleration <= 0 || derived.reactionMassKg() <= 0) throw new AssertionError("Flight capability exhausted");
            double approach = Math.min(cruise, Math.sqrt(acceleration * Math.max(0, distance - radius * .5)));
            double dx = (distance > 0 ? d.deltaXM() / distance * approach : 0) - state.velocityXMps();
            double dy = (distance > 0 ? d.deltaYM() / distance * approach : 0) - state.velocityYMps();
            double dv = Math.hypot(dx, dy);
            double throttle = ENGINEERING.throttleForDeltaV(component, dv, step);
            campaign.setPilotThrust((float) (dv > 0 ? dx / dv * throttle : 0), (float) (dv > 0 ? dy / dv * throttle : 0), false);
            tick();
        }
    }

    private static double speed(LocalPhysicalKinematics state) {
        return Math.hypot(state.velocityXMps(), state.velocityYMps());
    }
}
