package com.spacesim;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.spacesim.content.ContentCatalog;
import com.spacesim.economy.Money;
import com.spacesim.persistence.PlayableWorldStateCodec;
import com.spacesim.player.GlobalFleetMapSnapshot;
import com.spacesim.player.PlayableTestWorldFactory;
import com.spacesim.player.PlayableWorldState;
import com.spacesim.player.PlayerRuntime;
import com.spacesim.player.PlayerRouteRiskView;
import com.spacesim.player.PlayerStrategicCommandService;
import com.spacesim.ui.GlobalFleetMapRenderer;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.StarSystemId;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Functional Stage-15/16 global fleet and owned-asset command map.
 *
 * <p>The application intentionally remains a thin strategic presentation harness. It advances the
 * same {@link PlayerRuntime}, renders only {@link com.spacesim.player.GlobalFleetMapModel} output
 * and submits commands through {@link PlayerStrategicCommandService}. It never mutates transforms,
 * transit, economy, construction or threat state directly.</p>
 *
 * <p>Controls: Up/Down select owned fleet; Left/Right select discovered system; Enter MOVE;
 * H HOLD; F FOLLOW active fleet; E ESCORT active fleet; P PATROL all discovered systems;
 * Space pause; 1/2/3/4 time scale; F5 save; F9 load.</p>
 */
public final class Stage15FleetMapGame extends ApplicationAdapter {
    private static final String SAVE_FILE = "saves/playable-test-world.sav";

    private PlayableTestWorldFactory.Scenario scenario;
    private ContentCatalog content;
    private PlayerRuntime runtime;
    private PlayerStrategicCommandService commands;
    private GlobalFleetMapRenderer renderer;
    private OrthographicCamera camera;
    private SpriteBatch batch;
    private BitmapFont font;
    private Path savePath;
    private int fleetIndex;
    private int systemIndex;
    private String status = "Strategic fleet/construction map ready.";

    /** Creates the map application; libGDX resources are allocated in {@link #create()}. */
    public Stage15FleetMapGame() {
    }

    /** Initializes deterministic playable state and strategic presentation resources. */
    @Override
    public void create() {
        scenario = PlayableTestWorldFactory.create(PlayableTestWorldFactory.DEFAULT_TEST_SEED);
        content = scenario.content();
        runtime = scenario.runtime();
        commands = new PlayerStrategicCommandService(runtime);
        renderer = new GlobalFleetMapRenderer();
        camera = new OrthographicCamera();
        camera.setToOrtho(false, Math.max(1, Gdx.graphics.getWidth()), Math.max(1, Gdx.graphics.getHeight()));
        batch = new SpriteBatch();
        font = new BitmapFont();
        savePath = Gdx.files.local(SAVE_FILE).file().toPath();
        Gdx.input.setInputProcessor(input());
        Gdx.gl.glClearColor(0.015f, 0.022f, 0.04f, 1f);
    }

