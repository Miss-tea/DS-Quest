package ui;

import core.Artifact;
import core.AssetLoader;
import core.LevelConfig;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.*;
import javafx.scene.media.AudioClip;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

/**
 * Level 1 with three parts: insertion, quiz, final puzzle.
 * Auto-screenshots are captured at:
 *  - Part 1 START (end of initLevel, after first pulse),
 *  - Part 2 START (after quiz overlay is added),
 *  - Part 3 START.
 * The Learnings page appears after LevelStatusBoard DONE and navigates to LevelSelect on NEXT.
 *
 * Added polish:
 * - Floating "−1 ❤" + HUD pulse.
 * - Green aura highlights for Poison targets (slots 1 & 3).
 * - Slow rotating fog motes on quiz overlay.
 * - Ghostly whisper when Poison placed correctly.
 * - Dynamic explanations on mistakes.
 * - Shake + crack effect when wrong.
 * - Holographic preview in sacrifice mode.
 */
public class Level1 extends BaseLevel {

    private final List<SlotView> slots = new ArrayList<>(5);
    private final InventoryView inventory = new InventoryView();

    // Keep a reference to the world layout so we can add/remove UI pieces
    private VBox worldRoot;

    // Single DONE button reused across parts (no duplicates)
    private HBox doneWrap;
    private Button doneBtn;

    // Flow flags
    private boolean part1Done = false;
    private int quizRound = 0;

    // --- Quiz sequencing control ---
    private static final int TOTAL_QUIZ = 4; // exactly 4 questions
    private final List<Integer> quizOrder = new ArrayList<>(TOTAL_QUIZ);

    // Final puzzle state
    private boolean removalMode = false;
    private Artifact removedArtifact = null; // only the first removal counts (still tracked)
    private boolean poisonWired = false;     // wire poison listener once
    private Integer poisonPlacedIdx = null;  // null until poison is placed (1 or 3)

    // Requested final scoring
    private static final int SCORE_SHIELD_REMOVED = 20;
    private static final int SCORE_KEY_REMOVED    = 10;
    private static final int SCORE_TORCH_REMOVED  = 30;
    private static final int SCORE_POTION_REMOVED = 25;

    // === Quiz visual lock fields ===
    private StackPane quizOverlay;      // smoky overlay layer
    private boolean quizVisualActive = false;
    private GaussianBlur quizBlur;      // blur effect applied to the board
    private BorderPane boardRef;        // reference to the board (so we can blur/unblur)

    // === Instruction Paper reference (for dynamic text) ===
    private VBox instructionBox;        // the paper panel (has a Label with id="instrText")

    // -------------------- Max score for star calculation --------------------
    private static final int MAX_FINAL_STRATEGY =
            Math.max(Math.max(SCORE_SHIELD_REMOVED, SCORE_KEY_REMOVED),
                    Math.max(SCORE_TORCH_REMOVED, SCORE_POTION_REMOVED));

    private static final int MAX_SCORE_LEVEL1 =
            LevelConfig.SCORE_INSERTION_CORRECT
                    + (TOTAL_QUIZ * LevelConfig.SCORE_QUIZ_CORRECT)
                    + MAX_FINAL_STRATEGY;

    // === Learnings: snapshot store ===
    private final LearningsSnapStore learnSnaps = new LearningsSnapStore();

    // ---- Assets / FX for new polish ----
    private AudioClip whisperClip;              // optional SFX
    private StackPane holoOverlay;              // hologram preview overlay (status layer)

    @Override
    protected String getLevelTitle() {
        return LevelConfig.LEVEL_1_TITLE;
    }

