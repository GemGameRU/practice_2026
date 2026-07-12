package com.floydwarshall.model;

public final class FloydWarshall {

    private FloydWarshall() {

    }

    public static Integer[][] run(Integer[][] source, int n) {
        Integer[][] dist = MatrixOps.deepCopy(source);

        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                if (dist[i][k] == null) {
                    continue;
                }
                for (int j = 0; j < n; j++) {
                    if (dist[k][j] == null) {
                        continue;
                    }
                    int alt = dist[i][k] + dist[k][j];
                    if (dist[i][j] == null || alt < dist[i][j]) {
                        dist[i][j] = alt;
                    }
                }
            }
        }
        return dist;
    }

    public static int totalSteps(int n) {
        return n * n * n;
    }
}
