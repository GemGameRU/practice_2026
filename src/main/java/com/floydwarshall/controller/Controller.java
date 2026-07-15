package com.floydwarshall.controller;

import com.floydwarshall.logging.Logger;
import com.floydwarshall.model.FloydWarshall;
import com.floydwarshall.model.FloydWarshallExecutor;
import com.floydwarshall.model.Graph;
import com.floydwarshall.view.ControlPanel;
import com.floydwarshall.view.SmartGraphView;
import com.floydwarshall.view.MatrixTableView;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import javafx.stage.FileChooser;
import java.io.File;

public class Controller {
    private enum State {
        WAITING_INPUT, ALGORITHM_PAUSED, ALGORITHM_FINISHED
    }

    private State state = State.WAITING_INPUT;
    private final Graph inputGraph;
    private final Graph resultGraph;
    private final SmartGraphView canvas1;
    private final SmartGraphView canvas2;
    private final MatrixTableView table1;
    private final MatrixTableView table2;
    private final ControlPanel controlPanel;
    private final Logger logger;

    private StepDescriptionUpdater stepDescriptionUpdater;

    // Состояние алгоритма
    private FloydWarshallExecutor executor;
    private int currentStep = 0;
    private int totalSteps = 0;

    // Автоматическое выполнение
    private boolean isAutoPlaying = false;
    private Timeline autoPlayTimeline;
    private double currentSpeed = 1.0;

    public interface StepDescriptionUpdater {
        void update(String text);
    }

    public Controller(Graph inputGraph,
            SmartGraphView canvas1, SmartGraphView canvas2,
            MatrixTableView table1, MatrixTableView table2,
            ControlPanel controlPanel,
            Logger logger) {
        this.inputGraph = inputGraph;
        this.resultGraph = new Graph(inputGraph);
        this.canvas1 = canvas1;
        this.canvas2 = canvas2;
        this.table1 = table1;
        this.table2 = table2;
        this.controlPanel = controlPanel;
        this.logger = logger;

        wire();
        updateButtonsState();
    }

    public void setStepDescriptionUpdater(StepDescriptionUpdater u) {
        this.stepDescriptionUpdater = u;
    }

    public void stepForward() {
        doStepForward();
    }

    private void wire() {
        controlPanel.setListener(this::onButton);
        controlPanel.setSpeedListener(this::onSpeedChanged); // Подключаем слушатель скорости

        table1.setEditListener((i, j, newValue) -> applyCellEdit(i, j, newValue));

        wireSelectionSync(canvas1, table1, canvas2, table2);
        wireSelectionSync(canvas2, table2, canvas1, table1);
    }

    private void wireSelectionSync(SmartGraphView canvas, MatrixTableView table,
            SmartGraphView otherCanvas, MatrixTableView otherTable) {
        canvas.setSelectionListener((type, a, b) -> {
            otherTable.clearSelectionSync();
            if (type == SmartGraphView.SelectionType.VERTEX) {
                table.selectVertex(a); // Выделяем строку и столбец
            } else if (type == SmartGraphView.SelectionType.EDGE) {
                table.selectEdge(a, b); // Выделяем ячейку
            } else {
                table.clearSelectionSync();
            }
        });

        table.setSelectionListener(new MatrixTableView.CellSelectionListener() {
            @Override
            public void onCellSelected(int i, int j) {
                canvas.selectEdge(i, j);
                table.selectEdge(i, j);
            }

            @Override
            public void onRowHeaderSelected(int i) {
                canvas.selectVertex(i);
                table.selectVertex(i);
            }

            @Override
            public void onColumnHeaderSelected(int j) {
                canvas.selectVertex(j);
                table.selectVertex(j);
            }

            @Override
            public void onSelectionCleared() {
                table.clearSelectionSync();
                canvas.clearSelection();
            }
        });
    }

