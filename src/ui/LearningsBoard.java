package ui;

import core.AssetLoader;
import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.util.List;

/**
 * Learnings overlay board:
 * - Background: learningsboard.png (centered)
 * - Title: "LEARNINGS"
 * - Modes:
 *   (a) list mode (default): vertical list of rows; each row is [snapshot | caption]
 *   (b) sequential mode: shows one snapshot+caption at a time with Back/Next navigation
 * - Transparent rows + transparent ScrollPane viewport (no white panels)
 * - Staggered pop-in animation per row (list mode)
 */
public class LearningsBoard extends StackPane {

    private final Node blurTarget;
    private final ImageView boardBg;
    private final VBox contentBox = new VBox(18);
    private final Button nextBtn;

    // Layout tuning
    // Layout tuning
    private static final double BOARD_FIT_WIDTH = 900; // was 900
    private static final double ROW_WIDTH       = 680;  // was 680
    private static final double IMAGE_WIDTH     = 260;  // was 260
    private static final double CAPTION_WRAP    = 360;  // was 360// wrapping width for text (list mode)

    // Sequential mode UI refs
    private final boolean sequential;
    private int seqIndex = 0;
    private List<LearningSnap> seqSnaps;
    private VBox seqPane;
    private Text seqCaption;
    private ImageView seqImage;
    private Text seqProgress;
    private Button backBtn;

    // Old constructor (backward compatible) -> defaults to list mode
    public LearningsBoard(Node blurTarget, javafx.scene.image.Image boardBackground, List<LearningSnap> snaps, Runnable onNext) {
        this(blurTarget, boardBackground, snaps, onNext, false);
    }

    // New constructor with sequential flag
    public LearningsBoard(Node blurTarget, javafx.scene.image.Image boardBackground, List<LearningSnap> snaps, Runnable onNext, boolean sequential) {
        this.blurTarget = blurTarget;
        this.sequential = sequential;

        // Dim layer (over the game; under the board)
        Region dim = new Region();
        dim.setStyle("-fx-background-color: rgba(0,0,0,0.55);");

        // Board background image (parchment)
        boardBg = new ImageView(boardBackground);
        boardBg.setPreserveRatio(true);
        boardBg.setSmooth(true);
        boardBg.setFitWidth(BOARD_FIT_WIDTH);

        // Title
        Text title = new Text("LEARNINGS");
        Font titleFont = AssetLoader.loadFont("/fonts/CinzelDecorative-Bold.ttf", 36);
        title.setFont(titleFont);
        title.setFill(Color.WHITE);

        if (!sequential) {
            // === LIST MODE (existing behavior) ===
            VBox list = new VBox(20);
            list.setAlignment(Pos.TOP_CENTER);
            list.setBackground(Background.EMPTY);

            for (int i = 0; i < snaps.size(); i++) {
                Node row = makeRow(snaps.get(i));
                applyPopAnimation(row, i);   // ⭐ staggered pop-in
                list.getChildren().add(row);
            }

            // ScrollPane (transparent viewport/background)
            ScrollPane scroll = new ScrollPane(list);
            scroll.setFitToWidth(true);
            scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-padding: 0;");
            scroll.skinProperty().addListener((obs, oldSkin, skin) -> {
                Node vp = scroll.lookup(".viewport");
                if (vp instanceof Region r) {
                    r.setBackground(Background.EMPTY);
                    r.setStyle("-fx-background-color: transparent;");
                }
                Node content = scroll.getContent();
                if (content instanceof Region r2) {
                    r2.setBackground(Background.EMPTY);
                    r2.setStyle("-fx-background-color: transparent;");
                }
            });

            nextBtn = UiUtil.btn("Next");
            nextBtn.setOnAction(e -> {
                hide();
                if (onNext != null) onNext.run();
            });

            contentBox.setAlignment(Pos.TOP_CENTER);
            contentBox.setPadding(new Insets(28, 32, 28, 32));
            contentBox.getChildren().addAll(title, scroll, nextBtn);
            contentBox.setBackground(Background.EMPTY);

            contentBox.maxWidthProperty().bind(boardBg.fitWidthProperty().subtract(80));
            scroll.setMaxWidth(ROW_WIDTH + 40);
        } else {
            // === SEQUENTIAL MODE ===
            this.seqSnaps = snaps;

            // Big image + caption centered
            seqImage = new ImageView();
            seqImage.setPreserveRatio(true);
            seqImage.setSmooth(true);
            seqImage.setFitWidth(BOARD_FIT_WIDTH * 0.50); // larger than list thumbnails

            seqCaption = new Text();
            seqCaption.setFill(Color.web("#3a2f21"));
            seqCaption.setFont(AssetLoader.loadFont("/fonts/Montaga-Regular.ttf", 22));
            seqCaption.setWrappingWidth(BOARD_FIT_WIDTH * 0.55);

            seqProgress = new Text();
            seqProgress.setFill(Color.WHITE);
            seqProgress.setFont(AssetLoader.loadFont("/fonts/CinzelDecorative-Regular.ttf", 18));

            HBox nav = new HBox(12);
            nav.setAlignment(Pos.CENTER);

            backBtn = UiUtil.btn("Back");
            backBtn.setOnAction(e -> {
                if (seqIndex > 0) {
                    seqIndex--;
                    renderSequentialSlide(true);
                }
            });

            nextBtn = UiUtil.btn("Next");
            nextBtn.setOnAction(e -> {
                if (seqIndex < seqSnaps.size() - 1) {
                    seqIndex++;
                    renderSequentialSlide(false);
                } else {
                    hide();
                    if (onNext != null) onNext.run();
                }
            });

            nav.getChildren().addAll(backBtn, nextBtn);

            seqPane = new VBox(16,
                    title,
                    seqImage,
                    seqCaption,
                    seqProgress,
                    nav
            );
            seqPane.setAlignment(Pos.TOP_CENTER);
            seqPane.setBackground(Background.EMPTY);
            seqPane.setPadding(new Insets(28, 32, 28, 32));

            contentBox.setAlignment(Pos.TOP_CENTER);
            contentBox.setBackground(Background.EMPTY);
            contentBox.getChildren().add(seqPane);

            // Render first slide
            renderSequentialSlide(false);
        }

        StackPane boardLayer = new StackPane(boardBg, contentBox);
        boardLayer.setPickOnBounds(false);
        StackPane.setAlignment(boardLayer, Pos.CENTER);

        getChildren().addAll(dim, boardLayer);

        setVisible(false);
        setOpacity(0);
        setPickOnBounds(true);
        setCache(true);
        setCacheHint(CacheHint.SPEED);
    }

