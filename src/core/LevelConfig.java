
package core;

import java.util.Map;

public final class LevelConfig {
    private LevelConfig() {}

    // Global config for Level-1
    public static final int START_HEARTS = 5;
    public static final int QUIZ_ROUNDS = 4;

    // Scores
    public static final int SCORE_INSERTION_CORRECT = 20;
    public static final int SCORE_QUIZ_CORRECT = 5;
    public static final int SCORE_FINAL_KEY = 5;      // lowest
    public static final int SCORE_FINAL_SHIELD = 10;  // average
    public static final int SCORE_FINAL_BEST = 20;    // best strategy

    // Correct slot arrangement
    // slot 0=potion, 1=key, 2=sword, 3=shield, 4=torch
    public static final Map<Integer, Artifact> CORRECT_ORDER = Map.of(
            0, Artifact.POTION,
            1, Artifact.KEY,
            2, Artifact.SWORD,
            3, Artifact.SHIELD,
            4, Artifact.TORCH
    );
}

