
package core;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

public class GameState {
    private final IntegerProperty score = new SimpleIntegerProperty(0);
    private final IntegerProperty hearts = new SimpleIntegerProperty(LevelConfig.START_HEARTS);

    public IntegerProperty scoreProperty() { return score; }
    public IntegerProperty heartsProperty() { return hearts; }

    public int getScore() { return score.get(); }
    public void addScore(int delta) { score.set(score.get() + delta); }

    public int getHearts() { return hearts.get(); }
    public void loseHeart() {
        if (hearts.get() > 0) hearts.set(hearts.get() - 1);
    }

    public boolean isAlive() { return hearts.get() > 0; }
}

