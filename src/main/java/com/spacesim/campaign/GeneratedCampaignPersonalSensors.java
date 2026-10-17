package com.spacesim.campaign;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.SensorKnowledgeComponent;
import com.spacesim.ship.*;
import com.spacesim.world.FleetLocationKind;
import java.util.List;
import java.util.ArrayList;

/** Fitted personal passive measurements and explicitly confirmed radar pulses. */
public final class GeneratedCampaignPersonalSensors {
    /** Nominal radar reflection sample of the existing authored station footprint; no emitted power. */
    public static final String STATION_RADAR_SIGNATURE_VERSION = "stage23b.station-footprint-radar.v1";
    private static final ProductionEngineeringRuntimeResolver ENGINEERING = new ProductionEngineeringRuntimeResolver();
    private static final ShipSensorEngineeringAdapter FITTED = new ShipSensorEngineeringAdapter();
    private static final ShipObservationService SENSOR = new ShipObservationService();
    private static final SensorKnowledgeRuntime KNOWLEDGE = new SensorKnowledgeRuntime();
    private static final com.spacesim.world.calibration.Stage20StationPhysicalGeometryProfile STATION_GEOMETRY =
            com.spacesim.world.calibration.Stage20StationPhysicalGeometryProfile.deriveCurrent();
    private GeneratedCampaignPersonalSensors() { }

    /**
     * Performs a one-second passive review only after an actual completed world tick.
     * @param campaign owning campaign
     * @param tick completed world tick
     * @return newly detected local contact IDs, without names or affiliation claims
     */
    public static List<Long> review(Stage228CampaignAuthority campaign, long tick) {
        var p = campaign.playerState().orElse(null);
        long period = Math.max(1, Math.round(1d / campaign.coordinator().session().fixedStepSeconds()));
        if (p == null || p.activeFleetId() == null || tick % period != 0) return List.of();
        return observe(campaign, tick, false, 0);
    }

    /**
     * Commits one pulse through the authority's completed physical interval and shared electrical budget.
     * @param campaign owning campaign
     * @return newly detected local contacts
     */
    static List<Long> pulse(Stage228CampaignAuthority campaign) {
        return pulse(campaign, 0);
    }

    static List<Long> pulse(Stage228CampaignAuthority campaign, double rechargePowerW) {
        return pulse(campaign, rechargePowerW, null);
    }

    static List<Long> pulse(Stage228CampaignAuthority campaign, double rechargePowerW, ShipEngineeringRuntime.TickResult propulsion) {
        return observe(campaign, campaign.coordinator().runtime().world().getAuthoritativeWorldTick(), true, rechargePowerW, propulsion);
    }

    private static List<Long> observe(Stage228CampaignAuthority campaign, long tick, boolean active, double rechargePowerW) {
        return observe(campaign, tick, active, rechargePowerW, null);
    }

