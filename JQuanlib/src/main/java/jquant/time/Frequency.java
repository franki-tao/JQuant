package jquant.time;

import java.util.HashMap;
import java.util.Map;

/**
 * Units used to describe the frequency of events (e.g., bond coupon payments).
 * This corresponds to the Frequency enum in QuantLib.
 */
public enum Frequency {

    // 1. 枚举常量定义：(Value, Output String)
    NO_FREQUENCY(-1, "No-Frequency"),     //!< null frequency
    ONCE(0, "Once"),                      //!< only once, e.g., a zero-coupon
    ANNUAL(1, "Annual"),                  //!< once a year
    SEMIANNUAL(2, "Semiannual"),          //!< twice a year
    EVERY_FOURTH_MONTH(3, "Every-Fourth-Month"), //!< every fourth month
    QUARTERLY(4, "Quarterly"),            //!< every third month
    BIMONTHLY(6, "Bimonthly"),            //!< every second month
    MONTHLY(12, "Monthly"),               //!< once a month
    EVERY_FOURTH_WEEK(13, "Every-fourth-week"), //!< every fourth week
    BIWEEKLY(26, "Biweekly"),             //!< every second week
    WEEKLY(52, "Weekly"),                 //!< once a week
    DAILY(365, "Daily"),                  //!< once a day
    OTHER_FREQUENCY(999, "Unknown frequency"); //!< some other unknown frequency

    // 2. 私有字段
    private final int value;
    private final String outputName;

    // 3. 私有构造函数
    private Frequency(int value, String outputName) {
        this.value = value;
        this.outputName = outputName;
    }

    // 4. 公共方法：获取整数值

    /**
     * 获取 QuantLib 中定义的频率整数值。
     * @return 整数值 (e.g., 1 for ANNUAL, 12 for MONTHLY)
     */
    public int getValue() {
        return value;
    }

    // 5. 替代 C++ 的 operator<< 重载：使用标准的 toString() 方法。
    @Override
    public String toString() {
        return this.outputName;
    }

    // --- 6. 静态查找功能（用于通过整数值查找枚举） ---

    private static final Map<Integer, Frequency> VALUE_MAP = new HashMap<>();

    // 使用静态块初始化查找表，确保高效查找
    static {
        for (Frequency frequency : values()) {
            VALUE_MAP.put(frequency.value, frequency);
        }
    }

    /**
     * 根据整数值查找对应的 Frequency 枚举常量。
     * @param value 频率整数值
     * @return 对应的 Frequency 枚举常量
     * @throws IllegalArgumentException 如果值未定义
     */
    public static Frequency fromValue(int value) {
        Frequency frequency = VALUE_MAP.get(value);
        if (frequency == null) {
            // 模仿 C++ 中的 QL_FAIL 逻辑
            throw new IllegalArgumentException("Unknown frequency value: " + value);
        }
        return frequency;
    }

    public static void main(String[] args) {
        // 获取一个枚举常量
        Frequency freq = Frequency.SEMIANNUAL;

        // 默认输出 (调用 toString() 方法，对应 C++ 的 operator<<)
        System.out.println("Payment Frequency: " + freq);
        // 输出: Payment Frequency: Semiannual

        // 获取关联的整数值
        int value = freq.getValue();
        System.out.println("Value (Times per Year): " + value);
        // 输出: Value (Times per Year): 2

        // 使用整数值进行查找
        Frequency unknownFreq = Frequency.fromValue(-1);
        System.out.println("Frequency from value -1: " + unknownFreq);
        // 输出: Frequency from value -1: No-Frequency
    }
}