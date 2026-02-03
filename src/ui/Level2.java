package ui;

import core.AssetLoader;
import core.LevelConfig;
import core.GameState;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Level 2:
 * - 4 shelf rows in code (no PNG rows). Continuous frame (top cap + posts + bottom base).
 * - Books bottom-align to plank (RowView y-nudges for perfection).
 * - Randomized spines (no adjacent repeats); numbers 10..99 except targets.
 * - Guided cases: BEST -> AVERAGE -> NONE -> WORST.
 * - Targets: L2_TARGETS[0], [1], [2]=172 (never found), [3].
 * - Replayability: per-run row->case shuffle; positions (Best=0, Worst=9; Avg ~3..6).
 * - Hearts FX: HUD pulse + floating "−1 ❤" near clicked spine.
 * - Telemetry counters; case-based snapshots for LearningsBoard.
 * - Checklist: HUD overlay (non-blocking) showing targets and status.
 */
public class Level2 extends BaseLevel {

    // ---------- Layout tuning ----------
    private static final double SHELF_DESIGN_WIDTH = 670.0;
    private static final double SHELF_SCALE        = 0.70;

    /** Inner width: space available between the vertical posts */
    private static final double TARGET_SHELF_WIDTH = SHELF_DESIGN_WIDTH * SHELF_SCALE;

    // Books
    private static final double BOOK_SPACING = -3;
    private static final double BOOK_FIT_H   = 175 * SHELF_SCALE;

    // Stack shelves tightly (use child margins for overlap)
    private static final double ROW_OVERLAP_PX = 6;

    // Per-row vertical nudges so spines sit on the plank
    private static final int[] ROW_Y_NUDGE = { -10, -9, -9, -15 };

    // --- Geometry for code-drawn shelf rows ---
    private static final double ROW_VISUAL_HEIGHT           = 200 * SHELF_SCALE; // total height for each shelf row
    private static final double PLANK_THICKNESS             = 15  * SHELF_SCALE; // plank lip thickness
    private static final double BASELINE_OFFSET_FROM_BOTTOM = 20  * SHELF_SCALE; // baseline above bottom

    // --- Frame (continuous borders) ---
    private static final double FRAME_POST_W   = 24 * SHELF_SCALE; // left/right posts
    private static final double FRAME_TOP_H    = 28 * SHELF_SCALE; // thick top cap
    private static final double FRAME_BOTTOM_H = 22 * SHELF_SCALE; // bottom base enabled

    // --------- Replayability: teaching cases & plan ----------
    private enum L2Case { BEST, AVERAGE, NONE, WORST }

    /** rowIndex -> case this row represents for THIS RUN */
    private List<L2Case> rowCases = new ArrayList<>(4);

    private int posBest, posAvg, posWorst; // target positions for cases
    private long seed;                     // per-run seed for reproducible randomness

    private int findRowForCase(L2Case c) {
        for (int i = 0; i < rowCases.size(); i++) {
            if (rowCases.get(i) == c) return i;
        }
        return -1;
    }
    private L2Case caseOfRow(int rowIdx) {
        return rowCases.get(rowIdx);
    }

    private void makeReplayablePlan() {
        Random r = new Random(seed);

        // Shuffle which physical row represents each case
        List<L2Case> plan = new ArrayList<>(List.of(L2Case.BEST, L2Case.AVERAGE, L2Case.NONE, L2Case.WORST));
        Collections.shuffle(plan, r);
        this.rowCases = plan;

        // Teaching positions (fixed or variable)
        posBest  = 0;                // Best at index 0 (first book)
        posAvg   = 4 + r.nextInt(3); // 3..6 (middle-ish)
        posWorst = 9;                // last (index 9)
    }

    // ------------- UI containers -------------
    private VBox shelf;
    private final OpenBookOverlay overlay = new OpenBookOverlay();
    private RowView[] rows;

    private final Random rng = new Random();

    private int currentTargetIdx = 0; // 0..3 (24,17,172,31)
    private int currentRowIdx = 0;    // 0..3

