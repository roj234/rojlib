package roj.math;

import java.util.ArrayList;
import java.util.List;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/2/15 8:36
 */
public class MPShapeImpl implements MPShape {
    public List<Vec2d> points = new ArrayList<>();
    public final Rect2d bounds = Rect2d.from();

    public MPShape addPoint(Vec2d point) {
        bounds.expandIf(point);
        points.add(point);
        return this;
    }

    @Override
    public Rect2d getBounds() {
        return bounds;
    }

    @Override
    public List<Vec2d> getPoints() {
        return points;
    }
}
