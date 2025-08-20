package math;

import java.util.List;

public class Pair {
    private double first;
    private List<Double> second;

    public Pair(double first, List<Double> second) {
        this.first = first;
        this.second = second;
    }

    public Pair() {
    }

    public double getFirst() {
        return first;
    }

    public List<Double> getSecond() {
        return second;
    }

    public void setFirst(double first) {
        this.first = first;
    }

    public void setSecond(List<Double> second) {
        this.second = second;
    }

    @Override
    public String toString() {
        return "[" + first + ", " + second + "]";
    }
}
