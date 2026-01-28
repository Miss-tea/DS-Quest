package ui;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Places the board by absolute screen position and overlays by exact Figma image pixels.
 * - Board (entire image) is placed at (BOARD_X, BOARD_Y) relative to this control's top-left.
 * - Stars/status/content are positioned in IMAGE coordinates, then the whole canvas is scaled.
 */
public class LevelStatusBoard extends StackPane {

    public enum Mode { SURVIVED, GAME_OVER }

    // =========== SCREEN POSITION OF THE WHOLE BOARD ===========
    // Top-left of the entire board (art) relative to the overlay control
    private static final double BOARD_X = 384;  // screen coordinate X
    private static final double BOARD_Y = 7;   // screen coordinate Y (can be negative)

    // =========== DISPLAY SCALING ===========
    // Final displayed width of the board art (the image will be scaled uniformly from intrinsic size)
    private static final double BOARD_FIT_WIDTH = 520;

    // =========== FIGMA ANCHORS IN IMAGE PIXELS ===========
    // Stars row (centered at this point on the rock)
    private static final double STAR_CX   = 215;
    private static final double STAR_Y    = 115;

    // Status text (centered at this point on the red ribbon)
    private static final double STATUS_CX = 150;
    private static final double STATUS_Y  = 145;

    // White paper content (top-left) and a usable width (adjust once to match your paper interior)
    private static final double PAPER_X   = 130;
    private static final double PAPER_Y   = 220;
    private static final double PAPER_W   = 200;

    // Stars visual/animation
    private static final int STAR_COUNT = 3;
    private static final double STAR_SIZE = 36;
    private static final int STAR_STAGGER_MS = 120;
    private static final int STAR_POP_MS = 260;

    private final Node blurTarget;

    // Layers
    private final Region dimLayer;     // full-screen dimmer
    private final Pane absoluteLayer;  // absolute layer that fills parent; we place the board here at (BOARD_X, BOARD_Y)

    // Board canvas built in IMAGE coordinates, then scaled
    private final Pane canvas;         // size = image intrinsic (imgW x imgH)
    private final Pane boardHolder;    // holds the canvas and is placed at (BOARD_X, BOARD_Y)

    // Background art
    private final ImageView boardImage;

    // Overlays
    private final HBox starsRow;
    private final Label statusLabel;   // SURVIVED / GAME OVER
    private final VBox contentBox;     // score + message + done

    private final Label scoreLabel;
    private final Label messageLabel;
    private final Button doneButton;

    private final Image starImg;
    private final List<ImageView> starViews = new ArrayList<>(STAR_COUNT);

    // Intrinsic image size
    private final double imgW;
    private final double imgH;