    /** One row: [ snapshot | caption ], with transparent background (no white panel). */
    private Node makeRow(LearningSnap snap) {
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10));
        row.setMaxWidth(ROW_WIDTH);
        row.setBackground(Background.EMPTY);
        row.setBorder(Border.EMPTY);

        if (snap.image() != null) {
            ImageView iv = new ImageView(snap.image());
            iv.setPreserveRatio(true);
            iv.setFitWidth(IMAGE_WIDTH);
            iv.setSmooth(true);
            row.getChildren().add(iv);
        }

        Text caption = new Text(snap.message());
        caption.setFill(Color.web("#3a2f21"));
        caption.setFont(AssetLoader.loadFont("/fonts/Montaga-Regular.ttf", 20));
        caption.setWrappingWidth(CAPTION_WRAP);

        VBox captionBox = new VBox(6, caption);
        captionBox.setAlignment(Pos.CENTER_LEFT);
        captionBox.setBackground(Background.EMPTY);
        HBox.setHgrow(captionBox, Priority.ALWAYS);

        row.getChildren().add(captionBox);
        return row;
    }

    // ⭐ Staggered pop-in animation per row (fade + scale)
    private void applyPopAnimation(Node node, int index) {
        node.setOpacity(0.0);
        node.setScaleX(0.90);
        node.setScaleY(0.90);

        FadeTransition fade = new FadeTransition(Duration.millis(220), node);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);

        ScaleTransition scale = new ScaleTransition(Duration.millis(260), node);
        scale.setFromX(0.90);
        scale.setFromY(0.90);
        scale.setToX(1.0);
        scale.setToY(1.0);

        ParallelTransition pop = new ParallelTransition(fade, scale);
        pop.setDelay(Duration.millis(150 + index * 120));  // staggered start
        pop.play();
    }

    // Render a single slide in sequential mode
    private void renderSequentialSlide(boolean reverse) {
        if (!sequential || seqSnaps == null || seqSnaps.isEmpty()) return;
        LearningSnap snap = seqSnaps.get(seqIndex);

        // Image (may be null)
        if (snap.image() != null) {
            seqImage.setImage(snap.image());
            seqImage.setVisible(true);
            seqImage.setManaged(true);
        } else {
            seqImage.setImage(null);
            seqImage.setVisible(false);
            seqImage.setManaged(false);
        }

        seqCaption.setText(snap.message());
        seqProgress.setText((seqIndex + 1) + " / " + seqSnaps.size());

        // Button enable/disable + label
        backBtn.setDisable(seqIndex == 0);
        nextBtn.setText(seqIndex == seqSnaps.size() - 1 ? "Done" : "Next");

        // Small directional slide animation
        double fromX = reverse ? -16 : 16;
        seqPane.setOpacity(0.0);
        seqPane.setTranslateX(fromX);

        Timeline tl = new Timeline(
                new KeyFrame(Duration.millis(160),
                        new KeyValue(seqPane.opacityProperty(), 1.0),
                        new KeyValue(seqPane.translateXProperty(), 0.0)
                )
        );
        tl.play();
    }

    public void show() {
        UiBlur.apply(blurTarget, true);

        FadeTransition fade = new FadeTransition(Duration.millis(240), this);
        fade.setFromValue(0);
        fade.setToValue(1);

        ScaleTransition scale = new ScaleTransition(Duration.millis(240), this);
        scale.setFromX(0.96);
        scale.setFromY(0.96);
        scale.setToX(1);
        scale.setToY(1);

        setVisible(true);
        new ParallelTransition(fade, scale).play();
    }

    public void hide() {
        FadeTransition fade = new FadeTransition(Duration.millis(180), this);
        fade.setToValue(0);
        fade.setOnFinished(e -> {
            setVisible(false);
            UiBlur.apply(blurTarget, false);
        });
        fade.play();
    }
}