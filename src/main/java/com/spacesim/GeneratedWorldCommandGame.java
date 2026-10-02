package com.spacesim;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.ui.FactionCharacterPortraitOverlay;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitKind;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.SelectionKind;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.UiSelection;
import com.spacesim.ui.GeneratedWorldUiModel;
import com.spacesim.ui.GeneratedWorldUiSnapshot;
import com.spacesim.ui.ProductionUiProjector;
import com.spacesim.ui.ProductionUiSnapshot;
import com.spacesim.ui.ProductionUiWorkspace;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.StarSystemId;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Production navigation over the accepted generated campaign; owns only presentation state. */
public final class GeneratedWorldCommandGame extends ApplicationAdapter {
    private static final String SAVE_FILE = "saves/generated-world-runtime.s25";
    private static final long DOUBLE_CLICK_NANOS = 450_000_000L;
    private final long initialSeed;
    private final ProductionUiWorkspace workspace = new ProductionUiWorkspace();
    private Stage228CampaignAuthority campaign;
    private GeneratedWorldUiModel model;
    private GeneratedWorldCommandUiRenderer renderer;
    private FactionCharacterPortraitOverlay portraits;
    private GeneratedWorldUiSnapshot snapshot;
    private ProductionUiProjector projector;
    private ProductionUiSnapshot production;
    private Stage228CampaignAuthority.MissionCommandPreview pendingMissionPreview;
    private Stage228CampaignAuthority.PilotStartPreview pendingPilotStart;
    private Stage228CampaignAuthority.PlayerPhysicalPreview pendingPilotPhysical;
    private String pendingPilotSelection = "";
    private int pilotKilograms = 1;
    private float projectionAge;
    private String status = "Генерация мира…";
    private Path savePath;
    private int dragPointer = -1;
    private float previousDragX;
    private float previousDragY;
    private HitKind previousClickKind;
    private String previousClickId = "";
    private long previousClickNanos;

    /** @param initialSeed exact deterministic campaign seed */
    public GeneratedWorldCommandGame(long initialSeed) { this.initialSeed = initialSeed; }

    /** Generates the campaign and binds all read-only surfaces to the same authority. */
    @Override
    public void create() {
        campaign = Stage228CampaignAuthority.create(initialSeed);
        bindCampaign();
        renderer = new GeneratedWorldCommandUiRenderer();
        portraits = new FactionCharacterPortraitOverlay();
        savePath = Gdx.files.local(SAVE_FILE).file().toPath();
        campaign.coordinator().setPaused(true);
        campaign.coordinator().setTimeScale(1d);
        workspace.navigate(Tab.SETTINGS);
        workspace.select(new UiSelection(SelectionKind.SURFACE_ROW, "pilot-start"));
        status = "Новая игра на паузе. Проверьте условия старта независимого пилота.";
        Gdx.input.setInputProcessor(input());
    }

    private void bindCampaign() {
        var coordinator = campaign.coordinator();
        model = new GeneratedWorldUiModel(coordinator.rootSeed(), coordinator.runtime(), coordinator.content());
        snapshot = model.capture();
        // A knowledge viewer is not player ownership. No ship, wallet or control is granted here.
        String viewer = coordinator.actors().capture().stream()
                .map(actor -> actor.factionContentId()).sorted().findFirst().orElseThrow();
        projector = new ProductionUiProjector(viewer);
        refreshProjection();
    }

    private void refreshProjection() {
        production = projector.capture(campaign, snapshot, true);
        projectionAge = 0f;
    }

