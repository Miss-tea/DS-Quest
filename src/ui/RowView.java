package ui;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

public class RowView extends HBox {

    public final int rowIndex;
    public final List<BookViewSpine> spines = new ArrayList<>();

    private int nextClickableIndex = 0;
    private boolean locked = true;
    private int activeIdx = -1; // which spine has the persistent glow now

    // --- Subtle "scan cursor" tag that moves with the active index ---
    private final Rectangle cursorTag = new Rectangle(24, 6, Color.web("#E6C15A90")); // soft amber
    // We attach this to the active BookViewSpine at TOP_CENTER.

    @FunctionalInterface public interface OnOpen { void handle(RowView row, int index); }
    @FunctionalInterface public interface OnWrongOrder { void handle(RowView row, int clickedIndex); }
    @FunctionalInterface public interface OnLockedClick { void handle(RowView row, int clickedIndex); }

    private OnOpen onOpen = (r,i)->{};
    private OnWrongOrder onWrongOrder = (r,i)->{};
    private OnLockedClick onLockedClick = (r,i)->{};

    public RowView(int rowIndex,
                   int bookCount,
                   IntFunction<BookViewSpine> spineFactory,
                   double spacingPx) {
        this.rowIndex = rowIndex;
        setSpacing(spacingPx);
        setAlignment(Pos.BOTTOM_LEFT); // books sit on the plank line

        // cursor tag look
        cursorTag.setArcWidth(8);
        cursorTag.setArcHeight(8);
        cursorTag.setMouseTransparent(true);
        cursorTag.setOpacity(0.0); // will fade in when first attached

        for (int i = 0; i < bookCount; i++) {
            BookViewSpine b = spineFactory.apply(i);
            spines.add(b);
            getChildren().add(b);

            final int idx = i;
            b.setOnMouseClicked(e -> {
                if (locked) { onLockedClick.handle(this, idx); return; }
                if (idx != nextClickableIndex) { onWrongOrder.handle(this, idx); return; }
                onOpen.handle(this, idx);
            });
        }
        enableNone();
        clearActiveHighlight();
    }

    public void setOnOpen(OnOpen cb) { this.onOpen = cb; }
    public void setOnWrongOrder(OnWrongOrder cb) { this.onWrongOrder = cb; }
    public void setOnLockedClick(OnLockedClick cb) { this.onLockedClick = cb; }

    public void unlock() {
        locked = false;
        nextClickableIndex = 0;
        setActiveIndex(nextClickableIndex); // highlight index 0 persistently + move cursor tag
    }

    public void lock() {
        locked = true;
        enableNone();
        clearActiveHighlight(); // remove glow when row locks
        detachCursorTag();
    }

    /**
     * Decision: advance to next index. If within bounds, the glow shifts to the next.
     * If past the end, everything disables and glow clears.
     */
    public boolean advance() {
        nextClickableIndex++;
        if (nextClickableIndex < spines.size()) {
            setActiveIndex(nextClickableIndex);
            return true;
        }
        enableNone();
        clearActiveHighlight(); // end-of-row -> clear glow
        detachCursorTag();
        return false;
    }

    /** Decision: jump to last index (Skip to End), glow moves there. */
    public void skipToEnd() {
        nextClickableIndex = spines.size() - 1;
        setActiveIndex(nextClickableIndex);
    }

    /** Decision reset: Double Check -> unlock again, glow returns to index 0. */
    public void resetForDoubleCheck() {
        unlock();
    }

    // ---------------- helpers ----------------

    private void setActiveIndex(int i) {
        enableOnly(i);                 // only i is clickable
        applyActiveOnly(i);            // only i has persistent glow
        activeIdx = i;
        attachCursorTagTo(i);          // move subtle cursor tag
    }

    private void enableOnly(int i) {
        for (int j = 0; j < spines.size(); j++) {
            spines.get(j).setEnabled(j == i);
        }
    }

    private void enableNone() {
        for (BookViewSpine b : spines) b.setEnabled(false);
    }

    private void applyActiveOnly(int i) {
        for (int j = 0; j < spines.size(); j++) {
            spines.get(j).setActiveHighlight(j == i);
        }
    }

    private void clearActiveHighlight() {
        for (BookViewSpine b : spines) b.setActiveHighlight(false);
        activeIdx = -1;
    }

    public int currentIndex() { return nextClickableIndex; }

    // ------------- scan cursor tag placement & animation -------------

    private void detachCursorTag() {
        if (cursorTag.getParent() instanceof StackPane p) {
            p.getChildren().remove(cursorTag);
        }
        cursorTag.setOpacity(0.0);
    }

    private void attachCursorTagTo(int i) {
        if (i < 0 || i >= spines.size()) { detachCursorTag(); return; }

        // Remove from old parent if any
        detachCursorTag();

        StackPane target = spines.get(i);
        StackPane.setAlignment(cursorTag, Pos.TOP_CENTER);
        cursorTag.setTranslateY(0);
        target.getChildren().add(cursorTag);

        // Fade/slide in subtly
        cursorTag.setOpacity(0.0);
        cursorTag.setTranslateY(-8);

        Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(cursorTag.opacityProperty(), 0.0),
                        new KeyValue(cursorTag.translateYProperty(), -8)
                ),
                new KeyFrame(Duration.millis(220),
                        new KeyValue(cursorTag.opacityProperty(), 1.0, Interpolator.EASE_BOTH),
                        new KeyValue(cursorTag.translateYProperty(), 0, Interpolator.EASE_BOTH)
                )
        );
        tl.play();
    }
}