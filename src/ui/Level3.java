package ui;

import core.AssetLoader;
import core.LevelConfig;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.media.AudioClip;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Level 3 — Binary Dungeon
 * Follows the scripted story beats with midpoint highlights, wrong/right flow,
 * collapse FX, and a closing Learnings board comparing Binary vs Linear search.
 */
public class Level3 extends BaseLevel {

    /** Set to true to log retry/arrow flow to console (e.g. after wrong choice → Continue → showMidpoint). */
    private static final boolean DEBUG_RETRY_FLOW = false;

    private static final int ARRAY_SIZE = 15;
    private static final int TARGET = 7;
    private static final int STEP_REWARD = 15;
    private static final int WRONG_PENALTY = 10;
    private static final int MAX_SCORE = STEP_REWARD * 3;
    private static final double MUSIC_BASE_VOLUME = 0.55;

    private StackPane world;
    private StackPane fxLayer;
    private StackPane overlayLayer;
    private GridPane doorGrid; // Changed from HBox to GridPane for better layout

    private final List<Integer> doorValues = new ArrayList<>();
    private final List<StackPane> doorTiles = new ArrayList<>();

    private Label targetLabel;
    private VBox instructionPaper;
    private ImageView leftOverlay;
    private ImageView rightOverlay;

    private AudioClip musicClip;

    private int low;
    private int high;
    private int mid;
    private boolean searchActive = false;
    private boolean firstCorrectStepDone = false;
    private int wrongCount = 0;

    // Step visuals
    private ImageView midpointArrow;
    private StackPane victoryOverlay;

    // Corridor: after dialogue auto-hides, this button starts the search
    private StackPane corridorStartWrap;
    private Button corridorStartBtn;

    // Cancel this when showing victory so Claim Victory dialogue does not auto-hide
    private PauseTransition pendingDialogueHide;

    /** Corridor intro dialogue auto-hide (4.5s). Must be cancelled when search starts and when showing punishment dialogue. */
    private PauseTransition corridorDialogueAutoHide;

    /** When true, we are showing the custom punishment dialog. Blocks ALL system dialogue (say) and defers game over until Continue. */
    private boolean showingPunishment = false;

    /** When true, hearts hit 0 during punishment flow; show game over when user clicks Continue. */
    private boolean pendingGameOver = false;

    // Learnings snapshots
    private final LearningsSnapStore learnSnaps = new LearningsSnapStore();
    private boolean snapIntroCaptured = false;
    private boolean snapMidpointCaptured = false;
    private boolean snapHalfCaptured = false;
    private boolean snapVictoryCaptured = false;

    @Override
    protected String getLevelTitle() {
        return "BINARY DUNGEON"; // Shorter title
    }

    @Override
    protected String getBackgroundPath() {
        return AssetLoader.L3_BG_CORRIDOR;
    }