    @Override
    protected void initLevel(StackPane worldLayer, double w, double h) {
        // --- Slots row ---
        HBox slotRow = new HBox(20);
        slotRow.setAlignment(Pos.CENTER_LEFT);
        for (int i = 0; i < 5; i++) {
            SlotView s = new SlotView(i);
            slots.add(s);
            slotRow.getChildren().add(s);
        }

        // --- Instruction (left) with instruction_paper.png background ---
        instructionBox = InstructionPaper.build(); // <- build paper panel with label id
        VBox leftWrap = new VBox(instructionBox);
        leftWrap.setAlignment(Pos.TOP_LEFT);
        leftWrap.setPadding(new Insets(0, 0, 0, 10));
        leftWrap.setPrefWidth(280);

        // --- Board (slots in center, instruction on the left) ---
        BorderPane board = new BorderPane();
        board.setCenter(slotRow);
        board.setLeft(leftWrap);
        BorderPane.setMargin(slotRow, new Insets(150, 0, 0, 0)); // push slots down
        this.boardRef = board; // keep a reference for blur/overlay

        // --- Inventory (bottom) ---
        HBox bottom = new HBox(inventory);
        bottom.setAlignment(Pos.BOTTOM_CENTER);
        bottom.setPadding(new Insets(10, 40, 30, 0));
        inventory.setTranslateY(6);

        // --- Combine into world container ---
        VBox world = new VBox(20, board, bottom);
        world.setAlignment(Pos.CENTER);
        world.setPadding(new Insets(40, 30, 40, 30));
        worldRoot = world;

        worldLayer.getChildren().add(world);

        // --- Initialize inventory (no poison yet) ---
        inventory.clear();
        inventory.add(Artifact.KEY);
        inventory.add(Artifact.SWORD);
        inventory.add(Artifact.POTION);
        inventory.add(Artifact.TORCH);
        inventory.add(Artifact.SHIELD);

        // On placement: hide in shelf + pop feedback
        slots.forEach(s -> s.addEventHandler(SlotView.SlotChangedEvent.SLOT_CHANGED, ev -> {
            SlotView sv = ev.getSlot();
            if (!sv.isEmpty()) {
                inventory.hide(sv.getArtifact());

                var a = sv.getArtifact();
                var color =
                        (a == Artifact.POTION) ? javafx.scene.paint.Color.LIGHTGREEN :
                                (a == Artifact.KEY)    ? javafx.scene.paint.Color.GOLD :
                                        (a == Artifact.SWORD)  ? javafx.scene.paint.Color.SILVER :
                                                (a == Artifact.SHIELD) ? javafx.scene.paint.Color.DEEPSKYBLUE :
                                                        (a == Artifact.TORCH)  ? javafx.scene.paint.Color.ORANGERED :
                                                                (a == Artifact.POISON) ? javafx.scene.paint.Color.MEDIUMPURPLE :
                                                                        javafx.scene.paint.Color.WHITE;

                UiUtil.placePop(sv, color);
            }
        }));

        // Right‑click: return to shelf + record first removal (for final puzzle)
        slots.forEach(s ->
                s.addEventHandler(SlotView.SlotClearedEvent.SLOT_CLEARED, ev -> {
                    Artifact rem = ev.getRemoved();
                    if (rem != null) {
                        inventory.show(rem);
                        if (removalMode && removedArtifact == null) {
                            removedArtifact = rem;
                        }
                    }
                })
        );

        // === Single DONE button for the entire level (reused) ===
        doneBtn = UiUtil.btn("DONE");
        doneBtn.setOnAction(e -> validatePart1()); // Part 1 behavior
        doneWrap = new HBox(doneBtn);
        doneWrap.setAlignment(Pos.CENTER);
        world.getChildren().add(doneWrap);

        // === SNAP 1: Part 1 START (after first pulse) — full frame (background + world + HUD/Dialog) ===
        Platform.runLater(() -> {
            SnapshotUtil.captureNode(getBlurTarget(), 2.0, Color.TRANSPARENT, img -> {
                learnSnaps.clear(); // start fresh
                learnSnaps.add(img);
            });
        });
        // --- Wizard start instruction (with button) ---
        Button gotIt = UiUtil.btn("Got it");
        gotIt.setOnAction(ev -> dialogue.hide());

        say("\nPlace all artifacts according to the conditions.", null, gotIt);

        // Init optional whisper
        initWhisper();
    }

    // ----------------------- Part 1: validate insertion order -----------------------
    // ----------------------- Part 1: validate insertion order -----------------------
    private void validatePart1() {
        if (part1Done) return;

        boolean allFilled = slots.stream().noneMatch(SlotView::isEmpty);
        if (!allFilled) {
            // 🔄 Restore whole-row shake when not all slots are filled
            shake(slots.toArray(new Node[0]));
            say("Place all 5 artifacts into the slots according to the instruction first.", null);
            hideDialogueThen(1600, null);
            return; // ⛔ Stop here; don't proceed to order validation
        }

        boolean correct = true;
        SlotView firstWrong = null; // HEART FX anchor
        for (SlotView s : slots) {
            Artifact should = LevelConfig.CORRECT_ORDER.get(s.getIndex());
            if (s.getArtifact() != should) {
                correct = false;
                if (firstWrong == null) firstWrong = s;
                break;
            }
        }

        if (correct) {
            getGameState().addScore(LevelConfig.SCORE_INSERTION_CORRECT);
            part1Done = true;

            quizRound = 0;
            prepareQuizOrder();

            Button startBtn = UiUtil.btn("Start Quiz");
            startBtn.setOnAction(ev -> startQuiz());
            say("So,you know Insertion....Now I'll teleport you to the slots randomly.You have to say what you see there.", null, startBtn);

        } else {
            // ❤️ Heart FX near the first wrong slot (fallback to DONE button or board)
            Node anchor = (firstWrong != null) ? firstWrong : (doneBtn != null ? doneBtn : boardRef);
            loseHeartWithFx(anchor);

            // 🔄 Already had whole-row shake here — keep it
            shake(slots.toArray(new Node[0]));

            say("Wrong order! Heart -1.\nAll items are back on the shelf—try inserting again.", null);

            // Reset after feedback
            resetSlotsToInventory();
            hideDialogueThen(1800, null);
        }
    }

