package ui;

import javafx.scene.image.Image;

public record LearningSnap(Image image, String message) {
    public static LearningSnap textOnly(String message) {
        return new LearningSnap(null, message);
    }
}