package jquant.math.matrixutilities.impl;

import jquant.math.Matrix;
import org.apache.commons.math3.linear.OpenMapRealMatrix;

// 稀疏矩阵
public class SparseMatrix {
    private OpenMapRealMatrix matrix;

    public SparseMatrix(int row,  int col) {
        matrix = new OpenMapRealMatrix(row, col);
    }

    public int size1() {
        return matrix.getRowDimension();
    }

    public int size2() {
        return matrix.getColumnDimension();
    }

    public void set(int row, int col, double value) {
        matrix.setEntry(row, col, value);
    }

    public double get(int row, int col) {
        return matrix.getEntry(row, col);
    }

    public Matrix multiply(Matrix m) {
        return new Matrix(matrix.multiply(m.matrix));
    }
}
