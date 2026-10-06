package com.spacesim.campaign;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.Stage18ExtractionCatalog.ExtractionEnvironment;
import com.spacesim.content.ship.Stage22CivilianMiningEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.persistence.Stage20SourceSupplyMaterializer.MaterializedSource;
import com.spacesim.ship.ShipEngineeringRuntime;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.world.LocalPhysicalKinematics;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
class GeneratedCampaignPersonalMiningTest {
    @Test void miningUsesOnlyCompletedTicksAndContinuesExactlyAfterSaveLoad() {
        var c = equipped(); var source = localSource(c); position(c, LocalPhysicalKinematics.stationary(source.position()));
        c.coordinator().runtime().world().activateSystem(source.systemId());
        double reserve = source.sourceState().remainingAccessibleMassKg();
        var baseline = c.captureState(); var start = c.previewPilotAction("START_MINING", source.sourceId(), "", 1);
        var rows = com.spacesim.ui.GeneratedCampaignMiningUi.rows(c);
        assertTrue(rows.stream().anyMatch(row -> row.selection().stableId().equals("pilot-mining|" + source.sourceId())));
        assertTrue(rows.stream().noneMatch(row -> row.sections().toString().contains(Double.toString(reserve))));
        assertTrue(start.allowed()); assertEquals(baseline, c.captureState());
        c.submitPilotAction(start); assertEquals(reserve, source.sourceState().remainingAccessibleMassKg());
        assertTrue(com.spacesim.ui.GeneratedCampaignMiningUi.rows(c).stream().anyMatch(row -> row.selection().stableId().startsWith("pilot-mining-stop|")));
        assertEquals(0, c.coordinator().runtime().freight().capture().cargoLots().size());
        assertThrows(IllegalStateException.class, () -> c.submitPilotAction(start));
        float step = c.coordinator().session().fixedStepSeconds();
        var assigned = c.captureState(); c.advanceFrame(0); assertEquals(assigned, c.captureState());
        var object = new com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectRef(source.systemId(),
                com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectKind.RESOURCE_OCCURRENCE, source.sourceId());
        var beforeKnowledge = c.coordinator().runtime().discoveryState();
        assertTrue(beforeKnowledge.knowledgeFor(com.spacesim.world.Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID).knowledge(object).isEmpty());
        assertPersonalInspectorHasNoPhysicalReserve(c, source);
        c.advanceFrame(step);
        var knowledge = c.coordinator().runtime().discoveryState();
        var sample = knowledge.knowledgeFor(com.spacesim.world.Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID).knowledge(object).orElseThrow();
        assertEquals(com.spacesim.world.Stage20DiscoveryKnowledgeState.ResourceKnowledgeLevel.RESOURCE_INDICATION, sample.resourceKnowledge().level());
        assertTrue(sample.resourceKnowledge().estimate().isEmpty());
        assertTrue(sample.resourceKnowledge().resourceFamilyId().isEmpty());
        assertEquals(source.position(), sample.knownLocation().orElseThrow());
        assertEquals(step, sample.firstObservedSeconds(), 1e-9);
        assertPersonalInspectorHasNoPhysicalReserve(c, source);
        var intelligence = personalResourceIntelligence(c);
        assertEquals(1, intelligence.size());
        assertEquals("Получен физический образец", intelligence.get(0).summary());
        assertTrue(intelligence.get(0).sections().get(0).lines().stream()
                .anyMatch(line -> line.label().equals("Извлекаемая масса") && line.value().equals("Оценка отсутствует")));
        for (var owner : beforeKnowledge.knowledgeStates())
            if (!owner.ownerId().equals(com.spacesim.world.Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID))
                assertEquals(owner, knowledge.knowledgeFor(owner.ownerId()));
        double removed = Math.min(1, 25d * step);
        assertEquals(reserve - removed, source.sourceState().remainingAccessibleMassKg(), 1e-8);
        var saved = c.captureState(); var restored = roundtrip(c);
        assertEquals(1, restored.coordinator().runtime().freight().personalMiningOrders().size());
        assertEquals(saved, restored.captureState());
        assertEquals(intelligence, personalResourceIntelligence(restored));
        assertFalse(restored.coordinator().runtime().freight().claimPersonalMiningTick(
                restored.playerState().orElseThrow().activeFleetId(), restored.coordinator().runtime().world().getAuthoritativeWorldTick()));
        assertEquals(saved, restored.captureState());
        c.advanceFrame(step); restored.advanceFrame(step);
        assertEquals(sample, c.coordinator().runtime().discoveryState()
                .knowledgeFor(com.spacesim.world.Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID).knowledge(object).orElseThrow());
        var expected = c.captureState(); var actual = restored.captureState();
        assertTrue(expected.equals(actual), () -> "Continuation differs at " + difference(expected, actual));
        var stop = restored.previewPilotAction("STOP_MINING", "", "", 0); assertTrue(stop.allowed());
        restored.submitPilotAction(stop); assertTrue(restored.coordinator().runtime().freight().personalMiningOrders().isEmpty());
        double remaining = restored.coordinator().runtime().industry().sourceOutposts().sources().source(source.sourceId())
                .sourceState().remainingAccessibleMassKg();
        restored.advanceFrame(step);
        assertEquals(remaining, restored.coordinator().runtime().industry().sourceOutposts().sources().source(source.sourceId())
                .sourceState().remainingAccessibleMassKg());
    }

