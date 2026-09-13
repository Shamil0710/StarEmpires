package com.spacesim;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.spacesim.campaign.GeneratedCampaignCoordinator;
import com.spacesim.campaign.GeneratedCampaignSession;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.ui.FactionCharacterPortraitOverlay;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitKind;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.SelectionKind;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.UiSelection;
import com.spacesim.ui.GeneratedWorldUiModel;
import com.spacesim.ui.GeneratedWorldUiSnapshot;
import com.spacesim.world.StarSystemId;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Player-facing command interface over the accepted integrated generated campaign. */
public final class GeneratedWorldCommandGame extends ApplicationAdapter {
    private static final String SAVE_FILE = "saves/generated-campaign.s21i";
    private static final String LEGACY_SAVE_FILE = "saves/generated-world-runtime.s25";

    private final long initialSeed;

    private GeneratedCampaignCoordinator campaign;
    private GeneratedCampaignSession session;
    private GeneratedWorldUiModel model;
    private GeneratedWorldCommandUiRenderer renderer;
    private FactionCharacterPortraitOverlay characterPortraitOverlay;
    private GeneratedWorldUiSnapshot snapshot;
    private Tab tab = Tab.SYSTEM;
    private UiSelection selection = UiSelection.none();
    private int detailScrollRows;
    private int listScrollRows;
    private String status = "Генерация принятого мира…";
    private Path savePath;
    private Path legacySavePath;
    private boolean middleDragging;
    private int dragPointer = -1;
    private float previousDragX;
    private float previousDragY;
    private HitKind previousClickKind;
    private String previousClickId = "";
    private long previousClickNanos;

    private static final long DOUBLE_CLICK_NANOS = 450_000_000L;

    /**
     * Creates an application for a deterministic new-world seed.
     *
     * @param initialSeed generated campaign seed
     */
    public GeneratedWorldCommandGame(long initialSeed) {
        this.initialSeed = initialSeed;
    }

    /** Generates the accepted world and binds the read-only UI to its integrated campaign. */
    @Override
    public void create() {
        bindCampaign(GeneratedCampaignCoordinator.create(initialSeed));
        renderer = new GeneratedWorldCommandUiRenderer();
        characterPortraitOverlay = new FactionCharacterPortraitOverlay();
        savePath = Gdx.files.local(SAVE_FILE).file().toPath();
        legacySavePath = Gdx.files.local(LEGACY_SAVE_FILE).file().toPath();
        snapshot = model.capture();
        status = "Мир сгенерирован: " + snapshot.galaxy().systems().size()
                + " систем, " + snapshot.localObjects().size() + " объектов в активной системе.";
        session.setPaused(false);
        session.setTimeScale(1d);
        Gdx.input.setInputProcessor(input());
    }

