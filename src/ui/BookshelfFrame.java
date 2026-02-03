package ui;

import javafx.scene.layout.Region;
import javafx.scene.paint.*;
import javafx.scene.shape.Rectangle;

/** Continuous bookshelf frame: left/right posts and a thick top cap (optional bottom base). */
public class BookshelfFrame extends Region {

    private final double postW;       // width of the vertical posts
    private final double topCapH;     // height of the top cap
    private final double bottomCapH;  // height of the bottom base (0 to hide)

    private final double outerW;
    private final double outerH;

    // POSTS (golden carved wood)
    // Gradient top->bottom: highlight -> mid -> deep shadow
    private final Paint postFill = new LinearGradient(
            0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
            new Stop(0.00, Color.web("#CC6024")),  // highlight
            new Stop(0.55, Color.web("#622415")),  // mid core
            new Stop(1.00, Color.web("#250A08"))   // deep shadow
    );

    // TOP CAP (similar golden tone, slightly less contrast)
    private final Paint capFill = new LinearGradient(
            0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
            new Stop(0.00, Color.web("#CC6024")),
            new Stop(1.00, Color.web("#622415"))
    );

    public BookshelfFrame(double outerW, double outerH, double postW, double topCapH, double bottomCapH) {
        this.outerW = outerW;
        this.outerH = outerH;
        this.postW = postW;
        this.topCapH = topCapH;
        this.bottomCapH = bottomCapH;

        setMinSize(outerW, outerH);
        setPrefSize(outerW, outerH);
        setMaxSize(outerW, outerH);

        draw();
    }

    private void draw() {
        getChildren().clear();

        // Left post
        Rectangle left = new Rectangle(0, 0, postW, outerH);
        left.setFill(postFill);

        // Right post
        Rectangle right = new Rectangle(outerW - postW, 0, postW, outerH);
        right.setFill(postFill);

        // Top cap (full width)
        Rectangle top = new Rectangle(0, 0, outerW, topCapH);
        top.setFill(capFill);

        getChildren().addAll(left, right, top);

        // Optional bottom base (use capFill, or darker if you prefer)
        if (bottomCapH > 0) {
            Rectangle bottom = new Rectangle(0, outerH - bottomCapH, outerW, bottomCapH);
            bottom.setFill(capFill);
            getChildren().add(bottom);
        }
    }
}