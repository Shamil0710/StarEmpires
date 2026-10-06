package com.spacesim.persistence;

import com.spacesim.economy.ShipyardRepairQueueState;
import com.spacesim.player.PlayerState;
import com.spacesim.world.FleetId;

/** Personal repair ownership, ship identity and actual completed-time checkpoint checks. */
final class ShipyardRepairCheckpointValidator {
    private ShipyardRepairCheckpointValidator() { }
    static void validate(ShipyardRepairQueueState queue, Stage20GeneratedWorldRuntimePersistentState stage20,
            PlayerState player, long tick) {
        if (queue.lastProcessedTick() > tick || !queue.orders().isEmpty() && queue.lastProcessedTick() != tick)
            throw new IllegalArgumentException("Repair watermark must match actual completed time");
        for (var order : queue.orders()) {
            var fleet = stage20.worldState().fleets().stream().filter(f -> f.id().equals(new FleetId(order.fleetId()))).findFirst().orElse(null);
            if (player == null || !player.ownedFleetIds().contains(new FleetId(order.fleetId()))
                    || fleet == null || fleet.localEntityId().value() != order.assetId())
                throw new IllegalArgumentException("Repair requires the exact persisted personal ship owner");
            boolean owner = player.ownedStations().stream().anyMatch(ref -> stage20.worldState().systems().stream()
                    .filter(s -> s.systemId().equals(ref.systemId())).flatMap(s -> s.simulationState().entities().stream())
                    .anyMatch(e -> e.id().equals(ref.stationEntityId()) && e.market() != null && e.identity() != null
                            && (e.identity().name().equals("Generated market " + order.stationId())
                            || e.identity().name().equals("Generated market v2 " + order.stationId()))));
            if (owner && order.servicePayment() != null)
                throw new IllegalArgumentException("Owner work cannot hold a foreign service payment");
            if (!owner) validatePaidOperator(order, stage20, player, fleet);
        }
    }

    private static void validatePaidOperator(ShipyardRepairQueueState.RepairOrder order,
            Stage20GeneratedWorldRuntimePersistentState stage20, PlayerState player,
            com.spacesim.world.FleetPlacementState fleet) {
        var payment = order.servicePayment();
        if (payment == null) throw new IllegalArgumentException("Foreign repair requires its held payment");
        validatePaidService(order.stationId(), order.yardInstanceId(), order.yardDefinitionId(), payment.sellerFactionId(), stage20, player, fleet);
    }

    static void validatePaidService(String stationId, String yardInstanceId, String yardDefinitionId, String seller,
            Stage20GeneratedWorldRuntimePersistentState stage20, PlayerState player,
            com.spacesim.world.FleetPlacementState fleet) {
        validateCivilianBerth(stationId, seller, stage20, player, fleet);
        if (stage20.campaign().industrialState().yards().stream().noneMatch(y ->
                y.stationId().equals(stationId) && y.state().yardInstanceId().equals(yardInstanceId)
                        && y.state().yardDefinitionId().equals(yardDefinitionId)))
            throw new IllegalArgumentException("Paid service requires its exact installed yard");
    }

    static void validateCivilianBerth(String stationId, String seller,
            Stage20GeneratedWorldRuntimePersistentState stage20, PlayerState player,
            com.spacesim.world.FleetPlacementState fleet) {
        if (!fleet.id().equals(player.activeFleetId()) || player.dockedAt() == null
                || !player.dockedAt().systemId().equals(fleet.systemId())
                || fleet.locationKind() != com.spacesim.world.FleetLocationKind.IN_SYSTEM
                || stage20.worldState().fleetJumps().stream().anyMatch(j -> j.fleetId().equals(fleet.id())))
            throw new IllegalArgumentException("Foreign repair requires its held payment and actual personal berth");
        var station = stage20.worldState().systems().stream().filter(s -> s.systemId().equals(fleet.systemId()))
                .flatMap(s -> s.simulationState().entities().stream()).filter(e -> e.id().equals(player.dockedAt().entityId())
                        && e.market() != null && e.identity() != null
                        && (e.identity().name().equals("Generated market " + stationId)
                        || e.identity().name().equals("Generated market v2 " + stationId))).findFirst().orElse(null);
        var identity = stage20.worldState().factionIdentities().stream().filter(i -> i.stableFactionId().equals(seller)).findFirst();
        var authored = identity.isPresent() ? null : AuthoredFactions.CONTENT.findFaction(seller);
        Integer runtimeId = identity.map(com.spacesim.world.WorldFactionIdentityState::runtimeFactionId)
                .orElse(authored == null ? null : authored.runtimeId());
        if (station == null || station.faction() == null || runtimeId == null || station.faction().factionId() != runtimeId
                || stage20.worldState().factions().stream().noneMatch(f -> f.factionContentId().equals(seller)))
            throw new IllegalArgumentException("Paid repair operator must match the actual legal station owner and treasury");
        var rows = stage20.campaign().materializedWorld().worldRows();
        boolean specialization = rows.stream().filter(r -> r.domain().equals("INDUSTRIAL_SPECIALIZATION"))
                .anyMatch(r -> r.values().size() >= 4 && r.values().get(0).equals(Long.toString(fleet.systemId().value()))
                        && r.values().get(1).equals(stationId) && r.values().get(2).equals(seller));
        boolean civilian = rows.stream().filter(r -> r.domain().equals("INFRASTRUCTURE_PLACEMENT"))
                .anyMatch(r -> r.stableId().equals(fleet.systemId().value() + ":" + stationId)
                        && r.values().size() >= 3
                        && com.spacesim.campaign.GeneratedCampaignStationSalePolicy.priceMilliCredits(r.values().get(2)) > 0);
        if (!specialization || !civilian)
            throw new IllegalArgumentException("Service requires its actual civilian operator");
        var placement = rows.stream().filter(r -> r.domain().equals("INFRASTRUCTURE_PLACEMENT")
                && r.stableId().equals(fleet.systemId().value() + ":" + stationId)).findFirst().orElseThrow();
        if (placement.values().size() < 7) throw new IllegalArgumentException("Paid repair berth has no exact physical position");
        var values = placement.values();
        var berth = new com.spacesim.world.LocalPhysicalPosition(Long.parseLong(values.get(3)), Long.parseLong(values.get(4)),
                Double.parseDouble(values.get(5)), Double.parseDouble(values.get(6)));
        var exact = stage20.localFleetPhysicalStates().stream().filter(f -> f.fleetId().equals(fleet.id())
                && f.systemId().equals(fleet.systemId())).findFirst().orElse(null);
        if (exact == null || exact.physicalState().position().distanceTo(berth) > 1000
                || Math.hypot(exact.physicalState().velocityXMps(), exact.physicalState().velocityYMps()) > 1)
            throw new IllegalArgumentException("Paid repair checkpoint must retain actual physical berth contact");
    }
    private static final class AuthoredFactions {
        private static final com.spacesim.content.ContentCatalog CONTENT = com.spacesim.content.ContentCatalogLoader.loadDefault();
    }
}
