package jquant.time;

/**
 * Units used to describe time periods.
 * This corresponds to the TimeUnit enum in QuantLib.
 */
public enum TimeUnit {

    // 1. 枚举常量定义
    DAYS("Days"),
    WEEKS("Weeks"),
    MONTHS("Months"),
    YEARS("Years"),
    HOURS("Hours"),
    MINUTES("Minutes"),
    SECONDS("Seconds"),
    MILLISECONDS("Milliseconds"),
    MICROSECONDS("Microseconds");

    // 2. 私有字段：存储要作为输出的字符串。
    private final String outputName;

    // 3. 私有构造函数：初始化枚举实例的数据。
    private TimeUnit(String outputName) {
        this.outputName = outputName;
    }

    // 4. 替代 C++ 的 operator<< 重载：使用标准的 toString() 方法。
    // 它返回 C++ 代码中 switch-case 语句对应的字符串。
    @Override
    public String toString() {
        return this.outputName;
    }

    // 5. 辅助方法（可选）：提供获取全大写名称的 Java 标准方法
    public String nameUpperCase() {
        return this.name();
    }

    public static void main(String[] args) {
        // 获取一个枚举常量
        TimeUnit unit = TimeUnit.MONTHS;

        // 默认输出 (调用 toString() 方法，对应 C++ 的 operator<<)
        System.out.println("Time Unit Default Output: " + unit);
        // 输出: Time Unit Default Output: Months

        // 可以在 switch 语句中使用
        switch (unit) {
            case DAYS:
                // 处理天
                break;
            case YEARS:
                // 处理年
                break;
            case MONTHS:
                System.out.println("Processing " + unit.toString()); // 输出: Processing Months
                break;
            default:
                // 模仿 QL_FAIL 逻辑，但在 Java 枚举中，default 不应被 reach
                throw new IllegalStateException("Unexpected TimeUnit: " + unit);
        }

        // 访问 Java 枚举的默认名称 (全大写)
        System.out.println("Java Standard Name: " + unit.name());
        // 输出: Java Standard Name: MONTHS
    }
}
