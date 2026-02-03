package ui;

import javafx.scene.image.WritableImage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LearningsSnapStore {
    private final List<WritableImage> snaps = new ArrayList<>(3);

    public void clear() { snaps.clear(); }
    public void add(WritableImage img) { if (img != null) snaps.add(img); }

    public List<WritableImage> all() {
        return Collections.unmodifiableList(snaps);
    }
}