    private void onButton(ControlPanel.ButtonId id) {
        switch (id) {
            case STEP_FORWARD -> doStepForward();
            case STEP_N -> doStepN();
            case START_PAUSE -> doStartPause();
            case RESET -> doReset();
            case ADD_VERTEX -> doAddVertex();
            case REMOVE_VERTEX -> doRemoveVertex();
            case LOAD_FILE -> doLoadFile(); // <--- NEW
            case SAVE -> doSaveFile(); // <--- NEW
            case STEP_BACK -> logger.log(Logger.Type.INFO, "Кнопка «Шаг назад» будет доступна в Версии 2");
            case SPEED -> {
            }
        }
    }

    private void onSpeedChanged(String speedStr) {
        try {
            String val = speedStr.replace("x", "");
            currentSpeed = Double.parseDouble(val);
            logger.log(Logger.Type.ACTION, "Скорость: " + currentSpeed + "x");
            if (isAutoPlaying && autoPlayTimeline != null) {
                autoPlayTimeline.stop();
                startAutoPlayTimeline();
            }
        } catch (Exception e) {
            logger.log(Logger.Type.ERROR, "Некорректное значение скорости: " + speedStr);
        }
    }

    private void doStepForward() {
        if (state == State.ALGORITHM_FINISHED)
            return;

        if (state == State.WAITING_INPUT) {
            Integer[][] source = table1.toMatrix();
            inputGraph.replaceMatrix(source);
            resultGraph.replaceMatrix(source);
            executor = new FloydWarshallExecutor(source);
            currentStep = 0;
            totalSteps = FloydWarshall.totalSteps(inputGraph.size());
            state = State.ALGORITHM_PAUSED;
            logger.log(Logger.Type.STATE, "Алгоритм запущен");
        }

        if (executor.isFinished()) {
            state = State.ALGORITHM_FINISHED;
            updateButtonsState();
            stepDescriptionUpdater.update("Алгоритм завершён");
            logger.log(Logger.Type.STATE, "Алгоритм завершён (всего шагов: " + totalSteps + ")");
            return;
        }

        currentStep++;
        FloydWarshallExecutor.StepResult res = executor.stepForward();
        resultGraph.replaceMatrix(res.dist());

        table2.rebuild(resultGraph);
        table2.setAlgorithmHighlight(res.i(), res.j(), res.k());

        canvas2.setGraph(resultGraph);
        canvas2.setAlgorithmHighlight(res.i(), res.j(), res.k());

        updateStepDescription(res);
        logger.log(Logger.Type.STATE, String.format("Шаг %d: k=%d, i=%d, j=%d, D[%d][%d] %s",
                currentStep, res.k(), res.i(), res.j(), res.i(), res.j(),
                res.wasUpdate() ? "обновлено " + res.oldValue() + " → " + res.altValue() : "не обновлено"));

        if (executor.isFinished()) {
            state = State.ALGORITHM_FINISHED;
            logger.log(Logger.Type.STATE, "Алгоритм завершён (всего шагов: " + totalSteps + ")");
        }
        updateButtonsState();
    }

