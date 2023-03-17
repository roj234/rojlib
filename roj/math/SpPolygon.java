package roj.math;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/2/15 8:36
 */
public class SpPolygon implements Polygon {
	public List<Vec2d> points = new ArrayList<>();
	public final Rect2d bounds = Rect2d.from();

	public Polygon addPoint(Vec2d point) {
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
