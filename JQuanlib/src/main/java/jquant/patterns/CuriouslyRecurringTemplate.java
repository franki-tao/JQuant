package jquant.patterns;

//! Support for the curiously recurring template pattern
/*! See James O. Coplien. A Curiously Recurring Template Pattern.
    In Stanley B. Lippman, editor, C++ Gems, 135-144.
    Cambridge University Press, New York, New York, 1996.

    \ingroup patterns
*/
public abstract class CuriouslyRecurringTemplate<T extends CuriouslyRecurringTemplate<T>> {

    // 限制构造，模拟 C++ 的 protected
    protected CuriouslyRecurringTemplate() {}

    // 获取派生类的引用
    @SuppressWarnings("unchecked")
    protected T impl() {
        return (T) this;
    }

    // 基类中的通用逻辑，调用派生类的方法
    public void execute() {
        System.out.println("Starting common logic...");
        // 调用子类特有的方法
        impl().specificAction();
    }

    // 强制子类必须实现该方法，或者作为扩展点
    protected abstract void specificAction();
}