    // === Learnings: snapshot store and flags ===
    private final LearningsSnapStore learnSnaps = new LearningsSnapStore();
    private boolean snap1FirstRowUnlockedCaptured = false;
    private boolean snap2BestFoundCaptured = false;
    private boolean snap3AvgFoundCaptured = false;
    private boolean snap4NonePromptCaptured = false;
    private boolean snap5WorstFoundCaptured = false;

    // --- Telemetry ---
    private int telemetryWrongOrder = 0;
    private int telemetryLockedClick = 0;
    private int telemetryCorrectThis = 0;
    private int telemetrySkipToEnd = 0;
    private int telemetryConfirmNone = 0;

    // ---------- Checklist (HUD, non-blocking) ----------
    private ChecklistPanel checklist;

    // Max score for star thresholds (4 correct events in Level 2)
    private static final int MAX_SCORE_LEVEL2 = LevelConfig.L2_SCORE_CORRECT * 4;

    @Override
    protected String getLevelTitle() {
        return LevelConfig.LEVEL_2_TITLE;
    }

    @Override
    protected String getBackgroundPath() {
        return AssetLoader.L2_BG;
    }

    @Override
    protected void initLevel(StackPane worldLayer, double w, double h) {
        // Seed & per-run plan for replayability
        this.seed = System.nanoTime();
        makeReplayablePlan();

        // Content canvas centered
        StackPane canvas = new StackPane();
        canvas.setAlignment(Pos.CENTER);
        worldLayer.getChildren().add(canvas);

        // Vertical block of rows
        shelf = new VBox();
        shelf.setAlignment(Pos.CENTER);
        shelf.setSpacing(0);
        shelf.setTranslateX(43);

        rows = new RowView[LevelConfig.L2_ROWS];
        buildShelfRows(shelf);

        // Overlay above rows
        canvas.getChildren().addAll(shelf, overlay);
        StackPane.setAlignment(overlay, Pos.CENTER);

        // Intro
        var next1 = UiUtil.btn("Next");
        var next2 = UiUtil.btn("Next");
        var startBtn = UiUtil.btn("Start");
        startBtn.setOnAction(e -> {
            dialogue.hide();
            unlockCase(L2Case.BEST);
            promptCurrentTarget();

            // Create checklist with L2 targets (non-blocking HUD)
            if (checklist == null) {
                checklist = new ChecklistPanel(LevelConfig.L2_TARGETS);
                addToStatusLayer(checklist);
                // Pin top-right with margin (no translate)
                StackPane.setAlignment(checklist, Pos.TOP_LEFT);
                StackPane.setMargin(checklist, new Insets(90,0, 0, 16));
            } else {
                checklist.reset();
            }

            // SNAP 1: first row unlocked (case-agnostic)
            if (!snap1FirstRowUnlockedCaptured) {
                SnapshotUtil.captureNode(getBlurTarget(), 2.0, Color.TRANSPARENT, img -> {
                    learnSnaps.clear();
                    learnSnaps.add(img);
                    snap1FirstRowUnlockedCaptured = true;
                });
            }
        });

        // --- PART 1 ---
        final String introPart1 =
                "\nYOU FOUND MY MAGICAL LIBRARY?!\n" +
                        "But beware, seeker - this is no ordinary bookshelf!\n" +
                        "There are magical books numbered 24, 17, 172, and 31...\n" +
                        "but they're cleverly hidden among ordinary tomes.";

        next1.setOnAction(e -> {
            // --- PART 2 ---
            final String introPart2 =
                    "\nEach row contains 10 books, but be careful:\n" +
                            "- Some rows hold a magical book, but only ONE magical book can exist per row.\n" +
                            "- Others may contain nothing at all.\n" +
                            "You must search each book LEFT to RIGHT with patience and precision!";
            say(introPart2, null, next2);
        });

        next2.setOnAction(e -> {
            // --- PART 3 ---
            final String introPart3 =
                    "\nThe magic reveals itself only to those who follow the path.\n" +
                            "When you find the magical book (or confirm its absence), you'll be whisked to the next mysterious row automatically.\n" +
                            "Begin your search when you're ready!\n" +
                            "Remember: first " + LevelConfig.L2_TARGETS[0] + ", then " +
                            LevelConfig.L2_TARGETS[1] + ", then " +
                            LevelConfig.L2_TARGETS[2] + ", and " +
                            LevelConfig.L2_TARGETS[3] + ".";
            say(introPart3, null, startBtn);
        });

        say(introPart1, null, next1);
    }

