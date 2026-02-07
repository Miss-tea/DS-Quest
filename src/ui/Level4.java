package ui;

import core.AssetLoader;
import core.LevelConfig;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.Cursor;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.media.AudioClip;
import javafx.scene.shape.Line;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Level 4 – Chain of Memory (Linked List).
 *
 * Screen 1: intro on "first screen of linked list.jpeg"; dialogue in box, then vanishes.
 * Screen 2: traversal on link2.jpg; instruction paper overlay (no dialogue).
 * Player moves with Arrow Keys (LEFT/RIGHT/UP/DOWN), crystal balls scattered; click animates into list (HEAD → … → NULL).
 * Insertion at HEAD / Position / End with separate instruction paper headings; deletion with D.
 */
public class Level4 extends BaseLevel {

    private static final int SCORE_INSERT = 20;
    private static final int SCORE_DELETE = 15;
    private static final int SCORE_CONNECTIONS = 10;
    private static final int WRONG_TRAVERSAL_PENALTY = 10;
    private static final int MISSING_CONNECTIONS_PENALTY = 5;
    private static final int MAX_SCORE_LEVEL4 = SCORE_INSERT * 3 + SCORE_CONNECTIONS + SCORE_DELETE;

    private static final double PAPER_W = 240;
    private static final double PAPER_H = 320;
    private static final double PAPER_MAX_HEIGHT = 260;
    private static final int INSTRUCTION_STEP_CHARS = 220;
    private PauseTransition instructionPart2Transition;
    private String instructionPart2Pending;

    private final LearningsSnapStore learnSnaps = new LearningsSnapStore();

    private StackPane world;
    private StackPane fxLayer;

    private ScrollPane scroll;
    private Pane track;

    private final List<MemoryNode> nodes = new ArrayList<>();
    private MemoryNode head;
    private MemoryNode current;

    private double sceneW;
    private double sceneH;

    /** Chain and nodes sit lower so instruction paper does not cover last nodes/NULL. */
    private static final double NODE_Y_FRACTION = 0.88;
    private static final double NODE_SPACING = 200.0;

    private boolean didInsertHead;
    private boolean didInsertMiddle;
    private boolean didInsertTail;
    private boolean didDeleteOnce;
    private boolean didDeleteHead;
    private boolean didDeleteMiddle;
    private boolean didDeleteTail;
    private boolean traversalActive;
    private boolean victoryShown;

    /** true = only traversal (move + click to build chain); false = insertion/deletion enabled */
    private boolean traversalPhase = true;

    /** Chain built above: HEAD → … → NULL */
    private HBox chainAboveStrip;
    private final List<String> chainAboveLabels = new ArrayList<>();

    private StackPane victoryOverlay;

    /** Instruction paper overlay (replaces dialogue during gameplay) */
    private VBox instructionPaper;
    private Label instructionTitle;
    private Label instructionChainLabel;
    private Label instructionBody;
    private Label instructionSelection;

    /** Random collection order during traversal; built in onTraversalComplete. */
    private List<MemoryNode> collectionOrder = new ArrayList<>();
    private int collectionIndex = 0;
    private Label nullLabel;
    private Label headLabel;
    /** Line from last node to NULL label (cleared and redrawn in rebuildPointersForInsertion). */
    private Line tailToNullLine;
    /** Line from HEAD label to first node. */
    private Line headToFirstLine;
    /** Decoy nodes (scattered, not to be collected). */
    private final List<MemoryNode> decoyNodes = new ArrayList<>();

    /** Highest node index visited in order (for sequential traversal to B). Only enforced when didInsertHead && !didInsertMiddle. */
    private int maxVisitedIndex = 0;

    /** True after middle insert; triggers wizard transition to deletion phase. */
    private boolean deletionPhaseActive = false;

    /** Connection mode after middle insert: user must click in order (prev→new, new→next). */
    private boolean connectionMode = false;
    private MemoryNode connectionPrev, connectionNew, connectionNext;
    private boolean connection1Done = false;
    private boolean connection2Done = false;
    private MemoryNode connectionFromNode = null;
    private Timeline connectionTimeoutTimeline;

    /** State machine so user must click in exact order; wizard gives feedback. */
    private enum ConnectionState { WAITING_FIRST_FROM, WAITING_FIRST_TO, WAITING_SECOND_FROM, WAITING_SECOND_TO, COMPLETE }
    private ConnectionState connectionState = ConnectionState.WAITING_FIRST_FROM;

    private boolean deletionReconnectFirstClicked = false;

    /** 3-phase deletion: DISCONNECT (break link) -> RECONNECT (user clicks) -> DELETE (remove node). */
    private enum DeletionPhase { NONE, DISCONNECT, RECONNECT, FINALIZE }
    private DeletionPhase currentDeletionPhase = DeletionPhase.NONE;
    private MemoryNode deletionTarget = null;
    private MemoryNode deletionPrev = null;
    private MemoryNode deletionNext = null;

    /** Prevents multiple I presses during an insertion. */
    private boolean insertionInProgress = false;

    /** Two wrong actions in a row (or in same phase) deduct 1 heart. */
    private int wrongActionCount = 0;

    private static final double MUSIC_VOLUME = 0.28;
    //private static final String INTRO_MUSIC_PATH = "/Music_binarysearch/dark-ambient-soundscape-dreamscape-462864.mp3";
    private static final String INTRO_MUSIC_PATH = AssetLoader.L4_MUSIC_INTRO; // <--------------Changed line
    private AudioClip musicClip;
    private AudioClip introMusicPreloaded;

    @Override
    protected String getLevelTitle() {
        return "LEVEL 4: THE CHAIN OF MEMORY";
    }

    @Override
    protected String getBackgroundPath() {
        // Intro artwork
        //return "/first screen of linked list.jpeg";
        return AssetLoader.L4_BG_INTRO; // <--------------Changed line
    }

    @Override
    protected void initLevel(StackPane worldLayer, double w, double h) {
        sceneW = w;
        sceneH = h;
        stopMusic();
        preloadIntroMusic();

        world = new StackPane();
        world.setPickOnBounds(false);
        world.setPrefSize(w, h);

        fxLayer = new StackPane();
        fxLayer.setPickOnBounds(false);

        worldLayer.getChildren().addAll(world, fxLayer);

        // First show level name and number splash, then intro dialogue
        Platform.runLater(this::showLevelSplash);
    }

    // ---------- Level splash (name + number at start) ----------

