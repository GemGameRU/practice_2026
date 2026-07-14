package com.floydwarshall.model;

public final class FloydWarshallExecutor {

    private final Integer[][] dist;
    private final int n;

    private int k;
    private int i;
    private int j;
    private boolean isFinished;

    public FloydWarshallExecutor(Integer[][] source) {
        this.n = source.length;
        this.dist = MatrixOps.deepCopy(source);
        this.k = 0;
        this.i = 0;
        this.j = 0;
        this.isFinished = false;
    }

    public StepResult stepForward() {
        if (isFinished) {
            return new StepResult(k, i, j, false, "Алгоритм завершён", dist);
        }

        boolean wasUpdate = false;
        String description;

        if (dist[i][k] == null) {
            description = String.format("Нет пути из %d в %d. Пропускаем все j для этой пары.", i, k);
            i++;
            j = 0;
        }
        else if (dist[k][j] == null) {
            description = String.format("Нет пути из %d в %d. Пропускаем проверку.", k, j);
            wasUpdate = false;
            j++;
        }
        else {
            int alt = dist[i][k] + dist[k][j];

            if (dist[i][j] == null || alt < dist[i][j]) {
                dist[i][j] = alt;
                wasUpdate = true;
                description = String.format("Путь %d → %d через %d стал короче (новый вес: %d)", i, j, k, alt);
            }             
            else {
                wasUpdate = false;
                description = String.format("Путь %d → %d через %d (вес: %d) не короче текущего (%d).", i, j, k, alt, dist[i][j]);
            }
            j++;
        }

        if (j == n) {
            j = 0;
            i++;
        }
        if (i == n) {
            i = 0;
            k++;
        }
        if (k == n) {
            isFinished = true;
        }

        return new StepResult(k, i, j, wasUpdate, description, dist);
    }

    public boolean isFinished() {
        return isFinished;
    }

    public record StepResult(
        int k,
        int i,
        int j,
        boolean wasUpdate,
        String description,
        Integer[][] dist
    ) {}
}