package core;

import javafx.scene.ImageCursor;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.text.Font;

import java.io.InputStream;
import java.net.URL;

public final class AssetLoader {
    private AssetLoader(){}

    // ===== Global audio switch (music + one-shots) =====
    // Set to false to silence all audio across levels that use this flag.
    public static boolean MUSIC_ENABLED = false; // <<<<<<<< Disabled by default (your request)

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

    // ---------- Level 3 assets ----------
    public static final String L3_BG_CORRIDOR    = "/dungeon corridor.png";
    public static final String L3_OVERLAY_LEFT   = "/final left.png";
    public static final String L3_OVERLAY_RIGHT  = "/final right.png";
    public static final String L3_HINT_ARROWS    = "/hint_arrows_glowing.png";
    public static final String L3_DOOR           = "/door.png";

    // FX images
    public static final String L3_STONE_DEBRIS_1 = "/stone debris.png";
    public static final String L3_STONE_DEBRIS_2 = "/stone_debris_falling.png";
    public static final String L3_FLOOR_GLYPH    = "/A glowing magical fl.png";
    public static final String L3_RUNE_BURST     = "/A magical rune burst.png";
    public static final String L3_VICTORY_BANNER = "/victory_banner_medieval.png";

    // Victory / post-level background
    public static final String L3_THRONE_BG      = "/throne_room_victory_ui.jpg";

    // ---------- Level 3 audio ----------
    public static final String L3_MUSIC_INTRO    = "/Music_binarysearch/dark-ambient-soundscape-dreamscape-462864.mp3";
    public static final String L3_MUSIC_CORRIDOR = "/Music_binarysearch/tense-suspense-background-music-442839.mp3";
    public static final String L3_SFX_DANGER     = "/Music_binarysearch/darkness-approaching-cinematic-danger-407228.mp3";
    public static final String L3_MUSIC_GOTHIC   = "/Music_binarysearch/gothic-horror-380504.mp3";
    public static final String L3_MUSIC_VICTORY  = "/Music_binarysearch/emotional-cinematic-inspirational-piano-main-10524.mp3";

    // ---------- Level 4 assets (images & audio) ----------
    public static final String L4_BG_INTRO             = "/first screen of linked list.jpeg";          // <--------------Changed line (added)
    public static final String L4_TRANSITION_MIST      = "/scene_transition_mist.png";                 // <--------------Changed line (added)
    public static final String L4_CORRIDOR_BG_1        = "/A high-quality medie2.png";                 // <--------------Changed line (added)
    public static final String L4_CORRIDOR_BG_2        = "/A high-quality medie.png";                  // <--------------Changed line (added)
    public static final String L4_CORRIDOR_BG_FALLBACK = "/link2.jpg";                                 // <--------------Changed line (added)

    public static final String L4_ORB_DIM              = "/A dim mystical orb a.png";                  // <--------------Changed line (added)
    public static final String L4_ORB_ALT              = "/A mystical crystal H.png";                  // <--------------Changed line (added)
    public static final String L4_ORB_ALT2             = "/A glowing memory fra.png";                  // <--------------Changed line (added)

    public static final String L4_VICTORY_LIGHT        = "/light_burst_victory.png";                   // <--------------Changed line (added)
    public static final String L4_VICTORY_BANNER       = "/victory_banner_medieval.png";               // <--------------Changed line (added)
    public static final String L4_THRONE_BG            = "/throne_room_victory_ui.jpg";                // <--------------Changed line (added)
    public static final String L4_MIST                 = "/mist.png";                                  // <--------------Changed line (added)

    public static final String L4_MUSIC_INTRO          = "/Music_binarysearch/dark-ambient-soundscape-dreamscape-462864.mp3";   // <--------------Changed line (added)
    public static final String L4_MUSIC_CORRIDOR       = "/Music_binarysearch/darkness-approaching-cinematic-danger-407228.mp3"; // <--------------Changed line (added)
    public static final String L4_MUSIC_TRAVERSAL      = "/Music_level4/syouki_takahashi-midnight-forest-184304.mp3";            // <--------------Changed line (added)
    public static final String L4_MUSIC_VICTORY        = "/Music_binarysearch/emotional-cinematic-inspirational-piano-main-10524.mp3"; // <--------------Changed line (added)

    // -------------------- common loaders --------------------

    public static ImageView imageView(String path, double fitW, double fitH, boolean preserve) {
        Image img = image(path);
        ImageView iv = new ImageView(img);
        if (fitW > 0) iv.setFitWidth(fitW);
        if (fitH > 0) iv.setFitHeight(fitH);
        iv.setPreserveRatio(preserve);
        return iv;
    }

    /** Strict image loader: throws if resource is missing. */
    public static Image image(String path) {
        InputStream is = AssetLoader.class.getResourceAsStream(path);
        if (is == null) {
            throw new IllegalArgumentException("Resource not found: " + path);
        }
        return new Image(is);
    }

    /** Safe image loader: returns null if resource missing (useful for optional FX). */
    public static Image imageOrNull(String path) {
        InputStream is = AssetLoader.class.getResourceAsStream(path);
        if (is == null) return null;
        return new Image(is);
    }

    /** Returns a URL for the resource or throws if missing. */
    public static URL resourceUrl(String path) {
        URL url = AssetLoader.class.getResource(path);
        if (url == null) {
            throw new IllegalArgumentException("Resource not found: " + path);
        }
        return url;
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