    private void showLevelSplash() {
        Label title = new Label("LEVEL 4 – The Chain of Memory");
        title.setTextFill(Color.web("#F8E7C0"));
        title.setFont(AssetLoader.loadFont("/fonts/CinzelDecorative-Bold.ttf", 44));
        title.setTextAlignment(TextAlignment.CENTER);
        title.setAlignment(Pos.CENTER);
        title.setWrapText(true);
        title.setMaxWidth(900);
        title.setStyle("-fx-background-color: transparent; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.9), 20, 0.8, 0, 3);");

        VBox titleBox = new VBox(title);
        titleBox.setAlignment(Pos.CENTER);
        titleBox.setPrefSize(sceneW, sceneH);
        titleBox.setStyle("-fx-background-color: transparent;");

        StackPane splash = new StackPane(titleBox);
        splash.setPickOnBounds(false);
        splash.setStyle("-fx-background-color: transparent;");
        fxLayer.getChildren().add(splash);
        StackPane.setAlignment(titleBox, Pos.CENTER);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(500), splash);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);

        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(500), splash);
        scaleIn.setFromX(0.85);
        scaleIn.setFromY(0.85);
        scaleIn.setToX(1.0);
        scaleIn.setToY(1.0);

        PauseTransition hold = new PauseTransition(Duration.seconds(3));

        FadeTransition fadeOut = new FadeTransition(Duration.millis(600), splash);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);

        ScaleTransition scaleOut = new ScaleTransition(Duration.millis(600), splash);
        scaleOut.setFromX(1.0);
        scaleOut.setFromY(1.0);
        scaleOut.setToX(1.15);
        scaleOut.setToY(1.15);

        fadeOut.setOnFinished(e -> {
            fxLayer.getChildren().remove(splash);
            showIntro();
        });

        ParallelTransition in = new ParallelTransition(fadeIn, scaleIn);
        ParallelTransition out = new ParallelTransition(fadeOut, scaleOut);
        new SequentialTransition(in, hold, out).playFromStart();
    }

    // ---------- Intro (two parts so dialogue fits; reader has time) ----------

    private void showIntro() {
        playLoop(INTRO_MUSIC_PATH);
        Button next = UiUtil.btn("Next");
        next.setOnAction(e -> showIntroPart2());

        say("You have escaped the Binary Search Dungeon... but your journey is far from over.\nWelcome to the Memory Crypt, seeker.\nHere knowledge is chained... link by link.", null, next);
    }

    private void showIntroPart2() {
        Button begin = UiUtil.btn("Begin Traversal");
        begin.setOnAction(e -> startTransition());

        say("Each crystal holds a fragment of memory... connected to the next, until the chain ends at NULL.\nTo master this crypt you must traverse, insert, and break links.\nThe Chain of Memory awaits...", null, begin);
    }

    private void startTransition() {
        dialogue.hide();  // Dialogue box vanishes after instruction; gameplay uses instruction paper only

        try {
            //ImageView mist = AssetLoader.imageView("/scene_transition_mist.png", sceneW, sceneH, false);
            ImageView mist = AssetLoader.imageView(AssetLoader.L4_TRANSITION_MIST, sceneW, sceneH, false); // <--------------Changed line
            mist.setOpacity(0.0);
            fxLayer.getChildren().add(mist);
            StackPane.setAlignment(mist, Pos.CENTER);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(500), mist);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);

            FadeTransition fadeOut = new FadeTransition(Duration.millis(500), mist);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(e -> fxLayer.getChildren().remove(mist));

            fadeIn.setOnFinished(e -> {
                buildCorridorAndShow();
                fadeOut.playFromStart();
            });
            fadeIn.playFromStart();
        } catch (Exception ex) {
            // If mist image fails to load, fall back to a direct corridor build
            buildCorridorAndShow();
        }
    }

    private void buildCorridorAndShow() {
        try {
            buildCorridor();
        } catch (Exception e) {
            e.printStackTrace();
            buildCorridorFallback();
        }
        setCorridorBackground();
        //playLoop("/Music_binarysearch/darkness-approaching-cinematic-danger-407228.mp3");
        playLoop(AssetLoader.L4_MUSIC_CORRIDOR); // <--------------Changed line
    }

    /** Use corridor background (A high-quality medie2) on second screen. */
    private void setCorridorBackground() {
        if (backgroundView == null) return;
        try {
            //backgroundView.setImage(AssetLoader.image("/A high-quality medie2.png"));
            backgroundView.setImage(AssetLoader.image(AssetLoader.L4_CORRIDOR_BG_1)); // <--------------Changed line
            backgroundView.setPreserveRatio(false);
        } catch (Exception e) {
            try {
                //backgroundView.setImage(AssetLoader.image("/A high-quality medie.png"));
                backgroundView.setImage(AssetLoader.image(AssetLoader.L4_CORRIDOR_BG_2)); // <--------------Changed line
            } catch (Exception e2) {
                try {
                    //backgroundView.setImage(AssetLoader.image("/link2.jpg"));
                    backgroundView.setImage(AssetLoader.image(AssetLoader.L4_CORRIDOR_BG_FALLBACK)); // <--------------Changed line
                } catch (Exception e3) { /* keep current */ }
            }
        }
    }

    // ---------- Corridor + list ----------

    private void buildCorridor() {
        track = new Pane();
        double trackW = sceneW * 2.5;
        double trackH = sceneH * 1.6;
        track.setMinSize(trackW, trackH);
        track.setPrefSize(trackW, trackH);

        try {
            //ImageView bg = AssetLoader.imageView("/A high-quality medie2.png", trackW, trackH, false);
            ImageView bg = AssetLoader.imageView(AssetLoader.L4_CORRIDOR_BG_1, trackW, trackH, false); // <--------------Changed line
            bg.setPreserveRatio(false);
            track.getChildren().add(bg);
        } catch (Exception e) {
            try {
                //ImageView bg2 = AssetLoader.imageView("/A high-quality medie.png", trackW, trackH, false);
                ImageView bg2 = AssetLoader.imageView(AssetLoader.L4_CORRIDOR_BG_2, trackW, trackH, false); // <--------------Changed line
                bg2.setPreserveRatio(false);
                track.getChildren().add(bg2);
            } catch (Exception e2) {
                track.getChildren().add(new Rectangle(trackW, trackH, Color.web("#1a1a2e")));
            }
        }

        scroll = new ScrollPane(track);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPannable(true);
        scroll.setFitToHeight(true);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportWidth(sceneW);
        scroll.setPrefViewportHeight(sceneH);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        buildInitialList();
        layoutNodes();
        installNodeClicks();
        buildChainAboveStrip();
        buildInstructionPaper();

        VBox topLevel = new VBox();
        topLevel.getChildren().addAll(chainAboveStrip, scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        VBox.setMargin(chainAboveStrip, new Insets(72, 0, 0, 0));

        StackPane worldContent = new StackPane();
        worldContent.getChildren().add(topLevel);
        worldContent.getChildren().add(instructionPaper);
        StackPane.setAlignment(instructionPaper, Pos.TOP_RIGHT);
        StackPane.setMargin(instructionPaper, new Insets(70, 14, 0, 0));
        world.getChildren().setAll(worldContent);

        traversalActive = true;
        traversalPhase = true;
        chainAboveLabels.clear();

        showInstructionPaper(
                "Traversal",
                "Find exactly 4 glowing crystal balls. Click each to collect. Each node animates upward and links into the list above. After 4 nodes: chain built!"
        );

        Platform.runLater(() -> installKeyHandler());
    }

    private void installKeyHandler() {
        if (root.getScene() == null) return;
        root.getScene().setOnKeyPressed(e -> {
            if (!traversalActive || victoryShown) return;
            if (e.getCode() == KeyCode.UP || e.getCode() == KeyCode.DOWN || e.getCode() == KeyCode.LEFT || e.getCode() == KeyCode.RIGHT) {
                if (traversalPhase) {
                    int dx = e.getCode() == KeyCode.RIGHT ? 1 : (e.getCode() == KeyCode.LEFT ? -1 : 0);
                    int dy = e.getCode() == KeyCode.DOWN ? 1 : (e.getCode() == KeyCode.UP ? -1 : 0);
                    panView(dx, dy);
                } else {
                    if (e.getCode() == KeyCode.RIGHT) moveNext();
                    else if (e.getCode() == KeyCode.LEFT) movePrev();
                    else {
                        int dy = e.getCode() == KeyCode.DOWN ? 1 : (e.getCode() == KeyCode.UP ? -1 : 0);
                        panView(0, dy);
                    }
                }
            } else if (!traversalPhase && !connectionMode) {
                if (e.getCode() == KeyCode.I) doInsert();
                else if (e.getCode() == KeyCode.D) doDeleteNext();
                else if (e.getCode() == KeyCode.ENTER) updateSelectionFeedback();
            }
        });
    }

    private void buildCorridorFallback() {
        track = new Pane();
        double trackW = sceneW * 2.5;
        double trackH = sceneH * 1.6;
        track.setMinSize(trackW, trackH);
        track.setPrefSize(trackW, trackH);
        track.getChildren().add(new Rectangle(trackW, trackH, Color.web("#1a1a2e")));

        scroll = new ScrollPane(track);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPannable(true);
        scroll.setFitToHeight(true);
        scroll.setPrefViewportWidth(sceneW);
        scroll.setPrefViewportHeight(sceneH);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        buildInitialList();
        layoutNodes();
        installNodeClicks();
        buildChainAboveStrip();
        buildInstructionPaper();

        VBox topLevel = new VBox();
        topLevel.getChildren().addAll(chainAboveStrip, scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        VBox.setMargin(chainAboveStrip, new Insets(72, 0, 0, 0));
        StackPane worldContent = new StackPane();
        worldContent.getChildren().add(topLevel);
        worldContent.getChildren().add(instructionPaper);
        StackPane.setAlignment(instructionPaper, Pos.TOP_RIGHT);
        StackPane.setMargin(instructionPaper, new Insets(70, 14, 0, 0));
        world.getChildren().setAll(worldContent);

        traversalActive = true;
        traversalPhase = true;
        chainAboveLabels.clear();
        showInstructionPaper("Traversal", "Find exactly 4 glowing crystal balls. Click each to collect. Each node animates upward and links into the list above. After 4 nodes: chain built!");

        Platform.runLater(() -> installKeyHandler());
    }

    private void buildInstructionPaper() {
        ImageView parchmentBg;
        try {
            parchmentBg = new ImageView(AssetLoader.image(AssetLoader.INSTRUCTION_PAPER));
        } catch (Exception e) {
            parchmentBg = new ImageView();
            parchmentBg.setOpacity(0.9);
        }
        parchmentBg.setFitWidth(PAPER_W);
        parchmentBg.setFitHeight(PAPER_H);
        parchmentBg.setPreserveRatio(false);
        parchmentBg.setSmooth(true);
        parchmentBg.setMouseTransparent(true);

        instructionTitle = new Label("Instructions");
        instructionTitle.setTextFill(Color.web("#2c2416"));
        instructionTitle.setStyle("-fx-text-fill: #2c2416;");
        instructionTitle.setFont(AssetLoader.loadFont("/fonts/CinzelDecorative-Bold.ttf", 18));
        instructionTitle.setWrapText(true);
        instructionTitle.setMaxWidth(PAPER_W - 50);
        instructionTitle.setTextAlignment(TextAlignment.CENTER);
        instructionTitle.setAlignment(Pos.CENTER);

        instructionChainLabel = new Label("");
        instructionChainLabel.setTextFill(Color.web("#2c2416"));
        instructionChainLabel.setStyle("-fx-text-fill: #2c2416;");
        instructionChainLabel.setFont(AssetLoader.loadFont("/fonts/Montaga-Regular.ttf", 13));
        instructionChainLabel.setWrapText(true);
        instructionChainLabel.setMaxWidth(PAPER_W - 50);
        instructionChainLabel.setTextAlignment(TextAlignment.CENTER);
        instructionChainLabel.setAlignment(Pos.CENTER);

        instructionBody = new Label("");
        instructionBody.setTextFill(Color.web("#2c2416"));
        instructionBody.setStyle("-fx-text-fill: #2c2416;");
        instructionBody.setFont(AssetLoader.loadFont("/fonts/Montaga-Regular.ttf", 14));
        instructionBody.setWrapText(true);
        instructionBody.setMaxWidth(PAPER_W - 50);
        instructionBody.setLineSpacing(4);
        instructionBody.setTextAlignment(TextAlignment.LEFT);
        instructionBody.setAlignment(Pos.TOP_LEFT);

        instructionSelection = new Label("");
        instructionSelection.setTextFill(Color.web("#2c2416"));
        instructionSelection.setStyle("-fx-text-fill: #2c2416;");
        instructionSelection.setFont(AssetLoader.loadFont("/fonts/CinzelDecorative-Regular.ttf", 12));
        instructionSelection.setWrapText(true);
        instructionSelection.setMaxWidth(PAPER_W - 50);
        instructionSelection.setTextAlignment(TextAlignment.CENTER);
        instructionSelection.setAlignment(Pos.CENTER);

        VBox content = new VBox(8, instructionTitle, instructionChainLabel, instructionBody, instructionSelection);
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(25, 25, 25, 25));
        content.setStyle("-fx-background-color: transparent;");
        content.setMaxWidth(PAPER_W - 20);

        ScrollPane scrollContent = new ScrollPane(content);
        scrollContent.setFitToWidth(true);
        scrollContent.setFitToHeight(false);
        scrollContent.setPrefViewportHeight(PAPER_MAX_HEIGHT);
        scrollContent.setMinViewportHeight(PAPER_MAX_HEIGHT);
        scrollContent.setMaxHeight(PAPER_H);
        scrollContent.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollContent.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollContent.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollContent.setPannable(false);
        content.setMinHeight(Region.USE_PREF_SIZE);

        StackPane paperStack = new StackPane();
        paperStack.setPrefSize(PAPER_W, PAPER_H);
        paperStack.getChildren().addAll(parchmentBg, scrollContent);
        StackPane.setAlignment(parchmentBg, Pos.CENTER);
        StackPane.setAlignment(scrollContent, Pos.CENTER);

        instructionPaper = new VBox(paperStack);
        instructionPaper.setPrefWidth(PAPER_W);
        instructionPaper.setMaxWidth(PAPER_W);
        instructionPaper.setMaxHeight(PAPER_H);
        instructionPaper.setPickOnBounds(true);
        instructionPaper.setMouseTransparent(false);
    }

    private String buildCurrentChainString() {
        if (nodes.isEmpty()) return "The chain is empty.";
        StringBuilder sb = new StringBuilder("Chain: HEAD");
        for (MemoryNode n : nodes) {
            sb.append(" → ").append(n.getData());
        }
        sb.append(" → NULL");
        return sb.toString();
    }

    private void showInstructionPaper(String title, String body) {
        if (instructionPart2Transition != null) {
            instructionPart2Transition.stop();
            instructionPart2Transition = null;
        }
        instructionPart2Pending = null;
        if (instructionTitle != null) instructionTitle.setText(title);
        if (instructionChainLabel != null) instructionChainLabel.setText(buildCurrentChainString());
        if (instructionBody != null) {
            String cleanBody = body != null ? body.replace("CURRENT CHAIN:", "").trim() : "";
            if (cleanBody.length() > INSTRUCTION_STEP_CHARS) {
                int split = cleanBody.lastIndexOf("\n\n", INSTRUCTION_STEP_CHARS);
                if (split <= 0) split = cleanBody.indexOf(" ", INSTRUCTION_STEP_CHARS);
                if (split <= 0) split = INSTRUCTION_STEP_CHARS;
                String part1 = cleanBody.substring(0, split).trim();
                instructionPart2Pending = cleanBody.substring(split).trim();
                instructionBody.setText(part1 + "\n\n(More in a moment...)");
                instructionPart2Transition = new PauseTransition(Duration.seconds(5));
                instructionPart2Transition.setOnFinished(e -> {
                    if (instructionBody != null && instructionPart2Pending != null) {
                        instructionBody.setText(instructionPart2Pending);
                        instructionPart2Pending = null;
                    }
                    instructionPart2Transition = null;
                });
                instructionPart2Transition.play();
            } else {
                instructionBody.setText(cleanBody);
            }
        }
        updateSelectionFeedback();
        if (instructionPaper != null) {
            instructionPaper.setVisible(true);
            instructionPaper.setOpacity(0);
            FadeTransition ft = new FadeTransition(Duration.millis(500), instructionPaper);
            ft.setToValue(1.0);
            ft.play();
        }
    }

    /** Pan camera with arrow keys so the screen moves like a real game. */
    private void panView(int dx, int dy) {
        if (scroll == null || track == null) return;
        double step = 0.12;
        double newH = scroll.getHvalue() + dx * step;
        double newV = scroll.getVvalue() + dy * step;
        scroll.setHvalue(Math.max(0, Math.min(1, newH)));
        scroll.setVvalue(Math.max(0, Math.min(1, newV)));
    }

    private void buildChainAboveStrip() {
        Label chainTitle = new Label("Chain: ");
        chainTitle.setTextFill(Color.web("#F8E7C0"));
        chainTitle.setFont(AssetLoader.loadFont("/fonts/CinzelDecorative-Bold.ttf", 22));
        chainAboveStrip = new HBox(12);
        chainAboveStrip.setAlignment(Pos.CENTER_LEFT);
        chainAboveStrip.setPadding(new Insets(14, 28, 14, 28));
        chainAboveStrip.setStyle("-fx-background-color: rgba(0,0,0,0.55); -fx-background-radius: 10;");
        chainAboveStrip.getChildren().add(chainTitle);
        updateChainAboveVisual();
    }

    /** Rebuild the "list above" and nodes list from actual list (head → … → null). Call after every insert/delete. */
    private void syncChainAboveFromList() {
        chainAboveLabels.clear();
        nodes.clear();
        MemoryNode p = head;
        while (p != null) {
            chainAboveLabels.add(p.getData());
            nodes.add(p);
            p = p.next;
        }
        updateChainAboveVisual();
    }

    private void updateChainAboveVisual() {
        while (chainAboveStrip.getChildren().size() > 1) {
            chainAboveStrip.getChildren().remove(1);
        }
        Font nodeFont = AssetLoader.loadFont("/fonts/CinzelDecorative-Regular.ttf", 22);
        if (!chainAboveLabels.isEmpty()) {
            Label headL = new Label("HEAD");
            headL.setTextFill(Color.web("#88aaff"));
            headL.setFont(nodeFont);
            headL.setTooltip(new Tooltip("HEAD points to first node"));
            chainAboveStrip.getChildren().add(headL);
            Label arrow0 = new Label(" → ");
            arrow0.setTextFill(Color.web("#aaa"));
            arrow0.setFont(nodeFont);
            chainAboveStrip.getChildren().add(arrow0);
        }
        for (int i = 0; i < chainAboveLabels.size(); i++) {
            Label n = new Label(chainAboveLabels.get(i));
            n.setTextFill(Color.web("#ffd27f"));
            n.setFont(nodeFont);
            chainAboveStrip.getChildren().add(n);
            if (i < chainAboveLabels.size() - 1) {
                Label arrow = new Label(" → ");
                arrow.setTextFill(Color.web("#aaa"));
                arrow.setFont(nodeFont);
                chainAboveStrip.getChildren().add(arrow);
            }
        }
        if (!chainAboveLabels.isEmpty()) {
            Label arrow = new Label(" → ");
            arrow.setTextFill(Color.web("#aaa"));
            arrow.setFont(nodeFont);
            chainAboveStrip.getChildren().add(arrow);
            Label nullL = new Label("NULL");
            nullL.setTextFill(Color.web("#888"));
            nullL.setFont(nodeFont);
            chainAboveStrip.getChildren().add(nullL);
        }
    }

    private void installNodeClicks() {
        List<MemoryNode> clickables = new ArrayList<>();
        if (!nodes.isEmpty()) clickables.addAll(nodes);
        else if (!collectionOrder.isEmpty()) clickables.addAll(collectionOrder);
        clickables.addAll(decoyNodes);
        for (MemoryNode n : clickables) {
            attachNodeClickHandler(n);
        }
    }

    /** Attach the standard click handler so the node can be selected (and used in traversal/connection/reconnect). Call when adding new nodes. */
    private void attachNodeClickHandler(MemoryNode n) {
        n.setPickOnBounds(true);
        n.setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            if (traversalPhase) {
                if (current == null || n != current) return;
                addCurrentToChainAbove();
            } else {
                if (currentDeletionPhase == DeletionPhase.RECONNECT && deletionPrev != null
                        && (n == deletionPrev || (deletionNext != null && n == deletionNext))) {
                    onThreePhaseReconnectClick(n);
                    return;
                }
                if (connectionMode) {
                    if (n == connectionPrev || n == connectionNew || n == connectionNext) {
                        onConnectionClick(n);
                    }
                } else if (nodes.contains(n) && trySelectByTraversal(n)) {
                    centerOn(n);
                }
            }
        });
    }

    private void addCurrentToChainAbove() {
        if (current == null) return;
        MemoryNode nodeToAdd = current;
        nodeToAdd.setCurrent(false);
        String data = nodeToAdd.getData();
        Bounds nodeBounds = nodeToAdd.localToScene(nodeToAdd.getBoundsInLocal());
        double startX = nodeBounds.getMinX() + nodeBounds.getWidth() / 2.0;
        double startY = nodeBounds.getMinY() + nodeBounds.getHeight() / 2.0;

        chainAboveLabels.add(data);
        updateChainAboveVisual();

        Platform.runLater(() -> runFlyUpAnimation(nodeToAdd, data, startX, startY));
    }

    private void runFlyUpAnimation(MemoryNode fromNode, String data, double startSceneX, double startSceneY) {
        javafx.scene.Node targetInStrip = null;
        int idx = 1 + 2 * (chainAboveLabels.size() - 1);
        if (idx >= 1 && idx < chainAboveStrip.getChildren().size()) {
            targetInStrip = chainAboveStrip.getChildren().get(idx);
        }
        if (targetInStrip == null) {
            finishAddToChain();
            return;
        }
        Bounds targetBounds = targetInStrip.localToScene(targetInStrip.getBoundsInLocal());
        double endX = targetBounds.getMinX() + targetBounds.getWidth() / 2.0;
        double endY = targetBounds.getMinY() + targetBounds.getHeight() / 2.0;

        ImageView orb = loadOrbImage(48);
        orb.setMouseTransparent(true);
        Point2D startLocal = fxLayer.sceneToLocal(startSceneX, startSceneY);
        orb.setTranslateX(startLocal.getX() - 24);
        orb.setTranslateY(startLocal.getY() - 24);
        fxLayer.getChildren().add(orb);

        Point2D endLocal = fxLayer.sceneToLocal(endX, endY);
        double byX = endLocal.getX() - 24 - orb.getTranslateX();
        double byY = endLocal.getY() - 24 - orb.getTranslateY();

        TranslateTransition fly = new TranslateTransition(Duration.millis(520), orb);
        fly.setByX(byX);
        fly.setByY(byY);
        fly.setOnFinished(e -> {
            fxLayer.getChildren().remove(orb);
            finishAddToChain();
        });
        fly.play();
    }

    private static ImageView loadOrbImage(double size) {
        try {
            //return AssetLoader.imageView("/A dim mystical orb a.png", size, size, true);
            return AssetLoader.imageView(AssetLoader.L4_ORB_DIM, size, size, true); // <--------------Changed li
        } catch (Exception e) {
            try {
                //return AssetLoader.imageView("/A mystical crystal H.png", size, size, true);
                return AssetLoader.imageView(AssetLoader.L4_ORB_ALT, size, size, true); // <--------------Changed line
            } catch (Exception e2) {
                //return AssetLoader.imageView("/A glowing memory fra.png", size, size, true);
                return AssetLoader.imageView(AssetLoader.L4_ORB_ALT2, size, size, true); // <--------------Changed line
            }
        }
    }

    private void finishAddToChain() {
        getGameState().addScore(5);
        collectionIndex++;
        if (collectionIndex < collectionOrder.size()) {
            current = collectionOrder.get(collectionIndex);
            setCurrent(current);
        } else {
            current = null;
            setCurrent(null);
            onTraversalComplete();
        }
    }

    private void onTraversalComplete() {
        //playLoop("/Music_level4/syouki_takahashi-midnight-forest-184304.mp3");
        playLoop(AssetLoader.L4_MUSIC_TRAVERSAL); // <--------------Changed line
        traversalPhase = false;
        for (MemoryNode n : collectionOrder) n.setCurrent(false);
        head = collectionOrder.get(0);
        for (int i = 0; i < collectionOrder.size() - 1; i++)
            collectionOrder.get(i).next = collectionOrder.get(i + 1);
        collectionOrder.get(collectionOrder.size() - 1).next = null;
        nodes.clear();
        nodes.addAll(collectionOrder);
        track.getChildren().removeAll(decoyNodes);
        if (headLabel != null && !track.getChildren().contains(headLabel)) track.getChildren().add(1, headLabel);
        track.getChildren().add(nullLabel);
        layoutNodesForInsertion();
        rebuildPointersForInsertion();
        if (nullLabel != null) positionNull(nullLabel);
        if (headLabel != null) positionHead(headLabel);
        current = head;
        maxVisitedIndex = 0;
        setCurrent(head);
        centerOn(head);
        updateInsertionPhasePaper();
        Platform.runLater(() -> SnapshotUtil.captureNode(getBlurTarget(), 2.0, Color.TRANSPARENT, img -> {
            learnSnaps.clear();
            learnSnaps.add(img);
        }));
        Button okBtn = UiUtil.btn("OK");
        okBtn.setOnAction(e -> dialogue.hide());
        say("The chain sings... four crystals bound as one.\nNow learn the art of insertion.", null, okBtn);
    }

    private void showInsertHeadInstruction() {
        showInstructionPaper(
                "Insertion at HEAD",
                "Place a new crystal before the first one. The new crystal becomes HEAD.\n\nMove to the first crystal (HEAD), then press I to insert."
        );
    }

    private void showInsertMiddleInstruction() {
        showInstructionPaper(
                "Insertion at Position",
                "Traverse to the target crystal, then insert after it. The chain must reconnect properly.\n\nMove to the second crystal (B), then press I to insert after it."
        );
    }

    private void showInsertTailInstruction() {
        showInstructionPaper(
                "Insertion at End",
                "Traverse to the last crystal before NULL, then insert the new crystal.\n\nMove to the last crystal (before NULL), then press I to insert at the tail."
        );
    }

    private void showDeleteInstruction() {
        showInstructionPaper(
                "Deletion",
                "Now you must learn deletion. Traverse to the crystal before the one to remove, then delete the next crystal.\n\nPress D to delete the next crystal after the current one."
        );
    }

    private void scheduleWizardAndDeletionPhase() {
        Button nextBtn = UiUtil.btn("Continue to Deletion");
        nextBtn.setOnAction(e -> {
            dialogue.hide();
            startDeletionPhaseClean();
        });
        Platform.runLater(() -> SnapshotUtil.captureNode(getBlurTarget(), 2.0, Color.TRANSPARENT, learnSnaps::add));
        say("Insertion mastered... the chain bends to your will.\nNow you shall learn to BREAK links.", null, nextBtn);
    }

    /** Remove any separate white overlay that shows all deletion types at once. */
    private void removeWhiteDeletionOverlay() {
        if (fxLayer == null) return;
        fxLayer.getChildren().removeIf(node -> {
            if (node instanceof Label) {
                String t = ((Label) node).getText();
                return t != null && t.contains("DELETION IN LINKED LIST");
            }
            if (node instanceof Pane) {
                for (javafx.scene.Node child : ((Pane) node).getChildren()) {
                    if (child instanceof Label) {
                        String t = ((Label) child).getText();
                        if (t != null && t.contains("DELETION IN LINKED LIST")) return true;
                    }
                }
            }
            return false;
        });
    }

    /** Enter deletion phase with no overlays; show only wizard + instruction paper. */
    private void startDeletionPhaseClean() {
        //playLoop("/Music_level4/syouki_takahashi-midnight-forest-184304.mp3");
        playOnce(AssetLoader.L4_MUSIC_TRAVERSAL); // <--------------Changed line
        removeWhiteDeletionOverlay();
        deletionPhaseActive = true;
        showDeletionExplanationWizard();
    }

    /** Show only HEAD deletion first; full steps on instruction paper, short dialogue with button. */
    private void showDeletionExplanationWizard() {
        showInstructionPaper("DELETE FROM HEAD",
                "Steps:\n" +
                        "1. Traverse to first node (HEAD)\n" +
                        "2. Store node in temp variable\n" +
                        "3. Move HEAD pointer to next node\n" +
                        "4. Free memory of deleted node\n\n" +
                        "Click Start Head Deletion when ready.");
        Button startHeadBtn = UiUtil.btn("Start Head Deletion");
        startHeadBtn.setOnAction(e -> {
            dialogue.hide();
            showHeadDeletionInstructions();
        });
        say("First... sever the HEAD.\nThe first crystal vanishes... the chain begins anew.\nRead the steps on the parchment.", null, startHeadBtn);
    }

    private void showHeadDeletionInstructions() {
        for (MemoryNode n : nodes) n.setCurrent(false);
        current = head;
        if (head != null) head.setCurrent(true);
        centerOn(head);
        updateDeletionPaper();
    }

    private void showMiddleDeletionInstructions() {
        showInstructionPaper("DELETE FROM MIDDLE",
                "To remove a crystal from the middle:\n\n" +
                        "1. DISCONNECT: Break link to target crystal\n" +
                        "2. RECONNECT: Link predecessor to the next crystal\n" +
                        "3. DELETE: Remove the target crystal\n\n" +
                        "Example: Remove C from A→B→C→D\n" +
                        "• Find the crystal before C (B)\n• Disconnect B→C\n• Reconnect B→D\n• Remove C\n\n" +
                        "Select the crystal BEFORE the one to delete, then press D.");
        Button readyBtn = UiUtil.btn("Ready for Middle Deletion");
        readyBtn.setOnAction(e -> {
            dialogue.hide();
            for (MemoryNode n : nodes) n.setCurrent(false);
            MemoryNode predecessor = nodes.size() >= 2 ? nodes.get(0) : null;
            if (predecessor != null) {
                current = predecessor;
                predecessor.setCurrent(true);
                centerOn(current);
            }
            updateSelectionFeedback();
            updateDeletionPaper();
        });
        say("To break a link in the middle... find the memory that points to your target.\nSever its bond... then forge a new bond to the next crystal.\nThe isolated fragment fades.", null, readyBtn);
    }

    private void showTailDeletionInstructions() {
        showInstructionPaper("DELETE FROM TAIL",
                "Tail deletion (3-phase):\n\n" +
                        "1. DISCONNECT: Break link to tail\n" +
                        "2. RECONNECT: Point predecessor to NULL\n" +
                        "3. DELETE: Remove tail node\n\n" +
                        "Example: Delete D from A→B→C→D→NULL\n" +
                        "• Find predecessor C\n• Disconnect C→D\n• Reconnect C→NULL\n• Delete D\n\n" +
                        "Traverse to second-last node and press D.");
        Button readyBtn = UiUtil.btn("Ready for Tail Deletion");
        readyBtn.setOnAction(e -> {
            dialogue.hide();
            MemoryNode secondLast = findSecondLastNode();
            if (secondLast != null) {
                current = secondLast;
                setCurrent(current);
                centerOn(current);
            }
            updateDeletionPaper();
        });
        say("The final fragment... break its bond to NULL, and watch it fade into nothingness.\nStand on the second-last crystal, then press D.", null, readyBtn);
    }

    private void showDeletionCompleteMessage() {
        victoryShown = true;
        traversalActive = false;
        Platform.runLater(() -> SnapshotUtil.captureNode(getBlurTarget(), 2.0, Color.TRANSPARENT, learnSnaps::add));
        if (instructionPaper != null) instructionPaper.setVisible(false);
        dialogue.hide();
        stopMusic();
        //playOnce("/Music_binarysearch/emotional-cinematic-inspirational-piano-main-10524.mp3");
        playOnce(AssetLoader.L4_MUSIC_VICTORY); // <--------------Changed line
        javafx.scene.Node blurTarget = getBlurTarget();
        if (blurTarget != null) blurTarget.setEffect(new GaussianBlur(12));

        Region dim = new Region();
        dim.setStyle("-fx-background-color: rgba(0,0,0,0.55);");

        Label msg = new Label("VICTORY!\nYou have mastered the Chain of Memory.");
        msg.setTextFill(Color.web("#FFD86B"));
        msg.setFont(AssetLoader.loadFont("/fonts/CinzelDecorative-Bold.ttf", 36));
        msg.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.85), 18, 0.6, 0, 2);");
        msg.setAlignment(Pos.CENTER);
        msg.setWrapText(true);

        Button continueBtn = UiUtil.btn("Continue");
        continueBtn.setOnAction(e -> {
            if (getBlurTarget() != null) getBlurTarget().setEffect(null);
            if (victoryOverlay != null) {
                getStatusLayer().getChildren().remove(victoryOverlay);
                victoryOverlay = null;
            }
            showLevel4VictoryBoard();
        });

        ImageView banner = null;
        try {
            //banner = new ImageView(AssetLoader.image("/victory_banner_medieval.png"));
            banner = new ImageView(AssetLoader.image(AssetLoader.L4_VICTORY_BANNER));//<---------CHANGED LINE
            banner.setPreserveRatio(true);
            banner.setFitWidth(380);
        } catch (Exception ignored) {}
        VBox box = new VBox(14);
        if (banner != null) box.getChildren().add(banner);
        box.getChildren().addAll(msg, continueBtn);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(36));
        box.setStyle("-fx-background-color: rgba(20,16,10,0.35); -fx-background-radius: 16;");

        victoryOverlay = new StackPane(dim, box);
        victoryOverlay.setPickOnBounds(false);
        getStatusLayer().getChildren().add(victoryOverlay);
        StackPane.setAlignment(victoryOverlay, Pos.CENTER);

        FadeTransition ft = new FadeTransition(Duration.millis(320), victoryOverlay);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.playFromStart();
    }

    private void showLevel4VictoryBoard() {
        dialogue.hide();
        try {
            if (backgroundView != null) backgroundView.setImage(AssetLoader.image("/throne_room_victory_ui.jpg"));
        } catch (Exception ignored) {}
        if (backgroundView != null) backgroundView.setEffect(null);
        if (world != null) world.setVisible(false);

        int finalScore = getGameState().getScore();
        showSurvivedThen(
                finalScore,
                "Linked lists are chains of nodes connected by pointers. You mastered traversal, insertion, and deletion.",
                MAX_SCORE_LEVEL4,
                this::showLevel4Learnings
        );
    }

    private void runThreeGentleShakes(Runnable onFinished) {
        javafx.scene.Node target = getBlurTarget();
        if (target == null) {
            if (onFinished != null) onFinished.run();
            return;
        }
        Timeline t = new Timeline(
                new KeyFrame(Duration.millis(0),   new KeyValue(target.translateXProperty(), 6), new KeyValue(target.translateYProperty(), 2)),
                new KeyFrame(Duration.millis(80),  new KeyValue(target.translateXProperty(), -4), new KeyValue(target.translateYProperty(), -2)),
                new KeyFrame(Duration.millis(160), new KeyValue(target.translateXProperty(), 5), new KeyValue(target.translateYProperty(), 1)),
                new KeyFrame(Duration.millis(240), new KeyValue(target.translateXProperty(), -3), new KeyValue(target.translateYProperty(), -1)),
                new KeyFrame(Duration.millis(320), new KeyValue(target.translateXProperty(), 4), new KeyValue(target.translateYProperty(), 1)),
                new KeyFrame(Duration.millis(400), new KeyValue(target.translateXProperty(), 0), new KeyValue(target.translateYProperty(), 0))
        );
        t.setOnFinished(e -> {
            target.setTranslateX(0);
            target.setTranslateY(0);
            if (onFinished != null) onFinished.run();
        });
        t.play();
    }

    private void showDeletionPhaseInstructions() {
        updateDeletionPaper();
    }

    private void updateInsertionPhasePaper() {
        String chain = buildCurrentChainString();
        String path = buildTraversalPathString();
        String nextExpected = nodes.isEmpty() ? "—" : (maxVisitedIndex + 1 < nodes.size() ? nodes.get(maxVisitedIndex + 1).getData() : "INSERT HERE");
        String body;
        if (didInsertHead && !didInsertMiddle && !connectionMode) {
            String betweenNodes = (current != null && current.next != null) ? ("INSERT BETWEEN: " + current.getData() + " and " + current.next.getData()) : (!nodes.isEmpty() && nodes.size() >= 2 ? "INSERT BETWEEN: " + nodes.get(0).getData() + " and " + nodes.get(1).getData() : "INSERT AT MIDDLE");
            body = "MIDDLE INSERTION TASK\n" + betweenNodes + "\n\nSTEPS:\n1. Traverse to insertion point (follow chain order!)\n2. Press I ONCE to create new node\n3. The chain reconnects automatically; the wizard will explain.";
        } else if (connectionMode) {
            String prevD = connectionPrev != null ? connectionPrev.getData() : "?";
            String nextD = connectionNext != null ? connectionNext.getData() : "?";
            body = "CONNECT ARROWS:\n1. Click " + prevD + " (blue), then NEW (yellow).\n2. Click NEW, then " + nextD + " (blue).\n\nDon't press I again!";
        } else if (didInsertTail && !deletionPhaseActive) {
            body = "Now learn deletion. Move to the predecessor (node before the one to delete) and press D.";
        } else if (deletionPhaseActive) {
            body = buildDeletionPaperBody();
        } else {
            body = "Press I to insert:\n• At HEAD: New node becomes first\n• Middle: traverse in order, then press I\n• At TAIL: Insert before NULL";
        }
        if (instructionTitle != null) instructionTitle.setText(connectionMode ? "CONNECTION MODE" : (deletionPhaseActive ? "DELETION PHASE" : "INSERTION PHASE"));
        if (instructionChainLabel != null) instructionChainLabel.setText(chain);
        if (instructionBody != null) instructionBody.setText(body);
        updateSelectionFeedback();
        if (instructionPaper != null) instructionPaper.setVisible(true);
    }

    private String buildTraversalPathString() {
        if (nodes.isEmpty()) return "HEAD";
        StringBuilder sb = new StringBuilder("HEAD");
        for (int i = 0; i <= maxVisitedIndex && i < nodes.size(); i++) {
            sb.append(" → ").append(nodes.get(i).getData());
        }
        return sb.toString();
    }

    /** Deletion steps for current phase only (one at a time). */
    private String buildDeletionPaperBody() {
        if (!didDeleteHead) {
            return "CURRENT TASK: Delete first node\n\n" +
                    "1. Click HEAD (already selected).\n" +
                    "2. Press D once.\n" +
                    "3. temp storage → HEAD moves → node freed.\n\n" +
                    "After head deletion, you will learn middle deletion.";
        }
        if (!didDeleteMiddle) {
            return "✓ Head deleted.\n\n" +
                    "CURRENT TASK: Delete from middle (3-phase)\n\n" +
                    "1. DISCONNECT: Break link to target\n" +
                    "2. RECONNECT: Create new link (click prev, then next)\n" +
                    "3. DELETE: Remove target node\n\n" +
                    "Select the node BEFORE the target, then press D.";
        }
        if (!didDeleteTail) {
            return "✓ Head deleted.\n✓ Middle deleted.\n\n" +
                    "CURRENT TASK: Delete from tail (3-phase)\n\n" +
                    "1. DISCONNECT: Break link to tail\n" +
                    "2. RECONNECT: Link to NULL (click prev, then NULL)\n" +
                    "3. DELETE: Remove tail node";
        }
        return "✓ All deletions mastered!\n" +
                "• Head deletion ✓\n• Middle deletion ✓\n• Tail deletion ✓";
    }

    private void updateDeletionPaper() {
        String title = "DELETION PHASE";
        String body;
        if (currentDeletionPhase != DeletionPhase.NONE) {
            switch (currentDeletionPhase) {
                case DISCONNECT:
                    title = "PHASE 1: DISCONNECT";
                    body = "Link broken: " + (deletionPrev != null ? deletionPrev.getData() : "?")
                            + " → " + (deletionTarget != null ? deletionTarget.getData() : "?") + "\n\n"
                            + "Click Continue to Reconnection, then click " + (deletionPrev != null ? deletionPrev.getData() : "?")
                            + ", then " + (deletionNext != null ? deletionNext.getData() : "NULL") + " to reconnect.";
                    break;
                case RECONNECT:
                    title = "PHASE 2: RECONNECT";
                    body = deletionNext == null
                            ? "Click " + (deletionPrev != null ? deletionPrev.getData() : "?") + " (yellow), then NULL to create new link."
                            : "Click " + (deletionPrev != null ? deletionPrev.getData() : "?") + " (yellow), then " + (deletionNext != null ? deletionNext.getData() : "?") + " (yellow) to reconnect.";
                    break;
                default:
                    title = "DELETION PHASE";
                    body = buildDeletionPaperBody();
                    break;
            }
        } else {
            body = buildDeletionPaperBody();
            if (!didDeleteHead) title = "DELETE FROM HEAD";
            else if (!didDeleteMiddle) title = "DELETE FROM MIDDLE";
            else if (!didDeleteTail) title = "DELETE FROM TAIL";
            else title = "DELETION COMPLETE";
        }
        if (instructionTitle != null) instructionTitle.setText(title);
        if (instructionChainLabel != null) instructionChainLabel.setText(buildCurrentChainString());
        if (instructionBody != null) instructionBody.setText(body != null ? body : buildDeletionPaperBody());
        updateSelectionFeedback();
        if (instructionPaper != null) instructionPaper.setVisible(true);
    }

    private MemoryNode findNode(String data) {
        for (MemoryNode node : nodes) {
            if (node.getData().equals(data)) return node;
        }
        return null;
    }

    private MemoryNode findSecondLastNode() {
        if (nodes.size() < 2) return null;
        return nodes.get(nodes.size() - 2);
    }

    private static final int REQUIRED_NODES = 4;

    private void buildInitialList() {
        nodes.clear();
        collectionOrder.clear();
        decoyNodes.clear();
        collectionIndex = 0;

        MemoryNode a = new MemoryNode("A");
        MemoryNode b = new MemoryNode("B");
        MemoryNode c = new MemoryNode("C");
        MemoryNode d = new MemoryNode("D");

        List<MemoryNode> targets = new ArrayList<>();
        targets.add(a);
        targets.add(b);
        targets.add(c);
        targets.add(d);
        Collections.shuffle(targets);
        collectionOrder = new ArrayList<>(targets);

        head = null;
        current = collectionOrder.isEmpty() ? null : collectionOrder.get(0);
        if (current != null) current.setCurrent(true);
        for (int i = 0; i < collectionOrder.size(); i++) {
            if (collectionOrder.get(i) != current) collectionOrder.get(i).setCurrent(false);
        }

        for (int i = 0; i < 4; i++) {
            MemoryNode decoy = new MemoryNode(String.valueOf((char) ('X' + i)));
            decoy.setCurrent(false);
            decoyNodes.add(decoy);
        }
        track.getChildren().addAll(a, b, c, d);
        track.getChildren().addAll(decoyNodes);

        nullLabel = new Label("NULL");
        nullLabel.setTextFill(Color.web("#f5f5f5"));
        nullLabel.setFont(AssetLoader.loadFont("/fonts/CinzelDecorative-Regular.ttf", 20));
        nullLabel.setMouseTransparent(true);
        headLabel = new Label("HEAD");
        headLabel.setTextFill(Color.web("#88aaff"));
        headLabel.setFont(AssetLoader.loadFont("/fonts/CinzelDecorative-Regular.ttf", 20));
        headLabel.setMouseTransparent(true);
    }

    private void positionNull(Label label) {
        if (nodes.isEmpty()) {
            label.setLayoutX(sceneW / 2 - 40);
            label.setLayoutY(sceneH * NODE_Y_FRACTION);
            return;
        }
        MemoryNode tail = nodes.get(nodes.size() - 1);
        Bounds b = tail.getBoundsInParent();
        label.setLayoutX(b.getMaxX() + 200);
        label.setLayoutY(b.getMinY() + (b.getHeight() / 2) - 15);
        label.toFront();
    }

    /** Scatter crystal balls throughout the full track so player moves the screen to find them. */
    private void layoutNodes() {
        double trackW = sceneW * 2.5;
        double trackH = sceneH * 1.6;
        double baseY = sceneH * NODE_Y_FRACTION;
        double margin = 120;
        List<MemoryNode> toLayout = new ArrayList<>();
        if (!nodes.isEmpty()) toLayout.addAll(nodes);
        else toLayout.addAll(collectionOrder);
        double[][] scatter = {
                { margin, 0 }, { trackW * 0.22, -80 }, { trackW * 0.38, 55 }, { trackW * 0.52, -45 }, { trackW * 0.65, 65 }, { trackW * 0.78, -25 },
                { trackW * 0.12, -50 }, { trackW * 0.28, 70 }, { trackW * 0.45, -35 }, { trackW * 0.58, 50 }, { trackW * 0.72, -60 }, { trackW * 0.88, 20 }
        };
        for (int i = 0; i < toLayout.size(); i++) {
            MemoryNode n = toLayout.get(i);
            double x = i < scatter.length ? scatter[i][0] : margin + (i * (trackW - 2 * margin) / 12);
            double yOff = i < scatter.length ? scatter[i][1] : (i % 3 - 1) * 50;
            n.setLayoutX(Math.min(x, trackW - margin - 80));
            n.setLayoutY(baseY + yOff);
        }
        for (int i = 0; i < decoyNodes.size(); i++) {
            MemoryNode n = decoyNodes.get(i);
            int j = toLayout.size() + i;
            double x = j < scatter.length ? scatter[j][0] : margin + (j * (trackW - 2 * margin) / 12);
            double yOff = j < scatter.length ? scatter[j][1] : ((j % 4) - 2) * 45;
            n.setLayoutX(Math.min(x, trackW - margin - 80));
            n.setLayoutY(baseY + yOff);
        }
    }

    /** Insertion phase: nodes in a row with chain lines between them. */
    private void layoutNodesForInsertion() {
        double baseY = sceneH * NODE_Y_FRACTION;
        double startX = 120;
        double spacing = 180;
        for (int i = 0; i < nodes.size(); i++) {
            MemoryNode n = nodes.get(i);
            n.setLayoutX(startX + i * spacing);
            n.setLayoutY(baseY);
        }
        if (headLabel != null) {
            if (!nodes.isEmpty()) {
                headLabel.setLayoutX(startX - 72);
                headLabel.setLayoutY(baseY - 8);
                headLabel.setVisible(true);
                headLabel.toFront();
            } else {
                headLabel.setVisible(false);
            }
        }
    }

    private void positionHead(Label label) {
        if (nodes.isEmpty()) {
            label.setVisible(false);
            return;
        }
        MemoryNode first = nodes.get(0);
        Bounds b = first.getBoundsInParent();
        label.setLayoutX(b.getMinX() - 72);
        label.setLayoutY(b.getMinY() + (b.getHeight() / 2) - 15);
        label.setVisible(true);
        label.toFront();
    }

    /** Insertion phase: draw chain lines between consecutive nodes. In connection mode, skip prev→new and new→next so user draws them. */
    private void rebuildPointersForInsertion() {
        for (MemoryNode n : nodes) {
            if (n.pointerLine != null) {
                track.getChildren().remove(n.pointerLine);
                n.pointerLine = null;
            }
        }
        if (tailToNullLine != null) {
            track.getChildren().remove(tailToNullLine);
            tailToNullLine = null;
        }
        if (headToFirstLine != null) {
            track.getChildren().remove(headToFirstLine);
            headToFirstLine = null;
        }
        if (track != null) track.layout();
        for (MemoryNode n : nodes) {
            if (n.next == null) continue;
            if (connectionMode && connectionPrev != null && connectionNew != null && connectionNext != null) {
                if ((n == connectionPrev && n.next == connectionNew) || (n == connectionNew && n.next == connectionNext))
                    continue;
            }
            if (currentDeletionPhase != DeletionPhase.NONE && deletionPrev != null && deletionTarget != null
                    && n == deletionPrev && n.next == deletionTarget) {
                continue; // Phase 1/2: break link prev→target (e.g. F→A)
            }
            if (currentDeletionPhase != DeletionPhase.NONE && deletionTarget != null && deletionNext != null
                    && n == deletionTarget && n.next == deletionNext) {
                continue; // Phase 1/2: break link target→next (e.g. A→D) so target is fully disconnected
            }
            Bounds b1 = n.getBoundsInParent();
            Bounds b2 = n.next.getBoundsInParent();
            double sx = b1.getMaxX() + 8;
            double sy = b1.getMinY() + b1.getHeight() / 2.0;
            double ex = b2.getMinX() - 8;
            double ey = b2.getMinY() + b2.getHeight() / 2.0;
            Line line = new Line(sx, sy, ex, ey);
            line.setStroke(Color.web("#ffd27f"));
            line.setStrokeWidth(3);
            line.getStrokeDashArray().addAll(8.0, 10.0);
            track.getChildren().add(line);
            n.pointerLine = line;
        }
        // Draw HEAD → first node horizontally so HEAD is visibly connected to the chain
        if (!nodes.isEmpty() && headLabel != null && headLabel.isVisible()) {
            track.layout();
            Bounds headB = headLabel.getBoundsInParent();
            Bounds firstB = nodes.get(0).getBoundsInParent();
            double midY = firstB.getMinY() + firstB.getHeight() / 2.0;
            double sx = headB.getMaxX() + 8;
            double ex = firstB.getMinX() - 8;
            headToFirstLine = new Line(sx, midY, ex, midY);
            headToFirstLine.setStroke(Color.web("#44AAFF"));
            headToFirstLine.setStrokeWidth(4);
            headToFirstLine.getStrokeDashArray().addAll(8.0, 10.0);
            track.getChildren().add(headToFirstLine);
        }

        // Draw last node → NULL (or during tail-deletion reconnect: predecessor → NULL)
        if (!nodes.isEmpty() && nullLabel != null) {
            MemoryNode tail = nodes.get(nodes.size() - 1);
            boolean drawTailToNull = (currentDeletionPhase == DeletionPhase.NONE || deletionTarget != tail)
                    && !(currentDeletionPhase == DeletionPhase.RECONNECT && deletionNext == null);
            if (currentDeletionPhase == DeletionPhase.RECONNECT && deletionNext == null && deletionPrev != null) {
                Bounds b1 = deletionPrev.getBoundsInParent();
                double sx = b1.getMaxX() + 8;
                double sy = b1.getMinY() + b1.getHeight() / 2.0;
                double ex = b1.getMaxX() + 200 - 8;
                double ey = b1.getMinY() + (b1.getHeight() / 2) - 15;
                tailToNullLine = new Line(sx, sy, ex, ey);
                tailToNullLine.setStroke(Color.web("#00FFAA"));
                tailToNullLine.setStrokeWidth(4);
                tailToNullLine.getStrokeDashArray().addAll(8.0, 10.0);
                track.getChildren().add(tailToNullLine);
            } else if (drawTailToNull) {
                Bounds b1 = tail.getBoundsInParent();
                double sx = b1.getMaxX() + 8;
                double sy = b1.getMinY() + b1.getHeight() / 2.0;
                double ex = b1.getMaxX() + 200 - 8;
                double ey = b1.getMinY() + (b1.getHeight() / 2) - 15;
                tailToNullLine = new Line(sx, sy, ex, ey);
                tailToNullLine.setStroke(Color.web("#ffaa55"));
                tailToNullLine.setStrokeWidth(4);
                tailToNullLine.getStrokeDashArray().addAll(8.0, 10.0);
                track.getChildren().add(tailToNullLine);
            }
        }
    }

    private void rebuildPointers() {
        for (MemoryNode n : nodes) {
            if (n.pointerLine != null) {
                track.getChildren().remove(n.pointerLine);
                n.pointerLine = null;
            }
        }
        if (tailToNullLine != null) {
            track.getChildren().remove(tailToNullLine);
            tailToNullLine = null;
        }
        if (headToFirstLine != null) {
            track.getChildren().remove(headToFirstLine);
            headToFirstLine = null;
        }
    }

    // ---------- Traversal ----------

    private void moveNext() {
        if (traversalPhase && !collectionOrder.isEmpty()) {
            if (collectionIndex + 1 >= collectionOrder.size()) return;
            collectionIndex++;
            current = collectionOrder.get(collectionIndex);
            setCurrent(current);
            return;
        }
        if (current == null || current.next == null) return;
        MemoryNode n = current.next;
        if (!trySelectByTraversal(n)) return;
        centerOn(n);
    }

    private void movePrev() {
        if (traversalPhase && !collectionOrder.isEmpty()) {
            if (collectionIndex <= 0) return;
            collectionIndex--;
            current = collectionOrder.get(collectionIndex);
            setCurrent(current);
            return;
        }
        if (current == null || current == head) return;
        MemoryNode p = findPrev(current);
        if (p != null && trySelectByTraversal(p)) {
            centerOn(p);
        }
    }

    /** Enforce sequential traversal in list order when didInsertHead && !didInsertMiddle. Returns true if selection allowed. */
    private boolean trySelectByTraversal(MemoryNode n) {
        int idx = nodes.indexOf(n);
        if (idx < 0) return false;
        boolean requireSequence = didInsertHead && !didInsertMiddle && !connectionMode;
        if (!requireSequence) {
            maxVisitedIndex = Math.max(maxVisitedIndex, idx);
            setCurrent(n);
            return true;
        }
        if (idx > maxVisitedIndex + 1) {
            onWrongTraversal(n);
            return false;
        }
        wrongActionCount = 0;
        maxVisitedIndex = Math.max(maxVisitedIndex, idx);
        setCurrent(n);
        updateInsertionPhasePaper();
        return true;
    }

    /** Call on any wrong action; returns true if this was the second wrong (heart deducted). */
    private boolean onWrongAction() {
        wrongActionCount++;
        if (wrongActionCount >= 2) {
            wrongActionCount = 0;
            getGameState().loseHeart();
            if (getHud() != null) getHud().pulseHearts();
            showWrongCrossFx();
            return true;
        }
        return false;
    }

    /** Show a red cross when a heart is deducted (wrong click or wrong key). */
    private void showWrongCrossFx() {
        Label cross = new Label("✗");
        cross.setFont(AssetLoader.loadFont("/fonts/CinzelDecorative-Bold.ttf", 72));
        cross.setTextFill(Color.web("#CC2222"));
        cross.setStyle("-fx-background-color: transparent; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.8), 12, 0.5, 0, 2);");
        cross.setMouseTransparent(true);
        StackPane wrap = new StackPane(cross);
        wrap.setAlignment(Pos.CENTER);
        wrap.setMouseTransparent(true);
        if (fxLayer != null) {
            fxLayer.getChildren().add(wrap);
            cross.setOpacity(0);
            FadeTransition in = new FadeTransition(Duration.millis(150), cross);
            in.setToValue(1.0);
            FadeTransition out = new FadeTransition(Duration.millis(400), cross);
            out.setDelay(Duration.millis(650));
            out.setToValue(0.0);
            out.setOnFinished(e -> fxLayer.getChildren().remove(wrap));
            in.play();
            out.play();
        }
    }

    private void onWrongTraversal(MemoryNode wrongNode) {
        getGameState().loseScore(WRONG_TRAVERSAL_PENALTY);
        boolean lostHeart = onWrongAction();
        wrongNode.setConnectionGlow("red");
        for (int i = 0; i <= maxVisitedIndex && i < nodes.size(); i++) {
            nodes.get(i).setConnectionGlow("green");
        }
        StringBuilder sb = new StringBuilder("HEAD");
        for (MemoryNode x : nodes) sb.append(" → ").append(x.getData());
        sb.append(" → NULL");
        String chainOrder = sb.toString();
        Button cont = UiUtil.btn("Continue");
        cont.setOnAction(e -> {
            dialogue.hide();
            clearAllConnectionGlows();
            current = head;
            maxVisitedIndex = 0;
            setCurrent(head);
            centerOn(head);
            updateInsertionPhasePaper();
        });
        String page1 = "FOOL! The chain does not bend to random clicks...\nFollow the path from HEAD to NULL.";
        String page2 = "Correct order: " + chainOrder + "\n\n-10 points. Begin anew from HEAD...";
        if (lostHeart) page2 += "\n\nTwo wrongs: -1 heart.";
        List<String> pages = new ArrayList<>(Arrays.asList(page1, page2));
        showPaginatedDialogue(pages, 0, cont);
    }

    private void clearAllConnectionGlows() {
        for (MemoryNode n : nodes) n.setConnectionGlow(null);
        if (connectionPrev != null) connectionPrev.setConnectionGlow(null);
        if (connectionNew != null) connectionNew.setConnectionGlow(null);
        if (connectionNext != null) connectionNext.setConnectionGlow(null);
    }

    private MemoryNode findPrev(MemoryNode target) {
        MemoryNode p = head;
        while (p != null && p.next != target) {
            p = p.next;
        }
        return p;
    }

    private void setCurrent(MemoryNode n) {
        if (current != null) current.setCurrent(false);
        current = n;
        if (current != null) current.setCurrent(true);
        updateSelectionFeedback();
    }

    private void updateSelectionFeedback() {
        if (instructionSelection == null) return;
        if (traversalPhase || current == null) {
            instructionSelection.setText("");
            return;
        }
        instructionSelection.setText("SELECTED: NODE " + current.getData() + " [GLOWING]");
    }

    private void centerOn(MemoryNode n) {
        if (scroll == null) return;
        Bounds b = n.getBoundsInParent();
        double contentW = track.getBoundsInLocal().getWidth();
        double contentH = track.getBoundsInLocal().getHeight();
        double viewportW = scroll.getViewportBounds().getWidth();
        double viewportH = scroll.getViewportBounds().getHeight();

        double centerX = b.getMinX() + b.getWidth() / 2.0;
        double centerY = b.getMinY() + b.getHeight() / 2.0;
        double targetH = centerX - viewportW / 2.0;
        double targetV = centerY - viewportH / 2.0;
        double h = (contentW > viewportW) ? Math.max(0, Math.min(1, targetH / (contentW - viewportW))) : 0;
        double v = (contentH > viewportH) ? Math.max(0, Math.min(1, targetV / (contentH - viewportH))) : 0;

        Timeline tl = new Timeline(
                new KeyFrame(Duration.millis(260),
                        new KeyValue(scroll.hvalueProperty(), h, Interpolator.EASE_BOTH),
                        new KeyValue(scroll.vvalueProperty(), v, Interpolator.EASE_BOTH))
        );
        tl.play();
    }

    // ---------- Insertion ----------

    private void doInsert() {
        if (current == null) return;

        if (insertionInProgress) {
            showInstructionPaper("Wait", "Insertion already in progress. Complete the current operation first.");
            UiUtil.shake(getBlurTarget());
            return;
        }
        if (didInsertMiddle && !connectionMode) {
            showInstructionPaper("Already done", "Middle insertion already performed. Complete the next steps or continue to deletion.");
            UiUtil.shake(getBlurTarget());
            return;
        }
        if (connectionMode) {
            showInstructionPaper("Connect first", "Insertion already done! Connect the nodes first (blue → yellow → blue).");
            UiUtil.shake(getBlurTarget());
            return;
        }

        insertionInProgress = true;
        MemoryNode newNode = new MemoryNode(nextLabel());

        if (current == head) {
            newNode.next = head;
            head = newNode;
            nodes.add(0, newNode);
            didInsertHead = true;
            updateInsertionPhasePaper();
            insertionInProgress = false;
            track.getChildren().add(newNode);
            attachNodeClickHandler(newNode);
            getGameState().addScore(SCORE_INSERT);
            syncChainAboveFromList();
            layoutNodesForInsertion();
            Platform.runLater(() -> {
                rebuildPointersForInsertion();
                if (nullLabel != null) positionNull(nullLabel);
                if (headLabel != null) positionHead(headLabel);
            });
            setCurrent(newNode);
            centerOn(newNode);
            Button nextBtn = UiUtil.btn("Next");
            nextBtn.setOnAction(ev -> {
                dialogue.hide();
                showInsertMiddleInstruction();
            });
            Platform.runLater(() -> SnapshotUtil.captureNode(getBlurTarget(), 2.0, Color.TRANSPARENT, learnSnaps::add));
            say("The new crystal is now the HEAD of the chain...\n\nNext: insertion in the middle. Stand on the crystal after which you wish to insert, then press I.", null, nextBtn);
            return;
        } else if (current.next == null) {
            current.next = newNode;
            newNode.next = null;
            nodes.add(newNode);
            didInsertTail = true;
            updateInsertionPhasePaper();
            insertionInProgress = false;
        } else {
            MemoryNode after = current.next;
            newNode.next = after;
            current.next = newNode;
            int idx = nodes.indexOf(after);
            if (idx >= 0) nodes.add(idx, newNode);
            else nodes.add(newNode);
            didInsertMiddle = true;
        }

        track.getChildren().add(newNode);
        attachNodeClickHandler(newNode);
        getGameState().addScore(SCORE_INSERT);

        syncChainAboveFromList();
        layoutNodesForInsertion();
        Platform.runLater(() -> {
            rebuildPointersForInsertion();
            if (nullLabel != null) positionNull(nullLabel);
            if (headLabel != null) positionHead(headLabel);
        });
        setCurrent(newNode);
        centerOn(newNode);

        if (didInsertMiddle) {
            MemoryNode prevNode = findPrev(newNode);
            String prevD = prevNode != null ? prevNode.getData() : "?";
            String nextD = newNode.next != null ? newNode.next.getData() : "?";
            Button okBtn = UiUtil.btn("OK");
            okBtn.setOnAction(ev -> {
                dialogue.hide();
                showInstructionPaper("Good! Inserted in middle", "The wizard will teach deletion.");
                Platform.runLater(() -> {
                    PauseTransition delay = new PauseTransition(Duration.millis(800));
                    delay.setOnFinished(e -> scheduleWizardAndDeletionPhase());
                    delay.play();
                });
            });
            Platform.runLater(() -> SnapshotUtil.captureNode(getBlurTarget(), 2.0, Color.TRANSPARENT, learnSnaps::add));
            say("The chain sings with harmony... " + prevD + " bonds to the new crystal,\nand the new crystal bonds to " + nextD + ".\nYou have inserted in the middle.", null, okBtn);
        }
        checkVictory();
    }

    private void startConnectionTimeout() {
        if (connectionTimeoutTimeline != null) connectionTimeoutTimeline.stop();
        connectionTimeoutTimeline = new Timeline(new KeyFrame(Duration.seconds(15), e -> {
            if (!connectionMode) return;
            getGameState().loseScore(MISSING_CONNECTIONS_PENALTY);
            Button cont = UiUtil.btn("Continue");
            cont.setOnAction(ev -> {
                dialogue.hide();
                completeConnectionMode();
            });
            say("The bonds remain unforged, seeker!\nClick previous → new → new → next.\n\n-5 points. I shall complete the links for you.", null, cont);
            PauseTransition autoDismiss = new PauseTransition(Duration.seconds(8));
            autoDismiss.setOnFinished(ev2 -> {
                if (dialogue.isVisible()) {
                    dialogue.hide();
                    completeConnectionMode();
                }
            });
            autoDismiss.play();
        }));
        connectionTimeoutTimeline.play();
    }

    private void completeConnectionMode() {
        connectionMode = false;
        insertionInProgress = false;
        connectionState = ConnectionState.COMPLETE;
        if (connectionTimeoutTimeline != null) {
            connectionTimeoutTimeline.stop();
            connectionTimeoutTimeline = null;
        }
        clearAllConnectionGlows();
        track.layout();
        Platform.runLater(() -> {
            rebuildPointersForInsertion();
            if (nullLabel != null) positionNull(nullLabel);
            if (headLabel != null) positionHead(headLabel);
        });
        getGameState().addScore(SCORE_CONNECTIONS);
        if (instructionPaper != null) instructionPaper.setVisible(false);
        Platform.runLater(() -> {
            PauseTransition delay = new PauseTransition(Duration.millis(800));
            delay.setOnFinished(ev -> scheduleWizardAndDeletionPhase());
            delay.play();
        });
    }

    private void onConnectionClick(MemoryNode n) {
        handleConnectionClick(n);
    }

    private void handleConnectionClick(MemoryNode clickedNode) {
        switch (connectionState) {
            case WAITING_FIRST_FROM:
                if (clickedNode == connectionPrev) {
                    connectionState = ConnectionState.WAITING_FIRST_TO;
                    if (instructionSelection != null) instructionSelection.setText("Now click NEW (yellow)");
                    if (instructionBody != null) instructionBody.setText("Step 1 done. Now click the YELLOW node (new).");
                } else {
                    String prevD = connectionPrev != null ? connectionPrev.getData() : "?";
                    Button err1 = UiUtil.btn("OK");
                    err1.setOnAction(ev -> dialogue.hide());
                    say("Click the BLUE crystal (" + prevD + ") first, seeker!", null, err1);
                    UiUtil.shake(getBlurTarget());
                }
                break;
            case WAITING_FIRST_TO:
                if (clickedNode == connectionNew) {
                    connection1Done = true;
                    connectionState = ConnectionState.WAITING_SECOND_FROM;
                    if (instructionSelection != null) instructionSelection.setText("Click NEW, then next BLUE");
                    if (instructionBody != null) instructionBody.setText("Good! Now click NEW (yellow) again, then the next BLUE node.");
                } else {
                    Button err2 = UiUtil.btn("OK");
                    err2.setOnAction(ev -> dialogue.hide());
                    say("Click the YELLOW crystal... the new fragment.", null, err2);
                    UiUtil.shake(getBlurTarget());
                }
                break;
            case WAITING_SECOND_FROM:
                if (clickedNode == connectionNew) {
                    String nextD = connectionNext != null ? connectionNext.getData() : "?";
                    connectionState = ConnectionState.WAITING_SECOND_TO;
                    if (instructionSelection != null) instructionSelection.setText("Click " + nextD + " (blue)");
                    if (instructionBody != null) instructionBody.setText("Now click the next BLUE node (" + nextD + ").");
                } else {
                    Button err3 = UiUtil.btn("OK");
                    err3.setOnAction(ev -> dialogue.hide());
                    say("Click the YELLOW crystal first... the new one.", null, err3);
                    UiUtil.shake(getBlurTarget());
                }
                break;
            case WAITING_SECOND_TO:
                if (clickedNode == connectionNext) {
                    connection2Done = true;
                    connectionState = ConnectionState.COMPLETE;
                    insertionInProgress = false;
                    if (connectionTimeoutTimeline != null) connectionTimeoutTimeline.stop();
                    connectionTimeoutTimeline = null;
                    connectionMode = false;
                    clearAllConnectionGlows();
                    rebuildPointersForInsertion();
                    if (nullLabel != null) positionNull(nullLabel);
                    if (headLabel != null) positionHead(headLabel);
                    getGameState().addScore(SCORE_CONNECTIONS);
                    if (instructionSelection != null) instructionSelection.setText("");
                    Button okDone = UiUtil.btn("OK");
                    okDone.setOnAction(ev -> {
                        dialogue.hide();
                        if (instructionPaper != null) instructionPaper.setVisible(false);
                        Platform.runLater(() -> {
                            PauseTransition delay = new PauseTransition(Duration.millis(800));
                            delay.setOnFinished(e -> scheduleWizardAndDeletionPhase());
                            delay.play();
                        });
                    });
                    say("The chain sings with harmony... every fragment perfectly aligned.", null, okDone);
                    updateInsertionPhasePaper();
                } else {
                    String nextD = connectionNext != null ? connectionNext.getData() : "?";
                    Button err4 = UiUtil.btn("OK");
                    err4.setOnAction(ev -> dialogue.hide());
                    say("Click the BLUE crystal—the next in line (" + nextD + ").", null, err4);
                    UiUtil.shake(getBlurTarget());
                }
                break;
            default:
                break;
        }
        updateInsertionPhasePaper();
    }

    private String nextLabel() {
        int idx = nodes.size();
        char c = (char) ('A' + (idx % 26));
        return String.valueOf(c);
    }

    // ---------- Deletion ----------

    private static final int WRONG_DELETE_PENALTY = 5;

    private void doDeleteNext() {
        if (current == null) return;
        if (currentDeletionPhase != DeletionPhase.NONE) return;
        if (nodes.size() <= 1) {
            showInstructionPaper("Cannot Delete", "Cannot delete the last node in the list.");
            return;
        }

        if (current == head) {
            for (MemoryNode n : nodes) n.setCurrent(false);
            current = head;
            if (head != null) head.setCurrent(true);
            centerOn(head);
            updateSelectionFeedback();
            Button startDelBtn = UiUtil.btn("Start Deletion");
            startDelBtn.setOnAction(ev -> {
                dialogue.hide();
                actuallyAnimateDeleteFromHead();
            });
            say("You stand at the HEAD of the chain...\nClick Start Deletion when ready.", null, startDelBtn);
            return;
        }

        if (current.next == null) {
            getGameState().loseScore(WRONG_DELETE_PENALTY);
            boolean lostHeart = onWrongAction();
            Button cont = UiUtil.btn("Continue");
            cont.setOnAction(e -> dialogue.hide());
            String msg = "FOOL! You must stand on the crystal BEFORE the one you wish to sever.\n\n-5 points.";
            if (lostHeart) msg += "\n\nTwo wrongs: -1 heart.";
            say(msg, null, cont);
            return;
        }

        // 3-phase deletion for middle and tail
        deletionTarget = current.next;
        deletionPrev = current;
        deletionNext = deletionTarget.next;
        boolean isTailDeletion = (deletionNext == null);

        Button startBtn = UiUtil.btn("Start Deletion");
        startBtn.setOnAction(e -> {
            dialogue.hide();
            currentDeletionPhase = DeletionPhase.DISCONNECT;
            deletionReconnectFirstClicked = false;
            startDeletionPhase1_Disconnect(isTailDeletion);
        });

        if (isTailDeletion) {
            String page1 = "\nThe final fragment...\nYou shall sever " + deletionTarget.getData() + " from the chain.\n\nSteps 1–2:\nBreak the bond " + deletionPrev.getData() + " → " + deletionTarget.getData() + "\nThen forge " + deletionPrev.getData() + " → NULL.";
            String page2 = "Step 3: Remove the crystal " + deletionTarget.getData() + ".";
            showPaginatedDialogue(Arrays.asList(page1, page2), 0, startBtn);
        } else {
            String page1 = "\nYou're breaking the chain at " + deletionTarget.getData() + "...\n\nSteps 1–2:\nBreak " + deletionPrev.getData() + " → " + deletionTarget.getData() + "\nBreak " + deletionTarget.getData() + " → " + deletionNext.getData();
            String page2 = "Steps 3–4:\nForge new bond: " + deletionPrev.getData() + " → " + deletionNext.getData() + "\nThen remove the crystal " + deletionTarget.getData() + ".";
            showPaginatedDialogue(Arrays.asList(page1, page2), 0, startBtn);
        }
    }

    private void startDeletionPhase1_Disconnect(boolean isTailDeletion) {
        showInstructionPaper("PHASE 1: DISCONNECT",
                isTailDeletion
                        ? "Link broken: " + deletionPrev.getData() + " → " + deletionTarget.getData() + "\n\n"
                        + "Now you must reconnect the chain.\n"
                        + "Click " + deletionPrev.getData() + ", then click the NULL pointer."
                        : "Link broken: " + deletionPrev.getData() + " → " + deletionTarget.getData() + "\n\n"
                        + "Now you must reconnect the chain.\n"
                        + "Click " + deletionPrev.getData() + ", then click " + deletionNext.getData() + ".");

        for (MemoryNode n : nodes) n.setConnectionGlow(null);
        deletionPrev.setConnectionGlow("yellow");
        deletionTarget.setConnectionGlow("red");
        if (deletionNext != null) deletionNext.setConnectionGlow("yellow");

        track.layout();
        Platform.runLater(() -> rebuildPointersForInsertion());

        animateBrokenLink(deletionPrev, deletionTarget);

        Button continueBtn = UiUtil.btn("Continue to Reconnection");
        continueBtn.setOnAction(e -> {
            dialogue.hide();
            enableReconnectionMode();
        });
        say("The bond is broken... " + deletionTarget.getData() + " hangs disconnected.\n\n"
                        + "Now forge the chain anew " + (isTailDeletion ? "to NULL" : "— skip " + deletionTarget.getData()) + ".",
                null, continueBtn);
        ensureDeletionElementsVisible();
    }

    private void ensureDeletionElementsVisible() {
        if (deletionPrev != null && scroll != null && track != null) {
            Platform.runLater(() -> {
                double vw = scroll.getViewportBounds().getWidth();
                double tw = track.getBoundsInLocal().getWidth();
                if (tw <= vw) return;
                Bounds prevB = deletionPrev.getBoundsInParent();
                double midX = prevB.getMinX() + prevB.getWidth() / 2.0;
                if (deletionNext != null) {
                    Bounds nextB = deletionNext.getBoundsInParent();
                    midX = (midX + (nextB.getMinX() + nextB.getWidth() / 2.0)) / 2.0;
                }
                double targetH = (midX - vw / 2.0) / (tw - vw);
                targetH = Math.max(0, Math.min(1, targetH));
                scroll.setHvalue(targetH);
            });
        }
    }

    private void enableReconnectionMode() {
        currentDeletionPhase = DeletionPhase.RECONNECT;
        deletionReconnectFirstClicked = false;
        ensureDeletionElementsVisible();

        showInstructionPaper("PHASE 2: RECONNECT",
                deletionNext == null
                        ? "Click " + deletionPrev.getData() + " (yellow), then click NULL to create new link."
                        : "Click " + deletionPrev.getData() + " (yellow), then click " + deletionNext.getData() + " (yellow) to reconnect.");

        for (MemoryNode n : nodes) n.setConnectionGlow(null);
        deletionPrev.setConnectionGlow("yellow");
        deletionTarget.setConnectionGlow("red");
        if (deletionNext != null) deletionNext.setConnectionGlow("yellow");

        if (track != null) track.toFront();
        if (deletionNext == null && nullLabel != null) {
            nullLabel.setMouseTransparent(false);
            nullLabel.setCursor(Cursor.HAND);
            nullLabel.setStyle("-fx-background-color: rgba(100, 100, 255, 0.3); -fx-padding: 5 10; -fx-border-color: #44AAFF; -fx-border-width: 2;");
            nullLabel.setTextFill(Color.web("#FFFFFF"));
            if (track != null) nullLabel.toFront();
            nullLabel.setOnMouseClicked(ev -> {
                if (ev.getButton() != MouseButton.PRIMARY) return;
                if (deletionReconnectFirstClicked) {
                    onThreePhaseReconnectClick(null);
                } else {
                    Button ok = UiUtil.btn("OK");
                    ok.setOnAction(a -> dialogue.hide());
                    say("Click " + deletionPrev.getData() + " first, seeker!", null, ok);
                }
            });
        }
        updateDeletionPaper();
    }

    /** Called from attachNodeClickHandler (MemoryNode) or NULL label handler (clicked = null). */
    private void onThreePhaseReconnectClick(Object clicked) {
        if (currentDeletionPhase != DeletionPhase.RECONNECT || deletionPrev == null) return;
        if (!deletionReconnectFirstClicked) {
            if (clicked == deletionPrev) {
                deletionReconnectFirstClicked = true;
                deletionPrev.setConnectionGlow("green");
                String nextTarget = deletionNext != null ? deletionNext.getData() : "NULL";
                if (instructionSelection != null) instructionSelection.setText("Now click " + nextTarget);
                if (instructionBody != null) instructionBody.setText("Good! Now click " + nextTarget + " to complete reconnection.");
                Button okBtn = UiUtil.btn("OK");
                okBtn.setOnAction(ev -> dialogue.hide());
                say("The first bond is forged... now click " + nextTarget + " to complete the chain.", null, okBtn);
            } else {
                boolean lostHeart = onWrongAction();
                Button err = UiUtil.btn("OK");
                err.setOnAction(ev -> dialogue.hide());
                String msg = "Click " + deletionPrev.getData() + " first, seeker!";
                if (lostHeart) msg += "\n\nTwo wrongs: -1 heart.";
                say(msg, null, err);
                UiUtil.shake(getBlurTarget());
            }
            return;
        }
        boolean validSecondClick = (deletionNext != null && clicked == deletionNext) || (deletionNext == null && clicked == null);
        if (validSecondClick) {
            wrongActionCount = 0;
            completeReconnection();
        } else {
            boolean lostHeart = onWrongAction();
            Button err = UiUtil.btn("OK");
            err.setOnAction(ev -> dialogue.hide());
            String expected = deletionNext != null ? deletionNext.getData() : "NULL";
            String msg = "Click " + expected + " to forge the final bond.";
            if (lostHeart) msg += "\n\nTwo wrongs: -1 heart.";
            say(msg, null, err);
            UiUtil.shake(getBlurTarget());
        }
    }

    private void completeReconnection() {
        animateNewConnection(deletionPrev, deletionNext);

        Button finalizeBtn = UiUtil.btn("Finalize Deletion");
        finalizeBtn.setOnAction(e -> {
            dialogue.hide();
            startDeletionPhase3_DeleteNode();
        });
        String reconnectionDesc = deletionPrev.getData() + " → " + (deletionNext != null ? deletionNext.getData() : "NULL");
        say("The chain sings again... " + reconnectionDesc + ".\n\n"
                        + "The severed crystal " + deletionTarget.getData() + " may now be removed.",
                null, finalizeBtn);

        if (deletionNext == null && nullLabel != null) {
            nullLabel.setStyle(null);
            nullLabel.setTextFill(Color.web("#f5f5f5"));
            nullLabel.setMouseTransparent(true);
            nullLabel.setCursor(Cursor.DEFAULT);
        }
        updateDeletionPaper();
    }

    private void startDeletionPhase3_DeleteNode() {
        showInstructionPaper("PHASE 3: DELETE/FREE",
                "Node " + deletionTarget.getData() + " is now:\n"
                        + "1. Disconnected from chain ✓\n"
                        + "2. Chain reconnected around it ✓\n"
                        + "3. Ready to be freed from memory");

        deletionTarget.showCorruptedFx(fxLayer);

        PauseTransition delay = new PauseTransition(Duration.millis(800));
        delay.setOnFinished(e -> {
            if (deletionPrev != null) deletionPrev.next = deletionNext;
            nodes.remove(deletionTarget);
            track.getChildren().remove(deletionTarget);

            syncChainAboveFromList();
            layoutNodesForInsertion();
            track.layout();
            Platform.runLater(() -> {
                rebuildPointersForInsertion();
                if (nullLabel != null) positionNull(nullLabel);
                if (headLabel != null) positionHead(headLabel);
            });

            getGameState().addScore(SCORE_DELETE);
            didDeleteOnce = true;
            if (deletionNext == null) didDeleteTail = true;
            else didDeleteMiddle = true;

            cleanupDeletionState();
            updateDeletionPaper();
            checkDeletionProgress();
        });
        delay.play();
    }

    private void cleanupDeletionState() {
        currentDeletionPhase = DeletionPhase.NONE;
        deletionTarget = null;
        deletionPrev = null;
        deletionNext = null;
        deletionReconnectFirstClicked = false;
        for (MemoryNode n : nodes) n.setConnectionGlow(null);
        if (nullLabel != null) {
            nullLabel.setOnMouseClicked(null);
            nullLabel.setMouseTransparent(true);
            nullLabel.setCursor(Cursor.DEFAULT);
        }
    }

    private void checkDeletionProgress() {
        if (!didDeleteHead) {
            showHeadDeletionInstructions();
        } else if (!didDeleteMiddle) {
            showMiddleDeletionInstructions();
        } else if (!didDeleteTail) {
            Platform.runLater(() -> SnapshotUtil.captureNode(getBlurTarget(), 2.0, Color.TRANSPARENT, learnSnaps::add));
            showTailDeletionInstructions();
        } else {
            ensureTailAndNullVisible();
            PauseTransition showConnection = new PauseTransition(Duration.seconds(1.5));
            showConnection.setOnFinished(e -> showTutorialCompleteThenVictory());
            showConnection.play();
        }
    }

    /** Scroll view so the last node and NULL (tail → NULL connection) are visible before victory. */
    private void ensureTailAndNullVisible() {
        if (scroll == null || track == null || nodes.isEmpty() || nullLabel == null) return;
        Platform.runLater(() -> {
            track.layout();
            MemoryNode last = nodes.get(nodes.size() - 1);
            Bounds lastB = last.getBoundsInParent();
            Bounds nullB = nullLabel.getBoundsInParent();
            double midX = (lastB.getMinX() + lastB.getMaxX() + nullB.getMinX() + nullB.getMaxX()) / 4.0;
            double vw = scroll.getViewportBounds().getWidth();
            double tw = track.getBoundsInLocal().getWidth();
            if (tw > vw) {
                double targetH = (midX - vw / 2.0) / (tw - vw);
                scroll.setHvalue(Math.max(0, Math.min(1, targetH)));
            }
        });
    }

    /** Wizard congratulates; on Claim Victory, show the victory screen. */
    private void showTutorialCompleteThenVictory() {
        Button claimBtn = UiUtil.btn("Claim Victory");
        claimBtn.setOnAction(e -> {
            dialogue.hide();
            PauseTransition victoryDelay = new PauseTransition(Duration.millis(300));
            victoryDelay.setOnFinished(ev -> showDeletionCompleteMessage());
            victoryDelay.play();
        });
        say("Congratulations, seeker! You have mastered the Chain of Memory—traversal, insertion, and deletion. The dungeon yields to your wisdom. Claim your victory.",
                null, claimBtn);
    }

    private void animateBrokenLink(MemoryNode from, MemoryNode to) {
        track.layout();
        Bounds b1 = from.getBoundsInParent();
        Bounds b2 = to.getBoundsInParent();
        double sx = b1.getMaxX() + 8;
        double sy = b1.getMinY() + b1.getHeight() / 2.0;
        double ex = b2.getMinX() - 8;
        double ey = b2.getMinY() + b2.getHeight() / 2.0;
        Line breakLine = new Line(sx, sy, ex, ey);
        breakLine.setStroke(Color.web("#FF5555"));
        breakLine.setStrokeWidth(4);
        breakLine.getStrokeDashArray().addAll(10.0, 5.0);
        track.getChildren().add(breakLine);

        TranslateTransition shake = new TranslateTransition(Duration.millis(200), breakLine);
        shake.setByX(10);
        shake.setCycleCount(4);
        shake.setAutoReverse(true);
        FadeTransition fade = new FadeTransition(Duration.millis(800), breakLine);
        fade.setToValue(0);
        fade.setDelay(Duration.millis(400));
        SequentialTransition seq = new SequentialTransition(shake, fade);
        seq.setOnFinished(ev -> track.getChildren().remove(breakLine));
        seq.play();
    }

    private void animateNewConnection(MemoryNode from, MemoryNode to) {
        track.layout();
        Bounds b1 = from.getBoundsInParent();
        double sx = b1.getMaxX() + 8;
        double sy = b1.getMinY() + b1.getHeight() / 2.0;
        double ex, ey;
        if (to != null) {
            Bounds b2 = to.getBoundsInParent();
            ex = b2.getMinX() - 8;
            ey = b2.getMinY() + b2.getHeight() / 2.0;
        } else {
            ex = sx + 100;
            ey = sy;
        }
        Line newLine = new Line(sx, sy, ex, ey);
        newLine.setStroke(Color.web("#00FFAA"));
        newLine.setStrokeWidth(4);
        newLine.setOpacity(0);
        track.getChildren().add(newLine);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(400), newLine);
        fadeIn.setToValue(1.0);
        FadeTransition fadeOut = new FadeTransition(Duration.millis(400), newLine);
        fadeOut.setToValue(0);
        fadeOut.setDelay(Duration.millis(1000));
        SequentialTransition seq = new SequentialTransition(fadeIn, fadeOut);
        seq.setOnFinished(ev -> track.getChildren().remove(newLine));
        seq.play();
    }

    private void actuallyAnimateDeleteFromHead() {
        // DS Algorithm: head = head.next; delete old head (no temp required for this tutorial)
        MemoryNode toDelete = head;
        MemoryNode newHead = head.next;
        Bounds b = toDelete.localToScene(toDelete.getBoundsInLocal());
        double centerX = (b.getMinX() + b.getMaxX()) / 2.0;
        double centerY = (b.getMinY() + b.getMaxY()) / 2.0;

        Label headPointer = new Label("HEAD → " + (newHead != null ? newHead.getData() : "NULL"));
        headPointer.setStyle("-fx-background-color: #44AAFF; -fx-text-fill: white; -fx-padding: 8 12; -fx-font-size: 14;");
        headPointer.setFont(AssetLoader.loadFont("/fonts/CinzelDecorative-Regular.ttf", 14));
        fxLayer.getChildren().add(headPointer);
        Point2D headStart = fxLayer.sceneToLocal(centerX, centerY + 60);
        headPointer.setTranslateX(headStart.getX() - 60);
        headPointer.setTranslateY(headStart.getY());
        headPointer.setOpacity(0);

        FadeTransition headIn = new FadeTransition(Duration.millis(300), headPointer);
        headIn.setFromValue(0);
        headIn.setToValue(1.0);
        PauseTransition hold = new PauseTransition(Duration.millis(600));

        Runnable performDeletion = () -> {
            fxLayer.getChildren().remove(headPointer);
            toDelete.showCorruptedFx(fxLayer);
            head = newHead;
            current = head;
            nodes.remove(toDelete);
            track.getChildren().remove(toDelete);
            syncChainAboveFromList();
            layoutNodesForInsertion();
            track.layout();
            Platform.runLater(() -> {
                rebuildPointersForInsertion();
                if (nullLabel != null) positionNull(nullLabel);
                if (headLabel != null) positionHead(headLabel);
            });
            if (current != null) setCurrent(current);
            getGameState().addScore(SCORE_DELETE);
            didDeleteOnce = true;
            didDeleteHead = true;
            updateDeletionPaper();
            checkVictory();
            PauseTransition delay = new PauseTransition(Duration.seconds(1));
            delay.setOnFinished(ev -> {
                Platform.runLater(() -> SnapshotUtil.captureNode(getBlurTarget(), 2.0, Color.TRANSPARENT, learnSnaps::add));
                Button nextBtn = UiUtil.btn("Next");
                nextBtn.setOnAction(e2 -> {
                    dialogue.hide();
                    showMiddleDeletionInstructions();
                });
                say("The first crystal vanishes... the chain now begins anew from the second memory fragment.", null, nextBtn);
            });
            delay.play();
        };

        SequentialTransition seq = new SequentialTransition(headIn, hold);
        seq.setOnFinished(e -> {
            if (newHead != null) {
                Bounds b2 = newHead.localToScene(newHead.getBoundsInLocal());
                double endX = (b2.getMinX() + b2.getMaxX()) / 2.0;
                double endY = (b2.getMinY() + b2.getMaxY()) / 2.0 - 40;
                Point2D headEnd = fxLayer.sceneToLocal(endX, endY);
                double byX = (headEnd.getX() - 60) - headPointer.getTranslateX();
                double byY = headEnd.getY() - headPointer.getTranslateY();
                TranslateTransition moveHead = new TranslateTransition(Duration.millis(550), headPointer);
                moveHead.setByX(byX);
                moveHead.setByY(byY);
                moveHead.setOnFinished(ev2 -> performDeletion.run());
                moveHead.play();
            } else {
                performDeletion.run();
            }
        });
        seq.play();
    }

    // ---------- Victory ----------

    private void checkVictory() {
        if (victoryShown) return;
        if (didInsertHead && didInsertMiddle && didInsertTail && didDeleteHead && didDeleteMiddle && didDeleteTail) {
            ensureTailAndNullVisible();
            PauseTransition showConnection = new PauseTransition(Duration.seconds(1.5));
            showConnection.setOnFinished(e -> showTutorialCompleteThenVictory());
            showConnection.play();
        }
    }

    private void showVictory() {
        victoryShown = true;
        traversalActive = false;
        stopMusic();
        //playOnce("/Music_binarysearch/emotional-cinematic-inspirational-piano-main-10524.mp3");
        playOnce(AssetLoader.L4_MUSIC_VICTORY);//<--------CHANGED LINE
        Region dim = new Region();
        dim.setStyle("-fx-background-color: rgba(0,0,0,0.65);");

        ImageView lightBurst = null;
        try {
             lightBurst = new ImageView(AssetLoader.image(AssetLoader.L4_VICTORY_LIGHT));//<---------CHANGED LINE
            //lightBurst = new ImageView(AssetLoader.image("/light_burst_victory.png"));
            lightBurst.setPreserveRatio(true);
            lightBurst.setFitWidth(sceneW * 0.7);
            lightBurst.setOpacity(0.85);
        } catch (Exception ignored) {}

        //ImageView banner = new ImageView(AssetLoader.image("/victory_banner_medieval.png"));
        ImageView banner = new ImageView(AssetLoader.image(AssetLoader.L4_VICTORY_BANNER));//<------CHANGED LINE
        banner.setPreserveRatio(true);
        banner.setFitWidth(420);

        Label msg = new Label("You have mastered traversal, insertion, and deletion.\nThe Chain of Memory is restored!");
        msg.setTextFill(Color.web("#ffe8b0"));
        msg.setFont(AssetLoader.loadFont("/fonts/CinzelDecorative-Regular.ttf", 24));
        msg.setWrapText(true);
        msg.setAlignment(Pos.CENTER);

        Button claim = UiUtil.btn("Claim Victory");
        claim.setOnAction(e -> showScoreBoard());

        VBox box = new VBox(16, banner, msg, claim);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(24));
        box.setStyle("-fx-background-color: rgba(20,16,10,0.65); -fx-background-radius: 16;");

        victoryOverlay = new StackPane(dim, box);
        if (lightBurst != null) {
            victoryOverlay.getChildren().add(0, lightBurst);
            StackPane.setAlignment(lightBurst, Pos.CENTER);
        }
        fxLayer.getChildren().add(victoryOverlay);
        StackPane.setAlignment(victoryOverlay, Pos.CENTER);

        FadeTransition ft = new FadeTransition(Duration.millis(260), victoryOverlay);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        ft.play();
    }

    private void showScoreBoard() {
        if (victoryOverlay != null) {
            fxLayer.getChildren().remove(victoryOverlay);
            victoryOverlay = null;
        }
        showLevel4VictoryBoard();
    }

    private void showLevel4Learnings() {
        var imgs = learnSnaps.all();
        List<LearningSnap> snaps = new ArrayList<>();

        if (imgs.size() > 0 && imgs.get(0) != null) {
            snaps.add(new LearningSnap(imgs.get(0),
                    "Traversal:\nHEAD → A → B → C → D → NULL\n\nYou collected 4 crystals in order. Each node links to the next (→). Singly linked list: one direction only."
            ));
        }
        if (imgs.size() > 1 && imgs.get(1) != null) {
            snaps.add(new LearningSnap(imgs.get(1),
                    "Insert at head:\nBEFORE: HEAD → A → …\nAFTER:  HEAD → [NEW] → A → …\n\nThe new node became HEAD. Pointer update: HEAD → new, new → old first. O(1)."
            ));
        }
        if (imgs.size() > 2 && imgs.get(2) != null) {
            snaps.add(new LearningSnap(imgs.get(2),
                    "Insert in the middle:\nBEFORE: … → prev → next → …\nSTEPS:  prev → new → next\n        (break prev→next, add prev→new, new→next)\n\nThe list grew without moving other nodes."
            ));
        }
        if (imgs.size() > 3 && imgs.get(3) != null) {
            snaps.add(new LearningSnap(imgs.get(3),
                    "Insert at tail:\nBEFORE: … → last → NULL\nAFTER:  … → last → [NEW] → NULL\n\nlast → new, new → NULL. O(1) with a tail pointer."
            ));
        }
        if (imgs.size() > 4 && imgs.get(4) != null) {
            snaps.add(new LearningSnap(imgs.get(4),
                    "Delete from head:\nBEFORE: HEAD → A → B → …\nAFTER:  HEAD → B → …\n\nSteps: HEAD moves to next (HEAD = head.next); old first node freed. O(1)."
            ));
        }
        if (imgs.size() > 5 && imgs.get(5) != null) {
            snaps.add(new LearningSnap(imgs.get(5),
                    "Delete from middle (3 phases):\n1. DISCONNECT: prev ⤏ target (break link)\n2. RECONNECT: prev → next (skip target)\n3. DELETE: remove target\n\nBefore: … → prev → target → next → …\nAfter:  … → prev → next → …"
            ));
        }
        if (imgs.size() > 6 && imgs.get(6) != null) {
            snaps.add(new LearningSnap(imgs.get(6),
                    "Delete from tail:\nBEFORE: … → secondLast → last → NULL\nAFTER:  … → secondLast → NULL\n\nSteps: break secondLast → last; set secondLast → NULL; remove last. O(1) with tail pointer."
            ));
        }

        if (imgs.size() > 0 && imgs.get(0) != null) {
            snaps.add(new LearningSnap(imgs.get(0),
                    "Summary:\n• Chain: HEAD → node₁ → node₂ → … → NULL\n• Pointers connect nodes; traversal is sequential.\n• Insert/delete at ends: O(1); middle: find node then link."
            ));
        } else {
            snaps.add(LearningSnap.textOnly(
                    "• Chain: HEAD → … → NULL. Pointers connect nodes; insertion/deletion at ends O(1), traversal sequential."
            ));
        }
        snaps.add(LearningSnap.textOnly(
                "Doubly linked list: each node has prev and next.\n\n" +
                        "Node: [prev] [data] [next]. First node’s prev is NULL; last node’s next is NULL.\n\n" +
                        "You can move forward (HEAD to tail) or backward (tail to HEAD).\n\n" +
                        "To delete a node you have in hand: link its prev to its next and its next back to prev. No need to search for the node before it—so deletion is O(1) when you have the node.\n\n" +
                        "Unlike an array, you cannot jump directly to index i; you must walk node by node. But the list is dynamic: you can grow or shrink it without shifting other elements.\n\n" +
                        "Cost: one extra pointer per node. Use it when you need to go backwards or delete quickly by reference."
        ));

        if (snaps.isEmpty()) {
            snaps.add(LearningSnap.textOnly("You mastered traversal, insertion, and deletion in a singly linked list. Each step used pointers to connect nodes from HEAD to NULL."));
        }
        LearningsBoard board = new LearningsBoard(
                getBlurTarget(),
                AssetLoader.image(AssetLoader.LEARNING_BOARD),
                snaps,
                () -> {
                    stopMusic();
                    learnSnaps.clear();
                    goToLevelSelectAfterCompletion(3);
                },
                true
        );
        addToStatusLayer(board);
        board.show();
    }

    @Override
    protected void goToLevelSelect() {
        stopMusic();
        super.goToLevelSelect();
    }

    @Override
    protected void showGameOverImmediate() {
        dialogue.hide();
        traversalActive = false;
        victoryShown = true;

        Region dim = new Region();
        dim.setStyle("-fx-background-color: rgba(0,0,0,0.65);");

        Label msg = new Label("The Chain of Memory has broken.\nGame Over.");
        msg.setTextFill(Color.web("#FFB0B0"));
        msg.setFont(AssetLoader.loadFont("/fonts/CinzelDecorative-Bold.ttf", 36));
        msg.setAlignment(Pos.CENTER);
        msg.setTextAlignment(TextAlignment.CENTER);
        msg.setWrapText(true);
        msg.setMaxWidth(500);

        Button retry = UiUtil.btn("RETRY");
        Button quit = UiUtil.btn("Quit to Level Select");

        HBox buttons = new HBox(14, retry, quit);
        buttons.setAlignment(Pos.CENTER);

        VBox box = new VBox(18, msg, buttons);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(30));
        box.setStyle("-fx-background-color: rgba(20,16,10,0.38); -fx-background-radius: 16;");

        StackPane over = new StackPane(dim, box);
        retry.setOnAction(e -> {
            getStatusLayer().getChildren().remove(over);
            restartThisLevel();
        });
        quit.setOnAction(e -> {
            getStatusLayer().getChildren().remove(over);
            goToLevelSelect();
        });

        addToStatusLayer(over);
        StackPane.setAlignment(over, Pos.CENTER);
    }

    // ---------- Paginated dialogue (fit long text in box) ----------

    private static final int MAX_CHARS_PER_PAGE = 120;

    private List<String> splitIntoPages(String text, int maxChars) {
        List<String> pages = new ArrayList<>();
        if (text == null || text.isEmpty()) return pages;
        String[] sentences = text.split("\n");
        StringBuilder currentPage = new StringBuilder();
        for (String sentence : sentences) {
            if (currentPage.length() + sentence.length() + 1 > maxChars) {
                if (currentPage.length() > 0) {
                    pages.add(currentPage.toString().trim());
                    currentPage = new StringBuilder();
                }
            }
            currentPage.append(sentence).append("\n");
        }
        if (currentPage.length() > 0) pages.add(currentPage.toString().trim());
        return pages;
    }

    private void showPaginatedDialogue(List<String> pages, int currentIndex, Button... finalButtons) {
        if (pages == null || currentIndex < 0 || currentIndex >= pages.size()) return;
        dialogue.hide();
        if (currentIndex == pages.size() - 1) {
            say(pages.get(currentIndex), null, finalButtons);
        } else {
            Button continueBtn = UiUtil.btn("Continue");
            continueBtn.setOnAction(e -> showPaginatedDialogue(pages, currentIndex + 1, finalButtons));
            say(pages.get(currentIndex), null, continueBtn);
        }
    }

    private void sayPaginated(String fullText, Button... finalButtons) {
        dialogue.hide();
        List<String> pages = splitIntoPages(fullText, MAX_CHARS_PER_PAGE);
        if (pages.isEmpty()) return;
        if (pages.size() == 1) {
            say(pages.get(0), null, finalButtons);
        } else {
            showPaginatedDialogue(pages, 0, finalButtons);
        }
    }

    // ---------- Audio ----------

    private void preloadIntroMusic() {
        try {
            java.net.URL url = getClass().getResource(INTRO_MUSIC_PATH);
            if (url != null) introMusicPreloaded = new AudioClip(url.toExternalForm());
        } catch (Exception ignored) {}
    }

    private void playLoop(String path) {
        try {
            if (musicClip != null) musicClip.stop();
            java.net.URL url = getClass().getResource(path);
            if (url == null) return;
            musicClip = new AudioClip(url.toExternalForm());
            musicClip.setCycleCount(AudioClip.INDEFINITE);
            musicClip.setVolume(MUSIC_VOLUME);
            musicClip.play();
        } catch (Exception ignored) {}
    }

    private void playOnce(String path) {
        try {
            if (musicClip != null) musicClip.stop();
            java.net.URL url = getClass().getResource(path);
            if (url == null) return;
            musicClip = new AudioClip(url.toExternalForm());
            musicClip.setCycleCount(1);
            musicClip.setVolume(0.4);
            musicClip.play();
        } catch (Exception ignored) {}
    }

    private void playOneShot(String path) {
        try {
            java.net.URL url = getClass().getResource(path);
            if (url == null) return;
            AudioClip clip = new AudioClip(url.toExternalForm());
            clip.setVolume(MUSIC_VOLUME);
            clip.play();
        } catch (Exception ignored) {}
    }

    private void stopMusic() {
        try {
            if (musicClip != null) musicClip.stop();
        } catch (Exception ignored) {}
    }

    // ---------- MemoryNode ----------

    private static class MemoryNode extends StackPane {
        private MemoryNode next;
        private final ImageView visual;
        private final Label label;
        private Line pointerLine;
        private Timeline pulseTimeline;
        private TranslateTransition floatUpTransition;

        MemoryNode(String data) {
            visual = loadOrbImage(96);
            visual.setPreserveRatio(true);

            label = new Label(data);
            label.setTextFill(Color.web("#FFFFFF"));
            label.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.95), 8, 0, 1, 1);");
            label.setFont(AssetLoader.loadFont("/fonts/CinzelDecorative-Regular.ttf", 22));

            getChildren().addAll(visual, label);
            setAlignment(Pos.CENTER);
            setPickOnBounds(true);
            setMinSize(120, 120);
            setCursor(Cursor.HAND);

            setOnMouseEntered(e -> setStyle("-fx-effect: dropshadow(three-pass-box, rgba(200,255,200,0.5), 14, 0, 0, 0);"));
            setOnMouseExited(e -> {
                if (pulseTimeline == null && floatUpTransition == null) setStyle(null);
            });
        }

        String getData() {
            return label.getText();
        }

        void setCurrent(boolean selected) {
            if (pulseTimeline != null) {
                pulseTimeline.stop();
                pulseTimeline = null;
            }
            if (floatUpTransition != null) {
                floatUpTransition.stop();
                floatUpTransition = null;
            }
            if (selected) {
                setScaleX(1.18);
                setScaleY(1.18);
                setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,255,140,0.9), 24, 1, 0, 0);");
                setTranslateY(0);
                floatUpTransition = new TranslateTransition(Duration.millis(500), this);
                floatUpTransition.setByY(-15);
                floatUpTransition.setCycleCount(1);
                floatUpTransition.setOnFinished(ev -> floatUpTransition = null);
                floatUpTransition.play();
                pulseTimeline = new Timeline(
                        new KeyFrame(Duration.ZERO, new KeyValue(scaleXProperty(), 1.18), new KeyValue(scaleYProperty(), 1.18)),
                        new KeyFrame(Duration.millis(400), new KeyValue(scaleXProperty(), 1.25), new KeyValue(scaleYProperty(), 1.25)),
                        new KeyFrame(Duration.millis(800), new KeyValue(scaleXProperty(), 1.18), new KeyValue(scaleYProperty(), 1.18))
                );
                pulseTimeline.setCycleCount(Animation.INDEFINITE);
                pulseTimeline.setAutoReverse(false);
                pulseTimeline.play();
            } else {
                setScaleX(1.0);
                setScaleY(1.0);
                setTranslateY(0);
                setStyle(null);
            }
        }

        /** Connection mode / error glow: "blue", "yellow", "green", "red", or null to clear. */
        void setConnectionGlow(String type) {
            if (pulseTimeline != null) {
                pulseTimeline.stop();
                pulseTimeline = null;
            }
            if (type == null) {
                setScaleX(1.0);
                setScaleY(1.0);
                setStyle(null);
                return;
            }
            switch (type) {
                case "blue":
                    setStyle("-fx-effect: dropshadow(three-pass-box, rgba(50,150,255,0.9), 22, 1, 0, 0);");
                    break;
                case "yellow":
                    setStyle("-fx-effect: dropshadow(three-pass-box, rgba(255,220,0,0.9), 22, 1, 0, 0);");
                    break;
                case "green":
                    setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,255,100,0.9), 22, 1, 0, 0);");
                    break;
                case "red":
                    setStyle("-fx-effect: dropshadow(three-pass-box, rgba(255,80,80,0.95), 24, 1, 0, 0);");
                    break;
                default:
                    setStyle(null);
            }
            setScaleX(1.1);
            setScaleY(1.1);
        }

        void showCorruptedFx(StackPane fxLayer) {
            if (fxLayer == null || getScene() == null) return;

            Bounds bScene = localToScene(getBoundsInLocal());
            Point2D center = new Point2D(
                    (bScene.getMinX() + bScene.getMaxX()) / 2.0,
                    (bScene.getMinY() + bScene.getMaxY()) / 2.0
            );

            ImageView mistView = new ImageView(AssetLoader.image(AssetLoader.L4_MIST));//<------CHANGED LINE
            //ImageView mistView = new ImageView(AssetLoader.image("/mist.png"));
            mistView.setPreserveRatio(true);
            mistView.setFitWidth(220);
            mistView.setOpacity(0);

            fxLayer.getChildren().add(mistView);
            StackPane.setAlignment(mistView, Pos.TOP_LEFT);

            Platform.runLater(() -> {
                Point2D p = fxLayer.sceneToLocal(center);
                mistView.setTranslateX(p.getX() - 110);
                mistView.setTranslateY(p.getY() - 110);
            });

            FadeTransition mFade = new FadeTransition(Duration.millis(350), mistView);
            mFade.setFromValue(0.0);
            mFade.setToValue(0.85);

            FadeTransition mOut = new FadeTransition(Duration.millis(400), mistView);
            mOut.setFromValue(0.85);
            mOut.setToValue(0.0);
            mOut.setDelay(Duration.millis(350));
            SequentialTransition seq = new SequentialTransition(mFade, mOut);
            seq.setOnFinished(e -> fxLayer.getChildren().remove(mistView));
            seq.play();
        }
    }
}


