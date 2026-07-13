package com.floydwarshall.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

class MatrixOpsTest {

    @Test
    @DisplayName("Глубокое копирование создаёт независимую копию")
    void testDeepCopyIndependent() {
        Integer[][] original = {
            {0, 5, null},
            {3, 0, 2},
            {null, 1, 0}
        };
        
        Integer[][] copy = MatrixOps.deepCopy(original);
        
        // Изменение копии не влияет на оригинал
        copy[0][1] = 100;
        assertEquals(5, original[0][1]);
        assertEquals(100, copy[0][1]);
    }

    @Test
    @DisplayName("Глубокое копирование сохраняет null значения")
    void testDeepCopyPreservesNull() {
        Integer[][] original = {
            {0, null},
            {null, 0}
        };
        
        Integer[][] copy = MatrixOps.deepCopy(original);
        
        assertNull(copy[0][1]);
        assertNull(copy[1][0]);
        assertEquals(0, copy[0][0]);
        assertEquals(0, copy[1][1]);
    }

    @Test
    @DisplayName("Глубокое копирование матрицы 1x1")
    void testDeepCopySingleElement() {
        Integer[][] original = {{0}};
        Integer[][] copy = MatrixOps.deepCopy(original);
        
        assertEquals(1, copy.length);
        assertEquals(1, copy[0].length);
        assertEquals(0, copy[0][0]);
    }
}
