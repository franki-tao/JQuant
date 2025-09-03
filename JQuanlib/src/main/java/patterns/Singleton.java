package patterns;

/**
 * 考虑java特殊性， 我们通过声明空Singleton来标志单例
 * 例如
 * class MyClass implements Singleton<MyClass> {
 *     private MyClass() {}
 *     private static MyClass instance = new MyClass();
 *     public static MyClass Instance() {return instance;}
 * }
 *
 *
 * @param <T>
 */
public interface Singleton<T>{
}
