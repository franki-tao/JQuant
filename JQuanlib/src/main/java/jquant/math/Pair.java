package jquant.math;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Pair implements Comparable<Pair>{
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

    @Override
    public int compareTo(Pair other) {
        // 1. 先比较 first（降序）：当前对象的 first - 其他对象的 first（结果为负则当前小，正则当前大）
        int firstCompare = Double.compare(other.first, this.first);
        // （Double.compare(a,b) 返回：a<b→负，a==b→0，a>b→正；这里用 other.first - this.first 实现降序）

        if (firstCompare != 0) {
            return firstCompare; // first 不同，直接返回结果（降序）
        }

        // 2. first 相同，比较 second（List<Double> 字典序降序）
        // 处理 null 情况（避免空指针）：默认 null 小于任何非空列表
        if (this.second == null && other.second == null) {
            return 0;
        }
        if (this.second == null) {
            return -1; // 当前 second 为 null，排在后面（降序：非空在前）
        }
        if (other.second == null) {
            return 1; // 其他 second 为 null，当前排在前面
        }

        // 逐元素比较 List<Double>（字典序降序）
        int minSize = Math.min(this.second.size(), other.second.size());
        for (int i = 0; i < minSize; i++) {
            Double d1 = this.second.get(i);
            Double d2 = other.second.get(i);
            int elemCompare = Double.compare(d2, d1); // 降序：d2 - d1
            if (elemCompare != 0) {
                return elemCompare;
            }
        }

        // 3. 前 minSize 个元素都相同，短列表排在后面（降序：长列表在前）
        return Integer.compare(this.second.size(), other.second.size());
    }

    public static void main(String[] args) {
        // 1. 创建 List<Pair> 并赋值（对应 C++ 的 temp 容器）
        List<Pair> temp = new ArrayList<>();
        temp.add(new Pair(2.5, Arrays.asList(1.0, 3.0)));
        temp.add(new Pair(3.8, Arrays.asList(2.0)));
        temp.add(new Pair(2.5, Arrays.asList(1.0, 4.0)));
        temp.add(new Pair(2.5, Arrays.asList(1.0))); // 测试 second 长度不同的情况

        // 2. 排序（自然排序：按 first 降序 → second 字典序降序）
        Collections.sort(temp); // 方式 1：Collections 工具类
        // 或 List.sort()（Java 8+）：temp.sort(null); （null 表示使用自然排序）

        // 3. 输出结果
        System.out.println("排序后：");
        for (Pair p : temp) {
            System.out.println(p);
        }
    }
}
