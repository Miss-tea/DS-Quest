package ui;

import core.AssetLoader;
import core.GameState;
import javafx.animation.ScaleTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.util.Duration;

public class HUD extends HBox {

    private static HUD CURRENT; // NEW: allow access from levels

    private final Label scoreLbl = new Label();
    private final HBox heartsBox = new HBox(15);
    private final Label titleLbl = new Label(); // <-- added previously

    public HUD(GameState gs) {
        CURRENT = this; // set current HUD instance

        setSpacing(40);
        setAlignment(Pos.TOP_LEFT);
        setPickOnBounds(false);

        // Dynamic title label
        titleLbl.setTextFill(Color.WHITESMOKE);
        titleLbl.setFont(AssetLoader.loadFont("/fonts/CinzelDecorative-Bold.ttf", 32));

        scoreLbl.setTextFill(Color.WHITESMOKE);
        scoreLbl.setFont(AssetLoader.loadFont("/fonts/CinzelDecorative-Regular.ttf", 24));

        heartsBox.setAlignment(Pos.CENTER_LEFT);
        refreshHearts(gs.getHearts());

        gs.scoreProperty().addListener((obs, o, n) -> scoreLbl.setText("SCORE: " + n));
        gs.heartsProperty().addListener((obs, o, n) -> refreshHearts(n.intValue()));

        scoreLbl.setText("SCORE: " + gs.getScore());

        Region spacerLeftCenter = new Region();
        Region spacerCenterRight = new Region();
        HBox.setHgrow(spacerLeftCenter, Priority.ALWAYS);
        HBox.setHgrow(spacerCenterRight, Priority.ALWAYS);

        getChildren().setAll(heartsBox, spacerLeftCenter, titleLbl, spacerCenterRight, scoreLbl);
        setFillHeight(false);
    }

    // Access the current HUD instance
    public static HUD getCurrent() { return CURRENT; }

    // NEW METHOD: allows BaseLevel + Levels to set title
    public void setTitle(String title) {
        titleLbl.setText(title != null ? title : "");
    }

    private void refreshHearts(int count) {
        heartsBox.getChildren().clear();
        for (int i = 0; i < count; i++) {
            ImageView h = AssetLoader.imageView(AssetLoader.HEART, 50, 50, true);
            heartsBox.getChildren().add(h);
        }
    }

    // NEW: quick pulse animation on hearts box
    public void pulseHearts() {
        ScaleTransition st = new ScaleTransition(Duration.millis(160), heartsBox);
        st.setFromX(1.0); st.setToX(1.12);
        st.setFromY(1.0); st.setToY(1.12);
        st.setAutoReverse(true);
        st.setCycleCount(2);
        st.play();
    }
}