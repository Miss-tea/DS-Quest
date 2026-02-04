package ui;

import core.AssetLoader;
import core.GameState;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * BaseLevel builds the base scene: background + world + overlay (HUD/Dialog),
 * and a top status layer for boards. Exposes helpers for overlays, blur target,
 * status board, and navigation.
 */
public abstract class BaseLevel {

    protected final GameState gameState = new GameState();
    protected final BorderPane root = new BorderPane();
    protected final DialoguePane dialogue = new DialoguePane();
    protected final HUD hud = new HUD(gameState);

    // Everything that blurs together (and that we snapshot as the "full frame")
    private StackPane allRef;          // background + world + overlay (HUD/Dialog)
    // Topmost layer for LevelStatusBoard, LearningsBoard, etc. (not blurred)
    private StackPane statusLayerRef;

    private LevelStatusBoard statusBoard;

    // Add near other fields
    protected ImageView backgroundView;

    /** Allows each level to provide its own background. Default: Level-1 background. */
    protected String getBackgroundPath() {
        return AssetLoader.BG;
    }

    // -------------------- Quit UI fields --------------------
    private HBox quitWrap;                    // bottom-left Quit button container
    private Button quitBtn;                   // "QUIT" button
    private StackPane quitConfirmOverlay;     // YES/NO confirmation overlay

    public Scene buildScene(double w, double h) {
        // === BACKGROUND ===
        backgroundView = AssetLoader.imageView(getBackgroundPath(), w, h, false);
        // Fill the entire scene. If you prefer keeping aspect ratio + cropping, set preserveRatio(true) and adjust.
        backgroundView.setPreserveRatio(false);

        StackPane canvas = new StackPane(backgroundView);
        canvas.setAlignment(Pos.TOP_LEFT);

        // === WORLD LAYER ===
        StackPane worldLayer = new StackPane();
        worldLayer.setPadding(new Insets(20));

        // === OVERLAY (HUD + Dialogue) ===
        StackPane overlay = new StackPane();
        overlay.setPickOnBounds(false); // let clicks pass through anywhere there is no actual node

        StackPane.setAlignment(hud, Pos.TOP_LEFT);
        StackPane.setMargin(hud, new Insets(10, 20, 0, 20));

        dialogue.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(dialogue, Pos.BOTTOM_CENTER);
        StackPane.setMargin(dialogue, new Insets(0, 0, -30, 300));

        overlay.getChildren().addAll(hud, dialogue);

        // === QUIT BUTTON (bottom-left) ===
        installQuitButton(overlay);

        // === COMBINE: background + world + overlay (full-frame composite) ===
        allRef = new StackPane(canvas, worldLayer, overlay);
        allRef.setPickOnBounds(false);

        // === TOP STATUS LAYER (boards go here, not blurred) ===
        statusLayerRef = new StackPane();
        statusLayerRef.setPickOnBounds(false);

        StackPane rootStack = new StackPane(allRef, statusLayerRef);
        root.setCenter(rootStack);
        root.setBackground(new Background(new BackgroundFill(Color.BLACK, null, null)));

        dialogue.hide();

        Scene scene = new Scene(root, w, h, Color.BLACK);

        // Responsive bindings
        backgroundView.fitWidthProperty().bind(scene.widthProperty());
        backgroundView.fitHeightProperty().bind(scene.heightProperty());
        canvas.minWidthProperty().bind(scene.widthProperty());
        canvas.minHeightProperty().bind(scene.heightProperty());
        worldLayer.minWidthProperty().bind(scene.widthProperty());
        worldLayer.minHeightProperty().bind(scene.heightProperty());
        overlay.minWidthProperty().bind(scene.widthProperty());
        overlay.minHeightProperty().bind(scene.heightProperty());
        statusLayerRef.minWidthProperty().bind(scene.widthProperty());
        statusLayerRef.minHeightProperty().bind(scene.heightProperty());

        // Build level content after scene exists (so bindings apply)
        initLevel(worldLayer, w, h);
        hud.setTitle(getLevelTitle());

        // Init board + auto game over
        initStatusBoard();
        installAutoGameOver();

        return scene;
    }

    protected abstract void initLevel(StackPane worldLayer, double w, double h);

