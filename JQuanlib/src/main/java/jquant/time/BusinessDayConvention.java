package jquant.time;

import java.util.HashMap;
import java.util.Map;

/**
 * Units used to adjust dates that fall on a non-business day (holiday or weekend).
 * This corresponds to the BusinessDayConvention enum in QuantLib.
 */
public enum BusinessDayConvention {

    // 1. 枚举常量定义：(Value, Output String, Description)
    // 假设默认值从 0 开始 (C++ 行为)

    // ISDA Conventions
    FOLLOWING(0, "Following",
            "Choose the first business day after the given holiday."),
    MODIFIED_FOLLOWING(1, "Modified Following",
            "Choose the first business day after the holiday unless it belongs to a different month, in which case choose the first business day before the holiday."),
    PRECEDING(2, "Preceding",
            "Choose the first business day before the given holiday."),

    // NON ISDA Conventions
    MODIFIED_PRECEDING(3, "Modified Preceding",
            "Choose the first business day before the given holiday unless it belongs to a different month, in which case choose the first business day after the holiday."),
    UNADJUSTED(4, "Unadjusted",
            "Do not adjust the date."),
    HALF_MONTH_MODIFIED_FOLLOWING(5, "Half-Month Modified Following",
            "Choose the first business day after the holiday unless that day crosses the mid-month (15th) or the end of month, in which case choose the first business day before the holiday."),
    NEAREST(6, "Nearest",
            "Choose the nearest business day to the given holiday. If both the preceding and following business days are equally far away, default to following business day.");

    // 2. 私有字段
    private final int value;
    private final String outputName;
    private final String description;

    // 3. 私有构造函数
    private BusinessDayConvention(int value, String outputName, String description) {
        this.value = value;
        this.outputName = outputName;
        this.description = description;
    }

    // 4. 公共方法：获取整数值

    /**
     * 获取 C++ 枚举中隐式或显式的整数值。
     * @return 整数值 (e.g., 0 for FOLLOWING)
     */
    public int getValue() {
        return value;
    }

    /**
     * 获取惯例的描述。
     * @return 描述字符串
     */
    public String getDescription() {
        return description;
    }

    // 5. 替代 C++ 的 operator<< 重载：使用标准的 toString() 方法。
    @Override
    public String toString() {
        return this.outputName;
    }

    // --- 6. 静态查找功能（通过整数值查找枚举） ---

    private static final Map<Integer, BusinessDayConvention> VALUE_MAP = new HashMap<>();

    static {
        for (BusinessDayConvention convention : values()) {
            VALUE_MAP.put(convention.value, convention);
        }
    }

    /**
     * 根据整数值查找对应的 BusinessDayConvention 枚举常量。
     * @param value 整数值
     * @return 对应的枚举常量
     * @throws IllegalArgumentException 如果值未定义
     */
    public static BusinessDayConvention fromValue(int value) {
        BusinessDayConvention convention = VALUE_MAP.get(value);
        if (convention == null) {
            // 模仿 C++ 中的 QL_FAIL 逻辑
            throw new IllegalArgumentException("Unknown BusinessDayConvention value: " + value);
        }
        return convention;
    }

    public static void main(String[] args) {
        // 获取一个枚举常量
        BusinessDayConvention convention = BusinessDayConvention.MODIFIED_FOLLOWING;

        // 默认输出 (调用 toString() 方法，对应 C++ 的 operator<<)
        System.out.println("Convention Name: " + convention);
        // 输出: Convention Name: Modified Following

        // 获取关联的整数值
        int value = convention.getValue();
        System.out.println("Convention Value: " + value);
        // 输出: Convention Value: 1

        // 获取描述信息
        String desc = convention.getDescription();
        System.out.println("Description: " + desc);
        // 输出: Description: Choose the first business day after the holiday unless it belongs to a different month, in which case choose the first business day before the holiday.

        // 使用整数值进行查找
        BusinessDayConvention foundConvention = BusinessDayConvention.fromValue(3);
        System.out.println("Convention from value 3: " + foundConvention);
        // 输出: Convention from value 3: Modified Preceding
    }
}