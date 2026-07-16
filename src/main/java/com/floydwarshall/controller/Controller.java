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

    private FloydWarshallExecutor executor;
    private int currentStep = 0;
    private int totalSteps = 0;

    private boolean isAutoPlaying = false;
    private Timeline autoPlayTimeline;
    private double currentSpeed = 1.0;
    private File lastLoadedFile = null;

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
        controlPanel.setSpeedListener(this::onSpeedChanged);

        controlPanel.setFixVerticesListener(fixed -> {
            canvas1.setVerticesFixed(fixed);
            canvas2.setVerticesFixed(fixed);
            logger.log(Logger.Type.ACTION, "Фиксация вершин: " + (fixed ? "вкл" : "выкл"));
        });
        table1.setEditListener((i, j, newValue) -> applyCellEdit(i, j, newValue));
        wireSelectionSync(canvas1, table1, canvas2, table2);
        wireSelectionSync(canvas2, table2, canvas1, table1);
    }

    private void wireSelectionSync(SmartGraphView canvas, MatrixTableView table,
            SmartGraphView otherCanvas, MatrixTableView otherTable) {
        canvas.setSelectionListener((type, a, b) -> {
            otherTable.clearSelectionSync();
            if (type == SmartGraphView.SelectionType.VERTEX) {
                table.selectVertex(a);
            } else if (type == SmartGraphView.SelectionType.EDGE) {
                table.selectEdge(a, b);
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
                canvas.selectVertexOutgoing(i);
                table.selectRow(i);
            }

            @Override
            public void onColumnHeaderSelected(int j) {
                canvas.selectVertexIncoming(j);
                table.selectCol(j);
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
            case LOAD_FILE -> doLoadFile();
            case SAVE -> doSaveFile();
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

    // --- Инициализация алгоритма (общая для stepForward / stepN) ---
    private void ensureAlgorithmStarted() {
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
    }

    // --- Один шаг вперёд ---
    private void doStepForward() {
        if (state == State.ALGORITHM_FINISHED)
            return;
        ensureAlgorithmStarted();

        if (executor.isFinished()) {
            state = State.ALGORITHM_FINISHED;
            updateButtonsState();
            stepDescriptionUpdater.update("Алгоритм завершён");
            logger.log(Logger.Type.STATE, "Алгоритм завершён (всего шагов: " + totalSteps + ")");
            return;
        }

        currentStep++;
        FloydWarshallExecutor.StepResult res = executor.stepForward();
        applyStepResult(res);

        logger.log(Logger.Type.STATE, String.format("Шаг %d: k=%d, i=%d, j=%d, D[%d][%d] %s",
                currentStep, res.k(), res.i(), res.j(), res.i(), res.j(),
                res.wasUpdate() ? "обновлено " + res.oldValue() + " → " + res.altValue() : "не обновлено"));

        if (executor.isFinished()) {
            state = State.ALGORITHM_FINISHED;
            logger.log(Logger.Type.STATE, "Алгоритм завершён (всего шагов: " + totalSteps + ")");
        }
        updateButtonsState();
    }

    private void applyStepResult(FloydWarshallExecutor.StepResult res) {
        resultGraph.replaceMatrix(res.dist());
        table2.rebuild(resultGraph);
        table2.setAlgorithmHighlight(res.i(), res.j(), res.k(), res.wasUpdate());
        canvas2.setGraph(resultGraph);
        canvas2.setAlgorithmHighlight(res.i(), res.j(), res.k(), res.wasUpdate());
        updateStepDescription(res);
    }

    private void updateStepDescription(FloydWarshallExecutor.StepResult res) {
        StringBuilder sb = new StringBuilder();
        sb.append("Шаг ").append(currentStep).append(" из ").append(totalSteps).append("\n");
        sb.append("k = ").append(res.k()).append(", i = ").append(res.i())
                .append(", j = ").append(res.j()).append("\n");
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
        String rawInput = controlPanel.getStepNText().toLowerCase();

        if (rawInput.equals("k") || rawInput.equals("i") || rawInput.equals("j")) {
            doStepByIteration(rawInput);
            return;
        }

        int n;
        try {
            n = Integer.parseInt(rawInput);
        } catch (NumberFormatException e) {
            logger.log(Logger.Type.ERROR, "N должно быть числом или буквой k, i, j");
            return;
        }

        if (n <= 0) {
            logger.log(Logger.Type.ERROR, "N должно быть положительным числом");
            return;
        }

        ensureAlgorithmStarted();
        if (state == State.ALGORITHM_FINISHED)
            return;

        logger.log(Logger.Type.STATE, "Шаг N: выполнение " + n + " шаг(ов)");

        FloydWarshallExecutor.StepResult lastRes = null;
        int performed = 0;
        for (int count = 0; count < n; count++) {
            if (executor.isFinished())
                break;
            currentStep++;
            lastRes = executor.stepForward();
            performed++;

            logger.log(Logger.Type.STATE, String.format("Шаг %d: k=%d, i=%d, j=%d, D[%d][%d] %s",
                    currentStep, lastRes.k(), lastRes.i(), lastRes.j(), lastRes.i(), lastRes.j(),
                    lastRes.wasUpdate() ? "обновлено " + lastRes.oldValue() + " → " + lastRes.altValue()
                            : "не обновлено"));
        }

        if (lastRes != null) {
            applyStepResult(lastRes);
        }

        logger.log(Logger.Type.STATE, "Выполнено " + performed + " шаг(ов)");

        if (executor.isFinished()) {
            state = State.ALGORITHM_FINISHED;
            logger.log(Logger.Type.STATE, "Алгоритм завершён (всего шагов: " + totalSteps + ")");
        }
        updateButtonsState();
    }

    private void doStepByIteration(String mode) {
        ensureAlgorithmStarted();
        if (state == State.ALGORITHM_FINISHED) {
            logger.log(Logger.Type.STATE, "Шаг N (режим " + mode + "): 0 шагов (алгоритм завершён)");
            return;
        }

        int n = executor.getN();
        int targetSteps;

        switch (mode) {
            case "j" -> {
                // До начала следующей средней итерации (следующего i)
                int cj = executor.getCurrentJ();
                targetSteps = (cj == 0) ? n : (n - cj);
            }
            case "i" -> {
                // До начала следующей крупной итерации (следующего k)
                int ci = executor.getCurrentI();
                int cj = executor.getCurrentJ();
                targetSteps = (n - ci) * n - cj;
                if (targetSteps <= 0)
                    targetSteps = n * n; // уже на границе — полный цикл k
            }
            case "k" -> {
                // Все оставшиеся шаги
                targetSteps = totalSteps - currentStep;
            }
            default -> targetSteps = 0;
        }

        if (targetSteps <= 0) {
            logger.log(Logger.Type.STATE, "Шаг N (режим " + mode + "): 0 шагов");
            return;
        }

        logger.log(Logger.Type.STATE, "Шаг N (режим " + mode + "): выполнение до " + targetSteps + " шаг(ов)");

        FloydWarshallExecutor.StepResult lastRes = null;
        int performed = 0;
        for (int count = 0; count < targetSteps; count++) {
            if (executor.isFinished())
                break;
            currentStep++;
            lastRes = executor.stepForward();
            performed++;
            logger.log(Logger.Type.STATE, String.format("Шаг %d: k=%d, i=%d, j=%d, D[%d][%d] %s",
                    currentStep, lastRes.k(), lastRes.i(), lastRes.j(), lastRes.i(), lastRes.j(),
                    lastRes.wasUpdate() ? "обновлено " + lastRes.oldValue() + " → " + lastRes.altValue()
                            : "не обновлено"));
        }

        if (lastRes != null) {
            applyStepResult(lastRes);
        }

        logger.log(Logger.Type.STATE, "Выполнено " + performed + " шаг(ов)");

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
                new FileChooser.ExtensionFilter("Graph Files", "*.csv", "*.txt"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        if (lastLoadedFile != null && lastLoadedFile.getParentFile() != null)
            fileChooser.setInitialDirectory(lastLoadedFile.getParentFile());
        javafx.stage.Window window = canvas1.getScene() != null ? canvas1.getScene().getWindow() : null;
        File file = fileChooser.showOpenDialog(window);
        if (file != null) {
            try {
                Integer[][] matrix = loadCsvMatrix(file);
                inputGraph.replaceMatrix(matrix);
                resultGraph.replaceMatrix(matrix);
                rebuildAll();
                canvas1.setGraph(inputGraph);
                canvas2.setGraph(resultGraph);
                resetAlgorithmState();
                lastLoadedFile = file;
                logger.log(Logger.Type.ACTION,
                        "Загружен файл: " + file.getName() + " (" + inputGraph.size() + " вершин)");
            } catch (IllegalArgumentException e) {
                logger.log(Logger.Type.ERROR, "Ошибка валидации файла: " + e.getMessage());
            } catch (Exception e) {
                logger.log(Logger.Type.ERROR, "Не удалось загрузить файл: " + e.getMessage());
            }
        }
    }

    private Integer[][] loadCsvMatrix(File file) throws Exception {
        java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(new java.io.FileInputStream(file),
                        java.nio.charset.StandardCharsets.UTF_8));
        java.util.List<Integer[]> rows = new java.util.ArrayList<>();
        int lineNumber = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            line = line.trim();
            if (line.isEmpty())
                continue;
            String[] parts = line.split(",");
            Integer[] row = new Integer[parts.length];
            for (int j = 0; j < parts.length; j++) {
                String val = parts[j].trim();
                if (val.isEmpty() || val.equalsIgnoreCase("inf")) {
                    row[j] = null;
                } else {
                    try {
                        int weight = Integer.parseInt(val);
                        if (weight < 0)
                            throw new IllegalArgumentException("Вес ребра не может быть отрицательным (строка "
                                    + lineNumber + ", столбец " + (j + 1) + ")");
                        row[j] = weight;
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Некорректное значение ячейки (строка " + lineNumber
                                + ", столбец " + (j + 1) + "): " + val);
                    }
                }
            }
            rows.add(row);
        }
        reader.close();
        int n = rows.size();
        if (n < 2)
            throw new IllegalArgumentException("Матрица должна содержать минимум 2 вершины");
        if (n > 20)
            throw new IllegalArgumentException("Матрица не может содержать больше 20 вершин");
        for (int i = 0; i < n; i++) {
            if (rows.get(i).length != n)
                throw new IllegalArgumentException("Матрица должна быть квадратной (ошибка в строке " + (i + 1) + ")");
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    if (rows.get(i)[j] != null && rows.get(i)[j] != 0)
                        logger.log(Logger.Type.INFO,
                                "Диагональный элемент [" + i + "][" + j + "] автоматически изменён на 0");
                    rows.get(i)[j] = 0;
                } else {
                    if (rows.get(i)[j] != null && rows.get(i)[j] <= 0)
                        throw new IllegalArgumentException(
                                "Вес ребра вне диагонали должен быть строго положительным (строка " + (i + 1)
                                        + ", столбец " + (j + 1) + ")");
                }
            }
        }
        return rows.toArray(new Integer[0][]);
    }

    private void doSaveFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Сохранение графа в файл");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Graph Files", "*.csv", "*.txt"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        if (lastLoadedFile != null) {
            File parentDir = lastLoadedFile.getParentFile();
            if (parentDir != null && parentDir.exists() && parentDir.isDirectory())
                fileChooser.setInitialDirectory(parentDir);
            String originalName = lastLoadedFile.getName();
            int dotIndex = originalName.lastIndexOf('.');
            String baseName = (dotIndex > 0) ? originalName.substring(0, dotIndex) : originalName;
            fileChooser.setInitialFileName(baseName);
        }
        javafx.stage.Window window = canvas1.getScene() != null ? canvas1.getScene().getWindow() : null;
        File file = fileChooser.showSaveDialog(window);
        if (file != null) {
            try {
                String filePath = file.getAbsolutePath();
                String baseName;
                String ext = "";
                int dotIndex = filePath.lastIndexOf('.');
                if (dotIndex > 0 && dotIndex < filePath.length() - 1) {
                    baseName = filePath.substring(0, dotIndex);
                    ext = filePath.substring(dotIndex).toLowerCase();
                } else {
                    baseName = filePath;
                }
                String matrixPath, logPath;
                if (".csv".equals(ext)) {
                    matrixPath = baseName + ".csv";
                    logPath = baseName + ".txt";
                } else if (".txt".equals(ext)) {
                    matrixPath = baseName + ".txt";
                    logPath = baseName + "_log.txt";
                } else {
                    matrixPath = baseName + ".csv";
                    logPath = baseName + ".txt";
                }

                Integer[][] matrixToSave = (state == State.WAITING_INPUT) ? table1.toMatrix() : resultGraph.snapshot();
                saveMatrix(new File(matrixPath), matrixToSave);
                saveLogFile(new File(logPath));
                logger.log(Logger.Type.ACTION,
                        "Сохранено: " + new File(matrixPath).getName() + " и " + new File(logPath).getName());
            } catch (Exception e) {
                logger.log(Logger.Type.ERROR, "Не удалось сохранить файл: " + e.getMessage());
            }
        }
    }

    private void saveMatrix(File file, Integer[][] matrix) throws Exception {
        java.io.PrintWriter writer = new java.io.PrintWriter(
                new java.io.OutputStreamWriter(new java.io.FileOutputStream(file),
                        java.nio.charset.StandardCharsets.UTF_8));
        for (Integer[] row : matrix) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < row.length; j++) {
                if (j > 0)
                    sb.append(",");
                sb.append(row[j] == null ? "inf" : String.valueOf(row[j]));
            }
            writer.println(sb.toString());
        }
        writer.close();
    }

    private void saveLogFile(File file) throws Exception {
        java.io.PrintWriter writer = new java.io.PrintWriter(
                new java.io.OutputStreamWriter(new java.io.FileOutputStream(file),
                        java.nio.charset.StandardCharsets.UTF_8));
        for (String entry : logger.getEntries())
            writer.println(entry);
        writer.close();
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
        controlPanel.setStartPauseLabel(isAutoPlaying ? "Пауза" : "Пуск");
    }

    private void rebuildAll() {
        table1.rebuild(inputGraph);
        table2.rebuild(resultGraph);
    }
}