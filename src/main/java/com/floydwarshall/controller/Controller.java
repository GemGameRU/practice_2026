package com.floydwarshall.controller;

import com.floydwarshall.logging.Logger;
import com.floydwarshall.model.FloydWarshall;
import com.floydwarshall.model.Graph;
import com.floydwarshall.view.ControlPanel;
import com.floydwarshall.view.GraphCanvas;
import com.floydwarshall.view.MatrixTableView;

public class Controller {

    private enum State {
        WAITING_INPUT, ALGORITHM_FINISHED
    }

    private State state = State.WAITING_INPUT;

    private final Graph inputGraph;
    private final Graph resultGraph; // состояние таблицы 2 / холста 2

    private final GraphCanvas canvas1;
    private final GraphCanvas canvas2;
    private final MatrixTableView table1;
    private final MatrixTableView table2;
    private final ControlPanel controlPanel;
    private final Logger logger;

    // Поле описания шага обновляется через этот интерфейс.
    private StepDescriptionUpdater stepDescriptionUpdater;

    public interface StepDescriptionUpdater {
        void update(String text);
    }

    public Controller(Graph inputGraph,
            GraphCanvas canvas1, GraphCanvas canvas2,
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
    }

    public void setStepDescriptionUpdater(StepDescriptionUpdater u) {
        this.stepDescriptionUpdater = u;
    }

    private void wire() {
        // Панель управления.
        controlPanel.setListener(this::onButton);

        // Редактирование таблицы 1 → перерисовка холста 1, сброс алгоритма.
        table1.setEditListener((i, j, newValue) -> {
            applyCellEdit(i, j, newValue);
        });

        // Выделение на холстах синхронизируется с таблицами и наоборот.
        wireSelectionSync(canvas1, table1, canvas2, table2);
        wireSelectionSync(canvas2, table2, canvas1, table1);
    }

    private void wireSelectionSync(GraphCanvas canvas, MatrixTableView table,
            GraphCanvas otherCanvas, MatrixTableView otherTable) {
        canvas.setSelectionListener((type, a, b) -> {
            otherTable.clearSelectionSync();
            if (type == GraphCanvas.SelectionType.VERTEX) {
                // Подсветка вершины в таблице (полная реализация — в версии 1)
            } else if (type == GraphCanvas.SelectionType.EDGE) {
                // Подсветка ребра в таблице (полная реализация — в версии 1)
            } else {
                table.clearSelectionSync();
            }
        });

        table.setSelectionListener(new MatrixTableView.CellSelectionListener() {
            @Override
            public void onCellSelected(int i, int j) {
                canvas.selectEdge(i, j);
            }

            @Override
            public void onRowHeaderSelected(int i) {
                canvas.selectVertex(i);
            }

            @Override
            public void onColumnHeaderSelected(int j) {
                canvas.selectVertex(j);
            }

            @Override
            public void onSelectionCleared() {
                canvas.clearSelection();
            }
        });
    }

    private void onButton(ControlPanel.ButtonId id) {
        switch (id) {
            case START_PAUSE -> doStartPause();
            case RESET -> doReset();
            case ADD_VERTEX -> doAddVertex();
            case REMOVE_VERTEX -> doRemoveVertex();
            case STEP_FORWARD, STEP_BACK, STEP_N, SPEED, LOAD_FILE, SAVE ->
                logger.log(Logger.Type.INFO, "Кнопка «" + id + "» неактивна в прототипе");
        }
    }

    private void doStartPause() {
        if (state == State.ALGORITHM_FINISHED) {
            logger.log(Logger.Type.INFO, "Алгоритм уже завершён. Нажмите «Сброс» для перезапуска.");
            return;
        }
        logger.log(Logger.Type.STATE, "Алгоритм запущен");

        // Считываем матрицу из таблицы 1.
        Integer[][] source = table1.toMatrix();
        int n = inputGraph.size();
        inputGraph.replaceMatrix(source);

        // Выполняем алгоритм до конца.
        Integer[][] result = FloydWarshall.run(source, n);
        resultGraph.replaceMatrix(result);

        int totalSteps = FloydWarshall.totalSteps(n);

        // Отображаем результат.
        table2.rebuild(resultGraph);
        canvas2.setGraph(resultGraph);
        canvas2.clearAlgorithmHighlight();

        stepDescriptionUpdater.update("Алгоритм завершён");
        logger.log(Logger.Type.STATE, "Алгоритм завершён (всего шагов: " + totalSteps + ")");

        state = State.ALGORITHM_FINISHED;
    }

    private void doReset() {
        Integer[][] source = table1.toMatrix();
        inputGraph.replaceMatrix(source);
        resultGraph.replaceMatrix(source);

        table2.rebuild(resultGraph);
        canvas2.setGraph(resultGraph);
        canvas2.clearAlgorithmHighlight();

        stepDescriptionUpdater.update("Алгоритм не запущен");
        logger.log(Logger.Type.ACTION, "Алгоритм сброшен");

        state = State.WAITING_INPUT;
    }

    private void doAddVertex() {
        try {
            inputGraph.addVertex();
        } catch (IllegalStateException e) {
            logger.log(Logger.Type.ERROR, e.getMessage());
            return;
        }
        int newVertex = inputGraph.size() - 1;
        // Копируем граф в результат.
        resultGraph.replaceMatrix(inputGraph.snapshot());

        rebuildAll();
        canvas1.setGraph(inputGraph);
        canvas2.setGraph(resultGraph);
        canvas2.clearAlgorithmHighlight();

        stepDescriptionUpdater.update("Алгоритм не запущен");
        logger.log(Logger.Type.ACTION, "Добавлена вершина " + newVertex);
        state = State.WAITING_INPUT;
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
        canvas2.clearAlgorithmHighlight();

        stepDescriptionUpdater.update("Алгоритм не запущен");
        logger.log(Logger.Type.ACTION, "Удалена вершина " + toRemove);
        state = State.WAITING_INPUT;
    }

    private void applyCellEdit(int i, int j, String newValue) {
        if (i == j)
            return; // диагональ не редактируется
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
                // Восстанавливаем предыдущее значение в таблице.
                table1.rebuild(inputGraph);
                return;
            }
        }
        inputGraph.set(i, j, v);
        resultGraph.replaceMatrix(inputGraph.snapshot());

        canvas1.setGraph(inputGraph);
        canvas2.setGraph(resultGraph);
        canvas2.clearAlgorithmHighlight();
        table2.rebuild(resultGraph);

        // Любое изменение сбрасывает алгоритм.
        state = State.WAITING_INPUT;
        stepDescriptionUpdater.update("Алгоритм не запущен");
        logger.log(Logger.Type.ACTION, "Ячейка (" + i + ", " + j + ") изменена: " + (v == null ? "inf" : v));
    }

    private void rebuildAll() {
        table1.rebuild(inputGraph);
        table2.rebuild(resultGraph);
    }
}
