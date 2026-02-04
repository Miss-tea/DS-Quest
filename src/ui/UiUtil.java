
package ui;

import core.AssetLoader;
import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.util.Duration;

import javafx.animation.PauseTransition;     // <-- add

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.effect.DropShadow;

public final class UiUtil {
    private UiUtil(){}

    public static Button btn(String text) {
        Button b = new Button(text);
        b.setFont(AssetLoader.loadFont("/fonts/CinzelDecorative-Regular.ttf", 16));
        b.setStyle("-fx-background-color: #8b5a2b; -fx-text-fill: white; -fx-padding: 8 18; -fx-background-radius: 6;");
        b.setOnMouseEntered(ev -> b.setStyle("-fx-background-color: #a16930; -fx-text-fill: white; -fx-padding: 8 18; -fx-background-radius: 6;"));
        b.setOnMouseExited(ev -> b.setStyle("-fx-background-color: #8b5a2b; -fx-text-fill: white; -fx-padding: 8 18; -fx-background-radius: 6;"));
        return b;
    }
    public static Button btn(String text,char e) {
        Button b = new Button(text);
        b.setFont(AssetLoader.loadFont("/fonts/SegoeUI-Symbol.ttf", 46));
        b.setStyle("-fx-background-color: #8b5a2b; -fx-text-fill: white; -fx-padding: 8 18; -fx-background-radius: 6;");
        b.setOnMouseEntered(ev -> b.setStyle("-fx-background-color: #a16930; -fx-text-fill: white; -fx-padding: 8 18; -fx-background-radius: 6;"));
        b.setOnMouseExited(ev -> b.setStyle("-fx-background-color: #8b5a2b; -fx-text-fill: white; -fx-padding: 8 18; -fx-background-radius: 6;"));
        return b;
    }

    public static Label paper(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setTextFill(Color.WHITESMOKE);
        Font f = AssetLoader.loadFont("/fonts/CinzelDecorative-Regular.ttf", 18);
        l.setFont(f);
        return l;
    }

    public static void shake(javafx.scene.Node n) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(70), n);
        tt.setByX(8);
        tt.setCycleCount(6);
        tt.setAutoReverse(true);
        tt.setInterpolator(Interpolator.EASE_BOTH);
        tt.setOnFinished(ev -> n.setTranslateX(0));
        tt.playFromStart();
    }



    public static void placePop(javafx.scene.Node n, javafx.scene.paint.Color color) {
        // Glow
        var prev = n.getEffect();
        DropShadow ds = new DropShadow(0, color);
        ds.setSpread(0.75);
        n.setEffect(ds);

        Timeline glow = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(ds.radiusProperty(), 0)),
                new KeyFrame(Duration.millis(140), new KeyValue(ds.radiusProperty(), 28)),
                new KeyFrame(Duration.millis(260), new KeyValue(ds.radiusProperty(), 0))
        );
        glow.setOnFinished(ev -> n.setEffect(prev));

        // Scale pop
        n.setScaleX(1.0); n.setScaleY(1.0);
        Timeline scale = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(n.scaleXProperty(), 1.0),
                        new KeyValue(n.scaleYProperty(), 1.0)
                ),
                new KeyFrame(Duration.millis(110),
                        new KeyValue(n.scaleXProperty(), 1.06),
                        new KeyValue(n.scaleYProperty(), 1.06)
                ),
                new KeyFrame(Duration.millis(260),
                        new KeyValue(n.scaleXProperty(), 1.0),
                        new KeyValue(n.scaleYProperty(), 1.0)
                )
        );

        glow.playFromStart();
        scale.playFromStart();
    }

    public static void pulseGlow(javafx.scene.Node n, int ms) {
        var prev = n.getEffect();
        DropShadow ds = new DropShadow(0, Color.RED);
        n.setEffect(ds);
        Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(ds.radiusProperty(), 0)),
                new KeyFrame(Duration.millis(ms * 0.5), new KeyValue(ds.radiusProperty(), 28)),
                new KeyFrame(Duration.millis(ms), new KeyValue(ds.radiusProperty(), 0))
        );
        tl.setOnFinished(ev -> n.setEffect(prev));
        tl.playFromStart();
    }



        /** Pulses a soft colored glow on the node, then clears it. */
        public static void glowPulse(Node node, Color color, double maxRadius, int ms) {
            DropShadow ds = new DropShadow();
            ds.setColor(color);
            ds.setRadius(0);
            ds.setSpread(0.65); // higher spread to look like glow instead of shadow
            node.setEffect(ds);

            Timeline tl = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(ds.radiusProperty(), 0),
                            new KeyValue(ds.colorProperty(), color.deriveColor(0,1,1,0.0)) // start almost invisible
                    ),
                    new KeyFrame(Duration.millis(ms * 0.35),
                            new KeyValue(ds.radiusProperty(), maxRadius),
                            new KeyValue(ds.colorProperty(), color.deriveColor(0,1,1,0.85))
                    ),
                    new KeyFrame(Duration.millis(ms),
                            new KeyValue(ds.radiusProperty(), 0),
                            new KeyValue(ds.colorProperty(), color.deriveColor(0,1,1,0.0))
                    )
            );

            tl.setOnFinished(e -> node.setEffect(null));
            tl.playFromStart();
        }


}
