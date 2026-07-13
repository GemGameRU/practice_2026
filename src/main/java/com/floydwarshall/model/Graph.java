package com.floydwarshall.model;

import java.util.Objects;

public class Graph {

    private int n;
    private Integer[][] matrix;

    public Graph(int n) {
        if (n < 2 || n > 20) {
            throw new IllegalArgumentException(
                    "Число вершин должно быть в диапазоне от 2 до 20, получено: " + n);
        }
        this.n = n;
        this.matrix = new Integer[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                matrix[i][j] = (i == j) ? 0 : null;
            }
        }
    }

    public Graph(Graph other) {
        this.n = other.n;
        this.matrix = MatrixOps.deepCopy(other.matrix);
    }

    public int size() {
        return n;
    }

    public Integer get(int i, int j) {
        return matrix[i][j];
    }

    public void set(int i, int j, Integer v) {
        if (i == j) {
            return; // диагональ не редактируется
        }
        if (v != null && v <= 0) {
            throw new IllegalArgumentException("Вес ребра должен быть положительным");
        }
        matrix[i][j] = v;
    }

    public boolean hasEdge(int i, int j) {
        if (i == j) {
            return false;
        }
        return matrix[i][j] != null;
    }

    public Integer[][] snapshot() {
        return MatrixOps.deepCopy(matrix);
    }

    public void replaceMatrix(Integer[][] newMatrix) {
        Objects.requireNonNull(newMatrix, "Матрица не должна быть null");
        int newSize = newMatrix.length;
        if (newSize < 2 || newSize > 20) {
            throw new IllegalArgumentException(
                    "Размер матрицы должен быть от 2 до 20, получено: " + newSize);
        }
        for (Integer[] row : newMatrix) {
            if (row.length != newSize) {
                throw new IllegalArgumentException("Матрица должна быть квадратной");
            }
        }
        this.n = newSize;
        this.matrix = new Integer[newSize][newSize];
        for (int i = 0; i < newSize; i++) {
            for (int j = 0; j < newSize; j++) {
                if (i == j) {
                    this.matrix[i][j] = 0;
                } else {
                    this.matrix[i][j] = newMatrix[i][j];
                }
            }
        }
    }

    public void addVertex() {
        if (n >= 20) {
            throw new IllegalStateException("Достигнуто максимальное число вершин (20)");
        }
        int newN = n + 1;
        Integer[][] newMatrix = new Integer[newN][newN];
        for (int i = 0; i < newN; i++) {
            for (int j = 0; j < newN; j++) {
                if (i < n && j < n) {
                    newMatrix[i][j] = matrix[i][j];
                } else if (i == j) {
                    newMatrix[i][j] = 0;
                } else {
                    newMatrix[i][j] = null;
                }
            }
        }
        this.n = newN;
        this.matrix = newMatrix;
    }

    public void removeVertex(int index) {
        if (n <= 2) {
            throw new IllegalStateException("В графе должно быть не менее двух вершин");
        }
        if (index < 0 || index >= n) {
            throw new IndexOutOfBoundsException("Неверный индекс вершины: " + index);
        }
        int newN = n - 1;
        Integer[][] newMatrix = new Integer[newN][newN];
        int ri = 0;
        for (int i = 0; i < n; i++) {
            if (i == index)
                continue;
            int rj = 0;
            for (int j = 0; j < n; j++) {
                if (j == index)
                    continue;
                newMatrix[ri][rj] = matrix[i][j];
                rj++;
            }
            ri++;
        }
        this.n = newN;
        this.matrix = newMatrix;
    }
}