    private void updateStepDescription(FloydWarshallExecutor.StepResult res) {
        StringBuilder sb = new StringBuilder();
        sb.append("Шаг ").append(currentStep).append(" из ").append(totalSteps).append("\n");
        sb.append("k = ").append(res.k()).append(", i = ").append(res.i()).append(", j = ").append(res.j())
                .append("\n");

        String oldStr = (res.oldValue() == null) ? "inf" : String.valueOf(res.oldValue());
        if (res.altValue() != null) {
            String dikStr = (res.dik() == null) ? "inf" : String.valueOf(res.dik());
            String dkjStr = (res.dkj() == null) ? "inf" : String.valueOf(res.dkj());
            sb.append("Сравниваем: D[").append(res.i()).append("][").append(res.j()).append("] = ").append(oldStr)
                    .append("  и  D[").append(res.i()).append("][").append(res.k()).append("] + D[").append(res.k())
                    .append("][").append(res.j()).append("] = ")
                    .append(dikStr).append(" + ").append(dkjStr).append(" = ").append(res.altValue()).append("\n");

            if (res.wasUpdate()) {
                sb.append("Результат: ").append(res.altValue()).append(" < ").append(oldStr).append(" — обновлено\n");
                sb.append("Описание: путь из вершины ").append(res.i()).append(" в вершину ").append(res.j())
                        .append(" через вершину ").append(res.k()).append(" короче текущего; значение D[")
                        .append(res.i()).append("][").append(res.j()).append("] заменено на ").append(res.altValue())
                        .append(".");
            } else {
                sb.append("Результат: ").append(res.altValue()).append(" >= ").append(oldStr)
                        .append(" — не обновлено\n");
                sb.append("Описание: путь через вершину ").append(res.k()).append(" не короче текущего.");
            }
        } else {
            sb.append("Сравниваем: D[").append(res.i()).append("][").append(res.j()).append("] = ").append(oldStr)
                    .append("\n");
            sb.append("Результат: путь через k=").append(res.k()).append(" невозможен\n");
            sb.append("Описание: ").append(res.description());
        }
        stepDescriptionUpdater.update(sb.toString());
    }

    private void doStepN() {
        int n = controlPanel.getStepN();
        if (n <= 0) {
            logger.log(Logger.Type.ERROR, "N должно быть положительным числом");
            return;
        }

        if (state == State.WAITING_INPUT) {
            Integer[][] source = table1.toMatrix();
            inputGraph.replaceMatrix(source);
            resultGraph.replaceMatrix(source);
            executor = new FloydWarshallExecutor(source);
            currentStep = 0;
            totalSteps = FloydWarshall.totalSteps(inputGraph.size());
            state = State.ALGORITHM_PAUSED;
            logger.log(Logger.Type.STATE, "Алгоритм запущен");
        }

        if (state == State.ALGORITHM_FINISHED)
            return;

        FloydWarshallExecutor.StepResult lastRes = null;
        int performed = 0;
        for (int count = 0; count < n; count++) {
            if (executor.isFinished())
                break;
            currentStep++;
            lastRes = executor.stepForward();
            performed++;
        }

        if (lastRes == null)
            return;

        resultGraph.replaceMatrix(lastRes.dist());
        table2.rebuild(resultGraph);
        table2.setAlgorithmHighlight(lastRes.i(), lastRes.j(), lastRes.k());
        canvas2.setGraph(resultGraph);
        canvas2.setAlgorithmHighlight(lastRes.i(), lastRes.j(), lastRes.k());

        updateStepDescription(lastRes);
        logger.log(Logger.Type.STATE, String.format(
                "Шаг N: выполнено %d шаг(ов), текущий шаг %d из %d (k=%d, i=%d, j=%d, D[%d][%d] %s)",
                performed, currentStep, totalSteps, lastRes.k(), lastRes.i(), lastRes.j(),
                lastRes.i(), lastRes.j(),
                lastRes.wasUpdate() ? "обновлено " + lastRes.oldValue() + " → " + lastRes.altValue() : "не обновлено"));

        if (executor.isFinished()) {
            state = State.ALGORITHM_FINISHED;
            logger.log(Logger.Type.STATE, "Алгоритм завершён (всего шагов: " + totalSteps + ")");
        }
        updateButtonsState();
    }

    private void doStartPause() {
        if (state == State.ALGORITHM_FINISHED)
            return;

        if (isAutoPlaying) {
            if (autoPlayTimeline != null)
                autoPlayTimeline.pause();
            isAutoPlaying = false;
            controlPanel.setStartPauseLabel("Пуск");
            logger.log(Logger.Type.STATE, "Пауза на шаге " + currentStep);
        } else {
            if (state == State.WAITING_INPUT)
                doStepForward();
            if (state == State.ALGORITHM_FINISHED)
                return;

            isAutoPlaying = true;
            controlPanel.setStartPauseLabel("Пауза");
            logger.log(Logger.Type.STATE, "Автоматическое выполнение возобновлено");
            startAutoPlayTimeline();
        }
    }

