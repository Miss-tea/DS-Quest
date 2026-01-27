
package ui;

import javafx.animation.*;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Glow;
import javafx.scene.effect.InnerShadow;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import javafx.animation.Interpolator;

/**
 * Attaches a mysterious, pulsing aura to a wizard Node (e.g., the dialogue avatar ImageView).
 * Effect stack: InnerShadow (inward rim) -> DropShadow (soft outer aura) -> Glow (overall bloom)
 * + a very subtle vertical bob.
 */
public final class WizardAura {
    private WizardAura(){}

    /**
     * Attaches the animated aura to the given wizard node and starts it immediately.
     * @param wizard the avatar node (e.g., ImageView) to decorate
     * @param base   the base color for the aura (e.g., Color.MEDIUMPURPLE)
     * @return an Animation you can stop later (ParallelTransition of bob + pulse)
     */
    public static Animation attach(Node wizard, Color base) {
        if (wizard == null) return null;

        // --- Build effect chain: InnerShadow -> DropShadow -> Glow ---
        InnerShadow inner = new InnerShadow();
        inner.setColor(base);
        inner.setRadius(20);
        inner.setChoke(0.70); // tighter magical rim

        DropShadow outer = new DropShadow();
        outer.setColor(base.deriveColor(0, 1.0, 1.0, 0.75));
        outer.setRadius(18);
        outer.setSpread(0.75);
        outer.setInput(inner);

        Glow glow = new Glow(0.25);
        glow.setInput(outer);

        wizard.setEffect(glow);

        // --- Soft bobbing (float) ---
        double startY = wizard.getTranslateY();
        TranslateTransition bob = new TranslateTransition(Duration.millis(1600), wizard);
        bob.setFromY(startY - 2.0);
        bob.setToY(startY + 2.0);
        bob.setAutoReverse(true);
        bob.setCycleCount(Animation.INDEFINITE);
        bob.setInterpolator(Interpolator.EASE_BOTH);

        // --- Pulse the glow and radii over time ---
        Timeline pulse = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(glow.levelProperty(), 0.22, Interpolator.EASE_BOTH),
                        new KeyValue(outer.radiusProperty(), 14.0, Interpolator.EASE_BOTH),
                        new KeyValue(inner.chokeProperty(), 0.58, Interpolator.EASE_BOTH)
                ),
                new KeyFrame(Duration.millis(900),
                        new KeyValue(glow.levelProperty(), 0.45, Interpolator.EASE_BOTH),
                        new KeyValue(outer.radiusProperty(), 26.0, Interpolator.EASE_BOTH),
                        new KeyValue(inner.chokeProperty(), 0.76, Interpolator.EASE_BOTH)
                )
        );
        pulse.setAutoReverse(true);
        pulse.setCycleCount(Animation.INDEFINITE);

        ParallelTransition fx = new ParallelTransition(bob, pulse);
        fx.play();
        return fx;
    }

    /** Removes any effect from the wizard node. (Call after stopping the returned Animation.) */
    public static void detach(Node wizard) {
        if (wizard != null) wizard.setEffect(null);
    }
}

