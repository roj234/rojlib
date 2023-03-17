package roj.math;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/2/15 9:04
 */
public interface Polygon {
	/**
	 * 矩形边界
	 */
	default Rect2d getBounds() {
		return new Rect2d(Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE).expandIf(getPoints());
	}

	/**
	 * 点, 有序
	 */
	List<Vec2d> getPoints();
}
