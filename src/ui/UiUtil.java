
package ui;

import core.AssetLoader;
import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.util.Duration;

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
}
