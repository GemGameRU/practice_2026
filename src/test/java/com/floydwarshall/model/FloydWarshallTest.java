package com.floydwarshall.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

class FloydWarshallTest {

    @Test
    @DisplayName("Простой граф с известным результатом")
    void testSimpleGraph() {
        // Граф: 3 вершины
        // 0 -> 1 (вес 3)
        // 0 -> 2 (вес 8)
        // 1 -> 2 (вес 1)
        Integer[][] matrix = {
            {0, 3, 8},
            {null, 0, 1},
            {null, null, 0}
        };
        
        Integer[][] result = FloydWarshall.run(matrix, 3);
        
        assertEquals(0, result[0][0]);
        assertEquals(3, result[0][1]);
        assertEquals(4, result[0][2]); // 0 -> 1 -> 2 = 3 + 1 = 4
        assertNull(result[1][0]); // нет пути из 1 в 0
        assertEquals(0, result[1][1]);
        assertEquals(1, result[1][2]);
        assertNull(result[2][0]);
        assertNull(result[2][1]);
        assertEquals(0, result[2][2]);
    }

    @Test
    @DisplayName("Граф с недостижимыми вершинами")
    void testUnreachableVertices() {
        // Граф: 0 -> 1, но нет пути к 2
        Integer[][] matrix = {
            {0, 5, null},
            {null, 0, null},
            {null, null, 0}
        };
        
        Integer[][] result = FloydWarshall.run(matrix, 3);
        
        assertEquals(5, result[0][1]);
        assertNull(result[0][2]); // 2 недостижима из 0
        assertNull(result[1][2]); // 2 недостижима из 1
    }

    @Test
    @DisplayName("Граф только с диагональю (изолированные вершины)")
    void testIsolatedVertices() {
        Integer[][] matrix = {
            {0, null, null},
            {null, 0, null},
            {null, null, 0}
        };
        
        Integer[][] result = FloydWarshall.run(matrix, 3);
        
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (i == j) {
                    assertEquals(0, result[i][j]);
                } else {
                    assertNull(result[i][j]);
                }
            }
        }
    }

    @Test
    @DisplayName("Полный граф")
    void testCompleteGraph() {
        Integer[][] matrix = {
            {0, 1, 2},
            {3, 0, 4},
            {5, 6, 0}
        };
        
        Integer[][] result = FloydWarshall.run(matrix, 3);
        
        // Все кратчайшие пути должны быть найдены
        // 0 -> 2: min(2, 1+4) = 2
        // 1 -> 0: min(3, 4+5) = 3
        // 2 -> 0: 5
        // 2 -> 1: min(6, 5+1) = 6
        assertEquals(2, result[0][2]);
        assertEquals(3, result[1][0]);
        assertEquals(5, result[2][0]);
        assertEquals(6, result[2][1]);
    }

    @Test
    @DisplayName("Граф с циклом")
    void testGraphWithCycle() {
        Integer[][] matrix = {
            {0, 1, null},
            {null, 0, 2},
            {3, null, 0}
        };
        
        Integer[][] result = FloydWarshall.run(matrix, 3);
        
        // 0 -> 2: 1 + 2 = 3
        // 2 -> 1: 3 + 1 = 4
        assertEquals(3, result[0][2]);
        assertEquals(4, result[2][1]);
        assertEquals(3, result[2][0]);
    }

    @Test
    @DisplayName("totalSteps вычисляет N³")
    void testTotalSteps() {
        assertEquals(8, FloydWarshall.totalSteps(2));
        assertEquals(27, FloydWarshall.totalSteps(3));
        assertEquals(64, FloydWarshall.totalSteps(4));
        assertEquals(8000, FloydWarshall.totalSteps(20));
    }
}
