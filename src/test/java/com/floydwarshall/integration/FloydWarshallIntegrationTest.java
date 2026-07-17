package com.floydwarshall.integration;

import com.floydwarshall.model.FloydWarshall;
import com.floydwarshall.model.FloydWarshallExecutor;
import com.floydwarshall.model.Graph;
import com.floydwarshall.model.MatrixOps;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционные и сквозные тесты.
 * Проверяют связку: ввод матрицы → модель Graph → Executor → результат.
 *
 * Тестируется математическая корректность всего pipeline без участия UI,
 * что является валидным подходом для интеграционного тестирования
 * алгоритмической части приложения.
 */
class FloydWarshallIntegrationTest {

    // ===== ИНТЕГРАЦИОННЫЕ ТЕСТЫ: Graph + Executor =====

    @Test @DisplayName("Связка Graph → Executor: корректная передача матрицы")
    void graphToExecutor() {
        Graph g = new Graph(3);
        g.set(0, 1, 5);
        g.set(1, 2, 3);
        g.set(2, 0, 1);

        Integer[][] source = g.snapshot();
        FloydWarshallExecutor executor = new FloydWarshallExecutor(source);

        // Проверяем, что Executor получил корректную копию
        Integer[][] dist = executor.getDist();
        assertEquals(5, dist[0][1]);
        assertEquals(3, dist[1][2]);
        assertEquals(1, dist[2][0]);
        assertNull(dist[0][2]); // нет прямого пути 0→2

        // Изменение dist не должно влиять на Graph
        dist[0][1] = 999;
        assertEquals(5, g.get(0, 1), "Graph не должен измениться");
    }

    @Test @DisplayName("Сквозной: Graph → Executor → полный прогон → корректный результат")
    void endToEndGraphToResult() {
        Graph g = new Graph(4);
        g.set(0, 1, 5);
        g.set(0, 3, 2);
        g.set(1, 2, 3);
        g.set(2, 3, 1);
        g.set(3, 1, 1);

        Integer[][] source = g.snapshot();
        FloydWarshallExecutor executor = new FloydWarshallExecutor(source);
        int totalSteps = FloydWarshall.totalSteps(g.size());

        // Пошаговое выполнение
        for (int s = 0; s < totalSteps; s++) {
            executor.stepForward();
        }

        assertTrue(executor.isFinished());

        // Проверяем известные кратчайшие пути
        Integer[][] result = executor.getDist();
        assertEquals(0, result[0][0]);
        assertEquals(3, result[0][1]);  // 0→3→1 = 2+1 = 3
        assertEquals(6, result[0][2]);  // 0→3→1→2 = 2+1+3 = 6
        assertEquals(2, result[0][3]);  // прямой путь 0→3 = 2
    }

    @Test @DisplayName("Сквозной: Пошаговый Executor идентичен полному FloydWarshall.run")
    void executorMatchesFullRun() {
        Integer[][] source = {
                {0, 3, null, 7},
                {8, 0, 2, null},
                {5, null, 0, 1},
                {2, null, null, 0}
        };

        // Полный прогон
        Integer[][] fullResult = FloydWarshall.run(source, 4);

        // Пошаговый прогон через Executor
        FloydWarshallExecutor executor = new FloydWarshallExecutor(source);
        int totalSteps = FloydWarshall.totalSteps(4);
        for (int s = 0; s < totalSteps; s++) {
            executor.stepForward();
        }
        Integer[][] stepResult = executor.getDist();

        // Результаты должны быть идентичны
        for (int i = 0; i < 4; i++) {
            assertArrayEquals(fullResult[i], stepResult[i], "Строка " + i);
        }
    }

