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
    // ---------- Level 2 ----------
    public static final int L2_ROWS = 4;
    public static final int L2_BOOKS_PER_ROW = 10;

    // Wizard announces in this order
    public static final int[] L2_TARGETS = { 31, 24,172, 17 };

    // Row roles (0-based). NOT_FOUND is on row index 2 (third row).
    public static final int L2_ROW_BEST  = 0; // 17 near start
    public static final int L2_ROW_AVG   = 1; // 24 near middle
    public static final int L2_ROW_NONE  = 2; // NOT FOUND while searching for 31
    public static final int L2_ROW_WORST = 3; // 31 near the end

    // Scoring (+15 for correct THIS IS IT or correct CONFIRM NOT FOUND)
    public static final int L2_SCORE_CORRECT = 15;

    // Penalties (−1 ❤)
    public static final int L2_PENALTY_WRONG_ORDER    = 1;
    public static final int L2_PENALTY_SKIP_TO_END    = 1;
    public static final int L2_PENALTY_WRONG_CLAIM    = 1;
    public static final int L2_PENALTY_WRONG_NOTFOUND = 1;
    public static final int L2_PENALTY_DOUBLE_CHECK   = 1;

    // ---------------- Level Titles ----------------
    public static final String LEVEL_1_TITLE = "Array Dungeon";
    public static final String LEVEL_2_TITLE = "Linear Search in Library";
    public static final String LEVEL_3_TITLE = "Binary Search-Cursed Apple";
    public static final String LEVEL_4_TITLE = "Level 4";
    public static final String LEVEL_5_TITLE = "Level 5";
    public static final String LEVEL_6_TITLE = "Level 6";
    public static final String LEVEL_7_TITLE = "Level 7";
}