    /** Moves all items from slots back to the inventory and clears the board. */
    private void resetSlotsToInventory() {
        for (SlotView s : slots) {
            if (!s.isEmpty()) {
                inventory.show(s.getArtifact());
                s.setArtifact(null);
            }
        }
    }

    // ----------------------- Quiz order: 4 rounds; last is Sword -----------------------
    private void prepareQuizOrder() {
        quizOrder.clear();

        Integer swordIdx = findIndexOf(Artifact.SWORD);
        if (swordIdx == null) swordIdx = 2;

        List<Integer> pool = new ArrayList<>(Arrays.asList(0, 1, 2, 3, 4));
        pool.remove(swordIdx);
        Collections.shuffle(pool);

        quizOrder.add(pool.get(0));
        quizOrder.add(pool.get(1));
        quizOrder.add(pool.get(2));
        quizOrder.add(swordIdx);
    }

    // ----------------------- Part 2: Quiz (4 rounds; last is sword) -----------------------
    private void startQuiz() {
        // Hide the DONE button during quiz (only on first entry)
        if (quizRound == 0 && doneWrap != null) {
            worldRoot.getChildren().remove(doneWrap);
        }

        // Lock interactions + enter blur/fume on first entry
        if (quizRound == 0) {
            setSlotsInteractive(false);
            enterQuizVisualLock();

            // SNAP 2: Part 2 START (whole frame with smoky overlay) — defer a pulse
            Platform.runLater(() ->
                    SnapshotUtil.captureNode(getBlurTarget(), 2.0, Color.TRANSPARENT, learnSnaps::add)
            );
        }

        if (quizRound == 0 || quizOrder.size() != TOTAL_QUIZ) {
            prepareQuizOrder();
        }

        if (quizRound >= TOTAL_QUIZ) {
            // Quiz ended — restore interactions and remove blur/fume
            setSlotsInteractive(true);
            exitQuizVisualLock();

            startFinalPuzzle();
            return;
        }

        final int qIdx = quizOrder.get(quizRound);

        Artifact c = slots.get(qIdx).getArtifact();
        if (c == null) c = LevelConfig.CORRECT_ORDER.get(qIdx);
        final Artifact correctAns = c;

        final List<Artifact> pool = Arrays.stream(Artifact.values())
                .filter(a -> a != Artifact.POISON)
                .filter(a -> a != correctAns)
                .toList();
        final Artifact wrongAns = pool.get(new Random().nextInt(pool.size()));

        final Button optCorrect = UiUtil.btn(correctAns.displayName());
        final Button optWrong   = UiUtil.btn(wrongAns.displayName());

        final boolean isLastRound = (quizRound == TOTAL_QUIZ - 1);
        final boolean isSwordQuestion = (correctAns == Artifact.SWORD);

        optCorrect.setOnAction(ev -> {
            getGameState().addScore(LevelConfig.SCORE_QUIZ_CORRECT);

            if (isLastRound && isSwordQuestion) {
                dialogue.hide();
                quizRound++;
                startQuiz(); // jumps to final puzzle
            } else {
                say("Correct! +" + LevelConfig.SCORE_QUIZ_CORRECT + " points.", null);
                hideDialogueThen(900, () -> {
                    quizRound++;
                    startQuiz();
                });
            }
        });

        optWrong.setOnAction(ev -> {
            // Heart FX near the asked slot; dynamic explanation; shake+crack
            loseHeartWithFx(slots.get(qIdx));
            explainWrongQuiz(qIdx, correctAns);
            feedbackWrongOnSlot(slots.get(qIdx));
            hideDialogueThen(900, () -> {
                quizRound++;
                startQuiz();
            });
        });

        final String qText = "What do you see at the index[" + qIdx + "]?";
        if (new Random().nextBoolean()) {
            say(qText, null, optCorrect, optWrong);
        } else {
            say(qText, null, optWrong, optCorrect);
        }
    }

