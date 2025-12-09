package jquant.math;

public class Point <T extends Number, R extends Number> {
    private T first;
    private R second;

    public Point() {
        this.first = null;
        this.second = null;
    }

    public Point(T a, R b) {
        this.first = a;
        this.second = b;
    }

    public T getFirst() {
        return first;
    }

    public void setFirst(T first) {
        this.first = first;
    }

    public R getSecond() {
        return second;
    }

    public void setSecond(R second) {
        this.second = second;
    }
}