    private void startAutoPlayTimeline() {
        double duration = 1000.0 / currentSpeed;
        autoPlayTimeline = new Timeline(new KeyFrame(Duration.millis(duration), e -> {
            if (state == State.ALGORITHM_FINISHED || executor.isFinished()) {
                autoPlayTimeline.stop();
                isAutoPlaying = false;
                state = State.ALGORITHM_FINISHED;
                controlPanel.setStartPauseLabel("Пуск");
                updateButtonsState();
                return;
            }
            doStepForward();
        }));
        autoPlayTimeline.setCycleCount(Timeline.INDEFINITE);
        autoPlayTimeline.play();
    }

    private void doReset() {
        if (autoPlayTimeline != null)
            autoPlayTimeline.stop();
        isAutoPlaying = false;

        Integer[][] source = table1.toMatrix();
        inputGraph.replaceMatrix(source);
        resultGraph.replaceMatrix(source);

        table2.rebuild(resultGraph);
        canvas2.setGraph(resultGraph);
        canvas2.clearAlgorithmHighlight();
        table2.clearAlgorithmHighlight();

        stepDescriptionUpdater.update("Алгоритм не запущен");
        logger.log(Logger.Type.ACTION, "Алгоритм сброшен");

        state = State.WAITING_INPUT;
        executor = null;
        currentStep = 0;
        updateButtonsState();
    }

    private void resetAlgorithmState() {
        if (autoPlayTimeline != null)
            autoPlayTimeline.stop();
        isAutoPlaying = false;
        state = State.WAITING_INPUT;
        executor = null;
        currentStep = 0;

        canvas2.clearAlgorithmHighlight();
        table2.clearAlgorithmHighlight();
        stepDescriptionUpdater.update("Алгоритм не запущен");
        updateButtonsState();
    }

    private void doAddVertex() {
        try {
            inputGraph.addVertex();
        } catch (IllegalStateException e) {
            logger.log(Logger.Type.ERROR, e.getMessage());
            return;
        }
        int newVertex = inputGraph.size() - 1;
        resultGraph.replaceMatrix(inputGraph.snapshot());
        rebuildAll();
        canvas1.setGraph(inputGraph);
        canvas2.setGraph(resultGraph);
        logger.log(Logger.Type.ACTION, "Добавлена вершина " + newVertex);
        resetAlgorithmState();
    }

    private void doRemoveVertex() {
        int selected = canvas1.getSelectedVertex();
        int toRemove = (selected >= 0) ? selected : inputGraph.size() - 1;
        try {
            inputGraph.removeVertex(toRemove);
        } catch (IllegalStateException e) {
            logger.log(Logger.Type.ERROR, e.getMessage());
            return;
        }
        resultGraph.replaceMatrix(inputGraph.snapshot());
        rebuildAll();
        canvas1.setGraph(inputGraph);
        canvas2.setGraph(resultGraph);
        logger.log(Logger.Type.ACTION, "Удалена вершина " + toRemove);
        resetAlgorithmState();
    }

