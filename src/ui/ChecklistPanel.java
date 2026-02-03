package ui;

import core.AssetLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.LinkedHashMap;
import java.util.Map;

public class ChecklistPanel extends VBox {

    public enum Status { PENDING, FOUND, NOT_FOUND }

    private static class ItemRow extends HBox {
        final Label statusIcon = new Label("☐");
        final Label textLabel  = new Label();
        Status status = Status.PENDING;

        ItemRow(int target, Font textFont, Font iconFont, Color textColor) {
            setSpacing(8);
            setAlignment(Pos.CENTER_LEFT);

            // Status icon: use a symbol-capable font so ☑/✅/❌ always render
            if (iconFont != null) statusIcon.setFont(iconFont);
            // pastel off-white
            statusIcon.setTextFill(Color.web("#e8e6df"));

            // Number text
            textLabel.setText(String.valueOf(target));
            if (textFont != null) textLabel.setFont(textFont);
            textLabel.setTextFill(textColor);

            getChildren().addAll(statusIcon, textLabel);
        }

        void setStatus(Status s) {
            this.status = s;
            switch (s) {
                case PENDING    -> statusIcon.setText("☐");
                case FOUND      -> statusIcon.setText("✅");
                case NOT_FOUND  -> statusIcon.setText("❌");
            }
        }
    }

    private final Map<Integer, ItemRow> items = new LinkedHashMap<>();

    public ChecklistPanel(int[] targets) {
        // ---- PANEL LOOK & BEHAVIOR ----
        setSpacing(8);                       // compact
        setAlignment(Pos.TOP_LEFT);
        setPadding(new Insets(6));
        setStyle("""
            -fx-background-color: rgba(216,158,101,0.68);
            -fx-border-color: rgba(2,1,1,0.53);
            -fx-border-width: 1.2;
            -fx-background-radius: 10;
            -fx-border-radius: 10;
        """);

        // Hug content height (prevent StackPane from stretching)
        setFillWidth(false);
        setPrefHeight(Region.USE_COMPUTED_SIZE);
        setMinHeight(Region.USE_PREF_SIZE);
        setMaxHeight(Region.USE_PREF_SIZE);

        // HUD overlay: never block clicks
        setMouseTransparent(true);
        setPickOnBounds(false);

        // Narrower width helps reduce height (less horizontal room)
        setMaxWidth(210);

        // ---- FONTS (ensure resources exist under /fonts) ----
        // Title font (Cinzel Decorative)
        Font titleFont = AssetLoader.loadFont("/fonts/Montaga-Regular.ttf", 25);

        // Row text font (Montaga). NOTE: ensure file exists at /fonts/Montaga-Regular.ttf
        Font rowTextFont = AssetLoader.loadFont("/fonts/Montaga-Regular.ttf", 25);

        // Status icon font — pick a symbol-capable font; if not available, null is fine (use default)
        // Try Segoe UI Symbol if you ship it; otherwise leave null to use system fallback.
        Font iconFont =  AssetLoader.loadFont("/fonts/SegoeUI-Symbol.ttf", 28);

        // ---- TITLE ----
        Label title = new Label("Targets Checklist");
        if (titleFont != null) title.setFont(titleFont);
        title.setTextFill(Color.web("#ffffff"));
        getChildren().add(title);

        // ---- ROWS ----
        Color rowTextColor = Color.web("#f2f2f2");
        for (int t : targets) {
            ItemRow row = new ItemRow(t, rowTextFont, iconFont, rowTextColor);
            items.put(t, row);
            getChildren().add(row);
        }
    }

    public void setFound(int target) {
        ItemRow row = items.get(target);
        if (row != null) row.setStatus(Status.FOUND);
    }

    public void setNotFound(int target) {
        ItemRow row = items.get(target);
        if (row != null) row.setStatus(Status.NOT_FOUND);
    }

    public void reset() {
        items.values().forEach(r -> r.setStatus(Status.PENDING));
    }
}