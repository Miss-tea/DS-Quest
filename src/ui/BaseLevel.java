
package ui;

import core.AssetLoader;
import core.GameState;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

public abstract class BaseLevel {

    protected final GameState gameState = new GameState();
    protected final BorderPane root = new BorderPane();
    protected final DialoguePane dialogue = new DialoguePane();
    protected final HUD hud = new HUD(gameState);


    public Scene buildScene(double w, double h) {
        // === BACKGROUND ===
        ImageView bg = AssetLoader.imageView(AssetLoader.BG, w, h, false);
        bg.setPreserveRatio(false);

        StackPane canvas = new StackPane(bg);
        canvas.setAlignment(Pos.TOP_LEFT);

        // === WORLD LAYER ===
        StackPane worldLayer = new StackPane();
        worldLayer.setPadding(new Insets(20));

        // === OVERLAY ===
        StackPane overlay = new StackPane();
        overlay.setPickOnBounds(false);

        StackPane.setAlignment(hud, Pos.TOP_LEFT);
        StackPane.setMargin(hud, new Insets(10, 20, 0, 20));

        dialogue.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(dialogue, Pos.BOTTOM_CENTER);
        StackPane.setMargin(dialogue, new Insets(0, 0, -30, 300));

        overlay.getChildren().addAll(hud, dialogue);

        // === COMBINE ===
        StackPane all = new StackPane(canvas, worldLayer, overlay);
        all.setPickOnBounds(false);

        root.setCenter(all);
        root.setBackground(new Background(new BackgroundFill(Color.BLACK, null, null))); // avoid white gaps

        dialogue.hide();

        // Build the scene
        Scene scene = new Scene(root, w, h, Color.BLACK);

        // 🔗 RESPONSIVE BINDINGS: Make background fill the scene
        bg.fitWidthProperty().bind(scene.widthProperty());
        bg.fitHeightProperty().bind(scene.heightProperty());

        // Optional: ensure container panes expand with scene
        canvas.minWidthProperty().bind(scene.widthProperty());
        canvas.minHeightProperty().bind(scene.heightProperty());
        worldLayer.minWidthProperty().bind(scene.widthProperty());
        worldLayer.minHeightProperty().bind(scene.heightProperty());
        overlay.minWidthProperty().bind(scene.widthProperty());
        overlay.minHeightProperty().bind(scene.heightProperty());

        // Initialize your level content AFTER scene is created so bindings apply
        initLevel(worldLayer, w, h);

        return scene;
    }

    /**public Scene buildScene(double w, double h) {

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
        overlay.setPickOnBounds(false); // don't block clicks on world

        // ---- HUD positioning ----
        StackPane.setAlignment(hud, Pos.TOP_LEFT);
        StackPane.setMargin(hud, new Insets(10, 20, 0, 20));

        // ---- DIALOGUE positioning ----
        dialogue.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE); // prevent stretching
        StackPane.setAlignment(dialogue, Pos.BOTTOM_CENTER);
        StackPane.setMargin(dialogue, new Insets(0, 0, -30, 300)); // adjust lower/higher here

        overlay.getChildren().addAll(hud, dialogue);

        // === COMBINE ALL LAYERS ===
        StackPane all = new StackPane(canvas, worldLayer, overlay);
        all.setPickOnBounds(false);

        root.setCenter(all);

        // Wizard hidden until speaking
        dialogue.hide();

        initLevel(worldLayer, w, h);

        return new Scene(root, w, h, Color.BLACK);
    }**/

    protected abstract void initLevel(StackPane worldLayer, double w, double h);

    protected void say(String msg, Runnable after, javafx.scene.Node... actions) {
        dialogue.show(msg, actions);
        if (after != null) after.run();
    }

    protected void silence() {
        dialogue.hide();
    }

    public GameState getGameState() { return gameState; }
}
