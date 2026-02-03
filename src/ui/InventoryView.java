
package ui;

import core.Artifact;
import core.AssetLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

import java.util.EnumMap;
import java.util.Map;

public class InventoryView extends StackPane {

    private final HBox row = new HBox(28);
    private final Map<Artifact, ImageView> items = new EnumMap<>(Artifact.class);


    public InventoryView() {
        setPickOnBounds(false);
        setMouseTransparent(false);

        ImageView shelf = AssetLoader.imageView(AssetLoader.SHELF, 850, 160, true);
        shelf.setMouseTransparent(true);

        row.setAlignment(Pos.CENTER);
        row.setPadding(new Insets(12));
        row.setMouseTransparent(false);


        row.setTranslateY(-8); // try -6 to -12 depending on your art

        getChildren().addAll(shelf, row);
    }


    public void clear() {
        row.getChildren().clear();
        items.clear();
    }

    public void add(Artifact a) {
        if (items.containsKey(a)) return; // only once
        ImageView iv = AssetLoader.artifactView(a, 72);
        iv.setUserData(a);

        iv.setOnDragDetected(ev -> {
            Dragboard db = iv.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent cc = new ClipboardContent();
            cc.putString("artifact:" + ((Artifact) iv.getUserData()).name());
            db.setContent(cc);
            SnapshotParameters sp = new SnapshotParameters();
            sp.setFill(Color.TRANSPARENT);
            db.setDragView(iv.snapshot(sp, null));
            ev.consume();
        });

        items.put(a, iv);
        row.getChildren().add(iv);
    }

    public void show(Artifact a) {
        ImageView iv = items.get(a);
        if (iv == null) {
            add(a);
        } else {
            if (!row.getChildren().contains(iv)) row.getChildren().add(iv);
            iv.setVisible(true);
            iv.setManaged(true);
        }
    }

    public void hide(Artifact a) {
        ImageView iv = items.get(a);
        if (iv != null) {
            row.getChildren().remove(iv);
            iv.setVisible(false);
            iv.setManaged(false);
        }
    }
}
