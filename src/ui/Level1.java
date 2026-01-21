
package ui;

import core.Artifact;
import core.LevelConfig;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;   // <-- added
import java.util.List;
import java.util.Random;

public class Level1 extends BaseLevel {

    private final List<SlotView> slots = new ArrayList<>(5);
    private final InventoryView inventory = new InventoryView();

    // Flow flags
    private boolean part1Done = false;
    private int quizRound = 0;

    // --- Quiz sequencing control ---
    private static final int TOTAL_QUIZ = 4;                 // exactly 4 questions
    private final List<Integer> quizOrder = new ArrayList<>(TOTAL_QUIZ);

    // Final puzzle state
    private boolean removalMode = false;
    private Artifact removedArtifact = null;
    private boolean poisonWired = false; // ensure we wire poison listeners only once

    @Override
    protected void initLevel(StackPane worldLayer, double w, double h) {
        // --- Slots row (nearer and lower, as you adjusted) ---
        HBox slotRow = new HBox(-30);
        slotRow.setAlignment(Pos.BASELINE_LEFT);
        for (int i = 0; i < 5; i++) {
            SlotView s = new SlotView(i);
            slots.add(s);
            slotRow.getChildren().add(s);
        }

        // --- Instruction (left) with instruction_paper.png background ---
        VBox leftPaper = new VBox();
        leftPaper.setAlignment(Pos.TOP_LEFT);
        leftPaper.setPadding(new Insets(0, 0, 0, 10));
        leftPaper.setPrefWidth(280);
        leftPaper.getChildren().add(InstructionPaper.build());

        // --- Board (slots in center, instruction on the left) ---
        BorderPane board = new BorderPane();
        board.setCenter(slotRow);
        board.setLeft(leftPaper);
        // push slots down a bit to match your visual
        BorderPane.setMargin(slotRow, new Insets(150, 0, 0, 0));

        // --- Inventory (bottom, slightly right & lower per your adjustments) ---
        HBox bottom = new HBox(inventory);
        bottom.setAlignment(Pos.BOTTOM_CENTER);
        // top, right, bottom, left
        bottom.setPadding(new Insets(10, 40, 30, 0));
        inventory.setTranslateY(6); // nudge shelf a little lower

        // --- Combine into world container ---
        VBox world = new VBox(20, board, bottom);
        world.setAlignment(Pos.CENTER);
        world.setPadding(new Insets(40, 30, 40, 30));

        worldLayer.getChildren().add(world);

        // --- Initialize inventory (no poison yet) ---
        inventory.clear();
        inventory.add(Artifact.KEY);
        inventory.add(Artifact.SWORD);
        inventory.add(Artifact.POTION);
        inventory.add(Artifact.TORCH);
        inventory.add(Artifact.SHIELD);

        // Hide an item in shelf when it is placed into a slot
        slots.forEach(s ->
                s.addEventHandler(SlotView.SlotChangedEvent.SLOT_CHANGED, ev -> {
                    SlotView sv = ev.getSlot();
                    if (!sv.isEmpty()) {
                        inventory.hide(sv.getArtifact());
                    }
                })
        );

        // Right‑click on a slot returns the item to shelf
        slots.forEach(s ->
                s.addEventHandler(SlotView.SlotClearedEvent.SLOT_CLEARED, ev -> {
                    Artifact rem = ev.getRemoved();
                    if (rem != null) {
                        inventory.show(rem);
                        // During final puzzle, only the first removal counts for strategy scoring
                        if (removalMode && removedArtifact == null) {
                            removedArtifact = rem;
                        }
                    }
                })
        );

        // DONE button for Part 1 validation
        Button done = UiUtil.btn("DONE");
        done.setOnAction(e -> validatePart1());
        HBox doneWrap = new HBox(done);
        doneWrap.setAlignment(Pos.CENTER);
        world.getChildren().add(doneWrap);
    }

