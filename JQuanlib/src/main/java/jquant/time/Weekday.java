package jquant.time;

/**
 * Days serial number MOD 7, mapping to Excel's WEEKDAY function (where Sunday = 7, not 1).
 * This enum provides the standard day index (1-7) and multiple string formats.
 */
public enum Weekday {

    // (Value, Long Name, Short Name, Shortest Name)
    SUNDAY(1, "Sunday", "Sun", "Su"),
    MONDAY(2, "Monday", "Mon", "Mo"),
    TUESDAY(3, "Tuesday", "Tue", "Tu"),
    WEDNESDAY(4, "Wednesday", "Wed", "We"),
    THURSDAY(5, "Thursday", "Thu", "Th"),
    FRIDAY(6, "Friday", "Fri", "Fr"),
    SATURDAY(7, "Saturday", "Sat", "Sa");

    private final int value;
    private final String longName;
    private final String shortName;
    private final String shortestName;

    private Weekday(int value, String longName, String shortName, String shortestName) {
        this.value = value;
        this.longName = longName;
        this.shortName = shortName;
        this.shortestName = shortestName;
    }


    /**
     * 获取日期的序列值 (1 = Sunday ... 7 = Saturday).
     * @return 整数值
     */
    public int getValue() {
        return value;
    }

    /**
     * 获取长格式名称 (e.g., "Sunday").
     * @return 完整的星期名称
     */
    public String getLongName() {
        return longName;
    }

    /**
     * 获取短格式名称 (三字母, e.g., "Sun").
     * @return 三字母缩写
     */
    public String getShortName() {
        return shortName;
    }

    /**
     * 获取最短格式名称 (两字母, e.g., "Su").
     * @return 两字母缩写
     */
    public String getShortestName() {
        return shortestName;
    }

    // 替代 C++ 的 operator<< 重载：使用标准的 toString() 方法。
    // 默认输出长格式名称。
    @Override
    public String toString() {
        return this.longName;
    }

    // 静态查找方法 (Java 特有，非常实用)

    private static final Weekday[] BY_VALUE = new Weekday[8]; // 索引 1-7 有效

    // 使用静态块初始化查找表，确保高效查找
    static {
        for (Weekday day : values()) {
            BY_VALUE[day.value] = day;
        }
    }

    /**
     * 根据整数值 (1-7) 查找对应的 Weekday 枚举常量。
     * @param value 星期值 (1=Sunday, 7=Saturday)
     * @return 对应的 Weekday 枚举常量
     * @throws IllegalArgumentException 如果值不在 1-7 范围内
     */
    public static Weekday valueOf(int value) {
        if (value < 1 || value > 7) {
            throw new IllegalArgumentException("Unknown weekday value: " + value);
        }
        return BY_VALUE[value];
    }

    public static void main(String[] args) {
        // 默认输出 (调用 toString(), 对应 C++ 的默认 operator<<)
        Weekday day = Weekday.WEDNESDAY;
        System.out.println("Default output (Long): " + day); // 输出: Default output (Long): Wednesday

        // 使用 getter 方法获取特定格式（对应 C++ 的 io::* 函数）
        System.out.println("Short name: " + day.getShortName());      // 输出: Short name: Wed
        System.out.println("Shortest name: " + day.getShortestName());  // 输出: Shortest name: We

        // 使用整数值进行查找（Java 常用功能）
        Weekday foundDay = Weekday.valueOf(1);
        System.out.println("Day with value 1: " + foundDay); // 输出: Day with value 1: Sunday
    }
}