    protected void say(String msg, Runnable after, javafx.scene.Node... actions) {
        dialogue.show(msg, actions);
        if (after != null) after.run();
    }

    protected void silence() {
        dialogue.hide();
    }

    public GameState getGameState() { return gameState; }

    // -------------------- Level status helpers --------------------

    private void initStatusBoard() {
        if (statusBoard != null) return;

        // Reuse your styled DONE button for consistent look
        Button doneTemplate = UiUtil.btn("DONE");

        statusBoard = new LevelStatusBoard(allRef, doneTemplate);
        statusLayerRef.getChildren().add(statusBoard);
        StackPane.setAlignment(statusBoard, Pos.CENTER);

        // Keep parent interactive so other overlays (like LearningsBoard) can capture input
        // With pickOnBounds(false), clicks pass through if there are no visible children
        statusLayerRef.setMouseTransparent(false);
    }

    protected void showGameOverImmediate() {
        if (statusBoard == null) initStatusBoard();
        dialogue.hide();

        // Wire RETRY -> restart the same level with a fresh instance
        statusBoard.setOnRetry(this::restartThisLevel);

        statusBoard.showGameOver(0, "Be careful with your hearts.");
    }

    // Default (no max score): assumes 100%
    protected void showSurvived(int finalScore, String strategyMsg) {
        if (statusBoard == null) initStatusBoard();
        dialogue.hide();
        statusBoard.showSurvived(finalScore, strategyMsg);
    }

    // Overload with maxScore → enables star thresholds
    protected void showSurvived(int finalScore, String strategyMsg, int maxScore) {
        if (statusBoard == null) initStatusBoard();
        dialogue.hide();
        statusBoard.showSurvived(finalScore, strategyMsg, maxScore);
    }

    // Show survived and then run a callback when DONE is clicked
    protected void showSurvivedThen(int finalScore, String strategyMsg, int maxScore, Runnable onDone) {
        if (statusBoard == null) initStatusBoard();
        dialogue.hide();
        statusBoard.setOnDone(onDone);
        statusBoard.showSurvived(finalScore, strategyMsg, maxScore);
    }

