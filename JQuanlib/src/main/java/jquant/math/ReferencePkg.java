package jquant.math;

/**
 * 消除java中值传递带来的问题
 * @param <T>
 */
public final class ReferencePkg <T> {
    private T t;

    public ReferencePkg() {
    }

    public ReferencePkg(T t) {
        this.t = t;
    }

    public T getT() {
        return t;
    }

    public void setT(T t) {
        this.t = t;
    }

    public static void main(String[] args) {
        ReferencePkg<Integer> referencePkg = new ReferencePkg<>(10);
    }
}
