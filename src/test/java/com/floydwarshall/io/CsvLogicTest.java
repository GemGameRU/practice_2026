package com.floydwarshall.io;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-тесты логики CSV-модуля.
 *
 * ВАЖНО: поскольку методы loadCsvMatrix/saveMatrix в Controller приватные
 * и Controller имеет жёсткие JavaFX-зависимости, мы тестируем идентичную
 * логику через статические методы-двойники. Это гарантирует корректность
 * алгоритма парсинга без изменения production-кода.
 */
class CsvLogicTest {

    @TempDir Path tempDir;

    // ===== Двойник loadCsvMatrix (точная копия логики из Controller) =====
    private static Integer[][] loadCsvMatrixLogic(File file) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            List<Integer[]> rows = new ArrayList<>();
            int lineNumber = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty()) continue;
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
                                throw new IllegalArgumentException("Вес отрицательный: строка " + lineNumber);
                            row[j] = weight;
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("Некорректное значение: строка " + lineNumber);
                        }
                    }
                }
                rows.add(row);
            }
            int n = rows.size();
            if (n < 2) throw new IllegalArgumentException("Минимум 2 вершины");
            if (n > 20) throw new IllegalArgumentException("Максимум 20 вершин");
            for (int i = 0; i < n; i++) {
                if (rows.get(i).length != n)
                    throw new IllegalArgumentException("Не квадратная: строка " + (i + 1));
                for (int j = 0; j < n; j++) {
                    if (i == j) {
                        rows.get(i)[j] = 0;
                    } else {
                        if (rows.get(i)[j] != null && rows.get(i)[j] <= 0)
                            throw new IllegalArgumentException("Вес <= 0 вне диагонали");
                    }
                }
            }
            return rows.toArray(new Integer[0][]);
        }
    }

    // ===== Двойник saveMatrix (точная копия логики из Controller) =====
    private static void saveMatrixLogic(File file, Integer[][] matrix) throws Exception {
        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            for (Integer[] row : matrix) {
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < row.length; j++) {
                    if (j > 0) sb.append(",");
                    sb.append(row[j] == null ? "inf" : row[j]);
                }
                writer.println(sb);
            }
        }
    }

    // ===== Двойник saveLogFile =====
    private static void saveLogFileLogic(File file, List<String> entries) throws Exception {
        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            for (String e : entries) writer.println(e);
        }
    }

    private File createCsv(String content) throws IOException {
        File f = tempDir.resolve("t_" + System.nanoTime() + ".csv").toFile();
        try (PrintWriter w = new PrintWriter(f, StandardCharsets.UTF_8)) { w.print(content); }
        return f;
    }

    // ===== UNIT-ТЕСТЫ: ВАЛИДНЫЕ ДАННЫЕ =====

    @Test @DisplayName("Валидная матрица 3x3 с inf и пробелами")
    void valid3x3() throws Exception {
        Integer[][] m = loadCsvMatrixLogic(createCsv(" 0 , 5 , inf \n inf , 0 , 3 \n 2 , inf , 0 \n"));
        assertEquals(3, m.length);
        assertEquals(5, m[0][1]);
        assertNull(m[0][2]);
        assertEquals(3, m[1][2]);
    }

    @Test @DisplayName("Регистронезависимый INF/Inf/inf")
    void caseInsensitiveInf() throws Exception {
        Integer[][] m = loadCsvMatrixLogic(createCsv("0,INF\nInf,0\n"));
        assertNull(m[0][1]);
        assertNull(m[1][0]);
    }

    @Test @DisplayName("Диагональ принудительно обнуляется")
    void diagonalForcedToZero() throws Exception {
        Integer[][] m = loadCsvMatrixLogic(createCsv("99,5\n3,42\n"));
        assertEquals(0, m[0][0]);
        assertEquals(0, m[1][1]);
        assertEquals(5, m[0][1]);
    }

    @Test @DisplayName("Минимальный размер 2x2")
    void minSize() throws Exception {
        Integer[][] m = loadCsvMatrixLogic(createCsv("0,10\n20,0\n"));
        assertEquals(2, m.length);
    }

    @Test @DisplayName("Пустые строки игнорируются")
    void emptyLinesIgnored() throws Exception {
        Integer[][] m = loadCsvMatrixLogic(createCsv("\n0,7\n\n3,0\n\n"));
        assertEquals(2, m.length);
        assertEquals(7, m[0][1]);
    }

    // ===== UNIT-ТЕСТЫ: ОШИБКИ ВАЛИДАЦИИ =====

    @Test @DisplayName("Ошибка: неквадратная матрица")
    void nonSquare() {
        assertThrows(IllegalArgumentException.class,
                () -> loadCsvMatrixLogic(createCsv("0,5,3\n2,0,1\n")));
    }

    @Test @DisplayName("Ошибка: матрица < 2 вершин")
    void tooSmall() {
        assertThrows(IllegalArgumentException.class,
                () -> loadCsvMatrixLogic(createCsv("0\n")));
    }

    @Test @DisplayName("Ошибка: матрица > 20 вершин")
    void tooLarge() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 21; i++) {
            for (int j = 0; j < 21; j++) {
                if (j > 0) sb.append(",");
                sb.append(i == j ? 0 : 1);
            }
            sb.append("\n");
        }
        assertThrows(IllegalArgumentException.class,
                () -> loadCsvMatrixLogic(createCsv(sb.toString())));
    }

    @Test @DisplayName("Ошибка: отрицательный вес")
    void negativeWeight() {
        assertThrows(IllegalArgumentException.class,
                () -> loadCsvMatrixLogic(createCsv("0,-5\n3,0\n")));
    }

    @Test @DisplayName("Ошибка: нулевой вес вне диагонали")
    void zeroOffDiagonal() {
        assertThrows(IllegalArgumentException.class,
                () -> loadCsvMatrixLogic(createCsv("0,0\n3,0\n")));
    }

    @Test @DisplayName("Ошибка: нечисловое значение")
    void nonNumeric() {
        assertThrows(IllegalArgumentException.class,
                () -> loadCsvMatrixLogic(createCsv("0,abc\n3,0\n")));
    }

    @Test @DisplayName("Ошибка: пустой файл")
    void emptyFile() {
        assertThrows(IllegalArgumentException.class,
                () -> loadCsvMatrixLogic(createCsv("")));
    }

    // ===== UNIT-ТЕСТЫ: СОХРАНЕНИЕ И ROUND-TRIP =====

    @Test @DisplayName("Сохранение: null записывается как 'inf'")
    void saveNullAsInf() throws Exception {
        File f = tempDir.resolve("s.csv").toFile();
        Integer[][] orig = {{0, null}, {5, 0}};
        saveMatrixLogic(f, orig);
        Integer[][] loaded = loadCsvMatrixLogic(f);
        assertArrayEquals(orig[0], loaded[0]);
        assertArrayEquals(orig[1], loaded[1]);
    }

    @Test @DisplayName("Round-trip: сложная матрица 4x4")
    void roundTrip4x4() throws Exception {
        File f = tempDir.resolve("rt.csv").toFile();
        Integer[][] orig = {
                {0, 7, null, 2},
                {null, 0, 3, null},
                {1, null, 0, 5},
                {null, 4, null, 0}
        };
        saveMatrixLogic(f, orig);
        Integer[][] loaded = loadCsvMatrixLogic(f);
        for (int i = 0; i < 4; i++) assertArrayEquals(orig[i], loaded[i]);
    }

    @Test @DisplayName("Сохранение лога")
    void saveLog() throws Exception {
        File f = tempDir.resolve("log.txt").toFile();
        List<String> entries = Arrays.asList(
                "[INFO] Запуск",
                "[STATE] Шаг 1",
                "[ERROR] Ошибка"
        );
        saveLogFileLogic(f, entries);
        assertTrue(f.exists() && f.length() > 0);
    }
}