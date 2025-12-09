package jquant.math;
import java.util.Comparator;

// 假设你的 Point<T> 类保持不变

public class PointComparator implements Comparator<Point<Double, Double>> {
    @Override
    public int compare(Point<Double, Double> p1, Point<Double, Double> p2) {
        // 1. 比较 first 元素 (主键)
        int firstComparison = p1.getFirst().compareTo(p2.getFirst());

        // 如果 first 元素不相等，则直接返回结果
        if (firstComparison != 0) {
            return firstComparison;
        }

        // 2. 如果 first 元素相等，则比较 second 元素 (次要键)
        return p1.getSecond().compareTo(p2.getSecond());
    }
}