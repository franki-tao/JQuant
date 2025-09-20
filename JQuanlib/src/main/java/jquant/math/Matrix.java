package jquant.math;

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

    public Matrix multipy(Matrix m) {
        return new Matrix(this.matrix.multiply(m.matrix));
    }

    public Matrix subtract(Matrix m) {
        return new Matrix(this.matrix.subtract(m.matrix));
    }

    public Matrix inverse() {
        return new Matrix(MatrixUtils.inverse(matrix));
    }

    public double[] toArray() {
        int r = rows();
        int c = cols();
        double[] res = new double[r * c];
        for (int i = 0; i < rows(); i++) {
            for (int j = 0; j < cols(); j++) {
                res[i * c + j] = get(i, j);
            }
        }
        return res;
    }

    public void ArraytoMatrix(double[] arr) {
        for (int i = 0; i < rows(); i++) {
            for (int j = 0; j < cols(); j++) {
                set(i,j, arr[i*cols()+j]);
            }
        }
    }

    //后乘 ， M * V
    public Array mutiply(Array arr) {
        return new Array(matrix.transpose().preMultiply(arr.realVector));
    }
}
