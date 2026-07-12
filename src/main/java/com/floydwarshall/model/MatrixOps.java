package com.floydwarshall.model;

public final class MatrixOps {

    private MatrixOps() {
    }

    public static Integer[][] deepCopy(Integer[][] source) {
        Integer[][] copy = new Integer[source.length][source[0].length];
        for (int i = 0; i < source.length; i++) {
            System.arraycopy(source[i], 0, copy[i], 0, source[i].length);
        }
        return copy;
    }
}
