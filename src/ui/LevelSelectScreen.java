
package ui;

import javafx.animation.PathTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

public class LevelSelectScreen extends Pane {

    // ----- CONFIG -----
    private static final int WIDTH = 1200;
    private static final int HEIGHT = 720;

    private static final int LEVEL_COUNT = 7;
    private static final String[] LEVEL_NAMES = new String[]{
            "Array", "Linked List", "Stack", "Queue",
            "Traversal", "Graph", "Tree"
    };

    // Resource paths in classpath (adjust RES_BASE if you put assets in a subfolder)
    private static final String RES_BASE = "/";
    private static final String BG       = RES_BASE + "levelselectionbg.png";
    private static final String DOOR     = RES_BASE + "door.png";
    private static final String LOCK     = RES_BASE + "lock.png";
    private static final String GIRL     = RES_BASE + "girlrunning.png";
    private static final String BTN_HOME = RES_BASE + "home.png";
    private static final String BTN_RETRY= RES_BASE + "retry.png";

    // Fonts (fallback to default if not found)
    private final Font titleFont = loadFontOrDefault("/fonts/CinzelDecorative-Bold.ttf", 50);
    private final Font labelFont = loadFontOrDefault("/fonts/CinzelDecorative-Bold.ttf", 30);

    private Font loadFontOrDefault(String path, double size) {
        try {
            var url = getClass().getResource(path);
            if (url != null) {
                Font f = Font.loadFont(url.toExternalForm(), size);
                if (f != null) return f;
            }
        } catch (Exception ignored) {}
        return Font.font(size);
    }

    private Image loadImageOrFail(String path) {
        var is = getClass().getResourceAsStream(path);
        if (is == null) {
            throw new IllegalStateException("Missing resource on classpath: " + path);
        }
        return new Image(is);
    }

    // Persistence
    private final Preferences prefs = Preferences.userRoot().node("array-dungeon");
    private static final String PREF_UNLOCKED = "highestUnlocked"; // inclusive index (0-based)

    // Stage for navigation
    private final Stage stage;

    // UI data structures
    private final List<DoorView> doors = new ArrayList<>();
    private ImageView girlMarker;

    // State
    private int highestUnlocked;   // inclusive
    private int lastEnteredLevel = 0;

    public LevelSelectScreen(Stage stage) {
        this.stage = stage;
        setPrefSize(WIDTH, HEIGHT);

        // Load progress (default 0: only level 0 unlocked)
        highestUnlocked = Math.max(0, prefs.getInt(PREF_UNLOCKED, 0));

        // Build UI
        buildBackground();
        buildTitle();
        buildPathLines();
        buildDoors();
        buildGirlMarker();
        buildBottomButtons();
    }

    // ---------------- UI BUILDERS ----------------

    private void buildBackground() {
        ImageView bg = new ImageView(loadImageOrFail(BG));
        bg.setFitWidth(WIDTH);
        bg.setFitHeight(HEIGHT);
        getChildren().add(bg);
    }

    private void buildTitle() {
        Text t = new Text("SELECT");
        t.setFont(titleFont);
        t.setFill(Color.WHITE);
        t.setStroke(Color.BLACK);
        t.setStrokeWidth(0.75);
        t.setLayoutX(WIDTH / 2.0 - 110);
        t.setLayoutY(90);
        getChildren().add(t);
    }

    /**
     * Dotted route lines (top rail, bottom rail) + one straight vertical connector at x=1000.
     */
    private void buildPathLines() {
        // Your current rail Y positions and node columns:
        double[][] top = new double[][]{
                {165, 220}, {375, 220}, {600, 220}, {1000, 220}
        };
        double[][] bottom = new double[][]{
                {275, 470}, {525, 470}, {1000, 470}
        };

        // Horizontal top path
        for (int i = 0; i < top.length - 1; i++) {
            getChildren().add(dashedLine(top[i][0], top[i][1], top[i + 1][0], top[i + 1][1]));
        }
        // Horizontal bottom path
        for (int i = 0; i < bottom.length - 1; i++) {
            getChildren().add(dashedLine(bottom[i][0], bottom[i][1], bottom[i + 1][0], bottom[i + 1][1]));
        }

        // Straight vertical dotted connector at x=1000, between y=220 (top rail) and y=470 (bottom rail)
        getChildren().add(dashedLine(1000, 220, 1000, 470));
    }

    private Line dashedLine(double x1, double y1, double x2, double y2) {
        Line l = new Line(x1, y1, x2, y2);
        l.setStroke(Color.web("#FFFFFFCC"));
        l.setStrokeWidth(3);
        l.getStrokeDashArray().addAll(10.0, 12.0);
        l.setOpacity(0.9);
        return l;
    }

