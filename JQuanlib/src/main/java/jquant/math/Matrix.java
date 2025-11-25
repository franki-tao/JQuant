package jquant.math;

import org.apache.commons.math3.linear.*;

public class Matrix {
    public RealMatrix matrix;

    public Matrix(double[][] m) {
        matrix = new Array2DRowRealMatrix(m);
    }

    public Matrix(RealMatrix matrix) {
        this.matrix = matrix;
    }

    public Matrix(int row, int col) {
        if (row * col == 0) {
            matrix = null;
            return;
        }
        matrix = new Array2DRowRealMatrix(row, col);
        matrix = matrix.scalarAdd(Double.NaN);
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

    public Array getRowArray(int row) {
        return new Array(matrix.getRowVector(row));
    }

    public Array getColArray(int col) {
        return new Array(matrix.getColumnVector(col));
    }

    public Matrix transpose() {
        RealMatrix transpose = matrix.transpose();
        return new Matrix(transpose);
    }


    public Matrix multipy(Matrix m) {
        return new Matrix(this.matrix.multiply(m.matrix));
    }

    public Matrix multiply(double val) {
        return new Matrix(this.matrix.scalarMultiply(val));
    }

    public Matrix add(Matrix m) {
        return new Matrix(this.matrix.add(m.matrix));
    }

    public Matrix subtract(Matrix m) {
        return new Matrix(this.matrix.subtract(m.matrix));
    }

    public Matrix inverse() {
        return new Matrix(MatrixUtils.inverse(matrix));
    }

    public void row_fill(int row, int begin, int end, double val) {
        for (int i = begin; i < end; i++) {
            set(row, i, val);
        }
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
                set(i, j, arr[i * cols() + j]);
            }
        }
    }

    //后乘 ， M * V
    public Array mutiply(Array arr) {
        return new Array(matrix.transpose().preMultiply(arr.realVector));
    }

    public void substractEq(int i, int j, double val) {
        set(i, j, get(i, j) - val);
    }

    public void addEq(int i, int j, double val) {
        substractEq(i, j, -val);
    }

    public void multipyEq(int i, int j, double val) {
        set(i, j, get(i, j) * val);
    }

    public void swap(int li, int lj, int ri, int rj) {
        double tmp = get(li, lj);
        set(li, lj, get(ri, rj));
        set(ri, rj, tmp);
    }

    public double maxEle() {
        double maxval =  Double.MIN_VALUE;
        for (int i = 0; i < rows(); i++) {
            for (int j = 0; j < cols(); j++) {
                if (get(i, j) > maxval) {
                    maxval = get(i, j);
                }
            }
        }
        return maxval;
    }

    public double minEle() {
        double minval =  Double.MAX_VALUE;
        for (int i = 0; i < rows(); i++) {
            for (int j = 0; j < cols(); j++) {
                if (get(i, j) < minval) {
                    minval = get(i, j);
                }
            }
        }
        return minval;
    }

    public Array diagonal() {
        int size = Math.min(rows(), cols());
        Array res = new Array(size);
        for (int i = 0; i < size; i++) {
            res.set(i, get(i, i));
        }
        return res;
    }

    public void fill(double val) {
        for (int i = 0; i < rows(); i++) {
            for (int j = 0; j < cols(); j++) {
                set(i, j, val);
            }
        }
    }

    @Override
    public String toString() {
        return "Matrix{" +
                "matrix=" + matrix +
                '}';
    }

    public static void main(String[] args) {
        Matrix m = new Matrix(3, 2, 10);
        System.out.println(m.matrix);
        System.out.println(m.getColArray(0));
        Matrix m1 = new Matrix(0,0);
        System.out.println(m1.matrix == null);
    }

}
