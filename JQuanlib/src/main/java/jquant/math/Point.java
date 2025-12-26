package jquant.math;

public class Point<T extends Number & Comparable<T>, R extends Number & Comparable<R>>
        implements Comparable<Point<T, R>> {
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

    @Override
    public int compareTo(Point<T, R> other) {
        // 比较 first
        int cmp = this.first.compareTo(other.first);
        if (cmp != 0) return cmp;
        // first 相等则比较 second
        return this.second.compareTo(other.second);
    }
}
