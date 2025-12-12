package jquant.time;

import java.util.HashMap;
import java.util.Map;

/**
 * Rules for generating a schedule of dates, typically for coupon payments or observations.
 * This corresponds to the DateGeneration::Rule enum in QuantLib.
 */
public enum DateGenerationRule {

    // 1. 枚举常量定义：(Implicit Value, Output String)

    BACKWARD(0, "Backward",
            "Backward from termination date to effective date."),
    FORWARD(1, "Forward",
            "Forward from effective date to termination date."),
    ZERO(2, "Zero",
            "No intermediate dates between effective date and termination date."),
    THIRD_WEDNESDAY(3, "ThirdWednesday",
            "All dates but effective date and termination date are taken to be on the third wednesday of their month (with forward calculation.)"),
    THIRD_WEDNESDAY_INCLUSIVE(4, "ThirdWednesdayInclusive",
            "All dates including effective date and termination date are taken to be on the third wednesday of their month (with forward calculation.)"),
    TWENTIETH(5, "Twentieth",
            "All dates but the effective date are taken to be the twentieth of their month (used for CDS schedules in emerging markets.) The termination date is also modified."),
    TWENTIETH_IMM(6, "TwentiethIMM",
            "All dates but the effective date are taken to be the twentieth of an IMM month (used for CDS schedules.) The termination date is also modified."),
    OLD_CDS(7, "OldCDS",
            "Same as TwentiethIMM with unrestricted date ends and log/short stub coupon period (old CDS convention)."),
    CDS(8, "CDS",
            "Credit derivatives standard rule since 'Big Bang' changes in 2009."),
    CDS2015(9, "CDS2015",
            "Credit derivatives standard rule since December 20th, 2015.");

    // 2. 私有字段
    private final int value;
    private final String outputName;
    private final String description;

    // 3. 私有构造函数
    private DateGenerationRule(int value, String outputName, String description) {
        this.value = value;
        this.outputName = outputName;
        this.description = description;
    }

    // 4. 公共方法：获取整数值和描述

    /**
     * 获取 C++ 枚举中隐式或显式的整数值。
     * @return 整数值 (e.g., 0 for BACKWARD)
     */
    public int getValue() {
        return value;
    }

    /**
     * 获取规则的描述。
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

    private static final Map<Integer, DateGenerationRule> VALUE_MAP = new HashMap<>();

    static {
        for (DateGenerationRule rule : values()) {
            VALUE_MAP.put(rule.value, rule);
        }
    }

    /**
     * 根据整数值查找对应的 DateGenerationRule 枚举常量。
     * @param value 整数值
     * @return 对应的枚举常量
     * @throws IllegalArgumentException 如果值未定义
     */
    public static DateGenerationRule fromValue(int value) {
        DateGenerationRule rule = VALUE_MAP.get(value);
        if (rule == null) {
            // 模仿 C++ 中的 QL_FAIL 逻辑
            throw new IllegalArgumentException("Unknown DateGenerationRule value: " + value);
        }
        return rule;
    }

    public static void main(String[] args) {
        // 获取一个枚举常量
        DateGenerationRule rule = DateGenerationRule.BACKWARD;

        // 默认输出 (调用 toString() 方法，对应 C++ 的 operator<<)
        System.out.println("Rule Name: " + rule);
        // 输出: Rule Name: Modified Following

        // 获取关联的整数值
        int value = rule.getValue();
        System.out.println("Rule Value: " + value);
        // 输出: Rule Value: 1

        // 使用整数值进行查找
        DateGenerationRule foundRule = DateGenerationRule.fromValue(7); // 7 对应 OldCDS
        System.out.println("Rule from value 7: " + foundRule);
        // 输出: Rule from value 7: OldCDS
    }
}
