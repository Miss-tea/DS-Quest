
package core;

import javafx.scene.Cursor;
import javafx.scene.ImageCursor;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.text.Font;

import java.io.InputStream;
import java.util.Objects;

public final class AssetLoader {
    private AssetLoader(){}

    // FILE NAMES (adjust here if your names differ)
    public static final String BG = "/LevelBg.png";
    public static final String WIZARD = "/wizard.png";
    public static final String HEART = "/heart.png"; // your folder shows heart.png
    public static final String SLOT_BG = "/perslot.png"; // fallback to /slot.png if you prefer
    public static final String SHELF = "/shelf.png";
    public static final String DIALOGUE_BOX = "/dialoguebox.jpg";
    public static final String INSTRUCTION_PAPER = "/instruction_paper.png";

    public static ImageView imageView(String path, double fitW, double fitH, boolean preserve) {
        Image img = image(path);
        ImageView iv = new ImageView(img);
        if (fitW > 0) iv.setFitWidth(fitW);
        if (fitH > 0) iv.setFitHeight(fitH);
        iv.setPreserveRatio(preserve);
        return iv;
    }

    public static Image image(String path) {
        InputStream is = AssetLoader.class.getResourceAsStream(path);
        if (is == null) {
            throw new IllegalArgumentException("Resource not found: " + path);
        }
        return new Image(is);
    }

    public static ImageView artifactView(Artifact a, double size) {
        return imageView("/" + a.imageName(), size, size, true);
    }

    public static Font loadFont(String path, double size) {
        InputStream is = AssetLoader.class.getResourceAsStream(path);
        if (is == null) return Font.font(size); // fallback system font
        return Font.loadFont(is, size);
    }

    public static ImageCursor makeCursor(Artifact a) {
        Image i = image("/" + a.imageName());
        return new ImageCursor(i, i.getWidth() / 2, i.getHeight() / 2);
    }
}