    @Override
    protected void initLevel(StackPane worldLayer, double w, double h) {
        world = new StackPane();
        world.setPickOnBounds(false);
        world.setPrefSize(w, h);

        fxLayer = new StackPane();
        fxLayer.setPickOnBounds(false);

        overlayLayer = new StackPane();
        overlayLayer.setPickOnBounds(false);

        // Use GridPane instead of HBox for better door layout
        doorGrid = new GridPane();
        doorGrid.setAlignment(Pos.CENTER);
        doorGrid.setHgap(15);
        doorGrid.setVgap(10);

        // Make door grid responsive to screen size
        doorGrid.prefWidthProperty().bind(world.widthProperty().multiply(0.95));
        doorGrid.prefHeightProperty().bind(world.heightProperty().multiply(0.7));

        buildInstructionPaper();
        buildSelectionOverlays(worldLayer, w, h);
        buildMidpointArrow();

        // Container for door grid
        StackPane doorContainer = new StackPane(doorGrid);
        doorContainer.setAlignment(Pos.CENTER);
        doorContainer.setPadding(new Insets(30));

        corridorStartWrap = new StackPane();
        corridorStartWrap.setVisible(false);
        corridorStartWrap.setPickOnBounds(true);

        world.getChildren().addAll(doorContainer, instructionPaper, corridorStartWrap);
        StackPane.setAlignment(doorContainer, Pos.CENTER);
        StackPane.setAlignment(instructionPaper, Pos.TOP_RIGHT);
        StackPane.setMargin(instructionPaper, new Insets(32, 42, 0, 0));
        StackPane.setAlignment(corridorStartWrap, Pos.BOTTOM_CENTER);
        StackPane.setMargin(corridorStartWrap, new Insets(0, 0, 50, 0));

        worldLayer.getChildren().addAll(world, fxLayer, overlayLayer);

        // Add resize listener for dynamic door layout
        world.widthProperty().addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> updateDoorLayout());
        });

        world.heightProperty().addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> updateDoorLayout());
        });

        playIntro();
    }

    private void buildInstructionPaper() {
        // Parchment behind; content in front. Short sentences so all text fits inside the paper.
        final double PAPER_W = 320;
        final double PAPER_H = 460;
        final double INNER_MARGIN = 28;

        ImageView parchmentBg = new ImageView(AssetLoader.image(AssetLoader.INSTRUCTION_PAPER));
        parchmentBg.setFitWidth(PAPER_W);
        parchmentBg.setFitHeight(PAPER_H);
        parchmentBg.setPreserveRatio(false);
        parchmentBg.setSmooth(true);
        parchmentBg.setMouseTransparent(true);

        VBox content = new VBox(2);
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(16, 20, 16, 20));
        content.setMinHeight(Region.USE_PREF_SIZE);
        double contentW = PAPER_W - 2 * INNER_MARGIN;
        content.setPrefWidth(contentW);
        content.setMaxWidth(contentW);
        content.setMouseTransparent(false);
        content.setClip(null);

        Label titleLine1 = new Label("Find this number");
        Label titleLine2 = new Label("to escape:");
        for (Label t : new Label[]{titleLine1, titleLine2}) {
            t.setTextFill(Color.web("#2c2416"));
            t.setFont(AssetLoader.loadFont("/fonts/CinzelDecorative-Bold.ttf", 18));
            t.setMaxWidth(Double.MAX_VALUE);
            t.setAlignment(Pos.CENTER);
        }

        StackPane sevenWrap = new StackPane();
        sevenWrap.setMinHeight(48);
        sevenWrap.setPrefHeight(48);
        Label targetNumber = new Label("7");
        targetNumber.setTextFill(Color.web("#2c2416"));
        targetNumber.setFont(AssetLoader.loadFont("/fonts/CinzelDecorative-Bold.ttf", 48));
        targetNumber.setMaxWidth(Double.MAX_VALUE);
        targetNumber.setAlignment(Pos.CENTER);
        sevenWrap.getChildren().add(targetNumber);
        StackPane.setAlignment(targetNumber, Pos.CENTER);
        VBox.setMargin(sevenWrap, new Insets(4, 0, 8, 0));

        Label rules = new Label(
                "How to play:\n\n" +
                        "1. Doors are 1 to 15. Find door 7.\n\n" +
                        "2. Game shows the middle door.\n7 smaller? Pick LEFT.\n7 larger? Pick RIGHT.\n\n" +
                        "3. Repeat until you find 7. You escape!"
        );
        rules.setTextFill(Color.web("#2c2416"));
        rules.setFont(AssetLoader.loadFont("/fonts/Montaga-Regular.ttf", 14));
        rules.setWrapText(true);
        rules.setMaxWidth(Double.MAX_VALUE);
        rules.setAlignment(Pos.CENTER_LEFT);
        rules.setLineSpacing(1);

        content.getChildren().addAll(titleLine1, titleLine2, sevenWrap, rules);

        StackPane paperStack = new StackPane();
        paperStack.setPrefSize(PAPER_W, PAPER_H);
        paperStack.setMinSize(PAPER_W, PAPER_H);
        paperStack.setMaxSize(PAPER_W, PAPER_H);
        paperStack.setClip(null);
        paperStack.getChildren().addAll(parchmentBg, content);
        StackPane.setAlignment(parchmentBg, Pos.CENTER);
        StackPane.setAlignment(content, Pos.CENTER);
        StackPane.setMargin(content, new Insets(INNER_MARGIN));

        instructionPaper = new VBox(paperStack);
        instructionPaper.setPrefWidth(PAPER_W);
        instructionPaper.setMinWidth(PAPER_W);
        instructionPaper.setMaxWidth(PAPER_W);
        instructionPaper.setClip(null);
        instructionPaper.setOpacity(0);
    }

    private void buildSelectionOverlays(StackPane worldLayer, double sceneWidth, double sceneHeight) {
        Image leftImg = AssetLoader.image(AssetLoader.L3_OVERLAY_LEFT);
        Image rightImg = AssetLoader.image(AssetLoader.L3_OVERLAY_RIGHT);

        leftOverlay = new ImageView(leftImg);
        rightOverlay = new ImageView(rightImg);

        leftOverlay.setPreserveRatio(true);
        rightOverlay.setPreserveRatio(true);

        leftOverlay.setOpacity(0);
        rightOverlay.setOpacity(0);

        leftOverlay.setOnMouseClicked(e -> chooseHalf(true));
        rightOverlay.setOnMouseClicked(e -> chooseHalf(false));

        overlayLayer.getChildren().addAll(leftOverlay, rightOverlay);
        StackPane.setAlignment(leftOverlay, Pos.CENTER_LEFT);
        StackPane.setAlignment(rightOverlay, Pos.CENTER_RIGHT);
        StackPane.setMargin(leftOverlay, new Insets(0, 0, 0, 0));
        StackPane.setMargin(rightOverlay, new Insets(0, 0, 0, 0));
        overlayLayer.setMouseTransparent(true);

        leftOverlay.fitWidthProperty().bind(backgroundView.fitWidthProperty().multiply(0.5));
        rightOverlay.fitWidthProperty().bind(backgroundView.fitWidthProperty().multiply(0.5));
        leftOverlay.fitHeightProperty().bind(backgroundView.fitHeightProperty().multiply(0.78));
        rightOverlay.fitHeightProperty().bind(backgroundView.fitHeightProperty().multiply(0.78));
    }

    private void buildMidpointArrow() {
        midpointArrow = new ImageView(AssetLoader.image(AssetLoader.L3_HINT_ARROWS));
        midpointArrow.setPreserveRatio(true);
        midpointArrow.setFitWidth(120);
        midpointArrow.setOpacity(0);
        fxLayer.getChildren().add(midpointArrow);
        StackPane.setAlignment(midpointArrow, Pos.TOP_LEFT);
    }

    private void updateDoorLayout() {
        if (doorGrid == null || doorGrid.getChildren().isEmpty()) return;

        int doorCount = doorGrid.getChildren().size();
        double availableWidth = doorGrid.getWidth();
        double availableHeight = doorGrid.getHeight();

        // Calculate optimal grid layout
        int cols = calculateOptimalColumns(doorCount);
        int rows = (int) Math.ceil((double) doorCount / cols);

        // Clear existing constraints
        doorGrid.getChildren().clear();
        doorGrid.getColumnConstraints().clear();
        doorGrid.getRowConstraints().clear();

        // Set up dynamic column widths
        for (int i = 0; i < cols; i++) {
            ColumnConstraints colConst = new ColumnConstraints();
            colConst.setPercentWidth(100.0 / cols);
            colConst.setHgrow(Priority.ALWAYS);
            doorGrid.getColumnConstraints().add(colConst);
        }

        // Set up dynamic row heights
        for (int i = 0; i < rows; i++) {
            RowConstraints rowConst = new RowConstraints();
            rowConst.setPercentHeight(100.0 / rows);
            rowConst.setVgrow(Priority.ALWAYS);
            doorGrid.getRowConstraints().add(rowConst);
        }

        // Reposition doors
        int index = 0;
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (index < doorTiles.size()) {
                    StackPane door = doorTiles.get(index);
                    doorGrid.add(door, j, i);
                    index++;
                }
            }
        }

        // Adjust spacing based on door count
        double spacing = calculateOptimalSpacing(doorCount);
        doorGrid.setHgap(spacing);
        doorGrid.setVgap(spacing * 0.8);
    }

    private int calculateOptimalColumns(int doorCount) {
        if (doorCount <= 4) return doorCount;
        if (doorCount <= 8) return 4;
        if (doorCount <= 12) return 4;
        return 5; // For 13-15 doors
    }

    private double calculateOptimalSpacing(int doorCount) {
        if (doorCount <= 4) return 40;
        if (doorCount <= 8) return 30;
        if (doorCount <= 12) return 20;
        return 15;
    }

    private void playIntro() {
        swapBackground(AssetLoader.L3_BG_CORRIDOR);
        backgroundView.setEffect(null);
        playLoop("/Music_binarysearch/dark-ambient-soundscape-dreamscape-462864.mp3");

        UiUtil.glowPulse(backgroundView, Color.web("#8364a9"), 22, 1100);

        // After the level name/number splash vanishes, show wizard dialogue and instruction paper
        showLevelSplash(() -> {
            instructionPaper.setVisible(true);
            fadeNodeIn(instructionPaper, 600, 0);
            UiUtil.glowPulse(instructionPaper, Color.web("#d6b46a"), 26, 900);
            Button proceed = UiUtil.btn("Begin Search");
            proceed.setOnAction(ev -> startCorridor());
            say("""
Adventurer… the scroll reveals your Target Number.
To escape this dungeon, you must use Binary Search.
Each wrong choice will drain your strength. The walls will collapse, and you may be buried beneath the ruins.""", null, proceed);
        });

        Platform.runLater(() ->
                SnapshotUtil.captureNode(
                        getBlurTarget(),
                        2.0,
                        Color.TRANSPARENT,
                        img -> {
                            learnSnaps.clear();
                            learnSnaps.add(img);
                            snapIntroCaptured = true;
                        }
                )
        );
    }

    private void showLevelSplash(Runnable onSplashComplete) {
        Label title = new Label("LEVEL 3 – Binary Search – Dungeon Escape");
        title.setTextFill(Color.web("#F8E7C0"));
        title.setFont(AssetLoader.loadFont("/fonts/CinzelDecorative-Bold.ttf", 44));
        title.setTextAlignment(TextAlignment.CENTER);
        title.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.9), 20, 0.8, 0, 3);");
        title.setWrapText(true);
        title.setMaxWidth(900);
        StackPane.setAlignment(title, Pos.CENTER);

        StackPane splash = new StackPane(title);
        splash.setPickOnBounds(false);
        fxLayer.getChildren().add(splash);
        StackPane.setAlignment(splash, Pos.CENTER);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(400), splash);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);

        PauseTransition hold = new PauseTransition(Duration.millis(1700));

        FadeTransition fadeOut = new FadeTransition(Duration.millis(450), splash);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(e -> {
            fxLayer.getChildren().remove(splash);
            if (onSplashComplete != null) onSplashComplete.run();
        });

        SequentialTransition seq = new SequentialTransition(fadeIn, hold, fadeOut);
        seq.playFromStart();
    }

    private void startCorridor() {
        dialogue.hide();
        swapBackground(AssetLoader.L3_BG_CORRIDOR);
        backgroundView.setEffect(new GaussianBlur(10));
        instructionPaper.setVisible(false);

        playLoop("/Music_binarysearch/tense-suspense-background-music-442839.mp3");

        rebuildDoors(1, ARRAY_SIZE, true);
        fadeNodeIn(doorGrid, 420, 0);

        // Wizard gives instruction; after 4.5s dialogue disappears and "I will begin" button appears
        corridorStartBtn = UiUtil.btn("I will begin");
        corridorStartBtn.setOnAction(e -> {
            if (corridorStartWrap != null) corridorStartWrap.setVisible(false);
            stopAllDialogueAutoHideTimers();
            dialogue.hide();
            startSearch();
        });
        corridorStartWrap.getChildren().setAll(corridorStartBtn);

        say("""
Behold the array of doors, numbered from 1 to 15.
Your Target Number is 7. Begin your search.""", null);

        corridorDialogueAutoHide = new PauseTransition(Duration.millis(4500));
        corridorDialogueAutoHide.setOnFinished(e -> {
            dialogue.hide();
            if (corridorStartWrap != null) corridorStartWrap.setVisible(true);
        });
        corridorDialogueAutoHide.play();
    }

    private void rebuildDoors(int start, int end, boolean animate) {
        doorValues.clear();
        doorTiles.clear();
        doorGrid.getChildren().clear();
        doorGrid.getColumnConstraints().clear();
        doorGrid.getRowConstraints().clear();

        int doorCount = end - start + 1;
        int cols = calculateOptimalColumns(doorCount);
        int rows = (int) Math.ceil((double) doorCount / cols);

        // Setup dynamic grid constraints
        for (int i = 0; i < cols; i++) {
            ColumnConstraints colConst = new ColumnConstraints();
            colConst.setPercentWidth(100.0 / cols);
            colConst.setHgrow(Priority.ALWAYS);
            doorGrid.getColumnConstraints().add(colConst);
        }

        for (int i = 0; i < rows; i++) {
            RowConstraints rowConst = new RowConstraints();
            rowConst.setPercentHeight(100.0 / rows);
            rowConst.setVgrow(Priority.ALWAYS);
            doorGrid.getRowConstraints().add(rowConst);
        }

        double spacing = calculateOptimalSpacing(doorCount);
        doorGrid.setHgap(spacing);
        doorGrid.setVgap(spacing * 0.8);

        int row = 0, col = 0;
        for (int i = start; i <= end; i++) {
            doorValues.add(i);
            StackPane tile = createDoorTile(i);
            doorTiles.add(tile);

            doorGrid.add(tile, col, row);

            if (animate) {
                fadeNodeIn(tile, 220, (i - start) * 40);
            } else {
                tile.setOpacity(1.0);
            }

            col++;
            if (col >= cols) {
                col = 0;
                row++;
            }
        }
    }

    private StackPane createDoorTile(int value) {
        // Dynamic door sizing based on available space
        double doorWidth = 80;
        double doorHeight = 160;

        // Adjust size based on total doors
        int doorCount = doorValues.size();
        if (doorCount <= 4) {
            doorWidth = 100;
            doorHeight = 200;
        } else if (doorCount <= 8) {
            doorWidth = 90;
            doorHeight = 180;
        }

        ImageView doorImg = AssetLoader.imageView(AssetLoader.L3_DOOR, doorWidth, doorHeight, true);
        doorImg.setPreserveRatio(false); // Allow stretching

        Label num = new Label(String.valueOf(value));
        num.setTextFill(Color.WHITE);
        num.setFont(AssetLoader.loadFont("/fonts/CinzelDecorative-Bold.ttf", 22));

        StackPane tile = new StackPane(doorImg, num);
        tile.setUserData(value);

        // Make door tile expandable
        tile.setMinSize(doorWidth, doorHeight);
        tile.setPrefSize(doorWidth, doorHeight);
        tile.setMaxSize(doorWidth, doorHeight);

        return tile;
    }

    private void startSearch() {
        low = 1;
        high = ARRAY_SIZE;
        searchActive = true;
        firstCorrectStepDone = false;
        Platform.runLater(this::showMidpoint);
    }

    private void showMidpoint() {
        if (showingPunishment) {
            showingPunishment = false;
            overlayLayer.getChildren().removeIf(node ->
                    node instanceof StackPane && "PUNISHMENT_DIALOG".equals(node.getId()));
        }
        if (DEBUG_RETRY_FLOW) System.out.println("[Level3] showMidpoint() called. searchActive=" + searchActive + " low=" + low + " high=" + high);
        if (!searchActive) return;
        if (low > high) {
            showGameOverImmediate();
            return;
        }

        mid = (low + high) / 2;
        StackPane midDoor = findDoor(mid);
        if (midDoor != null) {
            UiUtil.glowPulse(midDoor, Color.web("#4cd1ff"), 30, 900);
            UiUtil.glowPulse(midDoor, Color.web("#ffd27f"), 24, 700);
            positionArrowAbove(midDoor);
            if (DEBUG_RETRY_FLOW) System.out.println("[Level3] showMidpoint: arrow positioned above door " + mid);
        } else if (DEBUG_RETRY_FLOW) {
            System.out.println("[Level3] showMidpoint: midDoor is null for mid=" + mid);
        }

        overlayLayer.setMouseTransparent(false);
        fadeOverlay(true);
        if (DEBUG_RETRY_FLOW) System.out.println("[Level3] showMidpoint: overlayLayer mouseTransparent=false, fadeOverlay(true).");

        playOneShot("/Music_binarysearch/darkness-approaching-cinematic-danger-407228.mp3");

        String text = "The midpoint is " + mid + ". Compare it to the Target: 7.\nIf smaller, step left. If greater, step right.";
        say(text, null);
        // Wizard gives instruction; stay visible long enough for the reader to read (only for this midpoint line; wrong-choice dialogue has no auto-hide)
        stopAndClearDialogueAutoHide();
        pendingDialogueHide = new PauseTransition(Duration.millis(8000));
        pendingDialogueHide.setOnFinished(e -> {
            if (!showingPunishment) dialogue.hide();
        });
        pendingDialogueHide.play();

        if (snapIntroCaptured && !snapMidpointCaptured && low == 1 && high == ARRAY_SIZE) {
            SnapshotUtil.captureNode(
                    getBlurTarget(),
                    2.0,
                    Color.TRANSPARENT,
                    img -> {
                        learnSnaps.add(img);
                        snapMidpointCaptured = true;
                    }
            );
        }
    }

    private StackPane findDoor(int value) {
        for (Node n : doorGrid.getChildren()) {
            if (n instanceof StackPane sp && value == (int) sp.getUserData()) {
                return sp;
            }
        }
        return null;
    }

    private void chooseHalf(boolean left) {
        if (!searchActive || showingPunishment) return;

        if (mid == TARGET) {
            handleTargetFound();
            return;
        }

        boolean shouldGoLeft = TARGET < mid;
        boolean correct = (left && shouldGoLeft) || (!left && !shouldGoLeft);

        if (correct) {
            handleCorrectHalf();
        } else {
            handleWrongChoice();
        }
    }

    private void handleWrongChoice() {
        if (DEBUG_RETRY_FLOW) System.out.println("=== HANDLE WRONG CHOICE START ===");
        searchActive = true;
        wrongCount++;
        showingPunishment = true;

        if (pendingDialogueHide != null) {
            pendingDialogueHide.stop();
            pendingDialogueHide.setOnFinished(null);
            pendingDialogueHide = null;
        }
        if (corridorDialogueAutoHide != null) {
            corridorDialogueAutoHide.stop();
            corridorDialogueAutoHide.setOnFinished(null);
            corridorDialogueAutoHide = null;
        }
        try {
            dialogue.hide();
            if (DEBUG_RETRY_FLOW) System.out.println("[Level3] Force-hid system dialogue");
        } catch (Exception ignored) {}

        overlayLayer.setMouseTransparent(true);
        fadeOverlay(false);
        hideArrow();

        StackPane midDoor = findDoor(mid);
        loseHeartWithFx(midDoor);
        getGameState().loseScore(WRONG_PENALTY);

        showCollapseFx();
        stopMusic();
        playLoop("/Music_binarysearch/gothic-horror-380504.mp3");
        PauseTransition stopGothicAfter = new PauseTransition(Duration.seconds(3));
        stopGothicAfter.setOnFinished(ev -> {
            stopMusic();
            if (searchActive) {
                playLoop("/Music_binarysearch/tense-suspense-background-music-442839.mp3");
            }
        });
        stopGothicAfter.play();

        Button continueBtn = UiUtil.btn("Continue");
        createAndShowPunishmentDialog(continueBtn);
        if (DEBUG_RETRY_FLOW) System.out.println("[Level3] Custom punishment dialog shown");
    }

    /**
     * Creates and shows the custom punishment dialog on overlayLayer. No auto-hide; only Continue removes it.
     */
    private void createAndShowPunishmentDialog(Button continueBtn) {
        overlayLayer.getChildren().removeIf(node ->
                node instanceof StackPane && "PUNISHMENT_DIALOG".equals(node.getId()));

        StackPane punishmentDialog = new StackPane();
        punishmentDialog.setId("PUNISHMENT_DIALOG");
        punishmentDialog.setAlignment(Pos.CENTER);

        Region blocker = new Region();
        blocker.setStyle("-fx-background-color: rgba(0,0,0,0.75);");
        blocker.setMouseTransparent(false);
        blocker.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        blocker.setMinSize(0, 0);

        VBox dialogContent = new VBox(25);
        dialogContent.setAlignment(Pos.CENTER);
        dialogContent.setStyle(
                "-fx-background-color: linear-gradient(to bottom, #3a2c1a, #2a1c0a); " +
                        "-fx-padding: 40px; " +
                        "-fx-background-radius: 20px; " +
                        "-fx-border-color: #b89a6d; " +
                        "-fx-border-width: 3px; " +
                        "-fx-border-radius: 20px; " +
                        "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.9), 25, 0.7, 0, 8);"
        );
        dialogContent.setMaxWidth(550);
        dialogContent.setMaxHeight(400);

        Label message = new Label("The dungeon punishes your mistake.\nA heart is lost, your score diminished.\nChoose again, or be crushed beneath the rubble.");
        message.setStyle(
                "-fx-text-fill: #f8e7c0; " +
                        "-fx-font-size: 18px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-wrap-text: true; " +
                        "-fx-text-alignment: center; " +
                        "-fx-line-spacing: 10px;"
        );
        message.setMaxWidth(500);
        try {
            message.setFont(AssetLoader.loadFont("/fonts/CinzelDecorative-Regular.ttf", 18));
        } catch (Exception ignored) {}

        final String btnStyle = "-fx-background-color: linear-gradient(to bottom, #b89a6d, #8b6d4a); " +
                "-fx-text-fill: #2c2416; -fx-font-weight: bold; -fx-font-size: 16px; " +
                "-fx-padding: 15px 40px; -fx-background-radius: 8px; " +
                "-fx-border-color: #d4af37; -fx-border-width: 2px; -fx-border-radius: 8px; -fx-cursor: hand;";
        final String btnHover = "-fx-background-color: linear-gradient(to bottom, #d4b686, #a88a62); " +
                "-fx-text-fill: #2c2416; -fx-font-weight: bold; -fx-font-size: 16px; " +
                "-fx-padding: 15px 40px; -fx-background-radius: 8px; " +
                "-fx-border-color: #e6c158; -fx-border-width: 2px; -fx-border-radius: 8px; -fx-cursor: hand;";
        continueBtn.setStyle(btnStyle);
        try {
            continueBtn.setFont(AssetLoader.loadFont("/fonts/CinzelDecorative-Regular.ttf", 16));
        } catch (Exception ignored) {}
        continueBtn.setOnMouseEntered(e -> continueBtn.setStyle(btnHover));
        continueBtn.setOnMouseExited(e -> continueBtn.setStyle(btnStyle));

        continueBtn.setOnAction(e -> {
            if (DEBUG_RETRY_FLOW) System.out.println("[Level3] Continue clicked. Hearts left: " + getGameState().getHearts());
            overlayLayer.getChildren().remove(punishmentDialog);
            showingPunishment = false;
            dialogue.hide();
            if (pendingGameOver || getGameState().getHearts() <= 0) {
                pendingGameOver = false;
                if (DEBUG_RETRY_FLOW) System.out.println("[Level3] No hearts left -> GAME OVER");
                showGameOverImmediate();
                return;
            }
            overlayLayer.setMouseTransparent(false);
            PauseTransition delay = new PauseTransition(Duration.millis(500));
            delay.setOnFinished(ev -> {
                if (DEBUG_RETRY_FLOW) System.out.println("[Level3] Showing midpoint again after punishment");
                showMidpoint();
            });
            delay.play();
        });

        dialogContent.getChildren().addAll(message, continueBtn);
        punishmentDialog.getChildren().addAll(blocker, dialogContent);
        punishmentDialog.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        punishmentDialog.minWidthProperty().bind(overlayLayer.widthProperty());
        punishmentDialog.minHeightProperty().bind(overlayLayer.heightProperty());
        punishmentDialog.setMouseTransparent(false);
        punishmentDialog.setPickOnBounds(true);
        overlayLayer.setMouseTransparent(false);
        overlayLayer.getChildren().add(punishmentDialog);
        if (DEBUG_RETRY_FLOW) System.out.println("[Level3] Punishment dialog created and shown");
    }

    /** Stops and clears the midpoint dialogue auto-hide timer. Call before showing wrong-choice dialogue so it never auto-hides. */
    private void stopAndClearDialogueAutoHide() {
        if (pendingDialogueHide != null) {
            pendingDialogueHide.stop();
            pendingDialogueHide.setOnFinished(null);
            pendingDialogueHide = null;
        }
    }

    /** Stops ALL dialogue auto-hide timers (midpoint + corridor). Call when starting search and when showing punishment dialogue. */
    private void stopAllDialogueAutoHideTimers() {
        stopAndClearDialogueAutoHide();
        if (corridorDialogueAutoHide != null) {
            corridorDialogueAutoHide.stop();
            corridorDialogueAutoHide.setOnFinished(null);
            corridorDialogueAutoHide = null;
        }
    }

    /** When showingPunishment is true, blocks system dialogue so only the custom punishment dialog is visible. */
    private void safeSay(String message, Runnable after, javafx.scene.Node... actions) {
        if (!showingPunishment) {
            say(message, after, actions);
        } else if (DEBUG_RETRY_FLOW) {
            System.out.println("[Level3] BLOCKED system dialogue during punishment: " +
                    (message.length() > 50 ? message.substring(0, 50) + "..." : message));
        }
    }

    private void handleCorrectHalf() {
        searchActive = true;
        overlayLayer.setMouseTransparent(true);
        fadeOverlay(false);
        hideArrow();

        if (TARGET < mid) {
            high = mid - 1;
        } else {
            low = mid + 1;
        }
        getGameState().addScore(STEP_REWARD);

        // Fade away unused half and reposition remaining doors
        trimDoors(low, high);

        boolean willBeFinalStep = (low == high && low == TARGET);

        if (!willBeFinalStep && !snapHalfCaptured) {
            SnapshotUtil.captureNode(
                    getBlurTarget(),
                    2.0,
                    Color.TRANSPARENT,
                    img -> {
                        learnSnaps.add(img);
                        snapHalfCaptured = true;
                    }
            );
        }

        PauseTransition pause = new PauseTransition(Duration.millis(700));
        pause.setOnFinished(e -> {
            if (low == high && low == TARGET) {
                handleTargetFound();
            } else {
                if (!firstCorrectStepDone) {
                    firstCorrectStepDone = true;
                    safeSay("Correct choice! Now the search area becomes half. Continue with Binary Search.", null);
                    // Wizard gives instruction; stay visible long enough for the reader to read
                    hideDialogueThen(8000, () -> {
                        overlayLayer.setMouseTransparent(false);
                        showMidpoint();
                    });
                } else {
                    overlayLayer.setMouseTransparent(false);
                    showMidpoint();
                }
            }
        });
        pause.playFromStart();
    }

    private void trimDoors(int newLow, int newHigh) {
        List<Node> toRemove = doorGrid.getChildren().stream()
                .filter(n -> {
                    int v = (int) n.getUserData();
                    return v < newLow || v > newHigh;
                })
                .collect(Collectors.toList());

        // Fade out removed doors
        ParallelTransition fades = new ParallelTransition();
        for (Node n : toRemove) {
            FadeTransition ft = new FadeTransition(Duration.millis(360), n);
            ft.setToValue(0);
            ft.setOnFinished(e -> doorGrid.getChildren().remove(n));
            fades.getChildren().add(ft);
        }
        fades.play();

        // Remove from doorValues list
        doorValues.removeIf(v -> v < newLow || v > newHigh);

        // Remove from doorTiles list
        doorTiles.removeIf(tile -> {
            int v = (int) tile.getUserData();
            return v < newLow || v > newHigh;
        });

        // After removing doors, reposition and enlarge remaining ones
        PauseTransition pause = new PauseTransition(Duration.millis(400));
        pause.setOnFinished(e -> {
            updateDoorLayout();
            emphasizeRemainingDoors();
        });
        pause.play();
    }

    private void emphasizeRemainingDoors() {
        // Animate remaining doors to be more prominent
        for (Node node : doorGrid.getChildren()) {
            if (node instanceof StackPane doorTile) {
                ScaleTransition scale = new ScaleTransition(Duration.millis(500), doorTile);
                scale.setToX(1.2);
                scale.setToY(1.2);
                scale.setAutoReverse(true);
                scale.setCycleCount(2);

                // Enlarge doors permanently after animation
                ScaleTransition enlarge = new ScaleTransition(Duration.millis(300), doorTile);
                enlarge.setToX(1.15);
                enlarge.setToY(1.15);
                enlarge.setOnFinished(event -> {
                    doorTile.setScaleX(1.15);
                    doorTile.setScaleY(1.15);
                });

                SequentialTransition seq = new SequentialTransition(scale, enlarge);
                seq.play();
            }
        }
    }

    private void handleTargetFound() {
        searchActive = false;
        overlayLayer.setMouseTransparent(true);
        fadeOverlay(false);
        hideArrow();
        stopAndClearDialogueAutoHide();
        // Turn off any previous tracks (including gothic horror) before victory music.
        stopMusic();
        playLoop("/Music_binarysearch/emotional-cinematic-inspirational-piano-main-10524.mp3");

        StackPane targetDoor = findDoor(TARGET);
        if (targetDoor != null) {
            UiUtil.glowPulse(targetDoor, Color.web("#ffd86b"), 34, 1200);
            UiUtil.glowPulse(targetDoor, Color.web("#ffd86b"), 34, 1400);
        }

        backgroundView.setEffect(new GaussianBlur(16));
        showVictoryFx();
        showVictoryMessageOverlay();

        if (!snapVictoryCaptured) {
            SnapshotUtil.captureNode(
                    getBlurTarget(),
                    2.0,
                    Color.TRANSPARENT,
                    img -> {
                        learnSnaps.add(img);
                        snapVictoryCaptured = true;
                    }
            );
        }

        Button finish = UiUtil.btn("Claim Victory");
        finish.setOnAction(e -> showFinalBoard());

        say("""
Splendid! You have found the Target Number.
The dungeon yields to your wisdom.""", null, finish);
    }

    private void showFinalBoard() {
        dialogue.hide();
        hideVictoryOverlay();
        swapBackground(AssetLoader.L3_THRONE_BG);
        backgroundView.setEffect(null);
        showSurvivedThen(
                getGameState().getScore(),
                "Binary Search found the target in log₂(N) steps.",
                MAX_SCORE,
                this::showLearnings
        );
    }

    private void fadeNodeIn(Node n, int ms, int delay) {
        FadeTransition ft = new FadeTransition(Duration.millis(ms), n);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.setDelay(Duration.millis(delay));
        ft.playFromStart();
    }

    private void fadeOverlay(boolean on) {
        double target = on ? 0.9 : 0.0;
        ParallelTransition p = new ParallelTransition(
                new FadeTransition(Duration.millis(260), leftOverlay),
                new FadeTransition(Duration.millis(260), rightOverlay)
        );
        ((FadeTransition) p.getChildren().get(0)).setToValue(target);
        ((FadeTransition) p.getChildren().get(1)).setToValue(target);
        p.play();
    }

    private void swapBackground(String path) {
        try {
            backgroundView.setImage(AssetLoader.image(path));
        } catch (Exception ignored) {
        }
    }

    private void hideDialogueThen(int ms, Runnable next) {
        PauseTransition p = new PauseTransition(Duration.millis(ms));
        p.setOnFinished(ev -> {
            dialogue.hide();
            if (next != null) next.run();
        });
        p.playFromStart();
    }

    // ---------- FX helpers ----------

    private void showCollapseFx() {
        javafx.scene.image.Image debrisImg = null;
        try {
            debrisImg = AssetLoader.image(AssetLoader.L3_STONE_DEBRIS_1);
        } catch (Exception ignored) {}
        if (debrisImg == null) {
            try {
                debrisImg = AssetLoader.image(AssetLoader.L3_STONE_DEBRIS_2);
            } catch (Exception ignored) {}
        }
        if (debrisImg == null) return;
        ImageView debris = new ImageView(debrisImg);
        debris.setPreserveRatio(true);
        debris.setFitWidth(520);
        debris.setOpacity(0);
        StackPane.setAlignment(debris, Pos.CENTER);

        fxLayer.getChildren().add(debris);

        FadeTransition dFade = new FadeTransition(Duration.millis(320), debris);
        dFade.setFromValue(0);
        dFade.setToValue(1);

        TranslateTransition shake = new TranslateTransition(Duration.millis(80), getBlurTarget());
        shake.setByX(8);
        shake.setCycleCount(8);
        shake.setAutoReverse(true);

        dFade.setOnFinished(e -> {
            PauseTransition wait = new PauseTransition(Duration.millis(700));
            wait.setOnFinished(done -> {
                fxLayer.getChildren().remove(debris);
                if (getBlurTarget() != null) {
                    getBlurTarget().setTranslateX(0);
                    getBlurTarget().setTranslateY(0);
                }
            });
            wait.play();
        });

        shake.play();
        dFade.play();
    }

    private void showVictoryFx() {
        ImageView floorGlyph = new ImageView(AssetLoader.image(AssetLoader.L3_FLOOR_GLYPH));
        floorGlyph.setPreserveRatio(true);
        floorGlyph.setFitWidth(360);
        floorGlyph.setOpacity(0);
        StackPane.setAlignment(floorGlyph, Pos.BOTTOM_CENTER);
        StackPane.setMargin(floorGlyph, new Insets(0, 0, 40, 0));

        Label targetNum = new Label(String.valueOf(TARGET));
        targetNum.setFont(AssetLoader.loadFont("/fonts/CinzelDecorative-Bold.ttf", 56));
        targetNum.setTextFill(Color.GOLD);
        StackPane glyphWrap = new StackPane(floorGlyph, targetNum);
        glyphWrap.setOpacity(0);
        StackPane.setAlignment(glyphWrap, Pos.BOTTOM_CENTER);
        StackPane.setMargin(glyphWrap, new Insets(0, 0, 40, 0));

        ImageView burst = new ImageView(AssetLoader.image(AssetLoader.L3_RUNE_BURST));
        burst.setPreserveRatio(true);
        burst.setFitWidth(280);
        burst.setOpacity(0);
        StackPane.setAlignment(burst, Pos.CENTER);

        ImageView banner = new ImageView(AssetLoader.image(AssetLoader.L3_VICTORY_BANNER));
        banner.setPreserveRatio(true);
        banner.setFitWidth(420);
        banner.setTranslateY(-400);
        StackPane.setAlignment(banner, Pos.TOP_CENTER);

        fxLayer.getChildren().addAll(glyphWrap, burst, banner);

        FadeTransition glyphFade = new FadeTransition(Duration.millis(520), glyphWrap);
        glyphFade.setFromValue(0);
        glyphFade.setToValue(1);

        FadeTransition burstFade = new FadeTransition(Duration.millis(380), burst);
        burstFade.setFromValue(0);
        burstFade.setToValue(1);

        TranslateTransition burstRise = new TranslateTransition(Duration.millis(820), burst);
        burstRise.setFromY(40);
        burstRise.setToY(-180);

        TranslateTransition bannerDrop = new TranslateTransition(Duration.millis(620), banner);
        bannerDrop.setFromY(-420);
        bannerDrop.setToY(0);
        bannerDrop.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition all = new ParallelTransition(glyphFade, burstFade, burstRise, bannerDrop);
        all.play();
    }

    private void showVictoryMessageOverlay() {
        if (victoryOverlay != null && victoryOverlay.getParent() != null) return;

        Region dim = new Region();
        dim.setStyle("-fx-background-color: rgba(0,0,0,0.55);");

        Label msg = new Label("VICTORY!\nYou escaped the dungeon.");
        msg.setTextFill(Color.web("#FFD86B"));
        msg.setFont(AssetLoader.loadFont("/fonts/CinzelDecorative-Bold.ttf", 54));
        msg.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.85), 18, 0.6, 0, 2);");
        msg.setAlignment(Pos.CENTER);

        VBox box = new VBox(14, msg);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(36));
        box.setStyle("-fx-background-color: rgba(20,16,10,0.35); -fx-background-radius: 16;");

        victoryOverlay = new StackPane(dim, box);
        victoryOverlay.setPickOnBounds(false);
        victoryOverlay.setMouseTransparent(true);
        fxLayer.getChildren().add(victoryOverlay);
        StackPane.setAlignment(victoryOverlay, Pos.CENTER);

        FadeTransition ft = new FadeTransition(Duration.millis(320), victoryOverlay);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.playFromStart();
    }

    private void hideVictoryOverlay() {
        if (victoryOverlay == null) return;
        fxLayer.getChildren().remove(victoryOverlay);
        victoryOverlay = null;
    }

    // ---------- Audio ----------

    private java.net.URL resolveMusicUrl(String path) {
        java.net.URL url = getClass().getResource(path);
        if (url == null && path.startsWith("/")) {
            url = getClass().getClassLoader().getResource(path.substring(1));
        }
        return url;
    }

    private void playLoop(String path) {
        try {
            if (musicClip != null) musicClip.stop();
            java.net.URL url = resolveMusicUrl(path);
            if (url == null) {
                System.err.println("Level3: Music not found (check classpath): " + path);
                return;
            }
            musicClip = new AudioClip(url.toExternalForm());
            musicClip.setCycleCount(AudioClip.INDEFINITE);
            musicClip.setVolume(MUSIC_BASE_VOLUME);
            musicClip.play();
        } catch (Exception e) {
            System.err.println("Level3: Failed to play music: " + path + " - " + e.getMessage());
        }
    }

    private void playOneShot(String path) {
        try {
            java.net.URL url = resolveMusicUrl(path);
            if (url == null) {
                System.err.println("Level3: Music not found (check classpath): " + path);
                return;
            }
            AudioClip clip = new AudioClip(url.toExternalForm());
            clip.setVolume(MUSIC_BASE_VOLUME);
            clip.play();
        } catch (Exception e) {
            System.err.println("Level3: Failed to play one-shot: " + path + " - " + e.getMessage());
        }
    }

    private void stopMusic() {
        try {
            if (musicClip != null) musicClip.stop();
        } catch (Exception ignored) {}
    }

    // ---------- Hearts + HUD feedback ----------

    private void loseHeartWithFx(Node anchor) {
        getGameState().loseHeart();
        try {
            HUD hudAccess = getHud();
            if (hudAccess != null) hudAccess.pulseHearts();
        } catch (Throwable ignore) {
        }
        showMinusOneHeartBubble(anchor);
    }

    private void showMinusOneHeartBubble(Node anchor) {
        if (anchor == null || anchor.getScene() == null) return;

        Label bubble = new Label("−1 ❤");
        bubble.setStyle("-fx-text-fill: #FF4A4A; -fx-font-size: 18px; -fx-font-weight: bold;");
        bubble.setMouseTransparent(true);
        bubble.setOpacity(0.0);

        addToStatusLayer(bubble);
        StackPane.setAlignment(bubble, Pos.TOP_LEFT);

        Platform.runLater(() -> {
            StackPane statusLayer = getStatusLayer();
            if (statusLayer == null || bubble.getParent() == null) return;

            Bounds bScene = anchor.localToScene(anchor.getBoundsInLocal());
            Point2D topCenterScene = new Point2D(
                    bScene.getMinX() + bScene.getWidth() / 2.0,
                    bScene.getMinY()
            );
            Point2D p = statusLayer.sceneToLocal(topCenterScene);

            bubble.setTranslateX(p.getX());
            bubble.setTranslateY(p.getY());

            Timeline tl = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(bubble.opacityProperty(), 0.0),
                            new KeyValue(bubble.translateYProperty(), p.getY())
                    ),
                    new KeyFrame(Duration.millis(150),
                            new KeyValue(bubble.opacityProperty(), 1.0)
                    ),
                    new KeyFrame(Duration.millis(750),
                            new KeyValue(bubble.translateYProperty(), p.getY() - 24),
                            new KeyValue(bubble.opacityProperty(), 0.0)
                    )
            );
            tl.setOnFinished(e -> statusLayer.getChildren().remove(bubble));
            tl.play();
        });
    }

    // ---------- Step arrow positioning ----------

    private void positionArrowAbove(Node doorTile) {
        if (midpointArrow == null || doorTile == null || doorTile.getScene() == null) return;
        midpointArrow.setOpacity(0);

        Platform.runLater(() -> {
            if (midpointArrow.getParent() == null) {
                if (DEBUG_RETRY_FLOW) System.out.println("[Level3] positionArrowAbove: arrow has no parent, skipping.");
                return;
            }
            midpointArrow.toFront();
            Bounds bScene = doorTile.localToScene(doorTile.getBoundsInLocal());
            Point2D p = fxLayer.sceneToLocal(
                    bScene.getMinX() + bScene.getWidth() / 2.0 - (midpointArrow.getFitWidth() / 2.0),
                    bScene.getMinY() - 40
            );
            midpointArrow.setTranslateX(p.getX());
            midpointArrow.setTranslateY(p.getY());

            FadeTransition in = new FadeTransition(Duration.millis(180), midpointArrow);
            in.setToValue(1.0);
            in.playFromStart();

            TranslateTransition bob = new TranslateTransition(Duration.millis(650), midpointArrow);
            bob.setFromY(p.getY());
            bob.setToY(p.getY() - 8);
            bob.setAutoReverse(true);
            bob.setCycleCount(6);
            bob.playFromStart();
        });
    }

    private void hideArrow() {
        if (midpointArrow == null) return;
        midpointArrow.setOpacity(0);
    }

    // ---------- Game over ----------

    @Override
    protected void showGameOverImmediate() {
        if (showingPunishment) {
            pendingGameOver = true;
            return;
        }
        searchActive = false;
        overlayLayer.setMouseTransparent(true);
        fadeOverlay(false);
        hideArrow();
        stopMusic();
        dialogue.hide();

        // Blur the entire playfield (background + doors + HUD) for a deep game‑over feel.
        getBlurTarget().setEffect(new GaussianBlur(18));

        Region dim = new Region();
        dim.setStyle("-fx-background-color: rgba(0,0,0,0.65);");

        Label msg = new Label("You have been buried beneath the ruins.\nGame Over.");
        msg.setTextFill(Color.web("#FFB0B0"));
        msg.setFont(AssetLoader.loadFont("/fonts/CinzelDecorative-Bold.ttf", 40));
        msg.setAlignment(Pos.CENTER);
        msg.setTextAlignment(TextAlignment.CENTER);
        msg.setWrapText(true);
        msg.setMaxWidth(900);
        msg.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.85), 18, 0.6, 0, 2);");

        Button retry = UiUtil.btn("RETRY");
        Button back = UiUtil.btn("Back to Level Select");

        HBox buttons = new HBox(14, retry, back);
        buttons.setAlignment(Pos.CENTER);

        VBox box = new VBox(18, msg, buttons);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(30));
        box.setStyle("-fx-background-color: rgba(20,16,10,0.38); -fx-background-radius: 16;");

        StackPane over = new StackPane(dim, box);
        retry.setOnAction(e -> {
            getStatusLayer().getChildren().remove(over);
            getBlurTarget().setEffect(null);
            restartThisLevel();
        });
        back.setOnAction(e -> {
            getStatusLayer().getChildren().remove(over);
            goToLevelSelect();
        });

        addToStatusLayer(over);
        StackPane.setAlignment(over, Pos.CENTER);
    }

    // ---------- Learnings ----------

    private void showLearnings() {
        var imgs = learnSnaps.all();
        List<LearningSnap> snaps = new ArrayList<>(4);

        if (imgs.size() > 0 && imgs.get(0) != null) {
            snaps.add(new LearningSnap(
                    imgs.get(0),
                    "Binary Search uses a midpoint formula and a condition to narrow the search. Midpoint = (low + high) / 2. If the target is smaller than the midpoint, search left; if larger, search right. You cut the dungeon in half with each choice until you escape."
            ));
        }
        if (imgs.size() > 1 && imgs.get(1) != null) {
            snaps.add(new LearningSnap(
                    imgs.get(1),
                    "In the first search here, the corridor has 15 doors. Using the formula: midpoint = (low + high) / 2 = (1 + 15) / 2 = 8. So the middle door is 8. We compare it to the target: if the target is smaller than 8, we go left; if larger, we go right."
            ));
        }
        if (imgs.size() > 2 && imgs.get(2) != null) {
            snaps.add(new LearningSnap(
                    imgs.get(2),
                    "After a correct choice, the right half vanishes so the search space reduces. The search space shrinks each time. Binary Search narrows down the range until only the target remains."
            ));
        }
        if (imgs.size() > 3 && imgs.get(3) != null) {
            snaps.add(new LearningSnap(
                    imgs.get(3),
                    "You found the target in just a few steps. Binary Search is faster than Linear Search: it finds the answer in log₂(N) steps instead of N steps. That is the power of divide and conquer you used in this dungeon."
            ));
        }

        if (snaps.isEmpty()) {
            snaps.add(LearningSnap.textOnly(
                    "Binary Search halves the search space each step. Use the scroll for your target, check the midpoint, then narrow down the corridor until you escape. Fewer steps mean fewer chances to lose hearts."
            ));
        }

        snaps.add(LearningSnap.textOnly(
                "• The number is found: you saw this in the dungeon when you reached the target door and escaped.\n\n" +
                        "• The number is not found: if we keep halving the range until there are no doors left (the range becomes empty), the target is not in the array; we report \"not found\" and stop.\n\n" +
                        "• Binary Search stops when either we find the target or the search range becomes empty (no more doors to check); no further steps are needed."
        ));

        LearningsBoard board = new LearningsBoard(
                getBlurTarget(),
                AssetLoader.image(AssetLoader.LEARNING_BOARD),
                snaps,
                () -> {
                    stopMusic();
                    learnSnaps.clear();
                    goToLevelSelectAfterCompletion(2);
                },
                true
        );

        addToStatusLayer(board);
        board.show();
    }
}