    private static java.util.List<com.spacesim.ui.ProductionUiSnapshot.Row> personalResourceIntelligence(
            Stage228CampaignAuthority campaign) {
        var runtime = campaign.coordinator().runtime();
        var model = new com.spacesim.ui.GeneratedWorldUiModel(1L, runtime, campaign.coordinator().content());
        var viewer = campaign.coordinator().actors().capture().get(0).factionContentId();
        return new com.spacesim.ui.ProductionUiProjector(viewer).capture(campaign, model.capture())
                .rows(com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab.INTELLIGENCE).stream()
                .filter(row -> row.selection().stableId().startsWith("personal-resource:")).toList();
    }

    private static void assertPersonalInspectorHasNoPhysicalReserve(Stage228CampaignAuthority c, MaterializedSource source) {
        var runtime = c.coordinator().runtime();
        var model = new com.spacesim.ui.GeneratedWorldUiModel(c.coordinator().rootSeed(), runtime,
                c.coordinator().content(), com.spacesim.ui.GeneratedWorldUiModel.CarrierUiSource.none(), () -> c.playerState().orElse(null));
        var baseline = c.captureState();
        var keys = new java.util.HashSet<String>(); keys.add("resource:" + source.sourceId());
        runtime.industry().sourceOutposts().outposts().stream().filter(o -> o.source().sourceId().equals(source.sourceId()))
                .forEach(o -> keys.add("outpost:" + o.site().siteId()));
        var projection = model.capture();
        var resource = projection.localObjects().stream().filter(o -> keys.contains(o.stableId())).findFirst()
                .orElseThrow(() -> new AssertionError("Missing occurrence " + keys + " in viewed " + projection.activeSystemId()
                        + "; occurrence system=" + source.systemId()));
        var lines = resource.sections().stream().flatMap(s -> s.lines().stream()).toList();
        assertTrue(lines.stream().anyMatch(l -> l.value().equals("Оценка отсутствует")));
        assertTrue(lines.stream().noneMatch(l -> l.label().equals("Остаток") || l.label().equals("Начальная масса")));
        assertTrue(lines.stream().noneMatch(l -> l.value().equals(String.format(java.util.Locale.ROOT, "%.1f%%", source.sourceState().gradeFraction() * 100))));
        assertTrue(baseline.equals(c.captureState()), "Personal resource projection must not create observations or events");
    }

    @Test void rangeSpeedAbsentEquipmentAndStaleOrForeignPreviewsCannotGrantCargo() {
        var plain = FoundedCampaignFixture.restore(); var source = localSource(plain);
        assertTrue(com.spacesim.ui.GeneratedCampaignMiningUi.rows(plain).isEmpty());
        position(plain, LocalPhysicalKinematics.stationary(source.position())); var before = plain.captureState();
        assertFalse(plain.previewPilotAction("START_MINING", source.sourceId(), "", 1).allowed()); assertEquals(before, plain.captureState());
        var c = equipped(); source = localSource(c);
        position(c, LocalPhysicalKinematics.stationary(source.position().translated(5_001, 0)));
        assertFalse(c.previewPilotAction("START_MINING", source.sourceId(), "", 1).allowed());
        position(c, new LocalPhysicalKinematics(source.position(), 1.01, 0));
        assertFalse(c.previewPilotAction("START_MINING", source.sourceId(), "", 1).allowed());
        position(c, LocalPhysicalKinematics.stationary(source.position()));
        assertFalse(c.previewPilotAction("START_MINING", source.sourceId(), "", 0).allowed());
        assertFalse(c.previewPilotAction("START_MINING", "source.absent", "", 1).allowed());
        var start = c.previewPilotAction("START_MINING", source.sourceId(), "", 1); assertTrue(start.allowed());
        var other = roundtrip(c); before = other.captureState();
        assertThrows(IllegalStateException.class, () -> other.submitPilotAction(start)); assertEquals(before, other.captureState());
        c.advanceFrame(c.coordinator().session().fixedStepSeconds()); before = c.captureState();
        assertThrows(IllegalStateException.class, () -> c.submitPilotAction(start)); assertEquals(before, c.captureState());
    }

