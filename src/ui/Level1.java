
package ui;

import core.Artifact;
import core.LevelConfig;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

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

    @Override
    protected void initLevel(StackPane worldLayer, double w, double h) {
        // --- Slots row ---
        HBox slotRow = new HBox(-30);
        slotRow.setAlignment(Pos.BASELINE_LEFT);
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
        bottom.setPadding(new Insets(10, 40, 30, 0)); // top, right, bottom, left
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

        // Hide an item in shelf when it is placed into a slot
        /**slots.forEach(s ->
                s.addEventHandler(SlotView.SlotChangedEvent.SLOT_CHANGED, ev -> {
                    SlotView sv = ev.getSlot();
                    if (!sv.isEmpty()) {
                        inventory.hide(sv.getArtifact());
                    }
                })
        );**/
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


        // Right‑click on a slot returns the item to shelf; record first removal in final puzzle
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
    }

    // ----------------------- Part 1: validate insertion order -----------------------
    private void validatePart1() {
        if (part1Done) return;

        boolean allFilled = slots.stream().noneMatch(SlotView::isEmpty);
        if (!allFilled) {
            say("Place all 5 artifacts into the slots according to the instruction first.", null);
            hideDialogueThen(1600, null);
            // return; // non-blocking per your choice
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

            quizRound = 0;
            prepareQuizOrder();

            Button startBtn = UiUtil.btn("Start Quiz");
            startBtn.setOnAction(ev -> startQuiz());
            say("So,you know Insertion....Now I'll teleport you to the slots randomly.You have to say what you see there.", null, startBtn);

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
            gameState.addScore(LevelConfig.SCORE_QUIZ_CORRECT);

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
            gameState.loseHeart();
            say("Wrong! Heart -1.", null);
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

        // 🔁 Update instruction paper to your Tip for Part 3
        if (instructionBox != null) {

            var node = instructionBox.lookup("#instrText");
            if (node instanceof javafx.scene.control.Label tipLbl) {

                // Fade out
                var out = new javafx.animation.FadeTransition(Duration.millis(180), tipLbl);
                out.setFromValue(1.0);
                out.setToValue(0.0);

                out.setOnFinished(ev -> {
                    // Update new text + style
                    tipLbl.setText("Tip:\nFavor rises\nwhen flame\nfades,and\ndefense\nendures.");
                    tipLbl.setStyle("-fx-text-fill: #2c2416; -fx-font-size: 18px; -fx-font-weight: bold;");

                    // Fade back in
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
            gameState.loseHeart();
            // Proceed to gameplay: show POISON and allow removal.
            removalMode = true;
            removedArtifact = null;
            addPoisonToInventory(true);
            say("FOOL!Do you think slots grows on trees!\nNO realoc()in this dungeon!\nPoison is now in your inventory. Free index 1 or 3 and place it.", null);
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
            gameState.loseHeart();
            say("insert potion,either the curse will not removed", null);
            hideDialogueThen(1600, null);
            return;
        }

        // Prefer deriving the removed item from the final layout (robust to odd sequences)
        Artifact removedByLayout = determineRemovedByFinalLayout();
        Artifact decision = (removedByLayout != null) ? removedByLayout : removedArtifact;

        int addScore;
        String strategyMsg;

        if (decision == Artifact.SHIELD) {
            // 1) Shield removed => Healing, Key, Sword, Magical Potion, Torch
            addScore = SCORE_SHIELD_REMOVED; // 20
            strategyMsg = "You chose everything over COVER. A calculated risk.     \n" +
                    "   The next chamber's traps will test if your        \n" +
                    "   calculations were correct.";
        } else if (decision == Artifact.KEY) {
            // 2) Key removed => Healing, Magical Potion, Sword, Shield, Torch
            addScore = SCORE_KEY_REMOVED; // 10
            strategyMsg = "You traded ACCESS for POWER. A programmer's folly\n" +
                    "   Ahead lie doors you cannot open, paths forever   \n" +
                    "   sealed. The dungeon remembers your sacrifice.";
        } else if (decision == Artifact.TORCH) {
            // 3) Torch removed => Healing, Key, Sword, Magical Potion, Shield
            addScore = SCORE_TORCH_REMOVED; // 30
            strategyMsg = "You paid with light to keep both key and shield.  \n" +
                    "   A trade of vision for versatility. The mark of    \n" +
                    "   a true strategist in this dungeon of choices.";
        } else if (decision == Artifact.POTION) {
            // 4) Healing removed => Key, Magical Potion, Sword, Shield
            addScore = SCORE_POTION_REMOVED; // 25
            strategyMsg = "You paid with healing to keep both key and shield. But how will you survive for your wrong steps,in the large dungeon?";
        } else {
            // No removal detected (shouldn't happen in normal flow)
            addScore = 0;
            strategyMsg = "Strategy applied.";
        }

        gameState.addScore(addScore);
        removalMode = false;

        // Hide the reused DONE button after completion
        if (doneWrap != null) {
            worldRoot.getChildren().remove(doneWrap);
        }

        say("Level Complete! Strategy score +" + addScore + ".\n" + strategyMsg,
                () -> hideDialogueThen(8000, null));
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

    // ---------- Quiz visual effects (blur + fume) ----------
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
            quizOverlay.setPickOnBounds(true);     // capture clicks
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

            // Optional: If you have a smoke texture, add it here and animate its drift
            // ImageView smoke = core.AssetLoader.imageView("/smoke.png", 1600, 0, true);
            // smoke.setOpacity(0.22);
            // quizOverlay.getChildren().add(smoke);
            // TranslateTransition drift = new TranslateTransition(Duration.seconds(12), smoke);
            // drift.setFromX(-120); drift.setToX(120); drift.setAutoReverse(true);
            // drift.setCycleCount(Animation.INDEFINITE); drift.play();
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
            lbl.setId("instrText"); // <- so we can change it in Part 3
            lbl.setStyle("-fx-text-fill: #2c2416; -fx-font-size: 18px; -fx-font-weight: bold;");
            lbl.setWrapText(true);
            //lbl.setMaxWidth(box.getPrefWidth() -  (44 + 44));
            lbl.setMaxWidth(box.getPrefWidth() - (box.getPadding().getLeft() + box.getPadding().getRight()));
            box.getChildren().add(lbl);
            return box;
        }
    }
}