    private void buildShelfRows(VBox container) {
        for (int rowIdx = 0; rowIdx < 4; rowIdx++) {
            L2Case c = caseOfRow(rowIdx);
            List<Integer> nums;
            switch (c) {
                case BEST   -> nums = randomRowNumbersWithTarget(LevelConfig.L2_TARGETS[0], posBest);
                case AVERAGE-> nums = randomRowNumbersWithTarget(LevelConfig.L2_TARGETS[1], posAvg);
                case NONE   -> nums = randomRowNumbersWithoutTargets(); // 10..99 only (so 172 won't appear)
                case WORST  -> nums = randomRowNumbersWithTarget(LevelConfig.L2_TARGETS[3], posWorst);
                default     -> throw new IllegalStateException("Unexpected case: " + c);
            }
            rows[rowIdx] = makeRow(rowIdx, nums, BOOK_FIT_H, BOOK_SPACING);
        }

        // Wrap each RowView with a code-drawn ShelfRow (first row shows a thin top cap)
        StackPane r1 = makeShelfRow(0, rows[0], TARGET_SHELF_WIDTH, true);
        StackPane r2 = makeShelfRow(1, rows[1], TARGET_SHELF_WIDTH, false);
        StackPane r3 = makeShelfRow(2, rows[2], TARGET_SHELF_WIDTH, false);
        StackPane r4 = makeShelfRow(3, rows[3], TARGET_SHELF_WIDTH, false);

        VBox rowsBox = new VBox();
        rowsBox.setAlignment(Pos.TOP_LEFT);
        rowsBox.setSpacing(0);
        VBox.setMargin(r1, new Insets(0, 0, 0, 0));
        VBox.setMargin(r2, new Insets(-ROW_OVERLAP_PX, 0, 0, 0));
        VBox.setMargin(r3, new Insets(-ROW_OVERLAP_PX, 0, 0, 0));
        VBox.setMargin(r4, new Insets(-ROW_OVERLAP_PX, 0, 0, 0));
        rowsBox.getChildren().setAll(r1, r2, r3, r4);

        double rowsTotalH = (ROW_VISUAL_HEIGHT * 4) - (ROW_OVERLAP_PX * 3);
        double outerW = TARGET_SHELF_WIDTH + 2 * FRAME_POST_W;
        double outerH = FRAME_TOP_H + rowsTotalH + FRAME_BOTTOM_H;

        BookshelfFrame frame = new BookshelfFrame(outerW, outerH, FRAME_POST_W, FRAME_TOP_H, FRAME_BOTTOM_H);

        StackPane holder = new StackPane();
        holder.setMinSize(outerW, outerH);
        holder.setPrefSize(outerW, outerH);
        holder.setMaxSize(outerW, outerH);

        rowsBox.setPadding(new Insets(FRAME_TOP_H, FRAME_POST_W, FRAME_BOTTOM_H, FRAME_POST_W));
        StackPane.setAlignment(rowsBox, Pos.TOP_LEFT);

        holder.getChildren().setAll(frame, rowsBox);
        container.getChildren().setAll(holder);
    }

    /** One visual row: code-drawn shelf behind, RowView on top, bottom-aligned to plank baseline. */
    private StackPane makeShelfRow(int rowIdx, RowView rowView, double innerWidth, boolean showTopCap) {
        ShelfRow shelfNode = new ShelfRow(
                innerWidth, ROW_VISUAL_HEIGHT, PLANK_THICKNESS, BASELINE_OFFSET_FROM_BOTTOM, showTopCap
        );

        StackPane layer = new StackPane(shelfNode, rowView);
        StackPane.setAlignment(shelfNode, Pos.TOP_LEFT);
        StackPane.setAlignment(rowView, Pos.TOP_LEFT);

        layer.setMinWidth(innerWidth);
        layer.setPrefWidth(innerWidth);
        layer.setMaxWidth(innerWidth);

        double baselineY = shelfNode.getPlankBaselineY();
        double y = baselineY - BOOK_FIT_H;
        rowView.setTranslateY(y);

        if (rowIdx >= 0 && rowIdx < ROW_Y_NUDGE.length) {
            rowView.setTranslateY(rowView.getTranslateY() + ROW_Y_NUDGE[rowIdx]);
        }

        layer.setMinHeight(ROW_VISUAL_HEIGHT);
        layer.setPrefHeight(ROW_VISUAL_HEIGHT);
        layer.setMaxHeight(ROW_VISUAL_HEIGHT);

        return layer;
    }