    private InputAdapter input() {
        return new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (workspace.searching()) {
                    if (keycode == Input.Keys.ENTER || keycode == Input.Keys.ESCAPE) workspace.endSearch();
                    return true; // Text editing never sends gameplay/time/save commands.
                }
                if (keycode == Input.Keys.F && controlHeld()) {
                    if (workspace.tab() == Tab.SYSTEM || workspace.tab() == Tab.GALAXY) switchTab(Tab.INTELLIGENCE);
                    workspace.beginSearch();
                    return true;
                }
                return switch (keycode) {
                    case Input.Keys.F1 -> switchTab(Tab.SYSTEM);
                    case Input.Keys.F2 -> switchTab(Tab.GALAXY);
                    case Input.Keys.F3 -> switchTab(Tab.FACTIONS);
                    case Input.Keys.F4 -> switchTab(Tab.MILITARY);
                    case Input.Keys.F5 -> switchTab(Tab.LOGISTICS);
                    case Input.Keys.F6 -> switchTab(Tab.CONTACTS);
                    case Input.Keys.F7 -> switchTab(Tab.SETTINGS);
                    case Input.Keys.TAB -> {
                        renderer.moveKeyboardFocus(Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                                || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT) ? -1 : 1);
                        yield true;
                    }
                    case Input.Keys.ENTER -> renderer.keyboardTarget() == null
                            ? focusSelection() : activate(renderer.keyboardTarget(), false);
                    case Input.Keys.UP -> moveSelection(-1);
                    case Input.Keys.DOWN -> moveSelection(1);
                    case Input.Keys.PAGE_UP -> scroll(-5);
                    case Input.Keys.PAGE_DOWN -> scroll(5);
                    case Input.Keys.LEFT -> renderer.keyboardPan(workspace.tab(), 32f, 0f);
                    case Input.Keys.RIGHT -> renderer.keyboardPan(workspace.tab(), -32f, 0f);
                    case Input.Keys.PLUS, Input.Keys.EQUALS -> keyboardZoom(-1f);
                    case Input.Keys.MINUS -> keyboardZoom(1f);
                    case Input.Keys.HOME -> {
                        renderer.resetSystemMapCamera();
                        status = "Камера: обзор системы.";
                        yield true;
                    }
                    case Input.Keys.C -> focusSelection();
                    case Input.Keys.O -> switchTab(Tab.INTELLIGENCE);
                    case Input.Keys.SPACE -> togglePause();
                    case Input.Keys.NUM_1 -> setTimeScale(1d);
                    case Input.Keys.NUM_2 -> setTimeScale(2d);
                    case Input.Keys.NUM_3 -> setTimeScale(4d);
                    case Input.Keys.NUM_4 -> setTimeScale(8d);
                    case Input.Keys.F8 -> save();
                    case Input.Keys.F9 -> load();
                    case Input.Keys.ESCAPE -> {
                        if (!workspace.goBack()) workspace.navigate(Tab.SETTINGS);
                        renderer.clearKeyboardFocus();
                        yield true;
                    }
                    default -> false;
                };
            }

            @Override
            public boolean keyTyped(char character) {
                if (!workspace.searching()) return false;
                workspace.type(character);
                return true;
            }

            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                float y = Gdx.graphics.getHeight() - screenY;
                if (button == Input.Buttons.MIDDLE && renderer.isMapPoint(screenX, y)) {
                    dragPointer = pointer;
                    previousDragX = screenX;
                    previousDragY = y;
                    return true;
                }
                if (button != Input.Buttons.LEFT) return false;
                var hit = renderer.hitTest(screenX, y);
                if (hit == null) return false;
                renderer.clearKeyboardFocus();
                return activate(hit, isDoubleClick(hit));
            }

            @Override
            public boolean touchDragged(int screenX, int screenY, int pointer) {
                if (pointer != dragPointer) return false;
                float y = Gdx.graphics.getHeight() - screenY;
                boolean result = renderer.panMap(workspace.tab(), screenX - previousDragX, y - previousDragY);
                previousDragX = screenX;
                previousDragY = y;
                return result;
            }

            @Override
            public boolean touchUp(int x, int y, int pointer, int button) {
                if (button != Input.Buttons.MIDDLE || pointer != dragPointer) return false;
                dragPointer = -1;
                return true;
            }

            @Override
            public boolean scrolled(float xAmount, float yAmount) {
                float x = Gdx.input.getX();
                float y = Gdx.graphics.getHeight() - Gdx.input.getY();
                if (renderer.zoomMap(workspace.tab(), x, y, yAmount)) return true;
                int delta = yAmount > 0 ? 1 : yAmount < 0 ? -1 : 0;
                if (renderer.isInspectorPoint(x, y)) workspace.scrollDetail(delta * 3);
                else if (renderer.isListPoint(x, y)) workspace.scrollList(delta);
                else return false;
                return delta != 0;
            }
        };
    }

    private boolean activate(GeneratedWorldCommandUiRenderer.HitTarget hit, boolean doubleClick) {
        if (hit.kind() == HitKind.TAB) return switchTab(hit.tab());
        if (hit.kind() == HitKind.ACTION) return action(hit.id());
        if (hit.kind() == HitKind.ACTIVATE_SYSTEM) return openSystem(new StarSystemId(Long.parseLong(hit.id())));
        UiSelection selected = switch (hit.kind()) {
            case LOCAL_OBJECT -> new UiSelection(SelectionKind.LOCAL_OBJECT, hit.id());
            case SYSTEM -> new UiSelection(SelectionKind.SYSTEM, hit.id());
            case FACTION -> new UiSelection(SelectionKind.FACTION, hit.id());
            case FREIGHT -> new UiSelection(SelectionKind.FREIGHT, hit.id());
            case MILITARY -> new UiSelection(SelectionKind.MILITARY, hit.id());
            case SURFACE_ROW -> production.rows(workspace.tab()).stream()
                    .map(ProductionUiSnapshot.Row::selection).filter(value -> value.stableId().equals(hit.id()))
                    .findFirst().orElse(UiSelection.none());
            default -> UiSelection.none();
        };
        workspace.select(selected);
        if (doubleClick) return focusSelection();
        return true;
    }

    private boolean action(String id) {
        return switch (id) {
            case "back" -> workspace.goBack();
            case "search" -> {
                if (workspace.tab() == Tab.SYSTEM || workspace.tab() == Tab.GALAXY) switchTab(Tab.INTELLIGENCE);
                workspace.beginSearch(); yield true;
            }
            case "sort" -> { workspace.cycleSort(); yield true; }
            case "filter" -> { workspace.cycleCategory(production); yield true; }
            case "density" -> { workspace.cycleDensity(); yield true; }
            case "pause" -> togglePause();
            case "save" -> save();
            case "load" -> load();
            case "focus" -> focusSelection();
            case "pilot.preview" -> {
                if (!selectedPilotStart()) yield false;
                campaign.coordinator().setPaused(true);
                pendingPilotStart = campaign.previewIndependentPilotStart();
                status = pendingPilotStart.allowed() ? "Условия проверены. Подтверждение переведёт оплату продавцу и оформит личное владение."
                        : "Старт недоступен: резервный корабль или возможность оплаты отсутствуют.";
                refreshProjection(); yield true;
            }
            case "pilot.confirm" -> {
                var preview = pendingPilotStart; pendingPilotStart = null;
                if (preview == null || !selectedPilotStart()) yield false;
                try {
                    var pilot = campaign.submitIndependentPilotStart(preview);
                    var world = campaign.coordinator().runtime().world();
                    world.activateSystem(world.findFleet(pilot.activeFleetId()).orElseThrow().systemId());
                    renderer.resetSystemMapCamera();
                    switchTab(Tab.SHIPS);
                    workspace.select(new UiSelection(SelectionKind.SURFACE_ROW, "personal-ship:" + preview.fleetId().value()));
                    status = "Корабль куплен. Кампания на паузе; Пробел — продолжить.";
                } catch (IllegalStateException exception) {
                    status = "Условия изменились. Проверьте старт заново.";
                }
                snapshot = model.capture(); refreshProjection(); yield true;
            }
            case "pilot.less", "pilot.more" -> {
                pilotKilograms = Math.max(1, Math.min(10000, pilotKilograms + (id.equals("pilot.less") ? -1 : 1)));
                pendingPilotPhysical = null; yield true;
            }
            case "pilot.dock" -> previewPilotPhysical("DOCK");
            case "pilot.undock" -> previewPilotPhysical("UNDOCK");
            case "pilot.buy" -> previewPilotPhysical("BUY");
            case "pilot.sell" -> previewPilotPhysical("SELL");
            case "pilot.physical-confirm" -> {
                var p = pendingPilotPhysical; pendingPilotPhysical = null;
                if (p == null || !pendingPilotSelection.equals(workspace.view().selection().stableId())) yield false;
                try { campaign.submitPilotAction(p); status = "Действие выполнено. Для новой сделки продолжите время: Пробел."; }
                catch (IllegalStateException exception) { status = "Состояние изменилось. Проверьте действие заново."; }
                snapshot = model.capture(); refreshProjection(); yield true;
            }
            case "mission.accept" -> previewMission(com.spacesim.world.Stage21HNpcMissionService.PlayerCommand.ACCEPT);
            case "mission.reject" -> previewMission(com.spacesim.world.Stage21HNpcMissionService.PlayerCommand.REJECT);
            case "mission.cancel" -> previewMission(com.spacesim.world.Stage21HNpcMissionService.PlayerCommand.CANCEL);
            case "mission.confirm" -> confirmMission();
            case "exit" -> { Gdx.app.exit(); yield true; }
            default -> false;
        };
    }

    private boolean selectedPilotStart() {
        return workspace.tab() == Tab.SETTINGS && workspace.view().selection().stableId().equals("pilot-start")
                && campaign.canStartIndependentPilot();
    }

    private boolean previewPilotPhysical(String action) {
        String id = workspace.view().selection().stableId();
        String[] pieces = id.split("\\|", -1);
        if (workspace.tab() != Tab.LOGISTICS || pieces.length < 2
                || !(pieces[0].equals("pilot-station") || pieces[0].equals("pilot-market"))) return false;
        campaign.coordinator().setPaused(true);
        pendingPilotSelection = id;
        pendingPilotPhysical = campaign.previewPilotAction(action, pieces[1], pieces.length > 2 ? pieces[2] : "", pilotKilograms);
        status = pendingPilotPhysical.allowed() ? ((action.equals("BUY") || action.equals("SELL"))
                ? String.format(java.util.Locale.ROOT, "Проверено: %d кг; кошелёк %+.3f кр. Подтвердите сделку.", pilotKilograms, pendingPilotPhysical.walletChangeMilliCredits() / 1000d)
                : "Стыковка проверена. Подтвердите действие.")
                : "Действие недоступно: проверьте расстояние, скорость, стыковку, запас, деньги и обработку за такт.";
        refreshProjection(); return true;
    }

    private String selectedPersonalMissionId() {
        var selected = workspace.view().selection();
        String prefix = "player-mission:";
        return workspace.tab() == Tab.CONTACTS && selected.kind() == SelectionKind.SURFACE_ROW
                && selected.stableId().startsWith(prefix)
                && production.find(Tab.CONTACTS, selected).isPresent()
                ? selected.stableId().substring(prefix.length()) : null;
    }

    private boolean previewMission(com.spacesim.world.Stage21HNpcMissionService.PlayerCommand command) {
        String id = selectedPersonalMissionId();
        pendingMissionPreview = null;
        if (id == null) return false;
        campaign.coordinator().setPaused(true);
        pendingMissionPreview = campaign.previewMissionCommand(command, id);
        status = pendingMissionPreview.allowed()
                ? switch (command) {
                    case ACCEPT -> "На паузе. Принять контракт? Награда выплачивается только после проверки результата и участия.";
                    case REJECT -> "На паузе. Отклонить предложение? Эскроу вернётся выдавшей контракт фракции.";
                    case CANCEL -> "На паузе. Отменить контракт? Эскроу вернётся выдавшей контракт фракции.";
                }
                : "Действие недоступно: проверьте срок, состояние контракта и личную доступность выдающего контакт.";
        refreshProjection();
        return true;
    }

    private boolean confirmMission() {
        var preview = pendingMissionPreview;
        pendingMissionPreview = null;
        if (preview == null || !preview.missionId().equals(selectedPersonalMissionId())) return false;
        try {
            campaign.submitMissionCommand(preview);
            status = "Контракт обновлён. Кампания на паузе; Пробел — продолжить.";
        } catch (RuntimeException exception) {
            status = "Действие не выполнено: состояние изменилось или команда недоступна. Проверьте действие снова.";
        }
        snapshot = model.capture();
        refreshProjection();
        return true;
    }

    private boolean moveSelection(int delta) {
        if (workspace.tab() == Tab.SYSTEM) {
            List<UiSelection> selections = snapshot.localObjects().stream()
                    .map(value -> new UiSelection(SelectionKind.LOCAL_OBJECT, value.stableId())).toList();
            selectMapNeighbor(selections, delta);
        } else if (workspace.tab() == Tab.GALAXY) {
            List<UiSelection> selections = snapshot.galaxy().systems().stream()
                    .map(value -> new UiSelection(SelectionKind.SYSTEM, Long.toString(value.id().value()))).toList();
            selectMapNeighbor(selections, delta);
        } else {
            workspace.moveSelection(production, delta, 5);
        }
        renderer.clearKeyboardFocus();
        return true;
    }

    private void selectMapNeighbor(List<UiSelection> selections, int delta) {
        if (selections.isEmpty()) return;
        int index = selections.indexOf(workspace.view().selection());
        int next = index < 0 ? 0 : Math.floorMod(index + delta, selections.size());
        workspace.select(selections.get(next));
    }

    private boolean scroll(int delta) {
        if (controlHeld()) workspace.scrollDetail(delta);
        else workspace.scrollList(delta);
        return true;
    }

    private boolean keyboardZoom(float amount) {
        return renderer.zoomMap(workspace.tab(), Gdx.graphics.getWidth() * 0.35f,
                Gdx.graphics.getHeight() * 0.5f, amount);
    }

    private boolean focusSelection() {
        UiSelection selected = workspace.view().selection();
        if (selected.kind() == SelectionKind.FREIGHT || selected.kind() == SelectionKind.MILITARY) {
            return focusFleet(Long.parseLong(selected.stableId()));
        }
        if (workspace.tab() == Tab.GALAXY && selected.kind() == SelectionKind.SYSTEM) {
            return openSystem(new StarSystemId(Long.parseLong(selected.stableId())));
        }
        var row = production.find(workspace.tab(), selected).orElse(null);
        if (row != null) {
            if (row.focusFleet() > 0) return focusFleet(row.focusFleet());
            if (row.focusSystem() != null) {
                openSystem(row.focusSystem());
                String objectId = selected.stableId().startsWith("pilot-station|")
                        ? "station:" + selected.stableId().substring("pilot-station|".length()) : selected.stableId();
                workspace.select(new UiSelection(SelectionKind.LOCAL_OBJECT, objectId));
                return renderer.focusLocalObject(snapshot, objectId);
            }
        }
        return selected.kind() == SelectionKind.LOCAL_OBJECT
                && renderer.focusLocalObject(snapshot, selected.stableId());
    }

    private boolean focusFleet(long fleetId) {
        var placement = campaign.coordinator().runtime().world().findFleet(new FleetId(fleetId)).orElse(null);
        if (placement == null || placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
            status = "Нельзя открыть корабль: нет текущей локальной позиции (перелёт или потеря).";
            return true;
        }
        openSystem(placement.systemId());
        workspace.select(new UiSelection(SelectionKind.LOCAL_OBJECT, "fleet:" + fleetId));
        status = renderer.focusLocalObject(snapshot, workspace.view().selection().stableId())
                ? "Камера сопровождает корабль. СКМ/Home — обзор." : "У корабля нет локальной визуализации.";
        return true;
    }

    private boolean openSystem(StarSystemId system) {
        var world = campaign.coordinator().runtime().world();
        if (world.getTopology().findSystem(system).isEmpty()) {
            status = "Система больше недоступна."; return true;
        }
        boolean changed = !world.getActiveSystemId().equals(system);
        world.activateSystem(system);
        if (changed) renderer.resetSystemMapCamera();
        switchTab(Tab.SYSTEM);
        snapshot = model.capture();
        refreshProjection();
        status = "Открыта система «" + snapshot.activeSystemName() + "».";
        return true;
    }

    private boolean switchTab(Tab target) {
        pendingMissionPreview = null;
        pendingPilotStart = null;
        pendingPilotPhysical = null;
        workspace.navigate(target);
        renderer.clearKeyboardFocus();
        dragPointer = -1;
        status = "Открыт раздел «" + target.label() + "».";
        return true;
    }

    private boolean isDoubleClick(GeneratedWorldCommandUiRenderer.HitTarget hit) {
        long now = System.nanoTime();
        boolean result = hit.kind() == previousClickKind && hit.id().equals(previousClickId)
                && now - previousClickNanos <= DOUBLE_CLICK_NANOS;
        previousClickKind = hit.kind(); previousClickId = hit.id(); previousClickNanos = now;
        return result;
    }

    private boolean togglePause() {
        boolean paused = !campaign.coordinator().isPaused();
        campaign.coordinator().setPaused(paused);
        status = paused ? "Симуляция приостановлена." : "Симуляция продолжена.";
        return true;
    }

    private boolean setTimeScale(double scale) {
        campaign.coordinator().setTimeScale(scale);
        status = "Скорость симуляции ×" + (int) scale;
        return true;
    }

    private boolean save() {
        if (campaign.canStartIndependentPilot()) {
            status = "Сначала завершите создание пилота: Меню — условия старта. Загрузка доступна.";
            return true;
        }
        try {
            Stage228GeneratedCampaignPersistenceCodec.write(savePath, campaign.captureState());
            status = "Кампания сохранена.";
        } catch (IOException | RuntimeException exception) {
            status = "Ошибка сохранения: " + safeMessage(exception);
        }
        return true;
    }

    private boolean load() {
        try {
            var candidate = Stage228CampaignAuthority.restore(
                    Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(savePath));
            var coordinator = candidate.coordinator();
            var nextModel = new GeneratedWorldUiModel(coordinator.rootSeed(), coordinator.runtime(), coordinator.content());
            var nextSnapshot = nextModel.capture();
            var nextProjector = new ProductionUiProjector(coordinator.actors().capture().stream()
                    .map(actor -> actor.factionContentId()).sorted().findFirst().orElseThrow());
            var nextProjection = nextProjector.capture(candidate, nextSnapshot, true);
            pendingMissionPreview = null;
            pendingPilotStart = null;
            pendingPilotPhysical = null;
            campaign = candidate; model = nextModel; snapshot = nextSnapshot;
            projector = nextProjector; production = nextProjection; projectionAge = 0;
            workspace.reset(); renderer.resetSystemMapCamera(); renderer.clearKeyboardFocus();
            status = "Кампания загружена.";
        } catch (IOException | RuntimeException exception) {
            status = "Ошибка загрузки: " + safeMessage(exception);
        }
        return true;
    }

    /** Advances one authority clock and refreshes slower strategic projections at bounded cadence. */
    @Override
    public void render() {
        float delta = Math.min(0.1f, Math.max(0f, Gdx.graphics.getDeltaTime()));
        try {
            boolean flying = !workspace.searching() && workspace.tab() == Tab.SYSTEM;
            campaign.setPilotThrust(flying ? (Gdx.input.isKeyPressed(Input.Keys.D) ? 1 : 0) - (Gdx.input.isKeyPressed(Input.Keys.A) ? 1 : 0) : 0,
                    flying ? (Gdx.input.isKeyPressed(Input.Keys.W) ? 1 : 0) - (Gdx.input.isKeyPressed(Input.Keys.S) ? 1 : 0) : 0,
                    flying && Gdx.input.isKeyPressed(Input.Keys.X));
            campaign.advanceFrame(delta);
            snapshot = model.capture();
            projectionAge += delta;
            if (projectionAge >= 0.25f) refreshProjection();
        } catch (RuntimeException exception) {
            campaign.coordinator().setPaused(true);
            status = "Сведения недоступны; симуляция на паузе: " + safeMessage(exception);
        }
        String missionId = selectedPersonalMissionId();
        if (pendingMissionPreview != null && !pendingMissionPreview.missionId().equals(missionId)) pendingMissionPreview = null;
        renderer.bindMissionActions(missionId != null, pendingMissionPreview != null && pendingMissionPreview.allowed());
        if (!selectedPilotStart()) pendingPilotStart = null;
        renderer.bindPilotStartActions(selectedPilotStart(), pendingPilotStart != null && pendingPilotStart.allowed());
        String selectedPilot = workspace.view().selection().stableId();
        if (!selectedPilot.equals(pendingPilotSelection)) pendingPilotPhysical = null;
        renderer.bindPhysicalPilotActions(workspace.tab() == Tab.LOGISTICS && (selectedPilot.startsWith("pilot-market|") || selectedPilot.startsWith("pilot-station|")),
                selectedPilot.startsWith("pilot-market|"), pendingPilotPhysical != null && pendingPilotPhysical.allowed(), pilotKilograms);
        renderer.bindSaveAvailability(!campaign.canStartIndependentPilot());
        renderer.bindWorkspace(production, workspace);
        renderer.render(snapshot, workspace.tab(), workspace.view().selection(), workspace.view().detailScroll(),
                workspace.view().listScroll(), campaign.coordinator().isPaused(), campaign.coordinator().timeScale(),
                campaign.coordinator().interpolationAlpha(), status);
        portraits.render(snapshot, workspace.tab(), workspace.view().selection());
    }

    /**
     * @param width logical width
     * @param height logical height
     */
    @Override
    public void resize(int width, int height) {
        if (renderer != null) renderer.resize(width, height);
        if (portraits != null) portraits.resize(width, height);
    }

    /** Releases graphics resources. */
    @Override
    public void dispose() {
        if (Gdx.input != null) Gdx.input.setInputProcessor(null);
        if (renderer != null) renderer.dispose();
        if (portraits != null) portraits.dispose();
    }

    private static boolean controlHeld() {
        return Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
    }

    static String safeMessage(Throwable exception) {
        // Exception messages can include absolute paths or save payload fragments.
        if (exception instanceof IOException) return "Не удалось прочитать или записать файл.";
        if (exception instanceof IllegalArgumentException) return "Данные или параметры не прошли проверку.";
        if (exception instanceof IllegalStateException) return "Состояние кампании не прошло проверку.";
        return "Внутренняя ошибка обработки.";
    }
}
