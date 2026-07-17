package com.floydwarshall.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public class ControlPanel {
    public enum ButtonId {
        STEP_BACK, START_PAUSE, STEP_FORWARD, STEP_N, ADD_VERTEX,
        RESET, LOAD_FILE, SAVE, SPEED, REMOVE_VERTEX
    }

    public interface ButtonListener {
        void onButton(ButtonId id);
    }

    private final VBox root;
    private final Map<ButtonId, Button> buttons = new LinkedHashMap<>();
    private ButtonListener listener;
    private final TextField stepNField;
    private final ComboBox<String> speedCombo;
    private Consumer<String> speedListener;

    private final CheckBox fixVerticesCheck;
    private Consumer<Boolean> fixVerticesListener;

    public ControlPanel() {
        Button stepBack = makeButton("Шаг назад");
        Button startPause = makeButton("Пуск / Пауза");
        Button stepForward = makeButton("Шаг вперёд");
        Button stepN = makeButton("Шаг N");
        Button addVertex = makeButton("Добавить вершину");

        stepNField = new TextField("5");
        stepNField.setPrefWidth(50);
        stepNField.setPromptText("N");

        Button reset = makeButton("Сброс");
        Button loadFile = makeButton("Ввод из файла");
        Button save = makeButton("Сохранение");

        speedCombo = new ComboBox<>();
        speedCombo.getItems().addAll("0.5x", "1x", "2x", "4x");
        speedCombo.setValue("1x");
        speedCombo.setPrefWidth(80);
        speedCombo.setMaxHeight(Double.MAX_VALUE);
        speedCombo.setOnAction(e -> {
            if (speedListener != null) {
                speedListener.accept(speedCombo.getValue());
            }
        });

        Button removeVertex = makeButton("Удалить вершину");

        fixVerticesCheck = new CheckBox("Фиксировать вершины");
        fixVerticesCheck.setStyle("-fx-font-size: 11px;");
        fixVerticesCheck.setMaxHeight(Double.MAX_VALUE);
        fixVerticesCheck.setOnAction(e -> {
            if (fixVerticesListener != null) {
                fixVerticesListener.accept(fixVerticesCheck.isSelected());
            }
        });

        buttons.put(ButtonId.STEP_BACK, stepBack);
        buttons.put(ButtonId.START_PAUSE, startPause);
        buttons.put(ButtonId.STEP_FORWARD, stepForward);
        buttons.put(ButtonId.STEP_N, stepN);
        buttons.put(ButtonId.ADD_VERTEX, addVertex);
        buttons.put(ButtonId.RESET, reset);
        buttons.put(ButtonId.LOAD_FILE, loadFile);
        buttons.put(ButtonId.SAVE, save);
        buttons.put(ButtonId.REMOVE_VERTEX, removeVertex);

        buttons.forEach((id, btn) -> btn.setOnAction(e -> {
            if (listener != null)
                listener.onButton(id);
        }));

        GridPane row1 = new GridPane();
        row1.setHgap(8);
        row1.setVgap(4);
        row1.setPadding(new Insets(4, 4, 2, 4));
        row1.add(stepBack, 0, 0);
        row1.add(startPause, 1, 0);
        row1.add(stepForward, 2, 0);
        HBox stepNBox = new HBox(4, stepNField, stepN);
        stepNBox.setAlignment(Pos.CENTER_LEFT);
        row1.add(stepNBox, 3, 0);
        row1.add(addVertex, 4, 0);
        for (int c = 0; c < 5; c++) {
            GridPane.setHgrow(row1.getChildren().get(c), Priority.ALWAYS);
            if (row1.getChildren().get(c) instanceof Button b)
                b.setMaxWidth(Double.MAX_VALUE);
        }

        GridPane row2 = new GridPane();
        row2.setHgap(8);
        row2.setVgap(4);
        row2.setPadding(new Insets(2, 4, 4, 4));
        row2.add(reset, 0, 0);
        row2.add(loadFile, 1, 0);
        row2.add(save, 2, 0);
        row2.add(speedCombo, 3, 0);
        row2.add(removeVertex, 4, 0);
        for (int c = 0; c < 5; c++) {
            GridPane.setHgrow(row2.getChildren().get(c), Priority.ALWAYS);
            if (row2.getChildren().get(c) instanceof Button b)
                b.setMaxWidth(Double.MAX_VALUE);
        }

        HBox row3 = new HBox(8, fixVerticesCheck);
        row3.setPadding(new Insets(2, 4, 4, 4));
        row3.setAlignment(Pos.CENTER_LEFT);

        root = new VBox(4, row1, row2, row3);
        root.setPadding(new Insets(4));
        root.setStyle("-fx-border-color: #cfd8dc; -fx-border-width: 1; -fx-background-color: #ffffff;");
    }

    private Button makeButton(String text) {
        Button b = new Button(text);
        b.setWrapText(true);
        b.setMaxHeight(Double.MAX_VALUE);
        b.setMinHeight(40);
        b.setStyle("-fx-font-size: 11px;");
        return b;
    }

    public VBox getRoot() {
        return root;
    }

    public void setListener(ButtonListener l) {
        this.listener = l;
    }

    public void setSpeedListener(Consumer<String> listener) {
        this.speedListener = listener;
    }

    public void setFixVerticesListener(Consumer<Boolean> listener) {
        this.fixVerticesListener = listener;
    }

    public void setEnabled(ButtonId id, boolean enabled) {
        if (id == ButtonId.SPEED) {
            speedCombo.setDisable(!enabled);
            return;
        }
        Button b = buttons.get(id);
        if (b != null)
            b.setDisable(!enabled);
    }

    public void setStartPauseLabel(String text) {
        buttons.get(ButtonId.START_PAUSE).setText(text);
    }

    public int getStepN() {
        try {
            return Integer.parseInt(stepNField.getText().trim());
        } catch (NumberFormatException e) {
            return 5;
        }
    }

    public String getStepNText() {
        return stepNField.getText().trim();
    }
}