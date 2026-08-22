package com.spacesim.world;

import com.spacesim.world.FactionActorObservationSnapshot.ActorObservation;
import com.spacesim.world.FactionActorObservationSnapshot.Domain;
import com.spacesim.world.FactionActorObservationSnapshot.InterestKind;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationChannel;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationEvidence;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FactionInterestResolverTest {

    @Test
    void sameBoundedEvidenceProducesByteIdenticalDecisionIndependentOfInputOrder() {
        ActorObservation dependencyA = observation(
                Domain.ECONOMIC,
                InterestKind.SUPPLY_DEPENDENCY,
                "resource.propellant",
                7200,
                ObservationChannel.ECONOMIC_LEDGER,
                "ledger.a",
                100L,
                150L);
        ActorObservation dependencyB = observation(
                Domain.ECONOMIC,
                InterestKind.SUPPLY_DEPENDENCY,
                "resource.propellant",
                6100,
                ObservationChannel.OWNED_ASSET_REPORT,
                "freight.a",
                98L,
                140L);
        ActorObservation market = observation(
                Domain.ECONOMIC,
                InterestKind.MARKET_ACCESS,
                "faction.trade",
                7200,
                ObservationChannel.ECONOMIC_LEDGER,
                "market.access.a",
                100L,
                130L);

        FactionActorObservationSnapshot left = new FactionActorObservationSnapshot(
                "faction.alpha", 100L, List.of(dependencyA, market, dependencyB), List.of(), List.of(), List.of());
        FactionActorObservationSnapshot right = new FactionActorObservationSnapshot(
                "faction.alpha", 100L, List.of(dependencyB, dependencyA, market), List.of(), List.of(), List.of());

        FactionInterestResolver.DecisionTrace leftTrace = FactionInterestResolver.resolve(left);
        FactionInterestResolver.DecisionTrace rightTrace = FactionInterestResolver.resolve(right);

        assertArrayEquals(leftTrace.canonicalBytes(), rightTrace.canonicalBytes());
        assertEquals(InterestKind.SUPPLY_DEPENDENCY, leftTrace.primaryInterest().orElseThrow().kind());
        assertEquals(2, leftTrace.primaryInterest().orElseThrow().supportingObservations().size());
    }

    @Test
    void staleHiddenLikeEvidenceCannotAffectDecisionUntilFreshAllowedReportExists() {
        ActorObservation currentDeficit = observation(
                Domain.ECONOMIC,
                InterestKind.RESOURCE_DEFICIT,
                "resource.metals",
                3500,
                ObservationChannel.ECONOMIC_LEDGER,
                "stock.current",
                20L,
                30L);
        ActorObservation staleBorder = observation(
                Domain.SECURITY,
                InterestKind.BORDER_SECURITY,
                "system.frontier",
                9000,
                ObservationChannel.INTELLIGENCE_REPORT,
                "intel.old",
                5L,
                10L);
        FactionActorObservationSnapshot beforeFreshReport = new FactionActorObservationSnapshot(
                "faction.alpha", 20L, List.of(currentDeficit), List.of(), List.of(staleBorder), List.of());

        assertEquals(
                InterestKind.RESOURCE_DEFICIT,
                FactionInterestResolver.resolve(beforeFreshReport).primaryInterest().orElseThrow().kind());

        ActorObservation freshBorder = observation(
                Domain.SECURITY,
                InterestKind.BORDER_SECURITY,
                "system.frontier",
                9000,
                ObservationChannel.INTELLIGENCE_REPORT,
                "intel.new",
                20L,
                40L);
        FactionActorObservationSnapshot afterFreshReport = new FactionActorObservationSnapshot(
                "faction.alpha", 20L, List.of(currentDeficit), List.of(), List.of(staleBorder, freshBorder), List.of());

        assertEquals(
                InterestKind.BORDER_SECURITY,
                FactionInterestResolver.resolve(afterFreshReport).primaryInterest().orElseThrow().kind());
    }

    @Test
    void allStage21AInterestFamiliesAreRepresentableOnlyInAllowedDomains() {
        List<ActorObservation> economic = List.of(
                observation(Domain.ECONOMIC, InterestKind.SUPPLY_DEPENDENCY, "supplier", 1000,
                        ObservationChannel.ECONOMIC_LEDGER, "e1", 10, 20),
                observation(Domain.ECONOMIC, InterestKind.MARKET_ACCESS, "market", 2000,
                        ObservationChannel.ECONOMIC_LEDGER, "e2", 10, 20),
                observation(Domain.ECONOMIC, InterestKind.ROUTE_EXPOSURE, "route", 3000,
                        ObservationChannel.ECONOMIC_LEDGER, "e3", 10, 20),
                observation(Domain.ECONOMIC, InterestKind.RESOURCE_DEFICIT, "resource", 4000,
                        ObservationChannel.ECONOMIC_LEDGER, "e4", 10, 20));
        List<ActorObservation> territorial = List.of(
                observation(Domain.TERRITORIAL, InterestKind.BORDER_SECURITY, "border", 5000,
                        ObservationChannel.TERRITORY_LEDGER, "t1", 10, 20),
                observation(Domain.TERRITORIAL, InterestKind.TERRITORIAL_OPPORTUNITY, "system", 6000,
                        ObservationChannel.DISCOVERY_KNOWLEDGE, "t2", 10, 20));
        List<ActorObservation> diplomatic = List.of(
                observation(Domain.DIPLOMATIC, InterestKind.TREATY_OBLIGATION, "treaty", 7000,
                        ObservationChannel.DIPLOMATIC_REGISTRY, "d1", 10, -1));

        FactionActorObservationSnapshot snapshot = new FactionActorObservationSnapshot(
                "faction.alpha", 10L, economic, territorial, List.of(), diplomatic);

        assertEquals(
                Arrays.stream(InterestKind.values()).sorted().toList(),
                FactionInterestResolver.resolve(snapshot).orderedEvidence().stream()
                        .map(FactionInterestEvidence::kind)
                        .distinct()
                        .sorted()
                        .toList());
        assertThrows(IllegalArgumentException.class, () -> observation(
                Domain.DIPLOMATIC,
                InterestKind.RESOURCE_DEFICIT,
                "illegal",
                1000,
                ObservationChannel.DIPLOMATIC_REGISTRY,
                "bad",
                10,
                20));
    }

    private static ActorObservation observation(
            Domain domain,
            InterestKind kind,
            String target,
            int severity,
            ObservationChannel channel,
            String provenance,
            long observedAt,
            long freshUntil) {
        return new ActorObservation(
                domain,
                kind,
                target,
                severity,
                new ObservationEvidence(channel, provenance, observedAt, freshUntil));
    }
}
