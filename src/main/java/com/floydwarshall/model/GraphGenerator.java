package com.floydwarshall.model;

import java.util.Random;

public class GraphGenerator {
    private Random random;

    public GraphGenerator(Random random) {
        this.random = random;
    }

    public Integer[][] generateMatrix(int n, int[] degree, int minWeight, int maxWeight) {
        Integer[][] matrix = new Integer[n][n];

        for (int i = 0; i < n; i++) {
            int[] candidates = new int[n - 1];
            int idx = 0;
            for (int j = 0; j < n; j++) {
                if (j != i) {
                    candidates[idx++] = j;
                }
            }

            for (int j = candidates.length - 1; j > 0; j--) {
                int k = random.nextInt(j + 1);
                int temp = candidates[j];
                candidates[j] = candidates[k];
                candidates[k] = temp;
            }

            if (degree[i] >= n) {
                degree[i] = n - 1;
            }

            for (int j = 0; j < degree[i]; j++) {
                int target = candidates[j];
                matrix[i][target] = random.nextInt(maxWeight - minWeight + 1) + minWeight;
            }
        }
        
        return matrix;
    }
}