    // ----------------------- Part 1: validate insertion order -----------------------
    private void validatePart1() {
        if (part1Done) return;

        boolean allFilled = slots.stream().noneMatch(SlotView::isEmpty);
        if (!allFilled) {
            say("Place all 5 artifacts into the slots according to the instruction first.", null);
            hideDialogueThen(1600, null);
            //return; // (you kept this non-blocking)
        }

        boolean correct = true;
        for (SlotView s : slots) {
            Artifact should = LevelConfig.CORRECT_ORDER.get(s.getIndex());
            if (s.getArtifact() != should) {
                correct = false;
                break;
            }
        }

        if (correct) {
            gameState.addScore(LevelConfig.SCORE_INSERTION_CORRECT);
            part1Done = true;

            // Prepare the 4-question sequence now (so it's ready when quiz starts)
            quizRound = 0;
            prepareQuizOrder();

            // Let the player decide when to start the quiz; do not auto-hide here
            Button startBtn = UiUtil.btn("Start Quiz");
            startBtn.setOnAction(ev -> startQuiz());
            say("So,you know Insertion\nNow I'll teleport you to the slots randomly\nYou have to say what you see there.", null, startBtn);

        } else {
            gameState.loseHeart();
            resetSlotsToInventory(); // return everything to shelf for retry
            shake(slots.toArray(new Node[0]));
            say("Wrong order! Heart -1.\nAll items are back on the shelf—try inserting again.", null);
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

    // ----------------------- Quiz order: 4 rounds; last is Sword; first 3 exclude Sword -----------------------
    /** Build a 4‑question order: first 3 exclude sword, last is sword. */
    private void prepareQuizOrder() {
        quizOrder.clear();

        // Determine sword's index in slots (fallback to 2 if null)
        Integer swordIdx = findIndexOf(Artifact.SWORD);
        if (swordIdx == null) swordIdx = 2;

        // Build pool of indices [0..4] excluding the sword index
        List<Integer> pool = new ArrayList<>(Arrays.asList(0, 1, 2, 3, 4));
        pool.remove(swordIdx);
        Collections.shuffle(pool);

        // Take any 3 non-sword indices first, then the sword index as last
        quizOrder.add(pool.get(0));
        quizOrder.add(pool.get(1));
        quizOrder.add(pool.get(2));
        quizOrder.add(swordIdx);
    }

    // ----------------------- Part 2: Quiz (4 rounds; last is sword) -----------------------

    // ----------------------- Part 2: Quiz (4 rounds; last is sword) -----------------------
    private void startQuiz() {
        // Build the order on first call if needed
        if (quizRound == 0 || quizOrder.size() != TOTAL_QUIZ) {
            prepareQuizOrder();
        }

        // Stop after exactly 4 questions
        if (quizRound >= TOTAL_QUIZ) {
            startFinalPuzzle();
            return;
        }

        // Use the precomputed index for this round
        final int qIdx = quizOrder.get(quizRound);

        Artifact c = slots.get(qIdx).getArtifact();
        if (c == null) c = LevelConfig.CORRECT_ORDER.get(qIdx);
        final Artifact correctAns = c;

        // one wrong answer (different artifact, no POISON)
        final List<Artifact> pool = Arrays.stream(Artifact.values())
                .filter(a -> a != Artifact.POISON)
                .filter(a -> a != correctAns)
                .toList();
        final Artifact wrongAns = pool.get(new Random().nextInt(pool.size()));

        final Button optCorrect = UiUtil.btn(correctAns.displayName());
        final Button optWrong   = UiUtil.btn(wrongAns.displayName());

        // Is this the last question and about SWORD?
        final boolean isLastRound = (quizRound == TOTAL_QUIZ - 1);
        final boolean isSwordQuestion = (correctAns == Artifact.SWORD);

        optCorrect.setOnAction(ev -> {
            gameState.addScore(LevelConfig.SCORE_QUIZ_CORRECT);

            if (isLastRound && isSwordQuestion) {
                // ✅ Last question is SWORD and answered correctly:
                //    add points but DO NOT show "Correct!" message — go on immediately.
                dialogue.hide();
                quizRound++;
                startQuiz(); // will immediately jump to final puzzle
            } else {
                // Normal rounds (1..3) — show feedback briefly
                say("Correct! +" + LevelConfig.SCORE_QUIZ_CORRECT + " points.", null);
                hideDialogueThen(900, () -> {
                    quizRound++;
                    startQuiz();
                });
            }
        });

        optWrong.setOnAction(ev -> {
            gameState.loseHeart();
            // Even in last round, show wrong feedback
            say("Wrong! Heart -1.", null);
            hideDialogueThen(900, () -> {
                quizRound++;
                startQuiz();
            });
        });

        final String qText = "What do you see at the index[" + qIdx + "]?";
        // Keep the question visible until user answers (no auto-hide)
        if (new Random().nextBoolean()) {
            say(qText, null, optCorrect, optWrong);
        } else {
            say(qText, null, optWrong, optCorrect);
        }
    }


    // ----------------------- Part 3: Cursed Sword + Poison strategy -----------------------
    private void startFinalPuzzle() {
        say("Huhahahah! The Sword at index 2 is cursed.\n" +
                        "You must place magical poison at an adjacent slot of the Sword!\n" +
                        "You have two options:",
                null, requestBtn(), removeBtn());
    }

    private Button requestBtn() {
        Button b = UiUtil.btn("[Request New Slot]");
        b.setOnAction(e -> {
            gameState.loseHeart();
            // Even though this is wrong, proceed to gameplay: show POISON and allow removal.
            removalMode = true;
            removedArtifact = null;
            addPoisonToInventory(true);
            say("FOOL!Do you think slots grows on trees!\nNO realoc()in this dungeon!\nPoison is now in your inventory. Free index 1 or 3 and place it.", null);
            hideDialogueThen(3500, null); // return to game screen
        });
        return b;
    }

    private Button removeBtn() {
        Button b = UiUtil.btn("[Sacrifice an Artifact]");
        b.setOnAction(e -> {
            removalMode = true;
            removedArtifact = null;     // only the first removal will count
            addPoisonToInventory(true); // show poison immediately for insertion
            say("Right‑click ONE slot to remove that item back to the shelf.\nThen place POISON at index 1 or 3.", null);
            hideDialogueThen(1500, null); // return to game screen
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

        // Wire once: on poison placement → evaluate strategy and finish
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


    private void onPoisonPlaced(int idx) {
        if (!removalMode) return;

        if (idx != 1 && idx != 3) {
            say("Poison must be at index 1 or 3 (adjacent to Sword). Try again.", null);
            return;
        }

        int addScore = 0;
        String strategyMsg = ""; // <-- dynamic message per removed artifact

        if (removedArtifact == Artifact.KEY) {
            addScore = LevelConfig.SCORE_FINAL_KEY;        // lowest
            strategyMsg = "You traded ACCESS for POWER. A programmer's folly\n" +
                    "   Ahead lie doors you cannot open, paths forever   \n" +
                    "   sealed. The dungeon remembers your sacrifice.";
        } else if (removedArtifact == Artifact.SHIELD) {
            addScore = LevelConfig.SCORE_FINAL_SHIELD;     // average
            strategyMsg = "You chose CURE over COVER. A calculated risk.     \n" +
                    "   The next chamber's traps will test if your        \n" +
                    "   calculations were correct.";
        } else if (removedArtifact == Artifact.TORCH) {
            // best if shield moved to index 4 and poison at 3
            boolean shieldAt4 = findIndexOf(Artifact.SHIELD) == 4;
            boolean poisonAt3 = idx == 3;
            addScore = (shieldAt4 && poisonAt3) ? LevelConfig.SCORE_FINAL_BEST
                    : LevelConfig.SCORE_FINAL_SHIELD;

            // Regardless of best/average scoring derived from layout, your requested text for torch:
            strategyMsg = "You paid with light to keep both key and shield.  \n" +
                    "   A trade of vision for versatility. The mark of    \n" +
                    "   a true strategist in this dungeon of choices.";
        } else {
            // If some other item was removed (not expected), keep neutral messaging
            addScore = 0;
            strategyMsg = "Strategy applied.";
        }

        gameState.addScore(addScore);
        removalMode = false;

        // Show the custom strategy text along with score info (optional to keep points disclosure)
        say(
                "Level Complete! Strategy score +" + addScore + ".\n" + strategyMsg,
                () -> hideDialogueThen(5000, null)
        );
    }


    private Integer findIndexOf(Artifact a) {
        for (SlotView s : slots) {
            if (s.getArtifact() == a) return s.getIndex();
        }
        return null;
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

    /** Shake helper (for wrong order feedback). */
    private void shake(Node... nodes) {
        if (nodes == null) return;
        for (Node n : nodes) {
            UiUtil.shake(n);
        }
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
            lbl.setStyle("-fx-text-fill: #2c2416; -fx-font-size: 18px; -fx-font-weight: bold;");
            lbl.setWrapText(true);
            lbl.setMaxWidth(box.getPrefWidth() -  (44 + 44));

            box.getChildren().add(lbl);
            return box;
        }
    }
}
