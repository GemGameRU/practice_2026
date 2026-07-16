package com.floydwarshall.model;

public final class FloydWarshallExecutor {
    private Integer[][] dist;
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

    public int getCurrentK() {
        return k;
    }

    public int getCurrentI() {
        return i;
    }

    public int getCurrentJ() {
        return j;
    }

    public int getN() {
        return n;
    }

    public Integer[][] getDist() {
        return MatrixOps.deepCopy(dist);
    }

    public void setState(int k, int i, int j, Integer[][] dist) {
        this.k = k;
        this.i = i;
        this.j = j;
        this.dist = MatrixOps.deepCopy(dist);
        this.isFinished = (k == n);
    }

    public StepResult stepForward() {
        if (isFinished) {
            return new StepResult(k, i, j, false, null, null, null, null, "Алгоритм завершён", dist);
        }
        int currK = k, currI = i, currJ = j;
        Integer oldValue = dist[i][j];
        Integer dik = dist[i][k];
        Integer dkj = dist[k][j];
        Integer altValue = null;
        boolean wasUpdate = false;
        String description;

        if (dik == null || dkj == null) {
            if (dik == null) {
                description = String.format("Нет пути из %d в %d. Обновление невозможно.", i, k);
            } else {
                description = String.format("Нет пути из %d в %d. Обновление невозможно.", k, j);
            }
        } else {
            altValue = dik + dkj;
            if (oldValue == null || altValue < oldValue) {
                dist[i][j] = altValue;
                wasUpdate = true;
                description = String.format("Путь %d → %d через %d стал короче (новый вес: %d)", i, j, k, altValue);
            } else {
                description = String.format("Путь %d → %d через %d (вес: %d) не короче текущего (%d).", i, j, k,
                        altValue, oldValue);
            }
        }

        j++;
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

        return new StepResult(currK, currI, currJ, wasUpdate, oldValue, altValue, dik, dkj, description, dist);
    }

    public boolean isFinished() {
        return isFinished;
    }

    public record StepResult(
            int k, int i, int j,
            boolean wasUpdate,
            Integer oldValue,
            Integer altValue,
            Integer dik,
            Integer dkj,
            String description,
            Integer[][] dist) {
    }
}