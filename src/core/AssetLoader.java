package core;

import javafx.scene.ImageCursor;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.text.Font;

import java.io.InputStream;

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
    public static final String LEARNING_BOARD = "/learningsboard.png";

    // ---------- Level 2 assets ----------
    public static final String L2_BG = "/level2Bg.png";

    // Shelf row planks
    //public static final String L2_ROW_1 = "/row-1.png";
    //public static final String L2_ROW_2 = "/row-2.png";
    //public static final String L2_ROW_3 = "/row-3.png";
    //public static final String L2_ROW_4 = "/row-4.png";

    // Number overlay background
    public static final String L2_OPEN_BOOK = "/openbook.png";

    // Spine images (extend or change freely)
    public static final String L2_SPINE_BLUE   = "/bluebook.png";
    public static final String L2_SPINE_GREEN  = "/greenbook.png";
    public static final String L2_SPINE_RED    = "/redbook.png";
    public static final String L2_SPINE_ORANGE = "/orangebook.png";
    public static final String L2_SPINE_BROWN  = "/brownbook.png";
    public static final String L2_SPINE_DOTS   = "/dottedbrownbook.png";
    public static final String L2_SPINE_STRIPE = "/brownstripebook.png";
    public static final String L2_SPINE_OLIVE  = "/browndiamond.png";
    public static final String L2_SPINE_MIX    = "/redgreen.png";

    private static final String[] L2_SPINE_SET = {
            L2_SPINE_BLUE, L2_SPINE_GREEN, L2_SPINE_RED, L2_SPINE_ORANGE,
            L2_SPINE_BROWN, L2_SPINE_DOTS, L2_SPINE_STRIPE, L2_SPINE_OLIVE, L2_SPINE_MIX
    };

    /** Number of available spine styles (used to randomize designs). */
    public static int l2SpineCount() {
        return L2_SPINE_SET.length;
    }

    /** Get a spine image by index (index is modulo the available styles). */
    public static Image spineByIndex(int i) {
        String path = L2_SPINE_SET[Math.floorMod(i, L2_SPINE_SET.length)];
        return image(path);
    }

    // -------------------- common loaders --------------------

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
