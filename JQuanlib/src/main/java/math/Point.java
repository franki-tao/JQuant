package math;

public class Point <T> {
    private T first;
    private T second;

    public Point(T a, T b) {
        this.first = a;
        this.second = b;
    }

    public T getFirst() {
        return first;
    }

    public void setFirst(T first) {
        this.first = first;
    }

    public T getSecond() {
        return second;
    }

    public void setSecond(T second) {
        this.second = second;
    }
}
