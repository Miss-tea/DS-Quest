package ui;

import core.AssetLoader;
import core.GameState;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

public abstract class BaseLevel {

    protected final GameState gameState = new GameState();
    protected final BorderPane root = new BorderPane();
    protected final DialoguePane dialogue = new DialoguePane();
    protected final HUD hud = new HUD(gameState);

    // Layers: everything that blurs together, and the top status layer
    private StackPane allRef;          // background + world + overlay (blur target)
    private StackPane statusLayerRef;  // topmost layer (board lives here)

    private LevelStatusBoard statusBoard;

    public Scene buildScene(double w, double h) {
        // === BACKGROUND ===
        ImageView bg = AssetLoader.imageView(AssetLoader.BG, w, h, false);
        bg.setPreserveRatio(false);

        StackPane canvas = new StackPane(bg);
        canvas.setAlignment(Pos.TOP_LEFT);

        // === WORLD LAYER ===
        StackPane worldLayer = new StackPane();
        worldLayer.setPadding(new Insets(20));

        // === OVERLAY (HUD + Dialogue) ===
        StackPane overlay = new StackPane();
        overlay.setPickOnBounds(false);

        StackPane.setAlignment(hud, Pos.TOP_LEFT);
        StackPane.setMargin(hud, new Insets(10, 20, 0, 20));

        dialogue.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(dialogue, Pos.BOTTOM_CENTER);
        StackPane.setMargin(dialogue, new Insets(0, 0, -30, 300));

        overlay.getChildren().addAll(hud, dialogue);

        // === COMBINE: background + world + overlay (will blur)
        allRef = new StackPane(canvas, worldLayer, overlay);
        allRef.setPickOnBounds(false);

        // === TOP STATUS LAYER (board sits here, not blurred) ===
        statusLayerRef = new StackPane();
        // IMPORTANT: let clicks pass to the world when board is hidden
        statusLayerRef.setPickOnBounds(false);

        StackPane rootStack = new StackPane(allRef, statusLayerRef);
        root.setCenter(rootStack);
        root.setBackground(new Background(new BackgroundFill(Color.BLACK, null, null)));

        dialogue.hide();

        Scene scene = new Scene(root, w, h, Color.BLACK);

        // Responsive bindings
        bg.fitWidthProperty().bind(scene.widthProperty());
        bg.fitHeightProperty().bind(scene.heightProperty());
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

        // Reuse your styled DONE button as a template for consistent look
        Button doneTemplate = UiUtil.btn("DONE");

        statusBoard = new LevelStatusBoard(allRef, doneTemplate);
        statusLayerRef.getChildren().add(statusBoard);
        StackPane.setAlignment(statusBoard, Pos.CENTER);

        // While the board is hidden, this layer should not intercept mouse input
        statusLayerRef.setMouseTransparent(true);
        statusBoard.visibleProperty().addListener((obs, wasVisible, isVisible) -> {
            statusLayerRef.setMouseTransparent(!isVisible);
        });
    }

    protected void showGameOverImmediate() {
        if (statusBoard == null) initStatusBoard();
        dialogue.hide();
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

    private void installAutoGameOver() {
        gameState.heartsProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && newV.intValue() <= 0) {
                showGameOverImmediate();
            }
        });
    }
}
