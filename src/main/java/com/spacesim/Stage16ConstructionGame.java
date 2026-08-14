package com.spacesim;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.spacesim.content.ContentCatalog;
import com.spacesim.economy.Money;
import com.spacesim.persistence.PlayableWorldStateCodec;
import com.spacesim.player.PlayableTestWorldFactory;
import com.spacesim.player.PlayableWorldState;
import com.spacesim.player.PlayerConstructionArchetypeView;
import com.spacesim.player.PlayerConstructionCancellationService;
import com.spacesim.player.PlayerConstructionManagementModel;
import com.spacesim.player.PlayerConstructionManagementSnapshot;
import com.spacesim.player.PlayerConstructionPlacementView;
import com.spacesim.player.PlayerConstructionProjectView;
import com.spacesim.player.PlayerConstructionService;
import com.spacesim.player.PlayerFleetOrderService;
import com.spacesim.player.PlayerRuntime;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.ui.Stage16ConstructionRenderer;
import com.spacesim.ui.WorldMapLayout;
import com.spacesim.world.ConstructionProjectId;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.StarSystemId;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Functional Stage-16 local construction and project-management harness.
 *
 * <p>This application is deliberately a thin UI shell around authoritative Stage-16 services.
 * Mouse position is transformed to local world coordinates for
 * {@link PlayerConstructionService#previewPlacement(float, float)}; confirmation calls the same
 * service's project-creation boundary. Funding, cancellation and autonomous construction supply
 * likewise go through their ordinary player services. No UI path spawns stations, delivers cargo,
 * edits project progress or applies placement rules locally.</p>
 *
 * <p>Controls: C toggles placement mode; PageUp/PageDown cycle station archetypes; mouse moves the
 * ghost; Enter creates at an allowed preview; Up/Down select owned projects; F funds the selected
 * project's current minimum-funding shortfall; X cancels when authoritative cancellation allows;
 * U assigns one inactive owned fleet to supply the first missing material; Space pauses; 1-4 set
 * time scale; F5/F9 save/load.</p>
 */
public final class Stage16ConstructionGame extends ApplicationAdapter {
    private static final String SAVE_FILE = "saves/stage16-construction.sav";
    private static final float MAP_X = 20f;
    private static final float MAP_Y = 72f;
    private static final float MAP_RIGHT_MARGIN = 20f;
    private static final float MAP_TOP_HUD = 250f;
    private static final float MAP_PADDING = 8f;

    private PlayableTestWorldFactory.Scenario scenario;
    private ContentCatalog content;
    private PlayerRuntime runtime;
    private PlayerConstructionService construction;
    private PlayerConstructionCancellationService cancellation;
    private PlayerConstructionManagementModel management;
    private PlayerFleetOrderService fleetOrders;
    private Stage16ConstructionRenderer renderer;
    private OrthographicCamera camera;
    private SpriteBatch batch;
    private BitmapFont font;
    private WorldMapLayout layout;
    private Path savePath;
    private final Vector2 cursorWorld = new Vector2();
    private boolean cursorInsideMap;
    private boolean placementMode;
    private int archetypeIndex;
    private int projectIndex;
    private String status = "Stage 16 construction management ready.";

    /** Creates the application shell; libGDX resources are allocated in {@link #create()}. */
    public Stage16ConstructionGame() {
    }

    /** Initializes deterministic playable state and construction UI resources. */
    @Override
    public void create() {
        scenario = PlayableTestWorldFactory.create(PlayableTestWorldFactory.DEFAULT_TEST_SEED);
        content = scenario.content();
        runtime = scenario.runtime();
        bindServices();
        renderer = new Stage16ConstructionRenderer();
        camera = new OrthographicCamera();
        batch = new SpriteBatch();
        font = new BitmapFont();
        savePath = Gdx.files.local(SAVE_FILE).file().toPath();
        resize(Math.max(1, Gdx.graphics.getWidth()), Math.max(1, Gdx.graphics.getHeight()));
        cursorWorld.set(com.spacesim.constants.Constants.WORLD_WIDTH / 2f,
                com.spacesim.constants.Constants.WORLD_HEIGHT / 2f);
        cursorInsideMap = true;
        Gdx.input.setInputProcessor(input());
        Gdx.gl.glClearColor(0.012f, 0.018f, 0.032f, 1f);
    }

    private void bindServices() {
        construction = new PlayerConstructionService(runtime);
        cancellation = new PlayerConstructionCancellationService(runtime);
        management = new PlayerConstructionManagementModel(runtime);
        fleetOrders = new PlayerFleetOrderService(runtime);
    }

    private InputAdapter input() {
        return new InputAdapter() {
            @Override
            public boolean mouseMoved(int screenX, int screenY) {
                updateCursor(screenX, screenY);
                return placementMode;
            }

            @Override
            public boolean touchDragged(int screenX, int screenY, int pointer) {
                updateCursor(screenX, screenY);
                return placementMode;
            }

            @Override
            public boolean keyDown(int keycode) {
                return switch (keycode) {
                    case Input.Keys.C -> togglePlacement();
                    case Input.Keys.PAGE_UP -> cycleArchetype(-1);
                    case Input.Keys.PAGE_DOWN -> cycleArchetype(1);
                    case Input.Keys.ENTER -> confirmPlacement();
                    case Input.Keys.UP -> cycleProject(-1);
                    case Input.Keys.DOWN -> cycleProject(1);
                    case Input.Keys.F -> fundSelectedProject();
                    case Input.Keys.X -> cancelSelectedProject();
                    case Input.Keys.U -> assignSupplyFleet();
                    case Input.Keys.SPACE -> togglePause();
                    case Input.Keys.NUM_1 -> setTimeScale(1d);
                    case Input.Keys.NUM_2 -> setTimeScale(2d);
                    case Input.Keys.NUM_3 -> setTimeScale(4d);
                    case Input.Keys.NUM_4 -> setTimeScale(8d);
                    case Input.Keys.F5 -> save();
                    case Input.Keys.F9 -> load();
                    case Input.Keys.ESCAPE -> leavePlacement();
                    default -> false;
                };
            }
        };
    }

    private void updateCursor(int screenX, int screenYTopDown) {
        if (layout == null) {
            return;
        }
        float screenY = Gdx.graphics.getHeight() - screenYTopDown;
        cursorInsideMap = layout.containsMapPoint(screenX, screenY)
                && layout.screenToWorld(screenX, screenY, cursorWorld);
    }

    private boolean togglePlacement() {
        placementMode = !placementMode;
        status = placementMode
                ? "Placement mode enabled; authoritative preview follows the mouse."
                : "Placement mode disabled.";
        return true;
    }

    private boolean leavePlacement() {
        if (placementMode) {
            placementMode = false;
            status = "Placement mode cancelled without world mutation.";
        }
        return true;
    }

    private boolean cycleArchetype(int delta) {
        List<PlayerConstructionArchetypeView> options = construction.buildableArchetypes();
        if (options.isEmpty()) {
            status = "No constructible station archetypes in the current content catalog.";
            return true;
        }
        archetypeIndex = Math.floorMod(archetypeIndex + delta, options.size());
        status = "Selected construction archetype: " + selectedArchetype(options).displayName() + ".";
        return true;
    }

    private boolean confirmPlacement() {
        if (!placementMode || !cursorInsideMap) {
            status = "Enable placement mode and move the cursor inside the local map first.";
            return true;
        }
        List<PlayerConstructionArchetypeView> options = construction.buildableArchetypes();
        PlayerConstructionArchetypeView archetype = selectedArchetype(options);
        PlayerConstructionPlacementView preview = currentPreview();
        if (archetype == null || preview == null || !preview.allowed()) {
            status = preview == null
                    ? "No authoritative placement preview is currently available."
                    : "Placement rejected: " + preview.rejection() + ".";
            return true;
        }
        try {
            ConstructionProjectId projectId = construction.createProject(
                    archetype.archetypeContentId(), preview.x(), preview.y());
            PlayerConstructionManagementSnapshot snapshot = management.capture();
            projectIndex = Math.max(0, snapshot.projects().size() - 1);
            status = "Project #" + projectId.value() + " created as a physical construction site.";
        } catch (RuntimeException exception) {
            status = "Project creation rejected: " + safeMessage(exception);
        }
        return true;
    }

    private boolean cycleProject(int delta) {
        List<PlayerConstructionProjectView> projects = management.capture().projects();
        if (projects.isEmpty()) {
            projectIndex = 0;
            status = "No live owned construction projects.";
            return true;
        }
        projectIndex = Math.floorMod(projectIndex + delta, projects.size());
        status = "Selected project #" + projects.get(projectIndex).projectId().value() + ".";
        return true;
    }

    private boolean fundSelectedProject() {
        PlayerConstructionProjectView project = selectedProject(management.capture());
        if (project == null) {
            status = "No owned project selected for funding.";
            return true;
        }
        long amount = Math.min(project.fundingShortfallMilliCredits(), runtime.player().walletMilliCredits());
        if (amount <= 0L) {
            status = project.fundingShortfallMilliCredits() <= 0L
                    ? "Selected project already meets minimum funding."
                    : "Personal wallet cannot cover any of the current funding shortfall.";
            return true;
        }
        try {
            long transferred = construction.fundProject(project.projectId(), amount);
            status = transferred > 0L
                    ? "Funded project #" + project.projectId().value() + " by " + formatCredits(transferred) + "."
                    : "Funding rejected by current wallet/site constraints.";
        } catch (RuntimeException exception) {
            status = "Funding rejected: " + safeMessage(exception);
        }
        return true;
    }

    private boolean cancelSelectedProject() {
        PlayerConstructionProjectView project = selectedProject(management.capture());
        if (project == null) {
            status = "No owned project selected for cancellation.";
            return true;
        }
        if (!project.cancellation().allowed()) {
            status = "Cancellation rejected: " + project.cancellation().rejection() + ".";
            return true;
        }
        try {
            status = cancellation.cancel(project.projectId())
                    ? "Project #" + project.projectId().value() + " cancelled through world lifecycle."
                    : "Cancellation was not accepted by current authoritative state.";
            clampProjectSelection(management.capture());
        } catch (RuntimeException exception) {
            status = "Cancellation failed and rolled back: " + safeMessage(exception);
        }
        return true;
    }

    private boolean assignSupplyFleet() {
        PlayerConstructionProjectView project = selectedProject(management.capture());
        if (project == null || project.totalMissingUnits() <= 0L) {
            status = "Selected project has no missing material suitable for a supply order.";
            return true;
        }
        FleetId delegated = firstInactiveOwnedFleet();
        if (delegated == null) {
            status = "SUPPLY_PROJECT requires an inactive owned fleet; active direct control is never commandeered.";
            return true;
        }
        String item = project.materials().stream()
                .filter(material -> material.missingUnits() > 0)
                .findFirst().orElseThrow().itemContentId();
        status = fleetOrders.supplyProject(delegated, project.projectId(), item)
                ? "Fleet #" + delegated.value() + " assigned SUPPLY_PROJECT for " + item + "."
                : "SUPPLY_PROJECT rejected by current ownership/project/material rules.";
        return true;
    }

    private FleetId firstInactiveOwnedFleet() {
        FleetId active = runtime.player().activeFleetId();
        for (FleetId fleetId : runtime.player().ownedFleetIds()) {
            if (!fleetId.equals(active) && runtime.world().findFleet(fleetId).isPresent()) {
                return fleetId;
            }
        }
        return null;
    }

    private boolean togglePause() {
        runtime.setPaused(!runtime.isPaused());
        status = runtime.isPaused() ? "Simulation paused." : "Simulation resumed.";
        return true;
    }

    private boolean setTimeScale(double scale) {
        runtime.setTimeScale(scale);
        status = String.format(Locale.ROOT, "Time scale x%.0f.", scale);
        return true;
    }

    private boolean save() {
        try {
            PlayableWorldStateCodec.write(savePath, runtime.snapshot());
            status = "Saved construction/fleet/world state to " + SAVE_FILE + ".";
        } catch (IOException | RuntimeException exception) {
            status = "Save failed: " + safeMessage(exception);
        }
        return true;
    }

    private boolean load() {
        try {
            PlayableWorldState state = PlayableWorldStateCodec.read(savePath);
            if (state.playerState() == null) {
                throw new IllegalStateException("Save contains no initialized player");
            }
            runtime = PlayerRuntime.restore(state, content, restoreSystem(state));
            bindServices();
            archetypeIndex = 0;
            projectIndex = 0;
            placementMode = false;
            status = "Save loaded with construction ownership, progress and fleet orders.";
        } catch (IOException | RuntimeException exception) {
            status = "Load failed: " + safeMessage(exception);
        }
        return true;
    }

    private StarSystemId restoreSystem(PlayableWorldState state) {
        FleetId active = state.playerState().activeFleetId();
        if (active != null) {
            for (FleetPlacementState placement : state.worldState().fleets()) {
                if (active.equals(placement.id()) && placement.locationKind() == FleetLocationKind.IN_SYSTEM) {
                    return placement.systemId();
                }
            }
        }
        return state.playerState().homeSystemId() != null
                ? state.playerState().homeSystemId()
                : scenario.route().sourceSystem();
    }

    private PlayerConstructionPlacementView currentPreview() {
        if (!placementMode || !cursorInsideMap) {
            return null;
        }
        try {
            return construction.previewPlacement(cursorWorld.x, cursorWorld.y);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** Advances ordinary gameplay and renders local construction read models. */
    @Override
    public void render() {
        runtime.advanceFrame(Gdx.graphics.getDeltaTime());
        PlayerConstructionManagementSnapshot snapshot = management.capture();
        clampProjectSelection(snapshot);
        PlayerConstructionPlacementView preview = currentPreview();
        SimulationSession session = runtime.world().findSession(runtime.world().getActiveSystemId()).orElseThrow();
        camera.update();
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        renderer.render(camera, layout, session, preview);
        drawHud(snapshot, preview);
    }

    private void drawHud(
            PlayerConstructionManagementSnapshot snapshot,
            PlayerConstructionPlacementView preview) {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        float top = Gdx.graphics.getHeight() - 12f;
        font.draw(batch, "STAR EMPIRES — STAGE 16 LOCAL CONSTRUCTION", 12f, top);
        font.draw(batch,
                "Personal credits " + formatCredits(runtime.player().walletMilliCredits())
                        + "   Projects " + snapshot.projects().size()
                        + "   Owned stations " + snapshot.stations().size()
                        + "   Time x" + String.format(Locale.ROOT, "%.0f", runtime.getTimeScale())
                        + (runtime.isPaused() ? " PAUSED" : ""),
                12f,
                top - 20f);
        PlayerConstructionArchetypeView archetype = selectedArchetype(construction.buildableArchetypes());
        if (archetype != null) {
            font.draw(batch,
                    "Build option: " + archetype.displayName()
                            + " | min funding " + formatCredits(archetype.minimumFundingMilliCredits())
                            + " | materials " + archetype.requiredMaterials()
                            + " | estimate " + String.format(Locale.ROOT, "%.1fs", archetype.estimatedBuildSeconds()),
                    12f,
                    top - 42f);
        }
        if (placementMode) {
            String previewText = preview == null
                    ? "Placement preview unavailable outside the map/current local actor state."
                    : String.format(Locale.ROOT,
                            "Placement %.1f, %.1f — %s%s",
                            preview.x(),
                            preview.y(),
                            preview.allowed() ? "VALID" : "INVALID",
                            preview.allowed() ? "" : " (" + preview.rejection() + ")");
            font.draw(batch, previewText, 12f, top - 64f);
        }
        PlayerConstructionProjectView project = selectedProject(snapshot);
        if (project != null) {
            font.draw(batch,
                    "Project #" + project.projectId().value() + " " + project.stationDisplayName()
                            + " | " + project.status()
                            + " | site wallet " + formatCredits(project.siteWalletMilliCredits())
                            + " | shortfall " + formatCredits(project.fundingShortfallMilliCredits()),
                    12f,
                    top - 88f);
            font.draw(batch,
                    String.format(Locale.ROOT,
                            "Materials %d/%d (missing %d) | build %.1f%% | remaining %d ticks | cancel %s | supply fleets %s",
                            project.totalDeliveredUnits(),
                            project.totalRequiredUnits(),
                            project.totalMissingUnits(),
                            project.buildProgress() * 100d,
                            project.remainingBuildTicks(),
                            project.cancellation().allowed() ? "allowed" : project.cancellation().rejection(),
                            project.supplyFleetIds()),
                    12f,
                    top - 108f);
        }
        font.draw(batch,
                "C placement | PgUp/PgDn archetype | mouse ghost | ENTER create | UP/DOWN project | F fund | X cancel | U supply | SPACE pause | 1-4 time | F5/F9 save/load",
                12f,
                34f);
        font.draw(batch, "STATUS — " + status, 12f, 16f);
        batch.end();
    }

    private void clampProjectSelection(PlayerConstructionManagementSnapshot snapshot) {
        projectIndex = snapshot.projects().isEmpty()
                ? 0 : Math.min(projectIndex, snapshot.projects().size() - 1);
    }

    private PlayerConstructionProjectView selectedProject(PlayerConstructionManagementSnapshot snapshot) {
        return snapshot.projects().isEmpty()
                ? null : snapshot.projects().get(Math.min(projectIndex, snapshot.projects().size() - 1));
    }

    private PlayerConstructionArchetypeView selectedArchetype(List<PlayerConstructionArchetypeView> options) {
        if (options.isEmpty()) {
            return null;
        }
        archetypeIndex = Math.min(archetypeIndex, options.size() - 1);
        return options.get(archetypeIndex);
    }

    /** Rebuilds screen-space camera and local-map projection while preserving authoritative world state. */
    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (camera != null) {
            camera.setToOrtho(false, width, height);
        }
        float mapWidth = Math.max(120f, width - MAP_X - MAP_RIGHT_MARGIN);
        float mapHeight = Math.max(120f, height - MAP_Y - MAP_TOP_HUD);
        layout = new WorldMapLayout(MAP_X, MAP_Y, mapWidth, mapHeight, MAP_PADDING);
    }

    /** Releases construction presentation resources. */
    @Override
    public void dispose() {
        if (Gdx.input != null) {
            Gdx.input.setInputProcessor(null);
        }
        if (renderer != null) {
            renderer.dispose();
        }
        if (batch != null) {
            batch.dispose();
        }
        if (font != null) {
            font.dispose();
        }
    }

    private static String formatCredits(long milliCredits) {
        return String.format(Locale.ROOT, "%,.2f cr", Money.toCredits(milliCredits));
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}