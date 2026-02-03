package ui;

import javafx.scene.Cursor;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

public class BookViewSpine extends StackPane {
    public final int index;
    private final ImageView spineView;
    private boolean enabled = false;
    private boolean active = false; // NEW: is this the currently unlocked/active book?

    // Keep books vibrant: enabled vs locked looks
    private final ColorAdjust enabledFx = new ColorAdjust(0.0, 0.18, 0.02, 0.06);
    private final ColorAdjust lockedFx  = new ColorAdjust(0.0, -0.15, -0.08, 0.04);

    // Optional subtle ring for locked look (not applied when active)
    private final DropShadow lockedRing = new DropShadow();

    // NEW: persistent active glow (stays until decision)
    private final DropShadow activeGlow = new DropShadow();

    public BookViewSpine(int index, Image spine, double fitHeight) {
        this.index = index;

        this.spineView = new ImageView(spine);
        spineView.setPreserveRatio(true);
        spineView.setFitHeight(fitHeight);

        // Locked ring
        lockedRing.setRadius(8);
        lockedRing.setOffsetX(0);
        lockedRing.setOffsetY(1.5);
        lockedRing.setColor(Color.rgb(0, 0, 0, 0.15));

        // Persistent active glow — warm gold
        activeGlow.setRadius(16);
        activeGlow.setSpread(0.60);
        activeGlow.setColor(Color.web("#E6C15A", 0.80));

        getChildren().add(spineView);

        // Always opaque & clickable (RowView controls logic)
        setOpacity(1.0);
        setMouseTransparent(false);
        setCursor(Cursor.DEFAULT);

        applyLockedLook(); // initial
    }

    /** RowView calls this to toggle clickability + base visual state. */
    public void setEnabled(boolean v) {
        this.enabled = v;
        setCursor(v ? Cursor.HAND : Cursor.DEFAULT);
        if (v) {
            applyEnabledLook();
        } else {
            applyLockedLook();
        }
        // Keep/restore active glow if necessary:
        applyActiveOverlay();
    }

    public boolean isEnabled() { return enabled; }

    /** NEW: RowView calls this to mark this book as the active one (persistent glow). */
    public void setActiveHighlight(boolean value) {
        this.active = value;
        applyActiveOverlay();
    }

    // ---------------- internal visuals ----------------

    private void applyEnabledLook() {
        spineView.setEffect(enabledFx);
        // Do not clear container effect here; active glow may be applied on container
        if (!active) setEffect(null);
    }

    private void applyLockedLook() {
        spineView.setEffect(lockedFx);
        if (!active) setEffect(lockedRing);
    }

    /** Applies / clears the persistent active glow while preserving enabled/locked looks on the image. */
    private void applyActiveOverlay() {
        if (active) {
            setEffect(activeGlow); // active takes precedence over lockedRing
        } else {
            // not active: show locked ring if disabled, nothing if enabled
            if (enabled) {
                setEffect(null);
            } else {
                setEffect(lockedRing);
            }
        }
    }
}