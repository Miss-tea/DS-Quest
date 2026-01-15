package ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.text.Font;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.scene.text.Text;
import javafx.scene.effect.DropShadow;


public class TitleScreen extends Application {

    @Override
    public void start(Stage stage) {

        //  Root Pane
        Pane root = new Pane();

        // Background Image

        Image bgImage = new Image(getClass().getResource("/titleScreenBg.jpg").toExternalForm());
        ImageView bgView = new ImageView(bgImage);
        bgView.setFitWidth(1200);
        bgView.setFitHeight(700);
        root.getChildren().add(bgView);


        //Load Fonts
        Font cinzelTitle = Font.loadFont(
                getClass().getResourceAsStream("/fonts/CinzelDecorative-Bold.ttf"),
                75
        );

        Font cinzelSubtitle = Font.loadFont(
                getClass().getResourceAsStream("/fonts/CinzelDecorative-Regular.ttf"),
                30
        );

        Font cinzelButton = Font.loadFont(
                getClass().getResourceAsStream("/fonts/CinzelDecorative-Bold.ttf"),
                30
        );

        Font cinzelText = Font.loadFont(
                getClass().getResourceAsStream("/fonts/CinzelDecorative-Regular.ttf"),
                30
        );

// ===== FALLBACK (THIS PREVENTS CRASH) =====
        if (cinzelTitle == null || cinzelSubtitle == null) {
            System.out.println("⚠ Fonts not found, using system fonts");
            cinzelTitle = Font.font("System", 75);
            cinzelSubtitle = Font.font("System", 30);
            cinzelButton = Font.font("System", 30);
            cinzelText = Font.font("System", 30);
        }


        //  Title Label

        Text title = new Text("DUNGEON OF DS");
        title.setFont(cinzelTitle);
        title.setFill(Color.web("#FFFFFF"));
        title.setStroke(Color.BLACK);
        title.setStrokeWidth(2);
        title.setLayoutX(80);
        title.setLayoutY(250);


        //Ready Text

        Text subtitle = new Text("READY TO SURVIVE?");
        subtitle.setFont(cinzelSubtitle);
        subtitle.setFill(Color.web("#FFFFFF"));
        title.setStroke(Color.BLACK);
        title.setStrokeWidth(2);
        subtitle.setLayoutX(285);
        subtitle.setLayoutY(325);


        //Start Button

        Button startBtn = createButton("ENTER  DUNGEON", cinzelButton);
        startBtn.setLayoutX(240);
        startBtn.setLayoutY(340);

        //OR Text

        Text orLabel = new Text("OR");
        orLabel.setFont(cinzelText);
        orLabel.setFill(Color.web("#FFFFFF"));
        orLabel.setStroke(Color.BLACK);
        orLabel.setStrokeWidth(0.5);
        orLabel.setLayoutX(390);
        orLabel.setLayoutY(455);


        //How To Survive Button

        Button howBtn = createButton("HOW TO SURVIVE", cinzelButton);
        howBtn.setLayoutX(240);
        howBtn.setLayoutY(479);

        //  Add all nodes to root

        root.getChildren().addAll(title, subtitle, startBtn, orLabel, howBtn);


        //Scene & Stage

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
                        "-fx-padding: 10 40;" +
                        "-fx-background-radius: 5;"
        );

        // Hover effect
        btn.setOnMouseEntered(e -> btn.setStyle(
                "-fx-background-color: rgba(200,184,144,0.7);" +
                        "-fx-border-color: #E8E0C2;" +
                        "-fx-border-width: 2;" +
                        "-fx-padding: 10 40;" +
                        "-fx-background-radius: 5;"
        ));

        btn.setOnMouseExited(e -> btn.setStyle(
                "-fx-background-color: rgba(0,0,0,0.6);" +
                        "-fx-border-color: #C8B890;" +
                        "-fx-border-width: 2;" +
                        "-fx-padding: 10 40;" +
                        "-fx-background-radius: 5;"
        ));

        return btn;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
