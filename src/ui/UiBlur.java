package ui;

import javafx.scene.Node;
import javafx.scene.effect.GaussianBlur;

public final class UiBlur {
    private UiBlur() {}

    public static void apply(Node target, boolean on) {
        if (target == null) return;
        if (on) {
            if (!(target.getEffect() instanceof GaussianBlur)) {
                target.setEffect(new GaussianBlur(16));
            }
        } else {
            target.setEffect(null);
        }
    }
}