    // ----------------------- Part 3: Cursed Sword + Poison strategy -----------------------
    private void startFinalPuzzle() {
        // 🔒 Lock Sword slot [2] from removal in Part 3
        SlotView swordSlot = slots.get(2);
        swordSlot.setRemovable(false);
        swordSlot.setOnBlockedRemoval(sv -> {
            UiUtil.shake(sv);
            UiUtil.pulseGlow(sv, 320);
            say("The Sword is cursed—you cannot remove it.", null);
            hideDialogueThen(900, null);
        });

        // SNAP 3: Part 3 START (whole frame)
        SnapshotUtil.captureNode(getBlurTarget(), 2.0, Color.TRANSPARENT, learnSnaps::add);

        // 🔁 Update instruction paper to your Tip for Part 3
        if (instructionBox != null) {
            var node = instructionBox.lookup("#instrText");
            if (node instanceof javafx.scene.control.Label tipLbl) {

                var out = new javafx.animation.FadeTransition(Duration.millis(180), tipLbl);
                out.setFromValue(1.0);
                out.setToValue(0.0);

                out.setOnFinished(ev -> {
                    tipLbl.setText("Tip:\nFavor rises\nwhen flame\nfades,and\ndefense\nendures.");
                    tipLbl.setStyle("-fx-text-fill: #2c2416; -fx-font-size: 18px; -fx-font-weight: bold;");

                    var in = new javafx.animation.FadeTransition(Duration.millis(4000), tipLbl);
                    in.setFromValue(0.0);
                    in.setToValue(1.0);
                    in.play();
                });

                out.play();
            }
        }

        // Narrative + options
        say("Huhahahah! The Sword at index 2 is cursed.\n" +
                        "You must place magical poison at an adjacent slot of the Sword!\n" +
                        "You have two options:",
                null, requestBtn(), removeBtn());

        // Reuse the same DONE button for Part 3 finalization
        if (doneBtn != null) {
            doneBtn.setOnAction(e -> finalizeFinalPuzzle());
            if (!worldRoot.getChildren().contains(doneWrap)) {
                worldRoot.getChildren().add(doneWrap);
            }
        }
    }

    private Button requestBtn() {
        Button b = UiUtil.btn("[Request New Slot]");
        b.setOnAction(e -> {
            // Lose a heart here anchored on the button
            loseHeartWithFx(b);
            // Proceed to gameplay: show POISON and allow removal.
            removalMode = true;
            removedArtifact = null;
            addPoisonToInventory(true);
            installRemovalPreviews(); // allow hover previews in this path too (optional)
            say("FOOL! Do you think slots grow on trees?\nNO realoc() in this dungeon!\nPoison is now in your inventory. Free index 1 or 3 and place it.", null);
            hideDialogueThen(3500, null);
        });
        return b;
    }

    private Button removeBtn() {
        Button b = UiUtil.btn("[Sacrifice an Artifact]");
        b.setOnAction(e -> {
            removalMode = true;
            removedArtifact = null;     // only the first removal will count
            addPoisonToInventory(true); // show poison immediately for insertion
            installRemovalPreviews();   // enable holographic hover previews
            say("Right‑click ONE slot to remove that item back to the shelf.\nThen place POISON at index 1 or 3.", null);
            hideDialogueThen(1500, null);
        });
        return b;
    }

    private void addPoisonToInventory(boolean show) {
        // ensure poison exists in inventory map
        inventory.add(Artifact.POISON);
        if (show) inventory.show(Artifact.POISON);
        else inventory.hide(Artifact.POISON);

        // Allow POISON only at indices 1 or 3 and only when empty
        slots.forEach(s -> s.setAcceptPredicate(a -> {
            if (a != Artifact.POISON) return true;
            int idx = s.getIndex();
            return (idx == 1 || idx == 3) && s.isEmpty();
        }));

        // Turn on green aura highlights for valid targets
        highlightPoisonTargets(true);

        // Wire once: on poison placement -> just remember index (no auto-complete)
        if (!poisonWired) {
            for (SlotView s : slots) {
                s.addEventHandler(SlotView.SlotChangedEvent.SLOT_CHANGED, ev -> {
                    SlotView sv = ev.getSlot();
                    if (sv.getArtifact() == Artifact.POISON) {
                        onPoisonPlaced(sv.getIndex());
                    }
                });
            }
            poisonWired = true;
        }
    }

    /** Only records the index where Poison is placed; finalization is done on DONE button. */
    private void onPoisonPlaced(int idx) {
        poisonPlacedIdx = idx; // valid indices enforced by acceptPredicate
        highlightPoisonTargets(false);  // remove aura after success
        playGhostWhisper();             // ghostly feedback
    }

