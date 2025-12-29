package jquant.utilities;

import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * ObservableValue 类，用于封装一个值 (T)，并在该值改变时通知所有注册的监听器。
 * 这遵循了 Java Bean 的规范。
 * @param <T> 被封装的值的类型。
 */
public class ObservableValue<T> implements Observable {
    private final Set<Observer> observers = new HashSet<>();
    // 存储被封装的实际值
    private T value;

    // 观察者模式的核心：PropertyChangeSupport 负责管理所有监听器和发送通知
    private final PropertyChangeSupport support = new PropertyChangeSupport(this);

    // 用于通知的属性名
    public static final String VALUE_PROPERTY = "value";

    // --- 构造函数 ---

    // 默认构造函数
    public ObservableValue() {
        this.value = null; // 或者根据 T 的类型使用默认值
    }

    // 接受初始值的构造函数
    public ObservableValue(T initialValue) {
        this.value = initialValue;
    }

    // --- Getter 方法 (类似 C++ 的 value() 或 operator T()) ---

    /**
     * 获取当前被封装的值。
     * @return 当前值。
     */
    public T getValue() {
        return value;
    }

    // --- Setter 方法 (类似 C++ 的 operator=) ---

    /**
     * 设置新值，并通知所有观察者。
     * 只有当新值与旧值不同时，才会触发通知。
     * @param newValue 要设置的新值。
     */
    public void setValue(T newValue) {
        notifyObservers();
        // 1. 获取旧值
        T oldValue = this.value;

        // 2. 检查值是否真正发生变化 (避免不必要的通知)
        if (!Objects.equals(oldValue, newValue)) {
            // 3. 更新值
            this.value = newValue;

            // 4. 通知观察者
            // firePropertyChange 会通知所有监听器，并传递属性名、旧值和新值。
            support.firePropertyChange(VALUE_PROPERTY, oldValue, newValue);
        }
    }

    // --- 观察者管理方法 (类似 C++ 的 operator ext::shared_ptr<Observable>()) ---

    /**
     * 注册一个观察者（监听器）。
     * @param listener 要注册的 PropertyChangeListener。
     */
    public void addChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }

    /**
     * 移除一个观察者（监听器）。
     * @param listener 要移除的 PropertyChangeListener。
     */
    public void removeChangeListener(PropertyChangeListener listener) {
        support.removePropertyChangeListener(listener);
    }

    @Override
    public void registerObserver(Observer o) {
        observers.add(o);
    }

    @Override
    public void unregisterObserver(Observer o) {
        observers.remove(o);
    }

    @Override
    public void notifyObservers() {
        if (!ObservableSettings.getInstance().isUpdatesEnabled()) {
            ObservableSettings.getInstance().registerDeferred(observers);
        } else {
            for (Observer observer : observers) {
                try {
                    observer.update();
                } catch (Exception e) {
                    System.err.println("Error notifying observer: " + e.getMessage());
                }
            }
        }
    }

    public static void main(String[] args) {
        // 1. 创建一个可观察的整数值，初始值为 10
        ObservableValue<Integer> counter = new ObservableValue<>(10);

        // 2. 创建一个观察者 (监听器)
        PropertyChangeListener observer = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                // 打印通知的详细信息
                System.out.println("--- 收到通知 ---");
                System.out.println("属性名: " + evt.getPropertyName()); // "value"
                System.out.println("旧值: " + evt.getOldValue());      // 10 (Integer)
                System.out.println("新值: " + evt.getNewValue());      // 20 (Integer)
            }
        };

        // 3. 注册观察者到 ObservableValue
        counter.addChangeListener(observer);

        // --- 触发变化 ---
        System.out.println("尝试设置新值 20...");
        counter.setValue(20); // 触发通知

        // --- 不触发变化 ---
        System.out.println("\n尝试设置相同值 20...");
        counter.setValue(20); // 不触发通知 (因为值未改变)

        // --- 移除观察者 ---
        counter.removeChangeListener(observer);

        System.out.println("\n移除观察者，尝试设置新值 30...");
        counter.setValue(30); // 不会打印通知

        System.out.println("当前值: " + counter.getValue());
    }
}
