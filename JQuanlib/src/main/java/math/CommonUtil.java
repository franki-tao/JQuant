package math;

import java.util.*;

public class CommonUtil {

    public static void QL_REQUIRE(boolean flag, String error_info) {
        if (!flag) {
            throw new IllegalArgumentException(error_info);
        }
    }

    public static void QL_FAIL(String s) {
        throw new IllegalArgumentException(s);
    }

    public static void QL_ASSERT(boolean flag, String error_info) {
        if (!flag) {
            throw new IllegalArgumentException(error_info);
        }
    }

    public static <T> List<T> ArraySet(T... a) {
        List<T> result = new ArrayList<>();
        Collections.addAll(result, a);
        return result;
    }

    public static <T> List<T> ArrayInit(int n) {
        List<T> result = new ArrayList<>(n);
        for (int i=0; i<n; i++) {
            result.add(null);
        }
        return result;
    }

    public static <T> List<T> ArrayInit(int n, T t) {
        List<T> result = new ArrayList<>(n);
        for (int i=0; i<n; i++) {
            result.add(t);
        }
        return result;
    }

    public static <T extends Number> boolean All_of(List<T> arr, Compare<T> compare) {
        for(T t : arr) {
            if (!compare.call(t)) {
                return false;
            }
        }
        return true;
    }

    public static <T> List<T> resize(List<T> arr, int n, T t) {
        // 处理边界情况
        if (n < 0) {
            throw new IllegalArgumentException("n不能为负数: " + n);
        }
        if (arr == null) {
            return new ArrayList<>(Collections.nCopies(n, null));
        }

        int size = arr.size();
        // 如果大小相等，直接返回新的ArrayList避免外部修改影响
        if (size == n) {
            return new ArrayList<>(arr);
        }

        List<T> result = new ArrayList<>(n); // 预分配容量

        if (size > n) {
            // 截断：使用subList获取前n个元素
            result.addAll(arr.subList(0, n));
        } else {
            // 扩容：先添加所有元素，再补充null
            result.addAll(arr);
            // 添加剩余需要的null元素
            result.addAll(Collections.nCopies(n - size, t));
        }

        return result;
    }

    public static List<Double> ArrayT(int size, double start, double increase) {
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            result.add(start + increase * i);
        }
        return result;
    }

    public static <T> List<T> clone(List<T> array) {
        return new ArrayList<>(array);
    }
    public static <T> Vector<T> clone(Vector<T> array) {
        return new Vector<>(array);
    }

    public static void main(String[] args) {
        List<Integer> a = Arrays.asList(1, 2, 3);
        List<Integer> c = a;
        List<Integer> b = clone(a);
        a.set(1, 100);
//        System.out.println(b.get(1));
//        System.out.println(c.get(1));
        List<Double> doubles = ArrayT(10, 1, 2);
        for (int i = 0; i < 10; i++) {
            System.out.println(doubles.get(i));
        }
    }
}