    private void buildDoors() {
        // Door anchor points
        double[][] pos = new double[][]{
                {120, 130},  // 0 Array
                {345, 130},  // 1 Linked List
                {570, 130},  // 2 Stack
                {795, 130},  // 3 Queue
                {195, 380},  // 4 Traversal
                {495, 380},  // 5 Graph
                {795, 380}   // 6 Tree
        };

        Image doorImg = loadImageOrFail(DOOR);
        Image lockImg = loadImageOrFail(LOCK);

        for (int i = 0; i < LEVEL_COUNT; i++) {
            final int idx = i; // IMPORTANT for lambdas
            DoorView d = new DoorView(idx, LEVEL_NAMES[idx], pos[idx][0], pos[idx][1], doorImg, lockImg);
            d.setLocked(idx > highestUnlocked); // lock all beyond highestUnlocked
            d.setOnDoorClicked(() -> handleDoorClick(idx));
            doors.add(d);
            getChildren().add(d.root);
        }
    }

    private void buildGirlMarker() {
        girlMarker = new ImageView(loadImageOrFail(GIRL));
        girlMarker.setFitWidth(100);
        girlMarker.setPreserveRatio(true);
        // Place near the current door (highestUnlocked) — slightly left of its center
        moveGirlToLevel(highestUnlocked, false);
        getChildren().add(girlMarker);
    }

    private void buildBottomButtons() {
        // Home (left)
        ImageView home = new ImageView(loadImageOrFail(BTN_HOME));
        home.setFitWidth(90);
        home.setPreserveRatio(true);
        home.setLayoutX(30);
        home.setLayoutY(HEIGHT - 90);
        addHoverScale(home);
        home.setOnMouseClicked(e -> goHome());
        getChildren().add(home);

        // Retry (right)
        ImageView retry = new ImageView(loadImageOrFail(BTN_RETRY));
        retry.setFitWidth(110);
        retry.setPreserveRatio(true);
        retry.setLayoutX(WIDTH - 130);
        retry.setLayoutY(HEIGHT - 90);
        addHoverScale(retry);
        retry.setOnMouseClicked(e -> retryLastEntered());
        getChildren().add(retry);
    }

