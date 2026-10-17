package com.spacesim.ui;

import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.content.ship.Stage228SmallCraftEngineeringCatalogLoader;
import com.spacesim.world.FleetId;
import com.spacesim.world.SmallCraftHangarCapacity.BayDefinition;
import com.spacesim.world.SmallCraftHangarCapacity.BayId;
import java.util.Optional;
import java.util.TreeMap;

/** Live read-only carrier projection bounded by personal ownership and installed physical bays. */
public final class GeneratedCampaignCarrierUi {
    private GeneratedCampaignCarrierUi() { }

    /**
     * Binds presentation to the current campaign instead of a disconnected empty carrier source.
     * @param campaign current owning composition root
     * @return lazy read-only source; no craft, bays, assignments or missions are created
     */
    public static GeneratedWorldUiModel.CarrierUiSource source(Stage228CampaignAuthority campaign) {
        java.util.Objects.requireNonNull(campaign);
        return new GeneratedWorldUiModel.CarrierUiSource() {
            @Override public Optional<CarrierOperationsUiProjection.CarrierView> find(FleetId fleetId) {
                return GeneratedCampaignCarrierUi.find(campaign, fleetId);
            }
            @Override public java.util.List<GeneratedWorldUiSnapshot.LocalObjectView> localObjects(com.spacesim.world.StarSystemId systemId) {
                return GeneratedCampaignCarrierUi.localObjects(campaign, systemId);
            }
        };
    }

    /**
     * Reads only deployed individual craft with explicit personal property rights.
     * Current hangar availability does not teleport or hide its already deployed local position.
     * @param campaign actual owning campaign
     * @param systemId displayed local system
     * @return immutable own-craft map markers without foreign mission target disclosure
     */
    public static java.util.List<GeneratedWorldUiSnapshot.LocalObjectView> localObjects(Stage228CampaignAuthority campaign,
            com.spacesim.world.StarSystemId systemId) {
        var visible = ownedCraftIds(campaign);
        var physical = campaign.localFlightState();
        var result = new java.util.ArrayList<GeneratedWorldUiSnapshot.LocalObjectView>();
        for (var flight : physical.flights()) {
            if (!flight.systemId().equals(systemId) || !visible.contains(flight.craftId())) continue;
            var individual = campaign.smallCraft().find(flight.craftId()).orElse(null);
            if (individual == null) continue;
            var footprint = campaign.smallCraft().physicalFootprint(flight.craftId());
            result.add(new GeneratedWorldUiSnapshot.LocalObjectView("small-craft:" + flight.craftId().value(),
                    GeneratedWorldUiSnapshot.ObjectKind.LOCAL_ENTITY, "Аппарат №" + flight.craftId().value(), "Собственный аппарат в полёте",
                    systemId, flight.physical().position(), individual.stableFactionId(), "Личная собственность", null,
                    java.util.List.of(GeneratedWorldUiSnapshot.InfoSection.of("Физический полёт",
                            "Положение", "Текущее физическое состояние собственного аппарата",
                            "Скорость", String.format(java.util.Locale.ROOT, "%.3f м/с", Math.hypot(flight.physical().velocityXMps(), flight.physical().velocityYMps())),
                            "Масса", String.format(java.util.Locale.ROOT, "%.3f кг", footprint.currentMassKg()),
                            "Такт состояния", Long.toString(physical.lastProcessedTick()), "Состояние", "Вне ангара")),
                    footprint.envelopeM().lengthM(), footprint.envelopeM().widthM()));
        }
        return java.util.List.copyOf(result);
    }

    /**
     * Resolves current personal inventory access independently of spectator faction selection.
     * @param campaign actual owning campaign
     * @return live craft with explicit personal property rights
     */
    public static java.util.Set<com.spacesim.world.SmallCraftId> ownedCraftIds(Stage228CampaignAuthority campaign) {
        return campaign.personalCraftOwnership().stream().filter(id -> campaign.smallCraft().find(id).isPresent())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * Projects only an existing personally owned local carrier with a consistent legal operator.
     * @param campaign current owning campaign
     * @param fleetId candidate carrier identity
     * @return current physical operations, or empty without access or installed bays
     */
    public static Optional<CarrierOperationsUiProjection.CarrierView> find(Stage228CampaignAuthority campaign, FleetId fleetId) {
        var p = campaign.playerState().orElse(null);
        if (p == null || !p.ownedFleetIds().contains(fleetId)) return Optional.empty();
        var wing = campaign.carrierWings().stream().filter(w -> w.carrierFleetId().equals(fleetId)).findFirst();
        if (wing.isEmpty()) return Optional.empty();
        var canonical = Catalogs.CANONICAL;
        var physical = com.spacesim.campaign.GeneratedCampaignCarrierBays.resolve(campaign, wing.orElseThrow());
        if (physical.isEmpty()) return Optional.empty();
        var bays = new TreeMap<BayId, BayDefinition>();
        physical.forEach(b -> bays.put(b.id(), b));
        // Persisted AI missions do not confer personal target knowledge. Only already observed
        // references can become visible; unknown references remain explicitly hidden.
        var knownReferences = campaign.coordinator().runtime().discoveryState()
                .knowledgeFor(com.spacesim.world.Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID).entries().stream()
                .filter(k -> !k.object().resourceObject() && k.knownLocation().isPresent()).map(k -> k.object().objectId())
                .collect(java.util.stream.Collectors.toSet());
        p.ownedFleetIds().forEach(id -> knownReferences.add(id.toString()));
        knownReferences.add(wing.orElseThrow().hostStableId());
        return Optional.of(new CarrierOperationsUiProjection(canonical).capture(
                wing.orElseThrow(), campaign.smallCraft(), campaign.hangars(), campaign.flightDeck(), campaign.missions(), bays,
                mission -> (mission.target().kind() == com.spacesim.world.SmallCraftMissionState.TargetKind.HOST
                        || mission.target().kind() == com.spacesim.world.SmallCraftMissionState.TargetKind.OWNED_ASSET)
                        && knownReferences.contains(mission.target().referenceId())));
    }

    private static final class Catalogs {
        private static final com.spacesim.content.ship.ShipEngineeringCatalog CANONICAL = Stage228SmallCraftEngineeringCatalogLoader.loadDefault();
    }
}