    public LevelStatusBoard(Node blurTarget, Button doneButtonTemplate) {
        this.blurTarget = blurTarget;

        // ---------- Dim layer (fills parent) ----------
        dimLayer = new Region();
        dimLayer.setStyle("-fx-background-color: rgba(0,0,0,0.45);");
        dimLayer.setPickOnBounds(true);

        // ---------- Absolute layer (fills parent; we position board by layoutX/Y inside) ----------
        absoluteLayer = new Pane();
        absoluteLayer.setPickOnBounds(false); // interact only on visible nodes

        // Keep both filling our parent
        parentProperty().addListener((obs, o, p) -> {
            if (p instanceof Region r) {
                bindTo(r, dimLayer);
                bindTo(r, absoluteLayer);
            }
        });

        // ---------- Load board image synchronously to get intrinsic size ----------
        Image img = new Image("/levelstatus.png", false);
        imgW = img.getWidth();
        imgH = img.getHeight();
        if (imgW <= 0 || imgH <= 0) {
            throw new IllegalStateException("levelstatus.png failed to load or has zero size.");
        }

        boardImage = new ImageView(img);
        boardImage.setSmooth(true);

        // ---------- Canvas in IMAGE coordinate space ----------
        canvas = new Pane();
        canvas.setPrefSize(imgW, imgH);
        canvas.setMinSize(imgW, imgH);
        canvas.setMaxSize(imgW, imgH);

        // Background image at (0,0) image space
        boardImage.setLayoutX(0);
        boardImage.setLayoutY(0);
        canvas.getChildren().add(boardImage);

        // ---------- Overlays (built in IMAGE pixels) ----------
        starImg = new Image("/star.png", true);

        starsRow = new HBox(8);
        starsRow.setAlignment(Pos.CENTER);
        buildStars();
        canvas.getChildren().add(starsRow);

        statusLabel = new Label();
        statusLabel.setTextFill(Color.WHITE); // Status text white
        statusLabel.setFont(Font.loadFont(
                getClass().getResourceAsStream("/fonts/CinzelDecorative-Bold.ttf"),
                25
        ));
        statusLabel.setEffect(new DropShadow(6, Color.color(0,0,0,0.25)));
        canvas.getChildren().add(statusLabel);

        scoreLabel = new Label();
        scoreLabel.setFont(Font.loadFont(
                getClass().getResourceAsStream("/fonts/CinzelDecorative-Regular.ttf"),
                22
        ));
        scoreLabel.setTextFill(Color.web("#3a2f21"));

        messageLabel = new Label();
        messageLabel.setWrapText(true);
        messageLabel.setAlignment(Pos.TOP_RIGHT);
        messageLabel.setFont(Font.loadFont(
                getClass().getResourceAsStream("/fonts/CinzelDecorative-Bold.ttf"),
                13
        ));
        messageLabel.setTextFill(Color.web("#4a3b2a"));
        messageLabel.setMaxWidth(PAPER_W);


            doneButton = UiUtil.btn("DONE");
        doneButton.setOnAction(e -> hide());

        contentBox = new VBox(8, scoreLabel, messageLabel, doneButton);
        contentBox.setAlignment(Pos.TOP_CENTER);
        contentBox.setPrefWidth(PAPER_W);
        contentBox.setMaxWidth(PAPER_W);
        canvas.getChildren().add(contentBox);

        // ---------- Position overlays in IMAGE pixel space ----------
        positionOverlaysInImagePixels();

        // ---------- Scale canvas uniformly to desired display width ----------
        double scale = BOARD_FIT_WIDTH / imgW;
        canvas.setScaleX(scale);
        canvas.setScaleY(scale);

        // ---------- Holder (positioned absolutely on screen) ----------
        boardHolder = new Pane(canvas);
        boardHolder.setPickOnBounds(false);
        // Place the whole board at the requested screen coordinates:
        boardHolder.setLayoutX(BOARD_X);
        boardHolder.setLayoutY(BOARD_Y);

        // ---------- Put everything together ----------
        absoluteLayer.getChildren().add(boardHolder);

        setAlignment(Pos.TOP_LEFT); // we use absolute coordinates inside absoluteLayer
        getChildren().setAll(dimLayer, absoluteLayer);

        setVisible(false);
        setOpacity(0.0);
        setCache(true);
        setCacheHint(CacheHint.SPEED);
        setCursor(Cursor.DEFAULT);
    }

    // Utility to bind a child Region's size to parent Region
    private static void bindTo(Region parent, Region child) {
        child.minWidthProperty().bind(parent.widthProperty());
        child.prefWidthProperty().bind(parent.widthProperty());
        child.maxWidthProperty().bind(parent.widthProperty());
        child.minHeightProperty().bind(parent.heightProperty());
        child.prefHeightProperty().bind(parent.heightProperty());
        child.maxHeightProperty().bind(parent.heightProperty());
    }

    private Button cloneButton(Button src) {
        Button b = new Button(src.getText());
        b.getStyleClass().addAll(src.getStyleClass());
        b.setPrefWidth(src.getPrefWidth());
        b.setPrefHeight(src.getPrefHeight());
        b.setMnemonicParsing(src.isMnemonicParsing());
        b.setDisable(src.isDisable());
        b.setOpacity(src.getOpacity());
        return b;
    }

    // -------------------- Public API --------------------

    public void showSurvived(int score, String strategyMsg, int maxScore) {
        if (maxScore <= 0) maxScore = Math.max(1, score);
        show(Mode.SURVIVED, score, strategyMsg, maxScore);
    }

    public void showSurvived(int score, String strategyMsg) {
        show(Mode.SURVIVED, score, strategyMsg, Math.max(1, score));
    }

    public void showGameOver(int score, String msg) {
        show(Mode.GAME_OVER, score, msg, 1);
    }

