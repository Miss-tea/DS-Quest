
package ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.text.Font;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.scene.text.Text;

public class TitleScreen extends Application {

    @Override
    public void start(Stage stage) {
        // Root Pane
        Pane root = new Pane();

        // Background Image
        Image bgImage = new Image(TitleScreen.class.getResourceAsStream("/titleScreenBg.jpg"));
        ImageView bgView = new ImageView(bgImage);
        bgView.setFitWidth(1200);
        bgView.setFitHeight(700);
        root.getChildren().add(bgView);

        // Load Fonts (fall back if missing)
        Font cinzelTitle = Font.loadFont(
                getClass().getResource("/fonts/CinzelDecorative-Bold.ttf").toExternalForm(), 75
        );
        if (cinzelTitle == null) cinzelTitle = Font.font(75);

        Font cinzelSubtitle = Font.loadFont(
                getClass().getResource("/fonts/CinzelDecorative-Regular.ttf").toExternalForm(), 30
        );
        if (cinzelSubtitle == null) cinzelSubtitle = Font.font(30);

        Font cinzelButton = Font.loadFont(
                getClass().getResource("/fonts/CinzelDecorative-Bold.ttf").toExternalForm(), 30
        );
        if (cinzelButton == null) cinzelButton = Font.font(30);

        Font cinzelText = Font.loadFont(
                getClass().getResource("/fonts/CinzelDecorative-Regular.ttf").toExternalForm(), 30
        );
        if (cinzelText == null) cinzelText = Font.font(30);

        // Title Label
        Text title = new Text("DUNGEON OF DS");
        title.setFont(cinzelTitle);
        title.setFill(Color.web("#FFFFFF"));
        title.setStroke(Color.BLACK);
        title.setStrokeWidth(2);
        title.setLayoutX(85);
        title.setLayoutY(250);

        // Ready Text
        Text subtitle = new Text("READY TO EXPLORE DSA?");
        subtitle.setFont(cinzelSubtitle);
        subtitle.setFill(Color.web("#FFFFFF"));
        subtitle.setStroke(Color.BLACK);
        subtitle.setStrokeWidth(0.75);
        subtitle.setLayoutX(245);
        subtitle.setLayoutY(325);

        // Start Button
        Button startBtn = createButton("ENTER  DUNGEON", cinzelButton);
        startBtn.setLayoutX(240);
        startBtn.setLayoutY(340);

        // ✅ Properly closed handler — only navigation logic inside
        startBtn.setOnAction(e -> {
            LevelSelectScreen select = new LevelSelectScreen(stage);
            Scene selectScene = new Scene(select, 1200, 720);
            stage.setScene(selectScene);
        });

        // OR Text
        Text orLabel = new Text("OR");
        orLabel.setFont(cinzelText);
        orLabel.setFill(Color.web("#FFFFFF"));
        orLabel.setStroke(Color.BLACK);
        orLabel.setStrokeWidth(0.5);
        orLabel.setLayoutX(390);
        orLabel.setLayoutY(455);

        // How To Survive Button
        Button howBtn = createButton("HOW TO SURVIVE", cinzelButton);
        howBtn.setLayoutX(240);
        howBtn.setLayoutY(479);

        // Add all nodes to root
        root.getChildren().addAll(title, subtitle, startBtn, orLabel, howBtn);

        // Scene & Stage
        Scene scene = new Scene(root, 1200, 720);
        stage.setTitle("Dungeon of DS");
        stage.setScene(scene);
        stage.show();
    }

    // BUTTON CREATOR WITH HOVER
    private Button createButton(String text, Font font) {
        Button btn = new Button(text);
        btn.setFont(font);
        btn.setTextFill(Color.web("#FFFFFF"));
        btn.setStyle(
                "-fx-background-color: rgba(0,0,0,0.6);" +
                        "-fx-border-color: #C8B890;" +
                        "-fx-border-width: 2;" +
                        "-fx-padding: 14 45 14 45;" +
                        "-fx-background-radius: 18;" +
                        "-fx-border-radius: 18;"
        );

        // Hover effect
        btn.setOnMouseEntered(e -> btn.setStyle(
                "-fx-background-color: rgba(200,184,144,0.7);" +
                        "-fx-border-color: #E8E0C2;" +
                        "-fx-border-width: 2;" +
                        "-fx-padding: 14 45 14 45;" +
                        "-fx-background-radius: 18;" +
                        "-fx-border-radius: 18;"
        ));

        btn.setOnMouseExited(e -> btn.setStyle(
                "-fx-background-color: rgba(0,0,0,0.6);" +
                        "-fx-border-color: #C8B890;" +
                        "-fx-border-width: 2;" +
                        "-fx-padding: 14 45 14 45;" +
                        "-fx-background-radius: 18;" +
                        "-fx-border-radius: 18;"
        ));
        return btn;
    }

    // public static void main(String[] args) { launch(args); }
}
