package com.spacesim.world;

import com.spacesim.world.Stage21HImperialGoldSlice.ContractBlueprint;
import com.spacesim.world.Stage21HNpcMissionState.MissionTemplate;
import com.spacesim.world.Stage21HNpcMissionState.NpcRole;
import com.spacesim.world.Stage21HNpcMissionState.ObjectiveAuthority;
import com.spacesim.world.Stage21HNpcMissionState.ObjectiveKind;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage21HImperialGoldSliceTest {

    @Test
    void goldSliceHasExactlySixPersistentRolesEightPhysicalContractsAndFourLivingWorldSteps() {
        StarSystemId posting = new StarSystemId(1L);
        var contacts = Stage21HImperialGoldSlice.recurringImperialContacts(posting);
        List<ContractBlueprint> contracts = Stage21HImperialGoldSlice.minimumContractBlueprints();
        var chain = Stage21HImperialGoldSlice.imperialChain();

        assertEquals(6, contacts.size());
        assertEquals(EnumSet.allOf(NpcRole.class),
                contacts.stream().map(value -> value.role()).collect(Collectors.toSet()));
        assertTrue(contacts.stream().allMatch(value ->
                value.factionContentId().equals(Stage21HImperialGoldSlice.IMPERIAL_FACTION_ID)
                        && value.locationSystemId().equals(posting)
                        && value.knowledge().isEmpty()));

        assertEquals(8, contracts.size());
        assertEquals(EnumSet.complementOf(EnumSet.of(MissionTemplate.IMPERIAL_ACCESS_NEGOTIATION)),
                contracts.stream().map(ContractBlueprint::template).collect(Collectors.toSet()));
        assertEquals(ObjectiveKind.FREIGHT_ORDER_DELIVERED_KG_AT_LEAST,
                blueprint(contracts, MissionTemplate.EMERGENCY_SUPPLY_DELIVERY).objectiveKind());
        assertEquals(ObjectiveKind.ESCORT_FLEETS_PRESENT_IN_SYSTEM,
                blueprint(contracts, MissionTemplate.CONVOY_ESCORT).objectiveKind());
        assertEquals(ObjectiveKind.FLEET_REACTION_MASS_KG_AT_LEAST,
                blueprint(contracts, MissionTemplate.STRANDED_FLEET_RESCUE_REFUEL).objectiveKind());
        assertEquals(ObjectiveAuthority.INDUSTRY,
                blueprint(contracts, MissionTemplate.DERELICT_INVESTIGATION_RECOVERY).objectiveAuthority());
        assertEquals(ObjectiveKind.DERELICT_DISCOVERED_AND_SALVAGED_KG_AT_LEAST,
                blueprint(contracts, MissionTemplate.DERELICT_INVESTIGATION_RECOVERY).objectiveKind());

        assertEquals(4, chain.size());
        assertEquals(List.of(1, 2, 3, 4), chain.stream().map(value -> value.ordinal()).toList());
        assertEquals(Set.of(
                        MissionTemplate.EMERGENCY_SUPPLY_DELIVERY,
                        MissionTemplate.CONSTRUCTION_REPAIR_INPUT_DELIVERY,
                        MissionTemplate.IMPERIAL_ACCESS_NEGOTIATION,
                        MissionTemplate.CONVOY_ESCORT),
                chain.stream().map(value -> value.missionTemplate()).collect(Collectors.toSet()));
        assertTrue(chain.stream().allMatch(value ->
                value.stepId().startsWith(Stage21HImperialGoldSlice.IMPERIAL_CHAIN_ID + ".")
                        && !value.requiredLivingWorldSignal().isBlank()
                        && !value.adaptationRule().isBlank()));
    }

    private static ContractBlueprint blueprint(List<ContractBlueprint> contracts, MissionTemplate template) {
        return contracts.stream().filter(value -> value.template() == template).findFirst().orElseThrow();
    }
}
