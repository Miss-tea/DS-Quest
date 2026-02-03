package ui;

import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;

/**
 * A single shelf row drawn in code:
 *  - wood backboard
 *  - raised plank (lip + highlight + shadow)
 *  - optional thin top cap (use only for the top-most row)
 *  - exposes getPlankBaselineY() so you can bottom-align book spines precisely.
 *
 * NOTE: No left/right borders here—the continuous posts come from BookshelfFrame.
 */
public class ShelfRow extends Region {

    // Thin top cap for the first row (small accent so it doesn't fight the frame)
    private static final double TOP_CAP_H = 10.0;

    // Optional soft vignette (kept subtle to match reference)
    private static final boolean SHOW_VIGNETTE = true;
    private static final double VIGNETTE_TOP_ALPHA    = 0.03;
    private static final double VIGNETTE_BOTTOM_ALPHA = 0.05;

    // Slight rounding looks good against backgrounds
    private static final double ARC = 8.0;

    private final double width;              // inner width (between posts)
    private final double rowHeight;          // height of this row visual
    private final double plankThickness;     // thickness of the plank lip
    private final double plankBaselineY;     // Y (from top) where book bottoms should sit
    private final boolean showTopCap;

    /**
     * @param width                    inner width (between posts)
     * @param rowHeight                visual height allocated for the row
     * @param plankThickness           thickness of the plank lip
     * @param baselineOffsetFromBottom distance from bottom edge to the book baseline
     * @param showTopCap               draw a thin cap at the top (use for the top-most row only)
     */
    public ShelfRow(double width,
                    double rowHeight,
                    double plankThickness,
                    double baselineOffsetFromBottom,
                    boolean showTopCap) {
        this.width = width;
        this.rowHeight = rowHeight;
        this.plankThickness = plankThickness;
        this.showTopCap = showTopCap;

        // Baseline measured from the top
        this.plankBaselineY = rowHeight - baselineOffsetFromBottom;

        setMinSize(width, rowHeight);
        setPrefSize(width, rowHeight);
        setMaxSize(width, rowHeight);

        draw();
    }

    public double getPlankBaselineY() {
        return plankBaselineY;
    }

    private void draw() {
        getChildren().clear();

        // --- Backboard (PANEL WOOD) ---
        // Matches: #A2553A -> #5A2218 -> #331312
        Rectangle back = new Rectangle(width, rowHeight);
        back.setFill(new LinearGradient(
                0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0.00, Color.web("#8A5A3A")),
                new Stop(0.50, Color.web("#6F472E")),
                new Stop(1.00, Color.web("#4D2E1C"))
        ));
        back.setArcWidth(ARC);
        back.setArcHeight(ARC);
        getChildren().add(back);

        // Optional soft inner vignette (very subtle, as in reference)
        if (SHOW_VIGNETTE) {
            Rectangle vignette = new Rectangle(0, 0, width, rowHeight);
            vignette.setFill(new LinearGradient(
                    0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0.00, Color.color(0, 0, 0, VIGNETTE_TOP_ALPHA)),
                    new Stop(0.50, Color.TRANSPARENT),
                    new Stop(1.00, Color.color(0, 0, 0, VIGNETTE_BOTTOM_ALPHA))
            ));
            getChildren().add(vignette);
        }

        // --- Plank lip (SHELF EDGE) ---
        // Matches: #96513B -> #693421 -> #451C12
        double lipHeight = Math.max(6, plankThickness * 0.55);
        double lipY = plankBaselineY - lipHeight;

        Rectangle plankLip = new Rectangle(0, lipY, width, lipHeight);
        plankLip.setFill(new LinearGradient(
                0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0.00, Color.web("#96513B")),
                new Stop(0.40, Color.web("#693421")),
                new Stop(1.00, Color.web("#451C12"))
        ));

        // Warm highlight along lip top edge (reference has warm specular)
        Rectangle highlight = new Rectangle(0, lipY, width, Math.max(1, lipHeight * 0.08));
        highlight.setFill(Color.rgb(255, 188, 110, 0.26));

        // Soft shadow immediately below the baseline
        Rectangle shadow = new Rectangle(0, plankBaselineY, width, Math.max(2, plankThickness * 0.25));
        shadow.setFill(Color.rgb(0, 0, 0, 0.25));

        getChildren().addAll(plankLip, highlight, shadow);

        // --- Optional thin top cap (for the very first row only) ---
        if (showTopCap) {
            Rectangle topCap = new Rectangle(0, 0, width, TOP_CAP_H);
            topCap.setFill(new LinearGradient(
                    0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0.00, Color.web("#A2553A")),
                    new Stop(1.00, Color.web("#5A2218"))
            ));
            getChildren().add(topCap);
        }

        // Subtle drop shadow to separate from background (kept same)
        DropShadow ds = new DropShadow();
        ds.setRadius(8);
        ds.setOffsetY(2);
        ds.setColor(Color.rgb(0, 0, 0, 0.25));
        setEffect(ds);
    }
}