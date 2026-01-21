
package ui;

import core.AssetLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 * Wizard + dialogue box that only appears when speaking.
 * - Uses dialoguebox.jpg as background
 * - Fixed size (does not change with text length)
 * - Buttons centered below the text
 */
public class DialoguePane extends StackPane {

    // 👉 Pick a fixed size that matches your dialoguebox.jpg artwork
    private static final double FIXED_WIDTH  = 800;   // e.g., 600–700
    private static final double FIXED_HEIGHT = 280;   // e.g., 230–280
    // Padding inside the box (already baked into the art margin? keep modest)
    private static final Insets PANEL_PADDING = new Insets(18, 28, 18, 28);

    private final ImageView wizard = AssetLoader.imageView(AssetLoader.WIZARD, 300, 0, true);
    private final VBox panel = new VBox(16);
    private final Label text = new Label();

    public DialoguePane() {
        setPickOnBounds(false);  // empty areas don't block mouse

        // === Dialogue skin: dialoguebox.jpg as background ===
        BackgroundSize bs = new BackgroundSize(
                BackgroundSize.AUTO, BackgroundSize.AUTO, true, true, true, false
        );
        try {
            BackgroundImage bi = new BackgroundImage(
                    AssetLoader.image(AssetLoader.DIALOGUE_BOX),   // <- dialogue box image
                    BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT,
                    BackgroundPosition.CENTER, bs
            );
            panel.setBackground(new Background(bi));
        } catch (Exception e) {
            // Fallback solid bg
            panel.setBackground(new Background(new BackgroundFill(
                    Color.rgb(40, 35, 30, 0.92), CornerRadii.EMPTY, Insets.EMPTY)));
        }

        // === Fixed size: lock min = pref = max (prevents auto-resize with text) ===
        panel.setPrefSize(FIXED_WIDTH, FIXED_HEIGHT);
        panel.setMinSize(FIXED_WIDTH, FIXED_HEIGHT);
        panel.setMaxSize(FIXED_WIDTH, FIXED_HEIGHT);

        panel.setPadding(PANEL_PADDING);
        panel.setAlignment(Pos.CENTER);
        panel.setEffect(new DropShadow(12, Color.BLACK));

        // === Text settings ===
        text.setWrapText(true);
        Font f = AssetLoader.loadFont("/fonts/CinzelDecorative-Regular.ttf", 18);
        text.setFont(f);
        text.setTextFill(Color.web("#15110a"));

        // Give the label a wrap width that fits inside panel padding
        //   wrap = FIXED_WIDTH - (left + right insets)
        double wrap = FIXED_WIDTH - (PANEL_PADDING.getLeft() + PANEL_PADDING.getRight());
        if (wrap < 200) wrap = FIXED_WIDTH * 0.85; // safe fallback
        text.setMaxWidth(wrap);
        text.setPrefWidth(wrap);

        panel.getChildren().add(text);

        // === Layout: panel at left, wizard at right ===
        HBox row = new HBox(-100, panel,wizard);
        row.setAlignment(Pos.BOTTOM_CENTER);

        getChildren().add(row);

        // Hidden by default; BaseLevel shows/hides as needed
        setVisible(false);
        // Keep mouse events active when interactive (buttons present),
        // you may toggle this in BaseLevel if you want non-blocking hints.
        setMouseTransparent(false);
    }

    /** Show with text and (optional) action buttons. */
    public void show(String message, Node... actions) {
        text.setText(message);

        // reset panel children and rebuild (text + optional buttons)
        panel.getChildren().setAll(text);

        if (actions != null && actions.length > 0) {
            HBox btns = new HBox(16);
            btns.setAlignment(Pos.CENTER);
            for (Node a : actions) {
                if (a instanceof Button b) {
                    b.setFont(AssetLoader.loadFont("/fonts/CinzelDecorative-Regular.ttf", 16));
                    b.setStyle("-fx-background-color: #8b5a2b; -fx-text-fill: white; -fx-padding: 8 18; -fx-background-radius: 6;");
                    b.setOnMouseEntered(ev -> b.setStyle("-fx-background-color: #a16930; -fx-text-fill: white; -fx-padding: 8 18; -fx-background-radius: 6;"));
                    b.setOnMouseExited(ev -> b.setStyle("-fx-background-color: #8b5a2b; -fx-text-fill: white; -fx-padding: 8 18; -fx-background-radius: 6;"));
                }
                btns.getChildren().add(a);
            }
            panel.getChildren().add(btns);
        }

        setVisible(true);
    }

    public void hide() {
        setVisible(false);
    }
}
