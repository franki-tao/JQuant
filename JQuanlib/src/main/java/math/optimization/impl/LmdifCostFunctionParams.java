package math.optimization.impl;

//LmdifCostFunction参数定义
//后三个参数需要注意，传入的是指针，可能变动
public class LmdifCostFunctionParams {
    public int x1;
    public int x2;
    public double[] v1;
    public double[] v2;
    public int v3;

    public LmdifCostFunctionParams(int x1, int x2, double[] v1, double[] v2, int v3) {
        this.x1 = x1;
        this.x2 = x2;
        this.v1 = v1;
        this.v2 = v2;
        this.v3 = v3;
    }
}
