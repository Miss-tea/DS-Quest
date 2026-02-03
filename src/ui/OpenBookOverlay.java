package ui;

import core.AssetLoader;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

public class OpenBookOverlay extends StackPane {

    private final ImageView bg = new ImageView(AssetLoader.image(AssetLoader.L2_OPEN_BOOK));
    private final Label number = new Label();

    public OpenBookOverlay() {
        setPickOnBounds(false);
        bg.setPreserveRatio(true);
        bg.setFitWidth(260); // tune as needed
        getChildren().addAll(bg, number);
        setAlignment(Pos.CENTER);

        Font f = AssetLoader.loadFont("/fonts/CinzelDecorative-Regular.ttf", 28);
        number.setFont(f);
        number.setTextFill(Color.web("#2c1b0f"));
        number.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.45), 4, 0.2, 0, 1);");

        setVisible(false);
        setMouseTransparent(true);
    }

    public void showValue(int value) {
        number.setText(String.valueOf(value));
        setVisible(true);
        setOpacity(0);
        setScaleX(0.9); setScaleY(0.9);

        var tl = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.ZERO,
                        new javafx.animation.KeyValue(opacityProperty(), 0),
                        new javafx.animation.KeyValue(scaleXProperty(), 0.9),
                        new javafx.animation.KeyValue(scaleYProperty(), 0.9)
                ),
                new javafx.animation.KeyFrame(javafx.util.Duration.millis(220),
                        new javafx.animation.KeyValue(opacityProperty(), 1.0),
                        new javafx.animation.KeyValue(scaleXProperty(), 1.0),
                        new javafx.animation.KeyValue(scaleYProperty(), 1.0)
                )
        );
        tl.playFromStart();
    }

    public void hideOverlay() {
        setVisible(false);
    }
}