    private void installAutoGameOver() {
        gameState.heartsProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && newV.intValue() <= 0) {
                showGameOverImmediate();
            }
        });
    }

    // -------------------- Overlay & navigation helpers for subclasses --------------------

    /** Add an overlay (e.g., LearningsBoard) to the top status layer. */
    protected void addToStatusLayer(javafx.scene.Node n) {
        if (n != null && statusLayerRef != null) {
            statusLayerRef.getChildren().add(n);
            StackPane.setAlignment(n, Pos.CENTER);
        }
    }

    /** Provide access to the full-frame blur/snapshot target (background + world + HUD/Dialog). */
    protected javafx.scene.Node getBlurTarget() {
        return allRef;
    }

    /** Expose HUD to levels (e.g., to pulse hearts or set title contextually). */
    protected HUD getHud() {
        return hud;
    }

    /** Expose status layer if a level needs to add/remove transient overlays directly. */
    protected StackPane getStatusLayer() {
        return statusLayerRef;
    }

    /**
     * Navigate to Level Selection screen.
     * Your LevelSelectScreen requires a Stage in its constructor and is used directly in a new Scene.
     */
    protected void goToLevelSelect() {
        Scene current = root.getScene();
        if (current == null || current.getWindow() == null) return;

        Stage stage = (Stage) current.getWindow();
        double w = current.getWidth()  > 0 ? current.getWidth()  : 1200;
        double h = current.getHeight() > 0 ? current.getHeight() : 720;

        // Construct LevelSelectScreen with the Stage (as your class requires)
        LevelSelectScreen selector = new LevelSelectScreen(stage);
        Scene next = new Scene(selector, w, h);
        stage.setScene(next);
    }

    /** Navigate back to LevelSelect, and notify progression (unlocking/moving marker). */
    protected void goToLevelSelectAfterCompletion(int completedLevelIndex) {
        Scene current = root.getScene();
        if (current == null || current.getWindow() == null) return;

        Stage stage = (Stage) current.getWindow();
        double w = current.getWidth()  > 0 ? current.getWidth()  : 1200;
        double h = current.getHeight() > 0 ? current.getHeight() : 720;

        LevelSelectScreen selector = new LevelSelectScreen(stage);
        selector.onLevelCompleted(completedLevelIndex);  // unlock next + move girl

        Scene next = new Scene(selector, w, h);
        stage.setScene(next);
        javafx.application.Platform.runLater(() -> selector.onLevelCompleted(completedLevelIndex));
    }

    /** Override in each Level class to provide the title string. */
    protected String getLevelTitle() {
        return "";
    }

    // ===================== QUIT UI =====================

    /** Install a bottom-left "QUIT" button that opens a YES/NO confirmation. */
    private void installQuitButton(StackPane overlay) {
        if (overlay == null || quitWrap != null) return;

        quitBtn = UiUtil.btn("❌",'e');
        quitBtn.setMouseTransparent(false);   // button itself accepts clicks
        quitBtn.setFocusTraversable(false);   // don't steal keyboard focus
        quitBtn.setOnAction(e -> showQuitConfirm());

        quitWrap = new HBox(quitBtn);
        quitWrap.setAlignment(Pos.BOTTOM_LEFT);

        // CRITICAL: Only the actual button area should be clickable, not any empty space
        quitWrap.setPickOnBounds(false);

        overlay.getChildren().add(quitWrap);
        StackPane.setAlignment(quitWrap, Pos.BOTTOM_LEFT);
        StackPane.setMargin(quitWrap, new Insets(0, 0, 16, 16)); // bottom-left spacing
    }

    /** Show a small confirmation card on the status layer: "Quit Game? [YES] [NO]". */
    private void showQuitConfirm() {
        if (quitConfirmOverlay != null) return;

        // Question + buttons
        Label question = new Label("Quit Game?");
        question.setStyle("-fx-text-fill: #f7f1e3; -fx-font-size: 18px; -fx-font-weight: bold;");

        Button yes = UiUtil.btn("YES");
        Button no  = UiUtil.btn("NO");

        yes.setOnAction(e -> {
            hideQuitConfirm();
            goToLevelSelect();
        });
        no.setOnAction(e -> hideQuitConfirm());

        HBox buttons = new HBox(10, yes, no);
        buttons.setAlignment(Pos.CENTER);

        VBox card = new VBox(10, question, buttons);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(14, 16, 14, 16));
        card.setStyle("""
            -fx-background-color: rgba(20,20,20,0.78);
            -fx-background-radius: 10;
            -fx-border-color: rgba(255,255,255,0.28);
            -fx-border-width: 1;
            -fx-border-radius: 10;
        """);

        // Container overlay that sits centered in the status layer — this is intentionally modal
        quitConfirmOverlay = new StackPane(card);
        quitConfirmOverlay.setPickOnBounds(true);   // click outside can be used to dismiss if you want
        quitConfirmOverlay.setMouseTransparent(false);
        StackPane.setAlignment(card, Pos.CENTER);

        addToStatusLayer(quitConfirmOverlay);
        StackPane.setAlignment(quitConfirmOverlay, Pos.CENTER);
    }

    /** Remove the YES/NO quit confirmation if visible. */
    private void hideQuitConfirm() {
        if (quitConfirmOverlay == null) return;
        if (statusLayerRef != null) {
            statusLayerRef.getChildren().remove(quitConfirmOverlay);
        }
        quitConfirmOverlay = null;
    }

    // ===================== RETRY: hard restart (fresh hearts & score) =====================

    /** Create a fresh instance of the SAME level class and replace the Scene. */
    protected void restartThisLevel() {
        Scene current = root.getScene();
        if (current == null || current.getWindow() == null) return;

        try {
            Stage stage = (Stage) current.getWindow();
            double w = current.getWidth()  > 0 ? current.getWidth()  : 1200;
            double h = current.getHeight() > 0 ? current.getHeight() : 720;

            // Fresh instance of the same level (default constructor required)
            BaseLevel fresh = this.getClass().getDeclaredConstructor().newInstance();

            // New Scene -> new HUD + new GameState (fresh hearts & score)
            Scene scene = fresh.buildScene(w, h);
            stage.setScene(scene);
        } catch (Throwable t) {
            t.printStackTrace();
            // Fallback: if anything fails, return to Level Select
            goToLevelSelect();
        }
    }
}