    /** Determine which original artifact is missing from the final layout (ignores Magical Potion). */
    private Artifact determineRemovedByFinalLayout() {
        EnumSet<Artifact> expected = EnumSet.of(
                Artifact.POTION, Artifact.KEY, Artifact.SWORD, Artifact.SHIELD, Artifact.TORCH
        );
        for (SlotView s : slots) {
            Artifact a = s.getArtifact();
            if (a != null && a != Artifact.POISON) {
                expected.remove(a);
            }
        }
        return (expected.size() == 1) ? expected.iterator().next() : null;
    }

    /** Pressing DONE in Part 3 validates poison and applies the requested strategy scores/messages. */
    private void finalizeFinalPuzzle() {
        // If player presses DONE without inserting poison -> lose heart and warn
        if (poisonPlacedIdx == null) {
            loseHeartWithFx(doneBtn != null ? doneBtn : boardRef);
            explainNeedPoisonFirst();
            hideDialogueThen(1600, null);
            return;
        }

        // Prefer deriving the removed item from the final layout (robust to odd sequences)
        Artifact removedByLayout = determineRemovedByFinalLayout();
        Artifact decision = (removedByLayout != null) ? removedByLayout : removedArtifact;

        int addScore;
        String strategyMsg;

        if (decision == Artifact.SHIELD) {
            addScore = SCORE_SHIELD_REMOVED; // 20
            strategyMsg = "You chose everything over COVER. A calculated risk." +
                    "The next chamber's traps will test if your calculations were correct.";
        } else if (decision == Artifact.KEY) {
            addScore = SCORE_KEY_REMOVED; // 10
            strategyMsg = "You traded ACCESS for POWER. A programmer's folly." +
                    "Ahead lie doors you cannot open—paths forever sealed.";
        } else if (decision == Artifact.TORCH) {
            addScore = SCORE_TORCH_REMOVED; // 30
            strategyMsg = "You paid with light to keep both key and shield." +
                    "A trade of vision for versatility. True strategist vibes.";
        } else if (decision == Artifact.POTION) {
            addScore = SCORE_POTION_REMOVED; // 25
            strategyMsg = "You paid with healing to keep both key and shield." +
                    "But how will you survive your wrong steps in the large dungeon?";
        } else {
            addScore = 0;
            strategyMsg = "Strategy applied.";
        }

        getGameState().addScore(addScore);
        removalMode = false;
        clearHologram();

        // Hide the reused DONE button after completion
        if (doneWrap != null) {
            worldRoot.getChildren().remove(doneWrap);
        }

        // === Show the Level Status Board (with stars), then Learnings ===
        int finalScore = getGameState().getScore();
        showSurvivedThen(finalScore, strategyMsg, MAX_SCORE_LEVEL1, this::showLevel1Learnings);
    }

    private Integer findIndexOf(Artifact a) {
        for (SlotView s : slots) {
            if (s.getArtifact() == a) return s.getIndex();
        }
        return null;
    }

    // ---------- Interaction locking (for quiz) ----------
    private void setSlotsInteractive(boolean interactive) {
        slots.forEach(s -> {
            if (interactive) {
                // Restore to permissive; Part 3 will reapply poison-only rule
                s.setAcceptPredicate(a -> true);
            } else {
                // Block all drops
                s.setAcceptPredicate(a -> false);
            }
            // Block mouse interactions entirely (right-click removal, etc.)
            s.setMouseTransparent(!interactive);
        });

        // Lock/unlock the inventory so the player can't drag icons
        inventory.setMouseTransparent(!interactive);
    }