    private RowView makeRow(int rowIndex, List<Integer> numbers, double fitH, double spacing) {
        final int styles = AssetLoader.l2SpineCount();
        final int[] lastIdx = { -1 };

        RowView row = new RowView(
                rowIndex,
                LevelConfig.L2_BOOKS_PER_ROW,
                i -> {
                    int styleIdx;
                    do {
                        styleIdx = rng.nextInt(styles);
                    } while (styles > 1 && styleIdx == lastIdx[0]);
                    lastIdx[0] = styleIdx;

                    Image spine = AssetLoader.spineByIndex(styleIdx);
                    return new BookViewSpine(i, spine, fitH);
                },
                spacing
        );

        row.setOnLockedClick((r, clickedIdx) -> {
            telemetryLockedClick++;
            var spine = r.spines.get(clickedIdx);
            loseHeartWithFx(spine);
            UiUtil.shake(spine);
            glow(spine, Color.web("#FF4A4A"), 22, 240);
            say("You can't check books randomly.\nUnlock the row and scan left to right.", null);
            hideDialogueAfter(1500);
        });

        row.setOnWrongOrder((r, clickedIdx) -> {
            telemetryWrongOrder++;
            var spine = r.spines.get(clickedIdx);
            loseHeartWithFx(spine);
            UiUtil.shake(spine);
            glow(spine, Color.web("#FF4A4A"), 22, 240);
            say("Whoa there, eager seeker! In Linear Search, you must check each book\n" +
                    "systematically from LEFT TO RIGHT. Return to where you left off and continue\n" +
                    "your search properly, one book at a time.", null);
            hideDialogueAfter(2500);
        });

        row.setOnOpen((r, idx) -> {
            int number = numbers.get(idx);
            overlay.showValue(number);
            glow(r.spines.get(idx), Color.web("#E6C15A"), 24, 280);
            showPerBookActions(r, idx, number);
        });

        return row;
    }

    private void showPerBookActions(RowView row, int idx, int number) {
        var btnThis = UiUtil.btn("THIS IS IT");
        var btnNot  = UiUtil.btn("NOT THIS ONE");
        var btnSkip = UiUtil.btn("SKIP TO END (-1 ❤)");

        btnThis.setOnAction(e -> onThisIsIt(row, idx, number));

        btnNot.setOnAction(e -> {
            glow(row.spines.get(idx), Color.web("#FFC04A"), 24, 300);
            onNotThisOne(row, idx);
        });

        btnSkip.setOnAction(e -> {
            telemetrySkipToEnd++;
            // 1) Lose heart near the current spine (same as before)
            loseHeartWithFx(row.spines.get(idx));

            // 2) Do NOT move the scanner — so DO NOT call row.skipToEnd() anymore

            // 3) Give feedback: shake the whole row a little
            UiUtil.shake(row);

            // 4) Wizard message (auto-hide shortly)
            say("Fool! you think you can take shortcuts in linear search?", null);
            hideDialogueAfter(1800);

            // 5) Close the open-book overlay so they can continue from the same position
            overlay.hideOverlay();
        });

        say("What do you think?", null, btnThis, btnNot, btnSkip);
    }

