package ui;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;

import java.util.function.Consumer;

public final class SnapshotUtil {
    private SnapshotUtil() {}

    /**
     * Capture a node snapshot (after a pulse), with optional scaling and background fill.
     * @param node target node (ideally attached to a live Scene for accurate visuals)
     * @param scale image scale (e.g., 1.0 native, 2.0 for HiDPI thumbnails)
     * @param bgFill background fill; use Color.TRANSPARENT for alpha PNGs
     * @param callback receives the image on the FX thread
     */
    public static void captureNode(Node node, double scale, Color bgFill, Consumer<WritableImage> callback) {
        Runnable task = () -> {
            // Ensure CSS is applied for correct styling
            node.applyCss();
            // Only Parents can layout children
            if (node instanceof Parent p) {
                p.layout();
            }

            SnapshotParameters params = new SnapshotParameters();
            params.setFill(bgFill != null ? bgFill : Color.TRANSPARENT);
            if (scale != 1.0) {
                params.setTransform(new Scale(scale, scale));
            }

            WritableImage img = node.snapshot(params, null);
            if (callback != null) callback.accept(img);
        };

        // Defer one pulse so any pending CSS/layout/animations settle
        if (Platform.isFxApplicationThread()) {
            Platform.runLater(task);
        } else {
            Platform.runLater(() -> Platform.runLater(task));
        }
    }

    /**
     * Capture a cropped area of the node defined by boundsInNode (node-local coordinates).
     * @param node target node (ideally attached to a live Scene for accurate visuals)
     * @param boundsInNode the region to capture, in the node's local coordinate space
     * @param scale image scale (e.g., 2.0)
     * @param bgFill background fill; use Color.TRANSPARENT for alpha PNGs
     * @param callback receives the image on the FX thread
     */
    public static void captureNodeBounds(Node node, Bounds boundsInNode, double scale, Color bgFill, Consumer<WritableImage> callback) {
        Runnable task = () -> {
            node.applyCss();
            if (node instanceof Parent p) {
                p.layout();
            }

            SnapshotParameters params = new SnapshotParameters();
            params.setFill(bgFill != null ? bgFill : Color.TRANSPARENT);
            if (scale != 1.0) {
                params.setTransform(new Scale(scale, scale));
            }

            // Convert Bounds -> Rectangle2D for viewport
            Rectangle2D viewport = new Rectangle2D(
                    boundsInNode.getMinX(),
                    boundsInNode.getMinY(),
                    boundsInNode.getWidth(),
                    boundsInNode.getHeight()
            );
            params.setViewport(viewport);

            WritableImage img = node.snapshot(params, null);
            if (callback != null) callback.accept(img);
        };

        if (Platform.isFxApplicationThread()) {
            Platform.runLater(task);
        } else {
            Platform.runLater(() -> Platform.runLater(task));
        }
    }
}