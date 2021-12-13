package roj.terrain;

import roj.math.MathUtils;
import roj.math.Rect2d;
import roj.math.Vec2d;

public class RectX extends Rect2d {
    public final double width, height;

    public RectX(double x, double y, double width, double height) {
        super(x, y, x + width, y + height);
        this.width = width;
        this.height = height;
    }

    public boolean liesOnAxes(Vec2d p) {
        return MathUtils.nearEps(p.x, xmin, 1) || MathUtils.nearEps(p.y, ymin, 1) ||
            MathUtils.nearEps(p.x, xmax, 1) || MathUtils.nearEps(p.y, ymax, 1);
    }
}
