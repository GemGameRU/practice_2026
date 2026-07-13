package com.floydwarshall.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

class GraphTest {

    @Test
    @DisplayName("Создание графа с минимальным размером (2)")
    void testCreateMinSize() {
        Graph g = new Graph(2);
        assertEquals(2, g.size());
        assertEquals(0, g.get(0, 0));
        assertEquals(0, g.get(1, 1));
        assertNull(g.get(0, 1));
        assertNull(g.get(1, 0));
    }

    @Test
    @DisplayName("Создание графа с максимальным размером (20)")
    void testCreateMaxSize() {
        Graph g = new Graph(20);
        assertEquals(20, g.size());
    }

    @Test
    @DisplayName("Ошибка при размере меньше 2")
    void testCreateTooSmall() {
        assertThrows(IllegalArgumentException.class, () -> new Graph(1));
        assertThrows(IllegalArgumentException.class, () -> new Graph(0));
    }

    @Test
    @DisplayName("Ошибка при размере больше 20")
    void testCreateTooLarge() {
        assertThrows(IllegalArgumentException.class, () -> new Graph(21));
    }

    @Test
    @DisplayName("Установка положительного веса ребра")
    void testSetPositiveWeight() {
        Graph g = new Graph(3);
        g.set(0, 1, 5);
        assertEquals(5, g.get(0, 1));
    }

    @Test
    @DisplayName("Установка null (бесконечность)")
    void testSetNullWeight() {
        Graph g = new Graph(3);
        g.set(0, 1, 10);
        g.set(0, 1, null);
        assertNull(g.get(0, 1));
    }

    @Test
    @DisplayName("Ошибка при отрицательном весе")
    void testSetNegativeWeight() {
        Graph g = new Graph(3);
        assertThrows(IllegalArgumentException.class, () -> g.set(0, 1, -5));
    }

    @Test
    @DisplayName("Ошибка при нулевом весе")
    void testSetZeroWeight() {
        Graph g = new Graph(3);
        assertThrows(IllegalArgumentException.class, () -> g.set(0, 1, 0));
    }

    @Test
    @DisplayName("Диагональ защищена от редактирования")
    void testDiagonalNotEditable() {
        Graph g = new Graph(3);
        g.set(0, 0, 100);
        assertEquals(0, g.get(0, 0)); // осталось 0
    }

    @Test
    @DisplayName("Добавление вершины")
    void testAddVertex() {
        Graph g = new Graph(2);
        g.addVertex();
        assertEquals(3, g.size());
        assertEquals(0, g.get(2, 2)); // диагональ новой вершины = 0
        assertNull(g.get(0, 2)); // новое ребро = null
    }

    @Test
    @DisplayName("Ошибка при добавлении вершины сверх максимума")
    void testAddVertexMaxExceeded() {
        Graph g = new Graph(20);
        assertThrows(IllegalStateException.class, g::addVertex);
    }

    @Test
    @DisplayName("Удаление вершины")
    void testRemoveVertex() {
        Graph g = new Graph(3);
        g.set(0, 1, 5);
        g.set(1, 2, 3);
        g.removeVertex(1);
        assertEquals(2, g.size());
    }

    @Test
    @DisplayName("Ошибка при удалении из графа с 2 вершинами")
    void testRemoveVertexMinExceeded() {
        Graph g = new Graph(2);
        assertThrows(IllegalStateException.class, () -> g.removeVertex(0));
    }

    @Test
    @DisplayName("Ошибка при некорректном индексе вершины")
    void testRemoveVertexInvalidIndex() {
        Graph g = new Graph(3);
        assertThrows(IndexOutOfBoundsException.class, () -> g.removeVertex(5));
    }

    @Test
    @DisplayName("hasEdge корректно определяет наличие ребра")
    void testHasEdge() {
        Graph g = new Graph(3);
        g.set(0, 1, 5);
        assertTrue(g.hasEdge(0, 1));
        assertFalse(g.hasEdge(1, 0));
        assertFalse(g.hasEdge(0, 0)); // диагональ не считается ребром
    }

    @Test
    @DisplayName("replaceMatrix заменяет матрицу")
    void testReplaceMatrix() {
        Graph g = new Graph(2);
        Integer[][] newMatrix = {
            {0, 10, null},
            {5, 0, 3},
            {null, 2, 0}
        };
        g.replaceMatrix(newMatrix);
        assertEquals(3, g.size());
        assertEquals(10, g.get(0, 1));
        assertEquals(5, g.get(1, 0));
        assertNull(g.get(0, 2));
    }

    @Test
    @DisplayName("replaceMatrix ошибка при неквадратной матрице")
    void testReplaceMatrixNotSquare() {
        Graph g = new Graph(2);
        Integer[][] badMatrix = {
            {0, 1, 2},
            {3, 0, 4}
        };
        assertThrows(IllegalArgumentException.class, () -> g.replaceMatrix(badMatrix));
    }
}