    private void onThisIsIt(RowView row, int idx, int number) {
        dialogue.hide();
        overlay.hideOverlay();

        final int target = LevelConfig.L2_TARGETS[currentTargetIdx]; // 24 -> 17 -> 172 -> 31
        final L2Case caseAtFind = caseOfRow(currentRowIdx);
        boolean correctClaim = (caseAtFind != L2Case.NONE) && (number == target);

        if (!correctClaim) {
            var spine = row.spines.get(idx);
            loseHeartWithFx(spine);
            getGameState().loseScore(5);
            UiUtil.shake(spine);
            glow(spine, Color.web("#FF4A4A"), 28, 360);
            return;
        }

        telemetryCorrectThis++;
        getGameState().addScore(LevelConfig.L2_SCORE_CORRECT);
        UiUtil.placePop(row.spines.get(idx), Color.LIGHTGREEN);
        glow(row.spines.get(idx), Color.web("#4EED6A"), 28, 360);
        row.lock();

        // Mark FOUND for this target in the checklist
        if (checklist != null) {
            checklist.setFound(target);
        }

        var keep = new PauseTransition(Duration.millis(380));
        keep.setOnFinished(ev2 -> {
            setPermanentGlow(row.spines.get(idx), Color.web("#4EED6A"), 24, 0.65, 0.85);

            // Case-based snapshots
            if (caseAtFind == L2Case.BEST && !snap2BestFoundCaptured) {
                SnapshotUtil.captureNode(getBlurTarget(), 2.0, Color.TRANSPARENT, img -> {
                    learnSnaps.add(img);
                    snap2BestFoundCaptured = true;
                });
            }
            if (caseAtFind == L2Case.AVERAGE && !snap3AvgFoundCaptured) {
                SnapshotUtil.captureNode(getBlurTarget(), 2.0, Color.TRANSPARENT, img -> {
                    learnSnaps.add(img);
                    snap3AvgFoundCaptured = true;
                });
            }
            if (caseAtFind == L2Case.WORST) {
                if (!snap5WorstFoundCaptured) {
                    SnapshotUtil.captureNode(getBlurTarget(), 2.0, Color.TRANSPARENT, img -> {
                        learnSnaps.add(img);
                        snap5WorstFoundCaptured = true;

                        int finalScore = getGameState().getScore();
                        showSurvivedThen(finalScore, null, MAX_SCORE_LEVEL2, this::showLevel2Learnings);
                    });
                } else {
                    int finalScore = getGameState().getScore();
                    showSurvivedThen(finalScore, null, MAX_SCORE_LEVEL2, this::showLevel2Learnings);
                }
                return;
            }

            // Advance guided flow
            if (caseAtFind == L2Case.BEST) {
                currentTargetIdx = 1;
                unlockCase(L2Case.AVERAGE);
                promptCurrentTarget();
            } else if (caseAtFind == L2Case.AVERAGE) {
                currentTargetIdx = 2;          // now looking for 172 (not found)
                unlockCase(L2Case.NONE);
                promptCurrentTarget();
            } else {
                unlockCase(L2Case.WORST);
                promptCurrentTarget();
            }
        });
        keep.play();
    }

    private void onNotThisOne(RowView row, int idx) {
        dialogue.hide();
        overlay.hideOverlay();

        boolean hasNext = row.advance();
        if (!hasNext) {
            var btnConfirm = UiUtil.btn("CONFIRM NOT FOUND");
            var btnDouble  = UiUtil.btn("DOUBLE CHECK (-1 ❤)");

            btnConfirm.setOnAction(e -> onConfirmNotFound(row));
            btnDouble.setOnAction(e -> {
                var spine = row.spines.get(Math.max(0, idx));
                loseHeartWithFx(spine);
                row.resetForDoubleCheck();
                dialogue.hide();
            });

            say("Reached the end of this row.", null, btnConfirm, btnDouble);

            if (caseOfRow(row.rowIndex) == L2Case.NONE && !snap4NonePromptCaptured) {
                SnapshotUtil.captureNode(getBlurTarget(), 2.0, Color.TRANSPARENT, img -> {
                    learnSnaps.add(img);
                    snap4NonePromptCaptured = true;
                });
            }
        }
    }