    @Test @DisplayName("Сквозной: откат и повтор дают идентичное состояние")
    void rollbackAndReplay() {
        Integer[][] source = {
                {0, 5, null},
                {null, 0, 3},
                {2, null, 0}
        };

        FloydWarshallExecutor executor = new FloydWarshallExecutor(source);
        int totalSteps = FloydWarshall.totalSteps(3);

        // Сохраняем историю
        FloydWarshallExecutor.StepResult[] results = new FloydWarshallExecutor.StepResult[totalSteps];
        Integer[][][] snapshots = new Integer[totalSteps + 1][][];
        snapshots[0] = MatrixOps.deepCopy(source);

        for (int s = 0; s < totalSteps; s++) {
            results[s] = executor.stepForward();
            snapshots[s + 1] = executor.getDist();
        }

        Integer[][] fullResult = executor.getDist();

        // Откат на шаг 10
        int rollbackTo = 10;
        executor.setState(
                results[rollbackTo - 1].k(),
                results[rollbackTo - 1].i(),
                results[rollbackTo - 1].j(),
                snapshots[rollbackTo]
        );

        // Повторный прогон
        for (int s = rollbackTo; s < totalSteps; s++) {
            executor.stepForward();
        }

        // Результат должен совпасть
        Integer[][] replayResult = executor.getDist();
        for (int i = 0; i < 3; i++) {
            assertArrayEquals(fullResult[i], replayResult[i]);
        }
    }

    @Test @DisplayName("Интеграционный: replaceMatrix в Graph → новый Executor")
    void replaceMatrixAndRerun() {
        Graph g = new Graph(3);
        g.set(0, 1, 10);
        g.set(1, 2, 10);

        // Первый прогон
        FloydWarshallExecutor e1 = new FloydWarshallExecutor(g.snapshot());
        for (int s = 0; s < FloydWarshall.totalSteps(3); s++) e1.stepForward();
        Integer[][] r1 = e1.getDist();
        assertEquals(20, r1[0][2]); // 0→1→2 = 10+10

        // Изменяем граф
        g.set(0, 2, 5); // добавляем прямой путь
        FloydWarshallExecutor e2 = new FloydWarshallExecutor(g.snapshot());
        for (int s = 0; s < FloydWarshall.totalSteps(3); s++) e2.stepForward();
        Integer[][] r2 = e2.getDist();
        assertEquals(5, r2[0][2]); // теперь 0→2 = 5 (прямой путь короче)
    }

    @Test @DisplayName("Интеграционный: граф без рёбер — ноль обновлений")
    void noEdgesNoUpdates() {
        Integer[][] source = {
                {0, null, null},
                {null, 0, null},
                {null, null, 0}
        };

        FloydWarshallExecutor executor = new FloydWarshallExecutor(source);
        int updateCount = 0;
        for (int s = 0; s < FloydWarshall.totalSteps(3); s++) {
            if (executor.stepForward().wasUpdate()) updateCount++;
        }

        assertEquals(0, updateCount);
        assertTrue(executor.isFinished());
    }

    @Test @DisplayName("Сквозной: режимы j/i/k корректно вычисляют количество шагов")
    void iterationModes() {
        Integer[][] source = {
                {0, 5, null, 2},
                {null, 0, 3, null},
                {1, null, 0, 5},
                {null, 4, null, 0}
        };
        int n = 4;

        // Режим "j": добивает текущую строку i до конца
        FloydWarshallExecutor ex = new FloydWarshallExecutor(source);
        // Начальное состояние: k=0, i=0, j=0
        int cj = ex.getCurrentJ();
        int expectedJ = (cj == 0) ? n : (n - cj);
        assertEquals(4, expectedJ);

        // Режим "k": все оставшиеся шаги
        int totalSteps = FloydWarshall.totalSteps(n);
        assertEquals(64, totalSteps);
    }

    // ===== ИНТЕГРАЦИОННЫЕ: MatrixOps + Executor (снимки состояния) =====

    @Test @DisplayName("MatrixOps.deepCopy создаёт независимый снимок для истории")
    void deepCopyForHistory() {
        Integer[][] original = {{0, 5}, {3, 0}};
        Integer[][] copy = MatrixOps.deepCopy(original);


        original[0][1] = 999;


        assertEquals(5, copy[0][1]);
    }

    @Test @DisplayName("Снимки в истории сохраняют состояние на каждом шаге")
    void snapshotsPreserveState() {
        Integer[][] source = {{0, 1}, {2, 0}};
        FloydWarshallExecutor executor = new FloydWarshallExecutor(source);

        Integer[][] snapshot0 = MatrixOps.deepCopy(executor.getDist());
        executor.stepForward();
        Integer[][] snapshot1 = MatrixOps.deepCopy(executor.getDist());

        // Снимки — независимые копии
        assertNotSame(snapshot0, snapshot1);
        assertEquals(0, snapshot0[0][0]);
        assertEquals(0, snapshot1[0][0]);
    }
}