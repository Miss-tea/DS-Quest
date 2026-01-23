
package ui;

import core.Artifact;
import core.AssetLoader;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.Pos;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.function.Predicate;


import java.util.function.Consumer;
public class SlotView extends StackPane {

    private final int index;
    private Artifact artifact; // nullable
    private final ImageView iconHolder = new ImageView();
    private final ImageView slotFrame = AssetLoader.imageView(AssetLoader.SLOT_BG, 140, 140, true);
    private final Label idxLabel = new Label();

    private Predicate<Artifact> acceptPredicate = a -> true;

    private boolean removable = true;  // default true
    private Consumer<SlotView> onBlockedRemoval = null; // <-- NEW: callback when blocked
    public void setRemovable(boolean value) {
        this.removable = value;
    }


    public void setOnBlockedRemoval(Consumer<SlotView> callback) { // <-- NEW
        this.onBlockedRemoval = callback;
    }

    public SlotView(int index) {
        this.index = index;
        setAlignment(Pos.CENTER);
        setPrefSize(150, 150);
        setMaxSize(150, 150);

        Font f = AssetLoader.loadFont("/fonts/CinzelDecorative-Bold.ttf", 18);
        idxLabel.setText("[" + index + "]");
        idxLabel.setTextFill(Color.rgb(220, 180, 120));
        idxLabel.setFont(f);
        idxLabel.setTranslateY(-58);

        getChildren().addAll(slotFrame, iconHolder, idxLabel);
        iconHolder.setTranslateY(-12);
        setEffect(new DropShadow(12, Color.BLACK));

        // Accept drags from inventory
        setOnDragOver(e -> {
            if (e.getGestureSource() != this && e.getDragboard().hasString() && isEmpty()) {
                String s = e.getDragboard().getString();
                if (s.startsWith("artifact:")) {
                    Artifact a = Artifact.valueOf(s.substring("artifact:".length()));
                    if (acceptPredicate.test(a)) {
                        e.acceptTransferModes(TransferMode.MOVE);
                    }
                }
            }
            e.consume();
        });

        setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            boolean success = false;
            if (db.hasString() && isEmpty()) {
                String s = db.getString();
                if (s.startsWith("artifact:")) {
                    Artifact a = Artifact.valueOf(s.substring("artifact:".length()));
                    if (acceptPredicate.test(a)) {
                        setArtifact(a);
                        success = true;
                    }
                }
            }
            e.setDropCompleted(success);
            e.consume();
        });

        // Right-click to remove the item back to inventory
        setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.SECONDARY && !isEmpty()) {
                Artifact removed = removeArtifact();
                fireEvent(new SlotClearedEvent(this, removed));
            }
        });


        setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.SECONDARY && !isEmpty()) {
                if (removable) {
                    Artifact removed = removeArtifact();
                    fireEvent(new SlotClearedEvent(this, removed));
                } else {
                    // 🔴 Removal is blocked — play feedback if provided
                    if (onBlockedRemoval != null) onBlockedRemoval.accept(this);
                    e.consume();
                }
            }
        });


    }

    public void setAcceptPredicate(Predicate<Artifact> p) {
        this.acceptPredicate = p != null ? p : a -> true;
    }

    public boolean isEmpty() { return artifact == null; }
    public Artifact getArtifact() { return artifact; }
    public int getIndex() { return index; }

    public void setArtifact(Artifact a) {
        this.artifact = a;
        if (a == null) {
            iconHolder.setImage(null);
        } else {
            ImageView iv = AssetLoader.artifactView(a, 80);
            SnapshotParameters sp = new SnapshotParameters();
            sp.setFill(Color.TRANSPARENT);
            iconHolder.setImage(iv.snapshot(sp, null));
        }
        fireEvent(new SlotChangedEvent(this));
    }

    /** Removes artifact and returns the previous item. */
    public Artifact removeArtifact() {
        Artifact prev = this.artifact;
        this.artifact = null;
        iconHolder.setImage(null);
        fireEvent(new SlotChangedEvent(this));
        return prev;
    }

    // -------- Custom events (safe getters, no name clash with EventObject.source) --------
    public static class SlotChangedEvent extends Event {
        public static final EventType<SlotChangedEvent> SLOT_CHANGED =
                new EventType<>(Event.ANY, "SLOT_CHANGED");
        private final SlotView slot;
        public SlotChangedEvent(SlotView slot) {
            super(SLOT_CHANGED);
            this.slot = slot;
        }
        public SlotView getSlot() { return slot; }
    }

    public static class SlotClearedEvent extends Event {
        public static final EventType<SlotClearedEvent> SLOT_CLEARED =
                new EventType<>(Event.ANY, "SLOT_CLEARED");
        private final SlotView slot;
        private final Artifact removed;
        public SlotClearedEvent(SlotView slot, Artifact removed) {
            super(SLOT_CLEARED);
            this.slot = slot;
            this.removed = removed;
        }
        public SlotView getSlot() { return slot; }
        public Artifact getRemoved() { return removed; }
    }
}
