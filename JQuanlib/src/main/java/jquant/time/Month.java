package jquant.time;

import java.util.HashMap;
import java.util.Map;

/**
 * 代表月份的枚举类 (1 = January, 12 = December)。
 * 提供了月份的整数值、全名和短名称。
 */
public enum Month {

    // 枚举常量定义：(Value, Short Name, Full Name)
    JANUARY(1, "Jan", "January"),
    FEBRUARY(2, "Feb", "February"),
    MARCH(3, "Mar", "March"),
    APRIL(4, "Apr", "April"),
    MAY(5, "May", "May"),
    JUNE(6, "Jun", "June"),
    JULY(7, "Jul", "July"),
    AUGUST(8, "Aug", "August"),
    SEPTEMBER(9, "Sep", "September"),
    OCTOBER(10, "Oct", "October"),
    NOVEMBER(11, "Nov", "November"),
    DECEMBER(12, "Dec", "December");

    private final int value;
    private final String shortName;
    private final String fullName;

    private Month(int value, String shortName, String fullName) {
        this.value = value;
        this.shortName = shortName;
        this.fullName = fullName;
    }

    /**
     * 获取月份的整数值 (1-12)。
     * @return 整数值
     */
    public int getValue() {
        return value;
    }

    /**
     * 获取月份的短格式名称 (e.g., "Jan")。
     * @return 三字母缩写
     */
    public String getShortName() {
        return shortName;
    }

    // 替代 C++ 的 operator<< 重载：使用标准的 toString() 方法。
    // 默认输出完整的月份名称。
    @Override
    public String toString() {
        return this.fullName;
    }


    private static final Map<Integer, Month> VALUE_MAP = new HashMap<>();

    // 使用静态块初始化查找表，确保高效查找
    static {
        for (Month month : values()) {
            VALUE_MAP.put(month.value, month);
        }
    }

    /**
     * 根据整数值 (1-12) 查找对应的 Month 枚举常量。
     * @param value 月份值 (1=January, 12=December)
     * @return 对应的 Month 枚举常量
     * @throws IllegalArgumentException 如果值不在 1-12 范围内
     */
    public static Month fromValue(int value) {
        Month month = VALUE_MAP.get(value);
        if (month == null) {
            // 模仿 C++ 中的 QL_FAIL
            throw new IllegalArgumentException("Unknown month value: " + value);
        }
        return month;
    }

    public static void main(String[] args) {
        // 默认输出 (调用 toString(), 对应 C++ 的 operator<<)
        Month currentMonth = Month.SEPTEMBER;
        System.out.println("Default output (Full Name): " + currentMonth); // 输出: Default output (Full Name): September

        // 获取整数值
        int monthValue = currentMonth.getValue();
        System.out.println("Integer value: " + monthValue); // 输出: Integer value: 9

        // 获取短名称 (对应 C++ 代码中冗余定义的 Jan, Feb, Mar...)
        String shortName = currentMonth.getShortName();
        System.out.println("Short name: " + shortName); // 输出: Short name: Sep

        // 使用整数值进行查找
        Month foundMonth = Month.fromValue(3);
        System.out.println("Month with value 3: " + foundMonth); // 输出: Month with value 3: March
    }
}