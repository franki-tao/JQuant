package jquant.utilities;

// 模仿
// 声明一个接受 int 和 double，返回 bool 的函数对象
//std::function<bool(int)> func;
//SharePtr<bool>(int) 实现value
public abstract class SharePtr<T, S> {

    public abstract T value(S v);
}