    private void applyCellEdit(int i, int j, String newValue) {
        if (i == j)
            return;

        Integer v;
        if (newValue == null || newValue.trim().isEmpty() || newValue.trim().equalsIgnoreCase("inf")) {
            v = null;
        } else {
            try {
                v = Integer.parseInt(newValue.trim());
                if (v <= 0) {
                    logger.log(Logger.Type.ERROR, "Вес ребра должен быть положительным (ячейка " + i + ", " + j + ")");
                    table1.rebuild(inputGraph);
                    return;
                }
            } catch (NumberFormatException e) {
                logger.log(Logger.Type.ERROR, "Некорректное значение ячейки (" + i + ", " + j + "): " + newValue);
                table1.rebuild(inputGraph);
                return;
            }
        }

        inputGraph.set(i, j, v);
        resultGraph.replaceMatrix(inputGraph.snapshot());

        canvas1.setGraph(inputGraph);
        canvas2.setGraph(resultGraph);

        table1.rebuild(inputGraph);
        table2.rebuild(resultGraph);

        logger.log(Logger.Type.ACTION, "Ячейка (" + i + ", " + j + ") изменена: " + (v == null ? "inf" : v));
        resetAlgorithmState();
    }

    private void doLoadFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Загрузка графа из файла");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Graph Files", "*.csv"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));

        // Get the main window to attach the dialog to it
        javafx.stage.Window window = canvas1.getScene() != null ? canvas1.getScene().getWindow() : null;
        File file = fileChooser.showOpenDialog(window);

        if (file != null) {
            logger.log(Logger.Type.ACTION, "Выбран файл для загрузки: " + file.getAbsolutePath());

            // TODO: Call external file parsing and graph loading method here
            // Execution halts here as this is a fake implementation stub.

            logger.log(Logger.Type.INFO, "Загрузка из файла не реализована (заглушка).");
        }
    }

    private void doSaveFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Сохранение графа в файл");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Graph Files", "*.csv"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));

        // Get the main window to attach the dialog to it
        javafx.stage.Window window = canvas1.getScene() != null ? canvas1.getScene().getWindow() : null;
        File file = fileChooser.showSaveDialog(window);

        if (file != null) {
            String csvPath = file.getAbsolutePath();
            String basePath = csvPath.toLowerCase().endsWith(".csv")
                    ? csvPath.substring(0, csvPath.length() - 4)
                    : csvPath;
            File logFile = new File(basePath + ".txt");

            logger.log(Logger.Type.ACTION, "Выбран файл для сохранения матрицы: " + file.getAbsolutePath());
            logger.log(Logger.Type.ACTION, "Файл для сохранения логов: " + logFile.getAbsolutePath());

            // TODO: Call external file writing and graph saving method here
            // Execution halts here as this is a fake implementation stub.

            logger.log(Logger.Type.INFO, "Сохранение в файл не реализовано (заглушка).");
        }
    }

    private void updateButtonsState() {
        boolean isFinished = (state == State.ALGORITHM_FINISHED);
        boolean isRunning = (state != State.WAITING_INPUT);

        controlPanel.setEnabled(ControlPanel.ButtonId.STEP_FORWARD, !isFinished);
        controlPanel.setEnabled(ControlPanel.ButtonId.STEP_N, !isFinished);
        controlPanel.setEnabled(ControlPanel.ButtonId.START_PAUSE, !isFinished);
        controlPanel.setEnabled(ControlPanel.ButtonId.RESET, true);
        controlPanel.setEnabled(ControlPanel.ButtonId.ADD_VERTEX, !isRunning);
        controlPanel.setEnabled(ControlPanel.ButtonId.REMOVE_VERTEX, !isRunning);
        controlPanel.setEnabled(ControlPanel.ButtonId.LOAD_FILE, !isRunning);
        controlPanel.setEnabled(ControlPanel.ButtonId.SAVE, true);
        controlPanel.setEnabled(ControlPanel.ButtonId.SPEED, !isFinished);
        controlPanel.setEnabled(ControlPanel.ButtonId.STEP_BACK, false);

        table1.setEditingLocked(isRunning);

        if (isAutoPlaying) {
            controlPanel.setStartPauseLabel("Пауза");
        } else {
            controlPanel.setStartPauseLabel("Пуск");
        }
    }

    private void rebuildAll() {
        table1.rebuild(inputGraph);
        table2.rebuild(resultGraph);
    }
}