    private void show(Mode mode, int score, String msg, int maxScore) {
        statusLabel.setText(mode == Mode.SURVIVED ? "SURVIVED" : "GAME OVER");
        statusLabel.setTextFill(Color.WHITE);
        scoreLabel.setText("SCORE: " + score);
        messageLabel.setText(msg != null ? msg : "");

        int stars = (mode == Mode.GAME_OVER) ? 0 : computeStars(score, maxScore);
        resetStarsDim();
        if (stars > 0) animateStars(stars);

        UiBlur.apply(blurTarget, true);

        // Intro animation
        setVisible(true);
        boardHolder.setScaleX(0.92);
        boardHolder.setScaleY(0.92);

        Timeline scale = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(boardHolder.scaleXProperty(), 0.92),
                        new KeyValue(boardHolder.scaleYProperty(), 0.92)),
                new KeyFrame(Duration.millis(220),
                        new KeyValue(boardHolder.scaleXProperty(), 1.0),
                        new KeyValue(boardHolder.scaleYProperty(), 1.0))
        );
        FadeTransition fade = new FadeTransition(Duration.millis(240), this);
        fade.setFromValue(0);
        fade.setToValue(1);
        new ParallelTransition(scale, fade).play();
    }

    public void hide() {
        FadeTransition fade = new FadeTransition(Duration.millis(160), this);
        fade.setToValue(0);
        fade.setOnFinished(e -> {
            setVisible(false);
            UiBlur.apply(blurTarget, false);
        });
        fade.play();
    }

    // -------------------- Stars logic --------------------

    private void buildStars() {
        starViews.clear();
        starsRow.getChildren().clear();
        for (int i = 0; i < STAR_COUNT; i++) {
            ImageView iv = new ImageView(starImg);
            iv.setPreserveRatio(true);
            iv.setFitWidth(STAR_SIZE);
            iv.setSmooth(true);
            iv.setEffect(new DropShadow(6, Color.rgb(0, 0, 0, 0.25)));
            starViews.add(iv);
            starsRow.getChildren().add(iv);
        }
        resetStarsDim();
    }

    private void resetStarsDim() {
        for (ImageView iv : starViews) {
            setStarLit(iv, false);
            iv.setOpacity(0.55);
            iv.setScaleX(1.0);
            iv.setScaleY(1.0);
        }
    }

    private void setStarLit(ImageView iv, boolean lit) {
        if (lit) {
            iv.setOpacity(1.0);
            iv.setEffect(new DropShadow(6, Color.rgb(0, 0, 0, 0.25)));
        } else {
            ColorAdjust gray = new ColorAdjust();
            gray.setSaturation(-1.0);
            gray.setBrightness(-0.25);
            DropShadow ds = new DropShadow(6, Color.rgb(0, 0, 0, 0.25));
            ds.setInput(gray);
            iv.setEffect(ds);
        }
    }

    private int computeStars(int score, int maxScore) {
        double pct = (maxScore <= 0) ? 0.0 : (double) score / (double) maxScore;
        if (pct >= 1.0) return 3;
        if (pct >= 0.60) return 2;
        if (pct >= 0.30) return 1;
        return 0;
    }

    private void animateStars(int count) {
        resetStarsDim();
        for (int i = 0; i < count && i < starViews.size(); i++) {
            ImageView iv = starViews.get(i);
            setStarLit(iv, true);
            iv.setOpacity(0.0);
            iv.setScaleX(0.6);
            iv.setScaleY(0.6);

            Timeline pop = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(iv.opacityProperty(), 0.0),
                            new KeyValue(iv.scaleXProperty(), 0.6),
                            new KeyValue(iv.scaleYProperty(), 0.6)),
                    new KeyFrame(Duration.millis((int)(STAR_POP_MS * 0.62)),
                            new KeyValue(iv.opacityProperty(), 1.0),
                            new KeyValue(iv.scaleXProperty(), 1.12),
                            new KeyValue(iv.scaleYProperty(), 1.12)),
                    new KeyFrame(Duration.millis(STAR_POP_MS),
                            new KeyValue(iv.scaleXProperty(), 1.0),
                            new KeyValue(iv.scaleYProperty(), 1.0))
            );
            pop.setDelay(Duration.millis(120 + i * STAR_STAGGER_MS));
            pop.play();
        }
        for (int i = count; i < starViews.size(); i++) {
            ImageView iv = starViews.get(i);
            setStarLit(iv, false);
            iv.setOpacity(0.55);
            iv.setScaleX(1.0);
            iv.setScaleY(1.0);
        }
    }

    // -------------------- Absolute IMAGE-pixel placement --------------------

    private void positionOverlaysInImagePixels() {
        // Stars row centered at (STAR_CX, STAR_Y)
        starsRow.applyCss();
        starsRow.autosize();
        double starsW = starsRow.prefWidth(-1);
        double starsH = starsRow.prefHeight(-1);
        starsRow.resizeRelocate(STAR_CX - starsW / 2.0, STAR_Y - starsH / 2.0, starsW, starsH);

        // Status label centered at (STATUS_CX, STATUS_Y)
        statusLabel.applyCss();
        statusLabel.autosize();
        double stW = statusLabel.prefWidth(-1);
        double stH = statusLabel.prefHeight(-1);
        statusLabel.resizeRelocate(STATUS_CX - stW / 2.0, STATUS_Y - stH / 2.0, stW, stH);

        // Content at PAPER_X, PAPER_Y with width PAPER_W
        contentBox.applyCss();
        contentBox.setLayoutX(PAPER_X);
        contentBox.setLayoutY(PAPER_Y);
    }
}