    private void onConfirmNotFound(RowView row) {
        dialogue.hide();

        if (caseOfRow(row.rowIndex) == L2Case.NONE) {
            telemetryConfirmNone++;
            getGameState().addScore(LevelConfig.L2_SCORE_CORRECT);

            // Mark NOT FOUND for current target (should be 172 here)
            if (checklist != null) {
                int targetNow = LevelConfig.L2_TARGETS[currentTargetIdx];
                checklist.setNotFound(targetNow);
            }

            currentTargetIdx = 3;              // now looking for 31
            unlockCase(L2Case.WORST);
            promptCurrentTarget();
        } else {
            loseHeartWithFx(row);
            getGameState().loseScore(5);
            UiUtil.pulseGlow(row, 420);
            row.resetForDoubleCheck();
        }
    }

    private void unlockRow(int idx) {
        currentRowIdx = idx;
        rows[idx].unlock();
    }

    private void unlockCase(L2Case c) {
        int idx = findRowForCase(c);
        if (idx < 0) throw new IllegalStateException("Row for case " + c + " not found");
        unlockRow(idx);
    }

    private void promptCurrentTarget() {
        if (currentTargetIdx != 0) {
            say("You've uncovered the secret of this row.\n" +
                    "Now, prepare to be transported... Find " + LevelConfig.L2_TARGETS[currentTargetIdx] + ".", null);
            hideDialogueAfter(2500);
        }
    }

    private void hideDialogueAfter(int ms) {
        PauseTransition p = new PauseTransition(Duration.millis(ms));
        p.setOnFinished(ev -> dialogue.hide());
        p.playFromStart();
    }

    // ---------------- Randomized numbers per row ----------------

    private List<Integer> randomRowNumbersWithTarget(int target, int targetPos) {
        int n = LevelConfig.L2_BOOKS_PER_ROW;
        Set<Integer> banned = Arrays.stream(LevelConfig.L2_TARGETS).boxed()
                .filter(t -> t != target)
                .collect(Collectors.toSet());

        List<Integer> nums = new ArrayList<>(Collections.nCopies(n, 0));
        Set<Integer> used = new HashSet<>();
        used.add(target);

        for (int i = 0; i < n; i++) {
            if (i == targetPos) continue;
            int val;
            do {
                val = rng.nextInt(90) + 10; // 10..99
            } while (used.contains(val) || banned.contains(val));
            used.add(val);
            nums.set(i, val);
        }
        nums.set(targetPos, target);
        return nums;
    }

    private List<Integer> randomRowNumbersWithoutTargets() {
        int n = LevelConfig.L2_BOOKS_PER_ROW;
        Set<Integer> banned = Arrays.stream(LevelConfig.L2_TARGETS).boxed().collect(Collectors.toSet());

        List<Integer> nums = new ArrayList<>(n);
        Set<Integer> used = new HashSet<>();

        for (int i = 0; i < n; i++) {
            int val;
            do {
                val = rng.nextInt(90) + 10; // 10..99
            } while (used.contains(val) || banned.contains(val));
            used.add(val);
            nums.add(val);
        }
        return nums;
    }