    private InputAdapter input() {
        return new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                return switch (keycode) {
                    case Input.Keys.UP -> cycleFleet(-1);
                    case Input.Keys.DOWN -> cycleFleet(1);
                    case Input.Keys.LEFT -> cycleSystem(-1);
                    case Input.Keys.RIGHT -> cycleSystem(1);
                    case Input.Keys.ENTER -> issueMove();
                    case Input.Keys.H -> issueHold();
                    case Input.Keys.F -> issueFollow(false);
                    case Input.Keys.E -> issueFollow(true);
                    case Input.Keys.P -> issuePatrol();
                    case Input.Keys.SPACE -> togglePause();
                    case Input.Keys.NUM_1 -> setTimeScale(1d);
                    case Input.Keys.NUM_2 -> setTimeScale(2d);
                    case Input.Keys.NUM_3 -> setTimeScale(4d);
                    case Input.Keys.NUM_4 -> setTimeScale(8d);
                    case Input.Keys.F5 -> save();
                    case Input.Keys.F9 -> load();
                    default -> false;
                };
            }
        };
    }

    private boolean cycleFleet(int delta) {
        GlobalFleetMapSnapshot snapshot = commands.mapSnapshot();
        if (snapshot.fleets().isEmpty()) {
            status = "No owned fleets are currently visible.";
            return true;
        }
        fleetIndex = Math.floorMod(fleetIndex + delta, snapshot.fleets().size());
        status = "Selected Fleet #" + selectedFleet(snapshot).fleetId().value() + ".";
        return true;
    }

    private boolean cycleSystem(int delta) {
        GlobalFleetMapSnapshot snapshot = commands.mapSnapshot();
        if (snapshot.systems().isEmpty()) {
            status = "No discovered systems.";
            return true;
        }
        systemIndex = Math.floorMod(systemIndex + delta, snapshot.systems().size());
        status = "Selected system " + selectedSystem(snapshot).name() + ".";
        return true;
    }

    private boolean issueMove() {
        GlobalFleetMapSnapshot snapshot = commands.mapSnapshot();
        GlobalFleetMapSnapshot.FleetMarker fleet = selectedFleet(snapshot);
        GlobalFleetMapSnapshot.SystemMarker system = selectedSystem(snapshot);
        if (fleet == null || system == null) {
            status = "MOVE requires an owned fleet and discovered system.";
            return true;
        }
        PlayerRouteRiskView route = commands.previewMove(fleet.fleetId(), system.systemId()).orElse(null);
        if (route == null) {
            status = "No player-known route to selected system.";
            return true;
        }
        status = commands.move(fleet.fleetId(), system.systemId())
                ? "MOVE accepted via shared fleet-order pipeline."
                : "MOVE rejected by current ownership/discovery rules.";
        return true;
    }

    private boolean issueHold() {
        GlobalFleetMapSnapshot.FleetMarker fleet = selectedFleet(commands.mapSnapshot());
        if (fleet == null) {
            status = "HOLD requires an owned fleet.";
            return true;
        }
        status = commands.hold(fleet.fleetId()) ? "HOLD assigned." : "HOLD rejected.";
        return true;
    }

    private boolean issueFollow(boolean escort) {
        GlobalFleetMapSnapshot snapshot = commands.mapSnapshot();
        GlobalFleetMapSnapshot.FleetMarker fleet = selectedFleet(snapshot);
        FleetId active = runtime.player().activeFleetId();
        if (fleet == null || active == null || fleet.fleetId().equals(active)) {
            status = "Select a non-active owned fleet to follow/escort the active FleetId.";
            return true;
        }
        boolean accepted = escort
                ? commands.escort(fleet.fleetId(), active)
                : commands.follow(fleet.fleetId(), active);
        status = accepted ? (escort ? "ESCORT assigned." : "FOLLOW assigned.") : "Formation order rejected.";
        return true;
    }

    private boolean issuePatrol() {
        GlobalFleetMapSnapshot snapshot = commands.mapSnapshot();
        GlobalFleetMapSnapshot.FleetMarker fleet = selectedFleet(snapshot);
        if (fleet == null || snapshot.systems().size() < 2) {
            status = "PATROL requires an owned fleet and at least two discovered systems.";
            return true;
        }
        List<StarSystemId> systems = snapshot.systems().stream()
                .map(GlobalFleetMapSnapshot.SystemMarker::systemId)
                .toList();
        status = commands.patrol(fleet.fleetId(), systems) ? "PATROL cycle assigned." : "PATROL rejected.";
        return true;
    }

    private boolean togglePause() {
        boolean paused = !runtime.isPaused();
        runtime.setPaused(paused);
        status = paused ? "Simulation paused." : "Simulation resumed.";
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
            status = "Strategic/player state saved to " + SAVE_FILE + ".";
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
            commands = new PlayerStrategicCommandService(runtime);
            fleetIndex = 0;
            systemIndex = 0;
            status = "Save loaded with fleet, threat and construction ownership state.";
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

    /** Advances ordinary gameplay and renders the current strategic read model. */
    @Override
    public void render() {
        runtime.advanceFrame(Gdx.graphics.getDeltaTime());
        GlobalFleetMapSnapshot snapshot = commands.mapSnapshot();
        clampSelection(snapshot);
        camera.update();
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        GlobalFleetMapSnapshot.FleetMarker fleet = selectedFleet(snapshot);
        GlobalFleetMapSnapshot.SystemMarker system = selectedSystem(snapshot);
        renderer.render(
                camera,
                snapshot,
                system == null ? null : system.systemId(),
                fleet == null ? null : fleet.fleetId(),
                Gdx.graphics.getWidth(),
                Gdx.graphics.getHeight());
        drawHud(snapshot, fleet, system);
    }

    private void drawHud(
            GlobalFleetMapSnapshot snapshot,
            GlobalFleetMapSnapshot.FleetMarker fleet,
            GlobalFleetMapSnapshot.SystemMarker system) {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        float top = Gdx.graphics.getHeight() - 14f;
        font.draw(batch, "STAR EMPIRES — STAGE 16 GLOBAL ASSET MAP", 12f, top);
        font.draw(batch,
                "Credits " + String.format(Locale.ROOT, "%,.2f", Money.toCredits(runtime.player().walletMilliCredits()))
                        + "   Fleets " + snapshot.fleets().size()
                        + "   Projects " + snapshot.projects().size()
                        + "   Stations " + snapshot.stations().size()
                        + "   Known systems " + snapshot.systems().size(),
                12f,
                top - 20f);
        if (fleet != null) {
            font.draw(batch,
                    "Selected Fleet #" + fleet.fleetId().value() + "  order " + fleet.orderType(),
                    12f,
                    top - 40f);
        }
        if (system != null) {
            long projectCount = snapshot.projects().stream()
                    .filter(project -> project.systemId().equals(system.systemId()))
                    .count();
            long stationCount = snapshot.stations().stream()
                    .filter(station -> station.systemId().equals(system.systemId()))
                    .count();
            font.draw(batch,
                    "Destination " + system.name() + "   owned projects " + projectCount
                            + "   owned stations " + stationCount,
                    12f,
                    top - 60f);
        }
        if (fleet != null && system != null) {
            PlayerRouteRiskView route = commands.previewMove(fleet.fleetId(), system.systemId()).orElse(null);
            if (route != null) {
                font.draw(batch,
                        String.format(Locale.ROOT,
                                "Route %s  travel %d ticks  risk %.1f  vulnerability %.2f",
                                route.path(), route.travelTicks(), route.riskCostTicks(), route.vulnerability()),
                        12f,
                        top - 80f);
            }
        }
        if (system != null) {
            GlobalFleetMapSnapshot.ConstructionProjectMarker project = snapshot.projects().stream()
                    .filter(candidate -> candidate.systemId().equals(system.systemId()))
                    .findFirst().orElse(null);
            if (project != null) {
                font.draw(batch,
                        String.format(Locale.ROOT,
                                "Project #%d %s  %.0f%%  missing %d  funding shortfall %,.2f cr  supply fleets %s",
                                project.projectId().value(),
                                project.status(),
                                project.buildProgress() * 100d,
                                project.missingMaterialUnits(),
                                Money.toCredits(project.fundingShortfallMilliCredits()),
                                project.supplyFleetIds()),
                        12f,
                        top - 100f);
            }
        }
        font.draw(batch,
                "UP/DOWN fleet | LEFT/RIGHT system | ENTER move | H hold | F follow active | E escort active | P patrol | SPACE pause | 1-4 time | F5/F9 save/load",
                12f,
                28f);
        font.draw(batch, "STATUS — " + status, 12f, 12f);
        batch.end();
    }

    private void clampSelection(GlobalFleetMapSnapshot snapshot) {
        fleetIndex = snapshot.fleets().isEmpty() ? 0 : Math.min(fleetIndex, snapshot.fleets().size() - 1);
        systemIndex = snapshot.systems().isEmpty() ? 0 : Math.min(systemIndex, snapshot.systems().size() - 1);
    }

    private GlobalFleetMapSnapshot.FleetMarker selectedFleet(GlobalFleetMapSnapshot snapshot) {
        return snapshot.fleets().isEmpty() ? null : snapshot.fleets().get(Math.min(fleetIndex, snapshot.fleets().size() - 1));
    }

    private GlobalFleetMapSnapshot.SystemMarker selectedSystem(GlobalFleetMapSnapshot snapshot) {
        return snapshot.systems().isEmpty() ? null : snapshot.systems().get(Math.min(systemIndex, snapshot.systems().size() - 1));
    }

    /** Keeps strategic rendering in screen coordinates after window resize. */
    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0 || camera == null) {
            return;
        }
        camera.setToOrtho(false, width, height);
    }

    /** Releases map presentation resources. */
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

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}