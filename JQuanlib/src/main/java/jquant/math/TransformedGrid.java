package jquant.math;

/*! \deprecated Part of the old FD framework; copy this function
                in your codebase if needed.
                Deprecated in version 1.37.
*/
public class TransformedGrid {
    protected Array grid_;
    protected Array transformedGrid_;
    protected Array dxm_;
    protected Array dxp_;
    protected Array dx_;

    public TransformedGrid(final Array grid) {
        grid_ = new Array(grid);
        transformedGrid_ = new Array(grid);
        dxm_ = new Array(grid.size());
        dxp_ = new Array(grid.size());
        dx_ = new Array(grid.size());
        for (int i = 1; i < transformedGrid_.size() - 1; i++) {
            dxm_.set(i, transformedGrid_.get(i) - transformedGrid_.get(i - 1));
            dxp_.set(i, transformedGrid_.get(i + 1) - transformedGrid_.get(i));
            dx_.set(i, dxm_.get(i) + dxp_.get(i));
        }
    }

    public TransformedGrid(final Array grid, Function func) {
        grid_ = new Array(grid);
        transformedGrid_ = new Array(grid.size());
        dxm_ = new Array(grid.size());
        dxp_ = new Array(grid.size());
        dx_ = new Array(grid.size());
        for (int i = 0; i < grid_.size(); i++) {
            transformedGrid_.set(i, func.value(grid_.get(i)));
        }
        for (int i = 1; i < transformedGrid_.size() - 1; i++) {
            dxm_.set(i, transformedGrid_.get(i) - transformedGrid_.get(i - 1));
            dxp_.set(i, transformedGrid_.get(i + 1) - transformedGrid_.get(i));
            dx_.set(i, dxm_.get(i) + dxp_.get(i));
        }
    }

    public final Array gridArray() {
        return grid_;
    }

    public final Array transformedGridArray() {
        return transformedGrid_;
    }

    public final Array dxmArray() {
        return dxm_;
    }

    public final Array dxpArray() {
        return dxp_;
    }

    public final Array dxArray() {
        return dx_;
    }

    public double grid(int i) {
        return grid_.get(i);
    }

    public double transformedGrid(int i) {
        return transformedGrid_.get(i);
    }

    public double dxm(int i) {
        return dxm_.get(i);
    }

    public double dxp(int i) {
        return dxp_.get(i);
    }

    public double dx(int i) {
        return dx_.get(i);
    }

    public int size() {
        return grid_.size();
    }
}