    @Test void lostPhysicalContactOrDirectControlStopsWorkWithoutDepletingSource() {
        var c = equipped(); var source = localSource(c); position(c, LocalPhysicalKinematics.stationary(source.position()));
        c.submitPilotAction(c.previewPilotAction("START_MINING", source.sourceId(), "", 1));
        double reserve = source.sourceState().remainingAccessibleMassKg();
        position(c, LocalPhysicalKinematics.stationary(source.position().translated(5_001, 0)));
        c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        assertEquals(reserve, source.sourceState().remainingAccessibleMassKg());
        assertTrue(c.coordinator().runtime().freight().personalMiningOrders().isEmpty());
        position(c, LocalPhysicalKinematics.stationary(source.position()));
        c.submitPilotAction(c.previewPilotAction("START_MINING", source.sourceId(), "", 1));
        assertTrue(c.setPilotThrust(0, 0, true)); c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        assertEquals(reserve, source.sourceState().remainingAccessibleMassKg());
        assertTrue(c.coordinator().runtime().freight().personalMiningOrders().isEmpty());
    }

    @Test void lossOfInstalledEquipmentStopsMiningAndFutureTickCannotEnterCheckpoint() {
        var c = equipped(); var source = localSource(c); position(c, LocalPhysicalKinematics.stationary(source.position()));
        c.submitPilotAction(c.previewPilotAction("START_MINING", source.sourceId(), "", 1));
        double reserve = source.sourceState().remainingAccessibleMassKg();
        var r = c.coordinator().runtime(); var fleet = r.world().findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        r.world().findSession(fleet.systemId()).orElseThrow().getEntityRegistry().require(fleet.localEntityId()).remove(EngineeringComponent.class);
        c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        assertEquals(reserve, source.sourceState().remainingAccessibleMassKg()); assertTrue(r.freight().personalMiningOrders().isEmpty());
        var stopped = c.playerJournal().entries().get(c.playerJournal().entries().size() - 1);
        assertEquals(com.spacesim.player.PlayerJournalState.Kind.MINING_STOPPED, stopped.kind());
        assertEquals("CONTACT_UNAVAILABLE", stopped.action());
        assertEquals(r.world().getAuthoritativeWorldTick(), stopped.tick());
        var stoppedJournal = c.playerJournal();
        c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        assertEquals(stoppedJournal, c.playerJournal(), "An already stopped job must not notify again");
        var future = equipped(); source = localSource(future); position(future, LocalPhysicalKinematics.stationary(source.position()));
        future.submitPilotAction(future.previewPilotAction("START_MINING", source.sourceId(), "", 1));
        future.coordinator().runtime().freight().claimPersonalMiningTick(future.playerState().orElseThrow().activeFleetId(), Long.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, future::captureState);
    }

    private static Stage228CampaignAuthority equipped() {
        var c = FoundedCampaignFixture.restore(); var r = c.coordinator().runtime();
        var fleet = r.world().findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        var catalog = Stage22FreightStrategicEngineeringCatalogLoader.loadDefault();
        var fit = InstalledFit.fromDemonstrator(catalog.findDemonstratorFit(Stage22CivilianMiningEngineeringCatalogLoader.MINING_FIT_ID));
        // Explicit installed-ship fixture, not a production purchase/refit or starting-asset grant.
        r.world().findSession(fleet.systemId()).orElseThrow().getEntityRegistry().require(fleet.localEntityId())
                .add(new EngineeringComponent(fit, new ShipEngineeringRuntime(catalog).initialize(fit, ConsumableState.empty())));
        r.synchronizeFreightEngineeringCargo(fleet.fleetId());
        return c;
    }
    private static MaterializedSource localSource(Stage228CampaignAuthority c) {
        var r = c.coordinator().runtime(); var fleet = r.world().findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        return r.industry().sourceOutposts().sources().sources().stream().filter(s -> s.systemId().equals(fleet.systemId())
                && s.sourceState().environment() == ExtractionEnvironment.FREE_BODY
                && s.sourceState().requiredCapabilityTags().isEmpty() && s.sourceState().remainingAccessibleMassKg() >= 10)
                .findFirst().orElseThrow();
    }
    private static void position(Stage228CampaignAuthority c, LocalPhysicalKinematics kinematics) {
        var r = c.coordinator().runtime(); var fleet = r.world().findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        r.arrival().materialization(fleet.systemId()).updatePhysicalState(fleet.localEntityId(), kinematics);
    }
    private static Stage228CampaignAuthority roundtrip(Stage228CampaignAuthority c) {
        return Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(c.captureState())));
    }
    private static String difference(Object a, Object b) {
        if (java.util.Objects.equals(a, b)) return "equal";
        if (a != null && b != null && a.getClass().equals(b.getClass()) && a.getClass().isRecord()) {
            for (var field : a.getClass().getRecordComponents()) try {
                var left = field.getAccessor().invoke(a); var right = field.getAccessor().invoke(b);
                if (!java.util.Objects.equals(left, right)) return field.getName() + "." + difference(left, right);
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        }
        if (a instanceof java.util.List<?> left && b instanceof java.util.List<?> right && left.size() == right.size()) {
            for (int i = 0; i < left.size(); i++) if (!java.util.Objects.equals(left.get(i), right.get(i)))
                return "[" + i + "]." + difference(left.get(i), right.get(i));
        }
        String text = String.valueOf(a) + " != " + b;
        return text.substring(0, Math.min(text.length(), 200));
    }
}
