
package ui;

import core.AssetLoader;
import core.GameState;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

public class HUD extends HBox {
    private final Label scoreLbl = new Label();
    private final HBox heartsBox = new HBox(15);

    public HUD(GameState gs) {
        // Keep your spacing (you can reduce if you want tighter)
        setSpacing(40);
        setAlignment(Pos.TOP_LEFT);
        setPickOnBounds(false);

        Label title = new Label("ARRAY DUNGEON");
        title.setTextFill(Color.WHITESMOKE);
        title.setFont(AssetLoader.loadFont("/fonts/CinzelDecorative-Bold.ttf", 32));

        scoreLbl.setTextFill(Color.WHITESMOKE);
        scoreLbl.setFont(AssetLoader.loadFont("/fonts/CinzelDecorative-Regular.ttf", 24));

        heartsBox.setAlignment(Pos.CENTER_LEFT);
        refreshHearts(gs.getHearts());

        gs.scoreProperty().addListener((obs, o, n) -> scoreLbl.setText("SCORE: " + n));
        gs.heartsProperty().addListener((obs, o, n) -> refreshHearts(n.intValue()));

        scoreLbl.setText("SCORE: " + gs.getScore());

        // --- Minimal change: two flexible spacers to center title and push score to right ---
        Region spacerLeftCenter = new Region();
        Region spacerCenterRight = new Region();
        HBox.setHgrow(spacerLeftCenter, Priority.ALWAYS);
        HBox.setHgrow(spacerCenterRight, Priority.ALWAYS);

        // Final order: [Hearts] [spacer] [Title] [spacer] [Score]
        getChildren().setAll(heartsBox, spacerLeftCenter, title, spacerCenterRight, scoreLbl);

        setFillHeight(false);
    }

    private void refreshHearts(int count) {
        heartsBox.getChildren().clear();
        for (int i = 0; i < count; i++) {
            ImageView h = AssetLoader.imageView(AssetLoader.HEART, 50, 50, true);
            heartsBox.getChildren().add(h);
        }
    }
}