    // ---------------- Visual feedback helpers ----------------
    private void glow(StackPane node, Color color, double maxRadius, int ms) {
        DropShadow ds = new DropShadow();
        ds.setSpread(0.65);
        ds.setRadius(0);
        ds.setColor(color.deriveColor(0, 1, 1, 0.0));
        node.setEffect(ds);

        Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(ds.radiusProperty(), 0),
                        new KeyValue(ds.colorProperty(), color.deriveColor(0, 1, 1, 0.0))
                ),
                new KeyFrame(Duration.millis(ms * 0.35),
                        new KeyValue(ds.radiusProperty(), maxRadius),
                        new KeyValue(ds.colorProperty(), color.deriveColor(0, 1, 1, 0.85))
                ),
                new KeyFrame(Duration.millis(ms),
                        new KeyValue(ds.radiusProperty(), 0),
                        new KeyValue(ds.colorProperty(), color.deriveColor(0, 1, 1, 0.0))
                )
        );
        tl.setOnFinished(ev -> node.setEffect(null));
        tl.playFromStart();
    }

    private void setPermanentGlow(StackPane node, Color color, double radius, double spread, double opacity) {
        DropShadow ds = new DropShadow();
        ds.setSpread(spread);
        ds.setRadius(radius);
        ds.setColor(color.deriveColor(0, 1, 1, opacity));
        node.setEffect(ds);
    }

    // -------- Hearts feedback --------
    private void loseHeartWithFx(Node anchor) {
        getGameState().loseHeart();
        try {
            HUD hudAccess = getHud();
            if (hudAccess != null) hudAccess.pulseHearts();
        } catch (Throwable ignore) {}
        showMinusOneHeartBubble(anchor);
    }

    private void showMinusOneHeartBubble(Node anchor) {
        if (anchor == null || anchor.getScene() == null) return;

        Label bubble = new Label("−1 ❤");
        bubble.setStyle("-fx-text-fill: #FF4A4A; -fx-font-size: 18px; -fx-font-weight: bold;");
        bubble.setMouseTransparent(true);
        bubble.setOpacity(0);

        addToStatusLayer(bubble);
        StackPane.setAlignment(bubble, Pos.TOP_LEFT);

        Platform.runLater(() -> {
            StackPane statusLayer = getStatusLayer();
            if (statusLayer == null || bubble.getParent() == null) return;

            Bounds bScene = anchor.localToScene(anchor.getBoundsInLocal());
            Point2D topCenterScene = new Point2D(
                    bScene.getMinX() + bScene.getWidth() / 2.0,
                    bScene.getMinY()
            );
            Point2D p = statusLayer.sceneToLocal(topCenterScene);

            bubble.setTranslateX(p.getX());
            bubble.setTranslateY(p.getY());

            Timeline tl = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(bubble.opacityProperty(), 0.0),
                            new KeyValue(bubble.translateYProperty(), p.getY())
                    ),
                    new KeyFrame(Duration.millis(150),
                            new KeyValue(bubble.opacityProperty(), 1.0)
                    ),
                    new KeyFrame(Duration.millis(750),
                            new KeyValue(bubble.translateYProperty(), p.getY() - 24),
                            new KeyValue(bubble.opacityProperty(), 0.0)
                    )
            );
            tl.setOnFinished(e -> statusLayer.getChildren().remove(bubble));
            tl.play();
        });
    }

    // ---------- Learnings screen for Level 2 ----------
    private void showLevel2Learnings() {
        var imgs = learnSnaps.all();
        var snaps = new java.util.ArrayList<LearningSnap>(5);

        if (imgs.size() >= 1 && imgs.get(0) != null)
            snaps.add(new LearningSnap(imgs.get(0),
                    "[Start] First active row unlocked — scan left to right."));

        if (imgs.size() >= 2 && imgs.get(1) != null)
            snaps.add(new LearningSnap(imgs.get(1),
                    "[Best Case] Found immediately — Linear Search best case: O(1)."));

        if (imgs.size() >= 3 && imgs.get(2) != null)
            snaps.add(new LearningSnap(imgs.get(2),
                    "[Average Case] Found after several checks — Linear Search average: O(n)."));

        if (imgs.size() >= 4 && imgs.get(3) != null)
            snaps.add(new LearningSnap(imgs.get(3),
                    "[Not Found] End reached — confirm not found or double check."));

        if (imgs.size() >= 5 && imgs.get(4) != null)
            snaps.add(new LearningSnap(imgs.get(4),
                    "[Worst Case] Found at the end — Linear Search worst: O(n)."));

        if (snaps.isEmpty()) return;

        LearningsBoard board = new LearningsBoard(
                getBlurTarget(),
                AssetLoader.image(AssetLoader.LEARNING_BOARD),
                snaps,
                () -> {
                    reportTelemetry();
                    learnSnaps.clear();
                    goToLevelSelectAfterCompletion(1);
                },
                true
        );

        addToStatusLayer(board);
        board.show();
    }

    private void reportTelemetry() {
        System.out.printf(Locale.US,
                "L2 Telemetry — wrongOrder=%d, lockedClick=%d, correctThis=%d, skipToEnd=%d, confirmNone=%d%n",
                telemetryWrongOrder, telemetryLockedClick, telemetryCorrectThis, telemetrySkipToEnd, telemetryConfirmNone);
    }
}