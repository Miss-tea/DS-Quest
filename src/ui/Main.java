
package ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) {
        Level1 level1 = new Level1();
        // Your screenshots are ~1280x720
        Scene scene = level1.buildScene(1280, 720);
        stage.setTitle("Array Dungeon - Level 1");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