    private InputAdapter input() {
        return new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                return switch (keycode) {
                    case Input.Keys.F1 -> switchTab(Tab.SYSTEM);
                    case Input.Keys.F2 -> switchTab(Tab.GALAXY);
                    case Input.Keys.F3 -> switchTab(Tab.FACTIONS);
                    case Input.Keys.F4 -> switchTab(Tab.MILITARY);
                    case Input.Keys.F5 -> switchTab(Tab.LOGISTICS);
                    case Input.Keys.HOME -> {
                        renderer.resetSystemMapCamera();
                        yield true;
                    }
                    case Input.Keys.SPACE -> togglePause();
                    case Input.Keys.NUM_1 -> setTimeScale(1d);
                    case Input.Keys.NUM_2 -> setTimeScale(2d);
                    case Input.Keys.NUM_3 -> setTimeScale(4d);
                    case Input.Keys.NUM_4 -> setTimeScale(8d);
                    case Input.Keys.F8 -> save();
                    case Input.Keys.F9 -> load();
                    case Input.Keys.ESCAPE -> exit();
                    default -> false;
                };
            }

            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                if (renderer == null) {
                    return false;
                }
                float uiY = Gdx.graphics.getHeight() - screenY;
                if (button == Input.Buttons.MIDDLE && renderer.isMapPoint(screenX, uiY)) {
                    middleDragging = true;
                    dragPointer = pointer;
                    previousDragX = screenX;
                    previousDragY = uiY;
                    return true;
                }
                if (button != Input.Buttons.LEFT) {
                    return false;
                }
                var hit = renderer.hitTest(screenX, uiY);
                if (hit == null) {
                    return false;
                }
                detailScrollRows = 0;
                if (hit.kind() == HitKind.TAB) {
                    return switchTab(hit.tab());
                }
                selection = switch (hit.kind()) {
                    case LOCAL_OBJECT -> new UiSelection(SelectionKind.LOCAL_OBJECT, hit.id());
                    case SYSTEM -> new UiSelection(SelectionKind.SYSTEM, hit.id());
                    case FACTION -> new UiSelection(SelectionKind.FACTION, hit.id());
                    case FREIGHT -> new UiSelection(SelectionKind.FREIGHT, hit.id());
                    case MILITARY -> new UiSelection(SelectionKind.MILITARY, hit.id());
                    case ACTIVATE_SYSTEM -> selection;
                    case TAB -> throw new IllegalStateException("Tab hit handled above");
                };
                if (hit.kind() == HitKind.ACTIVATE_SYSTEM) {
                    StarSystemId target = new StarSystemId(Long.parseLong(hit.id()));
                    session.runtime().world().activateSystem(target);
                    renderer.resetSystemMapCamera();
                    tab = Tab.SYSTEM;
                    selection = UiSelection.none();
                    status = "Активная область симуляции: система #" + target.value()
                            + ". Флоты не телепортированы.";
                    snapshot = model.capture();
                }
                if (hit.kind() == HitKind.LOCAL_OBJECT && isDoubleClick(hit)) {
                    return renderer.focusLocalObject(snapshot, hit.id());
                }
                if ((hit.kind() == HitKind.FREIGHT || hit.kind() == HitKind.MILITARY)
                        && isDoubleClick(hit)) {
                    return focusFleet(Long.parseLong(hit.id()));
                }
                return true;
            }

            @Override
            public boolean touchDragged(int screenX, int screenY, int pointer) {
                if (!middleDragging || pointer != dragPointer || renderer == null) {
                    return false;
                }
                float uiY = Gdx.graphics.getHeight() - screenY;
                float deltaX = screenX - previousDragX;
                float deltaY = uiY - previousDragY;
                previousDragX = screenX;
                previousDragY = uiY;
                return renderer.panMap(tab, deltaX, deltaY);
            }

            @Override
            public boolean touchUp(int screenX, int screenY, int pointer, int button) {
                if (button == Input.Buttons.MIDDLE && middleDragging && pointer == dragPointer) {
                    middleDragging = false;
                    dragPointer = -1;
                    return true;
                }
                return false;
            }

            @Override
            public boolean scrolled(float amountX, float amountY) {
                float x = Gdx.input.getX();
                float y = Gdx.graphics.getHeight() - Gdx.input.getY();
                if (renderer != null && renderer.zoomMap(tab, x, y, amountY)) {
                    return true;
                }
                if (renderer == null || !renderer.isInspectorPoint(x, y)) {
                    if (renderer == null || !renderer.isListPoint(x, y)) {
                        return false;
                    }
                    int listDelta = amountY > 0f ? 1 : amountY < 0f ? -1 : 0;
                    listScrollRows = Math.max(0, Math.min(10_000, listScrollRows + listDelta));
                    return listDelta != 0;
                }
                int delta = amountY > 0f ? 3 : amountY < 0f ? -3 : 0;
                detailScrollRows = Math.max(0, Math.min(200, detailScrollRows + delta));
                return delta != 0;
            }
        };
    }

    private boolean isDoubleClick(GeneratedWorldCommandUiRenderer.HitTarget hit) {
        long now = System.nanoTime();
        boolean result = hit.kind() == previousClickKind
                && hit.id().equals(previousClickId)
                && now - previousClickNanos <= DOUBLE_CLICK_NANOS;
        previousClickKind = hit.kind();
        previousClickId = hit.id();
        previousClickNanos = now;
        return result;
    }

    private boolean focusFleet(long fleetIdValue) {
        var runtime = session.runtime();
        var placement = runtime.world().findFleet(new com.spacesim.world.FleetId(fleetIdValue))
                .orElse(null);
        if (placement == null) {
            status = "Корабль #" + fleetIdValue + " уже не существует.";
            return true;
        }
        if (placement.locationKind() == com.spacesim.world.FleetLocationKind.IN_TRANSIT) {
            status = "Корабль #" + fleetIdValue
                    + " находится в межсистемном перелёте; локальной точки для камеры нет.";
            return true;
        }
        boolean changedSystem = !runtime.world().getActiveSystemId().equals(placement.systemId());
        runtime.world().activateSystem(placement.systemId());
        if (changedSystem) {
            renderer.resetSystemMapCamera();
        }
        tab = Tab.SYSTEM;
        selection = new UiSelection(SelectionKind.LOCAL_OBJECT, "fleet:" + fleetIdValue);
        detailScrollRows = 0;
        listScrollRows = 0;
        snapshot = model.capture();
        if (renderer.focusLocalObject(snapshot, selection.stableId())) {
            status = "Камера переведена к кораблю #" + fleetIdValue + ".";
        } else {
            status = "Корабль #" + fleetIdValue + " не имеет локальной визуализации.";
        }
        return true;
    }

    private boolean switchTab(Tab target) {
        tab = target;
        selection = UiSelection.none();
        detailScrollRows = 0;
        listScrollRows = 0;
        status = "Открыта вкладка «" + target.label().toLowerCase(Locale.ROOT) + "».";
        return true;
    }

    private boolean togglePause() {
        boolean paused = !session.isPaused();
        session.setPaused(paused);
        status = paused ? "Симуляция приостановлена." : "Симуляция продолжена.";
        return true;
    }

    private boolean setTimeScale(double scale) {
        session.setTimeScale(scale);
        status = String.format(Locale.ROOT, "Скорость симуляции ×%.0f.", scale);
        return true;
    }

    private boolean save() {
        try {
            Stage21IGeneratedWorldRuntimePersistenceCodec.write(savePath, campaign.captureState());
            status = "Кампания сохранена: " + SAVE_FILE + ".";
        } catch (IOException | RuntimeException exception) {
            status = "Ошибка сохранения: " + safeMessage(exception);
        }
        return true;
    }

    private boolean load() {
        try {
            Path source = Files.exists(savePath) ? savePath : legacySavePath;
            bindCampaign(GeneratedCampaignCoordinator.restore(
                    Stage21IGeneratedWorldRuntimePersistenceCodec.readOrMigrate(source)));
            selection = UiSelection.none();
            detailScrollRows = 0;
            listScrollRows = 0;
            renderer.resetSystemMapCamera();
            snapshot = model.capture();
            status = source.equals(legacySavePath)
                    ? "Старое сохранение generated world мигрировано в полную кампанию без повторной генерации."
                    : "Полная кампания Stage-21I загружена без повторной генерации.";
        } catch (IOException | RuntimeException exception) {
            status = "Ошибка загрузки: " + safeMessage(exception);
        }
        return true;
    }

    private void bindCampaign(GeneratedCampaignCoordinator nextCampaign) {
        campaign = nextCampaign;
        session = campaign.session();
        model = new GeneratedWorldUiModel(session.rootSeed(), session.runtime(), session.content());
    }

    private boolean exit() {
        Gdx.app.exit();
        return true;
    }

    /** Advances the ordinary campaign session and renders its current read-only projection. */
    @Override
    public void render() {
        float delta = Math.min(0.1f, Math.max(0f, Gdx.graphics.getDeltaTime()));
        try {
            session.advanceFrame(delta);
        } catch (RuntimeException exception) {
            status = "Автономная симуляция остановила операцию: " + safeMessage(exception);
        }
        snapshot = model.capture();
        renderer.render(snapshot, tab, selection, detailScrollRows, listScrollRows,
                session.isPaused(), session.timeScale(), status);
        characterPortraitOverlay.render(snapshot, tab, selection);
    }

    /** Keeps the UI in logical screen coordinates and regenerates fonts for the new pixel size. */
    @Override
    public void resize(int width, int height) {
        if (renderer != null) {
            renderer.resize(width, height);
        }
        if (characterPortraitOverlay != null) {
            characterPortraitOverlay.resize(width, height);
        }
    }

    /** Releases all owned graphics resources. */
    @Override
    public void dispose() {
        if (Gdx.input != null) {
            Gdx.input.setInputProcessor(null);
        }
        if (renderer != null) {
            renderer.dispose();
        }
        if (characterPortraitOverlay != null) {
            characterPortraitOverlay.dispose();
        }
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}
