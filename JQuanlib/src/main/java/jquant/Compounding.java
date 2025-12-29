package jquant;
import java.util.HashMap;
import java.util.Map;

/**
 * 利率复利计算规则枚举
 * 对应C++的Compounding枚举及operator<<输出运算符功能
 */
public enum Compounding {
    // 枚举常量定义（与C++一一对应，显式指定索引值保持一致性）
    Simple(0, "Simple"),                  // 对应公式: 1+rt
    Compounded(1, "Compounded"),          // 对应公式: (1+r)^t
    Continuous(2, "Continuous"),          // 对应公式: e^(rt)
    SimpleThenCompounded(3, "SimpleThenCompounded"),  // 首期前单利，之后复利
    CompoundedThenSimple(4, "CompoundedThenSimple");  // 首期前复利，之后单利

    // 枚举常量对应的索引值（对应C++枚举的赋值）
    private final int index;
    // 枚举常量对应的字符串描述（对应C++输出运算符的返回值）
    private final String description;

    // 静态映射：用于通过索引快速查找枚举常量（可选增强功能，保持与C++枚举索引一致性）
    private static final Map<Integer, Compounding> INDEX_TO_ENUM_MAP;

    // 静态代码块：初始化索引-枚举映射
    static {
        INDEX_TO_ENUM_MAP = new HashMap<>();
        for (Compounding compounding : values()) {
            INDEX_TO_ENUM_MAP.put(compounding.getIndex(), compounding);
        }
    }

    /**
     * 枚举构造方法（Java枚举构造方法默认私有）
     * @param index 枚举索引值
     * @param description 枚举字符串描述
     */
    Compounding(int index, String description) {
        this.index = index;
        this.description = description;
    }

    /**
     * 获取枚举索引值
     * @return 对应C++枚举的赋值（0/1/2/3/4）
     */
    public int getIndex() {
        return index;
    }

    /**
     * 获取枚举字符串描述（对应C++ operator<<的输出功能）
     * @return 枚举对应的字符串名称
     */
    public String getDescription() {
        return description;
    }

    /**
     * 覆盖toString方法，直接返回描述字符串（简化输出调用，更贴近C++的输出行为）
     * @return 枚举对应的字符串名称
     */
    @Override
    public String toString() {
        return this.description;
    }

    /**
     * 静态方法：通过索引查找枚举常量（模拟C++枚举的索引访问特性）
     * @param index 枚举索引
     * @return 对应的Compounding枚举常量
     * @throws IllegalArgumentException 当索引不存在时抛出异常（对应C++的QL_FAIL）
     */
    public static Compounding fromIndex(int index) {
        Compounding compounding = INDEX_TO_ENUM_MAP.get(index);
        if (compounding == null) {
            throw new IllegalArgumentException("unknown compounding type, index: " + index);
        }
        return compounding;
    }
}