    private void addHoverScale(Node n) {
        n.setOnMouseEntered(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(120), n);
            st.setToX(1.1);
            st.setToY(1.1);
            st.play();
        });
        n.setOnMouseExited(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(120), n);
            st.setToX(1.0);
            st.setToY(1.0);
            st.play();
        });
    }

    // ---------------- BEHAVIOR ----------------

    private void handleDoorClick(int levelIndex) {
        DoorView d = doors.get(levelIndex);
        if (d.locked) {
            // Little shake to show it's locked
            shake(d.root);
            return;
        }
        lastEnteredLevel = levelIndex;
        animateGirlTo(levelIndex, () -> launchLevel(levelIndex));
    }

    private void shake(Node n) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(55), n);
        tt.setFromX(n.getTranslateX() - 6);
        tt.setToX(n.getTranslateX() + 6);
        tt.setCycleCount(5);
        tt.setAutoReverse(true);
        tt.play();
    }

    private void animateGirlTo(int levelIndex, Runnable after) {
        DoorView d = doors.get(levelIndex);
        double targetX = d.centerX() - 80;
        double targetY = d.centerY() - 70;

        Line path = new Line(
                girlMarker.getLayoutX(), girlMarker.getLayoutY(),
                targetX, targetY
        );
        PathTransition pt = new PathTransition(Duration.millis(320), path, girlMarker);
        pt.setOnFinished(e -> after.run());
        pt.play();
    }

    private void moveGirlToLevel(int levelIndex, boolean animate) {
        DoorView d = doors.get(levelIndex);
        double targetX = d.centerX() - 80;
        double targetY = d.centerY() - 70;
        if (animate) {
            animateGirlTo(levelIndex, () -> {});
        } else {
            girlMarker.setLayoutX(targetX);
            girlMarker.setLayoutY(targetY);
        }
    }

    /**
     * Open the requested level. Array (index 0) -> Level1. Others -> placeholder for now.
     */
    private void launchLevel(int levelIndex) {
        /**if (levelIndex == 0) {
            // ✅ Works when Level1 (via BaseLevel) is a JavaFX Parent
            Level1 level1 = new Level1();
            stage.setScene(new Scene(level1, WIDTH, HEIGHT));
            return;
        }**/
        //change korlam

        if (levelIndex == 0) { // Array
            Level1 level1 = new Level1();
            Scene s = level1.buildScene(WIDTH, HEIGHT);   // <-- use buildScene
            stage.setScene(s);
            return;
        }


        // Placeholder for other levels (ESC to return)
        javafx.scene.layout.Pane placeholder = new javafx.scene.layout.Pane();
        placeholder.setStyle("-fx-background-color: black;");

        javafx.scene.text.Text t = new javafx.scene.text.Text(
                "Placeholder Level " + (levelIndex + 1) + "\n\nPress ESC to return to Level Select"
        );
        t.setFill(javafx.scene.paint.Color.WHITE);
        t.setFont(javafx.scene.text.Font.font(32));
        t.setLayoutX(180);
        t.setLayoutY(220);

        placeholder.getChildren().add(t);

        placeholder.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                stage.setScene(new Scene(new LevelSelectScreen(stage), WIDTH, HEIGHT));
            }
        });
        placeholder.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) placeholder.requestFocus();
        });

        stage.setScene(new Scene(placeholder, WIDTH, HEIGHT));
    }

    /**
     * Call this when a level is completed (not used during placeholder testing).
     */
    public void onLevelCompleted(int completedLevelIndex) {
        // Unlock the next level (if any)
        if (completedLevelIndex >= highestUnlocked && completedLevelIndex + 1 < LEVEL_COUNT) {
            highestUnlocked = completedLevelIndex + 1;
            prefs.putInt(PREF_UNLOCKED, highestUnlocked);

            // Update locks visually
            for (int i = 0; i < doors.size(); i++) {
                doors.get(i).setLocked(i > highestUnlocked);
            }

            // Move girl to next level
            moveGirlToLevel(highestUnlocked, true);
        }
    }

    private void retryLastEntered() {
        handleDoorClick(Math.min(lastEnteredLevel, highestUnlocked));
    }

    private void goHome() {
        TitleScreen title = new TitleScreen();
        try {
            title.start(stage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---------------- HELPER INNER TYPE ----------------

    private class DoorView {
        final Group root = new Group();
        final ImageView door;
        final ImageView lock;
        final Text label;
        final int index;
        boolean locked = true;

        // Small visual bias if your art's "visual center" differs from image center.
        // Tweak to taste (e.g., -4..+4). 0,0 keeps exact center.
        private static final double LOCK_BIAS_X = -4; // move slightly left
        private static final double LOCK_BIAS_Y = -1; // move slightly up

        DoorView(int index, String name, double x, double y, Image doorImg, Image lockImg) {
            this.index = index;

            // Door & lock images
            door = new ImageView(doorImg);
            door.setFitWidth(160);
            door.setPreserveRatio(true);

            lock = new ImageView(lockImg);
            lock.setFitWidth(70);
            lock.setPreserveRatio(true);

            // Put door and lock into a StackPane so lock stays centered
            javafx.scene.layout.StackPane stack = new javafx.scene.layout.StackPane(door, lock);
            javafx.scene.layout.StackPane.setAlignment(lock, javafx.geometry.Pos.CENTER);
            stack.setPickOnBounds(false); // so empty transparent areas don’t block clicks

            // Optional tiny bias to account for asymmetric borders/transparent padding
            lock.setTranslateX(LOCK_BIAS_X);
            lock.setTranslateY(LOCK_BIAS_Y);

            // Level label
            label = new Text(name);
            label.setFont(labelFont);
            label.setFill(Color.WHITE);
            label.setStroke(Color.BLACK);
            label.setStrokeWidth(0.7);
            label.setLayoutY(210);

            // Layout: use stack instead of door/lock directly
            root.getChildren().addAll(stack, label);
            root.setLayoutX(x);
            root.setLayoutY(y);

            // Center label under the door width
            label.layoutBoundsProperty().addListener((obs, oldB, newB) -> {
                label.setLayoutX((door.getFitWidth() - newB.getWidth()) / 2.0);
            });
            // Trigger initial centering
            root.sceneProperty().addListener((o, os, ns) -> {
                if (ns != null) {
                    root.applyCss(); root.layout();
                    label.setLayoutX((door.getFitWidth() - label.getLayoutBounds().getWidth()) / 2.0);
                }
            });

            addDoorInteractions();
        }

        void setOnDoorClicked(Runnable r) {
            root.setOnMouseClicked(e -> r.run());
        }

        void setLocked(boolean value) {
            locked = value;
            lock.setVisible(value);
            root.setOpacity(value ? 0.85 : 1.0);
        }

        double centerX() {
            return root.getLayoutX() + door.getFitWidth() / 2.0;
        }

        double centerY() {
            // Use door's rendered height (bounds) for better accuracy
            double h = door.getBoundsInParent().getHeight();
            return root.getLayoutY() + h / 2.0 - 5;
        }

        private void addDoorInteractions() {
            root.setOnMouseEntered(e -> {
                if (!locked) {
                    ScaleTransition st = new ScaleTransition(Duration.millis(120), root);
                    st.setToX(1.05);
                    st.setToY(1.05);
                    st.play();
                }
            });
            root.setOnMouseExited(e -> {
                ScaleTransition st = new ScaleTransition(Duration.millis(120), root);
                st.setToX(1.0);
                st.setToY(1.0);
                st.play();
            });
        }
    }
}