    // ---------- Quiz visual effects (blur + fume + fog motes) ----------
    private void enterQuizVisualLock() {
        if (quizVisualActive) return;
        quizVisualActive = true;

        // 1) Apply blur to the board area (slots + instruction)
        if (quizBlur == null) quizBlur = new GaussianBlur(0);
        Timeline blurOn = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(quizBlur.radiusProperty(), 0)),
                new KeyFrame(Duration.millis(300), new KeyValue(quizBlur.radiusProperty(), 16))
        );
        boardRef.setEffect(quizBlur);
        blurOn.playFromStart();

        // 2) Add a smoky translucent overlay on top of the world
        if (quizOverlay == null) {
            quizOverlay = new StackPane();
            quizOverlay.setPickOnBounds(true);      // capture clicks
            quizOverlay.setMouseTransparent(false); // block interactions
            quizOverlay.setStyle("-fx-background-color: rgba(10,10,10,0.28);"); // base dim

            // Soft “breathing” opacity animation (fume feel)
            Timeline fadeIn = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(quizOverlay.opacityProperty(), 0.0)),
                    new KeyFrame(Duration.millis(300), new KeyValue(quizOverlay.opacityProperty(), 1.0))
            );
            fadeIn.playFromStart();

            Timeline pulse = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(quizOverlay.opacityProperty(), 0.95)),
                    new KeyFrame(Duration.seconds(2.0), new KeyValue(quizOverlay.opacityProperty(), 0.85)),
                    new KeyFrame(Duration.seconds(4.0), new KeyValue(quizOverlay.opacityProperty(), 0.95))
            );
            pulse.setCycleCount(Animation.INDEFINITE);
            pulse.play();

            // --- Fog motes ---
            Group motes = createFogMotes(70, 1600, 900);
            quizOverlay.getChildren().add(motes);
        }

        // Place overlay over the world (worldRoot's parent is the StackPane worldLayer)
        ((StackPane) worldRoot.getParent()).getChildren().add(quizOverlay);
    }

    private void exitQuizVisualLock() {
        if (!quizVisualActive) return;
        quizVisualActive = false;

        // Animate blur off
        if (quizBlur != null) {
            Timeline blurOff = new Timeline(
                    new KeyFrame(Duration.millis(2500), new KeyValue(quizBlur.radiusProperty(), 0))
            );
            blurOff.setOnFinished(e -> boardRef.setEffect(null));
            blurOff.playFromStart();
        } else {
            boardRef.setEffect(null);
        }

        // Remove overlay
        if (quizOverlay != null) {
            Timeline fadeOut = new Timeline(
                    new KeyFrame(Duration.millis(600), new KeyValue(quizOverlay.opacityProperty(), 0.0))
            );
            fadeOut.setOnFinished(ev -> {
                ((StackPane) worldRoot.getParent()).getChildren().remove(quizOverlay);
                quizOverlay.setOpacity(1.0); // reset for next time
            });
            fadeOut.playFromStart();
        }
    }

    // ---------- small helpers ----------
    /** Hide the dialogue after 'ms' and then run 'next' (if provided). */
    private void hideDialogueThen(int ms, Runnable next) {
        PauseTransition p = new PauseTransition(Duration.millis(ms));
        p.setOnFinished(ev -> {
            dialogue.hide();
            if (next != null) next.run();
        });
        p.playFromStart();
    }

    /** Shake helper. */
    private void shake(Node... nodes) {
        if (nodes == null) return;
        for (Node n : nodes) UiUtil.shake(n);
    }

    // ---------- HEART FX: floating "-1 ❤" + HUD pulse ----------
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
        bubble.setOpacity(0.0);

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

    // ---------- GREEN AURA for Poison targets ----------
    private void highlightPoisonTargets(boolean on) {
        for (int i = 0; i < slots.size(); i++) {
            SlotView s = slots.get(i);
            if (on && (i == 1 || i == 3)) {
                s.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(76,200,120,0.85), 20, 0.6, 0, 0);");
            } else {
                s.setStyle(null);
            }
        }
    }

    // ---------- Fog motes ----------
    private Group createFogMotes(int count, double width, double height) {
        Group g = new Group();
        Random r = new Random();
        for (int i = 0; i < count; i++) {
            double x = r.nextDouble() * width;
            double y = r.nextDouble() * height;
            double radius = 1.5 + r.nextDouble() * 2.5;

            Circle mote = new Circle(radius, Color.web("#ffffff", 0.22));
            mote.setTranslateX(x);
            mote.setTranslateY(y);

            Timeline flicker = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(mote.opacityProperty(), 0.15 + r.nextDouble() * 0.25)),
                    new KeyFrame(Duration.seconds(2 + r.nextDouble() * 2), new KeyValue(mote.opacityProperty(), 0.05 + r.nextDouble() * 0.20))
            );
            flicker.setAutoReverse(true);
            flicker.setCycleCount(Animation.INDEFINITE);
            flicker.play();

            g.getChildren().add(mote);
        }

        RotateTransition rot = new RotateTransition(Duration.seconds(40), g);
        rot.setFromAngle(0);
        rot.setToAngle(360);
        rot.setCycleCount(Animation.INDEFINITE);
        rot.play();

        return g;
    }

    // ---------- Ghostly whisper ----------
    private void initWhisper() {
        try {
            // Place a whisper.wav at /audio/whisper.wav in resources; else fallback will be used
            var res = getClass().getResource("/audio/whisper.wav");
            if (res != null) {
                whisperClip = new AudioClip(res.toExternalForm());
                whisperClip.setVolume(0.35);
                whisperClip.setRate(0.95);
            }
        } catch (Exception ignore) {
            whisperClip = null;
        }
    }

    private void playGhostWhisper() {
        if (whisperClip != null) {
            whisperClip.play();
        } else {
            say("(a ghostly whisper) \"Good... the curse quivers...\"", null);
            hideDialogueThen(1200, null);
        }
    }

    // ---------- Dynamic explanations ----------
    private void explainWrongInsertion(SlotView wrong) {
        int i = wrong.getIndex();
        Artifact placed = wrong.getArtifact();
        Artifact expected = LevelConfig.CORRECT_ORDER.get(i);

        String placedName = (placed == null) ? "Empty" : placed.displayName();
        String expectedName = (expected == null) ? "Empty" : expected.displayName();

        say("Wrong at index [" + i + "].\n" +
                "Expected: " + expectedName + "\n" +
                "But you placed: " + placedName + "\n" +
                "Heart −1.", null);
    }

    private void explainWrongQuiz(int idx, Artifact shouldBe) {
        String expectedName = (shouldBe == null) ? "Empty" : shouldBe.displayName();
        say("Not quite.\nAt index [" + idx + "] the array holds: " + expectedName + ".\nHeart −1.", null);
    }

    private void explainNeedPoisonFirst() {
        say("The curse resists…\nPoison must be inserted at index 1 or 3 first.\nHeart −1.", null);
    }

    // ---------- Shake + crack feedback ----------
    private void feedbackWrongOnSlot(Node slotNode) {
        if (slotNode == null) return;

        UiUtil.shake(slotNode);

        // Draw temp cracks on parent overlay
        if (!(slotNode.getParent() instanceof Pane parent)) return;

        Group cracks = new Group();
        cracks.setMouseTransparent(true);

        Line l1 = new Line(6, 6, 34, 18);
        Line l2 = new Line(34, 18, 12, 28);
        Line l3 = new Line(12, 28, 30, 42);
        for (Line l : new Line[]{l1, l2, l3}) {
            l.setStroke(Paint.valueOf("#ffffff"));
            l.setOpacity(0.9);
            l.setStrokeWidth(1.6);
        }

        Bounds b = slotNode.getBoundsInParent();
        cracks.setTranslateX(b.getMinX() + 6);
        cracks.setTranslateY(b.getMinY() + 6);

        parent.getChildren().add(cracks);

        Timeline t = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(cracks.opacityProperty(), 0.95)),
                new KeyFrame(Duration.millis(450), new KeyValue(cracks.opacityProperty(), 0.0))
        );
        t.setOnFinished(e -> parent.getChildren().remove(cracks));
        t.play();
    }

    // ---------- Holographic preview on sacrifice mode ----------
    private void installRemovalPreviews() {
        if (holoOverlay == null) {
            holoOverlay = new StackPane();
            holoOverlay.setPickOnBounds(false);
            holoOverlay.setMouseTransparent(true);
        }
        addToStatusLayer(holoOverlay);

        for (SlotView s : slots) {
            s.setOnMouseEntered(ev -> {
                if (!removalMode) return;
                Artifact a = s.getArtifact();
                if (a == null || a == Artifact.SWORD) return;
                showHologramForSlot(s, a);
            });
            s.setOnMouseExited(ev -> clearHologram());
        }
    }

    private void showHologramForSlot(SlotView s, Artifact a) {
        clearHologram();
        String text = switch (a) {
            case SHIELD -> "Fate Preview:\nLose DEFENSE.\nStrategy +20";
            case KEY    -> "Fate Preview:\nLose ACCESS.\nStrategy +10";
            case TORCH  -> "Fate Preview:\nLose LIGHT.\nStrategy +30";
            case POTION -> "Fate Preview:\nLose HEALING.\nStrategy +25";
            default     -> "Fate Preview:\nNo change.";
        };

        Label holo = new Label(text);
        holo.setStyle("""
            -fx-background-color: rgba(120,200,255,0.12);
            -fx-text-fill: rgba(180,230,255,0.95);
            -fx-border-color: rgba(120,200,255,0.35);
            -fx-border-width: 1.2;
            -fx-padding: 10 14;
            -fx-font-size: 14px;
            -fx-background-radius: 10;
            -fx-border-radius: 10;
        """);
        holo.setMouseTransparent(true);

        Timeline shimmer = new Timeline(
                new KeyFrame(Duration.ZERO,        new KeyValue(holo.opacityProperty(), 0.0)),
                new KeyFrame(Duration.millis(220), new KeyValue(holo.opacityProperty(), 1.0)),
                new KeyFrame(Duration.millis(1200),new KeyValue(holo.opacityProperty(), 0.9))
        );
        shimmer.setAutoReverse(true);
        shimmer.setCycleCount(Animation.INDEFINITE);

        holoOverlay.getChildren().add(holo);
        StackPane.setAlignment(holo, Pos.TOP_LEFT);

        Platform.runLater(() -> {
            if (holo.getParent() == null) return;
            Bounds bScene = s.localToScene(s.getBoundsInLocal());
            Point2D p = getStatusLayer().sceneToLocal(
                    bScene.getMinX() + bScene.getWidth() + 10,
                    bScene.getMinY() - 6
            );
            holo.setTranslateX(p.getX());
            holo.setTranslateY(p.getY());
            shimmer.play();
        });
    }

    private void clearHologram() {
        if (holoOverlay != null) holoOverlay.getChildren().clear();
    }

    // ---------- Instruction panel with PAPER background ----------
    private static class InstructionPaper {
        static VBox build() {
            VBox box = new VBox(8);
            box.setAlignment(Pos.TOP_LEFT);

            // Comfortable padding inside paper
            box.setPadding(new Insets(36, 44, 32, 80));
            // Keep stable size
            box.setMinWidth(280);
            box.setPrefWidth(280);
            box.setMaxWidth(280);

            // Background image: instruction_paper.png
            try {
                BackgroundImage bi = new BackgroundImage(
                        core.AssetLoader.image(core.AssetLoader.INSTRUCTION_PAPER),
                        BackgroundRepeat.NO_REPEAT,
                        BackgroundRepeat.NO_REPEAT,
                        BackgroundPosition.CENTER,
                        new BackgroundSize(
                                BackgroundSize.AUTO, BackgroundSize.AUTO,
                                true, true, true, false
                        )
                );
                box.setBackground(new Background(bi));
            } catch (Exception e) {
                box.setBackground(new Background(new BackgroundFill(
                        javafx.scene.paint.Color.rgb(250, 244, 223, 0.85),
                        CornerRadii.EMPTY, Insets.EMPTY
                )));
            }

            String text = "CONDITION:\n" +
                    "[0] = Healing \n" +
                    "[1] = Escape  \n" +
                    "[2] = Attack  \n" +
                    "[3] = Defence \n" +
                    "[4] = Light   ";

            var lbl = UiUtil.paper(text);
            lbl.setId("instrText"); // <- so we can change it in Part 3
            lbl.setStyle("-fx-text-fill: #2c2416; -fx-font-size: 18px; -fx-font-weight: bold;");
            lbl.setWrapText(true);
            lbl.setMaxWidth(box.getPrefWidth() - (box.getPadding().getLeft() + box.getPadding().getRight()));
            box.getChildren().add(lbl);
            return box;
        }
    }

    // ---------- Learnings screen ----------
    private void showLevel1Learnings() {
        var imgs = learnSnaps.all();
        var snaps = new java.util.ArrayList<LearningSnap>(3);

        if (imgs.size() > 0 && imgs.get(0) != null)
            snaps.add(new LearningSnap(imgs.get(0), "Array is a sequential memory,here you saw slots address are sequential.You can insert elements in array simply"));
        if (imgs.size() > 1 && imgs.get(1) != null)
            snaps.add(new LearningSnap(imgs.get(1), "Array is random accessible.You can access data from array randomly"));
        if (imgs.size() > 2 && imgs.get(2) != null)
            snaps.add(new LearningSnap(imgs.get(2), "Array is fixed-sized Data Structure..You can't just increase its size.It's fixed at creation.Also deletion of a element is imple in array.\n"));
        snaps.add(LearningSnap.textOnly(
                "    BONUS:\n    ARRAY SYNTAX (C/C++):\n" +
                        "    *Declaration with Size:\n" +
                        "    dataType arrayName[arraySize];\n" +
                        "     Example: int numbers[5];\n\n" +
                        "    *Declaration and Initialization:\n" +
                        "    dataType arrayName[] =                       {value1,value2,value3};\n" +
                        "     Example: int numbers[] = {10, 20, 30};\n\n" +
                        "    Accessing Elements:\n" +
                        "    arrayName[index]\n" +
                        "     Example: int first = numbers[0];\n"
        ));
        if (snaps.isEmpty()) return;

        LearningsBoard board = new LearningsBoard(
                getBlurTarget(),
                AssetLoader.image(AssetLoader.LEARNING_BOARD),
                snaps,
                () -> {
                    learnSnaps.clear();
                    goToLevelSelectAfterCompletion(1);
                },
                true // <-- sequential mode ON
        );

        addToStatusLayer(board);
        board.show();
    }
}