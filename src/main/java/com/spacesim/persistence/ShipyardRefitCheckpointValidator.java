package com.spacesim.persistence;

import com.spacesim.economy.ShipyardRefitQueueState;
import com.spacesim.economy.ShipyardRepairQueueState;
import com.spacesim.player.PlayerState;
import com.spacesim.world.FleetId;

/** Composed ownership, original physical ship, installed yard and interval checks for refits. */
final class ShipyardRefitCheckpointValidator {
    private ShipyardRefitCheckpointValidator() { }

    static void validate(ShipyardRefitQueueState queue, Stage20GeneratedWorldRuntimePersistentState stage20,
            PlayerState player, long tick, ShipyardRepairQueueState repairs) {
        if (queue.lastProcessedTick() > tick || !queue.orders().isEmpty() && queue.lastProcessedTick() != tick)
            throw new IllegalArgumentException("Refit watermark must match actual completed time");
        for (var order : queue.orders()) {
            var fleetId = new FleetId(order.fleetId());
            var fleet = stage20.worldState().fleets().stream().filter(f -> f.id().equals(fleetId)).findFirst().orElse(null);
            if (player == null || !player.ownedFleetIds().contains(fleetId)
                    || fleet == null || fleet.localEntityId().value() != order.assetId())
                throw new IllegalArgumentException("Refit requires the exact persisted personal ship owner");
            if (repairs.orders().stream().anyMatch(repair -> repair.fleetId() == order.fleetId()))
                throw new IllegalArgumentException("The same physical ship cannot undergo repair and refit simultaneously");
            boolean owner = player.ownedStations().stream().anyMatch(ref -> stage20.worldState().systems().stream()
                    .filter(s -> s.systemId().equals(ref.systemId())).flatMap(s -> s.simulationState().entities().stream())
                    .anyMatch(e -> e.id().equals(ref.stationEntityId()) && e.market() != null && e.identity() != null
                            && (e.identity().name().equals("Generated market " + order.stationId())
                            || e.identity().name().equals("Generated market v2 " + order.stationId()))));
            if (owner && order.servicePayment() != null) throw new IllegalArgumentException("Owner refit cannot hold foreign service money");
            if (!owner) {
                if (order.servicePayment() == null) throw new IllegalArgumentException("Foreign refit requires held money");
                ShipyardRepairCheckpointValidator.validatePaidService(order.stationId(), order.yardInstanceId(),
                        order.yardDefinitionId(), order.servicePayment().sellerFactionId(), stage20, player, fleet);
            }
            for (var input : order.reservedUsedModulesByTargetMount().values())
                if (input.ownerActorId() != null && !input.ownerActorId().equals(com.spacesim.world.Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID)
                        || !owner && input.ownerActorId() == null)
                    throw new IllegalArgumentException("Used refit input requires actual personal equipment ownership");
            boolean installed = stage20.campaign().industrialState().yards().stream().anyMatch(y ->
                    y.stationId().equals(order.stationId()) && y.state().yardInstanceId().equals(order.yardInstanceId())
                            && y.state().yardDefinitionId().equals(order.yardDefinitionId()));
            if (!installed) throw new IllegalArgumentException("Refit requires its exact installed physical yard");
            var entity = stage20.worldState().systems().stream().filter(s -> s.systemId().equals(fleet.systemId()))
                    .flatMap(s -> s.simulationState().entities().stream())
                    .filter(e -> e.id().value() == order.assetId()).findFirst().orElse(null);
            if (entity == null || entity.engineering() == null
                    || !EngineeringStatePersistenceMapper.restore(entity.engineering()).fit.equals(order.sourceFit()))
                throw new IllegalArgumentException("Pending refit source must match the actual installed fitting");
        }
    }
}