    private static List<Long> observe(Stage228CampaignAuthority campaign, long tick, boolean active, double rechargePowerW,
            ShipEngineeringRuntime.TickResult propulsion) {
        var p = campaign.playerState().orElse(null);
        if (p == null || p.activeFleetId() == null) {
            if (active) throw new IllegalStateException("No local fitted owned ship for active scanning");
            return List.of();
        }
        var r = campaign.coordinator().runtime(); var world = r.world();
        var observer = world.findFleet(p.activeFleetId()).orElse(null);
        if (observer == null || observer.locationKind() != FleetLocationKind.IN_SYSTEM
                || world.findFleetJump(observer.id()).isPresent()) {
            if (active) throw new IllegalStateException("Active scanning requires a local ship without a jump");
            return List.of();
        }
        var session = world.findSession(observer.systemId()).orElseThrow();
        var entity = session.getEntityRegistry().require(observer.localEntityId());
        var fitted = entity.getComponent(EngineeringComponent.class);
        if (fitted == null) {
            if (active) throw new IllegalStateException("Active scanning requires fitted equipment");
            return List.of();
        }
        var own = ENGINEERING.derive(fitted);
        var sensors = own.continuousPowerMarginW() >= 0 ? FITTED.derive(own).sensors().stream()
                .filter(s -> active == (s.definition().mode() == SensorDefinition.Mode.ACTIVE_RADAR)).toList() : List.<ShipSensorEngineeringAdapter.FittedSensor>of();
        var physical = r.arrival().materialization(observer.systemId()).physicalState(observer.localEntityId()).orElse(null);
        if (physical == null || sensors.isEmpty()) {
            if (active) throw new IllegalStateException("No working active radar or local physical state");
            return List.of();
        }
        var grants = new java.util.HashMap<String, ShipObservationService.EngineeringGrant>();
        if (active) {
            // Every mode shares one budget; a denied later mode cannot partially spend live energy/heat.
            var candidate = new EngineeringComponent(fitted.fit, fitted.runtimeState, fitted.instanceState);
            var service = ENGINEERING.incrementalOperations(candidate);
            double duration = campaign.coordinator().session().fixedStepSeconds();
            var budget = propulsion == null ? service.beginInterval(candidate, duration)
                    : service.beginAfterPropulsion(candidate, duration, propulsion, rechargePowerW);
            if (propulsion == null && rechargePowerW > 0) {
                var reservation = service.grantAndCommit(candidate, sensors.get(0).mountId(), rechargePowerW, 0, duration, budget);
                if (!reservation.committed()) throw new IllegalStateException("Idle recharge exhausts the interval power budget");
            }
            for (var sensor : sensors) {
                var plan = SENSOR.planOperation(sensor, SensorRuntimeState.nominal());
                var grant = service.grantAndCommit(candidate, sensor.mountId(), plan.requiredPowerW(), plan.requiredHeatW(), duration, budget);
                if (!grant.committed()) throw new IllegalStateException("Radar pulse has insufficient power or thermal capacity");
                grants.put(sensor.mountId() + ':' + sensor.definition().id(), grant.grant());
            }
            fitted.setRuntimeState(candidate.runtimeState);
        }
        var knowledge = entity.getComponent(SensorKnowledgeComponent.class);
        if (knowledge == null) { knowledge = new SensorKnowledgeComponent(); entity.add(knowledge); }
        double now = tick * (double) campaign.coordinator().session().fixedStepSeconds();
        var prior = knowledge.tracks().stream().map(TrackState::targetId).collect(java.util.stream.Collectors.toSet());
        knowledge.pruneMeasurements(now, DatalinkState.local().maxMeasurementAgeSeconds());
        var from = sensorPosition(physical.position());
        var detected = new ArrayList<Long>();
        for (var target : world.getFleetPlacements()) {
            if (target.locationKind() != FleetLocationKind.IN_SYSTEM || !target.systemId().equals(observer.systemId())
                    || p.ownedFleetIds().contains(target.id())) continue;
            var targetEntity = session.getEntityRegistry().require(target.localEntityId());
            var engineering = targetEntity.getComponent(EngineeringComponent.class);
            var position = r.arrival().materialization(target.systemId()).physicalState(target.localEntityId()).orElse(null);
            if (engineering == null || position == null) continue;
            var signature = FITTED.derive(ENGINEERING.derive(engineering)).staticSignature();
            long targetId = target.localEntityId().value();
            for (var sensor : sensors) {
                var plan = SENSOR.planOperation(sensor, SensorRuntimeState.nominal());
                var observation = SENSOR.execute(plan, grants.getOrDefault(sensor.mountId() + ':' + sensor.definition().id(), new ShipObservationService.EngineeringGrant(0, 0)),
                        sensor, SensorRuntimeState.nominal(), observer.localEntityId().value(), targetId,
                        from, sensorPosition(position.position()),
                        signature, ElectronicWarfareState.empty(), now);
                if (observation.measurement().isPresent()) KNOWLEDGE.receiveLocal(knowledge, observation.measurement().orElseThrow());
            }
            var track = KNOWLEDGE.updateTarget(knowledge, targetId, DatalinkState.local(), ShipSensorRuntime.TrackQualityPolicy.defaultPolicy(), now);
            if (track.isPresent() && track.orElseThrow().lastMeasurementSeconds() == now && !prior.contains(targetId)) detected.add(targetId);
        }
        if (active) for (var endpoint : r.infrastructure().endpoints()) {
            if (!endpoint.systemId().equals(observer.systemId())) continue;
            var reference = campaign.pilotMarketReference(endpoint.stationId()).orElse(null);
            if (reference == null) continue;
            long targetId = reference.entityId().value();
            var geometry = STATION_GEOMETRY.stationDesign(endpoint.stationArchetypeId());
            // Nominal authored reflection model: one percent of the existing footprint area.
            // It emits no heat or radio power and discloses no station payload or classification.
            var signature = new SignatureState(0, 0,
                    .01 * geometry.footprintLengthM() * geometry.footprintWidthM(), 0, 0, 0);
            for (var sensor : sensors) {
                var plan = SENSOR.planOperation(sensor, SensorRuntimeState.nominal());
                var observation = SENSOR.execute(plan, grants.get(sensor.mountId() + ':' + sensor.definition().id()),
                        sensor, SensorRuntimeState.nominal(), observer.localEntityId().value(), targetId,
                        from, sensorPosition(endpoint.position()), signature, ElectronicWarfareState.empty(), now);
                if (observation.measurement().isPresent()) KNOWLEDGE.receiveLocal(knowledge, observation.measurement().orElseThrow());
            }
            var track = KNOWLEDGE.updateTarget(knowledge, targetId, DatalinkState.local(), ShipSensorRuntime.TrackQualityPolicy.defaultPolicy(), now);
            if (track.isPresent() && track.orElseThrow().lastMeasurementSeconds() == now && !prior.contains(targetId)) detected.add(targetId);
        }
        return List.copyOf(detected);
    }

    private static ShipSensorRuntime.Position2d sensorPosition(com.spacesim.world.LocalPhysicalPosition position) {
        var d = new com.spacesim.world.LocalPhysicalPosition(0, 0, 0, 0).displacementTo(position);
        return new ShipSensorRuntime.Position2d(d.deltaXM(), d.deltaYM());
    }
}
