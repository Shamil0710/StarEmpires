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
    private Stage228CampaignAuthority.PilotRoutePreview pendingRoute;
    private String pendingRouteSelection = "";
    private Stage228CampaignAuthority.PlayerFactionCommandPreview pendingGovernment;
    private String pendingGovernmentSelection = "";
    private Stage228CampaignAuthority.PlayerFactionFoundationPreview pendingFactionFoundation;
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
        pendingGovernment = null;
        pendingRoute = null;
        var coordinator = campaign.coordinator();
        model = new GeneratedWorldUiModel(coordinator.rootSeed(), coordinator.runtime(), coordinator.content(),
                com.spacesim.ui.GeneratedWorldUiModel.CarrierUiSource.none(), () -> campaign.playerState().orElse(null));
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
                    case Input.Keys.C -> controlHeld() ? focusPlayer() : focusSelection();
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
        if (id.startsWith("pilot.government-")) return governmentAction(id.substring("pilot.government-".length()));
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
            case "focus-player" -> focusPlayer();
            case "pilot.route-preview" -> {
                var selected = workspace.view().selection();
                if (workspace.tab() != Tab.GALAXY || selected.kind() != SelectionKind.SYSTEM) yield false;
                campaign.coordinator().setPaused(true);
                pendingRouteSelection = selected.stableId();
                pendingRoute = campaign.previewPilotRoute(new StarSystemId(Long.parseLong(selected.stableId()))).orElse(null);
                status = pendingRoute == null ? "Маршрут недоступен: нужны открытые системы, местный активный корабль и подходящий запас топлива."
                        : pendingRoute.departure().allowed() ? "Маршрут рассчитан. Подтверждение отправит корабль только в первую систему пути."
                        : "Путь рассчитан, но первый вылет недоступен. Проверьте стыковку, топливо и готовность FTL.";
                yield true;
            }
            case "pilot.route-confirm" -> {
                var preview = pendingRoute; pendingRoute = null;
                if (preview == null || workspace.tab() != Tab.GALAXY
                        || !workspace.view().selection().stableId().equals(pendingRouteSelection)) yield false;
                try {
                    campaign.submitPilotAction(preview.departure());
                    status = "Первый вылет начат. Пробел — продолжить время; после прибытия проверьте следующий участок маршрута.";
                } catch (IllegalStateException exception) { status = "Условия изменились. Рассчитайте маршрут заново."; }
                snapshot = model.capture(); refreshProjection(); yield true;
            }
            case "pilot.preview" -> {
                if (!selectedPilotStart()) yield false;
                campaign.coordinator().setPaused(true);
                pendingPilotStart = campaign.previewIndependentPilotStart();
                status = pendingPilotStart.allowed() ? "Условия проверены. Подтверждение оплатит корабль и оформит владение; продавец передаст имеющиеся у него координаты собственных гражданских станций."
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
                pendingPilotPhysical = null; pendingMissionPreview = null; yield true;
            }
            case "pilot.faction-preview" -> {
                if (workspace.tab() != Tab.SETTINGS || !workspace.view().selection().stableId().equals("pilot-faction-foundation")) yield false;
                campaign.coordinator().setPaused(true);
                pendingFactionFoundation = campaign.previewPlayerFactionFoundation("faction.player", "Содружество пилота");
                status = pendingFactionFoundation.allowed() ? "Основание проверено: нулевая казна, без территории и новых активов. Подтвердите." : "Основание недоступно.";
                refreshProjection(); yield true;
            }
            case "pilot.faction-confirm" -> {
                var preview = pendingFactionFoundation; pendingFactionFoundation = null;
                if (preview == null || !workspace.view().selection().stableId().equals("pilot-faction-foundation")) yield false;
                try {
                    campaign = campaign.submitPlayerFactionFoundation(preview);
                    pendingPilotPhysical = null; pendingMissionPreview = null;
                    bindCampaign(); workspace.select(new UiSelection(SelectionKind.SURFACE_ROW, "pilot-faction-finance"));
                    status = "Собственная фракция основана. Казна пуста; существующие личные средства доступны для пополнения.";
                } catch (IllegalStateException exception) { status = "Условия изменились. Проверьте основание заново."; }
                refreshProjection(); yield true;
            }
            case "pilot.faction-capitalize" -> previewPilotPhysical("CAPITALIZE");
            case "pilot.faction-withdraw" -> previewPilotPhysical("WITHDRAW");
            case "pilot.purchase" -> previewPilotPhysical("PURCHASE");
            case "pilot.purchase-station" -> previewPilotPhysical("PURCHASE_STATION");
            case "pilot.switch" -> previewPilotPhysical("SWITCH");
            case "pilot.jump" -> previewPilotPhysical("JUMP");
            case "pilot.load-consumable" -> previewPilotPhysical("LOAD_CONSUMABLE");
            case "pilot.start-mining" -> previewPilotPhysical("START_MINING");
            case "pilot.stop-mining" -> previewPilotPhysical("STOP_MINING");
            case "pilot.start-manufacturing" -> previewPilotPhysical("START_MANUFACTURING");
            case "pilot.start-construction" -> previewPilotPhysical("START_FACILITY_CONSTRUCTION");
            case "pilot.start-yard-construction" -> previewPilotPhysical("START_YARD_CONSTRUCTION");
            case "pilot.allocate-yard-resources" -> previewPilotPhysical("ALLOCATE_YARD_RESOURCES");
            case "pilot.cancel-yard-construction" -> previewPilotPhysical("CANCEL_YARD_CONSTRUCTION");
            case "pilot.cancel-construction" -> previewPilotPhysical("CANCEL_FACILITY_CONSTRUCTION");
            case "pilot.allocate-facility-resources" -> previewPilotPhysical("ALLOCATE_FACILITY_RESOURCES");
            case "pilot.start-repair" -> previewPilotPhysical("START_REPAIR");
            case "pilot.start-refit" -> previewPilotPhysical("START_REFIT");
            case "pilot.start-refit-used" -> previewPilotPhysical("START_REFIT_USED");
            case "pilot.load-module" -> previewPilotPhysical("LOAD_MODULE");
            case "pilot.unload-module" -> previewPilotPhysical("UNLOAD_MODULE");
            case "pilot.cancel-module-transfer" -> previewPilotPhysical("CANCEL_MODULE_TRANSFER");
            case "pilot.load-product" -> previewPilotPhysical("LOAD_PRODUCT");
            case "pilot.unload-product" -> previewPilotPhysical("UNLOAD_PRODUCT");
            case "pilot.cancel-product-transfer" -> previewPilotPhysical("CANCEL_PRODUCT_TRANSFER");
            case "pilot.cancel-refit" -> previewPilotPhysical("CANCEL_REFIT");
            case "pilot.cancel-repair" -> previewPilotPhysical("CANCEL_REPAIR");
            case "pilot.acknowledge-journal" -> previewPilotPhysical("ACKNOWLEDGE_JOURNAL");
            case "pilot.cancel-manufacturing" -> previewPilotPhysical("CANCEL_MANUFACTURING");
            case "pilot.report-discovery" -> previewPilotPhysical("REPORT_DISCOVERY");
            case "pilot.dock" -> previewPilotPhysical("DOCK");
            case "pilot.undock" -> previewPilotPhysical("UNDOCK");
            case "pilot.buy" -> previewPilotPhysical("BUY");
            case "pilot.sell" -> previewPilotPhysical("SELL");
            case "pilot.physical-confirm" -> {
                var p = pendingPilotPhysical; pendingPilotPhysical = null;
                if (p == null || !pendingPilotSelection.equals(workspace.view().selection().stableId())) yield false;
                try { campaign.submitPilotAction(p); status = "Действие выполнено. Пробел — продолжить время; личный корабль доступен во вкладке кораблей."; }
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

    private boolean governmentAction(String action) {
        String selected=workspace.view().selection().stableId();
        boolean fleet = workspace.tab()==Tab.MILITARY && !com.spacesim.ui.GeneratedCampaignFleetUi.actions(selected).isEmpty();
        if(production.find(workspace.tab(),workspace.view().selection()).isEmpty()
                || (!fleet && (workspace.tab()!=Tab.FACTIONS || com.spacesim.ui.GeneratedCampaignFactionUi.actions(selected).isEmpty())))return false;
        if(action.equals("confirm")) {
            var preview=pendingGovernment;pendingGovernment=null;
            if(preview==null||!selected.equals(pendingGovernmentSelection))return false;
            try {
                campaign=campaign.submitPlayerFactionCommand(preview);
                pendingPilotPhysical=null;pendingMissionPreview=null;pendingFactionFoundation=null;
                bindCampaign();status="Собственное решение выполнено. Ресурсы не предоставляются; Пробел — продолжить время.";
            } catch(IllegalStateException exception){status="Условия изменились. Проверьте решение заново.";}
        } else {
            campaign.coordinator().setPaused(true);pendingGovernmentSelection=selected;pendingGovernment=null;
            try {
                pendingGovernment=fleet ? com.spacesim.ui.GeneratedCampaignFleetUi.preview(campaign,selected,action) : com.spacesim.ui.GeneratedCampaignFactionUi.preview(campaign,selected,action);
                status=pendingGovernment.allowed()?(fleet ? "Личный приказ проверен. Топливо и ресурсы не выдаются; активный корабль остаётся под ручным управлением." : com.spacesim.ui.GeneratedCampaignFactionUi.explanation(campaign,selected,action))+" Подтвердите решение.":"Решение отклонено. Проверьте личное владение, известность цели и условия в сведениях.";
            } catch(IllegalArgumentException | ArithmeticException exception){status="Изменение вне допустимых границ. Проверьте значение и направление.";}
        }
        snapshot=model.capture();refreshProjection();return true;
    }

    private boolean selectedPilotStart() {
        return workspace.tab() == Tab.SETTINGS && workspace.view().selection().stableId().equals("pilot-start")
                && campaign.canStartIndependentPilot();
    }

    private boolean previewPilotPhysical(String action) {
        String id = workspace.view().selection().stableId();
        String[] pieces = id.split("\\|", -1);
        if ((action.equals("START_REFIT_USED") || action.equals("LOAD_MODULE")) && id.startsWith("stored-module|")) {
            String custodyId = id.substring("stored-module|".length());
            var row = campaign.moduleCustody().modules().stream().filter(m -> m.custodyId().equals(custodyId)).findFirst().orElse(null);
            if (row == null) return false;
            pieces = new String[]{"stored-module", row.stationId(), custodyId};
        }
        if (action.equals("UNLOAD_MODULE") && id.startsWith("carried-module|"))
            pieces = new String[]{"carried-module", campaign.dockedModuleStationId().orElse(""), id.substring("carried-module|".length())};
        if (action.equals("SWITCH") && id.startsWith("personal-ship:")) pieces = new String[]{"personal-ship", id.substring("personal-ship:".length())};
        boolean finance = workspace.tab() == Tab.SETTINGS && id.equals("pilot-faction-finance")
                && (action.equals("CAPITALIZE") || action.equals("WITHDRAW"));
        if (finance) pieces = new String[]{"pilot-faction-finance", "1000000"};
        boolean jump = action.equals("JUMP") && workspace.tab() == Tab.SHIPS && pieces[0].equals("pilot-jump");
        boolean supply = action.equals("LOAD_CONSUMABLE") && workspace.tab() == Tab.SHIPS && pieces[0].equals("pilot-supply") && pieces.length == 3;
        boolean report = action.equals("REPORT_DISCOVERY") && workspace.tab() == Tab.CONTACTS && pieces[0].equals("pilot-report") && pieces.length == 2;
        boolean mining = workspace.tab() == Tab.INDUSTRY && pieces.length == 2
                && (action.equals("START_MINING") && pieces[0].equals("pilot-mining")
                || action.equals("STOP_MINING") && pieces[0].equals("pilot-mining-stop"));
        boolean manufacturing = workspace.tab() == Tab.INDUSTRY && pieces.length == 3
                && (action.equals("START_MANUFACTURING") && pieces[0].equals("pilot-manufacturing")
                || action.equals("CANCEL_MANUFACTURING") && pieces[0].equals("pilot-manufacturing-cancel"));
        boolean construction = workspace.tab() == Tab.INDUSTRY && pieces.length == 3
                && (action.equals("START_FACILITY_CONSTRUCTION") && pieces[0].equals("pilot-construction")
                || action.equals("CANCEL_FACILITY_CONSTRUCTION") && pieces[0].equals("pilot-construction-cancel")
                || action.equals("ALLOCATE_FACILITY_RESOURCES") && pieces[0].equals("pilot-facility-resources")
                || action.equals("ALLOCATE_YARD_RESOURCES") && pieces[0].equals("pilot-yard-resources")
                || action.equals("START_YARD_CONSTRUCTION") && pieces[0].equals("pilot-yard-construction")
                || action.equals("CANCEL_YARD_CONSTRUCTION") && pieces[0].equals("pilot-yard-construction-cancel"));
        boolean journal = workspace.tab() == Tab.HISTORY && pieces.length == 2
                && action.equals("ACKNOWLEDGE_JOURNAL") && pieces[0].equals("pilot-journal");
        boolean repair = workspace.tab() == Tab.INDUSTRY && pieces.length == 3
                && (action.equals("START_REPAIR") && pieces[0].equals("pilot-repair")
                || action.equals("CANCEL_REPAIR") && pieces[0].equals("pilot-repair-cancel"));
        boolean refit = workspace.tab() == Tab.INDUSTRY && pieces.length == 3
                && (action.equals("START_REFIT") && pieces[0].equals("pilot-refit")
                || action.equals("START_REFIT_USED") && pieces[0].equals("stored-module")
                || action.equals("CANCEL_REFIT") && pieces[0].equals("pilot-refit-cancel"));
        boolean moduleTransfer = workspace.tab() == Tab.INDUSTRY && pieces.length == 3
                && (action.equals("LOAD_MODULE") && pieces[0].equals("stored-module")
                || action.equals("UNLOAD_MODULE") && pieces[0].equals("carried-module")
                || action.equals("CANCEL_MODULE_TRANSFER") && pieces[0].equals("module-transfer-cancel"));
        boolean productTransfer = workspace.tab() == Tab.INDUSTRY && pieces.length == 3
                && (action.equals("LOAD_PRODUCT") && pieces[0].equals("pilot-product-load")
                || action.equals("UNLOAD_PRODUCT") && pieces[0].equals("pilot-product-unload")
                || action.equals("CANCEL_PRODUCT_TRANSFER") && pieces[0].equals("product-transfer-cancel"));
        boolean asset = workspace.tab() == Tab.SHIPS && (action.equals("PURCHASE") && pieces[0].equals("pilot-reserve")
                || action.equals("SWITCH") && pieces[0].equals("personal-ship"));
        asset = asset || workspace.tab() == Tab.INDUSTRY && action.equals("PURCHASE_STATION")
                && pieces[0].equals("pilot-station-purchase") && pieces.length == 2;
        boolean market = workspace.tab() == Tab.LOGISTICS && (pieces[0].equals("pilot-station") || pieces[0].equals("pilot-market"));
        if (pieces.length < 2 || !(jump || market || asset || finance || supply || report || mining || manufacturing || construction || journal || repair || refit || moduleTransfer || productTransfer)) return false;
        campaign.coordinator().setPaused(true);
        pendingPilotSelection = id;
        pendingPilotPhysical = campaign.previewPilotAction(action, pieces[1], pieces.length > 2 ? pieces[2] : "", manufacturing ? 1 : pilotKilograms);
        if (action.equals("PURCHASE_STATION")) {
            status = pendingPilotPhysical.allowed() ? String.format(java.util.Locale.ROOT,
                    "Покупка существующей станции проверена: %.3f кр. продавцу. Склад, установки и верфь сохранятся. Подтвердите.",
                    -pendingPilotPhysical.walletChangeMilliCredits() / 1000d)
                    : "Покупка недоступна: проверьте предложение, деньги и физическую стыковку.";
            refreshProjection(); return true;
        }
        if (productTransfer) {
            status = pendingPilotPhysical.allowed() ? action.equals("CANCEL_PRODUCT_TRANSFER")
                    ? "Подтвердите отмену: товар останется у источника; выполненная обработка не возвращается."
                    : "Подтвердите обработку " + pilotKilograms + " шт. товара. Перенос завершится после работы на общих тактах."
                    : "Перенос недоступен: проверьте стыковку, владение, доступный товар, обработку и место.";
            refreshProjection(); return true;
        }
        if (moduleTransfer) {
            status = pendingPilotPhysical.allowed() ? "Подтвердите обработку оборудования: дождитесь погрузки или выгрузки; состояние модуля сохранится."
                    : "Операция недоступна: проверьте стыковку, владение, резерв, обработку и свободное место.";
            refreshProjection(); return true;
        }
        if (refit) {
            status = pendingPilotPhysical.allowed() ? action.startsWith("START_REFIT")
                    ? "Подтвердите резерв оборудования и места. Компоновка изменится после работы верфи; груз сохранится."
                    : "Подтвердите отмену: входящее оборудование вернётся на склад; выполненная работа не возвращается."
                    : "Переоснащение недоступно: проверьте владение, стыковку, верфь, оборудование, свободное место и вместимость целевого трюма.";
            refreshProjection(); return true;
        }
        if (repair) {
            status = pendingPilotPhysical.allowed() ? action.equals("START_REPAIR")
                    ? "Подтвердите резерв материалов для ремонта. На чужой верфи также резервируется показанная стоимость услуги; владелец получит её после завершения. На паузе повреждения сохраняются."
                    : "Подтвердите отмену: материалы вернутся на склад, выполненная работа не возвращается."
                    : "Ремонт недоступен: проверьте повреждения, свой корабль, стыковку, совместимую верфь, материалы и деньги для чужой услуги.";
            refreshProjection(); return true;
        }
        if (journal) {
            status = pendingPilotPhysical.allowed() ? "Отметка прочтения проверена. Подтвердите: история и игровые ресурсы сохранятся."
                    : "Отметка прочтения недоступна: список событий изменился.";
            refreshProjection(); return true;
        }
        if (construction) {
            boolean allocation = action.equals("ALLOCATE_FACILITY_RESOURCES") || action.equals("ALLOCATE_YARD_RESOURCES");
            boolean starting = action.startsWith("START_");
            status = pendingPilotPhysical.allowed() ? allocation
                    ? "Подтвердите перераспределение существующих ресурсов станции. Мощность прежних установок уменьшится."
                    : action.equals("START_YARD_CONSTRUCTION") ? "Подтвердите заказ: доступные материалы будут доставляться со склада на стройплощадку. Работа начнётся после полной поставки; готовая структура будет отключена."
                    : starting ? "Подтвердите резерв полного состава материалов. Для выполнения продолжите время; новая структура будет отключена."
                    : "Подтвердите отмену до начала работы: зарезервированные материалы вернутся на склад."
                    : "Строительство недоступно: проверьте владение, физическую стыковку, полный запас материалов, вместимость и действующую линию.";
            refreshProjection(); return true;
        }
        if (manufacturing) {
            status = pendingPilotPhysical.allowed() ? action.equals("START_MANUFACTURING")
                    ? "Подтвердите резерв материалов для одной секции. Для выполнения продолжите время."
                    : "Подтвердите отмену: резерв вернётся на склад, выполненная работа будет потеряна."
                    : "Производство недоступно: проверьте владение станцией, стыковку, материалы, мощности и место на складе.";
            refreshProjection(); return true;
        }
        if (mining) {
            status = pendingPilotPhysical.allowed()
                    ? action.equals("START_MINING") ? "Контакт и оборудование проверены. Подтвердите задание, затем продолжите время для добычи."
                    : "Подтвердите остановку. Уже добытый груз останется в трюме."
                    : "Добыча недоступна: проверьте оборудование, контакт, скорость и другие задания корабля.";
            refreshProjection(); return true;
        }
        if (report) {
            status = pendingPilotPhysical.allowed() ? String.format(java.util.Locale.ROOT,
                    "Личное открытие проверено. Подтвердите передачу; контракт проверит результат и выплату %.2f cr из эскроу.",
                    pendingPilotPhysical.walletChangeMilliCredits() / 1000d)
                    : "Отчёт недоступен: проверьте личное открытие, срок контракта и доступность получателя в системе вашего корабля.";
            refreshProjection(); return true;
        }
        status = pendingPilotPhysical.allowed() ? ((action.equals("BUY") || action.equals("SELL"))
                ? String.format(java.util.Locale.ROOT, "Проверено: %d кг; кошелёк %+.3f кр. Подтвердите сделку.", pilotKilograms, pendingPilotPhysical.walletChangeMilliCredits() / 1000d)
                : supply ? "Проверено снабжение из собственного груза. Подтверждение уменьшит трюм и заполнит установленный интерфейс без изменения денег."
                : finance ? String.format(java.util.Locale.ROOT, "Перевод проверен: личный кошелёк %+.3f кр. Подтвердите.", pendingPilotPhysical.walletChangeMilliCredits() / 1000d) : asset ? "Владение и условия проверены. Подтвердите действие." : jump ? "Вылет проверен: существующее топливо, движение к выходу и перелёт на общих тактах. Подтвердите вылет." : "Стыковка проверена. Подтвердите действие.")
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
        var mission = campaign.coordinator().npcMissions().missions().stream().filter(m -> m.missionId().equals(id)).findFirst().orElseThrow();
        int supplyPortion = command == com.spacesim.world.Stage21HNpcMissionService.PlayerCommand.ACCEPT
                && mission.objective().kind() == com.spacesim.world.Stage21HNpcMissionState.ObjectiveKind.PLAYER_SUPPLY_DELIVERY_KG_AT_LEAST
                ? Math.toIntExact(Math.min(pilotKilograms, mission.objective().threshold())) : 0;
        pendingMissionPreview = campaign.previewMissionCommand(command, id, supplyPortion);
        status = pendingMissionPreview.allowed()
                ? switch (command) {
                    case ACCEPT -> supplyPortion > 0 ? "На паузе. Принять поставку " + supplyPortion
                            + " кг? Премия " + String.format(java.util.Locale.ROOT, "%.3f кр.", pendingMissionPreview.proposedRewardMilliCredits() / 1000d)
                            + "; остаток эскроу вернётся заказчику."
                            : "На паузе. Принять контракт? Награда выплачивается только после проверки результата и участия.";
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
            var updated = campaign.submitMissionCommand(preview);
            workspace.select(new UiSelection(SelectionKind.SURFACE_ROW, "player-mission:" + updated.missionId()));
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

    private boolean focusPlayer() {
        var player = campaign.playerState().orElse(null);
        if (player == null || player.activeFleetId() == null) {
            status = "Нет активного личного корабля. Выберите его во вкладке кораблей.";
            return true;
        }
        return focusFleet(player.activeFleetId().value());
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
            var nextModel = new GeneratedWorldUiModel(coordinator.rootSeed(), coordinator.runtime(), coordinator.content(),
                    com.spacesim.ui.GeneratedWorldUiModel.CarrierUiSource.none(), () -> candidate.playerState().orElse(null));
            var nextSnapshot = nextModel.capture();
            var nextProjector = new ProductionUiProjector(coordinator.actors().capture().stream()
                    .map(actor -> actor.factionContentId()).sorted().findFirst().orElseThrow());
            var nextProjection = nextProjector.capture(candidate, nextSnapshot, true);
            pendingMissionPreview = null;
            pendingPilotStart = null;
            pendingPilotPhysical = null;
            pendingFactionFoundation = null;
            pendingGovernment = null;
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
        var selectedMission = missionId == null ? null : campaign.coordinator().npcMissions().missions().stream()
                .filter(m -> m.missionId().equals(missionId)).findFirst().orElse(null);
        boolean supplyOffer = selectedMission != null && selectedMission.status() == com.spacesim.world.Stage21HNpcMissionState.MissionStatus.OFFERED
                && selectedMission.objective().kind() == com.spacesim.world.Stage21HNpcMissionState.ObjectiveKind.PLAYER_SUPPLY_DELIVERY_KG_AT_LEAST;
        renderer.bindSupplyMissionQuantity(supplyOffer, supplyOffer ? Math.toIntExact(Math.min(pilotKilograms, selectedMission.objective().threshold())) : 0);
        if (!selectedPilotStart()) pendingPilotStart = null;
        renderer.bindPilotStartActions(selectedPilotStart(), pendingPilotStart != null && pendingPilotStart.allowed());
        String selectedPilot = workspace.view().selection().stableId();
        if (workspace.tab() != Tab.GALAXY || !selectedPilot.equals(pendingRouteSelection)
                || !campaign.coordinator().isPaused()) pendingRoute = null;
        renderer.bindPersonalRoute(pendingRoute, campaign.coordinator().runtime().world().getTopology(),
                campaign.coordinator().session().fixedStepSeconds());
        if (!selectedPilot.equals(pendingPilotSelection)) pendingPilotPhysical = null;
        boolean selectedJump = workspace.tab() == Tab.SHIPS && selectedPilot.startsWith("pilot-jump|");
        String assetAction = workspace.tab() == Tab.SHIPS ? (selectedPilot.startsWith("pilot-reserve|") ? "PURCHASE"
                : selectedPilot.startsWith("personal-ship:") ? "SWITCH" : "") : "";
        boolean foundation = workspace.tab() == Tab.SETTINGS && selectedPilot.equals("pilot-faction-foundation");
        boolean finance = workspace.tab() == Tab.SETTINGS && selectedPilot.equals("pilot-faction-finance");
        if (!foundation) pendingFactionFoundation = null;
        if (workspace.tab() == Tab.INDUSTRY && selectedPilot.startsWith("pilot-station-purchase|")) assetAction = "PURCHASE_STATION";
        renderer.bindPilotAssetAction(foundation ? "FOUNDATION" : finance ? "FINANCE" : assetAction);
        renderer.bindPilotJumpAction(selectedJump);
        boolean selectedSupply = workspace.tab() == Tab.SHIPS && selectedPilot.startsWith("pilot-supply|");
        renderer.bindPilotSupplyAction(selectedSupply);
        boolean selectedReport = workspace.tab() == Tab.CONTACTS && selectedPilot.startsWith("pilot-report|");
        renderer.bindPilotReportAction(selectedReport);
        String miningAction = workspace.tab() == Tab.INDUSTRY ? selectedPilot.startsWith("pilot-mining|") ? "START_MINING"
                : selectedPilot.startsWith("pilot-mining-stop|") ? "STOP_MINING" : "" : "";
        renderer.bindPilotMiningAction(miningAction);
        String manufacturingAction = workspace.tab() == Tab.INDUSTRY ? selectedPilot.startsWith("pilot-manufacturing|") ? "START_MANUFACTURING"
                : selectedPilot.startsWith("pilot-manufacturing-cancel|") ? "CANCEL_MANUFACTURING"
                : selectedPilot.startsWith("pilot-construction|") ? "START_FACILITY_CONSTRUCTION"
                : selectedPilot.startsWith("pilot-construction-cancel|") ? "CANCEL_FACILITY_CONSTRUCTION"
                : selectedPilot.startsWith("pilot-yard-construction|") ? "START_YARD_CONSTRUCTION"
                : selectedPilot.startsWith("pilot-yard-construction-cancel|") ? "CANCEL_YARD_CONSTRUCTION"
                : selectedPilot.startsWith("pilot-yard-resources|") ? "ALLOCATE_YARD_RESOURCES"
                : selectedPilot.startsWith("pilot-facility-resources|") ? "ALLOCATE_FACILITY_RESOURCES" : "" : "";
        renderer.bindPilotManufacturingAction(manufacturingAction);
        String repairAction = workspace.tab() == Tab.INDUSTRY ? selectedPilot.startsWith("pilot-repair|") ? "START_REPAIR"
                : selectedPilot.startsWith("pilot-repair-cancel|") ? "CANCEL_REPAIR" : "" : "";
        renderer.bindPilotRepairAction(repairAction);
        String refitAction = workspace.tab() == Tab.INDUSTRY ? selectedPilot.startsWith("pilot-refit|") ? "START_REFIT"
                : selectedPilot.startsWith("pilot-refit-cancel|") ? "CANCEL_REFIT"
                : selectedPilot.startsWith("stored-module|") ? "START_REFIT_USED" : "" : "";
        renderer.bindPilotRefitAction(refitAction);
        String moduleTransferAction = workspace.tab() == Tab.INDUSTRY ? selectedPilot.startsWith("stored-module|") ? "LOAD_MODULE"
                : selectedPilot.startsWith("carried-module|") ? "UNLOAD_MODULE"
                : selectedPilot.startsWith("module-transfer-cancel|") ? "CANCEL_MODULE_TRANSFER" : "" : "";
        renderer.bindModuleTransferAction(moduleTransferAction);
        String productTransferAction = workspace.tab() == Tab.INDUSTRY ? selectedPilot.startsWith("pilot-product-load|") ? "LOAD_PRODUCT"
                : selectedPilot.startsWith("pilot-product-unload|") ? "UNLOAD_PRODUCT"
                : selectedPilot.startsWith("product-transfer-cancel|") ? "CANCEL_PRODUCT_TRANSFER" : "" : "";
        renderer.bindProductTransferAction(productTransferAction);
        boolean selectedJournal = workspace.tab() == Tab.HISTORY && selectedPilot.startsWith("pilot-journal|");
        renderer.bindPilotJournalAction(selectedJournal);
        renderer.bindPersonalNotificationCount(campaign.playerJournal().unreadCount());
        renderer.bindPhysicalPilotActions(selectedJournal || foundation || finance || selectedJump || selectedSupply || selectedReport || !productTransferAction.isEmpty() || !moduleTransferAction.isEmpty() || !repairAction.isEmpty() || !refitAction.isEmpty() || !manufacturingAction.isEmpty() || !miningAction.isEmpty() || !assetAction.isEmpty() || workspace.tab() == Tab.LOGISTICS && (selectedPilot.startsWith("pilot-market|") || selectedPilot.startsWith("pilot-station|")),
                selectedPilot.startsWith("pilot-market|"), foundation ? pendingFactionFoundation != null && pendingFactionFoundation.allowed()
                        : pendingPilotPhysical != null && pendingPilotPhysical.allowed(), pilotKilograms);
        if (!selectedPilot.equals(pendingGovernmentSelection)) pendingGovernment = null;
        renderer.bindFactionActions(workspace.tab() == Tab.FACTIONS ? com.spacesim.ui.GeneratedCampaignFactionUi.actions(selectedPilot) : workspace.tab() == Tab.MILITARY ? com.spacesim.ui.GeneratedCampaignFleetUi.actions(selectedPilot) : List.of(),
                pendingGovernment != null && pendingGovernment.allowed());
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
