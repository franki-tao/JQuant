package jquant.utilities;

public class Null<T> {
    private static final Null<?> INSTANCE = new Null<>();

    // 私有构造函数，确保单例
    private Null() {}

    // 获取单例实例
    @SuppressWarnings("unchecked")
    public static <T> Null<T> getInstance() {
        return (Null<T>) INSTANCE;
    }

    // 获取空值的表示
    public T getValue() {
        // 这里无法直接使用Java的泛型获取T的类型信息
        // 因此需要在调用时指定类型
        throw new UnsupportedOperationException("getValue() requires explicit type specification");
    }

    // 根据Class对象获取空值的表示
    public static <T> T getValue(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null");
        }

        if (type == Double.class || type == double.class) {
            return type.cast(Double.MAX_VALUE);
        } else if (type == Float.class || type == float.class) {
            return type.cast(Float.MAX_VALUE);
        } else if (type == Integer.class || type == int.class) {
            return type.cast(Integer.MAX_VALUE);
        } else if (type == Long.class || type == long.class) {
            return type.cast(Long.MAX_VALUE);
        } else if (type == String.class) {
            return type.cast("");
        } else {
            try {
                // 尝试通过默认构造函数创建实例
                return type.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                // 如果没有默认构造函数，返回null
                return null;
            }
        }
    }

    public static void main(String[] args) {
        // 获取Integer类型的空值
        Integer intNull = Null.getValue(Integer.class);
        System.out.println("Integer Null: " + intNull); // 输出: 2147483647

        // 获取Double类型的空值
        Float doubleNull = Null.getValue(Float.class);
        System.out.println("Double Null: " + doubleNull); // 输出: 1.7976931348623157E308

        // 获取String类型的空值
        String stringNull = Null.getValue(String.class);
        System.out.println("String Null: " + stringNull); // 输出: ""
    }
}
