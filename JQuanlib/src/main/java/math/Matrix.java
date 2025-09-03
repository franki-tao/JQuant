package math;

import org.apache.commons.math3.linear.*;

public class Matrix {
    public RealMatrix matrix;

    public Matrix(double[][] m) {
        matrix = new Array2DRowRealMatrix(m);
    }

    private Matrix(RealMatrix matrix) {
        this.matrix = matrix;
    }

    public Matrix(int row, int col, double value) {
        if (row * col == 0) {
            matrix = null;
            return;
        }
        matrix = new Array2DRowRealMatrix(row, col);
        matrix = matrix.scalarAdd(value);
    }

    public int rows() {
        return matrix.getRowDimension();
    }

    public int cols() {
        return matrix.getColumnDimension();
    }

    public double get(int row, int col) {
        return matrix.getEntry(row, col);
    }

    public void set(int i, int j, double val) {
        matrix.setEntry(i, j, val);
    }

    public Matrix transpose() {
        RealMatrix transpose = matrix.transpose();
        return new Matrix(transpose);
    }

    public static void main(String[] args) {
        Matrix m = new Matrix(0, 2, 10);
        System.out.println(m.matrix);
    }

}
